package com.bimob.app.core.model

import com.bimob.app.core.dependency.WallGeometry
import com.bimob.app.core.dependency.OpeningCut
import com.bimob.app.core.dependency.EntityProperty
import com.bimob.app.core.dependency.PropertyAspect

/** Typed wall mutations intended to be portable across Kotlin and the future native command ABI. */
data class AddWallCommand(val wall: WallElement) : ModelMutation {
    override val label = "Add wall"
    override fun apply(before: Project): Project {
        require(before.elements.none { it.id == wall.id }) { "Wall ID already exists: ${wall.id.value}" }
        return before.copy(elements = before.elements + wall)
    }
}

data class MoveWallCommand(val wallId: ModelId, val deltaX: Double, val deltaY: Double) : ModelMutation {
    override val label = "Move wall"
    override fun apply(before: Project): Project {
        require(deltaX.isFinite() && deltaY.isFinite())
        return before.copy(elements = before.elements.map { element ->
            if (element is WallElement && element.id == wallId) element.copy(axis = WallAxis(
                Point2(element.axis.start.xMeters + deltaX, element.axis.start.yMeters + deltaY),
                Point2(element.axis.end.xMeters + deltaX, element.axis.end.yMeters + deltaY)
            )) else element
        })
    }
}

data class DeleteWallCommand(val wallId: ModelId) : ModelMutation {
    override val label = "Delete wall"
    override fun apply(before: Project): Project = cascade(before)
    override fun stage(before: Project, context: TransactionContext): Project {
        before.openings.filter { it.hostWallId == wallId }.forEach { context.removeDependencyNode(OpeningCut(it.id)) }
        before.catalogInstances.filter { it.hostWallId == wallId }.forEach { context.removeDependencyNode(EntityProperty(it.id, PropertyAspect.HOST)) }
        context.removeDependencyNode(WallGeometry(wallId))
        return cascade(before)
    }
    private fun cascade(before: Project): Project {
        require(before.elements.any { it is WallElement && it.id == wallId }) { "Wall does not exist: ${wallId.value}" }
        return before.copy(elements = before.elements.filterNot { e ->
            e.id == wallId || (e is OpeningElement && e.opening.hostWallId == wallId) ||
                (e is CatalogInstanceElement && e.instance.hostWallId == wallId)
        })
    }
}

data class SetWallThicknessCommand(val wallId: ModelId, val thicknessMeters: Double) : ModelMutation {
    override val label = "Change wall thickness"
    override fun apply(before: Project): Project {
        require(thicknessMeters.isFinite() && thicknessMeters > 0.0)
        return before.copy(elements = before.elements.map { element ->
            if (element is WallElement && element.id == wallId) element.copy(thicknessMeters = thicknessMeters) else element
        })
    }
}
