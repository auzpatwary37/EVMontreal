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
				"--charger","charger_new_step2.xml",
				"--evpricing","data\\10p\\pricingProfiles_new_step2.xml",
				"--vehicles","vehicle.xml",
				"--thread","10",
				"--output", "F:\\EvLocationChoiceResult\\output_step2",
				"--scale",".1",
//				"--firstiterations","40",
				"--lastiterations","100"
			};
		RunEVExampleV2.main(args2);
		
	}

}
