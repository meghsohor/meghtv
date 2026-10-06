package dev.meghsohor.meghtv.ui.player

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.ui.R as Media3R
import dev.meghsohor.meghtv.theme.MeghBackground
import dev.meghsohor.meghtv.theme.MeghCyan
import dev.meghsohor.meghtv.theme.MeghLive
import dev.meghsohor.meghtv.theme.MeghOnSurfaceMuted
import dev.meghsohor.meghtv.ui.MeghIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

private const val ControlsAutoHideMs = 5000

/** Live-edge rejoins before a stream counts as failed. */
private const val MaxLiveRejoins = 3

private const val AudioPrefsName = "player_audio"
private const val MutedKey = "muted"
private const val VolumeKey = "volume"

/** Behind live by less than this after a pause isn't worth a Go live button. */
private const val GoLiveMinBehindMs = 3_000L

enum class PlayerCommand { TogglePlayPause, Play, Pause, ShowControls, HideControls, GoLive, FocusTrackControls }

/** [available]: at least one of Quality, Subtitles or Audio has a choice, so the remote can reach it. [focused]: one has D-pad focus. */
data class TrackControlsState(val available: Boolean = false, val focused: Boolean = false)

/** [behind]: paused, or playing on behind the live edge. [goLiveOffered]: the Go live chip is up. */
data class LiveState(val behind: Boolean = false, val goLiveOffered: Boolean = false)

/**
 * Plays [streamUrls] in order, moving on when one fails; the error screen shows once all have failed.
 * [channelId] restarts playback on a switch even when two channels share the same URL list.
 * [onTap] returns true when it used the tap. [onAllSourcesFailed] isn't called while offline.
 */
