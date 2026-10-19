package dev.meghsohor.meghtv.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.meghsohor.meghtv.data.db.ChannelEntity
import dev.meghsohor.meghtv.theme.MeghBackground
import dev.meghsohor.meghtv.theme.MeghSurface
import dev.meghsohor.meghtv.theme.MeghSurfaceVariant
import dev.meghsohor.meghtv.ui.MeghIcons
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** The row last focused in a view (or [TabRowKey] for the tabs), where it was, and what was playing then. */
internal data class FocusedRow(val key: String, val index: Int, val playingId: String?)

private const val TabRowKey = "#tab"

private val ArrowDirections = setOf(FocusDirection.Left, FocusDirection.Right, FocusDirection.Up, FocusDirection.Down, FocusDirection.Next, FocusDirection.Previous)

@Composable
internal fun SidePanel(
  state: TvHomeUiState,
  viewModel: TvHomeViewModel,
  touchMode: Boolean,
  entryFocusRequester: FocusRequester,
  entryRequest: Int,
  open: Boolean,
  listState: LazyListState,
  openedFrom: MutableMap<PanelState, Int>,
  focusedRows: MutableMap<PanelState, FocusedRow>,
  onActivity: () -> Unit,
  onSearchFieldFocusChanged: (Boolean) -> Unit,
  onBackHandled: () -> Unit,
  onRefresh: () -> Unit,
  onInfo: () -> Unit,
  onChannelMenu: (index: Int, ChannelEntity) -> Unit,
  onChannelPicked: (channelId: String) -> Unit,
  refocusAfterDelete: IndexedValue<String>?,
  onRefocused: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val currentOnActivity by rememberUpdatedState(onActivity)
  // A list change removes the focused row: focus the row it was opened from, or the first visible one.
  var panelHasFocus by remember { mutableStateOf(false) }
  val contentFocusRequester = remember { FocusRequester() }
  var focusIndex by remember(state.panel) { mutableIntStateOf(openedFrom[state.panel] ?: listState.firstVisibleItemIndex) }
  suspend fun focusRow(index: Int) {
    withFrameNanos {} // compose the list...
    withFrameNanos {} // ...and lay it out
    if (panelHasFocus) return
    if (listState.layoutInfo.visibleItemsInfo.none { it.index == index }) listState.scrollToItem(index)
    val shown = withTimeoutOrNull(2_000) { snapshotFlow { listState.layoutInfo.visibleItemsInfo.any { it.index == index } }.first { it } }
    if (shown != null) {
      withFrameNanos {}
      runCatching { contentFocusRequester.requestFocus() } // the row may have scrolled away meanwhile
    }
  }
  // The rows as displayed: an in-list search filters them, and the menu index points into that filtered list.
  // Per view, so a new list isn't looked up in the last one's rows before it reports its own.
  var shownRows by remember(state.panel) { mutableStateOf<List<ChannelEntity>?>(null) }
  fun rowIndex(key: String): Int? =
    when (state.panel) {
      PanelState.CategoriesMenu ->
        when (key) {
          AllChannelsRowKey -> 0
          CountriesRowKey -> 1
          else -> state.categories.indexOfFirst { it.id == key }.takeIf { it >= 0 }?.plus(2)
        }
      PanelState.CountriesMenu -> state.countries.indexOfFirst { it.code == key }.takeIf { it >= 0 }
      // Null right after opening, until the list reports it.
      is PanelState.ChannelList -> (shownRows ?: state.listChannels)?.indexOfFirst { it.id == key }?.takeIf { it >= 0 }
    }
  fun rowCount(): Int =
    when (state.panel) {
      PanelState.CategoriesMenu -> state.categories.size + 2
      PanelState.CountriesMenu -> state.countries.size
      is PanelState.ChannelList -> (shownRows ?: state.listChannels)?.size ?: 0
    }
  // The row last focused here, or the playing channel if it changed since (Channel Up/Down with the panel closed).
  // Null: the selected tab.
  fun entryRow(): Int? {
    val playingId = state.currentChannel?.id
    val playing = if (state.panel is PanelState.ChannelList) playingId?.let(::rowIndex) else null
    val last = focusedRows[state.panel] ?: return playing
    // A row gone since (un-starred, deleted, filtered out): the one now in its place.
    val lastIndex = if (last.key == TabRowKey) null else rowIndex(last.key) ?: last.index.coerceAtMost(rowCount() - 1).takeIf { it >= 0 }
    return when {
      last.playingId != playingId -> playing ?: lastIndex
      last.key == TabRowKey -> null
      else -> lastIndex ?: playing
    }
  }
  fun onRowFocused(key: String) {
    focusedRows[state.panel] = FocusedRow(key, rowIndex(key) ?: -1, state.currentChannel?.id)
  }
  // A channel change with the panel open (a pick, Channel Up/Down) leaves focus where it is. Not while it slides
  // away: a change then counts as made with it closed.
  val currentOpen by rememberUpdatedState(open)
  val seenPlaying = remember { arrayOf(state.currentChannel?.id) }
  LaunchedEffect(state.currentChannel?.id) {
    val playingId = state.currentChannel?.id
    if (playingId == seenPlaying[0]) return@LaunchedEffect
    seenPlaying[0] = playingId
    if (currentOpen) focusedRows[state.panel]?.let { focusedRows[state.panel] = it.copy(playingId = playingId) }
  }
  // A tab opened with OK keeps focus, so the new view never sees it arrive: record it there too.
  var tabRowHasFocus by remember { mutableStateOf(false) }
  LaunchedEffect(state.panel) { if (tabRowHasFocus) onRowFocused(TabRowKey) }
  // Wait for the query: a revisited list's scroll state still describes the previous visit.
  val contentReady = state.startupPanelChosen && (state.panel !is PanelState.ChannelList || state.listChannels != null)
  // Opening, or a key with focus left on the root, lands on entryRow(), else the selected tab. A view change lands on
  // the row a level was opened from, else entryRow().
  val servedEntry = remember { intArrayOf(-1) }
  LaunchedEffect(state.panel, contentReady, entryRequest) {
    if (!contentReady) return@LaunchedEffect
    val opening = servedEntry[0] != entryRequest
    servedEntry[0] = entryRequest
    // Touch has no focus: opening only brings the playing channel into view (after the arrows switched it, say).
    if (touchMode) {
      val playing = if (opening && state.panel is PanelState.ChannelList) state.currentChannel?.id?.let(::rowIndex) else null
      if (playing != null) {
        withFrameNanos {}
        withFrameNanos {}
        if (listState.layoutInfo.visibleItemsInfo.none { it.index == playing }) listState.scrollToItem(playing)
      }
      return@LaunchedEffect
    }
    val row = (if (opening) null else openedFrom[state.panel]) ?: entryRow()
    if (row == null && opening) {
      withFrameNanos {}
      runCatching { entryFocusRequester.requestFocus() }
      return@LaunchedEffect
    }
    if (row != null) focusIndex = row
    focusRow(focusIndex)
  }
  // Once a deleted row leaves the list, focus the row that took its place.
  LaunchedEffect(refocusAfterDelete, shownRows) {
    val (index, deletedId) = refocusAfterDelete ?: return@LaunchedEffect
    val rows = shownRows ?: return@LaunchedEffect
    if (rows.any { it.id == deletedId }) return@LaunchedEffect
    if (rows.isNotEmpty()) {
      focusIndex = index.coerceAtMost(rows.lastIndex)
      focusRow(focusIndex)
    }
    onRefocused()
  }
  // Entry focus is the selected tab, not Refresh: a double OK to wake the panel would start a check.
  fun entry(selected: Boolean) = if (selected) Modifier.focusRequester(entryFocusRequester) else Modifier
  fun open(index: Int, navigate: () -> Unit) {
    openedFrom[state.panel] = index
    navigate()
  }

  Box(
    modifier
      .drawBehind {
        drawRect(PanelBackground)
        drawRect(LineColor, size = size.copy(width = 1.dp.toPx()))
      }
      .onFocusChanged { panelHasFocus = it.hasFocus }
      // An arrow at the panel's edge stays put: it would land on the error or stopped screen's buttons behind it.
      .focusProperties { onExit = { if (requestedFocusDirection in ArrowDirections) cancelFocusChange() } }
      .focusGroup()
      // Observes every touch: restarts the countdown, and stops a tap on empty space reaching the video.
      .pointerInput(Unit) {
        awaitPointerEventScope {
          while (true) {
            awaitPointerEvent(PointerEventPass.Initial)
            currentOnActivity()
          }
        }
      }
      .imePadding()
  ) {
    val toTab = { runCatching { entryFocusRequester.requestFocus() }; Unit }
    val tabFocus =
      Modifier.onFocusChanged {
        tabRowHasFocus = it.hasFocus
        if (it.hasFocus) onRowFocused(TabRowKey)
      }
    // Empty until launch picks the opening view, or Categories flashes up first. The gutter and top gap live on
    // the lists inside, not here, so a drilled-in list's header can span the full panel width flush to the tabs.
    val content: @Composable (Modifier) -> Unit = { contentModifier ->
      Box(contentModifier) {
        if (state.startupPanelChosen) when (val panel = state.panel) {
          PanelState.CategoriesMenu ->
            CategoriesMenuContent(
              categories = state.categories,
              listState = listState,
              focusIndex = focusIndex,
              focusRequester = contentFocusRequester,
              onRowFocused = ::onRowFocused,
              onNavigateToTab = toTab,
              onAllChannels = { open(0) { viewModel.onSelectAllChannelsRow() } },
              onCountries = { open(1) { viewModel.onSelectCountriesRow() } },
              onCategory = { index, category -> open(index) { viewModel.onSelectCategoryRow(category) } },
            )
          PanelState.CountriesMenu ->
            CountriesMenuContent(
              countries = state.countries,
              listState = listState,
              focusIndex = focusIndex,
              focusRequester = contentFocusRequester,
              onRowFocused = ::onRowFocused,
              onBack = { viewModel.onBack() },
              onCountry = { index, country -> open(index) { viewModel.onSelectCountryRow(country) } },
            )
          is PanelState.ChannelList ->
            ChannelListContent(
              source = panel.source,
              touchMode = touchMode,
              listState = listState,
              focusIndex = focusIndex,
              focusRequester = contentFocusRequester,
              onRowFocused = ::onRowFocused,
              onNavigateToTab = toTab,
              searchQuery = state.searchQuery,
              channels = state.listChannels,
              countries = state.countries,
              bookmarkedIds = state.bookmarkedIds,
              failedIds = state.failedIds,
              currentChannelId = state.currentChannel?.id,
              onBack = { viewModel.onBack() },
              onSearchQueryChange = viewModel::onSearchQueryChange,
              onSelectChannel = { id, shownIds ->
                viewModel.onSelectChannel(id, shownIds)
                onChannelPicked(id)
              },
              onToggleBookmark = viewModel::onToggleBookmark,
              onChannelMenu = onChannelMenu,
              onSearchFieldFocusChanged = onSearchFieldFocusChanged,
              onBackHandled = onBackHandled,
              onRowsShown = { shownRows = it },
            )
        }
      }
    }
    // One row of tabs on every device, as on a phone. TV adds Info at its start; touch has the corner button instead.
    Column(Modifier.fillMaxSize()) {
      val band = Modifier.fillMaxWidth().background(PinnedBand).drawBehind {
        drawRect(LineColor, topLeft = Offset(0f, size.height - 1.dp.toPx()), size = size.copy(height = 1.dp.toPx()))
      }
      // Up from the list lands on the open tab, not whichever is nearest (Info, at the far left).
      val upToSelectedTab = Modifier.focusProperties { onEnter = { if (requestedFocusDirection == FocusDirection.Up) entryFocusRequester.requestFocus() } }.focusGroup()
      Row(band.height(52.dp).padding(bottom = 1.dp).then(tabFocus).then(upToSelectedTab), verticalAlignment = Alignment.CenterVertically) {
        if (!touchMode) {
          InfoTab(onInfo)
          PinnedDivider()
        }
        PinnedRow("Refresh", onRefresh, modifier = Modifier.weight(1f))
        PinnedDivider()
        PinnedRow("Search", viewModel::onPinnedSearch, selected = isSearch(state.panel), modifier = Modifier.weight(1f).then(entry(isSearch(state.panel))))
        PinnedDivider()
        PinnedRow("Favourites", viewModel::onPinnedFavourites, selected = isFavourites(state.panel), modifier = Modifier.weight(1f).then(entry(isFavourites(state.panel))))
        PinnedDivider()
        PinnedRow("Categories", viewModel::onPinnedCategories, selected = isCategories(state.panel), modifier = Modifier.weight(1f).then(entry(isCategories(state.panel))))
      }
      content(Modifier.weight(1f))
    }
  }
}

