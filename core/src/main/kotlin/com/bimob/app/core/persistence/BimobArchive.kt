package com.bimob.app.core.persistence

import com.bimob.app.core.model.Project
import com.bimob.app.core.model.ModelValidator
import com.bimob.app.core.model.ModelRevision
import com.bimob.app.core.model.ModelRevision
import com.bimob.app.core.model.ModelRevision
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

sealed class PersistenceException(message:String,cause:Throwable?=null):Exception(message,cause)
class IntegrityException(message:String,cause:Throwable?=null):PersistenceException(message,cause)
class UnsupportedSchemaException(val found:Int,val supported:Int):PersistenceException("Schema "+found+" is newer than supported schema "+supported+".")
class ArchiveFormatException(message:String,cause:Throwable?=null):PersistenceException(message,cause)
class MigrationException(message:String,cause:Throwable?=null):PersistenceException(message,cause)

internal fun sha(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
data class ManifestEntry(val path:String,val size:Long,val sha256:String)
data class BimobManifest(val containerVersion:Int,val schemaVersion:Int,val minimumReaderVersion:Int,val writerVersion:String,val projectId:String,val features:List<String>,val entries:List<ManifestEntry>,val reservedDirectories:List<String>)
data class ArchiveLimits(val maxEntries:Int=128,val maxEntryBytes:Long=64L*1024*1024,val maxTotalBytes:Long=128L*1024*1024,val maxRatio:Long=200)

class BimobArchiveWriter {
    fun write(project:Project):ByteArray {
        val errors=ModelValidator.validate(project).filter{it.severity==com.bimob.app.core.model.ValidationSeverity.ERROR}
        require(errors.isEmpty()){"Project validation failed: "+errors.joinToString { it.code }}
        val model=CanonicalProjectJson.encode(project)
        val declared=listOf(ManifestEntry("model/model.json",model.size.toLong(),sha(model)))
        val digestEntries=declared.map { entry -> JO(linkedMapOf("path" to JS(entry.path), "digest" to JS(entry.sha256))) }
        val integrity=Json.bytes(JO(linkedMapOf("sha256" to JA(digestEntries))))
        val manifest=manifestJson(BimobManifest(1,project.schemaVersion,1,"BIMOB-Kotlin/0.1",project.id.value,emptyList(),declared,listOf("chunks/")))
        val entries=linkedMapOf("manifest.json" to manifest,"model/model.json" to model,"integrity/sha256.json" to integrity,"chunks/" to ByteArray(0))
        val output=ByteArrayOutputStream()
        ZipOutputStream(output).use{zip->entries.forEach{(path,data)->val e=ZipEntry(path);e.time=0L;e.method=ZipEntry.DEFLATED;zip.putNextEntry(e);zip.write(data);zip.closeEntry()}}
        return output.toByteArray()
    }
    private fun manifestJson(m:BimobManifest)=Json.bytes(JO(linkedMapOf(
        "containerVersion" to JN(m.containerVersion.toString()),"schemaVersion" to JN(m.schemaVersion.toString()),
        "minimumReaderVersion" to JN(m.minimumReaderVersion.toString()),"writerVersion" to JS(m.writerVersion),
        "projectId" to JS(m.projectId),"features" to JA(m.features.map(::JS)),
        "entries" to JA(m.entries.map{JO(linkedMapOf("path" to JS(it.path),"size" to JN(it.size.toString()),"sha256" to JS(it.sha256)))}),
        "reservedDirectories" to JA(m.reservedDirectories.map(::JS))
    )))
}

class SchemaMigration(val fromVersion:Int,val toVersion:Int,val migrate:(Project)->Project) {
    init { require(toVersion==fromVersion+1) }
}
class SchemaMigrationRegistry(migrations:List<SchemaMigration>,val currentVersion:Int=1) {
    private val migrations=migrations.associateBy{it.fromVersion}
    fun migrate(project:Project):Project {
        if(project.schemaVersion>currentVersion)throw UnsupportedSchemaException(project.schemaVersion,currentVersion)
        var p=project
        while(p.schemaVersion<currentVersion){val m=migrations[p.schemaVersion]?:throw MigrationException("Missing migration from schema "+p.schemaVersion+".");p=m.migrate(p);require(p.schemaVersion==m.toVersion){"Migration returned wrong schema version"}}
        return p
    }
    companion object { val NONE=SchemaMigrationRegistry(emptyList(),1) }
}

class BimobArchiveReader(private val limits:ArchiveLimits=ArchiveLimits(),private val migrations:SchemaMigrationRegistry=SchemaMigrationRegistry.NONE) {
    fun read(bytes:ByteArray):Project {
        if(bytes.size>limits.maxTotalBytes)throw ArchiveFormatException("Archive exceeds total-size limit.")
        try {
            val entries=linkedMapOf<String,ByteArray>()
            var total=0L
            ZipInputStream(ByteArrayInputStream(bytes)).use{zip->
                while(true){val e=zip.nextEntry?:break
                    if(entries.size>=limits.maxEntries)throw ArchiveFormatException("Too many ZIP entries.")
                    val name=e.name;validatePath(name)
                    if(entries.containsKey(name))throw ArchiveFormatException("Duplicate ZIP entry: "+name)
                    val out=ByteArrayOutputStream();val buf=ByteArray(8192);var count=0L
                    while(true){val n=zip.read(buf);if(n<0)break;count+=n;total+=n
                        if(count>limits.maxEntryBytes||total>limits.maxTotalBytes)throw ArchiveFormatException("Expanded archive size limit exceeded.")
                        out.write(buf,0,n)
                    }
                    if(e.size>=0&&e.size!=count)throw IntegrityException("ZIP entry length mismatch: "+name)
                    if(e.compressedSize>0&&count.toDouble()/e.compressedSize.toDouble()>limits.maxRatio.toDouble())throw ArchiveFormatException("Compression ratio limit exceeded.")
                    entries[name]=out.toByteArray();zip.closeEntry()
                }
            }
            val manifest=entries["manifest.json"]?:throw ArchiveFormatException("manifest.json missing")
            val m=parseManifest(Json.parse(manifest).obj())
            if(m.containerVersion!=1)throw ArchiveFormatException("Unsupported container version "+m.containerVersion+".")
            if(m.minimumReaderVersion>1)throw ArchiveFormatException("Reader version too old.")
            if(m.schemaVersion>migrations.currentVersion)throw UnsupportedSchemaException(m.schemaVersion,migrations.currentVersion)
            if(m.reservedDirectories.none{it=="chunks/"})throw ArchiveFormatException("Manifest does not declare chunks/ reservation.")
            require(m.entries.map{it.path}.distinct().size==m.entries.size){"Duplicate manifest payload path"}
            m.entries.forEach{validatePath(it.path);require(it.sha256.matches(Regex("[0-9a-f]{64}")))}
            if(m.entries.none{it.path=="model/model.json"})throw IntegrityException("Manifest omits canonical model payload.")
            if(m.features.isNotEmpty())throw ArchiveFormatException("Unsupported required feature flags: "+m.features.joinToString())
            val index=entries["integrity/sha256.json"]?:throw IntegrityException("Integrity index missing.")
            val hashRecords=Json.parse(index).obj().req("sha256").array().a
            val hashes=hashRecords.associate{val x=it.obj();x.req("path").text() to x.req("digest").text()}
            if(hashes.size!=hashRecords.size||hashes.keys!=m.entries.map{it.path}.toSet())throw IntegrityException("Integrity index does not match manifest payload set.")
            m.entries.forEach{decl->
                val data=entries[decl.path]?:throw IntegrityException("Required payload missing: "+decl.path)
                if(data.size.toLong()!=decl.size)throw IntegrityException("Payload size mismatch: "+decl.path)
                if(sha(data)!=decl.sha256||hashes[decl.path]!=decl.sha256)throw IntegrityException("SHA-256 mismatch: "+decl.path)
            }
            val model=entries["model/model.json"]?:throw IntegrityException("Canonical model missing.")
            val project=CanonicalProjectJson.decode(model)
            if(project.id.value!=m.projectId||project.schemaVersion!=m.schemaVersion)throw IntegrityException("Manifest/model identity or schema mismatch.")
            val migrated=migrations.migrate(project)
            val issues=ModelValidator.validate(migrated).filter{it.severity==com.bimob.app.core.model.ValidationSeverity.ERROR}
            if(issues.isNotEmpty())throw ArchiveFormatException("Model invariants failed: "+issues.joinToString{it.code})
            return migrated
        }catch(e:PersistenceException){throw e}catch(e:Exception){throw ArchiveFormatException("Invalid or damaged BIMOB archive.",e)}
    }
    private fun validatePath(p:String){if(p.startsWith("/")||p.contains("\\")||p.split('/').any{it==".."||it=="."}||p.contains(":"))throw ArchiveFormatException("Unsafe ZIP path.")}
    private fun parseManifest(o:JO):BimobManifest {
        fun int(k:String)=o.req(k).long().toInt()
        val entries=o.req("entries").array().a.map{val x=it.obj();ManifestEntry(x.req("path").text(),x.req("size").long(),x.req("sha256").text())}
        return BimobManifest(int("containerVersion"),int("schemaVersion"),int("minimumReaderVersion"),o.req("writerVersion").text(),o.req("projectId").text(),o.req("features").array().a.map{it.text()},entries,o.req("reservedDirectories").array().a.map{it.text()})
    }
}

data class AtomicStorageCapabilities(
    val atomicReplace:Boolean,
    val durableFileSync:Boolean,
    val stagedReadbackVerification:Boolean,
    val directorySync:Boolean
)

/** Implementations must write a same-volume temp, flush/fsync, verify staged bytes, then atomically replace. */
interface AtomicStorage {
    val capabilities:AtomicStorageCapabilities
    fun read(path:String):ByteArray
    fun writeAtomically(path:String,bytes:ByteArray)
    fun appendDurably(path:String,bytes:ByteArray)
    fun exists(path:String):Boolean
    fun delete(path:String)
}
data class SaveResult(val digest:String,val journalRotationWarning:String?=null)
class ProjectRepository(private val storage:AtomicStorage,private val writer:BimobArchiveWriter=BimobArchiveWriter(),private val reader:BimobArchiveReader=BimobArchiveReader(),private val journal:CrashRecoveryJournal?=null) {
    fun save(path:String,project:Project):SaveResult {
        val caps=storage.capabilities
        require(caps.atomicReplace&&caps.durableFileSync&&caps.stagedReadbackVerification){"Storage does not satisfy atomic-save durability requirements."}
        val bytes=writer.write(project)
        val staged=reader.read(bytes);require(staged==project){"Staged archive round-trip mismatch."}
        storage.writeAtomically(path,bytes)
        val digest=sha(bytes)
        var warning:String?=null
        try{journal?.compact(project.id.value,digest,project.schemaVersion)}catch(e:Exception){warning=e.message?:e.javaClass.simpleName}
        return SaveResult(digest,warning)
    }
    fun load(path:String)=reader.read(storage.read(path))
    /** Stores an exact immutable revision snapshot in app-private repository storage. */
    fun save(path:String, revision:ModelRevision):SaveResult {
        val caps=storage.capabilities
        require(caps.atomicReplace&&caps.durableFileSync&&caps.stagedReadbackVerification){"Storage does not satisfy atomic-save durability requirements."}
        val bytes=ModelRevisionCodec.encode(revision)
        val staged=ModelRevisionCodec.decode(bytes)
        require(staged==revision){"Revision round-trip mismatch."}
        storage.writeAtomically(path,bytes)
        val digest=sha(bytes)
        require(sha(storage.read(path))==digest){"Revision read-back verification failed."}
        return SaveResult(digest)
    }
    fun loadRevision(path:String):ModelRevision = ModelRevisionCodec.decode(storage.read(path))
    fun revisionExists(path:String):Boolean = storage.exists(path)

    fun recover(path:String,project:Project):Project? { val bytes=storage.read(path);val saved=reader.read(bytes);require(saved==project){"Recovery base project does not match saved file."};return journal?.recover(sha(bytes),saved) }
}