@Composable
fun VideoPlayer(
  channelId: String,
  streamUrls: List<String>,
  controlsAllowed: Boolean,
  touchControls: Boolean,
  onTap: () -> Boolean,
  onControlsVisibilityChange: (Boolean) -> Unit,
  onPlaybackActiveChange: (Boolean) -> Unit,
  onAllSourcesFailed: () -> Unit,
  onPlaying: () -> Unit,
  onDeleteChannel: () -> Unit,
  modifier: Modifier = Modifier,
  onLiveStateChange: (LiveState) -> Unit = {},
  onTrackControlsChange: (TrackControlsState) -> Unit = {},
  overlayEndPadding: Dp = 0.dp,
  controlsEdgeInset: Dp = 0.dp,
  playerCommands: Flow<PlayerCommand> = emptyFlow(),
) {
  val context = LocalContext.current
  val player = remember {
    // Low-end TV chips can fail to open their preferred hardware decoder for a stream's profile.
    ExoPlayer.Builder(context, DefaultRenderersFactory(context).setEnableDecoderFallback(true))
      .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
      .setHandleAudioBecomingNoisy(true)
      .build()
  }
  // Unkeyed: the player listener closes over these once; the load effect resets them on a switch.
  var attempt by remember { mutableStateOf(SourceAttempt()) }
  var retryTick by remember(channelId, streamUrls) { mutableIntStateOf(0) }
  var playbackError by remember { mutableStateOf<PlaybackException?>(null) }
  var keepScreenOn by remember { mutableStateOf(false) }
  var buffering by remember { mutableStateOf(false) }
  var controlsVisible by remember { mutableStateOf(false) }
  var tracks by remember { mutableStateOf(Tracks.EMPTY) }
  var openMenu by remember { mutableStateOf<TrackKind?>(null) }
  var trackControlsFocused by remember { mutableStateOf(false) }
  val trackControlsFocus = remember { FocusRequester() }
  var playerView by remember { mutableStateOf<PlayerView?>(null) }
  // Kept across channel switches and app restarts.
  val audioPrefs = remember { context.getSharedPreferences(AudioPrefsName, Context.MODE_PRIVATE) }
  var muted by remember { mutableStateOf(audioPrefs.getBoolean(MutedKey, false)) }
  var playing by remember { mutableStateOf(true) }
  // Replays the centre animation.
  var pulse by remember { mutableIntStateOf(0) }
  var volume by remember { mutableFloatStateOf(audioPrefs.getFloat(VolumeKey, 1f)) }
  val currentStreamUrls by rememberUpdatedState(streamUrls)
  val currentOnTap by rememberUpdatedState(onTap)
  val currentControlsAllowed by rememberUpdatedState(controlsAllowed)
  val currentOnControlsVisibilityChange by rememberUpdatedState(onControlsVisibilityChange)
  val currentOnAllSourcesFailed by rememberUpdatedState(onAllSourcesFailed)
  val currentOnPlaying by rememberUpdatedState(onPlaying)
  val liveRejoins = remember { intArrayOf(0) }
  // A resumed live stream plays on from where it was paused. Most streams carry no wall clock, so how far behind it is
  // is the time spent paused (or held back by another app's audio) since the last join with the live edge. Where the
  // stream does carry one, the measured offset replaces it: the player then also creeps back to live by itself.
  var behindLiveMs by remember { mutableLongStateOf(0L) }
  val pausedSince = remember { longArrayOf(0L) }
  val edgeOffsetMs = remember { longArrayOf(C.TIME_UNSET) }
  var isLive by remember { mutableStateOf(false) }
  fun heldBack() = !player.playWhenReady || player.playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE
  fun joinedLiveEdge() {
    behindLiveMs = 0L
    pausedSince[0] = if (heldBack()) SystemClock.elapsedRealtime() else 0L
    edgeOffsetMs[0] = C.TIME_UNSET
  }
  fun updateHeldBack() {
    val now = SystemClock.elapsedRealtime()
    if (heldBack()) {
      if (pausedSince[0] == 0L) pausedSince[0] = now
    } else if (pausedSince[0] > 0L) {
      behindLiveMs += now - pausedSince[0]
      pausedSince[0] = 0L
    }
  }

  // Each new attempt relaunches the load effect; the final failure doesn't, so nothing reloads behind the error screen.
  fun onSourceFailed(error: PlaybackException) {
    val url = currentStreamUrls.getOrNull(attempt.index)
    attempt =
      when {
        error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED && !attempt.asHls && url != null && !url.looksLikeHls() ->
          attempt.copy(asHls = true)
        attempt.index + 1 < currentStreamUrls.size -> SourceAttempt(attempt.index + 1)
        else -> {
          playbackError = error
          if (!context.isOffline(error)) currentOnAllSourcesFailed()
          return
        }
      }
  }

  DisposableEffect(player) {
    val listener =
      object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
          // Paused past the live window: the source is fine, rejoin at the live edge.
          if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW && liveRejoins[0] < MaxLiveRejoins) {
            liveRejoins[0]++
            player.seekToDefaultPosition()
            player.prepare()
            joinedLiveEdge()
            return
          }
          // A pinned quality takes away Media3's fallback to the other variants: drop the pin and try this source again
          // before counting the source as broken.
          if (player.trackSelectionParameters.overrides.values.any { it.type == C.TRACK_TYPE_VIDEO }) {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO).build()
            player.prepare()
            return
          }
          onSourceFailed(error)
        }

        // Paused: the controls stay up over a dimmed picture.
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
          playing = playWhenReady
          updateHeldBack()
          val view = playerView ?: return
          view.findViewById<View>(Media3R.id.exo_controls_background)?.background = edgeScrim(view.resources.displayMetrics.density, dimmed = !playWhenReady)
          view.controllerShowTimeoutMs = if (playWhenReady && openMenu == null) ControlsAutoHideMs else 0
          if (view.isControllerFullyVisible) view.showController() // re-arm with the new timeout
        }

        // Another app's audio (a call, the Assistant) holds playback back without a pause.
        override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) = updateHeldBack()

        override fun onTracksChanged(newTracks: Tracks) {
          tracks = newTracks
        }

        override fun onEvents(player: Player, events: Player.Events) {
          val state = player.playbackState
          keepScreenOn = player.playWhenReady && state != Player.STATE_IDLE && state != Player.STATE_ENDED
          buffering = state == Player.STATE_BUFFERING
          isLive = player.isCurrentMediaItemLive
          // The first reading at the live edge, for streams that report their offset.
          if (state == Player.STATE_READY && behindLiveMs == 0L && edgeOffsetMs[0] == C.TIME_UNSET) edgeOffsetMs[0] = player.currentLiveOffset
          if (state == Player.STATE_READY) liveRejoins[0] = 0
        }

        // Not STATE_READY: that is also reached paused, before anything has played.
        override fun onIsPlayingChanged(isPlaying: Boolean) {
          if (isPlaying) currentOnPlaying()
        }
      }
    player.addListener(listener)
    onDispose {
      player.removeListener(listener)
      player.release()
    }
  }

  // PlayerView doesn't keep the screen on by itself.
  val hostView = LocalView.current
  DisposableEffect(keepScreenOn) {
    hostView.keepScreenOn = keepScreenOn
    onDispose { hostView.keepScreenOn = false }
  }

  // Live TV can't resume: disconnect in the background, rejoin at the live edge.
  val lifecycleOwner = LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner, player) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_STOP -> player.stop()
        Lifecycle.Event.ON_START ->
          if (player.mediaItemCount > 0 && player.playbackState == Player.STATE_IDLE && playbackError == null) {
            player.seekToDefaultPosition()
            player.prepare()
            joinedLiveEdge()
          }
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  val playbackActive = playing && playbackError == null
  val currentOnPlaybackActiveChange by rememberUpdatedState(onPlaybackActiveChange)
  LaunchedEffect(playbackActive) { currentOnPlaybackActiveChange(playbackActive) }

  LaunchedEffect(muted, volume) {
    player.volume = if (muted) 0f else volume
    audioPrefs.edit {
      putBoolean(MutedKey, muted)
      putFloat(VolumeKey, volume)
    }
  }

  // Where the stream reports its offset, follow it while playing behind: the player closes the gap at up to 1.03x.
  LaunchedEffect(behindLiveMs > 0L && playing) {
    if (behindLiveMs == 0L || !playing) return@LaunchedEffect
    while (true) {
      delay(2_000)
      val offset = player.currentLiveOffset
      if (offset == C.TIME_UNSET || edgeOffsetMs[0] == C.TIME_UNSET || pausedSince[0] > 0L) continue
      behindLiveMs = (offset - edgeOffsetMs[0]).coerceAtLeast(0L).let { if (it < GoLiveMinBehindMs) 0L else it }
      if (behindLiveMs == 0L) break
    }
  }

  val behind = isLive && playbackError == null && (!playing || behindLiveMs >= GoLiveMinBehindMs)
  // On TV only once playing again: while paused the controls stay up, and Right must still open the panel.
  val goLiveOffered = behind && (touchControls || playing)
  val currentOnLiveStateChange by rememberUpdatedState(onLiveStateChange)
  LaunchedEffect(behind, goLiveOffered) { currentOnLiveStateChange(LiveState(behind, goLiveOffered)) }
  // Gone with the player: nothing is behind, and no controls are up.
  DisposableEffect(Unit) {
    onDispose {
      currentOnLiveStateChange(LiveState())
      currentOnControlsVisibilityChange(false)
    }
  }

  fun setPlaying(play: Boolean) {
    if (player.playWhenReady == play) return
    player.playWhenReady = play
    pulse++
    if (currentControlsAllowed && playbackError == null) playerView?.showController()
  }

  // Media3's hide timer doesn't see touches or keys on our Compose controls.
  fun keepControlsAlive() {
    playerView?.takeIf { it.isControllerFullyVisible }?.showController()
  }

  // All three buttons always show; one is enabled only while the stream offers a choice. A pick changes tracks too.
  val enabledKinds = remember(tracks) { TrackKind.entries.filter { trackOptions(it, tracks, player.trackSelectionParameters).isNotEmpty() }.toSet() }
  val currentOnTrackControlsChange by rememberUpdatedState(onTrackControlsChange)
  val trackControlsShown = controlsVisible && playbackError == null
  val trackControlsReachable = trackControlsShown && enabledKinds.isNotEmpty()
  LaunchedEffect(trackControlsReachable, trackControlsFocused) {
    currentOnTrackControlsChange(TrackControlsState(trackControlsReachable, trackControlsReachable && trackControlsFocused))
  }
  DisposableEffect(Unit) { onDispose { currentOnTrackControlsChange(TrackControlsState()) } }
  // An open picker holds the controls up, so the button it came from is still there to return to.
  LaunchedEffect(openMenu) {
    val view = playerView ?: return@LaunchedEffect
    view.controllerShowTimeoutMs = if (openMenu == null && player.playWhenReady) ControlsAutoHideMs else 0
    if (view.isControllerFullyVisible) view.showController()
  }

  fun goLive() {
    player.seekToDefaultPosition()
    setPlaying(true)
    joinedLiveEdge()
  }

  LaunchedEffect(player, playerCommands) {
    playerCommands.collect { command ->
      when (command) {
        PlayerCommand.HideControls -> playerView?.hideController()
        PlayerCommand.ShowControls -> if (currentControlsAllowed && playbackError == null) playerView?.showController()
        PlayerCommand.Play -> setPlaying(true)
        PlayerCommand.Pause -> setPlaying(false)
        PlayerCommand.TogglePlayPause -> setPlaying(!player.playWhenReady)
        PlayerCommand.GoLive -> goLive()
        PlayerCommand.FocusTrackControls -> {
          keepControlsAlive()
          runCatching { trackControlsFocus.requestFocus() }
        }
      }
    }
  }

  // The channel attempt and playbackError belong to; a switch resets them here, so it loads once, at the first mirror.
  val loadedFor = remember { arrayOfNulls<Pair<String, List<String>>>(1) }
  LaunchedEffect(channelId, streamUrls, attempt, retryTick) {
    val key = channelId to streamUrls
    if (loadedFor[0] != key) {
      loadedFor[0] = key
      playbackError = null
      liveRejoins[0] = 0
      if (attempt != SourceAttempt()) {
        attempt = SourceAttempt() // relaunches this effect, at the first mirror
        return@LaunchedEffect
      }
    }
    val url = streamUrls.getOrNull(attempt.index)
    if (url == null) {
      player.stop()
      player.clearMediaItems()
    } else {
      // A fresh decoder per stream: a reused one can leave the old channel's larger frame around a smaller new one.
      player.stop()
      // A picked quality or track belongs to the stream it was picked on.
      player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().clearOverrides().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
      openMenu = null
      // Every load, a backup source too, starts at the live edge, with its own offset baseline.
      behindLiveMs = 0L
      pausedSince[0] = 0L
      edgeOffsetMs[0] = C.TIME_UNSET
      // A format with no Media3 module throws here instead of reporting a playback error.
      try {
        player.setMediaItem(MediaItem.Builder().setUri(url).apply { if (attempt.asHls) setMimeType(MimeTypes.APPLICATION_M3U8) }.build())
        player.prepare()
      } catch (e: IllegalStateException) {
        onSourceFailed(PlaybackException("Unsupported stream format", e, PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED))
        return@LaunchedEffect
      }
      player.playWhenReady = true
    }
  }

  Box(modifier) {
    AndroidView(
      modifier = Modifier.fillMaxSize(),
      factory = {
        PlayerView(it).apply {
          useController = true
          // Out of the focus chain: the controller grabbed D-pad focus, and the app handles the remote keys itself.
          descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
          isFocusable = false
          controllerAutoShow = false
          controllerShowTimeoutMs = ControlsAutoHideMs
          // Animated, Media3 reports the controller visible for ~2 s after it has gone.
          setControllerAnimationEnabled(false)
          setShowRewindButton(false)
          setShowFastForwardButton(false)
          setShowPreviousButton(false)
          setShowNextButton(false)
          setShowSubtitleButton(false)
          // Play/pause is in ControlsRow; the centre only gets PlayPausePulse.
          findViewById<View>(Media3R.id.exo_center_controls)?.visibility = View.GONE
          // No setter for the time bar.
          findViewById<View>(Media3R.id.exo_progress)?.visibility = View.GONE
          findViewById<View>(Media3R.id.exo_time)?.visibility = View.GONE

          findViewById<View>(Media3R.id.exo_controls_background)?.background = edgeScrim(resources.displayMetrics.density, dimmed = false)
          // Its settings and CC buttons and their popups are replaced by TrackControls and TrackPickerDialog.
          findViewById<View>(Media3R.id.exo_bottom_bar)?.visibility = View.GONE

          setControllerVisibilityListener(
            PlayerView.ControllerVisibilityListener { visibility ->
              controlsVisible = visibility == View.VISIBLE
              currentOnControlsVisibilityChange(controlsVisible)
            }
          )
          installTapHandler { view ->
            if (!currentOnTap()) {
              when {
                playbackError != null -> Unit
                view.isControllerFullyVisible -> view.hideController()
                currentControlsAllowed -> view.showController()
              }
            }
          }
          playerView = this
        }
      },
      update = { view ->
        view.player = player
        // Controls left under the error screen would still take taps and Back.
        if (!controlsAllowed || playbackError != null) view.hideController()
      },
    )

    if (controlsVisible && playbackError == null) {
      ControlsRow(
        playing = playing,
        onTogglePlay = { setPlaying(!player.playWhenReady) },
        onGoLive = if (goLiveOffered) ::goLive else null,
        touchControls = touchControls,
        muted = muted || volume == 0f,
        volume = volume,
        onToggleMute = {
          if (muted || volume == 0f) {
            muted = false
            if (volume == 0f) volume = 1f
          } else {
            muted = true
          }
        },
        onVolumeChange = {
          volume = it
          if (it > 0f) muted = false
        },
        onTouch = ::keepControlsAlive,
        // Excluded from the edge back-swipe zone, which swallowed taps on play/pause.
        modifier = Modifier.align(Alignment.BottomStart).height(60.dp).systemGestureExclusion().padding(start = controlsEdgeInset),
      )
    }

    if (trackControlsShown) {
      TrackControls(
        enabledKinds = enabledKinds,
        focusRequester = trackControlsFocus,
        onOpen = { openMenu = it },
        onFocusChange = { trackControlsFocused = it },
        onActivity = ::keepControlsAlive,
        modifier = Modifier.align(Alignment.BottomEnd).height(60.dp).systemGestureExclusion().padding(end = controlsEdgeInset),
      )
    }

    openMenu?.let { kind ->
      val options = trackOptions(kind, tracks, player.trackSelectionParameters, player.videoFormat?.height ?: 0)
      if (options.isEmpty()) {
        LaunchedEffect(Unit) { openMenu = null }
      } else {
        TrackPickerDialog(
          kind,
          options,
          touchMode = touchControls,
          onPick = { option ->
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply(option.apply).build()
            openMenu = null
          },
          onDismiss = { openMenu = null },
        )
      }
    }

    // Centred on the part of the video the panel doesn't cover.
    PlayPausePulse(playing = playing, trigger = pulse, modifier = Modifier.align(Alignment.Center).padding(end = overlayEndPadding))

    if (buffering && playbackError == null) {
      CircularProgressIndicator(
        color = MeghCyan,
        strokeWidth = 3.dp,
        modifier = Modifier.align(Alignment.Center).padding(end = overlayEndPadding).size(48.dp),
      )
    }

    playbackError?.let { error ->
      PlaybackErrorOverlay(
        offline = remember(error) { context.isOffline(error) },
        onDelete = onDeleteChannel,
        onRetry = {
          attempt = SourceAttempt()
          playbackError = null
          retryTick++ // re-runs the load effect even at the first mirror
        },
        endPadding = overlayEndPadding,
        focusRetry = controlsAllowed,
        modifier = Modifier.fillMaxSize(),
      )
    }
  }
}

