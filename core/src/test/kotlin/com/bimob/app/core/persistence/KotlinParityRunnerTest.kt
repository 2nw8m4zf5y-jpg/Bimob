package com.bimob.app.core.persistence
import com.bimob.app.core.model.*
import com.bimob.app.core.dependency.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
class KotlinParityRunner {
 private fun root()=sequenceOf(File("."),File("..")).first{File(it,"shared-tests/golden-scenarios").isDirectory}
 private fun fixture(n:String)=Json.parse(File(root(),"shared-tests/golden-scenarios/$n.json").readBytes()).obj()
 private fun id(v:J)=ModelId(v.text())
 private fun depNode(name:String)=EntityProperty(ModelId(name),PropertyAspect.PARAMETERS)
 private fun initialGraph(f:JO):DependencyGraph {
  val deps=f.req("initialState").obj().req("dependencies").array().a.map{val pair=it.array().a;DependencyEdge(depNode(pair[0].text()),depNode(pair[1].text()))}
  return when(val admitted=DependencyGraph.empty().admitEdges(deps)){is EdgeAdmission.Accepted->admitted.graph;is EdgeAdmission.Rejected->error("Invalid initial dependency fixture: ${admitted.diagnostic}")}
 }
 private fun point(v:J):Point2 {val a=v.array().a;return Point2(a[0].double(),a[1].double())}
 private fun initial(f:JO):Project {
  val s=f.req("initialState").obj();val p=s.req("project").obj()
  val lv=s.req("levels").array().a.map{val x=it.obj();Level(id(x.req("id")),x.req("name").text(),x.req("elevation").double(),x.opt("height").dOrNull())}
  val ws=s.req("walls").array().a.map{val x=it.obj();WallElement(id(x.req("id")),levelId=x.opt("levelId").strOrNull()?.let(::ModelId),axis=WallAxis(point(x.req("start")),point(x.req("end"))),heightMeters=x.req("height").double(),thicknessMeters=x.req("thickness").double())}
  val os=s.req("openings").array().a.map{val x=it.obj();OpeningElement(Opening(id(x.req("id")),id(x.req("hostWallId")),OpeningKind.valueOf(x.req("kind").text().uppercase()),x.req("offset").double(),x.req("width").double(),x.req("height").double(),x.req("sill").double()))}
  val cs=s.req("catalogInstances").array().a.map{val x=it.obj();val w=x.req("width").double();val d=x.req("depth").double();val h=x.req("height").double();CatalogInstanceElement(CatalogInstance(id(x.req("id")),x.req("assetId").text(),point(x.req("position")),x.req("rotation").double(),id(x.req("levelId")),id(x.req("hostWallId")),w,d,h,if(x.req("scalingMode").text()=="freeStretch") ScalingMode.FREE_STRETCH else ScalingMode.valueOf(x.req("scalingMode").text().uppercase()),CatalogInstanceBounds(DimensionBounds(.001,1000.0),DimensionBounds(.001,1000.0),DimensionBounds(.001,1000.0)),x.opt("category").strOrNull()?: "fixture",x.opt("scheduleTag").strOrNull()?: ""))}
  val pid=id(p.req("id"));return Project(pid,p.req("name").text(),site=Site(ModelId(pid.value+"-site"),"Fixture Site"),levels=lv,elements=ws+os+cs)
 }
 private fun wall(x:JO):ModelMutation=CreateWallMutation(Wall(id(x.req("id")),WallAxis(point(x.req("start")),point(x.req("end"))),x.req("height").double(),x.req("thickness").double(),x.opt("levelId").strOrNull()?.let(::ModelId)?:ModelId("unassigned")))
 private fun mutation(x:JO):ModelMutation=when(x.req("op").text()){
  "moveWall"->{val d=x.req("delta").array().a;MoveWallCommand(id(x.req("id")),d[0].double(),d[1].double())}
  "resizeWall"->ResizeWallMutation(id(x.req("id")),x.req("length").double())
  "createLevel"->{val l=x.req("level").obj();CreateLevelMutation(Level(id(l.req("id")),l.req("name").text(),l.req("elevation").double(),l.opt("height").dOrNull()))}
  "createWall"->wall(x.req("wall").obj())
  "deleteWall"->DeleteWallCommand(id(x.req("id")))
  "batch"->BatchCommand("fixture batch",x.req("commands").array().a.map{mutation(it.obj())})
  else->error("Unsupported operation: "+x.req("op").text())
 }
 fun runScenario(name:String) {
  val f=fixture(name);val baselineGraph=initialGraph(f);val bus=CommandBus(initial(f),initialDependencyGraph=baselineGraph);val states=mutableListOf<Project>();var batches=0;var lastRejected:CommandResult.Rejected?=null
  f.req("commands").array().a.forEach{c->val x=c.obj();val op=x.req("op").text();if(op=="batch")batches++;val r=when(op){"undo"->bus.undo();"redo"->bus.redo();"admitDependency"->bus.execute(object:ModelMutation{override val label="Fixture dependency admission";override fun apply(before:Project)=before;override fun stage(before:Project,context:TransactionContext):Project{context.admitDependency(DependencyEdge(depNode(x.req("source").text()),depNode(x.req("dependent").text())));return before}});"assertRejected"->{val rejected=lastRejected?:error("assertRejected without prior rejection");assertEquals("DEPENDENCY_CYCLE",x.req("expectedCode").text());assertTrue(rejected.message.contains("CYCLE_DETECTED"));assertEquals(baselineGraph,bus.snapshot().dependencyGraph);assertEquals(0L,bus.snapshot().number);assertEquals(0,bus.undoDepth());assertEquals(0,bus.redoDepth());CommandResult.Committed(bus.snapshot(),emptyList())};else->bus.execute(mutation(x))};if(name=="cycle_rollback"&&op=="admitDependency"){assertTrue("Expected cycle rejection: $r",r is CommandResult.Rejected);assertEquals(CommandFailureCode.DEPENDENCY_REJECTED,(r as CommandResult.Rejected).code);assertEquals(baselineGraph,bus.snapshot().dependencyGraph);assertEquals(0L,bus.snapshot().number);assertEquals(0,bus.undoDepth());lastRejected=r}else{assertTrue("Rejected $op in $name: $r",r is CommandResult.Committed)};states+=bus.snapshot().project}
  val final=bus.snapshot().project;val a=f.req("assertions").obj()
  when(name){
   "crud_batch"->{assertEquals(a.req("batchCommitCount").long().toInt(),batches);assertEquals(a.req("finalLevelIds").array().a.map{it.text()}.sorted(),final.levels.map{it.id.value}.sorted());assertEquals(a.req("finalWallIds").array().a.map{it.text()}.sorted(),states[4].walls.map{it.id.value}.sorted());assertEquals(listOf("wall-1"),final.walls.map{it.id.value}.sorted());assertEquals(5.0,final.walls.single{it.id.value=="wall-1"}.axis.lengthMeters,1e-6)
    val rollback=CommandBus(initial(f));val rev=rollback.snapshot();val fail=rollback.execute(BatchCommand("atomic reject",listOf(CreateLevelMutation(Level(ModelId("level-1"),"duplicate",0.0)),wall(f.req("initialState").obj().req("walls").array().a.first().obj()))));assertTrue(fail is CommandResult.Rejected);assertEquals(rev,rollback.snapshot());assertEquals(0,rollback.undoDepth())
   }
   "cascade_delete"->{val ad=a.req("afterDelete").obj();assertEquals(ad.req("walls").long().toInt(),states[0].walls.size);assertEquals(ad.req("openings").long().toInt(),states[0].openings.size);assertEquals(ad.req("catalogInstances").long().toInt(),states[0].catalogInstances.size);val au=a.req("afterUndo").obj();assertEquals(au.req("walls").long().toInt(),states[1].walls.size);assertEquals(au.req("openings").long().toInt(),states[1].openings.size);assertEquals(au.req("catalogInstances").long().toInt(),states[1].catalogInstances.size);val ar=a.req("afterRedo").obj();assertEquals(ar.req("walls").long().toInt(),states[2].walls.size);assertEquals(ar.req("openings").long().toInt(),states[2].openings.size);assertEquals(ar.req("catalogInstances").long().toInt(),states[2].catalogInstances.size)}
   "cycle_rollback"->{assertTrue(a.req("revisionUnchanged").bool());assertTrue(a.req("modelUnchanged").bool());assertTrue(a.req("graphUnchanged").bool());assertTrue(a.req("undoDepthUnchanged").bool());assertTrue(a.req("redoDepthUnchanged").bool());assertEquals(2,bus.snapshot().dependencyGraph.edgeCount);assertEquals(3,bus.snapshot().dependencyGraph.nodeCount);assertTrue(lastRejected!!.message.contains("entity:A:property:PARAMETERS"));assertTrue(lastRejected!!.message.contains("entity:B:property:PARAMETERS"));assertTrue(lastRejected!!.message.contains("entity:C:property:PARAMETERS"))}\n   else->error("Unsupported scenario in parity runner: $name")
  }
  val out=File(root(),"core/build/parity-out/kotlin/$name.json");out.parentFile.mkdirs();out.writeBytes(CanonicalProjectJson.encode(final))
 }
}
class KotlinParityRunnerTest {
 @Test fun crudBatchFixture(){KotlinParityRunner().runScenario("crud_batch")}
 @Test fun cascadeDeleteFixture(){KotlinParityRunner().runScenario("cascade_delete")}
}
