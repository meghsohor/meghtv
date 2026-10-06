package dev.meghsohor.meghtv.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.meghsohor.meghtv.data.db.CategoryEntity
import dev.meghsohor.meghtv.data.db.ChannelEntity
import dev.meghsohor.meghtv.data.db.CountryEntity
import dev.meghsohor.meghtv.ui.MeghIcons
import kotlinx.coroutines.flow.first

// A list shorter than this isn't worth an in-list search box.
private const val InListSearchThreshold = 10

private val ThumbIdle = Color(0xFF2C3860)
private val ThumbActive = Color(0xFF4A5A8C)

/** Rows are near-equal height, so their average stands in for real offsets. Draw phase only: scrolling never recomposes. */
private fun Modifier.scrollIndicator(state: LazyListState): Modifier = drawWithContent {
  drawContent()
  val info = state.layoutInfo
  val visible = info.visibleItemsInfo
  if (visible.isEmpty() || !(state.canScrollForward || state.canScrollBackward)) return@drawWithContent
  val itemSize = visible.sumOf { it.size }.toFloat() / visible.size + info.mainAxisItemSpacing
  val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
  val content = itemSize * info.totalItemsCount
  val scrolled = visible.first().index * itemSize - visible.first().offset
  // A search list squeezed by the keyboard can be shorter than the min thumb: cap the min at the viewport so the range never inverts.
  val thumb = (viewport * viewport / content).coerceIn(28.dp.toPx().coerceAtMost(viewport), viewport)
  val top = (scrolled / (content - viewport)).coerceIn(0f, 1f) * (viewport - thumb)
  val width = 3.dp.toPx()
  drawRoundRect(
    if (state.isScrollInProgress) ThumbActive else ThumbIdle,
    topLeft = Offset(size.width + (RowGutter.toPx() - width) / 2, top),
    size = Size(width, thumb),
    cornerRadius = CornerRadius(width / 2),
  )
}

/** Left on the row itself goes up a level. Not from the star: its Left bubbles up here and must move focus to the row. */
@Composable
private fun Modifier.leftGoesUp(interaction: MutableInteractionSource, onNavigateUp: (() -> Unit)?): Modifier {
  if (onNavigateUp == null) return this
  val focused by interaction.collectIsFocusedAsState()
  return onKeyEvent { event ->
    if (focused && event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
      onNavigateUp()
      true
    } else {
      false
    }
  }
}

@Composable
internal fun CategoriesMenuContent(
  categories: List<CategoryEntity>,
  listState: LazyListState,
  focusIndex: Int,
  focusRequester: FocusRequester,
  onAllChannels: () -> Unit,
  onCountries: () -> Unit,
  onCategory: (index: Int, CategoryEntity) -> Unit,
) {
  fun rowModifier(index: Int) = if (index == focusIndex) Modifier.focusRequester(focusRequester) else Modifier
  LazyColumn(Modifier.padding(horizontal = RowGutter).scrollIndicator(listState), state = listState, contentPadding = MenuPadding, verticalArrangement = Arrangement.spacedBy(RowGap)) {
    item { PlainRow("All Channels", onClick = onAllChannels, modifier = rowModifier(0)) }
    item { PlainRow("Countries", onClick = onCountries, modifier = rowModifier(1)) }
    itemsIndexed(categories, key = { _, it -> it.id }) { i, category ->
      PlainRow(category.name, onClick = { onCategory(i + 2, category) }, modifier = rowModifier(i + 2))
    }
  }
}

@Composable
internal fun CountriesMenuContent(
  countries: List<CountryEntity>,
  listState: LazyListState,
  focusIndex: Int,
  focusRequester: FocusRequester,
  onBack: () -> Unit,
  onCountry: (index: Int, CountryEntity) -> Unit,
) {
  Column {
    ListHeader("Countries", onBack = onBack)
    LazyColumn(Modifier.padding(horizontal = RowGutter).scrollIndicator(listState), state = listState, contentPadding = ListPadding, verticalArrangement = Arrangement.spacedBy(RowGap)) {
      itemsIndexed(countries, key = { _, it -> it.code }) { i, country ->
        PlainRow(
          "${country.flag}  ${country.name}",
          onClick = { onCountry(i, country) },
          onNavigateUp = onBack,
          modifier = if (i == focusIndex) Modifier.focusRequester(focusRequester) else Modifier,
        )
      }
    }
  }
}

