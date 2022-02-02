package scenarioGeneration;

import java.lang.ModuleLayer.Controller;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.contrib.ev.EvConfigGroup;
import org.matsim.contrib.ev.EvModule;
import org.matsim.contrib.ev.fleet.ElectricFleetSpecification;
import org.matsim.contrib.ev.fleet.ElectricFleetSpecificationImpl;
import org.matsim.contrib.ev.fleet.ElectricFleetWriter;
import org.matsim.contrib.ev.fleet.ElectricVehicle;
import org.matsim.contrib.ev.fleet.ElectricVehicleSpecification;
import org.matsim.contrib.ev.fleet.ImmutableElectricVehicleSpecification;
import org.matsim.contrib.ev.infrastructure.Charger;
import org.matsim.contrib.ev.infrastructure.ChargerSpecification;
import org.matsim.contrib.ev.infrastructure.ChargerWriter;
import org.matsim.contrib.ev.infrastructure.ChargingInfrastructureSpecification;
import org.matsim.contrib.ev.infrastructure.ChargingInfrastructureSpecificationImpl;
import org.matsim.contrib.ev.infrastructure.ImmutableChargerSpecification;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.ConfigWriter;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup;
import org.matsim.core.controler.Controler;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.vehicles.EngineInformation;
import org.matsim.vehicles.MatsimVehicleWriter;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;
import org.matsim.vehicles.Vehicles;
import org.matsim.vehicles.VehiclesFactory;

import com.google.common.collect.ImmutableList;

import urbanEV.UrbanEVConfigGroup;
import urbanEV.UrbanEVModule;
import urbanEV.UrbanVehicleChargingHandler;

public class Tutorial {
	public static void main(String[] args) {
		
		//Inputs
		double evPercentage = 0.1;
		
		
		String configIn = "";
		String planInput = "";
		String networkInput = "";
		
		String planOutput = "";
		String vehicleOutput = "";
		
		String chargerOutput = "";
		String evVehicleOutput = "";
		String configOut = "";
		
		//____________________________________________________
		
		
		
		Config config = ConfigUtils.createConfig();
		ConfigUtils.loadConfig(config,configIn);
		config.plans().setInputFile(planInput);
		
		Scenario scenario = ScenarioUtils.loadScenario(config);
		
		Vehicles vs = scenario.getVehicles();
		VehiclesFactory vf = vs.getFactory();
		
		//Create a vehicleType with ev
		VehicleType ev1 = vf.createVehicleType(Id.create("ev1", VehicleType.class));
		VehicleUtils.setHbefaTechnology(ev1.getEngineInformation(),"electricity");
		vs.addVehicleType(ev1);
		
		//Create a vehicleType without ev
		VehicleType noEV = vf.createVehicleType(Id.create("nonEv", VehicleType.class));
		VehicleUtils.setHbefaTechnology(ev1.getEngineInformation(),EngineInformation.FuelType.gasoline.toString());
		vs.addVehicleType(noEV);
		
		//Crate container for electric vehicle specification
		ElectricFleetSpecification sp = new ElectricFleetSpecificationImpl();
		
		
		Random random = new Random();
		scenario.getPopulation().getPersons().entrySet().forEach(p->{

//		PersonUtils.setCarAvail(p.getValue(),"always"); requested by rena Feb 1, 2022.
//		PersonUtils.getCarAvail(p.getValue());
			
			
			String cc = (String) p.getValue().getAttributes().getAttribute("carAvail");
			
			if(cc.equals("always")) {// have cars
				if(Math.random()<=evPercentage) {
					Vehicle v = vf.createVehicle(Id.createVehicleId(p.getKey().toString()), ev1);// create ev vehicle in the vehicles file.
					Map<Id<Vehicle>,Vehicle> vMap = new HashMap<>();
					vMap.put(v.getId(), v);
					vs.addVehicle(v);
					
					//Create vehicle in the ElectricVehicle file
					Double b = (30+(60-30)*random.nextDouble())*36e5;
					Double c = 20*36e5+(b-20*36e5)*random.nextDouble();
					ElectricVehicleSpecification s = ImmutableElectricVehicleSpecification.newBuilder()
							.id(Id.create(p.getKey().toString(), ElectricVehicle.class))
							.batteryCapacity(b.intValue())
							.initialSoc(c.intValue())
							.chargerTypes(ImmutableList.of("default"))
							.vehicleType(ev1.getId().toString())
							.build();

					sp.addVehicleSpecification(s);
				}else {
					Vehicle v = vf.createVehicle(Id.createVehicleId(p.getKey().toString()), noEV);// Create a non EV vehicle
					Map<Id<Vehicle>,Vehicle> vMap = new HashMap<>();
					vMap.put(v.getId(), v);
					vs.addVehicle(v);
				}
			}
		});
		
		ChargingInfrastructureSpecification csp = new ChargingInfrastructureSpecificationImpl();// Create container for charger specification
		
		Network net = NetworkUtils.readNetwork(networkInput); // read network
		
		//put the logic for selecting charger links and put the charger creation in a loop. One is demonstrated
		
		//scenario.getActivityFacilities().getFacilitiesForActivityType(null) It is possible to use activity facilities as well
		ChargerSpecification c = ImmutableChargerSpecification.newBuilder()
				.id(Id.create("ChargerId",Charger.class))
				.linkId(Id.createLinkId(""))
				.chargerType("default")
				.plugCount(3)
				.plugPower(3600000*220)
				.build();
		
		csp.addChargerSpecification(c);
		
		

		
		UrbanEVConfigGroup evReplanningCfg = new UrbanEVConfigGroup(); // create the urbanEV config group
		config.addModule(evReplanningCfg);

		//TODO actually, should also work with all AccessEgressTypes but we have to check (write JUnit test)
		config.plansCalcRoute().setAccessEgressType(PlansCalcRouteConfigGroup.AccessEgressType.walkConstantTimeToLink);

		//register charging interaction activities for car
		config.planCalcScore().addActivityParams(
				new PlanCalcScoreConfigGroup.ActivityParams(TransportMode.car + UrbanVehicleChargingHandler.PLUGOUT_INTERACTION)
						.setScoringThisActivityAtAll(false));
		config.planCalcScore().addActivityParams(
				new PlanCalcScoreConfigGroup.ActivityParams(TransportMode.car + UrbanVehicleChargingHandler.PLUGIN_INTERACTION)
						.setScoringThisActivityAtAll(false));


		new ChargerWriter(csp.getChargerSpecifications().values().stream()).write(chargerOutput);
		new PopulationWriter(scenario.getPopulation()).write(planOutput);
		new MatsimVehicleWriter(vs).writeFile(vehicleOutput);


		new ElectricFleetWriter(sp.getVehicleSpecifications().values().stream()).write(evVehicleOutput);
		
		EvConfigGroup evgroup = new EvConfigGroup();
		evgroup.setChargersFile(chargerOutput);
		evgroup.setVehiclesFile(evVehicleOutput);
		
		config.plans().setInputFile(planOutput);
		
		config.vehicles().setVehiclesFile(vehicleOutput);
		
		config.addModule(evgroup);
		
		
		new ConfigWriter(config).write(configOut);


	}
	/**
	 * Use this function after creating the controller before running urban ev. 
	 * @param controler
	 * @return
	 */
	public static Controler createUrbanEVController(Controler controler) {
		
		//plug in UrbanEVModule
		controler.addOverridingModule(new UrbanEVModule());
		//register EV qsim components
		controler.configureQSimComponents(components -> components.addNamedComponent(EvModule.EV_COMPONENT));
		return controler;
	}
}
