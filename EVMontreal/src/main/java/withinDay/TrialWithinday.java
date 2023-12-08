package withinDay;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contrib.ev.charging.ChargingLogic;
import org.matsim.contrib.ev.charging.ChargingPower;
import org.matsim.contrib.ev.discharging.AuxEnergyConsumption;
import org.matsim.contrib.ev.discharging.DriveEnergyConsumption;
import org.matsim.contrib.ev.fleet.ElectricFleetSpecification;
import org.matsim.contrib.ev.fleet.ElectricVehicle;
import org.matsim.contrib.ev.fleet.ElectricVehicleImpl;
import org.matsim.contrib.ev.fleet.ElectricVehicleSpecification;
import org.matsim.contrib.ev.infrastructure.ChargingInfrastructureSpecification;
import org.matsim.core.mobsim.framework.MobsimAgent;
import org.matsim.core.mobsim.framework.events.MobsimInitializedEvent;
import org.matsim.core.mobsim.framework.listeners.MobsimInitializedListener;
import org.matsim.core.mobsim.qsim.QSim;
import org.matsim.core.mobsim.qsim.agents.WithinDayAgentUtils;
import org.matsim.core.router.SingleModeNetworksCache;
import org.matsim.core.router.TripRouter;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.util.TravelTime;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleUtils;
import org.matsim.vehicles.Vehicles;

import com.google.inject.Inject;
import com.google.inject.Provider;

import one.util.streamex.StreamEx;
import urbanEV.UrbanVehicleChargingHandler;

public class TrialWithinday implements MobsimInitializedListener{
	@Inject
	private Provider<TripRouter> tripRouterProvider;

	@Inject
	Scenario scenario;

	@Inject
	Vehicles vehicles;

	@Inject
	private SingleModeNetworksCache singleModeNetworksCache;

	@Inject
	private ElectricFleetSpecification electricFleetSpecification;

	@Inject
	private ChargingInfrastructureSpecification chargingInfrastructureSpecification;

	@Inject
	private DriveEnergyConsumption.Factory driveConsumptionFactory;

	@Inject
	private AuxEnergyConsumption.Factory auxConsumptionFactory;

	@Inject
	private ChargingPower.Factory chargingPowerFactory;

	@Inject
	private ChargingLogic.Factory chargingLogicFactory;

	@Inject
	private Map<String, TravelTime> travelTimes;
	
	private QSim qsim;

	@Override
	public void notifyMobsimInitialized(MobsimInitializedEvent e) {
		if (!(e.getQueueSimulation() instanceof QSim)) {
			throw new IllegalStateException(TrialWithinday.class.toString() + " only works with a mobsim of type " + QSim.class);
		}
		//collect all selected plans that contain ev legs and map them to the set of ev used
		Map<Plan, Set<Id<Vehicle>>> selectedEVPlans = StreamEx.of(scenario.getPopulation().getPersons().values())
		.mapToEntry(p -> p.getSelectedPlan(), p -> getUsedEV(p.getSelectedPlan()))
		.filterValues(evSet -> !evSet.isEmpty())
		.collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
//		Map<Plan, Set<Id<Vehicle>>> selectedEVPlans = new HashMap<>();
//		scenario.getPopulation().getPersons().values().parallelStream().forEach(p->{
//			Plan pl = p.getSelectedPlan();
//			Set<Id<Vehicle>> usedEV = getUsedEV(p.getSelectedPlan());
//			if(!usedEV.isEmpty())selectedEVPlans.put(pl, usedEV);
//		});
		
		this.qsim = (QSim) e.getQueueSimulation();
		
		for (Plan plan : selectedEVPlans.keySet()) {
			MobsimAgent mobsimagent = qsim.getAgents().get(plan.getPerson().getId());
			Plan modifiablePlan = WithinDayAgentUtils.getModifiablePlan(mobsimagent);
			Plan purePlan = (Plan) plan.getPerson().getAttributes().getAttribute("purePlan");
			if(purePlan!=null) {
			for (Id<Vehicle> ev : selectedEVPlans.get(plan)) {
				ElectricVehicleSpecification electricVehicleSpecification = electricFleetSpecification.getVehicleSpecifications()
					.get(getWrappedElectricVehicleId(ev));
					ElectricVehicle pseudoVehicle = ElectricVehicleImpl.create(electricVehicleSpecification, driveConsumptionFactory, auxConsumptionFactory, chargingPowerFactory);
					if (haveChargingAtStart(modifiablePlan) && (electricVehicleSpecification.getInitialSoc() / pseudoVehicle.getBattery().getCapacity() ) >.70 ) {
						List<PlanElement> planEl = modifiablePlan.getPlanElements().subList(0, 7);
						planEl.clear();
						planEl.add(purePlan.getPlanElements().get(0));
						planEl.add(purePlan.getPlanElements().get(1));
					}
				

					if (haveChargingAtEnd(modifiablePlan) &&(electricVehicleSpecification.getInitialSoc() / pseudoVehicle.getBattery().getCapacity() ) >.70 ) {
						List<PlanElement> planEle = modifiablePlan.getPlanElements().subList(plan.getPlanElements().size()-9, plan.getPlanElements().size()-1);
						planEle.clear();
						planEle.add(purePlan.getPlanElements().get(purePlan.getPlanElements().size()-2));
						planEle.add(purePlan.getPlanElements().get(purePlan.getPlanElements().size()-1));
//						subListRangeCheck(plan.getPlanElements().size()-9, plan.getPlanElements().size()-1, 8);
					}
			}
		}
		}

		
	}
	
	private boolean haveChargingAtStart(Plan plan) {
		if(plan.getPlanElements().size()<9)return false;
		if(!((Activity)plan.getPlanElements().get(2)).getType().contains(UrbanVehicleChargingHandler.PLUGIN_IDENTIFIER))return false;
		if(((Activity)plan.getPlanElements().get(0)).getFacilityId().equals(((Activity)plan.getPlanElements().get(4)).getFacilityId()))return true;
		return false;
	}

	private boolean haveChargingAtEnd(Plan plan) {
		if(plan.getPlanElements().size()<9)return false;
		if(!((Activity)plan.getPlanElements().get(plan.getPlanElements().size()-3)).getType().contains(UrbanVehicleChargingHandler.PLUGOUT_IDENTIFIER))return false;
		if(((Activity)plan.getPlanElements().get(plan.getPlanElements().size()-1)).getFacilityId().equals(((Activity)plan.getPlanElements().get(plan.getPlanElements().size()-5)).getFacilityId()))return true;
		return false;
	}
	
	private int size() {
		// TODO Auto-generated method stub
		return 0;
	}

	private Set<Id<Vehicle>> getUsedEV(Plan plan) {
		return TripStructureUtils.getLegs(plan).stream().filter(leg->leg.getMode().equals("car")||leg.getMode().equals("car_passenger"))
				.map(leg -> VehicleUtils.getVehicleId(plan.getPerson(), leg.getMode()))
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

	private boolean isEV(Id<Vehicle> vehicleId) {
		return this.electricFleetSpecification.getVehicleSpecifications().containsKey(getWrappedElectricVehicleId(vehicleId));
	}
	
	public Id<ElectricVehicle> getWrappedElectricVehicleId(Id<Vehicle> vehicleId){
		Id<ElectricVehicle> eId = Id.create(vehicleId.toString(),ElectricVehicle.class);
		return eId;
	}
	
	@Inject
	public TrialWithinday() {
		
	}
}
