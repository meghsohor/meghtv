package dev.meghsohor.meghtv.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// Paths after Lucide (lucide.dev, ISC licence), inlined instead of an icon library.
// The solid ones are filled and stroked in one colour, which rounds their corners.
object MeghIcons {
  val ChevronLeft = line("ChevronLeft", "M15,18l-6,-6 6,-6")
  val ChevronRight = line("ChevronRight", "M9,18l6,-6 -6,-6")
  val ArrowBack = line("ArrowBack", "M12,19l-7,-7 7,-7", "M19,12H5")
  val Refresh =
    line(
      "Refresh",
      "M3,12a9,9 0,0 1,9,-9 9.75,9.75 0,0 1,6.74,2.74L21,8",
      "M21,3v5h-5",
      "M21,12a9,9 0,0 1,-9,9 9.75,9.75 0,0 1,-6.74,-2.74L3,16",
      "M8,16H3v5",
    )
  val Search = line("Search", "M11,3a8,8 0,1 0,0,16a8,8 0,1 0,0,-16z", "M21,21l-4.3,-4.3")
  val Close = line("Close", "M6,6l12,12", "M6,18l12,-12")
  val ClearCircle = line("ClearCircle", "M12,3a9,9 0,1 0,0,18a9,9 0,1 0,0,-18z", "M15,9l-6,6", "M9,9l6,6")
  val Grid = line("Grid", roundRect(3f, 3f), roundRect(14f, 3f), roundRect(14f, 14f), roundRect(3f, 14f))
  val Star = line("Star", StarPath)
  val StarFilled = solid("StarFilled", StarPath)
  val Play = solid("Play", "M7,4.5l12,7.5 -12,7.5z")
  val Pause = solid("Pause", roundRect(6f, 4f, 3f, 16f, 0.5f), roundRect(15f, 4f, 3f, 16f, 0.5f))
  val Stop = solid("Stop", roundRect(5.5f, 5.5f, 13f, 13f, 1.5f))
  val StopOutline = line("StopOutline", roundRect(5f, 5f, 14f, 14f, 2f))
  val PlayCircle = line("PlayCircle", "M12,2a10,10 0,1 0,0,20a10,10 0,1 0,0,-20z", "M10,8l6,4 -6,4z")
  val VolumeUp = line("VolumeUp", SpeakerPath, "M16,9a5,5 0,0 1,0,6", "M19.364,18.364a9,9 0,0 0,0,-12.728")
  val VolumeOff = line("VolumeOff", SpeakerPath, "M22,9l-6,6", "M16,9l6,6")
  val Coffee =
    line(
      "Coffee",
      "M10,2v2",
      "M14,2v2",
      "M6,2v2",
      "M16,8a1,1 0,0 1,1,1v8a4,4 0,0 1,-4,4H7a4,4 0,0 1,-4,-4V9a1,1 0,0 1,1,-1h14a4,4 0,1 1,0,8h-1",
    )
  val Info = line("Info", "M12,2a10,10 0,1 0,0,20a10,10 0,1 0,0,-20z", "M12,16v-4", "M12,8h0.01")
  val Check = line("Check", "M20,6L9,17l-5,-5")
  val Captions = line("Captions", roundRect(3f, 5f, 18f, 14f, 2f), "M7,15h4", "M15,15h2", "M7,11h2", "M13,11h4")
  val Headphones =
    line("Headphones", "M3,14h3a2,2 0,0 1,2,2v3a2,2 0,0 1,-2,2H5a2,2 0,0 1,-2,-2v-7a9,9 0,0 1,18,0v7a2,2 0,0 1,-2,2h-1a2,2 0,0 1,-2,-2v-3a2,2 0,0 1,2,-2h3")
  // Lucide "settings-2": stands for picture quality.
  val Sources =
    line(
      "Sources",
      "M12,10a2,2 0,1 0,0,4a2,2 0,1 0,0,-4z",
      "M8.5,8.5a5,5 0,0 0,0,7",
      "M15.5,8.5a5,5 0,0 1,0,7",
      "M5.6,5.6a9,9 0,0 0,0,12.8",
      "M18.4,5.6a9,9 0,0 1,0,12.8",
    )
  val Quality = line("Quality", "M20,7h-9", "M14,17H5", "M17,14a3,3 0,1 0,0,6a3,3 0,1 0,0,-6z", "M7,4a3,3 0,1 0,0,6a3,3 0,1 0,0,-6z")
  val Delete =
    line(
      "Delete",
      "M3,6h18",
      "M19,6v14c0,1 -1,2 -2,2H7c-1,0 -2,-1 -2,-2V6",
      "M8,6V4c0,-1 1,-2 2,-2h4c1,0 2,1 2,2v2",
      "M10,11v6",
      "M14,11v6",
    )

  private const val StarPath = "M12,2l3.09,6.26L22,9.27l-5,4.87 1.18,6.88L12,17.77l-6.18,3.25L7,14.14 2,9.27l6.91,-1.01z"
  private const val SpeakerPath =
    "M11,4.702a0.705,0.705 0,0 0,-1.203,-0.498L6.413,7.587A1.4,1.4 0,0 1,5.416,8H3a1,1 0,0 0,-1,1v6a1,1 0,0 0,1,1h2.416a1.4,1.4 0,0 1,0.997,0.413l3.383,3.384A0.705,0.705 0,0 0,11,19.298z"

  private fun roundRect(x: Float, y: Float, w: Float = 7f, h: Float = 7f, r: Float = 1f) =
    "M${x + r},${y}h${w - 2 * r}a$r,$r 0,0 1,$r,${r}v${h - 2 * r}a$r,$r 0,0 1,-$r,${r}h-${w - 2 * r}a$r,$r 0,0 1,-$r,-${r}v-${h - 2 * r}a$r,$r 0,0 1,$r,-${r}z"

  private fun line(name: String, vararg paths: String) = build(name, paths, filled = false)

  private fun solid(name: String, vararg paths: String) = build(name, paths, filled = true)

  private fun build(name: String, paths: Array<out String>, filled: Boolean): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
      .apply {
        for (path in paths) {
          addPath(
            pathData = addPathNodes(path),
            fill = if (filled) SolidColor(Color.White) else null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
          )
        }
      }
      .build()
}
