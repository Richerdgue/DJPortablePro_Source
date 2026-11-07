package com.djportablepro.app.logging

import android.util.Log

/**
 * Safe logger that keeps Android runtime logs on device while remaining compatible with JVM tests.
 */
object DjLogger {
    fun d(tag: String, message: String) = safeLog({ Log.d(tag, message) }, tag, message)
    fun i(tag: String, message: String) = safeLog({ Log.i(tag, message) }, tag, message)
    fun w(tag: String, message: String) = safeLog({ Log.w(tag, message) }, tag, message)
    fun e(tag: String, message: String) = safeLog({ Log.e(tag, message) }, tag, message)

    private fun safeLog(
        androidBlock: () -> Unit,
        tag: String,
        message: String,
    ) {
        try {
            androidBlock()
        } catch (e: RuntimeException) {
            println("[$tag] $message")
        }
    }
}
