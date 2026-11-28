package com.bimob.app.persistence

import com.bimob.app.core.persistence.AtomicStorage
import com.bimob.app.core.persistence.AtomicStorageCapabilities
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

/** Internal-only durable file adapter. SAF URIs never enter this class. */
class PrivateFileAtomicStorage(private val root: File) : AtomicStorage {
    override val capabilities = AtomicStorageCapabilities(
        atomicReplace = true,
        durableFileSync = true,
        stagedReadbackVerification = true,
        directorySync = false
    )

    init { require(root.mkdirs() || root.isDirectory) { "Unable to create private repository directory." } }

    private fun resolve(path: String): File {
        require(path.isNotBlank() && !path.startsWith("/") && !path.split('/').any { it == "." || it == ".." })
        val target = File(root, path)
        require(target.canonicalPath.startsWith(root.canonicalPath + File.separator))
        return target
    }

    override fun read(path: String): ByteArray = resolve(path).readBytes()
    override fun exists(path: String): Boolean = resolve(path).isFile
    override fun delete(path: String) { resolve(path).delete() }

    override fun appendDurably(path: String, bytes: ByteArray) {
        val file = resolve(path)
        file.parentFile?.mkdirs()
        RandomAccessFile(file, "rw").use { it.seek(it.length()); it.write(bytes); it.fd.sync() }
    }

    override fun writeAtomically(path: String, bytes: ByteArray) {
        val target = resolve(path)
        val parent = target.parentFile ?: throw IllegalStateException("Repository target has no parent directory.")
        require(parent.mkdirs() || parent.isDirectory)
        val temp = File(parent, target.name + ".tmp-" + System.nanoTime())
        try {
            FileOutputStream(temp).use { it.write(bytes); it.flush(); it.fd.sync() }
            require(temp.length() == bytes.size.toLong() && temp.readBytes().contentEquals(bytes)) { "Staged write verification failed." }
            if (!temp.renameTo(target)) throw IllegalStateException("Atomic rename failed; original file was not replaced.")
            require(target.readBytes().contentEquals(bytes)) { "Repository read-back verification failed." }
        } finally { temp.delete() }
    }
}
