package locationChoice;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.math.linear.MatrixUtils;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NetworkUtils;
import org.matsim.facilities.ActivityFacility;

import de.xypron.jcobyla.Calcfc;
import de.xypron.jcobyla.Cobyla;
import de.xypron.jcobyla.CobylaExitStatus;

public class SecondStepTrial {
	public static void main(String[] args) throws IOException {
		String HotspotFile = "data/10p/chargerCoordsNew.csv";// only has the hotspot name and coordinates. 
		String facilityFile = "data/10p/features_noDuration.csv";// have facility features with x, y and the usage of ev and non ev users including their activity durations
		
		BufferedReader bf_f = new BufferedReader(new FileReader(new File(facilityFile)));
		
		String header = bf_f.readLine();
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
		while((line = bf_f.readLine())!=null) {
			String[] part = line.split(",");
			Id<ActivityFacility> facId = Id.create(part[0],ActivityFacility.class);
			Map<String,Double> feature = new HashMap<>();
			for(int i=0;i<keys.size();i++) {
				feature.put(keys.get(i), Double.parseDouble(part[i+1]));
			}
			features.put(facId, feature);
		}
		bf_f.close();
		
		BufferedReader bf = new BufferedReader(new FileReader(new File(HotspotFile)));
		
		bf.readLine();
		
		line = null;
		Map<ChargerType,Double> chargerPower = new HashMap<>();
		chargerPower.put(ChargerType.fast, 1000*50.);
		chargerPower.put(ChargerType.level1, 1000*30.);
		chargerPower.put(ChargerType.level2, 1000*10.);
		chargerPower.put(ChargerType.home, 1000*6.);
		
		Map<ChargerType, Double> setupCostPerChargerType = new HashMap<>();
		Map<ChargerType, Double> operationCostPerChargerType = new HashMap<>();
		double setUpBudget = 500000; // Example budget, adjust as necessary
		double operationBudget = 3000;//Example operation budget, adjust as necessary
		
		// Define the setup and operation costs for each charger type
		setupCostPerChargerType.put(ChargerType.level1, 5000.0); // Example setup cost for level1 charger
		setupCostPerChargerType.put(ChargerType.level2, 10000.0); // Example setup cost for level2 charger
		setupCostPerChargerType.put(ChargerType.fast, 20000.0);   // Example setup cost for fast charger

		operationCostPerChargerType.put(ChargerType.level1, 200.0); // Example operation cost for level1 charger
		operationCostPerChargerType.put(ChargerType.level2, 400.0); // Example operation cost for level2 charger
		operationCostPerChargerType.put(ChargerType.fast, 800.0);   // Example operation cost for fast charger

		
		
		
		Hotspot.setPowerPerChargerType(chargerPower);
		Map<Id<Hotspot>,Hotspot> hotspots = new HashMap<>();
		
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
			h.setPlugCountPerChargerType(Map.of(type,plugCount));
			h.setCoord(new Coord(x,y));
			hotspots.put(h.getHotspotId(), h);
		}
		bf.close();
		
		DemandAllocationModel model = new DemandAllocationModel(hotspots, features, featureKeys);
		
		Map<Id<Hotspot>,Map<ChargerType,Integer>> chargers = new HashMap<>();
		hotspots.entrySet().forEach(e->{
			chargers.put(e.getKey(), new HashMap<>(e.getValue().getPlugCountPerChargerType()));
		});
		
//		Map<Id<ActivityFacility>,Double> demand = new HashMap<>();
//		
//		features.entrySet().forEach(e->{
//			demand.put(e.getKey(), e.getValue().get(Hotspot.EvUserString+"_"+Hotspot.activityNumberString));
//		});
//		
//		model.allocateDemand(chargers, null);
		System.out.println("Done");
		
		
		String plugCountkey = "plug";
		String typeKey = "type";
		String seperator = "___";
		
		
		//create the variables
		Map<String,Double> variables = new HashMap<>();
		Map<String,Double> variablesUpperLimit = new HashMap<>();
		Map<String,Double> variablesLowerLimit = new HashMap<>();
		
