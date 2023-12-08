package test;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkFactory;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.TransitScheduleFactory;



public class Arsham {
	public static void main(String[] args) {
		
		String[] cities = new String[3];
		Plan[] plans = new Plan[5];
		Person[] persons = new Person[7];
		for (int i = 0; i <5; i++) {
			System.out.println("HI");
		}
		
		for (Plan plan : plans) {
			
		}
		for (String city : cities) {
			
		}
		
		Config config = ConfigUtils.createConfig();
		Scenario scenario = ScenarioUtils.createScenario(config);
		Population population = scenario.getPopulation();
		PopulationFactory popFactory = population.getFactory();
		NetworkFactory netFac = scenario.getNetwork().getFactory();
		TransitScheduleFactory tsFactory = scenario.getTransitSchedule().getFactory();
		
		int i = 0;
		for (Person person : persons) {
			Id<Person> pId = Id.create(Integer.toString(i), Person.class);
			person = popFactory.createPerson(pId);
			Plan plan = popFactory.createPlan();
			person.addPlan(plan);
			Activity act = popFactory.createActivityFromCoord("home", new Coord(0,0));
			plan.addActivity(act);
			Leg leg = popFactory.createLeg("car");
			plan.addLeg(leg);
			act = popFactory.createActivityFromCoord("work", new Coord(10000,10000));
			plan.addActivity(act);
			population.addPerson(person);
			persons[i] = person;
			i= i+1;
		}
		new PopulationWriter(population).write("target/population.xml");
		Network network = scenario.getNetwork();
		Node node0 = netFac.createNode(Id.create("n0", Node.class), new Coord(0,1000));
		network.addNode(node0);
		Node node1 = netFac.createNode(Id.create("n1", Node.class), new Coord(500,0));
		network.addNode(node1);
		Node node2 = netFac.createNode(Id.create("n2", Node.class), new Coord(750,1000));
		network.addNode(node2);
		Node node3 = netFac.createNode(Id.create("n3", Node.class), new Coord(1000,1000));
		network.addNode(node3);
		
		Link link0 = netFac.createLink(Id.create("n0_n2", Link.class), node0, node2);
		network.addLink(link0);
		Link link1 = netFac.createLink(Id.create("n0_n1", Link.class), node0, node1);
		network.addLink(link1);
		Link link2 = netFac.createLink(Id.create("n1_n2", Link.class), node1, node2);
		network.addLink(link2);
		Link link3 = netFac.createLink(Id.create("n2_n3", Link.class), node2, node3);
		network.addLink(link3);
		
		new NetworkWriter(network).write("target/network.xml");
		
		System.out.println();
	}
	
}
