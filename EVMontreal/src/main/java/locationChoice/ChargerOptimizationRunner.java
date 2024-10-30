package locationChoice;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.apache.commons.math.linear.MatrixUtils;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NetworkUtils;
import org.matsim.facilities.ActivityFacility;
import org.opt4j.core.Individual;
import org.opt4j.core.genotype.DoubleGenotype;
import org.opt4j.core.optimizer.Archive;
import org.opt4j.core.problem.Creator;
import org.opt4j.core.problem.Decoder;
import org.opt4j.core.problem.Evaluator;
import org.opt4j.core.problem.ProblemModule;
import org.opt4j.core.start.Opt4JTask;
import org.opt4j.optimizers.ea.EvolutionaryAlgorithmModule;
import org.opt4j.viewer.ViewerModule;

import com.google.inject.Binder;
import com.google.inject.Inject;
import com.google.inject.multibindings.Multibinder;

public class ChargerOptimizationRunner {

    public static void main(String[] args) throws NumberFormatException, IOException {
    	
//    	String HotspotFile = "data/10p/chargerCoordsNew.csv";// only has the hotspot name and coordinates. 
//		String facilityFile = "data/10p/features_noDuration.csv";// have facility features with x, y and the usage of ev and non ev users including their activity durations
//		
//		BufferedReader bf_f = new BufferedReader(new FileReader(new File(facilityFile)));
//		
//		String header = bf_f.readLine();
//		String[] headers = header.split(",");
//		List<String> keys = new ArrayList<>();
////		for(int i=1;i<headers.length;i++) {
////			keys.add(headers[i]);
////		}
//		keys.add(Hotspot.locationX);
//		keys.add(Hotspot.locationY);
//		keys.add(Hotspot.EvUserString+"_"+Hotspot.activityNumberString);
//		keys.add(Hotspot.EvUserString+"_"+Hotspot.acitivityDurationString);
//		keys.add(Hotspot.nonEvUserString+"_"+Hotspot.activityNumberString);
//		keys.add(Hotspot.nonEvUserString+"_"+Hotspot.acitivityDurationString);
//		keys.add(Hotspot.EvUserString+"_"+Hotspot.startTime);
//		keys.add(Hotspot.nonEvUserString+"_"+Hotspot.startTime);
//		
//		
//		MapToArray<String> featureKeys = new MapToArray<String>("featureMap",keys);
//		String line = null;
//		Map<Id<ActivityFacility>,Map<String,Double>> features = new HashMap<>();
//		while((line = bf_f.readLine())!=null) {
//			String[] part = line.split(",");
//			Id<ActivityFacility> facId = Id.create(part[0],ActivityFacility.class);
//			Map<String,Double> feature = new HashMap<>();
//			for(int i=0;i<keys.size();i++) {
//				feature.put(keys.get(i), Double.parseDouble(part[i+1]));
//			}
//			features.put(facId, feature);
//		}
//		bf_f.close();
//		
//		BufferedReader bf = new BufferedReader(new FileReader(new File(HotspotFile)));
//		
//		bf.readLine();
//		
//		line = null;
//		Map<ChargerType,Double> chargerPower = new HashMap<>();
//		chargerPower.put(ChargerType.fast, 1000*50.);
//		chargerPower.put(ChargerType.level1, 1000*30.);
//		chargerPower.put(ChargerType.level2, 1000*10.);
//		chargerPower.put(ChargerType.home, 1000*6.);
//		
//		Map<ChargerType, Double> setupCostPerChargerType = new HashMap<>();
//		Map<ChargerType, Double> operationCostPerChargerType = new HashMap<>();
//		
//		
//		double setUpBudget = 1284600*5; // Example budget, adjust as necessary
//		double operationBudget = 1284600*1.6;//Example operation budget, adjust as necessary
//		
//		
//		
//		// Define the setup and operation costs for each charger type
//		setupCostPerChargerType.put(ChargerType.level1, 5000.0); // Example setup cost for level1 charger
//		setupCostPerChargerType.put(ChargerType.level2, 10000.0); // Example setup cost for level2 charger
//		setupCostPerChargerType.put(ChargerType.fast, 20000.0);   // Example setup cost for fast charger
//
//		operationCostPerChargerType.put(ChargerType.level1, 200.0); // Example operation cost for level1 charger
//		operationCostPerChargerType.put(ChargerType.level2, 400.0); // Example operation cost for level2 charger
//		operationCostPerChargerType.put(ChargerType.fast, 800.0);   // Example operation cost for fast charger
//
//		
//		
//		
//		Hotspot.setPowerPerChargerType(chargerPower);
//		Map<Id<Hotspot>,Hotspot> hotspots = new HashMap<>();
//		
//		while((line = bf.readLine())!=null) {
//			String[] part = line.split(",");
//			Hotspot h = new Hotspot(part[0], featureKeys);
//			Id<ActivityFacility> facId = Id.create(part[5], ActivityFacility.class);
//			double x = Double.parseDouble(part[1]);
//			double y = Double.parseDouble(part[2]);
//			double[] f = new double[featureKeys.getKeySet().size()];
//			f[0] = x;
//			f[1] = y;
//			h.setCentroidFacilityId(facId,MatrixUtils.createRealVector(f));
//			String chargerType = part[6];
//			ChargerType type = null;
//			if(chargerType.equals("Fast"))type = ChargerType.fast;
//			else if(chargerType.equals("Level 1")) type = ChargerType.level1;
//			else if(chargerType.equals("Level 2")) type = ChargerType.level2;
//			int plugCount = Integer.parseInt(part[3]);
//			double power = Double.parseDouble(part[4]);
//			chargerPower.put(type, power);
//			if(!h.getHotspotId().toString().contains("dynamic")) {
//				h.setLockedCentroid(true);
//			}else {
//				type = ChargerType.fast;
//				plugCount = 0;
//				power = 1000*50.;
//			}
//			h.setPlugCountPerChargerType(Map.of(type,plugCount));
//			h.setCoord(new Coord(x,y));
//			hotspots.put(h.getHotspotId(), h);
//		}
//		bf.close();
//		
//		DemandAllocationModel model = new DemandAllocationModel(hotspots, features, featureKeys);
//		
//		Map<Id<Hotspot>,Map<ChargerType,Integer>> chargers = new HashMap<>();
//		hotspots.entrySet().forEach(e->{
//			chargers.put(e.getKey(), new HashMap<>(e.getValue().getPlugCountPerChargerType()));
//		});
//		
////		Map<Id<ActivityFacility>,Double> demand = new HashMap<>();
////		
////		features.entrySet().forEach(e->{
////			demand.put(e.getKey(), e.getValue().get(Hotspot.EvUserString+"_"+Hotspot.activityNumberString));
////		});
////		
////		model.allocateDemand(chargers, null);
//		System.out.println("Done");
//		
//		
//		String plugCountkey = "plug";
//		String typeKey = "type";
//		String seperator = "___";
//		
//		
//		//create the variables
//		Map<String,Double> variables = new HashMap<>();
//		Map<String,Double> variablesUpperLimit = new HashMap<>();
//		Map<String,Double> variablesLowerLimit = new HashMap<>();
//		
//		chargers.entrySet().forEach(c->{
//			if(c.getKey().toString().contains("dynamic")) {
//				variables.put(c.getKey().toString()+seperator+typeKey, 0.);
//				variables.put(c.getKey().toString()+seperator+plugCountkey, 0.);
//				
//				variablesLowerLimit.put(c.getKey().toString()+seperator+typeKey, 0.);
//				variablesLowerLimit.put(c.getKey().toString()+seperator+plugCountkey, 0.);
//				
//				variablesUpperLimit.put(c.getKey().toString()+seperator+typeKey, 1.);
//				variablesUpperLimit.put(c.getKey().toString()+seperator+plugCountkey, 1.);
//				
//			}
//		});
//		
//		//Read the zones file
//		Network zonesNet = NetworkUtils.createNetwork();
//		BufferedReader bf_zones = new BufferedReader(new FileReader(new File("zones.csv")));
//		bf_zones.readLine();
//		
//		line = null;
//		while((line = bf_zones.readLine())!=null) {
//			String[] part = line.split(",");
//			NetworkUtils.createAndAddNode(zonesNet, Id.createNodeId(part[0]), new Coord(Double.parseDouble(part[1]),Double.parseDouble(part[2])));
//			zonesNet.getNodes().get(Id.createNodeId(part[0])).getAttributes().putAttribute("pricing multiplier", Double.parseDouble(part[3]));
//			zonesNet.getNodes().get(Id.createNodeId(part[0])).getAttributes().putAttribute("Power Limit",Double.parseDouble(part[4]));
//		}
//		bf_zones.close();
//		Map<Id<Node>,Set<Id<Hotspot>>> chargerToZonesAssignment = new LinkedHashMap<>();
//		hotspots.entrySet().forEach(h->{
//			Node zone = NetworkUtils.getNearestNode(zonesNet, h.getValue().getCoord());
//			if(!chargerToZonesAssignment.containsKey(zone.getId()))chargerToZonesAssignment.put(zone.getId(), new HashSet<>());
//			chargerToZonesAssignment.get(zone.getId()).add(h.getKey());
//		});
//		
//		MapToArray<String> variablesMapToArray = new MapToArray<String>("variables",variables.keySet());
		
		
        ChargerOptimizationRunner runner = new ChargerOptimizationRunner();
        runner.runOptimization();
    }

