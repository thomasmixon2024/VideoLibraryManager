package com.example.videolibrarymanager.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * BugLogger — persistent run/bug log written to internal storage.
 *
 * Log file location: <filesDir>/vlm_runlog.txt
 * Each session appends a session header and tagged entries.
 * Log is rotated to vlm_runlog_prev.txt once it exceeds 512 KB.
 *
 * Usage:
 *   BugLogger.init(context)         // call once in Application.onCreate()
 *   BugLogger.info(TAG, "msg")
 *   BugLogger.warn(TAG, "msg")
 *   BugLogger.error(TAG, "msg", ex) // ex is optional
 *   BugLogger.debug(TAG, "msg")
 *   val text = BugLogger.readLog()  // retrieve full log as String
 *   BugLogger.logFile()             // get File reference for FileProvider sharing
 */
object BugLogger {

    private const val MAX_FILE_BYTES = 512 * 1024L
    private const val SELF = "BugLogger"
    private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    @Volatile private var logFile: File? = null
    @Volatile private var initialized = false

    // ── Public API ────────────────────────────────────────────────────────────

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val file = File(context.filesDir, "vlm_runlog.txt")
            rotateIfNeeded(file)
            logFile = file
            initialized = true
            appendRaw(buildSessionHeader())
            Log.i(SELF, "BugLogger → ${file.absolutePath}")
        }
    }

    fun info(tag: String, msg: String)                       = write("INFO ", tag, msg, null)
    fun warn(tag: String, msg: String)                       = write("WARN ", tag, msg, null)
    fun debug(tag: String, msg: String)                      = write("DEBUG", tag, msg, null)
    fun error(tag: String, msg: String, t: Throwable? = null)= write("ERROR", tag, msg, t)

    /** Full log as a string (for LogViewerScreen or share sheet). */
    fun readLog(): String = logFile?.takeIf { it.exists() }?.readText()
        ?: "(BugLogger not initialized)"

    /** Raw File reference for FileProvider / share-intent. */
    fun logFile(): File? = logFile

    /** Erase and restart log. */
    fun clear() {
        logFile?.writeText("")
        appendRaw(buildSessionHeader())
        info(SELF, "Log cleared by user")
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private fun write(level: String, tag: String, msg: String, t: Throwable?) {
        try {
            when (level.trim()) {
                "INFO"  -> Log.i(tag, msg)
                "WARN"  -> Log.w(tag, msg)
                "ERROR" -> if (t != null) Log.e(tag, msg, t) else Log.e(tag, msg)
                "DEBUG" -> Log.d(tag, msg)
            }
        } catch (_: Throwable) {
            // Running in unit tests or unsupported runtime where android.util.Log is unavailable.
        }

        val sb = StringBuilder()
        sb.append("${sdf.format(Date())} [$level] [$tag] $msg\n")
        t?.let {
            sb.append("  ↳ ${it::class.java.simpleName}: ${it.message}\n")
            it.stackTrace.take(8).forEach { f -> sb.append("      at $f\n") }
        }
        appendRaw(sb.toString())
    }

    private fun appendRaw(text: String) {
        try {
            FileWriter(logFile ?: return, true).use { it.write(text) }
        } catch (e: Exception) {
            Log.e(SELF, "Write failed: ${e.message}")
        }
    }

    private fun rotateIfNeeded(file: File) {
        if (file.exists() && file.length() > MAX_FILE_BYTES) {
            val prev = File(file.parent, "vlm_runlog_prev.txt")
            file.copyTo(prev, overwrite = true)
            file.writeText("")
            Log.i(SELF, "Rotated log → ${prev.absolutePath}")
        }
    }

    private fun buildSessionHeader(): String {
        val bar = "═".repeat(72)
        val ts  = sdf.format(Date())
        return "\n$bar\n  SESSION START  $ts\n$bar\n"
    }
}
