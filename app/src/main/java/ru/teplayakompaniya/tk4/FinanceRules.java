package ru.teplayakompaniya.tk4;

import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;

/** Period filtering of an explicitly incomplete API list; never a source of KPI totals. */
final class FinanceRules {
    private FinanceRules() {}
    static boolean inPeriod(LocalDate date, String period, LocalDate today) {
        if (date == null || today == null) return false;
        LocalDate start, end;
        switch (period) {
            case "Сегодня": start=today; end=start.plusDays(1); break;
            case "Неделя": start=today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); end=start.plusWeeks(1); break;
            case "Месяц": start=today.withDayOfMonth(1); end=start.plusMonths(1); break;
            case "Квартал": start=LocalDate.of(today.getYear(),((today.getMonthValue()-1)/3)*3+1,1); end=start.plusMonths(3); break;
            case "Год": start=today.withDayOfYear(1); end=start.plusYears(1); break;
            default: return false;
        }
        return !date.isBefore(start) && date.isBefore(end);
    }
}
