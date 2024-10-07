package scenarioGeneration;


import EVPricing.RunEVExampleV2;


public class RunFromEclipse {
	public static void main(String[] args) {
		
		String[] args2 = new String[] {
				"--config","data\\10p\\config.xml",
				"--tv","montreal_transit_vehicles.xml",
				"--ts","montreal_transit_schedules.xml",
				"--facilities","montreal_facilities.xml.gz",
				"--plan","plan.xml",
				"--evpricing","data\\10p\\pricingProfiles_new.xml",
				"--vehicles","vehicle.xml",
				"--thread","10",
				"--output", "data\\10p\\output\\all_features_kmeans",
				"--scale",".01",
//				"--firstiterations","40",
				"--lastiterations","100"
			};
		RunEVExampleV2.main(args2);
		
	}

}
