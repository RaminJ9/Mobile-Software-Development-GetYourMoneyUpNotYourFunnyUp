package com.example.getyourmoneyupnotyourfunnyup.model

import java.util.Locale
import kotlin.math.abs

/**
 * One line of text the scanner found on the photo: its text, its box in pixels and how sure the
 * recognizer was (0–1).
 */
data class OcrLine(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val confidence: Float = 1f,
)

/** What a receipt photo turned into. [totalCents] is the printed total, null when it wasn't found. */
data class ScannedReceipt(
    val merchant: String?,
    val items: List<ReceiptItem>,
    val totalCents: Long?,
)

/**
 * Turns the text lines of a Danish shop receipt into [ReceiptItem]s.
 *
 * Receipts print the name on the left and the price on the right, and a recognizer often returns those
 * as separate lines, so lines are first put back together into rows by their height on the photo.
 * Rows above the first item are the header (shop name, address); everything after the total (payment,
 * VAT, change) is ignored. Prices the recognizer was unsure about are marked `lowConfidence`, which the
 * check-items screen highlights for the user.
 */
object ReceiptParser {

    /** Below this the recognizer's own confidence counts as "check this price". */
    const val MIN_CONFIDENCE = 0.8f

    fun parse(lines: List<OcrLine>): ScannedReceipt {
        val items = mutableListOf<ReceiptItem>()
        var merchant: String? = null
        var total: Long? = null
        var pendingName: String? = null // a name whose price is on the next row (e.g. "2 x 7,00   14,00")
        var pendingConfidence = 1f

        fun addItem(name: String, cents: Long, lowConfidence: Boolean) {
            val words = words(name)
            val isDiscount = cents < 0 || "RABAT" in words
            items += ReceiptItem(
                id = items.size + 1,
                name = tidyName(name),
                priceText = DanishAmount.forInput(if (isDiscount) -abs(cents) else cents),
                kind = when {
                    isDiscount -> ReceiptItemKind.DISCOUNT
                    words.any { it.startsWith("PANT") } -> ReceiptItemKind.DEPOSIT
                    else -> ReceiptItemKind.REGULAR
                },
                isAlcohol = !isDiscount && words.any(::isAlcoholWord),
                // Plant-based products ("Plantefars", "Vegansk hakket") are not meat, whatever they imitate.
                isMeat = !isDiscount && words.none(::isPlantBased) && words.any(::isMeatWord),
                lowConfidence = lowConfidence,
            )
        }

        for (row in rows(lines)) {
            val price = trailingPrice(row.text)
            val name = (price?.let { row.text.substring(0, it.start) } ?: row.text).trim()
            val upper = name.uppercase(Locale.ROOT)
            val unsure = row.confidence < MIN_CONFIDENCE || price?.repaired == true
            val quantity = QUANTITY.matchEntire(row.text.trim().uppercase(Locale.ROOT))

            when {
                // The total ends the item list; payment, VAT and change lines follow it.
                price != null && isTotal(upper) -> {
                    total = price.cents
                    break
                }
                isIgnored(upper) -> pendingName = null
                quantity != null -> {
                    // "2 x 7,00   14,00" (or just "2 x 7,00") under a name without a price: that name costs
                    // the line total. Under a name that already had a price, it is only a detail line.
                    val heldName = pendingName
                    if (heldName != null) {
                        addItem(heldName, quantityTotal(quantity), unsure || pendingConfidence < MIN_CONFIDENCE)
                    }
                    pendingName = null
                }
                price == null -> {
                    if (items.isEmpty() && merchant == null && looksLikeName(upper)) {
                        merchant = tidyMerchant(name)
                    } else {
                        pendingName = name.takeIf { looksLikeName(upper) }
                        pendingConfidence = row.confidence
                    }
                }
                name.isEmpty() || !looksLikeName(upper) -> {
                    // A price on its own row belongs to the name above it.
                    pendingName?.let { addItem(it, price.cents, unsure) }
                    pendingName = null
                }
                else -> {
                    addItem(name, price.cents, unsure)
                    pendingName = null
                }
            }
        }
        return ScannedReceipt(merchant, items, total)
    }

