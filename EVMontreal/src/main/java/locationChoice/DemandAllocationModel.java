package locationChoice;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NetworkUtils;
import org.matsim.facilities.ActivityFacility;


public class DemandAllocationModel {

	private Map<Id<Hotspot>, Hotspot> hotspots;
	private Map<Id<ActivityFacility>, Map<String, Double>> facilities;
	private Map<Id<Hotspot>, Double> activeHotspots;  // Hotspots with capacity
	private Map<Id<ActivityFacility>, List<Hotspot>> nearestChargersPerFacility;  // Facility -> nearest 6 chargers
	private Map<Id<Hotspot>, Map<ChargerType, Integer>> chargerAllocation;  // External input with charger info
	private Map<Id<Hotspot>, Double> AverageChargingDuration;  // Holds t_0
	private Map<Id<Hotspot>, Double> ChargingTime;  // Holds t (charging time including queue)
	private Map<Id<ActivityFacility>, Map<Id<Hotspot>, Double>> facilityToChargerProbability;  // Facility to charger probability
	private Map<Id<ActivityFacility>, Double> demand;  // Demand per facility
	private Map<Id<Hotspot>,Double> demandPerCharger = new HashMap<>();
	private Map<Id<Hotspot>,Double> startTimePerCharger = new HashMap<>();
	private MapToArray<String>featureMap;// the map to array converter for facility features
	private Map<Id<Hotspot>,Double> averagePlugPowerAtCharger = new HashMap<>();
	
	private double servedDemand = 0;
	private int unAllocatedFacilities = 0;
	
	// Add this at the top of your class
	private Map<Id<Hotspot>, double[]> hourlyDemandPerCharger = new HashMap<>();
	private Map<Id<Hotspot>, Integer> peakHourPerCharger = new HashMap<>();

	private double bprAlpha = 0.15;
	private double bprBeta = 1;
	//private double chargerEfficiencyFactor = 0.75;
	private double peakHourFactor = 0.12;
	// Class variable to store the previous total gap
	private double previousTotalGap = Double.MAX_VALUE;  // Initialized to a large value

	private double maxChargerDistance = 2000;

	private double logitScalingParameter = 0.000001;
	
	
	private double averageQueueTime = 0;

	// Constructor
	public DemandAllocationModel(Map<Id<Hotspot>, Hotspot> hotspots, Map<Id<ActivityFacility>, Map<String,Double>> facilities,MapToArray<String> featureMap) {
		this.hotspots = hotspots;
		this.facilities = facilities;
		this.featureMap = featureMap;
	}

	// Main function to run demand allocation
	public void allocateDemand(Map<Id<Hotspot>, Map<ChargerType, Integer>> chargerAllocation, Map<Id<ActivityFacility>, Double> demand) {
		this.chargerAllocation = chargerAllocation;
		if(demand!=null) {
			this.demand = demand;
		}else {
			demand = new HashMap<>();
			for(Entry<Id<ActivityFacility>, Map<String,Double>> f:this.facilities.entrySet()){
				demand.put(f.getKey(), f.getValue().get(Hotspot.EvUserString+"_"+Hotspot.activityNumberString));
			}
			this.demand = demand;
		}
		// Step 1: Initialize variables
		initialize();

		// Step 2: Iteratively update demand, t_0, and t until equilibrium is reached


		int maxIterations = 1000;  // Set the maximum number of iterations

		for (int iteration = 1; iteration <= maxIterations; iteration++) {
			System.out.println("Iteration: " + iteration);

			// Step 3: Calculate allocation probabilities using the logit model            calculateAllocationProbability();
			this.calculateAllocationProbability();
			// Step 4: Update demand, t_0, and t using MSA
			boolean equilibrium = update(iteration);

			// Break the loop if equilibrium is reached
			if (equilibrium) {
				System.out.println("Equilibrium reached at iteration: " + iteration);
				this.outputMetrics();
				break;
			}

			// If max iteration is reached without equilibrium
			if (iteration == maxIterations) {
				System.out.println("Max iterations reached without equilibrium.");
				this.outputMetrics();
			}
		}

	}

