package dev.meghsohor.meghtv.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.meghsohor.meghtv.data.ListCheck
import dev.meghsohor.meghtv.data.ListUpdate
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

sealed interface UpdateOffer {
  /** New or changed channels: Update, Full refresh or Later. */
  data class Available(val update: ListUpdate) : UpdateOffer

  /** A check from the menu found nothing new: Full refresh or Close. */
  data class UpToDate(val updatedAt: String) : UpdateOffer
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
  /** In play order: the saved source first. */
  val currentStreamUrls: List<String> = emptyList(),
  /** The same sources in their listed order, which numbers them in the Source picker. */
  val currentSources: List<String> = emptyList(),
  val searchQuery: String = "",
  /** Null when no refresh dialog is up. */
  val refresh: RefreshStatus? = null,
  val updateOffer: UpdateOffer? = null,
)

class TvHomeViewModel(private val repository: MeghTVRepository) : ViewModel() {

  private val panel = MutableStateFlow<PanelState>(PanelState.CategoriesMenu)
  private val currentChannelId = MutableStateFlow<String?>(null)

  /** The list [currentChannelId] was picked from, for Channel Up/Down. */
  private val currentPlaybackList = MutableStateFlow<List<String>>(emptyList())
  private val searchQuery = MutableStateFlow("")
  private val refreshStatus = MutableStateFlow<RefreshStatus?>(null)
  private val updateOffer = MutableStateFlow<UpdateOffer?>(null)

  /** A check or a list write is running. Main thread only. */
  private var listBusy = false