    // --- Rows ----------------------------------------------------------------------------------------

    private data class Row(val text: String, val confidence: Float)

    /** Joins lines that sit at the same height into one row, read top to bottom, left to right. */
    private fun rows(lines: List<OcrLine>): List<Row> {
        val groups = mutableListOf<MutableList<OcrLine>>()
        lines.filter { it.text.isNotBlank() }.sortedBy { (it.top + it.bottom) / 2 }.forEach { line ->
            val row = groups.lastOrNull()
            if (row != null && row.any { sameRow(it, line) }) row += line else groups += mutableListOf(line)
        }
        return groups.map { group ->
            val ordered = group.sortedBy { it.left }
            Row(ordered.joinToString(" ") { it.text.trim() }, ordered.minOf { it.confidence })
        }
    }

    /** Two lines are on the same row when they overlap by at least half of the shorter one's height. */
    private fun sameRow(a: OcrLine, b: OcrLine): Boolean {
        val overlap = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
        return overlap * 2 >= minOf(a.bottom - a.top, b.bottom - b.top)
    }

    // --- Prices --------------------------------------------------------------------------------------

    private class Price(val cents: Long, val start: Int, val repaired: Boolean)

    /**
     * A Danish amount at the end of the row: `59,95`, `1.240,50`, `-10,00`, `10,00-`, optionally followed
     * by a VAT code (`A`, `B`, `*`). Letters that recognizers confuse with digits (O → 0, l → 1, S → 5)
     * are repaired, and such a price is reported as [Price.repaired].
     */
    private fun trailingPrice(text: String): Price? {
        // Numbered groups only: named groups need Android 8 (API 26).
        val match = PRICE.find(text) ?: return null
        val (lead, raw, trail) = match.destructured
        val digits = raw.map { DIGIT_LOOKALIKES[it] ?: it }.joinToString("")
        val cents = DanishAmount.parse(digits) ?: return null
        val negative = lead.isNotEmpty() || trail.isNotEmpty()
        return Price(if (negative) -cents else cents, match.range.first, repaired = digits != raw)
    }

    /** The printed line total ("2 x 7,00   14,00"), or count × unit price when only "2 x 7,00" is printed. */
    private fun quantityTotal(match: MatchResult): Long {
        val printedTotal = match.groups[3]?.value?.let(DanishAmount::parse)
        return printedTotal ?: (match.groupValues[1].toLong() * (DanishAmount.parse(match.groupValues[2]) ?: 0L))
    }

    // --- Classification ------------------------------------------------------------------------------

    private fun isTotal(upper: String): Boolean = TOTAL.containsMatchIn(upper)

    private fun isIgnored(upper: String): Boolean =
        words(upper).any { it in IGNORED_WORDS } || IGNORED_PHRASES.any { it in upper } || DATE_OR_TIME.containsMatchIn(upper)

    private fun looksLikeName(upper: String): Boolean = upper.count { it.isLetter() } >= 2

