# DEVLOG

## 2026-09-30: 1.0.3, scoped fixes, Spyglass Astronomy and JEI from a controller

User feedback from in-game testing of 1.0.2 (which works): (1) while scoped the guide still showed
Controlify's "Zoom [LT]"; (2) the pad buzzed lightly for as long as the spyglass was up; (3) no
controller access to Spyglass Astronomy while scoped; (4) X swapped hands while scoped; (5) no way to
show JEI recipes/uses for the item under the cursor in inventories.

Findings (read in Controlify 3.5.3 source at the tag, Spyglass Astronomy 1.0.27, Spyglass Improvements
1.5.13 and JEI 29.43.0.106 decompiled, Minecraft 26.1.2):

- **Vibration**: Controlify's `mixins/feature/rumble/useitem/LocalPlayerMixin` starts a
  `ContinuousRumbleEffect` on `startUsingItem`; case `BLOCK, SPYGLASS` is `RumbleState(0, tick % 4 / 4 *
  0.12 + 0.05)` with no timeout, stopped only by `stopUsingItem`. Spyglass Improvements' key handler
  calls `gameMode.useItem` on the real spyglass (swapping it into the hand), so D-up scoping starts it
  the same as LT. Not ours: our rumble is one `BasicRumbleEffect.constant(0.25, 0.1, 3)` per HOLD or
  MULTI_TAP trigger (the D-up hold toggle fires once; REPEAT follow-ups and Scoped binds never rumble).
  Fix: each client tick, if the player is using a SPYGLASS-animation item, `stop()` the effect from
  Controlify's public `UseItemEffectHolder` interface (its LocalPlayer mixin implements it). Only that
  effect; shield (same pulse, BLOCK), bow, eat, damage rumbles untouched. Config `scopeRumble` (off).