  /** The menu asked while a background check was running: that check answers it. */
  private var manualJoined = false
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
  private data class PlayerState(
    val requestedId: String?,
    val currentChannel: ChannelEntity?,
    val currentStreamUrls: List<String>,
    val currentSources: List<String> = currentStreamUrls,
  )

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
            else urls.map { it.url }.let { sources -> PlayerState(id, channel, orderedByPreference(sources, channel.selectedSourceUrl), sources) }
          }
        }
      }
      // A refresh can delete the playing channel's row: keep playing it rather than drop to the banner.
      .runningReduce { previous, new -> if (new.requestedId != null && new.currentChannel == null) previous else new }
      // Eagerly: uiState stops collecting in the background, and a restart would lose the last good state.
      .stateIn(viewModelScope, SharingStarted.Eagerly, PlayerState(null, null, emptyList()))

  val uiState =
    combine(browseState, playerState, searchQuery, refreshStatus, updateOffer) { browse, player, query, refresh, offer ->
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
          currentSources = player.currentSources,
          searchQuery = query,
          refresh = refresh,
          updateOffer = offer,
        )
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TvHomeUiState())

  init {
    viewModelScope.launch {
      if (startedInitialSelection) return@launch
      startedInitialSelection = true
      if (repository.bookmarkedChannels.first().isNotEmpty()) panel.value = PanelState.ChannelList(ChannelListSource.Favourites)
      // The first download starts before startup is reported done, so nothing waiting on startup sees a gap between them.
      val empty = !repository.hasChannels()
      if (empty) runListJob(full = true, RefreshProgress.Checking) { repository.fullRefresh(::onRefreshProgress) }
      startupPanelChosen.value = true
      // Once per launch, in the background.
      if (!empty) {
        repository.pruneEmptyMenus()
        checkForUpdate(manual = false)
      }
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

  /** Played first from now on, here and after a refresh while the channel still lists it. */
  fun onSourcePicked(channelId: String, url: String) {
    viewModelScope.launch { repository.setSelectedSource(channelId, url) }
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

  /** The menu's Refresh Channels. */
  fun onCheckForUpdate() = checkForUpdate(manual = true)

  // A check in the background shows nothing unless there's something to offer, and offers a list put off with Later only
  // when asked from the menu.
  private fun checkForUpdate(manual: Boolean) {
    if (updateOffer.value != null) return
    if (listBusy) {
      // Only a background check can be running here: a write's dialog covers the menu.
      if (manual && refreshStatus.value == null) {
        manualJoined = true
        refreshStatus.value = RefreshStatus.Running(RefreshProgress.Checking)
      }
      return
    }
    listBusy = true
    manualJoined = false
    if (manual) refreshStatus.value = RefreshStatus.Running(RefreshProgress.Checking)
    viewModelScope.launch {
      // Progress only shows while the dialog is up, so a background check reports into nothing until the menu joins it.
      val result = runCatching { repository.checkForUpdate(background = !manual, ::onRefreshProgress) }
      listBusy = false
      val asked = manual || manualJoined
      manualJoined = false
      val check = result.getOrElse {
        if (asked) refreshStatus.value = RefreshStatus.Failed(it.message)
        return@launch
      }
      if (check is ListCheck.Applied || check is ListCheck.Offer) onListChanged(full = false)
      when (check) {
        is ListCheck.UpToDate -> if (asked) updateOffer.value = UpdateOffer.UpToDate(check.updatedAt)
        is ListCheck.Applied -> if (asked) refreshStatus.value = RefreshStatus.Done(check.result)
        is ListCheck.Offer -> if (asked || !repository.isPostponed(check.update.updatedAt)) updateOffer.value = UpdateOffer.Available(check.update)
        // Skipped the download; the menu wants the offer back, so check again in full.
        ListCheck.Postponed ->
          if (asked) {
            refreshStatus.value = null
            checkForUpdate(manual = true)
            return@launch
          }
      }
      if (asked && refreshStatus.value is RefreshStatus.Running) refreshStatus.value = null
    }
  }

  /** Update ([full] false) or Full refresh, from the update popup. */
  fun onApplyUpdate(full: Boolean) {
    val offer = updateOffer.value ?: return
    updateOffer.value = null
    when (offer) {
      is UpdateOffer.Available -> runListJob(full, RefreshProgress.Saving(offer.update.total)) { repository.apply(offer.update, full, ::onRefreshProgress) }
      is UpdateOffer.UpToDate -> runListJob(full = true, RefreshProgress.Checking) { repository.fullRefresh(::onRefreshProgress) }
    }
  }

  /** Later, or Close on "up to date". A list put off isn't offered again by itself; a newer one is. */
  fun onDismissUpdateOffer() {
    (updateOffer.value as? UpdateOffer.Available)?.let { repository.postpone(it.update.updatedAt) }
    updateOffer.value = null
  }

  private fun runListJob(full: Boolean, firstStep: RefreshProgress, job: suspend () -> RefreshResult) {
    if (listBusy) return
    listBusy = true
    refreshStatus.value = RefreshStatus.Running(firstStep)
    viewModelScope.launch {
      val result = runCatching { job() }
      listBusy = false
      refreshStatus.value = result.fold(onSuccess = { RefreshStatus.Done(it) }, onFailure = { RefreshStatus.Failed(it.message) })
      if (result.isSuccess) onListChanged(full)
    }
  }

  private suspend fun onListChanged(full: Boolean) {
    // A Full refresh brings deleted channels back.
    if (full) deletedIds.clear()
    // Drop removed channels from the zap list, except the playing one: Up/Down step from it.
    val validIds = repository.allChannelIds().toSet()
    val playing = currentChannelId.value
    currentPlaybackList.value = currentPlaybackList.value.filter { it in validIds || it == playing }
  }

  private fun onRefreshProgress(progress: RefreshProgress) {
    refreshStatus.update { current -> if (current is RefreshStatus.Running) RefreshStatus.Running(progress) else current }
  }

  fun onDismissRefreshResult() {
    if (refreshStatus.value !is RefreshStatus.Running) refreshStatus.value = null
  }
}

private const val SearchDebounceMs = 250L

private fun orderedByPreference(urls: List<String>, preferred: String?): List<String> =
  if (preferred == null || preferred !in urls) urls else listOf(preferred) + urls.filterNot { it == preferred }
