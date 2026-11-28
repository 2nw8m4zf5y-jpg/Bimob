package com.bimob.app

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.atomic.AtomicBoolean

/** Persists the last uncaught JVM failure before Android terminates the process. */
internal object CrashReporter {
    private val installed = AtomicBoolean(false)
    private const val FILE_NAME = "last_crash.txt"

    fun install(context: Context) {
        if (!installed.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, failure ->
            try {
                val trace = StringWriter().also { writer ->
                    failure.printStackTrace(PrintWriter(writer))
                }.toString()
                val contents = buildString {
                    append("NAGAPLAS JVM CRASH\n")
                    append("thread=").append(thread.name).append('\n')
                    append("timeMillis=").append(System.currentTimeMillis()).append('\n')
                    append(trace)
                }
                val target = File(appContext.filesDir, FILE_NAME)
                FileOutputStream(target, false).use { stream ->
                    stream.write(contents.toByteArray(Charsets.UTF_8))
                    stream.fd.sync()
                }
            } catch (_: Throwable) {
                // Never replace the original uncaught exception with a logger failure.
            }
            previous?.uncaughtException(thread, failure)
        }
    }

    fun readLastCrash(context: Context): String? =
        runCatching {
            File(context.applicationContext.filesDir, FILE_NAME)
                .takeIf { it.isFile && it.length() in 1..(512L * 1024L) }
                ?.readText(Charsets.UTF_8)
        }.getOrNull()

    fun clear(context: Context) {
        runCatching { File(context.applicationContext.filesDir, FILE_NAME).delete() }
    }
}