	// Initialization function to set active hotspots, nearest chargers, etc.
	private void initialize() {
		activeHotspots = new HashMap<>();
		nearestChargersPerFacility = new HashMap<>();
		AverageChargingDuration = new HashMap<>();
		ChargingTime = new HashMap<>();
		facilityToChargerProbability = new HashMap<>();
		this.averageQueueTime = 0;

		// Step 1: Calculate active hotspots based on charger allocation
		calculateActiveHotspots();
		calculateAverageChargerPower();

		Network network = NetworkUtils.createNetwork();
		for(Id<Hotspot> hId:this.activeHotspots.keySet()) {
			network.addNode(NetworkUtils.createNode(Id.createNodeId(hId.toString()),this.hotspots.get(hId).getCoord()));
			this.demandPerCharger.put(hId, 0.);
			this.startTimePerCharger.put(hId, 0.);
		}


		// Step 2: For each facility, find the 6 nearest chargers and initialize probabilities
		for (Id<ActivityFacility> facilityId : facilities.keySet()) {
			// Find the nearest 6 chargers for this facility
			List<Hotspot> nearestChargers = findNearestChargers(facilityId,network);
			nearestChargersPerFacility.put(facilityId, nearestChargers);

			// Step 2.1: Initialize facility-to-charger probabilities to be equal (1/6)
			Map<Id<Hotspot>, Double> chargerProbabilities = new HashMap<>();
			double equalProbability = 1.0 / nearestChargers.size();  // Equal probability for each charger

			for (Hotspot charger : nearestChargers) {
				chargerProbabilities.put(charger.getHotspotId(), equalProbability);  // Set equal probability for each charger
			}

			facilityToChargerProbability.put(facilityId, chargerProbabilities);  // Store the probabilities for this facility
		}

		// Step 3: Initialize average charging duration (t_0) and charging time (t)
		initializeChargingDurations();
	}


	// Initialize average charging duration t_0 and charging time t
	private void initializeChargingDurations() {
		for (Map.Entry<Id<Hotspot>, Map<ChargerType, Integer>> entry : chargerAllocation.entrySet()) {
			if(this.activeHotspots.containsKey(entry.getKey())) {
				Id<Hotspot> hotspotId = entry.getKey();
				double t_0 = calculateInitialChargingDuration(entry.getValue());  // Calculate t_0 from charger type and power
				AverageChargingDuration.put(hotspotId, t_0);  // Initialize with t_0
				ChargingTime.put(hotspotId, t_0);  // Initialize charging time with t_0 (no queue at the beginning)
			}
		}
	}

	// Placeholder to calculate initial t_0 based on charger type and plug count
	private double calculateInitialChargingDuration(Map<ChargerType, Integer> chargers) {
		// Implement logic to calculate t_0 based on charger power and standard EV battery capacity
		// Placeholder example:
		double standardEVBatteryCapacity = 50;  // kWh (example)
		double totalPower = chargers.keySet().stream().mapToDouble(type -> Hotspot.powerPerChargerType.get(type) * chargers.get(type)).sum();
		if(totalPower==0) {
			System.out.println("averageDuration is infinite!!!");
		}
		return standardEVBatteryCapacity / totalPower;  // Charging time t_0 = Battery capacity / total power

	}

	// Calculate active hotspots based on the number of plugs and charger type
	private void calculateActiveHotspots() {
		for (Map.Entry<Id<Hotspot>, Map<ChargerType, Integer>> entry : chargerAllocation.entrySet()) {
			Id<Hotspot> hotspotId = entry.getKey();
			Map<ChargerType, Integer> chargers = entry.getValue();

			// Capacity (for now, calculated based on plug count, can be extended)
			double capacity = calculateHotspotCapacity(chargers);
			if(capacity>0)activeHotspots.put(hotspotId, capacity);
		}
	}

	// Method to calculate the capacity of a hotspot in vehicles per hour
	private double calculateHotspotCapacity(Map<ChargerType, Integer> chargers) {
		double totalPowerCapacity = 0.0;

		// Sum up the total power output of all chargers
		for (Map.Entry<ChargerType, Integer> entry : chargers.entrySet()) {
			ChargerType chargerType = entry.getKey();
			int numberOfPlugs = entry.getValue();

			// Get the power output of the charger type
			double powerPerPlug = Hotspot.powerPerChargerType.get(chargerType);

			// Total power for this charger type
			totalPowerCapacity += numberOfPlugs * powerPerPlug;
		}


		return totalPowerCapacity;
	}
	
	