@Composable
internal fun ChannelListContent(
  source: ChannelListSource,
  touchMode: Boolean,
  listState: LazyListState,
  focusIndex: Int,
  focusRequester: FocusRequester,
  searchQuery: String,
  channels: List<ChannelEntity>?,
  countries: List<CountryEntity>,
  bookmarkedIds: Set<String>,
  failedIds: Set<String>,
  currentChannelId: String?,
  onBack: () -> Unit,
  onSearchQueryChange: (String) -> Unit,
  onSelectChannel: (channelId: String, shownIds: List<String>) -> Unit,
  onToggleBookmark: (ChannelEntity) -> Unit,
  onChannelMenu: (index: Int, ChannelEntity) -> Unit,
  onSearchFieldFocusChanged: (Boolean) -> Unit,
  onBackHandled: () -> Unit,
  onRowsShown: (List<ChannelEntity>?) -> Unit,
) {
  val flagByCountry = remember(countries) { countries.associate { it.code to it.flag } }
  val isSearch = source == ChannelListSource.Search
  val hasHeader = !isSearch && source != ChannelListSource.Favourites
  val keyboard = LocalSoftwareKeyboardController.current

  // In-list search, scoped to the current list, offered once it is long enough to be worth filtering.
  var searchExpanded by remember(source) { mutableStateOf(false) }
  var localQuery by remember(source) { mutableStateOf("") }
  // Only scoped lists get in-list search; All Channels is already covered by the Search tab.
  val searchable = (source is ChannelListSource.Category || source is ChannelListSource.Country) && (channels?.size ?: 0) > InListSearchThreshold
  // A refresh can shrink a list below the threshold: don't leave the field stuck open.
  LaunchedEffect(searchable) { if (!searchable) { searchExpanded = false; localQuery = "" } }
  // onBackHandled stamps the shared Back de-dupe, so a doubled press collapses the search without also closing the panel.
  BackHandler(enabled = searchExpanded) { onBackHandled(); searchExpanded = false; localQuery = "" }

  val shown =
    remember(channels, searchExpanded, localQuery) {
      if (searchExpanded && localQuery.isNotBlank()) channels?.filter { it.displayName.contains(localQuery, ignoreCase = true) }
      else channels
    }
  LaunchedEffect(shown) { onRowsShown(shown) }

  Column {
    // A drilled-in list's own header sits flush under the tabs; a header-less list (Favourites, Search) needs the gap itself.
    if (!hasHeader) Spacer(Modifier.height(10.dp))
    // Favourites and Search are named by their highlighted tab; a heading would repeat it.
    if (hasHeader) {
      ListHeader(
        title = source.headerName,
        count = shown?.size,
        onBack = onBack,
        searchable = searchable,
        searchExpanded = searchExpanded,
        onToggleSearch = { searchExpanded = !searchExpanded; if (!searchExpanded) localQuery = "" },
        searchQuery = localQuery,
        onSearchQueryChange = { localQuery = it },
        onSearchFieldFocusChanged = onSearchFieldFocusChanged,
      )
    }
    if (isSearch) {
      Box(Modifier.padding(horizontal = RowGutter)) {
        SearchField(
          value = searchQuery,
          onValueChange = onSearchQueryChange,
          autoFocus = touchMode,
          onFocusChanged = onSearchFieldFocusChanged,
        )
      }
    }
    if (channels == null) return@Column // still loading
    if ((isSearch || source == ChannelListSource.Favourites) && channels.isNotEmpty()) ChannelCount(channels.size)
    val rows = shown.orEmpty()
    if (rows.isEmpty()) {
      EmptyListMessage(
        when {
          source == ChannelListSource.Favourites -> "No favourites yet.\nUse the star next to any channel to add it here."
          isSearch && searchQuery.isBlank() -> "Type a channel name to search."
          isSearch -> "No channels match \"$searchQuery\"."
          searchExpanded && localQuery.isNotBlank() -> "No channels match \"$localQuery\"."
          else -> "No channels here."
        }
      )
    }
    LazyColumn(
      Modifier.padding(horizontal = RowGutter).scrollIndicator(listState),
      state = listState,
      contentPadding = ListPadding,
      verticalArrangement = Arrangement.spacedBy(RowGap),
    ) {
      itemsIndexed(rows, key = { _, it -> it.id }) { i, channel ->
        ChannelRow(
          modifier = if (i == focusIndex) Modifier.focusRequester(focusRequester) else Modifier,
          channel = channel,
          flag = flagByCountry[channel.countryCode].orEmpty(),
          bookmarked = channel.id in bookmarkedIds,
          playing = channel.id == currentChannelId,
          failed = channel.id in failedIds,
          onLongClick = {
            if (isSearch || searchExpanded) keyboard?.hide()
            onChannelMenu(i, channel)
          },
          onClick = {
            if (isSearch || searchExpanded) keyboard?.hide() // tapping a row leaves the keyboard up
            onSelectChannel(channel.id, rows.map { it.id })
          },
          onToggleBookmark = { onToggleBookmark(channel) },
          // Top-level tabs: nothing above them.
          onNavigateUp = if (isSearch || source == ChannelListSource.Favourites) null else onBack,
        )
      }
    }
  }
}

