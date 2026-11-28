package com.bimob.app.core.persistence

import com.bimob.app.core.model.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

internal sealed interface J
internal data class JO(val m: LinkedHashMap<String,J>):J
internal data class JA(val a:List<J>):J
internal data class JS(val s:String):J
internal data class JN(val n:String):J
internal data class JB(val b:Boolean):J
internal data object JNull:J

internal object Json {
    fun bytes(v:J)=render(v).toByteArray(StandardCharsets.UTF_8)
    fun render(v:J):String=when(v){
        is JO->v.m.entries.sortedBy{it.key}.joinToString(",","{","}"){quote(it.key)+":"+render(it.value)}
        is JA->v.a.joinToString(",","[","]"){render(it)}
        is JS->quote(v.s); is JN->canon(v.n); is JB->if(v.b)"true" else "false"; JNull->"null"
    }
    fun quote(s:String)=buildString {
        append('"')
        s.forEach { c -> when(c){
            '"'->append("\\\""); '\\'->append("\\\\"); '\b'->append("\\b"); '\u000c'->append("\\f")
            '\n'->append("\\n"); '\r'->append("\\r"); '\t'->append("\\t")
            else->if(c.code<32) append("\\u%04x".format(c.code)) else append(c)
        } }
        append('"')
    }
    /** Canonical floating-point rule: finite values rounded HALF_UP to 6 decimals, plain notation, no trailing zeros, -0 normalized. */
    fun d(v:Double):JN {
        require(v.isFinite()){"Non-finite numbers are not canonical JSON values."}
        val rounded=BigDecimal.valueOf(v).setScale(6,RoundingMode.HALF_UP).stripTrailingZeros()
        return JN(if(rounded.compareTo(BigDecimal.ZERO)==0)"0" else rounded.toPlainString())
    }
    fun l(v:Long)=JN(v.toString())
    private fun canon(s:String):String { val n=BigDecimal(s); return if(n.compareTo(BigDecimal.ZERO)==0)"0" else n.stripTrailingZeros().toPlainString() }
    fun parse(bytes:ByteArray):J {
        require(bytes.size<=64*1024*1024){"JSON size limit exceeded"}
        val text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        return P(text).read()
    }
    private class P(val s:String) {
        var i=0
        fun read():J { val v=value(0); ws(); require(i==s.length){"Trailing JSON bytes"}; return v }
        fun value(depth:Int):J { require(depth<=64); ws(); require(i<s.length); return when(s[i]){
            '{'->obj(depth+1); '['->array(depth+1); '"'->JS(string()); 't'->{lit("true");JB(true)}
            'f'->{lit("false");JB(false)}; 'n'->{lit("null");JNull}; '-',in '0'..'9'->num(); else->error("Invalid JSON") } }
        fun obj(d:Int):JO { i++;ws();val m=linkedMapOf<String,J>();if(take('}'))return JO(m);while(true){ws();require(i<s.length&&s[i]=='"');val k=string();require(k !in m){"Duplicate key"};ws();require(take(':'));m[k]=value(d);ws();if(take('}'))break;require(take(','))};return JO(m) }
        fun array(d:Int):JA { i++;ws();val a=mutableListOf<J>();if(take(']'))return JA(a);while(true){a+=value(d);ws();if(take(']'))break;require(take(','))};return JA(a) }
        fun string():String { require(take('"'));val b=StringBuilder();while(i<s.length){val c=s[i++];if(c=='"')return b.toString();if(c=='\\'){require(i<s.length);when(val e=s[i++]){'"'->b.append('"');'\\'->b.append('\\');'/'->b.append('/');'b'->b.append('\b');'f'->b.append('\u000c');'n'->b.append('\n');'r'->b.append('\r');'t'->b.append('\t');'u'->{require(i+4<=s.length);b.append(s.substring(i,i+4).toInt(16).toChar());i+=4};else->error("Bad escape")}}else{require(c.code>=32);b.append(c)}};error("Unclosed string") }
        fun num():JN { val st=i;if(take('-')){};if(take('0')){require(i==s.length||s[i] !in '0'..'9')}else{require(i<s.length&&s[i] in '1'..'9');while(i<s.length&&s[i] in '0'..'9'){i++}};if(take('.')){require(i<s.length&&s[i] in '0'..'9');while(i<s.length&&s[i] in '0'..'9'){i++}};if(i<s.length&&(s[i]=='e'||s[i]=='E')){i++;if(i<s.length&&(s[i]=='+'||s[i]=='-'))i++;require(i<s.length&&s[i] in '0'..'9');while(i<s.length&&s[i] in '0'..'9'){i++}};return JN(s.substring(st,i)) }
        fun lit(x:String){require(s.startsWith(x,i));i+=x.length}
        fun ws(){while(i<s.length&&s[i] in " \n\r\t")i++}
        fun take(c:Char)=if(i<s.length&&s[i]==c){i++;true}else false
    }
}
internal fun jo(vararg p:Pair<String,J>)=JO(linkedMapOf(*p))
internal fun js(s:String)=JS(s)
internal fun nullable(s:String?)=s?.let(::JS)?:JNull
internal fun J.obj()=this as? JO?:error("Object expected")
internal fun J.array()=this as? JA?:error("Array expected")
internal fun J.text()=(this as? JS)?.s?:error("String expected")
internal fun J.long()=(this as? JN)?.n?.toLongOrNull()?:error("Integer expected")
internal fun J.double()=(this as? JN)?.n?.toDoubleOrNull()?.also{require(it.isFinite())}?:error("Finite number expected")
internal fun J.bool()=(this as? JB)?.b?:error("Boolean expected")
internal fun JO.req(k:String)=m[k]?:error("Missing field")
internal fun JO.opt(k:String)=m[k]?:JNull
internal fun J.strOrNull()=if(this==JNull)null else text()
internal fun J.dOrNull()=if(this==JNull)null else double()

