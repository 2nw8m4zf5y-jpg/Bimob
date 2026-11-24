package com.bimob.app.core.nativebridge

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Version-1 render export. All coordinates and dimensions are world-space metres.
 * Each primitive section uses fixed 8-float records; wall/join records are 4 XY vertices.
 */
data class RenderGeometryBuffer(
    val formatVersion: Int,
    val revision: Long,
    val walls: FloatArray,
    val wallJoins: FloatArray,
    val openings: FloatArray,
    val catalogInstances: FloatArray,
) {
    companion object {
        private const val HEADER_BYTES = 32
        private const val RECORD_FLOATS = 8

        internal fun decode(bytes: ByteArray): RenderGeometryBuffer {
            require(bytes.size >= HEADER_BYTES) { "Native render geometry unavailable or truncated." }
            val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            require(b.get().toInt().toChar() == 'B' && b.get().toInt().toChar() == 'G' &&
                b.get().toInt().toChar() == 'R' && b.get().toInt().toChar() == '1') {
                "Invalid render geometry magic."
            }
            val version = b.int
            require(version == 1) { "Unsupported render geometry format version: $version" }
            val revision = b.long
            val counts = IntArray(4) { b.int }
            require(counts.all { it >= 0 }) { "Invalid render geometry section count." }
            val expected = counts.fold(HEADER_BYTES.toLong()) { total, count ->
                total + count.toLong() * RECORD_FLOATS * Float.SIZE_BYTES
            }
            require(expected == bytes.size.toLong()) { "Render geometry buffer size mismatch." }
            fun readSection(count: Int): FloatArray = FloatArray(count * RECORD_FLOATS) { b.float }
            val wallSection = readSection(counts[0])
            val joinSection = readSection(counts[1])
            val openingSection = readSection(counts[2])
            val catalogSection = readSection(counts[3])
            require((wallSection + joinSection + openingSection + catalogSection).all { it.isFinite() }) {
                "Render geometry contains non-finite coordinates."
            }
            return RenderGeometryBuffer(version, revision, wallSection, joinSection, openingSection, catalogSection)
        }
    }
}

/** Thin JNI boundary. */
object NativeEngineBridge {
    init { System.loadLibrary("bimob_jni") }
    fun createSession(): Long = nativeCreateSession()
    fun destroySession(handle: Long) = nativeDestroySession(handle)
    fun dispatchCommandJson(handle: Long, commandJson: String): String = nativeDispatchCommandJson(handle, commandJson)
    fun exportCanonicalSnapshot(handle: Long): String = nativeExportCanonicalSnapshot(handle)
    fun exportRenderGeometry(handle: Long): RenderGeometryBuffer = RenderGeometryBuffer.decode(nativeExportRenderGeometry(handle))
    fun beginGesture(handle: Long, gestureId: String): Boolean = nativeBeginGesture(handle, gestureId)
    fun commitGesture(handle: Long, gestureId: String): Boolean = nativeCommitGesture(handle, gestureId)
    fun cancelGesture(handle: Long, gestureId: String): Boolean = nativeCancelGesture(handle, gestureId)
    private external fun nativeCreateSession(): Long
    private external fun nativeDestroySession(handle: Long)
    private external fun nativeDispatchCommandJson(handle: Long, commandJson: String): String
    private external fun nativeExportCanonicalSnapshot(handle: Long): String
    private external fun nativeExportRenderGeometry(handle: Long): ByteArray
    private external fun nativeBeginGesture(handle: Long, gestureId: String): Boolean
    private external fun nativeCommitGesture(handle: Long, gestureId: String): Boolean
    private external fun nativeCancelGesture(handle: Long, gestureId: String): Boolean
}
