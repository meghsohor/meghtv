package dev.meghsohor.meghtv

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.meghsohor.meghtv.data.ChannelListPrefs
import dev.meghsohor.meghtv.data.MeghTVRepository
import dev.meghsohor.meghtv.data.db.MeghTVDatabase
import dev.meghsohor.meghtv.theme.MeghTVTheme

class MainActivity : ComponentActivity() {

  private val repository: MeghTVRepository by lazy { MeghTVRepository(MeghTVDatabase.getInstance(applicationContext), ChannelListPrefs(applicationContext)) }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Always dark, so light system bar icons whatever the device theme.
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
    )
    hideStatusBar()
    setContent {
      MeghTVTheme {
        // Not a Surface: its fill would repaint the navy the window background already draws.
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) { MainNavigation(repository) }
      }
    }
  }

  // Before Android 11 leaving the app clears the hide, and a dialog window can bring the bar back.
  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (hasFocus) hideStatusBar()
  }

  // The navigation bar stays, so Back is always reachable.
  private fun hideStatusBar() {
    WindowCompat.getInsetsController(window, window.decorView).apply {
      systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
      hide(WindowInsetsCompat.Type.statusBars())
    }
  }
}