/** Compact UTF-8 JSON. Object keys are ASCII-lexicographically sorted; entity arrays sort by ID. */
object CanonicalProjectJson {
    fun encode(p:Project)=Json.bytes(projectJson(p))
    fun decode(bytes:ByteArray):Project=decodeProject(Json.parse(bytes).obj())
    private fun id(x:ModelId?)=nullable(x?.value)
    private fun parameters(ps:List<Parameter>)=JA(ps.sortedBy{it.definitionId.value}.map{jo("definitionId" to js(it.definitionId.value),"value" to parameter(it.value))})
    private fun parameter(v:ParameterValue):J=when(v){
        is ParameterValue.Bool->jo("kind" to js("bool"),"value" to JB(v.value))
        is ParameterValue.IntNumber->jo("kind" to js("int"),"value" to Json.l(v.value))
        is ParameterValue.Real->jo("kind" to js("real"),"value" to Json.d(v.value))
        is ParameterValue.Text->jo("kind" to js("text"),"value" to js(v.value))
        is ParameterValue.Reference->jo("kind" to js("ref"),"value" to js(v.id.value))
        is ParameterValue.EnumValue->jo("kind" to js("enum"),"value" to js(v.value))
    }
    private fun bounds(b:DimensionBounds)=jo("min" to Json.d(b.min),"max" to Json.d(b.max))
    private fun point(p:Point2)=jo("xMeters" to Json.d(p.xMeters),"yMeters" to Json.d(p.yMeters))
    private fun projectJson(p:Project)=jo(
        "schemaVersion" to Json.l(p.schemaVersion.toLong()),"projectId" to js(p.id.value),"name" to js(p.name),
        "site" to jo("id" to js(p.site.id.value),"name" to js(p.site.name)),
        "buildings" to JA(p.buildings.sortedBy{it.id.value}.map{jo("id" to js(it.id.value),"name" to js(it.name))}),
        "levels" to JA(p.levels.sortedBy{it.id.value}.map{jo("id" to js(it.id.value),"name" to js(it.name),"elevationMeters" to Json.d(it.elevationMeters),"heightMeters" to (it.heightMeters?.let(Json::d)?:JNull))}),
        "materials" to JA(p.materials.sortedBy{it.id.value}.map{jo("id" to js(it.id.value),"name" to js(it.name),"category" to js(it.category),"densityKgPerCubicMeter" to (it.densityKgPerCubicMeter?.let(Json::d)?:JNull),"thermalConductivityWPerMeterKelvin" to (it.thermalConductivityWPerMeterKelvin?.let(Json::d)?:JNull),"costPerUnit" to (it.costPerUnit?.let(Json::d)?:JNull),"costUnit" to nullable(it.costUnit))}),
        "types" to JA(p.types.sortedBy{it.id.value}.map{jo("id" to js(it.id.value),"name" to js(it.name),"category" to js(it.category.name),"parameters" to parameters(it.parameters))}),
        "joins" to JA(p.joins.sortedBy{it.id.value}.map{jo("id" to js(it.id.value),"wallA" to js(it.wallA.value),"endA" to JB(it.endA),"wallB" to js(it.wallB.value),"endB" to JB(it.endB),"point" to point(it.point))}),
        "elements" to JA(p.elements.sortedBy{it.id.value}.map{e->when(e){
            is WallElement->jo("kind" to js("wall"),"id" to js(e.id.value),"typeId" to id(e.typeId),"levelId" to id(e.levelId),
                "axis" to jo("start" to point(e.axis.start),"end" to point(e.axis.end)),
                "heightMeters" to Json.d(e.heightMeters),"thicknessMeters" to Json.d(e.thicknessMeters),"baseElevationMeters" to Json.d(e.baseElevationMeters),"topElevationMeters" to (e.topElevationMeters?.let(Json::d)?:JNull),
                "locationLine" to js(e.locationLine.name),"layers" to JA(e.layers.map{jo("materialId" to id(it.materialId),"thicknessMeters" to Json.d(it.thicknessMeters),"function" to js(it.function.name),"isCore" to JB(it.isCore))}),"parameters" to parameters(e.parameters))
            is OpeningElement->jo("kind" to js("opening"),"id" to js(e.id.value),"typeId" to id(e.typeId),"levelId" to id(e.levelId),"hostWallId" to js(e.opening.hostWallId.value),"openingKind" to js(e.opening.kind.name),"offsetAlongWallMeters" to Json.d(e.opening.offsetAlongWall),"widthMeters" to Json.d(e.opening.width),"heightMeters" to Json.d(e.opening.height),"sillHeightMeters" to Json.d(e.opening.sillHeight),"parameters" to parameters(e.parameters))
            is CatalogInstanceElement->jo("kind" to js("catalogInstance"),"id" to js(e.id.value),"typeId" to id(e.typeId),"levelId" to id(e.levelId),"assetId" to js(e.instance.assetId),"position" to point(e.instance.position),"rotationRadians" to Json.d(e.instance.rotationRadians),"hostWallId" to js(e.instance.hostWallId.value),"targetWidthMeters" to Json.d(e.instance.targetWidth),"targetDepthMeters" to Json.d(e.instance.targetDepth),"targetHeightMeters" to Json.d(e.instance.targetHeight),"scalingMode" to js(e.instance.scalingMode.name),"widthBounds" to bounds(e.instance.bounds.width),"depthBounds" to bounds(e.instance.bounds.depth),"heightBounds" to bounds(e.instance.bounds.height),"category" to js(e.instance.category),"scheduleTag" to js(e.instance.scheduleTag),"parameters" to parameters(e.parameters))
        }})
    )
    private fun decodeProject(o:JO):Project {
        fun modelId(v:J)=ModelId(v.text())
        fun readParams(v:J)=v.array().a.map{val p=it.obj();val x=p.req("value").obj();val raw=x.req("value");Parameter(modelId(p.req("definitionId")),when(x.req("kind").text()){
            "bool"->ParameterValue.Bool(raw.bool());"int"->ParameterValue.IntNumber(raw.long());"real"->ParameterValue.Real(raw.double());"text"->ParameterValue.Text(raw.text());"ref"->ParameterValue.Reference(modelId(raw));"enum"->ParameterValue.EnumValue(raw.text());else->error("Unknown parameter kind")})}
        fun readPoint(v:J):Point2 {val x=v.obj();return Point2(x.req("xMeters").double(),x.req("yMeters").double())}
        fun readBounds(v:J):DimensionBounds {val x=v.obj();return DimensionBounds(x.req("min").double(),x.req("max").double())}
        val s=o.req("site").obj()
        val buildings=o.req("buildings").array().a.map{val x=it.obj();Building(modelId(x.req("id")),x.req("name").text())}
        val levels=o.req("levels").array().a.map{val x=it.obj();Level(modelId(x.req("id")),x.req("name").text(),x.req("elevationMeters").double(),x.opt("heightMeters").dOrNull())}
        val materials=o.req("materials").array().a.map{val x=it.obj();Material(modelId(x.req("id")),x.req("name").text(),x.req("category").text(),x.opt("densityKgPerCubicMeter").dOrNull(),x.opt("thermalConductivityWPerMeterKelvin").dOrNull(),x.opt("costPerUnit").dOrNull(),x.opt("costUnit").strOrNull())}
        val types=o.req("types").array().a.map{val x=it.obj();ElementType(modelId(x.req("id")),x.req("name").text(),ElementCategory.valueOf(x.req("category").text()),readParams(x.req("parameters")))}
        val elements=o.req("elements").array().a.map{val x=it.obj();val kind=x.req("kind").text();val type=x.opt("typeId").strOrNull()?.let(::ModelId);val level=x.opt("levelId").strOrNull()?.let(::ModelId);val params=readParams(x.req("parameters"));when(kind){
            "wall"->{val a=x.req("axis").obj();WallElement(modelId(x.req("id")),type,level,WallAxis(readPoint(a.req("start")),readPoint(a.req("end"))),x.req("heightMeters").double(),x.req("thicknessMeters").double(),x.req("baseElevationMeters").double(),x.opt("topElevationMeters").dOrNull(),WallLocationLine.valueOf(x.req("locationLine").text()),x.req("layers").array().a.map{l->val z=l.obj();WallLayer(z.opt("materialId").strOrNull()?.let(::ModelId),z.req("thicknessMeters").double(),WallLayerFunction.valueOf(z.req("function").text()),z.req("isCore").bool())},params)}
            "opening"->OpeningElement(Opening(modelId(x.req("id")),modelId(x.req("hostWallId")),OpeningKind.valueOf(x.req("openingKind").text()),x.req("offsetAlongWallMeters").double(),x.req("widthMeters").double(),x.req("heightMeters").double(),x.req("sillHeightMeters").double()),type,level,params)
            "catalogInstance"->{CatalogInstanceElement(CatalogInstance(modelId(x.req("id")),x.req("assetId").text(),readPoint(x.req("position")),x.req("rotationRadians").double(),modelId(x.req("levelId")),modelId(x.req("hostWallId")),x.req("targetWidthMeters").double(),x.req("targetDepthMeters").double(),x.req("targetHeightMeters").double(),ScalingMode.valueOf(x.req("scalingMode").text()),CatalogInstanceBounds(readBounds(x.req("widthBounds")),readBounds(x.req("depthBounds")),readBounds(x.req("heightBounds"))),x.req("category").text(),x.req("scheduleTag").text()),type,level,params)}
            else->error("Unsupported element kind: $kind")
        }}
        val joins=o.opt("joins").let { if(it==JNull) emptyList() else it.array().a.map{j->val x=j.obj();WallJoin(modelId(x.req("id")),modelId(x.req("wallA")),x.req("endA").bool(),modelId(x.req("wallB")),x.req("endB").bool(),readPoint(x.req("point")))} }
        return Project(modelId(o.req("projectId")),o.req("name").text(),o.req("schemaVersion").long().toInt(),Site(modelId(s.req("id")),s.req("name").text()),buildings,levels,elements,materials,types,joins)
    }
}
