package com.bimob.app.editor
import androidx.lifecycle.ViewModel
import com.bimob.app.core.nativebridge.NativeEngineBridge
import com.bimob.app.core.nativebridge.RenderGeometryBuffer
import java.util.UUID
import kotlin.math.hypot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
internal interface NativeEditorEngine {
 fun createSession():Long;fun destroySession(handle:Long);fun dispatch(handle:Long,json:String):String
 fun export(handle:Long):RenderGeometryBuffer;fun importSnapshot(handle:Long,json:String):String
 fun snapshot(handle:Long):String
 fun beginGesture(handle:Long,id:String):Boolean;fun commitGesture(handle:Long,id:String):Boolean;fun cancelGesture(handle:Long,id:String):Boolean
}
private object JniNativeEditorEngine:NativeEditorEngine {
 override fun createSession()=NativeEngineBridge.createSession()
 override fun destroySession(handle:Long)=NativeEngineBridge.destroySession(handle)
 override fun dispatch(handle:Long,json:String)=NativeEngineBridge.dispatchCommandJson(handle,json)
 override fun export(handle:Long)=NativeEngineBridge.exportRenderGeometry(handle)
 override fun snapshot(handle:Long)=NativeEngineBridge.exportCanonicalSnapshot(handle)
 override fun importSnapshot(handle:Long,json:String)=NativeEngineBridge.importCanonicalSnapshot(handle,json)
 override fun beginGesture(handle:Long,id:String)=NativeEngineBridge.beginGesture(handle,id)
 override fun commitGesture(handle:Long,id:String)=NativeEngineBridge.commitGesture(handle,id)
 override fun cancelGesture(handle:Long,id:String)=NativeEngineBridge.cancelGesture(handle,id)
}
enum class EditorElementKind { WALL,OPENING,CATALOG }
data class EditorSelection(val kind:EditorElementKind,val id:String)
data class OpeningRenderInfo(val kind:String,val hostWallId:String)
data class CatalogPreset(val assetId:String,val label:String,val category:String,val width:Double,val depth:Double,val height:Double) {
 companion object {
  val TABLE=CatalogPreset("furniture.table.basic","Стол","furniture",1.2,0.7,0.75)
  val BED=CatalogPreset("furniture.bed.single","Кровать","furniture",2.0,0.9,0.55)
  val COLUMN=CatalogPreset("plumbing.column","Сантехника","plumbing",0.6,0.6,0.9)
  val all=listOf(TABLE,BED,COLUMN)
 }
}
data class NativeEditorState(val geometry:RenderGeometryBuffer?=null,val canUndo:Boolean=false,val canRedo:Boolean=false,
 val error:String?=null,val projectId:String="",val selection:EditorSelection?=null,val openingInfo:Map<String,OpeningRenderInfo> = emptyMap())
