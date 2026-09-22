package ru.teplayakompaniya.tk4;

import org.junit.Test;
import static org.junit.Assert.*;

public class ReferenceRulesTest {
    @Test public void greetingChangesAtLocalBoundaries(){
        assertEquals("Добрый вечер",ReferenceRules.greeting(4));
        assertEquals("Доброе утро",ReferenceRules.greeting(5));
        assertEquals("Доброе утро",ReferenceRules.greeting(11));
        assertEquals("Добрый день",ReferenceRules.greeting(12));
        assertEquals("Добрый день",ReferenceRules.greeting(17));
        assertEquals("Добрый вечер",ReferenceRules.greeting(18));
        assertEquals("Добрый вечер",ReferenceRules.greeting(0));
    }
    @Test public void preparationIsNotPhysicallyActive(){
        assertTrue(ReferenceRules.isActiveWork("В работе"));
        assertFalse(ReferenceRules.isActiveWork("Запланирован"));
        assertFalse(ReferenceRules.isActiveWork("Подтверждён"));
        assertFalse(ReferenceRules.isActiveWork("Завершён"));
    }
}
