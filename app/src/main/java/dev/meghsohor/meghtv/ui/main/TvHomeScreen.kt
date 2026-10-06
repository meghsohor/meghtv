package dev.meghsohor.meghtv.ui.main

import android.content.Context
import android.content.res.Configuration
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.edit
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.meghsohor.meghtv.R
import dev.meghsohor.meghtv.data.MeghTVRepository
import dev.meghsohor.meghtv.data.RefreshProgress
import dev.meghsohor.meghtv.data.db.CategoryEntity
import dev.meghsohor.meghtv.data.db.ChannelEntity
import dev.meghsohor.meghtv.data.db.CountryEntity
import dev.meghsohor.meghtv.theme.MeghBackground
import dev.meghsohor.meghtv.theme.MeghLive
import dev.meghsohor.meghtv.theme.MeghSurface
import dev.meghsohor.meghtv.theme.MeghSurfaceVariant
import dev.meghsohor.meghtv.ui.DialogBand
import dev.meghsohor.meghtv.ui.DialogButton
import dev.meghsohor.meghtv.ui.DialogCard
import dev.meghsohor.meghtv.ui.MeghIcons
import dev.meghsohor.meghtv.ui.player.LiveState
import dev.meghsohor.meghtv.ui.player.PlayerCommand
import dev.meghsohor.meghtv.ui.player.TrackControlsState
import dev.meghsohor.meghtv.ui.player.VideoPlayer
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

private val RowGutter = 10.dp
private val RowGap = 6.dp
private val RowRadius = 12.dp
// Three nested layers told apart by tone alone, each a step lighter than the one around it:
// the pinned tabs (outermost), a drilled-in list's header, then the list itself.
private val PinnedBand = lerp(MeghSurface, MeghBackground, 0.4f)
private val HeaderBand = MeghSurface
private val ListTone = lerp(MeghSurface, MeghSurfaceVariant, 0.55f)
private val PanelBackground = ListTone.copy(alpha = 0.97f)
// Opaque and a shade darker than the list layer, so rows read as set into it.
private val RowFill = lerp(ListTone, MeghBackground, 0.15f)
private val LineColor = Color.White.copy(alpha = 0.09f)

// A list shorter than this isn't worth an in-list search box.
private const val InListSearchThreshold = 10

private val DimmedRowFill = MeghSurfaceVariant.copy(alpha = 0.2f)
private val DimmedLineColor = Color.White.copy(alpha = 0.04f)
private const val DimmedTextAlpha = 0.5f

/**
 * A [block] (list row) is a rounded block with a hairline border, fainter when [dimmed]; a pinned tab
 * only fills when selected, pressed or focused. Selected is brand cyan (pair with [rowContentColor]),
 * and D-pad focus draws a 2dp ring. One drawBehind, no clip or animation: those cost per row on a low-end TV.
 */