class NativeEditorViewModel internal constructor(private val engine:NativeEditorEngine):ViewModel() {
 constructor():this(JniNativeEditorEngine)
 private var handle=engine.createSession();private var levelId=""
 private val mutableState=MutableStateFlow(NativeEditorState())
 val state:StateFlow<NativeEditorState> = mutableState.asStateFlow()
 init{check(handle!=0L){"Native engine could not create a session."}}
 fun resetProject(projectId:String,projectName:String){
  if(mutableState.value.projectId==projectId&&levelId.isNotEmpty())return
  if(levelId.isNotEmpty()||mutableState.value.projectId.isNotEmpty()){if(handle!=0L)engine.destroySession(handle);handle=engine.createSession()}
  check(handle!=0L){"Native engine could not create a session."}
  levelId="level-"+UUID.randomUUID().toString()
  val r=engine.dispatch(handle,"""{"type":"CreateLevel","id":"$levelId","name":"Level 1","elevationMeters":0,"heightMeters":3}""")
  if(!isOk(r)){publishError(errorFrom(r));return};refresh(projectId)
 }
 fun exportProjectSnapshot():String=engine.snapshot(handle)
 fun openProjectSnapshot(json:String):Boolean {
  val pid=Regex(""""projectId"\s*:\s*"([^"]+)"""").find(json)?.groupValues?.get(1)
  val importedLevel=Regex(""""levels"\s*:\s*\[\s*\{[^}]*"id"\s*:\s*"([^"]+)"""").find(json)?.groupValues?.get(1)
  if(pid.isNullOrBlank()||importedLevel.isNullOrBlank()){
   publishError("Canonical snapshot is missing projectId or a level.")
   return false
  }
  val r=engine.importSnapshot(handle,json)
  if(!isOk(r)){publishError(errorFrom(r));return false}
  levelId=importedLevel
  mutableState.value=mutableState.value.copy(selection=null)
  refresh(pid)
  return mutableState.value.projectId==pid && mutableState.value.geometry!=null
 }
 fun closeAndReset(){if(handle!=0L)engine.destroySession(handle);handle=engine.createSession();levelId="";mutableState.value=NativeEditorState()}
 fun createWall(sx:Double,sy:Double,ex:Double,ey:Double){
  if(hypot(ex-sx,ey-sy)<0.05)return
  mutate("""{"type":"CreateWall","id":"wall-${UUID.randomUUID()}","levelId":"$levelId","startX":$sx,"startY":$sy,"endX":$ex,"endY":$ey,"heightMeters":2.7,"thicknessMeters":0.2}""")
 }
 fun createOpening(kind:String,x:Double,y:Double):Boolean{
  require(kind=="DOOR"||kind=="WINDOW");val g=mutableState.value.geometry?:return false
  var best=.35;var host:String?=null;var along=0.0;var length=0.0
  g.wallIds.indices.forEach{i->val s=wallSegment(g,i)?:return@forEach;val dx=s.second.x-s.first.x;val dy=s.second.y-s.first.y
   val len=hypot(dx,dy);if(len<=1e-6)return@forEach
   val t=(((x-s.first.x)*dx+(y-s.first.y)*dy)/(len*len)).coerceIn(0.0,1.0)
   val d=hypot(x-s.first.x-t*dx,y-s.first.y-t*dy)
   if(d<best){best=d;host=g.wallIds[i];along=t*len;length=len}
  }
  val wall=host?:return false;val width=if(kind=="DOOR")0.9 else 1.2
  if(length<width+.02)return false
  val offset=(along-width/2).coerceIn(.01,length-width-.01)
  val height=if(kind=="DOOR")2.1 else 1.2;val sill=if(kind=="DOOR")0.0 else .9
  mutate("""{"type":"CreateOpening","id":"opening-${UUID.randomUUID()}","hostWallId":"$wall","openingKind":"$kind","offsetAlongWallMeters":$offset,"widthMeters":$width,"heightMeters":$height,"sillHeightMeters":$sill}""")
  return mutableState.value.error==null
 }
 fun createCatalog(p:CatalogPreset,x:Double,y:Double,rotation:Double){
  mutate("""{"type":"CreateCatalogInstance","id":"catalog-${UUID.randomUUID()}","assetId":"${p.assetId}","category":"${p.category}","levelId":"$levelId","x":$x,"y":$y,"rotationRadians":$rotation,"widthMeters":${p.width},"depthMeters":${p.depth},"heightMeters":${p.height}}""")
 }
 fun selectAt(x:Double,y:Double){
  val g=mutableState.value.geometry?:return
  for(i in g.catalogInstanceIds.indices.reversed()){val o=i*8;val dx=x-g.catalogInstances[o];val dy=y-g.catalogInstances[o+1];val a=g.catalogInstances[o+2].toDouble()
   val lx=dx*kotlin.math.cos(a)+dy*kotlin.math.sin(a);val ly=-dx*kotlin.math.sin(a)+dy*kotlin.math.cos(a)
   if(kotlin.math.abs(lx)<=g.catalogInstances[o+3].toDouble()/2&&kotlin.math.abs(ly)<=g.catalogInstances[o+4].toDouble()/2){select(EditorSelection(EditorElementKind.CATALOG,g.catalogInstanceIds[i]));return}
  }
  for(i in g.openingIds.indices.reversed()){val o=i*8;val info=mutableState.value.openingInfo[g.openingIds[i]]?:continue
   if(g.wallIds.contains(info.hostWallId)&&hypot(x-g.openings[o],y-g.openings[o+1])<=maxOf(g.openings[o+3].toDouble()/2,.35)){select(EditorSelection(EditorElementKind.OPENING,g.openingIds[i]));return}
  }
  var chosen:String?=null;var best=.3
  g.wallIds.indices.forEach{i->val s=wallSegment(g,i)?:return@forEach;val dx=s.second.x-s.first.x;val dy=s.second.y-s.first.y;val l2=dx*dx+dy*dy
   if(l2<=1e-12)return@forEach;val t=(((x-s.first.x)*dx+(y-s.first.y)*dy)/l2).coerceIn(0.0,1.0)
   val d=hypot(x-s.first.x-t*dx,y-s.first.y-t*dy);if(d<best){best=d;chosen=g.wallIds[i]}
  }
  select(chosen?.let{EditorSelection(EditorElementKind.WALL,it)})
 }
 fun deleteSelection(){val s=mutableState.value.selection?:return
  val type=when(s.kind){EditorElementKind.WALL->"DeleteWall";EditorElementKind.OPENING->"DeleteOpening";EditorElementKind.CATALOG->"DeleteCatalogInstance"}
  mutate("""{"type":"$type","id":"${s.id}"}""");if(mutableState.value.error==null)select(null)
 }
 private fun select(s:EditorSelection?){mutableState.value=mutableState.value.copy(selection=s)}
 fun moveWall(id:String,dx:Double,dy:Double){if(dx!=0.0||dy!=0.0)mutate("""{"type":"MoveWall","id":"$id","deltaX":$dx,"deltaY":$dy}""")}
 fun beginGesture():String?{val id="gesture-"+UUID.randomUUID().toString();return id.takeIf{handle!=0L&&engine.beginGesture(handle,it)}}
 fun commitGesture(id:String){if(engine.commitGesture(handle,id))refresh(mutableState.value.projectId)else publishError("Native gesture commit failed.")}
 fun cancelGesture(id:String){if(engine.cancelGesture(handle,id))refresh(mutableState.value.projectId)else publishError("Native gesture cancellation failed.")}
 fun undo()=history("Undo");fun redo()=history("Redo")
 private fun history(t:String){if(handle==0L)return;val r=engine.dispatch(handle,"""{"type":"$t"}""");if(isOk(r))refresh(mutableState.value.projectId)else publishError(errorFrom(r))}
 private fun mutate(c:String){val r=engine.dispatch(handle,c);if(isOk(r))refresh(mutableState.value.projectId)else publishError(errorFrom(r))}
 private fun refresh(projectId:String){try{
  val g=engine.export(handle);val snap=engine.snapshot(handle);val h=engine.dispatch(handle,"""{"type":"HistoryState"}""")
  val info=mutableMapOf<String,OpeningRenderInfo>()
  val pattern=Regex("""\{[^{}]*"hostWallId"\s*:\s*"([^"]+)"[^{}]*"id"\s*:\s*"([^"]+)"[^{}]*"openingKind"\s*:\s*"([^"]+)"[^{}]*\}""")
  pattern.findAll(snap).forEach{info[it.groupValues[2]]=OpeningRenderInfo(it.groupValues[3],it.groupValues[1])}
  val sel=mutableState.value.selection?.takeIf{when(it.kind){EditorElementKind.WALL->g.wallIds.contains(it.id);EditorElementKind.OPENING->g.openingIds.contains(it.id);EditorElementKind.CATALOG->g.catalogInstanceIds.contains(it.id)}}
  mutableState.value=NativeEditorState(g,Regex(""""canUndo"\s*:\s*true""").containsMatchIn(h),Regex(""""canRedo"\s*:\s*true""").containsMatchIn(h),projectId=projectId,selection=sel,openingInfo=info)
 }catch(e:Exception){publishError(e.message?:"BGR1 export failed.")}}
 private data class Point(val x:Double,val y:Double)
 private fun wallSegment(g:RenderGeometryBuffer,i:Int):Pair<Point,Point>?{val o=i*10;if(o+7>=g.walls.size)return null
  return Point((g.walls[o]+g.walls[o+6])/2.0,(g.walls[o+1]+g.walls[o+7])/2.0) to Point((g.walls[o+2]+g.walls[o+4])/2.0,(g.walls[o+3]+g.walls[o+5])/2.0)}
 private fun isOk(s:String)=Regex(""""ok"\s*:\s*true""").containsMatchIn(s)
 private fun errorFrom(s:String)=Regex(""""error"\s*:\s*"([^"]*)"""").find(s)?.groupValues?.getOrNull(1)?:"Native command rejected."
 private fun publishError(s:String){mutableState.value=mutableState.value.copy(error=s)}
 fun close(){if(handle!=0L){engine.destroySession(handle);handle=0L}}
 override fun onCleared(){close();super.onCleared()}
}
