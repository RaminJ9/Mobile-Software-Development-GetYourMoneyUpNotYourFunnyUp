package com.example.getyourmoneyupnotyourfunnyup.model

enum class ReceiptItemKind {
    REGULAR,

    /** Bottle deposit ("pant") – shared by how much each person had. */
    DEPOSIT,

    /** Discount ("rabat") – shared by how much each person had. */
    DISCOUNT
}

/**
 * Someone a receipt can be split between: a member of the chosen group. [excludeAlcohol] and
 * [excludeMeat] are their "Never charge me for" settings from the profile.
 */
data class ScanPerson(
    val id: String,
    val name: String,
    val colourIndex: Int,
    val excludeAlcohol: Boolean = false,
    val excludeMeat: Boolean = false
) {
    val initial: String get() = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val hasDietRule: Boolean get() = excludeAlcohol || excludeMeat
}

/**
 * One line on a scanned (or manually typed) receipt. [priceText] is kept as typed so the edit field
 * never fights the user; [priceCents] is the parsed value.
 */
data class ReceiptItem(
    val id: Int,
    val name: String,
    val priceText: String,
    val kind: ReceiptItemKind = ReceiptItemKind.REGULAR,
    val isAlcohol: Boolean = false,
    val isMeat: Boolean = false,
    /** The scanner was unsure about this price; the UI highlights it. */
    val lowConfidence: Boolean = false
) {
    val priceCents: Long get() = DanishAmount.parse(priceText) ?: 0L

    /**
     * Deposits, discounts and any other negative line are spread by how much each person had instead of
     * being ticked per person, so nobody's share can drop below zero.
     */
    val sharedByShare: Boolean get() = kind != ReceiptItemKind.REGULAR || priceCents < 0
}

/** Turns receipt lines + "who had what" into one amount per person. All maths in øre. */
object ReceiptSplitter {

    /** People who share an item unless someone changes it: everyone whose diet rules allow it. */
    fun defaultSharers(item: ReceiptItem, members: List<ScanPerson>): List<String> = members.filter { person ->
        !(item.isAlcohol && person.excludeAlcohol) && !(item.isMeat && person.excludeMeat)
    }.map { it.id }

    /** Who shares [item], after per-item overrides and people left out of the whole receipt. */
    fun sharers(
        item: ReceiptItem,
        members: List<ScanPerson>,
        overrides: Map<Int, List<String>>,
        excluded: Set<String>
    ): List<String> {
        val chosen = overrides[item.id] ?: defaultSharers(item, members)
        return members.map { it.id }.filter { it in chosen && it !in excluded }
    }

    /**
     * Each included person's share. Regular items are split equally between their sharers (an item
     * nobody is ticked on is split between everyone). Deposits and discounts are spread in proportion
     * to what each person had. Rounding leftovers go to the payer, so shares always add up to the
     * receipt total.
     */
    fun shares(
        items: List<ReceiptItem>,
        members: List<ScanPerson>,
        overrides: Map<Int, List<String>>,
        excluded: Set<String>,
        payerId: String
    ): Map<String, Long> {
        val active = members.map { it.id }.filter { it !in excluded }
        if (active.isEmpty()) return emptyMap()
        val base = LinkedHashMap<String, Long>().apply { active.forEach { put(it, 0L) } }

        items.filterNot { it.sharedByShare }.forEach { item ->
            val who = sharers(item, members, overrides, excluded).ifEmpty { active }
            splitEqually(item.priceCents, who).forEach { (id, part) ->
                base[id] = (base[id] ?: 0L) + part
            }
        }

        val extra = items.filter { it.sharedByShare }.sumOf { it.priceCents }
        val baseTotal = base.values.sum()
        val shares = LinkedHashMap<String, Long>()
        if (baseTotal == 0L) {
            splitEqually(extra, active).forEach { (id, part) -> shares[id] = (base[id] ?: 0L) + part }
        } else {
            base.forEach { (id, amount) -> shares[id] = amount + Math.floorDiv(extra * amount, baseTotal) }
        }

        val total = items.sumOf { it.priceCents }
        val leftover = total - shares.values.sum()
        val absorber = if (payerId in shares) payerId else active.first()
        shares[absorber] = (shares[absorber] ?: 0L) + leftover
        return shares
    }

    /**
     * Splits [totalCents] equally between [userIds]. Leftover øre go to the first people in the list, so
     * the parts always add up to the total.
     */
    internal fun splitEqually(totalCents: Long, userIds: List<String>): Map<String, Long> {
        if (userIds.isEmpty()) return emptyMap()
        val base = Math.floorDiv(totalCents, userIds.size.toLong())
        val remainder = totalCents - base * userIds.size
        return userIds.mapIndexed { index, id -> id to base + if (index < remainder) 1L else 0L }.toMap(LinkedHashMap())
    }
}
