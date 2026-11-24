# Native Render Geometry Buffer v1

The JNI render export is a deterministic binary snapshot, implemented by the G3 Step 2b native session and Kotlin bridge. It is intended for viewport consumption without JSON parsing. It is **not** a mesh/triangulation format; the UI builds render primitives from these records.

## Header

All integer values and IEEE-754 Float32 values are little-endian.

| Offset | Type | Meaning |
|---:|---|---|
| 0 | 4 bytes | ASCII magic `BGR1` |
| 4 | uint32 | `formatVersion = 1` |
| 8 | uint64 | Native model revision number |
| 16 | uint32 | Wall record count |
| 20 | uint32 | WallJoin record count |
| 24 | uint32 | Opening record count |
| 28 | uint32 | CatalogInstance record count |

The fixed header is 32 bytes. Each section is emitted in order: Walls, WallJoins, Openings, CatalogInstances. Section records are sorted by their corresponding IDs.

## Numeric sections

Every record is Float32; all world coordinates, offsets, and dimensions are metres.

### Walls: 10 floats / record

- `0..7`: four XY vertices in polygon order: start-side+, end-side+, end-side−, start-side−.
- `8`: base elevation from the host Level, or 0 if the wall has no Level.
- `9`: wall height.

Endpoint wall junctions are miter-trimmed. The export currently trims endpoint L/T junctions; interior X intersections are represented by a WallJoin overlay and do not split a wall into subsegments.

### WallJoins: 9 floats / record

- `0..7`: four XY vertices of the junction footprint, in polygon order.
- `8`: maximum height of the two joined walls.

Explicit model joins are exported; intersections between finite non-parallel wall axes are also inferred deterministically when the model has no explicit join for that wall pair. Auto IDs use `auto:<wallIdA>:<wallIdB>`, with wall IDs lexicographically ordered.

### Openings: 8 floats / record

- `0,1`: opening center XY in world coordinates, computed along the host wall axis.
- `2`: offset along the host wall from its start, metres.
- `3`: width, metres.
- `4`: height, metres.
- `5`: sill height, metres.
- `6`: host Level elevation, metres (0 if unavailable).
- `7`: host wall axis length, metres.

### CatalogInstances: 8 floats / record

- `0,1`: world XY position.
- `2`: rotation in radians.
- `3,4,5`: target width, depth, height in metres.
- `6`: host Level elevation in metres (0 if unavailable).
- `7`: 1 if hosted on a wall, otherwise 0.

## ID tables

After all numeric sections, four ID tables are appended in section order. Each ID is encoded as uint32 UTF-8 byte length followed by that many UTF-8 bytes. Each table has exactly the same item count and order as its numeric section. This keeps selection identity without embedding strings into per-frame geometry records.

## Revision semantics

The revision is the exact `ModelRevision.number` used for the export. Export is performed while holding the NativeSession registry lock and snapshots that revision once. A viewport may skip upload/render preparation when the revision matches its last accepted buffer. No revision change means the model geometry is unchanged; the buffer remains deterministic for the same revision.

## Kotlin decoding

`NativeEngineBridge.exportRenderGeometry()` validates magic, version, section counts, payload bounds, finite floats, UTF-8 ID strings, and trailing bytes before returning `RenderGeometryBuffer`. Unsupported format versions fail explicitly rather than being misinterpreted.