/**
 * A drilled-in list's header: a flat band with a bottom hairline, the middle of the panel's three layer tones, so
 * it reads as a section between the tabs and the list rather than as another row. Clicking it goes back; when the
 * list is long enough, the trailing icon turns the row into a search field scoped to this list (✕ closes it).
 */
@Composable
private fun ListHeader(
  title: String,
  onBack: () -> Unit,
  count: Int? = null,
  searchable: Boolean = false,
  searchExpanded: Boolean = false,
  onToggleSearch: () -> Unit = {},
  searchQuery: String = "",
  onSearchQueryChange: (String) -> Unit = {},
  onSearchFieldFocusChanged: (Boolean) -> Unit = {},
) {
  val backInteraction = remember { MutableInteractionSource() }
  val iconInteraction = remember { MutableInteractionSource() }
  Column(
    Modifier.padding(bottom = RowGap)
      .fillMaxWidth()
      .animateContentSize()
      .drawBehind {
        // Flat middle-layer fill with only a bottom hairline, so it sits flush under the tabs.
        val hair = 1.dp.toPx()
        drawRect(HeaderBand)
        drawRect(LineColor, topLeft = Offset(0f, size.height - hair), size = size.copy(height = hair))
      },
  ) {
    // One row, always: the field replaces the title while searching, so it never sinks a level under the keyboard.
    Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
      if (searchExpanded) {
        Box(Modifier.weight(1f).padding(start = 10.dp)) {
          SearchField(value = searchQuery, onValueChange = onSearchQueryChange, autoFocus = true, onFocusChanged = onSearchFieldFocusChanged)
        }
      } else {
        Row(
          Modifier.weight(1f)
            .fillMaxHeight()
            .panelRow(backInteraction, block = false, onClick = onBack)
            .padding(horizontal = 14.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(MeghIcons.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
          Spacer(Modifier.width(12.dp))
          Text(
            title,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
          )
          // Separate Text, so a long name ellipsizes without cutting off the count.
          if (count != null) {
            Text("  ($count)", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium, maxLines = 1)
          }
        }
      }
      if (searchable) {
        Box(
          Modifier.fillMaxHeight().width(52.dp).panelRow(iconInteraction, block = false, onClick = onToggleSearch),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            if (searchExpanded) MeghIcons.Close else MeghIcons.Search,
            contentDescription = if (searchExpanded) "Close search" else "Search this list",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun ChannelCount(count: Int) {
  Text(
    if (count == 1) "1 channel" else "$count channels",
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    style = MaterialTheme.typography.labelMedium,
    modifier = Modifier.padding(start = RowGutter + 6.dp, bottom = RowGap),
  )
}

@Composable
private fun EmptyListMessage(text: String) {
  Text(
    text,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    style = MaterialTheme.typography.bodyMedium,
    textAlign = TextAlign.Center,
    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
  )
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit, autoFocus: Boolean, onFocusChanged: (Boolean) -> Unit) {
  // The field would swallow DPAD_DOWN as a cursor move: hand it to focus traversal.
  val focusManager = LocalFocusManager.current
  val keyboard = LocalSoftwareKeyboardController.current
  val fieldFocusRequester = remember { FocusRequester() }
  // Touch: straight to the keyboard, unless there are earlier results it would cover.
  LaunchedEffect(Unit) { if (autoFocus && value.isBlank()) fieldFocusRequester.requestFocus() }
  // A field removed while focused (search collapsed, tab switched, panel closed) can't report the loss itself;
  // left set, the flag would keep the panel from ever auto-hiding.
  val currentOnFocusChanged by rememberUpdatedState(onFocusChanged)
  DisposableEffect(Unit) { onDispose { currentOnFocusChanged(false) } }

  var focused by remember { mutableStateOf(false) }
  val shape = RoundedCornerShape(RowRadius)
  Box(
    Modifier.fillMaxWidth()
      .padding(bottom = RowGap)
      .background(MaterialTheme.colorScheme.surfaceVariant, shape)
      .border(if (focused) 2.dp else 1.dp, if (focused) MaterialTheme.colorScheme.primary else LineColor, shape)
      .padding(start = 14.dp)
      .onFocusChanged { // the field inside is the focusable one
        focused = it.hasFocus
        onFocusChanged(it.hasFocus)
      }
      .onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
          focusManager.moveFocus(FocusDirection.Down)
          true
        } else {
          false
        }
      }
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.weight(1f).padding(vertical = 8.dp)) {
        if (value.isEmpty()) Text("Search channels…", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        BasicTextField(
          value = value,
          onValueChange = onValueChange,
          singleLine = true,
          textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
          cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
          keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
          // Full width, so a tap anywhere in the box lands on the field.
          modifier = Modifier.fillMaxWidth().focusRequester(fieldFocusRequester),
        )
      }
      // The tap target is the field's full 44dp height and 48dp wide, with a small glyph. Kept when empty, so the
      // field doesn't change height on the first letter: on a landscape phone with the keyboard up there's no spare.
      val clearInteraction = remember { MutableInteractionSource() }
      val clearFocused by clearInteraction.collectIsFocusedAsState()
      val clearPressed by clearInteraction.collectIsPressedAsState()
      Box(
        Modifier.size(width = 48.dp, height = 44.dp)
          .then(
            if (value.isNotEmpty()) {
              Modifier.clickable(interactionSource = clearInteraction, indication = null, onClickLabel = "Clear search") {
                onValueChange("")
                fieldFocusRequester.requestFocus() // keep the keyboard up for the next query
              }
            } else {
              Modifier
            }
          ),
        contentAlignment = Alignment.Center,
      ) {
        if (value.isNotEmpty()) {
          Icon(
            MeghIcons.ClearCircle,
            contentDescription = "Clear search",
            tint = if (clearFocused || clearPressed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun PlainRow(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, onNavigateUp: (() -> Unit)? = null) {
  val interaction = remember { MutableInteractionSource() }
  Row(
    modifier
      .fillMaxWidth()
      .leftGoesUp(interaction, onNavigateUp)
      .panelRow(interaction, onClick = onClick)
      .heightIn(min = 48.dp)
      .padding(horizontal = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(label, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}

@Composable
private fun ChannelRow(
  channel: ChannelEntity,
  flag: String,
  bookmarked: Boolean,
  playing: Boolean,
  failed: Boolean,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
  onToggleBookmark: () -> Unit,
  onNavigateUp: (() -> Unit)?,
  modifier: Modifier = Modifier,
) {
  val interaction = remember { MutableInteractionSource() }
  val rowFocus = remember { FocusRequester() }
  val starFocus = remember { FocusRequester() }
  Row(
    modifier
      .fillMaxWidth()
      // The star is inside the row's bounds, which directional search skips: link it explicitly.
      .focusRequester(rowFocus)
      .focusProperties { right = starFocus }
      .leftGoesUp(interaction, onNavigateUp)
      .panelRow(interaction, selected = playing, dimmed = failed, onLongClick = onLongClick, onClick = onClick)
      .semantics { if (failed) stateDescription = "Not working" }
      .heightIn(min = 48.dp)
      .padding(start = 14.dp, end = 2.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
      if (playing) Icon(MeghIcons.Play, contentDescription = null, tint = rowContentColor(selected = true), modifier = Modifier.padding(end = 8.dp).size(12.dp))
      Text(
        "$flag  ${channel.displayName}",
        color = rowContentColor(playing),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (playing) FontWeight.Bold else null,
        overflow = TextOverflow.Ellipsis,
        maxLines = 1,
        // alpha(), not a text colour: colour alpha doesn't reach the flag emoji.
        modifier = Modifier.weight(1f, fill = false).then(if (failed && !playing) Modifier.alpha(DimmedTextAlpha) else Modifier),
      )
    }
    val starInteraction = remember { MutableInteractionSource() }
    val starFocused by starInteraction.collectIsFocusedAsState()
    val starPressed by starInteraction.collectIsPressedAsState()
    val starHighlight = if (playing) Color.White.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant
    // 48dp touch target. A shaped background, not clip(): a clip gives every row a render layer.
    Box(
      Modifier.size(48.dp)
        .background(if (starFocused || starPressed) starHighlight else Color.Transparent, RoundedCornerShape(8.dp))
        .focusRequester(starFocus)
        .focusProperties { left = rowFocus }
        .toggleable(
          value = bookmarked,
          onValueChange = { onToggleBookmark() },
          role = Role.Checkbox,
          interactionSource = starInteraction,
          indication = null,
        )
        .semantics { contentDescription = "${channel.displayName} favourite" },
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        if (bookmarked) MeghIcons.StarFilled else MeghIcons.Star,
        contentDescription = null,
        // On the cyan row both states are dark; the shape tells them apart.
        tint =
          when {
            playing -> rowContentColor(selected = true)
            bookmarked -> Color.White
            else -> MaterialTheme.colorScheme.onSurfaceVariant
          },
        modifier = Modifier.size(20.dp),
      )
    }
  }
}

// A header gives its own list the top gap (equal to the inter-row gap); only the header-less top menu needs one here.
private val ListPadding = PaddingValues(bottom = 12.dp)
private val MenuPadding = PaddingValues(top = 10.dp, bottom = 12.dp)
