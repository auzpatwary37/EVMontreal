package test;

import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PersonUtils;
import org.matsim.core.population.PopulationUtils;
 
public class PopulationStiching {
	public static void main(String[] args) {
		String originalDailyPopulationLocation = "C:\\Users\\arsha\\Desktop\\population_with_locations.xml.gz";
		String weeklyPopulationWriteLocation = "C:\\Users\\arsha\\Desktop\\population_with_locations_stiched.xml.gz";
		Population pop = PopulationUtils.readPopulation(originalDailyPopulationLocation);
		Population popWeekly = PopulationUtils.createPopulation(ConfigUtils.createConfig());
		PopulationFactory popFac = popWeekly.getFactory();
		pop.getPersons().entrySet().forEach(p->{
			Person personWeekly = popFac.createPerson(p.getKey());
			p.getValue().getAttributes().getAsMap().entrySet().forEach(a->personWeekly.getAttributes().putAttribute(a.getKey(),a.getValue()));
			Plan dailyPlan = p.getValue().getSelectedPlan();
			Plan weeklyPlan = popFac.createPlan();
			dailyPlan.getAttributes().getAsMap().entrySet().forEach(a->weeklyPlan.getAttributes().putAttribute(a.getKey(),a.getValue()));
			personWeekly.addPlan(weeklyPlan);
			personWeekly.setSelectedPlan(weeklyPlan);
			popWeekly.addPerson(personWeekly);
			for(int i=0;i<7;i++) {
				int j = 0;
				Activity lastAct = null;
				boolean addLeg = true;
				for(PlanElement pe:dailyPlan.getPlanElements()) {
					if(pe instanceof Activity) {
						Activity oldAct = (Activity)pe;
						Activity act = popFac.createActivityFromActivityFacilityId(oldAct.getType(),oldAct.getFacilityId());
						oldAct.getAttributes().getAsMap().entrySet().forEach(a->{
							act.getAttributes().putAttribute(a.getKey(),a.getValue());
						});
						if(j==0) {//First act
							if(lastAct!=null && lastAct.getType().equals(act.getType()) && lastAct.getFacilityId().equals(act.getFacilityId())) {
								if(oldAct.getEndTime().isDefined())lastAct.setEndTime(oldAct.getEndTime().seconds()+i*24*3600);
							}else {
								String mode = "pt";
								if(PersonUtils.getCarAvail(personWeekly).equals("always")) {
									mode = "car";
								}
								Leg leg = popFac.createLeg(mode);
								weeklyPlan.addLeg(leg);
								if(oldAct.getStartTime().isDefined())act.setStartTime(oldAct.getStartTime().seconds()+i*24*3600);
								if(oldAct.getEndTime().isDefined())act.setEndTime(oldAct.getEndTime().seconds()+i*24*3600);
								weeklyPlan.addActivity(act);
							}
						}else if(j==dailyPlan.getPlanElements().size()-1) {//last act
							lastAct = act;
							if(oldAct.getStartTime().isDefined())act.setStartTime(oldAct.getStartTime().seconds()+i*24*3600);
							if(oldAct.getEndTime().isDefined())act.setEndTime(oldAct.getEndTime().seconds()+i*24*3600);
							weeklyPlan.addActivity(act);
						}else {
							if(oldAct.getStartTime().isDefined())act.setStartTime(oldAct.getStartTime().seconds()+i*24*3600);
							if(oldAct.getEndTime().isDefined())act.setEndTime(oldAct.getEndTime().seconds()+i*24*3600);
							weeklyPlan.addActivity(act);
						}
						if(i>4 && act.getType().equals("work")) {
							weeklyPlan.getPlanElements().remove(weeklyPlan.getPlanElements().size()-1);
							addLeg = false;
						}
					}else {
						Leg oldLeg = (Leg)pe;
						Leg leg = popFac.createLeg(oldLeg.getMode());
						oldLeg.getAttributes().getAsMap().entrySet().forEach(a->{
							leg.getAttributes().putAttribute(a.getKey(),a.getValue());
						});
						if(oldLeg.getDepartureTime().isDefined())leg.setDepartureTime(oldLeg.getDepartureTime().seconds()+i*24*3600);
						if(oldLeg.getTravelTime().isDefined())leg.setTravelTime(oldLeg.getTravelTime().seconds());
						if(addLeg) {
							weeklyPlan.addLeg(leg);
						}else {
							addLeg = true;
						}
					}
					j++;
				}
			}
		});
		new PopulationWriter(popWeekly).write(weeklyPopulationWriteLocation);
	}
}