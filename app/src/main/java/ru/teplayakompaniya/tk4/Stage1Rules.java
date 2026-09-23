package ru.teplayakompaniya.tk4;

import java.util.Locale;

/** Pure rules shared by the native screens; never derive identity from display names. */
public final class Stage1Rules {
    private Stage1Rules() {}
    public static String role(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
    public static boolean isAdmin(String value) {
        return "OWNER".equals(role(value)) || "ADMIN".equals(role(value));
    }
    public static boolean canSeeCompanyFinance(String value, boolean financeGranted) {
        return isAdmin(value) || ("PARTNER".equals(role(value)) && financeGranted);
    }
    public static boolean needsScopedData(String value) {
        return !isAdmin(value) && !"PARTNER".equals(role(value));
    }
    public static String objectGroup(String status) {
        if ("В работе".equals(status) || "Приостановлен".equals(status)) return "В работе";
        if ("Завершён".equals(status) || "Работы завершены".equals(status) || "Закрыт 100%".equals(status)) return "Завершены";
        if ("Подтверждён".equals(status) || "Подтверждён клиентом".equals(status) || "Готов к монтажу".equals(status)) return "Запланированы и подтверждены";
        return "Ожидают даты";
    }
    public static boolean canCreateTask(String status, String startDate) {
        return ("Запланированы и подтверждены".equals(objectGroup(status)) || "В работе".equals(objectGroup(status)))
                && startDate != null && !startDate.trim().isEmpty();
    }
    public static boolean isClosed(String status) {
        return "Завершены".equals(objectGroup(status));
    }
    public static boolean snapshotMatches(String requestUser, String currentUser, String requestPeriod, String currentPeriod) {
        return requestUser != null && requestUser.equals(currentUser) && requestPeriod != null && requestPeriod.equals(currentPeriod);
    }
}
