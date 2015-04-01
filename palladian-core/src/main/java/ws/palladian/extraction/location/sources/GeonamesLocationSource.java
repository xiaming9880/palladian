package ws.palladian.extraction.location.sources;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import ws.palladian.extraction.location.ImmutableLocation;
import ws.palladian.extraction.location.Location;
import ws.palladian.extraction.location.LocationBuilder;
import ws.palladian.extraction.location.LocationSource;
import ws.palladian.extraction.location.sources.importers.GeonamesUtil;
import ws.palladian.helper.UrlHelper;
import ws.palladian.helper.collection.CollectionHelper;
import ws.palladian.helper.constants.Language;
import ws.palladian.helper.geo.GeoCoordinate;
import ws.palladian.helper.html.XPathHelper;
import ws.palladian.retrieval.HttpResult;
import ws.palladian.retrieval.HttpRetriever;
import ws.palladian.retrieval.HttpRetrieverFactory;
import ws.palladian.retrieval.parser.DocumentParser;
import ws.palladian.retrieval.parser.ParserFactory;

/**
 * <p>
 * A {@link LocationSource} witch uses the <a href="http://www.geonames.org">Geonames</a> API. The services provides
 * 2,000 queries/hour, 30,000 queries/day for free accounts.
 * </p>
 *
 * @see <a href="http://www.geonames.org/login">Account registration</a>
 * @see <a href="http://www.geonames.org/manageaccount">Account activation</a> (enable access to web services after
 *      registration)
 * @see <a href="http://www.geonames.org/export/web-services.html">Web Service documentation</a>
 * @author Philipp Katz
 */
public class GeonamesLocationSource extends SingleQueryLocationSource {

    /** The logger for this class. */
    private static final Logger LOGGER = LoggerFactory.getLogger(GeonamesLocationSource.class);

    private final String username;

    private final DocumentParser xmlParser = ParserFactory.createXmlParser();

    private final HttpRetriever httpRetriever = HttpRetrieverFactory.getHttpRetriever();

    private boolean showLanguageWarning = true;

    /**
     * <p>
     * Create a new {@link GeonamesLocationSource} which caches requests.
     * </p>
     *
     * @param username The signed up user name, not <code>null</code> or empty.
     * @return A new {@link GeonamesLocationSource} with caching.
     */
    public static LocationSource newCachedLocationSource(String username) {
        return new CachingLocationSource(new GeonamesLocationSource(username));
    }

    /**
     * <p>
     * Create a new {@link GeonamesLocationSource}.
     * </p>
     *
     * @param username The signed up user name, not <code>null</code> or empty.
     * @deprecated Prefer using the cached variant, which can be obtained via {@link #newCachedLocationSource(String)}.
     */
    @Deprecated
    public GeonamesLocationSource(String username) {
        Validate.notEmpty(username, "username must not be empty");
        this.username = username;
    }