@Composable
private fun Modifier.panelRow(
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
private fun PinnedDivider(vertical: Boolean) {
  Box(if (vertical) Modifier.width(1.dp).height(24.dp).background(LineColor) else Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(LineColor))
}

@Composable
private fun rowContentColor(selected: Boolean): Color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

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
private fun CategoriesMenuContent(
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
private fun CountriesMenuContent(
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
private fun ChannelListContent(
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

private const val RefreshResultAutoCloseMs = 4000L

@Composable
private fun ConfirmDialog(
  title: String,
  message: String?,
  confirmLabel: String,
  touchMode: Boolean,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  // Cancel first, so OK pressed twice can't confirm.
  val cancelFocus = remember { FocusRequester() }
  Dialog(onDismissRequest = onDismiss) {
    // Inside the dialog, whose content is composed in its own window.
    LaunchedEffect(Unit) { if (!touchMode) cancelFocus.requestFocus() }
    DialogCard {
      Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
        if (message != null) Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
          DialogButton("Cancel", onClick = onDismiss, modifier = Modifier.focusRequester(cancelFocus), secondary = true)
          DialogButton(confirmLabel, onClick = onConfirm)
        }
      }
    }
  }
}

@Composable
private fun ChannelMenuDialog(
  channel: ChannelEntity,
  bookmarked: Boolean,
  failed: Boolean,
  touchMode: Boolean,
  onToggleBookmark: () -> Unit,
  onDelete: () -> Unit,
  onDismiss: () -> Unit,
) {
  val firstFocus = remember { FocusRequester() }
  // Opened by holding OK: its repeats and release would click the first option. Keys count from a fresh press.
  var armed by remember { mutableStateOf(false) }
  Dialog(onDismissRequest = onDismiss) {
    LaunchedEffect(Unit) { if (!touchMode) firstFocus.requestFocus() }
    DialogCard {
      Column(
        Modifier.padding(16.dp).onPreviewKeyEvent { event ->
          if (!armed && event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) armed = true
          !armed
        },
        verticalArrangement = Arrangement.spacedBy(RowGap),
      ) {
        Text(
          channel.displayName,
          color = MaterialTheme.colorScheme.onSurface,
          style = MaterialTheme.typography.titleMedium,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(start = 6.dp, top = 4.dp),
        )
        if (failed) {
          Text("Didn't play last time", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 6.dp))
        }
        Spacer(Modifier.height(4.dp))
        MenuOption(if (bookmarked) "Remove from favourites" else "Add to favourites", MeghIcons.Star, onToggleBookmark, Modifier.focusRequester(firstFocus))
        MenuOption("Delete channel", MeghIcons.Delete, onDelete, tint = MeghLive)
        Text(
          "A deleted channel comes back with the next refresh.",
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          style = MaterialTheme.typography.labelMedium,
          modifier = Modifier.padding(start = 6.dp, top = 4.dp),
        )
      }
    }
  }
}

@Composable
private fun MenuOption(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.primary) {
  val interaction = remember { MutableInteractionSource() }
  Row(
    modifier.fillMaxWidth().panelRow(interaction, onClick = onClick).heightIn(min = 48.dp).padding(horizontal = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    Spacer(Modifier.width(14.dp))
    Text(label, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
  }
}

// One card for progress and result, the same size throughout: only the texts and the bar change.
@Composable
private fun RefreshDialog(status: RefreshStatus, touchMode: Boolean, onDismiss: () -> Unit) {
  val running = status is RefreshStatus.Running
  LaunchedEffect(status is RefreshStatus.Done) {
    if (status is RefreshStatus.Done) {
      delay(RefreshResultAutoCloseMs)
      onDismiss()
    }
  }
  val closeFocus = remember { FocusRequester() }
  Dialog(
    onDismissRequest = { if (!running) onDismiss() },
    properties = DialogProperties(dismissOnBackPress = !running, dismissOnClickOutside = false),
  ) {
    LaunchedEffect(running) { if (!running && !touchMode) runCatching { closeFocus.requestFocus() } }
    val texts = refreshTexts(status)
    DialogCard(Modifier.width(RefreshDialogWidth)) {
      Column(Modifier.padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(texts.title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
        RefreshBar(status)
        // Fixed line counts, so the card keeps its size whatever the step or message.
        Text(texts.line, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
          texts.detail,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          style = MaterialTheme.typography.labelMedium,
          minLines = 2,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        // Held empty while running, so the card doesn't grow when Close appears.
        Box(Modifier.fillMaxWidth().height(40.dp)) {
          if (!running) DialogButton("Close", onClick = onDismiss, modifier = Modifier.align(Alignment.CenterEnd).focusRequester(closeFocus))
        }
      }
    }
  }
}

@Composable
private fun RefreshBar(status: RefreshStatus) {
  val modifier = Modifier.fillMaxWidth().height(4.dp)
  val track = MaterialTheme.colorScheme.surfaceVariant
  val progress = (status as? RefreshStatus.Running)?.progress
  when {
    progress is RefreshProgress.Playlists && progress.total > 0 -> {
      val fraction by animateFloatAsState(progress.done.toFloat() / progress.total, label = "refresh")
      LinearProgressIndicator(progress = { fraction }, modifier = modifier, trackColor = track)
    }
    status is RefreshStatus.Running -> LinearProgressIndicator(modifier = modifier, trackColor = track)
    status is RefreshStatus.Done -> LinearProgressIndicator(progress = { 1f }, modifier = modifier, trackColor = track)
    else -> LinearProgressIndicator(progress = { 1f }, modifier = modifier, color = MeghLive, trackColor = track)
  }
}

private class RefreshTexts(val title: String, val line: String, val detail: String)

private fun refreshTexts(status: RefreshStatus): RefreshTexts {
  val number = NumberFormat.getIntegerInstance()
  return when (status) {
    is RefreshStatus.Running ->
      when (val progress = status.progress) {
        RefreshProgress.ChannelInfo -> RefreshTexts("Refreshing channels", "Downloading channel info…", "")
        is RefreshProgress.Playlists ->
          RefreshTexts(
            "Refreshing channels",
            "Downloading playlists: ${progress.done} of ${progress.total}",
            "${number.format(progress.channelsFound)} channels found",
          )
        RefreshProgress.CombinedPlaylist -> RefreshTexts("Refreshing channels", "Downloading the channel list…", "")
        is RefreshProgress.Saving -> RefreshTexts("Refreshing channels", "Saving ${number.format(progress.channels)} channels…", "")
      }
    is RefreshStatus.Done -> {
      val result = status.result
      val changes =
        listOfNotNull(
          "${number.format(result.added)} new",
          "${number.format(result.removed)} removed",
          if (result.bookmarksRemoved > 0) "${result.bookmarksRemoved} ${if (result.bookmarksRemoved == 1) "favourite" else "favourites"} removed" else null,
        )
      RefreshTexts("Channels updated", "${number.format(result.total)} channels", changes.joinToString(" · "))
    }
    is RefreshStatus.Failed ->
      RefreshTexts("Refresh failed", "Couldn't download the channel list.", listOfNotNull("Check the connection and try again.", status.reason).joinToString("\n"))
  }
}

@Composable
private fun InfoButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
  val interaction = remember { MutableInteractionSource() }
  Box(modifier.panelRow(interaction, block = false, onClick = onClick).semantics { contentDescription = "About MeghTV" }, contentAlignment = Alignment.Center) {
    Icon(MeghIcons.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
  }
}

// A TV usually has no browser, and paying with a remote is no fun: there it shows a code to scan with a phone instead.
@Composable
private fun SupportDialog(touchMode: Boolean, onDismiss: () -> Unit) {
  val isTv = isTelevision() || !touchMode
  val uriHandler = LocalUriHandler.current
  val firstFocus = remember { FocusRequester() }
  Dialog(onDismissRequest = onDismiss) {
    LaunchedEffect(Unit) { if (!touchMode) firstFocus.requestFocus() }
    DialogCard {
      Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(MeghIcons.Coffee, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
          Spacer(Modifier.width(12.dp))
          Text("Support MeghTV", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
        }
        Text(
          SupportText,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          style = MaterialTheme.typography.bodyMedium,
        )
        if (isTv) {
          Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Image(
              painterResource(R.drawable.kofi_qr),
              contentDescription = "QR code for $KofiDisplayUrl",
              modifier = Modifier.size(128.dp).clip(RoundedCornerShape(8.dp)),
            )
            Text("Scan with your phone, or visit $KofiDisplayUrl", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
          }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
          if (isTv) {
            DialogButton("Close", onClick = onDismiss, modifier = Modifier.focusRequester(firstFocus))
          } else {
            DialogButton("Not now", onClick = onDismiss, modifier = Modifier.focusRequester(firstFocus), secondary = true)
            DialogButton(
              "Buy me a coffee",
              onClick = {
                runCatching { uriHandler.openUri(KofiUrl) }
                onDismiss()
              },
            )
          }
        }
      }
    }
  }
}

// A darker header and footer frame the scrolling feature list between them.
@Composable
private fun InfoDialog(touchMode: Boolean, onDismiss: () -> Unit) {
  val isTv = isTelevision() || !touchMode
  val scroll = rememberScrollState()
  val scope = rememberCoroutineScope()
  val uriHandler = LocalUriHandler.current
  val context = LocalContext.current
  val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() }
  val closeFocus = remember { FocusRequester() }
  Dialog(onDismissRequest = onDismiss) {
    LaunchedEffect(Unit) { if (!touchMode) closeFocus.requestFocus() }
    DialogCard {
      Column {
        Column(
          Modifier.fillMaxWidth()
            .background(DialogBand)
            .drawBehind { drawRect(LineColor, topLeft = Offset(0f, size.height - 1.dp.toPx()), size = size.copy(height = 1.dp.toPx())) }
            .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
          Text("MeghTV", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
          if (version != null) Text("Version $version", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        }
        // A remote can't scroll by itself: with a larger TV font the body may not fit, so Up/Down scroll it from Close.
        Column(
          Modifier.weight(1f, fill = false).verticalScroll(scroll).padding(horizontal = 24.dp, vertical = 16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            if (isTv) "$SupportText Visit $KofiDisplayUrl." else SupportText,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 8.dp),
          )
          for (feature in AppFeatures) {
            Row {
              Text("•", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(16.dp))
              Text(feature, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
            }
          }
          Text(
            "Channels are publicly available streams listed by the iptv-org project. MeghTV doesn't host any of them.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 8.dp),
          )
        }
        Box(
          Modifier.fillMaxWidth()
            .background(DialogBand)
            .drawBehind { drawRect(LineColor, size = size.copy(height = 1.dp.toPx())) }
            .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
          Row(Modifier.align(Alignment.CenterEnd), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!isTv) DialogButton("Buy me a coffee", onClick = { runCatching { uriHandler.openUri(KofiUrl) } })
            DialogButton(
              "Close",
              onClick = onDismiss,
              modifier =
                Modifier.focusRequester(closeFocus).onPreviewKeyEvent { event ->
                  val step = when (event.key) {
                    Key.DirectionUp -> -InfoScrollStepPx
                    Key.DirectionDown -> InfoScrollStepPx
                    else -> return@onPreviewKeyEvent false
                  }
                  if (event.type == KeyEventType.KeyDown && scroll.maxValue > 0) scope.launch { scroll.animateScrollBy(step) }
                  scroll.maxValue > 0
                },
            )
          }
        }
      }
    }
  }
}

private const val InfoScrollStepPx = 240f

private val AppFeatures =
  listOf(
    "Thousands of live TV channels, by category or country",
    "Search all channels, or within a category or country",
    "Favourites, newest first",
    "Works with a TV remote or by touch",
    "Channel Up/Down on a remote switches channels",
    "A paused channel resumes where it was paused; Go live jumps back to the live picture",
    "Remembers mute and volume between channels",
    "Pick the picture quality, subtitles or audio track when a stream offers them",
    "Tries a channel's backup streams when one fails, and marks channels that didn't play",
    "Delete channels you don't want; a refresh brings them back",
    "Refresh to get the latest channel list",
  )

@Composable
private fun isTelevision(): Boolean = (LocalConfiguration.current.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION

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

/** Remembers the day the support prompt last showed, so it shows at most once a day. */
private class SupportPrompt(context: Context) {
  private val prefs = context.getSharedPreferences("support_prompt", Context.MODE_PRIVATE)

  private fun today() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

  fun dueToday() = prefs.getString(LastShownKey, null) != today()

  fun markShown() = prefs.edit { putString(LastShownKey, today()) }

  private companion object {
    const val LastShownKey = "last_shown_day"
  }
}

private const val SupportText = "MeghTV is free to use and has no ads. If you like it, you can support its development to help keep it that way."
private const val KofiUrl = "https://ko-fi.com/Z5Z8281UOM"
private const val KofiDisplayUrl = "ko-fi.com/Z5Z8281UOM"
private val RefreshDialogWidth = 400.dp
