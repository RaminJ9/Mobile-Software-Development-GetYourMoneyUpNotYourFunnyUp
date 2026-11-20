package com.example.getyourmoneyupnotyourfunnyup.model

import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * One line of text the scanner found on the photo: its text, its box in pixels, how sure the recognizer
 * was (0–1) and the text's angle in degrees (positive when it slopes down to the right).
 */
data class OcrLine(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val confidence: Float = 1f,
    val angle: Float = 0f,
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

    /** Measured tilts below this many degrees are the recognizer's noise; the receipt is read as straight. */
    private const val MIN_TILT = 1.5

    fun parse(lines: List<OcrLine>): ScannedReceipt {
        val items = mutableListOf<ReceiptItem>()
        var merchant: String? = null
        var total: Long? = null
        var pendingName: String? = null // a name whose price is on the next row (e.g. "2 x 7,00   14,00")
        var pendingConfidence = 1f
        var totalPending = false // "TOTAL" was read but its amount is on the next row
        var paidCents: Long? = null // the card/MobilePay amount: the total, if no total line is found

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
                totalPending && price != null && !looksLikeName(upper) -> {
                    total = price.cents
                    break
                }
                // Never an item: the amount for this total is on the row below.
                price == null && isTotal(upper) -> {
                    totalPending = true
                    pendingName = null
                }
                isPayment(upper) -> {
                    if (price != null && paidCents == null) paidCents = price.cents
                    pendingName = null
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
        return ScannedReceipt(merchant, items, total ?: paidCents)
    }

    // --- Rows ----------------------------------------------------------------------------------------

    private data class Row(val text: String, val confidence: Float)

    /**
     * Joins the lines of each printed row, read top to bottom, left to right.
     *
     * Photos are rarely held straight, so positions are measured at right angles to the text. On a tilted
     * receipt a name on the left and its price on the far right then still form one row, instead of the
     * price sliding onto the next item.
     */
    private fun rows(lines: List<OcrLine>): List<Row> {
        val visible = lines.filter { it.text.isNotBlank() }
        if (visible.isEmpty()) return emptyList()
        // The recognizer's angles are noisy (a degree or two either way), and a curled receipt isn't tilted
        // the same everywhere. Below MIN_TILT the receipt is read as straight. Above it, straight, half the
        // measured tilt and the full tilt are tried, and the one where the prices line up best with their
        // rows is used (straight wins a tie).
        val measured = tiltOf(visible)
        if (abs(measured) < MIN_TILT) return layout(visible, 0.0).rows
        return listOf(0.0, measured / 2, measured).map { layout(visible, it) }.minBy { it.misfit }.rows
    }

    /** Rows read with the receipt straightened by [degrees]; [misfit] is how far prices sit from their rows. */
    private class Layout(val rows: List<Row>, val misfit: Double)

    private fun layout(visible: List<OcrLine>, degrees: Double): Layout {
        val tilt = Math.toRadians(degrees)
        val sinTilt = sin(tilt)
        val cosTilt = cos(tilt)
        // How far down the receipt a line is, measured across the text.
        fun across(line: OcrLine) = (line.top + line.bottom) / 2.0 * cosTilt - (line.left + line.right) / 2.0 * sinTilt
        // A tilted line's box is taller than its text by about width × sin(tilt).
        fun textHeight(line: OcrLine) =
            maxOf(((line.bottom - line.top) - (line.right - line.left) * abs(sinTilt)) / cosTilt, (line.bottom - line.top) * 0.25)
        val lineHeight = visible.map(::textHeight).sorted()[visible.size / 2]

        // Where a line sits from left to right, measured along the text.
        fun along(line: OcrLine) = (line.left + line.right) / 2.0 * cosTilt + (line.top + line.bottom) / 2.0 * sinTilt
        fun halfWidth(line: OcrLine) = (line.right - line.left) / 2.0
        // Text to the right of the price column isn't on the receipt (e.g. the label of a bottle next to it).
        // Joined onto a row it would hide that row's price.
        fun isLonePrice(line: OcrLine) = PRICE.find(line.text.trim())?.range?.first == 0
        val prices = visible.filter(::isLonePrice)
        val onReceipt = if (prices.size < 3) visible else {
            val columnEnd = prices.map { along(it) + halfWidth(it) }.sorted()[prices.size / 2]
            val margin = prices.map(::halfWidth).sorted()[prices.size / 2]
            visible.filter { along(it) - halfWidth(it) <= columnEnd + margin }
        }

        // Rows are built from the lines with words. Prices on their own, often printed a little above or
        // below their row, are then matched to rows closest pair first, one price per row: a line total
        // printed between a "2 x 14,00" row and a "RABAT" row goes to the row whose own price it is.
        val groups = mutableListOf<MutableList<OcrLine>>()
        val groupAt = mutableListOf<Double>()
        onReceipt.filterNot(::isLonePrice).sortedBy(::across).forEach { line ->
            val at = across(line)
            if (groups.isNotEmpty() && at - groupAt.last() <= lineHeight / 2) {
                groups.last() += line
            } else {
                groups += mutableListOf(line)
                groupAt += at
            }
        }
        val rowCount = groups.size
        val lonePrices = onReceipt.filter(::isLonePrice)
        val pairs = lonePrices.indices.flatMap { p -> (0 until rowCount).map { r -> Triple(p, r, abs(groupAt[r] - across(lonePrices[p]))) } }
            .filter { it.third <= lineHeight * 0.8 }
            .sortedBy { it.third }
        val placed = mutableMapOf<Int, Double>() // price -> distance to its row
        val taken = mutableSetOf<Int>()
        for ((p, r, distance) in pairs) {
            if (p in placed || r in taken) continue
            groups[r] += lonePrices[p]
            placed[p] = distance
            taken += r
        }
        val unplaced = lonePrices.indices.filterNot { it in placed }
        unplaced.forEach { p ->
            val at = across(lonePrices[p])
            // A second price printed on a row (a unit price beside the line total) joins that row.
            val row = (0 until rowCount).minByOrNull { abs(groupAt[it] - at) }?.takeIf { abs(groupAt[it] - at) <= lineHeight / 2 }
            if (row != null) {
                groups[row] += lonePrices[p]
            } else {
                groups += mutableListOf(lonePrices[p])
                groupAt += at
            }
        }
        // How far, in lines, prices sit from the rows they were matched to, plus one for each price that
        // wasn't: small when the straightening fits.
        val misfit = placed.values.sum() / lineHeight + unplaced.size
        val rows = groups.indices.sortedBy { groupAt[it] }.map { groups[it] }.map { group ->
            val ordered = group.sortedBy { it.left }
            Row(ordered.joinToString(" ") { it.text.trim() }, ordered.minOf { it.confidence })
        }
        return Layout(rows, misfit)
    }

    /**
     * The receipt's tilt in degrees: the median angle of the long lines, weighted by length. Short lines
     * such as "6,00" often come back as 0° even on a tilted photo, and logo text can be at any angle, so
     * only lines of at least 8 characters within ±15° count (all lines when there are none).
     */
    private fun tiltOf(lines: List<OcrLine>): Double {
        val reliable = lines.filter { it.text.count { c -> !c.isWhitespace() } >= 8 && abs(it.angle) <= 15f }.ifEmpty { lines }
        val byAngle = reliable.sortedBy { it.angle }
        val half = byAngle.sumOf { it.text.length } / 2.0
        var seen = 0
        return byAngle.first { line -> seen += line.text.length; seen >= half }.angle.toDouble()
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

    private fun isTotal(upper: String): Boolean = TOTAL.containsMatchIn(upper) || startsLike(upper, TOTAL_LIKE)

    private fun isPayment(upper: String): Boolean = words(upper).any { it in PAYMENT_WORDS } || startsLike(upper, PAYMENT_LIKE)

    /**
     * True when the row starts with one of [targets] give or take a misread letter ("AT DETALL" for
     * "AT BETALE", "DANNORT" for "DANKORT"): one letter for short words, two for long ones.
     */
    private fun startsLike(upper: String, targets: List<String>): Boolean {
        val start = words(upper)
        return targets.any { target ->
            val size = target.count { it == ' ' } + 1
            start.size >= size && editDistance(start.take(size).joinToString(" "), target) <= if (target.length >= 9) 2 else 1
        }
    }

    private fun editDistance(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            val current = IntArray(b.length + 1).also { it[0] = i + 1 }
            for (j in b.indices) {
                current[j + 1] = minOf(previous[j + 1] + 1, current[j] + 1, previous[j] + if (a[i] == b[j]) 0 else 1)
            }
            previous = current
        }
        return previous[b.length]
    }

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
        // 1: leading minus, 2: the amount (recognizers sometimes put a space next to the comma: "45, 00"),
        // 3: trailing minus.
        "([-−]?)\\s?([0-9OolIS]{1,3}(?:\\.[0-9OolIS]{3})+ ?, ?[0-9OolIS]{2}|[0-9OolIS]+ ?[,.] ?[0-9OolIS]{2})" +
            // then "kr" / "DKK" and a VAT code, both optional
            "([-−]?)\\s*(?:(?i:kr)\\.?|DKK)?\\s*(?:[AB*]{1,2})?\\s*$",
    )
    private val DIGIT_LOOKALIKES = mapOf('O' to '0', 'o' to '0', 'l' to '1', 'I' to '1', 'S' to '5')
    /** "2 x 7,00 [14,00]"; the recognizer often reads the "x" as "%", "K", "×" or "*". */
    private val QUANTITY =
        Regex("^(\\d+)\\s*(?:STK\\.?\\s*(?:X|Á|À|A)?|[X×%*KÁÀ@])\\s*(\\d+ ?[,.] ?\\d{2})(?:\\s+(\\d+ ?[,.] ?\\d{2}))?\\s*[AB*]{0,2}\\s*$")
    /** "TOTAL", "Total DKK", "SUM", "I alt", "Køb i alt", "At betale", "Til betaling" (not "Subtotal"). */
    private val TOTAL = Regex("^(?:TOTAL|TOTALT|SUM)\\b|\\b(?:I ALT|AT BETALE|TIL BETALING)\\b")

    /** Card and MobilePay lines print the amount paid, which is the total. Cash lines don't (change). */
    private val PAYMENT_WORDS = setOf("DANKORT", "VISA", "MASTERCARD", "MAESTRO", "MOBILEPAY", "BELØB", "KORTBETALING", "BETALINGSKORT")

    /** Total and payment words that are long enough to recognise with a misread letter. */
    private val TOTAL_LIKE = listOf("TOTAL", "AT BETALE", "TIL BETALING")
    private val PAYMENT_LIKE = listOf("DANKORT", "BETALINGSKORT", "MOBILEPAY", "MASTERCARD")
    private val DATE_OR_TIME = Regex(
        "\\b\\d{1,2}[./-]\\d{1,2}[./-]\\d{2,4}\\b|\\b\\d{1,2}:\\d{2}\\b|" +
            // "10. dec 2013", "3 NOVEMBER 2026"; whole month names and a year, so "1 JULEKAGE 450 G" is an item
            "\\b\\d{1,2}\\.? ?(?:JAN(?:UAR)?|FEB(?:RUAR)?|MAR(?:TS)?|APR(?:IL)?|MAJ|MAY|JUNI?|JULI?|AUG(?:UST)?|" +
            "SEP(?:T(?:EMBER)?)?|OKT(?:OBER)?|OCT(?:OBER)?|NOV(?:EMBER)?|DEC(?:EMBER)?)\\.? \\d{4}\\b",
    )
    private val IGNORED_WORDS = setOf(
        "MOMS", "MOMSBELØB", "KONTANT", "BYTTEPENGE", "RETUR",
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
