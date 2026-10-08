package dev.meghsohor.meghtv.data

import android.content.Context
import androidx.core.content.edit

/** Which published list the device has, and which one the user put off. */
class ChannelListPrefs(context: Context) {
  private val prefs = context.getSharedPreferences("channel_list", Context.MODE_PRIVATE)

  var appliedUpdatedAt: String?
    get() = prefs.getString(AppliedKey, null)
    set(value) = prefs.edit { putString(AppliedKey, value) }

  var postponedUpdatedAt: String?
    get() = prefs.getString(PostponedKey, null)
    set(value) = prefs.edit { putString(PostponedKey, value) }

  private companion object {
    const val AppliedKey = "applied_updated_at"
    const val PostponedKey = "postponed_updated_at"
  }
}
