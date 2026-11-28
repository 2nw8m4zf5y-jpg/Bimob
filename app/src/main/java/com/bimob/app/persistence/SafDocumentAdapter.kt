package com.bimob.app.persistence

import android.content.ContentResolver
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * SAF document-provider bridge. It deliberately does NOT implement core AtomicStorage:
 * providers may not offer atomic replace, durable sync, or directory sync semantics.
 * Export uses a newly-created document, stages bytes in app-private cache, and verifies
 * a read-back digest. A provider write can still be interrupted; callers must surface it.
 */
class SafDocumentAdapter(private val resolver:ContentResolver,private val cacheDir:File) {
    companion object {
        const val BIMOB_MIME="application/vnd.bimob.project"
        const val MAX_DOCUMENT_BYTES=128L*1024L*1024L
    }

    fun read(uri:Uri):ByteArray {
        resolver.openInputStream(uri)?.use { input ->
            val out=java.io.ByteArrayOutputStream()
            val buffer=ByteArray(8192)
            var total=0L
            while(true) {
                val n=input.read(buffer)
                if(n<0)break
                total+=n
                require(total<=MAX_DOCUMENT_BYTES){"BIMOB document exceeds SAF read limit."}
                out.write(buffer,0,n)
            }
            return out.toByteArray()
        } ?: throw IllegalStateException("Document provider returned no input stream.")
    }

    /** Write only to a URI created by ACTION_CREATE_DOCUMENT; this is not atomic replacement. */
    fun writeNewDocument(uri:Uri,bytes:ByteArray):String {
        require(bytes.size.toLong()<=MAX_DOCUMENT_BYTES){"BIMOB document exceeds SAF write limit."}
        val staged=File(cacheDir,"bimob-saf-stage-"+System.nanoTime()+".tmp")
        try {
            FileOutputStream(staged).use { stream -> stream.write(bytes);stream.flush();stream.fd.sync() }
            require(staged.length()==bytes.size.toLong()){"Local staging size mismatch."}
            resolver.openOutputStream(uri,"w")?.use { output ->
                FileInputStream(staged).use { input -> input.copyTo(output) }
                output.flush()
            } ?: throw IllegalStateException("Document provider returned no output stream.")
            val digest=sha256(bytes)
            val readBack=read(uri)
            require(sha256(readBack)==digest){"SAF document read-back digest mismatch."}
            return digest
        } finally {
            staged.delete()
        }
    }

    private fun sha256(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
