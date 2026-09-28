package com.fitcoach.app.data.club

import android.content.Context
import com.fitcoach.app.brand.BrandConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClubRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    baseClient: OkHttpClient
) : ClubRepository {

    private val client = baseClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val state = MutableStateFlow<ClubContent?>(null)
    private val loadMutex = Mutex()
    private var remoteRefreshStarted = false

    private val cacheFile: File get() = File(context.filesDir, CACHE_FILE)
    private val prefs get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun observe(): Flow<ClubContent> =
        state.filterNotNull().onStart { ensureLoaded() }

    override suspend fun current(): ClubContent {
        ensureLoaded()
        return state.value ?: ClubContent.EMPTY
    }

    override suspend fun refresh(): List<Promo> {
        ensureLoaded()
        fetchRemote()
        val content = state.value ?: ClubContent.EMPTY
        val seen: Set<String>? = prefs.getStringSet(KEY_SEEN, null)?.toSet()
        val fresh = PromoDiff.newPromos(content, seen)
        val allIds = content.promos.map { it.id }.toSet()
        prefs.edit().putStringSet(KEY_SEEN, (seen.orEmpty() + allIds)).apply()
        return fresh
    }

    /** Загружает assets и кэш один раз; затем один раз за процесс запускает обновление с сервера. */
    private suspend fun ensureLoaded() {
        if (state.value != null) return
        loadMutex.withLock {
            if (state.value != null) return
            val content = withContext(Dispatchers.IO) { loadCache() ?: loadAssets() }
            state.value = content
        }
        if (!remoteRefreshStarted && BrandConfig.clubDataUrl.isNotBlank()) {
            remoteRefreshStarted = true
            scope.launch { fetchRemote() }
            ClubNewsWorker.schedule(context)
        }
    }

    private fun loadAssets(): ClubContent = runCatching {
        context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
    }.mapCatching { ClubJsonParser.parse(it).getOrThrow() }.getOrDefault(ClubContent.EMPTY)

    private fun loadCache(): ClubContent? {
        if (BrandConfig.clubDataUrl.isBlank()) return null // без сервера кэш не используется
        val file = cacheFile
        if (!file.exists()) return null
        return runCatching { ClubJsonParser.parse(file.readText()).getOrThrow() }.getOrNull()
    }

    /** GET clubDataUrl → парсинг → кэш → state. Ошибки сети игнорируются (остаётся старый контент). */
    private suspend fun fetchRemote() {
        val url = BrandConfig.clubDataUrl
        if (url.isBlank()) return
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url(url).header("Accept", "application/json").build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val body = response.body?.string() ?: return@use
                    val parsed = ClubJsonParser.parse(body).getOrNull() ?: return@use
                    cacheFile.writeText(body)
                    state.value = parsed
                }
            }
        }
    }

    companion object {
        const val ASSET_PATH = "club/club.json"
        const val CACHE_FILE = "club_cache.json"
        private const val PREFS = "club_news"
        private const val KEY_SEEN = "seen_promo_ids"
    }
}
