package io.github.khalilurrrahmanridoykhan.outbreaksignal.observation;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrgUnitRepository extends JpaRepository<OrgUnit, Long> {

    Optional<OrgUnit> findByDhis2Uid(String dhis2Uid);
}
