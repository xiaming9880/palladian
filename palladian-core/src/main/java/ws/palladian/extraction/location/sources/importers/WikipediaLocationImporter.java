package ws.palladian.extraction.location.sources.importers;

import static ws.palladian.extraction.location.sources.importers.WikipediaLocationImporter.AlternativeNameExtraction.PAGE;
import static ws.palladian.extraction.location.sources.importers.WikipediaLocationImporter.AlternativeNameExtraction.REDIRECTS;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import ws.palladian.extraction.location.AlternativeName;
import ws.palladian.extraction.location.ImmutableLocation;
import ws.palladian.extraction.location.LocationType;
import ws.palladian.extraction.location.persistence.LocationDatabase;
import ws.palladian.extraction.location.sources.LocationStore;
import ws.palladian.helper.StopWatch;
import ws.palladian.helper.collection.CollectionHelper;
import ws.palladian.helper.functional.Consumer;
import ws.palladian.helper.io.FileHelper;
import ws.palladian.persistence.DatabaseManagerFactory;
import ws.palladian.retrieval.wiki.InfoboxTypeMapper;
import ws.palladian.retrieval.wiki.MarkupCoordinate;
import ws.palladian.retrieval.wiki.MediaWikiUtil;
import ws.palladian.retrieval.wiki.MultiStreamBZip2InputStream;
import ws.palladian.retrieval.wiki.WikiPage;
import ws.palladian.retrieval.wiki.WikiTemplate;

/**
 * <p>
 * Import locations from <a href="http://www.wikipedia.org">Wikipedia</a> pages.
 * </p>
 * 
 * @see <a href="http://dumps.wikimedia.org/enwiki/latest/">English dumps</a>
 * @see <a href="http://en.wikipedia.org/wiki/Wikipedia:Obtaining_geographic_coordinates">Link 1</a>
 * @see <a href="http://en.wikipedia.org/wiki/Wikipedia:WikiProject_Geographical_coordinates">Link 2</a>
 * @author Philipp Katz
 */
public class WikipediaLocationImporter {

    /** The logger for this class. */
    private static final Logger LOGGER = LoggerFactory.getLogger(WikipediaLocationImporter.class);

    /**
     * <p>
     * Flags to determine from where to extract alternative location names.
     * </p>
     */
    public static enum AlternativeNameExtraction {
        /** Extract alternative location names from redirects in the Wikipedia. */
        REDIRECTS,
        /** Extract alternative location names from the first paragraph of the articles. */
        PAGE
    }

    /** Pages with those titles will be ignored. */
    private static final Pattern IGNORED_PAGES = Pattern.compile("(?:Geography|Battle) of .*");

    private final LocationStore locationStore;

    private final Map<String, Integer> locationNamesIds;

    private final int idOffset;

    private final Set<AlternativeNameExtraction> nameExtraction;

    /**
     * @param locationStore The {@link LocationStore} where to store the imported data.
     * @param idOffset The offset for the inserted IDs. This way, ID clashes with existing data can be avoided. Zero for
     *            no offset (keep original IDs).
     * @param nameExtraction Specify from where to extract alternative location names (see
     *            {@link AlternativeNameExtraction}).
     */
    public WikipediaLocationImporter(LocationStore locationStore, int idOffset,
            AlternativeNameExtraction... nameExtraction) {
        Validate.notNull(locationStore, "locationStore must not be null");
        Validate.isTrue(idOffset >= 0);
        this.locationStore = locationStore;
        this.idOffset = idOffset;
        this.locationNamesIds = new HashMap<>();
        this.nameExtraction = new HashSet<AlternativeNameExtraction>(Arrays.asList(nameExtraction));
    }

    /**
     * <p>
     * Import locations from Wikipedia dump files: Pages dump file (like "enwiki-latest-pages-articles.xml.bz2").
     * </p>
     * 
     * @param dumpXml Path to the XML pages dump file (of type bz2).
     * @throws IllegalArgumentException In case the given dumps cannot be read or are of wrong type.
     * @throws IllegalStateException In case of any error during import.
     */
    public void importDumpBz2(File dumpXml) {
        Validate.notNull(dumpXml, "dumpXml must not be null");

        if (!dumpXml.isFile()) {
            throw new IllegalArgumentException("At least one of the given dump paths does not exist or is no file");
        }
        if (!dumpXml.getName().endsWith(".bz2")) {
            throw new IllegalArgumentException("XML dump file must be of type .bz2");
        }

        StopWatch stopWatch = new StopWatch();

        InputStream in = null;
        InputStream in2 = null;
        try {

            in = new MultiStreamBZip2InputStream(new BufferedInputStream(new FileInputStream(dumpXml)));
            LOGGER.info("Reading location data from {}", dumpXml);
            importLocationPages(in);

            if (nameExtraction.contains(REDIRECTS)) {
                in2 = new MultiStreamBZip2InputStream(new BufferedInputStream(new FileInputStream(dumpXml)));
                LOGGER.info("Reading location alternative names from redirects in {}", dumpXml);
                importAlternativeNames(in2);
            } else {
                LOGGER.info("Skip reading location alternative names from redirects.");
            }

        } catch (FileNotFoundException e) {
            throw new IllegalStateException(e);
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException(e);
        } catch (SAXException e) {
            throw new IllegalStateException(e);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } finally {
            FileHelper.close(in, in2);
        }
        LOGGER.info("Finished import in {}", stopWatch);
    }

