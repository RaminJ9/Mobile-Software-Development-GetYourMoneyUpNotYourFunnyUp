package com.example.getyourmoneyupnotyourfunnyup.data.demo

import com.example.getyourmoneyupnotyourfunnyup.model.DateLabels
import com.example.getyourmoneyupnotyourfunnyup.model.DebtCalculator
import com.example.getyourmoneyupnotyourfunnyup.model.DietRule
import com.example.getyourmoneyupnotyourfunnyup.model.Expense
import com.example.getyourmoneyupnotyourfunnyup.model.ExpenseSplit
import com.example.getyourmoneyupnotyourfunnyup.model.Group
import com.example.getyourmoneyupnotyourfunnyup.model.GroupMember
import com.example.getyourmoneyupnotyourfunnyup.model.GroupRole
import com.example.getyourmoneyupnotyourfunnyup.model.LedgerSnapshot
import com.example.getyourmoneyupnotyourfunnyup.model.Person
import com.example.getyourmoneyupnotyourfunnyup.model.ReceiptItem
import com.example.getyourmoneyupnotyourfunnyup.model.ReceiptItemKind
import com.example.getyourmoneyupnotyourfunnyup.model.Reminder
import com.example.getyourmoneyupnotyourfunnyup.model.Settlement
import com.example.getyourmoneyupnotyourfunnyup.model.SettlementStatus
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus

/**
 * Sample data matching the hi-fi prototype (`docs/HiFi.html`): nine groups, one expense waiting to
 * sync, one failed sync, and three groups where the user owes money.
 *
 * Timestamps are relative to `now` so labels like "Today" and "Yesterday" stay meaningful.
 * Balances are derived from these expenses and settlements, never hard-coded.
 */
object DemoLedger {
    const val ME = "me"
    const val CURRENCY = "DKK"

    fun create(now: Long): LedgerSnapshot = Builder(now).build()

    /** Lines read from the sample Netto receipt in the scan flow. */
    val receiptItems: List<ReceiptItem> = listOf(
        ReceiptItem(1, "Tuborg Grøn 6-pak", "59,95", isAlcohol = true),
        ReceiptItem(2, "Pant A ×6", "6,00", kind = ReceiptItemKind.DEPOSIT),
        ReceiptItem(3, "Fusilli pasta 500 g", "12,00"),
        ReceiptItem(4, "Hakket oksekød 8-12%", "45,00", isMeat = true),
        ReceiptItem(5, "Flåede tomater ×2", "14,00"),
        ReceiptItem(6, "Parmigiano 150 g", "3,95", lowConfidence = true),
        ReceiptItem(7, "Merlot rødvin", "79,00", isAlcohol = true),
        ReceiptItem(8, "Rugbrød", "22,00"),
        ReceiptItem(9, "Rabat oksekød", "-10,00", kind = ReceiptItemKind.DISCOUNT),
    )

    /** What the printed receipt says (the misread Parmigiano price makes the scanned lines not match). */
    const val RECEIPT_TOTAL_CENTS = 26_090L
    const val RECEIPT_MERCHANT = "Netto"
    const val RECEIPT_STORE = "Sanderum"

    /** The price actually printed for the item the scanner misread. */
    const val RECEIPT_CORRECT_LOW_PRICE = "32,95"