    private fun words(text: String): List<String> =
        text.uppercase(Locale.ROOT).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }

    private fun isAlcoholWord(word: String) = word in ALCOHOL_WORDS

    private fun isMeatWord(word: String) =
        word in MEAT_WORDS || MEAT_PREFIXES.any { word.startsWith(it) } || MEAT_SUFFIXES.any { word.endsWith(it) }

    private fun isPlantBased(word: String) = PLANT_BASED_PREFIXES.any { word.startsWith(it) }

    /** "HAKKET OKSEKØD 8-12%" → "Hakket oksekød 8-12%". Mixed-case names are kept as printed. */
    private fun tidyName(name: String): String {
        val trimmed = name.trim().replace(Regex("\\s+"), " ")
        if (trimmed.any { it.isLowerCase() }) return trimmed
        return trimmed.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }
    }

    /** "REMA 1000" → "Rema 1000", "NETTO" → "Netto". */
    private fun tidyMerchant(name: String): String =
        name.trim().lowercase(Locale.ROOT).split(Regex("\\s+")).joinToString(" ") { word ->
            word.replaceFirstChar { it.titlecase(Locale.ROOT) }
        }

    private val PRICE = Regex(
        // 1: leading minus, 2: the amount, 3: trailing minus, then an optional VAT code.
        "([-−]?)\\s?([0-9OolIS]{1,3}(?:\\.[0-9OolIS]{3})+,[0-9OolIS]{2}|[0-9OolIS]+[,.][0-9OolIS]{2})" +
            "([-−]?)\\s*(?:[AB*]{1,2})?\\s*$",
    )
    private val DIGIT_LOOKALIKES = mapOf('O' to '0', 'o' to '0', 'l' to '1', 'I' to '1', 'S' to '5')
    private val QUANTITY =
        Regex("^(\\d+)\\s*(?:X|STK\\.?\\s*(?:X|Á|À|A)?|Á|À|@)\\s*(\\d+[,.]\\d{2})(?:\\s+(\\d+[,.]\\d{2}))?\\s*[AB*]{0,2}\\s*$")
    private val TOTAL = Regex("^(TOTAL|TOTALT|I ALT|AT BETALE|TIL BETALING|SUM)\\b")
    private val DATE_OR_TIME = Regex("\\b\\d{1,2}[./-]\\d{1,2}[./-]\\d{2,4}\\b|\\b\\d{1,2}:\\d{2}\\b")
    private val IGNORED_WORDS = setOf(
        "MOMS", "MOMSBELØB", "DANKORT", "VISA", "MASTERCARD", "MOBILEPAY", "KONTANT", "BYTTEPENGE", "RETUR",
        "CVR", "TLF", "TELEFON", "KVITTERING", "BON", "KASSE", "EKSPEDIENT", "TERMINAL", "SUBTOTAL", "MELLEMSUM",
    )
    private val IGNORED_PHRASES = listOf("TAK FOR", "ANTAL VARER", "DU HAR SPARET", "DU SPAREDE")
    private val ALCOHOL_WORDS = setOf(
        "ØL", "PILSNER", "PILS", "LAGER", "FADØL", "JULEØL", "JULEBRYG", "HVEDEØL", "IPA", "TUBORG", "CARLSBERG",
        "HEINEKEN", "SOMERSBY", "CIDER", "VIN", "RØDVIN", "HVIDVIN", "ROSÉVIN", "ROSEVIN", "BOBLEVIN",
        "CAVA", "PROSECCO", "CHAMPAGNE", "MERLOT", "CABERNET", "SAUVIGNON", "CHARDONNAY", "RIOJA", "CHIANTI",
        "VODKA", "GIN", "ROM", "WHISKY", "WHISKEY", "SNAPS", "AKVAVIT", "SPIRITUS", "LIKØR", "BREEZER",
    )
    private val MEAT_WORDS = setOf(
        "KØD", "HAKKET", "HAKKEDE", "BACON", "SKINKE", "SALAMI", "SPEGEPØLSE", "MEDISTER", "FRIKADELLER", "FLÆSK",
        "FLÆSKESTEG", "MØRBRAD", "ENTRECOTE", "CULOTTE", "CHORIZO", "PEPPERONI", "LEVERPOSTEJ", "HAMBURGERRYG",
        "SVIN", "LAM", "KALKUN", "KYLLING",
    )
    private val MEAT_PREFIXES = listOf("OKSE", "KYLLING", "SVINE", "LAMME", "KALKUN", "KALVE")
    private val MEAT_SUFFIXES = listOf("KØD", "PØLSE", "PØLSER", "FARS", "BØF", "BØFFER")
    private val PLANT_BASED_PREFIXES = listOf("PLANTE", "SOJA", "VEGGIE", "VEGANSK", "VEGETAR", "VEGAN")
}