/** Dark behind the top and bottom controls; the middle is clear, or [dimmed] while paused. */
private fun edgeScrim(density: Float, dimmed: Boolean): Drawable {
  fun fade(orientation: GradientDrawable.Orientation, alpha: Float) =
    GradientDrawable(orientation, intArrayOf(MeghBackground.copy(alpha = alpha).toArgb(), android.graphics.Color.TRANSPARENT))
  val dim = ColorDrawable(if (dimmed) MeghBackground.copy(alpha = 0.55f).toArgb() else android.graphics.Color.TRANSPARENT)
  return LayerDrawable(arrayOf(dim, fade(GradientDrawable.Orientation.TOP_BOTTOM, 0.7f), fade(GradientDrawable.Orientation.BOTTOM_TOP, 0.85f))).apply {
    setLayerGravity(1, Gravity.TOP)
    setLayerHeight(1, (120 * density).toInt())
    setLayerGravity(2, Gravity.BOTTOM)
    setLayerHeight(2, (160 * density).toInt())
  }
}

/** Media3 picks the format from the URL extension; ~300 URLs have none, so an unrecognized one gets one more try as HLS. */
private data class SourceAttempt(val index: Int = 0, val asHls: Boolean = false)

private fun String.looksLikeHls() = substringBefore('?').contains(".m3u8", ignoreCase = true)

