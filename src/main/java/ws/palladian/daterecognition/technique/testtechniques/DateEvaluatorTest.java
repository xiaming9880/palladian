package ws.palladian.daterecognition.technique.testtechniques;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import ws.palladian.daterecognition.DateRaterHelper;
import ws.palladian.daterecognition.dates.ArchiveDate;
import ws.palladian.daterecognition.dates.ContentDate;
import ws.palladian.daterecognition.dates.ExtractedDate;
import ws.palladian.daterecognition.dates.HTTPDate;
import ws.palladian.daterecognition.dates.HeadDate;
import ws.palladian.daterecognition.dates.ReferenceDate;
import ws.palladian.daterecognition.dates.StructureDate;
import ws.palladian.daterecognition.dates.URLDate;
import ws.palladian.daterecognition.technique.ArchiveDateRater;
import ws.palladian.daterecognition.technique.ContentDateRater;
import ws.palladian.daterecognition.technique.HeadDateRater;
import ws.palladian.daterecognition.technique.HttpDateRater;
import ws.palladian.daterecognition.technique.PageDateType;
import ws.palladian.daterecognition.technique.ReferenceDateRater;
import ws.palladian.daterecognition.technique.StructureDateRater;
import ws.palladian.daterecognition.technique.UrlDateRater;
import ws.palladian.helper.date.ContentDateComparator;
import ws.palladian.helper.date.DateArrayHelper;
import ws.palladian.helper.date.DateComparator;

/**
 * This class is responsible for rating dates. <br>
 * Therefore it uses for each technique a own rater-class. <br>
 * In the end all techniques a compared to each other by deploying methods.
 * 
 * @author Martin Gregor
 * 
 */
public class DateEvaluatorTest {

    private String url;
    private boolean referneceLookUp = false;

    private HttpDateRater httpdr;
    private HeadDateRater headdr;
    private UrlDateRater udr;
    private StructureDateRater sdr;
    private ContentDateRater cdr;
    private ArchiveDateRater adr;
    private ReferenceDateRater rdr;

    /**
     * Standard constructor.
     */
    public DateEvaluatorTest() {
    	setPubMod(PageDateType.publish);
    }
    
    /**
     * Standard constructor.
     */
    public DateEvaluatorTest(PageDateType pub_mod) {
    	setPubMod(pub_mod);
    }

    /**
     * Constructor setting url.
     * 
     * @param url
     */
    public DateEvaluatorTest(String url, PageDateType pub_mod) {
        this.url = url;
        setPubMod(pub_mod);
    }

    /**
     * Constructor setting url and activates getting and rating of references.
     * 
     * @param url
     * @param referenceLookUp
     */
    public DateEvaluatorTest(String url, boolean referenceLookUp, PageDateType pub_mod) {
        this.url = url;
        this.referneceLookUp = referenceLookUp;
        setPubMod(pub_mod);
    }
    private void setPubMod(PageDateType pub_mod){
    	httpdr = new HttpDateRater(pub_mod);
		headdr = new HeadDateRater(pub_mod);
		udr = new UrlDateRater(pub_mod);
		sdr = new StructureDateRater(pub_mod);
		cdr = new ContentDateRater(pub_mod);
		adr = new ArchiveDateRater(pub_mod);
		rdr = new ReferenceDateRater(pub_mod);
    }

