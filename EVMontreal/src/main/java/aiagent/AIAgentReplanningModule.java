package aiagent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Provider;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.replanning.PlanStrategyModule;
import org.matsim.contrib.common.util.StraightLineKnnFinder;
import org.matsim.contrib.ev.charging.ChargingLogic;
import org.matsim.contrib.ev.charging.ChargingPower;
import org.matsim.contrib.ev.discharging.AuxEnergyConsumption;
import org.matsim.contrib.ev.discharging.DriveEnergyConsumption;
import org.matsim.contrib.ev.fleet.ElectricFleetSpecification;
import org.matsim.contrib.ev.fleet.ElectricVehicleSpecification;
import org.matsim.contrib.ev.infrastructure.ChargerSpecification;
import org.matsim.contrib.ev.infrastructure.ChargingInfrastructureSpecification;
import org.matsim.core.config.Config;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.replanning.ReplanningContext;
import org.matsim.core.router.SingleModeNetworksCache;
import org.matsim.core.router.TripRouter;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.utils.timing.TimeInterpretation;
import org.matsim.vehicles.VehicleUtils;
import org.matsim.vehicles.Vehicles;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;

import EVPricing.ChargerPricingProfiles;
import apikeys.APIKeys;
import gsonprocessor.PlanGson;
import nlprocessor.GsonTrial.PlanElementDeserializer;
import rest.ChatCompletionClient;
import rest.Prompt;
import urbanEV.ActivityWhileChargingFinder;
import urbanEV.UrbanEVConfigGroup;

public class AIAgentReplanningModule implements PlanStrategyModule{
	public static final String PLUGIN = "plugin";
	public static final String PLUGOUT = "plugout";
	private Map<Id<Person>, String> chatHistory = null; 
	private ChatCompletionClient aiChatClient = null; 
	private Set<Plan> plans = null;
	private Gson gson;
	private final Set<String> allowedMode = new HashSet<>();
	public static final String AIReplanningStategyName = "AIEvReplanning";
	public static final int maxTry = 5;
	
	@Inject
	protected Provider<TripRouter> tripRouterProvider;

	@Inject
	Scenario scenario;

	@Inject
	Vehicles vehicles;

	@Inject
	protected SingleModeNetworksCache singleModeNetworksCache;

	@Inject
	private ElectricFleetSpecification electricFleetSpecification;

	@Inject
	protected ChargingInfrastructureSpecification chargingInfrastructureSpecification;

	@Inject
	protected DriveEnergyConsumption.Factory driveConsumptionFactory;

	@Inject
	protected AuxEnergyConsumption.Factory auxConsumptionFactory;

	@Inject
	protected ChargingPower.Factory chargingPowerFactory;

	@Inject
	private ChargingLogic.Factory chargingLogicFactory;

	@Inject
	private Map<String, TravelTime> travelTimes;

	@Inject
	ActivityWhileChargingFinder activityWhileChargingFinder;

	@Inject
	OutputDirectoryHierarchy controlerIO;

	private final double thresholdDuration = 3*3600;
	
	@Inject
	private TimeInterpretation time;
	
	@Inject
	private UrbanEVConfigGroup urbanEV;


	Config config;
	
	

	@Inject 
	private ChargerPricingProfiles chargerPricingProfiles;
	
	@Inject
	AIAgentReplanningModule(Config config){
		this.config = config;
	}

	@Override
	public void prepareReplanning(ReplanningContext replanningContext) {
		chatHistory = new HashMap<>();
		aiChatClient = new ChatCompletionClient.Builder()
				.setChatAPI_URL("https://api.openai.com/v1/chat/completions")//http://localhost:1234/v1/chat/completions
				.setEmbeddingAPI_URL("http://localhost:1234/v1/embeddings")
				.setModelName("gpt-3.5-turbo")
				.setauthorization(APIKeys.GPT_KEY)
				.setOrganization(APIKeys.ORGANIZATION_ID)
				.setProject(APIKeys.PROJECT_ID)
				.setIfStream(false)
				.setMaxToken(4096)
				.setTemperature(.7)
				.build();
		plans = new HashSet<>();
		GsonBuilder gsonBuilder = new GsonBuilder()
				.registerTypeAdapter(PlanElement.class, new PlanElementDeserializer());
		gsonBuilder.serializeNulls();
	    gsonBuilder.serializeSpecialFloatingPointValues();
	    gsonBuilder.setPrettyPrinting();
	    gson = gsonBuilder.create(); 
	    allowedMode.add(TransportMode.walk);
	    allowedMode.add(TransportMode.pt);
	    allowedMode.add(TransportMode.car);
	    allowedMode.add(TransportMode.bike);
	    
	}

	@Override
	public void handlePlan(Plan plan) {
		this.plans.add(plan);
	}

