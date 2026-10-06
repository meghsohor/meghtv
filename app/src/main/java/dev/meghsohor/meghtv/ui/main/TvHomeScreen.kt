package dev.meghsohor.meghtv.ui.main

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.meghsohor.meghtv.R
import dev.meghsohor.meghtv.data.MeghTVRepository
import dev.meghsohor.meghtv.data.db.ChannelEntity
import dev.meghsohor.meghtv.theme.MeghBackground
import dev.meghsohor.meghtv.theme.MeghLive
import dev.meghsohor.meghtv.theme.MeghSurface
import dev.meghsohor.meghtv.theme.MeghSurfaceVariant
import dev.meghsohor.meghtv.ui.MeghIcons
import dev.meghsohor.meghtv.ui.player.LiveState
import dev.meghsohor.meghtv.ui.player.PlayerCommand
import dev.meghsohor.meghtv.ui.player.TrackControlsState
import dev.meghsohor.meghtv.ui.player.VideoPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

private val PanelWidth = 360.dp
private val PanelHandleWidth = 44.dp
private const val PanelAnimMs = 220
private const val SplashMs = 3_000L
private const val SplashFadeMs = 400
private const val PanelAutoHideDelayMs = 7000L
private const val SameBackPressWindowMs = 200L
private val PanelNavigationKeys =
  setOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight, Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

/** Below this height the four pinned tabs share one row. */
private val CompactPanelHeight = 480.dp