		chargers.entrySet().forEach(c->{
			if(c.getKey().toString().contains("dynamic")) {
				variables.put(c.getKey().toString()+seperator+typeKey, 0.);
				variables.put(c.getKey().toString()+seperator+plugCountkey, 0.);
				
				variablesLowerLimit.put(c.getKey().toString()+seperator+typeKey, 0.);
				variablesLowerLimit.put(c.getKey().toString()+seperator+plugCountkey, 0.);
				
				variablesUpperLimit.put(c.getKey().toString()+seperator+typeKey, 1.);
				variablesUpperLimit.put(c.getKey().toString()+seperator+plugCountkey, 1.);
				
			}
		});
		
		//Read the zones file
		Network zonesNet = NetworkUtils.createNetwork();
		BufferedReader bf_zones = new BufferedReader(new FileReader(new File("zones.csv")));
		bf_zones.readLine();
		
		line = null;
		while((line = bf_zones.readLine())!=null) {
			String[] part = line.split(",");
			NetworkUtils.createAndAddNode(zonesNet, Id.createNodeId(part[0]), new Coord(Double.parseDouble(part[1]),Double.parseDouble(part[2])));
			zonesNet.getNodes().get(Id.createNodeId(part[0])).getAttributes().putAttribute("pricing multiplier", Double.parseDouble(part[3]));
			zonesNet.getNodes().get(Id.createNodeId(part[0])).getAttributes().putAttribute("Power Limit",Double.parseDouble(part[4]));
		}
		bf_zones.close();
		Map<Id<Node>,Set<Id<Hotspot>>> chargerToZonesAssignment = new HashMap<>();
		hotspots.entrySet().forEach(h->{
			Node zone = NetworkUtils.getNearestNode(zonesNet, h.getValue().getCoord());
			if(!chargerToZonesAssignment.containsKey(zone.getId()))chargerToZonesAssignment.put(zone.getId(), new HashSet<>());
			chargerToZonesAssignment.get(zone.getId()).add(h.getKey());
		});
		