	@Override
	public void finishReplanning() {
		for(Plan plan:plans) {
			makeChargingNotStaged(plan);
			Plan planOut = null;
			String userMsg = Prompt.HARDCODED_PROMOPT_EV_CHARGING;
			
			for(int i=0; i<maxTry;i++) {
				List<ErrorMessage> errors = new ArrayList<>();
				PlanGson pg = PlanGson.createPlanGson(plan);
				userMsg = userMsg+"json```\n"+gson.toJson(pg)+"\n```";
				if(i>0)userMsg = errors.get(0).errorMessage;
				String aiString = aiChatClient.getResponse(Prompt.DEAFULT_SYSTEM_MSG, userMsg);
				int begin = aiString.indexOf("```"); 
				int end = aiString.lastIndexOf("```");
				String jsonString = aiString.substring(begin,end).replace("```", "");
				PlanGson pgOut = gson.fromJson(jsonString, PlanGson.class);
				planOut = pgOut.getPlan();
				boolean ifOkay = identifyChargingActivityAndInsertChargingLink(planOut,plan,errors);
				if(ifOkay)break;
			}
			makeChargingStaged(planOut);
			PopulationUtils.copyFromTo(planOut, plan);
		}
	}
	/**
	 * will return if the charging events were consistent and proper charger insertion was possible
	 * @param plan
	 * @return
	 */
	public boolean identifyChargingActivityAndInsertChargingLink(Plan plan, Plan originalPlan, List<ErrorMessage> msgs) {
		boolean ifCharging = false;
		Id<Link> linkIdForCharger = null;
		List<String> actsWhileCharging = new ArrayList<>();
		List<String> oldActs = new ArrayList<>();
		ElectricVehicleSpecification electricVehicleSpecification = electricFleetSpecification.getVehicleSpecifications()
				.get(VehicleUtils.getVehicleId(plan.getPerson(), TransportMode.car));
		
		for(PlanElement pe:originalPlan.getPlanElements()) {
			if(pe instanceof Activity && !TripStructureUtils.isStageActivityType(((Activity)pe).getType())) {
				oldActs.add(((Activity)pe).getType());
			}else {
				this.allowedMode.add(((Leg)pe).getMode());
			}
		}
		int i = 0;
		int a = 0;
		
		double time = 0;
		for(PlanElement pe:plan.getPlanElements()) {
			if(pe instanceof Activity) {// while charging is happening, there has to be only one link id for both the plugin and plugout event 
				Activity act = ((Activity)pe);
				if(act.getEndTime().isDefined())time = act.getEndTime().seconds();
				if(((Activity)pe).getType().contains(PLUGIN)) {
					if(ifCharging) {
						msgs.add(new ErrorMessage("Cannot have two consecutive plugin event!"));
						return false;
					}
					Leg beforeLeg = null;
					if(i>0)beforeLeg = (Leg)plan.getPlanElements().get(i-1);
					
					if(!beforeLeg.getMode().equals(TransportMode.car)) {
						msgs.add(new ErrorMessage("The mode before plugin must be car. You need to bring car to plug it in!!!"));
						return false;
					}
					
					ChargerSpecification charger = this.selectChargerNearToLink(plan.getPerson().getId(), act.getLinkId(), 
							electricVehicleSpecification, this.scenario.getNetwork(), time);
					
					act.setLinkId(charger.getLinkId());
					linkIdForCharger = charger.getLinkId();
					ifCharging = true;
					
				}else if(((Activity)pe).getType().contains(PLUGOUT)) {
					if(!ifCharging) {
						msgs.add(new ErrorMessage("Nothing to plug out!!! There is no plugin event before this!!!"));
						return false;
					}
					
					Leg afterLeg = null;
					afterLeg = (Leg)plan.getPlanElements().get(i+1);
					
					if(!afterLeg.getMode().equals(TransportMode.car)) {
						msgs.add(new ErrorMessage("The mode after plugout must be car. You need to take your car from charger!!!"));
						return false;
					}
					
					if(actsWhileCharging.isEmpty()) {
						msgs.add(new ErrorMessage("Charging must be performed while performing other activities. Activities while charging list is empty!!!"));
						return false;
					}
					act.setLinkId(linkIdForCharger);
					ifCharging = false;
					actsWhileCharging.clear();
				}else {
					if(!oldActs.get(a).equals(((Activity)pe).getType())) {
						msgs.add(new ErrorMessage("Missing activities or wrong activity order from the original plan!!!"));
						return false;
					}
					if(ifCharging) {
						actsWhileCharging.add(((Activity)pe).getType());
					}
					a++;
				}
			}else {
				Leg leg = (Leg)pe;
				if(!this.allowedMode.contains(leg.getMode())) {
					msgs.add(new ErrorMessage("Unrecognized leg mode!!!Exiting!!!"));
					return false;
				}
			}
			
			i++;
		}
		return true;
	}
	
