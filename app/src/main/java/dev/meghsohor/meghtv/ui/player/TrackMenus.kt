package dev.meghsohor.meghtv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import dev.meghsohor.meghtv.ui.ChoiceRow
import dev.meghsohor.meghtv.ui.DialogBand
import dev.meghsohor.meghtv.ui.DialogCard
import dev.meghsohor.meghtv.ui.DialogLineColor
import dev.meghsohor.meghtv.ui.MeghIcons
import java.util.Locale

/** The player's choices, in the order their buttons appear. */
internal enum class TrackKind(val title: String, val trackType: Int) {
  Quality("Quality", C.TRACK_TYPE_VIDEO),
  Subtitles("Subtitles", C.TRACK_TYPE_TEXT),
  Audio("Audio", C.TRACK_TYPE_AUDIO);

  val icon
    get() =
      when (this) {
        Quality -> MeghIcons.Quality
        Subtitles -> MeghIcons.Captions
        Audio -> MeghIcons.Headphones
      }
}

internal class TrackOption(val label: String, val selected: Boolean, val apply: TrackSelectionParameters.Builder.() -> Unit)

/** One choice made of one or more tracks of the same group, e.g. an audio language's bitrate tiers. */
private class TrackChoice(val group: Tracks.Group, val indices: List<Int>) {
  val format: Format
    get() = group.getTrackFormat(indices.first())

  val selected
    get() = indices.any { group.isTrackSelected(it) }

  fun override() = TrackSelectionOverride(group.mediaTrackGroup, indices)
}

private class TrackRef(val group: Tracks.Group, val index: Int) {
  val format: Format
    get() = group.getTrackFormat(index)

  val selected
    get() = group.isTrackSelected(index)

  fun override() = TrackSelectionOverride(group.mediaTrackGroup, index)
}

private fun Tracks.supported(type: Int): List<TrackRef> =
  groups.filter { it.type == type }.flatMap { group -> (0 until group.length).filter { group.isTrackSupported(it) }.map { TrackRef(group, it) } }

/**
 * What [kind] offers right now; empty when there is nothing to choose between, so its button stays hidden.
 * [playingHeight] is the picture height being rendered, for the Auto label.
 */
internal fun trackOptions(kind: TrackKind, tracks: Tracks, params: TrackSelectionParameters, playingHeight: Int = 0): List<TrackOption> =
  when (kind) {
    TrackKind.Quality -> qualityOptions(tracks, params, playingHeight)
    TrackKind.Subtitles -> subtitleOptions(tracks, params)
    TrackKind.Audio -> audioOptions(tracks)
  }

private fun qualityOptions(tracks: Tracks, params: TrackSelectionParameters, playingHeight: Int): List<TrackOption> {
  // One choice per picture height: variants that differ only in bitrate aren't worth telling apart. A playlist that
  // gives no heights keeps each variant, labelled by bitrate.
  val variants =
    tracks.supported(C.TRACK_TYPE_VIDEO)
      .sortedWith(compareByDescending<TrackRef> { it.format.height }.thenByDescending { it.format.bitrate })
      .distinctBy { if (it.format.height > 0) "h${it.format.height}" else "b${it.format.bitrate}" }
  if (variants.size < 2) return emptyList()
  val pinned = params.overrides.values.any { it.type == C.TRACK_TYPE_VIDEO }
  val auto = TrackOption(if (!pinned && playingHeight > 0) "Auto (${playingHeight}p)" else "Auto", selected = !pinned) { clearOverridesOfType(C.TRACK_TYPE_VIDEO) }
  return listOf(auto) +
    variants.map { ref ->
      val f = ref.format
      val label = if (f.height > 0) "${f.height}p" else if (f.bitrate > 0) "${f.bitrate / 1000} kbps" else "Variant"
      TrackOption(label, selected = pinned && ref.selected) { clearOverridesOfType(C.TRACK_TYPE_VIDEO).addOverride(ref.override()) }
    }
}

// Media3 exposes a CEA-608 track on any TS stream that doesn't declare its captions, whether or not it carries any.
private fun TrackRef.isUndeclaredCaption(): Boolean {
  val f = format
  val cea = f.sampleMimeType == MimeTypes.APPLICATION_CEA608 || f.sampleMimeType == MimeTypes.APPLICATION_CEA708
  return cea && f.label.isNullOrBlank() && (f.language.isNullOrBlank() || f.language == C.LANGUAGE_UNDETERMINED)
}

