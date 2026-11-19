package com.caglaryildiz.alarm

import android.content.Context
import java.util.Locale

/** One row of the bundled place list: a province, or a district inside a province. */
internal data class LocalPlace(
    val province: String,
    val district: String?,
    val lat: Double,
    val lon: Double,
    val population: Int
) {
    val isProvince get() = district == null
    /** "İstanbul" for a province, "İstanbul / Esenyurt" for a district. */
    val title get() = if (district == null) province else "$province / $district"
    internal val provinceKey = PlaceSearch.normalize(province)
    internal val districtKey = district?.let { PlaceSearch.normalize(it) } ?: ""
}

/**
 * Offline, as-you-type search over Turkey's 81 provinces and their districts
 * (bundled from GeoNames, CC BY 4.0). It needs no network, ignores Turkish
 * letters and case ("istan", "İstan" and "ıstan" all find İstanbul) and ranks:
 *   1. provinces whose name starts with the query,
 *   2. districts whose name starts with the query,
 *   3. every district of a matching province ("istan" → İstanbul / Esenyurt …),
 *   4. names that merely contain the query.
 * Bigger places come first inside each group. Two words narrow the search:
 * "istanbul esen" or "İstanbul/Esen" find İstanbul / Esenyurt.
 */
internal object PlaceSearch {
    private const val ASSET = "places_tr.tsv"
    @Volatile private var cache: List<LocalPlace>? = null

    fun places(context: Context): List<LocalPlace> = cache ?: synchronized(this) {
        cache ?: load(context).also { cache = it }
    }

    private fun load(context: Context): List<LocalPlace> =
        context.assets.open(ASSET).bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith("#") }.mapNotNull(::parseLine).toList()
        }

    internal fun parseLine(line: String): LocalPlace? {
        val parts = line.split('\t')
        if (parts.size < 6) return null
        return LocalPlace(
            province = parts[1],
            district = parts[2].ifEmpty { null }.takeIf { parts[0] == "D" },
            lat = parts[3].toDoubleOrNull() ?: return null,
            lon = parts[4].toDoubleOrNull() ?: return null,
            population = parts[5].toIntOrNull() ?: 0
        )
    }

    /** Lower-case ASCII form, so Turkish letters, dotted/dotless I and accents never block a match. */
    fun normalize(text: String): String {
        val out = StringBuilder(text.length)
        for (char in text) {
            out.append(when (char) {
                'İ', 'I', 'ı', 'î', 'Î' -> 'i'
                'Ş', 'ş' -> 's'
                'Ğ', 'ğ' -> 'g'
                'Ü', 'ü', 'û', 'Û' -> 'u'
                'Ö', 'ö' -> 'o'
                'Ç', 'ç' -> 'c'
                'Â', 'â' -> 'a'
                else -> char.lowercaseChar()
            })
        }
        return out.toString()
    }

    private fun tokens(query: String) =
        normalize(query).split(' ', '/', ',', '-', '.').filter { it.isNotEmpty() }

    fun search(all: List<LocalPlace>, query: String, limit: Int = 30): List<LocalPlace> {
        val words = tokens(query)
        if (words.isEmpty()) return emptyList()
        val ranked = all.mapNotNull { place -> rank(place, words)?.let { it to place } }
        return ranked
            .sortedWith(compareBy<Pair<Int, LocalPlace>> { it.first }.thenByDescending { it.second.population })
            .take(limit)
            .map { it.second }
    }

    private fun startsWithWord(key: String, word: String) =
        key.startsWith(word) || key.split(' ').any { it.startsWith(word) }

    /** Lower is better; `null` means no match. */
    private fun rank(place: LocalPlace, words: List<String>): Int? {
        val province = place.provinceKey
        val district = place.districtKey
        if (words.size == 1) {
            val word = words[0]
            return when {
                place.isProvince && startsWithWord(province, word) -> if (province == word) 0 else 1
                !place.isProvince && startsWithWord(district, word) -> if (district == word) 2 else 3
                !place.isProvince && startsWithWord(province, word) -> 4
                place.isProvince && word.length >= 3 && word in province -> 5
                !place.isProvince && word.length >= 3 && word in district -> 6
                else -> null
            }
        }
        // Several words: one must name the province, the others the district ("istanbul esen").
        val provinceWord = words.firstOrNull { startsWithWord(province, it) } ?: return null
        val rest = words - provinceWord
        if (place.isProvince) return if (rest.isEmpty()) 0 else null
        return if (rest.all { startsWithWord(district, it) }) 2 else null
    }

    fun toWeatherPlace(place: LocalPlace, locale: Locale) =
        WeatherPlace(place.title, Locale("", "TR").getDisplayCountry(locale), place.lat, place.lon)
}
