package scenarioGeneration;

import org.matsim.facilities.FacilitiesUtils;

import EVPricing.RunEVExampleV2;


public class RunFromEclipse {
	public static void main(String[] args) {
		
		String[] args2 = new String[] {
				"--config","RunFolder/config.xml",
				"--tv","montreal_transitVehicles.xml",
				"--ts","montreal_transitSchedule.xml",
				"--facilities","output_facilitiesV1.xml.gz",
				"--plan","plan.xml",
				"--evpricing","RunFolder/pricingProfiles.xml"
			};
		RunEVExampleV2.main(args2);
		
	}

}
