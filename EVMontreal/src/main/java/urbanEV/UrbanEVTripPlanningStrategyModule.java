package urbanEV;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Provider;

import org.apache.log4j.Logger;
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
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.api.core.v01.replanning.PlanStrategyModule;
import org.matsim.contrib.common.util.StraightLineKnnFinder;
import org.matsim.contrib.ev.charging.ChargingLogic;
import org.matsim.contrib.ev.charging.ChargingPower;
import org.matsim.contrib.ev.discharging.AuxEnergyConsumption;
import org.matsim.contrib.ev.discharging.DriveEnergyConsumption;
import org.matsim.contrib.ev.fleet.ElectricFleetSpecification;
import org.matsim.contrib.ev.fleet.ElectricVehicle;
import org.matsim.contrib.ev.fleet.ElectricVehicleImpl;
import org.matsim.contrib.ev.fleet.ElectricVehicleSpecification;
import org.matsim.contrib.ev.infrastructure.ChargerSpecification;
import org.matsim.contrib.ev.infrastructure.ChargingInfrastructureSpecification;
import org.matsim.core.config.Config;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.replanning.ReplanningContext;
import org.matsim.core.router.LinkWrapperFacility;
import org.matsim.core.router.PlanRouter;
import org.matsim.core.router.SingleModeNetworksCache;
import org.matsim.core.router.StageActivityTypeIdentifier;
import org.matsim.core.router.TripRouter;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.utils.timing.TimeInterpretation;
import org.matsim.facilities.ActivityFacility;
import org.matsim.facilities.FacilitiesUtils;
import org.matsim.facilities.Facility;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleUtils;
import org.matsim.vehicles.Vehicles;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.sun.istack.Nullable;

import EVPricing.ChargerPricingProfiles;




public class UrbanEVTripPlanningStrategyModule implements PlanStrategyModule{
	public static final String urbanEVTripPlannerStrategyName = "evTripPlanner";
	public static final double cov = 1.2;
	public static final String ifBrokenActivity = "ifBroken";
	private Map<Id<Person>,List<Plan>> purePlan = new HashMap<>();
	public double factorOfSafety = 1.5;
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


	Config config;

	@Inject 
	private ChargerPricingProfiles chargerPricingProfiles;


	protected static final Logger log = Logger.getLogger(UrbanEVTripPlanningStrategyModule.class);
	protected static List<PersonContainer2> personContainer2s = new ArrayList<>();
	private boolean ifDumbCharing = true;
	private double dumbChargingStartTime = 0*3600;
	private double dumbChargingEndTime =  24*3600;
//	private double dumbChargingStartTime = this.config.qsim().getStartTime().seconds();
//	private double dumbChargingEndTime =  this.config.qsim().getEndTime().seconds();

	@Override
	public void prepareReplanning(ReplanningContext replanningContext) {
		plans = new HashSet<>();
	}
	@Inject
	UrbanEVTripPlanningStrategyModule(Config config){
		this.config = config;
		if(this.config.qsim().getEndTime().isDefined())this.dumbChargingEndTime = this.config.qsim().getEndTime().seconds();
		if(this.config.qsim().getStartTime().isDefined())this.dumbChargingStartTime = this.config.qsim().getStartTime().seconds();
	}

	Set<Plan> plans = new HashSet<>();

	/**
	 * retrieve all used EV in the given plan
	 *
	 * @param plan
	 * @return
	 */


	private boolean hasCharging(Plan plan) {

		for(PlanElement pe:plan.getPlanElements()) {
			if(pe instanceof Activity && ((Activity)pe).getType().contains(UrbanVehicleChargingHandler.PLUGIN_IDENTIFIER))return true;
		}
		return false;
	}

	private boolean haveSufficientCharging(Plan plan) {
		int chargeNum = 0;
		for(PlanElement pe:plan.getPlanElements()) {
			if(pe instanceof Activity && ((Activity)pe).getType().contains(UrbanVehicleChargingHandler.PLUGIN_IDENTIFIER))chargeNum++;
		}
		if(chargeNum>=((UrbanEVConfigGroup) config.getModules().get(UrbanEVConfigGroup.GROUP_NAME)).getMaximumChargingProceduresPerAgent())return true;
		return false;
	}

	protected Set<Id<Vehicle>> getUsedEV(Plan plan) {
		return TripStructureUtils.getLegs(plan).stream().filter(leg->leg.getMode().equals("car")||leg.getMode().equals("car_passenger"))
				.map(leg -> getVehicleId(plan.getPerson(), leg.getMode()))
				.filter(vehicleId -> isEV(vehicleId))
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
		Object a=person.getAttributes().getAttribute("vehicles");
		if(a instanceof Integer) {
			System.out.println("Error here!!!");
		}
		var vehicleIds = (Map<String, Id<Vehicle>>) a;
		if (vehicleIds == null) {
			throw new RuntimeException("Could not retrieve vehicle id from person: " + person.getId().toString() +
					". \nIf you are not using config.qsim().getVehicleSource() with 'defaultVehicle' or 'modeVehicleTypesFromVehiclesData' you have to provide " +
					"a vehicle for each mode for each person. Attach a map of mode:String -> id:Id<Vehicle> with key 'vehicles' as person attribute to each person." +
					"\n VehicleUtils.insertVehicleIdIntoAttributes does this for you.");
		}
		return vehicleIds;
	}

	protected boolean isEV(Id<Vehicle> vehicleId) {
		return this.electricFleetSpecification.getVehicleSpecifications().containsKey(getWrappedElectricVehicleId(vehicleId));
	}
	public Id<ElectricVehicle> getWrappedElectricVehicleId(Id<Vehicle> vehicleId){
		Id<ElectricVehicle> eId = Id.create(vehicleId.toString(),ElectricVehicle.class);
		return eId;
	}

	@Override
	public void handlePlan(Plan plan) {
		// TODO Auto-generated method stub
		Set<Id<Vehicle>> evs = getUsedEV(plan);
		if(!evs.isEmpty()) {
			//if(!haveSufficientCharging(plan)) {
			if(!this.purePlan.containsKey(plan.getPerson().getId())) {
				this.purePlan.put(plan.getPerson().getId(), new ArrayList<>());
				plan.getPerson().getAttributes().putAttribute("purePlan", plan);
			}
			boolean unique = true;
			for(Plan pl : this.purePlan.get(plan.getPerson().getId())) {
				if(this.planEquals(plan, pl)) {
					unique = false;
					break;
				}
			}

			if(unique==true) {
				Plan newPlan = PopulationUtils.createPlan();
				PopulationUtils.copyFromTo(plan, newPlan);
				newPlan.setPerson(plan.getPerson());
				this.purePlan.get(plan.getPerson().getId()).add(newPlan);
			}

			//			}else {
			//				Random random = MatsimRandom.getRandom();
			//				int ind = random.nextInt(this.purePlan.get(plan.getPerson().getId()).size());
			//				PopulationUtils.copyFromTo(this.purePlan.get(plan.getPerson().getId()).get(ind), plan);
			//			}
			this.plans.add(plan);
		}
	}

	private boolean planEquals(Plan plan1, Plan plan2) {
		if(plan1.getPlanElements().size()!=plan2.getPlanElements().size())return false;
		for(int i = 0;i<plan1.getPlanElements().size();i++) {
			PlanElement pe1 = plan1.getPlanElements().get(i);
			PlanElement pe2 = plan2.getPlanElements().get(i);
			if(pe1 instanceof Activity) {
				if(!((Activity)pe1).getType().equals(((Activity)pe2).getType()))
					return false;
			}else {
				if(!((Leg)pe1).getMode().equals(((Leg)pe2).getMode())){
					return false;
				}
			}
		}
		return true;
	}


	public List<Activity> logicExperienceBased(Plan modifiablePlan){
		List<PlanElement> planElements = modifiablePlan.getPlanElements();
		List <Leg> evCarLegs = TripStructureUtils.getLegs(modifiablePlan).stream().filter(leg -> leg.getMode().equals(TransportMode.car)).collect(toList());
		int size = planElements.size();
		int activityIndex = -1;
		List<Activity> pe = new ArrayList<>();

		// 2 hours in seconds
		//		for (PlanElement planElement : modifiablePlan.getPlanElements()) {
		//			if (planElement instanceof Activity) {
		//				Activity activity = (Activity) planElement;
		//				double typicalDuration = this.scenario.getConfig().planCalcScore().getActivityParams(activity.getType()).getTypicalDuration().seconds();
		//
		//				if (typicalDuration > thresholdDuration) {
		//					pe.add(activity);
		//				}
		//			}
		//		}     
		Set<Integer> peIndex = new HashSet<>();
		int in = 0;
		boolean hasCritical = false;


		if (size > 0) {
			int actOrder = 0;
			for(PlanElement pl:modifiablePlan.getPlanElements()) {
				if(pl instanceof Activity) {
					//if(((Map<Integer,Double>)modifiablePlan.getAttributes().getAttribute("actSOC")).get(actOrder)!=null) {
						Double soc = (Double)((Activity)pl).getAttributes().getAttribute("actSOC");
//						Double soc = ((Map<Integer,Double>)modifiablePlan.getAttributes().getAttribute("actSOC")).get(actOrder);
						if(soc !=null && soc>= 0.3) {
							peIndex.add(in);	
						}else {
							hasCritical = true;
							break;
						}
					//}
					in++;
					
				}
				actOrder++;
			}
			if(hasCritical==false)peIndex.clear();

			PlanElement firstElement = planElements.get(0);
			if (firstElement instanceof Activity) {
				Activity firstActivity = (Activity) planElements.get(0);

				Activity secondActivity = null;
				secondActivity = (Activity) planElements.get(2);
				Activity thirdActivity = null;
				if(size>4)thirdActivity = (Activity) planElements.get(4);
				boolean isSecondPlugin = secondActivity.getType().contains(UrbanVehicleChargingHandler.PLUGIN_IDENTIFIER);
				boolean isThirdNotSameAsFirst = isSecondPlugin && !thirdActivity.getFacilityId().equals(firstActivity.getFacilityId());

				boolean isFirstWithinTimeRange = firstActivity.getStartTime().isDefined() &&
						firstActivity.getStartTime().seconds() >= this.dumbChargingStartTime &&
						firstActivity.getStartTime().isDefined() &&
						firstActivity.getStartTime().seconds() <= this.dumbChargingEndTime;  //Do we really need to check the end of activity?
						if ((!isSecondPlugin || isThirdNotSameAsFirst) && isThirdNotSameAsFirst && isFirstWithinTimeRange && planElements.indexOf(evCarLegs.get(0))==1) {
							if (peIndex.contains(0)) {
								pe.add(firstActivity);
							}
						}
			}




			// Iterate over the plan elements to find the desired activities
			for (int i = 1; i < size - 1; i++) {
				PlanElement currentElement = planElements.get(i);



				if (currentElement instanceof Activity) {

					Activity currentActivity = (Activity) currentElement;



					// Check if the current activity is already surrounded by plugin and plugout activities
					boolean isSurroundedByPluginOrPlugout = false;



					//			        Activity prevActivity = ((Activity) currentActivity).get(i - 2);
					Activity prevActivity = (Activity) planElements.get(i - 2);
					//			        Activity nextActivity = ((Category) currentActivity).get(i + 2);
					Activity nextActivity = (Activity) planElements.get(i + 2);



					if (prevActivity != null && nextActivity != null &&
							(prevActivity.getType().contains(UrbanVehicleChargingHandler.PLUGIN_IDENTIFIER) ||
									//										prevActivity.getType().contains(UrbanVehicleChargingHandler.PLUGOUT_IDENTIFIER) ||
									//										nextActivity.getType().contains(UrbanVehicleChargingHandler.PLUGIN_IDENTIFIER) ||
									nextActivity.getType().contains(UrbanVehicleChargingHandler.PLUGOUT_IDENTIFIER))) {
						isSurroundedByPluginOrPlugout = true;
					}





					// Check if the current activity is not of type "PLUGIN_IDENTIFIER" or "PLUGOUT_IDENTIFIER"
					boolean isNotPluginOrPlugout = !currentActivity.getType().contains(UrbanVehicleChargingHandler.PLUGIN_IDENTIFIER) &&
							!currentActivity.getType().contains(UrbanVehicleChargingHandler.PLUGOUT_IDENTIFIER);



					// Check if the start and end times of the current activity are within the specified range
					boolean isWithinTimeRange = currentActivity.getStartTime().isDefined() &&
							currentActivity.getStartTime().seconds() >= this.dumbChargingStartTime &&
							currentActivity.getStartTime().isDefined() &&
							currentActivity.getStartTime().seconds() <= this.dumbChargingEndTime;



							// Add the current activity to the pe list if it meets all the criteria
							if (!isSurroundedByPluginOrPlugout && isNotPluginOrPlugout && isWithinTimeRange) {
								if (peIndex.contains(i)) {
									pe.add(currentActivity);
								}
							}
				}
			}



			// Check the last activity separately
			if (size > 1) {
				Activity lastActivity = (Activity)planElements.get(size - 1);
				Activity secondLastActivity = (Activity)planElements.get(size - 3);
				Activity thirdLastActivity = null;
				if(size>3)thirdLastActivity = (Activity)planElements.get(size - 5);

				boolean isSecondLastPlugout = secondLastActivity.getType().contains(UrbanVehicleChargingHandler.PLUGOUT_IDENTIFIER);
				boolean isLastWithinTimeRange = lastActivity.getStartTime().isDefined() &&
						lastActivity.getStartTime().seconds() >= this.dumbChargingStartTime &&
						lastActivity.getStartTime().isDefined() &&
						lastActivity.getStartTime().seconds() <= this.dumbChargingEndTime;   
						boolean isThirdLastNotSameAsLast = isSecondLastPlugout && !thirdLastActivity.getFacilityId().equals(lastActivity.getFacilityId());
						if ((!isSecondLastPlugout || isThirdLastNotSameAsLast) && isLastWithinTimeRange && ((Leg) planElements.get(size-2)).getMode().equals(TransportMode.car)) {
							if (peIndex.contains(size-1)) {
								pe.add(lastActivity);
							}
						}

			}
		}

		return pe;

	}
	
