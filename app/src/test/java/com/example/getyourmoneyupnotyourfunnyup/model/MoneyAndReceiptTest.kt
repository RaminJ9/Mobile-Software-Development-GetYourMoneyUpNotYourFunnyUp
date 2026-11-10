package com.example.getyourmoneyupnotyourfunnyup.model

import com.example.getyourmoneyupnotyourfunnyup.data.demo.DemoLedger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyAndReceiptTest {

    @Test
    fun formatsDanishStyle() {
        assertEquals("1.240,50", MoneyFormat.kr(124_050))
        assertEquals("0,05", MoneyFormat.kr(5))
        assertEquals("1.234.567,00", MoneyFormat.kr(123_456_700))
        assertEquals("−10,00", MoneyFormat.signed(-1_000))
        assertEquals("+145,00", MoneyFormat.signed(14_500, plusSign = true))
        assertEquals("1.240", MoneyFormat.wholeKr(124_000))
    }

    @Test
    fun parsesWhatPeopleType() {
        assertEquals(5_995L, MoneyFormat.parse("59,95"))
        assertEquals(124_050L, MoneyFormat.parse("1.240,50"))
        assertEquals(-1_000L, MoneyFormat.parse("-10,00"))
        assertEquals(-1_000L, MoneyFormat.parse("−10"))
        assertEquals(1_250L, MoneyFormat.parse("12.5"))
        assertEquals(124_000L, MoneyFormat.parse("1.240"))
        assertNull(MoneyFormat.parse(""))
        assertNull(MoneyFormat.parse("abc"))
    }

    private val members = listOf(
        Person("me", "You", "", paletteIndex = -1),
        Person("mads", "Mads", "", dietRules = setOf(DietRule.NO_ALCOHOL)),
        Person("ida", "Ida", ""),
        Person("jonas", "Jonas", ""),
    )

    @Test
    fun dietRulesTakePeopleOffItemsByDefault() {
        val beer = DemoLedger.receiptItems.first { it.isAlcohol }
        assertFalse("mads" in ReceiptSplitter.defaultSharers(beer, members))
        val pasta = DemoLedger.receiptItems.first { it.name.startsWith("Fusilli") }
        assertTrue("mads" in ReceiptSplitter.defaultSharers(pasta, members))
    }

    @Test
    fun sharesAlwaysAddUpToTheReceiptTotal() {
        val items = DemoLedger.receiptItems
        val shares = ReceiptSplitter.shares(items, members, emptyMap(), emptySet(), payerId = "me")
        assertEquals(items.sumOf { it.priceCents }, shares.values.sum())
        // Mads skips the beer and wine, so pays the least.
        assertEquals("mads", shares.minByOrNull { it.value }?.key)
    }

    @Test
    fun leavingSomeoneOutMovesTheirShareToTheOthers() {
        val items = DemoLedger.receiptItems
        val shares = ReceiptSplitter.shares(items, members, emptyMap(), setOf("jonas"), payerId = "me")
        assertFalse("jonas" in shares)
        assertEquals(items.sumOf { it.priceCents }, shares.values.sum())
    }

    @Test
    fun perItemOverrideAddsSomeoneBack() {
        val items = DemoLedger.receiptItems
        val wine = items.first { it.name.startsWith("Merlot") }
        val withMads = ReceiptSplitter.shares(items, members, mapOf(wine.id to listOf("me", "mads")), emptySet(), "me")
        val without = ReceiptSplitter.shares(items, members, emptyMap(), emptySet(), "me")
        assertTrue(withMads.getValue("mads") > without.getValue("mads"))
    }
}
