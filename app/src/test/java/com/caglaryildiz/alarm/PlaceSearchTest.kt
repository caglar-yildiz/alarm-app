package com.caglaryildiz.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PlaceSearchTest {
    private val places: List<LocalPlace> by lazy {
        val file = listOf("src/main/assets/places_tr.tsv", "app/src/main/assets/places_tr.tsv")
            .map(::File).first { it.exists() }
        file.readLines().filter { it.isNotBlank() && !it.startsWith("#") }.mapNotNull(PlaceSearch::parseLine)
    }

    private fun titles(query: String, limit: Int = 30) = PlaceSearch.search(places, query, limit).map { it.title }

    @Test fun bundledListHasEveryProvinceAndDistrict() {
        assertEquals(81, places.count { it.isProvince })
        assertEquals(39, places.count { it.province == "İstanbul" && !it.isProvince })
        assertEquals(25, places.count { it.province == "Ankara" && !it.isProvince })
    }

    @Test fun partialProvinceListsTheProvinceThenItsDistricts() {
        val results = titles("istan")
        assertEquals("İstanbul", results.first())
        assertTrue(results.drop(1).all { it.startsWith("İstanbul / ") })
        assertTrue("İstanbul / Esenyurt" in results.take(5))
    }

    @Test fun turkishLettersAndCaseDoNotMatter() {
        for (query in listOf("izmir", "İZMİR", "ızmır", "Izmir")) assertEquals("İzmir", titles(query).first())
        assertEquals("Şanlıurfa", titles("sanliurfa").first())
    }

    @Test fun districtNamesAreFoundDirectly() {
        assertEquals("İstanbul / Kadıköy", titles("kadıköy").first())
        val esen = titles("esen", limit = 5)
        assertTrue("İstanbul / Esenyurt" in esen)
        assertTrue("İstanbul / Esenler" in esen)
    }

    @Test fun provinceAndDistrictTogetherNarrowTheResult() {
        assertEquals("İstanbul / Esenyurt", titles("istanbul esen").first())
        assertEquals("İstanbul / Esenyurt", titles("İstanbul/Esenyurt").first())
        assertEquals("Ankara / Çankaya", titles("ankara çank").first())
    }

    @Test fun nothingMatchesNonsenseOrEmptyText() {
        assertTrue(titles("qqqq").isEmpty())
        assertTrue(titles("   ").isEmpty())
    }
}
