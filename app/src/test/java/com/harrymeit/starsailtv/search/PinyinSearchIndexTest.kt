package com.harrymeit.starsailtv.search

import com.harrymeit.starsailtv.data.model.*
import org.junit.Assert.*
import org.junit.Test

class PinyinSearchIndexTest {
    private val earth = Media("earth", "流浪地球", aliases = listOf("The Wandering Earth"), year = 2019)
    private val sequel = Media("sequel", "流浪地球2", year = 2023)
    private val index = PinyinSearchIndex(listOf(sequel, earth, Media("space", "星际穿越", aliases = listOf("Interstellar"))))
    @Test fun initialsResolveToRealTitle() { assertEquals("流浪地球", index.suggest("LLDQ").first().title) }
    @Test fun fullPinyinMatches() { assertEquals("流浪地球", index.suggest("liulangdiqiu").first().title) }
    @Test fun englishAliasMatches() { assertEquals("星际穿越", index.suggest("interstellar").first().title) }
    @Test fun chinesePartialMatches() { assertEquals(2, index.suggest("流浪").size) }
    @Test fun exactTitlePrecedesLongerTitles() { assertEquals("earth", index.suggest("流浪地球").first().mediaId) }
    @Test fun emptyQueryDoesNotDumpCatalog() { assertTrue(index.suggest("   ").isEmpty()) }
    @Test fun missingCatalogDoesNotInventTitles() { assertTrue(PinyinSearchIndex(emptyList()).suggest("LLDQ").isEmpty()) }
    @Test fun punctuationAndFullWidthCharactersNormalize() { assertEquals("earth", index.suggest("ＬＬＤＱ").first().mediaId) }
    @Test fun numericSuffixDistinguishesSequel() { assertEquals("sequel", index.suggest("LLDQ2").first().mediaId) }
}
