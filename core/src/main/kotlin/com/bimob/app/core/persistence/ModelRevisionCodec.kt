package com.bimob.app.core.persistence

import com.bimob.app.core.dependency.*
import com.bimob.app.core.model.*

/** Versioned deterministic persistence for immutable model revisions (private repository format). */
object ModelRevisionCodec {
    private const val FORMAT = 1

    fun encode(revision: ModelRevision): ByteArray {
        val nodes = revision.dependencyGraph.nodes.map(::nodeJson)
        val edges = revision.dependencyGraph.edges.map { edge ->
            jo("source" to nodeJson(edge.source), "dependent" to nodeJson(edge.dependent), "kind" to js(edge.kind.name))
        }
        val values = revision.derivedValues.entries.sortedBy { it.key.stableKey() }.map { (key, value) ->
            jo("node" to nodeJson(key), "value" to valueJson(value))
        }
        return Json.bytes(jo(
            "formatVersion" to Json.l(FORMAT.toLong()),
            "revision" to Json.l(revision.number),
            "project" to js(CanonicalProjectJson.encode(revision.project).decodeToString()),
            "nodes" to JA(nodes),
            "edges" to JA(edges),
            "derivedValues" to JA(values)
        ))
    }

    fun decode(bytes: ByteArray): ModelRevision {
        val root = Json.parse(bytes).obj()
        require(root.req("formatVersion").long() == FORMAT.toLong()) { "Unsupported model-revision format." }
        val number = root.req("revision").long()
        require(number >= 0L)
        val project = CanonicalProjectJson.decode(root.req("project").text().encodeToByteArray())
        require(ModelValidator.validate(project).none { it.severity == ValidationSeverity.ERROR }) { "Stored project fails validation." }
        var graph = DependencyGraph.empty()
        root.req("nodes").array().a.forEach { graph = graph.withNode(nodeDecode(it.obj())) }
        val edges = root.req("edges").array().a.map { item ->
            val o = item.obj()
            DependencyEdge(nodeDecode(o.req("source").obj()), nodeDecode(o.req("dependent").obj()), DependencyKind.valueOf(o.req("kind").text()))
        }
        when (val admitted = graph.admitEdges(edges)) {
            is EdgeAdmission.Accepted -> graph = admitted.graph
            is EdgeAdmission.Rejected -> error("Stored dependency graph invalid: ${admitted.diagnostic.failure}")
        }
        val derived = root.req("derivedValues").array().a.associate { item ->
            val o = item.obj()
            nodeDecode(o.req("node").obj()) to valueDecode(o.req("value").obj())
        }
        return ModelRevision(number, freezeProject(project), graph, freezeDerivedValues(derived))
    }

    private fun nodeJson(node: DependencyNodeKey): JO = when (node) {
        is EntityProperty -> jo("type" to js("entityProperty"), "id" to js(node.entityId.value), "aspect" to js(node.property.name))
        is LevelElevation -> jo("type" to js("levelElevation"), "id" to js(node.levelId.value))
        is WallGeometry -> jo("type" to js("wallGeometry"), "id" to js(node.wallId.value))
        is OpeningCut -> jo("type" to js("openingCut"), "id" to js(node.openingId.value))
        is SpaceBoundary -> jo("type" to js("spaceBoundary"), "id" to js(node.spaceId.value))
        is SpaceArea -> jo("type" to js("spaceArea"), "id" to js(node.spaceId.value))
    }
    private fun nodeDecode(o: JO): DependencyNodeKey {
        val id = ModelId(o.req("id").text())
        return when (o.req("type").text()) {
            "entityProperty" -> EntityProperty(id, PropertyAspect.valueOf(o.req("aspect").text()))
            "levelElevation" -> LevelElevation(id)
            "wallGeometry" -> WallGeometry(id)
            "openingCut" -> OpeningCut(id)
            "spaceBoundary" -> SpaceBoundary(id)
            "spaceArea" -> SpaceArea(id)
            else -> error("Unknown dependency node type.")
        }
    }
    private fun valueJson(value: DerivedValue): JO = when (value) {
        is DerivedValue.NumberValue -> jo("type" to js("number"), "value" to Json.d(value.value))
        is DerivedValue.BooleanValue -> jo("type" to js("boolean"), "value" to JB(value.value))
        is DerivedValue.TextValue -> jo("type" to js("text"), "value" to js(value.value))
        is DerivedValue.IdValue -> jo("type" to js("id"), "value" to js(value.value))
        is DerivedValue.NumberList -> jo("type" to js("numberList"), "value" to JA(value.values.map(Json::d)))
    }
    private fun valueDecode(o: JO): DerivedValue = when (o.req("type").text()) {
        "number" -> DerivedValue.NumberValue(o.req("value").double())
        "boolean" -> DerivedValue.BooleanValue(o.req("value").bool())
        "text" -> DerivedValue.TextValue(o.req("value").text())
        "id" -> DerivedValue.IdValue(o.req("value").text())
        "numberList" -> DerivedValue.NumberList(o.req("value").array().a.map { it.double() })
        else -> error("Unknown derived-value type.")
    }
}
