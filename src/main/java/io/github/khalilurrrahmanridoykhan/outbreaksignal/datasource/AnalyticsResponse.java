package io.github.khalilurrrahmanridoykhan.outbreaksignal.datasource;

import java.util.List;

/**
 * The shape of {@code GET /api/analytics.json}: a list of column headers and
 * then each row as a plain list of strings in that column order. Reading
 * {@code headers} to find each column's index, rather than assuming
 * {@code [dx, ou, pe, value]}, is deliberate — the spike's own query always
 * came back in that order, but nothing about the DHIS2 API promises it will.
 */
record AnalyticsResponse(List<Header> headers, List<List<String>> rows) {

    record Header(String name) {
    }

    int columnIndex(String name) {
        for (int i = 0; i < headers.size(); i++) {
            if (headers.get(i).name().equals(name)) {
                return i;
            }
        }
        throw new DhisRequestException(
                "DHIS2 analytics response has no \"" + name + "\" column", null);
    }
}