internal fun isSearch(panel: PanelState) = panel is PanelState.ChannelList && panel.source == ChannelListSource.Search

private fun isFavourites(panel: PanelState) = panel is PanelState.ChannelList && panel.source == ChannelListSource.Favourites

/** Drill-downs from Categories keep that tab highlighted. */
private fun isCategories(panel: PanelState) = !isSearch(panel) && !isFavourites(panel)
internal val RowGutter = 10.dp
internal val RowGap = 6.dp
// The search field's corners; list rows are pills.
internal val RowRadius = 12.dp
// Three nested layers told apart by tone alone, each a step lighter than the one around it:
// the pinned tabs (outermost), a drilled-in list's header, then the list itself.
private val PinnedBand = lerp(MeghSurface, MeghBackground, 0.4f)
internal val HeaderBand = MeghSurface
private val ListTone = lerp(MeghSurface, MeghSurfaceVariant, 0.55f)
private val PanelBackground = ListTone.copy(alpha = 0.97f)
// Glossy pills (the user's choice, after WhatsApp's and YouTube's buttons): a faint sheen on the top edge, the tab
// bar's dark through the top half, easing to the earlier glass tone at the bottom, and a rim brighter along the top.
private fun glossy(dark: Color, light: Color) =
  Brush.verticalGradient(0f to lerp(dark, Color.White, 0.07f), 0.15f to dark, 0.5f to dark, 1f to light)
