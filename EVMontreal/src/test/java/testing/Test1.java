package testing;


import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.population.PopulationUtils;

public class Test1 {
public static void main(String[] args) {
	Population pop = PopulationUtils.readPopulation("output\\1_percent\\aiagent_Aug1\\output_plans.xml.gz");
	int haveBoth = 0;
	int selected = 0;
	for(Person p:pop.getPersons().values()) {
		for(Plan plan:p.getPlans()) {
			if(plan.getAttributes().getAttribute("IfAiGenerated")!=null) {
				haveBoth++;
				if(plan.equals(plan.getPerson().getSelectedPlan()))selected++;
			}
		}
	}
	System.out.println(haveBoth);
	System.out.println(selected);
	
}
}
