# DEVLOG

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
