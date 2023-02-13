package scenarioGeneration;


import EVPricing.RunEVExampleV2;


public class RunFromEclipse {
	public static void main(String[] args) {
		
		String[] args2 = new String[] {
				"--config","1_percent/config.xml",
				"--tv","montreal_transit_vehicles.xml.gz",
				"--ts","montreal_transit_schedules.xml.gz",
				"--facilities","montreal_facilities.xml.gz",
				"--plan","plan.xml",
				"--evpricing","1_percent/pricingProfiles.xml",
				"--evehicle","evehicle.xml",
				"--thread","10",
				"--output", "1_percent/dumb8_10"
			};
		RunEVExampleV2.main(args2);
		
	}

}
