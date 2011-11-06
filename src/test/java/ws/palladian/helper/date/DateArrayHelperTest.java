package ws.palladian.helper.date;

import static org.junit.Assert.assertEquals;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Ignore;
import org.junit.Test;

import ws.palladian.control.AllTests;
import ws.palladian.extraction.date.DateGetter;
import ws.palladian.extraction.date.dates.DateType;
import ws.palladian.extraction.date.dates.ExtractedDate;
import ws.palladian.helper.ResourceHelper;

public class DateArrayHelperTest {

    @Test
    public void testFilter() throws FileNotFoundException {
        final String url = ResourceHelper.getResourcePath("/webPages/dateExtraction/zeit1.htm");

        //
        // not working (size of list is 6, not 5):
        //
//        List<ExtractedDate> date = new ArrayList<ExtractedDate>();
//        DateGetter dateGetter = new DateGetter(url);
//        
//        List<ExtractedDate> dates = dateGetter.getDate();
//        date.addAll(dates);
//        List<ExtractedDate> filter = DateArrayHelper.filter(date, DateType.ContentDate);
//        assertEquals(5, filter.size());
        
        //
        // old code was (I do not understand these ALL_TESTS either):
        //
        if (!AllTests.ALL_TESTS) {
            ArrayList<ExtractedDate> date = new ArrayList<ExtractedDate>();
            DateGetter dateGetter = new DateGetter(url);
            
            ArrayList<ExtractedDate> dates = dateGetter.getDate();
            date.addAll(dates);
            List<ExtractedDate> filter = DateArrayHelper.filter(date, DateType.ContentDate);
            assertEquals(6, filter.size());
        }
    }

    @Test
    public void testArrangeByDate() {
        ArrayList<ExtractedDate> array = new ArrayList<ExtractedDate>();
        ExtractedDate date = new ExtractedDate("2010-08-01", "YYYY-MM-DD");
        array.add(date);
        date = new ExtractedDate("2010-08-02", "YYYY-MM-DD");
        array.add(date);
        date = new ExtractedDate("2010-08-03", "YYYY-MM-DD");
        array.add(date);
        date = new ExtractedDate("2010-08-04", "YYYY-MM-DD");
        array.add(date);
        date = new ExtractedDate("2010-08-05", "YYYY-MM-DD");
        array.add(date);
        date = new ExtractedDate("2010-08-03", "YYYY-MM-DD");
        array.add(date);
        date = new ExtractedDate("2010-08-04", "YYYY-MM-DD");
        array.add(date);
        date = new ExtractedDate("2010-08-05", "YYYY-MM-DD");
        array.add(date);
        date = new ExtractedDate("2010-08-05", "YYYY-MM-DD");
        array.add(date);
        date = new ExtractedDate("2010-08-05", "YYYY-MM-DD");
        array.add(date);
        date = new ExtractedDate("2010-08-04", "YYYY-MM-DD");
        array.add(date);
        date = new ExtractedDate("2010-08-03", "YYYY-MM-DD");
        array.add(date);
        date = new ExtractedDate("2010-08-05", "YYYY-MM-DD");
        array.add(date);
        date = new ExtractedDate("2010-08-02", "YYYY-MM-DD");
        array.add(date);
        date = new ExtractedDate("2010-08-04", "YYYY-MM-DD");
        array.add(date);
        List<List<ExtractedDate>> arrangedArray = DateArrayHelper.arrangeByDate(array);
        DateComparator dc = new DateComparator();
        for (int i = 0; i < 5; i++) {
            // ExtractedDateHelper.printDateArray(arrangedArray.get(i));
            // one time 2010-08-01, two time 2010-08-02, three time 2010-08-03
            assertEquals(i + 1, arrangedArray.get(i).size());

            for (int j = 0; j < arrangedArray.get(i).size() - 1; j++) {
                int compare = dc.compare(arrangedArray.get(i).get(j), arrangedArray.get(i).get(j + 1));
                assertEquals(0, compare);
            }
        }
    }

    @Ignore
    @Test
    public void testArrangeByDate2() throws FileNotFoundException {
        final String url = ResourceHelper.getResourcePath("/webPages/dateExtraction/kullin.htm");
        if (!AllTests.ALL_TESTS) {
            ArrayList<ExtractedDate> date = new ArrayList<ExtractedDate>();
            DateGetter dateGetter = new DateGetter(url);

            ArrayList<ExtractedDate> dates = dateGetter.getDate();
            date.addAll(dates);
            List<List<ExtractedDate>> arrangedArray = DateArrayHelper.arrangeByDate(date);
            for (int i = 0; i < arrangedArray.size(); i++) {
                System.out.println("==============================================================================");
                DateArrayHelper.printDateArray(arrangedArray.get(i));
            }
        }
    }

}