/** Consumes every touch, so PlayerView's own tap-to-toggle doesn't also run; the controller's buttons get theirs first. */
@SuppressLint("ClickableViewAccessibility")
private fun PlayerView.installTapHandler(onTap: (PlayerView) -> Unit) {
  val view = this
  val detector =
    GestureDetector(
      context,
      object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true

        // Not onSingleTapConfirmed: with no double tap to wait for, that only adds ~300 ms.
        override fun onSingleTapUp(e: MotionEvent): Boolean {
          onTap(view)
          return true
        }
      },
    )
      // Double-tap tracking would swallow a quick second tap.
      .apply { setOnDoubleTapListener(null) }
  setOnTouchListener { _, event ->
    detector.onTouchEvent(event)
    true
  }
}

@OptIn(ExperimentalMaterial3Api::class) // Slider's track slot
@Composable
private fun ControlsRow(
  playing: Boolean,
  onTogglePlay: () -> Unit,
  onGoLive: (() -> Unit)?,
  touchControls: Boolean,
  muted: Boolean,
  volume: Float,
  onToggleMute: () -> Unit,
  onVolumeChange: (Float) -> Unit,
  onTouch: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val currentOnTouch by rememberUpdatedState(onTouch)
  Row(
    modifier.pointerInput(Unit) {
      awaitPointerEventScope {
        while (true) {
          awaitPointerEvent(PointerEventPass.Initial)
          currentOnTouch()
        }
      }
    },
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // On TV only a state indicator, and so not a D-pad stop: OK and the media keys toggle it.
    Box(
      Modifier.size(48.dp).clip(CircleShape).then(if (touchControls) Modifier.clickable(onClick = onTogglePlay) else Modifier),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        if (playing) MeghIcons.Pause else MeghIcons.Play,
        contentDescription = if (playing) "Pause" else "Play",
        tint = MeghCyan,
        modifier = Modifier.size(30.dp),
      )
    }
    // Last in the row, so it doesn't shift mute and volume when it appears. On TV the Right key (or fast-forward) does
    // it, so the chip shows that arrow instead of taking focus.
    val goLiveChip = @Composable { if (onGoLive != null) GoLiveChip(onClick = onGoLive, touchControls = touchControls, modifier = Modifier.padding(start = 12.dp)) }
    if (!touchControls) {
      goLiveChip()
      return@Row
    }
    Box(
      Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onToggleMute),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        if (muted) MeghIcons.VolumeOff else MeghIcons.VolumeUp,
        contentDescription = if (muted) "Unmute" else "Mute",
        tint = MeghCyan,
      )
    }
    val colors = SliderDefaults.colors(thumbColor = MeghCyan, activeTrackColor = MeghCyan, inactiveTrackColor = Color.White.copy(alpha = 0.3f))
    // The default 16dp track is too heavy over video.
    Slider(
      value = if (muted) 0f else volume,
      onValueChange = onVolumeChange,
      modifier = Modifier.width(140.dp),
      colors = colors,
      thumb = { Box(Modifier.size(14.dp).background(MeghCyan, CircleShape)) },
      track = { sliderState ->
        SliderDefaults.Track(
          sliderState = sliderState,
          colors = colors,
          drawStopIndicator = null,
          thumbTrackGapSize = 0.dp,
          modifier = Modifier.height(4.dp),
        )
      },
    )
    goLiveChip()
  }
}

