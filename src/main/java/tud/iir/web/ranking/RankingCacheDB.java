package tud.iir.web.ranking;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.log4j.Logger;

import tud.iir.persistence.DatabaseManager;
import tud.iir.persistence.ResultCallback;
import tud.iir.persistence.SimpleRowConverter;
import tud.iir.web.ranking.RankingRetriever.Service;

/**
 * Cache for {@link RankingRetriever}. As those APIs have a considerable latency, we cache their results for a
 * specific time in the database.
 * 
 * TODO caching ttl sometimes does not work correctly.
 * 
 * @author Philipp Katz
 * 
 */
public class RankingCacheDB extends RankingCache {

    /** The logger for this class. */
    private static final Logger LOGGER = Logger.getLogger(RankingCacheDB.class);

    /** Prepared statements to get ranking with TTL. */
    private static final String getRankings = "SELECT ranking, service FROM rankingCache WHERE url = ? AND CURRENT_TIMESTAMP - updated < ?";

    /** Prepared statement to get ranking without TTL. */
    private static final String getRankings2 = "SELECT ranking, service FROM rankingCache WHERE url = ?";

    /** Prepared statement to add ranking. */
    private static final String addRanking = "INSERT INTO rankingCache (url, service, ranking) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE ranking = VALUES(ranking), updated = CURRENT_TIMESTAMP";

    /** The database manager. */
    private DatabaseManager databaseManager = new DatabaseManager();

    /*
     * (non-Javadoc)
     * @see tud.iir.web.URLRankingCache#get(tud.iir.knowledge.Source)
     */
    @Override
    public Map<Service, Float> get(final String url) {

        final Map<Service, Float> result = new HashMap<Service, Float>();

        ResultCallback<Map<String, Object>> callback = new ResultCallback<Map<String, Object>>() {

            @Override
            public void processResult(Map<String, Object> object, int number) {
                float ranking = (Float) object.get("ranking");
                Integer serviceId = (Integer) object.get("service");
                Service service = Service.getById(serviceId);
                result.put(service, ranking);
                LOGGER.debug("cache hit for " + url + " : " + service + ":" + ranking);
            }
        };

        if (getTtlSeconds() == -1) {
            // System.out.println("without ttl");
            databaseManager.runQuery(callback, new SimpleRowConverter(), getRankings2, url);
        } else {
            // System.out.println("with ttl");
            databaseManager.runQuery(callback, new SimpleRowConverter(), getRankings, url, getTtlSeconds());
        }

        return result;

    }

    /*
     * (non-Javadoc)
     * @see tud.iir.web.URLRankingCache#add(tud.iir.knowledge.Source, java.util.Map)
     */
    @Override
    public void add(String url, Map<Service, Float> rankings) {

        for (Entry<Service, Float> ranking : rankings.entrySet()) {
            databaseManager.runUpdate(addRanking, url, ranking.getKey().getServiceId(), ranking.getValue());
        }

    }

    private void clear() {
        databaseManager.runUpdate("TRUNCATE TABLE rankingCache");
    }

    public static void main(String[] args) {
        RankingCacheDB cache = new RankingCacheDB();
        cache.clear();
    }

}