	private Map<Id<Hotspot>, Double> calculateDemandPerCharger_new(double peakHourFactor) {
	    Map<Id<Hotspot>, Double> chargerMaxHourlyDemand = new HashMap<>();

	    // Define a 24-hour period (using an array of 24 bins)
	    int hoursInDay = 24;

	    // Step 1: Initialize the class-level map to store hourly demand for each charger
	    hourlyDemandPerCharger.clear();
	    this.peakHourPerCharger.clear();

	    // Loop through each facility and its nearest chargers
	    for (Id<ActivityFacility> facilityId : facilities.keySet()) {
	        double q_f = demand.get(facilityId);  // Demand from facility f
	        if (q_f == 0) continue;

	        // Get the allocation probabilities for this facility
	        Map<Id<Hotspot>, Double> chargerProbabilities = facilityToChargerProbability.get(facilityId);
	        
	        // Get the average start time of the activities at this facility in seconds and convert to hours
	        double avgStartTimeInSeconds = facilities.get(facilityId).get(Hotspot.EvUserString + "_" + Hotspot.startTime);
	        int startHour = (int) (avgStartTimeInSeconds / 3600) % hoursInDay;  // Convert to hour bin (0-23)

	        // Distribute demand to the appropriate time bin
	        for (Map.Entry<Id<Hotspot>, Double> entry : chargerProbabilities.entrySet()) {
	            Id<Hotspot> chargerId = entry.getKey();
	            double P_f_c = entry.getValue();  // Probability of facility f choosing charger c

	            // Calculate the probability-weighted demand
	            double q_c = q_f * peakHourFactor * P_f_c;

	            // Initialize the hourly demand array for the charger if not already present
	            hourlyDemandPerCharger.putIfAbsent(chargerId, new double[hoursInDay]);
	            double[] hourlyDemand = hourlyDemandPerCharger.get(chargerId);

	            // Add the demand to the specific start hour bin
	            hourlyDemand[startHour] += q_c;
	        }
	    }

	    // Step 2: Calculate the maximum hourly demand for each charger
	    for (Map.Entry<Id<Hotspot>, double[]> entry : hourlyDemandPerCharger.entrySet()) {
	        Id<Hotspot> chargerId = entry.getKey();
	        double[] hourlyDemand = entry.getValue();

	        // Find the maximum value in the hourly bins
	        double maxHourlyDemand = 0.0;
	        int peakHour = 0;
	        for (int hour = 0; hour < hoursInDay; hour++) {
	            double demand = hourlyDemand[hour];
	            if (demand > maxHourlyDemand) {
	                maxHourlyDemand = demand;
	                peakHour = hour;
	            }
	        }

	        // Store the maximum hourly demand for the charger
	        chargerMaxHourlyDemand.put(chargerId, maxHourlyDemand);
	        
	        // Store the peak hour information for the charger
	        peakHourPerCharger.put(chargerId, peakHour);
	    }

	    return chargerMaxHourlyDemand;  // Return the maximum hourly demand per charger
	}


	// Method to calculate vehicular demand for each charger
	private Map<Id<Hotspot>, Double> calculateDemandPerCharger(double peakHourFactor) {
		Map<Id<Hotspot>, Double> chargerDemand = new HashMap<>();

		// Loop through each facility and its nearest chargers
		for (Id<ActivityFacility> facilityId : facilities.keySet()) {
			double q_f = demand.get(facilityId);  // Demand from facility f
			if(q_f==0)continue;
			// Get the allocation probabilities for this facility
			Map<Id<Hotspot>, Double> chargerProbabilities = facilityToChargerProbability.get(facilityId);

			// Calculate demand for each charger
			for (Map.Entry<Id<Hotspot>, Double> entry : chargerProbabilities.entrySet()) {
				Id<Hotspot> chargerId = entry.getKey();
				double P_f_c = entry.getValue();  // Probability of facility f choosing charger c

				// Calculate the demand for charger c
				double q_c = q_f * peakHourFactor * P_f_c;
				if(Double.isNaN(q_c)) {
					System.out.println("q_c is NaN!!!");
				}

				// Update total demand for the charger
				chargerDemand.put(chargerId, chargerDemand.getOrDefault(chargerId, 0.0) + q_c);

			}
		}

		return chargerDemand;  // Return demand per charger
	}


