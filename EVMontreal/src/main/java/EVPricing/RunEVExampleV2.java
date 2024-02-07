package EVPricing;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.contrib.ev.EvConfigGroup;
import org.matsim.contrib.ev.EvModule;
import org.matsim.contrib.ev.charging.ChargingHandler;
import org.matsim.contrib.ev.fleet.ElectricVehicleSpecification;
import org.matsim.contrib.ev.routing.EvNetworkRoutingProvider;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ActivityParams;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup;
import org.matsim.core.config.groups.QSimConfigGroup;
import org.matsim.core.config.groups.StrategyConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.mobsim.qsim.AbstractQSimModule;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule.DefaultStrategy;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.vehicles.VehicleCapacity;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.Vehicles;

import binding.EVOutOfBatteryChecker;
import experienceSOC.ActivitySOCModule;
import modeChoiceFix.SubTourPlanStrategyBinder;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import urbanEV.UrbanEVConfigGroup;
import urbanEV.UrbanEVModule;
import urbanEV.UrbanEVTripPlanningStrategyModule;
import urbanEV.UrbanVehicleChargingHandler;
import withinDay.TrialWithinday;

public final class RunEVExampleV2 implements Callable<Integer> {
  public static final String COLOR = "@|bold,fg(81) ";
   
  private static final Logger log = LogManager.getLogger(RunEVExampleV2.class);
  
  @Option(names = {"--config"}, description = {"Optional Path to config file to load."}, defaultValue = "config.xml")
  private String config;
  
  @Option(names = {"--plan"}, description = {"Optional Path to plan file to load."}, defaultValue = "plan.xml")
  private String planFile;
  
  @Option(names = {"--network"}, description = {"Optional Path to network file to load."}, defaultValue = "montreal_network.xml.gz")
  private String networkFileLoc;
  
  @Option(names = {"--ts"}, description = {"Optional Path to transit schedule file to load."}, defaultValue = "montreal_transit_schedules.xml.gz")
  private String tsFileLoc;
  
  @Option(names = {"--tv"}, description = {"Optional Path to transit vehicle file to load."}, defaultValue = "montreal_transit_vehicles.xml.gz")
  private String tvFileLoc;
  
  @Option(names = {"--facilities"}, description = {"Optional Path to facilities file to load."}, defaultValue = "montreal_facilities.xml.gz")
  private String facilitiesFileLoc;
  
  @Option(names = {"--lastiterations"}, description = {"Maximum number of iteration to simulate."}, defaultValue = "100")
  private int maxIterations;
  
  @Option(names = {"--firstiterations"}, description = {"Maximum number of iteration to simulate."}, defaultValue = "0")
  private int minIterations;
  
  @Option(names = {"--household"}, description = {"Optional Path to household file to load."}, defaultValue = "montreal_households.xml.gz")
  private String householdFileLoc;
  
  @Option(names = {"--scale"}, description = {"Scale of simulation"}, defaultValue = "0.1")
  private Double scale;
  
  @Option(names = {"--output"}, description = {"Result output directory"}, defaultValue = "output/TimeBasedBaseScenarioAllHomeChargersAllLogicRevisedPunishment")
  private String output;
  
  @Option(names = {"--charger"}, description = {"Charger file location"}, defaultValue = "charger.xml")
  private String chargerFile;
  
  @Option(names = {"--vehicles"}, description = {"Vehicles file"}, defaultValue = "vehicle.xml")
  private String vehicleFile;
  
  @Option(names = {"--evehicle"}, description = {"Electric vehicle file"}, defaultValue = "evehicle.xml")
  private String evehicleFile;
  
  @Option(names = {"--evpricing"}, description = {"Charger pricing file location"}, defaultValue = "pricingProfiles.xml")
  private String pricingEVFile;
  
  @Option(names = {"--distanceForCharger"}, description = {"Maximum search radius for charger around activity"}, defaultValue = "1000.0")
  private Double chargerDist;
  