    private void runOptimization() {
        // Configure Evolutionary Algorithm module
        EvolutionaryAlgorithmModule eaModule = new EvolutionaryAlgorithmModule();
        eaModule.setGenerations(200);
        eaModule.setAlpha(50);

        // Optional viewer for monitoring optimization
        ViewerModule viewerModule = new ViewerModule();
        viewerModule.setCloseOnStop(true);

        // Define and initialize the task
        Opt4JTask task = new Opt4JTask(false);
        task.init(eaModule, new ChargerProblemModule(),viewerModule);

        try {
            task.execute();

            // Access and print solutions from the archive
            Archive archive = task.getInstance(Archive.class);
            for (Individual individual : archive) {
                System.out.println("Solution: " + individual.getPhenotype());
                System.out.println("Objectives: " + individual.getObjectives());
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            task.close();
        }
    }

    private static class ChargerProblemModule extends ProblemModule {
    	DemandAllocationModel demandModel;
        double setupBudget;
        double operationBudget;
        Map<ChargerType, Double> setupCostPerChargerType;
        Map<ChargerType, Double> operationCostPerChargerType;
        Network zonesNet; 
        Map<Id<Node>, Set<Id<Hotspot>>> chargerToZonesAssignment;
        MapToArray<String>variables;
        Random random = new Random();
        
        public ChargerProblemModule() {
        	
        }
    	
    	public ChargerProblemModule(DemandAllocationModel demandModel, 
                double setupBudget, 
                double operationBudget,
                Map<ChargerType, Double> setupCostPerChargerType,
                Map<ChargerType, Double> operationCostPerChargerType,
                Network zonesNet, 
                Map<Id<Node>, Set<Id<Hotspot>>> chargerToZonesAssignment,
                MapToArray<String>variables) {
    		this.demandModel = demandModel;
    		this.operationBudget = operationBudget;
    		this.setupBudget = setupBudget;
    		this.setupCostPerChargerType = setupCostPerChargerType;
    		this.operationCostPerChargerType = operationCostPerChargerType;
    		this.zonesNet = zonesNet;
    		this.chargerToZonesAssignment = chargerToZonesAssignment;
    		this.variables = variables;
    	}
        @Override
        protected void config() {
        	//Binder binder = binder();
            // Bind Creator, Decoder, and Evaluator in one call
//            bind(ChargerEvaluator.class).toInstance(new ChargerEvaluator(demandModel, setupBudget, operationBudget, setupCostPerChargerType, operationCostPerChargerType, zonesNet, chargerToZonesAssignment));
//            bind(ChargerCreator.class).toInstance(new ChargerCreator());
//            bind(ChargerDecoder.class).toInstance(new ChargerDecoder());
            bindProblem(ChargerCreator.class, ChargerDecoder.class, ChargerEvaluator.class);
            
//            bind(Creator.class).toInstance(new ChargerCreator(random, variables));
//            bind(Decoder.class).toInstance(new ChargerDecoder(variables));
//            bind(Evaluator.class).toInstance(new ChargerEvaluator(
//                demandModel, setupBudget, operationBudget, setupCostPerChargerType,
//                operationCostPerChargerType, zonesNet, chargerToZonesAssignment
//            ));
        }
    }

    private static class ChargerCreator implements Creator<DoubleGenotype> {
    	
    	
    	
    	MapToArray<String> variables;
    	Random random ;
    	
    	public ChargerCreator(){
    		this.random = new Random();
    		String HotspotFile = "data/10p/chargerCoordsNew.csv";// only has the hotspot name and coordinates. 
    		BufferedReader bf = null;
    		String line = null;

    		Set<Id<Hotspot>> hotspots = new HashSet<>();
    		try {
    			bf = new BufferedReader(new FileReader(new File(HotspotFile)));
    			bf.readLine();
    			while((line = bf.readLine())!=null) {
    				String[] part = line.split(",");
    				Id<Hotspot> h = Id.create(part[0], Hotspot.class);
    				if(h.toString().contains("dynamic")) {
    					hotspots.add(h);
    				}
    				
    			}
    			bf.close();
    			
    		} catch (NumberFormatException | IOException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		}
    		
    		String plugCountkey = "plug";
    		String typeKey = "type";
    		String seperator = "___";
    		
    		
    		//create the variables
    		Map<String,Double> variables = new HashMap<>();
    		
    		hotspots.forEach(c->{
    			if(c.toString().contains("dynamic")) {
    				variables.put(c.toString()+seperator+typeKey, 0.);
    				variables.put(c.toString()+seperator+plugCountkey, 0.);
    				
    			}
    		});
    		this.variables = new MapToArray<String>("variables",variables.keySet());
    	}
    	
        @Override
        public DoubleGenotype create() {
            DoubleGenotype genotype = new DoubleGenotype(0.0, 1.0); // Range 0 to 1 for all variables
            int numberOfVariables = this.variables.getKeySet().size();
            genotype.init(random, numberOfVariables); // Initialize with the required number of variables
            return genotype;
        }
    }

    private static class ChargerDecoder implements Decoder<DoubleGenotype, Map<String, Double>> {
    	
    	MapToArray<String> variables;
        
    	@Inject
    	public ChargerDecoder() {
    		String HotspotFile = "data/10p/chargerCoordsNew.csv";// only has the hotspot name and coordinates. 
    		BufferedReader bf = null;
    		String line = null;

    		Set<Id<Hotspot>> hotspots = new HashSet<>();
    		try {
    			bf = new BufferedReader(new FileReader(new File(HotspotFile)));
    			bf.readLine();
    			while((line = bf.readLine())!=null) {
    				String[] part = line.split(",");
    				Id<Hotspot> h = Id.create(part[0], Hotspot.class);
    				if(h.toString().contains("dynamic")) {
    					hotspots.add(h);
    				}
    				
    			}
    			bf.close();
    			
    		} catch (NumberFormatException | IOException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		}
    		
    		String plugCountkey = "plug";
    		String typeKey = "type";
    		String seperator = "___";
    		
    		
    		//create the variables
    		Map<String,Double> variables = new HashMap<>();
    		
    		hotspots.forEach(c->{
    			if(c.toString().contains("dynamic")) {
    				variables.put(c.toString()+seperator+typeKey, 0.);
    				variables.put(c.toString()+seperator+plugCountkey, 0.);
    				
    			}
    		});
    		this.variables = new MapToArray<String>("variables",variables.keySet());
    	}
    	
    	
    	@Override
        public Map<String, Double> decode(DoubleGenotype genotype) {
            Map<String, Double> decodedSolution = new HashMap<>();
            List<String> variables = getVariables();

            int i = 0;
            for (String variable : variables) {
                decodedSolution.put(variable, genotype.get(i));
                i++;
            }
            return decodedSolution;
        }

        private List<String> getVariables() {
           
            return variables.getKeySet();
        }
    }
}

