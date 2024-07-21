package aiagent;

import static java.util.stream.Collectors.toSet;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
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
import org.matsim.core.controler.MatsimServices;
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
import org.matsim.vehicles.PersonVehicles;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleUtils;
import org.matsim.vehicles.Vehicles;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;

import EVPricing.ChargerPricingProfiles;
import apikeys.APIKeys;
import gsonprocessor.ActivityGson;
import gsonprocessor.PlanElementGson;
import gsonprocessor.PlanElementGsonDeserializer;
import gsonprocessor.PlanGson;
import gsonprocessor.PlanSchema;
import nlprocessor.GsonTrial.PlanElementDeserializer;
import rest.ChatCompletionClient;
import rest.Prompt;
import urbanEV.ActivityWhileChargingFinder;
import urbanEV.UrbanEVConfigGroup;

public class AIAgentReplanningModule implements PlanStrategyModule{
	public static final String PLUGIN = "plugin";
	public static final String PLUGOUT = "plugout";
	 
	
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
	
	@Inject
	private MatsimServices controler;


	Config config;
	
	public FileWriter fw = null;
	public int unsuccessfulReplanning = 0;
	

	@Inject 
	private ChargerPricingProfiles chargerPricingProfiles;
	
	@Inject
	AIAgentReplanningModule(Config config){
		this.config = config;
		
	}
	
	public static ChatCompletionClient getChatClient() {
		return new ChatCompletionClient.Builder()
		.setChatAPI_URL("https://api.openai.com/v1/chat/completions")//http://localhost:1234/v1/chat/completions
		.setEmbeddingAPI_URL("http://localhost:1234/v1/embeddings")
		.setModelName("gpt-4-turbo")
		.setauthorization(APIKeys.GPT_KEY)
		.setOrganization(APIKeys.ORGANIZATION_ID)
		.setProject(APIKeys.PROJECT_ID)
		.setTools(List.of(PlanSchema.getPlanGsonSchemaAsFunctionTool()))
		.setIfStream(false)
		.setMaxToken(4096)
		.setTemperature(.7)
		.setToolChoice("required")
		.build();
	}

