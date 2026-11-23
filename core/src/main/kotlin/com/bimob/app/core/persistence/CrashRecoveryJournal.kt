package com.bimob.app.core.persistence

import com.bimob.app.core.model.Project
import com.bimob.app.core.model.ModelValidator
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets

private const val JOURNAL_MAGIC=0x424A4E31 // "BJN1"
private const val RECORD_COMMIT=0x434D5431 // "CMT1"
private const val MAX_JOURNAL_RECORD=16*1024*1024

/** Durable append-only full-snapshot journal; record framing makes incomplete tails detectable. */
class CrashRecoveryJournal(private val storage:AtomicStorage,private val path:String) {
    data class Recovery(val project:Project,val appliedRecords:Long,val stoppedAtCorruptTail:Boolean)
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
    fun append(projectId:String,baseDigest:String,project:Project,transactionId:String=java.util.UUID.randomUUID().toString()) {
        require(project.id.value==projectId){"Journal project identity mismatch"}
        require(baseDigest.matches(Regex("[0-9a-f]{64}"))){"Invalid base digest"}
        require(transactionId.isNotBlank()){"Transaction ID must not be blank"}
        require(ModelValidator.validate(project).none{it.severity==com.bimob.app.core.model.ValidationSeverity.ERROR}){"Journal snapshot failed model validation."}
        if(!storage.exists(path))storage.writeAtomically(path,frame(header(projectId,baseDigest,project.schemaVersion)))
        val state=scan(storage.read(path))
        require(!state.corrupt){"Journal has an incomplete/corrupt tail; compact it before appending."}
        require(state.headerProjectId==projectId&&state.baseDigest==baseDigest){"Journal base does not match current saved project; compact first."}
        if(transactionId in state.transactionIds)return // retry after an uncertain append is idempotent
        require(state.lastSequence<Long.MAX_VALUE){"Journal sequence exhausted."}
        val seq=state.lastSequence+1
        val payload=Json.bytes(JO(linkedMapOf("kind" to JS("snapshot"),"sequence" to JN(seq.toString()),"projectId" to JS(projectId),"transactionId" to JS(transactionId),"snapshot" to JS(java.util.Base64.getEncoder().encodeToString(CanonicalProjectJson.encode(project))))))
        storage.appendDurably(path,frame(payload))
    }
    fun recoverDetailed(baseDigest:String,baseProject:Project):Recovery? {
        if(!storage.exists(path))return null
        val s=scan(storage.read(path))
        if(s.headerProjectId!=baseProject.id.value||s.baseDigest!=baseDigest||s.schemaVersion!=baseProject.schemaVersion)return null
        val recovered=s.latestProject ?: return null
        require(ModelValidator.validate(recovered).none{issue->issue.severity==com.bimob.app.core.model.ValidationSeverity.ERROR})
        return Recovery(recovered,s.lastSequence,s.corrupt)
    }
    fun recover(baseDigest:String,baseProject:Project):Project?=recoverDetailed(baseDigest,baseProject)?.project
    /**
     * Crash-safe compaction: stage a replacement at a separate path, read it back and
     * validate the complete recovery header before atomically switching the live journal.
     * A failed stage/switch leaves the old journal untouched under AtomicStorage's contract.
     */
    fun compact(projectId:String,newBaseDigest:String,schemaVersion:Int) {
        require(projectId.isNotBlank())
        require(newBaseDigest.matches(Regex("[0-9a-f]{64}")))
        require(schemaVersion>0)
        val stagedPath="$path.compact-stage"
        val candidate=frame(header(projectId,newBaseDigest,schemaVersion))
        try {
            storage.writeAtomically(stagedPath,candidate)
            val staged=storage.read(stagedPath)
            require(staged.contentEquals(candidate)){"Compaction staging readback mismatch."}
            val verified=scan(staged)
            require(!verified.corrupt&&verified.headerProjectId==projectId&&verified.baseDigest==newBaseDigest&&verified.schemaVersion==schemaVersion&&verified.lastSequence==0L&&verified.latestProject==null){"Staged journal failed verification."}
            // The final switch is the only operation touching the current journal.
            storage.writeAtomically(path,staged)
            val switched=scan(storage.read(path))
            require(!switched.corrupt&&switched.headerProjectId==projectId&&switched.baseDigest==newBaseDigest&&switched.schemaVersion==schemaVersion&&switched.lastSequence==0L&&switched.latestProject==null){"Switched journal failed verification."}
        } finally {
            try { storage.delete(stagedPath) } catch (_:Exception) { /* harmless orphan; startup cleanup may remove it */ }
        }
    }
    private data class Scan(val headerProjectId:String?,val baseDigest:String?,val schemaVersion:Int,val lastSequence:Long,val latestProject:Project?,val transactionIds:Set<String>,val corrupt:Boolean)
    private fun scan(bytes:ByteArray):Scan {
        var offset=0;var headerId:String?=null;var base:String?=null;var schema=0;var sequence=0L;var latest:Project?=null;val transactionIds=mutableSetOf<String>();var corrupt=false
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
                    "header"->{require(headerId==null);headerId=record.req("projectId").text();base=record.req("baseDigest").text();require(base!!.matches(Regex("[0-9a-f]{64}")));schema=record.req("schemaVersion").long().toInt();require(record.req("journalVersion").long()==1L)}
                    "snapshot"->{require(headerId!=null);val seq=record.req("sequence").long();require(seq==sequence+1);require(record.req("projectId").text()==headerId);val tx=record.req("transactionId").text();require(tx.isNotBlank()&&tx !in transactionIds);val raw=java.util.Base64.getDecoder().decode(record.req("snapshot").text());val p=CanonicalProjectJson.decode(raw);require(p.id.value==headerId&&p.schemaVersion==schema);transactionIds.add(tx);sequence=seq;latest=p}
                    else->error("Unknown journal record")
                }
            }catch(_:Exception){corrupt=true;break}
        }
        return Scan(headerId,base,schema,sequence,latest,transactionIds.toSet(),corrupt)
    }
    companion object {
        internal fun hexToBytes(hex:String)=hex.chunked(2).map{it.toInt(16).toByte()}.toByteArray()
    }
}
