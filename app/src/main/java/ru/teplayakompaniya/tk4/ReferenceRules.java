package ru.teplayakompaniya.tk4;

/** Time-dependent presentation kept deterministic and independently testable. */
public final class ReferenceRules {
    private ReferenceRules() {}
    public static String greeting(int hour) {
        if(hour<0||hour>23)throw new IllegalArgumentException("hour");
        return hour>=5&&hour<12?"Доброе утро":hour>=12&&hour<18?"Добрый день":"Добрый вечер";
    }
    public static boolean isActiveWork(String status){return "В работе".equals(status)||"Монтаж".equals(status);}
}