- **Zoom [LT]**: `in_game.json` rules `for: controlify:use` with a spyglass in main or off hand, text
  `controlify.guide.ingame.zoom`. Spyglass Improvements' `MinecraftClientMixin` ORs its key into the
  `keyUse.isDown()` check in `handleKeybinds`, so with the D-up toggle LT does nothing (release doesn't
  stop, press is consumed). Plan now carries `hiddenBindings` (`controlify:use`, `controlify:attack`)
  while scoped and a `STOP_SCOPE` entry (`Stop` with the use binding's own glyph, right side) only
  when LT is held, the item is in use, and neither the Spyglass Improvements key nor our toggle holds it.
- **Spyglass Astronomy**: no KeyMappings. `SpyglassAstronomyClient.update()` (END_CLIENT_TICK) polls
  `keyPickItem.isDown()` rising edge (cycle `editMode` 0/1/2) and `keyAttack.isDown()` (mode 1 draw
  while held, the line end follows the view from its `MouseHandler.turnPlayer` TAIL hook; mode 2
  select). Its update-while-drawing call is gated on `isModLoaded("spyglass-improvements")`, the wrong id
  (the mod is `spyglass_improvements`), but the turnPlayer hook covers it. Commands `sga:info`,
  `sga:name`, `sga:select`, ... are Fabric client commands. Controlify: `attack` has
  `keyEmulation(keyAttack)` (edge-based `setPressed`), `pick_block` has none, so RT drew but nothing
  could change the mode. New actions `astronomy_mode` (3-tick `setDown` pulse of `keyPickItem`, no
  click), `astronomy_use` (hold `keyAttack` while B held, only in modes 1-2; release keeps it down if
  Controlify's attack binding is held), `astronomy_info` (`connection.sendCommand("sga:info")`, which
  Fabric's `ClientPacketListenerMixin.onSendCommand` runs locally). No mouse events needed.
- **Swap hands**: `controlify:swap_hands` on X (default layout, not rebound in our profile). X now has a
  Scoped bind, so the engine captures it while scoped.
- **Scoped Press/Repeat**: X's info must not auto-repeat, so Scoped + Press now fires once and Scoped +
  Repeat does the old repeat; the zoom defaults are Repeat, config version 2, version-1 files have their
  Scoped Press binds migrated to Repeat on load.
- **JEI**: Controlify container screens use `AbstractContainerScreenProcessor` (vmouse
  `CURSOR_SCROLL`, A `inv_select`, Y `inv_quick_move`, X `inv_take_half`, Y `drop_inventory` with a
  carried stack, B `gui_back`, LB/RB tabs, D-pad `vmouse_snap_*`, LS `vmouse_shift` (Shift held via its
  `InputConstantsMixin`), Back `vmouse_toggle`, LT/RT page). RS click is unused there. Our profile's
  BRBE recipe/usage view binds (LS/RS, `controlify_modded`) are dead in screens:
  `KeyMappingEmulationOutput.push` returns while a screen is open. JEI's Fabric side handles screen keys
  through Fabric's screen keyboard events (inside `KeyboardHandler.keyPress`); `RecipesGui.keyPressed`
  goes to its `UserInputHandler` (`recipeBack` = Backspace -> `back()`; close key and inventory key ->
  `onClose()`, which returns to the parent screen). Controlify's keyboard hook wraps the GLFW key
  callback (`KeyboardHandlerMixin.wrapKeyboardEvents`), above `keyPress`. So: `key_press/<name>` looks up
  the KeyMapping's bound key (`key` accessor) and invokes `keyPress` (press, release; `onButton` for a
  mouse key) at the end of the client tick. Controlify's container guide is `GuideInstanceImpl` with
  domain `controlify:container` (`ContainerContext` has the hovered slot), so the guide hook handles it
  too. JEI's recipe screen has no Controlify guide.

Engine: `InputContext` (GAME, SCREEN, RECIPE_SCREEN) on each `Bind`/`BindEntry` (missing = GAME, so old
configs load unchanged; Scoped binds compile only in GAME); `EngineSet` runs one `TriggerEngine` per
context and merges masks (replays of a button another engine masks are dropped). `client/Contexts`:
no screen = GAME; `AbstractContainerScreen` = SCREEN; `mezz.jei.gui.recipes.RecipesGui` = SCREEN +
RECIPE_SCREEN; none while a text field is focused (focused `EditBox`, any child `EditBox`, the creative
search box via an accessor). `GuidePlanner.plan` uses GAME binds only; `planScreen` the screen ones.

New mixins: `KeyboardHandlerInvoker` (`keyPress`), `MouseHandlerInvoker.onButton`,
`KeyMappingAccessor.key`, `CreativeModeInventoryScreenAccessor.searchBox`.

Default layout adds: scoped Y = astronomy mode, scoped B (hold while) = astronomy use, scoped X =
astronomy info; SCREEN tap RS = `key_press/key.jei.showRecipe`, hold RS 300 ms =
`key_press/key.jei.showUses`; RECIPE_SCREEN tap Y = `key_press/key.jei.recipeBack`. 25 binds.

Tests: 77 (was 52): 6 new engine tests (scoped Press once, scoped Y/B/X, face buttons normal outside a
scope, A still jumps), 10 `EngineSetTest` (screen binds silent in game, RS tap/hold, other inventory
buttons pass through, Y Back only in JEI recipes, text field = no context, button held across a context
change stays masked, scoped is game only, hard reset, contexts), 5 planner tests (hidden bindings and
LT Stop, astronomy entries, screen guide, level 0, in-game plan ignores screen binds), 4 config tests
(defaults compile with contexts, missing context = GAME, scoped screen bind rejected, v1 migration).
Dev-client smoke test (temporary `runClient` with Controlify and YACL on the runtime classpath and a
self-test that loaded the Controlify targets and `CreativeModeInventoryScreen`, read a KeyMapping's key
through the accessor, ran a screen key press through the `keyPress` invoker on the title screen, then
exited; reverted before the release build): all three `Controlify hook applied` lines, `Loaded 25
advanced controller binds (defaults)`, no mixin errors.

In-game test script (1.0.3; Controlify "Show in-game button guide" on, verbosity Reduced):
1. Log: `Loaded 25 advanced controller binds (defaults)`, the three `Controlify hook applied` lines.
2. Spyglass in the hotbar, hold D-up: spyglass up, **no continuous vibration** (one short pulse from
   the hold). Hold LT with a spyglass in hand: also no buzz. Block with a shield: its rumble still works.
3. Scoped by LT: guide right column shows `Stop [LT]`, no `Zoom [LT]`; release LT stops. Scoped by D-up:
   no LT line, `Hold: Stop Spyglass [D-up]`; LT does nothing.
4. Scoped, press X: no hand swap (chat says Spyglass Astronomy has nothing selected, or shows info in
   select mode). Y: no inventory, no World Tier; the scope overlay changes (draw mode), guide `[Y] Select
   mode`; again: select mode (`[Y] Normal view`, `[B] Select`, `[X] Info`); again: normal.
5. Draw mode, at night: aim at a star, hold B, look to another star, release: a constellation line.
   RT does the same. Select mode: B on a star selects it (action bar), X prints its info in chat.
6. A still jumps while scoped; RB/LB still zoom (and repeat when held); off-scope X swaps hands, Y opens
   the inventory, B rolls, all instantly.
7. Open the inventory, move the cursor onto an item, tap RS: JEI recipes for it. B: back to the
   inventory. Hold RS on an item: JEI uses. The bottom guide shows `Recipes [RS]` and `Hold: Uses [RS]`
   while the cursor is on an item. A/X/Y still pick up / take half / quick-move without delay.
8. In JEI's recipe screen: press Back once (Controlify cursor on), point at an ingredient, RS tap /
   hold: its recipes / uses; Y: back to the previous recipes; B: closes to the inventory.
9. Anvil (rename field focused), creative search: RS does nothing and typing is unaffected.
10. Advanced Binds Settings: each bind shows a Where option; screen binds' summaries start with
    `[screens]` / `[JEI recipes]`; "Rumble While Scoped" on brings the spyglass buzz back.

## 2026-09-30: 1.0.2, new D-pad layout and Controlify button guide hints

User's design: D-up tap = world map, hold = spyglass toggle (was the tap); D-down tap = crawl toggle,
hold = drop one item then keep dropping one at a time; D-left unchanged; Ok Zoomer bind removed. And
the advanced binds in Controlify's in-game button guide.

Checked first:

- Controlify 3.5.3 default layout: D-down = `controlify:drop` (`InGameInputHandler`: one item on
  press, then after 20 ticks one per tick). Our `profile-1.json` doesn't rebind D-down (it has
  `key.crawl` and the world map only in the radial menu). D-down now has a tap bind, so the engine
  holds it back and Controlify's drop never fires in game.
