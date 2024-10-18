package locationChoice;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.math.linear.RealVector;
import org.matsim.api.core.v01.Id;
import org.matsim.facilities.ActivityFacility;


public class DemanAllocationModel {

    private Map<Id<Hotspot>, Hotspot> hotspots;
    private Map<Id<ActivityFacility>, RealVector> facilities;
    private Map<Id<Hotspot>, Double> activeHotspots;  // Hotspots with capacity
    private Map<Id<ActivityFacility>, List<Hotspot>> nearestChargersPerFacility;  // Facility -> nearest 6 chargers
    private Map<Id<Hotspot>, Map<ChargerType, Integer>> chargerAllocation;  // External input with charger info
    private Map<Id<Hotspot>, Double> AverageChargingDuration;  // Holds t_0
    private Map<Id<Hotspot>, Double> ChargingTime;  // Holds t (charging time including queue)
    private Map<Id<ActivityFacility>, Map<Id<Hotspot>, Double>> facilityToChargerProbability;  // Facility to charger probability
    private Map<Id<ActivityFacility>, Double> demand;  // Demand per facility
    private Map<Id<Hotspot>,Double> demandPerCharger = new HashMap<>();
    private MapToArray<String>featureMap;// the map to array converter for facility features
    private double bprAlpha = 0.5;
    private double bprBeta = 2;
    //private double chargerEfficiencyFactor = 0.75;
    private double peakHourFactor = 1.0;

    // Constructor
    public DemanAllocationModel(Map<Id<Hotspot>, Hotspot> hotspots, Map<Id<ActivityFacility>, RealVector> facilities,MapToArray<String> featureMap) {
        this.hotspots = hotspots;
        this.facilities = facilities;
        this.featureMap = featureMap;
    }

