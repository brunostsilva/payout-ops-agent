package io.github.brunostsilva.payoutops.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Objects;

/** Inclusive date range over which a payout is settled. */
public record PayoutPeriod(LocalDate start, LocalDate end) {

    public PayoutPeriod {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("Period end " + end + " is before start " + start);
        }
    }

    /** The Monday-to-Sunday week containing {@code date}. */
    public static PayoutPeriod weekOf(LocalDate date) {
        LocalDate monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return new PayoutPeriod(monday, monday.plusDays(6));
    }

    /** The period of the same length immediately before this one. */
    public PayoutPeriod previous() {
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        return new PayoutPeriod(start.minusDays(days), end.minusDays(days));
    }

    public boolean contains(LocalDate date) {
        return !date.isBefore(start) && !date.isAfter(end);
    }
}
