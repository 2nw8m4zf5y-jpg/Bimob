package com.bimob.app.core.model
data class CreateWallMutation(val wall: Wall) : ModelMutation {
 override val label="Create wall"
 override fun apply(before:Project):Project { require(before.elements.none{it.id==wall.id}){"Entity ID already exists: ${wall.id.value}"};return before.copy(elements=before.elements+WallElement(wall.id,levelId=wall.levelId,axis=wall.axis,heightMeters=wall.height,thicknessMeters=wall.thickness)) }
}
data class UpdateWallMutation(val wall:Wall):ModelMutation {
 override val label="Update wall"
 override fun apply(before:Project):Project { val old=before.elements.filterIsInstance<WallElement>().firstOrNull{it.id==wall.id}?:error("Wall does not exist: ${wall.id.value}");return before.copy(elements=before.elements.map{if(it.id==wall.id)old.copy(axis=wall.axis,heightMeters=wall.height,thicknessMeters=wall.thickness,levelId=wall.levelId)else it}) }
}
data class CreateOpeningMutation(val opening:Opening):ModelMutation {
 override val label="Create opening"
 override fun apply(before:Project):Project { require(before.elements.none{it.id==opening.id}){"Entity ID already exists: ${opening.id.value}"};return before.copy(elements=before.elements+OpeningElement(opening)) }
}
data class UpdateOpeningMutation(val opening:Opening):ModelMutation {
 override val label="Update opening"
 override fun apply(before:Project):Project { require(before.openings.any{it.id==opening.id}){"Opening does not exist: ${opening.id.value}"};return before.copy(elements=before.elements.map{if(it is OpeningElement&&it.id==opening.id)it.copy(opening=opening)else it}) }
}
data class DeleteOpeningMutation(val openingId:ModelId):ModelMutation {
 override val label="Delete opening"
 override fun apply(before:Project):Project { require(before.openings.any{it.id==openingId}){"Opening does not exist: ${openingId.value}"};return before.copy(elements=before.elements.filterNot{it is OpeningElement&&it.id==openingId}) }
}
data class CreateCatalogInstanceMutation(val instance:CatalogInstance):ModelMutation {
 override val label="Create catalog instance"
 override fun apply(before:Project):Project { require(before.elements.none{it.id==instance.id}){"Entity ID already exists: ${instance.id.value}"};return before.copy(elements=before.elements+CatalogInstanceElement(instance)) }
}
data class UpdateCatalogInstanceMutation(val instance:CatalogInstance):ModelMutation {
 override val label="Update catalog instance"
 override fun apply(before:Project):Project { require(before.catalogInstances.any{it.id==instance.id}){"Catalog instance does not exist: ${instance.id.value}"};return before.copy(elements=before.elements.map{if(it is CatalogInstanceElement&&it.id==instance.id)it.copy(instance=instance)else it}) }
}
data class DeleteCatalogInstanceMutation(val instanceId:ModelId):ModelMutation {
 override val label="Delete catalog instance"
 override fun apply(before:Project):Project { require(before.catalogInstances.any{it.id==instanceId}){"Catalog instance does not exist: ${instanceId.value}"};return before.copy(elements=before.elements.filterNot{it is CatalogInstanceElement&&it.id==instanceId}) }
}
