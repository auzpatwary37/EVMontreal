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
import java.util.Map.Entry;
import java.util.Set;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NetworkUtils;
import org.matsim.facilities.ActivityFacility;
import org.opt4j.core.Objective.Sign;
import org.opt4j.core.Objectives;
import org.opt4j.core.problem.Evaluator;

import com.google.inject.Inject;

import org.apache.commons.math.linear.MatrixUtils;




public class ChargerEvaluatorV2 implements Evaluator<Map<Id<Hotspot>,Map<ChargerType,Integer>>> {

	private DemandAllocationModel demandModel;
	private double setupBudget;
	private double operationBudget;
	private Map<ChargerType, Double> setupCostPerChargerType;
	private Map<ChargerType, Double> operationCostPerChargerType;
	private Network zonesNet;
	private Map<Id<Node>, Set<Id<Hotspot>>> chargerToZonesAssignment;
	MapToArray<String> variableType;
	MapToArray<String> variablePlug;

	private void setUpRequirements() {
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

		Map<ChargerType, Double> setupCostPerChargerType = new HashMap<>();
		Map<ChargerType, Double> operationCostPerChargerType = new HashMap<>();


		double setUpBudget = 1284600*5; // Example budget, adjust as necessary
		double operationBudget = 1284600*1.6;//Example operation budget, adjust as necessary



		// Define the setup and operation costs for each charger type
		setupCostPerChargerType.put(ChargerType.level1, 5000.0); // Example setup cost for level1 charger
		setupCostPerChargerType.put(ChargerType.level2, 10000.0); // Example setup cost for level2 charger
		setupCostPerChargerType.put(ChargerType.fast, 20000.0);   // Example setup cost for fast charger

		operationCostPerChargerType.put(ChargerType.level1, 200.0); // Example operation cost for level1 charger
		operationCostPerChargerType.put(ChargerType.level2, 400.0); // Example operation cost for level2 charger
		operationCostPerChargerType.put(ChargerType.fast, 800.0);   // Example operation cost for fast charger




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
		Map<String,Double> variablesType = new HashMap<>();
		Map<String,Double> variablesPlug = new HashMap<>();
		Map<String,Double> variablesUpperLimit = new HashMap<>();
		Map<String,Double> variablesLowerLimit = new HashMap<>();

		chargers.entrySet().forEach(c->{
			if(c.getKey().toString().contains("dynamic")) {
				variablesType.put(c.getKey().toString()+seperator+typeKey, 0.);
				variablesPlug.put(c.getKey().toString()+seperator+plugCountkey, 0.);

				variablesLowerLimit.put(c.getKey().toString()+seperator+typeKey, 0.);
				variablesLowerLimit.put(c.getKey().toString()+seperator+plugCountkey, 0.);

				variablesUpperLimit.put(c.getKey().toString()+seperator+typeKey, 1.);
				variablesUpperLimit.put(c.getKey().toString()+seperator+plugCountkey, 1.);

			}
		});

		//Read the zones file
		Network zonesNet = NetworkUtils.createNetwork();
		BufferedReader bf_zones = null;
		try {
			bf_zones = new BufferedReader(new FileReader(new File("zones.csv")));
			bf_zones.readLine();
		} catch (FileNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}


		line = null;
		try {
			while((line = bf_zones.readLine())!=null) {
				String[] part = line.split(",");
				NetworkUtils.createAndAddNode(zonesNet, Id.createNodeId(part[0]), new Coord(Double.parseDouble(part[1]),Double.parseDouble(part[2])));
				zonesNet.getNodes().get(Id.createNodeId(part[0])).getAttributes().putAttribute("pricing multiplier", Double.parseDouble(part[3]));
				zonesNet.getNodes().get(Id.createNodeId(part[0])).getAttributes().putAttribute("Power Limit",Double.parseDouble(part[4]));
			}
			bf_zones.close();
		} catch (NumberFormatException | IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		Map<Id<Node>,Set<Id<Hotspot>>> chargerToZonesAssignment = new LinkedHashMap<>();
		hotspots.entrySet().forEach(h->{
			Node zone = NetworkUtils.getNearestNode(zonesNet, h.getValue().getCoord());
			if(!chargerToZonesAssignment.containsKey(zone.getId()))chargerToZonesAssignment.put(zone.getId(), new HashSet<>());
			chargerToZonesAssignment.get(zone.getId()).add(h.getKey());
		});

		this.variableType = new MapToArray<String>("variablesType",variablesType.keySet());
		this.variablePlug = new MapToArray<String>("variablesPlug",variablesPlug.keySet());

		this.demandModel = model;
		this.setupBudget = setUpBudget;
		this.operationBudget = operationBudget;
		this.setupCostPerChargerType = setupCostPerChargerType;
		this.operationCostPerChargerType = operationCostPerChargerType;
		this.zonesNet = zonesNet;
		this.chargerToZonesAssignment = chargerToZonesAssignment;
	}
	@Inject
	public ChargerEvaluatorV2() {
		setUpRequirements();
	}


	public ChargerEvaluatorV2(
			DemandAllocationModel demandModel, 
			double setupBudget, 
			double operationBudget,
			Map<ChargerType, Double> setupCostPerChargerType,
			Map<ChargerType, Double> operationCostPerChargerType,
			Network zonesNet, 
			Map<Id<Node>, Set<Id<Hotspot>>> chargerToZonesAssignment) {

		this.demandModel = demandModel;
		this.setupBudget = setupBudget;
		this.operationBudget = operationBudget;
		this.setupCostPerChargerType = setupCostPerChargerType;
		this.operationCostPerChargerType = operationCostPerChargerType;
		this.zonesNet = zonesNet;
		this.chargerToZonesAssignment = chargerToZonesAssignment;
	}

	@Override
	public Objectives evaluate(Map<Id<Hotspot>,Map<ChargerType, Integer>> candidateSolution) {
		Objectives objectives = new Objectives();

		double[] con = new double[this.chargerToZonesAssignment.size()+2];
		//        Map<Id<Hotspot>, ChargerType> chargerTypes = new HashMap<>();
		//        Map<Id<Hotspot>, Integer> plugCounts = new HashMap<>();
		//
		//        // Decode candidate solution into charger types and plug counts
		//        for (Map.Entry<String, Double> entry : candidateSolution.entrySet()) {
		//            String key = entry.getKey();
		//            double value = entry.getValue();
		//            String[] parts = key.split("___");
		//            Id<Hotspot> hotspotId = Id.create(parts[0], Hotspot.class);
		//
		//            if (parts[1].equals("type")) {
		//                chargerTypes.put(hotspotId, mapToChargerType(value));
		//            } else if (parts[1].equals("plug")) {
		//                plugCounts.put(hotspotId, (int) Math.round(value * 10));
		//            }
		//        }
		//
		//        // Set configurations in DemandAllocationModel
		//        chargerTypes.forEach((hotspotId, type) -> {
		//            demandModel.getHotspots().get(hotspotId).getPlugCountPerChargerType().clear();
		//            demandModel.getHotspots().get(hotspotId).getPlugCountPerChargerType().put(type,plugCounts.get(hotspotId));
		//        });

		candidateSolution.entrySet().forEach(e->{
			this.demandModel.getHotspots().get(e.getKey()).setPlugCountPerChargerType(e.getValue());
		});

		demandModel.allocateDemand(null,null);  // This call updates queue times based on the model
		// Calculate total setup and operational costs separately
		double setupCostTotal = 0.0;
		double operationalCostTotal = 0.0;

		// Setup cost: Only for dynamic chargers

		// Iterate through each hotspot in the candidate solution
		for (Map.Entry<Id<Hotspot>, Map<ChargerType, Integer>> hotspotEntry : candidateSolution.entrySet()) {
			Id<Hotspot> hotspotId = hotspotEntry.getKey();
			Map<ChargerType, Integer> chargerMap = hotspotEntry.getValue();

			// For each charger type and plug count at the current hotspot
			for (Map.Entry<ChargerType, Integer> chargerEntry : chargerMap.entrySet()) {
				ChargerType chargerType = chargerEntry.getKey();
				int plugCount = chargerEntry.getValue();

				// Add to the setup cost based on charger type and plug count
				setupCostTotal += setupCostPerChargerType.get(chargerType) * plugCount;
			}
		}

		// Operational cost: For all chargers, both fixed and dynamic
		for (Map.Entry<Id<Hotspot>, Map<ChargerType, Integer>> entry : this.demandModel.getChargerAllocation().entrySet()) {
			Map<ChargerType, Integer> chargerConfiguration = entry.getValue();

			for (Map.Entry<ChargerType, Integer> config : chargerConfiguration.entrySet()) {
				ChargerType chargerType = config.getKey();
				int plugCount = config.getValue();

				operationalCostTotal += operationCostPerChargerType.get(chargerType) * plugCount;
			}
		}

		// Apply the budget constraints
		con[0] = setupBudget - setupCostTotal;            // Setup cost constraint for dynamic chargers
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

				if(!this.demandModel.getActiveHotspots().containsKey(hotspotId))continue;

				double[] hotspotHourlyDemand = this.demandModel.getHourlyDemandPerCharger().get(hotspotId);
				if(hotspotHourlyDemand==null)continue;
				// ChargerType chargerType = chargerTypes.get(hotspotId);
				double chargerCapacity = this.demandModel.getActiveHotspots().get(hotspotId)*3600;

				for (int hour = 0; hour < 24; hour++) {
					// Accumulate demand or cap it to the charger's capacity
					zoneHourlyPowerDraw[hour] += Math.min(hotspotHourlyDemand[hour]*Math.min(this.demandModel.getAverageChargingDuration().get(hotspotId), 3600)*this.demandModel.getAveragePlugPowerAtCharger().get(hotspotId), chargerCapacity);
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
			con[constraintIndex++] = maxAllowedPowerDraw/3600000 - maxHourlyPowerDraw/3600000;
		}

		double obj = demandModel.getAverageQueueTime();

		//        for(double d:con) {
		//        	obj+=d*d;
		//        }
		//        

		objectives.add("Queue", Sign.MIN, obj);
		objectives.add("budget_1", Sign.MIN, -1*Math.min(con[0], 0));
		objectives.add("budget_2", Sign.MIN, -1*Math.min(con[1], 0));
		objectives.add("zone1",  Sign.MIN, -1*Math.min(con[2], 0));
		objectives.add("zone2",  Sign.MIN, -1*Math.min(con[3], 0));
		objectives.add("zone3",  Sign.MIN, -1*Math.min(con[4], 0));
		objectives.add("zone4",  Sign.MIN, -1*Math.min(con[5], 0));
		objectives.add("zone5",  Sign.MIN, -1*Math.min(con[6], 0));
		objectives.add("zone6",  Sign.MIN, -1*Math.min(con[7], 0));

		return objectives;
	}




	private ChargerType mapToChargerType(double value) {
		if (value < 1.0 / 3.0) {
			return ChargerType.level1;
		} else if (value < 2.0 / 3.0) {
			return ChargerType.level2;
		} else {
			return ChargerType.fast;
		}
	}
}

