# NAGAPLAS 104x48 UI coordinate contract

Approved grid: 104 columns x 48 rows = 4,992 cells. IDs are 1-based row-major: column=(id-1)%104, row=(id-1)/104.

Canonical Kotlin mapping is implemented in `NagaplasGrid104x48`: validates ids, maps cell coordinates, normalizes multi-row strips into cell-aligned rectangles, maps rectangles to screen pixels, and checks static placement for collisions.

| Element | Cell IDs |
|---|---|
| Logo | 1..7 |
| Current project | 45..60 |
| Delete | 77..83, 181..187, 285..291 |
| Redo | 84..90, 188..194, 292..298 |
| Undo | 91..97, 195..201, 299..305 |
| Theme | 98..104, 202..208, 306..312 |
| Main | 105..110, 209..214 |
| Architecture | 111..115, 215..219 |
| Tools | 116..120, 220..224 |
| Materials | 121..125, 225..229 |
| Slots 1..5 | 313..327, 417..431, 521..535 (3x3 each) |
| Information header | 711..728 |

Main-menu popup is transient overlay geometry and excluded from static collision checks. **This commit is grid foundation only**; workspace screen integration and action wiring are not yet completed.
