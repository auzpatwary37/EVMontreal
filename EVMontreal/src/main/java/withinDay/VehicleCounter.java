package withinDay;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.w3c.dom.Element;
import java.io.File;

public class VehicleCounter {
    public static void main(String[] args) {
        try {
            // Specify the path to your XML file
            File inputFile = new File("C:\\Users\\arsha\\git\\EVMontreal_newVersion\\EVMontreal\\1PDaily\\vehicle.xml");

            // Create a DocumentBuilderFactory
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            // Create a DocumentBuilder
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            // Parse the input file to get a Document object
            Document doc = dBuilder.parse(inputFile);
            // Normalize the document
            doc.getDocumentElement().normalize();

            // Get a NodeList of all the vehicle elements
            NodeList nodeList = doc.getElementsByTagName("vehicle");

            int evCount = 0;

            // Loop through all the vehicle elements
            for (int i = 0; i < nodeList.getLength(); i++) {
                Node node = nodeList.item(i);

                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element) node;
                    // Check if the type attribute is "ev1"
                    if ("ev1".equals(element.getAttribute("type"))) {
                        evCount++;
                    }
                }
            }

            // Print the count of EV1 vehicles
            System.out.println("Number of EV1 vehicles: " + evCount);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}


