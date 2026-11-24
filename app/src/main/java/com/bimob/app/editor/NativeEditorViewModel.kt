package com.bimob.app.editor

import androidx.lifecycle.ViewModel
import com.bimob.app.core.nativebridge.NativeEngineBridge
import com.bimob.app.core.nativebridge.RenderGeometryBuffer
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal interface NativeEditorEngine {
    fun createSession(): Long
    fun destroySession(handle: Long)
    fun dispatch(handle: Long, json: String): String
    fun export(handle: Long): RenderGeometryBuffer
    fun beginGesture(handle: Long, id: String): Boolean
    fun commitGesture(handle: Long, id: String): Boolean
    fun cancelGesture(handle: Long, id: String): Boolean
}

private object JniNativeEditorEngine : NativeEditorEngine {
    override fun createSession() = NativeEngineBridge.createSession()
    override fun destroySession(handle: Long) = NativeEngineBridge.destroySession(handle)
    override fun dispatch(handle: Long, json: String) = NativeEngineBridge.dispatchCommandJson(handle, json)
    override fun export(handle: Long) = NativeEngineBridge.exportRenderGeometry(handle)
    override fun beginGesture(handle: Long, id: String) = NativeEngineBridge.beginGesture(handle, id)
    override fun commitGesture(handle: Long, id: String) = NativeEngineBridge.commitGesture(handle, id)
    override fun cancelGesture(handle: Long, id: String) = NativeEngineBridge.cancelGesture(handle, id)
}

data class NativeEditorState(
    val geometry: RenderGeometryBuffer? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val error: String? = null,
    val projectId: String = ""
)

/** Activity-scoped owner of the native editing session. All committed geometry comes from BGR1. */
class NativeEditorViewModel internal constructor(
    private val engine: NativeEditorEngine
) : ViewModel() {
    constructor() : this(JniNativeEditorEngine)

    private var handle: Long = engine.createSession()
    private var levelId: String = ""
    private val mutableState = MutableStateFlow(NativeEditorState())
    val state: StateFlow<NativeEditorState> = mutableState.asStateFlow()

    init { check(handle != 0L) { "Native engine could not create a session." } }

    fun resetProject(projectId: String, projectName: String) {
        if (mutableState.value.projectId == projectId && levelId.isNotEmpty()) return
        if (handle != 0L) engine.destroySession(handle)
        handle = engine.createSession()
        check(handle != 0L) { "Native engine could not create a session." }
        levelId = "level-${UUID.randomUUID()}"
        val result = engine.dispatch(handle,
            "{\"type\":\"CreateLevel\",\"id\":\"$levelId\",\"name\":\"Level 1\",\"elevationMeters\":0,\"heightMeters\":3}")
        if (!isOk(result)) {
            mutableState.value = NativeEditorState(error = errorFrom(result), projectId = projectId)
            return
        }
        refresh(projectId)
    }

    fun createWall(startX: Double, startY: Double, endX: Double, endY: Double) {
        if (kotlin.math.hypot(endX - startX, endY - startY) < 0.05) return
        val id = "wall-${UUID.randomUUID()}"
        mutate("{\"type\":\"CreateWall\",\"id\":\"$id\",\"levelId\":\"$levelId\",\"startX\":$startX,\"startY\":$startY,\"endX\":$endX,\"endY\":$endY,\"heightMeters\":2.7,\"thicknessMeters\":0.2}")
    }

    fun moveWall(id: String, dx: Double, dy: Double) {
        if (dx == 0.0 && dy == 0.0) return
        mutate("{\"type\":\"MoveWall\",\"id\":\"$id\",\"deltaX\":$dx,\"deltaY\":$dy}")
    }

    fun beginGesture(): String? {
        val id = "gesture-${UUID.randomUUID()}"
        return id.takeIf { handle != 0L && engine.beginGesture(handle, it) }
    }

    fun commitGesture(id: String) {
        if (engine.commitGesture(handle, id)) refresh(mutableState.value.projectId)
        else publishError("Native gesture commit failed.")
    }

    fun cancelGesture(id: String) {
        if (engine.cancelGesture(handle, id)) refresh(mutableState.value.projectId)
        else publishError("Native gesture cancellation failed.")
    }

    fun undo() = history("Undo")
    fun redo() = history("Redo")

    private fun history(type: String) {
        if (handle == 0L) return
        val result = engine.dispatch(handle, "{\"type\":\"$type\"}")
        if (isOk(result)) refresh(mutableState.value.projectId) else publishError(errorFrom(result))
    }

    private fun mutate(command: String) {
        val result = engine.dispatch(handle, command)
        if (isOk(result)) refresh(mutableState.value.projectId) else publishError(errorFrom(result))
    }

    private fun refresh(projectId: String) {
        try {
            val geometry = engine.export(handle)
            // The native history endpoint is deliberately authoritative; derive availability from its snapshot response.
            val snapshot = engine.dispatch(handle, "{\"type\":\"HistoryState\"}")
            mutableState.value = NativeEditorState(
                geometry = geometry,
                canUndo = Regex("\"canUndo\"\\s*:\\s*true").containsMatchIn(snapshot),
                canRedo = snapshot.contains("\"canRedo\":true"),
                projectId = projectId
            )
        } catch (failure: Exception) { publishError(failure.message ?: "BGR1 export failed.") }
    }

    private fun isOk(json: String) = Regex("\"ok\"\\s*:\\s*true").containsMatchIn(json)
    private fun errorFrom(json: String) = Regex("\"error\"\\s*:\\s*\"([^\"]*)\"")
        .find(json)?.groupValues?.getOrNull(1) ?: "Native command rejected."
    private fun publishError(message: String) {
        mutableState.value = mutableState.value.copy(error = message)
    }

    fun close() {
        if (handle != 0L) {
            engine.destroySession(handle)
            handle = 0L
        }
    }

    override fun onCleared() {
        close()
        super.onCleared()
    }
}
