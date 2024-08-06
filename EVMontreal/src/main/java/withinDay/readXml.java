package withinDay;
import java.util.Map.Entry;

import org.matsim.api.core.v01.Id;
import org.matsim.contrib.ev.infrastructure.Charger;

import EVPricing.ChargerPricingProfile;
import EVPricing.ChargerPricingProfileReader;
import EVPricing.ChargerPricingProfileWriter;
import EVPricing.ChargerPricingProfiles;

public class readXml {
	public static void main(String[] args) {
		ChargerPricingProfiles cf = new ChargerPricingProfileReader().readChargerPricingProfiles("C:\\Users\\arsha\\git\\EVMontreal\\EVMontreal\\ABMTrans\\pricingProfiles.xml");
		for(Entry<Id<Charger>, ChargerPricingProfile> d:cf.getChargerPricingProfiles().entrySet()) {
			for(Entry<Integer, double[]> dd:d.getValue().getPricingProfilePerHr().entrySet()) {
//					getPricingProfile().entrySet()) {
				if(dd.getKey()>=6 && dd.getKey()<=15) {
					for(int i = 0;i<dd.getValue().length;i++){
						dd.getValue()[i] = dd.getValue()[i]*0.25;
					}
					
				}
					if(dd.getKey()>=16 && dd.getKey()<=22) {
						for(int i = 0;i<dd.getValue().length;i++){
							dd.getValue()[i] = dd.getValue()[i]*5.0;
				}
			}

		}
		}
		
	
		new ChargerPricingProfileWriter(cf).write("C:\\Users\\arsha\\git\\EVMontreal\\EVMontreal\\ABMTrans\\pricingProfilesPriced.xml");
		
		
		
		

		
		
//		Population pop= PopulationUtils.readPopulation("C:\\Users\\arsha\\git\\EVMontreal-2\\EVMontreal\\HQ\\prepared_population.xml");
//		pop.getPersons().values().stream().forEach(p->{
//			p.getPlans();
//			p.getAttributes().getAttribute("carAvail");
//		});
//	}
//}
//	
		}
		}