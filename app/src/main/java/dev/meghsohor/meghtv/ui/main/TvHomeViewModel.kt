package dev.meghsohor.meghtv.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.meghsohor.meghtv.data.MeghTVRepository
import dev.meghsohor.meghtv.data.RefreshProgress
import dev.meghsohor.meghtv.data.RefreshResult
import dev.meghsohor.meghtv.data.db.CategoryEntity
import dev.meghsohor.meghtv.data.db.ChannelEntity
import dev.meghsohor.meghtv.data.db.CountryEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningReduce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface RefreshStatus {
  data class Running(val progress: RefreshProgress) : RefreshStatus

  data class Done(val result: RefreshResult) : RefreshStatus

  data class Failed(val reason: String?) : RefreshStatus
}

data class TvHomeUiState(
  val panel: PanelState = PanelState.CategoriesMenu,
  /** False until launch has picked the opening view; [panel] may still change before that. */
  val startupPanelChosen: Boolean = false,
  val categories: List<CategoryEntity> = emptyList(),
  val countries: List<CountryEntity> = emptyList(),
  /** Null while the current [panel]'s list is still loading. */
  val listChannels: List<ChannelEntity>? = null,
  val bookmarkedIds: Set<String> = emptySet(),
  val failedIds: Set<String> = emptySet(),
  val currentChannel: ChannelEntity? = null,
  val currentStreamUrls: List<String> = emptyList(),
  val searchQuery: String = "",
  /** Null when no refresh dialog is up. */
  val refresh: RefreshStatus? = null,
)

class TvHomeViewModel(private val repository: MeghTVRepository) : ViewModel() {

  private val panel = MutableStateFlow<PanelState>(PanelState.CategoriesMenu)
  private val currentChannelId = MutableStateFlow<String?>(null)

  /** The list [currentChannelId] was picked from, for Channel Up/Down. */
  private val currentPlaybackList = MutableStateFlow<List<String>>(emptyList())
  private val searchQuery = MutableStateFlow("")
  private val refreshStatus = MutableStateFlow<RefreshStatus?>(null)
  private var startedInitialSelection = false

  private data class LoadedList(val panel: PanelState, val channels: List<ChannelEntity>)

  private val listChannels =
    panel.flatMapLatest { state ->
      val source = (state as? PanelState.ChannelList)?.source ?: return@flatMapLatest flowOf(LoadedList(state, emptyList()))
      when (source) {
        ChannelListSource.Favourites -> repository.bookmarkedChannels
        ChannelListSource.AllChannels -> repository.allChannels
        is ChannelListSource.Category -> repository.channelsByCategory(source.id)
        is ChannelListSource.Country -> repository.channelsByCountry(source.code)
        // Each query is a LIKE scan over ~11k rows.
        ChannelListSource.Search ->
          searchQuery.debounce(SearchDebounceMs).flatMapLatest { q -> if (q.isBlank()) flowOf(emptyList()) else repository.search(q) }
      }.map { LoadedList(state, it) }
    }

  private val bookmarkedIds = repository.bookmarkedChannels.map { list -> list.map { it.id }.toSet() }
  private val failedIds =
    repository.failedChannelIds.map { it.toSet() }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

  private data class BrowseState(
    val panel: PanelState,
    val startupPanelChosen: Boolean,
    val categories: List<CategoryEntity>,
    val countries: List<CountryEntity>,
    val listChannels: List<ChannelEntity>?,
    val bookmarkedIds: Set<String>,
    val failedIds: Set<String>,
  )

  /** [requestedId] tells "nothing picked" (null) apart from "picked, but its row is gone for now". */
  private data class PlayerState(val requestedId: String?, val currentChannel: ChannelEntity?, val currentStreamUrls: List<String>)

  private val startupPanelChosen = MutableStateFlow(false)

  private val browseState =
    combine(
      combine(panel, startupPanelChosen, ::Pair),
      repository.categories,
      repository.countries,
      listChannels,
      combine(bookmarkedIds, failedIds, ::Pair),
    ) { (p, chosen), cats, countries, loaded, (bm, failed) ->
      // Right after a panel change the latest list is still the previous panel's: report "loading".
      BrowseState(p, chosen, cats, countries, loaded.channels.takeIf { loaded.panel == p }, bm, failed)
    }

  private val playerState =
    currentChannelId
      .flatMapLatest { id ->
        if (id == null) {
          flowOf(PlayerState(null, null, emptyList()))
        } else {
          combine(repository.channelById(id), repository.streamUrls(id)) { channel, urls ->
            if (channel == null) PlayerState(id, null, emptyList())
            else PlayerState(id, channel, orderedByPreference(urls.map { it.url }, channel.selectedSourceUrl))
          }
        }
      }
      // A refresh can delete the playing channel's row: keep playing it rather than drop to the banner.
      .runningReduce { previous, new -> if (new.requestedId != null && new.currentChannel == null) previous else new }
      // Eagerly: uiState stops collecting in the background, and a restart would lose the last good state.
      .stateIn(viewModelScope, SharingStarted.Eagerly, PlayerState(null, null, emptyList()))

  val uiState =
    combine(browseState, playerState, searchQuery, refreshStatus) { browse, player, query, refresh ->
        TvHomeUiState(
          panel = browse.panel,
          startupPanelChosen = browse.startupPanelChosen,
          categories = browse.categories,
          countries = browse.countries,
          listChannels = browse.listChannels,
          bookmarkedIds = browse.bookmarkedIds,
          failedIds = browse.failedIds,
          currentChannel = player.currentChannel,
          currentStreamUrls = player.currentStreamUrls,
          searchQuery = query,
          refresh = refresh,
        )
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TvHomeUiState())

