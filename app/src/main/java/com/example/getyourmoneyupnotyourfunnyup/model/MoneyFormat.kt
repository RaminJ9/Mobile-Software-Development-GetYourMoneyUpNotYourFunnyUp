package com.example.getyourmoneyupnotyourfunnyup.model

import kotlin.math.abs

/**
 * Danish money formatting from the style guide: `1.240,50` — dot thousands separator, comma
 * decimals, always two decimals. The currency label ("kr") is added by the UI.
 */
object MoneyFormat {
    /** Typographic minus used for negative amounts (U+2212), as in the hi-fi design. */
    const val MINUS = "−"

    /** Formats the absolute value of [cents], e.g. `-124050` → `1.240,50`. */
    fun kr(cents: Long): String {
        val absolute = abs(cents)
        val whole = absolute / 100
        val fraction = absolute % 100
        return groupThousands(whole) + "," + fraction.toString().padStart(2, '0')
    }

    /** Like [kr] but keeps the sign: `−10,00` for negatives, `+145,00` when [plusSign] is true. */
    fun signed(cents: Long, plusSign: Boolean = false): String = when {
        cents < 0 -> MINUS + kr(cents)
        cents > 0 && plusSign -> "+" + kr(cents)
        else -> kr(cents)
    }

    /** Whole kroner with thousands separators (used in the centre of the pie chart): `1.240`. */
    fun wholeKr(cents: Long): String = groupThousands(Math.round(abs(cents) / 100.0))

    /**
     * Parses user input in Danish or plain format: `59,95`, `1.240,50`, `-10,00`, `−10`, `12.5`.
     * Returns null when the text is not a number.
     */
    fun parse(text: String): Long? {
        val cleaned = text.trim()
            .replace(" ", "")
            .replace(MINUS, "-")
            .removeSuffix("kr")
        if (cleaned.isEmpty() || cleaned == "-") return null
        val normalised = when {
            cleaned.contains(',') -> cleaned.replace(".", "").replace(',', '.')
            // "12.50" (one dot, ≤2 decimals) is a decimal point; "1.240" is a thousands separator.
            cleaned.count { it == '.' } == 1 && cleaned.substringAfter('.').length <= 2 -> cleaned
            else -> cleaned.replace(".", "")
        }
        val value = normalised.toBigDecimalOrNull() ?: return null
        return value.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).toLong()
    }

    /** Formats cents for an editable price field: `59,95`, `-10,00`. */
    fun input(cents: Long): String = (if (cents < 0) "-" else "") + kr(cents)

    private fun groupThousands(value: Long): String {
        val digits = value.toString()
        val builder = StringBuilder()
        digits.forEachIndexed { index, char ->
            if (index > 0 && (digits.length - index) % 3 == 0) builder.append('.')
            builder.append(char)
        }
        return builder.toString()
    }
}
