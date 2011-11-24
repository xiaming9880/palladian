package ws.palladian.retrieval.ranking.services;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import ws.palladian.helper.ConfigHolder;
import ws.palladian.helper.UrlHelper;
import ws.palladian.helper.nlp.StringHelper;
import ws.palladian.retrieval.HttpException;
import ws.palladian.retrieval.HttpResult;
import ws.palladian.retrieval.ranking.Ranking;
import ws.palladian.retrieval.ranking.RankingService;
import ws.palladian.retrieval.ranking.RankingType;

/**
 * <p>
 * RankingService implementation to get the number of bookmarks of a given URL on BibSonomy. At the moment it returns
 * number for all bookmarks containing the url or a longer version - e.g. www.google.com will give number for all
 * bookmarks containing www.google.com/...
 * </p>
 * <p>
 * No information about request limits.
 * </p>
 * 
 * @author Julien Schmehl
 * @author Philipp Katz
 * @see http://www.bibsonomy.org
 */
public class BibsonomyBookmarks extends BaseRankingService implements RankingService {

    /** The class logger. */
    private static final Logger LOGGER = Logger.getLogger(BibsonomyBookmarks.class);

    /** The config values. */
    private String login;
    private String apiKey;

    /** The id of this service. */
    private static final String SERVICE_ID = "bibsonomy";

    /** The ranking value types of this service **/
    public static final RankingType BOOKMARKS = new RankingType("bibsonomy_bookmarks", "Bibsonomy Bookmarks",
            "The number of bookmarks users have created for this url.");

    private static final List<RankingType> RANKING_TYPES = new ArrayList<RankingType>();
    static {
        RANKING_TYPES.add(BOOKMARKS);
    }

    /** Fields to check the service availability. */
    private static boolean blocked = false;
    private static long lastCheckBlocked;
    private final static int checkBlockedIntervall = 1000 * 60 * 1;

    public BibsonomyBookmarks() {

        super();

        PropertiesConfiguration configuration = ConfigHolder.getInstance().getConfig();

        if (configuration != null) {
            setLogin(configuration.getString("api.bibsonomy.login"));
            setApiKey(configuration.getString("api.bibsonomy.key"));
        } else {
            LOGGER.warn("could not load configuration, ranking retrieval won't work");
        }

    }

    @Override
    public Ranking getRanking(String url) {
        Map<RankingType, Float> results = new HashMap<RankingType, Float>();
        Ranking ranking = new Ranking(this, url, results);
        if (isBlocked()) {
            return ranking;
        }

        try {

            String encUrl = UrlHelper.urlEncode(url);
            // authenticate via HTTP Auth and send GET request
            String pass = getLogin() + ":" + getApiKey();
            Map<String, String> headerParams = new HashMap<String, String>();
            headerParams.put("Authorization", "Basic " + StringHelper.encodeBase64(pass));
            HttpResult getResult = retriever.httpGet(
                    "http://www.bibsonomy.org/api/posts?format=json&resourcetype=bookmark&start=0&end=999999&search="
                            + encUrl, headerParams);
            String response = new String(getResult.getContent());

            // create JSON-Object from response
            JSONObject json = null;
            if (response.length() > 0) {
                json = new JSONObject(response);
            }

            if (json != null) {
                float result = json.getJSONObject("posts").getInt("end");
                results.put(BOOKMARKS, result);
                LOGGER.trace("Bibsonomy bookmarks for " + url + " : " + result);
            } else {
                results.put(BOOKMARKS, null);
                LOGGER.trace("Bibsonomy bookmarks for " + url + "could not be fetched");
            }

        } catch (JSONException e) {
            LOGGER.error("JSONException " + e.getMessage());
            checkBlocked();
        } catch (IOException e) {
            LOGGER.error("IOException " + e.getMessage());
            checkBlocked();
        }

        return ranking;
    }

    @Override
    public boolean checkBlocked() {
        int status = -1;
        try {
            // authenticate via HTTP Auth and send GET request
            if (getLogin() == null || getApiKey() == null) {
                throw new IllegalStateException("login or api key is missing.");
            }
            String pass = getLogin() + ":" + getApiKey();
            Map<String, String> headerParams = new HashMap<String, String>();
            headerParams.put("Authorization", "Basic " + StringHelper.encodeBase64(pass));
            HttpResult getResult;
            getResult = retriever
                    .httpGet(
                            "http://www.bibsonomy.org/api/posts?format=json&resourcetype=bookmark&start=0&end=999999&search=http://www.google.com/",
                            headerParams);
            status = getResult.getStatusCode();
        } catch (HttpException e) {
            LOGGER.error("HttpException " + e.getMessage());
        }
        if (status == 200) {
            blocked = false;
            lastCheckBlocked = new Date().getTime();
            return false;
        }
        blocked = true;
        lastCheckBlocked = new Date().getTime();
        LOGGER.error("Bibsonomy Ranking Service is momentarily blocked. Will check again in 1min.");
        return true;
    }

    @Override
    public boolean isBlocked() {
        if (new Date().getTime() - lastCheckBlocked < checkBlockedIntervall) {
            return blocked;
        } else {
            return checkBlocked();
        }
    }

    @Override
    public void resetBlocked() {
        blocked = false;
        lastCheckBlocked = new Date().getTime();
    }

    @Override
    public String getServiceId() {
        return SERVICE_ID;
    }

    @Override
    public List<RankingType> getRankingTypes() {
        return RANKING_TYPES;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public String getLogin() {
        return login;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiKey() {
        return apiKey;
    }

}
