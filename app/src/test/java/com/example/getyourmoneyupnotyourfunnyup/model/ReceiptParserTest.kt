package com.example.getyourmoneyupnotyourfunnyup.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Recognizer output for real-looking Danish receipts, written out by hand. Like the real recognizer,
 * the name and the price of a row usually come back as two separate lines at the same height.
 */
class ReceiptParserTest {

    private class Receipt {
        val lines = mutableListOf<OcrLine>()
        private var y = 0

        /** A row printed as name on the left and price on the right (two recognizer lines). */
        fun row(name: String, price: String? = null, confidence: Float = 1f, drift: Int = 0) {
            lines += OcrLine(name, 20, y, 400, y + 40, confidence)
            if (price != null) lines += OcrLine(price, 520, y + drift, 640, y + 40 + drift, confidence)
            y += 50
        }

        /** A row the recognizer returned as one line. */
        fun line(text: String, confidence: Float = 1f) {
            lines += OcrLine(text, 20, y, 640, y + 40, confidence)
            y += 50
        }
    }

    private fun receipt(build: Receipt.() -> Unit) = ReceiptParser.parse(Receipt().apply(build).lines)

    private fun ScannedReceipt.item(name: String) = items.single { it.name == name }

    @Test
    fun netto_splitColumnsQuantityPantRabatAndAMisreadPrice() {
        val scanned = receipt {
            row("NETTO")
            row("Sanderumvej 12, 5250 Odense SV")
            row("CVR 35954716")
            row("18.11.2026 17:42")
            row("TUBORG GRØN 6-PAK", "59,95")
            row("PANT A", "6,00")
            row("FUSILLI PASTA 500G", "12,00")
            row("HAKKET OKSEKØD 8-12%", "45,00")
            row("FLÅEDE TOMATER")
            row("2 x 7,00", "14,00")
            row("PARMIGIANO 150G", "3S,95") // "5" read as "S"
            row("MERLOT RØDVIN", "79,00")
            row("RUGBRØD", "22,00")
            row("RABAT OKSEKØD", "-10,00")
            row("TOTAL", "260,90")
            row("DANKORT", "260,90")
            row("MOMS UDGØR", "52,18")
        }

        assertEquals("Netto", scanned.merchant)
        assertEquals(26_090L, scanned.totalCents)
        assertEquals(
            listOf(
                "Tuborg grøn 6-pak", "Pant a", "Fusilli pasta 500g", "Hakket oksekød 8-12%", "Flåede tomater",
                "Parmigiano 150g", "Merlot rødvin", "Rugbrød", "Rabat oksekød",
            ),
            scanned.items.map { it.name },
        )
        assertTrue(scanned.item("Tuborg grøn 6-pak").isAlcohol)
        assertTrue(scanned.item("Merlot rødvin").isAlcohol)
        assertTrue(scanned.item("Hakket oksekød 8-12%").isMeat)
        assertEquals(ReceiptItemKind.DEPOSIT, scanned.item("Pant a").kind)
        assertEquals("14,00", scanned.item("Flåede tomater").priceText)
        assertEquals("35,95", scanned.item("Parmigiano 150g").priceText)
        assertTrue("A repaired price must be checked", scanned.item("Parmigiano 150g").lowConfidence)
        assertFalse(scanned.item("Rugbrød").lowConfidence)
        val rabat = scanned.item("Rabat oksekød")
        assertEquals(ReceiptItemKind.DISCOUNT, rabat.kind)
        assertEquals(-1_000L, rabat.priceCents)
        assertFalse("A discount is never meat", rabat.isMeat)
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8, 9), scanned.items.map { it.id })
    }

    @Test
    fun rema_oneLineRowsVatCodesDetailLinesAndThousands() {
        val scanned = receipt {
            line("REMA 1000")
            line("BANANER 12,00 A")
            line("3 STK X 4,00") // detail of the row above, which already has its price
            line("KYLLINGEBRYST 900G 69,95 A")
            line("GAVEKORT 1.000,00")
            line("ANTAL VARER 5")
            line("I ALT 1.081,95")
        }

        assertEquals("Rema 1000", scanned.merchant)
        assertEquals(108_195L, scanned.totalCents)
        assertEquals(listOf("Bananer", "Kyllingebryst 900g", "Gavekort"), scanned.items.map { it.name })
        assertEquals(listOf(1_200L, 6_995L, 100_000L), scanned.items.map { it.priceCents })
        assertTrue(scanned.item("Kyllingebryst 900g").isMeat)
        assertEquals("The items add up to the printed total", scanned.totalCents, scanned.items.sumOf { it.priceCents })
    }

    @Test
    fun fotex_trailingMinusUnsureLinesAndFalseFriends() {
        val scanned = receipt {
            row("føtex")
            row("Plantefars 400 g", "32,00")
            row("VEGANSK HAKKET", "29,00")
            row("SALT AND VINEGAR CHIPS", "18,00")
            row("ROYAL GREENLAND REJER", "45,00")
            row("SVINEKOTELETTER", "54,00", confidence = 0.55f)
            row("KUPONRABAT", "10,00-")
            row("AT BETALE", "168,00")
        }

        assertEquals("Føtex", scanned.merchant)
        assertEquals(16_800L, scanned.totalCents)
        assertFalse("Plant-based is not meat", scanned.item("Plantefars 400 g").isMeat)
        assertFalse("Plant-based is not meat", scanned.item("Vegansk hakket").isMeat)
        assertFalse("'and' is not duck", scanned.item("Salt and vinegar chips").isMeat)
        assertFalse("Royal Greenland is not beer", scanned.item("Royal greenland rejer").isAlcohol)
        assertTrue(scanned.item("Svinekoteletter").isMeat)
        assertTrue("The recognizer was unsure", scanned.item("Svinekoteletter").lowConfidence)
        assertEquals(-1_000L, scanned.item("Kuponrabat").priceCents)
        assertEquals(ReceiptItemKind.DISCOUNT, scanned.item("Kuponrabat").kind)
    }

    @Test
    fun quantityLineWithOnlyTheUnitPrice_multipliesIt() {
        val scanned = receipt {
            row("LIDL")
            row("YOGHURT NATUREL")
            row("3 x 9,95")
            row("TOTAL", "29,85")
        }
        assertEquals(listOf(2_985L), scanned.items.map { it.priceCents })
    }

    @Test
    fun withoutATotalLine_theCardPaymentIsTheTotal_andPaymentLinesAreNotItems() {
        val scanned = receipt {
            row("NETTO")
            row("MÆLK", "11,95")
            row("DANKORT", "11,95")
            row("MOMS", "2,39")
            row("BYTTEPENGE", "0,00")
        }
        assertEquals(1_195L, scanned.totalCents)
        assertEquals(listOf("Mælk"), scanned.items.map { it.name })
    }

    @Test
    fun cashGiven_isNotTheTotal() {
        val scanned = receipt {
            row("MÆLK", "11,95")
            row("KONTANT", "100,00")
            row("BYTTEPENGE", "88,05")
        }
        assertNull("Cash handed over includes the change", scanned.totalCents)
        assertEquals(listOf("Mælk"), scanned.items.map { it.name })
    }

    @Test
    fun aTotalWhoseAmountIsOnTheNextRow_isTheTotal_notAnItem() {
        val scanned = receipt {
            row("ÆBLER", "15,00")
            row("PÆRER", "18,00")
            line("TOTAL")
            line("33,00")
            row("DANKORT", "33,00")
        }
        assertEquals(3_300L, scanned.totalCents)
        assertEquals(listOf("Æbler", "Pærer"), scanned.items.map { it.name })
    }

    @Test
    fun otherWaysOfPrintingTheTotal() {
        assertEquals(24_035L, receipt { row("KAFFE", "240,35"); row("Køb i alt", "240,35") }.totalCents)
        assertEquals(24_035L, receipt { row("KAFFE", "240,35"); row("Total DKK", "240,35") }.totalCents)
        assertEquals(24_035L, receipt { row("KAFFE", "240,35"); row("At betale", "240,35 kr") }.totalCents)
        val withSubtotal = receipt { row("KAFFE", "240,35"); row("SUBTOTAL", "240,35"); row("TE", "10,00"); row("TOTAL", "250,35") }
        assertEquals("A subtotal is neither the total nor an item", listOf("Kaffe", "Te"), withSubtotal.items.map { it.name })
        assertEquals(25_035L, withSubtotal.totalCents)
    }

    @Test
    fun pricesWithKrOrASpaceNextToTheComma_areReadInFull() {
        val scanned = receipt {
            row("PIZZA MARGHERITA", "125,00 kr")
            row("HAKKET OKSEKØD", "45, 00") // the recognizer put a space after the comma
            row("COLA 1,5 L", "25 ,00")
            row("TOTAL", "195,00")
        }
        assertEquals(listOf(12_500L, 4_500L, 2_500L), scanned.items.map { it.priceCents })
        assertEquals(19_500L, scanned.totalCents)
    }

    @Test
    fun aTiltedPhoto_pairsNamesWithTheirOwnPrices() {
        // Rotated 5°: a price on the far right sits lower than the start of its own name by more than a
        // row, so pairing by height alone moves every price one item down. Names report the angle; short
        // prices come back as 0°, as the real recognizer does.
        val rows = listOf("ARLA MINIMÆLK" to "12,95", "RUGBRØD" to "22,00", "HAKKET OKSEKØD" to "45,00",
            "TUBORG GRØN" to "59,95", "BANANER" to "15,50", "SMØR" to "24,95", "TOTAL" to "180,35")
        val lines = rows.flatMapIndexed { i, (name, price) ->
            val y = 100 + 62 * i
            listOf(rotated(name, 60, 60 + name.length * 23, y, y + 36, 5.0, reported = 5f),
                rotated(price, 760, 860, y, y + 36, 5.0, reported = 0f))
        }
        val scanned = ReceiptParser.parse(lines)
        assertEquals(rows.dropLast(1).map { (name, price) -> name to DanishAmount.parse(price) }, scanned.items.map { it.name.uppercase() to it.priceCents })
        assertEquals(18_035L, scanned.totalCents)
    }

    /** The box a recognizer reports for text printed at [x0]–[x1], [top]–[bottom] on a photo tilted by [degrees]. */
    private fun rotated(text: String, x0: Int, x1: Int, top: Int, bottom: Int, degrees: Double, reported: Float): OcrLine {
        val a = Math.toRadians(degrees)
        val corners = listOf(x0 to top, x1 to top, x0 to bottom, x1 to bottom).map { (x, y) ->
            (x * cos(a) - y * sin(a)) to (x * sin(a) + y * cos(a))
        }
        return OcrLine(
            text, corners.minOf { it.first }.roundToInt(), corners.minOf { it.second }.roundToInt(),
            corners.maxOf { it.first }.roundToInt(), corners.maxOf { it.second }.roundToInt(), angle = reported,
        )
    }

    @Test
    fun aSlightlyTiltedPhoto_stillPairsEachNameWithItsPrice() {
        val scanned = receipt {
            row("NETTO")
            row("ÆBLER", "15,00", drift = 12)
            row("PÆRER", "18,00", drift = 12)
            row("TOTAL", "33,00", drift = 12)
        }
        assertEquals(listOf("Æbler" to 1_500L, "Pærer" to 1_800L), scanned.items.map { it.name to it.priceCents })
        assertEquals(3_300L, scanned.totalCents)
    }

    @Test
    fun noText_givesAnEmptyReceipt() {
        val scanned = ReceiptParser.parse(emptyList())
        assertNull(scanned.merchant)
        assertNull(scanned.totalCents)
        assertTrue(scanned.items.isEmpty())
    }
}