    void importLocationPages(InputStream inputStream) throws ParserConfigurationException, SAXException, IOException {
        final int[] counter = new int[] {0};
        MediaWikiUtil.parseDump(inputStream, new Consumer<WikiPage>() {

            @Override
            public void process(WikiPage page) {
                if (page.getNamespaceId() != WikiPage.MAIN_NAMESPACE) {
                    return;
                }
                if (page.isRedirect()) {
                    return;
                }
                if (IGNORED_PAGES.matcher(page.getTitle()).matches()) {
                    LOGGER.debug("Ignoring '{}' by blacklist", page.getTitle());
                    return;
                }

                List<WikiTemplate> infoboxes = page.getInfoboxes();
                if (infoboxes.isEmpty()) {
                    LOGGER.debug("Page '{}' has no infobox; skip", page.getTitle());
                    return;
                }
                LocationType type = null;
                for (WikiTemplate infobox : infoboxes) {
                    type = InfoboxTypeMapper.getLocationType(infobox.getName());
                    if (type != null) {
                        break;
                    }
                }
                if (type == null) {
                    LOGGER.debug("Unmapped type for '{}'; ignore", page.getTitle());
                    return;
                }

                MarkupCoordinate coordinate = page.getCoordinate();
                // fallback, use infobox/geobox:
                if (coordinate == null) {
                    for (WikiTemplate infobox : infoboxes) {
                        Set<MarkupCoordinate> coordinates = infobox.getCoordinates();
                        // XXX we might also want to extract population information here in the future
                        if (coordinates.size() > 0) {
                            coordinate = CollectionHelper.getFirst(coordinates);
                        }
                    }
                }

                // save:
                if (coordinate != null) {
                    String cleanArticleName = page.getCleanTitle();
                    int locationId = Integer.parseInt(page.getIdentifier()) + idOffset;
                    locationStore.save(new ImmutableLocation(locationId, cleanArticleName, type, coordinate, coordinate
                            .getPopulation()));
                    LOGGER.trace("Saved location with ID {}, name {}", page.getIdentifier(), cleanArticleName);
                    locationNamesIds.put(page.getTitle(), Integer.parseInt(page.getIdentifier()));
                    counter[0]++;

                    // extract and save alternative names if requested
                    if (nameExtraction.contains(PAGE)) {
                        List<String> alternativeTitles = page.getAlternativeTitles();
                        if (alternativeTitles.size() > 0) {
                            Set<AlternativeName> alternativeNames = new HashSet<>();
                            for (String name : alternativeTitles) {
                                if (!name.equals(cleanArticleName)) {
                                    alternativeNames.add(new AlternativeName(name));
                                }
                            }
                            locationStore.addAlternativeNames(locationId, alternativeNames);
                            LOGGER.debug("Extracted {} alternative names from page", alternativeNames.size());
                        }
                    }
                }
            }
        });
        LOGGER.info("Finished importing {} locations", counter[0]);
    }

    /**
     * Import alternative names for the locations (which are given as Wikipedia redirects).
     * 
     * @param inputStream
     * @throws SAXException
     * @throws ParserConfigurationException
     * @throws IOException
     */
    void importAlternativeNames(InputStream inputStream) throws ParserConfigurationException, SAXException, IOException {
        final int[] counter = new int[] {0};
        MediaWikiUtil.parseDump(inputStream, new Consumer<WikiPage>() {

            @Override
            public void process(WikiPage page) {
                if (page.getNamespaceId() != WikiPage.MAIN_NAMESPACE) {
                    return;
                }
                if (!page.isRedirect()) {
                    return;
                }
                // ignore redirects pointing to an anchor (e.g. 'Ashmore and Cartier Islands/Government' -> Ashmore and
                // Cartier Islands#Government)
                String redirectTo = page.getRedirectTitle();
                if (redirectTo.contains("#")) {
                    LOGGER.debug("Skip anchor redirect '{}'", redirectTo);
                    return;
                }
                Integer id = locationNamesIds.get(redirectTo);
                if (id == null) {
                    return;
                }
                String name = page.getCleanTitle();
                if (name.startsWith(redirectTo + "/")) {
                    LOGGER.debug("Skip redirect from '{}' to '{}'", name, redirectTo);
                    return;
                }
                AlternativeName alternativeName = new AlternativeName(name);
                locationStore.addAlternativeNames(id + idOffset, Collections.singleton(alternativeName));
                LOGGER.debug("Save alternative name {} for location with ID {}", name, id);
                counter[0]++;
            }
        });
        LOGGER.info("Finished importing {} alternative names", counter[0]);
    }

    public static void main(String[] args) throws Exception {
        LocationDatabase locationStore = DatabaseManagerFactory.create(LocationDatabase.class, "locations2");
        locationStore.truncate();

        WikipediaLocationImporter importer = new WikipediaLocationImporter(locationStore, 100000000, PAGE);
        File dumpXml = new File("/Users/pk/Downloads/enwiki-latest-pages-articles.xml.bz2");
        importer.importDumpBz2(dumpXml);
    }

}
