package com.bimob.app.editor
import com.bimob.app.core.nativebridge.RenderGeometryBuffer
import org.junit.Assert.*
import org.junit.Test

class NativeEditorViewModelTest {
 private data class Model(val walls:MutableList<String> = mutableListOf(),val openings:MutableMap<String,Pair<String,String>> = mutableMapOf(),val catalogs:MutableList<String> = mutableListOf())
 private class FakeEngine:NativeEditorEngine {
  var next=1L;var destroyed=0;var revision=1L;var gesture:String?=null
  var model=Model();val undo=mutableListOf<Model>();val redo=mutableListOf<Model>();val commands=mutableListOf<String>()
  private fun copy(m:Model)=Model(m.walls.toMutableList(),m.openings.toMutableMap(),m.catalogs.toMutableList())
  private fun id(j:String)=Regex(""""id"\s*:\s*"([^"]+)"""").find(j)?.groupValues?.get(1)?:""
  private fun field(j:String,k:String)=Regex(""""$k"\s*:\s*"([^"]+)"""").find(j)?.groupValues?.get(1)?:""
  override fun createSession()=next++
  override fun destroySession(handle:Long){destroyed++}
  override fun dispatch(handle:Long,json:String):String {
   commands+=json
   when {
    json.contains("CreateWall")->{undo+=copy(model);model.walls+=id(json);redo.clear();revision++}
    json.contains("CreateOpening")->{undo+=copy(model);model.openings[id(json)]=field(json,"hostWallId") to field(json,"openingKind");redo.clear();revision++}
    json.contains("CreateCatalogInstance")->{undo+=copy(model);model.catalogs+=id(json);redo.clear();revision++}
    json.contains("DeleteWall")->{undo+=copy(model);val wall=id(json);model.walls.remove(wall);model.openings.entries.removeAll{it.value.first==wall};redo.clear();revision++}
    json.contains("DeleteOpening")->{undo+=copy(model);model.openings.remove(id(json));redo.clear();revision++}
    json.contains("DeleteCatalogInstance")->{undo+=copy(model);model.catalogs.remove(id(json));redo.clear();revision++}
    json.contains("\"Undo\"")&&undo.isNotEmpty()->{redo+=copy(model);model=undo.removeAt(undo.lastIndex);revision++}
    json.contains("\"Redo\"")&&redo.isNotEmpty()->{undo+=copy(model);model=redo.removeAt(redo.lastIndex);revision++}
   }
   return """{"ok":true,"canUndo":${undo.isNotEmpty()},"canRedo":${redo.isNotEmpty()}}"""
  }
  override fun export(handle:Long):RenderGeometryBuffer {
   val w=FloatArray(model.walls.size*10)
   model.walls.indices.forEach{i->val o=i*10;w[o]=0f;w[o+1]=.1f;w[o+2]=4f;w[o+3]=.1f;w[o+4]=4f;w[o+5]=-.1f;w[o+6]=0f;w[o+7]=-.1f;w[o+9]=2.7f}
   val op=FloatArray(model.openings.size*8)
   model.openings.keys.forEachIndexed{i,_->val o=i*8;op[o]=2f;op[o+1]=0f;op[o+2]=1.55f;op[o+3]=.9f;op[o+4]=2.1f;op[o+7]=4f}
   val cat=FloatArray(model.catalogs.size*8)
   model.catalogs.indices.forEach{i->val o=i*8;cat[o]=2f;cat[o+1]=1f;cat[o+3]=1.2f;cat[o+4]=.7f;cat[o+5]=.75f}
   return RenderGeometryBuffer(1,revision,model.walls.toList(),w,if(model.walls.size>=2)listOf("join-1")else emptyList(),FloatArray(if(model.walls.size>=2)9 else 0),
    model.openings.keys.toList(),op,model.catalogs.toList(),cat)
  }
  override fun importSnapshot(handle:Long,json:String):String { importedSnapshot=json; return """{"ok":true}""" }
  var importedSnapshot:String?=null
  override fun snapshot(handle:Long):String=importedSnapshot ?: model.openings.entries.joinToString(prefix="""{"elements":[""",postfix="]}"){(key,v)->"""{"hostWallId":"${v.first}","id":"$key","kind":"opening","openingKind":"${v.second}"}"""}
  override fun beginGesture(handle:Long,id:String):Boolean{gesture=id;return true}
  override fun commitGesture(handle:Long,id:String):Boolean{val ok=gesture==id;gesture=null;return ok}
  override fun cancelGesture(handle:Long,id:String):Boolean{gesture=null;return true}
 }
 @Test fun createsWallsAndHistoryFromBgr1(){
  val e=FakeEngine();val vm=NativeEditorViewModel(e);vm.resetProject("p1","Project")
  assertTrue(e.commands.first().contains("CreateLevel"));vm.createWall(0.0,0.0,3.0,0.0);vm.createWall(3.0,0.0,3.0,2.0)
  assertEquals(2,vm.state.value.geometry?.wallIds?.size);assertEquals(1,vm.state.value.geometry?.wallJoinIds?.size)
  vm.undo();assertEquals(1,vm.state.value.geometry?.wallIds?.size);assertTrue(vm.state.value.canRedo)
  vm.redo();assertEquals(2,vm.state.value.geometry?.wallIds?.size);vm.close();assertEquals(1,e.destroyed)
 }
 @Test fun createsSelectsAndCascadeDeletesThenUndoRestoresElements(){
  val e=FakeEngine();val vm=NativeEditorViewModel(e);vm.resetProject("p2","Project")
  vm.createWall(0.0,0.0,4.0,0.0);val wall=vm.state.value.geometry!!.wallIds.single()
  assertTrue(vm.createOpening("DOOR",2.0,0.0));vm.createCatalog(CatalogPreset.TABLE,2.0,1.0,0.0)
  assertEquals(1,vm.state.value.geometry?.openingIds?.size);assertEquals(1,vm.state.value.geometry?.catalogInstanceIds?.size)
  vm.selectAt(2.0,1.0);assertEquals(EditorElementKind.CATALOG,vm.state.value.selection?.kind);vm.deleteSelection()
  assertTrue(vm.state.value.geometry!!.catalogInstanceIds.isEmpty())
  vm.selectAt(2.0,0.0);assertEquals(EditorElementKind.OPENING,vm.state.value.selection?.kind)
  vm.selectAt(.2,0.0);assertEquals(EditorElementKind.WALL,vm.state.value.selection?.kind);vm.deleteSelection()
  assertTrue(vm.state.value.geometry!!.wallIds.isEmpty());assertTrue(vm.state.value.geometry!!.openingIds.isEmpty())
  vm.undo();assertEquals(listOf(wall),vm.state.value.geometry!!.wallIds);assertEquals(1,vm.state.value.geometry!!.openingIds.size)
 }
 @Test fun gestureLifecycleAndOpeningMetadataAreDecoded(){
  val e=FakeEngine();val vm=NativeEditorViewModel(e);vm.resetProject("p3","Project");vm.createWall(0.0,0.0,4.0,0.0)
  assertTrue(vm.createOpening("WINDOW",2.0,0.0));val id=vm.state.value.geometry!!.openingIds.single()
  assertEquals("WINDOW",vm.state.value.openingInfo[id]?.kind)
  val g=vm.beginGesture();assertNotNull(g);vm.commitGesture(g!!)
  assertEquals(1,vm.state.value.geometry!!.openingIds.size)
 }
}
