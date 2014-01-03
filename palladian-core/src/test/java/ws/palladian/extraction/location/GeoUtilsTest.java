package ws.palladian.extraction.location;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Collection;

import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import ws.palladian.helper.StopWatch;
import ws.palladian.helper.collection.CollectionHelper;

public class GeoUtilsTest {

    private static Collection<GeoCoordinate> coordinates1;
    private static Collection<GeoCoordinate> coordinates2;
    private static Collection<GeoCoordinate> coordinates3;
    private static Collection<GeoCoordinate> coordinates4;

    @BeforeClass
    public static void setUp() {
        coordinates1 = CollectionHelper.newHashSet();
        coordinates1.add(new ImmutableGeoCoordinate(52.52437, 13.41053));
        coordinates1.add(new ImmutableGeoCoordinate(51.50853, -0.12574));
        coordinates1.add(new ImmutableGeoCoordinate(47.66033, 9.17582));
        coordinates1.add(new ImmutableGeoCoordinate(45.74846, 4.84671));

        coordinates2 = CollectionHelper.newHashSet();
        coordinates2.add(new ImmutableGeoCoordinate(40.71427, -74.00597));
        coordinates2.add(new ImmutableGeoCoordinate(35.68950, 139.69171));

        coordinates3 = CollectionHelper.newHashSet();
        coordinates3.add(new ImmutableGeoCoordinate(52.52437, 13.41053));

        coordinates4 = CollectionHelper.newHashSet();
        coordinates4.add(new ImmutableGeoCoordinate(39.00027, -105.50083));
        coordinates4.add(new ImmutableGeoCoordinate(52.16045, -0.70312));
        coordinates4.add(new ImmutableGeoCoordinate(-33, -56));
        coordinates4.add(new ImmutableGeoCoordinate(39.5, -8));
        coordinates4.add(new ImmutableGeoCoordinate(54.75844, -2.69531));
        coordinates4.add(new ImmutableGeoCoordinate(39.76, -98.5));
        coordinates4.add(new ImmutableGeoCoordinate(51.297, 1.069));
        coordinates4.add(new ImmutableGeoCoordinate(52.5, -3.5));
        coordinates4.add(new ImmutableGeoCoordinate(38.89511, -77.03637));
    }

    @Test
    public void testMidpoint() {
        GeoCoordinate midpoint = GeoUtils.getMidpoint(coordinates1);
        assertEquals(49.464867, midpoint.getLatitude(), 0.01);
        assertEquals(6.7807, midpoint.getLongitude(), 0.01);

        midpoint = GeoUtils.getMidpoint(coordinates2);
        assertEquals(69.660652, midpoint.getLatitude(), 0.01);
        assertEquals(-153.661864, midpoint.getLongitude(), 0.01);

        midpoint = GeoUtils.getMidpoint(coordinates3);
        assertEquals(new ImmutableGeoCoordinate(52.52437, 13.41053), midpoint);

        midpoint = GeoUtils.getMidpoint(coordinates4);
        assertEquals(47.703117, midpoint.getLatitude(), 0.01);
        assertEquals(-41.737184, midpoint.getLongitude(), 0.01);
    }

    @Test
    public void testCenterOfMinimumDistance() {
        GeoCoordinate center = GeoUtils.getCenterOfMinimumDistance(coordinates1);
        assertEquals(48.337076, center.getLatitude(), 0.01);
        assertEquals(7.758056, center.getLongitude(), 0.01);

        center = GeoUtils.getCenterOfMinimumDistance(coordinates3);
        assertEquals(new ImmutableGeoCoordinate(52.52437, 13.41053), center);

        center = GeoUtils.getCenterOfMinimumDistance(coordinates4);
        assertEquals(52.52425, center.getLatitude(), 0.01);
        assertEquals(-5.220439, center.getLongitude(), 0.01);
    }

    @Test
    public void testDmsToDec() {
        assertEquals(40, GeoUtils.parseDms("40°"), 0);
        assertEquals(-73.94, GeoUtils.parseDms("73°56.4′W"), 0);
        assertEquals(40.446195, GeoUtils.parseDms("40:26:46.302N"), 0.05);
        assertEquals(40.446195, GeoUtils.parseDms("40d 26′ 47″ N"), 0.05);
        assertEquals(40.446195, GeoUtils.parseDms("40°26′47″N"), 0.05);
        assertEquals(33.676176, GeoUtils.parseDms("33.676176° N"), 0.05);
        assertEquals(33.575, GeoUtils.parseDms("33°34'30\" N"), 0.05);
        assertEquals(42.443333, GeoUtils.parseDms("42° 26' 36'' N"), 0.05);
        assertEquals(42.7335, GeoUtils.parseDms("42° 44′ 0.6″ N"), 0.05);
        assertEquals(42.904722, GeoUtils.parseDms("42°54'17\" N"), 0.05);
        assertEquals(39.716667, GeoUtils.parseDms("39°43' North"), 0.05);
        assertEquals(42.904722, GeoUtils.parseDms("42°54'17\" N"), 0.05);
    }

    @Test
    public void testGetTestPoints() {
        GeoCoordinate origin = new ImmutableGeoCoordinate(53.320556, 1.729722);
        GeoCoordinate[] points = GeoUtils.getTestPoints(origin, 100);
        for (GeoCoordinate point : points) {
            assertEquals(100, point.distance(origin), 0.001);
        }
    }
    
    @Test
    public void testApproximateDistance() {
        GeoCoordinate c1 = new ImmutableGeoCoordinate(33.662508, -95.547692);
        GeoCoordinate c2 = new ImmutableGeoCoordinate(48.85341, 2.3488);
        GeoCoordinate c3 = new ImmutableGeoCoordinate(49.265278, 4.028611);
        GeoCoordinate c4 = new ImmutableGeoCoordinate(48.858222, 2.2945);
        
        // should be: exact < approximate < 1.1 * exact
        double exact = c1.distance(c2);
        double approximate = GeoUtils.approximateDistance(c1, c2);
        assertTrue(exact < approximate);
        assertTrue(approximate < exact * 1.1);
        
        exact = c2.distance(c3);
        approximate = GeoUtils.approximateDistance(c2, c3);
        assertTrue(exact < approximate);
        assertTrue(approximate < exact * 1.1);
        
        exact = c2.distance(c4);
        approximate = GeoUtils.approximateDistance(c2, c4);
        assertTrue(exact < approximate);
        assertTrue(approximate < exact * 1.1);
    }
    
    @Test
    @Ignore
    public void performanceTest() {
        GeoCoordinate c1 = new ImmutableGeoCoordinate(33.662508, -95.547692);
        GeoCoordinate c2 = new ImmutableGeoCoordinate(48.85341, 2.3488);

        StopWatch stopExact = new StopWatch();
        for (int i = 0; i < 10000000; i++) {
            c1.distance(c2);
        }
        System.out.println("Time with exact distance: " + stopExact);

        StopWatch stopApproximate = new StopWatch();
        for (int i = 0; i < 10000000; i++) {
            GeoUtils.approximateDistance(c1, c2);
        }
        System.out.println("Time with approximate distance: " + stopApproximate);
        System.out.println("Speedup: " + (double)stopExact.getElapsedTime() / stopApproximate.getElapsedTime());
        // speedup is about 90x!
    }

}