  @Option(names = {"--thread"}, description = {"No of thread"}, defaultValue = "20")
  private int thread;
  
  public static void main(String[] args) {
    (new CommandLine(new RunEVExampleV2()))
      .setStopAtUnmatched(false)
      .setUnmatchedOptionsArePositionalParams(true)
      .execute(args);
  }
  
  public Integer call() throws Exception {
    
    Config config = ConfigUtils.loadConfig(this.config, new EvConfigGroup(), new UrbanEVConfigGroup());
    
    config.plans().setInputFile(this.planFile);
    config.households().setInputFile(this.householdFileLoc);
    config.facilities().setInputFile(this.facilitiesFileLoc);
    config.network().setInputFile(this.networkFileLoc);
    config.transit().setTransitScheduleFile(this.tsFileLoc);
    config.transit().setVehiclesFile(this.tvFileLoc);
    config.vehicles().setVehiclesFile(this.vehicleFile);
    config.global().setNumberOfThreads(thread);
    config.qsim().setNumberOfThreads(thread);
    config.controler().setLastIteration(this.maxIterations);
    config.controler().setFirstIteration(this.minIterations);
    //addStrategy(config, "SubtourModeChoice", null, 0.1D, (int)0.65 * this.maxIterations);
    addStrategy(config, UrbanEVTripPlanningStrategyModule.urbanEVTripPlannerStrategyName, null, 0.85D, (int).75 * this.maxIterations);
    addStrategy(config, "ChangeExpBeta", null, 0.25D, this.maxIterations);
    addStrategy(config, DefaultStrategy.TimeAllocationMutator_ReRoute, null, 0.05D, (int)0.7*this.maxIterations);
    addStrategy(config, DefaultStrategy.ReRoute, null, 0.05D, (int)0.8*this.maxIterations);
    config.controler().setOutputDirectory(this.output);
    config.controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
    config.qsim().setFlowCapFactor(this.scale.doubleValue() * 1.2D);
    config.qsim().setStorageCapFactor(this.scale.doubleValue() * 1.4D);
    
 
	((EvConfigGroup)config.getModules().get("ev")).setTimeProfiles(true);
	((UrbanEVConfigGroup)config.getModules().get("urbanEV")).setPluginBeforeStartingThePlan(true);
	((UrbanEVConfigGroup)config.getModules().get("urbanEV")).setMaxDistanceBetweenActAndCharger_m(chargerDist);
	((UrbanEVConfigGroup)config.getModules().get("urbanEV")).setMaximumChargingProceduresPerAgent(2);
	((UrbanEVConfigGroup)config.getModules().get("urbanEV")).setCriticalRelativeSOC(0.3);
	((EvConfigGroup)config.getModules().get("ev")).setChargersFile(this.chargerFile);
	((EvConfigGroup)config.getModules().get("ev")).setVehiclesFile(this.evehicleFile);
	
//    UrbanEVConfigGroup urbanEVConfigGroup = new UrbanEVConfigGroup();
//    config.addModule(urbanEVConfigGroup);
	
    //config.controler().setLastIteration(100);
	
	config.controler()
			.setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
	config.qsim().setFlowCapFactor(scale);
	config.qsim().setStorageCapFactor(scale);
	config.global().setNumberOfThreads(14);
	config.qsim().setNumberOfThreads(10);
	//config.qsim().setVehiclesSource(VehiclesSource.defaultVehicle);
	
//	config.planCalcScore().getActivityParams().forEach(actParam->actParam.setScoringThisActivityAtAll(true));
	
	config.planCalcScore().setPerforming_utils_hr(14);
	
//	UrbanEVConfigGroup evReplanningCfg = new UrbanEVConfigGroup(); // create the urbanEV config group
//	
//	config.addModule(evReplanningCfg);

	//TODO actually, should also work with all AccessEgressTypes but we have to check (write JUnit test)
	config.plansCalcRoute().setAccessEgressType(PlansCalcRouteConfigGroup.AccessEgressType.none);

	//register charging interaction activities for car
	config.planCalcScore().addActivityParams(
			new PlanCalcScoreConfigGroup.ActivityParams(TransportMode.car + UrbanVehicleChargingHandler.PLUGOUT_INTERACTION)
					.setScoringThisActivityAtAll(false));
	config.planCalcScore().addActivityParams(
			new PlanCalcScoreConfigGroup.ActivityParams(TransportMode.car + UrbanVehicleChargingHandler.PLUGIN_INTERACTION)
					.setScoringThisActivityAtAll(false));
	//config.qsim().setVehiclesSource(QSimConfigGroup.VehiclesSource.fromVehiclesData);

	SubTourPlanStrategyBinder.addStrategy(config, 0.05, 100); 
	
	
	Scenario scenario = ScenarioUtils.loadScenario(config);
	
	scenario.getActivityFacilities().getFacilities().entrySet().forEach(f->{
		if(f.getValue().getCoord()==null)f.getValue().setCoord(scenario.getNetwork().getLinks().get(f.getValue().getLinkId()).getCoord());
	});
	
	scaleDownPt(scenario.getTransitVehicles(), scale);
	
	 Map<String,Double> actDuration = new HashMap<>();
	    Map<String,Integer> actNum = new HashMap<>();
	    Set<String> actList = new HashSet<>();
	    scenario.getPopulation().getPersons().values().stream().forEach(p->{
	    	p.getSelectedPlan().getPlanElements().stream().filter(f-> f instanceof Activity).forEach(pe->{
	    		Activity act = ((Activity)pe);
	    		Double actDur = 0.;
	    		if(act.getEndTime().isDefined() && act.getStartTime().isDefined()) {
	    			actDur = act.getEndTime().seconds() - act.getStartTime().seconds();
	    		}
	    		double ad = actDur;
	    		if(actDur != 0.) {
	    			actDuration.compute(act.getType(), (k,v)->v==null?ad:ad+v);
	    			actNum.compute(act.getType(), (k,v)->v==null?1:v+1);
	    		}else {
	    			
	    		}
	    		
	    	});
	    });
	    
	    for(Entry<String, Double> a:actDuration.entrySet()){
	    	a.setValue(a.getValue()/actNum.get(a.getKey()));
	    	if(config.planCalcScore().getActivityParams(a.getKey())!=null) {
	    		config.planCalcScore().getActivityParams(a.getKey()).setTypicalDuration(a.getValue());
	    		config.planCalcScore().getActivityParams(a.getKey()).setMinimalDuration(a.getValue()*.25);
	    		config.planCalcScore().getActivityParams(a.getKey()).setScoringThisActivityAtAll(true);
	    	}else {
	    		ActivityParams param = new ActivityParams();
	    		param.setActivityType(a.getKey());
	    		param.setTypicalDuration(a.getValue());
	    		param.setMinimalDuration(a.getValue()*.25);
	    		param.setScoringThisActivityAtAll(true);
	    		config.planCalcScore().addActivityParams(param);
	    	}
	    }
	    for(String actType:actList) {
	    	if(config.planCalcScore().getActivityParams(actType)==null) {
	    		ActivityParams param = new ActivityParams();
	    		param.setActivityType(actType);
	    		param.setTypicalDuration(8*3600);
	    		param.setMinimalDuration(8*3600*.25);
	    		config.planCalcScore().addActivityParams(param);
	    		param.setScoringThisActivityAtAll(true);
	    		System.out.println("No start and end time was found for activity = "+actType+ " in the base population!! Inserting 8 hour as the typical duration.");
	    	}
	    }
	  
	    //Add the plugin and plugout activities 
	    
	    if(config.planCalcScore().getActivityParams(UrbanVehicleChargingHandler.PLUGIN_IDENTIFIER)==null) {
	    	ActivityParams param = new ActivityParams();
    		param.setActivityType(UrbanVehicleChargingHandler.PLUGIN_IDENTIFIER);
    		param.setTypicalDuration(5*60);
    		param.setMinimalDuration(1*60);
    		config.planCalcScore().addActivityParams(param);
    		param.setScoringThisActivityAtAll(false);
	    }
	    
	    if(config.planCalcScore().getActivityParams(UrbanVehicleChargingHandler.PLUGOUT_IDENTIFIER)==null) {
	    	ActivityParams param = new ActivityParams();
    		param.setActivityType(UrbanVehicleChargingHandler.PLUGOUT_IDENTIFIER);
    		param.setTypicalDuration(5*60);
    		param.setMinimalDuration(1*60);
    		config.planCalcScore().addActivityParams(param);
    		param.setScoringThisActivityAtAll(false);
	    }
	    
	Controler controler = new Controler(scenario);
	//controler.addOverridingModule(new EvModule());
	controler.addOverridingModule(new UrbanEVModule());
	//controler.addOverridingModule(new EVPriceModule());
	
	
	
	
	ChargerPricingProfiles pricingProfiles = new ChargerPricingProfileReader().readChargerPricingProfiles(this.pricingEVFile);

	
	controler.addOverridingModule(new AbstractModule() {
		@Override
		public void install() {
			bind(ChargerPricingProfiles.class).toInstance(pricingProfiles);
			addRoutingModuleBinding(TransportMode.car).toProvider(new EvNetworkRoutingProvider(TransportMode.car));
			installQSimModule(new AbstractQSimModule() {
				@Override
				protected void configureQSim() {
					//bind(VehicleChargingHandler.class).asEagerSingleton();
					bind(ChargePricingEventHandler.class).asEagerSingleton();
					//addMobsimScopeEventHandlerBinding().to(VehicleChargingHandler.class);
					
					addMobsimScopeEventHandlerBinding().to(ChargePricingEventHandler.class);
					this.addQSimComponentBinding(EvModule.EV_COMPONENT).to(ChargePricingEventHandler.class);
					this.bind(EVOutOfBatteryChecker.class).asEagerSingleton();
					this.addQSimComponentBinding(EvModule.EV_COMPONENT).to(EVOutOfBatteryChecker.class);
					
					
//					addMobsimListenerBinding().to(EVOutOfBatteryChecker.class);
//					addMobsimScopeEventHandlerBinding().to(EVOutOfBatteryChecker.class);
				}
				
			});
			addMobsimListenerBinding().to(TrialWithinday.class);
			
		}
	});
    
    
	controler.configureQSimComponents(components -> components.addNamedComponent(EvModule.EV_COMPONENT));
	SubTourPlanStrategyBinder.configure(controler);
	ActivitySOCModule.configure(controler);
	controler.run();
    return Integer.valueOf(0);
  }
  
  public static void addStrategy(Config config, String strategy, String subpopulationName, double weight, int disableAfter) {
    if (weight <= 0.0D || disableAfter < 0)
      throw new IllegalArgumentException("The parameters can't be less than or equal to 0!"); 
    StrategyConfigGroup.StrategySettings strategySettings = new StrategyConfigGroup.StrategySettings();
    strategySettings.setStrategyName(strategy);
    strategySettings.setSubpopulation(subpopulationName);
    strategySettings.setWeight(weight);
    if (disableAfter > 0)
      strategySettings.setDisableAfter(disableAfter); 
    config.strategy().addStrategySettings(strategySettings);
  }
  
  public static void scaleDownPt(Vehicles transitVehicles, double portion) {
		for(VehicleType vt: transitVehicles.getVehicleTypes().values()) {
			VehicleCapacity vc = vt.getCapacity();
			vc.setSeats((int) Math.ceil(vc.getSeats().intValue() * portion));
			vc.setStandingRoom((int) Math.ceil(vc.getStandingRoom().intValue() * portion));
			vt.setPcuEquivalents(vt.getPcuEquivalents() * portion);
		}
	}
}
