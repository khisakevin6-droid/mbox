package com.mbox.stream

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import com.mbox.stream.data.AssetCatalogRepository
import com.mbox.stream.data.RemoteCatalogRepository
import com.mbox.stream.data.TmdbCatalogRepository
import com.mbox.stream.data.db.CustomSource
import com.mbox.stream.data.db.ProgressEntry
import com.mbox.stream.data.db.UserListEntry
import com.mbox.stream.domain.CatalogRepository
import com.mbox.stream.domain.Episode
import com.mbox.stream.domain.Title
import com.mbox.stream.settings.Prefs
import com.mbox.stream.sources.CatalogSourceProvider
import com.mbox.stream.sources.HttpJsonSourceProvider
import com.mbox.stream.sources.ProviderResult
import com.mbox.stream.sources.SourceProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

sealed interface CatalogState {
    object Loading : CatalogState
    data class Error(val message: String) : CatalogState
    data class Ready(val titles: List<Title>) : CatalogState
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val mbox = app as MboxApplication
    private val dao = mbox.db.userDao()
    private val store = mbox.settings

    val prefs: StateFlow<Prefs> = store.prefs.stateIn(viewModelScope, SharingStarted.Eagerly, Prefs())
    val entries: StateFlow<List<UserListEntry>> =
        dao.observeLists().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val progress: StateFlow<List<ProgressEntry>> =
        dao.observeProgress().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val customSources: StateFlow<List<CustomSource>> =
        dao.observeCustomSources().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _catalog = MutableStateFlow<CatalogState>(CatalogState.Loading)
    val catalog: StateFlow<CatalogState> = _catalog.asStateFlow()

    private val _genre = MutableStateFlow<String?>(null)
    val selectedGenre: StateFlow<String?> = _genre.asStateFlow()

    private val reloadTick = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            combine(
                store.prefs.map { Triple(it.catalogMode, it.catalogUrl, it.tmdbKey) }.distinctUntilChanged(),
                reloadTick
            ) { cfg, _ -> cfg }.collectLatest { (mode, url, key) -> loadCatalog(mode, url, key) }
        }
    }

    private suspend fun loadCatalog(mode: String, url: String, key: String) {
        _catalog.value = CatalogState.Loading
        val repo: CatalogRepository = when (mode) {
            "remote" -> RemoteCatalogRepository(url)
            "tmdb" -> TmdbCatalogRepository(key)
            else -> AssetCatalogRepository(mbox)
        }
        val result = withTimeoutOrNull(25_000) { repo.load() }
        _catalog.value = when {
            result == null -> CatalogState.Error("Timed out loading ${repo.name}. Check your connection and try again.")
            result.isSuccess -> CatalogState.Ready(result.getOrThrow())
            else -> CatalogState.Error(friendly(result.exceptionOrNull()))
        }
    }

    private fun friendly(e: Throwable?): String = when (e) {
        null -> "Unknown error."
        is UnknownHostException -> "No internet connection or the server could not be found."
        is SocketTimeoutException -> "The server took too long to respond."
        is JSONException -> "The catalogue is not valid: ${e.message}"
        else -> e.message ?: e.javaClass.simpleName
    }

    fun reload() { reloadTick.value = reloadTick.value + 1 }

    fun setGenre(genre: String?) { _genre.value = genre }

    fun toggleList(titleId: String, kind: String) {
        viewModelScope.launch {
            val exists = entries.value.any { it.titleId == titleId && it.kind == kind }
            if (exists) dao.removeFromList(titleId, kind)
            else dao.addToList(UserListEntry(titleId, kind, System.currentTimeMillis()))
        }
    }

    fun addCustomSource(name: String, uri: String, isLocal: Boolean) {
        viewModelScope.launch { dao.addCustomSource(CustomSource(name = name, uri = uri, isLocal = isLocal)) }
    }

    fun deleteCustomSource(source: CustomSource) {
        viewModelScope.launch { dao.deleteCustomSource(source) }
    }

    fun clearHistory() { viewModelScope.launch { dao.clearProgress() } }

    fun clearCache() {
        val ctx = getApplication<Application>()
        ctx.imageLoader.diskCache?.clear()
        ctx.imageLoader.memoryCache?.clear()
        ctx.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    suspend fun loadSources(title: Title, episode: Episode?): List<ProviderResult> {
        val p = prefs.value
        val providers = buildList<SourceProvider> {
            add(CatalogSourceProvider())
            if (p.sourceApiTemplate.isNotBlank()) add(HttpJsonSourceProvider(p.sourceApiTemplate))
        }
        return providers.map { ProviderResult(it.displayName, it.sourcesFor(title, episode)) }
    }

    fun setTheme(v: String) { viewModelScope.launch { store.setTheme(v) } }
    fun setSpeed(v: Float) { viewModelScope.launch { store.setSpeed(v) } }
    fun setSubLang(v: String) { viewModelScope.launch { store.setSubLang(v) } }
    fun setMagnetEnabled(v: Boolean) { viewModelScope.launch { store.setMagnetEnabled(v) } }
    fun setCatalogMode(v: String) { viewModelScope.launch { store.setCatalogMode(v) } }
    fun saveConnections(url: String, tmdbKey: String, sourceApi: String) {
        viewModelScope.launch { store.saveConnections(url, tmdbKey, sourceApi) }
    }
}
