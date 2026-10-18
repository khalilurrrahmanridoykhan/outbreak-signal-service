package io.github.khalilurrrahmanridoykhan.outbreaksignal.datasource;

import java.util.List;

/** The shape of {@code GET /api/organisationUnits.json}, trimmed to the fields this client asks for. */
record OrgUnitsResponse(List<Entry> organisationUnits) {

    record Entry(String id, String name, int level, ParentRef parent) {
    }

    record ParentRef(String id) {
    }
}