- `key.crawl` is Crawl 0.15.0's (`CrawlClient.key`, a `ToggleKeyMapping` on its `toggleCrawl`
  option). Controlify's `ToggleKeyMappingMixin` replaces the toggle condition under controller input
  with the one passed at registration, which only sneak/sprint have, so for a controller it's a hold
  key: the tap bind uses Toggle mode (hold the binding down until the next tap). Bits and Balance also
  has `key.bitsandbalance.crawl`; not used.
- Drop: vanilla handles `while (options.keyDrop.consumeClick()) player.drop(ctrl)`. Controlify's
  `fakePress` needs 4 pushes per press (states 0-3), too slow for 150 ms repeats, so the new
  `gameoverse_controller_plus:drop_one` action increments `keyDrop`'s `clickCount` (accessor mixin):
  one vanilla single-item drop per event, any interval.
- Ok Zoomer: still not installed; its bind is gone from the defaults. `Scoping`'s zoom-key check is
  inert without the mod (`KeyMapping.get` returns null) and kept.

Engine: `ActionMode.REPEAT` (press on fire, then `Kind.REPEAT` every `windowTicks` while the tie
button stays held; cleared on release, screen open and hard reset; acts as PRESS when fired on
release; SCOPED binds keep their own repeat path). `BindEntry` compiles REPEAT binds with Tap Window as
the interval. Driver: REPEAT events run as presses without rumble.