	// Method to calculate average activity duration for each charger
	private void calculateAverageDurationPerCharger(Map<Id<Hotspot>, Double> chargerDemand) {
		Map<Id<Hotspot>, Double> weightedDurationSum = new HashMap<>();  // To track the sum of weighted durations
		Map<Id<Hotspot>, Double> totalDemand = new HashMap<>();  // To track total demand for each charger

		// Loop through each facility and its nearest chargers
		for (Id<ActivityFacility> facilityId : facilities.keySet()) {
			double q_f = demand.get(facilityId);// Demand from facility f
			if(q_f==0)continue;
			double facilityDuration = facilities.get(facilityId).get(Hotspot.EvUserString+"_"+Hotspot.acitivityDurationString);  // Get activity duration

			// Get the allocation probabilities for this facility
			Map<Id<Hotspot>, Double> chargerProbabilities = facilityToChargerProbability.get(facilityId);

			// Calculate weighted average duration for each charger
			for (Map.Entry<Id<Hotspot>, Double> entry : chargerProbabilities.entrySet()) {
				Id<Hotspot> chargerId = entry.getKey();
				double P_f_c = entry.getValue();  // Probability of facility f choosing charger c

				// Calculate weighted duration contribution from this facility
				double durationContribution = P_f_c * q_f * facilityDuration;

				// Update the weighted duration sum for this charger
				double currentDurationSum = weightedDurationSum.getOrDefault(chargerId, 0.0);
				weightedDurationSum.put(chargerId, currentDurationSum + durationContribution);

				// Update the total demand sum for this charger (for later averaging)
				totalDemand.put(chargerId, totalDemand.getOrDefault(chargerId, 0.0) + P_f_c * q_f);
			}
		}

		// Calculate the average duration for each charger (weighted by demand)
		for (Id<Hotspot> chargerId : chargerDemand.keySet()) {
			double durationSum = weightedDurationSum.getOrDefault(chargerId, 0.0);
			double totalDemandForCharger = totalDemand.getOrDefault(chargerId, 0.0);

			// Calculate the average duration (t_0) for this charger
			double averageDuration = totalDemandForCharger > 0 ? durationSum / totalDemandForCharger : 0.0;
			if(Double.isInfinite(averageDuration)) {
				System.out.println("average duration is nan");
			}
			// Store the average duration in the class variable
			AverageChargingDuration.put(chargerId, averageDuration);
		}
	}
	
	// Method to calculate average activity duration for each charger
		private void calculateAverageStartTimePerCharger(Map<Id<Hotspot>, Double> chargerDemand) {
			Map<Id<Hotspot>, Double> weightedStartTimeSum = new HashMap<>();  // To track the sum of weighted durations
			Map<Id<Hotspot>, Double> totalDemand = new HashMap<>();  // To track total demand for each charger

			// Loop through each facility and its nearest chargers
			for (Id<ActivityFacility> facilityId : facilities.keySet()) {
				double q_f = demand.get(facilityId);// Demand from facility f
				if(q_f==0)continue;
				double facilityStartTime = facilities.get(facilityId).get(Hotspot.EvUserString+"_"+Hotspot.startTime);  // Get activity duration

				// Get the allocation probabilities for this facility
				Map<Id<Hotspot>, Double> chargerProbabilities = facilityToChargerProbability.get(facilityId);

				// Calculate weighted average duration for each charger
				for (Map.Entry<Id<Hotspot>, Double> entry : chargerProbabilities.entrySet()) {
					Id<Hotspot> chargerId = entry.getKey();
					double P_f_c = entry.getValue();  // Probability of facility f choosing charger c

					// Calculate weighted duration contribution from this facility
					double startTimeContribution = P_f_c * q_f * facilityStartTime;

					// Update the weighted duration sum for this charger
					double currentStartTimeSum = weightedStartTimeSum.getOrDefault(chargerId, 0.0);
					weightedStartTimeSum.put(chargerId, currentStartTimeSum + startTimeContribution);

					// Update the total demand sum for this charger (for later averaging)
					totalDemand.put(chargerId, totalDemand.getOrDefault(chargerId, 0.0) + P_f_c * q_f);
				}
			}

			// Calculate the average startTime for each charger (weighted by demand)
			for (Id<Hotspot> chargerId : chargerDemand.keySet()) {
				double startTimeSum = weightedStartTimeSum.getOrDefault(chargerId, 0.0);
				double totalDemandForCharger = totalDemand.getOrDefault(chargerId, 0.0);

				// Calculate the average duration (t_0) for this charger
				double averageStartTime = totalDemandForCharger > 0 ? startTimeSum / totalDemandForCharger : 0.0;
				if(Double.isInfinite(averageStartTime)) {
					System.out.println("average duration is nan");
				}
				// Store the average duration in the class variable
				this.startTimePerCharger.put(chargerId, averageStartTime);
			}
		}




