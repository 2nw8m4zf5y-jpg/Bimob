package com.bimob.app.core.persistence

import com.bimob.app.core.model.Project
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets

private const val JOURNAL_MAGIC=0x424A4E31 // "BJN1"
private const val RECORD_COMMIT=0x434D5431 // "CMT1"
private const val MAX_JOURNAL_RECORD=16*1024*1024

/** Durable append-only full-snapshot journal; record framing makes incomplete tails detectable. */
class CrashRecoveryJournal(private val storage:AtomicStorage,private val path:String) {
    data class Recovery(val project:Project,val appliedRecords:Int,val stoppedAtCorruptTail:Boolean)
    private fun frame(payload:ByteArray):ByteArray {
        require(payload.size<=MAX_JOURNAL_RECORD)
        val digest=hexToBytes(sha(payload))
        val out=ByteArrayOutputStream()
        DataOutputStream(out).use{it.writeInt(JOURNAL_MAGIC);it.writeInt(payload.size);it.write(payload);it.write(digest);it.writeInt(RECORD_COMMIT)}
        return out.toByteArray()
    }
    private fun header(projectId:String,baseDigest:String,schema:Int)=Json.bytes(JO(linkedMapOf(
        "kind" to JS("header"),"projectId" to JS(projectId),"baseDigest" to JS(baseDigest),"schemaVersion" to JN(schema.toString()),"journalVersion" to JN("1")
    )))
    fun append(projectId:String,baseDigest:String,project:Project) {
        require(project.id.value==projectId){"Journal project identity mismatch"}
        if(!storage.exists(path))storage.writeAtomically(path,frame(header(projectId,baseDigest,project.schemaVersion)))
        val state=scan(storage.read(path))
        require(state.headerProjectId==projectId&&state.baseDigest==baseDigest){"Journal base does not match current saved project; compact first."}
        val seq=state.lastSequence+1
        val payload=Json.bytes(JO(linkedMapOf("kind" to JS("snapshot"),"sequence" to JN(seq.toString()),"projectId" to JS(projectId),"snapshot" to JS(java.util.Base64.getEncoder().encodeToString(CanonicalProjectJson.encode(project))))))
        storage.appendDurably(path,frame(payload))
    }
    fun recover(baseDigest:String,baseProject:Project):Project? {
        if(!storage.exists(path))return null
        val s=scan(storage.read(path))
        if(s.headerProjectId!=baseProject.id.value||s.baseDigest!=baseDigest||s.schemaVersion!=baseProject.schemaVersion)return null
        return s.latestProject
    }
    /** Rotate only after the primary file has been verified and made durable. AtomicStorage replaces the journal atomically. */
    fun compact(projectId:String,newBaseDigest:String,schemaVersion:Int) {
        storage.writeAtomically(path,frame(header(projectId,newBaseDigest,schemaVersion)))
    }
    private data class Scan(val headerProjectId:String?,val baseDigest:String?,val schemaVersion:Int,val lastSequence:Long,val latestProject:Project?,val corrupt:Boolean)
    private fun scan(bytes:ByteArray):Scan {
        var offset=0;var headerId:String?=null;var base:String?=null;var schema=0;var sequence=0L;var latest:Project?=null;var corrupt=false
        while(offset<bytes.size){
            try{
                require(bytes.size-offset>=8)
                val input=java.io.DataInputStream(java.io.ByteArrayInputStream(bytes,offset,bytes.size-offset))
                require(input.readInt()==JOURNAL_MAGIC)
                val length=input.readInt();require(length in 1..MAX_JOURNAL_RECORD)
                require(bytes.size-offset>=8+length+32+4)
                val payload=ByteArray(length);input.readFully(payload)
                val digest=ByteArray(32);input.readFully(digest)
                require(digest.contentEquals(hexToBytes(sha(payload))))
                require(input.readInt()==RECORD_COMMIT)
                offset+=8+length+32+4
                val record=Json.parse(payload).obj()
                when(record.req("kind").text()){
                    "header"->{require(headerId==null);headerId=record.req("projectId").text();base=record.req("baseDigest").text();schema=record.req("schemaVersion").long().toInt()}
                    "snapshot"->{require(headerId!=null);val seq=record.req("sequence").long();require(seq==sequence+1);require(record.req("projectId").text()==headerId);val raw=java.util.Base64.getDecoder().decode(record.req("snapshot").text());val p=CanonicalProjectJson.decode(raw);require(p.id.value==headerId&&p.schemaVersion==schema);sequence=seq;latest=p}
                    else->error("Unknown journal record")
                }
            }catch(_:Exception){corrupt=true;break}
        }
        return Scan(headerId,base,schema,sequence,latest,corrupt)
    }
    companion object {
        internal fun hexToBytes(hex:String)=hex.chunked(2).map{it.toInt(16).toByte()}.toByteArray()
    }
}