	public void cleanActSOC(Plan plan) {
		plan.getPlanElements().forEach(pl->{
			if(pl instanceof Activity && ((Activity)pl).getAttributes().getAttribute("actSOC")!=null) ((Activity)pl).getAttributes().removeAttribute("actSOC");
			
		});
		plan.getAttributes().removeAttribute("actSOC");
	}

	public List<Activity> logicActivityDuration(Plan modifiablePlan){
		List<PlanElement> planElements = modifiablePlan.getPlanElements();
		List <Leg> evCarLegs = TripStructureUtils.getLegs(modifiablePlan).stream().filter(leg -> leg.getMode().equals(TransportMode.car)).collect(toList());
		int size = planElements.size();
		int activityIndex = -1;
		List<Activity> pe = new ArrayList<>();

		// 2 hours in seconds
		//		for (PlanElement planElement : modifiablePlan.getPlanElements()) {
		//			if (planElement instanceof Activity) {
		//				Activity activity = (Activity) planElement;
		//				double typicalDuration = this.scenario.getConfig().planCalcScore().getActivityParams(activity.getType()).getTypicalDuration().seconds();
		//
		//				if (typicalDuration > thresholdDuration) {
		//					pe.add(activity);
		//				}
		//			}
		//		}     


		if (size > 0) {


			PlanElement firstElement = planElements.get(0);
			if (firstElement instanceof Activity) {
				Activity firstActivity = (Activity) planElements.get(0);

				Activity secondActivity = null;
				secondActivity = (Activity) planElements.get(2);
				Activity thirdActivity = null;
				if(size>4)thirdActivity = (Activity) planElements.get(4);
				boolean isSecondPlugin = secondActivity.getType().contains(UrbanVehicleChargingHandler.PLUGIN_IDENTIFIER);
				boolean isThirdNotSameAsFirst = isSecondPlugin && !thirdActivity.getFacilityId().equals(firstActivity.getFacilityId());

				boolean isFirstWithinTimeRange = firstActivity.getStartTime().isDefined() &&
						firstActivity.getStartTime().seconds() >= this.dumbChargingStartTime &&
						firstActivity.getStartTime().isDefined() &&
						firstActivity.getStartTime().seconds() <= this.dumbChargingEndTime;  //Do we really need to check the end of activity?
						if ((!isSecondPlugin || isThirdNotSameAsFirst) && isThirdNotSameAsFirst && isFirstWithinTimeRange && planElements.indexOf(evCarLegs.get(0))==1) {
							double typicalDuration = this.scenario.getConfig().planCalcScore().getActivityParams(firstActivity.getType()).getTypicalDuration().seconds();

							if (typicalDuration > thresholdDuration) {
								pe.add(firstActivity);
							}
						}
			}




			// Iterate over the plan elements to find the desired activities
			for (int i = 1; i < size - 1; i++) {
				PlanElement currentElement = planElements.get(i);



				if (currentElement instanceof Activity) {

					Activity currentActivity = (Activity) currentElement;



					// Check if the current activity is already surrounded by plugin and plugout activities
					boolean isSurroundedByPluginOrPlugout = false;



					//			        Activity prevActivity = ((Activity) currentActivity).get(i - 2);
					Activity prevActivity = (Activity) planElements.get(i - 2);
					//			        Activity nextActivity = ((Category) currentActivity).get(i + 2);
					Activity nextActivity = (Activity) planElements.get(i + 2);



					if (prevActivity != null && nextActivity != null &&
							(prevActivity.getType().contains(UrbanVehicleChargingHandler.PLUGIN_IDENTIFIER) ||
									//										prevActivity.getType().contains(UrbanVehicleChargingHandler.PLUGOUT_IDENTIFIER) ||
									//										nextActivity.getType().contains(UrbanVehicleChargingHandler.PLUGIN_IDENTIFIER) ||
									nextActivity.getType().contains(UrbanVehicleChargingHandler.PLUGOUT_IDENTIFIER))) {
						isSurroundedByPluginOrPlugout = true;
					}





					// Check if the current activity is not of type "PLUGIN_IDENTIFIER" or "PLUGOUT_IDENTIFIER"
					boolean isNotPluginOrPlugout = !currentActivity.getType().contains(UrbanVehicleChargingHandler.PLUGIN_IDENTIFIER) &&
							!currentActivity.getType().contains(UrbanVehicleChargingHandler.PLUGOUT_IDENTIFIER);



					// Check if the start and end times of the current activity are within the specified range
					boolean isWithinTimeRange = currentActivity.getStartTime().isDefined() &&
							currentActivity.getStartTime().seconds() >= this.dumbChargingStartTime &&
							currentActivity.getStartTime().isDefined() &&
							currentActivity.getStartTime().seconds() <= this.dumbChargingEndTime;



							// Add the current activity to the pe list if it meets all the criteria
							if (!isSurroundedByPluginOrPlugout && isNotPluginOrPlugout && isWithinTimeRange) {
								double typicalDuration = this.scenario.getConfig().planCalcScore().getActivityParams(currentActivity.getType()).getTypicalDuration().seconds();

								if (typicalDuration > thresholdDuration) {
									pe.add(currentActivity);
								}
							}
				}
			}



			// Check the last activity separately
			if (size > 1) {
				Activity lastActivity = (Activity)planElements.get(size - 1);
				Activity secondLastActivity = (Activity)planElements.get(size - 3);
				Activity thirdLastActivity = null;
				if(size>3)thirdLastActivity = (Activity)planElements.get(size - 5);

				boolean isSecondLastPlugout = secondLastActivity.getType().contains(UrbanVehicleChargingHandler.PLUGOUT_IDENTIFIER);
				boolean isLastWithinTimeRange = lastActivity.getStartTime().isDefined() &&
						lastActivity.getStartTime().seconds() >= this.dumbChargingStartTime &&
						lastActivity.getStartTime().isDefined() &&
						lastActivity.getStartTime().seconds() <= this.dumbChargingEndTime;   
						boolean isThirdLastNotSameAsLast = isSecondLastPlugout && !thirdLastActivity.getFacilityId().equals(lastActivity.getFacilityId());
						if ((!isSecondLastPlugout || isThirdLastNotSameAsLast) && isLastWithinTimeRange && ((Leg) planElements.get(size-2)).getMode().equals(TransportMode.car)) {
							double typicalDuration = this.scenario.getConfig().planCalcScore().getActivityParams(lastActivity.getType()).getTypicalDuration().seconds();

							if (typicalDuration > thresholdDuration) {
								pe.add(lastActivity);
							}
						}

			}
		}
		return pe;
	}


	public List<Activity> logicOptimized(Plan modifiablePlan){
		List<PlanElement> planElements = modifiablePlan.getPlanElements();
		List <Leg> evCarLegs = TripStructureUtils.getLegs(modifiablePlan).stream().filter(leg -> leg.getMode().equals(TransportMode.car)).collect(toList());
		int size = planElements.size();
		int activityIndex = -1;
		List<Activity> pe = new ArrayList<>();

		// 2 hours in seconds
		//		for (PlanElement planElement : modifiablePlan.getPlanElements()) {
		//			if (planElement instanceof Activity) {
		//				Activity activity = (Activity) planElement;
		//				double typicalDuration = this.scenario.getConfig().planCalcScore().getActivityParams(activity.getType()).getTypicalDuration().seconds();
		//
		//				if (typicalDuration > thresholdDuration) {
		//					pe.add(activity);
		//				}
		//			}
		//		}     


		if (size > 0) {


			PlanElement firstElement = planElements.get(0);
			if (firstElement instanceof Activity) {
				Activity firstActivity = (Activity) planElements.get(0);

				Activity secondActivity = null;
				secondActivity = (Activity) planElements.get(2);
				Activity thirdActivity = null;
				if(size>4)thirdActivity = (Activity) planElements.get(4);
				boolean isSecondPlugin = secondActivity.getType().contains(UrbanVehicleChargingHandler.PLUGIN_IDENTIFIER);
				boolean isThirdNotSameAsFirst = isSecondPlugin && !thirdActivity.getFacilityId().equals(firstActivity.getFacilityId());

				boolean isFirstWithinTimeRange = firstActivity.getStartTime().isDefined() &&
						firstActivity.getStartTime().seconds() >= this.dumbChargingStartTime;
//						firstActivity.getStartTime().isDefined() &&
//						firstActivity.getStartTime().seconds() <= this.dumbChargingEndTime;  //Do we really need to check the end of activity?
						if ((!isSecondPlugin || isThirdNotSameAsFirst) && isThirdNotSameAsFirst && isFirstWithinTimeRange && planElements.indexOf(evCarLegs.get(0))==1) {

							pe.add(firstActivity);

						}
			}




			// Iterate over the plan elements to find the desired activities
			for (int i = 1; i < size - 1; i++) {
				PlanElement currentElement = planElements.get(i);



				if (currentElement instanceof Activity) {

					Activity currentActivity = (Activity) currentElement;



					// Check if the current activity is already surrounded by plugin and plugout activities
					boolean isSurroundedByPluginOrPlugout = false;



					//			        Activity prevActivity = ((Activity) currentActivity).get(i - 2);
					Activity prevActivity = (Activity) planElements.get(i - 2);
					//			        Activity nextActivity = ((Category) currentActivity).get(i + 2);
					Activity nextActivity = (Activity) planElements.get(i + 2);



					if (prevActivity != null && nextActivity != null &&
							(prevActivity.getType().contains(UrbanVehicleChargingHandler.PLUGIN_IDENTIFIER) ||
									//										prevActivity.getType().contains(UrbanVehicleChargingHandler.PLUGOUT_IDENTIFIER) ||
									//										nextActivity.getType().contains(UrbanVehicleChargingHandler.PLUGIN_IDENTIFIER) ||
									nextActivity.getType().contains(UrbanVehicleChargingHandler.PLUGOUT_IDENTIFIER))) {
						isSurroundedByPluginOrPlugout = true;
					}





					// Check if the current activity is not of type "PLUGIN_IDENTIFIER" or "PLUGOUT_IDENTIFIER"
					boolean isNotPluginOrPlugout = !currentActivity.getType().contains(UrbanVehicleChargingHandler.PLUGIN_IDENTIFIER) &&
							!currentActivity.getType().contains(UrbanVehicleChargingHandler.PLUGOUT_IDENTIFIER);



					// Check if the start and end times of the current activity are within the specified range
					boolean isWithinTimeRange = currentActivity.getStartTime().isDefined() &&
							currentActivity.getStartTime().seconds() >= this.dumbChargingStartTime;
	//						currentActivity.getStartTime().isDefined() &&
//							currentActivity.getStartTime().seconds() <= this.dumbChargingEndTime;



							// Add the current activity to the pe list if it meets all the criteria
							if (!isSurroundedByPluginOrPlugout && isNotPluginOrPlugout && isWithinTimeRange) {

								pe.add(currentActivity);

							}
				}
			}



			// Check the last activity separately
			if (size > 1) {
				Activity lastActivity = (Activity)planElements.get(size - 1);
				Activity secondLastActivity = (Activity)planElements.get(size - 3);
				Activity thirdLastActivity = null;
				if(size>3)thirdLastActivity = (Activity)planElements.get(size - 5);

				boolean isSecondLastPlugout = secondLastActivity.getType().contains(UrbanVehicleChargingHandler.PLUGOUT_IDENTIFIER);
				boolean isLastWithinTimeRange = lastActivity.getStartTime().isDefined() &&
						lastActivity.getStartTime().seconds() >= this.dumbChargingStartTime;
	//					lastActivity.getStartTime().isDefined() &&
//						lastActivity.getStartTime().seconds() <= this.dumbChargingEndTime;   
						boolean isThirdLastNotSameAsLast = isSecondLastPlugout && !thirdLastActivity.getFacilityId().equals(lastActivity.getFacilityId());
						if ((!isSecondLastPlugout || isThirdLastNotSameAsLast) && isLastWithinTimeRange && ((Leg) planElements.get(size-2)).getMode().equals(TransportMode.car)) {

							pe.add(lastActivity);

						}

			}
		}
		return pe;
	}

