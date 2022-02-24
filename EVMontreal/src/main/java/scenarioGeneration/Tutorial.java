package scenarioGeneration;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.contrib.ev.EvConfigGroup;
import org.matsim.contrib.ev.EvModule;
import org.matsim.contrib.ev.charging.VehicleChargingHandler;
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
import org.matsim.contrib.ev.routing.EvNetworkRoutingProvider;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.ConfigWriter;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.mobsim.qsim.AbstractQSimModule;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.vehicles.EngineInformation;
import org.matsim.vehicles.EngineInformation.FuelType;
import org.matsim.vehicles.MatsimVehicleWriter;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;
import org.matsim.vehicles.Vehicles;
import org.matsim.vehicles.VehiclesFactory;

import com.google.common.collect.ImmutableList;

import EVPricing.ChargePricingEventHandler;
import urbanEV.EVUtils;
import urbanEV.UrbanEVConfigGroup;
import urbanEV.UrbanEVModule;
import urbanEV.UrbanVehicleChargingHandler;

//import org.matsim.urbanEV.UrbanEVConfigGroup;
//import org.matsim.urbanEV.UrbanEVModule;
//import org.matsim.urbanEV.UrbanVehicleChargingHandler;
/**
 *
 * @author Ashraf
 *
 */
public class Tutorial {
	public static void main(String[] args) {

		//Inputs
		double evPercentage = 0.1; // Percentage of cars to take as EV
		boolean assignChargersToEveryone = true;

		String configIn = "config_with_calibrated_parameters.xml";// input MATSim Montreal Config without ev
		String planInput = "prepared_population.xml.gz";// Population file without EV
		String networkInput = "montreal_network.xml.gz";// Input Network File
		String chargerFileInput = "cleaned_station.csv";//Charger file with exactly same headers as given to me by Arsham but in .csv format. Save the excel as csv and input its file location

		String planOutput = "Output/plan.xml"; // Saving location of the EV included population
		String vehicleOutput = "Output/vehicle.xml"; // Vehicle xml file write location

		String chargerOutput = "Output/charger.xml"; // charger xml file write location
		String evVehicleOutput = "Output/evehicle.xml";// ev vehicle file write location
		String configOut = "Output/config.xml"; // Config out file write location. The charger file, vehicle file, plan file, ev vehicle file locations are already set in the config as the out location.
		String resultOut = "Output";

		double BatteryCapMin = 30; // Min Battery capacity
		double BatteryCapMax = 50;// Max Battery Capacity
		//put the min and max same to make capacity non random

		double socMIn = 20; // Min initial soc level.
		double chargeAtStartOfDayAboveSocMin = 5;

		//ChargerTypes and power

		Map<String,Double> cp = new HashMap<>();
		cp.put("Level 1", 50.);
		cp.put("Level 2", 70.);
		cp.put("Fast", 100.);
		cp.put("home",11.5);

		//____________________________________________________
		
		


		Config config = ConfigUtils.createConfig();
		config.network().setInputFile(resultOut);
		ConfigUtils.loadConfig(config,configIn);
		config.plans().setInputFile(planInput);

		Scenario scenario = ScenarioUtils.loadScenario(config);
		checkPlanConsistancy(scenario.getPopulation());
		Vehicles vs = scenario.getVehicles();
		VehiclesFactory vf = vs.getFactory();

		//Create a vehicleType with ev
		VehicleType ev1 = vf.createVehicleType(Id.create("ev1", VehicleType.class));
		VehicleUtils.setHbefaTechnology(ev1.getEngineInformation(),"electricity");
		vs.addVehicleType(ev1);
		EVUtils.setChargerTypes(ev1.getEngineInformation(), ImmutableList.copyOf(cp.keySet()));
		EVUtils.setInitialEnergy(ev1.getEngineInformation(), socMIn+chargeAtStartOfDayAboveSocMin);

		//Create a vehicleType without ev
		VehicleType noEV = vf.createVehicleType(Id.create("nonEv", VehicleType.class));
		VehicleUtils.setHbefaTechnology(ev1.getEngineInformation(),EngineInformation.FuelType.gasoline.toString());
		vs.addVehicleType(noEV);

		//Crate container for electric vehicle specification
		

		ChargingInfrastructureSpecification csp = new ChargingInfrastructureSpecificationImpl();// Create container for charger specification
		Set<Id<Link>> chargerLinkIds = new HashSet<>();
		Network net = NetworkUtils.readNetwork(networkInput); // read network
		
		Map<Id<Link>, ? extends Link>linkSet = new HashMap<>(net.getLinks());
		linkSet.entrySet().stream().forEach(l->{
			if(!l.getValue().getAllowedModes().contains("car") && !l.getValue().getAllowedModes().contains("car_passenger"))net.removeLink(l.getKey());
		});
		Map<Id<Node>, ? extends Node>nodeSet = new HashMap<>(net.getNodes());
		nodeSet.entrySet().stream().forEach(n->{
			if(n.getValue().getInLinks().size()==0 && n.getValue().getOutLinks().size()==0)net.removeNode(n.getKey());
		});
		

		CoordinateTransformation tsf = TransformationFactory.getCoordinateTransformation(TransformationFactory.WGS84, "EPSG:32188");
		Map<String,Coord> homeChargerLocations = new HashMap<>();
		
		//______________________________________________________________________
		//Reading the charger csv file

		String chargerFileHeader = "charger_id,wkt_geom,Fuel Type,Station Na,Street Add,Intersecti,City,State,ZIP,Plus4,Station Ph,Status Cod,Expected D,Groups Wit,Access Day,Cards Acce,BD Blends,NG Fill Ty,NG PSI,EV Level1,EV Level2,EV DC Fast,EV Other I,power,num_plug,EV Network,EV Netwo_1,Geocode St,Latitude,Longitude,Date Last,ID,Updated At,Owner Type,Federal Ag,Federal _1,Open Date,Hydrogen S,NG Vehicle,LPG Primar,E85 Blende,EV Connect,Country,Intersec_1,Access D_1,BD Blend_1,Groups W_1,Hydrogen I,Access Cod,Access Det,Federal _2,Facility T,CNG Dispen,CNG On-Sit,CNG Total,CNG Storag,LNG On-Sit,E85 Other,EV Pricing,EV Prici_1,LPG Nozzle,Hydrogen P,Hydrogen_1,CNG Fill T,CNG PSI,CNG Vehicl,LNG Vehicl,EV On-Site,Restricted,join_ID,join_fromID,join_toID,join_length,join_freespeed,join_capacity,join_lanes,join_visWidth,join_type,distance";

		String[] headers = chargerFileHeader.split(",");

		Reader in;
		Iterable<CSVRecord> records = null;
		try {
			in = new FileReader(chargerFileInput);

			records = CSVFormat.DEFAULT
					.withHeader(headers)
					.withFirstRecordAsHeader()
					.parse(in);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}


		catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		for (CSVRecord record : records) {

			Coord coord = new Coord(Double.parseDouble(record.get("Longitude")),Double.parseDouble(record.get("Latitude")));
			coord = tsf.transform(coord);
			Id<Link> linkId = NetworkUtils.getNearestRightEntryLink(net, coord).getId();
			String baseChargerId = record.get("charger_id");



			int noOfl1 = Integer.parseInt(record.get("EV Level1"));
			int noOfl2 = Integer.parseInt(record.get("EV Level2"));
			int noOfFast = Integer.parseInt(record.get("EV DC Fast"));

			Map<String,Integer> plugCount = new HashMap<>();

			plugCount.put("Level 1", noOfl1);
			plugCount.put("Level 2", noOfl2);
			plugCount.put("Fast", noOfFast);
			

			for(Entry<String, Integer> d:plugCount.entrySet()) {
				if (d.getValue() != 0) {
					ChargerSpecification c = ImmutableChargerSpecification.newBuilder()
							.id(Id.create(baseChargerId + "_" + d.getKey(), Charger.class))
							.linkId(linkId)
							.chargerType(d.getKey())
							.plugCount(d.getValue())
							.plugPower(1000 * cp.get(d.getKey()))
							.build();

					csp.addChargerSpecification(c);
					chargerLinkIds.add(linkId);
				}
			}

		}


		//__________________________________________________________________


		ElectricFleetSpecification sp = new ElectricFleetSpecificationImpl();
		

		Random random = new Random();
		scenario.getPopulation().getPersons().entrySet().forEach(p->{

//		PersonUtils.setCarAvail(p.getValue(),"always"); requested by rena Feb 1, 2022.
//		PersonUtils.getCarAvail(p.getValue());


			String cc = (String) p.getValue().getAttributes().getAttribute("carAvail");

			if(cc.equals("always") && p.getValue().getSelectedPlan().getPlanElements().size()>1) {// have cars and have at least one leg 
				if(Math.random()<=evPercentage) {
					Vehicle v = vf.createVehicle(Id.createVehicleId(p.getKey().toString()), ev1);// create ev vehicle in the vehicles file.
					Map<Id<Vehicle>,Vehicle> vMap = new HashMap<>();
					vMap.put(v.getId(), v);
					vs.addVehicle(v);
					
					//Create vehicle in the ElectricVehicle file
					Double b = (BatteryCapMin+(BatteryCapMax-BatteryCapMin)*random.nextDouble())*36e5;
					Double c = socMIn*36e5+(b-socMIn*36e5)*random.nextDouble();
					ElectricVehicleSpecification s = ImmutableElectricVehicleSpecification.newBuilder()
							.id(Id.create(p.getKey().toString(), ElectricVehicle.class))
							.batteryCapacity(b.intValue())
							.initialSoc(c.intValue())
							.chargerTypes(ImmutableList.copyOf(cp.keySet()))
							.vehicleType(ev1.getId().toString())
							.build();
					
					sp.addVehicleSpecification(s);
					// Now insert a charger at the home location. 
					p.getValue().getSelectedPlan().getPlanElements().stream().filter(pp->pp instanceof Activity)
					.filter(a->((Activity)a).getType().equals("home")).forEach(a->homeChargerLocations.put(p.getKey().toString(),((Activity)a).getCoord()));
					
					// check if have access to at least one charger
					Set<Id<Link>> actLinkIds = new HashSet<>();
					p.getValue().getSelectedPlan().getPlanElements().stream().filter(pp->pp instanceof Activity)
					.forEach(a->actLinkIds.add(((Activity)a).getLinkId()));
					boolean haveAccessToCharger = false;
					for(Id<Link> lIds:actLinkIds) {
						if(chargerLinkIds.contains(lIds)) {
							haveAccessToCharger = true;
							break;
						}
					}
//					if(haveAccessToCharger == false) {
//						homeChargerLocations.put(p.getKey().toString(), ((Activity)p.getValue().getSelectedPlan().getPlanElements().get(0)).getCoord());
//					}
					
				}else {
					Vehicle v = vf.createVehicle(Id.createVehicleId(p.getKey().toString()), noEV);// Create a non EV vehicle
					Map<Id<Vehicle>,Vehicle> vMap = new HashMap<>();
					vMap.put(v.getId(), v);
					vs.addVehicle(v);
				}
			}
		});
		
		if(assignChargersToEveryone) {
			for(Entry<String, Coord> d:homeChargerLocations.entrySet()) {
				Id<Link> linkId = NetworkUtils.getNearestRightEntryLink(net, d.getValue()).getId();
				if(!chargerLinkIds.contains(linkId)) {
				ChargerSpecification c = ImmutableChargerSpecification.newBuilder()
						.id(Id.create(d.getKey()+"_home", Charger.class))
						.linkId(linkId)
						.chargerType("home")
						.plugCount(1)
						.plugPower(1000 * 11.5)
						.build();
	
				csp.addChargerSpecification(c);
				}
			}
		}


		new ChargerWriter(csp.getChargerSpecifications().values().stream()).write(chargerOutput);
		new PopulationWriter(scenario.getPopulation()).write(planOutput);
		new MatsimVehicleWriter(vs).writeFile(vehicleOutput);


		new ElectricFleetWriter(sp.getVehicleSpecifications().values().stream()).write(evVehicleOutput);

		EvConfigGroup evgroup = new EvConfigGroup();
		evgroup.setChargersFile(chargerOutput);
		evgroup.setVehiclesFile(evVehicleOutput);
		evgroup.setTimeProfiles(false);
		config.removeModule(evgroup.getName());
		config.addModule(evgroup);

		config.plans().setInputFile(planOutput);

		config.vehicles().setVehiclesFile(vehicleOutput);

		


		new ConfigWriter(config).write(configOut);

		//____________________________
		//running steps according to runningUrbanEV

		Config configRun = ConfigUtils.createConfig();
		ConfigUtils.loadConfig(configRun,configOut);
		
		UrbanEVConfigGroup evReplanningCfg = new UrbanEVConfigGroup(); // create the urbanEV config group
		configRun.addModule(evReplanningCfg);

		//TODO actually, should also work with all AccessEgressTypes but we have to check (write JUnit test)
		configRun.plansCalcRoute().setAccessEgressType(PlansCalcRouteConfigGroup.AccessEgressType.walkConstantTimeToLink);

		//register charging interaction activities for car
		configRun.planCalcScore().addActivityParams(
				new PlanCalcScoreConfigGroup.ActivityParams(TransportMode.car + UrbanVehicleChargingHandler.PLUGOUT_INTERACTION)
						.setScoringThisActivityAtAll(false));
		configRun.planCalcScore().addActivityParams(
				new PlanCalcScoreConfigGroup.ActivityParams(TransportMode.car + UrbanVehicleChargingHandler.PLUGIN_INTERACTION)
						.setScoringThisActivityAtAll(false));
		Scenario scRun = ScenarioUtils.loadScenario(configRun);
		Controler controler = new Controler(scRun);
		controler.addOverridingModule(new UrbanEVModule());
		controler.configureQSimComponents(components -> components.addNamedComponent(EvModule.EV_COMPONENT));
	//	controler.run();
	}
	/**
	 * Use this function after creating the controller before running urban ev.
	 * @param controler
	 * @return
	 */
	public static Controler createUrbanEVController(Controler controler) {
		controler.addOverridingModule(new EvModule());
		//controler.addOverridingModule(new EVPriceModule());
		controler.addOverridingModule(new AbstractModule() {
			@Override
			public void install() {
				addRoutingModuleBinding(TransportMode.car).toProvider(new EvNetworkRoutingProvider(TransportMode.car));
				installQSimModule(new AbstractQSimModule() {
					@Override
					protected void configureQSim() {
						bind(VehicleChargingHandler.class).asEagerSingleton();
						bind(ChargePricingEventHandler.class).asEagerSingleton();
						//addMobsimScopeEventHandlerBinding().to(VehicleChargingHandler.class);
						//addMobsimScopeEventHandlerBinding().to(ChargePricingEventHandler.class);
					}
				});
			}
		});

		
		//plug in UrbanEVModule
		controler.addOverridingModule(new UrbanEVModule());
		//register EV qsim components
		controler.configureQSimComponents(components -> components.addNamedComponent(EvModule.EV_COMPONENT));
		return controler;
	}
	
	public static void checkPlanConsistancy(Population population) {
		long t = System.currentTimeMillis();
		int personDeleted = 0;
		Map<Id<Person>,Person> persons = new HashMap<>(population.getPersons());
		for(Entry<Id<Person>, Person> p:persons.entrySet()){
			if(p.getValue().getSelectedPlan().getPlanElements().size()<2) {
				population.getPersons().remove(p.getKey());
				personDeleted++;
				continue;
			}
			Activity beforeAct = null;
			for(PlanElement pe:p.getValue().getSelectedPlan().getPlanElements()) {
				if(pe instanceof Activity && beforeAct == null) beforeAct = ((Activity)pe);
				else if(pe instanceof Activity) {
					double d = NetworkUtils.getEuclideanDistance(beforeAct.getCoord(), ((Activity)pe).getCoord());
					if(d < 10) {
						population.getPersons().remove(p.getKey());
						personDeleted++;
						break;
					}else {
						beforeAct = ((Activity)pe);
					}
				}
			}
		}
		System.out.println("person deleted = " + personDeleted);
		System.out.println("time in milisec = " +(System.currentTimeMillis() - t));
		System.out.println();
	}
}