    /**
     * Main method of this class.<br>
     * Coordinates all rating techniques and deploying of rates to lower techniques.
     * 
     * @param <T>
     * @param extractedDates ArrayList of ExtractedDates.
     * @return HashMap of dates, with rate as value.
     */
    @SuppressWarnings("unchecked")
    public <T> HashMap<T, Double> rate(ArrayList<T> extractedDates) {
        HashMap<T, Double> evaluatedDates = new HashMap<T, Double>();

        ArrayList<T> dates = DateArrayHelper.filter(extractedDates, DateArrayHelper.FILTER_IS_IN_RANGE);
        HashMap<T, Double> urlResult = new HashMap<T, Double>();
        HashMap<T, Double> httpResult = new HashMap<T, Double>();
        HashMap<T, Double> headResult = new HashMap<T, Double>();
        HashMap<T, Double> structResult = new HashMap<T, Double>();
        HashMap<T, Double> contResult = new HashMap<T, Double>();

        ArrayList<URLDate> urlDates = (ArrayList<URLDate>) DateArrayHelper.filter(dates, ExtractedDate.TECH_URL);

        ArrayList<HTTPDate> httpDates = (ArrayList<HTTPDate>) DateArrayHelper.filter(dates,
                ExtractedDate.TECH_HTTP_HEADER);

        ArrayList<HeadDate> headDates = (ArrayList<HeadDate>) DateArrayHelper.filter(dates,
                ExtractedDate.TECH_HTML_HEAD);

        ArrayList<StructureDate> structDates = (ArrayList<StructureDate>) DateArrayHelper.filter(dates,
                ExtractedDate.TECH_HTML_STRUC);

        ArrayList<ContentDate> contDates = (ArrayList<ContentDate>) DateArrayHelper.filter(dates,
                ExtractedDate.TECH_HTML_CONT);
        ArrayList<ContentDate> contFullDates = (ArrayList<ContentDate>) DateArrayHelper.filter(contDates,
                DateArrayHelper.FILTER_FULL_DATE);

        ArrayList<ArchiveDate> archiveDate = (ArrayList<ArchiveDate>) DateArrayHelper.filter(dates,
                ExtractedDate.TECH_ARCHIVE);

        ArrayList<ReferenceDate> referenceDate = (ArrayList<ReferenceDate>) DateArrayHelper.filter(dates,
                ExtractedDate.TECH_REFERENCE);

        if (urlDates != null && urlDates.size() > 0) {
            urlResult.putAll((Map<? extends T, ? extends Double>) udr.rate(urlDates));
        }
        if (httpDates != null && httpDates.size() > 0) {
            httpResult.putAll((Map<? extends T, ? extends Double>) httpdr.rate(httpDates));
        }
        if (headDates != null && headDates.size() > 0) {
            headResult.putAll((Map<? extends T, ? extends Double>) headdr.rate(headDates));
        }

        if (contFullDates != null && contFullDates.size() > 0) {
            contResult.putAll((Map<? extends T, ? extends Double>) cdr.rate(contFullDates));
        } else if (contDates != null && contDates.size() > 0) {
            contResult.putAll((Map<? extends T, ? extends Double>) cdr.rate(contDates));
        }
        if (urlResult.size() > 0 && contResult.size() > 0) {
            checkDayMonthYearOrder(DateArrayHelper.getFirstElement(urlResult), contResult);
        }

        if (structDates != null && structDates.size() > 0) {
            structResult.putAll((Map<? extends T, ? extends Double>) sdr.rate(structDates));
        }

        evaluatedDates.putAll(urlResult);

        evaluatedDates.putAll(headResult);
        evaluatedDates.putAll(httpResult);
        evaluatedDates.putAll(structResult);
        evaluatedDates.putAll(contResult);

        evaluatedDates.putAll(influenceHttpAndHead(httpResult, headResult));

        evaluatedDates.putAll(deployStructureDates(contResult, (HashMap<StructureDate, Double>) structResult));

        evaluatedDates.putAll(deployMetaDates(headResult, contResult));
        evaluatedDates.putAll(deployMetaDates(httpResult, contResult));

        evaluatedDates.putAll(deployMetaDates(headResult, structResult));
        evaluatedDates.putAll(deployMetaDates(httpResult, structResult));

        evaluatedDates.putAll(deployURLDate(urlResult, httpResult));
        evaluatedDates.putAll(deployURLDate(urlResult, headResult));
        evaluatedDates.putAll(deployURLDate(urlResult, structResult));
        evaluatedDates.putAll(deployURLDate(urlResult, contResult));

        if (referenceDate != null && referenceDate.size() > 0) {
            rdr.rate(referenceDate);
        } else if (referneceLookUp && url != null) {
            rdr.rate(url);
        }

        if (DateArrayHelper.isAllZero(evaluatedDates)) {
            // evaluatedDates.putAll(reRateIfAllZero(contResult));
            evaluatedDates
                    .putAll((Map<? extends T, ? extends Double>) guessRate((HashMap<ContentDate, Double>) contResult));

        }

        DateRaterHelper.writeRateInDate(evaluatedDates);

        if (archiveDate != null && archiveDate.size() > 0) {
            evaluatedDates.putAll((Map<? extends T, ? extends Double>) adr.rate(archiveDate, evaluatedDates));
        }

        return evaluatedDates;
    }

