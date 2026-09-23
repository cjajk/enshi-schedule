package com.hbesxy.schedule.util

import android.content.Context

/**
 * 崩溃日志捕获：把未捕获异常堆栈写入应用私有文件，
 * 下次启动时在界面显示，便于定位启动闪退根因。
 */
object CrashLogger {
    private const val FILE = "crash_log.txt"

    fun install(context: Context) {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            try {
                context.openFileOutput(FILE, Context.MODE_PRIVATE).use {
                    it.write(throwable.stackTraceToString().toByteArray(Charsets.UTF_8))
                }
            } catch (_: Exception) { }
            if (prev != null) {
                prev.uncaughtException(Thread.currentThread(), throwable)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
    }

    fun read(context: Context): String? =
        try {
            context.openFileInput(FILE).bufferedReader().use { it.readText() }.takeIf { it.isNotBlank() }
        } catch (_: Exception) { null }

    fun clear(context: Context) {
        context.deleteFile(FILE)
    }
}
