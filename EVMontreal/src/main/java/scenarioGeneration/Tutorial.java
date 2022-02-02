package org.matsim.run;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.contrib.ev.fleet.ElectricFleetSpecification;
import org.matsim.contrib.ev.fleet.ElectricFleetSpecificationImpl;
import org.matsim.contrib.ev.fleet.ElectricFleetWriter;
import org.matsim.contrib.ev.fleet.ElectricVehicle;
import org.matsim.contrib.ev.fleet.ElectricVehicleSpecification;
import org.matsim.contrib.ev.fleet.ImmutableElectricVehicleSpecification;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.vehicles.EngineInformation;
import org.matsim.vehicles.MatsimVehicleWriter;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;
import org.matsim.vehicles.Vehicles;
import org.matsim.vehicles.VehiclesFactory;

import com.google.common.collect.ImmutableList;

public class Tutorial {
	public static void main(String[] args) {
		Config config = ConfigUtils.createConfig();
		double evPercentage = 0.1;
		config.plans().setInputFile("C:\\Users\\arsha\\git\\matsim-berlin\\prepared_population.xml.gz");
		Scenario scenario = ScenarioUtils.loadScenario(config);
		Vehicles vs = scenario.getVehicles();
		VehiclesFactory vf = vs.getFactory();
		//v.getVehicleTypes()
		VehicleType ev1 = vf.createVehicleType(Id.create("ev1", VehicleType.class));
		VehicleUtils.setHbefaTechnology(ev1.getEngineInformation(),"electricity");
		vs.addVehicleType(ev1);
		VehicleType noEV = vf.createVehicleType(Id.create("nonEv", VehicleType.class));
		VehicleUtils.setHbefaTechnology(ev1.getEngineInformation(),EngineInformation.FuelType.gasoline.toString());
		vs.addVehicleType(noEV);
		ElectricFleetSpecification sp = new ElectricFleetSpecificationImpl();
		Random random = new Random();
		scenario.getPopulation().getPersons().entrySet().forEach(p->{
//		PersonUtils.setCarAvail(p.getValue(),"always");
//		PersonUtils.getCarAvail(p.getValue());
			String cc = (String) p.getValue().getAttributes().getAttribute("carAvail");
			if(cc.equals("always")) {
				if(Math.random()<=evPercentage) {
					Vehicle v = vf.createVehicle(Id.createVehicleId(p.getKey().toString()), ev1);
					Map<Id<Vehicle>,Vehicle> vMap = new HashMap<>();
					vMap.put(v.getId(), v);
					vs.addVehicle(v);
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
					Vehicle v = vf.createVehicle(Id.createVehicleId(p.getKey().toString()), noEV);
					Map<Id<Vehicle>,Vehicle> vMap = new HashMap<>();
					vMap.put(v.getId(), v);
					vs.addVehicle(v);
				}
			}
		});

		new PopulationWriter(scenario.getPopulation()).write("C:\\Users\\arsha\\git\\matsim-berlin\\vehicleInsertedPopulation.xml");
		new MatsimVehicleWriter(vs).writeFile("C:\\Users\\arsha\\git\\matsim-berlin\\10PEV.xml");


		new ElectricFleetWriter(sp.getVehicleSpecifications().values().stream()).write("C:\\Users\\arsha\\git\\matsim-berlin\\ElectricVehicle.xml");









//	config.addModule(new UrbanEVConfigGroup());
//	
//	UrbanEVConfigGroup evCon = (UrbanEVConfigGroup)config.getModule("urbanEV");
//	
//	config.addModule(new EvConfigGroup());
//	EvConfigGroup ev = (EvConfigGroup)config.getModules().get("ev");
//	
//	ev.setVehiclesFile("");//vehicle file
//	ev.setChargersFile("");//Charger File
//	
//	scenario.ad
	}
}
