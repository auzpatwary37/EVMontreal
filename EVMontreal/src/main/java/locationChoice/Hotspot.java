package locationChoice;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.math.linear.MatrixUtils;
import org.apache.commons.math.linear.RealVector;
import org.matsim.api.core.v01.Id;
import org.matsim.facilities.ActivityFacility;

public class Hotspot {
	
	public static final String locationX = "x";
	public static final String locationY = "y";
	public static final String activityNumberString = "demand";
	public static final String acitivityDurationString = "duration";
	public static final String EvUserString = "evUser";
	public static final String nonEvUserString = "nonEvUser";
	
	public Map<ChargerType,Integer> plugCountPerChargerType = new HashMap<>();
	public static Map<ChargerType,Double> powerPerChargerType = new HashMap<>();
	// hour should go from 1-24 or Am-peak (7-11) , Pm-Peak (5-8), BeforeAm Peak (0-7), AfterAmPeak (11-5), AfterPmPeak (8-12 and onwards)
	
	private boolean isLockedCentroid = false;
	private final Id<Hotspot> hotspotId;
	private Id<ActivityFacility> centroidFacilityId;
	private RealVector centroidFacilityFeature;
	//private Map<Id<ActivityFacility>, ActivityFacility> facilities = new HashMap<>();
	private MapToArray<String> featureMap;
	private Map<Id<ActivityFacility>,RealVector> features = new HashMap<>();
	
	public Hotspot(String id, MapToArray<String> featureMap) {
		this.hotspotId = Id.create(id, Hotspot.class);
		this.featureMap = featureMap;
	}
	
	public void addFacility(Id<ActivityFacility> facilityId,RealVector Feature) {
		//this.facilities.put(facility.getId(),facility);
		this.features.put(facilityId, Feature);
	}
	
	public void addFacility(Id<ActivityFacility> facilityId,Map<String,Double> feature) {
		//this.facilities.put(facility.getId(),facility);
		this.features.put(facilityId, MatrixUtils.createRealVector(this.featureMap.getMatrix(feature)));
	}
	
	public void removeFacility(Id<ActivityFacility> facilityId) {
		this.features.remove(facilityId);
		//this.facilities.remove(facility.getId());
	}
	public void removeAllFacilities() {
		this.features.clear();
		//this.facilities.clear();
	}
	
	public Id<ActivityFacility> getCentroidFacility() {
        if (this.isLockedCentroid) {
            return centroidFacilityId;
        } else {
             centroidFacilityId = calculateCentroidFacility();
             return centroidFacilityId;
        }
    }

    private Id<ActivityFacility> calculateCentroidFacility() {
        if (this.features.isEmpty()) {
            return null; // No facilities to calculate a centroid from.
        }

        RealVector centroidVector = null;
        int count = 0;

        // Calculate the average feature vector to determine the centroid
        for (RealVector featureVector : features.values()) {
            if (centroidVector == null) {
                centroidVector = featureVector.copy();
            } else {
                centroidVector = centroidVector.add(featureVector);
            }
            count++;
        }

        if (centroidVector != null) {
            centroidVector.mapDivideToSelf(count);
        }

        // Find the facility that is closest to the calculated centroid vector
        Id<ActivityFacility> closestFacilityId = null;
        double minDistance = Double.MAX_VALUE;
        RealVector closestFacilityFeature = null;

        for (Map.Entry<Id<ActivityFacility>, RealVector> entry : features.entrySet()) {
            double distance = centroidVector.getDistance(entry.getValue());
            if (distance < minDistance) {
                minDistance = distance;
                closestFacilityId = entry.getKey();
                closestFacilityFeature = entry.getValue();
            }
        }
        
        centroidFacilityId = closestFacilityId;
        this.centroidFacilityFeature = closestFacilityFeature;
        return centroidFacilityId;
    }
	
	public boolean isLockedCentroid() {
		return isLockedCentroid;
	}

	public void setLockedCentroid(boolean isLockedCentroid) {
		this.isLockedCentroid = isLockedCentroid;
	}

	public Id<Hotspot> getHotspotId() {
		return hotspotId;
	}

	public Id<ActivityFacility> getCentroidFacilityId() {
		return centroidFacilityId;
	}

//	public Map<Id<ActivityFacility>, ActivityFacility> getFacilities() {
//		return facilities;
//	}

	public MapToArray<String> getFeatureMap() {
		return featureMap;
	}

	public Map<Id<ActivityFacility>, RealVector> getFeatures() {
		return features;
	}

	public RealVector getCentroidFeatureVector() {
		return this.centroidFacilityFeature;
	}

	public void setCentroidFacilityId(Id<ActivityFacility> centroidFacilityId, RealVector centroidVector) {
		this.centroidFacilityId = centroidFacilityId;
		this.centroidFacilityFeature = centroidVector;
	}

	public Map<ChargerType, Integer> getPlugCountPerChargerType() {
		return plugCountPerChargerType;
	}

	public void setPlugCountPerChargerType(Map<ChargerType, Integer> plugCountPerChargerType) {
		this.plugCountPerChargerType = plugCountPerChargerType;
	}

	public Map<ChargerType, Double> getPowerPerChargerType() {
		return powerPerChargerType;
	}

	public static void setPowerPerChargerType(Map<ChargerType, Double> powerPerCharger) {
		powerPerChargerType = powerPerCharger;
	}
	
	
}