@Composable
fun TvHomeScreen(repository: MeghTVRepository, modifier: Modifier = Modifier) {
  val viewModel: TvHomeViewModel = viewModel { TvHomeViewModel(repository) }
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val touchMode = LocalInputModeManager.current.inputMode == InputMode.Touch

  // Auto-hide: arrow keys and touches inside the panel restart the count. Touches only stamp
  // lastActivityAt, not state, so a drag doesn't recompose the screen on every move.
  // A short loading screen at cold start; the panel slides in once it fades. Saved, so recreation doesn't replay it.
  var splashDone by rememberSaveable { mutableStateOf(false) }
  var panelOpen by remember { mutableStateOf(splashDone) }
  var activityTick by remember { mutableIntStateOf(0) }
  val lastActivityAt = remember { longArrayOf(SystemClock.uptimeMillis()) }
  var confirmRefresh by remember { mutableStateOf(false) }
  var confirmExit by remember { mutableStateOf(false) }
  var showSupport by rememberSaveable { mutableStateOf(false) }
  var showInfo by remember { mutableStateOf(false) }
  var searchFieldFocused by remember { mutableStateOf(false) }
  var playbackActive by remember { mutableStateOf(false) }
  var menuChannel by remember { mutableStateOf<IndexedValue<ChannelEntity>?>(null) }
  var refocusAfterDelete by remember { mutableStateOf<IndexedValue<String>?>(null) }
  var playerControlsVisible by remember { mutableStateOf(false) }
  var liveState by remember { mutableStateOf(LiveState()) }
  var trackControls by remember { mutableStateOf(TrackControlsState()) }
  val rootFocusRequester = remember { FocusRequester() }
  val panelEntryFocusRequester = remember { FocusRequester() }
  // Hoisted per view, so a list is where it was left after the panel closes or a level goes back.
  val listStates = remember { mutableMapOf<PanelState, LazyListState>() }
  val listState = listStates.getOrPut(state.panel) { LazyListState() }
  // The row each menu was left through, to put D-pad focus back on it.
  val openedFrom = remember { mutableMapOf<PanelState, Int>() }
  val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
  val playerCommands = remember { MutableSharedFlow<PlayerCommand>(extraBufferCapacity = 1) }
  val activity = LocalActivity.current
  var rootHasFocus by remember { mutableStateOf(false) }
  var rootSelfFocused by remember { mutableStateOf(false) }
  val hasPlayer = state.currentStreamUrls.isNotEmpty() && state.currentChannel != null

  fun openPanel() {
    panelOpen = true
    lastActivityAt[0] = SystemClock.uptimeMillis()
    activityTick++
  }

  // Back: the controls, then the panel, then the exit question. Android 13+ can deliver one press
  // twice (the forwarded key, then the platform callback); the echo is ignored.
  val lastBackAt = remember { longArrayOf(0L) }
  BackHandler {
    val now = SystemClock.uptimeMillis()
    if (now - lastBackAt[0] < SameBackPressWindowMs) return@BackHandler
    lastBackAt[0] = now
    when {
      !splashDone -> Unit
      !panelOpen && playerControlsVisible -> playerCommands.tryEmit(PlayerCommand.HideControls)
      panelOpen -> panelOpen = false
      // finish(): the system default only moves the task back, and reopening would resume the last channel.
      else -> confirmExit = true
    }
  }

  // Search freezes it for the whole Search view on touch, but only while typing on TV, where nothing but the timer closes
  // the panel. On touch a focused in-list search box also freezes it, so the field can't auto-hide out from under typing.
  val refreshInProgress = state.refresh != null
  val searching = if (touchMode) isSearch(state.panel) || searchFieldFocused else searchFieldFocused
  // Nothing to watch: on touch unless something plays; on TV only before the first pick, since there
  // only the timer can move the panel off a paused picture or the error screen.
  val nothingToWatch = if (touchMode) !(hasPlayer && playbackActive) else !hasPlayer
  val suppressAutoHide = !splashDone || refreshInProgress || confirmRefresh || menuChannel != null || confirmExit || showSupport || showInfo || searching || nothingToWatch
  LaunchedEffect(Unit) {
    if (splashDone) return@LaunchedEffect
    delay(SplashMs)
    splashDone = true
    panelOpen = true
  }
  LaunchedEffect(refreshInProgress, splashDone) { if (refreshInProgress && splashDone) panelOpen = true }

  // Once a day at most, checked on every return to the app: TV apps often stay in memory overnight. On a new install
  // the first refresh comes first: the prompt waits until its result is closed, and skips an install left empty.
  val context = LocalContext.current
  val supportPrompt = remember { SupportPrompt(context) }
  var resumeCount by remember { mutableIntStateOf(0) }
  LifecycleResumeEffect(Unit) {
    resumeCount++
    onPauseOrDispose {}
  }
  val hasChannels = state.categories.isNotEmpty()
  LaunchedEffect(resumeCount, splashDone, state.startupPanelChosen, refreshInProgress, hasChannels, showInfo) {
    if (splashDone && state.startupPanelChosen && !refreshInProgress && hasChannels && !showInfo && supportPrompt.dueToday()) {
      supportPrompt.markShown()
      showSupport = true
    }
  }

  LaunchedEffect(activityTick, suppressAutoHide, panelOpen) {
    if (suppressAutoHide || !panelOpen) return@LaunchedEffect
    lastActivityAt[0] = SystemClock.uptimeMillis() // each relaunch restarts the count
    while (true) {
      val remaining = lastActivityAt[0] + PanelAutoHideDelayMs - SystemClock.uptimeMillis()
      if (remaining <= 0) break
      delay(remaining)
    }
    panelOpen = false
  }

  // Keyed on the flip, not activityTick, so it doesn't steal focus mid-browse. None on touch: it would
  // only paint a highlight. At launch it waits for the opening view.
  LaunchedEffect(panelOpen, state.startupPanelChosen) {
    if (!panelOpen) rootFocusRequester.requestFocus()
    else if (!touchMode && state.startupPanelChosen) panelEntryFocusRequester.requestFocus()
  }

  // Removing the focused element (Retry, an opened or un-starred row) drops focus, and with it every
  // remote key. Take it back; the next key moves it into the panel.
  LaunchedEffect(rootHasFocus) {
    if (rootHasFocus) return@LaunchedEffect
    withFrameNanos {}
    withFrameNanos {}
    if (!rootHasFocus) runCatching { rootFocusRequester.requestFocus() }
  }

  Box(
    modifier
      .fillMaxSize()
      // No background: the window's navy is already there. The insets keep clear of the navigation bar and cutout.
      .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
      .focusRequester(rootFocusRequester)
      .onFocusChanged {
        rootHasFocus = it.hasFocus
        rootSelfFocused = it.isFocused
      }
      .focusable()
      .onPreviewKeyEvent { event ->
        // Compose would turn Back into a focus move and consume it: send it to the back dispatcher, on key-up.
        if (event.key == Key.Back && backDispatcher != null) {
          if (event.type == KeyEventType.KeyUp) backDispatcher.onBackPressed()
          return@onPreviewKeyEvent true
        }
        if (!splashDone) return@onPreviewKeyEvent true
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        // A held key repeats KeyDown; toggles act on the first only.
        val repeated = event.nativeKeyEvent.repeatCount > 0
        // In the player's Quality/Subtitles/Audio buttons: Left/Right move between them, Up leaves, Down stays.
        if (!panelOpen && trackControls.focused) {
          when (event.key) {
            Key.DirectionUp -> {
              runCatching { rootFocusRequester.requestFocus() }
              return@onPreviewKeyEvent true
            }
            Key.DirectionDown -> return@onPreviewKeyEvent true
            else -> Unit
          }
        }
        // A held Up: a fresh press with the panel closed opens it at once, so repeats arriving while it's still closed
        // only follow leaving those buttons, and would open the panel mid-press.
        if (!panelOpen && repeated && event.key == Key.DirectionUp) return@onPreviewKeyEvent true
        // With the controls up and those buttons showing, Down moves into them instead of opening the panel.
        if (!panelOpen && rootSelfFocused && playerControlsVisible && trackControls.available && event.key == Key.DirectionDown) {
          return@onPreviewKeyEvent playerCommands.tryEmit(PlayerCommand.FocusTrackControls)
        }
        // Focus parked on the root with the panel up: directional search doesn't look inside it, so move it in directly.
        if (panelOpen && rootSelfFocused && !touchMode && event.key in PanelNavigationKeys) {
          openPanel()
          runCatching { panelEntryFocusRequester.requestFocus() }
          return@onPreviewKeyEvent true
        }
        // Focus on the error screen's buttons: Left/Right move between them.
        if (!panelOpen && !rootSelfFocused && (event.key == Key.DirectionLeft || event.key == Key.DirectionRight)) return@onPreviewKeyEvent false
        when (event.key) {
          Key.ChannelUp -> {
            viewModel.onChannelUp()
            true
          }
          Key.ChannelDown -> {
            viewModel.onChannelDown()
            true
          }
          Key.MediaPlayPause -> repeated || playerCommands.tryEmit(PlayerCommand.TogglePlayPause)
          Key.MediaPlay -> playerCommands.tryEmit(PlayerCommand.Play)
          Key.MediaPause -> playerCommands.tryEmit(PlayerCommand.Pause)
          // A held key acts once: its repeats would rejoin live again, or open the panel once the flag clears.
          Key.MediaFastForward -> repeated || (liveState.behind && playerCommands.tryEmit(PlayerCommand.GoLive))
          // With the chip up (controls showing, behind live), Right goes live; else it opens the panel. A fresh press
          // with the panel closed opens it at once, so repeats arriving while it's still closed only follow a go-live.
          Key.DirectionRight if !panelOpen && repeated -> true
          Key.DirectionRight if !panelOpen && rootSelfFocused && playerControlsVisible && liveState.goLiveOffered ->
            playerCommands.tryEmit(PlayerCommand.GoLive)
          // Panel closed: OK shows the controls, then plays/pauses, for remotes without media keys.
          Key.DirectionCenter, Key.Enter, Key.NumPadEnter ->
            when {
              panelOpen -> {
                openPanel() // keeps the timer alive; the row handles the click
                false
              }
              !rootSelfFocused -> false // Retry or Delete has focus
              playerControlsVisible -> repeated || playerCommands.tryEmit(PlayerCommand.TogglePlayPause)
              hasPlayer -> repeated || playerCommands.tryEmit(PlayerCommand.ShowControls)
              else -> {
                openPanel()
                false
              }
            }
          Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight -> {
            openPanel()
            false // normal focus handling still runs
          }
          else -> false
        }
      }
  ) {
    val currentChannel = state.currentChannel
    if (hasPlayer && currentChannel != null) {
      VideoPlayer(
        channelId = currentChannel.id,
        streamUrls = state.currentStreamUrls,
        controlsAllowed = !panelOpen,
        touchControls = touchMode,
        onTap = {
          if (panelOpen) {
            panelOpen = false
            true
          } else {
            false
          }
        },
        onControlsVisibilityChange = { playerControlsVisible = it },
        onLiveStateChange = { liveState = it },
        onTrackControlsChange = { trackControls = it },
        onPlaybackActiveChange = { playbackActive = it },
        onAllSourcesFailed = { viewModel.onPlaybackFailed(currentChannel.id) },
        onPlaying = { viewModel.onPlaybackWorked(currentChannel.id) },
        onDeleteChannel = {
          viewModel.onDeleteChannel(currentChannel.id)
          openPanel()
        },
        overlayEndPadding = if (panelOpen) PanelWidth else 0.dp,
        // Clear of the edge tab, and the same on the left.
        controlsEdgeInset = PanelHandleWidth + 8.dp,
        playerCommands = playerCommands,
        modifier = Modifier.fillMaxSize(),
      )
      if (panelOpen || playerControlsVisible) {
        NowPlayingBadge(channel = currentChannel, live = !liveState.behind, modifier = Modifier.align(Alignment.TopStart).padding(16.dp))
      }
    } else {
      Image(
        painter = painterResource(R.drawable.tv_banner),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        // Clickable only with a menu to close, or a screen reader announces a no-op.
        modifier =
          Modifier.fillMaxSize()
            .then(
              if (panelOpen) {
                Modifier.clickable(onClickLabel = "Close menu", indication = null, interactionSource = null) { panelOpen = false }
                  .semantics { contentDescription = "Close menu" }
              } else {
                Modifier
              }
            ),
      )
    }

    // Touch only: a remote can't reach it, since with the menu closed its keys open the menu. The menu covers it when open.
    if (touchMode) CornerInfoButton(onClick = { showInfo = true }, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp))

    // Slides in from the edge it lives on. One placement offset, so a low-end TV keeps up.
    AnimatedVisibility(
      visible = panelOpen,
      modifier = Modifier.align(Alignment.CenterEnd),
      enter = slideInHorizontally(tween(PanelAnimMs)) { it } + fadeIn(tween(PanelAnimMs)),
      exit = slideOutHorizontally(tween(PanelAnimMs)) { it } + fadeOut(tween(PanelAnimMs)),
    ) {
      SidePanel(
        state = state,
        viewModel = viewModel,
        touchMode = touchMode,
        entryFocusRequester = panelEntryFocusRequester,
        onActivity = { lastActivityAt[0] = SystemClock.uptimeMillis() },
        onSearchFieldFocusChanged = { searchFieldFocused = it },
        // The in-list search's own Back handler stamps the shared de-dupe, so a doubled press can't also close the panel.
        onBackHandled = { lastBackAt[0] = SystemClock.uptimeMillis() },
        onRefresh = { confirmRefresh = true },
        onInfo = { showInfo = true },
        onChannelMenu = { index, channel -> menuChannel = IndexedValue(index, channel) },
        refocusAfterDelete = refocusAfterDelete,
        onRefocused = { refocusAfterDelete = null },
        listState = listState,
        openedFrom = openedFrom,
        modifier = Modifier.fillMaxHeight().width(PanelWidth),
      )
    }
    AnimatedVisibility(
      visible = touchMode && !panelOpen,
      modifier = Modifier.align(Alignment.CenterEnd),
      enter = fadeIn(tween(PanelAnimMs)),
      exit = fadeOut(tween(PanelAnimMs)),
    ) {
      PanelHandle(onClick = ::openPanel)
    }

    if (confirmRefresh) {
      ConfirmDialog(
        title = "Refresh channels?",
        message = "This downloads the latest channel list from iptv-org. Any channels you deleted will come back.",
        confirmLabel = "Refresh",
        touchMode = touchMode,
        onConfirm = {
          confirmRefresh = false
          viewModel.onRefresh()
        },
        onDismiss = { confirmRefresh = false },
      )
    }

    if (confirmExit) {
      ConfirmDialog(
        title = "Exit MeghTV?",
        message = null,
        confirmLabel = "Exit",
        touchMode = touchMode,
        onConfirm = { activity?.finish() },
        onDismiss = { confirmExit = false },
      )
    }

    menuChannel?.let { (index, channel) ->
      ChannelMenuDialog(
        channel = channel,
        bookmarked = channel.id in state.bookmarkedIds,
        failed = channel.id in state.failedIds,
        touchMode = touchMode,
        onToggleBookmark = {
          menuChannel = null
          viewModel.onToggleBookmark(channel)
        },
        onDelete = {
          menuChannel = null
          if (!touchMode) refocusAfterDelete = IndexedValue(index, channel.id)
          viewModel.onDeleteChannel(channel.id)
        },
        onDismiss = { menuChannel = null },
      )
    }

    if (splashDone) state.refresh?.let { status -> RefreshDialog(status = status, touchMode = touchMode, onDismiss = viewModel::onDismissRefreshResult) }

    if (showSupport) SupportDialog(touchMode = touchMode, onDismiss = { showSupport = false })
    if (showInfo) InfoDialog(touchMode = touchMode, onDismiss = { showInfo = false })

    AnimatedVisibility(visible = !splashDone, enter = EnterTransition.None, exit = fadeOut(tween(SplashFadeMs))) { LoadingScreen() }
  }
}