/**
 * Quality, Subtitles and Audio, always shown; one with nothing to choose is dimmed, inert and skipped by the remote.
 * The enabled ones are focusable, so a TV remote reaches them with Down; Up or Back leaves (handled by the screen).
 */
@Composable
private fun TrackControls(
  enabledKinds: Set<TrackKind>,
  focusRequester: FocusRequester,
  onOpen: (TrackKind) -> Unit,
  onFocusChange: (Boolean) -> Unit,
  onActivity: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val currentOnFocusChange by rememberUpdatedState(onFocusChange)
  DisposableEffect(Unit) { onDispose { currentOnFocusChange(false) } }
  Row(
    modifier.onFocusChanged { currentOnFocusChange(it.hasFocus) }.onPreviewKeyEvent {
      onActivity()
      false
    },
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    val firstEnabled = TrackKind.entries.firstOrNull { it in enabledKinds }
    TrackKind.entries.forEach { kind ->
      key(kind) {
        val enabled = kind in enabledKinds
        val interaction = remember { MutableInteractionSource() }
        val focused by interaction.collectIsFocusedAsState()
        Box(
          Modifier.size(48.dp)
            .then(if (kind == firstEnabled) Modifier.focusRequester(focusRequester) else Modifier)
            .clip(CircleShape)
            .background(if (focused) MeghCyan.copy(alpha = 0.2f) else Color.Transparent)
            .border(2.dp, if (focused) MeghCyan else Color.Transparent, CircleShape)
            // Disabled: neither clickable nor focusable, so D-pad Left/Right skip it.
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClickLabel = kind.title) {
              onActivity()
              onOpen(kind)
            }
            .semantics { contentDescription = if (enabled) kind.title else "${kind.title} (not available)" },
          contentAlignment = Alignment.Center,
        ) {
          Icon(kind.icon, contentDescription = null, tint = if (enabled) MeghCyan else Color.White.copy(alpha = 0.3f), modifier = Modifier.size(24.dp))
        }
      }
    }
  }
}

