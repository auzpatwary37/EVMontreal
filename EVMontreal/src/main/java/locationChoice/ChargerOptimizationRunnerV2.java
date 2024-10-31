package locationChoice;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;

import org.matsim.api.core.v01.Id;
import org.opt4j.core.Genotype;
import org.opt4j.core.Individual;
import org.opt4j.core.Objective;
import org.opt4j.core.Value;
import org.opt4j.core.genotype.CompositeGenotype;
import org.opt4j.core.genotype.IntegerGenotype;
import org.opt4j.core.optimizer.Archive;
import org.opt4j.core.problem.Creator;
import org.opt4j.core.problem.Decoder;
import org.opt4j.core.problem.ProblemModule;
import org.opt4j.core.start.Opt4JTask;
import org.opt4j.optimizers.ea.EvolutionaryAlgorithmModule;
import org.opt4j.viewer.ViewerModule;

import com.google.inject.Inject;

public class ChargerOptimizationRunnerV2 {

    public static void main(String[] args) throws NumberFormatException, IOException {
    	
        ChargerOptimizationRunnerV2 runner = new ChargerOptimizationRunnerV2();
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
            this.writeSolutionsToFile(archive, "data/10p/secondStepOptimizationResult.csv");
            
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            task.close();
        }
        
        
    }
 // Method to write optimization details to a file
    public void writeSolutionsToFile(Archive archive, String filePath) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath, true))) { // Append mode

            // Iterate over each individual in the archive
            for (Individual individual : archive) {
                // Write header for a new solution
                writer.write("Hotspot ID, Charger Type, Plug Count\n");

                // Get the phenotype (solution representation)
                Map<Id<Hotspot>, Map<ChargerType, Integer>> phenotypeMap = (Map<Id<Hotspot>, Map<ChargerType, Integer>>) individual.getPhenotype();

                // Write each Hotspot ID, Charger Type, and Plug Count as a row
                for (Map.Entry<Id<Hotspot>, Map<ChargerType, Integer>> hotspotEntry : phenotypeMap.entrySet()) {
                    Id<Hotspot> hotspotId = hotspotEntry.getKey();
                    Map<ChargerType, Integer> chargerMap = hotspotEntry.getValue();

                    // Write each charger type and plug count for this hotspot
                    for (Map.Entry<ChargerType, Integer> chargerEntry : chargerMap.entrySet()) {
                        ChargerType chargerType = chargerEntry.getKey();
                        int plugCount = chargerEntry.getValue();
                        writer.write(hotspotId + ", " + chargerType + ", " + plugCount + "\n");
                    }
                }

                // Add a blank line for separation before objectives
                writer.write("\nObjectives\n");

                // Write each objective as a key-value pair
                for (Entry<Objective, Value<?>> objective : individual.getObjectives()) {
                    writer.write(objective.getKey() + ", " + objective.getValue() + "\n");
                }

                // Add a blank line between solutions
                writer.write("\n\n");
            }

            System.out.println("Solutions written to file: " + filePath);

        } catch (IOException e) {
            System.out.println("Error writing to file: " + e.getMessage());
        }
    }

    

    private static class ChargerProblemModule extends ProblemModule {
        
        public ChargerProblemModule() {
        	
        }
    	
        @Override
        protected void config() {
        	
            bindProblem(ChargerCreatorV2.class, ChargerDecoderV2.class, ChargerEvaluatorV2.class);
           
        }
    }

    private static class ChargerCreatorV2 implements Creator<CompositeGenotype<String, Genotype>> {

    	MapToArray<String> variablesType;
    	MapToArray<String> variablesPlug;
    	Random random ;
    	Map<ChargerType, Double> setupCostPerChargerType = new HashMap<>();
		Map<ChargerType, Double> operationCostPerChargerType = new HashMap<>();


		double setUpBudget = 1284600*5; // Example budget, adjust as necessary
		double operationBudget = 1284600*1.6;//Example operation budget, adjust as necessary



		
    	
    	@Inject
    	public ChargerCreatorV2(){
    		this.random = new Random();
    		String HotspotFile = "data/10p/chargerCoordsNew.csv";// only has the hotspot name and coordinates. 
    		BufferedReader bf = null;
    		String line = null;

    		List<Id<Hotspot>> hotspots = new ArrayList<>();
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
    		Map<String,Double> variablesType = new HashMap<>();
    		Map<String,Double> variablesPlug = new HashMap<>();
    		hotspots.forEach(c->{
    			if(c.toString().contains("dynamic")) {
    				variablesType.put(c.toString()+seperator+typeKey, 0.);
    				variablesPlug.put(c.toString()+seperator+plugCountkey, 0.);
    				
    			}
    		});
    		this.variablesType = new MapToArray<String>("variablesType",variablesType.keySet());
    		this.variablesPlug = new MapToArray<String>("variablesPlug",variablesPlug.keySet());
    		// Define the setup and operation costs for each charger type
    		setupCostPerChargerType.put(ChargerType.level1, 5000.0); // Example setup cost for level1 charger
    		setupCostPerChargerType.put(ChargerType.level2, 10000.0); // Example setup cost for level2 charger
    		setupCostPerChargerType.put(ChargerType.fast, 20000.0);   // Example setup cost for fast charger

    		operationCostPerChargerType.put(ChargerType.level1, 200.0); // Example operation cost for level1 charger
    		operationCostPerChargerType.put(ChargerType.level2, 400.0); // Example operation cost for level2 charger
    		operationCostPerChargerType.put(ChargerType.fast, 800.0);   // Example operation cost for fast charger
    	}
    	
    	@Override
        public CompositeGenotype<String, Genotype> create() {
            // IntegerGenotype for charger type (0–2) to represent ChargerType enum values
            IntegerGenotype typeGenotype = new IntegerGenotype(0, ChargerType.values().length - 1);
            int numberOfTypeVariables = variablesType.getKeySet().size();
            typeGenotype.init(random, numberOfTypeVariables);

            // IntegerGenotype for plug count (1–10)
            IntegerGenotype plugGenotype = new IntegerGenotype(0, 5);
            int numberOfPlugVariables = variablesPlug.getKeySet().size();
            plugGenotype.init(random, numberOfPlugVariables);
            // Random reduction factors for adjustment (e.g., 50% probability for each reduction)
            double plugReductionProbability = 0.25;
            double typeReductionProbability = 0.15;
            
            // Calculate initial setup and operation costs
            double setupCost = calculateTotalSetupCost(typeGenotype, plugGenotype);
            double operationCost = calculateTotalOperationCost(typeGenotype, plugGenotype);
            // Adjust until the solution meets budget constraints
            int maxTry = 30;
            int trial = 0;
            double probabiltiyBudgetViolation = 0.1;
            double probBudgetViolationForThisInstance = random.nextDouble();
            while (setupCost > setUpBudget || operationCost > operationBudget) {
                // Calculate how far off we are from the budget, used to scale probabilities
                double setupScalingFactor = Math.max(0.1, (setupCost - setUpBudget) / setUpBudget);
                double operationScalingFactor = Math.max(0.1, (operationCost - operationBudget) / operationBudget);

                // Dynamically adjust probabilities based on scaling factors
                double adjustedPlugReductionProb = plugReductionProbability * setupScalingFactor;
                double adjustedTypeReductionProb = typeReductionProbability * operationScalingFactor;

                for (int i = 0; i < typeGenotype.size(); i++) {
                    // Randomly decide to reduce plug count, with a higher chance if far from budget
                    if (random.nextDouble() < adjustedPlugReductionProb && plugGenotype.get(i) > 1) {
                        plugGenotype.set(i, plugGenotype.get(i) - 1);
                    }

                    // Randomly decide to downgrade charger type
                    if (random.nextDouble() < adjustedTypeReductionProb && typeGenotype.get(i) > 0) {
                        typeGenotype.set(i, typeGenotype.get(i) - 1);
                    }
                }

                    // Recalculate costs after adjustments
                    setupCost = calculateTotalSetupCost(typeGenotype, plugGenotype);
                    operationCost = calculateTotalOperationCost(typeGenotype, plugGenotype);

                    // Exit early if costs meet budget constraints
                    if (setupCost <= setUpBudget && operationCost <= operationBudget) {
                        break;
                    }
                    trial++;
                    if(probBudgetViolationForThisInstance<probabiltiyBudgetViolation && trial>maxTry) break;
                }
                

            // Combine into a CompositeGenotype
            CompositeGenotype<String, Genotype> compositeGenotype = new CompositeGenotype<>();
            compositeGenotype.put("type", typeGenotype);
            compositeGenotype.put("plug", plugGenotype);

            return compositeGenotype;
       }
    	
    	
    	private double calculateTotalSetupCost(IntegerGenotype typeGenotype, IntegerGenotype plugGenotype) {
    	    double totalSetupCost = 0.0;

    	    for (int i = 0; i < typeGenotype.size(); i++) {
    	        ChargerType chargerType = mapToChargerType(typeGenotype.get(i)); // Converts the integer to ChargerType
    	        int plugCount = plugGenotype.get(i); // Number of plugs at this index

    	        // Calculate the setup cost for this charger type and plug count
    	        totalSetupCost += setupCostPerChargerType.get(chargerType) * plugCount;
    	    }

    	    return totalSetupCost;
    	}

    	private double calculateTotalOperationCost(IntegerGenotype typeGenotype, IntegerGenotype plugGenotype) {
    	    double totalOperationCost = 0.0;

    	    for (int i = 0; i < typeGenotype.size(); i++) {
    	        ChargerType chargerType = mapToChargerType(typeGenotype.get(i));
    	        int plugCount = plugGenotype.get(i);

    	        // Calculate the operation cost for this charger type and plug count
    	        totalOperationCost += operationCostPerChargerType.get(chargerType) * plugCount;
    	    }

    	    return totalOperationCost;
    	}
    	
    	private ChargerType mapToChargerType(Integer value) {
	        if (value == 0) {
	            return ChargerType.level1;
	        } else if (value == 1) {
	            return ChargerType.level2;
	        } else {
	            return ChargerType.fast;
	        }
	    }
    }

    private static class ChargerDecoderV2 implements Decoder<CompositeGenotype<String, IntegerGenotype>, Map<Id<Hotspot>, Map<ChargerType, Integer>>> {
    	Map<ChargerType, Double> setupCostPerChargerType = new HashMap<>();
		Map<ChargerType, Double> operationCostPerChargerType = new HashMap<>();


		double setUpBudget = 1284600*5; // Example budget, adjust as necessary
		double operationBudget = 1284600*1.6;//Example operation budget, adjust as necessary
    	MapToArray<String> variablesType;
    	MapToArray<String> variablesPlug;
        
    	@Inject
    	public ChargerDecoderV2() {
    		String HotspotFile = "data/10p/chargerCoordsNew.csv";// only has the hotspot name and coordinates. 
    		BufferedReader bf = null;
    		String line = null;

    		List<Id<Hotspot>> hotspots = new ArrayList<>();
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
    		Map<String,Double> variablesType = new HashMap<>();
    		Map<String,Double> variablesPlug = new HashMap<>();
    		
    		hotspots.forEach(c->{
    			if(c.toString().contains("dynamic")) {
    				variablesType.put(c.toString()+seperator+typeKey, 0.);
    				variablesPlug.put(c.toString()+seperator+plugCountkey, 0.);
    				
    			}
    		});
    		this.variablesType = new MapToArray<String>("variablesType",variablesType.keySet());
    		this.variablesPlug = new MapToArray<String>("variablesPlug",variablesPlug.keySet());
    		// Define the setup and operation costs for each charger type
    		setupCostPerChargerType.put(ChargerType.level1, 5000.0); // Example setup cost for level1 charger
    		setupCostPerChargerType.put(ChargerType.level2, 10000.0); // Example setup cost for level2 charger
    		setupCostPerChargerType.put(ChargerType.fast, 20000.0);   // Example setup cost for fast charger

    		operationCostPerChargerType.put(ChargerType.level1, 200.0); // Example operation cost for level1 charger
    		operationCostPerChargerType.put(ChargerType.level2, 400.0); // Example operation cost for level2 charger
    		operationCostPerChargerType.put(ChargerType.fast, 800.0);   // Example operation cost for fast charger
    	}
    	
    	
    	 @Override
    	    public Map<Id<Hotspot>, Map<ChargerType, Integer>> decode(CompositeGenotype<String, IntegerGenotype> genotype) {
    	        Map<Id<Hotspot>, Map<ChargerType, Integer>> solutionMap = new HashMap<>();

    	        // Retrieve individual genotypes for type and plug
    	        IntegerGenotype typeGenotype = genotype.get("type");
    	        IntegerGenotype plugGenotype = genotype.get("plug");

    	        // Convert types to ChargerType and map them with plug counts
    	        int typeIndex = 0;
    	        for (String key : variablesType.getKeySet()) {
    	            int typeValue = typeGenotype.get(typeIndex++);
    	            ChargerType chargerType = mapToChargerType(typeValue); // Convert integer to ChargerType
    	            Id<Hotspot> hotspotId = Id.create(key.split("___")[0],Hotspot.class); // Extract Id from key

    	            // Get the corresponding plug count for the same hotspot
    	            int plugValue = plugGenotype.get(variablesPlug.getKeySet().indexOf(hotspotId.toString() + "___plug"));

    	            // Populate the nested map structure
    	            solutionMap.computeIfAbsent(hotspotId, k -> new HashMap<>()).put(chargerType, plugValue);
    	        }

    	        return solutionMap;
    	    }

    	 private ChargerType mapToChargerType(Integer value) {
    	        if (value == 0) {
    	            return ChargerType.level1;
    	        } else if (value == 1) {
    	            return ChargerType.level2;
    	        } else {
    	            return ChargerType.fast;
    	        }
    	    }
    }
}

