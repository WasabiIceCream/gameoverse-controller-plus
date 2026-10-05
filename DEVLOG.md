# DEVLOG

## 2026-10-02: 1.0.10, crawl toggle stays on while there's no room to stand

User report: toggle crawl, crawl into a one-block gap, tap D-down again while inside: the toggle desynced, like the
spyglass toggle before 1.0.8. Crawl 0.15.0 (decompiled) only requests crawling while `key.crawl` is down
(`LocalPlayerMixin.beforeSuperAiStep`); released under a low ceiling, vanilla forces the swimming pose and Crawl's
`PlayerMixin` turns that into crawling, until the player gets out and stands up on their own. So the tap turned the
toggle off with the player still crawling, the next tap turned it on with no visible change, and the player kept
crawling after getting out. Now `crawlGate` (after `spyglassGate` in `ControllerPlus`) refuses a crawl toggle's STOP
while the player can't stand or crouch (vanilla's own `canPlayerFitWithinBlocksAndEntitiesWhen` test: `noCollision`
on the pose's box deflated by 1.0E-7), with "No room to stand up"; new engine call `keepToggle` re-arms it, so the next
tap out in the open stops it. Opening a screen still ends the toggle (the game keeps the player crawling until there is
room, then stands them up, so nothing is left out of step). New engine test `keptToggleStaysOnUntilTheNextTap`; 94
tests pass.

## 2026-10-01: 1.0.9, controller glyphs on Spell Engine's spell hotbar

User request: Spell Engine's spell hotbar labels each slot with its keyboard key (mouse icon for use, "1"
for the hotbar-1 fallback when its own spell keys are unbound), even with a controller. Spell Engine 1.10.9
(decompiled): `HudRenderHelper.SpellHotBarWidget.KeyBindingViewModel.from(KeyMapping)` builds the label (a
`Drawable.Component` texture for mouse buttons, else acronym text); `SpellHotBarWidget.drawKeybinding` draws a
key cap with that text. `SpellHotbar.Slot.modifier` is always null in this version.

`client.SpellHudGlyphs` + two mixins (`mixin.spell`, applied only with Spell Engine loaded, confirmed in the log):
while Controlify is in controller mode, `from` returns a marker label for the key's controller glyph and
`drawKeybinding` draws that glyph component instead of a key cap. The glyph: the use key gets Controlify's use
binding (LT); spell slot n's key (identity with `SpellSlots.resolve(n)`) gets our `spell_slot_n` bind's buttons
(LB+A by default, "modifier+button" for layers, both buttons for chords); any other key its
`controlify_modded:` binding if bound, else Spell Engine's own label.

First in-game look (user): with four spells the LB+A style combos overlapped each other (a slot is 20 px, a
combo ~50). Layer slots now show only their face button, and the layer's modifier (LB/RB) is drawn once,
centred above each run of consecutive slots on the same layer (drawn by the run's last slot; Spell Engine
draws the slots left to right). Non-layer binds still show their full combo.

## 2026-10-01: 1.0.8, the D-up spyglass toggle needs a spyglass

User report: holding D-up still toggled Spyglass mode with no spyglass. Spyglass Improvements 1.5.13
(decompiled): its key raises a spyglass from the hands, the inventory or a Trinkets slot, or with none in
creative or with its `userForceSpyglass` setting; otherwise it does nothing, but its `MinecraftClientMixin`
makes vanilla's use-key check true while its key is down, so our toggle left "use" held (an item in use kept
going) and the next D-up hold only turned that off.

Fix: `compat.SpyglassImprovements.canScope` mirrors those checks (Trinkets and the setting by reflection);
`ControllerPlus.spyglassGate` drops the toggle's START when it fails (overlay "You don't have a spyglass") and
releases a toggle that's on without scoping for 10 ticks (not counting ticks with another item in use, since
the spyglass comes up after it), e.g. the spyglass was dropped or swapped away. Engine: `dropToggle` /
`isToggledOn` (TriggerEngine and EngineSet), with a test that the next hold toggles on again.

## 2026-10-01: 1.0.7, right-stick scrolling in BRBE's recipe book

User request: the right stick needs the same scrolling fix in BRBE's recipe book. Findings (BRBE
`backport-26.1.2-v2` source, Controlify 3.5.3 jar):

- Controlify's `AbstractContainerScreenProcessor` returns `VirtualMouseBehaviour.CURSOR_SCROLL`, so in
  inventories `handleControllerInput` calls `handleScroll` (our hook) and `updateMouse` hands the pending
  scroll out per frame through `MouseHandler.onScroll`.
- BRBE's `MouseScrollHandler` (HEAD of `MouseHandler.onScroll`) calls `RecipeBookGesture.claimScroll`: over
  the book panel or its tab strip it sets `queuedScroll = amount > 0 ? -1 : 1` (one page) or RBIP's
  `rbip$scrollPages` steps one tab page, for any nonzero amount. `RecipeViewerOverlay.mouseScrolled`
  (`mouseScrolledTabs`, its pages) also steps per call on the sign. Outside vanilla recipe-book screens
  the same hook queues one page unconditionally for BRBE's brewing stand and smithing books.
