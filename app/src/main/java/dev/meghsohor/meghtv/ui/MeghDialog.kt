package dev.meghsohor.meghtv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.drawBehind
import dev.meghsohor.meghtv.theme.MeghBackground
import dev.meghsohor.meghtv.theme.MeghSurface

// The dialog parts shared by the menu and the player, so every popup looks the same.

internal val DialogLineColor = Color.White.copy(alpha = 0.09f)
internal val DialogBand = lerp(MeghSurface, MeghBackground, 0.5f)

@Composable
internal fun DialogCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
  val shape = RoundedCornerShape(16.dp)
  Box(modifier.widthIn(min = 300.dp, max = 420.dp).clip(shape).background(MaterialTheme.colorScheme.surface).border(1.dp, DialogLineColor, shape)) { content() }
}

/** [secondary] is the way out (Cancel, Not now): muted, with a fainter outline. */
@Composable
internal fun DialogButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, secondary: Boolean = false) {
  val interaction = remember { MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()
  val color = if (secondary) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
  val outline = if (focused) color else color.copy(alpha = if (secondary) 0.2f else 0.45f)
  Text(
    label,
    color = color,
    style = MaterialTheme.typography.titleSmall,
    modifier =
      modifier
        .clip(RoundedCornerShape(8.dp))
        .background(if (focused) color.copy(alpha = 0.15f) else Color.Transparent)
        .border(1.dp, outline, RoundedCornerShape(8.dp))
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
        .focusable(interactionSource = interaction)
        .padding(horizontal = 16.dp, vertical = 8.dp),
  )
}

/** One choice in a picker: the current one is cyan with a check; D-pad focus draws a ring. */
@Composable
internal fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
  val interaction = remember { MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()
  val colors = MaterialTheme.colorScheme
  Row(
    modifier
      .fillMaxWidth()
      .heightIn(min = 48.dp)
      .drawBehind {
        val radius = CornerRadius(10.dp.toPx())
        if (focused) drawRoundRect(colors.surfaceVariant, cornerRadius = radius)
        if (focused) {
          val w = 2.dp.toPx()
          drawRoundRect(colors.primary, topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w), cornerRadius = radius, style = Stroke(w))
        }
      }
      .clickable(interactionSource = interaction, indication = null, onClick = onClick)
      .focusable(interactionSource = interaction)
      .padding(horizontal = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      label,
      color = if (selected) colors.primary else colors.onSurface,
      style = MaterialTheme.typography.bodyLarge,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f),
    )
    if (selected) {
      Spacer(Modifier.width(12.dp))
      Icon(MeghIcons.Check, contentDescription = "Selected", tint = colors.primary, modifier = Modifier.size(20.dp))
    }
  }
}
