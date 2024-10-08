package scenarioGeneration;


import EVPricing.RunEVExampleV2;


public class RunFromEclipse {
	public static void main(String[] args) {
		
		String[] args2 = new String[] {
				"--config","data\\10p\\config.xml",
				"--tv","montreal_transit_vehicles.xml",
				"--ts","montreal_transit_schedules.xml",
				"--facilities","montreal_facilities.xml.gz",
				"--network","montreal_network.xml",
				"--plan","plan.xml",
				"--charger","charger.xml",
				"--evpricing","data\\10p\\pricingProfiles.xml",
				"--vehicles","vehicle.xml",
				"--thread","10",
				"--output", "F:\\EvLocationChoiceResult\\output_base",
				"--scale",".1",
//				"--firstiterations","40",
				"--lastiterations","10"
			};
		RunEVExampleV2.main(args2);
		
	}

}