    private val people = listOf(
        Person(ME, "Victor Munk", "victor@example.dk", phone = "+45 12 34 56 78", paletteIndex = -1),
        Person("mads", "Mads", "mads.h@mail.dk", paletteIndex = 1, dietRules = setOf(DietRule.NO_ALCOHOL)),
        Person("ida", "Ida", "ida.lund@mail.dk", paletteIndex = 2),
        Person("jonas", "Jonas", "+45 22 81 40 17", paletteIndex = 0),
        Person("sofie", "Sofie", "sofie@kollegiet.dk", paletteIndex = 3),
        Person("emil", "Emil", "emil.k@mail.dk", paletteIndex = 4),
        Person("freja", "Freja", "freja.n@mail.dk", paletteIndex = 5),
        Person("gustav", "Gustav", "gustav.r@mail.dk", paletteIndex = 4),
        Person("astrid", "Astrid", "astrid.m@mail.dk", paletteIndex = 6),
        Person("lars", "Lars", "lars.p@mail.dk", paletteIndex = 7),
        Person("maja", "Maja", "maja.s@mail.dk", paletteIndex = 8),
        Person("oliver", "Oliver", "oliver.t@mail.dk", paletteIndex = 0),
        Person("noah", "Noah", "+45 31 45 92 60", paletteIndex = 2),
        Person("alma", "Alma", "alma.v@mail.dk", paletteIndex = 3),
        Person("karla", "Karla", "karla.j@mail.dk", paletteIndex = 1),
        Person("clara", "Clara", "clara@mail.dk", paletteIndex = 6),
        Person("mum", "Mum", "mor@mail.dk", paletteIndex = 4),
        Person("dad", "Dad", "far@mail.dk", paletteIndex = 5),
    )

    private class Builder(private val now: Long) {
        private val groups = mutableListOf<Group>()
        private val expenses = mutableListOf<Expense>()
        private val settlements = mutableListOf<Settlement>()

        private fun at(days: Int, hour: Int, minute: Int = 0) = DateLabels.daysAgo(now, days, hour, minute)

        private fun group(
            id: String, name: String, colorIndex: Int, createdBy: String, createdDaysAgo: Int,
            description: String, members: List<String>
        ) {
            val createdAt = at(createdDaysAgo, 9)
            groups += Group(
                id = id, name = name, description = description, currency = CURRENCY,
                createdBy = createdBy, createdAt = createdAt, updatedAt = createdAt,
                members = members.map { userId ->
                    GroupMember(
                        groupId = id, userId = userId,
                        displayName = people.first { it.id == userId }.name,
                        role = if (userId == createdBy) GroupRole.ADMIN else GroupRole.MEMBER,
                        joinedAt = createdAt
                    )
                },
                colorIndex = colorIndex
            )
        }

        private fun expense(
            id: String, groupId: String, title: String, payer: String, amount: Long,
            splits: Map<String, Long>, time: Long,
            sync: SyncStatus = SyncStatus.SYNCED, receipt: Boolean = false
        ) {
            check(splits.values.sum() == amount) { "Splits for $id must add up to the amount" }
            expenses += Expense(
                id = id, groupId = groupId, title = title, amountCents = amount, currency = CURRENCY,
                payerId = payer, category = if (receipt) "Receipt" else "General",
                receiptLocalUri = if (receipt) "demo://receipt/$id" else null,
                splits = splits.map { (userId, owed) -> ExpenseSplit("$id-$userId", id, userId, owed) },
                createdAt = time, createdBy = payer, syncStatus = sync
            )
        }

        private fun equal(groupId: String, amount: Long) =
            DebtCalculator.splitEqually(amount, groups.first { it.id == groupId }.members.map { it.userId })

        private fun paid(id: String, groupId: String, from: String, to: String, amount: Long, time: Long) {
            settlements += Settlement(
                id = id, groupId = groupId, debtorId = from, creditorId = to, amountCents = amount,
                currency = CURRENCY, paymentMethod = "MobilePay", status = SettlementStatus.COMPLETED,
                createdAt = time
            )
        }

