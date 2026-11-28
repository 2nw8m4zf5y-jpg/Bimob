package com.bimob.app.core.persistence

import com.bimob.app.core.dependency.*
import com.bimob.app.core.model.*
import org.junit.Assert.*
import org.junit.Test

class ModelRevisionCodecTest {
    @Test fun roundTripsProjectGraphAndDerivedValuesDeterministically() {
        val a=EntityProperty(ModelId("A"),PropertyAspect.PARAMETERS)
        val b=WallGeometry(ModelId("B"))
        val graph=(DependencyGraph.empty().admitEdges(listOf(DependencyEdge(a,b,DependencyKind.FORMULA))) as EdgeAdmission.Accepted).graph.withNode(SpaceArea(ModelId("isolated")))
        val project=Project(id=ModelId("project"),name="Persist",elements=listOf(WallElement(id=ModelId("w"),axis=WallAxis(Point2(0.0,0.0),Point2(2.0,0.0)),heightMeters=2.7,thicknessMeters=.2)))
        val revision=ModelRevision(42,project,graph,mapOf(a to DerivedValue.NumberList(listOf(1.0,2.5)),b to DerivedValue.TextValue("derived")))
        val encoded=ModelRevisionCodec.encode(revision)
        val decoded=ModelRevisionCodec.decode(encoded)
        assertEquals(revision,decoded)
        assertArrayEquals(encoded,ModelRevisionCodec.encode(decoded))
    }
}
