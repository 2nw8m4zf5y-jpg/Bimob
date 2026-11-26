# NAGAPLAS CAD Workspace — Design Contract (W0)

Status: **Approved design baseline; implementation not yet accepted**  
Target branch: `feature/model-core-v1`  
Scope: Android adaptive CAD workspace, vector-first, touch + precision input.

## 1. Product intent

Build a professional drafting environment, not a mobile toolbar demo. The viewport is the primary work surface. All chrome must be purposeful, restrained, dense without becoming illegible, and consistent with the agreed dark navy/cyan engineering concept. Future improvements may exceed this baseline but may not regress its structure, usability, precision, or visual language.

## 2. Screen zones (top to bottom)

1. **Project / system bar** — app menu, quick actions, project name and modified marker, file actions, export, settings/theme. Keep compact; don't allow project text to consume command space.
2. **Ribbon tabs** — Home, Build, Edit, Architecture, BIM, Annotations, Layers, View, Tools. Only the active tab's groups are presented.
3. **Context ribbon** — horizontally scrollable, visually separated command groups. Commands have consistent vector glyphs, short labels, active/disabled states, tooltips/help, and accessible semantics.
4. **Left tool dock** — persistent, scrollable, one-tap access to selection, construction, editing, nodes, openings, dimensions. Group related flyouts; do not duplicate all ribbon commands.
5. **Viewport** — largest zone. Render model, grid, axes, UCS, selection, cursor, dynamic preview, snap markers and dimensions in separate visual layers.
6. **Properties inspector** — contextual, scrollable, collapsible on compact widths, with sections for identity/layer, geometry, nodes, constraints, construction/material and display. Values are editable only when backed by real model commands.
7. **Command/input strip** — command prompt/history, argument hints, numeric fields, accept/cancel, and keyboard-aware layout.
8. **Status bar** — coordinates, model/paper context, Grid, Snap, Ortho, Polar, Object Snap, Tracking, lineweight, UCS and scale. States must reflect actual application state.

## 3. Layout and adaptive rules

- The viewport receives all remaining space after measured chrome.
- Ribbon and command groups scroll; no command may be clipped silently.
- Left dock uses a compact rail at phone widths; labels appear via tooltip/context or wider layout.
- Properties inspector is full-width overlay/sheet on narrow portrait layouts and a dock on landscape/tablet. It must not permanently steal viewport width in portrait.
- On landscape, preserve all zones, with viewport as the dominant region.
- Insets (status/navigation/IME) are respected. No toolbar or command prompt may sit under system bars or keyboard.
- Avoid fixed widths derived from screenshots. Use window size classes and constraints, with documented breakpoints.
- Do not use decorative rounded cards. Use square/near-square CAD panels, fine dividers and aligned baselines.

## 4. Icon sizing and visual reference

- Cancel the 6 mm physical-glyph rule. It is not a product constraint and caused artwork to appear too small in the tested workspace.
- Size visible vector glyphs by their role and by the approved CAD workspace reference, using dp-based responsive sizes rather than unreliable physical-DPI calculations.
- Ribbon tool glyphs should be prominent (approximately 22–28 dp), with enough vertical space for icon, label and active state. Dock glyphs should be similarly legible (approximately 22–26 dp) inside larger hit areas.
- Touch targets remain larger than visible artwork and must be accessible; visual icon size and hit-area size are separate.
- Vector artwork uses a shared 24-unit grid, optical margins, consistent stroke family and cap/join rules; glyph geometry remains sharp at all densities.
- Labels are outside the glyph sizing target and must be readable at the reference workspace scale.
- Icons are semantic vector artwork, not arbitrary Unicode/emoji. Selected/pressed/disabled states are distinct and high-contrast.

## 5. Visual tokens (dark engineering theme)

- Canvas: `#0B1525` (or current theme-specific canvas)
- Panel: `#14243A`
- Panel elevated: `#182A40`
- Fine grid: `#182538`
- Major grid: `#2B4058`
- Divider: `#344B63`
- Primary geometry: cool light neutral
- Accent / active command: cyan-blue
- Selected geometry: amber
- Snap / valid state: green
- Warning: amber-orange
- Error: restrained red

No glow, gradients, glassmorphism, large rounded cards, decorative icons, or unnecessary animation. Contrast and drawing legibility win.

## 6. Command and state architecture

UI elements dispatch typed commands to the existing editor/command layer. The native C++20 model remains the source of truth for geometry. UI must not keep shadow geometry or present fake values.

Separate modules/components:
- Workspace shell and responsive layout
- Project bar
- Ribbon tab navigation and group rendering
- Tool dock
- Viewport renderer and overlay layers
- Properties inspector
- Command input/history
- Status bar
- Shared icon library and theme tokens
- Workspace state adapter (selected tool, active tab, panel visibility, command context)
- Command dispatch adapter (typed command routing)

Do not put all zones and all interaction logic in one giant composable. Keep state hoisted, typed, and observable.

## 7. Interaction contract

- Touch, pointer, keyboard/command entry and numeric input are first-class.
- Pan/zoom gestures must not conflict with active drawing gestures.
- Selection and snapping are visible and deterministic.
- Active command clearly shows its current prompt, required input and cancel/confirm actions.
- Numeric values and units are explicit. No silent rounding of model data.
- Undo/redo always operates on committed model transactions; previews are not committed.
- Tool buttons indicate activation and provide accessible descriptions.
- Every visible actionable control must have a real action or be explicitly disabled; no fake controls.

## 8. Quality gates / acceptance

### Visual
- Compare device screenshots against approved layout reference at the same aspect ratio.
- All zones are present and in intended hierarchy.
- No clipping, overlap, unexplained blank space, tiny illegible labels, or accidental horizontal compression.
- Icon visible artwork measured/calibrated to target 6 mm; touch target measured separately.
- Canvas dominates; panels adapt rather than crushing viewport.

### Functional
- Each tool has an explicit command mapping and actual availability.
- Selection, properties, layers, snap, ortho, grid, coordinate feedback, command prompts, save/export and undo/redo accurately reflect state.
- No placeholder counters or fake property values in accepted UI.
- Native contracts, parity and Android build pass.
- Device QA includes screenshots, wall creation with exact input, junction/snap tests, selection/property edit, undo/redo, pan/zoom, portrait and landscape.

## 9. Implementation sequence

W0 design contract (this document)  
W1 component architecture, responsive zone layout, theme/token and icon grid  
W2 project bar + ribbon/tab/group behavior  
W3 dock and vector icon system with physical sizing/calibration  
W4 viewport overlay and interaction integration  
W5 contextual inspector and real editable fields  
W6 command input and status modes wired to real state  
W7 end-to-end native command mapping, removal of placeholders  
W8 CI + device visual/functional QA and fix pass

Each stage is reviewed before advancing. A green compile is necessary but never sufficient for design acceptance.