	private void execute(Plan plan) {
		Set<Id<Vehicle>> evs = getUsedEV(plan);
		Plan pl = PopulationUtils.createPlan();
		PopulationUtils.copyFromTo(plan, pl);
		pl.setPerson(plan.getPerson());
		Plan modifiablePlan = plan;
		//System.out.println();
		TripRouter tripRouter = tripRouterProvider.get();
		Set<String> modesWithVehicles = new HashSet<>(scenario.getConfig().qsim().getMainModes());
		modesWithVehicles.addAll(scenario.getConfig().plansCalcRoute().getNetworkModes());
		UrbanEVConfigGroup configGroup = (UrbanEVConfigGroup) config.getModules().get(UrbanEVConfigGroup.GROUP_NAME);
		for (Id<Vehicle> ev : evs) {
			int cnt = configGroup.getMaximumChargingProceduresPerAgent();
			Id<Vehicle> evId = ev;
			ElectricVehicleSpecification electricVehicleSpecification = electricFleetSpecification.getVehicleSpecifications()
					.get(ev);
			Leg legWithCriticalSOC;
			ElectricVehicle pseudoVehicle = ElectricVehicleImpl.create(electricVehicleSpecification, driveConsumptionFactory, auxConsumptionFactory, chargingPowerFactory);
			
		}

		//	for(Id<Vehicle> ev: pl.getValue()) {
		for (Id<Vehicle> ev : evs) {
			//only replan cnt times per vehicle and person. otherwise, there might be a leg which is just too long and we end up in an infinity loop...
			
			//			boolean pluginAtHomeBeforeMobSim = configGroup.getPluginAtHomeBeforeMobSim;
			/*
			 * i had all of this implemented without so many if-statements and without do-while-loop. However, i felt like when replanning takes place, we need to start
			 * consumption estimation all over. The path to avoid this would be by complicated date/method structure, which would also be bad (especially to maintain...)
			 * ts, nov' 27, 2020
			 */
			
			
			//			List <Leg> evCarLegs = new ArrayList<>();
			//			TripStructureUtils.getLegs(modifiablePlan).stream().forEach(l->{
			//				if(l.getMode().equals(TransportMode.car))evCarLegs.add(l);
			//only replan cnt times per vehicle and person. otherwise, there might be a leg which is just too long and we end up in an infinity loop...
			int cnt = configGroup.getMaximumChargingProceduresPerAgent();
			//			boolean pluginAtHomeBeforeMobSim = configGroup.getPluginAtHomeBeforeMobSim;
			/*
			 * i had all of this implemented without so many if-statements and without do-while-loop. However, i felt like when replanning takes place, we need to start
			 * consumption estimation all over. The path to avoid this would be by complicated date/method structure, which would also be bad (especially to maintain...)
			 * ts, nov' 27, 2020
			 */
			ElectricVehicleSpecification electricVehicleSpecification = electricFleetSpecification.getVehicleSpecifications()
					.get(getWrappedElectricVehicleId(ev));
			Leg legWithCriticalSOC;
			ElectricVehicle pseudoVehicle = ElectricVehicleImpl.create(electricVehicleSpecification, driveConsumptionFactory, auxConsumptionFactory, chargingPowerFactory);;

			List <Leg> evCarLegs = TripStructureUtils.getLegs(modifiablePlan).stream().filter(leg -> leg.getMode().equals(TransportMode.car)).collect(toList());
			legWithCriticalSOC = getCriticalOrLastEvLeg(modifiablePlan, pseudoVehicle, ev);
			String mode = legWithCriticalSOC.getMode();
			List <Leg> evLegs = TripStructureUtils.getLegs(modifiablePlan).stream().filter(leg -> leg.getMode().equals(mode)).collect(toList());

			//			do {
			//			if(this.ifDumbCharing) {
			//			
			//			Leg firstEvLeg = evCarLegs.get(0);
			//			Activity actWhileCharging = (Activity) modifiablePlan.getPlanElements().get(0);
			//			int indFirstEvLeg=  modifiablePlan.getPlanElements().indexOf(firstEvLeg);
			//			actWhileCharging =  EditPlansReplan.findRealActBefore(plan,indFirstEvLeg);
			//

			double random = Math.random();
			String logicSwitch = null;
			if(random<=0.33) {
				logicSwitch = "activityDuration";
			}else if(random<=.67) {
				logicSwitch = "experienceBased";
			}else {
				logicSwitch = "optimized";
			}
			
//			logicSwitch= "activityDuration";
			modifiablePlan.getAttributes().putAttribute("logicSwitch", logicSwitch);
			List<Activity> pe;
			switch(logicSwitch){
			case "activityDuration":
				pe = this.logicActivityDuration(modifiablePlan);
				break;
			case "experienceBased":
				pe = this.logicExperienceBased(modifiablePlan);
				break;
			case "optimized":
				pe = this.logicOptimized(modifiablePlan);
				break;
			default:
				pe = this.logicOptimized(modifiablePlan);
				break;

			}



			if (pe.size()>0) {
				Activity act = pe.get(MatsimRandom.getRandom().nextInt(pe.size()));
				if(modifiablePlan.getPlanElements().indexOf(act)==0) {	
					//		Leg firstEvLeg = evCarLegs.get(0);
					//				Activity originalActWhileCharging = EditPlans.findRealActBefore(mobsimagent, modifiablePlan.getPlanElements().indexOf(firstEvLeg));//
					//				Activity lastAct = EditPlans.findRealActBefore(mobsimagent, modifiablePlan.getPlanElements().indexOf(firstEvLeg));
					//		Activity act = (Activity) modifiablePlan.getPlanElements().get(0); ****** maybe this is not necessary
					//		int indFirstEvLeg=  modifiablePlan.getPlanElements().indexOf(firstEvLeg);
					//		actWhileCharging =  EditPlansReplan.findRealActBefore(plan,indFirstEvLeg);// comment this line out TODO: figure out this line 


					//		if(((Activity)modifiablePlan.getPlanElements().get(indFirstEvLeg+1)).getType().contains(UrbanVehicleChargingHandler.PLUGIN_IDENTIFIER)
					//				&& ifSameAct((Activity)modifiablePlan.getPlanElements().get(indFirstEvLeg+3),actWhileCharging)){// Already replanned before 
					//			log.info("Already have a home charging trip. Skipping.");
					//			



					//		} else {
					Leg leg = ((Leg) modifiablePlan.getPlanElements().get(1));
					Network modeNetwork = this.singleModeNetworksCache.getSingleModeNetworksCache().get(leg.getMode());
					ChargerSpecification charger = selectChargerNearToLink(modifiablePlan.getPerson().getId(), act.getLinkId(), electricVehicleSpecification, modeNetwork, act.getStartTime().seconds());
					//			Link chargingLink = modeNetwork.getLinks().get(actWhileCharging.getLinkId());
					Link chargingLink = modeNetwork.getLinks().get(charger.getLinkId());
					if(charger!= null) {
						String routingMode = TripStructureUtils.getRoutingMode(leg);
						planPluginTripFromHomeToCharger(modifiablePlan, routingMode, electricVehicleSpecification, act, chargingLink, tripRouter);
						//			Leg plugoutLeg = leg;
						Activity plugoutTripOrigin = findRealOrChargingActBefore(plan, modifiablePlan.getPlanElements().indexOf(leg));
						Activity plugoutTripDestination = findRealOrChargingActAfter(plan, modifiablePlan.getPlanElements().indexOf(leg));
						planPlugoutTrip(modifiablePlan, routingMode, electricVehicleSpecification, plugoutTripOrigin, plugoutTripDestination, chargingLink, tripRouter, time.decideOnActivityEndTimeAlongPlan(plugoutTripOrigin, modifiablePlan).seconds());
						//TODO don't we need to trigger SoC emulation here (in order to account for the energy charged before home actvity is ended?) see also todo-comment below
					}
					//		}
				} else if(modifiablePlan.getPlanElements().indexOf(act) == (modifiablePlan.getPlanElements().size()-1)) {

					Activity actBefore = findRealOrChargingActBefore(plan, modifiablePlan.getPlanElements().size()-2);
					//		Activity lastAct = (Activity) modifiablePlan.getPlanElements().get(modifiablePlan.getPlanElements().size()-1);
					Leg leg = (Leg) modifiablePlan.getPlanElements().get(modifiablePlan.getPlanElements().size()-2);
					//				EditPlansReplan.findRealActAfter(plan, modifiablePlan.getPlanElements().indexOf(legWithCriticalSOC));
					//		if(!((Activity)modifiablePlan.getPlanElements().get(modifiablePlan.getPlanElements().indexOf(legWithCriticalSOC)+1)).getType().contains(UrbanVehicleChargingHandler.PLUGIN_IDENTIFIER) &&
					//				pseudoVehicle.getBattery().getSoc()/pseudoVehicle.getBattery().getCapacity()<.999) {
					Network modeNetwork = this.singleModeNetworksCache.getSingleModeNetworksCache().get(leg.getMode());
					//			Link chargingLink = modeNetwork.getLinks().get(act.getLinkId());
					ChargerSpecification charger = selectChargerNearToLink(modifiablePlan.getPerson().getId(), act.getLinkId(), electricVehicleSpecification, modeNetwork, act.getStartTime().seconds());
					if(charger!= null) {
						Link chargingLink = modeNetwork.getLinks().get(charger.getLinkId());
						String routingMode = TripStructureUtils.getRoutingMode(leg);
						planPluginTrip(modifiablePlan, routingMode, electricVehicleSpecification, actBefore, act, chargingLink, tripRouter);
						Activity actEnd = PopulationUtils.createActivity(act);
						act.setEndTime(config.qsim().getEndTime().seconds()-600);
						modifiablePlan.addActivity(actEnd);
						this.planPlugoutTrip(modifiablePlan, routingMode, electricVehicleSpecification, act, actEnd, chargingLink, tripRouter, config.qsim().getEndTime().seconds()-600);
						log.info(plan.getPerson().getId() + " is charging at home.");
						PersonContainer2 personContainer2 = new PersonContainer2(plan.getPerson().getId(), "is charging at home.");
						personContainer2s.add(personContainer2);
					}



				} else {


					Activity pluginTripOrigin = findRealOrChargingActBefore(modifiablePlan, modifiablePlan.getPlanElements().indexOf(act));
					Activity plugoutTripDestination = findRealOrChargingActAfter(modifiablePlan, modifiablePlan.getPlanElements().indexOf(act));
					String routingMode = TripStructureUtils.getRoutingMode(evLegs.get(0)); //can be an error that the leg before the activity is not an ev leg
					Network modeNetwork = this.singleModeNetworksCache.getSingleModeNetworksCache().get(evLegs.get(0).getMode());
					ChargerSpecification selectedCharger = selectChargerNearToLink(modifiablePlan.getPerson().getId(), act.getLinkId(), electricVehicleSpecification, modeNetwork, act.getStartTime().seconds());
					if(selectedCharger != null) {
						Link chargingLink = modeNetwork.getLinks().get(selectedCharger.getLinkId());
						planPluginTrip(modifiablePlan, routingMode, electricVehicleSpecification, pluginTripOrigin, act, chargingLink, tripRouter);
						planPlugoutTrip(modifiablePlan, routingMode, electricVehicleSpecification, act, plugoutTripDestination, chargingLink, tripRouter, time.decideOnActivityEndTimeAlongPlan(act, modifiablePlan).seconds());
						//cnt --;


					}
				}
			}

		}

		//         }
		
		cleanActSOC(modifiablePlan);
	}