	// Find the 6 nearest chargers for a given facility
	private List<Hotspot> findNearestChargers(Id<ActivityFacility> facilityId, Network network) {
		List<Hotspot> hotspots = new ArrayList<>();
		Collection<Node> nodes= NetworkUtils.getNearestNodes(network, new Coord(this.facilities.get(facilityId).get(Hotspot.locationX),this.facilities.get(facilityId).get(Hotspot.locationY)), maxChargerDistance);
		nodes.forEach(n->{
			hotspots.add(this.hotspots.get(Id.create(n.getId().toString(), Hotspot.class)));
		});
		return hotspots;
	}

	// Calculate Euclidean distance between a facility and a hotspot
	private double calculateDistance(Id<ActivityFacility> facilityId, Hotspot hotspot) {
		// Extract coordinates of the facility from the facilityFeatures_raw

		Map<String, Double> facilityFeatureMap = facilities.get(facilityId);  // Convert RealVector to Map
		double x1 = facilityFeatureMap.get(Hotspot.locationX);
		double y1 = facilityFeatureMap.get(Hotspot.locationY);

		// Extract coordinates of the hotspot's centroid
		Double d = NetworkUtils.getEuclideanDistance(new Coord(x1,y1), hotspot.getCoord());

		// Calculate Euclidean distance between the two points
		return d;
	}


	// Calculate allocation probabilities using the numerically stable logit model
	private void calculateAllocationProbability() {
		double betaDistance = -1.0;  // Sensitivity to distance
		double betaTime = -1.0;      // Sensitivity to charging time
		double betaAttractiveness = 1.0; //sensitivity to attractiveness of the charger.
		double betaPrice = -1.0; // sensitivity to attractiveness to the money.
		// Loop through each facility to calculate the probabilities of choosing each charger
		this.facilities.keySet().parallelStream().forEach(facilityId->{
			Map<Id<Hotspot>, Double> chargerProbabilities = new HashMap<>();
			List<Hotspot> nearestChargers = nearestChargersPerFacility.get(facilityId);

			// Step 1: Calculate the utilities for each charger
			Map<Id<Hotspot>, Double> utilities = new HashMap<>();
			double maxUtility = Double.NEGATIVE_INFINITY;

			for (Hotspot charger : nearestChargers) {
				Id<Hotspot> chargerId = charger.getHotspotId();

				// Calculate distance between facility and charger
				double distance = calculateDistance(facilityId, charger);

				// Get the initial charging time t_0 for this charger from class variable
				double t_0 = AverageChargingDuration.get(chargerId);

				// Use t_0 as the initial charging time (before any updates)
				double t = ChargingTime.getOrDefault(chargerId, t_0);
				
				double attractivness = Math.min(t_0*this.averagePlugPowerAtCharger.get(chargerId)/(50*1000*3600),1);
				double price = 0;

				// Utility function for this charger
				double utility = betaDistance * distance + betaTime * t+betaAttractiveness*attractivness+betaPrice*price;
				utilities.put(chargerId, utility);

				// Track the maximum utility for numerical stability
				if (utility > maxUtility) {
					maxUtility = utility;
				}
			}

			// Step 2: Calculate numerically stable probabilities
			double totalUtility = 0.0;
			for (Map.Entry<Id<Hotspot>, Double> entry : utilities.entrySet()) {
				Id<Hotspot> chargerId = entry.getKey();
				double utility = entry.getValue();

				// Numerically stable exponential: exp(utility - maxUtility)
				double stableUtility = Math.exp(this.logitScalingParameter*(utility - maxUtility));
				chargerProbabilities.put(chargerId, stableUtility);

				// Sum up total utility for normalization
				totalUtility += stableUtility;
			}

			// Step 3: Normalize the probabilities
			for (Map.Entry<Id<Hotspot>, Double> entry : chargerProbabilities.entrySet()) {
				Id<Hotspot> chargerId = entry.getKey();
				double stableUtility = entry.getValue();
				double probability = stableUtility / totalUtility;  // Normalize

				// Store the probability in the facilityToChargerProbability map
				facilityToChargerProbability.computeIfAbsent(facilityId, k -> new HashMap<>()).put(chargerId, probability);
			}
		});
		//        for (Id<ActivityFacility> facilityId : facilities.keySet()) {
		//            
		//        }
	}



