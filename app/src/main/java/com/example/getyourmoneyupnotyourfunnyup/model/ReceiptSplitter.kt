package com.example.getyourmoneyupnotyourfunnyup.model

enum class ReceiptItemKind {
    REGULAR,

    /** Bottle deposit ("pant") – shared by how much each person had. */
    DEPOSIT,

    /** Discount ("rabat") – shared by how much each person had. */
    DISCOUNT
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
    val priceCents: Long get() = MoneyFormat.parse(priceText) ?: 0L
}

/** Turns receipt lines + "who had what" into one amount per person. All maths in øre. */
object ReceiptSplitter {

    /** People who share an item unless someone changes it: everyone whose diet rules allow it. */
    fun defaultSharers(item: ReceiptItem, members: List<Person>): List<String> = members.filter { person ->
        !(item.isAlcohol && DietRule.NO_ALCOHOL in person.dietRules) &&
            !(item.isMeat && DietRule.NO_MEAT in person.dietRules)
    }.map { it.id }

    /** Who shares [item], after per-item overrides and people left out of the whole receipt. */
    fun sharers(
        item: ReceiptItem,
        members: List<Person>,
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
        members: List<Person>,
        overrides: Map<Int, List<String>>,
        excluded: Set<String>,
        payerId: String
    ): Map<String, Long> {
        val active = members.map { it.id }.filter { it !in excluded }
        if (active.isEmpty()) return emptyMap()
        val base = LinkedHashMap<String, Long>().apply { active.forEach { put(it, 0L) } }

        items.filter { it.kind == ReceiptItemKind.REGULAR }.forEach { item ->
            val who = sharers(item, members, overrides, excluded).ifEmpty { active }
            DebtCalculator.splitEqually(item.priceCents, who).forEach { (id, part) ->
                base[id] = (base[id] ?: 0L) + part
            }
        }

        val extra = items.filter { it.kind != ReceiptItemKind.REGULAR }.sumOf { it.priceCents }
        val baseTotal = base.values.sum()
        val shares = LinkedHashMap<String, Long>()
        if (baseTotal == 0L) {
            DebtCalculator.splitEqually(extra, active).forEach { (id, part) -> shares[id] = (base[id] ?: 0L) + part }
        } else {
            base.forEach { (id, amount) -> shares[id] = amount + Math.floorDiv(extra * amount, baseTotal) }
        }

        val total = items.sumOf { it.priceCents }
        val leftover = total - shares.values.sum()
        val absorber = if (payerId in shares) payerId else active.first()
        shares[absorber] = (shares[absorber] ?: 0L) + leftover
        return shares
    }
}