	@Override
	public void prepareReplanning(ReplanningContext replanningContext) {
		try {
			String fileLoc = controler.getControlerIO().getIterationFilename(replanningContext.getIteration(),"ai_call.txt");
			this.fw =  new FileWriter(new File(fileLoc));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		plans = new HashSet<>();
		GsonBuilder gsonBuilder = new GsonBuilder()
				.registerTypeAdapter(PlanElementGson.class, new PlanElementGsonDeserializer());
		gsonBuilder.serializeNulls();
	    gsonBuilder.serializeSpecialFloatingPointValues();
	    gsonBuilder.setPrettyPrinting();
	    gson = gsonBuilder.create(); 
	    allowedMode.add(TransportMode.walk);
	    allowedMode.add(TransportMode.pt);
	    allowedMode.add(TransportMode.car);
	    allowedMode.add(TransportMode.bike);
	    this.plans.clear();
	    unsuccessfulReplanning = 0;
	    
	}
	public static Id<Vehicle> getVehicleId(Person person, String mode) {
		Map<String, Id<Vehicle>> vehicleIds = getVehicleIds(person);
		if (!vehicleIds.containsKey(mode)) {
			throw new RuntimeException("Could not retrieve vehicle id from person: " + person.getId().toString() + " for mode: " + mode +
					". \nIf you are not using config.qsim().getVehicleSource() with 'defaultVehicle' or 'modeVehicleTypesFromVehiclesData' you have to provide " +
					"a vehicle for each mode for each person. Attach a map of mode:String -> id:Id<Vehicle> with key 'vehicles' as person attribute to each person." +
					"\n VehicleUtils.insertVehicleIdIntoAttributes does this for you."
			);
		}
		return vehicleIds.get(mode);
	}
	
	public static Map<String, Id<Vehicle>> getVehicleIds(Person person) {
		PersonVehicles a=(PersonVehicles) person.getAttributes().getAttribute("vehicles");
		var vehicleIds = a.getModeVehicles();
		if (vehicleIds == null) {
			throw new RuntimeException("Could not retrieve vehicle id from person: " + person.getId().toString() +
					". \nIf you are not using config.qsim().getVehicleSource() with 'defaultVehicle' or 'modeVehicleTypesFromVehiclesData' you have to provide " +
					"a vehicle for each mode for each person. Attach a map of mode:String -> id:Id<Vehicle> with key 'vehicles' as person attribute to each person." +
					"\n VehicleUtils.insertVehicleIdIntoAttributes does this for you.");
		}
		return vehicleIds;
	}
	protected Set<Id<Vehicle>> getUsedEV(Plan plan) {
		return TripStructureUtils.getLegs(plan).stream().filter(leg->leg.getMode().equals("car")||leg.getMode().equals("car_passenger"))
				.map(leg -> getVehicleId(plan.getPerson(), leg.getMode()))
				.filter(vehicleId -> this.electricFleetSpecification.getVehicleSpecifications().containsKey(vehicleId))
				.collect(toSet());
		
		
		
		//		Set<Id<Vehicle>> vs = new HashSet<>();
		//		TripStructureUtils.getLegs(plan).stream().forEach(l->{
		//			if(l.getMode().equals("car")&&l.getMode().equals("car_passenger")) {
		//				Id<Vehicle> v = VehicleUtils.getVehicleId(plan.getPerson(), l.getMode());
		//				if(isEV(v))vs.add(v);
		//			}
		//		});
		//		return vs;
	}

	@Override
	public void handlePlan(Plan plan) {
		if(!this.getUsedEV(plan).isEmpty()) {
			this.plans.add(plan);
		}
	}

	@Override
	public void finishReplanning() {
		int ii = 0;
		for(Plan plan:plans) {
			ChatCompletionClient aiChatClient = getChatClient();
			makeChargingNotStaged(plan);
			Plan planOut = null;
			String userMsg = Prompt.promptStartAndEndActivityWithExample;
			PlanGson pgOut = null;
			String aiString = null;
			List<ErrorMessage> errors = new ArrayList<>();
			for(int i=0; i<maxTry;i++) {
				
				PlanGson pg = PlanGson.createPlanGson(plan);
				String gsonPlan = gson.toJson(pg);
				
				userMsg = userMsg+"json```\n"+gsonPlan+"\n```";
				if(i>0) {
					userMsg  ="";
					for(ErrorMessage e:errors){
						userMsg = userMsg+e.errorMessage;
					}
					;
				}
				System.out.println();
				aiString = aiChatClient.getResponse(Prompt.prompt_system_evAgent, userMsg).getToolCalls().get(0).getFunction().getArguments();
				try {
					fw.append(gsonPlan);
					fw.append(aiString);
					fw.flush();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				try {
					pgOut = gson.fromJson(aiString, PlanGson.class);
					if(pgOut==null) {
						errors.add(new ErrorMessage("Wrong gson syntax. Could not convert."));
					}
				}catch (Exception e) {
					// TODO Auto-generated catch block
					//System.out.println("Wrong gson syntax. Could not convert.");
					errors.add(new ErrorMessage("Wrong gson syntax. Could not convert."));
				}
				
				if(pgOut!=null)planOut = identifyChargingActivityAndInsertChargingLink(pgOut,plan,errors);
				if(planOut!=null)break;
			}
			if(planOut==null) {
				unsuccessfulReplanning++;
				System.out.println("Total unsuccessful replanning = "+ unsuccessfulReplanning);	
				makeChargingStaged(plan);
			}else {
				makeChargingStaged(planOut);
				PopulationUtils.copyFromTo(planOut, plan);
				plan.getAttributes().putAttribute("IfAiGenerated", true);
			}
			ii++;
			if(ii%10==0)System.out.println(ii+" plans finished out of "+this.plans.size());
		}
	}
	
	public static void main(String[] args) {
		String aiString = "{\"activitiesAndLegs\":[{\"id\":\"home___0\",\"activityType\":\"home\",\"endTime\":43200.0,\"carLocation\":\"home_home515682\"},{\"mode\":\"car\"},{\"id\":\"plugin___0\",\"activityType\":\"plugin\",\"endTime\":45000.0,\"carLocation\":\"charger\"},{\"mode\":\"walk\"},{\"id\":\"work___0\",\"activityType\":\"work\",\"endTime\":82800.0,\"carLocation\":\"charger\"},{\"mode\":\"walk\"},{\"id\":\"plugout___0\",\"activityType\":\"plugout\",\"endTime\":84600.0,\"carLocation\":\"charger\"},{\"mode\":\"car\"},{\"id\":\"home___1\",\"activityType\":\"home\",\"endTime\":97200.0,\"carLocation\":\"home_home515682\"}]}";
		GsonBuilder gsonBuilder = new GsonBuilder()
				.registerTypeAdapter(PlanElement.class, new PlanElementDeserializer());
		gsonBuilder.serializeNulls();
	    gsonBuilder.serializeSpecialFloatingPointValues();
	    gsonBuilder.setPrettyPrinting();
	    Gson gson = gsonBuilder.create(); 
	    PlanGson pgOut = gson.fromJson(aiString, PlanGson.class);
	    //planOut = identifyChargingActivityAndInsertChargingLink(pgOut,plan,errors);
	    
	}
	
	/**
	 * will return if the charging events were consistent and proper charger insertion was possible
	 * @param plan
	 * @return
	 * TODO: Order check, existence of all old activity check. 
	 */
	public Plan identifyChargingActivityAndInsertChargingLink(PlanGson planGson, Plan originalPlan, List<ErrorMessage> msgs) {
		
		
		
		
		//first put back all activity details. 
		
		Map<String,Integer> actOrder = new HashMap<>();
		Map<String,Activity> actsWithId = new HashMap<>();
		
		for(PlanElement pe: originalPlan.getPlanElements()) {
			if( pe instanceof Activity && !TripStructureUtils.isStageActivityType(((Activity)pe).getType())) {
				Activity a = ((Activity)pe);
				actOrder.compute(a.getType(),(k,v)->v==null?0:v+1);
				actsWithId.put(a.getType()+"___"+actOrder.get(a.getType()), a);
			}
		}
		int numEv = 0;
		for(PlanElementGson peG:planGson.activitiesAndLegs) {
			if(peG instanceof ActivityGson) {
				ActivityGson ag = (ActivityGson)peG;
				if(actsWithId.containsKey(ag.id)) {
					ag.facilityId = actsWithId.get(ag.id).getFacilityId().toString();
					ag.linkId = actsWithId.get(ag.id).getLinkId().toString();
					ag.coord = actsWithId.get(ag.id).getCoord();
				}else {
					if(ag.activityType.equals(PLUGIN) || ag.activityType.equals(PLUGOUT)) {
						numEv++;
						//set a dummy link id to create the activity. 
						ag.linkId = "atCharger";
					}else {
						msgs.add(new ErrorMessage(ag.id+" is not present in the original given plan. You cannot insert new activity other than plug in and plugout."));
						return null;
					}
					
				}
			}
		}
		
		if(numEv==0) {
			msgs.add(new ErrorMessage("No plugin or plugout charger in the given plan!!! Your task was to include plugin and plugout in the given plan"));
			return null;
		}
		
		Plan plan = planGson.getPlan();
		
		boolean ifCharging = false;
		Id<Link> linkIdForCharger = null;
		List<String> actsWhileCharging = new ArrayList<>();
		Map<String,Activity> oldActs = new HashMap<>();
		Map<String,Integer> actOccurance = new HashMap<>();
		ElectricVehicleSpecification electricVehicleSpecification = electricFleetSpecification.getVehicleSpecifications()
				.get(VehicleUtils.getVehicleId(originalPlan.getPerson(), TransportMode.car));
		
		for(PlanElement pe:originalPlan.getPlanElements()) {
			if(pe instanceof Activity && !TripStructureUtils.isStageActivityType(((Activity)pe).getType())) {
				actOccurance.compute(((Activity)pe).getType(), (k,v)->v==null?0:v+1);
				oldActs.put(((Activity)pe).getType()+"___"+actOccurance.get(((Activity)pe).getType()),((Activity)pe));
			}else if(pe instanceof Leg){
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
						return null;
					}
					Leg beforeLeg = null;
					if(i>0)beforeLeg = (Leg)plan.getPlanElements().get(i-1);
					
					if(!beforeLeg.getMode().equals(TransportMode.car)) {
						msgs.add(new ErrorMessage("The mode before plugin must be car. You need to bring car to plug it in!!!"));
						return null;
					}
					
					
					Activity nextAct = (Activity)plan.getPlanElements().get(i+2);
					if(nextAct.getType().equals(PLUGOUT)) {
						msgs.add(new ErrorMessage("Charging must be performed while performing other activities. Activities while charging list is empty!!!"));
						return null;
					}
					
					ChargerSpecification charger = this.selectChargerNearToLink(originalPlan.getPerson().getId(), nextAct.getLinkId(), 
							electricVehicleSpecification, this.scenario.getNetwork(), time);
					
					if(charger!=null) {
						act.setLinkId(charger.getLinkId());
					}else {
						msgs.add(new ErrorMessage("No charger found within 1000m of the following activity to the plugin activity. Choose a different activity."));
						return null;
					}
					linkIdForCharger = charger.getLinkId();
					ifCharging = true;
					
				}else if(((Activity)pe).getType().contains(PLUGOUT)) {
					if(!ifCharging) {
						msgs.add(new ErrorMessage("Nothing to plug out!!! There is no plugin event before this!!!"));
						return null;
					}
					
					Leg afterLeg = null;
					afterLeg = (Leg)plan.getPlanElements().get(i+1);
					
					if(!afterLeg.getMode().equals(TransportMode.car)) {
						msgs.add(new ErrorMessage("The mode after plugout must be car. You need to take your car from charger!!!"));
						return null;
					}
					
					if(actsWhileCharging.isEmpty()) {
						msgs.add(new ErrorMessage("Charging must be performed while performing other activities. Activities while charging list is empty!!!"));
						return null;
					}
					act.setLinkId(linkIdForCharger);
					ifCharging = false;
					actsWhileCharging.clear();
				}else {
					if(ifCharging) {
						actsWhileCharging.add(act.getType());
					}
				}
				
			}else {
				Leg leg = (Leg)pe;
				if(!this.allowedMode.contains(leg.getMode())) {
					msgs.add(new ErrorMessage("Unrecognized leg mode!!!Exiting!!!"));
					return null;
				}
			}
			
			i++;
		}
		return plan;
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
