package io.github.khalilurrrahmanridoykhan.outbreaksignal.datasource;

/** One organisation unit as DHIS2 describes it, at the configured hierarchy level. */
public record DhisOrgUnit(String uid, String name, int level, String parentUid) {
}