  init {
    viewModelScope.launch {
      if (startedInitialSelection) return@launch
      startedInitialSelection = true
      if (repository.bookmarkedChannels.first().isNotEmpty()) panel.value = PanelState.ChannelList(ChannelListSource.Favourites)
      // The first refresh starts before startup is reported done, so nothing waiting on startup sees a gap between them.
      val empty = !repository.hasChannels()
      if (empty) onRefresh()
      startupPanelChosen.value = true
      if (!empty) repository.pruneEmptyMenus()
    }
  }

  /** Deleted since the last refresh; a list can still show them until the async write lands. */
  private val deletedIds = mutableSetOf<String>()

  /** Where a deleted playing channel sat in [currentPlaybackList], so Channel Up/Down carry on from there. */
  private var zapGap: Int? = null

  /** [shownIds] is the list as displayed, so Channel Up/Down stay inside an in-list search's results. */
  fun onSelectChannel(channelId: String, shownIds: List<String>) {
    if (channelId in deletedIds) return
    zapGap = null
    currentChannelId.value = channelId
    currentPlaybackList.value = shownIds.filterNot { it in deletedIds }
  }

  fun onChannelUp() = stepChannel(1)

  fun onChannelDown() = stepChannel(-1)

  private fun stepChannel(delta: Int) {
    val list = currentPlaybackList.value
    if (list.isEmpty()) return
    val index = list.indexOf(currentChannelId.value)
    val gap = zapGap
    val next =
      when {
        index != -1 -> index + delta
        gap != null -> if (delta > 0) gap else gap - 1 // the deleted channel's neighbours
        else -> return
      }
    zapGap = null
    currentChannelId.value = list[next.mod(list.size)]
  }

  fun onSelectCategoryRow(category: CategoryEntity) {
    panel.value = PanelState.ChannelList(ChannelListSource.Category(category.id, category.name))
  }

  fun onSelectAllChannelsRow() {
    panel.value = PanelState.ChannelList(ChannelListSource.AllChannels)
  }

  fun onSelectCountriesRow() {
    panel.value = PanelState.CountriesMenu
  }

  fun onSelectCountryRow(country: CountryEntity) {
    panel.value = PanelState.ChannelList(ChannelListSource.Country(country.code, country.name))
  }

  fun onPinnedFavourites() {
    panel.value = PanelState.ChannelList(ChannelListSource.Favourites)
  }

  fun onPinnedSearch() {
    searchQuery.value = ""
    panel.value = PanelState.ChannelList(ChannelListSource.Search)
  }

  fun onPinnedCategories() {
    panel.value = PanelState.CategoriesMenu
  }

  fun onSearchQueryChange(query: String) {
    searchQuery.value = query
  }

  fun onBack() {
    panel.value = panel.value.backTarget()
  }

  fun onToggleBookmark(channel: ChannelEntity) {
    viewModelScope.launch {
      if (channel.id in bookmarkedIds.first()) repository.removeBookmark(channel.id) else repository.addBookmark(channel.id)
    }
  }

  fun onPlaybackFailed(channelId: String) {
    viewModelScope.launch { repository.markFailed(channelId) }
  }

  // Unconditional: failedIds can lag a mark still being written, and Room runs writes in order.
  fun onPlaybackWorked(channelId: String) {
    viewModelScope.launch { repository.clearFailed(channelId) }
  }

  fun onDeleteChannel(channelId: String) {
    deletedIds += channelId
    if (currentChannelId.value == channelId) {
      zapGap = currentPlaybackList.value.indexOf(channelId).takeIf { it >= 0 }
      currentChannelId.value = null
    }
    currentPlaybackList.value -= channelId
    viewModelScope.launch { repository.deleteChannel(channelId) }
  }

  fun onRefresh() {
    if (refreshStatus.value is RefreshStatus.Running) return
    refreshStatus.value = RefreshStatus.Running(RefreshProgress.ChannelInfo)
    viewModelScope.launch {
      val result = runCatching { repository.refresh(::onRefreshProgress) }
      refreshStatus.value = result.fold(onSuccess = { RefreshStatus.Done(it) }, onFailure = { RefreshStatus.Failed(it.message) })
      if (result.isSuccess) {
        deletedIds.clear()
        // Drop removed channels from the zap list, except the playing one: Up/Down step from it.
        val validIds = repository.allChannelIds().toSet()
        val playing = currentChannelId.value
        currentPlaybackList.value = currentPlaybackList.value.filter { it in validIds || it == playing }
      }
    }
  }

  // Playlist callbacks arrive from several threads, so out of order: never step the count back.
  private fun onRefreshProgress(progress: RefreshProgress) {
    refreshStatus.update { current ->
      val shown = (current as? RefreshStatus.Running)?.progress
      if (current !is RefreshStatus.Running) current
      else if (shown is RefreshProgress.Playlists && progress is RefreshProgress.Playlists && shown.done > progress.done) current
      else RefreshStatus.Running(progress)
    }
  }

  fun onDismissRefreshResult() {
    if (refreshStatus.value !is RefreshStatus.Running) refreshStatus.value = null
  }
}

private const val SearchDebounceMs = 250L

private fun orderedByPreference(urls: List<String>, preferred: String?): List<String> =
  if (preferred == null || preferred !in urls) urls else listOf(preferred) + urls.filterNot { it == preferred }
