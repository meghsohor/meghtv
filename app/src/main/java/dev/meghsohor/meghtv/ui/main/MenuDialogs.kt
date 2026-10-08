package dev.meghsohor.meghtv.ui.main

import android.content.Context
import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.edit
import dev.meghsohor.meghtv.R
import dev.meghsohor.meghtv.data.RefreshProgress
import dev.meghsohor.meghtv.data.db.ChannelEntity
import dev.meghsohor.meghtv.theme.MeghLive
import dev.meghsohor.meghtv.ui.DialogBand
import dev.meghsohor.meghtv.ui.DialogButton
import dev.meghsohor.meghtv.ui.DialogCard
import dev.meghsohor.meghtv.ui.MeghIcons
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val RefreshResultAutoCloseMs = 4000L

@Composable
internal fun ConfirmDialog(
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
internal fun ChannelMenuDialog(
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
internal fun RefreshDialog(status: RefreshStatus, touchMode: Boolean, onDismiss: () -> Unit) {
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

// A TV usually has no browser, and paying with a remote is no fun: there it shows a code to scan with a phone instead.
@Composable
internal fun SupportDialog(touchMode: Boolean, onDismiss: () -> Unit) {
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
        if (isTv) KofiQrCode(Modifier.padding(top = 4.dp))
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

@Composable
private fun KofiQrCode(modifier: Modifier = Modifier) {
  Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
    Image(
      painterResource(R.drawable.kofi_qr),
      contentDescription = "QR code for $KofiDisplayUrl",
      modifier = Modifier.size(128.dp).clip(RoundedCornerShape(8.dp)),
    )
    Text("Scan with your phone, or visit $KofiDisplayUrl", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
  }
}

// A darker header and footer frame the scrolling feature list between them.
@Composable
internal fun InfoDialog(touchMode: Boolean, onDismiss: () -> Unit) {
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
          for (feature in AppFeatures) {
            Row {
              Text("•", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(16.dp))
              Text(feature, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
            }
          }
          Text(
            SupportText,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
          )
          if (isTv) KofiQrCode()
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
    "Pick a channel's source by hand; it plays first from then on",
    "Delete channels you don't want; a refresh brings them back",
    "Refresh to get the latest channel list",
  )

@Composable
private fun isTelevision(): Boolean = (LocalConfiguration.current.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION

/** Remembers the day the support prompt last showed, so it shows at most once a day. */
internal class SupportPrompt(context: Context) {
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