    @Override
    public List<Location> getLocations(String locationName, Set<Language> languages) {
        if (showLanguageWarning) {
            LOGGER.warn("Language queries are not supported; ignoring language parameter.");
            showLanguageWarning = false;
        }
        try {
            String getUrl = String.format("http://api.geonames.org/search?name_equals=%s&style=FULL&username=%s",
                    UrlHelper.encodeParameter(locationName), username);
            HttpResult httpResult = httpRetriever.httpGet(getUrl);
            Document document = xmlParser.parse(httpResult);
            List<Location> result = new ArrayList<>();
            List<Location> retrievedLocations = parseLocations(document);
            for (Location retrievedLocation : retrievedLocations) {
                List<Integer> hierarchy = getHierarchy(retrievedLocation.getId());
                result.add(new ImmutableLocation(retrievedLocation, retrievedLocation.getAlternativeNames(), hierarchy));
            }
            return result;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static List<Location> parseLocations(Document document) {
        checkError(document);
        List<Location> result = new ArrayList<>();
        List<Node> geonames = XPathHelper.getNodes(document, "//geoname");
        for (Node node : geonames) {
            Location location = parseLocation(node);
            result.add(location);
        }
        return result;
    }

    /**
     * Check, whether the service sent an error (usually, when the quota has been exceeded).
     *
     * @param document
     */
    static void checkError(Document document) {
        Node statusNode = XPathHelper.getNode(document, "//geonames/status");
        if (statusNode != null) {
            StringBuilder diagnosticsMessage = new StringBuilder();
            diagnosticsMessage.append("Error from the web service");
            NamedNodeMap attributes = statusNode.getAttributes();
            if (attributes != null) {
                Node message = attributes.getNamedItem("message");
                if (message != null) {
                    diagnosticsMessage.append(": ");
                    diagnosticsMessage.append(message.getTextContent());
                }
            }
            throw new IllegalStateException(diagnosticsMessage.toString());
        }
    }

    static Location parseLocation(Node node) {
        LocationBuilder builder = new LocationBuilder();
        builder.setPrimaryName(XPathHelper.getNode(node, "./toponymName").getTextContent());
        double latitude = Double.parseDouble(XPathHelper.getNode(node, "./lat").getTextContent());
        double longitude = Double.parseDouble(XPathHelper.getNode(node, "./lng").getTextContent());
        builder.setCoordinate(latitude, longitude);
        builder.setId(Integer.parseInt(XPathHelper.getNode(node, "./geonameId").getTextContent()));
        String featureClass = XPathHelper.getNode(node, "./fcl").getTextContent();
        String featureCode = XPathHelper.getNode(node, "./fcode").getTextContent();
        builder.setType(GeonamesUtil.mapType(featureClass, featureCode));
        String populationString = XPathHelper.getNode(node, "./population").getTextContent();
        if (!populationString.isEmpty()) {
            builder.setPopulation(Long.parseLong(populationString));
        }
        List<Node> altNameNodes = XPathHelper.getNodes(node, "./alternateName");
        for (Node altNameNode : altNameNodes) {
            String altName = altNameNode.getTextContent();
            NamedNodeMap attrs = altNameNode.getAttributes();
            Node langAttr = attrs.getNamedItem("lang");
            String altNameLang = langAttr.getTextContent();
            Language language = Language.getByIso6391(altNameLang);
            if (language != null) {
                builder.addAlternativeName(altName, language);
            }
        }
        return builder.create();
    }

    private List<Integer> getHierarchy(int locationId) {
        try {
            String getUrl = String.format("http://api.geonames.org/hierarchy?geonameId=%s&style=SHORT&username=%s",
                    locationId, username);
            HttpResult httpResult = httpRetriever.httpGet(getUrl);
            Document document = xmlParser.parse(httpResult);
            List<Node> geonames = XPathHelper.getNodes(document, "//geoname/geonameId");
            List<Integer> result = new ArrayList<>();
            for (int i = geonames.size() - 1; i >= 0; i--) {
                Node node = geonames.get(i);
                int geonameId = Integer.parseInt(node.getTextContent());
                if (geonameId == locationId) { // do not add the supplied Location itself.
                    continue;
                }
                result.add(geonameId);
            }
            return result;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public Location getLocation(int locationId) {
        try {
            String getUrl = String.format("http://api.geonames.org/get?geonameId=%s&username=%s&style=FULL",
                    locationId, username);
            HttpResult httpResult = httpRetriever.httpGet(getUrl);
            Document document = xmlParser.parse(httpResult);
            List<Location> locations = parseLocations(document);
            Location location = CollectionHelper.getFirst(locations);
            List<Integer> hierarchy = getHierarchy(locationId);
            return new ImmutableLocation(location, location.getAlternativeNames(), hierarchy);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public List<Location> getLocations(GeoCoordinate coordinate, double distance) {
        try {
            String getUrl = String.format(
                    "http://api.geonames.org/findNearby?lat=%s&lng=%s&radius=%s&username=%s&style=FULL&maxRows=100",
                    coordinate.getLatitude(), coordinate.getLongitude(), distance, username);
            LOGGER.debug("Retrieving {}", getUrl);
            HttpResult httpResult = httpRetriever.httpGet(getUrl);
            Document document = xmlParser.parse(httpResult);
            List<Location> locations = parseLocations(document);
            List<Location> result = new ArrayList<>();
            for (Location location : locations) {
                List<Integer> hierarchy = getHierarchy(location.getId());
                result.add(new ImmutableLocation(location, location.getAlternativeNames(), hierarchy));
            }
            return result;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static void main(String[] args) {
        GeonamesLocationSource locationSource = new GeonamesLocationSource("qqilihq");
        // Location location = locationSource.getLocation(7268814);
        // System.out.println(location);
        // List<Location> locations = locationSource.getLocations(new ImmutableGeoCoordinate(52.52, 13.41), 10);
        List<Location> locations = locationSource.getLocations("colombo", EnumSet.of(Language.ENGLISH));
        CollectionHelper.print(locations);
    }

}
