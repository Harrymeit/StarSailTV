package com.harrymeit.starsailtv.api

import com.harrymeit.starsailtv.Api
import com.harrymeit.starsailtv.data.cache.CatalogCodec
import com.harrymeit.starsailtv.data.model.*
import com.harrymeit.starsailtv.data.repository.CatalogQueries
import com.harrymeit.starsailtv.search.PinyinSearchIndex
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Explicit opt-in; the production password is only read from the test process environment. */
class LiveContractTest {
    @Test fun deployedServerCatalogSearchDetailsAndHlsAgree() {
        val password = System.getenv("STARSAIL_TEST_PASSWORD").orEmpty()
        assumeTrue("Set STARSAIL_TEST_PASSWORD for the live contract check", password.isNotEmpty())
        val api = Api("https://libretv-topaz-three.vercel.app")
        val status = api.login(password)
        assertTrue(status.verified); assertTrue(status.defaultSources.isNotEmpty())
        val sources = status.defaultSources.filterNot { it.isAdult }
        val search = api.search("流浪地球2", sources)
        assertTrue(search.items.isNotEmpty())
        val sample = search.items.first { it.name == "流浪地球2" && it.sourceName == "如意资源" }
        val source = sources.first { it.url == sample.sourceUrl }
        val detail = api.detail(sample.vodId, source)
        assertTrue(detail.episodes.isNotEmpty())
        val playlist = api.fetchText(detail.episodes.first())
        assertTrue(playlist.trimStart().startsWith("#EXTM3U"))
        val metadata = MetadataApi(api)
        val page = metadata.catalog(source, 1)
        assertTrue(page.items.isNotEmpty())
        val ranked = metadata.ranking("douban_movie_weekly", MediaCategory.MOVIE)
        assertTrue(ranked.isNotEmpty()); assertTrue(ranked.any { it.doubanRating != null && it.ratingSource != null })
        val cached = CatalogQueries.merge(page.items + ranked)
        assertEquals(cached.size, CatalogCodec.decode(CatalogCodec.encode(CatalogSnapshot(cached))).items.size)
        File("../dist").mkdirs(); File("../dist/live-catalog.json").writeText(CatalogCodec.encode(CatalogSnapshot(cached, System.currentTimeMillis())))
        File("../dist/live-contract.json").writeText(JSONObject().put("server", api.baseUrl).put("verified", status.verified).put("sources", sources.size).put("searchResults", search.items.size).put("catalogItems", page.items.size).put("rankedItems", ranked.size).put("episodes", detail.episodes.size).put("hlsAccessible", true).toString(2))
    }
}
