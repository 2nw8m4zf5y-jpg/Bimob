package com.bimob.app.core.model

interface ModelCommand {
    val description: String
    fun execute(store: ModelStore)
    fun undo(store: ModelStore)
}

/** In-memory prototype; repository persistence is a later implementation gate. */
class ModelStore(initialProject: Project) {
    var project: Project = initialProject
        private set
    fun replaceProject(value: Project) { project = value }
    fun addElement(element: BimElement) {
        require(project.elements.none { it.id == element.id }) { "Duplicate element ID: ${element.id.value}" }
        project = project.copy(elements = project.elements + element)
    }
    fun removeElement(id: ModelId): BimElement {
        val found = project.elements.firstOrNull { it.id == id } ?: error("Element not found: ${id.value}")
        project = project.copy(elements = project.elements.filterNot { it.id == id })
        return found
    }
}
class CreateWallCommand(private val wall: WallElement) : ModelCommand {
    override val description = "Create wall"
    override fun execute(store: ModelStore) = store.addElement(wall)
    override fun undo(store: ModelStore) { store.removeElement(wall.id) }
}
class DeleteElementCommand(private val elementId: ModelId) : ModelCommand {
    private var deleted: BimElement? = null
    override val description = "Delete element"
    override fun execute(store: ModelStore) { deleted = store.removeElement(elementId) }
    override fun undo(store: ModelStore) { store.addElement(deleted ?: error("Command not executed")) }
}
class CommandHistory(private val store: ModelStore) {
    private val undoStack = ArrayDeque<ModelCommand>()
    private val redoStack = ArrayDeque<ModelCommand>()
    fun execute(command: ModelCommand) { command.execute(store); undoStack.addLast(command); redoStack.clear() }
    fun undo(): Boolean {
        if (undoStack.isEmpty()) return false
        val command = undoStack.removeLast(); command.undo(store); redoStack.addLast(command); return true
    }
    fun redo(): Boolean {
        if (redoStack.isEmpty()) return false
        val command = redoStack.removeLast(); command.execute(store); undoStack.addLast(command); return true
    }
}
