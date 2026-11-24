# Stage F · Step 1 — Native 2D editor

The Android editor owns one native session through an Activity-scoped ViewModel. The ViewModel creates a default level on project activation, routes wall creation, wall movement, Undo and Redo through the JNI command boundary, exports BGR1 after accepted mutations, and destroys the handle from `onCleared()`.

The Compose viewport renders the four-vertex wall and wall-join records directly from BGR1. Coordinates are world-space metres transformed to screen pixels; the grid is 0.5 m and wall snapping is 0.1 m with endpoint priority. A two-pointer gesture pans and zooms the canvas. A one-pointer drag previews a wall footprint and commits one CreateWall command on release.

Moving an existing wall starts a native gesture, sends incremental MoveWall mutations under the same gesture ID, and commits or cancels the gesture as a single history entry. History availability is returned by the native bridge alongside the command response.

This step deliberately does not add opening or catalog tools. Native editor persistence/export integration is not part of this step; the legacy project archive controls are withheld from the editor screen so they cannot silently save a stale Kotlin-side model.
