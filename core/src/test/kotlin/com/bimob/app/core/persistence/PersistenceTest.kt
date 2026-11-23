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
        val damagedOut=java.io.ByteArrayOutputStream()
        java.util.zip.ZipInputStream(bytes.inputStream()).use { zin ->
            java.util.zip.ZipOutputStream(damagedOut).use { zout ->
                while(true) {
                    val entry=zin.nextEntry ?: break
                    val data=zin.readBytes()
                    val changed=if(entry.name=="model/model.json") data.copyOf().also { it[0]=(it[0].toInt() xor 1).toByte() } else data
                    val outEntry=java.util.zip.ZipEntry(entry.name).also { it.time=0L }
                    zout.putNextEntry(outEntry);zout.write(changed);zout.closeEntry()
                }
            }
        }
        try {
            BimobArchiveReader().read(damagedOut.toByteArray())
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
    @Test fun compactionFailureBeforeJournalSwitchPreservesOldRecoveryRecords() {
        val dir=File(System.getProperty("java.io.tmpdir"),"bimob-compact-crash-"+System.nanoTime())
        val real=JvmAtomicStorage(dir)
        val base=fixture()
        val journalPath="workspace/journal.bin"
        val journal=CrashRecoveryJournal(real,journalPath)
        val saved=ProjectRepository(real,journal=journal).save("base.bimob",base)
        val recovery=base.copy(name="Preserved recovery snapshot")
        journal.append(base.id.value,saved.digest,recovery,transactionId="before-rotation")
        val failingSwitch=object:AtomicStorage {
            override val capabilities=real.capabilities
            override fun read(path:String)=real.read(path)
            override fun appendDurably(path:String,bytes:ByteArray)=real.appendDurably(path,bytes)
            override fun exists(path:String)=real.exists(path)
            override fun delete(path:String)=real.delete(path)
            override fun writeAtomically(path:String,bytes:ByteArray) {
                if(path==journalPath) throw IllegalStateException("Injected crash during journal switch")
                real.writeAtomically(path,bytes)
            }
        }
        try {
            CrashRecoveryJournal(failingSwitch,journalPath).compact(base.id.value,sha("new-base".toByteArray()),base.schemaVersion)
            fail("Injected compaction interruption must fail.")
        } catch(e:IllegalStateException) {
            assertEquals("Injected crash during journal switch",e.message)
        }
        val afterFailure=CrashRecoveryJournal(real,journalPath).recoverDetailed(saved.digest,base)
        assertEquals("Old journal must remain recoverable after failed rotation.",recovery,afterFailure?.project)
        assertEquals(1L,afterFailure?.appliedRecords)
        assertFalse(real.exists("$journalPath.compact-stage"))
        dir.deleteRecursively()
    }

    @Test fun canonicalGoldenFixtureCoversMultilingualTextParametersAndLayeredWalls() {
        val material=Material(ModelId("mat-g"),"Кирпич","Стена",1600.0,0.5,12.0,"m3")
        val type=ElementType(ModelId("type-g"),"Стеновой тип",ElementCategory.WALL,listOf(
            Parameter(ModelId("bool-g"),ParameterValue.Bool(true)),
            Parameter(ModelId("int-g"),ParameterValue.IntNumber(7)),
            Parameter(ModelId("real-g"),ParameterValue.Real(0.5)),
            Parameter(ModelId("text-g"),ParameterValue.Text("Кавычки: \"тест\"; строка\nвторая")),
            Parameter(ModelId("ref-g"),ParameterValue.Reference(material.id)),
            Parameter(ModelId("enum-g"),ParameterValue.EnumValue("STRUCTURE"))
        ))
        val firstWall=WallElement(
            id=ModelId("wall-a"),typeId=type.id,levelId=ModelId("level-g"),
            axis=WallAxis(Point2(0.5,0.0),Point2(2.5,0.0)),heightMeters=3.0,thicknessMeters=0.25,
            baseElevationMeters=0.0,topElevationMeters=3.0,locationLine=WallLocationLine.CORE_CENTER,
            layers=listOf(WallLayer(material.id,0.125,WallLayerFunction.STRUCTURE,true),WallLayer(null,0.125,WallLayerFunction.INSULATION,false)),
            parameters=listOf(Parameter(ModelId("instance-real"),ParameterValue.Real(2.5)))
        )
        val secondWall=WallElement(
            id=ModelId("wall-b"),typeId=type.id,levelId=ModelId("level-g"),
            axis=WallAxis(Point2(2.5,0.0),Point2(2.5,1.5)),heightMeters=3.0,thicknessMeters=0.25,
            baseElevationMeters=0.0,topElevationMeters=3.0,locationLine=WallLocationLine.CENTER,
            layers=listOf(WallLayer(material.id,0.25,WallLayerFunction.STRUCTURE,true)),
            parameters=listOf(Parameter(ModelId("instance-text"),ParameterValue.Text("UTF-8: ё, 漢字, 🏔️")))
        )
        val rich=Project(
            id=ModelId("golden-rich"),name="BIMOB \"Кавказ\"\n🌄",schemaVersion=1,
            site=Site(ModelId("site-g"),"Дом 🏡"),buildings=listOf(Building(ModelId("building-g"),"Корпус")),
            levels=listOf(Level(ModelId("level-g"),"Этаж 1",0.5,3.0)),
            elements=listOf(firstWall,secondWall),materials=listOf(material),types=listOf(type)
        )
        val expected=javaClass.getResource("/golden/canonical-rich.json")!!.readBytes()
        val encoded=CanonicalProjectJson.encode(rich)
        assertArrayEquals(expected,encoded)
        assertEquals("25583cd0e19395e4ae6b59bfe08d93970a44cca12447f28ccac37f95574d9c42",sha(encoded))
        assertEquals(rich,CanonicalProjectJson.decode(expected))
    }

    @Test fun emptyProjectGoldenFixtureIsByteExact() {
        val empty=Project(id=ModelId("p"),name="x",schemaVersion=1,site=Site(ModelId("s"),"site"))
        val expected=javaClass.getResource("/golden/canonical-empty.json")!!.readBytes()
        assertArrayEquals(expected,CanonicalProjectJson.encode(empty))
        assertEquals("70829131cb7633faacb617b7662e1b1337d20177adf2e952e3fd1551f2c04ebc",sha(expected))
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

    @Test fun journalAppendIsIdempotentForTransactionRetry() {
        val dir=File(System.getProperty("java.io.tmpdir"),"bimob-idempotent-"+System.nanoTime())
        val storage=JvmAtomicStorage(dir)
        val journal=CrashRecoveryJournal(storage,"recovery/journal.bin")
        val base=fixture()
        val saved=ProjectRepository(storage,journal=journal).save("base.bimob",base)
        val change=base.copy(name="Transaction result")
        journal.append(base.id.value,saved.digest,change,transactionId="txn-fixed")
        journal.append(base.id.value,saved.digest,change.copy(name="Retry payload ignored"),transactionId="txn-fixed")
        val recovered=journal.recoverDetailed(saved.digest,base)
        assertEquals(change,recovered?.project)
        assertEquals(1L,recovered?.appliedRecords)
        dir.deleteRecursively()
    }

    @Test fun atomicStorageFailureLeavesPreviousProjectReadable() {
        val dir=File(System.getProperty("java.io.tmpdir"),"bimob-atomic-fail-"+System.nanoTime())
        val real=JvmAtomicStorage(dir)
        val repo=ProjectRepository(real)
        val original=fixture()
        repo.save("project.bimob",original)
        val failing=object:AtomicStorage {
            override val capabilities=real.capabilities
            override fun read(path:String)=real.read(path)
            override fun appendDurably(path:String,bytes:ByteArray)=real.appendDurably(path,bytes)
            override fun exists(path:String)=real.exists(path)
            override fun delete(path:String){real.delete(path)}
            override fun writeAtomically(path:String,bytes:ByteArray){throw IllegalStateException("Injected failure before replace")}
        }
        try {
            ProjectRepository(failing).save("project.bimob",original.copy(name="Not saved"))
            fail("Injected atomic write failure should propagate.")
        } catch(_:IllegalStateException) {}
        assertEquals(original,repo.load("project.bimob"))
        dir.deleteRecursively()
    }

}