Button guide: Controlify 3.5's guide (`GuideInstanceImpl.update`, source read at the 3.5.3 tag) is
data-driven: `assets/<ns>/contextual/guide/in_game.json` rules (`for` binding, `where` left/right,
`if` fact predicate, `then` text), first matching rule per (binding, side) wins, packs stack
highest-priority first. The public API only adds facts (`ContextualDomainRegistry.inGame()
.registerContributor`). Rules can't carry live spell names or two glyphs, and can't remove
Controlify's Drop rule, so `GuideInstanceImplMixin`: `@ModifyVariable` at the STORE of the winning
rule list (the method's only `List` local) filters rules whose binding is bound to a single button
our binds override right now; `@Inject` TAIL appends our lines to `leftGuides`/`rightGuides`
(`PrecomputedLines.Builder` is public) with Controlify's own layout maths. Glyphs:
`Controlify.instance().inputFontMapper().getComponentFromInputs(controllerType namespace, [input])`
per button; its own multi-input join puts a `+` into the bitmap controller font, which has no `+`
glyph, so chords join single glyphs with a plain-font "+". Spell names:
`SpellHotbar.structuredSlots.other().get(n).option().id()` through `SpellTooltip.spellTranslationKey`.
What to show is `engine/GuidePlanner` (pure, 8 tests): scoped, layer held (200 ms delay), otherwise
summaries and per-action levels against Controlify's verbosity (see README).

Tests: 52 (6 new engine tests for REPEAT and the D-pad layout, 8 planner tests). Dev-client smoke
test (temporary `runClient` with Controlify and YACL on the runtime classpath, a self-test that
loaded `GuideInstanceImpl`, `InputBindingImpl` and `InputComponent`, then exited; reverted before
building the release jar): `Controlify hook applied: in-game button guide (GuideInstanceImpl.update)`
and `held binding`, no mixin errors, `Loaded 19 advanced controller binds (defaults)`.

In-game test script (1.0.2):
1. Log: `Controlify hook applied: in-game button guide` and `held binding`, then the input mask line
   once the controller is used; `Loaded 19 advanced controller binds (defaults)`.
2. Tap D-up: world map opens. Hold D-up ~0.3 s: spyglass view (rumble); RB/LB zoom; hold D-up again:
   spyglass off. Tapping D-up never toggles the spyglass.
3. Tap D-down: crawling; tap again: standing. Holding an item stack, hold D-down: one item drops at
   ~0.25 s, then one about every 0.15 s until released; release after a short press never drops.
4. D-left: tap cycles the hotbar row, hold picks the block (unchanged).
5. Controlify settings > the controller > "Show in-game button guide" on. With a spell weapon: left
   column has `[LB] Hold: Spells` (and `[RB] Hold: More spells` with over 4 spells), `[LB+RB] Skill
   Forest`, `[Back] Hold: Guide`, `[Y] Hold: World Tier`; right column `Hold: Drop [D-down]` while
   holding an item (Controlify's own Drop line gone), `Hold: Spyglass [D-up]` with a spyglass.
6. Hold LB: after a moment the list shows `[A] <spell name>`, `[X] ...` per filled slot and
   `[RB] Skill Forest`; Controlify's `[A] Jump` is hidden. Same for RB. Quick LB taps don't flash it.
7. Look through the spyglass: `[RB] Zoom in`, `[LB] Zoom out`. Crawl (tap D-down): with verbosity
   Minimal the guide still shows `[D-down] Stop Crawl`. Verbosity Full adds Map, Crawl, Hotbar row,
   Pick block.

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
