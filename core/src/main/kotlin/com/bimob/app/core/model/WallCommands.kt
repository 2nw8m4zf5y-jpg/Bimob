package com.bimob.app.core.model

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
    override fun apply(before: Project): Project = before.copy(elements = before.elements.filterNot { it.id == wallId })
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
