package com.grafito.finanzas.model

import org.junit.Assert.*
import org.junit.Test

class MoneyTest {
    @Test fun parsePeruvianCents() {
        assertEquals(150L, Money.parse("1.50"))
        assertEquals(150L, Money.parse("1,50"))
        assertEquals(1000L, Money.parse("10"))
        assertEquals(1L, Money.parse("0.01"))
    }
    @Test fun rejectBadAmounts() {
        assertNull(Money.parse("0"))
        assertNull(Money.parse("-5"))
        assertNull(Money.parse("1.234"))
        assertNull(Money.parse("hola"))
        assertNull(Money.parse(""))
    }
}
