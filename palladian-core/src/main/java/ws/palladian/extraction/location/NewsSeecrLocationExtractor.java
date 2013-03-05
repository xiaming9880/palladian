package ws.palladian.extraction.location;

import org.apache.commons.lang3.Validate;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ws.palladian.extraction.entity.Annotations;
import ws.palladian.retrieval.HttpException;
import ws.palladian.retrieval.HttpRequest;
import ws.palladian.retrieval.HttpRequest.HttpMethod;
import ws.palladian.retrieval.HttpResult;
import ws.palladian.retrieval.HttpRetriever;
import ws.palladian.retrieval.HttpRetrieverFactory;
import ws.palladian.retrieval.helper.HttpHelper;

/**
 * <p>
 * This class provides the functionality of {@link PalladianLocationExtractor} as web service from <a
 * href="http://newsseecr.com">NewsSeecr</a>.
 * </p>
 * 
 * @see <a href="https://www.mashape.com/qqilihq/newsseecr">API documentation on Mashape</a>
 * @author Philipp Katz
 */
public final class NewsSeecrLocationExtractor extends LocationExtractor {

    /** The logger for this class. */
    private static final Logger LOGGER = LoggerFactory.getLogger(NewsSeecrLocationExtractor.class);

    /** The name of this extractor. */
    private static final String EXTRACTOR_NAME = "Palladian/NewsSeecr";

    // private static final String BASE_URL = "http://localhost:8080/api/locations/extract";
    private static final String BASE_URL = "https://qqilihq-newsseecr.p.mashape.com/locations/extract";

    private final String mashapeKey;

    private final HttpRetriever retriever = HttpRetrieverFactory.getHttpRetriever();

    /**
     * <p>
     * Create a new {@link NewsSeecrLocationExtractor} with the provided credentials from Mashape.
     * </p>
     * 
     * @param mashapeKey The Mashape key, not <code>null</code> or empty.
     */
    public NewsSeecrLocationExtractor(String mashapeKey) {
        Validate.notEmpty(mashapeKey, "mashapeKey must not be empty");
        this.mashapeKey = mashapeKey;
    }

    @Override
    public Annotations getAnnotations(String inputText) {
        HttpRequest request = new HttpRequest(HttpMethod.POST, BASE_URL);
        request.addParameter("text", inputText);
        request.addHeader("X-Mashape-Authorization", mashapeKey);
        HttpResult result;
        try {
            result = retriever.execute(request);
        } catch (HttpException e) {
            throw new IllegalStateException("HTTP exception while accessing the web service: " + e.getMessage(), e);
        }

        String resultString = HttpHelper.getStringContent(result);
        LOGGER.debug("Result JSON: {}", resultString);
        try {
            Annotations annotations = new Annotations();
            JSONObject jsonResult = new JSONObject(resultString);
            JSONArray resultArray = jsonResult.getJSONArray("results");
            for (int i = 0; i < resultArray.length(); i++) {
                JSONObject currentResult = resultArray.getJSONObject(i);
                int startPos = currentResult.getInt("startPosition");
                int endPos = currentResult.getInt("endPosition");
                String name = currentResult.getString("name");
                LocationType type = LocationType.valueOf(currentResult.getString("type"));
                Double lat = currentResult.optDouble("latitude");
                Double lng = currentResult.optDouble("longitude");
                annotations.add(new LocationAnnotation(startPos, endPos, name, type, lat, lng));

            }
            return annotations;
        } catch (JSONException e) {
            throw new IllegalStateException("Error while parsing the JSON: " + e.getMessage(), e);
        }
    }

    @Override
    public String getName() {
        return EXTRACTOR_NAME;
    }

}
