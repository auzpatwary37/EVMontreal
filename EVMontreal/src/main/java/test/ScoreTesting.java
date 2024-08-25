package test;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.households.Household;
import org.matsim.households.Households;
import org.matsim.households.HouseholdsWriterV10;
import org.matsim.vehicles.Vehicle;

public class ScoreTesting {
	public static void main(String[] args) throws IOException {
		Population pop = PopulationUtils.readPopulation("C:\\Users\\arsha\\git\\EVMontreal\\EVMontreal\\TRB2024\\population_with_locations_linkId.xml.gz");
		Config config = ConfigUtils.createConfig();
		config.households().setInputFile("C:\\Users\\arsha\\git\\EVMontreal\\EVMontreal\\TRB2024\\households.xml.gz");
		Households hh = ScenarioUtils.loadScenario(config).getHouseholds();
		//		FileWriter fw = new FileWriter(new File(""));
//		fw.append("personId, age, gender, employment,income,score, averageScore\n");
		
//		for(Person p:pop.getPersons().values()) {
//			fw.append(p.getId().toString()+","+p.getAttributes().getAttribute("age")
//					+","+p.getAttributes().getAttribute("sex")+","+p.getAttributes().getAttribute("employment")+
//					","+p.getAttributes().getAttribute("income")+","+p.getSelectedPlan().getScore()+","+
//					getAverageScore(p)+"\n");
//			fw.flush();
//		}
//		fw.close();
		
		scaleDownPopulation(pop,hh,0.1);
		new PopulationWriter(pop).write("C:\\Users\\arsha\\git\\EVMontreal\\EVMontreal\\TRB2024\\population_with_locations10P.xml.gz");
		new HouseholdsWriterV10(hh).writeFile("C:\\Users\\arsha\\git\\EVMontreal\\EVMontreal\\TRB2024\\households10P.xml.gz");
	}
	
	public static Population scaleDownPopulation(Population inPop,Households hh, double scale) {
		Set<Id<Person>> removedPid = new HashSet<>();
		Set<Id<Vehicle>> removedvId = new HashSet<>();
		for(Id<Person> pId:new HashSet<>(inPop.getPersons().keySet())) {
			if(Math.random()>scale) {
				inPop.getPersons().remove(pId);
				removedPid.add(pId);
				removedvId.add(Id.create(pId.toString(), Vehicle.class));
			}
		}
		hh.getHouseholds().values().stream().forEach(h->{
			h.getMemberIds().removeAll(removedPid);
			h.getVehicleIds().removeAll(removedvId);
		});
		for(Household h:new HashSet<>(hh.getHouseholds().values())) {
			if(h.getMemberIds().size()==0) {
				hh.getHouseholds().remove(h.getId());
			}
		}
		return inPop;
	}
	
	public static double getAverageScore(Person p) {
		double totalScore = 0;
		for(Plan pl:p.getPlans()) {
			totalScore+=pl.getScore();
		}
		return totalScore/p.getPlans().size();
	}

}