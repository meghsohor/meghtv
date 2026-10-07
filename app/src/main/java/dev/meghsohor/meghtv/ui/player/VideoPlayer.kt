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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
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
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.ui.R as Media3R
import dev.meghsohor.meghtv.theme.MeghBackground
import dev.meghsohor.meghtv.theme.MeghCyan
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

/** [available]: at least one of Source, Quality, Subtitles or Audio has a choice, so the remote can reach it. [focused]: one has D-pad focus. */
data class TrackControlsState(val available: Boolean = false, val focused: Boolean = false)

/** [behind]: paused, or playing on behind the live edge. [goLiveOffered]: the Go live chip is up. */
data class LiveState(val behind: Boolean = false, val goLiveOffered: Boolean = false)

/**
 * Plays [streamUrls] in order, moving on when one fails; the error screen shows once all have failed.
 * [sources] are the same URLs in listed order, for the Source picker; a pick goes to [onSourcePicked], which should
 * put it first in [streamUrls].
 * [channelId] restarts playback on a switch even when two channels share the same URL list.
 * [onTap] returns true when it used the tap. [onAllSourcesFailed] isn't called while offline.
 */
@Composable
fun VideoPlayer(
  channelId: String,
  streamUrls: List<String>,
  sources: List<String>,
  onSourcePicked: (String) -> Unit,
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
  // Caption tracks offered on this source; reset on each load.
  var keptCaptions by remember { mutableStateOf(emptySet<String>()) }
  // The rendered picture height, shown on the Quality button.
  var videoHeight by remember { mutableIntStateOf(0) }
  var openMenu by remember { mutableStateOf<TrackKind?>(null) }
  var sourcesOpen by remember { mutableStateOf(false) }
  // Sources that failed on this channel, marked in the Source picker; one is unmarked when it plays, all on Retry or a switch.
  var failedSources by remember { mutableStateOf(emptySet<String>()) }
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
    val retryAsHls = error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED && !attempt.asHls && url != null && !url.looksLikeHls()
    if (!retryAsHls && url != null) failedSources = failedSources + url
    attempt =
      when {
        retryAsHls -> attempt.copy(asHls = true)
        attempt.index + 1 < currentStreamUrls.size -> SourceAttempt(attempt.index + 1)
        else -> {
          playbackError = error
          // A picker left open would sit over the error screen, and a pick from it would play behind that screen.
          openMenu = null
          sourcesOpen = false
          if (!context.isOffline(error)) currentOnAllSourcesFailed()
          return
        }
      }
  }

  DisposableEffect(player) {
    val listener =
      object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
          val params = player.trackSelectionParameters
          // Paused past the live window: the source is fine, rejoin at the live edge. Fallen behind with a quality pinned,
          // the connection can't keep up with it: back to Auto too, or it stalls and rejoins forever.
          if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW && liveRejoins[0] < MaxLiveRejoins) {
            liveRejoins[0]++
            if (params.overrides.values.any { it.type == C.TRACK_TYPE_VIDEO }) {
              player.trackSelectionParameters = params.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO).build()
            }
            player.seekToDefaultPosition()
            player.prepare()
            joinedLiveEdge()
            return
          }
          // A picked quality, subtitle or audio track takes away Media3's own fallback: drop the picks and try this
          // source again at the live edge before counting it as broken. Once only: the picks are gone after this.
          if (params.overrides.isNotEmpty()) {
            player.trackSelectionParameters = params.buildUpon().clearOverrides().build()
            player.seekToDefaultPosition()
            player.prepare()
            joinedLiveEdge()
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
          view.controllerShowTimeoutMs = if (playWhenReady && openMenu == null && !sourcesOpen) ControlsAutoHideMs else 0
          if (view.isControllerFullyVisible) view.showController() // re-arm with the new timeout
        }

        // Another app's audio (a call, the Assistant) holds playback back without a pause.
        override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) = updateHeldBack()

        override fun onTracksChanged(newTracks: Tracks) {
          keptCaptions = keptCaptions + captionsToKeep(newTracks)
          tracks = newTracks
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
          videoHeight = videoSize.height
        }

        override fun onEvents(player: Player, events: Player.Events) {
          val state = player.playbackState
          keepScreenOn = player.playWhenReady && state != Player.STATE_IDLE && state != Player.STATE_ENDED
          buffering = state == Player.STATE_BUFFERING
          isLive = player.isCurrentMediaItemLive
          // The first reading at the live edge, for streams that report their offset.
          if (state == Player.STATE_READY && behindLiveMs == 0L && edgeOffsetMs[0] == C.TIME_UNSET) edgeOffsetMs[0] = player.currentLiveOffset
          if (state == Player.STATE_READY) {
            liveRejoins[0] = 0
            currentStreamUrls.getOrNull(attempt.index)?.let { if (it in failedSources) failedSources = failedSources - it }
          }
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

  // The track buttons always show; one is enabled only while the stream offers a choice. A pick changes tracks too.
  val enabledKinds =
    remember(tracks, keptCaptions) {
      TrackKind.entries.filter { trackOptions(it, tracks, player.trackSelectionParameters, keptCaptions = keptCaptions).isNotEmpty() }.toSet()
    }
  val playingSource = streamUrls.getOrNull(attempt.index)
  val sourceNumber = sources.indexOf(playingSource) + 1
  val sourceBadge = if (sources.size > 1 && sourceNumber > 0) "$sourceNumber/${sources.size}" else null
  val currentOnTrackControlsChange by rememberUpdatedState(onTrackControlsChange)
  val trackControlsShown = controlsVisible && playbackError == null
  val trackControlsReachable = trackControlsShown && (enabledKinds.isNotEmpty() || sourceBadge != null)
  LaunchedEffect(trackControlsReachable, trackControlsFocused) {
    currentOnTrackControlsChange(TrackControlsState(trackControlsReachable, trackControlsReachable && trackControlsFocused))
  }
  DisposableEffect(Unit) { onDispose { currentOnTrackControlsChange(TrackControlsState()) } }
  // An open picker holds the controls up, so the button it came from is still there to return to.
  LaunchedEffect(openMenu, sourcesOpen) {
    val view = playerView ?: return@LaunchedEffect
    view.controllerShowTimeoutMs = if (openMenu == null && !sourcesOpen && player.playWhenReady) ControlsAutoHideMs else 0
    if (view.isControllerFullyVisible) view.showController()
  }

  // Plays the pick here and now; the saved pick only reorders streamUrls, which the load effect plays on through.
  fun pickSource(url: String) {
    sourcesOpen = false
    onSourcePicked(url) // the playing one too: that's how to keep a fallback that worked
    val index = streamUrls.indexOf(url)
    if (index < 0 || (url == playingSource && playbackError == null)) return
    playbackError = null
    attempt = SourceAttempt(index)
    retryTick++ // reloads even when the index doesn't change
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
  // The URL handed to the player, and whether the next run of the load effect should leave it playing.
  val loadedUrl = remember { arrayOfNulls<String>(1) }
  val keepLoaded = remember { booleanArrayOf(false) }
  LaunchedEffect(channelId, streamUrls, attempt, retryTick) {
    val key = channelId to streamUrls
    if (loadedFor[0] != key) {
      val sameChannel = loadedFor[0]?.first == channelId
      loadedFor[0] = key
      if (!sameChannel) {
        failedSources = emptySet()
        sourcesOpen = false
      }
      // Same channel, its list only reordered or edited (a saved pick, a refresh): play on, at the source's new place.
      val kept = if (sameChannel && playbackError == null) streamUrls.indexOf(loadedUrl[0]) else -1
      if (kept >= 0) {
        if (attempt.index != kept) {
          keepLoaded[0] = true
          attempt = attempt.copy(index = kept) // relaunches this effect, which then leaves the player alone
        }
        return@LaunchedEffect
      }
      playbackError = null
      liveRejoins[0] = 0
      if (attempt != SourceAttempt()) {
        attempt = SourceAttempt() // relaunches this effect, at the first mirror
        return@LaunchedEffect
      }
    }
    if (keepLoaded[0]) {
      keepLoaded[0] = false
      return@LaunchedEffect
    }
    val url = streamUrls.getOrNull(attempt.index)
    loadedUrl[0] = url
    if (url == null) {
      player.stop()
      player.clearMediaItems()
    } else {
      // A fresh decoder per stream: a reused one can leave the old channel's larger frame around a smaller new one.
      player.stop()
      keptCaptions = emptySet()
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
        sourceBadge = sourceBadge,
        enabledKinds = enabledKinds,
        qualityBadge = qualityBadge(videoHeight),
        focusRequester = trackControlsFocus,
        onOpenSources = { sourcesOpen = true },
        onOpen = { openMenu = it },
        onFocusChange = { trackControlsFocused = it },
        onActivity = ::keepControlsAlive,
        modifier = Modifier.align(Alignment.BottomEnd).height(60.dp).systemGestureExclusion().padding(end = controlsEdgeInset),
      )
    }

    openMenu?.let { kind ->
      val options = trackOptions(kind, tracks, player.trackSelectionParameters, player.videoFormat?.height ?: 0, keptCaptions)
      if (options.isEmpty()) {
        LaunchedEffect(Unit) { openMenu = null }
      } else {
        TrackPickerDialog(
          kind.title,
          options.map { it.label },
          selectedIndex = options.indexOfFirst { it.selected },
          touchMode = touchControls,
          onPick = { i ->
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply(options[i].apply).build()
            openMenu = null
          },
          onDismiss = { openMenu = null },
        )
      }
    }

    if (sourcesOpen && playbackError == null) {
      TrackPickerDialog(
        "Source",
        sourceLabels(sources, failedSources),
        selectedIndex = sources.indexOf(playingSource),
        touchMode = touchControls,
        onPick = { pickSource(sources[it]) },
        onDismiss = { sourcesOpen = false },
      )
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
          failedSources = emptySet()
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
