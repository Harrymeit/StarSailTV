package com.harrymeit.starsailtv.data.repository

import android.content.Context
import com.harrymeit.starsailtv.*
import com.harrymeit.starsailtv.api.MetadataApi
import com.harrymeit.starsailtv.data.cache.CatalogCodec
import com.harrymeit.starsailtv.data.model.*
import com.harrymeit.starsailtv.search.Pinyin
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import java.io.File
import java.security.MessageDigest

class MetadataRepository(context: Context, private val api: () -> Api, private val sources: () -> List<SourceConfig>) {
    private val directory = File(context.filesDir, "catalog").apply { mkdirs() }
    private val mutex = Mutex()
    private var identity = ""
    private var snapshot = CatalogSnapshot(emptyList())
    private var favorites = emptyList<Media>()
    private fun key(): String = MessageDigest.getInstance("SHA-256").digest((api().baseUrl + sources().filter { it.enabled && !it.isAdult }.map { it.url }.sorted().joinToString()).toByteArray()).joinToString("") { "%02x".format(it) }.take(20)
    private fun initialize() {
        val id = key()
        if (id == identity) return
        identity = id
        snapshot = runCatching { CatalogCodec.decode(File(directory, "$id.json").readText()) }.getOrDefault(CatalogSnapshot(emptyList()))
        favorites = runCatching { val a = JSONArray(File(directory, "$id-favorites.json").readText()); (0 until a.length()).map { CatalogCodec.parse(a.getJSONObject(it)) } }.getOrDefault(emptyList())
    }
    fun cached(): CatalogSnapshot { initialize(); return snapshot }
    fun favorites(): List<Media> { initialize(); return favorites }
    fun isFavorite(m: Media) = favorites().any { it.mediaId == m.mediaId }
    fun toggleFavorite(m: Media): Boolean {
        initialize(); val add = !isFavorite(m)
        favorites = if (add) (listOf(m) + favorites).take(200) else favorites.filterNot { it.mediaId == m.mediaId }
        File(directory, "$identity-favorites.json").writeText(JSONArray().apply { favorites.forEach { put(CatalogCodec.media(it)) } }.toString())
        return add
    }
    fun ingestSearch(items: List<SearchItem>) {
        initialize()
        val incoming = items.map { s ->
            val year = s.year.toIntOrNull(); val cat = MetadataApi.category(s.typeName)
            Pinyin.enrich(Media(Media.id(s.name, year, cat), s.name, year = year, category = cat, posterUrl = s.pic, region = s.area, remarks = s.remarks, sourceRefs = listOf(SourceRef(s.sourceName, s.sourceUrl, s.vodId))))
        }
        snapshot = snapshot.copy(items = CatalogQueries.merge(snapshot.items + incoming).takeLast(6000))
        persist()
    }
    private fun persist() {
        val file = File(directory, "$identity.json"); val temp = File(directory, "$identity.tmp")
        temp.writeText(CatalogCodec.encode(snapshot)); if (!temp.renameTo(file)) { file.writeText(temp.readText()); temp.delete() }
    }
    suspend fun refresh(more: Boolean = false): CatalogSnapshot = mutex.withLock {
        initialize()
        val id = identity
        val old = snapshot
        val service = MetadataApi(api())
        val active = sources().filter { it.enabled && !it.isAdult && (!more || old.sourcePages[it.url] != Int.MAX_VALUE) }
        val semaphore = Semaphore(3)
        val fetched = coroutineScope {
            active.map { source -> async(Dispatchers.IO) {
                semaphore.withPermit {
                    val page = if (more) (old.sourcePages[source.url] ?: 0) + 1 else 1
                    try { val data = runInterruptible { service.catalog(source, page) }; Triple(source.url, if (page >= data.pageCount) Int.MAX_VALUE else page, data.items) }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { Triple(source.url, -page, emptyList<Media>()) }
                }
            } }.awaitAll()
        }
        val warnings = fetched.filter { it.second < 0 }.map { (url, _, _) -> "${active.firstOrNull { it.url == url }?.name ?: "片源"}目录暂不可用" }.toMutableList()
        val ranked = if (!more) coroutineScope {
            listOf("douban_movie_weekly" to MediaCategory.MOVIE, "douban_tv_chinese" to MediaCategory.TV, "douban_tv_global" to MediaCategory.TV, "douban_show_chinese" to MediaCategory.SHOW).map { (rank, cat) -> async(Dispatchers.IO) {
                try { runInterruptible { service.ranking(rank, cat) } }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { emptyList<Media>() }
            } }.awaitAll().flatten()
        } else emptyList()
        currentCoroutineContext().ensureActive()
        if (key() != id) return@withLock cached()
        if (ranked.isEmpty() && !more) warnings.add("榜单暂不可用，保留本地推荐")
        if (more && active.isEmpty()) warnings.add("当前启用源的目录已加载到末页")
        val merged = CatalogQueries.merge(snapshot.items + fetched.flatMap { it.third } + ranked).takeLast(6000)
        val stamp = if (fetched.any { it.second > 0 } || ranked.isNotEmpty()) System.currentTimeMillis() else old.fetchedAt
        snapshot = CatalogSnapshot(merged, stamp, old.sourcePages + fetched.filter { it.second > 0 }.associate { it.first to it.second }, warnings)
        withContext(Dispatchers.IO) { persist() }
        snapshot
    }
}
