package tv.own.owntv.glcn

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import tv.own.owntv.core.launcher.RemoteKeys

/**
 * glcn: the paired phone as this TV's remote. Its keys arrive as names and are pressed here as if the TV's own remote
 * had sent them (down then up, on the app's window); the volume goes to the system's media volume; typed words go to
 * Search ([tv.own.owntv.features.search.VoiceTrigger.type]).
 */
object RemoteInput {
    fun key(activity: Activity, name: String) {
        when (name) {
            RemoteKeys.VOLUME_UP -> volume(activity, AudioManager.ADJUST_RAISE)
            RemoteKeys.VOLUME_DOWN -> volume(activity, AudioManager.ADJUST_LOWER)
            RemoteKeys.MUTE -> volume(activity, AudioManager.ADJUST_TOGGLE_MUTE)
            else -> codeFor(name)?.let { press(activity, it) }
        }
    }

    fun text(activity: Activity, text: String) {
        tv.own.owntv.features.search.VoiceTrigger.type(text)
        press(activity, KeyEvent.KEYCODE_SEARCH)
    }

    private fun codeFor(name: String): Int? = when (name) {
        RemoteKeys.UP -> KeyEvent.KEYCODE_DPAD_UP
        RemoteKeys.DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
        RemoteKeys.LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
        RemoteKeys.RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
        RemoteKeys.OK -> KeyEvent.KEYCODE_DPAD_CENTER
        RemoteKeys.BACK -> KeyEvent.KEYCODE_BACK
        RemoteKeys.PLAY_PAUSE -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        RemoteKeys.CHANNEL_UP -> KeyEvent.KEYCODE_CHANNEL_UP
        RemoteKeys.CHANNEL_DOWN -> KeyEvent.KEYCODE_CHANNEL_DOWN
        RemoteKeys.INFO -> KeyEvent.KEYCODE_INFO
        RemoteKeys.GUIDE -> KeyEvent.KEYCODE_GUIDE
        else -> null
    }

    private fun press(activity: Activity, code: Int) {
        val down = SystemClock.uptimeMillis()
        val source = if (code in KeyEvent.KEYCODE_DPAD_UP..KeyEvent.KEYCODE_DPAD_CENTER) InputDevice.SOURCE_DPAD else InputDevice.SOURCE_KEYBOARD
        fun event(action: Int, at: Long) = KeyEvent(down, at, action, code, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, source)
        runCatching {
            activity.dispatchKeyEvent(event(KeyEvent.ACTION_DOWN, down))
            activity.dispatchKeyEvent(event(KeyEvent.ACTION_UP, SystemClock.uptimeMillis()))
        }
    }

    private fun volume(context: Context, direction: Int) {
        runCatching {
            (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
                .adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        }
    }
}