    /**
     * @see DateRaterHelper.checkDayMonthYearOrder
     * 
     */
    public <T> void checkDayMonthYearOrder(T orginalDate, HashMap<T, Double> toCheckcDates) {
        if (orginalDate != null) {
            for (Entry<T, Double> e : toCheckcDates.entrySet()) {
                if (e.getKey() != null) {
                    DateRaterHelper.checkDayMonthYearOrder(orginalDate, (ExtractedDate) e.getKey());
                }

            }
        }
    }

    /**
     * Method with calculations for new rating of dates by consideration of meta-dates like HTTP and Head.
     * 
     * @param <T>
     * @param metaDates HashMap with HTTP or head-Dates.
     * @param dates Dates to be rated.
     * @return
     */
    public static <T> HashMap<T, Double> deployMetaDates(HashMap<T, Double> metaDates, HashMap<T, Double> dates) {

        HashMap<T, Double> result = dates;
        HashMap<T, Double> temp = dates; // Where worked dates can be removed.
        HashMap<T, Double> tempContentDates; // only dates that are equal to metaDate.
        HashMap<T, Double> tempResult = new HashMap<T, Double>(); // worked dates can be put in.

        Entry<T, Double>[] orderedMetaDates = DateArrayHelper.orderHashMap(metaDates, true);
        for (int stopcounter = 0; stopcounter < 3; stopcounter++) {
            int stopFlag = DateComparator.STOP_MINUTE - stopcounter;

            for (int i = 0; i < orderedMetaDates.length; i++) {
                T metaDate = orderedMetaDates[i].getKey();
                // DateComparator.STOP_MINUTE instead of stopFlag, because original dates should be distinguished up to
                // minute.
                int countFactor = DateArrayHelper.countDates(metaDate, metaDates, DateComparator.STOP_MINUTE) + 1;
                double metaDateFactor = metaDates.get(metaDate);
                tempContentDates = DateArrayHelper.getSameDatesMap((ExtractedDate) metaDate, temp, stopFlag);
                for (Entry<T, Double> date : tempContentDates.entrySet()) {
                    double weight = (1.0 * countFactor / metaDates.size());
                    double oldRate = date.getValue();
                    double excatnesFactor = stopFlag / (1.0 * ((ExtractedDate) date.getKey()).getExactness());
                    double newRate = ((1 - oldRate) * metaDateFactor * weight * excatnesFactor) + oldRate;
                    tempResult.put(date.getKey(), Math.round(newRate * 10000) / 10000.0);
                    temp.remove(date.getKey());
                }

            }
        }
        result.putAll(tempResult);
        return result;
    }

    /**
     * Returns joint map of head and http, where rates are recalculated by cross-dependency.
     * 
     * @param <T>
     * @param httpMap
     * @param headMap
     * @return
     */
    private <T> HashMap<T, Double> influenceHttpAndHead(HashMap<T, Double> httpMap, HashMap<T, Double> headMap) {
        HashMap<T, Double> result = new HashMap<T, Double>();
        HashMap<T, Double> resultHTTP;
        HashMap<T, Double> resultHead;

        resultHead = recalc(httpMap, headMap);
        resultHTTP = recalc(headMap, httpMap);

        result.putAll(resultHead);
        result.putAll(resultHTTP);

        return result;
    }

