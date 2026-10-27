package com.harrymeit.starsailtv.data

import com.harrymeit.starsailtv.SourceConfig
import com.harrymeit.starsailtv.api.MetadataApi
import com.harrymeit.starsailtv.data.cache.CatalogCodec
import com.harrymeit.starsailtv.data.model.*
import com.harrymeit.starsailtv.data.repository.CatalogQueries
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CatalogTest {
    private fun film(id: String, year: Int? = null, time: Long? = null, rating: Double? = null) = Media(id, "作品$id", year = year, updatedAt = time, doubanRating = rating, category = MediaCategory.MOVIE)
    @Test fun updatesAreNotReleaseYears() {
        val list = listOf(film("newRelease", 2026, 10), film("oldRelease", 1999, 30), film("missing", 2027))
        assertEquals(listOf("oldRelease", "newRelease", "missing"), CatalogQueries.sorted(list, CatalogFilter()).map { it.mediaId })
    }
    @Test fun ratingsSortNumericallyAndNullLast() {
        val list = listOf(film("missing"), film("low", rating = 8.9), film("high", rating = 9.3))
        assertEquals(listOf("high", "low", "missing"), CatalogQueries.sorted(list, CatalogFilter(sort = CatalogSort.RATING)).map { it.mediaId })
    }
    @Test fun fullSnapshotSortedBeforePagination() {
        val items = (1..60).map { film(it.toString(), time = it.toLong()) }
        val page = CatalogQueries.page(items, CatalogFilter(), 0, 24)
        assertEquals("60", page.items.first().mediaId); assertEquals(60, page.total); assertTrue(page.hasMore)
        assertEquals("36", CatalogQueries.page(items, CatalogFilter(), 1, 24).items.first().mediaId)
    }
    @Test fun remakeYearsStaySeparate() {
        val title = "同名片"
        val a = Media(Media.id(title, 1990, MediaCategory.MOVIE), title, year = 1990, category = MediaCategory.MOVIE)
        val b = a.copy(mediaId = Media.id(title, 2020, MediaCategory.MOVIE), year = 2020)
        assertEquals(2, CatalogQueries.merge(listOf(a, b)).size)
    }
    @Test fun unknownYearRatingNeverMatchesByTitleAlone() {
        val a = Media("a", "同名片", year = 2020)
        val b = Media("b", "同名片", doubanRating = 9.0)
        assertEquals(2, CatalogQueries.merge(listOf(a, b)).size)
    }
    @Test fun externalIdCanJoinVerifiedRatingToKnownYear() {
        val a = Media("a", "同名片", year = 2020, doubanId = "123")
        val b = Media("b", "同名片", doubanId = "123", doubanRating = 9.0, ratingSource = "豆瓣")
        val merged = CatalogQueries.merge(listOf(a, b)).single()
        assertEquals(2020, merged.year); assertEquals(9.0, merged.doubanRating!!, 0.0)
    }
    @Test fun matchingKnownYearMergesDifferentSourceIds() {
        val a = Media("a", "同名片", year = 2020, sourceRefs = listOf(SourceRef("A", "https://a", "1")))
        val b = a.copy(mediaId = "b", doubanId = "123", sourceRefs = listOf(SourceRef("B", "https://b", "9")))
        assertEquals(2, CatalogQueries.merge(listOf(a, b)).single().sourceRefs.size)
    }
    @Test fun stableTiesAreDeterministic() { assertEquals(listOf("a", "b"), CatalogQueries.sorted(listOf(film("b"), film("a")), CatalogFilter()).map { it.mediaId }) }
    @Test fun largeCacheMergesWithoutDroppingDistinctFilms() {
        val items = (1..6000).map { film(it.toString(), 2024) }
        assertEquals(6000, CatalogQueries.merge(items + items).size)
    }
    @Test fun areaAndGenreAndYearAllFilter() {
        val a = film("a", 2025).copy(region = "中国大陆", genres = listOf("科幻"))
        assertEquals(listOf(a), CatalogQueries.sorted(listOf(a, film("b", 2025)), CatalogFilter(region = "中国", genre = "科幻", year = "2025")))
    }
    @Test fun cmsScoreIsNotAssumedToBeDouban() {
        val raw = JSONObject("""{"vod_id":"1","vod_name":"作品","vod_year":"2024","vod_score":"9.9","type_name":"科幻片","vod_time":"2026-10-25 12:00:00"}""")
        val item = MetadataApi.parseCms(raw, SourceConfig("demo", "https://demo"))!!
        assertNull(item.doubanRating); assertNotNull(item.updatedAt); assertEquals(2024, item.year)
    }
    @Test fun genreCategoriesAreExplicit() { assertEquals(MediaCategory.ANIME, MetadataApi.category("国产动漫")); assertEquals(MediaCategory.SHOW, MetadataApi.category("大陆综艺")); assertEquals(MediaCategory.UNKNOWN, MetadataApi.category("未分类")) }
    @Test fun invalidDatesRemainUnknown() { assertNull(MetadataApi.parseTime("未知")); assertNull(MetadataApi.parseTime("2026-99-99")) }
    @Test fun timestampSecondsAndMillisAgree() { assertEquals(MetadataApi.parseTime("1700000000"), MetadataApi.parseTime("1700000000000")) }
    @Test fun cacheRoundTripPreservesNullsAndSourceRefs() {
        val m = film("x").copy(sourceRefs = listOf(SourceRef("A", "https://a", "1")))
        val snapshot = CatalogSnapshot(listOf(m), 1234, mapOf("https://a" to 2), listOf("offline"))
        assertEquals(snapshot, CatalogCodec.decode(CatalogCodec.encode(snapshot)))
    }
}
