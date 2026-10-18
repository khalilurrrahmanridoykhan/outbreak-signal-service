package io.github.khalilurrrahmanridoykhan.outbreaksignal.datasource;

import io.github.khalilurrrahmanridoykhan.outbreaksignal.common.EpiWeek;
import java.util.List;

/**
 * A port onto a DHIS2 instance: only the two read operations this service
 * needs, so the detail of talking to DHIS2 (HTTP, auth, analytics query
 * syntax) stays behind {@link DhisWebClient} and out of the sync logic.
 */
public interface DhisClient {

    /** Every organisation unit at the configured level, across the whole instance. */
    List<DhisOrgUnit> fetchOrgUnits(int level);

    /**
     * The value of {@code dataElementUid} for every org unit at {@code level},
     * for exactly the given weeks. Weeks with no reported value for a given
     * org unit are simply absent from the result — that is normal (see the
     * spike notes on completeness) and is not an error.
     */
    List<DhisObservation> fetchWeeklyAnalytics(String dataElementUid, int level, List<EpiWeek> weeks);
}
