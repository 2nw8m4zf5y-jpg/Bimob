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
    @Test fun canonicalJsonMatchesGoldenBytesAndSha256() {
        val minimal=Project(id=ModelId("p"),name="x",schemaVersion=1,site=Site(ModelId("s"),"site"))
        val expected="""{"schemaVersion":1,"projectId":"p","name":"x","site":{"id":"s","name":"site"},"buildings":[],"levels":[],"materials":[],"types":[],"elements":[]}"""
        val bytes=CanonicalProjectJson.encode(minimal)
        assertEquals(expected,String(bytes,java.nio.charset.StandardCharsets.UTF_8))
        assertEquals("70829131cb7633faacb617b7662e1b1337d20177adf2e952e3fd1551f2c04ebc",sha(bytes))
    }

    @Test fun truncatedJournalTailPreservesLastCommittedRecoveryRecord() {
        val dir=File(System.getProperty("java.io.tmpdir"),"bimob-tail-"+System.nanoTime())
        val storage=JvmAtomicStorage(dir)
        val journal=CrashRecoveryJournal(storage,"journal/recovery.bin")
        val base=fixture()
        val repo=ProjectRepository(storage,journal=journal)
        val saved=repo.save("project.bimob",base)
        val changed=base.copy(name="Last committed")
        journal.append(base.id.value,saved.digest,changed)
        storage.appendDurably("journal/recovery.bin",byteArrayOf(0x01,0x02,0x03))
        val recovered=journal.recoverDetailed(saved.digest,base)
        assertEquals(changed,recovered?.project)
        assertTrue(recovered?.stoppedAtCorruptTail==true)
        dir.deleteRecursively()
    }

}