    /**
     * Returns map2 with new values, calculated in dependency of map1.
     * 
     * @param <T>
     * @param map1
     * @param map2
     * @return
     */
    private <T> HashMap<T, Double> recalc(HashMap<T, Double> map1, HashMap<T, Double> map2) {
        HashMap<T, Double> result = new HashMap<T, Double>();
        ArrayList<HashMap<T, Double>> arrangedMap1 = DateArrayHelper.arrangeMapByDate(map1, DateComparator.STOP_MINUTE);
        for (int i = 0; i < arrangedMap1.size(); i++) {
            HashMap<T, Double> tempMap1 = arrangedMap1.get(i);
            double map1Rate = DateArrayHelper.getHighestRate(tempMap1);
            T map1Date = DateArrayHelper.getFirstElement(tempMap1);
            HashMap<T, Double> sameMap2 = DateArrayHelper.getSameDatesMap((ExtractedDate) map1Date, map2,
                    DateComparator.STOP_DAY);

            for (Entry<T, Double> e : sameMap2.entrySet()) {
                double newRate = ((1 - e.getValue()) * map1Rate * (tempMap1.size() * 1.0 / map1.size())) + e.getValue();
                result.put(e.getKey(), Math.round(newRate * 1000) / 10000.0);
            }
        }
        return result;
    }

    /**
     * Recalculates dates in dependency of url-dates. <br>
     * 
     * @param <T>
     * @param urlDates
     * @param dates
     * @return
     */
    private <T> HashMap<T, Double> deployURLDate(HashMap<T, Double> urlDates, HashMap<T, Double> dates) {
        HashMap<T, Double> result = dates;
        for (Entry<T, Double> url : urlDates.entrySet()) {
            URLDate urlDate = (URLDate) url.getKey();
            double urlRate = url.getValue();
            double urlFactor = (Math.min(urlDate.getExactness(), 3)) / 3.0;
            HashMap<T, Double> temp = DateArrayHelper.getSameDatesMap(urlDate, dates, urlDate.getExactness());
            for (Entry<T, Double> date : temp.entrySet()) {
                double newRate = ((1 - date.getValue()) * (urlRate * urlFactor)) + date.getValue();
                result.put(date.getKey(), Math.round(newRate * 10000) / 10000.0);
            }
            /**
             * temp = DateArrayHelper.getDifferentDatesMap(urlDate, dates, urlDate.getExactness());
             * for (Entry<T, Double> date : temp.entrySet()) {
             * double newRate = date.getValue() - (0.2 * date.getValue() * (urlRate * urlFactor));
             * result.put(date.getKey(), Math.round(newRate * 10000) / 10000.0);
             * }
             */
        }
        return result;
    }

    /**
     * Recalculate rates, if all are zero.<br>
     * First get the dates with most dateparts (year, month, day, hour, minute, second). Oldest get the rate of <br>
     * (all oldest dates)/(all dates). <br>
     * Second get the oldest dates of all. New rate is <br>
     * ((1-value)*(all oldest dates)/(all dates))+(value). <br>
     * 
     * @param <T>
     * @param dates
     * @return
     */
    private <T> HashMap<T, Double> reRateIfAllZero(HashMap<T, Double> dates) {
        HashMap<T, Double> result = dates;

        HashMap<T, Double> exactestDates = DateArrayHelper.getExactestMap(dates);
        DateComparator dc = new DateComparator();
        T date = dc.getOldestDate(exactestDates);

        HashMap<T, Double> sameDates = DateArrayHelper.getSameDatesMap((ExtractedDate) date, result);
        for (Entry<T, Double> e : sameDates.entrySet()) {
            double newRate = (0.1 * sameDates.size()) / result.size();
            result.put(e.getKey(), Math.round(newRate * 10000) / 10000.0);
        }

        return result;

    }

