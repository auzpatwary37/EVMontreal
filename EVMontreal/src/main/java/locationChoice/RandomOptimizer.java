package locationChoice;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import org.apache.commons.math.linear.MatrixUtils;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.ev.infrastructure.Charger;
import org.matsim.contrib.ev.infrastructure.ChargerReader;
import org.matsim.contrib.ev.infrastructure.ChargerSpecification;
import org.matsim.contrib.ev.infrastructure.ChargerWriter;
import org.matsim.contrib.ev.infrastructure.ChargingInfrastructureSpecification;
import org.matsim.contrib.ev.infrastructure.ChargingInfrastructureSpecificationImpl;
import org.matsim.contrib.ev.infrastructure.ImmutableChargerSpecification;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.facilities.ActivityFacilities;
import org.matsim.facilities.ActivityFacility;
import org.opt4j.core.Objective;
import org.opt4j.core.Objective.Sign;
import org.opt4j.core.Objectives;

import EVPricing.ChargerPricingProfile;
import EVPricing.ChargerPricingProfileReader;
import EVPricing.ChargerPricingProfileWriter;
import EVPricing.ChargerPricingProfiles;

public class RandomOptimizer {

    // List for tracking zero plug hotspots, where charger type can be altered
    private final List<Id<Hotspot>> zeroPlugHotspots = new ArrayList<>();

    // Map for tracking non-zero plug hotspots by ChargerType
    private final Map<ChargerType, List<Id<Hotspot>>> nonZeroPlugMap = new HashMap<>();

    // Random instance for generating random values
    private final Random random = new Random();
    
    private Map<ChargerType,Map<Id<Hotspot>,Integer>> badSource = new HashMap<>();
    private Map<ChargerType,Map<Id<Hotspot>,Integer>> badSink = new HashMap<>();
    

    // Method to read an existing solution from a file
    public static Map<Id<Hotspot>, Map<ChargerType, Integer>> readSolution(String filePath) {
        Map<Id<Hotspot>, Map<ChargerType, Integer>> solution = new HashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            boolean inHotspotSection = false;

            while ((line = br.readLine()) != null) {
                line = line.trim();

                // Start reading the Hotspot data section
                if (line.equals("Hotspot ID, Charger Type, Plug Count")) {
                    inHotspotSection = true;
                    continue;
                }

                // Detect the end of the Hotspot section
                if (line.isEmpty()) {
                    inHotspotSection = false;
                }

                if (inHotspotSection && !line.equals("Hotspot ID, Charger Type, Plug Count")) {
                    // Parse the hotspot line
                    String[] parts = line.split(", ");
                    if (parts.length == 3) {
                        Id<Hotspot> hotspotId = Id.create(parts[0], Hotspot.class);
                        ChargerType chargerType = ChargerType.valueOf(parts[1].trim());
                        int plugCount = Integer.parseInt(parts[2].trim());

                        // Add the charger type and plug count to the hotspot entry
                        solution.computeIfAbsent(hotspotId, k -> new HashMap<>()).put(chargerType, plugCount);
                    }
                }
            }

            System.out.println("Solution loaded successfully from file: " + filePath);

        } catch (IOException e) {
            System.out.println("Error reading the file: " + e.getMessage());
        }