		MapToArray<String> variablesMapToArray = new MapToArray<String>("variables",variables.keySet());
		Calcfc newFunc = new Calcfc() {

			@Override
			public double compute(int n, int m, double[] x, double[] con) {
				
				Map<String,Double> variables = variablesMapToArray.getMap(x);
				
				Map<Id<Hotspot>, ChargerType> chargerTypes = new HashMap<>();
			    Map<Id<Hotspot>, Integer> plugCounts = new HashMap<>();

		        for (Map.Entry<String, Double> entry : variables.entrySet()) {
		            String key = entry.getKey();
		            double value = entry.getValue();

		            // Extract the parts from the key
		            String[] parts = key.split(seperator);
		            Id<Hotspot> hotspotId = Id.create(parts[0],Hotspot.class);
		            String typeOrPlug = parts[1];

		            // Process type and plug separately
		            if (typeOrPlug.equals(typeKey)) {
		                // Map to charger type
		                ChargerType chargerType = mapToChargerType(value);
		                chargerTypes.put(hotspotId, chargerType);
		            } else if (typeOrPlug.equals(plugCountkey)) {
		                // Round to nearest integer for plug count
		                int plugCount = (int) Math.round(value)*10;
		                plugCounts.put(hotspotId, plugCount);
		            }
		        }
		        
		        chargerTypes.entrySet().forEach(c->{
		        	chargers.get(c.getKey()).clear();
		        	chargers.get(c.getKey()).put(c.getValue(), plugCounts.get(c.getKey()));
		        });
				
		        model.allocateDemand(chargers, null);
		        
		     // Calculate total setup and operational costs separately
		        double setupCostTotal = 0.0;
		        double operationalCostTotal = 0.0;

		        // Setup cost: Only for dynamic chargers
		        for (Map.Entry<Id<Hotspot>, ChargerType> entry : chargerTypes.entrySet()) {
		            Id<Hotspot> hotspotId = entry.getKey();
		            ChargerType chargerType = entry.getValue();
		            int plugCount = plugCounts.get(hotspotId);

		            setupCostTotal += setupCostPerChargerType.get(chargerType) * plugCount;
		        }

		        // Operational cost: For all chargers, both fixed and dynamic
		        for (Map.Entry<Id<Hotspot>, Map<ChargerType, Integer>> entry : chargers.entrySet()) {
		            Map<ChargerType, Integer> chargerConfiguration = entry.getValue();

		            for (Map.Entry<ChargerType, Integer> config : chargerConfiguration.entrySet()) {
		                ChargerType chargerType = config.getKey();
		                int plugCount = config.getValue();

		                operationalCostTotal += operationCostPerChargerType.get(chargerType) * plugCount;
		            }
		        }

		        // Apply the budget constraints
		        con[0] = setUpBudget - setupCostTotal;            // Setup cost constraint for dynamic chargers
		        con[1] = operationBudget - operationalCostTotal;      // Operational cost constraint for all chargers

		        //Set the power limit constraints
		     // Step 3: Calculate max power draw for each zone and apply constraints
		        int constraintIndex = 2; // Start constraint index after budget constraints
		        for (Entry<Id<Node>, Set<Id<Hotspot>>> zoneEntry : chargerToZonesAssignment.entrySet()) {
		            Id<Node> zoneId = zoneEntry.getKey();
		            double maxAllowedPowerDraw = (double) zonesNet.getNodes().get(zoneId).getAttributes().getAttribute("Power Limit");
		            Set<Id<Hotspot>> zoneHotspots = zoneEntry.getValue();

		            // Initialize an array to store hourly power draw for the 24 hours
		            double[] zoneHourlyPowerDraw = new double[24];

		            // Calculate hourly power draw for each hotspot in the zone
		            for (Id<Hotspot> hotspotId : zoneHotspots) {
		            	
		            	if(!model.getActiveHotspots().containsKey(hotspotId))continue;
		            	
		                double[] hotspotHourlyDemand = model.getHourlyDemandPerCharger().get(hotspotId);
		                if(hotspotHourlyDemand==null)continue;
		               // ChargerType chargerType = chargerTypes.get(hotspotId);
		                double chargerCapacity = model.getActiveHotspots().get(hotspotId)*3600;

		                for (int hour = 0; hour < 24; hour++) {
		                    // Accumulate demand or cap it to the charger's capacity
		                    zoneHourlyPowerDraw[hour] += Math.min(hotspotHourlyDemand[hour]*Math.min(model.getAverageChargingDuration().get(hotspotId), 3600)*model.getAveragePlugPowerAtCharger().get(hotspotId), chargerCapacity);
		                }
		            }

		            // Find the max hourly power draw in the zone
		            double maxHourlyPowerDraw = 0.0;
		            for (double hourlyPower : zoneHourlyPowerDraw) {
		                if (hourlyPower > maxHourlyPowerDraw) {
		                    maxHourlyPowerDraw = hourlyPower;
		                }
		            }

		            // Set the constraint to ensure max draw does not exceed allowed limit
		            con[constraintIndex++] = maxAllowedPowerDraw - maxHourlyPowerDraw;
		        }
		     // Add upper and lower bound constraints at the end
		        for (Map.Entry<String, Double> entry : variables.entrySet()) {
		            double value = entry.getValue();

		            // Lower bound: value should be >= 0
		            con[constraintIndex++] = value - 0.0;

		            // Upper bound: value should be <= 1
		            con[constraintIndex++] = 1.0 - value;
		        }

		        
				return model.getAverageQueueTime();
			}
			
		};
		
		// Set up initial values for variables
		double[] initialValues = variablesMapToArray.getMatrix(variables);
		int n = initialValues.length; // Number of variables
		int m = 2 + chargerToZonesAssignment.size()+2*n; // Number of constraints (2 budgets + zone constraints)

		// Define tolerance and maximum number of iterations
		double tolerance = 1e-6;
		int maxIterations = 100000;

		// Call the optimizer
		CobylaExitStatus result = Cobyla.findMinimum(newFunc, n, m, initialValues, 0.6, tolerance, 3, maxIterations);

		
	}
	
	private static ChargerType mapToChargerType(double value) {
        if (value < 0.33) {
            return ChargerType.level1;
        } else if (value < 0.66) {
            return ChargerType.level2;
        } else {
            return ChargerType.fast;
        }
    }

}
