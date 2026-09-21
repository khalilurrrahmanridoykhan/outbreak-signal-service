package io.github.khalilurrrahmanridoykhan.outbreaksignal.observation;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeriesRepository extends JpaRepository<Series, Long> {

    Optional<Series> findByOrgUnitIdAndDhis2DataElementUid(Long orgUnitId, String dhis2DataElementUid);
}
