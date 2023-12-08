package experienceSOC;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.handler.ActivityStartEventHandler;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contrib.ev.fleet.ElectricFleet;
import org.matsim.contrib.ev.fleet.ElectricVehicle;
import org.matsim.core.events.MobsimScopeEventHandler;
import org.matsim.core.mobsim.framework.events.MobsimBeforeCleanupEvent;
import org.matsim.core.mobsim.framework.listeners.MobsimBeforeCleanupListener;

import com.google.inject.Inject;

public class ActivitySoc implements ActivityStartEventHandler,MobsimScopeEventHandler,MobsimBeforeCleanupListener{
	
	
	private Scenario scenario;
	
	private ElectricFleet electricFleet;
	
	@Inject
	public ActivitySoc(Scenario scenario, ElectricFleet ef) {
		this.scenario = scenario;
		this.electricFleet = ef;
		for(Person p:scenario.getPopulation().getPersons().values()) {
			Id<ElectricVehicle> eId = Id.create(p.getId().toString(), ElectricVehicle.class);
			if(this.electricFleet.getElectricVehicles().containsKey(eId)) {
				ElectricVehicle ev = this.electricFleet.getElectricVehicles().get(eId);
				//((Activity)p.getSelectedPlan().getPlanElements().get(0)).getAttributes().putAttribute(actSOCAttributeName, ev.getBattery().getSoc()/ev.getBattery().getCapacity());
				actOrder.put(p.getId(), 0);
				if(p.getSelectedPlan().getAttributes().getAttribute("actSOC")==null)p.getSelectedPlan().getAttributes().putAttribute("actSOC", new HashMap<Integer,Double>());
				((Map<Integer,Double>)p.getSelectedPlan().getAttributes().getAttribute("actSOC")).put(0, ev.getBattery().getSoc()/ev.getBattery().getCapacity());
				
			}
		}
		
	}
	
	public static final String actSOCAttributeName = "actSOC";
	
	private Map<Id<Person>,Integer> actOrder = new ConcurrentHashMap<>();

	@Override
	public void handleEvent(ActivityStartEvent event) {
		Plan plan = this.scenario.getPopulation().getPersons().get(event.getPersonId()).getSelectedPlan();
		
		
		Id<ElectricVehicle> eId = Id.create(event.getPersonId().toString(), ElectricVehicle.class);
		
		if(this.electricFleet.getElectricVehicles().get(eId)!=null) {
			if(actOrder.containsKey(event.getPersonId())) {
				int current = actOrder.get(event.getPersonId());
				actOrder.put(event.getPersonId(), current+2);
			}else {
				actOrder.put(event.getPersonId(), 0);
			}
			ElectricVehicle ev = this.electricFleet.getElectricVehicles().get(eId);
			Activity act = (Activity)this.scenario.getPopulation().getPersons().get(event.getPersonId()).getSelectedPlan().getPlanElements().get(actOrder.get(event.getPersonId()));
			//act.getAttributes().putAttribute(actSOCAttributeName,ev.getBattery().getSoc()/ev.getBattery().getCapacity());
			if(plan.getAttributes().getAttribute("actSOC")==null)plan.getAttributes().putAttribute("actSOC", new HashMap<Integer,Double>());
			((Map<Integer,Double>)plan.getAttributes().getAttribute("actSOC")).put(actOrder.get(event.getPersonId()), ev.getBattery().getSoc()/ev.getBattery().getCapacity());
		}
		
	}
	@Override
	public void reset(int i) {
		actOrder.clear();
	}
	@Override
	public void notifyMobsimBeforeCleanup(MobsimBeforeCleanupEvent e) {
		
		for(Person p:scenario.getPopulation().getPersons().values()) {
			Id<ElectricVehicle> eId = Id.create(p.getId().toString(), ElectricVehicle.class);
			if(this.electricFleet.getElectricVehicles().containsKey(eId)) {
				ElectricVehicle ev = this.electricFleet.getElectricVehicles().get(eId);
				int actOrder = 0;
				for(PlanElement pl:p.getSelectedPlan().getPlanElements()){
					if(pl instanceof Activity && ((Activity)pl).getStartTime().seconds()>this.scenario.getConfig().qsim().getEndTime().seconds()) {
						//((Activity)pl).getAttributes().putAttribute("actSOC", ev.getBattery().getSoc()/ev.getBattery().getCapacity());
						if(p.getSelectedPlan().getAttributes().getAttribute("actSOC")==null)p.getSelectedPlan().getAttributes().putAttribute("actSOC", new HashMap<Integer,Double>());
						if(!((Map<Integer,Double>)p.getSelectedPlan().getAttributes().getAttribute("actSOC")).containsKey(actOrder))((Map<Integer,Double>)p.getSelectedPlan().getAttributes().getAttribute("actSOC")).put(actOrder, ev.getBattery().getSoc()/ev.getBattery().getCapacity());
						
					}
					actOrder++;
				}
				
			}
		}
	}
	
	

}