        return solution;
    }

    // Method to run the optimization
    public void optimize(
            Map<Id<Hotspot>, Map<ChargerType, Integer>> candidateSolution,
            ChargerEvaluatorV2 evaluator,
            int maxIterations, int swapsPerIteration, String bestObjectiveFilePath, String iterationLoggerFilePath) {
    	FileWriter fw = null;
    	List<Double> objectivesToPlot = new ArrayList<>();
    	try {
			fw = new FileWriter(new File(iterationLoggerFilePath), true);
			fw.append("Iteration, bestObjective\n");
			fw.flush();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	
        // Track the best solution and objective value
        double bestObjectiveValue = evaluator.evaluate(candidateSolution).get(new Objective("Queue",Sign.MIN)).getDouble();
        objectivesToPlot.add(bestObjectiveValue);
        Map<Id<Hotspot>, Map<ChargerType, Integer>> bestSolution = deepCopySolution(candidateSolution);

        // Initialize tracking lists and maps
        initializeTrackingLists(candidateSolution);

        for (int i = 0; i < maxIterations; i++) {
            // Perform multiple plug swaps in each iteration
            Map<Id<Hotspot>, Map<ChargerType, Integer>> modifiedSolution = deepCopySolution(candidateSolution);
            List<swapDetails> swaps = new ArrayList<>();
            for (int j = 0; j < swapsPerIteration; j++) {
                swaps.add(performPlugSwap(modifiedSolution));
            }

            // Evaluate the modified solution
            Objectives objectives = evaluator.evaluate(modifiedSolution);
            double currentObjectiveValue = objectives.get(new Objective("Queue", Sign.MIN)).getDouble();

            // Update best solution if current solution is better
            if (currentObjectiveValue < bestObjectiveValue) {
                bestObjectiveValue = currentObjectiveValue;
                bestSolution = deepCopySolution(modifiedSolution);  // Store the best solution
                this.writeSolutionsToFile(bestSolution, bestObjectiveFilePath, bestObjectiveValue);
            }else {
            	for(swapDetails swap:swaps) {
            		if(!this.badSource.containsKey(swap.type))this.badSource.put(swap.type, new HashMap<>());
            		this.badSource.get(swap.type).compute(swap.source, (k,v)->v==null?1:v+1);
            		
            		if(!this.badSink.containsKey(swap.type))this.badSink.put(swap.type, new HashMap<>());
            		this.badSink.get(swap.type).compute(swap.sink, (k,v)->v==null?1:v+1);
            		
            	}
            }
            objectivesToPlot.add(bestObjectiveValue);
            try {
				fw.append(Integer.toString(i)+","+bestObjectiveValue+"\n");
				fw.flush();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
            ConvergencePlot.plotConvergence("Swap algorithm convergence", objectivesToPlot);
        }
        try {
			fw.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        // Output the best solution and objective value found
        System.out.println("Best objective value: " + bestObjectiveValue);
        System.out.println("Best solution: " + bestSolution);
    }
    
 // Method to write optimization details to a file
    public void writeSolutionsToFile( Map<Id<Hotspot>, Map<ChargerType, Integer>> candidateSolution, String filePath, double objective) {
    	try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath, true))) { // Append mode

    		// Iterate over each individual in the archive

    		// Write header for a new solution
    		writer.write("Hotspot ID, Charger Type, Plug Count\n");

    		// Get the phenotype (solution representation)
    		Map<Id<Hotspot>, Map<ChargerType, Integer>> phenotypeMap = candidateSolution;

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

    		writer.write("Queue with constraints" + ", " + objective + "\n");


    		// Add a blank line between solutions
    		writer.write("\n\n");


    		System.out.println("Solutions written to file: " + filePath);

    	} catch (IOException e) {
    		System.out.println("Error writing to file: " + e.getMessage());
    	}
    }

    // Initialize lists and maps for zero and non-zero plug tracking
    private void initializeTrackingLists(Map<Id<Hotspot>, Map<ChargerType, Integer>> solution) {
        zeroPlugHotspots.clear();
        nonZeroPlugMap.clear();

        for (Map.Entry<Id<Hotspot>, Map<ChargerType, Integer>> entry : solution.entrySet()) {
            Id<Hotspot> hotspotId = entry.getKey();
            Map<ChargerType, Integer> chargers = entry.getValue();

            boolean hasNonZeroPlug = false;
            for (Map.Entry<ChargerType, Integer> chargerEntry : chargers.entrySet()) {
                ChargerType chargerType = chargerEntry.getKey();
                int plugCount = chargerEntry.getValue();

                if (plugCount > 0) {
                    nonZeroPlugMap.computeIfAbsent(chargerType, k -> new ArrayList<>()).add(hotspotId);
                    hasNonZeroPlug = true;
                }
            }

            // If no non-zero plugs found, add to zero plug list
            if (!hasNonZeroPlug) {
                zeroPlugHotspots.add(hotspotId);
            }
        }
    }
    
    public static class swapDetails{
    	Id<Hotspot> source;
    	Id<Hotspot> sink;
    	ChargerType type;
    	
    	public swapDetails(Id<Hotspot>source,Id<Hotspot>sink,ChargerType type){
    		this.source = source;
    		this.sink = sink;
    		this.type = type;
    	}
    }

    // Method to perform a single plug swap
    private swapDetails performPlugSwap(Map<Id<Hotspot>, Map<ChargerType, Integer>> solution) {
        // Select a random source charger from non-zero plug hotspots
        ChargerType selectedType = selectRandomChargerType();
        List<Id<Hotspot>> sourceHotspots = new ArrayList<>(nonZeroPlugMap.get(selectedType));

        if (sourceHotspots == null || sourceHotspots.isEmpty()) {
            return null;  // No available source chargers with this type
        }
        
        for(Entry<Id<Hotspot>, Integer> h:this.badSource.get(selectedType).entrySet()) {
        	if(h.getValue()>50)sourceHotspots.remove(h.getKey());
        }

        // Randomly select a source hotspot
        Id<Hotspot> sourceHotspot = sourceHotspots.get(random.nextInt(sourceHotspots.size()));
        int sourcePlugCount = solution.get(sourceHotspot).get(selectedType);

        if (sourcePlugCount < 1) {
            return null;  // Skip if the source has only 1 plug, as we cannot reduce it further
        }

        // Create a pool of sink chargers: same-type non-zero plugs and zero plug chargers
        List<Id<Hotspot>> sinkPool = new ArrayList<>(nonZeroPlugMap.get(selectedType));
        sinkPool.remove(sourceHotspot);  // Remove the source from the sink pool
        sinkPool.addAll(zeroPlugHotspots);  // Add zero-plug hotspots
        
        for(Entry<Id<Hotspot>, Integer> h:this.badSink.get(selectedType).entrySet()) {
        	if(h.getValue()>50)sinkPool.remove(h.getKey());
        }

        if (sinkPool.isEmpty()) {
            return null;  // No available sinks
        }

        // Select a random sink hotspot
        Id<Hotspot> sinkHotspot = sinkPool.get(random.nextInt(sinkPool.size()));

        // Perform the plug swap: decrease plug count on source, increase on sink
        solution.get(sourceHotspot).put(selectedType, sourcePlugCount - 1);
        solution.get(sinkHotspot).put(selectedType, solution.get(sinkHotspot).getOrDefault(selectedType, 0) + 1);

        // If the sink was previously a zero-plug hotspot, update its type and tracking lists
        if (solution.get(sinkHotspot).get(selectedType) == 1) {
            zeroPlugHotspots.remove(sinkHotspot);
            nonZeroPlugMap.computeIfAbsent(selectedType, k -> new ArrayList<>()).add(sinkHotspot);
        }

        // Update source hotspot tracking if it now has zero plugs
        if (solution.get(sourceHotspot).get(selectedType) == 0) {
            nonZeroPlugMap.get(selectedType).remove(sourceHotspot);
            zeroPlugHotspots.add(sourceHotspot);
        }
        
        return new swapDetails(sourceHotspot,sinkHotspot,selectedType);
    }

    // Helper method to create a deep copy of the solution map
    private Map<Id<Hotspot>, Map<ChargerType, Integer>> deepCopySolution(
            Map<Id<Hotspot>, Map<ChargerType, Integer>> original) {

        Map<Id<Hotspot>, Map<ChargerType, Integer>> copy = new HashMap<>();
        for (Map.Entry<Id<Hotspot>, Map<ChargerType, Integer>> entry : original.entrySet()) {
            Map<ChargerType, Integer> chargerMap = new HashMap<>(entry.getValue());
            copy.put(entry.getKey(), chargerMap);
        }
        return copy;
    }

    // Helper method to randomly select a charger type
    private ChargerType selectRandomChargerType() {
        ChargerType[] types = ChargerType.values();
        return types[random.nextInt(types.length)];
    }

    public static void main(String[] args) {
        String filePath = "data\\10p\\solutionsAtIteration_400_2024-11-04_.csv";  // Specify the file path here
        String currentBestSolutionPath = "data\\10p\\solutionsAtIteration_400_2024-11-04_afterRandomOptimization.csv";// specify the file path for storing the current best solution. 
        String iterationLoggerFilePath = "data\\10p\\RandomOptimizationIterationLogger.csv";
        RandomOptimizer optimizer = new RandomOptimizer();
        Map<Id<Hotspot>, Map<ChargerType, Integer>> solution = readSolution(filePath);

        ChargerEvaluatorV2 evaluator = new ChargerEvaluatorV2();

        optimizer.optimize(solution, evaluator, 100, 5,currentBestSolutionPath,iterationLoggerFilePath);
    }
    
    public static class ConvergencePlot extends JFrame {

        public ConvergencePlot(String title, List<Double> objectiveValues) {
            super(title);

            // Create dataset
            XYSeries series = new XYSeries("Queue Objective");
            for (int i = 0; i < objectiveValues.size(); i++) {
                series.add(i, objectiveValues.get(i));
            }

            XYSeriesCollection dataset = new XYSeriesCollection(series);

            // Create chart
            JFreeChart chart = ChartFactory.createXYLineChart(
                    "Convergence Plot",
                    "Iteration",
                    "Queue",
                    dataset,
                    PlotOrientation.VERTICAL,
                    true, true, false);

            // Customize the plot
            XYPlot plot = chart.getXYPlot();
            XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
            renderer.setSeriesPaint(0, Color.RED);
            plot.setRenderer(renderer);

            // Set plot background
            plot.setBackgroundPaint(Color.WHITE);

            // Create Panel
            ChartPanel panel = new ChartPanel(chart);
            setContentPane(panel);
        }
        
        // Static method to create and display the plot
        public static void plotConvergence(String title, List<Double> objectiveValues) {
            SwingUtilities.invokeLater(() -> {
                ConvergencePlot example = new ConvergencePlot(title, objectiveValues);
                example.setSize(800, 400);
                example.setLocationRelativeTo(null);
                example.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
                example.setVisible(true);
            });
        }
    }

    
    public void readSolutionAndCreatePricingProfile(String solutionFileLoc, 
    		String oldPricingProfileFile, String oldChargerFile,String activityFacilityFile,String newPricingProfileFile,String networkFile, 
    		String newChargerFile) {
    	
    	String HotspotFile = "data/10p/chargerCoordsNew.csv";// only has the hotspot name and coordinates. 
		String facilityFile = "data/10p/features_noDuration.csv";// have facility features with x, y and the usage of ev and non ev users including their activity durations

		BufferedReader bf_f = null;
		try {
			bf_f = new BufferedReader(new FileReader(new File(facilityFile)));
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		String header = null;
		try {
			header = bf_f.readLine();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		String[] headers = header.split(",");
		List<String> keys = new ArrayList<>();
		//		for(int i=1;i<headers.length;i++) {
		//			keys.add(headers[i]);
		//		}
		keys.add(Hotspot.locationX);
		keys.add(Hotspot.locationY);
		keys.add(Hotspot.EvUserString+"_"+Hotspot.activityNumberString);
		keys.add(Hotspot.EvUserString+"_"+Hotspot.acitivityDurationString);
		keys.add(Hotspot.nonEvUserString+"_"+Hotspot.activityNumberString);
		keys.add(Hotspot.nonEvUserString+"_"+Hotspot.acitivityDurationString);
		keys.add(Hotspot.EvUserString+"_"+Hotspot.startTime);
		keys.add(Hotspot.nonEvUserString+"_"+Hotspot.startTime);


		MapToArray<String> featureKeys = new MapToArray<String>("featureMap",keys);
		String line = null;
		Map<Id<ActivityFacility>,Map<String,Double>> features = new HashMap<>();
		try {
			while((line = bf_f.readLine())!=null) {
				String[] part = line.split(",");
				Id<ActivityFacility> facId = Id.create(part[0],ActivityFacility.class);
				Map<String,Double> feature = new HashMap<>();
				for(int i=0;i<keys.size();i++) {
					feature.put(keys.get(i), Double.parseDouble(part[i+1]));
				}
				features.put(facId, feature);
			}
		} catch (NumberFormatException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			bf_f.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		BufferedReader bf = null;
		try {
			bf = new BufferedReader(new FileReader(new File(HotspotFile)));
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		try {
			bf.readLine();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		line = null;

		Map<ChargerType,Double> chargerPower = new HashMap<>();
    	chargerPower.put(ChargerType.fast, 1000*50.);
    	chargerPower.put(ChargerType.level1, 1000*30.);
    	chargerPower.put(ChargerType.level2, 1000*10.);
    	chargerPower.put(ChargerType.home, 1000*6.);
    	
    	
    	
    	Hotspot.setPowerPerChargerType(chargerPower);

		Map<Id<Hotspot>,Hotspot> hotspots = new LinkedHashMap<>();

		try {
			while((line = bf.readLine())!=null) {
				String[] part = line.split(",");
				Hotspot h = new Hotspot(part[0], featureKeys);
				Id<ActivityFacility> facId = Id.create(part[5], ActivityFacility.class);
				double x = Double.parseDouble(part[1]);
				double y = Double.parseDouble(part[2]);
				double[] f = new double[featureKeys.getKeySet().size()];
				f[0] = x;
				f[1] = y;
				h.setCentroidFacilityId(facId,MatrixUtils.createRealVector(f));
				String chargerType = part[6];
				ChargerType type = null;
				if(chargerType.equals("Fast"))type = ChargerType.fast;
				else if(chargerType.equals("Level 1")) type = ChargerType.level1;
				else if(chargerType.equals("Level 2")) type = ChargerType.level2;
				int plugCount = Integer.parseInt(part[3]);
				double power = Double.parseDouble(part[4]);
				chargerPower.put(type, power);
				if(!h.getHotspotId().toString().contains("dynamic")) {
					h.setLockedCentroid(true);
				}else {
					type = ChargerType.fast;
					plugCount = 0;
					power = 1000*50.;
				}
				Map<ChargerType,Integer> map = new HashMap<>();
				map.put(type, plugCount);
				h.setPlugCountPerChargerType(map);
				h.setCoord(new Coord(x,y));
				hotspots.put(h.getHotspotId(), h);
			}
			bf.close();

		} catch (NumberFormatException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	
    	
    	Map<Id<Hotspot>, Map<ChargerType, Integer>> solution = readSolution(solutionFileLoc);
    	ChargerPricingProfiles pricingProfiles = new ChargerPricingProfileReader().readChargerPricingProfiles(solutionFileLoc);
    	ChargingInfrastructureSpecification csp = new ChargingInfrastructureSpecificationImpl();
    	new ChargerReader(csp).readFile(oldChargerFile);
    	Config config = ConfigUtils.createConfig();
    	config.facilities().setInputFile(activityFacilityFile);
    	config.network().setInputFile(networkFile);
    	Scenario scn = ScenarioUtils.loadScenario(config);
    	ActivityFacilities facilities = scn.getActivityFacilities();
    	Network net = scn.getNetwork();
    	
    	//create charger and pricing profiles
    	Map<String,double[]> PeakPricing = new HashMap<>(); 
    	double[] nonLinear = null;
    	//Fast peak hour
    	nonLinear = new double[3];
    	nonLinear[0] = 10.00;
    	nonLinear[1] = 10.00;
    	nonLinear[2] = 10.00;
    	PeakPricing.put("Fast", nonLinear);
    	int[] peakTime = new int[] {};
    	int[] offPeakTime = new int[] {0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23};
    	Map<String,double[]> offPeakPricing = new HashMap<>(); 
    	//Fast off peak hour
    	nonLinear = new double[3];
    	nonLinear[0] = 4.0;
    	nonLinear[1] = 4.0;
    	nonLinear[2] = 4.0;
    	
    	offPeakPricing.put("Fast", nonLinear);
    	
    	
    	
    	hotspots.values().forEach(h->{
    		if(solution.containsKey(h.getHotspotId())) {
    			h.setPlugCountPerChargerType(solution.get(h.getHotspotId()));
    			if(!csp.getChargerSpecifications().containsKey(Id.create(h.getHotspotId().toString(), Charger.class))) {
    				h.getPlugCountPerChargerType().entrySet().forEach(e->{
    					ChargerSpecification c = ImmutableChargerSpecification.newBuilder()
    							.id(Id.create(h.getHotspotId().toString(), Charger.class))
    							.linkId(facilities.getFacilities().get(h.getCentroidFacility()).getLinkId())
    							.chargerType(e.getKey().toString())
    							.plugCount(e.getValue())
    							.plugPower(chargerPower.get(e.getKey()))
    							.build();

    					csp.addChargerSpecification(c);

    					ChargerPricingProfile pp = new ChargerPricingProfile(c.getId(), "zone1", 30);
    					for(int i: peakTime) {
    						double[] pprofile = PeakPricing.get(c.getChargerType()).clone();
    						pp.addHourlyPricingProfile(i,applyMultiplier(pprofile,1));
    						pp.addHourlyPricingProfilePerHr(i, applyMultiplier(pprofile,5));

    					}
    					for(int i: offPeakTime) {

    						pp.addHourlyPricingProfile(i, applyMultiplier(offPeakPricing.get(c.getChargerType()),1));
    						pp.addHourlyPricingProfilePerHr(i, applyMultiplier(offPeakPricing.get(c.getChargerType()),5));
    					}
    					pricingProfiles.addChargerPricingProfile(pp);

    				});
    			}
    		}
    	});
    	
    	
    	new ChargerWriter(csp.getChargerSpecifications().values().stream()).write(newChargerFile);
    	new ChargerPricingProfileWriter(pricingProfiles).write(newPricingProfileFile);
    }
    
    public static double[] applyMultiplier(double[] o,double m) {
    	double[] D = new double[o.length];
    	for(int i = 0; i<o.length;i++) {
    		D[i] = o[i]*m;
    	}
    	return D;
    }
}
