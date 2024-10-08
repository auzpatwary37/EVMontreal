package locationChoice;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;

import org.apache.commons.math.linear.MatrixUtils;
import org.apache.commons.math.linear.RealVector;
import org.matsim.api.core.v01.Id;
import org.matsim.facilities.ActivityFacility;

public class KMeansClusterer {

    private final List<Hotspot> hotspots = new ArrayList<>();
    private final int numClusters;
    private final List<Hotspot> fixedHotspots;

    public KMeansClusterer(List<Hotspot> initialHotspots, int numClusters) {
        this.fixedHotspots = initialHotspots;
        this.numClusters = numClusters;

        if (fixedHotspots.size() >= numClusters) {
            throw new IllegalArgumentException("The number of fixed hotspots must be less than the total number of clusters.");
        }

        // Initialize the fixed hotspots first and mark them as locked.
        for (Hotspot hotspot : fixedHotspots) {
            hotspot.setLockedCentroid(true);
            hotspots.add(hotspot);
        }

        // Create additional hotspots to reach the desired number of clusters.
        for (int i = fixedHotspots.size(); i < numClusters; i++) {
            String id = "dynamicHotspot_" + i;
            Hotspot h = new Hotspot(id, fixedHotspots.get(0).getFeatureMap());
            hotspots.add(h); // Use the same feature map for consistency.
        }
    }

    public void runKMeans(int maxIterations, Map<Id<ActivityFacility>, RealVector> facilityFeatures_raw) {
    	Map<Id<ActivityFacility>, RealVector> facilityFeatures = this.minMaxScaleFeatures(facilityFeatures_raw);
    	
    	assignRandomFacilitiesToHotspotsCentroid(facilityFeatures);
        boolean centroidsChanged;

        for (int iter = 0; iter < maxIterations; iter++) {
            centroidsChanged = false;

            // Clear all facilities in all hotspots
            for (Hotspot hotspot : hotspots) {
                hotspot.getFeatures().clear(); // Clear the feature map as well
            }

            // Assign each facility to the nearest hotspot
            for (Entry<Id<ActivityFacility>, RealVector> entry : facilityFeatures.entrySet()) {
                Id<ActivityFacility> facilityId = entry.getKey();
                RealVector featureVector = entry.getValue();
                Hotspot closestHotspot = findClosestHotspot(featureVector);
                closestHotspot.addFacility(facilityId, featureVector);
            }

            // Update centroid facilities for dynamic hotspots
            for (Hotspot hotspot : hotspots) {
                if (!hotspot.isLockedCentroid()) {
                    Id<ActivityFacility> oldCentroid = hotspot.getCentroidFacilityId();
                    Id<ActivityFacility> newCentroid = hotspot.getCentroidFacility();

                    if (newCentroid != null && !newCentroid.equals(oldCentroid)) {
                        centroidsChanged = true;
                    }
                }
            }

            // Stop if centroids do not change
            if (!centroidsChanged) {
                break;
            }
        }
    }
    
    private void assignRandomFacilitiesToHotspotsCentroid(Map<Id<ActivityFacility>, RealVector> facilityFeatures_scaled) {
    	Set<Id<ActivityFacility>> facIds = new HashSet<>(facilityFeatures_scaled.keySet()); 
    	Random random = new Random();
    	this.hotspots.stream().forEach(h->{
    		if(h.getCentroidFacility()!=null) {
    			facIds.remove(h.getCentroidFacility());
    			h.setCentroidFacilityId(h.getCentroidFacilityId(), facilityFeatures_scaled.get(h.getCentroidFacilityId()));
    		}
    	});
    	this.hotspots.stream().forEach(h->{
    		if(h.getCentroidFacility()==null) {
    			List<Id<ActivityFacility>> list = new ArrayList<>(facIds);

    	        // Generate a random index
    	       
    	        int randomIndex = random.nextInt(list.size());

    	        // Get a random element from the list
    	        Id<ActivityFacility> randomElement = list.get(randomIndex);;
    			h.setCentroidFacilityId(randomElement, facilityFeatures_scaled.get(randomElement));
    			facIds.remove(randomElement);
    		}
    	});
    }
    
    private Map<Id<ActivityFacility>, RealVector> minMaxScaleFeatures(Map<Id<ActivityFacility>, RealVector> facilityFeatures) {
        // Initialize min and max values for each feature
        int numFeatures = facilityFeatures.values().iterator().next().getDimension();
        double[] minValues = new double[numFeatures];
        double[] maxValues = new double[numFeatures];
        Arrays.fill(minValues, Double.MAX_VALUE);
        Arrays.fill(maxValues, Double.MIN_VALUE);

        // Find min and max for each feature across all facilities
        for (RealVector featureVector : facilityFeatures.values()) {
            for (int i = 0; i < numFeatures; i++) {
                double value = featureVector.getEntry(i);
                if (value < minValues[i]) {
                    minValues[i] = value;
                }
                if (value > maxValues[i]) {
                    maxValues[i] = value;
                }
            }
        }

        // Scale features using min-max scaling
        Map<Id<ActivityFacility>, RealVector> scaledFeatures = new HashMap<>();
        for (Map.Entry<Id<ActivityFacility>, RealVector> entry : facilityFeatures.entrySet()) {
            RealVector originalVector = entry.getValue();
            double[] scaledEntries = new double[numFeatures];
            for (int i = 0; i < numFeatures; i++) {
                double value = originalVector.getEntry(i);
                if (maxValues[i] - minValues[i] == 0) {
                    scaledEntries[i] = 0.5; // Assign a default value if min equals max to avoid division by zero
                } else {
                    scaledEntries[i] = (value - minValues[i]) / (maxValues[i] - minValues[i]);
                }
            }
            scaledFeatures.put(entry.getKey(), MatrixUtils.createRealVector(scaledEntries));
        }

        return scaledFeatures;
    }
    private Hotspot findClosestHotspot(RealVector featureVector) {
        Hotspot closestHotspot = null;
        double minDistance = Double.MAX_VALUE;

        for (Hotspot hotspot : hotspots) {
            RealVector centroidVector = hotspot.getCentroidFeatureVector();
            double distance = centroidVector.getDistance(featureVector);

            if (distance < minDistance) {
                minDistance = distance;
                closestHotspot = hotspot;
            }
        }

        return closestHotspot;
    }

    public List<Hotspot> getHotspots() {
        return hotspots;
    }
}
