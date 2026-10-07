package ru.teplayakompaniya.tk4;

import org.junit.Test;
import static org.junit.Assert.*;

public class Stage1RulesTest {
    @Test public void permissionsFailClosed() {
        for (String role : new String[]{"", "MANAGER", "INSTALLER", "ENGINEER", "UNKNOWN"}) {
            assertFalse(Stage1Rules.isAdmin(role));
            assertFalse(Stage1Rules.canSeeCompanyFinance(role, true));
            assertTrue(Stage1Rules.needsScopedData(role));
        }
        assertTrue(Stage1Rules.isAdmin("OWNER"));
        assertTrue(Stage1Rules.isAdmin("admin"));
        assertTrue(Stage1Rules.canSeeCompanyFinance("OWNER", false));
        assertFalse(Stage1Rules.canSeeCompanyFinance("ADMIN", false));
        assertTrue(Stage1Rules.canSeeCompanyFinance("ADMIN", true));
        assertFalse(Stage1Rules.canSeeCompanyFinance("PARTNER", false));
        assertTrue(Stage1Rules.canSeeCompanyFinance("PARTNER", true));
        assertTrue(Stage1Rules.canCreateObject("MANAGER"));
        assertTrue(Stage1Rules.canCreateSurvey("ENGINEER"));
        assertFalse(Stage1Rules.canCreateObject("INSTALLER"));
        assertTrue(Stage1Rules.canUploadMedia("INSTALLER"));
        assertFalse(Stage1Rules.canEditTech("MANAGER"));
    }
    @Test public void taskNeedsConfirmedObjectAndDate() {
        assertFalse(Stage1Rules.canCreateTask("Запланирован", "2026-09-18"));
        assertFalse(Stage1Rules.canCreateTask("Подтверждён", ""));
        assertFalse(Stage1Rules.canCreateTask("Подтверждён", null));
        assertTrue(Stage1Rules.canCreateTask("Подтверждён", "2026-09-18"));
        assertTrue(Stage1Rules.isClosed("Закрыт 100%"));
    }
    @Test public void stalePeriodAndDifferentAccountNeverMatch() {
        assertFalse(Stage1Rules.snapshotMatches("A", "B", "Месяц", "Месяц"));
        assertFalse(Stage1Rules.snapshotMatches("A", "A", "Месяц", "Год"));
        assertTrue(Stage1Rules.snapshotMatches("A", "A", "Месяц", "Месяц"));
    }
}