	// --------------------------------------------------------------------------------------------------------------------------------------------------------


	//			int i = 0;
	//			List<PlanElement> al = new ArrayList<>(modifiablePlan.getPlanElements());
	//			Random random = MatsimRandom.getRandom();
	//			for (PlanElement pel: new ArrayList <> (al.get(rand.nextInt(al.size()))) {
	//				if (pel instanceof Activity && ((Activity) pel).getStartTime().isDefined() && !pel.equals(modifiablePlan.getPlanElements().get(0)) && !pel.equals(modifiablePlan.getPlanElements().get(modifiablePlan.getPlanElements().size()-1)) && ((Activity) pel).getStartTime().seconds() >= this.dumbChargingStartTime && ((Activity) pel).getStartTime().seconds() <= this.dumbChargingEndTime) {
	//					if (!((Activity) pel).getType().contains(UrbanVehicleChargingHandler.PLUGOUT_IDENTIFIER) && !((Activity) pel).getType().contains(UrbanVehicleChargingHandler.PLUGIN_IDENTIFIER)) {
	//						if(i>1 && !((Activity) modifiablePlan.getPlanElements().get(i-2)).getType().contains(UrbanVehicleChargingHandler.PLUGIN_IDENTIFIER)) {
	//							Random rand = new Random();
	//					        PlanElement pel = (PlanElement) ((List<Plan>) pel).get(rand.nextInt(((List<Plan>) pel).size()));
	//							Activity pluginTripOrigin = findRealOrChargingActBefore(modifiablePlan, modifiablePlan.getPlanElements().indexOf((Activity) pel));
	//							Activity plugoutTripDestination = findRealOrChargingActAfter(modifiablePlan, modifiablePlan.getPlanElements().indexOf((Activity) pel));
	//							String routingMode = TripStructureUtils.getRoutingMode(evLegs.get(0)); //can be an error that the leg before the activity is not an ev leg
	//							Network modeNetwork = this.singleModeNetworksCache.getSingleModeNetworksCache().get(evLegs.get(0).getMode());
	//							ChargerSpecification selectedCharger = selectChargerNearToLink(modifiablePlan.getPerson().getId(),((Activity) pel).getLinkId(), electricVehicleSpecification, modeNetwork, ((Activity) pel).getStartTime().seconds());
	//							if(selectedCharger != null) {
	//							Link chargingLink = modeNetwork.getLinks().get(selectedCharger.getLinkId());
	//							planPluginTrip(modifiablePlan, routingMode, electricVehicleSpecification, pluginTripOrigin, (Activity) pel, chargingLink, tripRouter);
	//							planPlugoutTrip(modifiablePlan, routingMode, electricVehicleSpecification, (Activity) pel, plugoutTripDestination, chargingLink, tripRouter, PlanRouter.calcEndOfActivity((Activity) pel, modifiablePlan, config));
	//							cnt --;
	//							break;
	//							
	//								}
	//							}
	//					}
	//				
	//				}
	//				i++;
	//			}


	//			boolean pluginBeforeStart = configGroup.getPluginBeforeStartingThePlan();
	//			if(pluginBeforeStart && pseudoVehicle.getBattery().getSoc()/pseudoVehicle.getBattery().getCapacity() <.30 && hasHomeCharger(plan.getPerson(), modifiablePlan, evCarLegs, pseudoVehicle) 
	//					&& modifiablePlan.getPlanElements().indexOf(evCarLegs.get(0))==1){ //TODO potentially check for activity duration and/or SoC
	//
	//				Leg firstEvLeg = evCarLegs.get(0);
	//				//				Activity originalActWhileCharging = EditPlans.findRealActBefore(mobsimagent, modifiablePlan.getPlanElements().indexOf(firstEvLeg));//
	//				//				Activity lastAct = EditPlans.findRealActBefore(mobsimagent, modifiablePlan.getPlanElements().indexOf(firstEvLeg));
	//				Activity actWhileCharging = (Activity) modifiablePlan.getPlanElements().get(0);
	//				int indFirstEvLeg=  modifiablePlan.getPlanElements().indexOf(firstEvLeg);
	//				actWhileCharging =  EditPlansReplan.findRealActBefore(plan,indFirstEvLeg);// comment this line out TODO: figure out this line 
	//
	//				
	//				if(((Activity)modifiablePlan.getPlanElements().get(indFirstEvLeg+1)).getType().contains(UrbanVehicleChargingHandler.PLUGIN_IDENTIFIER)
	//						&& ifSameAct((Activity)modifiablePlan.getPlanElements().get(indFirstEvLeg+3),actWhileCharging)){// Already replanned before 
	//							log.info("Already have a home charging trip. Skipping.");				
	//										
	//							
	//				
	//				} else {
	//					Network modeNetwork = this.singleModeNetworksCache.getSingleModeNetworksCache().get(firstEvLeg.getMode());
	//					Link chargingLink = modeNetwork.getLinks().get(actWhileCharging.getLinkId());
	//					String routingMode = TripStructureUtils.getRoutingMode(firstEvLeg);
	//					planPluginTripFromHomeToCharger(modifiablePlan, routingMode, electricVehicleSpecification, actWhileCharging, chargingLink, tripRouter);
	//					Leg plugoutLeg = firstEvLeg;
	//					Activity plugoutTripOrigin = findRealOrChargingActBefore(plan, modifiablePlan.getPlanElements().indexOf(plugoutLeg));
	//					Activity plugoutTripDestination = findRealOrChargingActAfter(plan, modifiablePlan.getPlanElements().indexOf(plugoutLeg));
	//					planPlugoutTrip(modifiablePlan, routingMode, electricVehicleSpecification, plugoutTripOrigin, plugoutTripDestination, chargingLink, tripRouter, PlanRouter.calcEndOfActivity(plugoutTripOrigin, modifiablePlan, config));
	//				//TODO don't we need to trigger SoC emulation here (in order to account for the energy charged before home actvity is ended?) see also todo-comment below
	//					
	//				}
	//			}


	//			do {
	//				//double newSoC = EVUtils.getInitialEnergy(vehicles.getVehicles().get(ev).getType().getEngineInformation())* EvUnits.J_PER_kWh; //TODO is this correct if vehicle was plugged in before start (sse above) ?
	//				//pseudoVehicle.getBattery().setSoc(newSoC);
	//				double capacityThreshold = pseudoVehicle.getBattery().getCapacity() * (configGroup.getCriticalRelativeSOC());
	//				legWithCriticalSOC = getCriticalOrLastEvLeg(modifiablePlan, pseudoVehicle, ev);
	////				String mode = legWithCriticalSOC.getMode();
	////				List <Leg> evLegs = TripStructureUtils.getLegs(modifiablePlan).stream().filter(leg -> leg.getMode().equals(mode)).collect(toList());
	//				//				List <Leg> evLegs = new ArrayList<>();
	//				//				TripStructureUtils.getLegs(modifiablePlan).stream().forEach(l->{
	//				//					if(l.getMode().equals(mode))evLegs.add(l);
	//				//				});
	//				if (legWithCriticalSOC != null) {
	//
	//					if (evLegs.get(0).equals(legWithCriticalSOC)) {
	//						log.warn("SoC of Agent" + plan.getPerson().getId() + "is running beyond capacity threshold during the first leg of the day.");
	//						PersonContainer2 personContainer2 = new PersonContainer2(plan.getPerson().getId(), "is running beyond capacity threshold during the first leg of the day.");
	//						personContainer2s.add(personContainer2);
	//						break;
	//					}
	//
	//					
	//					
	//					else if (evLegs.get(evLegs.size()-1).equals(legWithCriticalSOC) && isHomeChargingTrip(modifiablePlan, evLegs, pseudoVehicle) && pseudoVehicle.getBattery().getSoc() > 0 && 
	//							!((Activity)modifiablePlan.getPlanElements().get(modifiablePlan.getPlanElements().indexOf(legWithCriticalSOC)-1)).getType().contains(UrbanVehicleChargingHandler.PLUGOUT_IDENTIFIER)) {
	//
	//						//trip leads to location of the first activity in the plan and there is a charger and so we can charge at home do not search for opportunity charge before
	//						Activity actBefore = EditPlansReplan.findRealActBefore(plan, modifiablePlan.getPlanElements().indexOf(legWithCriticalSOC));
	//						Activity lastAct = EditPlansReplan.findRealActAfter(plan, modifiablePlan.getPlanElements().indexOf(legWithCriticalSOC));
	//						if(!((Activity)modifiablePlan.getPlanElements().get(modifiablePlan.getPlanElements().indexOf(legWithCriticalSOC)+1)).getType().contains(UrbanVehicleChargingHandler.PLUGIN_IDENTIFIER) &&
	//								pseudoVehicle.getBattery().getSoc()/pseudoVehicle.getBattery().getCapacity()<.999) {
	//							Network modeNetwork = this.singleModeNetworksCache.getSingleModeNetworksCache().get(legWithCriticalSOC.getMode());
	//							Link chargingLink = modeNetwork.getLinks().get(lastAct.getLinkId());
	//							String routingMode = TripStructureUtils.getRoutingMode(legWithCriticalSOC);
	//
	//							planPluginTrip(modifiablePlan, routingMode, electricVehicleSpecification, actBefore, lastAct, chargingLink, tripRouter);
	//							Activity actEnd = PopulationUtils.createActivity(lastAct);
	//							lastAct.setEndTime(config.qsim().getEndTime().seconds()-600);
	//							modifiablePlan.addActivity(actEnd);
	//							this.planPlugoutTrip(modifiablePlan, routingMode, electricVehicleSpecification, lastAct, actEnd, chargingLink, tripRouter, config.qsim().getEndTime().seconds()-600);
	//							log.info(plan.getPerson().getId() + " is charging at home.");
	//							PersonContainer2 personContainer2 = new PersonContainer2(plan.getPerson().getId(), "is charging at home.");
	//							personContainer2s.add(personContainer2);
	//						}
	//						break;
	//						
	//
	//					} 
	//					
	//					
	//					
	//					
	//					
	//					else if( evLegs.get(evLegs.size()-1).equals(legWithCriticalSOC) && pseudoVehicle.getBattery().getSoc() > capacityThreshold ){
	//						
	//
	//						
	//						
	//						cnt = 0;
	//
	//					} else {
	//						replanPrecedentAndCurrentEVLegs(modifiablePlan, electricVehicleSpecification, legWithCriticalSOC);
	//						log.info(plan.getPerson().getId() + " is charging on the route.");
	//						PersonContainer2 personContainer2 = new PersonContainer2(plan.getPerson().getId(), "is charging on the route.");
	//						personContainer2s.add(personContainer2);
	//						cnt--;
	//					}
	//					if(!isConsistant(modifiablePlan) ||!checkPlanConsistancy(modifiablePlan)) {
	//						System.out.println("Plan is not consistant!!! Debug!!!");
	//					}
	//				} else {
	//					throw new IllegalStateException("critical leg is null. should not happen");
	//				}