@Composable
private fun GoLiveChip(onClick: () -> Unit, touchControls: Boolean, modifier: Modifier = Modifier) {
  Row(
    modifier
      .clip(RoundedCornerShape(50))
      .background(MeghBackground.copy(alpha = 0.7f))
      .border(1.dp, MeghLive.copy(alpha = 0.6f), RoundedCornerShape(50))
      .then(if (touchControls) Modifier.clickable(onClickLabel = "Go live", onClick = onClick) else Modifier)
      .padding(horizontal = 14.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Box(Modifier.size(8.dp).background(MeghLive, CircleShape))
    Text("Go live", color = Color.White, style = MaterialTheme.typography.labelLarge)
    if (!touchControls) Icon(MeghIcons.ChevronRight, contentDescription = null, tint = MeghCyan, modifier = Modifier.size(18.dp))
  }
}

@Composable
private fun PlaybackErrorOverlay(
  offline: Boolean,
  onRetry: () -> Unit,
  onDelete: () -> Unit,
  endPadding: Dp,
  focusRetry: Boolean,
  modifier: Modifier = Modifier,
) {
  val retryFocusRequester = remember { FocusRequester() }
  // Not while the panel is open: it would pull focus out of the list.
  LaunchedEffect(focusRetry) { if (focusRetry) retryFocusRequester.requestFocus() }

  Box(modifier.background(MeghBackground.copy(alpha = 0.92f)).padding(end = endPadding), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(
        if (offline) "No internet connection" else "This channel isn't available right now",
        color = Color.White,
        style = MaterialTheme.typography.titleMedium,
      )
      Text(
        if (offline) "Check your network and try again." else "All of its sources failed to load.",
        color = MeghOnSurfaceMuted,
        style = MaterialTheme.typography.bodyMedium,
      )
      Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OverlayButton("Retry", onClick = onRetry, primary = true, modifier = Modifier.focusRequester(retryFocusRequester))
        // Offline says nothing about the channel.
        if (!offline) OverlayButton("Delete channel", onClick = onDelete, primary = false)
      }
    }
  }
}