private val GlassTone = Color.White.copy(alpha = 0.07f).compositeOver(ListTone)
private val RowFill = glossy(PinnedBand, GlassTone)
private val RowFocusFill = glossy(lerp(PinnedBand, Color.White, 0.08f), lerp(GlassTone, Color.White, 0.08f))
private val RowRim = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.08f)))
internal val LineColor = Color.White.copy(alpha = 0.09f)

// A failed channel: faint see-through glass instead of the dark fill, so it reads as muted but stays visible.
private val DimmedRowFill = SolidColor(Color.White.copy(alpha = 0.035f))
private val DimmedRowRim = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.15f), Color.White.copy(alpha = 0.04f)))
internal const val DimmedTextAlpha = 0.6f

/**
 * A [block] (list row) is a glossy pill, faint glass when [dimmed]; a pinned tab
 * only fills when selected, pressed or focused. Selected is brand cyan (pair with [rowContentColor]),
 * and D-pad focus draws a 2dp ring (3dp white on a selected one); [ringed] draws it while a button inside has focus. One drawBehind, no clip or animation: those cost per row on a low-end TV.
 */
@Composable
internal fun Modifier.panelRow(
  interaction: MutableInteractionSource,
  selected: Boolean = false,
  block: Boolean = true,
  dimmed: Boolean = false,
  ringed: Boolean = false,
  onLongClick: (() -> Unit)? = null,
  onClick: () -> Unit,
): Modifier {
  val focused by interaction.collectIsFocusedAsState()
  val pressed by interaction.collectIsPressedAsState()
  val colors = MaterialTheme.colorScheme
  val fill: Brush =
    when {
      selected -> SolidColor(colors.primary)
      focused || pressed -> if (block) RowFocusFill else SolidColor(colors.surfaceVariant)
      dimmed -> DimmedRowFill
      block -> RowFill
      else -> SolidColor(Color.Transparent)
    }
  // On the cyan fill a dark ring barely shows: white, and thicker.
  val ring = if (selected) Color.White else colors.primary
  return this.drawBehind {
      drawRoundRect(fill, cornerRadius = CornerRadius(if (block) size.height / 2 else 0f))
      val ringOn = focused || ringed
      val width = (if (focused && selected) 3.dp else if (ringOn) 2.dp else 1.dp).toPx()
      val inset = Offset(width / 2, width / 2)
      val ringSize = Size(size.width - width, size.height - width)
      // Half the stroke's own height, so its outer edge follows the fill's.
      val corner = CornerRadius(if (block) ringSize.height / 2 else 0f)
      when {
        ringOn -> drawRoundRect(ring, topLeft = inset, size = ringSize, cornerRadius = corner, style = Stroke(width))
        block && !selected -> drawRoundRect(if (dimmed) DimmedRowRim else RowRim, topLeft = inset, size = ringSize, cornerRadius = corner, style = Stroke(width))
      }
    }
    .combinedClickable(interactionSource = interaction, indication = null, onLongClick = onLongClick, onClick = onClick)
    .focusable(interactionSource = interaction)
}

@Composable
private fun PinnedDivider() {
  Box(Modifier.width(1.dp).height(24.dp).background(LineColor))
}

@Composable
internal fun rowContentColor(selected: Boolean): Color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

/** A tab in the top row: its name only, centred; too narrow for an icon and "Favourites". */
@Composable
private fun PinnedRow(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false) {
  val interaction = remember { MutableInteractionSource() }
  Row(
    modifier.panelRow(interaction, selected, block = false, onClick = onClick).fillMaxHeight().padding(horizontal = 2.dp),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(label, color = rowContentColor(selected), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}

/** TV only: Info & Support at the start of the top row, as an icon. */
@Composable
private fun InfoTab(onClick: () -> Unit) {
  val interaction = remember { MutableInteractionSource() }
  Box(
    Modifier.width(52.dp).fillMaxHeight().panelRow(interaction, block = false, onClick = onClick).semantics { contentDescription = "Info & Support" },
    contentAlignment = Alignment.Center,
  ) {
    Icon(MeghIcons.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
  }
}
