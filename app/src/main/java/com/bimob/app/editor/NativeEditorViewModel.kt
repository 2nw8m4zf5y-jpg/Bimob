package com.bimob.app.editor
import androidx.lifecycle.ViewModel
import com.bimob.app.core.nativebridge.NativeEngineBridge
import com.bimob.app.core.nativebridge.RenderGeometryBuffer
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.hypot
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.lifecycle.viewModelScope
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
data class CanvasCamera(val zoom:Float=72f,val panX:Float=0f,val panY:Float=0f)
data class NativeEditorState(val geometry:RenderGeometryBuffer?=null,val typedGeometry:TypedGeometry?=null,val canUndo:Boolean=false,val canRedo:Boolean=false,
 val error:String?=null,val projectId:String="",val selection:EditorSelection?=null,val openingInfo:Map<String,OpeningRenderInfo> = emptyMap(),val isImporting:Boolean=false,val isExporting:Boolean=false)
class NativeEditorViewModel internal constructor(private val engine:NativeEditorEngine):ViewModel() {
 constructor():this(JniNativeEditorEngine)
 private var handle=engine.createSession();private var levelId=""
 private val snapshotIoMutex=Mutex();private val nativeSessionLock=Any();private var requestGeneration=0L;private var activeGestureId:String?=null
 private val mutableState=MutableStateFlow(NativeEditorState())
 val state:StateFlow<NativeEditorState> = mutableState.asStateFlow()
 private val mutableCamera=MutableStateFlow(CanvasCamera())
 val camera:StateFlow<CanvasCamera> = mutableCamera.asStateFlow()
 fun setCamera(zoom:Float,panX:Float,panY:Float){
  if(!zoom.isFinite()||!panX.isFinite()||!panY.isFinite())return
  mutableCamera.value=CanvasCamera(zoom.coerceIn(12f,240f),panX.coerceIn(-100000f,100000f),panY.coerceIn(-100000f,100000f))
 }
 init{check(handle!=0L){"Native engine could not create a session."}}
 fun resetProject(projectId:String,projectName:String)=synchronized(nativeSessionLock){
  if(mutableState.value.projectId==projectId&&levelId.isNotEmpty())return@synchronized
  if(levelId.isNotEmpty()||mutableState.value.projectId.isNotEmpty()){if(handle!=0L)engine.destroySession(handle);handle=engine.createSession()}
  check(handle!=0L){"Native engine could not create a session."}
  levelId="level-"+UUID.randomUUID().toString()
  val r=engine.dispatch(handle,"""{"type":"CreateLevel","id":"$levelId","name":"Level 1","elevationMeters":0,"heightMeters":3}""")
  if(!isOk(r)){levelId="";publishError(errorFrom(r));return@synchronized};refresh(projectId)
 }
 fun exportProjectSnapshot():String=synchronized(nativeSessionLock){val session=handle;if(session==0L)error("Native session is closed.");engine.snapshot(session)}
 fun exportProjectSnapshotAsync(onResult:(Result<String>)->Unit){if(mutableState.value.isExporting||mutableState.value.isImporting)return;mutableState.value=mutableState.value.copy(isExporting=true,error=null);viewModelScope.launch{snapshotIoMutex.withLock{val gen=requestGeneration;val result=runCatching{withContext(Dispatchers.IO){synchronized(nativeSessionLock){val session=handle;if(session==0L)error("Native session is closed.");engine.snapshot(session)}}};if(gen==requestGeneration)mutableState.value=mutableState.value.copy(isExporting=false);onResult(result)}}}
 fun openProjectSnapshotAsync(json:String,onResult:(Boolean)->Unit){if(mutableState.value.isImporting||mutableState.value.isExporting)return;mutableState.value=mutableState.value.copy(isImporting=true,error=null);viewModelScope.launch{snapshotIoMutex.withLock{val gen=requestGeneration;val ok=withContext(Dispatchers.IO){synchronized(nativeSessionLock){openProjectSnapshot(json)}};if(gen==requestGeneration)mutableState.value=mutableState.value.copy(isImporting=false);onResult(gen==requestGeneration&&ok)}}}
 fun openProjectSnapshot(json:String):Boolean = synchronized(nativeSessionLock) {
  val snapshot=try{JSONObject(json)}catch(e:Exception){publishError(e.message?:"Invalid canonical snapshot JSON.");return@synchronized false}
  val pid=snapshot.optString("projectId").takeIf{it.isNotBlank()}
  val levels=snapshot.optJSONArray("levels")
  val importedLevel=levels?.optJSONObject(0)?.optString("id")?.takeIf{it.isNotBlank()}
  if(pid==null||importedLevel==null){
   publishError("Canonical snapshot is missing projectId or a level.")
   return false
  }
  val r=engine.importSnapshot(handle,json)
  if(!isOk(r)){publishError(errorFrom(r));return@synchronized false}
  levelId=importedLevel
  mutableState.value=mutableState.value.copy(selection=null)
  refresh(pid)
  return mutableState.value.projectId==pid && mutableState.value.geometry!=null
 }
 fun closeAndReset(){requestGeneration++;synchronized(nativeSessionLock){cancelActiveGesture();if(handle!=0L)engine.destroySession(handle);handle=engine.createSession();levelId="";mutableState.value=NativeEditorState()}}
 fun createWall(sx:Double,sy:Double,ex:Double,ey:Double){
  if(levelId.isBlank()){publishError("Сначала создайте проект с активным уровнем.");return}
  if(!listOf(sx,sy,ex,ey).all{it.isFinite()}){publishError("Координаты стены должны быть конечными числами.");return}
  val length=hypot(ex-sx,ey-sy)
  if(!length.isFinite()||length<0.05){publishError("Длина стены должна быть не менее 0,05 м.");return}
  mutate("""{"type":"CreateWall","id":"wall-${UUID.randomUUID()}","levelId":"$levelId","startX":$sx,"startY":$sy,"endX":$ex,"endY":$ey,"heightMeters":2.7,"thicknessMeters":0.2}""")
 }
 fun createOpening(kind:String,x:Double,y:Double):Boolean{
  require(kind=="DOOR"||kind=="WINDOW");val g=mutableState.value.typedGeometry?:return false
  var best=.35;var host:WallItem?=null;var along=0.0;var length=0.0
  g.walls.forEach{wall->val dx=wall.axisEnd.x-wall.axisStart.x;val dy=wall.axisEnd.y-wall.axisStart.y
   val len=hypot(dx,dy);if(len<=1e-6)return@forEach
   val t=(((x-wall.axisStart.x)*dx+(y-wall.axisStart.y)*dy)/(len*len)).coerceIn(0.0,1.0)
   val d=hypot(x-wall.axisStart.x-t*dx,y-wall.axisStart.y-t*dy)
   if(d<best){best=d;host=wall;along=t*len;length=len}
  }
  val wall=host?:return false;val width=if(kind=="DOOR")0.9 else 1.2
  if(length<width+.02)return false
  val offset=(along-width/2).coerceIn(.01,length-width-.01)
  val height=if(kind=="DOOR")2.1 else 1.2;val sill=if(kind=="DOOR")0.0 else .9
  mutate("""{"type":"CreateOpening","id":"opening-${UUID.randomUUID()}","hostWallId":"${wall.id}","openingKind":"$kind","offsetAlongWallMeters":$offset,"widthMeters":$width,"heightMeters":$height,"sillHeightMeters":$sill}""")
  return mutableState.value.error==null
 }
 fun createCatalog(p:CatalogPreset,x:Double,y:Double,rotation:Double){
  mutate("""{"type":"CreateCatalogInstance","id":"catalog-${UUID.randomUUID()}","assetId":"${p.assetId}","category":"${p.category}","levelId":"$levelId","x":$x,"y":$y,"rotationRadians":$rotation,"widthMeters":${p.width},"depthMeters":${p.depth},"heightMeters":${p.height}}""")
 }
 fun selectAt(x:Double,y:Double){
  val g=mutableState.value.typedGeometry?:return
  for(item in g.catalog.asReversed()){
   val dx=x-item.center.x;val dy=y-item.center.y;val a=item.rotationRadians
   val lx=dx*cos(a)+dy*sin(a);val ly=-dx*sin(a)+dy*cos(a)
   if(abs(lx)<=item.width/2&&abs(ly)<=item.depth/2){select(EditorSelection(EditorElementKind.CATALOG,item.id));return}
  }
  for(item in g.openings.asReversed()){
   val wall=g.walls.firstOrNull{it.id==item.hostWallId}?:continue
   val dx=wall.axisEnd.x-wall.axisStart.x;val dy=wall.axisEnd.y-wall.axisStart.y;val len=hypot(dx,dy)
   if(len<=1e-9)continue
   val ux=dx/len;val uy=dy/len
   val ax=item.center.x-ux*item.width/2;val ay=item.center.y-uy*item.width/2
   val bx=item.center.x+ux*item.width/2;val by=item.center.y+uy*item.width/2
   val vx=bx-ax;val vy=by-ay;val l2=vx*vx+vy*vy
   val t=if(l2<=1e-12)0.0 else (((x-ax)*vx+(y-ay)*vy)/l2).coerceIn(0.0,1.0)
   if(hypot(x-ax-t*vx,y-ay-t*vy)<=maxOf(item.width/2,.35)){select(EditorSelection(EditorElementKind.OPENING,item.id));return}
  }
  var chosen:String?=null;var best=.3
  g.walls.forEach{w->val dx=w.axisEnd.x-w.axisStart.x;val dy=w.axisEnd.y-w.axisStart.y;val l2=dx*dx+dy*dy
   if(l2<=1e-12)return@forEach;val t=(((x-w.axisStart.x)*dx+(y-w.axisStart.y)*dy)/l2).coerceIn(0.0,1.0)
   val d=hypot(x-w.axisStart.x-t*dx,y-w.axisStart.y-t*dy);if(d<best){best=d;chosen=w.id}
  }
  select(chosen?.let{EditorSelection(EditorElementKind.WALL,it)})
 }
 fun deleteSelection(){val s=mutableState.value.selection?:return
  val type=when(s.kind){EditorElementKind.WALL->"DeleteWall";EditorElementKind.OPENING->"DeleteOpening";EditorElementKind.CATALOG->"DeleteCatalogInstance"}
  mutate("""{"type":"$type","id":"${s.id}"}""");if(mutableState.value.error==null)select(null)
 }
 fun selectElement(s:EditorSelection?){select(s)}\n private fun select(s:EditorSelection?){mutableState.value=mutableState.value.copy(selection=s)}
 fun moveWall(id:String,dx:Double,dy:Double){if(dx!=0.0||dy!=0.0)mutate("""{"type":"MoveWall","id":"$id","deltaX":$dx,"deltaY":$dy}""")}
 fun moveElement(kind:EditorElementKind,id:String,dx:Double,dy:Double){
  if(!dx.isFinite()||!dy.isFinite()||(dx==0.0&&dy==0.0))return
  val g=mutableState.value.typedGeometry?:return
  when(kind){
   EditorElementKind.WALL->mutate("""{"type":"MoveWall","id":"$id","deltaX":$dx,"deltaY":$dy}""")
   EditorElementKind.OPENING->{val item=g.openings.firstOrNull{it.id==id}?:return;val wall=g.walls.firstOrNull{it.id==item.hostWallId}?:return
    val ax=wall.axisEnd.x-wall.axisStart.x;val ay=wall.axisEnd.y-wall.axisStart.y;val len=hypot(ax,ay);if(len<=1e-9)return
    val delta=(dx*ax+dy*ay)/len;val offset=(item.offsetAlongWall+delta).coerceIn(0.0,(len-item.width).coerceAtLeast(0.0))
    mutate("""{"type":"UpdateOpening","id":"$id","offsetAlongWallMeters":$offset,"widthMeters":${item.width},"heightMeters":${item.height},"sillHeightMeters":${item.sill}}""")
   }
   EditorElementKind.CATALOG->{val item=g.catalog.firstOrNull{it.id==id}?:return
    mutate("""{"type":"UpdateCatalogInstance","id":"$id","x":${item.center.x+dx},"y":${item.center.y+dy},"widthMeters":${item.width},"depthMeters":${item.depth},"heightMeters":${item.height}}""")
   }
  }
 }
 fun beginGesture():String?{if(activeGestureId!=null)return null;val id="gesture-"+UUID.randomUUID().toString();if(handle!=0L&&engine.beginGesture(handle,id)){activeGestureId=id;return id};return null}
 fun commitGesture(id:String){if(activeGestureId!=id)return;try{if(engine.commitGesture(handle,id))refresh(mutableState.value.projectId)else publishError("Native gesture commit failed.")}finally{activeGestureId=null}}
 fun cancelGesture(id:String){if(activeGestureId!=id)return;try{if(engine.cancelGesture(handle,id))refresh(mutableState.value.projectId)else publishError("Native gesture cancellation failed.")}finally{activeGestureId=null}}
 fun cancelActiveGesture(){activeGestureId?.let(::cancelGesture)}
 fun undo()=history("Undo");fun redo()=history("Redo")
 private fun history(t:String){if(handle==0L)return;val r=engine.dispatch(handle,"""{"type":"$t"}""");if(isOk(r))refresh(mutableState.value.projectId)else publishError(errorFrom(r))}
 private fun mutate(c:String){try{if(handle==0L){publishError("Native session is closed.");return};val r=engine.dispatch(handle,c);if(isOk(r))refresh(mutableState.value.projectId)else publishError(errorFrom(r))}catch(e:Exception){publishError(e.message?:"Native command failed safely.")}catch(e:LinkageError){publishError(e.message?:"Native engine linkage failed safely.")}}
 private fun refresh(projectId:String){try{
  val g=engine.export(handle);val snap=engine.snapshot(handle);val h=engine.dispatch(handle,"""{"type":"HistoryState"}""")
  val info=mutableMapOf<String,OpeningRenderInfo>()
  val catalogInfo=mutableMapOf<String,CatalogRenderInfo>()
  val elements=JSONObject(snap).optJSONArray("elements")?:JSONArray()
  for(i in 0 until elements.length()){
   val element=elements.optJSONObject(i)?:continue
   val id=element.optString("id")
   when(element.optString("kind")){
    "opening"->{val host=element.optString("hostWallId");val kind=element.optString("openingKind");if(id.isNotBlank()&&host.isNotBlank()&&kind.isNotBlank())info[id]=OpeningRenderInfo(kind,host)}
    "catalogInstance"->{val host=element.optString("hostWallId");val asset=element.optString("assetId");val category=element.optString("category");if(id.isNotBlank())catalogInfo[id]=CatalogRenderInfo(host,asset,category)}
   }
  }
  val history=JSONObject(h)
  val sel=mutableState.value.selection?.takeIf{when(it.kind){EditorElementKind.WALL->g.wallIds.contains(it.id);EditorElementKind.OPENING->g.openingIds.contains(it.id);EditorElementKind.CATALOG->g.catalogInstanceIds.contains(it.id)}}
  mutableState.value=NativeEditorState(g,GeometryMapper.map(g,info,catalogInfo),history.optBoolean("canUndo"),history.optBoolean("canRedo"),projectId=projectId,selection=sel,openingInfo=info)
 }catch(e:Exception){publishError(e.message?:"BGR1 export failed.")}catch(e:LinkageError){publishError(e.message?:"Native geometry export linkage failed safely.")}}
 private data class Point(val x:Double,val y:Double)
 private fun wallSegment(g:RenderGeometryBuffer,i:Int):Pair<Point,Point>?{val o=i*10;if(o+7>=g.walls.size)return null
  return Point((g.walls[o]+g.walls[o+6])/2.0,(g.walls[o+1]+g.walls[o+7])/2.0) to Point((g.walls[o+2]+g.walls[o+4])/2.0,(g.walls[o+3]+g.walls[o+5])/2.0)}
 private fun isOk(s:String)=runCatching{JSONObject(s).optBoolean("ok")}.getOrDefault(false)
 private fun errorFrom(s:String)=runCatching{JSONObject(s).optString("error").takeIf{it.isNotBlank()}}.getOrNull()?:"Native command rejected."
 private fun publishError(s:String){mutableState.value=mutableState.value.copy(error=s)}
 fun close(){requestGeneration++;synchronized(nativeSessionLock){cancelActiveGesture();if(handle!=0L){engine.destroySession(handle);handle=0L}}}
 override fun onCleared(){close();super.onCleared()}
}