	// Method to calculate total charging time using VDF (power demand and capacity)
	private double calculateChargingTime(double t_0, double powerDemand, double capacity, double alpha, double beta) {
		if (powerDemand <= capacity) {
			return t_0;  // No delay if power demand is less than or equal to capacity
		} else {
			return t_0 * (1 + alpha * Math.pow((powerDemand / capacity), beta));
		}
	}

	// Helper method to calculate the average charger power for a given hotspot
	private double calculateAverageChargerPower(Id<Hotspot> chargerId) {
		Map<ChargerType, Integer> chargerTypes = chargerAllocation.get(chargerId);
		double totalPower = 0.0;
		int totalPlugs = 0;

		// Calculate weighted average power of all chargers at the hotspot
		for (Map.Entry<ChargerType, Integer> entry : chargerTypes.entrySet()) {
			ChargerType chargerType = entry.getKey();
			int plugCount = entry.getValue();
			totalPower += plugCount * Hotspot.powerPerChargerType.get(chargerType);
			totalPlugs += plugCount;
		}

		return totalPower / totalPlugs;  // Average charger power
	}
	
	private void calculateAverageChargerPower() {
		this.averagePlugPowerAtCharger=new HashMap<>();
		this.activeHotspots.entrySet().stream().forEach(e->{
			this.averagePlugPowerAtCharger.put(e.getKey(), this.calculateAverageChargerPower(e.getKey()));
		});
		
	}





