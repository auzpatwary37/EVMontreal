package locationChoice;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.matsim.api.core.v01.Id;

import de.xypron.jcobyla.Calcfc;

public class OptimizationSetup {
	public static void main(String[] args) {
		
		String plugCountkey = "plug";
		String typeKey = "type";
		String seperator = "___";
		MapToArray<String> variablesMapToArray = new MapToArray<String>("variables",new HashSet<>());
		Map<Id<Hotspot>,Hotspot> hotspots = new HashMap<>();
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
		                int plugCount = (int) Math.round(value);
		                plugCounts.put(hotspotId, plugCount);
		            }
		        }
		        hotspots.entrySet().forEach(h->{
		        	if(chargerTypes.containsKey(h.getKey())) {
		        		h.getValue().setPlugCountPerChargerType(Map.of(chargerTypes.get(h.getKey()),plugCounts.get(h.getKey())));
		        	}
		        });
				
				return 0;
			}
			
		};
		
		
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
