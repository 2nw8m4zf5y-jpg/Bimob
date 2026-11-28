# NAGAPLAS Workspace Grid Contract (104 × 48)

Coordinates are Excel/Sea-Battle style: columns A–CZ, rows 1–48, 1-based labels.
Cell ID remains row-major: ID = (row − 1) × 104 + column index (A=1).
The workbook `Эталончик список меню.xlsx` is the canonical region map.

## Header
Q0001 A1:L3 brand + main-menu trigger; Q0002 M1:T3 reserved; Q0003–Q0006 U1:AV3 quick-action slots; Q0007 AW1:BC3 project name; Q0008–Q0010 BD1:BX3 quick-action slots; Q0011 BY1:CE3 delete; Q0012 CF1:CL3 redo; Q0013 CM1:CS3 undo; Q0014 CT1:CZ3 theme selector.

## Tool ribbon
Q0015–Q0048: 34 icons, each 3×3, across A:CX rows 4–6. Q0049 CY4:CZ6 is a 2×3 reserved block.

## Panels and canvas
Q0050 A7:L7 left-panel header; Q0052 A8:L45 left panel.
Q0051 CO7:CZ7 right-panel header; Q0053 CO8:CZ45 right inspector.
Drawing canvas M7:CN45. Q0054 status strip A46:CZ48.

No region overlap is allowed. Base elements anchor to the map; popup/menu layers may overlay without changing the layout.