	// Method to update demand, average duration, and charging time using modified MSA with fixed parameters for increasing and decreasing errors
	private boolean update(int iterationCount) {
		double totalGap = 0;
		boolean isEquilibrium = true;  // Assume equilibrium is reached, we will check if it holds
		double tolerance = 0.001;  // Threshold for equilibrium check

		// Fixed parameters for increasing and decreasing errors
		double alphaIncrease = 2;  // Parameter for increasing error
		double alphaDecrease = 0.7;  // Parameter for decreasing error

		// Step 1: Store old demand (for equilibrium check)
		Map<Id<Hotspot>, Double> oldDemand = new HashMap<>(this.demandPerCharger);

		// Step 2: Calculate the new demand for each charger
		Map<Id<Hotspot>, Double> newDemand = calculateDemandPerCharger_new(peakHourFactor);  // New demand calculation

		// Step 3: Calculate the total gap
		for (Id<Hotspot> chargerId : newDemand.keySet()) {
			double oldDemand_c = oldDemand.getOrDefault(chargerId, 0.0);
			double newDemand_c = newDemand.getOrDefault(chargerId, 0.0);

			totalGap += Math.abs(newDemand_c - oldDemand_c);
		}

		// Step 4: Choose the alpha value based on whether the gap is increasing or decreasing
		double alpha = totalGap > previousTotalGap ? alphaIncrease : alphaDecrease;

		// Step 5: Apply modified MSA to update the demand
		for (Id<Hotspot> chargerId : newDemand.keySet()) {
			double oldDemand_c = oldDemand.getOrDefault(chargerId, 0.0);
			double newDemand_c = newDemand.getOrDefault(chargerId, 0.0);

			// Modified MSA formula: oldDemand + (newDemand - oldDemand) * 1 / (1 + alpha)
			double updatedDemand = oldDemand_c + (newDemand_c - oldDemand_c) * 1.0 / (1.0 + alpha);

			// Update the demand variable in class
			demandPerCharger.put(chargerId, updatedDemand);
		}

		// Step 6: Calculate the average duration using the updated (MSA) demand
		calculateAverageDurationPerCharger(demandPerCharger);  // Updates the AverageChargingDuration class variable

		// Step 7: Calculate the charging time using the average duration and the MSA demand
		for (Id<Hotspot> chargerId : demandPerCharger.keySet()) {
			double avgChargerPower = this.averagePlugPowerAtCharger.get(chargerId);
			double updatedDemand_c = demandPerCharger.getOrDefault(chargerId, 0.0);  // MSA demand
			double t_0 = AverageChargingDuration.get(chargerId);  // Updated average duration

			// Convert demand to power demand
			double powerDemand = updatedDemand_c * avgChargerPower*Double.max(t_0, 3600);

			// Get charger capacity in power
			double capacity = activeHotspots.get(chargerId)*3600;  // Capacity is in power (kW)

			// Calculate total charging time t using VDF
			double chargingTime = calculateChargingTime(t_0, powerDemand, capacity, this.bprAlpha, this.bprBeta);

			// Update the charging time in the class variable
			ChargingTime.put(chargerId, chargingTime);
		}

		// Step 8: Check for equilibrium
		for (Id<Hotspot> chargerId : newDemand.keySet()) {
			double oldDemand_c = oldDemand.getOrDefault(chargerId, 0.0);
			double newDemand_c = demandPerCharger.getOrDefault(chargerId, 0.0);

			if (Math.abs(newDemand_c - oldDemand_c) > tolerance) {
				isEquilibrium = false;  // If the change in demand is greater than the tolerance, equilibrium not reached
			}
		}

		// Update the previous total gap for the next iteration
		previousTotalGap = totalGap;

		// Increment iteration count for MSA
		iterationCount++;
		System.out.println("Total gap in iteration " + (iterationCount - 1) + " = " + totalGap);

		return isEquilibrium;  // Return true if equilibrium is reached
	}

