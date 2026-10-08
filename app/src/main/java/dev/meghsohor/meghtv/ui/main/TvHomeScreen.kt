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
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import dev.meghsohor.meghtv.ui.MeghIcons
import dev.meghsohor.meghtv.ui.player.LiveState
import dev.meghsohor.meghtv.ui.player.PlayerCommand
import dev.meghsohor.meghtv.ui.player.TrackControlsState
import dev.meghsohor.meghtv.ui.player.VideoPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow

private val PanelWidth = 360.dp
private val PanelHandleWidth = 44.dp
private const val PanelAnimMs = 220
private const val SplashMs = 3_000L
private const val SplashFadeMs = 400
private const val PanelAutoHideDelayMs = 7000L
private const val SameBackPressWindowMs = 200L
private val PanelNavigationKeys =
  setOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight, Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

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
  var confirmExit by remember { mutableStateOf(false) }
  var showSupport by rememberSaveable { mutableStateOf(false) }
  var showInfo by remember { mutableStateOf(false) }
  var searchFieldFocused by remember { mutableStateOf(false) }
  var playbackActive by remember { mutableStateOf(false) }
  var playbackFailed by remember { mutableStateOf(false) }
  var channelLoading by remember { mutableStateOf(false) }
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
  // Waits its turn behind any other popup and typing; a check at launch can finish during the loading screen.
  val showUpdateOffer =
    splashDone && state.updateOffer != null && !refreshInProgress && !showSupport && !showInfo && menuChannel == null && !confirmExit &&
      !trackControls.pickerOpen && !searching
  val suppressAutoHide = !splashDone || refreshInProgress || showUpdateOffer || menuChannel != null || confirmExit || showSupport || showInfo || searching || nothingToWatch
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
  val updateOfferPending = state.updateOffer != null || state.checkingInBackground
  LaunchedEffect(resumeCount, splashDone, state.startupPanelChosen, refreshInProgress, updateOfferPending, hasChannels, showInfo) {
    if (splashDone && state.startupPanelChosen && !refreshInProgress && !updateOfferPending && hasChannels && !showInfo && supportPrompt.dueToday()) {
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
        // In the player's Source/Quality/Subtitles/Audio buttons: Left/Right move between them, Up leaves, Down stays.
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
        sources = state.currentSources,
        onSourcePicked = { viewModel.onSourcePicked(currentChannel.id, it) },
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
        onPlaybackFailedChange = { playbackFailed = it },
        onLoadingChange = { channelLoading = it },
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
        previousChannelName = state.previousChannel?.displayName,
        nextChannelName = state.nextChannel?.displayName,
        onPreviousChannel = viewModel::onChannelDown,
        onNextChannel = viewModel::onChannelUp,
        modifier = Modifier.fillMaxSize(),
      )
      // Also while a channel loads and on the error screen, so it says which channel that is; LIVE is greyed until it plays.
      if (panelOpen || playerControlsVisible || playbackFailed || channelLoading) {
        NowPlayingBadge(channel = currentChannel, live = !liveState.behind && !playbackFailed && !channelLoading, modifier = Modifier.align(Alignment.TopStart).padding(16.dp))
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
    // Lined up with the menu tab below it, which is as wide.
    if (touchMode) CornerInfoButton(onClick = { showInfo = true }, modifier = Modifier.align(Alignment.TopEnd).padding(top = 16.dp))

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
        onRefresh = viewModel::onCheckForUpdate,
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

    state.updateOffer?.takeIf { showUpdateOffer }?.let { offer ->
      UpdateOfferDialog(
        offer = offer,
        touchMode = touchMode,
        onUpdate = { viewModel.onApplyUpdate(full = false) },
        onFullRefresh = { viewModel.onApplyUpdate(full = true) },
        onDismiss = viewModel::onDismissUpdateOffer,
      )
    }
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
private fun CornerInfoButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
  // As wide as the menu tab, with the icon a touch in from its left edge (the user's choice).
  Box(
    modifier
      .size(PanelHandleWidth)
      .clickable(onClickLabel = "About MeghTV", indication = null, interactionSource = null, onClick = onClick)
      .semantics { contentDescription = "About MeghTV" },
    contentAlignment = Alignment.CenterStart,
  ) {
    Icon(MeghIcons.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp).size(22.dp))
  }
}
