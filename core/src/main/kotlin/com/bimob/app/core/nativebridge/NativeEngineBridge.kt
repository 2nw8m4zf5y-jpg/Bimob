package com.bimob.app.core.nativebridge
/** Thin JNI boundary. */
object NativeEngineBridge {
 init { System.loadLibrary("bimob_jni") }
 fun createSession(): Long = nativeCreateSession()
 fun destroySession(handle: Long) = nativeDestroySession(handle)
 fun dispatchCommandJson(handle: Long, commandJson: String): String = nativeDispatchCommandJson(handle, commandJson)
 fun exportCanonicalSnapshot(handle: Long): String = nativeExportCanonicalSnapshot(handle)
 fun beginGesture(handle: Long, gestureId: String): Boolean = nativeBeginGesture(handle, gestureId)
 fun commitGesture(handle: Long, gestureId: String): Boolean = nativeCommitGesture(handle, gestureId)
 fun cancelGesture(handle: Long, gestureId: String): Boolean = nativeCancelGesture(handle, gestureId)
 private external fun nativeCreateSession(): Long
 private external fun nativeDestroySession(handle: Long)
 private external fun nativeDispatchCommandJson(handle: Long, commandJson: String): String
 private external fun nativeExportCanonicalSnapshot(handle: Long): String
 private external fun nativeBeginGesture(handle: Long, gestureId: String): Boolean
 private external fun nativeCommitGesture(handle: Long, gestureId: String): Boolean
 private external fun nativeCancelGesture(handle: Long, gestureId: String): Boolean
}
