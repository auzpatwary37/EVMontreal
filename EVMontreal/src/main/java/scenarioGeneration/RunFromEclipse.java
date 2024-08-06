package scenarioGeneration;


import EVPricing.RunEVExampleV2;


public class RunFromEclipse {
	public static void main(String[] args) {
		
		String[] args2 = new String[] {
				"--config","data/1p daily/config_with_calibrated_parameters.xml",
				"--tv","montreal_transit_vehicles.xml",
				"--ts","montreal_transit_schedules.xml",
				"--facilities","montreal_facilities.xml.gz",
				"--plan","plan.xml",
				"--evpricing","data/1p daily/pricingProfiles.xml",
				"--vehicles","vehicle.xml",
				"--thread","10",
				"--output", "output/1_percent/aiagent",
				"--scale",".01",
//				"--firstiterations","40",
				"--lastiterations","60"
			};
		RunEVExampleV2.main(args2);
		
	}

}
