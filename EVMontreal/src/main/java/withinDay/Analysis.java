package withinDay;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Population;
import org.matsim.contrib.ev.fleet.ElectricFleetReader;
import org.matsim.contrib.ev.fleet.ElectricFleetSpecification;
import org.matsim.contrib.ev.fleet.ElectricFleetSpecificationImpl;
import org.matsim.contrib.ev.fleet.ElectricVehicle;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.utils.misc.OptionalTime;

public class Analysis {
	public static void main(String[] args) throws IOException {
		Population population = PopulationUtils.readPopulation("data/100.plans.xml.gz");
		Map<Integer,Double> soc = new HashMap<>();
		Map<Integer,Integer> socount = new HashMap<>();
		ElectricFleetSpecification evs = new ElectricFleetSpecificationImpl();
		new ElectricFleetReader(evs).readFile("data/evehicle.xml");
		
		population.getPersons().values().stream().forEach(p->{
			p.getSelectedPlan().getPlanElements().stream().forEach(pl->{
				if(pl instanceof Activity && ((Activity)pl).getAttributes().getAttribute("actSOC")!=null) {
					double actSoc = (double) ((Activity)pl).getAttributes().getAttribute("actSOC");
					double cap = evs.getVehicleSpecifications().get(Id.create(p.getId().toString(), ElectricVehicle.class)).getBatteryCapacity();
					
					OptionalTime time = ((Activity)pl).getStartTime();
					double t;
					if(time.isDefined())t = time.seconds();
					else t = 0;
					int T = (int) (t/900);
					soc.compute(T, (K,V)->V==null?actSoc*cap/3600000:V+actSoc*cap/3600000);
					socount.compute(T, (k,v)->v==null?1:v+1);
				}
			});
		});
		soc.entrySet().forEach(e->e.setValue(e.getValue()/socount.get(e.getKey())));
		
		FileWriter fw = new FileWriter(new File("data/avgSoc.csv"));
		fw.append("hour,averageSOC\n");
		fw.flush();
		
		for(Entry<Integer, Double> e:soc.entrySet()) {
			fw.append(e.getKey()/4.+","+e.getValue()+"\n");
			fw.flush();
		}
		fw.close();
	}
	
}
