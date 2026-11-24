package com.bimob.app.core.nativebridge

/** Thin JNI boundary. This stage intentionally does not replace the UI's Kotlin ModelSession. */
object NativeEngineBridge {
    init { System.loadLibrary("bimob_jni") }
    fun createSession(): Long = nativeCreateSession()
    fun destroySession(handle: Long) = nativeDestroySession(handle)
    fun dispatchCommandJson(handle: Long, commandJson: String): String = nativeDispatchCommandJson(handle, commandJson)
    fun exportCanonicalSnapshot(handle: Long): String = nativeExportCanonicalSnapshot(handle)
    private external fun nativeCreateSession(): Long
    private external fun nativeDestroySession(handle: Long)
    private external fun nativeDispatchCommandJson(handle: Long, commandJson: String): String
    private external fun nativeExportCanonicalSnapshot(handle: Long): String
}
