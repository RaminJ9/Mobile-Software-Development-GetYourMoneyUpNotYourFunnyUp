package com.example.getyourmoneyupnotyourfunnyup.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DanishAmountTest {

    @Test
    fun parsesWhatPeopleTypeAndWhatReceiptsPrint() {
        assertEquals(5_995L, DanishAmount.parse("59,95"))
        assertEquals(124_050L, DanishAmount.parse("1.240,50"))
        assertEquals(-1_000L, DanishAmount.parse("-10,00"))
        assertEquals(-1_000L, DanishAmount.parse("−10"))
        assertEquals(1_250L, DanishAmount.parse("12.5"))
        assertEquals(124_000L, DanishAmount.parse("1.240"))
        assertEquals(5_995L, DanishAmount.parse("59,95 kr"))
        assertNull(DanishAmount.parse(""))
        assertNull(DanishAmount.parse("-"))
        assertNull(DanishAmount.parse("abc"))
    }

    @Test
    fun fillsPriceFieldsInDanishNotation() {
        assertEquals("59,95", DanishAmount.forInput(5_995))
        assertEquals("1.240,50", DanishAmount.forInput(124_050))
        assertEquals("1.234.567,00", DanishAmount.forInput(123_456_700))
        assertEquals("-10,00", DanishAmount.forInput(-1_000))
        assertEquals("0,05", DanishAmount.forInput(5))
        // What is filled in reads back to the same amount.
        listOf(0L, 5L, 5_995L, -1_000L, 124_050L).forEach { assertEquals(it, DanishAmount.parse(DanishAmount.forInput(it))) }
    }
}
