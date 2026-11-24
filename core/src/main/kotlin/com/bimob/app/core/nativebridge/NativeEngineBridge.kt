package com.bimob.app.core.nativebridge

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.CodingErrorAction

/** Version-1 render export in world-space metres.
 * Walls: 4 XY vertices + base elevation + height (10 floats).
 * Wall joins: 4 XY vertices + height (9 floats). Openings/catalogue: 8 floats.
 * IDs are length-prefixed UTF-8 strings after numeric sections in matching sorted order.
 */
data class RenderGeometryBuffer(
    val formatVersion: Int,
    val revision: Long,
    val wallIds: List<String>,
    val walls: FloatArray,
    val wallJoinIds: List<String>,
    val wallJoins: FloatArray,
    val openingIds: List<String>,
    val openings: FloatArray,
    val catalogInstanceIds: List<String>,
    val catalogInstances: FloatArray,
) {
    companion object {
        private const val HEADER_BYTES = 32
        private const val WALL_FLOATS = 10
        private const val JOIN_FLOATS = 9
        private const val OPENING_FLOATS = 8
        private const val CATALOG_FLOATS = 8

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
            val maxRecordFloats = maxOf(WALL_FLOATS, JOIN_FLOATS, OPENING_FLOATS, CATALOG_FLOATS)
            require(counts.all { it >= 0 && it <= Int.MAX_VALUE / maxRecordFloats }) {
                "Invalid render geometry section count."
            }
            val numericEnd = HEADER_BYTES.toLong() +
                counts[0].toLong() * WALL_FLOATS * Float.SIZE_BYTES +
                counts[1].toLong() * JOIN_FLOATS * Float.SIZE_BYTES +
                counts[2].toLong() * OPENING_FLOATS * Float.SIZE_BYTES +
                counts[3].toLong() * CATALOG_FLOATS * Float.SIZE_BYTES
            require(numericEnd <= bytes.size.toLong()) { "Render geometry buffer truncated." }
            fun readSection(count: Int, recordFloats: Int): FloatArray =
                FloatArray(count * recordFloats) { b.float }
            fun readIds(count: Int): List<String> = List(count) {
                require(b.remaining() >= 4) { "Truncated geometry ID length." }
                val size = b.int
                require(size >= 0 && size <= b.remaining()) { "Invalid geometry ID length." }
                val raw = ByteArray(size)
                b.get(raw)
                Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(raw)).toString()
            }
            val wallSection = readSection(counts[0], WALL_FLOATS)
            val joinSection = readSection(counts[1], JOIN_FLOATS)
            val openingSection = readSection(counts[2], OPENING_FLOATS)
            val catalogSection = readSection(counts[3], CATALOG_FLOATS)
            require(wallSection.all { it.isFinite() } && joinSection.all { it.isFinite() } &&
                openingSection.all { it.isFinite() } && catalogSection.all { it.isFinite() }) {
                "Render geometry contains non-finite coordinates."
            }
            val wallIds = readIds(counts[0])
            val joinIds = readIds(counts[1])
            val openingIds = readIds(counts[2])
            val catalogIds = readIds(counts[3])
            require(!b.hasRemaining()) { "Render geometry contains trailing data." }
            return RenderGeometryBuffer(version, revision, wallIds, wallSection, joinIds, joinSection,
                openingIds, openingSection, catalogIds, catalogSection)
        }
    }
}

/** Thin JNI boundary. */
object NativeEngineBridge {
    init { System.loadLibrary("bimob_jni") }
    private inline fun <T> guarded(fallback: T, operation: () -> T): T = try {
        operation()
    } catch (failure: Exception) {
        fallback
    } catch (failure: LinkageError) {
        fallback
    }

    fun createSession(): Long = guarded(0L) { nativeCreateSession() }
    fun destroySession(handle: Long) { guarded(Unit) { nativeDestroySession(handle) } }
    fun dispatchCommandJson(handle: Long, commandJson: String): String =
        guarded("""{"ok":false,"error":"JNI command invocation failed safely"}""") {
            nativeDispatchCommandJson(handle, commandJson)
        }
    fun exportCanonicalSnapshot(handle: Long): String =
        guarded("{}") { nativeExportCanonicalSnapshot(handle) }
    fun importCanonicalSnapshot(handle: Long, snapshotJson: String): String =
        guarded("""{"ok":false,"error":"JNI snapshot import failed safely"}""") {
            nativeImportCanonicalSnapshot(handle, snapshotJson)
        }
    fun exportRenderGeometry(handle: Long): RenderGeometryBuffer = guarded(
        RenderGeometryBuffer(1, 0, emptyList(), FloatArray(0), emptyList(), FloatArray(0),
            emptyList(), FloatArray(0), emptyList(), FloatArray(0))
    ) { RenderGeometryBuffer.decode(nativeExportRenderGeometry(handle)) }
    fun beginGesture(handle: Long, gestureId: String): Boolean = guarded(false) { nativeBeginGesture(handle, gestureId) }
    fun commitGesture(handle: Long, gestureId: String): Boolean = guarded(false) { nativeCommitGesture(handle, gestureId) }
    fun cancelGesture(handle: Long, gestureId: String): Boolean = guarded(false) { nativeCancelGesture(handle, gestureId) }
    private external fun nativeCreateSession(): Long
    private external fun nativeDestroySession(handle: Long)
    private external fun nativeDispatchCommandJson(handle: Long, commandJson: String): String
    private external fun nativeExportCanonicalSnapshot(handle: Long): String
    private external fun nativeImportCanonicalSnapshot(handle: Long, snapshotJson: String): String
    private external fun nativeExportRenderGeometry(handle: Long): ByteArray
    private external fun nativeBeginGesture(handle: Long, gestureId: String): Boolean
    private external fun nativeCommitGesture(handle: Long, gestureId: String): Boolean
    private external fun nativeCancelGesture(handle: Long, gestureId: String): Boolean
}
