package ws.palladian.retrieval.ranking.services;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ws.palladian.helper.UrlHelper;
import ws.palladian.helper.nlp.StringHelper;
import ws.palladian.retrieval.HttpException;
import ws.palladian.retrieval.HttpResult;
import ws.palladian.retrieval.ranking.Ranking;
import ws.palladian.retrieval.ranking.RankingService;
import ws.palladian.retrieval.ranking.RankingServiceException;
import ws.palladian.retrieval.ranking.RankingType;

/**
 * <p>
 * RankingService implementation to find the number of backlinks to the domain and concrete page using the SemRush
 * index.
 * </p>
 * 
 * @author David Urbansky
 * 
 */
public final class SemRush extends AbstractRankingService implements RankingService {

    /** The id of this service. */
    private static final String SERVICE_ID = "semrush";

    /** The ranking value types of this service **/
    public static final RankingType BACKLINKS_DOMAIN = new RankingType("semrushbacklinksdomain", "Backlinks Domain",
            "The Number of Backlinks to the Domain");
    public static final RankingType BACKLINKS_PAGE = new RankingType("semrushbacklinkspage", "Backlinks Page",
            "The Number of Backlinks to the Page");

    /** All available ranking types by {@link SemRush}. */
    private static final List<RankingType> RANKING_TYPES = Arrays.asList(BACKLINKS_DOMAIN, BACKLINKS_PAGE);

    @Override
    public Ranking getRanking(String url) throws RankingServiceException {
        Map<RankingType, Float> results = new HashMap<RankingType, Float>();

        String requestUrl = buildRequestUrl(url);
        HttpResult httpResult;
        try {
            httpResult = retriever.httpGet(requestUrl);
        } catch (HttpException e) {
            throw new RankingServiceException("HTTP exception while checking ranking for \"" + url + "\"", e);
        }
        String text = httpResult.getStringContent();
        try {
            long backlinksDomain = Long.valueOf(StringHelper.getSubstringBetween(text, "<links_domain>",
                    "</links_domain>"));
            long backlinksPage = Long.valueOf(StringHelper.getSubstringBetween(text, "<links>", "</links>"));
            results.put(BACKLINKS_DOMAIN, (float)backlinksDomain);
            results.put(BACKLINKS_PAGE, (float)backlinksPage);
        } catch (Exception e) {
            throw new RankingServiceException("Error while parsing the response (\"" + text + "\")", e);
        }

        return new Ranking(this, url, results);
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
        return "http://publicapi.bl.semrush.com/?url=" + UrlHelper.encodeParameter(url);
    }

    @Override
    public String getServiceId() {
        return SERVICE_ID;
    }

    @Override
    public List<RankingType> getRankingTypes() {
        return RANKING_TYPES;
    }

    public static void main(String[] a) throws RankingServiceException {
        SemRush gpl = new SemRush();
        Ranking ranking = null;

        ranking = gpl.getRanking("http://webknox.com/p/best-funny-comic-strips");
        System.out.println(ranking);
        System.out.println(ranking.getValues().get(SemRush.BACKLINKS_DOMAIN) + " backlinks to the domain");
        System.out.println(ranking.getValues().get(SemRush.BACKLINKS_PAGE) + " backlinks to the page");
    }

}