/** [live] false (paused, or playing on behind the live edge) greys the LIVE tag. */
@Composable
private fun NowPlayingBadge(channel: ChannelEntity, live: Boolean, modifier: Modifier = Modifier) {
  val tagColor = if (live) MeghLive else MaterialTheme.colorScheme.onSurfaceVariant
  Row(
    modifier.widthIn(max = 420.dp).clip(RoundedCornerShape(8.dp)).background(MeghBackground.copy(alpha = 0.8f)).padding(horizontal = 12.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Row(
      Modifier.clip(RoundedCornerShape(4.dp)).background(tagColor.copy(alpha = 0.18f)).padding(horizontal = 8.dp, vertical = 3.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
      Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(tagColor))
      Text("LIVE", color = tagColor, style = MaterialTheme.typography.labelLarge)
    }
    Text(
      channel.displayName,
      color = MaterialTheme.colorScheme.onBackground,
      style = MaterialTheme.typography.labelLarge,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
private fun PanelHandle(onClick: () -> Unit, modifier: Modifier = Modifier) {
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  Box(
    modifier
      .size(width = PanelHandleWidth, height = 96.dp)
      // Opaque, so the video doesn't show through; lighter than the navy letterbox it sits on.
      .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
      .clickable(interactionSource = interaction, indication = null, onClick = onClick)
      .semantics { contentDescription = "Open menu" },
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      MeghIcons.ChevronLeft,
      contentDescription = null,
      tint = if (pressed) Color.White else MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(32.dp),
    )
  }
}

@Composable
private fun SidePanel(
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
  // Entry focus is the selected tab, not Refresh: a double OK to wake the panel would start a refresh.
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
          PinnedDivider(vertical = true)
          InfoButton(onInfo, Modifier.fillMaxHeight().width(48.dp))
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

private fun isSearch(panel: PanelState) = panel is PanelState.ChannelList && panel.source == ChannelListSource.Search

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

@Composable
private fun InfoButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
  val interaction = remember { MutableInteractionSource() }
  Box(modifier.panelRow(interaction, block = false, onClick = onClick).semantics { contentDescription = "About MeghTV" }, contentAlignment = Alignment.Center) {
    Icon(MeghIcons.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
  }
}

@Composable
private fun CornerInfoButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
  Box(
    modifier
      .size(44.dp)
      .clip(RoundedCornerShape(50))
      .background(MeghBackground.copy(alpha = 0.8f))
      .clickable(onClickLabel = "About MeghTV", onClick = onClick)
      .semantics { contentDescription = "About MeghTV" },
    contentAlignment = Alignment.Center,
  ) {
    Icon(MeghIcons.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
  }
}
