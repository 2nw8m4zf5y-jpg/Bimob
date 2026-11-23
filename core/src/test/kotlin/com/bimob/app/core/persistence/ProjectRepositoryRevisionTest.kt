package com.bimob.app.core.persistence

import com.bimob.app.core.dependency.*
import com.bimob.app.core.model.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ProjectRepositoryRevisionTest {
    @Test fun atomicRepositoryRoundTripsRevisionSnapshot() {
        val root=File(System.getProperty("java.io.tmpdir"),"bimob-revision-test-"+System.nanoTime())
        val repo=ProjectRepository(JvmAtomicStorage(root))
        val key=EntityProperty(ModelId("entity"),PropertyAspect.HEIGHT)
        val graph=DependencyGraph.empty().withNode(key)
        val revision=ModelRevision(7,Project(id=ModelId("pid"),name="Saved"),graph,mapOf(key to DerivedValue.NumberValue(4.25)))
        val saved=repo.save("project/revision.bin",revision)
        assertTrue(saved.digest.isNotBlank())
        assertEquals(revision,repo.loadRevision("project/revision.bin"))
        root.deleteRecursively()
    }

    @Test fun legacySketchMigrationIsDeterministicAndValidated() {
        val json="""{"name":"Old","walls":[{"x1":0,"y1":0,"x2":3,"y2":0,"thickness":0.2}]}"""
        val first=LegacySketchMigrator.migrateJson(json)
        val second=LegacySketchMigrator.migrateJson(json)
        assertEquals(first,second)
        assertEquals("legacy-wall-0",first.elements.single().id.value)
        assertTrue(ModelValidator.validate(first).none { it.severity==ValidationSeverity.ERROR })
    }

    @Test fun legacySketchRejectsDegenerateWall() {
        val json="""{"name":"Old","walls":[{"x1":0,"y1":0,"x2":0,"y2":0}]}"""
        try { LegacySketchMigrator.migrateJson(json); fail("expected validation rejection") }
        catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("zero length")) }
    }
    @Test fun sessionSavePersistsOneRevisionAndTracksSavedContent() {
        val root = File(System.getProperty("java.io.tmpdir"), "bimob-session-save-" + System.nanoTime())
        val repository = ProjectRepository(JvmAtomicStorage(root))
        val session = ModelSession.newProject("Save test")
        assertTrue(session.snapshot().hasUnsavedChanges)
        session.addWall("wall-1", 0.0, 0.0, 4.0, 0.0)
        session.save(repository, "projects/save.revision")
        assertFalse(session.snapshot().hasUnsavedChanges)
        assertEquals(1, repository.loadRevision("projects/save.revision").project.elements.size)
        session.moveWall("wall-1", 1.0, 0.0)
        assertTrue(session.snapshot().hasUnsavedChanges)
        session.undo()
        assertFalse(session.snapshot().hasUnsavedChanges)
        root.deleteRecursively()
    }