private fun subtitleOptions(tracks: Tracks, params: TrackSelectionParameters): List<TrackOption> {
  val tracksAvailable = tracks.supported(C.TRACK_TYPE_TEXT).filterNot { it.isUndeclaredCaption() }
  if (tracksAvailable.isEmpty()) return emptyList()
  val off = C.TRACK_TYPE_TEXT in params.disabledTrackTypes || tracksAvailable.none { it.selected }
  val labels = labelsFor(tracksAvailable, fallback = "Subtitles")
  return listOf(TrackOption("Off", selected = off) { clearOverridesOfType(C.TRACK_TYPE_TEXT).setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true) }) +
    tracksAvailable.mapIndexed { i, ref ->
      TrackOption(labels[i], selected = !off && ref.selected) {
        setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).clearOverridesOfType(C.TRACK_TYPE_TEXT).addOverride(ref.override())
      }
    }
}

private fun audioOptions(tracks: Tracks): List<TrackOption> {
  // Tracks of one group that share language, label and channels are bitrate tiers of one choice: picking it keeps them
  // all, so Media3 still adapts between them.
  val choices =
    tracks.supported(C.TRACK_TYPE_AUDIO)
      .groupBy { Triple(it.group, it.format.language to it.format.label, it.format.channelCount) }
      .values
      .map { refs -> TrackChoice(refs.first().group, refs.map { it.index }) }
  if (choices.size < 2) return emptyList()
  val labels = labelsFor(choices.map { it.format }, fallback = "Audio")
  return choices.mapIndexed { i, choice ->
    TrackOption(labels[i], selected = choice.selected) { clearOverridesOfType(C.TRACK_TYPE_AUDIO).addOverride(choice.override()) }
  }
}

private fun labelsFor(refs: List<TrackRef>, fallback: String): List<String> = labelsFor(refs.map { it.format }, fallback)

/** The stream's own label, else the language, else [fallback]; numbered where two would read the same. */
@JvmName("labelsForFormats")
private fun labelsFor(formats: List<Format>, fallback: String): List<String> {
  val base =
    formats.map { format ->
      format.label?.takeIf { it.isNotBlank() }
        ?: format.language?.takeIf { it.isNotBlank() && it != C.LANGUAGE_UNDETERMINED }?.let { tag ->
          Locale.forLanguageTag(tag).getDisplayLanguage(Locale.getDefault()).replaceFirstChar { it.titlecase(Locale.getDefault()) }.ifBlank { tag }
        }
        ?: fallback
    }
  val counts = base.groupingBy { it }.eachCount()
  val seen = mutableMapOf<String, Int>()
  // Not Map.merge: it needs API 24.
  return base.map { label ->
    if (counts.getValue(label) > 1) {
      val n = (seen[label] ?: 0) + 1
      seen[label] = n
      "$label $n"
    } else {
      label
    }
  }
}

/** A choice list in the app's dialog style: picking applies and closes it; Back closes it. */
@Composable
internal fun TrackPickerDialog(kind: TrackKind, options: List<TrackOption>, touchMode: Boolean, onPick: (TrackOption) -> Unit, onDismiss: () -> Unit) {
  val selectedFocus = remember { FocusRequester() }
  Dialog(onDismissRequest = onDismiss) {
    // A remote starts on the current choice; touch needs no focus ring.
    LaunchedEffect(Unit) { if (!touchMode) runCatching { selectedFocus.requestFocus() } }
    DialogCard {
      Column {
        Text(
          kind.title,
          color = MaterialTheme.colorScheme.onSurface,
          style = MaterialTheme.typography.titleMedium,
          modifier =
            Modifier.fillMaxWidth()
              .background(DialogBand)
              .drawBehind { drawRect(DialogLineColor, topLeft = Offset(0f, size.height - 1.dp.toPx()), size = size.copy(height = 1.dp.toPx())) }
              .padding(horizontal = 24.dp, vertical = 16.dp),
        )
        Column(
          Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(8.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          val focusIndex = options.indexOfFirst { it.selected }.coerceAtLeast(0)
          options.forEachIndexed { i, option ->
            ChoiceRow(
              option.label,
              selected = option.selected,
              onClick = { onPick(option) },
              modifier = if (i == focusIndex) Modifier.focusRequester(selectedFocus) else Modifier,
            )
          }
        }
      }
    }
  }
}