	public void outputMetrics() {
		// 1. Average queue (t - t_0) for all active hotspots in hours
		double totalQueueTime = 0.0;
		int activeHotspotCount = 0;

		for (Id<Hotspot> chargerId : activeHotspots.keySet()) {
			double chargingTime = ChargingTime.getOrDefault(chargerId, 0.0);
			double averageDuration = AverageChargingDuration.getOrDefault(chargerId, 0.0);
			double queueTimeInSeconds = chargingTime - averageDuration;  // Calculate the queue time in seconds

			double queueTimeInHours = queueTimeInSeconds / 3600.0;  // Convert to hours

			if (!Double.isNaN(queueTimeInHours) && queueTimeInHours >= 0) {
				totalQueueTime += queueTimeInHours;
				activeHotspotCount++;
			}
		}

		double averageQueueTime = activeHotspotCount > 0 ? totalQueueTime / activeHotspotCount : 0.0;
		System.out.println("Average queue time (t - t_0) in hours for all active hotspots: " + averageQueueTime);
		
		this.averageQueueTime = averageQueueTime;

		// 2. Number of facilities without any assigned hotspots/chargers and with non-zero demand
		int unassignedFacilityCount = 0;

		for (Id<ActivityFacility> facilityId : facilities.keySet()) {
			double facilityDemand = demand.getOrDefault(facilityId, 0.0);  // Get facility demand

			Map<Id<Hotspot>, Double> chargerProbabilities = facilityToChargerProbability.get(facilityId);
			boolean hasAssignedCharger = chargerProbabilities != null && chargerProbabilities.values().stream().anyMatch(probability -> probability > 0);

			// Check if facility has non-zero demand and no assigned charger
			if (facilityDemand > 0 && !hasAssignedCharger) {
				unassignedFacilityCount++;
			}
		}
		this.unAllocatedFacilities = unassignedFacilityCount;
		System.out.println("Number of facilities with non-zero demand and without any assigned hotspots or chargers: " + unassignedFacilityCount);

		// 3. Served demand (sum of facility demand * peak factor - charger demand)
		double totalFacilityDemand = 0.0;
		double totalChargerDemand = 0.0;

		// Sum up facility demand with peak factor
		for (Id<ActivityFacility> facilityId : demand.keySet()) {
			double facilityDemand = demand.getOrDefault(facilityId, 0.0);
			totalFacilityDemand += facilityDemand * peakHourFactor;
		}

		// Sum up hourly demand for each charger from the hourlyDemandPerCharger map
		for (Id<Hotspot> chargerId : hourlyDemandPerCharger.keySet()) {
		    double[] hourlyDemand = hourlyDemandPerCharger.get(chargerId);
		    
		    // Sum the hourly demands to get the total demand across all hours for this charger
		    double chargerTotalHourlyDemand = 0.0;
		    for (double hourlyValue : hourlyDemand) {
		        chargerTotalHourlyDemand += hourlyValue;
		    }

		    // Add to total charger demand across all chargers
		    totalChargerDemand += chargerTotalHourlyDemand;
		}

		// Calculate the served demand
		double servedDemand = totalFacilityDemand - totalChargerDemand;
		
		this.servedDemand = servedDemand;

		System.out.println("Total unserved demand: " + servedDemand*this.peakHourFactor+" out of "+ totalFacilityDemand*this.peakHourFactor);
		writeChargerDemandToFile(this.demandPerCharger,this.AverageChargingDuration,this.ChargingTime,"chargerDemandFromModel.csv");
		this.calculateAverageStartTimePerCharger(this.demandPerCharger);
	}

	public void writeChargerDemandToFile(Map<Id<Hotspot>, Double> chargerDemand, 
			Map<Id<Hotspot>, Double> t0Map, 
			Map<Id<Hotspot>, Double> tMap, 
			String filePath) {
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
			// Write the header
			writer.write("HotspotID,Demand,X,Y,t_0,t\n");

			// Iterate over charger demand entries
			for (Map.Entry<Id<Hotspot>, Double> entry : chargerDemand.entrySet()) {
				Id<Hotspot> hotspotId = entry.getKey();

				// Get demand, coordinates, t_0, and t for each hotspot
				double demand = entry.getValue() * this.peakHourFactor;
				double x = this.hotspots.get(hotspotId).getCoord().getX();
				double y = this.hotspots.get(hotspotId).getCoord().getY();
				double t0 = t0Map.getOrDefault(hotspotId, 0.0); // Default to 0.0 if not present
				double t = tMap.getOrDefault(hotspotId, 0.0);   // Default to 0.0 if not present

				// Write the line with the Hotspot ID, Demand, X, Y, t_0, and t
				writer.write(hotspotId.toString() + "," 
						+ (int) demand + "," 
						+ x + "," 
						+ y + "," 
						+ t0 + "," 
						+ t + "\n");
			}
		} catch (IOException e) {
			System.out.println("Error writing to file: " + e.getMessage());
		}
		
		
	}

	public Map<Id<Hotspot>, double[]> getHourlyDemandPerCharger() {
		return hourlyDemandPerCharger;
	}

	public Map<Id<Hotspot>, Integer> getPeakHourPerCharger() {
		return peakHourPerCharger;
	}

	public double getAverageQueueTime() {
		return averageQueueTime;
	}

	public Map<Id<Hotspot>, Double> getActiveHotspots() {
		return activeHotspots;
	}

	public Map<Id<Hotspot>, Double> getAverageChargingDuration() {
		return AverageChargingDuration;
	}

	public Map<Id<Hotspot>, Double> getAveragePlugPowerAtCharger() {
		return averagePlugPowerAtCharger;
	}

	public double getServedDemand() {
		return servedDemand;
	}

	public int getUnAllocatedFacilities() {
		return unAllocatedFacilities;
	}

	
}