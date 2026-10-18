package io.github.khalilurrrahmanridoykhan.outbreaksignal.common;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An ISO-8601 week, identified by the Monday that starts it. This is the one
 * place in the codebase that knows how DHIS2 spells a week ({@code "2026W9"},
 * not zero-padded, not the same thing as a calendar year) and how that maps
 * onto the {@code week_start} column, which is always that Monday.
 *
 * <p>Confirmed against the public DHIS2 demo (see {@code docs/dhis2-spike.md}):
 * period strings carry the week number with no leading zero, and DHIS2's
 * {@code LAST_52_WEEKS} relative period is the longest one it defines, so a
 * longer window has to be built as an explicit list of these.
 */
public final class EpiWeek implements Comparable<EpiWeek> {

    private static final Pattern DHIS2_PERIOD = Pattern.compile("^(\\d{4})W(\\d{1,2})$");

    private final int isoYear;
    private final int isoWeek;

    private EpiWeek(int isoYear, int isoWeek) {
        this.isoYear = isoYear;
        this.isoWeek = isoWeek;
    }

    /** The epi week that contains {@code date}, whatever day of the week it is. */
    public static EpiWeek containing(LocalDate date) {
        return new EpiWeek(
                date.get(WeekFields.ISO.weekBasedYear()),
                date.get(WeekFields.ISO.weekOfWeekBasedYear()));
    }

    /**
     * Parses a DHIS2 weekly period string such as {@code "2026W9"} or
     * {@code "2026W42"}. Rejects anything else, including a zero-padded week
     * number, so a format DHIS2 does not actually produce is never silently
     * accepted.
     */
    public static EpiWeek parseDhis2Period(String period) {
        Matcher matcher = DHIS2_PERIOD.matcher(period);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Not a DHIS2 weekly period: " + period);
        }
        String weekDigits = matcher.group(2);
        if (weekDigits.length() > 1 && weekDigits.charAt(0) == '0') {
            throw new IllegalArgumentException("DHIS2 weekly periods are not zero-padded: " + period);
        }
        return new EpiWeek(Integer.parseInt(matcher.group(1)), Integer.parseInt(weekDigits));
    }

    /** The Monday that starts this week; what the {@code observation.week_start} column stores. */
    public LocalDate weekStart() {
        LocalDate date = LocalDate.ofYearDay(isoYear, 1)
                .with(IsoFields.WEEK_BASED_YEAR, isoYear)
                .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, isoWeek)
                .with(DayOfWeek.MONDAY);
        if (date.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new AssertionError("computed a non-Monday week start for " + this);
        }
        return date;
    }

    /** The format DHIS2 itself uses: no leading zero on the week number. */
    public String toDhis2Period() {
        return isoYear + "W" + isoWeek;
    }

    public EpiWeek plusWeeks(long weeks) {
        return containing(weekStart().plusWeeks(weeks));
    }

    public EpiWeek minusWeeks(long weeks) {
        return plusWeeks(-weeks);
    }

    /**
     * The {@code count} weeks ending with (and including) this one, oldest
     * first — the shape a DHIS2 {@code pe} dimension needs for a window
     * longer than the 52 weeks its own relative periods cover.
     */
    public List<EpiWeek> lastWeeksEndingHere(int count) {
        if (count < 1) {
            throw new IllegalArgumentException("count must be at least 1: " + count);
        }
        List<EpiWeek> weeks = new ArrayList<>(count);
        for (int i = count - 1; i >= 0; i--) {
            weeks.add(minusWeeks(i));
        }
        return weeks;
    }

    @Override
    public int compareTo(EpiWeek other) {
        return weekStart().compareTo(other.weekStart());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EpiWeek other)) {
            return false;
        }
        return isoYear == other.isoYear && isoWeek == other.isoWeek;
    }

    @Override
    public int hashCode() {
        return Objects.hash(isoYear, isoWeek);
    }

    @Override
    public String toString() {
        return toDhis2Period();
    }
}
