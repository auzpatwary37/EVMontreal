package EVPricing;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map.Entry;

import org.matsim.api.core.v01.Id;
import org.matsim.contrib.ev.infrastructure.Charger;

public class Utils {

	public static void extractChargerZoneId(String pricingFileLoc,String writeLoc) {
		ChargerPricingProfiles pp = new ChargerPricingProfileReader().readChargerPricingProfiles(pricingFileLoc);
		FileWriter fw = null;
		try {
			fw = new FileWriter(new File(writeLoc));
		} catch (IOException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
		}
		try {
			fw.append("ChargerId,ZoneId\n");
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		for(Entry<Id<Charger>, ChargerPricingProfile> p:pp.getChargerPricingProfiles().entrySet()) {
			try {
				fw.append(p.getKey().toString()+","+p.getValue().getZoneId().toString()+"\n");
				fw.flush();
			
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			
		}
		try {
			fw.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	public static void main(String[] args) {
		extractChargerZoneId("C:\\Users\\arsha\\OneDrive\\Desktop\\MUM\\MtlZonalBF\\pricingProfiles.xml","chargerzoneIDZBF.csv");
	}
}
