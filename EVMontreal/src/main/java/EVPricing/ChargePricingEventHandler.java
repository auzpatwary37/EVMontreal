package EVPricing;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.events.LinkLeaveEvent;
import org.matsim.api.core.v01.events.PersonEntersVehicleEvent;
import org.matsim.api.core.v01.events.PersonLeavesVehicleEvent;
import org.matsim.api.core.v01.events.PersonMoneyEvent;
import org.matsim.api.core.v01.events.TransitDriverStartsEvent;
import org.matsim.api.core.v01.events.VehicleEntersTrafficEvent;
import org.matsim.api.core.v01.events.VehicleLeavesTrafficEvent;
import org.matsim.api.core.v01.events.handler.LinkLeaveEventHandler;
import org.matsim.api.core.v01.events.handler.PersonEntersVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.PersonLeavesVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.TransitDriverStartsEventHandler;
import org.matsim.api.core.v01.events.handler.VehicleEntersTrafficEventHandler;
import org.matsim.api.core.v01.events.handler.VehicleLeavesTrafficEventHandler;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.contrib.ev.EvConfigGroup;
import org.matsim.contrib.ev.charging.ChargingEndEvent;
import org.matsim.contrib.ev.charging.ChargingEndEventHandler;
import org.matsim.contrib.ev.charging.ChargingStartEvent;
import org.matsim.contrib.ev.charging.ChargingStartEventHandler;
import org.matsim.contrib.ev.fleet.ElectricFleet;
import org.matsim.contrib.ev.fleet.ElectricFleetSpecification;
import org.matsim.contrib.ev.fleet.ElectricVehicle;
import org.matsim.contrib.ev.infrastructure.Charger;
import org.matsim.contrib.ev.infrastructure.ChargingInfrastructureSpecification;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.controler.MatsimServices;
import org.matsim.core.events.MobsimScopeEventHandler;
import org.matsim.core.mobsim.framework.events.MobsimAfterSimStepEvent;
import org.matsim.core.mobsim.framework.events.MobsimBeforeSimStepEvent;
import org.matsim.core.mobsim.framework.listeners.MobsimAfterSimStepListener;
import org.matsim.core.mobsim.framework.listeners.MobsimBeforeSimStepListener;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleUtils;

import com.google.inject.Inject;

import urbanEV.UrbanEVConfigGroup;
import urbanEV.UrbanEVConfigGroup.PricingLogic;


/**
 * 
 * @author ashrafzaman
 *
 *The class assumes that the owner of the EV pays for the charging. There is no distance based money cost for car
 *The gasoline cost is distributed among users based on distance. 
 *
 *For the EV, a car passenger will not pay any money for the distance traveled. Only the owner of the car will pay.
 *
 */


