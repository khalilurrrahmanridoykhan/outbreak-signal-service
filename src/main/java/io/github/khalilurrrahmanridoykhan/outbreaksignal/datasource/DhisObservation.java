package io.github.khalilurrrahmanridoykhan.outbreaksignal.datasource;

import io.github.khalilurrrahmanridoykhan.outbreaksignal.common.EpiWeek;
import java.math.BigDecimal;

/** One (data element, org unit, week) value, as returned by DHIS2 analytics. */
public record DhisObservation(String dataElementUid, String orgUnitUid, EpiWeek week, BigDecimal value) {
}