        fun build(): LedgerSnapshot {
            // Kollegiet 4. sal — the user is owed money; one scanned receipt is still waiting to sync.
            group(
                "kol", "Kollegiet 4. sal", 0, ME, 60,
                "Shared kitchen stuff, groceries and Sunday dinners on the 4th floor.",
                listOf(ME, "ida", "jonas", "sofie", "mads", "emil", "freja")
            )
            expense(
                "kol-netto", "kol", "Netto – fællesspisning", ME, 26_090,
                mapOf(ME to 7_537, "mads" to 3_413, "ida" to 7_570, "jonas" to 7_570),
                at(0, 17, 42), sync = SyncStatus.PENDING_INSERT, receipt = true
            )
            expense("kol-paper", "kol", "Toilet paper & soap", "ida", 18_900, equal("kol", 18_900), at(1, 14, 2))
            expense("kol-pizza", "kol", "Pizza night", "jonas", 42_000, equal("kol", 42_000), at(4, 19))
            expense("kol-wifi", "kol", "Kitchen Wi-Fi, November", "freja", 29_900, equal("kol", 29_900), at(5, 10))
            paid("kol-p1", "kol", "sofie", ME, 6_400, at(2, 16))
            paid("kol-p2", "kol", ME, "jonas", 15_319, at(12, 12))
            paid("kol-p3", "kol", "emil", "freja", 12_971, at(12, 13))
            paid("kol-p4", "kol", "mads", "jonas", 12_139, at(12, 14))
            paid("kol-p5", "kol", "sofie", "ida", 6_858, at(12, 15))
            paid("kol-p6", "kol", "sofie", "freja", 3_713, at(12, 16))
            paid("kol-p7", "kol", "mads", "freja", 245, at(12, 17))

            // Friday Bar crew — the user owes Sofie their share of today's bar tab.
            group(
                "fri", "Friday Bar crew", 2, "sofie", 90,
                "Friday bar tabs and the odd late-night pizza.",
                listOf(ME, "sofie", "ida", "jonas", "emil", "astrid", "lars", "maja", "oliver")
            )
            expense("fri-tab", "fri", "Bar tab", "sofie", 85_050, equal("fri", 85_050), at(0, 9, 40))
            listOf("ida", "jonas", "emil", "astrid", "lars", "maja", "oliver").forEachIndexed { i, who ->
                paid("fri-p$i", "fri", who, "sofie", 9_450, at(0, 10, i))
            }

            // Padel Tuesdays — the user owes Jonas for the court.
            group(
                "pad", "Padel Tuesdays", 5, "jonas", 45,
                "Court bookings and balls, every Tuesday at 19.",
                listOf(ME, "jonas", "mads", "emil")
            )
            expense("pad-court", "pad", "Court, 2 hours", "jonas", 32_000, equal("pad", 32_000), at(1, 19, 30))
            expense("pad-balls", "pad", "Balls & grip tape", ME, 16_000, equal("pad", 16_000), at(6, 18))
            paid("pad-p1", "pad", "emil", "jonas", 12_000, at(1, 21))
            paid("pad-p2", "pad", "mads", ME, 7_200, at(1, 21, 10))
            paid("pad-p3", "pad", "mads", "jonas", 4_800, at(1, 21, 15))

            // Ski trip Åre — the biggest debt; the user's own fuel expense failed to sync.
            group(
                "ski", "Ski trip Åre", 1, "mads", 20,
                "Cabin in Åre, 13–20 February. Lift passes, rental, food and fuel.",
                listOf(ME, "mads", "ida", "jonas", "sofie", "emil")
            )
            expense("ski-fuel", "ski", "Fuel for the drive", ME, 69_000, equal("ski", 69_000), at(2, 8, 5), sync = SyncStatus.FAILED)
            expense("ski-lift", "ski", "Lift passes ×6", "mads", 432_000, equal("ski", 432_000), at(2, 12))
            expense("ski-rent", "ski", "Ski rental deposit", "ida", 72_000, equal("ski", 72_000), at(3, 15))
            expense("ski-cabin", "ski", "Cabin deposit", "mads", 720_000, equal("ski", 720_000), at(4, 18))
            expense("ski-snack", "ski", "Netto – road trip snacks", "emil", 31_200, equal("ski", 31_200), at(5, 11), receipt = true)
            paid("ski-p1", "ski", ME, "mads", 123_100, at(2, 20))
            // Earlier payments to Mads, so the group's ledger leads with expenses as in the hi-fi.
            paid("ski-p2", "ski", "ida", "mads", 160_700, at(6, 10))
            paid("ski-p3", "ski", "jonas", "mads", 206_700, at(6, 11))
            paid("ski-p4", "ski", "sofie", "mads", 214_250, at(6, 12))
            paid("ski-p5", "ski", "emil", "mads", 175_350, at(6, 13))

            // Mobile Software team — two teammates owe the user for pizza.
            group(
                "mob", "Mobile Software team", 3, "gustav", 70,
                "Course project expenses: printing, pizza and demo day.",
                listOf(ME, "gustav", "astrid", "lars", "maja", "noah")
            )
            expense("mob-pizza", "mob", "Pizza for demo day", ME, 21_000, equal("mob", 21_000), at(3, 17))
            expense("mob-poster", "mob", "Poster printing", "gustav", 21_000, equal("mob", 21_000), at(2, 13))
            paid("mob-p1", "mob", "astrid", "gustav", 7_000, at(1, 9))
            paid("mob-p2", "mob", "lars", ME, 7_000, at(1, 9, 30))
            paid("mob-p3", "mob", "maja", "gustav", 4_000, at(1, 10))
            paid("mob-p4", "mob", "noah", "gustav", 3_000, at(1, 10, 30))
            paid("mob-p5", "mob", "noah", ME, 1_000, at(1, 10, 45))

            // Settled groups.
            group(
                "fam", "Family dinners", 4, "mum", 200,
                "Groceries for Sunday dinners at Mum and Dad's.",
                listOf(ME, "mum", "dad", "clara")
            )
            expense("fam-dinner", "fam", "Sunday dinner groceries", "mum", 34_000, equal("fam", 34_000), at(2, 16))
            paid("fam-p1", "fam", ME, "mum", 8_500, at(2, 20, 30))
            paid("fam-p2", "fam", "dad", "mum", 8_500, at(2, 20, 40))
            paid("fam-p3", "fam", "clara", "mum", 8_500, at(2, 21))

            group(
                "cph", "Copenhagen weekend", 6, ME, 40,
                "Weekend in Copenhagen with the old class.",
                listOf(ME, "ida", "sofie", "astrid", "karla")
            )
            expense("cph-bnb", "cph", "Airbnb Nørrebro", ME, 200_000, equal("cph", 200_000), at(29, 12))
            listOf("ida", "sofie", "astrid", "karla").forEachIndexed { i, who ->
                paid("cph-p$i", "cph", who, ME, 40_000, at(28, 10 + i))
            }

            group(
                "book", "Book club", 7, "freja", 120,
                "Monthly book club: cake, coffee and the odd book.",
                listOf(ME, "freja", "alma", "karla", "maja", "astrid")
            )
            expense("book-cake", "book", "Cake & coffee", "freja", 18_000, equal("book", 18_000), at(39, 19))
            listOf(ME, "alma", "karla", "maja", "astrid").forEachIndexed { i, who ->
                paid("book-p$i", "book", who, "freja", 3_000, at(38, 10 + i))
            }

            group(
                "house", "Sofie's housewarming", 8, "sofie", 60,
                "Gift and drinks for Sofie's new flat.",
                listOf(ME, "sofie", "ida", "jonas", "mads", "emil", "freja", "astrid", "lars", "maja", "oliver", "karla")
            )
            expense("house-gift", "house", "Gift: plant & vase", ME, 60_000, equal("house", 60_000), at(51, 14))
            listOf("sofie", "ida", "jonas", "mads", "emil", "freja", "astrid", "lars", "maja", "oliver", "karla")
                .forEachIndexed { i, who -> paid("house-p$i", "house", who, ME, 5_000, at(50, 9 + i / 2, (i % 2) * 30)) }

            return LedgerSnapshot(
                currentUserId = ME,
                people = people.associateBy { it.id },
                groups = groups.toList(),
                expenses = expenses.toList(),
                settlements = settlements.toList(),
                reminders = listOf(Reminder("rem-ski", "ski", "mads", ME, at(0, 10, 12))),
                favouriteGroupIds = setOf("kol", "fri", "ski"),
                lastSyncedAt = now
            )
        }
    }
}
