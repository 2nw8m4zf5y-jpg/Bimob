package com.bimob.app.core.persistence

import com.bimob.app.core.model.Project
import com.bimob.app.core.model.ModelValidator
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption
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
        val integrity=Json.bytes(JO(linkedMapOf("sha256" to JA(declared.map{JO(linkedMapOf("path" to JS(it.path),"digest" to JS(it.sha256))) })))
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
                    if(e.compressedSize>0&&count/e.compressedSize.coerceAtLeast(1)>limits.maxRatio)throw ArchiveFormatException("Compression ratio limit exceeded.")
                    entries[name]=out.toByteArray();zip.closeEntry()
                }
            }
            val manifest=entries["manifest.json"]?:throw ArchiveFormatException("manifest.json missing")
            val m=parseManifest(Json.parse(manifest).obj())
            if(m.containerVersion!=1)throw ArchiveFormatException("Unsupported container version "+m.containerVersion+".")
            if(m.minimumReaderVersion>1)throw ArchiveFormatException("Reader version too old.")
            if(m.schemaVersion>migrations.currentVersion)throw UnsupportedSchemaException(m.schemaVersion,migrations.currentVersion)
            if(m.reservedDirectories.none{it=="chunks/"})throw ArchiveFormatException("Manifest does not declare chunks/ reservation.")
            val index=entries["integrity/sha256.json"]?:throw IntegrityException("Integrity index missing.")
            val hashes=Json.parse(index).obj().req("sha256").array().a.associate{val x=it.obj();x.req("path").text() to x.req("digest").text()}
            m.entries.forEach{decl->
                val data=entries[decl.path]?:throw IntegrityException("Required payload missing: "+decl.path)
                if(data.size.toLong()!=decl.size)throw IntegrityException("Payload size mismatch: "+decl.path)
                if(sha(data)!=decl.sha256||hashes[decl.path]!=decl.sha256)throw IntegrityException("SHA-256 mismatch: "+decl.path)
            }
            val model=entries["model/model.json"]?:throw IntegrityException("Canonical model missing.")
            val project=CanonicalProjectJson.decode(model)
            if(project.id.value!=m.projectId||project.schemaVersion!=m.schemaVersion)throw IntegrityException("Manifest/model identity or schema mismatch.")
            return migrations.migrate(project)
        }catch(e:PersistenceException){throw e}catch(e:Exception){throw ArchiveFormatException("Invalid or damaged BIMOB archive.",e)}
    }
    private fun validatePath(p:String){if(p.startsWith("/")||p.contains("\\")||p.split('/').any{it==".."||it=="."}||p.contains(":"))throw ArchiveFormatException("Unsafe ZIP path.")}
    private fun parseManifest(o:JO):BimobManifest {
        fun int(k:String)=o.req(k).long().toInt()
        val entries=o.req("entries").array().a.map{val x=it.obj();ManifestEntry(x.req("path").text(),x.req("size").long(),x.req("sha256").text())}
        return BimobManifest(int("containerVersion"),int("schemaVersion"),int("minimumReaderVersion"),o.req("writerVersion").text(),o.req("projectId").text(),o.req("features").array().a.map{it.text()},entries,o.req("reservedDirectories").array().a.map{it.text()})
    }
}

interface AtomicStorage {
    fun read(path:String):ByteArray
    fun writeAtomically(path:String,bytes:ByteArray)
    fun appendDurably(path:String,bytes:ByteArray)
    fun exists(path:String):Boolean
    fun delete(path:String)
}
/** JVM adapter; Android SAF must implement AtomicStorage with provider-specific guarantees. */
class JvmAtomicStorage(private val root:File):AtomicStorage {
    init { root.mkdirs() }
    private fun resolve(path:String):File {require(!path.startsWith("/")&&!path.contains(".."));val f=File(root,path);require(f.canonicalPath.startsWith(root.canonicalPath+File.separator));return f}
    override fun read(path:String)=resolve(path).readBytes()
    override fun appendDurably(path:String,bytes:ByteArray){
        val f=resolve(path);f.parentFile?.mkdirs()
        RandomAccessFile(f,"rw").use{it.seek(it.length());it.write(bytes);it.fd.sync()}
    }
    override fun exists(path:String)=resolve(path).exists()
    override fun delete(path:String)=resolve(path).delete()
    override fun writeAtomically(path:String,bytes:ByteArray){
        val target=resolve(path);target.parentFile?.mkdirs()
        val temp=File(target.parentFile,target.name+".tmp-"+System.nanoTime())
        FileOutputStream(temp).use{it.write(bytes);it.flush();it.fd.sync()}
        try{Files.move(temp.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)}
        catch(e:Exception){temp.delete();throw PersistenceException("Atomic replacement unsupported or failed; destination retained.",e)}
        try{FileInputStream(target.parentFile).use{it.fd.sync()}}catch(_:Exception){/* best-effort directory sync */}
    }
}

data class SaveResult(val digest:String,val journalRotationWarning:String?=null)
class ProjectRepository(private val storage:AtomicStorage,private val writer:BimobArchiveWriter=BimobArchiveWriter(),private val reader:BimobArchiveReader=BimobArchiveReader(),private val journal:CrashRecoveryJournal?=null) {
    fun save(path:String,project:Project):SaveResult {
        val bytes=writer.write(project)
        val staged=reader.read(bytes);require(staged==project){"Staged archive round-trip mismatch."}
        storage.writeAtomically(path,bytes)
        val digest=sha(bytes)
        var warning:String?=null
        try{journal?.compact(project.id.value,digest,project.schemaVersion)}catch(e:Exception){warning=e.message?:e.javaClass.simpleName}
        return SaveResult(digest,warning)
    }
    fun load(path:String)=reader.read(storage.read(path))
    fun recover(path:String,project:Project):Project?=journal?.recover(sha(storage.read(path)),project)
}
