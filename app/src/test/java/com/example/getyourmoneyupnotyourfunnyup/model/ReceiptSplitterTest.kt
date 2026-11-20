package com.example.getyourmoneyupnotyourfunnyup.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptSplitterTest {

    private val members = listOf(
        ScanPerson("me", "Victor", -1),
        ScanPerson("mads", "Mads", 1, excludeAlcohol = true),
        ScanPerson("ida", "Ida", 2, excludeMeat = true),
        ScanPerson("jonas", "Jonas", 4),
    )

    private val beer = ReceiptItem(1, "Tuborg grøn 6-pak", "59,95", isAlcohol = true)
    private val mince = ReceiptItem(2, "Hakket oksekød", "45,00", isMeat = true)
    private val bread = ReceiptItem(3, "Rugbrød", "22,00")
    private val wine = ReceiptItem(4, "Merlot rødvin", "79,00", isAlcohol = true)
    private val pant = ReceiptItem(5, "Pant A", "6,00", ReceiptItemKind.DEPOSIT)
    private val receipt = listOf(beer, mince, bread, wine, pant)

    @Test
    fun dietRules_takePeopleOffMatchingItemsByDefault() {
        assertFalse("mads" in ReceiptSplitter.defaultSharers(beer, members))
        assertFalse("ida" in ReceiptSplitter.defaultSharers(mince, members))
        assertEquals(listOf("me", "mads", "ida", "jonas"), ReceiptSplitter.defaultSharers(bread, members))
    }

    @Test
    fun shares_alwaysAddUpToTheReceiptTotal() {
        val shares = ReceiptSplitter.shares(receipt, members, emptyMap(), emptySet(), payerId = "me")
        assertEquals(receipt.sumOf { it.priceCents }, shares.values.sum())
        assertTrue(shares.values.all { it >= 0 })
        // Mads skips the beer and wine, so he pays the least.
        assertEquals("mads", shares.minByOrNull { it.value }?.key)
    }

    @Test
    fun leavingSomeoneOut_movesTheirShareToTheOthers() {
        val shares = ReceiptSplitter.shares(receipt, members, emptyMap(), setOf("jonas"), payerId = "me")
        assertFalse("jonas" in shares)
        assertEquals(receipt.sumOf { it.priceCents }, shares.values.sum())
    }

    @Test
    fun aPerItemChoice_addsSomeoneBack() {
        val withMads = ReceiptSplitter.shares(receipt, members, mapOf(wine.id to listOf("me", "mads")), emptySet(), "me")
        val without = ReceiptSplitter.shares(receipt, members, emptyMap(), emptySet(), "me")
        assertTrue(withMads.getValue("mads") > without.getValue("mads"))
    }

    @Test
    fun aTypedNegativeLine_isSharedLikeADiscount_soNoShareGoesBelowZero() {
        // Jonas had nothing but is ticked on a "-50" typed on a normal line. Split per person, that would
        // give him -50 kr; it is a discount for the whole receipt instead.
        val items = listOf(ReceiptItem(1, "Pizza", "100,00"), ReceiptItem(2, "Bread", "200,00"), ReceiptItem(3, "Coupon", "-50,00"))
        val who = mapOf(1 to listOf("ida"), 2 to listOf("me", "mads"), 3 to listOf("jonas"))
        val shares = ReceiptSplitter.shares(items, members, who, emptySet(), "me")
        assertTrue(items[2].sharedByShare)
        assertEquals(25_000L, shares.values.sum())
        assertTrue("no negative share: $shares", shares.values.all { it >= 0 })
        assertEquals(0L, shares.getValue("jonas"))
    }

    @Test
    fun splittingEqually_givesLeftoverOereToTheFirstPeople() {
        val parts = ReceiptSplitter.splitEqually(29_900, listOf("a", "b", "c", "d", "e", "f", "g"))
        assertEquals(29_900L, parts.values.sum())
        assertEquals(listOf(4_272L, 4_272L, 4_272L, 4_271L, 4_271L, 4_271L, 4_271L), parts.values.toList())
    }
}
