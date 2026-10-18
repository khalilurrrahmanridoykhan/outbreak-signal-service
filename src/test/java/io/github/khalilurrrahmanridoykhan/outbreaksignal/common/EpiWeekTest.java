package io.github.khalilurrrahmanridoykhan.outbreaksignal.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Every date here was computed independently with Python's {@code isocalendar}
 * (see the spike notes) and checked by hand against the ISO-8601 definition,
 * not derived from {@link EpiWeek} itself.
 */
class EpiWeekTest {

    @ParameterizedTest
    @CsvSource({
            // DHIS2 period,    expected Monday
            "2026W9,  2026-02-23",
            "2025W1,  2024-12-30", // ISO week 1 of 2025 starts in December 2024.
            "2026W53, 2026-12-28", // 2026 is a 53-week ISO year.
            "2027W1,  2027-01-04", // The week right after 2026's 53rd, not a reset to January 1.
            "2025W52, 2025-12-22",
    })
    void parsesDhis2PeriodsToTheCorrectMonday(String period, LocalDate expectedMonday) {
        EpiWeek week = EpiWeek.parseDhis2Period(period);

        assertThat(week.weekStart()).isEqualTo(expectedMonday);
        assertThat(week.weekStart().getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
    }

    @Test
    void formatsBackToDhis2sOwnNonZeroPaddedSpelling() {
        assertThat(EpiWeek.parseDhis2Period("2026W9").toDhis2Period()).isEqualTo("2026W9");
        assertThat(EpiWeek.parseDhis2Period("2026W42").toDhis2Period()).isEqualTo("2026W42");
    }

    @Test
    void rejectsAZeroPaddedWeekNumber() {
        // DHIS2 itself never sends "2026W09"; accepting it would hide a bug
        // in whatever produced the string instead of surfacing it.
        assertThatThrownBy(() -> EpiWeek.parseDhis2Period("2026W09"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsSomethingThatIsNotAWeeklyPeriodAtAll() {
        assertThatThrownBy(() -> EpiWeek.parseDhis2Period("2026")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EpiWeek.parseDhis2Period("2026Q1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EpiWeek.parseDhis2Period("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void containingFindsTheWeekForAnyDayOfIt() {
        EpiWeek fromMonday = EpiWeek.containing(LocalDate.of(2026, 2, 23));
        EpiWeek fromSunday = EpiWeek.containing(LocalDate.of(2026, 3, 1));

        assertThat(fromMonday.toDhis2Period()).isEqualTo("2026W9");
        assertThat(fromSunday.toDhis2Period()).isEqualTo("2026W9");
    }

    @Test
    void stepsAcrossThe53WeekYearBoundaryWithoutSkippingOrRepeating() {
        EpiWeek week53 = EpiWeek.parseDhis2Period("2026W53");

        assertThat(week53.plusWeeks(1)).isEqualTo(EpiWeek.parseDhis2Period("2027W1"));
        assertThat(EpiWeek.parseDhis2Period("2027W1").minusWeeks(1)).isEqualTo(week53);
    }

    @Test
    void lastWeeksEndingHereIsContiguousOldestFirstAndIncludesTheEndpoint() {
        EpiWeek end = EpiWeek.parseDhis2Period("2026W9");

        List<EpiWeek> weeks = end.lastWeeksEndingHere(5);

        assertThat(weeks).hasSize(5);
        assertThat(weeks.getLast()).isEqualTo(end);
        assertThat(weeks.getFirst()).isEqualTo(end.minusWeeks(4));
        for (int i = 1; i < weeks.size(); i++) {
            assertThat(weeks.get(i).weekStart()).isEqualTo(weeks.get(i - 1).weekStart().plusWeeks(1));
        }
    }

    @Test
    void lastWeeksEndingHereOfOneIsJustThisWeek() {
        EpiWeek end = EpiWeek.parseDhis2Period("2026W9");

        assertThat(end.lastWeeksEndingHere(1)).containsExactly(end);
    }

    @Test
    void lastWeeksEndingHereRejectsANonPositiveCount() {
        EpiWeek end = EpiWeek.parseDhis2Period("2026W9");

        assertThatThrownBy(() -> end.lastWeeksEndingHere(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theHundredAndFourWeekWindowFromTheSpikeHasNoGapsOrDuplicates() {
        EpiWeek end = EpiWeek.containing(LocalDate.of(2026, 10, 18));

        List<EpiWeek> weeks = end.lastWeeksEndingHere(104);

        List<LocalDate> mondays = weeks.stream().map(EpiWeek::weekStart).collect(Collectors.toList());
        assertThat(mondays).doesNotHaveDuplicates().isSorted();
        assertThat(weeks).hasSize(104);
    }

    @Test
    void equalityAndOrderingFollowTheCalendarWeekNotTheRawFields() {
        assertThat(EpiWeek.parseDhis2Period("2026W9")).isEqualTo(EpiWeek.parseDhis2Period("2026W9"));
        assertThat(EpiWeek.parseDhis2Period("2025W52").compareTo(EpiWeek.parseDhis2Period("2026W1")))
                .isLessThan(0);
    }
}
