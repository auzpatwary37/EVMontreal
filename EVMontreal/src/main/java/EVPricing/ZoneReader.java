package EVPricing;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.ev.infrastructure.ChargerReader;
import org.matsim.contrib.ev.infrastructure.ChargingInfrastructureSpecification;
import org.matsim.contrib.ev.infrastructure.ChargingInfrastructureSpecificationImpl;
import org.matsim.core.network.NetworkUtils;

public class ZoneReader {
	public static void main(String[] args) throws IOException {
		String chargerFile = "Montreal_Scenario_New/Output/charger.xml";
		ChargingInfrastructureSpecification csp = new ChargingInfrastructureSpecificationImpl();
		new ChargerReader(csp).readFile(chargerFile);
		String netWorkFile = "Montreal_Scenario_New/montreal_network.xml.gz";
		Network net = NetworkUtils.readNetwork(netWorkFile);
		String zoneFile = "Montreal_Scenario_New/zones.csv";
		Map<String,Coord> zones = new HashMap<>();
		Map<String,Double> zoneMultiplier = new HashMap<>();
		if(new File(zoneFile).exists()) {
			String zoneFileHeader = "zoneId,X,Y,PricingMultiplier";

			String[] headers1 = zoneFileHeader.split(",");

			Reader in2;
			Iterable<CSVRecord> records2 = null;
			try {
				in2 = new FileReader(zoneFile);

				records2 = CSVFormat.DEFAULT
						.withHeader(headers1)
						.withFirstRecordAsHeader()
						.parse(in2);
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}


			catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			for (CSVRecord record : records2) {

				Coord coord = new Coord(Double.parseDouble(record.get("X")),Double.parseDouble(record.get("Y")));
				
				String zoneId = record.get("zoneId");
				zones.put(zoneId, coord);
				zoneMultiplier.put(zoneId, Double.parseDouble(record.get("PricingMultiplier")));
			}
		}else {
		
			Coord coord = new Coord(311903.,5049020.);
			zones.put("zone1", coord);
			zoneMultiplier.put("zone1",1.);
		}
		Network zoneNet = NetworkUtils.createNetwork();
		zones.entrySet().stream().forEach(z->{
			NetworkUtils.createAndAddNode(zoneNet, Id.createNodeId(z.getKey()), z.getValue());
		});
		FileWriter fw = new FileWriter(new File("target/zoneIDZonal.csv"));
		fw.append("chargerID,zoneID,zoneIDFromPricingProfile\n");
		fw.flush();
		String pricingProfileFile = "Montreal_Scenario_New/Output/pricingProfiles.xml"; 
		ChargerPricingProfiles a = new ChargerPricingProfileReader().readChargerPricingProfiles(pricingProfileFile);
		for(ChargerPricingProfile p: a.getChargerPricingProfiles().values()) {
			String oldZoneID = NetworkUtils.getNearestNode(zoneNet, net.getLinks().get(csp.getChargerSpecifications().get(p.getChargerId()).getLinkId()).getCoord()).getId().toString();
			fw.append(p.getChargerId().toString()+","+oldZoneID+","+p.getZoneId().toString()+"\n");
			fw.flush();
		}
		
		fw.close();
	}

}