	//			} while (legWithCriticalSOC != null && cnt > 0);

	//		if(!isConsistant(modifiablePlan) ||!checkPlanConsistancy(modifiablePlan)) {
	//			System.out.println("Plan is not consistant!!! Debug!!!");
	//		
	//	
	//	}


	//}



	public static boolean ifSameAct(Activity act1, Activity act2) {
		if(act1.getType().equals(act2.getType())&&act1.getLinkId().equals(act2.getLinkId())) {
			return true;
		}else {
			return false;
		}
	}

	private boolean checkPlanConsistancy(Plan plan) {

		Activity act = (Activity) plan.getPlanElements().get(0);

		for(PlanElement pe:plan.getPlanElements()) {

			if(pe instanceof Activity) {

				Activity a = (Activity)pe;
				if(a.equals(act))continue;
				if(act.getType().contains(UrbanVehicleChargingHandler.PLUGOUT_IDENTIFIER) && a.getType().contains(UrbanVehicleChargingHandler.PLUGIN_IDENTIFIER)
						) {
					if(a.getLinkId().equals(act.getLinkId())) {
						return false;
					}
				}
				act = a;
			}
		}
		return true;
	}

	public static int generateRandom(int start, int end, List<Integer> excludeRows) {

		int range = end - start + 1;
		Random rand = MatsimRandom.getRandom();
		int random = rand.nextInt(range) + 1;
		while(excludeRows.contains(random)) {
			random = rand.nextInt(range) + 1;
		}

		return random;
	}

	protected void replanPrecedentAndCurrentEVLegs(Plan modifiablePlan, ElectricVehicleSpecification electricVehicleSpecification, Leg leg) {
		Network modeNetwork = this.singleModeNetworksCache.getSingleModeNetworksCache().get(leg.getMode());

		String routingMode = TripStructureUtils.getRoutingMode(leg);
		List <Leg> evLegs = TripStructureUtils.getLegs(modifiablePlan).stream().filter(evleg -> evleg.getMode().equals(routingMode)).collect(toList());
		//		List <Leg> evLegs = new ArrayList<>();
		//		TripStructureUtils.getLegs(modifiablePlan).stream().forEach(l->{
		//			if(l.getMode().equals(routingMode))evLegs.add(l);
		//		});
		int legIndex = modifiablePlan.getPlanElements().indexOf(leg);
		Preconditions.checkState(legIndex > -1, "could not locate leg in plan");
		Activity actWhileCharging;
		List<Activity> actWhileChargingList;
		ChargerSpecification selectedCharger;
		int legIndexCounter = legIndex;
		//find suitable non-stage activity before SOC threshold passover
		actWhileChargingList = activityWhileChargingFinder.findActivitiesWhileChargingBeforeLeg(modifiablePlan, (Leg) modifiablePlan.getPlanElements().get(legIndexCounter));
		List<Integer> allreadyChecked = new ArrayList<>();
		if(actWhileChargingList!=null)actWhileChargingList = actWhileChargingList.stream().filter(a->a.getAttributes().getAttribute(ifBrokenActivity)!=null).collect(Collectors.toList());
		do {

			if (actWhileChargingList == null || actWhileChargingList.isEmpty()){
				log.warn(modifiablePlan.getPerson().getId() + " can't find a suitable activity prior the critical leg!");
				PersonContainer2 personContainer2 = new PersonContainer2(modifiablePlan.getPerson().getId(), "can't find a suitable activity prior the critical leg!");
				personContainer2s.add(personContainer2);
				return;
			}
			int ind = generateRandom(0,actWhileChargingList.size()-1,allreadyChecked);
			allreadyChecked.add(ind);
			selectedCharger = selectChargerNearToLink(modifiablePlan.getPerson().getId(),actWhileChargingList.get(ind).getLinkId(), electricVehicleSpecification, modeNetwork, actWhileChargingList.get(ind).getStartTime().seconds());

			if(selectedCharger == null){

				leg = evLegs.get(evLegs.indexOf(leg)-1);
				legIndexCounter = modifiablePlan.getPlanElements().indexOf(leg);
			}
			actWhileCharging = actWhileChargingList.get(ind);
		} while (actWhileCharging != null && selectedCharger == null && allreadyChecked.size()!=actWhileChargingList.size());
		if (selectedCharger == null){
			log.warn(modifiablePlan.getPerson().getId() + " can't find a suitable activity prior the critical leg which have a charger!");
			PersonContainer2 personContainer2 = new PersonContainer2(modifiablePlan.getPerson().getId(), "can't find a suitable activity prior the critical leg!");
			personContainer2s.add(personContainer2);
			return;
		}


		//		Preconditions.checkNotNull(actWhileCharging, "could not insert plugin activity in plan of agent " + mobsimagent.getId() +
		//				".\n One reason could be that the agent has no suitable activity prior to the leg for which the " +
		//				" energy threshold is expected to be exceeded. \n" +
		//				" Another reason  might be that it's vehicle is running beyond energy threshold during the first leg of the day." +
		//				"That could possibly be avoided by using EVNetworkRoutingModule..."); //TODO let the sim just run and let the ev run empty!?

		//TODO what if actWhileCharging does not hold a link id?

		Link chargingLink = modeNetwork.getLinks().get(selectedCharger.getLinkId());

		//		boolean breakAct = MatsimRandom.getRandom().nextBoolean();
		boolean breakAct = true; // if you keep it false, no activity break will happen**
		boolean chargeAtStart = false;
		if(breakAct == true) {
			chargeAtStart = MatsimRandom.getRandom().nextBoolean();

			// now we choose duration of the charging and place to break the activity
			ElectricVehicle pseudoVehicle = ElectricVehicleImpl.create(electricVehicleSpecification, driveConsumptionFactory, auxConsumptionFactory, chargingPowerFactory);
			pseudoVehicle.getBattery().setCharge(pseudoVehicle.getBattery().getCapacity());
			//double charge = pseudoVehicle.getChargingPower().calcChargingPower(selectedCharger) * chargingDuration;
			double reqCharge = 0;
			for(int i = modifiablePlan.getPlanElements().indexOf(leg);i<modifiablePlan.getPlanElements().size();i++) {
				if(modifiablePlan.getPlanElements().get(i) instanceof Leg) {
					Leg leg_ = (Leg) modifiablePlan.getPlanElements().get(i);
					if (leg_.getMode().equals(leg.getMode()) && VehicleUtils.getVehicleId(modifiablePlan.getPerson(), leg_.getMode()).toString().equals(pseudoVehicle.getId().toString())) {
						emulateVehicleDischarging(pseudoVehicle, leg_);
					}
				}
			}
			//TODO: fix the issue, the required 
			reqCharge = (pseudoVehicle.getBattery().getCapacity() - pseudoVehicle.getBattery().getSoc())*this.factorOfSafety;
			if(reqCharge<=0)reqCharge = pseudoVehicle.getBattery().getCapacity();
			double chargeTime = reqCharge/pseudoVehicle.getChargingPower().calcChargingPower(selectedCharger);
			double randomChargeTime = chargeTime + MatsimRandom.getRandom().nextGaussian()*chargeTime*cov;//
			if(randomChargeTime<600)randomChargeTime =600; 
			double actBreakTime = 0;
			TripRouter tripRouter = tripRouterProvider.get();
			int ind = modifiablePlan.getPlanElements().indexOf(actWhileCharging);
			if(chargeAtStart) {
				Activity pluginTripOrigin = findRealOrChargingActBefore(modifiablePlan, modifiablePlan.getPlanElements().indexOf(actWhileCharging));
				this.planPluginTrip(modifiablePlan, routingMode, electricVehicleSpecification, pluginTripOrigin, actWhileCharging, chargingLink, tripRouter);
				double beforeActStartTime = actWhileCharging.getStartTime().seconds();
//				double beforeActStartTime = TripRouter.calcEndOfPlanElement(pluginTripOrigin.getEndTime().seconds(), 
//						modifiablePlan.getPlanElements().get(ind-1), config);
				
				double beforeActEndTime = beforeActStartTime+randomChargeTime; 
				Activity actBeforeBreak = scenario.getPopulation().getFactory().createActivityFromCoord(actWhileCharging.getType(), actWhileCharging.getCoord());
				actBeforeBreak.setLinkId(actWhileCharging.getLinkId());
				actBeforeBreak.setFacilityId(actWhileCharging.getFacilityId());
				ActivityFacility fac;
				if((fac = scenario.getActivityFacilities().getFacilities().get(actBeforeBreak.getFacilityId())).getCoord()==null) {
					fac.setCoord(scenario.getNetwork().getLinks().get(fac.getLinkId()).getCoord());
				}
				actBeforeBreak.setEndTime(beforeActEndTime);

				actBeforeBreak.getAttributes().putAttribute(ifBrokenActivity, false);
				Activity actAfterBreak = scenario.getPopulation().getFactory().createActivityFromCoord(actWhileCharging.getType(), actWhileCharging.getCoord());
				actAfterBreak.setLinkId(actWhileCharging.getLinkId());
				actAfterBreak.setFacilityId(actWhileCharging.getFacilityId());
				actAfterBreak.setEndTime(actWhileCharging.getEndTime().seconds());
				actAfterBreak.getAttributes().putAttribute(ifBrokenActivity, false);

				if((fac = scenario.getActivityFacilities().getFacilities().get(actAfterBreak.getFacilityId())).getCoord()==null) {
					fac.setCoord(scenario.getNetwork().getLinks().get(fac.getLinkId()).getCoord());
				}
				Leg legDummy = scenario.getPopulation().getFactory().createLeg(TransportMode.walk);
				modifiablePlan.getPlanElements().add(ind,actBeforeBreak);
				modifiablePlan.getPlanElements().add(ind+1,legDummy);
				modifiablePlan.getPlanElements().add(ind+2,actAfterBreak);
				modifiablePlan.getPlanElements().remove(actWhileCharging);
				this.planPlugoutTrip(modifiablePlan, routingMode, electricVehicleSpecification, actBeforeBreak, actAfterBreak, chargingLink, tripRouter, beforeActEndTime);
			}else {
				actBreakTime = actWhileCharging.getEndTime().seconds()-randomChargeTime-600;
				Activity actBeforeBreak = scenario.getPopulation().getFactory().createActivityFromCoord(actWhileCharging.getType(), actWhileCharging.getCoord());
				actBeforeBreak.setEndTime(actBreakTime);
				actBeforeBreak.setLinkId(actWhileCharging.getLinkId());
				actBeforeBreak.setFacilityId(actWhileCharging.getFacilityId());
				ActivityFacility fac;
				if((fac = scenario.getActivityFacilities().getFacilities().get(actBeforeBreak.getFacilityId())).getCoord()==null) {
					fac.setCoord(scenario.getNetwork().getLinks().get(fac.getLinkId()).getCoord());
				}
				actBeforeBreak.getAttributes().putAttribute(ifBrokenActivity, false);
				Activity actAfterBreak = scenario.getPopulation().getFactory().createActivityFromCoord(actWhileCharging.getType(), actWhileCharging.getCoord());
				actAfterBreak.setLinkId(actWhileCharging.getLinkId());
				actAfterBreak.setFacilityId(actWhileCharging.getFacilityId());
				actAfterBreak.setEndTime(actWhileCharging.getEndTime().seconds());
				actAfterBreak.getAttributes().putAttribute(ifBrokenActivity, false);
				if((fac = scenario.getActivityFacilities().getFacilities().get(actAfterBreak.getFacilityId())).getCoord()==null) {
					fac.setCoord(scenario.getNetwork().getLinks().get(fac.getLinkId()).getCoord());
				}
				Leg legDummy = scenario.getPopulation().getFactory().createLeg(routingMode);
				modifiablePlan.getPlanElements().add(ind,actBeforeBreak);
				modifiablePlan.getPlanElements().add(ind+1,legDummy);
				modifiablePlan.getPlanElements().add(ind+2,actAfterBreak);
				modifiablePlan.getPlanElements().remove(actWhileCharging);
				this.planPluginTrip(modifiablePlan, routingMode, electricVehicleSpecification, actBeforeBreak, actAfterBreak, chargingLink, tripRouter);
				Leg plugoutLeg = activityWhileChargingFinder.getNextLegOfRoutingModeAfterActivity(ImmutableList.copyOf(modifiablePlan.getPlanElements()), actWhileCharging, routingMode);
				Activity plugoutTripDestination = findRealOrChargingActAfter(modifiablePlan, modifiablePlan.getPlanElements().indexOf(plugoutLeg));
				planPlugoutTrip(modifiablePlan, routingMode, electricVehicleSpecification, actAfterBreak, plugoutTripDestination, chargingLink, tripRouter, time.decideOnActivityEndTimeAlongPlan(actBeforeBreak, modifiablePlan).seconds());
			}


		}else {
			//
			//
			//
			//		Activity pluginTripOrigin = EditPlans.findRealActBefore(mobsimagent, modifiablePlan.getPlanElements().indexOf(actWhileCharging));
			Activity pluginTripOrigin = findRealOrChargingActBefore(modifiablePlan, modifiablePlan.getPlanElements().indexOf(actWhileCharging));

			Leg plugoutLeg = activityWhileChargingFinder.getNextLegOfRoutingModeAfterActivity(ImmutableList.copyOf(modifiablePlan.getPlanElements()), actWhileCharging, routingMode);
			Activity plugoutTripOrigin = findRealOrChargingActBefore(modifiablePlan, modifiablePlan.getPlanElements().indexOf(plugoutLeg));
			Activity plugoutTripDestination = findRealOrChargingActAfter(modifiablePlan, modifiablePlan.getPlanElements().indexOf(plugoutLeg));
			//
			//			//		{    //some consistency checks.. //TODO consider to put in a JUnit test..
			//			//			Preconditions.checkNotNull(pluginTripOrigin, "pluginTripOrigin is null. should never happen..");
			//			//			Preconditions.checkState(!pluginTripOrigin.equals(actWhileCharging), "pluginTripOrigin is equal to actWhileCharging. should never happen..");
			//			//
			//			//			PlanElement legToBeReplaced = modifiablePlan.getPlanElements().get(modifiablePlan.getPlanElements().indexOf(pluginTripOrigin) + 3);
			//			//			Preconditions.checkState(legToBeReplaced instanceof Leg);
			//			//			Preconditions.checkState(TripStructureUtils.getRoutingMode((Leg) legToBeReplaced).equals(routingMode), "leg after pluginTripOrigin has the wrong routing mode. should not happen..");
			//			//
			//			//			legToBeReplaced = modifiablePlan.getPlanElements().get(modifiablePlan.getPlanElements().indexOf(actWhileCharging) - 3);
			//			//			Preconditions.checkState(legToBeReplaced instanceof Leg);
			//			//			Preconditions.checkState(TripStructureUtils.getRoutingMode((Leg) legToBeReplaced).equals(routingMode), "leg before actWhileCharging has the wrong routing mode. should not happen..");
			//			//
			//			//			Preconditions.checkState(!plugoutTripDestination.equals(actWhileCharging), "plugoutTripDestination is equal to actWhileCharging. should never happen..");
			//			//
			//			//			Preconditions.checkState(modifiablePlan.getPlanElements().indexOf(pluginTripOrigin) < modifiablePlan.getPlanElements().indexOf(actWhileCharging));
			//			//			Preconditions.checkState(modifiablePlan.getPlanElements().indexOf(actWhileCharging) <= modifiablePlan.getPlanElements().indexOf(plugoutTripOrigin));
			//			//			Preconditions.checkState(modifiablePlan.getPlanElements().indexOf(plugoutTripOrigin) < modifiablePlan.getPlanElements().indexOf(plugoutTripDestination));
			//			//
			//			//			legToBeReplaced = modifiablePlan.getPlanElements().get(modifiablePlan.getPlanElements().indexOf(plugoutTripOrigin) + 3);
			//			//			Preconditions.checkState(legToBeReplaced instanceof Leg);
			//			//			Preconditions.checkState(TripStructureUtils.getRoutingMode((Leg) legToBeReplaced).equals(routingMode), "leg after plugoutTripOrigin has the wrong routing mode. should not happen..");
			//			//
			//			//			legToBeReplaced = modifiablePlan.getPlanElements().get(modifiablePlan.getPlanElements().indexOf(plugoutTripDestination) - 3);
			//			//			Preconditions.checkState(legToBeReplaced instanceof Leg);
			//			//			Preconditions.checkState(TripStructureUtils.getRoutingMode((Leg) legToBeReplaced).equals(routingMode), "leg before plugoutTripDestination has the wrong routing mode. should not happen..");
			//			//		}
			//
			TripRouter tripRouter = tripRouterProvider.get();
			planPluginTrip(modifiablePlan, routingMode, electricVehicleSpecification, pluginTripOrigin, actWhileCharging, chargingLink, tripRouter);
			planPlugoutTrip(modifiablePlan, routingMode, electricVehicleSpecification, plugoutTripOrigin, plugoutTripDestination, chargingLink, tripRouter, time.decideOnActivityEndTimeAlongPlan(plugoutTripOrigin, modifiablePlan).seconds());
		}
		if(!isConsistant(modifiablePlan)||!this.checkPlanConsistancy(modifiablePlan)) {
			System.out.println("Plan is not consistant!!! Debug!!!");
		}
		//		//this.checkPlanConsistancy(modifiablePlan);
		//
	}


