package ws.palladian.extraction.location.sources.importers;

import java.io.IOException;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ws.palladian.extraction.location.ImmutableLocation;
import ws.palladian.extraction.location.LocationType;
import ws.palladian.extraction.location.persistence.LocationDatabase;
import ws.palladian.extraction.location.sources.LocationStore;
import ws.palladian.helper.ProgressHelper;
import ws.palladian.helper.StopWatch;
import ws.palladian.helper.io.FileHelper;
import ws.palladian.helper.io.LineAction;
import ws.palladian.persistence.DatabaseManagerFactory;

/**
 * <p>
 * This class reads the HotelBase CSV locations and imports them into a given {@link LocationStore}.
 * </p>
 * 
 * @see <a href="http://api.hotelsbase.org/apiAccess.php">HotelBase</a>
 * @author David Urbansky
 */
public final class HotelsBaseImporter {

    /** The logger for this class. */
    private static final Logger LOGGER = LoggerFactory.getLogger(HotelsBaseImporter.class);

    /** The store where the imported locations are saved. */
    private final LocationStore locationStore;

    /**
     * <p>
     * Create a new {@link HotelsBaseImporter}.
     * </p>
     * 
     * @param locationStore The {@link LocationStore} where to store the data, not <code>null</code>.
     */
    public HotelsBaseImporter(LocationStore locationStore) {
        Validate.notNull(locationStore, "locationStore must not be null");
        this.locationStore = locationStore;
    }

    public void importLocations(String locationFilePath) {

        final StopWatch stopWatch = new StopWatch();

        // get the currently highest id
        final int maxId = locationStore.getHighestId();
        final int totalLocations = FileHelper.getNumberOfLines(locationFilePath) - 1;

        LineAction action = new LineAction() {

            @Override
            public void performAction(String line, int lineNumber) {
                String[] parts = line.split("~");
                if (lineNumber == 0 || parts.length < 15) {
                    return;
                }
                String hotelName = parts[1].replace("&amp;", "&");
                Double latitude = Double.valueOf(parts[12]);
                Double longitude = Double.valueOf(parts[13]);
                int id = maxId + lineNumber;
                locationStore.save(new ImmutableLocation(id, hotelName, LocationType.POI, latitude, longitude, null));
                ProgressHelper.printProgress(lineNumber, totalLocations, 1, stopWatch);
            }
        };

        FileHelper.performActionOnEveryLine(locationFilePath, action);
        LOGGER.info("imported {} locations in {}", totalLocations, stopWatch.getTotalElapsedTimeString());
    }

    public static void main(String[] args) throws IOException {
        // LocationStore locationStore = new CollectionLocationStore();
        LocationDatabase locationStore = DatabaseManagerFactory.create(LocationDatabase.class, "locations");

        String locationFilePath = "/Users/pk/Dropbox/LocationLab/hotelsbase.csv";
        HotelsBaseImporter importer = new HotelsBaseImporter(locationStore);
        importer.importLocations(locationFilePath);
    }

}