package urbanEV.analysis;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.matsim.core.controler.IterationCounter;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.mobsim.framework.events.MobsimBeforeCleanupEvent;
import org.matsim.core.mobsim.framework.listeners.MobsimBeforeCleanupListener;

import com.google.inject.Inject;

public class EVMobsimListenerAndWriter implements MobsimBeforeCleanupListener {



        @Inject
        ChargerToXY chargerToXY;
        @Inject
        OutputDirectoryHierarchy controlerIO;
        @Inject
        IterationCounter iterationCounter;


        @Override
        public void notifyMobsimBeforeCleanup(MobsimBeforeCleanupEvent mobsimBeforeCleanupEvent) {
            CSVPrinter csvPrinter = null;
            try {
                csvPrinter = new CSVPrinter(Files.newBufferedWriter(Paths.get(controlerIO.getIterationFilename(iterationCounter.getIterationNumber(), "chargerXYData.csv"))), CSVFormat.DEFAULT.withDelimiter(';').
                        withHeader("X", "Y", "chargeEndTime", "Time", "ChargerId", "chargingVehicles"));
                List<ChargerToXY.XYDataContainer> dataContainers = chargerToXY.getDataContainers();

                for (ChargerToXY.XYDataContainer dataContainer : dataContainers) {

                    csvPrinter.printRecord(dataContainer.x, dataContainer.y, dataContainer.time, dataContainer.chargerId, dataContainer.chargingVehicles);
                }
                csvPrinter.close();
            } catch (IOException exception) {
                exception.printStackTrace();
            }
        }
    }
