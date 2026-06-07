package tools.mo3ta.bazeed.util

import android.util.Log
import tools.mo3ta.bazeed.BuildConfig

/**
 * App-wide logging facade. Delegates to [android.util.Log] on debug builds and
 * is a no-op on release. Use this everywhere instead of [android.util.Log].
 */
object Logger {

    private val enabled: Boolean = BuildConfig.DEBUG

    fun v(tag: String, msg: String) {
        if (enabled) Log.v(tag, msg)
    }

    fun d(tag: String, msg: String) {
        if (enabled) Log.d(tag, msg)
    }

    fun i(tag: String, msg: String) {
        if (enabled) Log.i(tag, msg)
    }

    fun w(tag: String, msg: String, throwable: Throwable? = null) {
        if (enabled) Log.w(tag, msg, throwable)
    }

    fun e(tag: String, msg: String, throwable: Throwable? = null) {
        if (enabled) Log.e(tag, msg, throwable)
    }
}
