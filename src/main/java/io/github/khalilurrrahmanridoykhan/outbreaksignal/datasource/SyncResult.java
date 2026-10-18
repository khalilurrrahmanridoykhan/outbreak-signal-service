package io.github.khalilurrrahmanridoykhan.outbreaksignal.datasource;

import java.util.List;

/** What one run of {@link Dhis2SyncService#sync()} did, returned to the manual trigger and logged by the scheduler. */
public record SyncResult(int orgUnitsCreated, int weeksRequested, List<DataElementResult> dataElements) {

    /** The outcome for one data element. A failure here does not stop the others; see {@code Dhis2SyncService}. */
    public record DataElementResult(String uid, String name, boolean succeeded, int observationsUpserted, String errorMessage) {

        public static DataElementResult success(String uid, String name, int observationsUpserted) {
            return new DataElementResult(uid, name, true, observationsUpserted, null);
        }

        public static DataElementResult failed(String uid, String name, String errorMessage) {
            return new DataElementResult(uid, name, false, 0, errorMessage);
        }
    }
}
