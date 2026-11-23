package com.bimob.app.core.model
data class CreateWallMutation(val wall: Wall) : ModelMutation {
 override val label="Create wall"
 override fun apply(before:Project):Project { require(before.elements.none{it.id==wall.id}){"Entity ID already exists: ${wall.id.value}"};return before.copy(elements=before.elements+WallElement(wall.id,levelId=wall.levelId,axis=wall.axis,heightMeters=wall.height,thicknessMeters=wall.thickness)) }
}
data class UpdateWallMutation(val wall:Wall):ModelMutation {
 override val label="Update wall"
 override fun apply(before:Project):Project { val old=before.elements.filterIsInstance<WallElement>().firstOrNull{it.id==wall.id}?:error("Wall does not exist: ${wall.id.value}");return before.copy(elements=before.elements.map{if(it.id==wall.id)old.copy(axis=wall.axis,heightMeters=wall.height,thicknessMeters=wall.thickness,levelId=wall.levelId)else it}) }
}
data class CreateLevelMutation(val level: Level) : ModelMutation {
 override val label="Create level"
 override fun apply(before:Project):Project { require(before.levels.none{it.id==level.id}){"Level ID already exists: ${level.id.value}"};require(before.elements.none{it.id==level.id}){"Entity ID already exists: ${level.id.value}"};return before.copy(levels=before.levels+level) }
}
/** Resize along the existing axis direction, anchored at its start point. */
data class ResizeWallMutation(val wallId: ModelId,val targetLengthMeters:Double):ModelMutation {
 override val label="Resize wall"
 override fun apply(before:Project):Project {
  require(targetLengthMeters.isFinite()&&targetLengthMeters>0.0){"Target wall length must be positive and finite."}
  val w=before.elements.filterIsInstance<WallElement>().firstOrNull{it.id==wallId}?:error("Wall does not exist: ${wallId.value}")
  val dx=w.axis.end.xMeters-w.axis.start.xMeters;val dy=w.axis.end.yMeters-w.axis.start.yMeters;val old=w.axis.lengthMeters
  require(old.isFinite()&&old>0.0){"Wall axis must have positive finite length."}
  val scale=targetLengthMeters/old
  val axis=WallAxis(w.axis.start,Point2(w.axis.start.xMeters+dx*scale,w.axis.start.yMeters+dy*scale))
  return before.copy(elements=before.elements.map{if(it.id==wallId)w.copy(axis=axis)else it})
 }
}
data class CreateOpeningMutation(val opening:Opening):ModelMutation {
 override val label="Create opening"
 override fun apply(before:Project):Project { require(before.elements.none{it.id==opening.id}){"Entity ID already exists: ${opening.id.value}"};return before.copy(elements=before.elements+OpeningElement(opening)) }
}
data class UpdateOpeningMutation(val opening:Opening):ModelMutation {
 override val label="Update opening"
 override fun apply(before:Project):Project { require(before.openings.any{it.id==opening.id}){"Opening does not exist: ${opening.id.value}"};return before.copy(elements=before.elements.map{if(it is OpeningElement&&it.opening.id==opening.id)it.copy(opening=opening)else it}) }
}
data class DeleteOpeningMutation(val openingId:ModelId):ModelMutation {
 override val label="Delete opening"
 override fun apply(before:Project):Project { require(before.openings.any{it.id==openingId}){"Opening does not exist: ${openingId.value}"};return before.copy(elements=before.elements.filterNot{it is OpeningElement&&it.opening.id==openingId}) }
}
data class CreateCatalogInstanceMutation(val instance:CatalogInstance):ModelMutation {
 override val label="Create catalog instance"
 override fun apply(before:Project):Project { require(before.elements.none{it.id==instance.id}){"Entity ID already exists: ${instance.id.value}"};return before.copy(elements=before.elements+CatalogInstanceElement(instance)) }
}
data class UpdateCatalogInstanceMutation(val instance:CatalogInstance):ModelMutation {
 override val label="Update catalog instance"
 override fun apply(before:Project):Project { require(before.catalogInstances.any{it.id==instance.id}){"Catalog instance does not exist: ${instance.id.value}"};return before.copy(elements=before.elements.map{if(it is CatalogInstanceElement&&it.instance.id==instance.id)it.copy(instance=instance)else it}) }
}
data class DeleteCatalogInstanceMutation(val instanceId:ModelId):ModelMutation {
 override val label="Delete catalog instance"
 override fun apply(before:Project):Project { require(before.catalogInstances.any{it.id==instanceId}){"Catalog instance does not exist: ${instanceId.value}"};return before.copy(elements=before.elements.filterNot{it is CatalogInstanceElement&&it.instance.id==instanceId}) }
}

/** Moves one wall endpoint through the transaction boundary; join solving is deliberately separate. */
data class MoveWallEndpointMutation(val wallId:ModelId,val movedStart:Boolean,val point:Point2):ModelMutation {
 override val label="Move wall endpoint"
 override fun apply(before:Project):Project {
  require(point.xMeters.isFinite()&&point.yMeters.isFinite()){"Endpoint coordinates must be finite."}
  val wall=before.elements.filterIsInstance<WallElement>().firstOrNull{it.id==wallId}?:error("Wall does not exist: ${wallId.value}")
  val axis=if(movedStart)wall.axis.copy(start=point)else wall.axis.copy(end=point)
  return before.copy(elements=before.elements.map{if(it.id==wallId)wall.copy(axis=axis)else it})
 }
}
