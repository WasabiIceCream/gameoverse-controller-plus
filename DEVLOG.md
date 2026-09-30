# DEVLOG

## 2026-09-30: 1.0.1, zoom with LB/RB while scoped

User-approved design: while scoped, RB = one scroll-up step (zoom in), LB = one scroll-down step
(zoom out), auto-repeating while held; LB/RB do nothing else while scoped.

Checked in the jars first:

- Minecraft 26.1.2 `MouseHandler.onScroll(long, double, double)` is private; returns unless the
  window handle matches; with no screen and a player, `ScrollWheelHandler.onMouseScroll` (which
  accumulates, returns early on 0), then spectator fly speed or
  `Inventory.setSelectedSlot(getNextScrollWheelSelection(...))`.
- Spyglass Improvements 1.5.13: `fabric.mixin.MouseMixin` injects (cancellable) at the
  `getNextScrollWheelSelection` call in `onScroll`; `MouseEvents.onScroll` acts only when
  `player.isScoping()` and first person, changes `SpyglassImprovementsClient.MULTIPLIER` (positive
  scroll = zoom in) and cancels. Its `PlayerEntityMixin` makes `Player.isScoping()` return true
  while its static `force_spyglass` is set, so `isScoping()` covers its spyglass key too.
- Controlify 3.5.3 hooks mouse input at the GLFW callbacks (`core.MouseHandlerMixin.wrapMouseEvents`,
  which switches input mode), not in `onScroll`, so calling `onScroll` directly doesn't flip it to
  keyboard/mouse. It has its own `onScroll` invoker, but a mixin-package interface can't be
  referenced from outside, so we add ours (`MouseHandlerInvoker`).
- Other `onScroll` hooks in the Working instance (Bits and Balance arrow selection at HEAD, Amber
  events, Camerapture, Create, Enderscape, Inventory Management, Smooth Scroll, Spyglass Astronomy,
  Architectury, Fabric API) all see the step the way they see a wheel notch.
- Ok Zoomer is **not installed** in either mod set (no jar in the Working instance or
  `host-modpack/main/mods/`), so nothing could be checked against it. Detection falls back to
  `KeyMapping.get("key.ok_zoomer.zoom").isDown()`, which does nothing while it's absent. The
  1.0.0 D-up hold bind to it therefore does nothing either.

Engine: new trigger type `SCOPED` (fires on press, PRESS mode repeats after `ticks` every
`windowTicks`) and `tick(physical, active, scoped)`. While scoped, a button with a Scoped bind is
"captured" (sticky, runs only its scoped binds; its layers and chords can't form since a captured
modifier is never in a pressed phase). Scope start makes already-held scoped buttons sticky and
drops their pending tap/layer; scope end stops repeats, Hold While and Toggle scoped actions.
A button whose only binds are scoped is left alone outside a scope. 12 new tests (38 total).

Actions `gameoverse_controller_plus:scroll_up/scroll_down` (in the config screen's action dropdown;
the Scoped trigger reuses Hold Time as the repeat delay and Tap Window as the repeat interval).
Customized configs don't get the two new defaults: Reset to defaults adds them.

In-game test script (1.0.1):
1. Log: still two `Controlify hook applied` lines; `Loaded 17 advanced controller binds (defaults)`.
2. Not scoped: LB/RB taps still move the hotbar on release, LB+A still casts, LB+RB still opens the
   Skill Forest.
3. Hold a spyglass, hold LT to look through it. Tap RB: zooms in one step (spyglass sound); hold RB:
   keeps zooming in after ~0.3 s. LB zooms out. Hotbar slot never changes; LB+A does nothing but A
   jumps; LB+RB doesn't open the Skill Forest.
4. Tap D-up (Spyglass Improvements toggle), then RB/LB: same zoom; tap D-up again to stop.
5. Hold RB while scoped, release LT while still holding RB: zooming stops at once; releasing RB
   afterwards doesn't move the hotbar. Next RB tap moves the hotbar as usual.
6. Hold LB (not scoped), start scoping with LT, release LB: no hotbar change, no zoom.

## 2026-09-30: 1.0.0

Built from `docs/controller-advanced-bindings-design.md` (researched against Controlify 3.5.3). User
decisions: accept the two internal mixins; spell layers on LB/RB; ship the full layout; defaults in
code with user overrides and a YACL screen; MIT.