	protected boolean isConsistant(Plan plan) {
		//int multiCharge = 0;
		List<Activity> acts = new ArrayList<>();
		//	plan.getPlanElements().stream().filter(pe-> pe instanceof Activity).forEach(a->acts.add(((Activity)a)));
		plan.getPlanElements().stream().forEach(a->{
			if(a instanceof Activity)acts.add(((Activity)a));
		});
		int charging = 0;
		for(Activity a:acts) {
			if(a.getType().contains(UrbanVehicleChargingHandler.PLUGIN_IDENTIFIER)) {
				charging++;
				//multiCharge++;
			}
			else if(a.getType().contains(UrbanVehicleChargingHandler.PLUGOUT_IDENTIFIER))charging--;
			if(charging>1 || charging<0) {
				return false;
			}
		}
		//		if(multiCharge>1) {
		//			System.out.println("Debug Here!!!");
		//		}
		return true;
	}

	//TODO possibly put behind interface
	@Nullable
	protected ChargerSpecification selectChargerNearToLink(Id<Person>pId, Id<Link> linkId, ElectricVehicleSpecification vehicleSpecification, Network network) {

		UrbanEVConfigGroup configGroup = (UrbanEVConfigGroup) config.getModules().get(UrbanEVConfigGroup.GROUP_NAME);
		double maxDistanceToAct = configGroup.getMaxDistanceBetweenActAndCharger_m();

		List<ChargerSpecification> chargerList = chargingInfrastructureSpecification.getChargerSpecifications()
				.values()
				.stream()
				.filter(charger -> vehicleSpecification.getChargerTypes().contains(charger.getChargerType()))
				.filter(c->this.chargerPricingProfiles.getChargerPricingProfiles().get(c.getId()).getPersonsAccecibleTo().isEmpty()||this.chargerPricingProfiles.getChargerPricingProfiles().get(c.getId()).getPersonsAccecibleTo().contains(pId))
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
	/**
	 * 
	 * @param plan
	 * @param routingMode mode for the EV leg
	 * @param electricVehicleSpecification
	 * @param actBeforeCharging 
	 * @param actWhileCharging activity after running
	 * @param chargingLink
	 * @param tripRouter
	 */
	protected void planPluginTrip(Plan plan, String routingMode, ElectricVehicleSpecification electricVehicleSpecification, Activity actBeforeCharging, Activity actWhileCharging, Link chargingLink, TripRouter tripRouter) {
		Facility fromFacility = FacilitiesUtils.toFacility(actBeforeCharging, scenario.getActivityFacilities());
		Facility chargerFacility = new LinkWrapperFacility(chargingLink);
		Facility toFacility = FacilitiesUtils.toFacility(actWhileCharging, scenario.getActivityFacilities());

		List<PlanElement> trip = new ArrayList<>();
		//add leg to charger
		List<? extends PlanElement> routedSegment = tripRouter.calcRoute(routingMode, fromFacility, chargerFacility,
				time.decideOnActivityEndTimeAlongPlan(actBeforeCharging, plan).seconds(), plan.getPerson(),null);

		//set the vehicle id
		for (Leg leg : TripStructureUtils.getLegs(routedSegment)) {
			if(leg.getMode().equals(routingMode)){
				NetworkRoute route = ((NetworkRoute) leg.getRoute());
				if(route.getVehicleId() == null) route.setVehicleId(Id.createVehicleId(electricVehicleSpecification.getId()));
			}
		}

		Leg lastLeg = (Leg) routedSegment.get(routedSegment.size() - 1);
		double now = lastLeg.getDepartureTime().seconds() + lastLeg.getRoute().getTravelTime().seconds();
		trip.addAll(routedSegment);

		//add plugin act

		Activity pluginAct =PopulationUtils.createStageActivityFromCoordLinkIdAndModePrefix(chargingLink.getCoord(),
				chargingLink.getId(), routingMode + UrbanVehicleChargingHandler.PLUGIN_IDENTIFIER);
		//		Activity pluginAct = PopulationUtils.createActivityFromCoord(UrbanVehicleChargingHandler.PLUGIN_IDENTIFIER, chargingLink.getCoord());
		pluginAct.setStartTime(now);
		pluginAct.setLinkId(chargingLink.getId());
		pluginAct.setMaximumDuration(config.planCalcScore().getActivityParams(UrbanVehicleChargingHandler.PLUGIN_IDENTIFIER).getTypicalDuration().seconds());

		trip.add(pluginAct);

		now = time.decideOnActivityEndTime(pluginAct, now).seconds();
		pluginAct.setEndTime(now);

		//add walk leg to destination
		routedSegment = tripRouter.calcRoute(TransportMode.walk, chargerFacility, toFacility, now, plan.getPerson(),null);
		Leg egress = (Leg) routedSegment.get(0);
		TripStructureUtils.setRoutingMode(egress, routingMode);// should not it be walk???
		trip.add(egress);
		now = now+time.decideOnLegTravelTime(egress).seconds();

		//insert trip
		TripRouter.insertTrip(plan, actBeforeCharging, trip, actWhileCharging);
		actWhileCharging.setStartTime(now);
		//reset activity end time
		if (!plan.getPlanElements().get(plan.getPlanElements().size()-1).equals(actWhileCharging)) {
			actWhileCharging.setEndTime(time.decideOnActivityEndTime(actWhileCharging, now).seconds());
		}
	}

	@Override
	public void finishReplanning() {
		long t = System.currentTimeMillis();
		// TODO Auto-generated method stub
		this.plans.parallelStream().forEach(p->this.execute(p));
		log.info("total time for replanning"+this.plans.size()+"plans = "+(System.currentTimeMillis()-t));
	}

	protected Activity findRealOrChargingActBefore(Plan plan, int index) {
		List<PlanElement> planElements = plan.getPlanElements();

		Activity prevAct = null;
		for (int ii = 0; ii < index; ii++) {
			if (planElements.get(ii) instanceof Activity) {
				Activity act = (Activity) planElements.get(ii);
				if (!StageActivityTypeIdentifier.isStageActivity(act.getType()) ||
						act.getType().contains(UrbanVehicleChargingHandler.PLUGIN_INTERACTION) ||
						act.getType().contains(UrbanVehicleChargingHandler.PLUGOUT_INTERACTION)) {
					prevAct = act;
				}
			}
		}
		return prevAct;
	}



	protected Activity findRealOrChargingActAfter(Plan plan, int index) {
		List<PlanElement> planElements = plan.getPlanElements();
		return (Activity) planElements.get(findIndexOfRealActAfter(plan, index));
	}
	/**
	 * returns leg for which the critical soc is exceeded or the last of all ev legs.
	 *
	 * @param modifiablePlan
	 * @param pseudoVehicle
	 * @param originalVehicleId
	 * @return
	 */
	protected Leg getCriticalOrLastEvLeg(Plan modifiablePlan, ElectricVehicle pseudoVehicle, Id<Vehicle> originalVehicleId) {
		UrbanEVConfigGroup configGroup = (UrbanEVConfigGroup) config.getModules().get(UrbanEVConfigGroup.GROUP_NAME);


		double capacityThreshold = pseudoVehicle.getBattery().getCapacity() * (configGroup.getCriticalRelativeSOC()); //TODO randomize? Might also depend on the battery size!

		Double chargingBegin = null;

		Set<String> modesWithVehicles = new HashSet<>(scenario.getConfig().qsim().getMainModes());
		modesWithVehicles.addAll(scenario.getConfig().plansCalcRoute().getNetworkModes());

		Leg lastLegWithVehicle = null;
		//TODO: The current logic will go through each leg and discharge the ev for that leg; and then will check if the soc is lower than capacity threshold.
		//This does not ensure that the remaining soc is enough for completing the next leg, resulting in some en route dead evs. 
		//This should be fixed/ 
		//double socAfterLeg = pseudoVehicle.getBattery().getSoc();

		for (PlanElement planElement : modifiablePlan.getPlanElements()) {
			if (planElement instanceof Leg) {

				Leg leg = (Leg) planElement;
				if (modesWithVehicles.contains(leg.getMode()) && VehicleUtils.getVehicleId(modifiablePlan.getPerson(), leg.getMode()).equals(originalVehicleId)) {
					lastLegWithVehicle = leg;
					emulateVehicleDischarging(pseudoVehicle, leg);
					if (pseudoVehicle.getBattery().getSoc() <= capacityThreshold) {
						return leg;
					}
				}
			} else if (planElement instanceof Activity) {
				if (((Activity) planElement).getType().contains(UrbanVehicleChargingHandler.PLUGIN_INTERACTION)) {
					Leg legToCharger = (Leg) modifiablePlan.getPlanElements().get(modifiablePlan.getPlanElements().indexOf(planElement) - 1);
					chargingBegin = legToCharger.getDepartureTime().seconds() + legToCharger.getTravelTime().seconds();

				} else if (((Activity) planElement).getType().contains(UrbanVehicleChargingHandler.PLUGOUT_INTERACTION)) {

					Leg legFromCharger = (Leg) modifiablePlan.getPlanElements().get(modifiablePlan.getPlanElements().indexOf(planElement) + 1);
					if (chargingBegin == null) throw new IllegalStateException();
					double chargingDuration = legFromCharger.getDepartureTime().seconds() - chargingBegin;

					ChargerSpecification chargerSpecification = null;

					Optional<ChargerSpecification> a = chargingInfrastructureSpecification.getChargerSpecifications()
							.values()
							.stream()
							.filter(charger -> charger.getLinkId().equals(((Activity) planElement).getLinkId()))
							.filter(charger -> pseudoVehicle.getChargerTypes().contains(charger.getChargerType()))
							.findAny();

					if( !a.isPresent()) {
						throw new NoSuchElementException();
					}else {
						chargerSpecification = a.get();
					}
					//					List<ChargerSpecification> chargers = new ArrayList<>();
					//					chargingInfrastructureSpecification.getChargerSpecifications().values().stream().forEach(c->{
					//						if(c.getLinkId().equals(((Activity) planElement).getLinkId()) && pseudoVehicle.getChargerTypes().contains(c.getChargerType())){
					//							chargers.add(c);
					//						}
					//					});
					//					
					//					if(chargers.isEmpty()) {
					//						throw new NoSuchElementException();
					//					}else {
					//						chargerSpecification = chargers.get(MatsimRandom.getRandom().nextInt(chargers.size()));
					//					}


					pseudoVehicle.getBattery().setCharge(pseudoVehicle.getBattery().getCharge()+ pseudoVehicle.getChargingPower().calcChargingPower(chargerSpecification) * chargingDuration);
				}
			} else throw new IllegalArgumentException();
		}

		return lastLegWithVehicle;
	}
	/**
	 * this method has the side effect that the soc of the ev is altered by estimated energy consumption of the leg
	 *
	 * @param ev
	 * @param leg
	 */
	protected void emulateVehicleDischarging(ElectricVehicle ev, Leg leg) {
		//retrieve mode specific network
		Network network = this.singleModeNetworksCache.getSingleModeNetworksCache().get(leg.getMode());
		//retrieve routin mode specific travel time
		String routingMode = TripStructureUtils.getRoutingMode(leg);
		TravelTime travelTime = this.travelTimes.get(routingMode);
		if (travelTime == null) {
			throw new RuntimeException("No TravelTime bound for mode " + routingMode + ".");
		}

		//		Map<Link, Double> consumptions = new LinkedHashMap<>();
		NetworkRoute route = (NetworkRoute) leg.getRoute();
		List<Link> links = NetworkUtils.getLinks(network, route.getLinkIds());

		DriveEnergyConsumption driveEnergyConsumption = ev.getDriveEnergyConsumption();
		AuxEnergyConsumption auxEnergyConsumption = ev.getAuxEnergyConsumption();
		double linkEnterTime = leg.getDepartureTime().seconds();

		for (Link l : links) {
			double travelT = travelTime.getLinkTravelTime(l, leg.getDepartureTime().seconds(), null, null);

			double driveConsumption = driveEnergyConsumption.calcEnergyConsumption(l, travelT, linkEnterTime);
			double auxConsumption = auxEnergyConsumption.calcEnergyConsumption(leg.getDepartureTime().seconds(), travelT, l.getId());
			//			double consumption = driveEnergyConsumption.calcEnergyConsumption(l, travelT, linkEnterTime)
			//					+ auxEnergyConsumption.calcEnergyConsumption(leg.getDepartureTime().seconds(), travelT, l.getId());
			double consumption = driveConsumption + auxConsumption;
			ev.getBattery().setCharge(ev.getBattery().getCharge()-consumption);
			linkEnterTime += travelT;
		}
	}
	protected int findIndexOfRealActAfter(Plan plan, int index) {
		List<PlanElement> planElements = plan.getPlanElements();

		int theIndex = -1;
		for (int ii = planElements.size() - 1; ii > index; ii--) {
			if (planElements.get(ii) instanceof Activity) {
				Activity act = (Activity) planElements.get(ii);
				if (!StageActivityTypeIdentifier.isStageActivity(act.getType()) ||
						act.getType().contains(UrbanVehicleChargingHandler.PLUGIN_INTERACTION) ||
						act.getType().contains(UrbanVehicleChargingHandler.PLUGOUT_INTERACTION)) {
					theIndex = ii;
				}
			}
		}
		return theIndex;
	}
	/**
	 * 
	 * @param plan 
	 * @param routingMode EV leg mode
	 * @param electricVehicleSpecification
	 * @param actWhileCharging the activity both before and after the plugin
	 * @param chargingLink
	 * @param tripRouter
	 */
	protected double planPluginTripFromHomeToCharger(Plan plan, String routingMode, ElectricVehicleSpecification electricVehicleSpecification,Activity actWhileCharging, Link chargingLink, TripRouter tripRouter) {
		PopulationFactory factory = scenario.getPopulation().getFactory();
		Facility fromFacility = FacilitiesUtils.toFacility(actWhileCharging, scenario.getActivityFacilities());
		Facility chargerFacility = new LinkWrapperFacility(chargingLink);
		Facility toFacility = FacilitiesUtils.toFacility(actWhileCharging, scenario.getActivityFacilities());

		//copy the First Act and set the end time to 0s. Since every plan has to start with an act
		Activity newFirstAct = factory.createActivityFromLinkId(actWhileCharging.getType(), actWhileCharging.getLinkId());
		newFirstAct.setEndTime(0);
		double now = 0;
		//TripRouter.calcEndOfPlanElement(now, newFirstAct, config);
		plan.getPlanElements().add(0, newFirstAct);


		//add leg to charger
		List<? extends PlanElement> routeToCharger = tripRouter.calcRoute(TransportMode.car, fromFacility, chargerFacility, now, plan.getPerson(), null);// Should be routing mode
		//set the vehicle id
		for (Leg leg : TripStructureUtils.getLegs(routeToCharger)) {
			if(leg.getMode().equals(routingMode)){
				NetworkRoute route = ((NetworkRoute) leg.getRoute());
				if(route.getVehicleId() == null) route.setVehicleId(Id.createVehicleId(electricVehicleSpecification.getId()));
				now = time.decideOnElementEndTime(leg, now).seconds();
			}
		}


		//add plugin act

		//Activity pluginAct = PopulationUtils.createActivityFromCoord(UrbanVehicleChargingHandler.PLUGIN_IDENTIFIER, chargingLink.getCoord());
		Activity pluginAct = PopulationUtils.createStageActivityFromCoordLinkIdAndModePrefix(chargingLink.getCoord(),
				chargingLink.getId(), routingMode + UrbanVehicleChargingHandler.PLUGIN_IDENTIFIER);//This is how to add a aplugin activity
		pluginAct.setLinkId(chargingLink.getId());
		pluginAct.setMaximumDuration(config.planCalcScore().getActivityParams(UrbanVehicleChargingHandler.PLUGIN_IDENTIFIER).getTypicalDuration().seconds());
		pluginAct.setStartTime(now);
		now = time.decideOnElementEndTime(pluginAct, now).seconds();
		pluginAct.setEndTime(now);
		plan.getPlanElements().add(2, pluginAct);
		TripRouter.insertTrip(plan, newFirstAct,routeToCharger,pluginAct);
		//add walk leg to destination
		List<? extends PlanElement> routeFromChargerToAct = tripRouter.calcRoute(TransportMode.walk, chargerFacility, toFacility,
				now, plan.getPerson(),null);
		int indexRouteFromChargerToAct = plan.getPlanElements().indexOf(pluginAct) + 1;
		plan.getPlanElements().add(indexRouteFromChargerToAct, routeFromChargerToAct.get(0));
		now = time.decideOnElementEndTime(routeFromChargerToAct.get(0), now).seconds();
		plan.getPlanElements().add(indexRouteFromChargerToAct+1, actWhileCharging);
		actWhileCharging.setStartTime(now);
		actWhileCharging.setEndTime(time.decideOnActivityEndTime(actWhileCharging, now).seconds());
		Leg egress = (Leg) routeFromChargerToAct.get(0);
		TripStructureUtils.setRoutingMode(egress, routingMode);
		return now;
	}

	protected Boolean isHomeChargingTrip(Plan modifiablePlan, List<Leg> evLegs, ElectricVehicle ev) {

		int firstEvLegIndex = modifiablePlan.getPlanElements().indexOf(evLegs.get(0));
		Id<Link> homeLink = EditPlansReplan.findRealActBefore(modifiablePlan,firstEvLegIndex).getLinkId();
		Id<Person> person =  modifiablePlan.getPerson().getId();
		boolean isHomeTrip = EditPlansReplan.findRealActAfter(modifiablePlan,modifiablePlan.getPlanElements().indexOf(evLegs.get(evLegs.size()-1))).getLinkId().equals(homeLink);
		boolean hasHomeCharger = chargingInfrastructureSpecification.getChargerSpecifications().values().stream()
				.filter(chargerSpecification -> ev.getChargerTypes().contains(chargerSpecification.getChargerType()))
				.filter(chargerSpecification->this.chargerPricingProfiles.getChargerPricingProfiles().get(chargerSpecification.getId())
						.getPersonsAccecibleTo().contains(person) ||this.chargerPricingProfiles.getChargerPricingProfiles().get(chargerSpecification.getId())
						.getPersonsAccecibleTo().isEmpty())// Delete this line to go back to the original
				.map(chargerSpecification -> chargerSpecification.getLinkId())
				.anyMatch(linkId -> linkId.equals(homeLink));

		//		for(ChargerSpecification c:chargingInfrastructureSpecification.getChargerSpecifications().values()){
		//			if(ev.getChargerTypes().contains(c.getChargerType())&& 
		//					(this.chargerPricingProfiles.getChargerPricingProfiles().get(c.getId())
		//							.getPersonsAccecibleTo().contains(person) ||this.chargerPricingProfiles.getChargerPricingProfiles().get(c.getId())
		//							.getPersonsAccecibleTo().isEmpty()) && c.getLinkId().equals(homeLink)){
		//			return isHomeTrip;
		//		}
		//		}


		return isHomeTrip && hasHomeCharger;
		//	return false;
	}

	protected boolean hasHomeCharger(Person person, Plan modifiablePlan, List<Leg> evLegs, ElectricVehicle ev){

		int firstEvLegIndex = modifiablePlan.getPlanElements().indexOf(evLegs.get(0));
		Id<Link> homeLink = EditPlansReplan.findRealActBefore(modifiablePlan,firstEvLegIndex).getLinkId();
		Id<Person> personId =  person.getId();
		boolean hasHomeCharger = chargingInfrastructureSpecification.getChargerSpecifications().values().stream()
				.filter(chargerSpecification -> ev.getChargerTypes().contains(chargerSpecification.getChargerType()))
				.filter(chargerSpecification->this.chargerPricingProfiles.getChargerPricingProfiles().get(chargerSpecification.getId())
						.getPersonsAccecibleTo().contains(personId) ||this.chargerPricingProfiles.getChargerPricingProfiles().get(chargerSpecification.getId())
						.getPersonsAccecibleTo().isEmpty())// Delete this line to go back to the original
				.map(chargerSpecification -> chargerSpecification.getLinkId())
				.anyMatch(linkId -> linkId.equals(homeLink));

		//		for(ChargerSpecification c:chargingInfrastructureSpecification.getChargerSpecifications().values()){
		//			if(ev.getChargerTypes().contains(c.getChargerType())&& 
		//					(this.chargerPricingProfiles.getChargerPricingProfiles().get(c.getId())
		//							.getPersonsAccecibleTo().contains(person) ||this.chargerPricingProfiles.getChargerPricingProfiles().get(c.getId())
		//							.getPersonsAccecibleTo().isEmpty()) && c.getLinkId().equals(homeLink)){
		//			return true;
		//		}
		//		}
		return hasHomeCharger;
		//return false;
	}

	/**
	 * 
	 * @param plan
	 * @param routingMode is the mode of the ev
	 * @param electricVehicleSpecification
	 * @param origin the location of the activity before the ev leg
	 * @param destination the location of the activity after the ev leg
	 * @param chargingLink 
	 * @param tripRouter
	 * @param now is the time the origin activity finishes
	 */
	protected void planPlugoutTrip(Plan plan, String routingMode, ElectricVehicleSpecification electricVehicleSpecification, Activity origin, Activity destination, Link chargingLink, TripRouter tripRouter, double now) {
		Facility fromFacility = FacilitiesUtils.toFacility(origin, scenario.getActivityFacilities());
		Facility chargerFacility = new LinkWrapperFacility(chargingLink);
		Facility toFacility = FacilitiesUtils.toFacility(destination, scenario.getActivityFacilities());

		List<? extends PlanElement> routedSegment;
		//actually destination can not be null based on how we determine the actWhileCharging = origin at the moment...
		if (destination == null) throw new RuntimeException("should not happen");

		List<PlanElement> trip = new ArrayList<>();

		//add leg to charger
		routedSegment = tripRouter.calcRoute(TransportMode.walk, fromFacility, chargerFacility,
				now, plan.getPerson(),null);
		Leg accessLeg = (Leg) routedSegment.get(0);
		now = now + time.decideOnLegTravelTime(accessLeg).seconds();
		TripStructureUtils.setRoutingMode(accessLeg, routingMode);//TODO: Should not the routing mode here be walk???
		trip.add(accessLeg);

		//add plugout act

		Activity plugOutAct = PopulationUtils.createStageActivityFromCoordLinkIdAndModePrefix(chargingLink.getCoord(),
				chargingLink.getId(), routingMode + UrbanVehicleChargingHandler.PLUGOUT_IDENTIFIER);// this is how to plan a plugout activity
		//Activity plugOutAct = PopulationUtils.createActivityFromCoord(UrbanVehicleChargingHandler.PLUGOUT_IDENTIFIER, chargingLink.getCoord());
		plugOutAct.setLinkId(chargingLink.getId());
		plugOutAct.setMaximumDuration(config.planCalcScore().getActivityParams(UrbanVehicleChargingHandler.PLUGOUT_IDENTIFIER).getTypicalDuration().seconds());

		trip.add(plugOutAct);
		plugOutAct.setStartTime(now);
		now = time.decideOnActivityEndTime(plugOutAct, now).seconds();

		//add leg to destination
		routedSegment = tripRouter.calcRoute(routingMode, chargerFacility, toFacility, now, plan.getPerson(),null);
		trip.addAll(routedSegment);

		for (PlanElement element : routedSegment) {
			
			now = time.decideOnElementEndTime(element, now).seconds();
			//insert vehicle id
			if(element instanceof Leg){
				Leg leg = (Leg) element;
				if(leg.getMode().equals(routingMode)){
					NetworkRoute route = ((NetworkRoute) leg.getRoute());
					if(route.getVehicleId() == null) route.setVehicleId(Id.createVehicleId(electricVehicleSpecification.getId()));
				}
			}
		}

		//insert trip
		TripRouter.insertTrip(plan, origin, trip, destination);
		destination.setStartTime(now);
		//reset activity end time
		if (!plan.getPlanElements().get(plan.getPlanElements().size() - 1).equals(destination)) {
			destination.setEndTime(time.decideOnActivityEndTime(destination, now).seconds());
		}
	}

	/**
	 * 
	 * @param plan
	 * @param routingMode is the mode of the ev
	 * @param electricVehicleSpecification
	 * @param origin the location of the activity before the ev leg
	 * @param destination the location of the activity after the ev leg
	 * @param chargingLink 
	 * @param tripRouter
	 * @param now is the time the origin activity finishes
	 */
	protected void planPlugoutTripAtTheEnd(Plan plan, String routingMode, ElectricVehicleSpecification electricVehicleSpecification, Activity origin, Activity destination, Link chargingLink, TripRouter tripRouter, double now) {
		Facility fromFacility = FacilitiesUtils.toFacility(origin, scenario.getActivityFacilities());
		Facility chargerFacility = new LinkWrapperFacility(chargingLink);
		Facility toFacility = FacilitiesUtils.toFacility(destination, scenario.getActivityFacilities());

		List<? extends PlanElement> routedSegment;
		//actually destination can not be null based on how we determine the actWhileCharging = origin at the moment...
		if (destination == null) throw new RuntimeException("should not happen");

		List<PlanElement> trip = new ArrayList<>();

		//add leg to charger
		routedSegment = tripRouter.calcRoute(TransportMode.walk, fromFacility, chargerFacility,
				now, plan.getPerson(),null);
		Leg accessLeg = (Leg) routedSegment.get(0);
		now = time.decideOnElementEndTime(accessLeg, now).seconds();
		TripStructureUtils.setRoutingMode(accessLeg, routingMode);//TODO: Should not the routing mode here be walk???
		trip.add(accessLeg);

		//add plugout act

		Activity plugOutAct = PopulationUtils.createStageActivityFromCoordLinkIdAndModePrefix(chargingLink.getCoord(),
				chargingLink.getId(), routingMode + UrbanVehicleChargingHandler.PLUGOUT_IDENTIFIER);// this is how to plan a plugout activity
		//Activity plugOutAct = PopulationUtils.createActivityFromCoord(UrbanVehicleChargingHandler.PLUGOUT_IDENTIFIER, chargingLink.getCoord());
		plugOutAct.setLinkId(chargingLink.getId());
		plugOutAct.setMaximumDuration(config.planCalcScore().getActivityParams(UrbanVehicleChargingHandler.PLUGOUT_IDENTIFIER).getTypicalDuration().seconds());

		trip.add(plugOutAct);
		plugOutAct.setStartTime(now);
		now = time.decideOnElementEndTime(plugOutAct, now).seconds();

		//add leg to destination
		routedSegment = tripRouter.calcRoute(routingMode, chargerFacility, toFacility, now, plan.getPerson(),null);
		trip.addAll(routedSegment);

		for (PlanElement element : routedSegment) {
			now = time.decideOnElementEndTime(element, now).seconds();
			//insert vehicle id
			if(element instanceof Leg){
				Leg leg = (Leg) element;
				if(leg.getMode().equals(routingMode)){
					NetworkRoute route = ((NetworkRoute) leg.getRoute());
					if(route.getVehicleId() == null) route.setVehicleId(Id.createVehicleId(electricVehicleSpecification.getId()));
				}
			}
		}

		//insert trip
		TripRouter.insertTrip(plan, origin, trip, destination);
		destination.setStartTime(now);
		//reset activity end time
		if (!plan.getPlanElements().get(plan.getPlanElements().size() - 1).equals(destination)) {
			destination.setEndTime(time.decideOnActivityEndTime(destination, now).seconds());
		}
	}

}

class PersonContainer2{
private final Id<Person> personId;
private final String reason;


PersonContainer2 (Id<Person> personId, String reason){
this.personId = personId;
this.reason = reason;
}
}

