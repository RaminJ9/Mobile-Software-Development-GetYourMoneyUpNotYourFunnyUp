package com.example.getyourmoneyupnotyourfunnyup.model

import java.math.RoundingMode
import kotlin.math.abs

/**
 * Amounts typed by hand or read from a receipt, in Danish notation (`59,95`, `1.240,50`, `-10,00`).
 * Showing amounts uses `formatDanishAmountFromCents` from the theme; this only reads and pre-fills
 * editable price fields.
 */
object DanishAmount {
    /** Typographic minus (U+2212) used in the design and on some receipts. */
    private const val MINUS = "−"

    /**
     * Parses `59,95`, `1.240,50`, `-10,00`, `−10`, `12.5` or `59,95 kr` into øre. Null when it isn't a number.
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
        return value.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
    }

    /** Text for an editable price field: `59,95`, `1.240,50`, `-10,00` (a plain minus, easy to edit). */
    fun forInput(cents: Long): String {
        val absolute = abs(cents)
        val whole = (absolute / 100).toString().reversed().chunked(3).joinToString(".").reversed()
        return (if (cents < 0) "-" else "") + whole + "," + (absolute % 100).toString().padStart(2, '0')
    }
}