What was checked in the jars before writing code:

- `InputComponent.pushState(ControllerState)`: maps the state, stores it, rebuilds the deadzone
  view (a new `DeadzoneControllerStateView` object every push, which the wrap uses to spot the first
  binding call of a push), then one `invokeinterface InputBinding.pushState` in the binding loop.
- `InputBindingImpl.pushState`: `analogue = boundInput().state(view)`, then the `fakePressState`
  switch (0 -> 0, 1-2 -> 1, 3 -> 0), then `stateHistory.push(Float.valueOf(analogue))`. One
  `Float.valueOf` in the method. The design doc suggested a `@ModifyVariable` on `analogue`, but the
  local is loaded before the push call, so the hook is a `@ModifyArg` on the boxing call instead.
- `KeyMappingEmulationOutput.push()` (how `controlify_modded:*` bindings drive KeyMappings): on the
  binding's rising edge calls `KeyMappingHandle.controlify$setPressed(true)`, which Controlify's
  `KeyMappingMixin` implements by setting `isDown` and incrementing `clickCount` directly. So it
  works whatever keyboard key is bound, including `key.keyboard.unknown`. Skipped when a screen is
  open or the controller isn't current.
- `ControlifyBindings.registerModdedBindings`: id is `controlify_modded:` + the KeyMapping name
  lowercased with `[^a-z0-9/._-]` -> `_`; skipped for keys Controlify already correlates (vanilla).
- Spell Engine 1.10.9 `SpellHotbar`/`WrappedKeybinding`: a slot's key is its `spell_hotbar_N`
  KeyMapping only if bound, else the deferred vanilla key (`HOTBAR_KEY_N` in our client config),
  else none; with `spellHotbarUseKey: true` the first spell sits on the use key. Casting polls
  `isDown()` on that key. Hotbar selection from a deferred `key.hotbar.N` goes through
  `consumeClick` (wrapped by Spell Engine's `selectSlot_Wrap`). Hence the spell-slot action:
  resolve through `structuredSlots.other()` and set `isDown` only.
- Controlify defaults (`assets/controlify/controllers/default_bind/default.json`) plus our
  `profile-1.json`: D-up = Ok Zoomer zoom + Accessorify spyglass + Spyglass Improvements (open_chat
  unbound, `{}`), D-left = pick block + Hotbar Slot Cycling cycle-left. Y is inventory (and
  drop_inventory, a container binding).

Engine: 26 JUnit tests (`./gradlew test`) cover tap vs hold, hold-while stop on release, tap+hold
split on one button, tap-only fire-on-press, layers (no delay for layer buttons alone, modifier tap
replay, modifier timeout, modifier released first, same-tick press, layer button with its own hold,
two modifiers), chords in both orders and on the same tick, double/triple tap, single-tap replay
after the window, multi-tap hold passthrough, and deactivation (sticky masking, holds surviving a
screen, toggles stopped, hard reset, pending multi-tap dropped). One fix from the tests: the
multi-tap window was counting the release tick, so it closed 50 ms early.

Not done in game yet: needs a controller on the Working instance (test script in the report and
below).

In-game test script:
1. Start the Working instance, check `logs/latest.log` for two `Controlify hook applied` lines and
   `Loaded 15 advanced controller binds (defaults)`, and no `NOT applied` error.
2. Join a world with a controller. Tap LB / RB: hotbar slot moves on release. Hold LB 1 s, release:
   nothing. Press A alone: instant jump.
3. Hold a spell weapon. Hold LB, press A: second spell on the bar casts (first is on LT); hold for
   a charged spell, release A to release it. Release LB before A: no jump when A comes up. Try LB+X,
   LB+Y, LB+B, RB+A/X/Y/B.
4. Press LB and RB together: Skill Forest opens. Neither shoulder moves the hotbar.
5. Tap Back: perspective changes. Hold Back ~0.5 s: the Guide opens (rumble), no perspective change.
6. Tap Y: inventory. Hold Y ~0.4 s: World Tier screen.
7. Tap D-up with a spyglass in the inventory: scoped until tapped again. Hold D-up: zoom while held.
8. Tap D-left: hotbar row cycles. Hold D-left on a block you carry: pick block.
9. ModMenu > Gameoverse Controller Plus: change LB+A to Press mode, Save, confirm the file appears
   in `config/`; Reset to defaults, Save, file drops back to no binds.
