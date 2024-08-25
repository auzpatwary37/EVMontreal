package withinDay;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.w3c.dom.Element;
import java.util.HashMap;
import java.util.Map;

public class ChargerCounter {

    public static void main(String[] args) {
        try {
            // Load and parse the XML file
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse("C:\\Users\\arsha\\git\\EVMontreal_newVersion\\EVMontreal\\TRB2024\\charger.xml"); // Replace with your file path
            doc.getDocumentElement().normalize();

            // Get all charger elements
            NodeList nList = doc.getElementsByTagName("charger");

            // Maps to store the counts and plug counts
            Map<String, Integer> chargerCounts = new HashMap<>();
            Map<String, Integer> plugCounts = new HashMap<>();

            // Process each charger element
            for (int temp = 0; temp < nList.getLength(); temp++) {
                Node nNode = nList.item(temp);
                if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element eElement = (Element) nNode;

                    String type = eElement.getAttribute("type");
                    int plugCount = Integer.parseInt(eElement.getAttribute("plug_count"));

                    // Update the counts for the charger type
                    chargerCounts.put(type, chargerCounts.getOrDefault(type, 0) + 1);
                    plugCounts.put(type, plugCounts.getOrDefault(type, 0) + plugCount);
                }
            }

            // Print the results
            System.out.println("Charger Counts:");
            for (String type : chargerCounts.keySet()) {
                System.out.println(type + ": " + chargerCounts.get(type) + " chargers, " + plugCounts.get(type) + " plugs");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
