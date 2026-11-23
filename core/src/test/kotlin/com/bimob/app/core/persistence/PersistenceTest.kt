package com.bimob.app.core.persistence

import com.bimob.app.core.model.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PersistenceTest {
    private fun fixture(name:String="Demo",schema:Int=1):Project {
        val projectId=ModelId("project-1")
        val site=Site(ModelId("site-1"),"Site")
        val level=Level(ModelId("level-1"),"Ground",0.0,3.0)
        val material=Material(ModelId("material-1"),"Brick","Masonry",1800.0,0.7,25.0,"m3")
        val type=ElementType(ModelId("type-1"),"External wall",ElementCategory.WALL,listOf(Parameter(ModelId("p-1"),ParameterValue.Text("Type value"))))
        val wall=WallElement(
            id=ModelId("wall-1"),typeId=type.id,levelId=level.id,
            axis=WallAxis(Point2(0.125,2.5),Point2(4.75,2.5)),heightMeters=3.0,thicknessMeters=0.2,
            baseElevationMeters=0.0,topElevationMeters=3.0,locationLine=WallLocationLine.CORE_CENTER,
            layers=listOf(WallLayer(material.id,0.2,WallLayerFunction.STRUCTURE,true)),
            parameters=listOf(Parameter(ModelId("p-instance"),ParameterValue.Real(0.125)),Parameter(ModelId("p-text"),ParameterValue.Text("hello, \u043c\u0438\u0440"))))
        return Project(projectId,name,schema,site,listOf(Building(ModelId("building-1"),"Building")),listOf(level),listOf(wall),listOf(material),listOf(type))
    }

    @Test fun canonicalSerializationIsDeterministicAndRoundTrips() {
        val p=fixture()
        val first=CanonicalProjectJson.encode(p)
        val second=CanonicalProjectJson.encode(p)
        assertArrayEquals(first,second)
        assertEquals(p,CanonicalProjectJson.decode(first))
        val archive=BimobArchiveWriter().write(p)
        assertEquals(p,BimobArchiveReader().read(archive))
    }

    @Test fun damagedArchiveFailsIntegrityWithoutThrowingUncontrolledException() {
        val bytes=BimobArchiveWriter().write(fixture())
        val damaged=bytes.copyOf()
        damaged[damaged.size/2]=(damaged[damaged.size/2].toInt() xor 0x20).toByte()
        try {
            BimobArchiveReader().read(damaged)
            fail("Corrupt archive should be rejected.")
        } catch(e:PersistenceException) {
            assertNotNull(e.message)
        }
    }

    @Test fun recoveryRestoresUncommittedSnapshotAfterSimulatedRestart() {
        val dir=File(System.getProperty("java.io.tmpdir"),"bimob-test-"+System.nanoTime())
        val storage=JvmAtomicStorage(dir)
        val journal=CrashRecoveryJournal(storage,"workspace/recovery.journal")
        val repository=ProjectRepository(storage,journal=journal)
        val base=fixture()
        val saved=repository.save("projects/test.bimob",base)
        val changed=base.copy(name="Unsaved edit",levels=base.levels.map{it.copy(heightMeters=3.25)})
        journal.append(base.id.value,saved.digest,changed)

        // Simulated process restart: new repository and journal instances, same durable workspace.
        val restartedStorage=JvmAtomicStorage(dir)
        val restarted=ProjectRepository(restartedStorage,journal=CrashRecoveryJournal(restartedStorage,"workspace/recovery.journal"))
        assertEquals(changed,restarted.recover("projects/test.bimob",base))
        dir.deleteRecursively()
    }

    @Test fun futureSchemaIsRejectedExplicitly() {
        val future=BimobArchiveWriter().write(fixture(schema=42))
        try {
            BimobArchiveReader().read(future)
            fail("Future schema must not be silently opened.")
        } catch(e:UnsupportedSchemaException) {
            assertEquals(42,e.found)
            assertEquals(1,e.supported)
        }
    }

    @Test fun successfulSaveCompactsJournalToNewBase() {
        val dir=File(System.getProperty("java.io.tmpdir"),"bimob-compact-"+System.nanoTime())
        val storage=JvmAtomicStorage(dir)
        val journal=CrashRecoveryJournal(storage,"workspace/journal.bin")
        val repository=ProjectRepository(storage,journal=journal)
        val p=fixture()
        val first=repository.save("project.bimob",p)
        journal.append(p.id.value,first.digest,p.copy(name="unsaved"))
        val next=p.copy(name="Saved revision")
        repository.save("project.bimob",next)
        assertNull(journal.recover(sha(BimobArchiveWriter().write(next)),next))
        dir.deleteRecursively()
    }
}
