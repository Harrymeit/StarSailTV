package com.harrymeit.starsailtv.data.cache

import com.harrymeit.starsailtv.data.model.*
import org.json.JSONArray
import org.json.JSONObject

object CatalogCodec {
    fun media(m: Media): JSONObject = JSONObject().apply {
        put("id", m.mediaId); put("title", m.title); put("aliases", JSONArray(m.aliases))
        put("pinyin", m.fullPinyin); put("initials", m.pinyinInitials); put("year", m.year)
        put("category", m.category.name); put("genres", JSONArray(m.genres)); put("region", m.region)
        put("updatedAt", m.updatedAt); put("rating", m.doubanRating); put("doubanId", m.doubanId)
        put("popularity", m.popularityScore); put("poster", m.posterUrl); put("remarks", m.remarks)
        put("description", m.description); put("ratingSource", m.ratingSource)
        put("refs", JSONArray().apply { m.sourceRefs.forEach { put(JSONObject().put("name", it.name).put("url", it.url).put("id", it.vodId)) } })
    }
    fun parse(o: JSONObject): Media = Media(
        mediaId = o.getString("id"), title = o.getString("title"), aliases = strings(o.optJSONArray("aliases")),
        fullPinyin = o.optString("pinyin"), pinyinInitials = o.optString("initials"), year = if (o.has("year") && !o.isNull("year")) o.optInt("year") else null,
        category = runCatching { MediaCategory.valueOf(o.optString("category")) }.getOrDefault(MediaCategory.UNKNOWN),
        genres = strings(o.optJSONArray("genres")), region = o.optString("region"),
        updatedAt = if (o.has("updatedAt") && !o.isNull("updatedAt")) o.optLong("updatedAt") else null,
        doubanRating = o.optDouble("rating").takeIf { it.isFinite() && it > 0 && it <= 10 }, doubanId = o.optString("doubanId").takeIf { it.isNotEmpty() && it != "null" },
        popularityScore = o.optDouble("popularity").takeIf(Double::isFinite), posterUrl = o.optString("poster"), remarks = o.optString("remarks"), description = o.optString("description"),
        ratingSource = o.optString("ratingSource").takeIf { it.isNotEmpty() && it != "null" },
        sourceRefs = (o.optJSONArray("refs") ?: JSONArray()).let { a -> (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { SourceRef(it.optString("name"), it.optString("url"), it.optString("id")) } } },
    )
    private fun strings(a: JSONArray?): List<String> = if (a == null) emptyList() else (0 until a.length()).map { a.optString(it) }
    fun encode(snapshot: CatalogSnapshot): String = JSONObject().apply {
        put("version", 1); put("fetchedAt", snapshot.fetchedAt); put("pages", JSONObject(snapshot.sourcePages))
        put("items", JSONArray().apply { snapshot.items.forEach { put(media(it)) } }); put("warnings", JSONArray(snapshot.warnings))
    }.toString()
    fun decode(raw: String): CatalogSnapshot {
        val o = JSONObject(raw); require(o.optInt("version") == 1)
        val list = o.optJSONArray("items") ?: JSONArray(); val pages = o.optJSONObject("pages") ?: JSONObject()
        return CatalogSnapshot((0 until list.length()).mapNotNull { i -> runCatching { parse(list.getJSONObject(i)) }.getOrNull() }, o.optLong("fetchedAt"), pages.keys().asSequence().associateWith { pages.optInt(it) }, strings(o.optJSONArray("warnings")))
    }
}
