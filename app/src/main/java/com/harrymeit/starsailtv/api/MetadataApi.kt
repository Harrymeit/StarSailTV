package com.harrymeit.starsailtv.api

import com.harrymeit.starsailtv.Api
import com.harrymeit.starsailtv.SourceConfig
import com.harrymeit.starsailtv.data.model.*
import com.harrymeit.starsailtv.search.Pinyin
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class SourcePage(val items: List<Media>, val pageCount: Int)

class MetadataApi(private val api: Api) {
    fun catalog(source: SourceConfig, page: Int): SourcePage {
        val separator = if ('?' in source.url) "&" else "?"
        val root = JSONObject(api.fetchText(source.url + separator + "ac=videolist&pg=$page"))
        val classes = root.optJSONArray("class") ?: JSONArray()
        val types = (0 until classes.length()).mapNotNull { i -> classes.optJSONObject(i)?.let { it.optString("type_id") to it.optString("type_name") } }.toMap()
        val list = root.optJSONArray("list") ?: JSONArray()
        return SourcePage((0 until list.length()).mapNotNull { i -> list.optJSONObject(i)?.let { parseCms(it, source, types) } }, root.optInt("pagecount", 1))
    }

    fun ranking(id: String, category: MediaCategory): List<Media> {
        val root = api.metadataJson("/api/hot-list?id=" + URLEncoder.encode(id, "UTF-8"))
        val list = root.optJSONArray("items") ?: JSONArray()
        return (0 until list.length()).mapNotNull { i ->
            val item = list.optJSONObject(i) ?: return@mapNotNull null
            val title = item.optString("title").trim()
            if (title.isEmpty()) return@mapNotNull null
            val year = item.optString("year").toIntOrNull()
            val rating = item.optString("rating").toDoubleOrNull()?.takeIf { it > 0 && it <= 10 }
            Pinyin.enrich(Media(
                mediaId = Media.id(title, year, category), title = title, year = year, category = category,
                doubanId = item.optString("id").takeIf { id.startsWith("douban_") && it.all(Char::isDigit) && it.isNotEmpty() },
                doubanRating = rating?.takeIf { id.startsWith("douban_") },
                ratingSource = if (rating != null && id.startsWith("douban_")) "豆瓣（经影视周榜）" else null,
                popularityScore = 1000.0 / (i + 1), posterUrl = item.optString("cover"),
            ))
        }
    }

    companion object {
        fun category(type: String): MediaCategory = when {
            "动漫" in type || "动画" in type -> MediaCategory.ANIME
            "综艺" in type -> MediaCategory.SHOW
            listOf("剧", "电视", "日韩", "欧美", "国产", "港台").any { it in type } -> MediaCategory.TV
            listOf("片", "电影").any { it in type } -> MediaCategory.MOVIE
            else -> MediaCategory.UNKNOWN
        }

        fun parseTime(raw: String): Long? {
            if (raw.isBlank() || raw == "0") return null
            raw.toLongOrNull()?.let { return if (it < 100_000_000_000L) it * 1000 else it }
            for (pattern in listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd")) {
                val value = runCatching { SimpleDateFormat(pattern, Locale.ROOT).apply { isLenient = false; timeZone = TimeZone.getTimeZone("Asia/Shanghai") }.parse(raw)?.time }.getOrNull()
                if (value != null) return value
            }
            return null
        }

        fun parseCms(o: JSONObject, source: SourceConfig, types: Map<String, String> = emptyMap()): Media? {
            val title = o.optString("vod_name").replace(Regex("<[^>]*>"), "").trim()
            val id = o.optString("vod_id")
            if (title.isEmpty() || id.isEmpty()) return null
            val type = o.optString("type_name").ifEmpty { types[o.optString("type_id")].orEmpty() }
            if (listOf("伦理", "成人", "福利", "解说", "预告").any { it in type || it in title }) return null
            val cat = category(type)
            val year = Regex("(?:19|20)\\d{2}").find(o.optString("vod_year"))?.value?.toIntOrNull()
            return Pinyin.enrich(Media(
                mediaId = Media.id(title, year, cat), title = title, year = year, category = cat,
                aliases = o.optString("vod_sub").split('/', ',', '，').map(String::trim).filter { it.isNotEmpty() },
                genres = (o.optString("vod_class").split(',', '/', '，') + type).map(String::trim).filter { it.isNotEmpty() }.distinct(),
                region = o.optString("vod_area"), updatedAt = parseTime(o.optString("vod_time")),
                doubanId = o.optString("vod_douban_id").takeIf { it.toLongOrNull()?.let { n -> n > 0 } == true },
                posterUrl = o.optString("vod_pic"), remarks = o.optString("vod_remarks"),
                description = o.optString("vod_content").replace(Regex("<[^>]*>|&nbsp;"), " ").take(1000),
                sourceRefs = listOf(SourceRef(source.name, source.url, id)),
            ))
        }
    }
}