    // Main function to run demand allocation
    public void allocateDemand(Map<Id<Hotspot>, Map<ChargerType, Integer>> chargerAllocation, Map<Id<ActivityFacility>, Double> demand) {
        this.chargerAllocation = chargerAllocation;
        if(demand!=null)this.demand = demand;
        else {
        	demand = new HashMap<>();
        	for(Entry<Id<ActivityFacility>, RealVector> f:this.facilities.entrySet()){
        		demand.put(f.getKey(), this.featureMap.getMap(f.getValue().getData()).get(Hotspot.EvUserString+"_"+Hotspot.activityNumberString));
        	}
        }
        // Step 1: Initialize variables
        initialize();

        // Step 2: Iteratively update demand, t_0, and t until equilibrium is reached

       
        int maxIterations = 1000;  // Set the maximum number of iterations

        for (int iteration = 1; iteration <= maxIterations; iteration++) {
            System.out.println("Iteration: " + iteration);

            // Step 3: Calculate allocation probabilities using the logit model
            calculateAllocationProbability();

            // Step 4: Update demand, t_0, and t using MSA
            boolean equilibrium = update(iteration);

            // Break the loop if equilibrium is reached
            if (equilibrium) {
                System.out.println("Equilibrium reached at iteration: " + iteration);
                break;
            }

            // If max iteration is reached without equilibrium
            if (iteration == maxIterations) {
                System.out.println("Max iterations reached without equilibrium.");
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

        // Step 1: Calculate active hotspots based on charger allocation
        calculateActiveHotspots();

        // Step 2: For each facility, find the 6 nearest chargers and initialize probabilities
        for (Id<ActivityFacility> facilityId : facilities.keySet()) {
            // Find the nearest 6 chargers for this facility
            List<Hotspot> nearestChargers = findNearestChargers(facilityId);
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
            Id<Hotspot> hotspotId = entry.getKey();
            double t_0 = calculateInitialChargingDuration(entry.getValue());  // Calculate t_0 from charger type and power
            AverageChargingDuration.put(hotspotId, t_0);  // Initialize with t_0
            ChargingTime.put(hotspotId, t_0);  // Initialize charging time with t_0 (no queue at the beginning)
        }
    }

    // Placeholder to calculate initial t_0 based on charger type and plug count
    private double calculateInitialChargingDuration(Map<ChargerType, Integer> chargers) {
        // Implement logic to calculate t_0 based on charger power and standard EV battery capacity
        // Placeholder example:
        double standardEVBatteryCapacity = 50;  // kWh (example)
        double totalPower = chargers.keySet().stream().mapToDouble(type -> Hotspot.powerPerChargerType.get(type) * chargers.get(type)).sum();
        return standardEVBatteryCapacity / totalPower;  // Charging time t_0 = Battery capacity / total power
    }

    // Calculate active hotspots based on the number of plugs and charger type
    private void calculateActiveHotspots() {
        for (Map.Entry<Id<Hotspot>, Map<ChargerType, Integer>> entry : chargerAllocation.entrySet()) {
            Id<Hotspot> hotspotId = entry.getKey();
            Map<ChargerType, Integer> chargers = entry.getValue();

            // Capacity (for now, calculated based on plug count, can be extended)
            double capacity = calculateHotspotCapacity(chargers);
            activeHotspots.put(hotspotId, capacity);
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
    
 // Method to calculate vehicular demand for each charger
    private Map<Id<Hotspot>, Double> calculateDemandPerCharger(double peakHourFactor) {
        Map<Id<Hotspot>, Double> chargerDemand = new HashMap<>();

        // Loop through each facility and its nearest chargers
        for (Id<ActivityFacility> facilityId : facilities.keySet()) {
            double q_f = demand.get(facilityId);  // Demand from facility f

            // Get the allocation probabilities for this facility
            Map<Id<Hotspot>, Double> chargerProbabilities = facilityToChargerProbability.get(facilityId);

            // Calculate demand for each charger
            for (Map.Entry<Id<Hotspot>, Double> entry : chargerProbabilities.entrySet()) {
                Id<Hotspot> chargerId = entry.getKey();
                double P_f_c = entry.getValue();  // Probability of facility f choosing charger c

                // Calculate the demand for charger c
                double q_c = q_f * peakHourFactor * P_f_c;

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
            double q_f = demand.get(facilityId);  // Demand from facility f
            double facilityDuration = featureMap.getMap(facilities.get(facilityId).getData()).get(Hotspot.EvUserString+"_"+Hotspot.acitivityDurationString);  // Get activity duration

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

            // Store the average duration in the class variable
            AverageChargingDuration.put(chargerId, averageDuration);
        }
    }




    // Find the 6 nearest chargers for a given facility
    private List<Hotspot> findNearestChargers(Id<ActivityFacility> facilityId) {
        // Implement logic to find the nearest 6 active hotspots based on distance
        // Placeholder implementation:
        List<Hotspot> sortedHotspots = new ArrayList<>(hotspots.values());
        sortedHotspots.sort(Comparator.comparingDouble(h -> calculateDistance(facilityId, h)));
        return sortedHotspots.subList(0, Math.min(6, sortedHotspots.size()));
    }

 // Calculate Euclidean distance between a facility and a hotspot
    private double calculateDistance(Id<ActivityFacility> facilityId, Hotspot hotspot) {
        // Extract coordinates of the facility from the facilityFeatures_raw
        RealVector facilityFeatures = facilities.get(facilityId);
        Map<String, Double> facilityFeatureMap = featureMap.getMap(facilityFeatures.getData());  // Convert RealVector to Map
        double x1 = facilityFeatureMap.get(Hotspot.locationX);
        double y1 = facilityFeatureMap.get(Hotspot.locationY);

        // Extract coordinates of the hotspot's centroid
        Map<String, Double> hotspotFeatureMap = featureMap.getMap(hotspot.getCentroidFeatureVector().getData());
        double x2 = hotspotFeatureMap.get(Hotspot.locationX);
        double y2 = hotspotFeatureMap.get(Hotspot.locationY);

        // Calculate Euclidean distance between the two points
        return Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
    }


 // Calculate allocation probabilities using the logit model (without recalculating t, using t_0 initially)
    private void calculateAllocationProbability() {
        double betaDistance = -1.0;  // Sensitivity to distance
        double betaTime = -1.0;      // Sensitivity to charging time
//        double betaPrice = -1.0;	// Sensitivity to money.

        // Loop through each facility to calculate the probabilities of choosing each charger
        for (Id<ActivityFacility> facilityId : facilities.keySet()) {
            Map<Id<Hotspot>, Double> chargerProbabilities = new HashMap<>();
            List<Hotspot> nearestChargers = nearestChargersPerFacility.get(facilityId);

            // Extract facility features (coordinates) from the facility
//            RealVector facilityFeatures = facilities.get(facilityId);
//            Map<String, Double> facilityFeatureMap = featureMap.getMap(facilityFeatures.getData());

            // Calculate total utility for normalization
            double totalUtility = 0.0;
            for (Hotspot charger : nearestChargers) {
                Id<Hotspot> chargerId = charger.getHotspotId();

                // Calculate distance between facility and charger
                double distance = calculateDistance(facilityId, charger);

                // Get the initial charging time t_0 for this charger from class variable
                double t_0 = AverageChargingDuration.get(chargerId);

                // Use t_0 as the initial charging time (before any updates)
                double t = ChargingTime.getOrDefault(chargerId, t_0);  // t is initialized as t_0

                // Utility function for this charger
                double utility = Math.exp(betaDistance * distance + betaTime * t);

                // Store utility in probabilities map for normalization later
                chargerProbabilities.put(chargerId, utility);

                // Sum up total utility
                totalUtility += utility;
            }

            // Normalize the probabilities by dividing each utility by the total utility
            for (Map.Entry<Id<Hotspot>, Double> entry : chargerProbabilities.entrySet()) {
                Id<Hotspot> chargerId = entry.getKey();
                double utility = entry.getValue();
                double probability = utility / totalUtility;  // Normalize

                // Store the probability in the facilityToChargerProbability map
                facilityToChargerProbability.computeIfAbsent(facilityId, k -> new HashMap<>()).put(chargerId, probability);
            }
        }
    }


    // Method to calculate total charging time using VDF (power demand and capacity)
    private double calculateChargingTime(double t_0, double powerDemand, double capacity, double alpha, double beta) {
        if (powerDemand <= capacity) {
            return t_0;  // No delay if power demand is less than or equal to capacity
        } else {
            return t_0 * (1 + alpha * Math.pow((powerDemand / capacity - 1), beta));
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



 // Method to update demand, average duration, and charging time using MSA
    private boolean update(int iterationCount) {
 
        boolean isEquilibrium = true;  // Assume equilibrium is reached, we will check if it holds
        double tolerance = 0.001;  // Threshold for equilibrium check

        // Step 1: Store old demand (for equilibrium check)
        Map<Id<Hotspot>, Double> oldDemand = new HashMap<>(this.demandPerCharger);

        // Step 2: Calculate the new demand for each charger
        Map<Id<Hotspot>, Double> newDemand = calculateDemandPerCharger(peakHourFactor);  // New demand calculation

        // Step 3: Apply MSA to update the demand
        for (Id<Hotspot> chargerId : newDemand.keySet()) {
            double oldDemand_c = oldDemand.getOrDefault(chargerId, 0.0);
            double newDemand_c = newDemand.getOrDefault(chargerId, 0.0);

            // MSA formula: X^{(k+1)} = (k/(k+1)) * oldDemand + (1/(k+1)) * newDemand
            double updatedDemand = (iterationCount / (iterationCount + 1.0)) * oldDemand_c + (1.0 / (iterationCount + 1.0)) * newDemand_c;

            // Update the demand variable in class
            demandPerCharger.put(chargerId, updatedDemand);
        }

        // Step 4: Calculate the average duration using the updated (MSA) demand
        calculateAverageDurationPerCharger(demandPerCharger);  // Updates the AverageChargingDuration class variable

        // Step 5: Calculate the charging time using the average duration and the MSA demand
        for (Id<Hotspot> chargerId : demandPerCharger.keySet()) {
            double avgChargerPower = calculateAverageChargerPower(chargerId);
            double updatedDemand_c = demandPerCharger.getOrDefault(chargerId, 0.0);  // MSA demand
            double t_0 = AverageChargingDuration.get(chargerId);  // Updated average duration

            // Convert demand to power demand
            double powerDemand = updatedDemand_c * avgChargerPower;

            // Get charger capacity in power
            double capacity = activeHotspots.get(chargerId);  // Capacity is in power (kW)

            // Calculate total charging time t using VDF
            double chargingTime = calculateChargingTime(t_0, powerDemand, capacity, this.bprAlpha, this.bprBeta);

            // Update the charging time in the class variable
            ChargingTime.put(chargerId, chargingTime);
        }

        // Step 6: Check for equilibrium
        for (Id<Hotspot> chargerId : newDemand.keySet()) {
            double oldDemand_c = oldDemand.getOrDefault(chargerId, 0.0);
            double newDemand_c = demandPerCharger.getOrDefault(chargerId, 0.0);  // MSA updated demand

            if (Math.abs(newDemand_c - oldDemand_c) > tolerance) {
                isEquilibrium = false;  // If the change in demand is greater than the tolerance, equilibrium not reached
            }
        }

        // Increment iteration count for MSA
        iterationCount++;

        return isEquilibrium;  // Return true if equilibrium is reached
    }

}