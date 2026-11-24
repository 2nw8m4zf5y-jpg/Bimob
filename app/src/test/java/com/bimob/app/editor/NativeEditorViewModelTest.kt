package com.bimob.app.editor

import com.bimob.app.core.nativebridge.RenderGeometryBuffer
import org.junit.Assert.*
import org.json.JSONObject
import org.junit.Test
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize

class NativeEditorViewModelTest {
 private data class Model(
  val levels:MutableList<String> = mutableListOf(),
  val walls:MutableList<String> = mutableListOf(),
  val openings:MutableMap<String,Pair<String,String>> = mutableMapOf(),
  val catalogs:MutableList<String> = mutableListOf()
 )
 private class FakeEngine:NativeEditorEngine {
  var next=1L;var destroyed=0;var revision=1L;var gesture:String?=null
  var model=Model();val undo=mutableListOf<Model>();val redo=mutableListOf<Model>();val commands=mutableListOf<String>()
  var importedSnapshot:String?=null;var failWallDispatchWithLinkageError=false
  private fun copy(m:Model)=Model(m.levels.toMutableList(),m.walls.toMutableList(),m.openings.toMutableMap(),m.catalogs.toMutableList())
  private fun id(j:String)=JSONObject(j).optString("id")
  private fun field(j:String,k:String)=JSONObject(j).optString(k)
  override fun createSession()=next++
  override fun destroySession(handle:Long){destroyed++}
  override fun dispatch(handle:Long,json:String):String {
   commands+=json
   if(failWallDispatchWithLinkageError&&json.contains("CreateWall"))throw UnsatisfiedLinkError("simulated missing JNI symbol")
   when {
    json.contains("CreateLevel")->{undo+=copy(model);model.levels+=id(json);redo.clear();revision++}
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
  override fun importSnapshot(handle:Long,json:String):String {
   if(!json.trim().startsWith("{")||!json.trim().endsWith("}")||
      !json.contains("\"schemaVersion\":1")||!json.contains("\"projectId\":")||
      !json.contains("\"levels\":[")||!json.contains("\"kind\":\"wall\"")||
      !json.contains("\"kind\":\"opening\"")||!json.contains("\"kind\":\"catalogInstance\"")||
      json.contains("\"elements\":[}")) return """{"ok":false,"error":"Malformed canonical snapshot"}"""
   model=Model(
    walls=mutableListOf("wall-a","wall-b"),
    openings=mutableMapOf("opening-a" to ("wall-a" to "DOOR")),
    catalogs=mutableListOf("catalog-a")
   )
   importedSnapshot=json;undo.clear();redo.clear();revision++
   return """{"ok":true}"""
  }
  override fun snapshot(handle:Long):String {
   val elements=model.openings.entries.joinToString(","){(id,value)->
    """{"hostWallId":"${value.first}","id":"$id","kind":"opening","openingKind":"${value.second}"}"""
   }
   return """{"elements":[$elements]}"""
  }
  override fun beginGesture(handle:Long,id:String):Boolean{gesture=id;return true}
  override fun commitGesture(handle:Long,id:String):Boolean{val ok=gesture==id;gesture=null;return ok}
  override fun cancelGesture(handle:Long,id:String):Boolean{gesture=null;return true}
 }
 @Test fun endpointResizeStartAndEndAreSingleAtomicNativeCommandAndKeepOppositeEndpoint(){
  val e=FakeEngine();val vm=NativeEditorViewModel(e);vm.resetProject("endpoint-resize","Project");vm.createWall(0.0,0.0,4.0,0.0)
  val wallId=vm.state.value.geometry!!.wallIds.single()
  val beforeStart=e.commands.count{!it.contains("HistoryState")}
  vm.resizeWallEndpoint(wallId,true,-1.0,1.0)
  assertEquals(beforeStart+1,e.commands.count{!it.contains("HistoryState")})
  val startCmd=JSONObject(e.commands.last{!it.contains("HistoryState")})
  assertEquals("RotateWall",startCmd.optString("type"));assertEquals(wallId,startCmd.optString("id"))
  assertEquals(-1.0,startCmd.optDouble("startX"),0.0);assertEquals(1.0,startCmd.optDouble("startY"),0.0)
  assertEquals(4.0,startCmd.optDouble("endX"),0.0);assertEquals(0.0,startCmd.optDouble("endY"),0.0)
  val beforeEnd=e.commands.count{!it.contains("HistoryState")}
  vm.resizeWallEndpoint(wallId,false,5.0,2.0)
  assertEquals(beforeEnd+1,e.commands.count{!it.contains("HistoryState")})
  val endCmd=JSONObject(e.commands.last{!it.contains("HistoryState")})
  assertEquals(0.0,endCmd.optDouble("startX"),0.0);assertEquals(0.0,endCmd.optDouble("startY"),0.0)
  assertEquals(5.0,endCmd.optDouble("endX"),0.0);assertEquals(2.0,endCmd.optDouble("endY"),0.0)
  vm.close()
 }
 @Test fun endpointResizeStartAndEndDispatchAtomicCoordinatesWithHostedOpeningPresent(){
  val e=FakeEngine();val vm=NativeEditorViewModel(e);vm.resetProject("endpoint-resize-test","Project");vm.createWall(0.0,0.0,4.0,0.0);vm.createOpening("DOOR",2.0,0.0)
  val wallId=vm.state.value.geometry!!.wallIds.single()
  val openingId=vm.state.value.geometry!!.openingIds.single()
  assertEquals(wallId,vm.state.value.openingInfo[openingId]!!.hostWallId)
  val count1=e.commands.count{!it.contains("HistoryState")}
  vm.resizeWallEndpoint(wallId,true,-1.0,1.0)
  assertEquals(count1+1,e.commands.count{!it.contains("HistoryState")})
  val start=JSONObject(e.commands.last{!it.contains("HistoryState")})
  assertEquals("RotateWall",start.optString("type"));assertEquals(-1.0,start.optDouble("startX"),0.0);assertEquals(1.0,start.optDouble("startY"),0.0)
  assertEquals(4.0,start.optDouble("endX"),0.0);assertEquals(0.0,start.optDouble("endY"),0.0)
  val count2=e.commands.count{!it.contains("HistoryState")}
  vm.resizeWallEndpoint(wallId,false,5.0,2.0)
  assertEquals(count2+1,e.commands.count{!it.contains("HistoryState")})
  val end=JSONObject(e.commands.last{!it.contains("HistoryState")})
  assertEquals(0.0,end.optDouble("startX"),0.0);assertEquals(0.0,end.optDouble("startY"),0.0)
  assertEquals(5.0,end.optDouble("endX"),0.0);assertEquals(2.0,end.optDouble("endY"),0.0)
  assertEquals(wallId,vm.state.value.openingInfo[openingId]!!.hostWallId)
  vm.close()
 }
 @Test fun catalogPresetsIncludeFurnitureAndSanitaryAssetsWithRealisticDimensions(){
  val ids=CatalogPreset.all.map{it.assetId}
  assertTrue(ids.contains("furniture.bed.double"));assertTrue(ids.contains("furniture.sofa.3seat"))
  assertTrue(ids.contains("furniture.table.basic"));assertTrue(ids.contains("plumbing.toilet.floor"))
  assertTrue(ids.contains("plumbing.sink.basic"));assertTrue(ids.contains("plumbing.bath.standard"))
  CatalogPreset.all.forEach{assertTrue(it.width>0);assertTrue(it.depth>0);assertTrue(it.height>0);assertTrue(it.category.isNotBlank())}
 }
 @Test fun cameraZoomAndPanRemainInViewModelAndAreClamped(){
  val vm=NativeEditorViewModel(FakeEngine())
  vm.setCamera(144f,37f,-22f)
  val cameraAfterConfigurationChange=vm.camera.value
  assertEquals(144f,cameraAfterConfigurationChange.zoom,0f)
  assertEquals(37f,cameraAfterConfigurationChange.panX,0f)
  assertEquals(-22f,cameraAfterConfigurationChange.panY,0f)
  vm.setCamera(999f,Float.NaN,0f)
  assertEquals(cameraAfterConfigurationChange,vm.camera.value)
  vm.setCamera(999f,200000f,-200000f)
  assertEquals(240f,vm.camera.value.zoom,0f)
  assertEquals(100000f,vm.camera.value.panX,0f)
  assertEquals(-100000f,vm.camera.value.panY,0f)
  vm.close()
 }
 @Test fun firstWallInFreshProjectIsCommittedAndExported(){
  val e=FakeEngine();val vm=NativeEditorViewModel(e)
  vm.createWall(0.0,0.0,3.0,0.0)
  assertTrue(vm.state.value.geometry?.wallIds.isNullOrEmpty())
  assertTrue(vm.state.value.error!!.contains("активным уровнем"))
  vm.resetProject("first-wall","New project")
  vm.createWall(Double.NaN,0.0,3.0,0.0)
  assertTrue(vm.state.value.geometry!!.wallIds.isEmpty())
  assertTrue(vm.state.value.error!!.contains("конечными"))
  vm.createWall(0.0,0.0,0.01,0.0)
  assertTrue(vm.state.value.geometry!!.wallIds.isEmpty())
  vm.createWall(0.0,0.0,3.0,0.0)
  val geometry=vm.state.value.geometry!!
  assertEquals(1,geometry.wallIds.size)
  assertEquals("wall",geometry.wallIds.single().substringBefore("-"))
  assertEquals(10,geometry.walls.size)
  assertTrue(geometry.walls.all{it.isFinite()})
  val wallCommand=e.commands.last{it.contains("CreateWall")}
  assertTrue(JSONObject(wallCommand).optString("levelId").isNotBlank())
  vm.close()
 }
 @Test fun wallMoveDispatchesExactlyOneNativeCommandAtCommitBoundary(){
  val e=FakeEngine();val vm=NativeEditorViewModel(e);vm.resetProject("move-atomic","Project");vm.createWall(0.0,0.0,3.0,0.0)
  val before=e.commands.count{!it.contains("HistoryState")}
  vm.moveElement(EditorElementKind.WALL,"wall-test",1.25,-.5)
  assertEquals(before+1,e.commands.count{!it.contains("HistoryState")})
  val command=JSONObject(e.commands.last{!it.contains("HistoryState")})
  assertEquals("MoveWall",command.optString("type"));assertEquals("wall-test",command.optString("id"))
  assertEquals(1.25,command.optDouble("deltaX"),0.0);assertEquals(-.5,command.optDouble("deltaY"),0.0)
  vm.close()
 }
 @Test fun hostedOpeningAndCatalogMoveEachDispatchOneAtomicCommand(){
  val e=FakeEngine();val vm=NativeEditorViewModel(e);vm.resetProject("move-items","Project");vm.createWall(0.0,0.0,4.0,0.0);vm.createOpening("DOOR",2.0,0.0)
  val openingId=vm.state.value.geometry!!.openingIds.single();val beforeOpening=e.commands.count{!it.contains("HistoryState")}
  vm.moveElement(EditorElementKind.OPENING,openingId,.25,.4)
  assertEquals(beforeOpening+1,e.commands.count{!it.contains("HistoryState")})
  val openingCommand=JSONObject(e.commands.last{!it.contains("HistoryState")});assertEquals("UpdateOpening",openingCommand.optString("type"));assertEquals(openingId,openingCommand.optString("id"))
  assertEquals(1.8,openingCommand.optDouble("offsetAlongWallMeters"),1e-6)
  vm.createCatalog(CatalogPreset.TABLE,1.0,1.0,0.0);val catalogId=vm.state.value.geometry!!.catalogInstanceIds.single();val beforeCatalog=e.commands.count{!it.contains("HistoryState")}
  vm.moveElement(EditorElementKind.CATALOG,catalogId,.3,-.2)
  assertEquals(beforeCatalog+1,e.commands.count{!it.contains("HistoryState")})
  val catalogCommand=JSONObject(e.commands.last{!it.contains("HistoryState")});assertEquals("UpdateCatalogInstance",catalogCommand.optString("type"));assertEquals(catalogId,catalogCommand.optString("id"))
  assertEquals(2.3,catalogCommand.optDouble("x"),1e-6);assertEquals(.8,catalogCommand.optDouble("y"),1e-6)
  vm.close()
 }
 @Test fun rotateAndResizeEachIssueOneNativeCommand(){
  val e=FakeEngine();val vm=NativeEditorViewModel(e);vm.resetProject("transform-controls","Project");vm.createWall(0.0,0.0,4.0,0.0)
  val wallId=vm.state.value.geometry!!.wallIds.single();val beforeRotate=e.commands.count{!it.contains("HistoryState")}
  vm.rotateElement(EditorSelection(EditorElementKind.WALL,wallId),Math.PI/4)
  assertEquals(beforeRotate+1,e.commands.count{!it.contains("HistoryState")});assertEquals("RotateWall",JSONObject(e.commands.last{!it.contains("HistoryState")}).optString("type"))
  val beforeResize=e.commands.count{!it.contains("HistoryState")}
  vm.resizeElement(EditorSelection(EditorElementKind.WALL,wallId),1.1)
  assertEquals(beforeResize+1,e.commands.count{!it.contains("HistoryState")});assertEquals("ResizeWall",JSONObject(e.commands.last{!it.contains("HistoryState")}).optString("type"))
  vm.close()
 }
 @Test fun wallCommandJniLinkageFailureIsReportedWithoutEscapingViewModel(){
  val e=FakeEngine();val vm=NativeEditorViewModel(e);vm.resetProject("jni-failure","Project")
  e.failWallDispatchWithLinkageError=true
  vm.createWall(0.0,0.0,3.0,0.0)
  assertTrue(vm.state.value.error!!.contains("simulated missing JNI symbol"))
  assertTrue(vm.state.value.geometry!!.wallIds.isEmpty())
  vm.close()
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
  assertEquals(vm.state.value.geometry!!.wallIds.single(),vm.state.value.openingInfo[id]?.hostWallId)
  val g=vm.beginGesture();assertNotNull(g);vm.commitGesture(g!!)
  assertEquals(1,vm.state.value.geometry!!.openingIds.size)
 }
 @Test fun canonicalSnapshotRestoresAllEntityKindsAndRejectsCorruptJsonAtomically(){
  val e=FakeEngine();val vm=NativeEditorViewModel(e)
  val snapshot="""{"buildings":[],"elements":[{"axis":{"end":{"xMeters":4,"yMeters":0},"start":{"xMeters":0,"yMeters":0}},"heightMeters":3,"id":"wall-a","kind":"wall","levelId":"level-a","thicknessMeters":0.2},{"axis":{"end":{"xMeters":4,"yMeters":3},"start":{"xMeters":4,"yMeters":0}},"heightMeters":3,"id":"wall-b","kind":"wall","levelId":"level-a","thicknessMeters":0.2},{"heightMeters":2.1,"hostWallId":"wall-a","id":"opening-a","kind":"opening","offsetAlongWallMeters":0.5,"openingKind":"DOOR","sillHeightMeters":0,"widthMeters":0.9},{"assetId":"table","category":"furniture","depthBounds":{"max":2,"min":0.1},"heightBounds":{"max":3,"min":0.1},"hostWallId":null,"id":"catalog-a","kind":"catalogInstance","levelId":"level-a","position":{"xMeters":2,"yMeters":1},"rotationRadians":0,"scalingMode":"UNIFORM","scheduleTag":"","targetDepthMeters":0.7,"targetHeightMeters":0.75,"targetWidthMeters":1.2,"widthBounds":{"max":3,"min":0.1}}],"joins":[{"endA":true,"endB":false,"id":"join-a","point":{"xMeters":4,"yMeters":0},"wallA":"wall-a","wallB":"wall-b"}],"levels":[{"elevationMeters":0,"heightMeters":3,"id":"level-a","name":"Ground"}],"materials":[],"name":"Restored","projectId":"project-restored","schemaVersion":1,"site":{"id":"site-a","name":"Site"},"types":[]}"""
  assertTrue(vm.openProjectSnapshot(snapshot))
  assertEquals("project-restored",vm.state.value.projectId)
  assertEquals(listOf("wall-a","wall-b"),vm.state.value.geometry!!.wallIds)
  assertEquals(1,vm.state.value.geometry!!.wallJoinIds.size)
  assertEquals(listOf("opening-a"),vm.state.value.geometry!!.openingIds)
  assertEquals(listOf("catalog-a"),vm.state.value.geometry!!.catalogInstanceIds)
  assertEquals("DOOR",vm.state.value.openingInfo["opening-a"]?.kind)
  val before=vm.exportProjectSnapshot()
  val corrupted="""{"schemaVersion":1,"projectId":"broken","levels":[{"id":"broken-level"}],"joins":[],"elements":[}"""
  assertFalse(vm.openProjectSnapshot(corrupted))
  assertEquals(before,vm.exportProjectSnapshot())
  assertEquals(listOf("wall-a","wall-b"),vm.state.value.geometry!!.wallIds)
  assertEquals(listOf("opening-a"),vm.state.value.geometry!!.openingIds)
  assertEquals(listOf("catalog-a"),vm.state.value.geometry!!.catalogInstanceIds)
  vm.close()
 }


 @Test fun wallDragScreenToWorldToNativeExportCycleStaysFinite(){
  val engine=FakeEngine();val vm=NativeEditorViewModel(engine)
  vm.resetProject("drag-cycle","Drag cycle")
  val pan=Offset.Zero;val canvas=IntSize(400,400);val zoom=72f
  val start=screenToWorld(Offset(100f,100f),canvas,zoom,pan)
  val end=screenToWorld(Offset(316f,100f),canvas,zoom,pan)
  assertTrue(start.x.isFinite()&&start.y.isFinite())
  assertTrue(end.x.isFinite()&&end.y.isFinite())
  vm.createWall(start.x,start.y,end.x,end.y)
  val geometry=vm.state.value.geometry!!
  assertEquals(1,geometry.wallIds.size)
  assertEquals(10,geometry.walls.size)
  assertTrue(geometry.walls.all{it.isFinite()})
  assertTrue(engine.commands.any{JSONObject(it).optString("type")=="CreateWall"})
  val invalid=screenToWorld(Offset(Float.NaN,100f),canvas,zoom,pan)
  assertFalse(invalid.x.isFinite()||invalid.y.isFinite())
  val safe=worldToScreen(WorldPoint(Double.NaN,0.0),Size(400f,400f),zoom,pan)
  assertTrue(safe.x.isFinite()&&safe.y.isFinite())
  vm.close()
 }


 @Test fun completedGestureAlwaysReleasesHistoryAndNextDragCanBegin(){
  val engine=FakeEngine();val vm=NativeEditorViewModel(engine);vm.resetProject("gesture-cycle","Project");vm.createWall(0.0,0.0,3.0,0.0)
  val first=vm.beginGesture();assertNotNull(first);vm.commitGesture(first!!);assertNull(engine.gesture)
  vm.undo();assertTrue(vm.state.value.geometry!!.wallIds.isEmpty());assertTrue(vm.state.value.canRedo)
  vm.redo();assertEquals(1,vm.state.value.geometry!!.wallIds.size)
  val second=vm.beginGesture();assertNotNull(second);vm.cancelGesture(second!!);assertNull(engine.gesture)
  assertNotNull(vm.beginGesture());vm.cancelActiveGesture();assertNull(engine.gesture);vm.close()
 }
 @Test fun typedGeometryIterationUsesStableEntityIdsAndRejectsMismatches(){
  val oneWall=RenderGeometryBuffer(1,1L,listOf("wall-a"),floatArrayOf(0f,0f,2f,0f,2f,.2f,0f,.2f,0f,2.7f),emptyList(),FloatArray(0),emptyList(),FloatArray(0),emptyList(),FloatArray(0))
  assertEquals(listOf("wall-a"),GeometryMapper.map(oneWall,emptyMap()).walls.map{it.id})
  val mismatched=oneWall.copy(wallIds=listOf("wall-a","wall-b"))
  assertThrows(IllegalArgumentException::class.java){GeometryMapper.map(mismatched,emptyMap())}
  val empty=oneWall.copy(wallIds=emptyList(),walls=FloatArray(0))
  assertTrue(GeometryMapper.map(empty,emptyMap()).walls.isEmpty())
 }
 
}