/** An HTTP error means a server answered, so never offline; otherwise ask the device. */
private fun Context.isOffline(error: PlaybackException) =
  error.errorCode != PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS && !hasInternet()

private fun Context.hasInternet(): Boolean {
  val connectivity = getSystemService(ConnectivityManager::class.java) ?: return true
  val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
  // VALIDATED: Wi-Fi without internet, or a captive portal, still claims INTERNET.
  return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

@Composable
private fun OverlayButton(label: String, onClick: () -> Unit, primary: Boolean, modifier: Modifier = Modifier) {
  val interaction = remember { MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()
  val shape = RoundedCornerShape(8.dp)
  Text(
    label,
    color = if (primary) MeghBackground else Color.White,
    style = MaterialTheme.typography.titleSmall,
    modifier =
      modifier
        .clip(shape)
        .then(
          if (primary) Modifier.background(if (focused) MeghCyan else MeghCyan.copy(alpha = 0.85f))
          else Modifier.background(if (focused) Color.White.copy(alpha = 0.18f) else Color.Transparent).border(1.dp, Color.White.copy(alpha = if (focused) 0.9f else 0.4f), shape)
        )
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
        .focusable(interactionSource = interaction)
        .padding(horizontal = 24.dp, vertical = 10.dp),
  )
}

// Softened: full cyan glares over the picture.
private val PulseColor = MeghCyan.copy(alpha = 0.75f)

/** Zooms in and fades out on each change of [trigger]; nothing on first composition. */
@Composable
private fun PlayPausePulse(playing: Boolean, trigger: Int, modifier: Modifier = Modifier) {
  val progress = remember { Animatable(1f) }
  LaunchedEffect(trigger) {
    if (trigger == 0) return@LaunchedEffect
    progress.snapTo(0f)
    progress.animateTo(1f, tween(durationMillis = 650, easing = LinearOutSlowInEasing))
  }
  if (progress.value >= 1f) return
  Canvas(
    modifier.size(96.dp).graphicsLayer {
      val p = progress.value
      scaleX = 0.7f + 0.5f * p
      scaleY = scaleX
      alpha = ((1f - p) / 0.7f).coerceAtMost(1f) // holds full strength for the first 30%
    }
  ) {
    val stroke = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round, cap = StrokeCap.Round)
    drawCircle(MeghBackground.copy(alpha = 0.35f))
    drawCircle(PulseColor, radius = size.minDimension / 2 - stroke.width, style = stroke)
    val u = size.minDimension / 24f // icon drawn on a 24-unit grid
    if (playing) {
      val triangle = Path().apply {
        moveTo(9.5f * u, 7.5f * u)
        lineTo(17f * u, 12f * u)
        lineTo(9.5f * u, 16.5f * u)
        close()
      }
      drawPath(triangle, PulseColor, style = stroke)
    } else {
      drawRoundRect(PulseColor, topLeft = Offset(8.5f * u, 7.5f * u), size = Size(2.5f * u, 9f * u), cornerRadius = CornerRadius(u / 2), style = stroke)
      drawRoundRect(PulseColor, topLeft = Offset(13f * u, 7.5f * u), size = Size(2.5f * u, 9f * u), cornerRadius = CornerRadius(u / 2), style = stroke)
    }
  }
}
