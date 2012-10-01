package ws.palladian.retrieval.ranking.services;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.json.JSONObject;

import ws.palladian.helper.UrlHelper;
import ws.palladian.retrieval.HttpResult;
import ws.palladian.retrieval.helper.HttpHelper;
import ws.palladian.retrieval.ranking.Ranking;
import ws.palladian.retrieval.ranking.RankingService;
import ws.palladian.retrieval.ranking.RankingType;

/**
 * <p>
 * RankingService implementation to find the number of shares of a Web page on Linked In.
 * </p>
 * 
 * @author David Urbansky
 * 
 */
public final class LinkedInShares extends BaseRankingService implements RankingService {

    /** The class logger. */
    private static final Logger LOGGER = Logger.getLogger(LinkedInShares.class);

    /** The id of this service. */
    private static final String SERVICE_ID = "linkedin";

    /** The ranking value types of this service **/
    public static final RankingType SHARES = new RankingType("linkedinshares", "Linked In Shares",
            "The Number of Shares on Linked In");

    /** All available ranking types by {@link LinkedInShares}. */
    private static final List<RankingType> RANKING_TYPES = Arrays.asList(SHARES);

    @Override
    public Ranking getRanking(String url) {
        Map<RankingType, Float> results = new HashMap<RankingType, Float>();
        Ranking ranking = new Ranking(this, url, results);
        if (isBlocked()) {
            return ranking;
        }

        Integer shares = null;
        String requestUrl = buildRequestUrl(url);

        try {
            HttpResult httpResult = retriever.httpGet(requestUrl);
            String response = HttpHelper.getStringContent(httpResult);

            if (response != null) {
                JSONObject jsonObject = new JSONObject(response);

                shares = jsonObject.getInt("count");

                LOGGER.trace("Linked In Shares for " + url + " : " + shares);
            }
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
        }

        results.put(SHARES, (float)shares);
        return ranking;
    }

    /**
     * <p>
     * Build the request URL.
     * </p>
     * 
     * @param url The URL to search for.
     * @return The request URL.
     */
    private String buildRequestUrl(String url) {
        String requestUrl = "http://www.linkedin.com/countserv/count/share?format=json&url=" + UrlHelper.urlEncode(url);
        return requestUrl;
    }

    @Override
    public String getServiceId() {
        return SERVICE_ID;
    }

    @Override
    public List<RankingType> getRankingTypes() {
        return RANKING_TYPES;
    }

    public static void main(String[] a) {
        LinkedInShares gpl = new LinkedInShares();
        Ranking ranking = null;

        ranking = gpl.getRanking("http://google.com");
        System.out.println(ranking);
        System.out.println(ranking.getValues().get(LinkedInShares.SHARES) + " shares");
    }

}