    /**
     * This method calculates new rates for content-dates in dependency of position in document and age of date.
     * 
     * @param dates
     * @return New rated dates.
     */
    private HashMap<ContentDate, Double> guessRate(HashMap<ContentDate, Double> dates) {
        HashMap<ContentDate, Double> result = dates;
        if (result.size() > 0) {
            ArrayList<ContentDate> orderAge = DateArrayHelper.hashMapToArrayList(dates);
            ArrayList<ContentDate> orderPosInDoc = orderAge;

            DateComparator dc = new DateComparator();
            Collections.sort(orderAge, dc);
            Collections.sort(orderPosInDoc, new ContentDateComparator());

            double factorAge;
            double factorPos;
            double factorRate;
            double newRate;
            double oldRate;

            int ageSize = orderAge.size();
            int maxPos = orderPosInDoc.get(orderPosInDoc.size() - 1).get(ContentDate.DATEPOS_IN_DOC);
            int counter = 0;

            ContentDate temp = orderAge.get(0);
            ContentDate actDate;

            for (int i = 0; i < ageSize; i++) {
                actDate = orderAge.get(i);
                if (dc.compare(temp, actDate) != 0) {
                    temp = orderAge.get(i);
                    counter++;
                }
                factorAge = ((ageSize - counter) * 1.0) / (ageSize * 1.0);
                factorPos = 1 - ((actDate.get(ContentDate.DATEPOS_IN_DOC) * 1.0) / (maxPos * 1.0));
                factorPos += 0.01;
                factorRate = factorAge * factorPos;
                oldRate = dates.get(actDate);
                newRate = oldRate + (1 - oldRate) * factorRate;
                result.put(actDate, Math.round(newRate * 10000) / 10000.0);
            }
        }
        normalizeRate(result);
        return result;
    }

    /**
     * If some rates are greater then one, use this method to normalize them.
     * 
     * @param <T>
     * @param dates
     */
    private <T> void normalizeRate(HashMap<T, Double> dates) {
        double highestRate = DateArrayHelper.getHighestRate(dates);
        if (highestRate > 1.0) {
            for (Entry<T, Double> e : dates.entrySet()) {
                dates.put(e.getKey(), Math.round((e.getValue() / highestRate) * 10000) / 10000.0);
            }
        }

    }

    /**
     * Content-dates get a new rate in dependency of structure dates.
     * 
     * @param <C>
     * @param contentDates
     * @param structDates
     * @return
     */
    private <C> HashMap<C, Double> deployStructureDates(HashMap<C, Double> contentDates,
            HashMap<StructureDate, Double> structDates) {
        DateComparator dc = new DateComparator();
        ArrayList<StructureDate> structureDates = dc.orderDates(structDates, true);
        HashMap<C, Double> result = contentDates;
        HashMap<C, Double> temp = contentDates;
        HashMap<C, Double> tempContentDates = new HashMap<C, Double>();
        HashMap<C, Double> tempResult = new HashMap<C, Double>();
        for (int i = 0; i < structureDates.size(); i++) {
            tempContentDates = DateArrayHelper.getSameDatesMap((ExtractedDate) structureDates.get(i), temp,
                    DateComparator.STOP_MINUTE);
            if (tempContentDates.size() == 0) {
                tempContentDates = DateArrayHelper.getSameDatesMap((ExtractedDate) structureDates.get(i), temp,
                        DateComparator.STOP_HOUR);
            }
            if (tempContentDates.size() == 0) {
                tempContentDates = DateArrayHelper.getSameDatesMap((ExtractedDate) structureDates.get(i), temp,
                        DateComparator.STOP_DAY);
            }
            for (Entry<C, Double> cDate : tempContentDates.entrySet()) {
                String cDateTag = ((ContentDate) cDate.getKey()).getTagNode();
                String eTag = ((StructureDate) structureDates.get(i)).getTagNode();
                if (cDateTag.equalsIgnoreCase(eTag)) {
                    double structValue = structDates.get(structureDates.get(i));
                    double newRate = ((1 - cDate.getValue()) * structValue) + cDate.getValue();
                    tempResult.put(cDate.getKey(), Math.round(newRate * 10000) / 10000.0);
                    temp.remove(cDate.getKey());
                }
            }

        }
        result.putAll(tempResult);
        return result;
    }

    /**
     * Set url.
     * 
     * @param url
     */
    public void setUrl(String url) {
        this.url = url;
    }

    /**
     * Getter for url.
     * 
     * @return Url as a String.
     */
    public String getUrl() {
        return url;
    }

    /**
     * Activate or disable the possibility of using reference-technique.
     * 
     * @param referneceLookUp
     */
    public void setReferneceLookUp(boolean referneceLookUp) {
        this.referneceLookUp = referneceLookUp;
    }
}
