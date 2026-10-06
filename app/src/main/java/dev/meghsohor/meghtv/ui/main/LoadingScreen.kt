package dev.meghsohor.meghtv.ui.main

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.meghsohor.meghtv.R
import dev.meghsohor.meghtv.theme.MeghBackground
import dev.meghsohor.meghtv.theme.MeghCyan

/** The banner under a near-opaque overlay, the logo in the middle with a ring spinning around it. Swallows touches. */
@Composable
internal fun LoadingScreen() {
  val spin = rememberInfiniteTransition(label = "splash")
  val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "angle")
  val track = MaterialTheme.colorScheme.surfaceVariant
  Box(
    Modifier.fillMaxSize().pointerInput(Unit) {
      awaitPointerEventScope {
        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
      }
    },
    contentAlignment = Alignment.Center,
  ) {
    Image(painterResource(R.drawable.tv_banner), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    Box(Modifier.fillMaxSize().background(MeghBackground.copy(alpha = 0.9f)))
    Box(contentAlignment = Alignment.Center) {
      Canvas(Modifier.size(176.dp)) {
        val stroke = 4.dp.toPx()
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawCircle(track, radius = size.minDimension / 2 - inset, style = Stroke(stroke))
        // A cyan arc fading out at its tail, turning once a second.
        rotate(angle) {
          drawArc(
            Brush.sweepGradient(listOf(Color.Transparent, MeghCyan.copy(alpha = 0.4f), MeghCyan)),
            startAngle = 0f,
            sweepAngle = 300f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(stroke, cap = StrokeCap.Round),
          )
        }
      }
      Image(painterResource(R.drawable.splash_logo), contentDescription = "MeghTV", modifier = Modifier.size(112.dp))
    }
  }
}
