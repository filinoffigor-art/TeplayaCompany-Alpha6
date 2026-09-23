package ru.teplayakompaniya.tk4;

import java.time.LocalDate;
import org.junit.Test;
import static org.junit.Assert.*;

public class FinanceRulesTest {
    private final LocalDate today=LocalDate.of(2026,9,22);
    @Test public void weekStartsOnMondayAndExcludesNextWeek(){
        assertTrue(FinanceRules.inPeriod(LocalDate.of(2026,9,21),"Неделя",today));
        assertFalse(FinanceRules.inPeriod(LocalDate.of(2026,9,20),"Неделя",today));
        assertFalse(FinanceRules.inPeriod(LocalDate.of(2026,9,28),"Неделя",today));
    }
    @Test public void quarterIsCalendarQuarter(){
        assertTrue(FinanceRules.inPeriod(LocalDate.of(2026,7,1),"Квартал",today));
        assertFalse(FinanceRules.inPeriod(LocalDate.of(2026,6,30),"Квартал",today));
        assertFalse(FinanceRules.inPeriod(LocalDate.of(2026,10,1),"Квартал",today));
    }
    @Test public void incompleteDateIsNotTodaysOperation(){assertFalse(FinanceRules.inPeriod(null,"Сегодня",today));}
    @Test public void yearBoundary(){
        assertTrue(FinanceRules.inPeriod(LocalDate.of(2026,12,31),"Год",today));
        assertFalse(FinanceRules.inPeriod(LocalDate.of(2027,1,1),"Год",today));
    }
    @Test public void monthAndDayAreNotInterchangeable(){
        assertTrue(FinanceRules.inPeriod(today.minusDays(1),"Месяц",today));
        assertFalse(FinanceRules.inPeriod(today.minusDays(1),"Сегодня",today));
    }
}
