# Stage F · Step 2 — Openings, catalog and selection

The native editor remains backed by the C++ session and its undo journal. BGR1 supplies all world-space geometry and stable element IDs. The canonical native snapshot is read only for opening metadata omitted from BGR1 v1 (opening kind and host wall ID); it does not supply render coordinates.

## Tools
- Door and window tools project a tap onto the nearest wall centerline, validate a usable host segment, and dispatch CreateOpening with the native host ID and offset in metres.
- Catalog presets (table, single bed, plumbing column) dispatch CreateCatalogInstance with metric dimensions and a rotation in radians. The rotate control advances by 45 degrees.
- Selection hit-tests catalog oriented rectangles, hosted opening centers, then wall centerlines. Delete dispatches the matching native delete command. Deleting a wall uses native cascade semantics; Undo restores the native revision.

## Rendering
Walls and joins remain direct BGR1 polygons. Opening centers and dimensions and catalog transforms are read from their BGR1 records. Door/window classification and host-wall association are joined by ID from the canonical snapshot because BGR1 v1 intentionally contains no such fields.

## Scope
No 3D viewport, archive persistence, or export changes. Those remain Stage F · Step 3.
