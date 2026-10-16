package ir.anishop.imei.domain

/** Recognize a labelled IMEI first. Unlabelled candidates are accepted only if Luhn-valid and unambiguous. */
object BarcodeParser {
    data class Candidate(val imei: String, val slot: Int?)
    private val labelled = Regex("(?i)(?:IMEI[\\s:_-]*([12])?|IMEI\\s*NO\\.?)[^0-9]{0,10}([0-9۰-۹٠-٩]{15})(?![0-9۰-۹٠-٩])")
    private val numbers = Regex("(?<![0-9۰-۹٠-٩])[0-9۰-۹٠-٩]{15}(?![0-9۰-۹٠-٩])")
    fun parse(text: String): List<Candidate> {
        val candidates = mutableListOf<Candidate>()
        labelled.findAll(text).forEach { m ->
            val imei = Rules.digits(m.groupValues[2])
            if (Rules.imei(imei)) candidates += Candidate(imei, m.groupValues[1].toIntOrNull())
        }
        // Some labels have only a barcode containing the bare 15-digit IMEI.
        numbers.findAll(text).forEach { m ->
            val imei = Rules.digits(m.value)
            if (Rules.imei(imei) && candidates.none { it.imei == imei }) candidates += Candidate(imei, null)
        }
        return candidates.distinctBy { it.imei }.take(4)
    }
}
