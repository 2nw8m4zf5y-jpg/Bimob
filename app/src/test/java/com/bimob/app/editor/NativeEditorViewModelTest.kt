package com.bimob.app.editor

import com.bimob.app.core.nativebridge.RenderGeometryBuffer
import org.junit.Assert.*
import org.junit.Test

class NativeEditorViewModelTest {
    private class FakeEngine : NativeEditorEngine {
        var next = 1L
        val commands = mutableListOf<String>()
        var destroyed = 0
        var gesture: String? = null
        var revision = 1L
        var walls = 0
        var joins = 0
        var redoCount = 0
        override fun createSession() = next++
        override fun destroySession(handle: Long) { destroyed++ }
        override fun dispatch(handle: Long, json: String): String {
            commands += json
            when {
                json.contains("CreateWall") -> { walls++; redoCount = 0; if (walls >= 2) joins = 1; revision++ }
                json.contains("\"Undo\"") -> { walls = (walls - 1).coerceAtLeast(0); redoCount++; joins = if (walls >= 2) 1 else 0; revision++ }
                json.contains("\"Redo\"") -> { walls++; redoCount = (redoCount - 1).coerceAtLeast(0); joins = if (walls >= 2) 1 else 0; revision++ }
            }
            return "{\"ok\":true,\"canUndo\":${walls > 0},\"canRedo\":${redoCount > 0}}"
        }
        override fun export(handle: Long) = RenderGeometryBuffer(1, revision,
            (0 until walls).map { "wall-$it" }, FloatArray(walls * 10), (0 until joins).map { "join-$it" },
            FloatArray(joins * 9), emptyList(), FloatArray(0), emptyList(), FloatArray(0))
        override fun beginGesture(handle: Long, id: String): Boolean { gesture = id; return true }
        override fun commitGesture(handle: Long, id: String): Boolean { return gesture == id.also { gesture = null } }
        override fun cancelGesture(handle: Long, id: String): Boolean { gesture = null; return true }
    }

    @Test fun createsLevelAndExportsNativeGeometryAndHistory() {
        val engine = FakeEngine()
        val vm = NativeEditorViewModel(engine)
        vm.resetProject("p1", "Project")
        assertTrue(engine.commands.first().contains("CreateLevel"))
        vm.createWall(0.0, 0.0, 3.0, 0.0)
        vm.createWall(3.0, 0.0, 3.0, 2.0)
        assertEquals(2, vm.state.value.geometry?.wallIds?.size)
        assertEquals(1, vm.state.value.geometry?.wallJoinIds?.size)
        assertTrue(vm.state.value.canUndo)
        vm.undo()
        assertEquals(1, vm.state.value.geometry?.wallIds?.size)
        assertEquals(0, vm.state.value.geometry?.wallJoinIds?.size)
        assertTrue(vm.state.value.canRedo)
        vm.redo()
        assertEquals(2, vm.state.value.geometry?.wallIds?.size)
        assertEquals(1, vm.state.value.geometry?.wallJoinIds?.size)
        vm.close()
        assertEquals(1, engine.destroyed)
    }

    @Test fun gestureLifecycleAndJoinSectionArePreserved() {
        val engine = FakeEngine().also { it.joins = 1 }
        val vm = NativeEditorViewModel(engine)
        vm.resetProject("p2", "Project")
        val gesture = vm.beginGesture()
        assertNotNull(gesture)
        vm.commitGesture(gesture!!)
        assertEquals(1, vm.state.value.geometry?.wallJoinIds?.size)
    }
}
