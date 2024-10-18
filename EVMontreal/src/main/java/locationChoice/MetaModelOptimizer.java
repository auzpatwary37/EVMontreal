package locationChoice;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import cadyts.utilities.misc.Tuple;

public class MetaModelOptimizer {
	private Map<String,Tuple<Integer,Integer>> lowerAndUpperLimit = new HashMap<>();
	private Map<String,Integer> currentValue = new HashMap<>();
	private Map<String,Boolean> ifInteger = new HashMap<>();
	
	
	private double[] getConstraints() {
		return new double[5];
	}
	
	
	/**
	 * gets demand given the active hotspots. 
	 * @return
	 */
	private double getDemand(Set<Hotspot> hotspots) {
		return 0;
	}
	
	private double getQueue() {
		return 0;
	}
	
	private double getElectricityUsage() {
		return 0;
	}
}
