package dev.meghsohor.meghtv.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
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

/** Below this height the four pinned tabs share one row. */
private val CompactPanelHeight = 480.dp

@Composable
internal fun SidePanel(
  state: TvHomeUiState,
  viewModel: TvHomeViewModel,
  touchMode: Boolean,
  entryFocusRequester: FocusRequester,
  listState: LazyListState,
  openedFrom: MutableMap<PanelState, Int>,
  onActivity: () -> Unit,
  onSearchFieldFocusChanged: (Boolean) -> Unit,
  onBackHandled: () -> Unit,
  onRefresh: () -> Unit,
  onInfo: () -> Unit,
  onChannelMenu: (index: Int, ChannelEntity) -> Unit,
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
  // Wait for the query: a revisited list's scroll state still describes the previous visit.
  val contentReady = state.panel !is PanelState.ChannelList || state.listChannels != null
  // The caller focuses the view it opened on.
  val openedOn = remember { arrayOf<PanelState?>(state.panel) }
  LaunchedEffect(state.panel, contentReady) {
    if (openedOn[0] != state.panel) openedOn[0] = null
    if (touchMode || !contentReady || openedOn[0] != null) return@LaunchedEffect
    focusRow(focusIndex)
  }
  // The rows as displayed: an in-list search filters them, and the menu index points into that filtered list.
  var shownRows by remember { mutableStateOf<List<ChannelEntity>?>(null) }
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
  // Entry focus is the selected tab, not Refresh Channels: a double OK to wake the panel would start a check.
  fun entry(selected: Boolean) = if (selected) Modifier.focusRequester(entryFocusRequester) else Modifier
  fun open(index: Int, navigate: () -> Unit) {
    openedFrom[state.panel] = index
    navigate()
  }

  BoxWithConstraints(
    modifier
      .drawBehind {
        drawRect(PanelBackground)
        drawRect(LineColor, size = size.copy(width = 1.dp.toPx()))
      }
      .onFocusChanged { panelHasFocus = it.hasFocus }
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
    val compact = maxHeight < CompactPanelHeight
    Column(Modifier.fillMaxSize()) {
      val band = Modifier.fillMaxWidth().background(PinnedBand).drawBehind {
        drawRect(LineColor, topLeft = Offset(0f, size.height - 1.dp.toPx()), size = size.copy(height = 1.dp.toPx()))
      }
      if (compact) {
        Row(band.height(52.dp).padding(bottom = 1.dp), verticalAlignment = Alignment.CenterVertically) {
          PinnedRow("Refresh", MeghIcons.Refresh, onRefresh, compact = true, modifier = Modifier.weight(1f))
          PinnedDivider(vertical = true)
          PinnedRow("Search", MeghIcons.Search, viewModel::onPinnedSearch, selected = isSearch(state.panel), compact = true, modifier = Modifier.weight(1f).then(entry(isSearch(state.panel))))
          PinnedDivider(vertical = true)
          PinnedRow("Favourites", MeghIcons.Star, viewModel::onPinnedFavourites, selected = isFavourites(state.panel), compact = true, modifier = Modifier.weight(1f).then(entry(isFavourites(state.panel))))
          PinnedDivider(vertical = true)
          PinnedRow("Categories", MeghIcons.Grid, viewModel::onPinnedCategories, selected = isCategories(state.panel), compact = true, modifier = Modifier.weight(1f).then(entry(isCategories(state.panel))))
        }
      } else {
        Column(band) {
          PinnedRow("Info & Support", MeghIcons.Info, onInfo, modifier = Modifier.fillMaxWidth())
          PinnedDivider(vertical = false)
          PinnedRow("Refresh Channels", MeghIcons.Refresh, onRefresh, modifier = Modifier.fillMaxWidth())
          PinnedDivider(vertical = false)
          PinnedRow("Search", MeghIcons.Search, viewModel::onPinnedSearch, selected = isSearch(state.panel), modifier = Modifier.fillMaxWidth().then(entry(isSearch(state.panel))))
          PinnedDivider(vertical = false)
          PinnedRow("Favourites", MeghIcons.Star, viewModel::onPinnedFavourites, selected = isFavourites(state.panel), modifier = Modifier.fillMaxWidth().then(entry(isFavourites(state.panel))))
          PinnedDivider(vertical = false)
          PinnedRow("Categories", MeghIcons.Grid, viewModel::onPinnedCategories, selected = isCategories(state.panel), modifier = Modifier.fillMaxWidth().then(entry(isCategories(state.panel))))
        }
      }

      // Empty until launch picks the opening view, or Categories flashes up first. The gutter and top gap live on
      // the lists inside, not here, so a drilled-in list's header can span the full panel width flush to the tabs.
      Box(Modifier.weight(1f)) {
        if (state.startupPanelChosen) when (val panel = state.panel) {
          PanelState.CategoriesMenu ->
            CategoriesMenuContent(
              categories = state.categories,
              listState = listState,
              focusIndex = focusIndex,
              focusRequester = contentFocusRequester,
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
              searchQuery = state.searchQuery,
              channels = state.listChannels,
              countries = state.countries,
              bookmarkedIds = state.bookmarkedIds,
              failedIds = state.failedIds,
              currentChannelId = state.currentChannel?.id,
              onBack = { viewModel.onBack() },
              onSearchQueryChange = viewModel::onSearchQueryChange,
              onSelectChannel = viewModel::onSelectChannel,
              onToggleBookmark = viewModel::onToggleBookmark,
              onChannelMenu = onChannelMenu,
              onSearchFieldFocusChanged = onSearchFieldFocusChanged,
              onBackHandled = onBackHandled,
              onRowsShown = { shownRows = it },
            )
        }
      }
    }
  }
}