public class ChargePricingEventHandler implements ChargingStartEventHandler, ChargingEndEventHandler, LinkLeaveEventHandler,TransitDriverStartsEventHandler,
PersonEntersVehicleEventHandler, PersonLeavesVehicleEventHandler,VehicleEntersTrafficEventHandler,VehicleLeavesTrafficEventHandler, MobsimScopeEventHandler, MobsimAfterSimStepListener,MobsimBeforeSimStepListener{

	

	private Set<Event> queuedEvents = new HashSet<>();
	private ChargingInfrastructureSpecification chargingInfrastructure;
	
	
	@Inject
	private ChargerPricingProfiles pricingProfies;
	
	@Inject
	private ElectricFleetSpecification vehicleSpecifications;
	
	@Inject
	private UrbanEVConfigGroup urbanEv;
	
	private ElectricFleet fleet;
	
	private Set<Id<Vehicle>> transitVehicles = new HashSet<>();
	private Scenario scenario;
	
	public final String ChargingCostName = "EV charging";
	public final String gasMoneyString = "Gas Money";
	
	private EventsManager events;
	
	public final Map<Id<ElectricVehicle>,Id<Person>> personIdForEV;
	
	public Map<Id<Vehicle>,Set<Id<Person>>> vehicleToPersonMapping = new ConcurrentHashMap<>(); 
	public Map<Id<Vehicle>,Double> vehicleToDistance = new ConcurrentHashMap<>();
	
	public double gasMoneyCostPer_m = 2.0E-4;
	
	private Map<String,chargingDetails> personLists = new ConcurrentHashMap<>();// the price has to be per kW
	private Map<String,Double> price = new HashMap<>();//Charger type to price 
	//private Map<String,Set<Id<Person>>> vehicleToPersonMapping = new HashMap<>();
	
	private FileWriter fw;
	
	@Inject
	ChargePricingEventHandler(final MatsimServices controler,ChargingInfrastructureSpecification chargingInfrastructure, ElectricFleet data) {
		this.scenario = controler.getScenario();
		scenario.getPopulation().getPersons().values().forEach(p->{
			Id<Vehicle> vId= VehicleUtils.getVehicleId(p, TransportMode.car);
			this.vehicleToPersonMapping.put(vId, new HashSet<>());
			this.vehicleToPersonMapping.get(vId).add(p.getId());
		});
		events = controler.getEvents();
		this.fleet = data;
		this.chargingInfrastructure = chargingInfrastructure;
		price.put("Level 1", 0.28);
		price.put("Level 2", 0.58);
		price.put("Fast", 0.78);
		personIdForEV = new HashMap<>();
		controler.getScenario().getPopulation().getPersons().values().forEach(p->{
			VehicleUtils.getVehicleIds(p).values().forEach(v->{
				Id<ElectricVehicle> eId = Id.create(v.toString(), ElectricVehicle.class);
				if(fleet.getElectricVehicles().keySet().contains(eId)) {
					this.personIdForEV.put(eId, p.getId());
				}
			});
		});
		
		try {
			fw = new FileWriter(new File(controler.getControlerIO().getIterationFilename(controler.getIterationNumber(), "charging.csv")));
			fw.append("pId,chargerId,startingTime,EndingTime,duration,initialSOC,charge,finalSoC,cost\n");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	@Override
	public void reset(int iteration) {
//		this.vehicleToPersonMapping.clear();
		this.vehicleToDistance.clear();
		this.personLists.clear();
		
	}

	@Override
	public void handleEvent(ChargingEndEvent event) {

		Id<Person> pId = this.personIdForEV.get(event.getVehicleId());
		Plan plan = this.scenario.getPopulation().getPersons().get(pId).getSelectedPlan();
		chargingDetails cd = this.personLists.get(pId.toString());
		//TODO: maybe better to update the final state of charge in the cd when the charging ends??? Ashraf June 2024. 
		cd.endingTime = event.getTime();
//		String chargerType = this.chargingInfrastructure.getChargerSpecifications().get(cd.charger).getChargerType();
//		double pricePerkWhr = this.price.get(chargerType);
//		Double juleCharged= cd.v.getBattery().getSoc()-cd.initialSoc;//warning: unit Conversion
//		Double cost = pricePerkWhr*juleCharged/3600000;
//		this.events.processEvent(new PersonMoneyEvent(event.getTime(), pId, cost*-1, this.ChargingCostName, cd.charger.toString()+"___"+chargerType));
		this.chargePeopleForElectricity(cd, event.getTime());
		this.personLists.remove(pId.toString());
		this.scenario.getPopulation().getPersons().get(Id.createPersonId(event.getVehicleId().toString())).getAttributes().putAttribute("chargingIndicator", false);
		}

	/**
	 * the personId is assumed same as vehicleId
	 */
	@Override
	public void handleEvent(ChargingStartEvent event) {
		personLists.put(event.getVehicleId().toString(), new chargingDetails(event.getChargerId(),event.getTime(),this.fleet.getElectricVehicles().get(event.getVehicleId()),this.pricingProfies.getChargerPricingProfiles().get(event.getChargerId()).getPricingStepsSize()));
		this.scenario.getPopulation().getPersons().get(Id.createPersonId(event.getVehicleId().toString())).getAttributes().putAttribute("chargingIndicator", true);
	}
	@Override
	public void handleEvent(LinkLeaveEvent event) {
		if(!this.fleet.getElectricVehicles().containsKey(Id.create(event.getVehicleId().toString(),ElectricVehicle.class)) && !this.transitVehicles.contains(event.getVehicleId())){
			double d = this.scenario.getNetwork().getLinks().get(event.getLinkId()).getLength();
			this.vehicleToDistance.compute(event.getVehicleId(), (k,v)->v+d);
		}
	}

	@Override
	public void handleEvent(PersonEntersVehicleEvent event) {
		if(!this.transitVehicles.contains(event.getVehicleId())) {
			if(!this.vehicleToPersonMapping.containsKey(event.getVehicleId()))this.vehicleToPersonMapping.put(event.getVehicleId(), new HashSet<>());
			if(!this.fleet.getElectricVehicles().keySet().contains(Id.create(event.getVehicleId().toString(),ElectricVehicle.class)) && this.vehicleToDistance.containsKey(event.getVehicleId())) {this.chargePeopleForGasoline(event.getVehicleId(), event.getTime());}
			this.vehicleToPersonMapping.get(event.getVehicleId()).add(event.getPersonId());
			
			// if the vehicle did not have anyone
		}
		
		
	}

	@Override
	public void handleEvent(PersonLeavesVehicleEvent event) {
		// TODO Auto-generated method stub
		if(!this.transitVehicles.contains(event.getVehicleId())) {
			if(!this.fleet.getElectricVehicles().keySet().contains(Id.create(event.getVehicleId().toString(),ElectricVehicle.class))
					&& this.vehicleToDistance.containsKey(event.getVehicleId())) {
				this.chargePeopleForGasoline(event.getVehicleId(), event.getTime());
			
			}
			//this.vehicleToPersonMapping.get(event.getVehicleId()).remove(event.getPersonId());
		}
	}
	
	

	synchronized void chargePeopleForGasoline(Id<Vehicle> vId, double time) {
		if(!this.vehicleToPersonMapping.get(vId).isEmpty()){
			double distance = this.vehicleToDistance.get(vId);
			double cost = this.gasMoneyCostPer_m*distance;
			double indCost = cost/this.vehicleToPersonMapping.get(vId).size();
			for(Id<Person> pId :this.vehicleToPersonMapping.get(vId)) {
				this.events.processEvent(new PersonMoneyEvent(time, pId, indCost, this.gasMoneyString,  vId.toString()));
			}
			
		}
	}
	
	public synchronized void addQueuedEvenet(Event event) {
        this.queuedEvents.add(event);
    }

	public synchronized void cleanQueuedEvenet() {
        this.queuedEvents.clear();
    }
	/**
	 * 
	 * @param cd
	 * @param time
	 */
	@SuppressWarnings("deprecation")
	void chargePeopleForElectricity(chargingDetails cd,double time) {
		Id<Vehicle> vId = Id.createVehicleId(cd.v.getId().toString());
		
			int timeId = (int)(cd.startingTime/3600);
			if(timeId>23)timeId = timeId-24;
			//double[] pricingProfile = this.pricingProfies.getChargerPricingProfiles().get(cd.charger).getPricingProfile().get(timeId);
			
			PricingLogic  pricingLogic = urbanEv.getPricingLogic();
			
			
			double cost = 0;
			
			if(pricingLogic.equals(PricingLogic.TIME_BASED)) {
				cost = getCostBasedOnTimeUsage(cd,this.pricingProfies.getChargerPricingProfiles().get(cd.charger),timeId);
			}else if(pricingLogic.equals(PricingLogic.USAGE_BASED)) {
				cost = getCostBasedOnKwUsage(cd,this.pricingProfies.getChargerPricingProfiles().get(cd.charger),timeId);
			}else if(pricingLogic.equals(PricingLogic.COMBINED)) {
				cost = getCostBasedOnTimeAndKwUsageMax(cd,this.pricingProfies.getChargerPricingProfiles().get(cd.charger),timeId);
			}else {
				cost = getCostBasedOnTimeUsage(cd,this.pricingProfies.getChargerPricingProfiles().get(cd.charger),timeId);
			}
			
//			double charge = cd.initialSoc;
//			for(int i = 0; i<pricingProfile.length;i++) {
//				//System.out.println((cd.chargeDetails[i]-charge)*2.78e-7);
//				if(cd.chargeDetails[i]>0)cost+=pricingProfile[i]*(cd.chargeDetails[i]-charge)*2.78e-7;
//				else break;
//				charge=cd.chargeDetails[i];
//				cd.finalSoC = charge;
//			}
			
			writeDetails(cd,cost,vId);
//			if((cd.endingTime-cd.startingTime)<599){
//				System.out.println("Error");
//			}
			
//			Person person = scenario.getPopulation().getPersons().get(this.personIdForEV.get(cd.v.getId()));
			this.addQueuedEvenet(new PersonMoneyEvent(time, this.personIdForEV.get(cd.v.getId()), -1*cost, this.ChargingCostName,  cd.charger.toString()+"___"+vId.toString()));
			
			//System.out.println();
	}
	
	
	public static double getCostBasedOnKwUsage(chargingDetails cd,ChargerPricingProfile cpf, int timeId) {
		double[] pricingProfile = cpf.getPricingProfile().get(timeId);
		double cost = 0;
		double charge = cd.initialSoc;
		for(int i = 0; i<pricingProfile.length;i++) {
			//System.out.println((cd.chargeDetails[i]-charge)*2.78e-7);
			if(cd.chargeDetails[i]>0)cost+=pricingProfile[i]*(cd.chargeDetails[i]-charge)*2.78e-7;
			else break;
			charge=cd.chargeDetails[i];
			cd.finalSoC = charge;
		}
		return cost;
	}
	
	public static double getCostBasedOnTimeUsage(chargingDetails cd,ChargerPricingProfile cpf, int timeId) {
		double[] pricingProfile = cpf.getPricingProfilePerHr().get(timeId);
		double cost = 0;
		double duration = cd.endingTime-cd.startingTime;
		//double charge = cd.initialSoc;
		for(int i = 0; i<pricingProfile.length;i++) {
			if(duration >= cpf.getProfileTimeStepInMin()*60) {
				cost+=pricingProfile[i]*cpf.getProfileTimeStepInMin()*60/3600;
			}else {
				cost+=duration*pricingProfile[i]/3600;
				break;
			}
			duration = duration-cpf.getProfileTimeStepInMin()*60;
		}
		return cost;
	}
	
	public static double getCostBasedOnTimeAndKwUsageMax(chargingDetails cd,ChargerPricingProfile cpf, int timeId) {
		return Math.max(getCostBasedOnKwUsage(cd,cpf,timeId), getCostBasedOnTimeUsage(cd,cpf,timeId));
	}
	
	
	public synchronized void writeDetails(chargingDetails cd, double cost, Id<Vehicle>vId) {
		try {
			fw.append(this.vehicleToPersonMapping.get(vId).toString()+","+cd.toString()+","+ cost+"\n");
			fw.flush();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public void handleEvent(VehicleLeavesTrafficEvent event) {
		this.vehicleToDistance.remove(event.getVehicleId());
	}

	@Override
	public void handleEvent(VehicleEntersTrafficEvent event) {
		this.vehicleToDistance.put(event.getVehicleId(), 0.);
		
	}

	@Override
	public void handleEvent(TransitDriverStartsEvent event) {
		transitVehicles.add(event.getVehicleId());
	}
	@Override
	public void cleanupAfterMobsim(int iteration) {
		
	}

	@Override
	public void notifyMobsimAfterSimStep(MobsimAfterSimStepEvent e) {
		if((e.getSimulationTime()+1)%((EvConfigGroup)this.scenario.getConfig().getModules().get("ev")).chargeTimeStep==0) {
			for(chargingDetails cd:this.personLists.values()) {
				int o = (int)(Math.floor(e.getSimulationTime()-cd.startingTime)/(this.pricingProfies.getChargerPricingProfiles().get(cd.charger).getProfileTimeStepInMin()*60));
				if(o>=cd.chargeDetails.length)o = cd.chargeDetails.length-1;
				cd.chargeDetails[o] = cd.v.getBattery().getCharge();
			}
		}
		
	}

	@Override
	public synchronized void notifyMobsimBeforeSimStep(MobsimBeforeSimStepEvent e) {
		if(!this.queuedEvents.isEmpty()) {
			this.queuedEvents.stream().forEach(ee->{
				ee.setTime(e.getSimulationTime());
				this.events.processEvent(ee);
			});
			this.cleanQueuedEvenet();
		}
		
	}
	
	
	
}

class chargingDetails{
	public static String arrayToString(double[] a) {
		String out = "";
		String sep = "";
		for(double d:a) {
			out=out+sep+Double.toString(d);
			sep = " ";
		}
		
		return out;
	}

	public final Id<Charger> charger;
	public final double startingTime;
	public double endingTime;
	public ElectricVehicle v;
	public double initialSoc = 0;
	public double[] chargeDetails;
	public double finalSoC = 0;
	
	
	public chargingDetails(Id<Charger> chargerId, double startingTime, ElectricVehicle v, int pricingStepSize) {
		this.charger = chargerId;
		this.startingTime =startingTime;
		this.v =  v;
		this.initialSoc = v.getBattery().getCharge();
		chargeDetails = new double[pricingStepSize];
	}
	@Override
	public String toString() {
//		this.finalSoC = this.initialSoc+(this.chargeDetails[0]+this.chargeDetails[1]+this.chargeDetails[2]);
		return this.charger+","+this.startingTime/3600+","+this.endingTime/3600+","+(this.endingTime-this.startingTime)+","+this.initialSoc+","+arrayToString(this.chargeDetails)+","+this.finalSoC;
	}
	
}
