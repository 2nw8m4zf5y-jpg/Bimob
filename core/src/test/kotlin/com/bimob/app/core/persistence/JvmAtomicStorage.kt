package com.bimob.app.core.persistence

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Test-only JVM adapter exercising fsync + same-filesystem atomic rename. */
class JvmAtomicStorage(private val root:File):AtomicStorage {
    override val capabilities=AtomicStorageCapabilities(atomicReplace=true,durableFileSync=true,stagedReadbackVerification=true,directorySync=false)
    init { root.mkdirs() }
    private fun resolve(path:String):File {
        require(!path.startsWith("/")&&!path.split('/').any{it==".."||it=="."})
        val f=File(root,path);require(f.canonicalPath.startsWith(root.canonicalPath+File.separator));return f
    }
    override fun read(path:String)=resolve(path).readBytes()
    override fun exists(path:String)=resolve(path).exists()
    override fun delete(path:String){resolve(path).delete()}
    override fun appendDurably(path:String,bytes:ByteArray){
        val f=resolve(path);f.parentFile?.mkdirs()
        RandomAccessFile(f,"rw").use{it.seek(it.length());it.write(bytes);it.fd.sync()}
    }
    override fun writeAtomically(path:String,bytes:ByteArray){
        val target=resolve(path);target.parentFile?.mkdirs()
        val temp=File(target.parentFile,target.name+".tmp-"+System.nanoTime())
        try{FileOutputStream(temp).use{it.write(bytes);it.flush();it.fd.sync()};require(temp.readBytes().contentEquals(bytes)){"Staged-file verification failed."}}
        catch(e:Exception){temp.delete();throw IllegalStateException("Temporary write/fsync/verify failed.",e)}
        try{Files.move(temp.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)}
        catch(e:Exception){temp.delete();throw IllegalStateException("Atomic replacement failed; old destination must remain.",e)}
        try{FileInputStream(target.parentFile).use{it.fd.sync()}}catch(_:Exception){/* JVM/platform may not expose directory fsync */}
    }
}
