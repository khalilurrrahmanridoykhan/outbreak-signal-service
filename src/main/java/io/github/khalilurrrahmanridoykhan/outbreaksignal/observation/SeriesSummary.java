package io.github.khalilurrrahmanridoykhan.outbreaksignal.observation;

/** What {@code GET /series} returns — a {@link Series}, flattened for JSON. */
public record SeriesSummary(Long id, Long orgUnitId, String orgUnitName, String dhis2DataElementUid, String name, Frequency frequency) {

    static SeriesSummary from(Series series) {
        OrgUnit orgUnit = series.getOrgUnit();
        return new SeriesSummary(series.getId(), orgUnit.getId(), orgUnit.getName(),
                series.getDhis2DataElementUid(), series.getName(), series.getFrequency());
    }
}
