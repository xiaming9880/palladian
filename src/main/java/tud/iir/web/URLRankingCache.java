package tud.iir.web;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.log4j.Logger;

import tud.iir.helper.StopWatch;
import tud.iir.knowledge.Source;
import tud.iir.persistence.DatabaseManager;
import tud.iir.web.URLRankingServices.Service;

/**
 * Cache for {@link URLRankingServices}. As those APIs have a considerable latency, we cache their results for a
 * specific time in the database.
 * 
 * @author Philipp Katz
 * 
 */
public class URLRankingCache {

    private static final Logger LOGGER = Logger.getLogger(URLRankingCache.class);

    private int ttlSeconds = 60 * 60 * 24;

    private PreparedStatement getSourceByUrl;
    private PreparedStatement getSourceRankings;
    private PreparedStatement addSource;
    private PreparedStatement addSourceRanking;

    public URLRankingCache() {
        StopWatch sw = new StopWatch();
        Connection connection = DatabaseManager.getInstance().getConnection();
        try {
            getSourceByUrl = connection.prepareStatement("SELECT id FROM sources WHERE url = ?");
            getSourceRankings = connection
                    .prepareStatement("SELECT ranking, service FROM source_ranking_features WHERE sourceId = ? AND CURRENT_TIMESTAMP - updated < ?");
            addSource = connection.prepareStatement("INSERT IGNORE INTO sources SET url = ?");
            addSourceRanking = connection
                    .prepareStatement("INSERT INTO source_ranking_features (sourceId, service, ranking) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE ranking = VALUES(ranking), updated = CURRENT_TIMESTAMP");
        } catch (SQLException e) {
            LOGGER.error(e);
        }
        LOGGER.trace("<init> URLRankingCache:" + sw.getElapsedTime());
    }

    /**
     * Get a Source object for the specified url. Return <code>null</code> if no such Source.
     * 
     * @param url
     * @return
     */
    public Source getSource(String url) {

        Source source = null;

        try {
            getSourceByUrl.setString(1, url);
            ResultSet rs = getSourceByUrl.executeQuery();
            if (rs.next()) {
                int sourceId = rs.getInt(1);
                source = new Source(url);
                source.setID(sourceId);
            } else {
                LOGGER.debug("source for " + url + " not found.");
            }
            rs.close();

        } catch (SQLException e) {
            LOGGER.error(e);
        }

        return source;
    }

    /**
     * Get cached ranking values for specified Source. Returns only those values which are under the specified TTL or
     * an empty list if there are no cached or up-to-date ranking values, never <code>null</code>.
     * 
     * @param source
     * @return
     */
    public Map<Service, Float> get(Source source) {

        Map<Service, Float> result = new HashMap<Service, Float>();

        try {

            getSourceRankings.setInt(1, source.getID());
            getSourceRankings.setInt(2, ttlSeconds);
            ResultSet rs = getSourceRankings.executeQuery();
            while (rs.next()) {
                float ranking = rs.getFloat(1);
                Service service = Service.getById(rs.getInt(2));
                result.put(service, ranking);
                LOGGER.debug("cache hit for " + source + " : " + service + ":" + ranking);
            }
            rs.close();

        } catch (SQLException e) {
            LOGGER.error(e);
        }

        return result;

    }

    /**
     * Adds or updates rankings for a specific Source in the cache.
     * 
     * @param source
     * @param rankings
     */
    public void add(Source source, Map<Service, Float> rankings) {

        try {

            int sourceId = source.getID();

            // -1 means : source is not persistent, create new DB entry
            if (sourceId == -1) {
                addSource.setString(1, source.getUrl());
                addSource.executeUpdate();
                sourceId = DatabaseManager.getInstance().getLastInsertID();
                // write the ID of the persistent entry back to the object.
                source.setID(sourceId);
            }

            for (Entry<Service, Float> ranking : rankings.entrySet()) {
                addSourceRanking.setInt(1, sourceId);
                addSourceRanking.setInt(2, ranking.getKey().getServiceId());
                addSourceRanking.setFloat(3, ranking.getValue());
                addSourceRanking.executeUpdate();
            }

        } catch (SQLException e) {
            LOGGER.error(e);
        }

    }

    public void setTtlSeconds(int ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    private void clear() {
        DatabaseManager.getInstance().runUpdate("TRUNCATE TABLE sources");
        DatabaseManager.getInstance().runUpdate("TRUNCATE TABLE source_ranking_features");
    }

    public static void main(String[] args) {

        URLRankingCache cache = new URLRankingCache();
        cache.clear();

        // cache.setTtlSeconds(10);
        //        
        // cache.add("http://cnn.com/", URLRankingServices.Service.BITLY_CLICKS, 100);
        // cache.get("http://cnn.com/", URLRankingServices.Service.BITLY_CLICKS);
        // cache.add("http://cnn.com/", URLRankingServices.Service.BITLY_CLICKS, 120);
        // ThreadHelper.sleep(5000);
        // cache.get("http://cnn.com/", URLRankingServices.Service.BITLY_CLICKS);
        // ThreadHelper.sleep(5000);
        // cache.get("http://cnn.com/", URLRankingServices.Service.BITLY_CLICKS);
    }

}
