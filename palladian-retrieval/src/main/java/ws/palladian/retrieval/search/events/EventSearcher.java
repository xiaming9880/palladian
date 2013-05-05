package ws.palladian.retrieval.search.events;

import java.util.Date;
import java.util.List;

import ws.palladian.retrieval.search.SearcherException;

public abstract class EventSearcher {

    public abstract List<Event> search(String keywords, String location, Integer radius, Date startDate, Date endDate,
            EventType eventType) throws SearcherException;

    public abstract String getName();

    /**
     * <p>
     * The APIs don't always work correctly for time spans so we have to filter events outside the specified time frame
     * </p>
     * 
     * @param startDate The start date if specified.
     * @param endDate The end date if specified.
     * @param event The event in question.
     * @return True if the event is within the specified time frame or there is no time frame specified. False
     *         otherwise.
     */
    protected boolean isWithinTimeFrame(Date startDate, Date endDate, Event event) {
        boolean withinTimeFrame = true;

        if (startDate != null && event.getEndDate() != null) {
            if (event.getEndDate().getTime() < startDate.getTime()) {
                withinTimeFrame = false;
            } else if (endDate != null && event.getStartDate().getTime() > endDate.getTime()) {
                withinTimeFrame = false;
            }
        }

        return withinTimeFrame;
    }

}
