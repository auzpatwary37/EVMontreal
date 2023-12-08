package withinDay;

import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.io.PopulationReader;

public class ChargingLogicCounter {
	public static void main(String[] args) {

		int activityDuration = 0;
		int optimized = 0;
		int experienceBasedLogic=0;

		Population population = PopulationUtils.readPopulation("C:\\Users\\arsha\\OneDrive\\Desktop\\TRBREsults\\Combined\\it.150\\150.plans.xml");
		for (Person person : population.getPersons().values()) {
			String chargingLogic = (String) person.getSelectedPlan().getAttributes().getAttribute("logicSwitch");
			if (chargingLogic != null) {
				if (chargingLogic.equals("activityDuration")) {
					activityDuration++;
				} else if (chargingLogic.equals("optimized")) {
					optimized++;
				} else if (chargingLogic.equals("experienceBased")) {
					experienceBasedLogic++;
				}
			}
		}

		System.out.println("Activity Duration Logic Count: " + activityDuration);
		System.out.println("Optimized Logic Count: " + optimized);
		System.out.println("Experience-Based Logic Count: " + experienceBasedLogic);
	}
}