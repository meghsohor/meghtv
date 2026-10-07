package dev.meghsohor.meghtv.ui.player

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.meghsohor.meghtv.theme.MeghBackground
import dev.meghsohor.meghtv.theme.MeghCyan
import dev.meghsohor.meghtv.theme.MeghLive
import dev.meghsohor.meghtv.theme.MeghOnSurfaceMuted
import dev.meghsohor.meghtv.ui.MeghIcons

@OptIn(ExperimentalMaterial3Api::class) // Slider's track slot
@Composable
internal fun ControlsRow(
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
 * Source, Quality, Subtitles and Audio, always shown; one with nothing to choose is dimmed, inert and skipped by the
 * remote. The enabled ones are focusable, so a TV remote reaches them with Down; Up or Back leaves (handled by the screen).
 * [sourceBadge] is the playing source as "2/5", null with a single source.
 */
@Composable
internal fun TrackControls(
  sourceBadge: String?,
  enabledKinds: Set<TrackKind>,
  qualityBadge: String?,
  focusRequester: FocusRequester,
  onOpenSources: () -> Unit,
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
    // The remote lands on the first enabled button.
    val sourcesEnabled = sourceBadge != null
    val firstEnabledKind = TrackKind.entries.firstOrNull { it in enabledKinds }.takeUnless { sourcesEnabled }
    ControlButton("Source", sourcesEnabled, focusRequester.takeIf { sourcesEnabled }, onActivity, onOpenSources) { tint ->
      if (sourceBadge != null) TextBadge(sourceBadge, tint) else Icon(MeghIcons.Sources, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
    }
    TrackKind.entries.forEach { kind ->
      key(kind) {
        ControlButton(kind.title, kind in enabledKinds, focusRequester.takeIf { kind == firstEnabledKind }, onActivity, { onOpen(kind) }) { tint ->
          if (kind == TrackKind.Quality && qualityBadge != null) TextBadge(qualityBadge, tint)
          else Icon(kind.icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        }
      }
    }
  }
}

@Composable
private fun ControlButton(
  title: String,
  enabled: Boolean,
  focusRequester: FocusRequester?,
  onActivity: () -> Unit,
  onClick: () -> Unit,
  content: @Composable (tint: Color) -> Unit,
) {
  val interaction = remember { MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()
  Box(
    Modifier.size(48.dp)
      .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
      .clip(CircleShape)
      .background(if (focused) MeghCyan.copy(alpha = 0.2f) else Color.Transparent)
      .border(2.dp, if (focused) MeghCyan else Color.Transparent, CircleShape)
      // Disabled: neither clickable nor focusable, so D-pad Left/Right skip it.
      .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClickLabel = title) {
        onActivity()
        onClick()
      }
      .semantics { contentDescription = if (enabled) title else "$title (not available)" },
    contentAlignment = Alignment.Center,
  ) {
    content(if (enabled) MeghCyan else Color.White.copy(alpha = 0.3f))
  }
}

/** SD/HD/FHD/4K for the picture being played; null before its size is known. */
internal fun qualityBadge(height: Int): String? =
  when {
    height >= 2160 -> "4K"
    height >= 1080 -> "FHD"
    height >= 720 -> "HD"
    height > 0 -> "SD"
    else -> null
  }

/** Short text in an outlined box, as an icon (the quality, the source number); the stroke matches the other icons. */
@Composable
private fun TextBadge(text: String, tint: Color) {
  Text(
    text,
    color = tint,
    fontSize = 11.sp,
    fontWeight = FontWeight.Bold,
    lineHeight = 11.sp,
    maxLines = 1,
    modifier = Modifier.border(2.dp, tint, RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp),
  )
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
internal fun PlaybackErrorOverlay(
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
internal fun PlayPausePulse(playing: Boolean, trigger: Int, modifier: Modifier = Modifier) {
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
