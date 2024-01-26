package binding;

import java.util.HashMap;
import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.PersonEntersVehicleEvent;
import org.matsim.api.core.v01.events.PersonLeavesVehicleEvent;
import org.matsim.api.core.v01.events.PersonMoneyEvent;
import org.matsim.api.core.v01.events.handler.PersonEntersVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.PersonLeavesVehicleEventHandler;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contrib.ev.EvConfigGroup;
import org.matsim.contrib.ev.fleet.ElectricFleet;
import org.matsim.contrib.ev.fleet.ElectricVehicle;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.MobsimScopeEventHandler;
import org.matsim.core.mobsim.framework.events.MobsimAfterSimStepEvent;
import org.matsim.core.mobsim.framework.listeners.MobsimAfterSimStepListener;

import com.google.inject.Inject;

public class EVOutOfBatteryChecker implements MobsimAfterSimStepListener,MobsimScopeEventHandler{
	
	
	private final ElectricFleet Ev;
	@Inject
	private EventsManager manager;
	private final int chargeTimeStep;
	
	// the assumption is that the function looks like a*(exp(b*(soc-1))-1)
	private double rangeAnxietyCoefficientA = 10;
	private double rangeAnxietyCoefficientB = 5;
	
//	private Map<Id<ElectricVehicle>,Id<Person>> vehicleOwners = new HashMap<>();

	@Inject
	public EVOutOfBatteryChecker(ElectricFleet ev, EvConfigGroup evConfig) {
		this.Ev = ev;
		this.chargeTimeStep = evConfig.getChargeTimeStep();
	}

	@Override
	public void notifyMobsimAfterSimStep(@SuppressWarnings("rawtypes") MobsimAfterSimStepEvent e) {
		if ((e.getSimulationTime() + 1) % chargeTimeStep == 0) {
			for(ElectricVehicle ev:this.Ev.getElectricVehicles().values()) {
//				if(ev.getBattery().getSoc()<=0) {
//					//PersonMoneyEvent ee = new PersonMoneyEvent(e.getSimulationTime(), vehicleOwners.get(ev.getId()), -1000, "punnishment", "EVOut");
//					PersonMoneyEvent ee = new PersonMoneyEvent(e.getSimulationTime(), Id.createPersonId(ev.getId().toString()), -5000.00, "punnishment", "EVOut");
//					manager.processEvent(ee);
//				}
				double rangeAnxiety = this.rangeAnxietyCoefficientA*(Math.exp(this.rangeAnxietyCoefficientB*(ev.getBattery().getSoc()/ev.getBattery().getCapacity()-1))-1);
				PersonMoneyEvent ee = new PersonMoneyEvent(e.getSimulationTime(), Id.createPersonId(ev.getId().toString()), rangeAnxiety, "range anxiety", "EVOut");
				manager.processEvent(ee);
			}
		}
	}

//	@Override
//	public void handleEvent(PersonEntersVehicleEvent event) {
//		Id<ElectricVehicle> ev;
//		if(this.Ev.getElectricVehicles().containsKey(Id.create(event.getVehicleId().toString(), ElectricVehicle.class))) {
//			vehicleOwners.put(Id.create(event.getVehicleId().toString(), ElectricVehicle.class), event.getPersonId());
//		}
//	}

//	@Override
//	public void handleEvent(PersonLeavesVehicleEvent event) {
////		Id<ElectricVehicle> ev = Id.create(event.getVehicleId().toString(),ElectricVehicle.class);
////		if(this.vehicleOwners.containsKey(ev))this.vehicleOwners.remove(ev);
//	}
}
