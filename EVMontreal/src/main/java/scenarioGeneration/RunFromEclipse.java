package scenarioGeneration;


import EVPricing.RunEVExampleV2;


public class RunFromEclipse {
	public static void main(String[] args) {
		
		String[] args2 = new String[] {
				"--config","data\\1p daily\\config.xml",
				"--tv","montreal_transit_vehicles.xml",
				"--ts","montreal_transit_schedules.xml",
				"--facilities","montreal_facilities.xml.gz",
				"--plan","plan.xml",
				"--evpricing","data\\1p daily\\pricingProfiles.xml",
				"--vehicles","vehicle.xml",
				"--thread","10",
				"--output", "G:\\ai\\aiagent_1124_3",
				"--scale",".01",
				"--firstiterations","0",
				"--lastiterations","60"
			};
		RunEVExampleV2.main(args2);
		
	}

}