- So the book turned a page every frame while the stick was pushed, and for the frames the pending
  scroll took to decay after release.

Fix: `StickScroll.RATES` gets `AbstractRecipeBookScreen` (inventory, crafting table, furnace, blast
furnace, smoker), `BrewingStandScreen` and `SmithingScreen` at 6 notches/s (JEI recipe screen's rate).
`RATES` moved to `Map.ofEntries` (11 entries). Other containers (chests, the creative inventory, backpacks)
keep Controlify's fractional scrolling. Side effect, wanted: JEI's ingredient overlay in those screens
now pages one notch at a time too (it paged per frame, noted under 1.0.5).

Tests: 92, unchanged (rate table only). Not tested in game yet.

In-game test (controller, inventory open with the recipe book shown):
1. Cursor over the recipe book: a light right-stick push turns one page; holding at full tilt about 6
   per second; release stops at once.
2. Cursor over the book's tab strip with more tabs than fit: tab pages step one at a time.
3. Crafting table, furnace, brewing stand, smithing table books: same.
4. JEI's ingredient list on the right of the inventory: pages one at a time.
5. A chest or backpack: scrolling unchanged.

## 2026-09-30: 1.0.6, right-stick scrolling in Penchant's enchanting table

User report: the right stick didn't scroll the enchanting table's enchantment list. Penchant 0.5.6's
`PenchantmentScreen.mouseScrolled` hands the wheel to `ScrollbarComponent.mouseScrolled`, which calls
`addPosition((int) -amount)`: Controlify's fractional per-frame scroll truncates to 0. Added the screen to
`StickScroll.RATES` at 10 notches/s (one entry per notch). The user also once saw the list scroll wildly
while moving the cursor with the left stick and couldn't reproduce it; nothing in this path explains it.

## 2026-09-30: 1.0.5, right-stick scrolling in the Guide and book screens

User report (1.0.4 in game): in the Gameoverse Guide (Oracle Index) the virtual cursor moves and clicks,
but the right stick scrolls neither a long page nor the category list.

Findings (Controlify 3.5.3 source at the tag, Oracle Index 2.0.0-xplat, the other mods' jars, Minecraft
26.1.2):

- `rearth.oracle.ui.OracleScreen` is in `virtual_mouse_screens` (Working instance and the pack default
  `host-modpack/main/config/controlify/controlify.json`), and it has no Controlify screen processor, so
  the vmouse behaviour is DEFAULT and `VirtualMouseHandler.handleScroll` runs: `scrollY +=
  vmouse_scroll_up - vmouse_scroll_down` analogue (right stick up/down in Controlify's default binds; our
  profile doesn't rebind them) every controller tick. `updateMouse` (every frame) then sends
  `scrollY * realtimeDeltaTicks` through `MouseHandler.onScroll` and subtracts it: well under one notch
  per frame at any normal frame rate. The list isn't the gap.
- Oracle Index: `WikiBaseScreen.mouseScrolled` passes the amount to its root widgets'
  `handleMouseScroll`; `ScrollWidget.handleMouseScroll` checks `isInBounds` at the cursor, then
  `targetScrollOffset += -(int) amount * scrollSpeed (12)`. The `d2i` truncates every fraction to 0.
  The content pane and the sidebar are both `ScrollWidget`s. No errors in the client log.
- Other screens in the list: Field Guide (`BookScreen` subclasses: category/journal page per call,
  entry variant cycle per call, `VariantOverviewWidget` page per call), Scholar `SpreadBookScreen` (page
  per call), MapStitch `WorldMapScreen` (`zoomLevel += (int) signum(amount)`, -2..1), Create `PonderUI`
  (`scroll(boolean)` per call), `ValueSettingsScreen` (`signum` value step), JEI `RecipesGui` (next/prev
  page per call when not over a scroll area) all step on every frame's fraction, and keep stepping for
  the frames the pending scroll takes to decay after release. Fine with fractions: Skill Tree
  (`com.specialities` SkillsScreen, clamped double), Skill Forest (puffish, `pow(2, amount/4)` zoom),
  Better Advancements (pan by amount * 16; zoom needs Ctrl), Cloth Config, Apotheosis World Tier screens
  (no override), Haunted Harvest carving (no override). Lavender isn't in the pack.

Fix: `VirtualMouseHandlerMixin` (`@Inject` HEAD cancellable on `handleScroll(ControllerEntity)`, a
public Controlify method, `require = 0`, plugin logs `Controlify hook applied: stick scrolling`). For a
screen whose class or a superclass is in `StickScroll.RATES` it cancels Controlify's accumulation and
`StickScroller` (pure, 8 tests) turns the same bindings' deflection into whole notches: one at once when
the stick leaves a 0.15 deadzone, then (deflection past the deadzone, scaled 0-1) x max rate per second,
max rate 25/s for Oracle's `WikiBaseScreen`, 4/s Field Guide, Scholar and Ponder, 5/s the world map,
6/s JEI recipes, 8/s value boards. Each notch is its own `MouseHandler.onScroll(window, 0, +-1)` call
through our existing invoker (sign-only screens count calls), stopping if the screen changes. Direction
change or release restarts; a screen change resets. Config `stickScroll` (on), "Stick Scrolling Fix"
in the general category. No config version bump (a missing field loads as the default, true).

Not changed: Controlify's scrolling everywhere else, container screens (their processor), the virtual
mouse screen list. JEI's ingredient overlay in inventories (`CURSOR_SCROLL`) turns a page per frame the
same way, not reported, left alone.

Tests: 92 (was 84), `StickScrollerTest`. Not smoke-tested in a dev client this time (a plain HEAD
inject on a public method whose signature was checked with `javap`); the log line confirms it.

In-game test script (1.0.5; controller, virtual cursor on):
1. Log: `Controlify hook applied: stick scrolling (VirtualMouseHandler.handleScroll)` plus the other
   three, `Loaded 24 advanced controller binds (defaults)`.
2. Hold Back: Guide opens. Cursor over a long page: right stick down/up scrolls it, faster the further
   it is pushed; a light flick moves one step. Cursor over the left category list: it scrolls instead.
   Search screen results scroll too.
3. Field Guide / a Scholar book (or lectern): a push turns one page, holding turns about 4 per second at
   full tilt; releasing stops at once (no extra pages).
4. World map (tap D-up): right stick zooms one level at a time instead of jumping to the ends; left
   stick/cursor and dragging unchanged.
5. JEI recipe screen with the cursor: right stick pages through recipes one at a time.
6. Skill Tree, Skill Forest, Better Advancements, Cloth Config: scroll/zoom as before (Controlify's own).
7. Advanced Binds Settings: "Stick Scrolling Fix" off, Save: the Guide stops scrolling again (Controlify's
   own behaviour), back on restores it.

## 2026-09-30: 1.0.4, scoped B back to roll, RT hints for Spyglass Astronomy

User feedback (1.0.3 not yet tested in game otherwise): holding B to draw constellations while scoped is
awkward, the right thumb also has to move the right stick to aim. RT (Controlify's attack, which drives
vanilla's `key.attack`, which Spyglass Astronomy polls) already draws and selects.

- Defaults: the scoped B `astronomy_use` hold-while bind is gone (24 binds). B has no Scoped bind now,
  so the engine no longer captures it while scoped: it rolls without delay. A user's own scoped B bind
  still captures it. `astronomy_use` stays an available action.
- Config version 3: a customized version-2 list holding exactly the old entry (`Defaults.legacyScopedB()`,
  compared with `BindEntry.equals`: button, times, action, mode, context, enabled) loses it; if the list
  then equals the defaults it goes back to `customized: false`. Edited copies (other mode, times, ...)
  stay. Same approach as 1.0.3's v1 -> v2 migration (in memory on load; written on the next save).
- Guide: new `Form.SCOPED_ATTACK` on the right with the attack binding's own glyph (like LT's
  `STOP_SCOPE`), from `Context.astronomyMode()` (Spyglass Astronomy's `editMode`): mode 1
  `Hold: Draw [RT]`, mode 2 `Select [RT]`, mode 0 or mod missing nothing. Controlify's own attack line
  stays hidden while scoped (it would say Attack/Mine). B is no longer in the scoped overridden set, so
  Controlify's roll line shows. `astronomy.use.1` text is now "Hold: Draw".

Tests: 84 (was 77): removed the two B hold-while engine tests, added B rolls unmasked while scoped and a
custom scoped B still captures (engine), four planner tests (RT per mode, order above LT's Stop, nothing
in normal mode/missing mod/outside a scope, B keeps Controlify's line), three config tests (legacy B
removed from a customized list, defaults + legacy B back to defaults, edited B kept and v3 untouched).

In-game test script (1.0.4; also covers the untested 1.0.3 steps above, skip its step 5 B part):
1. Log: `Loaded 24 advanced controller binds (defaults)`, the three `Controlify hook applied` lines.
2. Scope (D-up hold or LT). Normal mode: no RT line, no Attack line; B rolls at once.
3. Y to draw mode: guide right column `Hold: Draw [RT]`; hold RT on a star, aim with the right stick,
   release on another star: a line. B still rolls.
4. Y to select mode: `Select [RT]` and `[X] Info`; RT on a star selects it, X prints the info.
5. Y back to normal: RT line gone.

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

## 1.0.11 (2026-10-05): hold D-right = boots toggle

Apotheosis 0.5.0 (upstream 9.1.0) adds boots affixes (Unhurried, Surefooted, Steadfast) that switch movement speed and
step height bonuses off with a key, `key.apotheosis.toggle_attribute_bonuses` (; by default in our port: upstream's
Ctrl+K collided with the Skill Forest's K, and Controlify's emulated presses can't hold Ctrl). Hold D-right 250 ms
presses it; tapping D-right still opens Controlify's radial menu (on release now, like the other D-pad splits). Not on
B: B is the combat roll, and A/X/B are never delayed. Config version 4 adds the bind to customized lists that have no
Hold bind on D-right yet (`ConfigTest`).