	public static void makeChargingNotStaged(Plan plan) {
		plan.getPlanElements().stream().filter(pe->pe instanceof Activity).forEach(pe->{
			if(((Activity)pe).getType().equals(PlanCalcScoreConfigGroup.createStageActivityType(PLUGIN))) {
				((Activity)pe).setType(PLUGIN);
			}else if(((Activity)pe).getType().equals(PlanCalcScoreConfigGroup.createStageActivityType(PLUGOUT))) {
				((Activity)pe).setType(PLUGOUT);
			}
		});
		}
		public static void makeChargingStaged(Plan plan) {
			plan.getPlanElements().stream().filter(pe->pe instanceof Activity).forEach(pe->{
				if(((Activity)pe).getType().equals(PLUGIN)) {
					((Activity)pe).setType(PlanCalcScoreConfigGroup.createStageActivityType(PLUGIN));
				}else if(((Activity)pe).getType().equals(PLUGOUT)) {
					((Activity)pe).setType(PlanCalcScoreConfigGroup.createStageActivityType(PLUGOUT));
				}
			});
			}

		public static class ErrorMessage{
			public ErrorMessage(String msg) {
				System.out.println(msg);
				errorMessage = msg;
			}
			public String errorMessage;
		}
		
		private int getTimeStep(double time) {
			if(time == 0) {
				time = 1;
			}else if(time>24*3600) {
				time = time-24*3600;
			}
			for(int i = 0; i<24;i++) {
				if(time>i*3600 && time<=(i+1)*3600) {
					return i;
				}
			}
			return 0;
		}
		
		protected ChargerSpecification selectChargerNearToLink(Id<Person>pId, Id<Link> linkId, ElectricVehicleSpecification vehicleSpecification, Network network, double time) {

			UrbanEVConfigGroup configGroup = (UrbanEVConfigGroup) config.getModules().get(UrbanEVConfigGroup.GROUP_NAME);
			double maxDistanceToAct = configGroup.getMaxDistanceBetweenActAndCharger_m();

			List<ChargerSpecification> chargerList = chargingInfrastructureSpecification.getChargerSpecifications()
					.values()
					.stream()
					.filter(charger -> vehicleSpecification.getChargerTypes().contains(charger.getChargerType()))
					.filter(c->this.chargerPricingProfiles.getChargerPricingProfiles().get(c.getId()).getPersonsAccecibleTo().isEmpty()||this.chargerPricingProfiles.getChargerPricingProfiles().get(c.getId()).getPersonsAccecibleTo().contains(pId))
					.filter(c->this.chargerPricingProfiles.getChargerPricingProfiles().get(c.getId()).getChargerSwitch().get((int) (this.getTimeStep(time))))
					.collect(Collectors.toList());

			//		List<ChargerSpecification> chargerList = new ArrayList<>();
			//		chargingInfrastructureSpecification.getChargerSpecifications()
			//		.values()
			//		.stream().forEach(c->{
			//			if(vehicleSpecification.getChargerTypes().contains(c.getChargerType()) && 
			//					(this.chargerPricingProfiles.getChargerPricingProfiles().get(c.getId()).getPersonsAccecibleTo().isEmpty() ||
			//							this.chargerPricingProfiles.getChargerPricingProfiles().get(c.getId()).getPersonsAccecibleTo().contains(pId))) {
			//				chargerList.add(c);
			//			}
			//		});

			StraightLineKnnFinder<Link, ChargerSpecification> straightLineKnnFinder = new StraightLineKnnFinder<>(
					6, l -> l.getFromNode().getCoord(), s -> network.getLinks().get(s.getLinkId()).getToNode().getCoord()); //TODO get closest X chargers and choose randomly?
			List<ChargerSpecification> nearestChargers = straightLineKnnFinder.findNearest(network.getLinks().get(linkId),chargerList.stream());
			List<ChargerSpecification>nearestChargersWithinLimit = nearestChargers.stream().filter(c->NetworkUtils.getEuclideanDistance(network.getLinks().get(linkId).getCoord(), network.getLinks().get(c.getLinkId()).getCoord())<maxDistanceToAct).collect(Collectors.toList()); 
			if (nearestChargersWithinLimit.isEmpty()) {
				//throw new RuntimeException("no charger could be found for vehicle type " + vehicleSpecification.getVehicleType());
				return null;
			}
			//double distanceFromActToCharger = NetworkUtils.getEuclideanDistance(network.getLinks().get(linkId).getToNode().getCoord(), network.getLinks().get(nearestChargers.get(0).getLinkId()).getToNode().getCoord());
			//		if (distanceFromActToCharger >= maxDistanceToAct) {
			//			return null;
			//		}
			//			//throw new RuntimeException("There are no chargers within 1000m");
			//			log.warn("Charger out of range. Inefficient charging " + NetworkUtils.getEuclideanDistance(network.getLinks().get(linkId).getToNode().getCoord(), network.getLinks().get(nearestChargers.get(0).getLinkId()).getToNode().getCoord()));
			//		}
			else{
				int rand = MatsimRandom.getRandom().nextInt(nearestChargersWithinLimit.size());//These two lines applies random charger selection. Delete these two lines and uncomment the commented line to get back to the original version. 
				return nearestChargersWithinLimit.get(rand);
				//return nearestChargers.get(0);

			}
		}
}
