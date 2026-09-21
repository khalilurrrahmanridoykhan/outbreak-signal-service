package io.github.khalilurrrahmanridoykhan.outbreaksignal.observation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.khalilurrrahmanridoykhan.outbreaksignal.TestcontainersConfiguration;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class PersistenceTest {

    // 2026-03-16 is a Monday (ISO week 12); 2026-03-17 is a Tuesday.
    private static final LocalDate MONDAY = LocalDate.of(2026, 3, 16);
    private static final LocalDate TUESDAY = LocalDate.of(2026, 3, 17);

    @Autowired
    private OrgUnitRepository orgUnits;
    @Autowired
    private SeriesRepository seriesRepository;
    @Autowired
    private ObservationRepository observations;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void flywayAppliedTheBaselineMigration() {
        Integer applied = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success", Integer.class);
        assertThat(applied).isEqualTo(1);
    }

    @Test
    void savesAndReadsBackASeriesWithItsObservations() {
        Series series = newSeries();
        observations.saveAndFlush(new Observation(series, MONDAY, new BigDecimal("12")));
        observations.saveAndFlush(new Observation(series, MONDAY.plusWeeks(1), new BigDecimal("15")));

        var found = observations.findBySeriesIdAndWeekStartGreaterThanEqualOrderByWeekStart(
                series.getId(), MONDAY.plusWeeks(1));

        assertThat(found).hasSize(1);
        assertThat(found.getFirst().getValue()).isEqualByComparingTo("15");
    }

    @Test
    void rejectsASecondObservationForTheSameSeriesAndWeek() {
        Series series = newSeries();
        observations.saveAndFlush(new Observation(series, MONDAY, new BigDecimal("12")));

        assertThatThrownBy(() -> observations.saveAndFlush(new Observation(series, MONDAY, new BigDecimal("13"))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsAWeekStartThatIsNotAMonday() {
        Series series = newSeries();

        assertThatThrownBy(() -> observations.saveAndFlush(new Observation(series, TUESDAY, BigDecimal.ONE)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsANegativeValue() {
        Series series = newSeries();

        assertThatThrownBy(() -> observations.saveAndFlush(new Observation(series, MONDAY, new BigDecimal("-1"))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsADuplicateDataElementForTheSameOrgUnit() {
        Series first = newSeries();

        assertThatThrownBy(() -> seriesRepository.saveAndFlush(
                new Series(first.getOrgUnit(), first.getDhis2DataElementUid(), "Duplicate")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Series newSeries() {
        OrgUnit district = orgUnits.saveAndFlush(new OrgUnit("O6uvpzGd5pu", "Bo", 2, null));
        return seriesRepository.saveAndFlush(new Series(district, "fbfJHSPpUQD", "Dengue cases"));
    }
}
