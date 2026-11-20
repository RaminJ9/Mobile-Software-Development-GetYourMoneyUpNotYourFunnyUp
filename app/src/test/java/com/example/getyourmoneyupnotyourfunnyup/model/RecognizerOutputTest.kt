package com.example.getyourmoneyupnotyourfunnyup.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Real recognizer output: what ML Kit returned for receipt photos from the accuracy test (NFR-6), stored
 * in `src/test/resources/receipts/`. The expected items were checked line by line against the printed
 * receipts; names are spelled as the recognizer read them ("Gronne druer", "Happy zo0").
 */
class RecognizerOutputTest {

    private fun read(name: String): ScannedReceipt {
        val text = checkNotNull(javaClass.getResource("/receipts/$name.tsv")) { "missing fixture $name" }.readText()
        val lines = text.lines().filter { it.isNotBlank() && !it.startsWith("#") }.map { row ->
            val f = row.split("\t")
            OcrLine(f[0], f[1].toInt(), f[2].toInt(), f[3].toInt(), f[4].toInt(), confidence = f[6].toFloat(), angle = f[5].toFloat())
        }
        return ReceiptParser.parse(lines)
    }

    private fun assertItems(expected: List<Pair<String, String>>, scanned: ScannedReceipt) =
        assertEquals(expected.map { (name, price) -> name to DanishAmount.parse(price) }, scanned.items.map { it.name to it.priceCents })

    @Test
    fun netto_textOnABottleBesideTheReceipt_isIgnored() {
        // "DR. PAULY" and "RIESLING" from a wine label, to the right of the price column, used to join the
        // LEGETØJSFLY and CASTELLO rows and hide their prices.
        val scanned = read("netto_beside_a_bottle")
        assertItems(
            listOf(
                "O8.paluly klosterberg" to "89,00", "Revitalise sho3thie" to "39,00", "Pant" to "4,50",
                "Kalvecuvette" to "164,30", "Tavlesat" to "69,00", "Pure fresh mint" to "24,95",
                "Labeponade 2 pak" to "12,95", "Haribo psh jp 85ml" to "7,00", "Xl steak n. apple" to "66,00",
                "Strenper" to "12,00", "Derma labepomade" to "29,95", "Rabat" to "-9,95", "Legetbjsfly" to "40,00",
                "Castello sliced 150g" to "95,00", "Jcrdbar 250g" to "20,00", "Pose" to "5,50",
            ),
            scanned,
        )
        assertEquals(66_920L, scanned.totalCents)
    }

    @Test
    fun superBrugsen_aLongRunOfDiscounts_eachStaysOnItsOwnRow() {
        // Ten RABAT rows in a row, 50 pixels apart, under the items they belong to: a price that slid by one
        // row would put every discount after it on the wrong line.
        val scanned = read("superbrugsen")
        assertItems(
            listOf(
                "Candy king p&m" to "20,80", "Candy king p&m" to "45,97", "Mini flutes 750 g" to "17,95",
                "Kims snack chips" to "29,95", "Snack chips sco" to "29,95", "Candy king p&m" to "18,31",
                "Champignoner hvide" to "17,00", "Bearnaise sauce" to "26,95", "Oko kartofler 1 kg" to "20,00",
                "Favorit sauce" to "18,95", "Yoggi yoghurt" to "22,95", "Minimalk" to "14,50", "Okseflanksteaks" to "78,03",
                "Menu 2 go" to "55,00", "Julepostej m bacon" to "61,90", "Gær 50 g" to "3,50",
                "Kylling klassisk 90g" to "18,95", "Tomat suppe" to "91,90", "Pal, hamb. ryg 90g" to "37,90",
                "Rabat" to "-15,95", "Rabat" to "-15,95", "Rabat" to "-12,95", "Rabat" to "-4,95", "Rabat" to "-10,95",
                "Rabat" to "-10,95", "Rabat" to "-17,95", "Rabat" to "-17,95", "Rabat" to "-8,95", "Rabat" to "-8,95",
                "Rabat" to "-8,95",
            ),
            scanned,
        )
        assertEquals(49_601L, scanned.totalCents)
    }
}