internal fun isSearch(panel: PanelState) = panel is PanelState.ChannelList && panel.source == ChannelListSource.Search

private fun isFavourites(panel: PanelState) = panel is PanelState.ChannelList && panel.source == ChannelListSource.Favourites

/** Drill-downs from Categories keep that tab highlighted. */
private fun isCategories(panel: PanelState) = !isSearch(panel) && !isFavourites(panel)

internal val RowGutter = 10.dp
internal val RowGap = 6.dp
internal val RowRadius = 12.dp
// Three nested layers told apart by tone alone, each a step lighter than the one around it:
// the pinned tabs (outermost), a drilled-in list's header, then the list itself.
private val PinnedBand = lerp(MeghSurface, MeghBackground, 0.4f)
internal val HeaderBand = MeghSurface
private val ListTone = lerp(MeghSurface, MeghSurfaceVariant, 0.55f)
private val PanelBackground = ListTone.copy(alpha = 0.97f)
// Opaque and a shade darker than the list layer, so rows read as set into it.
private val RowFill = lerp(ListTone, MeghBackground, 0.15f)
internal val LineColor = Color.White.copy(alpha = 0.09f)

private val DimmedRowFill = MeghSurfaceVariant.copy(alpha = 0.2f)
private val DimmedLineColor = Color.White.copy(alpha = 0.04f)
internal const val DimmedTextAlpha = 0.5f

/**
 * A [block] (list row) is a rounded block with a hairline border, fainter when [dimmed]; a pinned tab
 * only fills when selected, pressed or focused. Selected is brand cyan (pair with [rowContentColor]),
 * and D-pad focus draws a 2dp ring. One drawBehind, no clip or animation: those cost per row on a low-end TV.
 */
@Composable
internal fun Modifier.panelRow(
  interaction: MutableInteractionSource,
  selected: Boolean = false,
  block: Boolean = true,
  dimmed: Boolean = false,
  onLongClick: (() -> Unit)? = null,
  onClick: () -> Unit,
): Modifier {
  val focused by interaction.collectIsFocusedAsState()
  val pressed by interaction.collectIsPressedAsState()
  val colors = MaterialTheme.colorScheme
  val fill =
    when {
      selected -> colors.primary
      focused || pressed -> colors.surfaceVariant
      dimmed -> DimmedRowFill
      block -> RowFill
      else -> Color.Transparent
    }
  val ring = if (selected) colors.onPrimary else colors.primary
  return this.drawBehind {
      val radius = if (block) RowRadius.toPx() else 0f
      drawRoundRect(fill, cornerRadius = CornerRadius(radius))
      val width = (if (focused) 2.dp else 1.dp).toPx()
      val inset = Offset(width / 2, width / 2)
      val ringSize = Size(size.width - width, size.height - width)
      val corner = CornerRadius(radius - width / 2)
      when {
        focused -> drawRoundRect(ring, topLeft = inset, size = ringSize, cornerRadius = corner, style = Stroke(width))
        block && !selected -> drawRoundRect(if (dimmed) DimmedLineColor else LineColor, topLeft = inset, size = ringSize, cornerRadius = corner, style = Stroke(width))
      }
    }
    .combinedClickable(interactionSource = interaction, indication = null, onLongClick = onLongClick, onClick = onClick)
    .focusable(interactionSource = interaction)
}

@Composable
private fun PinnedDivider(vertical: Boolean) {
  Box(if (vertical) Modifier.width(1.dp).height(24.dp).background(LineColor) else Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(LineColor))
}

@Composable
internal fun rowContentColor(selected: Boolean): Color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

@Composable
private fun PinnedRow(
  label: String,
  icon: ImageVector,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  selected: Boolean = false,
  compact: Boolean = false,
) {
  val interaction = remember { MutableInteractionSource() }
  Row(
    modifier
      .panelRow(interaction, selected, block = false, onClick = onClick)
      .then(if (compact) Modifier.fillMaxHeight() else Modifier.heightIn(min = 48.dp))
      .padding(horizontal = if (compact) 2.dp else 16.dp),
    horizontalArrangement = if (compact) Arrangement.Center else Arrangement.Start,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // Too narrow for an icon and "Favourites".
    if (!compact) {
      Icon(icon, contentDescription = null, tint = if (selected) rowContentColor(true) else MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
      Spacer(Modifier.width(14.dp))
    }
    Text(
      label,
      color = rowContentColor(selected),
      style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.Normal,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}
