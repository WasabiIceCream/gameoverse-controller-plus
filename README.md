# Gameoverse Controller Plus

Client-only Fabric mod for Minecraft 26.1.2 that adds **hold**, **tap**, **multi-tap**, **chord**
and **modifier-layer** controller binds on top of [Controlify](https://github.com/isXander/Controlify)
3.5.x. Controlify binds one input per action, and upstream has wanted richer triggers since 2023
(issues #70, #543) without building them. On this server that left the spell hotbar with no
controller input at all and two D-pad buttons doing several things at once.

MIT licensed. Uses Controlify's public API plus two small mixins into its internals; no Controlify
code is copied (Controlify is LGPL-3.0-or-later).

## Default layout

Defaults live in code, so every player gets layout updates with the mod, until they edit their own
list in the config screen.

| Trigger | Action | Mode |
|---|---|---|
| hold LB + A / X / Y / B | spell slot 1 / 2 / 3 / 4 | hold while pressed |
| hold RB + A / X / Y / B | spell slot 5 / 6 / 7 / 8 | hold while pressed |
| LB + RB together (either order) | Skill Forest (`key.puffish_skills.open`) | press |
| hold Back 450 ms | Gameoverse Guide (`key.oracle_index.open`) | press |
| hold Y 400 ms | World Tier select (`key.apotheosis.open_world_tier_select`) | press |
| tap D-up | MapStitch world map (`mapstitch.key.open_world_map`) | press |
| hold D-up 250 ms | spyglass (`key.spyglass-improvements.use`) | toggle; only with a spyglass (1.0.8) |
| tap D-down | Crawl (`key.crawl`) | toggle; stays on while there is no room to stand (1.0.10) |
| hold D-down 250 ms | drop one item from the held stack, then one more every 150 ms while held | repeat |
| tap D-left | Hotbar Slot Cycling cycle left | press |
| hold D-left 250 ms | pick block | press |
| RB while scoped | scroll up one notch (zoom in), repeats after 300 ms every 100 ms | repeat |
| LB while scoped | scroll down one notch (zoom out), same repeat | repeat |
| Y while scoped | Spyglass Astronomy: next mode (normal, draw constellations, select) | press |
| X while scoped | Spyglass Astronomy: info on the selection (`/sga:info`); no hand swap | press |
| (RT while scoped, Controlify's own attack) | Spyglass Astronomy: hold to draw a constellation line / select | native |
| tap RS in an inventory or JEI's recipe screen | JEI recipes for the item under the cursor (`key.jei.showRecipe`) | press |
| hold RS 300 ms, same screens | JEI uses (`key.jei.showUses`) | press |
| Y in JEI's recipe screen | JEI back to the previous recipes (`key.jei.recipeBack`) | press |

"Scoped" means looking through a spyglass: vanilla's, or Spyglass Improvements' spyglass key (which
makes vanilla's `isScoping()` true), or holding Ok Zoomer's zoom key if that mod is installed. While
scoped, LB and RB only zoom: no hotbar change, no spell layers, no LB+RB chord. A shoulder already
held when scoping starts does nothing until it is pressed again; when scoping ends the repeats stop
at once and a still-held shoulder stays inert until released. Since 1.0.3 X and Y are Scoped too
(Spyglass Astronomy, below), so while scoped they don't swap hands or open the inventory or World Tier;
A still jumps and B still rolls (1.0.3 also had B held = draw; 1.0.4 dropped it, since the right thumb
is on the right stick aiming, and RT does the same).

### Spell hotbar glyphs (1.0.9)

With a controller in use, Spell Engine's spell hotbar labels each slot with the controller buttons that cast it
(LT for the use key, LB/RB + face button for spell slots) instead of keyboard keys. See the DEVLOG.

### Spyglass Astronomy while scoped (1.0.3)

Spyglass Astronomy 1.0.27 has no key bindings of its own. Its client tick polls vanilla keys while the
player is scoping: `key.pickItem` (middle mouse) going down cycles `editMode` (0 normal, 1 draw
constellations, 2 select), and `key.attack` held draws a line from the star nearest the crosshair to
the one nearest where you look when you let go (mode 1) or selects the nearest object (mode 2). Naming
and info are client commands (`/sga:name <name>`, `/sga:info`). Controlify's RT already drives
`key.attack` (key emulation), so RT could draw, but its pick-block binding picks directly and never
sets `key.pickItem`, so the mode couldn't be changed from a controller. The actions:

- `astronomy_mode`: holds `key.pickItem` down for 3 ticks (`setDown` only, no click, so nothing is
  picked; vanilla discards pick clicks while an item is in use anyway).
- `astronomy_use`: holds `key.attack` down while its button is held, only in modes 1 and 2 (in normal
  mode it does nothing). On release it lets go unless RT is held. Not in the defaults since 1.0.4 (RT
  already does this); still in the action list for a custom bind. Config files from 1.0.3 (version 2)
  that still hold its exact default "B while scoped" entry lose it on load; an edited one stays.
- `astronomy_info`: sends `sga:info` through `ClientPacketListener.sendCommand`, which Fabric's client
  command API runs locally (chat shows the info for the selected star, constellation or planet).

`editMode` is read by reflection (a public static int) for the guide lines and the gate. No mouse
events are needed: the draw line follows the view through the mod's own `MouseHandler.turnPlayer`
hook, which runs every frame whatever turns the camera.

### JEI in inventories (1.0.3)

Controlify drives container screens with its virtual cursor (`VirtualMouseBehaviour.CURSOR_SCROLL`):
A picks up, X takes half, Y quick-moves (drops the carried stack when outside), B closes, LB/RB
switch tabs, the D-pad snaps between slots, LS click holds Shift, Back toggles the cursor. The right
stick click has no screen action, so it carries the JEI binds. Over JEI's item list (not a slot) A and
X already click, which JEI reads as show recipes / show uses. Our profile's
`controlify_modded:key.brbe.recipeview`/`usageview` on LS/RS never work in screens: Controlify's key
emulation (`KeyMappingEmulationOutput`) is skipped whenever a screen is open, and JEI and BRBE match
screen key *events* anyway, not `isDown()`.

So `gameoverse_controller_plus:key_press/<KeyMapping name>` presses whatever key that mapping is bound
to, through Minecraft's private `KeyboardHandler.keyPress` (an `@Invoker`; `MouseHandler.onButton`
for a mouse-bound key), at the end of the client tick. Fabric's screen keyboard events fire inside it,
which is where JEI listens, and it reads the cursor position for the item under it, which Controlify's
virtual cursor keeps up to date. Controlify's keyboard-mode switch wraps the GLFW callback one level up,
so the press doesn't flip it to keyboard/mouse.

JEI's recipe screen isn't a container screen: Controlify navigates it by focus unless the cursor is on
for it (press Back once there; Controlify remembers it per screen). With the cursor, A/X click
ingredients (recipes/uses) and RS tap/hold work too. B still closes the screen back to the inventory;
Y goes back one step in JEI's recipe history (JEI's `recipeBack`, Backspace).

Controlify's own D-down binding (Drop Item, `controlify:drop`, from its default layout; our profile
doesn't rebind D-down) never fires in game: D-down has a tap bind, so it is held back like every
other advanced-bound button.

Unchanged: tapping LB/RB still moves the hotbar slot (on release, see below), tapping Back still
changes perspective, tapping Y still opens the inventory. A, X and B alone are never delayed.

Why these D-pad splits: our Controlify profile (`gameoverse-client-perf/config/controlify/profile-1.json`)
put Ok Zoomer zoom, Accessorify's spyglass and Spyglass Improvements' spyglass all on D-up (chat was
moved off it to the radial menu), and Hotbar Slot Cycling's cycle-left on D-left next to
Controlify's pick block. Every one of them fired together. Since 1.0.2 (user's design) D-up's tap
opens the world map and holding it toggles the spyglass (look at the stars hands-free, hold again to
stop); Accessorify's duplicate spyglass key and the profile's Ok Zoomer binding are no longer reachable
from D-up in game. D-down's tap toggles crawling and holding it drops items one at a time. On D-left,
the pack's hotbar-row cycling (used more often) is the tap and pick block the hold.

Crawl's `key.crawl` is a `ToggleKeyMapping` whose toggle option (`toggleCrawl`) only applies to
keyboard input: under controller input Controlify treats it as a hold key (it only passes a toggle
condition for sneak and sprint). So the tap bind uses Toggle mode, which holds the key down until the
next tap. Opening any screen releases it (vanilla releases every key when a screen opens, and the
engine ends toggles then), so crawling stops when you open your inventory. Bits and Balance has its
own crawl key (`key.bitsandbalance.crawl`), which isn't used here. Spell slot 9 has no default
controller input (LB+RB went to the Skill Forest instead).

"Spell slot N" is the N-th spell on Spell Engine's spell bar that is **not** on the use key. With
our Spell Engine config (`spellHotbarUseKey: true`) the first spell is cast with LT (use), so LB+A
casts the next one.

### Right-stick scrolling in the Guide and other book screens (1.0.5)

In screens on Controlify's virtual cursor, the right stick scrolls. Controlify adds the stick's
deflection to a pending scroll every controller tick and hands it out a fraction of a notch every
frame, which suits smooth scroll lists but not screens that read the wheel in whole notches:

- **Gameoverse Guide** (Oracle Index `WikiBaseScreen`, so page content, the category list and search
  results): its `ScrollWidget` moves `(int) amount * 12` px, and every fraction truncates to 0, so
  nothing scrolled.
- **Penchant's enchanting table** (1.0.6): its enchantment list's `ScrollbarComponent` moves
  `(int) -amount` entries, so it didn't scroll either (10 entries per second at full deflection).
- **BRBE's recipe book** (1.0.7; inventory, crafting table, furnaces, plus its brewing stand and
  smithing table books): BRBE takes the wheel in `MouseHandler.onScroll` and turns one page for any
  nonzero amount, and its recipe viewer steps tabs and pages the same way, so the book flipped a page
  every frame and kept going after release. Inventories reach the same `handleScroll` through
  Controlify's container processor (`CURSOR_SCROLL`). 6 pages/s at full deflection.
- **Field Guide** (page/variant), **Scholar** books and lecterns (page), **MapStitch world map** (zoom
  level), **Create Ponder** (scene), **Create value boards** (value) and **JEI's recipe screen** (page)
  act on each call's sign: a step on every frame, plus the frames the pending scroll takes to run out
  after the stick is released, so pages flew by and the map zoom jumped to its ends.

For those screens (matched by class or superclass name, in `client/StickScroll`) the mod skips
Controlify's accumulation (`VirtualMouseHandlerMixin`, `@Inject` at the head of
`VirtualMouseHandler.handleScroll`, `require = 0`) and scrolls in whole notches through
`MouseHandler.onScroll` at the cursor, the path of a mouse wheel: the first notch as soon as the
stick leaves a 0.15 deadzone (on top of Controlify's own), then a rate proportional to the deflection
past it (`engine/StickScroller`, unit tested), at full deflection 25 notches/s in the Guide (300 px/s),
4/s for page turns and Ponder, 5/s for the map zoom, 6/s in JEI's recipes and the recipe book, 8/s on value boards. It
reads Controlify's `vmouse_scroll_up`/`down` bindings, so a rebind in Controlify still applies. Every
other screen (Skill Tree, Skill Forest, Better Advancements, Cloth Config, chests and other
containers without a recipe book, the creative inventory, ...) keeps Controlify's smooth scrolling. The virtual cursor must be on in the screen (it is for all of these
in the pack's `controlify.json`; Back toggles it). Config "Stick Scrolling Fix" (on) turns it off.

## How it works

- **Trigger engine** (`engine/TriggerEngine`, pure Java, unit tested). Per controller, ticked on
  every Controlify state push (20 Hz). Only buttons that take part in a bind are held back from
  Controlify's normal bindings; everything else passes through with no delay.
  - Hold: held back while pressed; released early replays a one-tick tap to the normal bindings
    (or fires the button's Tap bind); reaching the time fires the hold action.
  - Tap: fires on release when the button also has a hold or multi-tap bind, otherwise on press.
    A button with a Tap bind never reaches its normal Controlify bindings in game.
  - Multi-tap: fires on the N-th press; fewer taps are replayed when the window closes; held
    longer than the window it becomes a normal held press.
  - Layer (modifier + button) and chord: while the modifier is held, its layer buttons fire the
    layer action instead of their own. The modifier's own action replays as a tap on release only
    if no layer button was used and it was held less than 300 ms (configurable).
  - A held-back button stays held back until physically released, so releasing LB before A never
    leaks a jump.
  - Scoped: the engine gets a per-tick "scoped" flag. While it is set, a button with a Scoped bind
    runs only that bind (on press, then auto-repeat) and is held back from everything else,
    including its layers and chords. Scoping starting mid-press cancels that press's pending
    tap/layer (layer actions already running continue until their own button is released).
  - Contexts (1.0.3, `engine/EngineSet`): each bind has one, In Game (default), Inventory Screens
    (container screens and JEI's recipe screen) or JEI Recipe Screen, and each context runs its own
    engine, active only while its context is: In Game with no screen open; the screen contexts in
    those screens only while no text field is focused (anvil name, creative or JEI search, any
    focused `EditBox`). Always on the current controller, in controller or mixed input mode. The
    engines' masks are merged, so a screen bind's button is held back from Controlify's GUI actions
    only in that screen, and a button held when the context changes stays held back until released.
    A screen opening drops the in-game engine's pending taps and toggles; hold-while actions keep
    going until their button is released. Leaving the world, switching to keyboard, disconnecting
    or saving the config releases everything.
- **Actions**: any Controlify binding id (`controlify:...`, or `controlify_modded:<KeyMapping name>`
  for other mods' keys, which Controlify generates automatically) run by `InputBinding.fakePress()`,
  or held by forcing the binding's state. `gameoverse_controller_plus:spell_slot_N` holds down the
  KeyMapping Spell Engine resolved for that slot (see below). `gameoverse_controller_plus:scroll_up`
  / `scroll_down` is one mouse wheel notch (see below). `gameoverse_controller_plus:drop_one` adds
  one click to vanilla's Drop key (`key.drop`, through a `KeyMapping.clickCount` accessor), so
  Minecraft's own key handling drops one item exactly as a press of Q does (never the whole stack).
  `astronomy_mode`/`astronomy_use`/`astronomy_info` and `key_press/<name>`: see the two 1.0.3
  sections above.
- **Repeat mode** (1.0.2): a press when the trigger fires, then another every Tap Window (the bind's
  `windowMs`) for as long as the trigger's button stays held; ends on release or when a screen opens.
  Follow-up presses don't rumble.
- **Scoped + Press** fires once (1.0.3); **Scoped + Repeat** repeats after Hold Time every Tap Window
  (the zoom). Before 1.0.3 a Scoped bind in Press mode repeated; config files from then (version 1)
  are migrated to Repeat on load.
- **Feedback**: a short rumble when a hold or multi-tap bind fires (toggle in the config).
- **Spyglass rumble** (1.0.3): the constant light buzz while looking through a spyglass was
  Controlify's own item-use rumble. `LocalPlayerMixin` (its `feature.rumble.useitem` package) starts a
  `ContinuousRumbleEffect` in `startUsingItem` per use animation; for SPYGLASS (and shields) it is a
  weak-motor pulse of 0.05-0.14 with no timeout, stopped only when the item use ends. Spyglass
  Improvements' key (our D-up hold) uses the real spyglass item, so the buzz lasted as long as the
  spyglass was up. Each client tick, while the player uses a SPYGLASS-animation item, we stop that one
  effect through Controlify's `UseItemEffectHolder` interface (implemented on `LocalPlayer`). Shield,
  bow, damage and every other rumble stay. Config "Rumble While Scoped" (off by default) turns it back
  on. None of our own rumbles repeat: they fire once per hold/multi-tap trigger, and Scoped binds never
  rumble.

## Controlify's in-game button guide

With Controlify's "Show in-game button guide" on, the guide also shows the advanced binds, picked
for the moment (`engine/GuidePlanner`, unit tested; labels and glyphs in `client/ButtonGuide`):

- **Scoped**: `[RB] Zoom in`, `[LB] Zoom out`, the Spyglass Astronomy buttons (`[Y] Draw mode` /
  `Select mode` / `Normal view`, the mode Y switches to; `[X] Info` in select mode), RT on the right by
  mode (`Hold: Draw [RT]` in draw mode, `Select [RT]` in select mode, nothing in normal mode), plus the
  other buttons' hints (B's roll included). Controlify's own lines for LT (its "Zoom" rule for a
  spyglass in hand) and RT (attack, which does nothing to a spyglass) are hidden, and so are X/Y's. When the spyglass
  is up because LT is held, `Stop [LT]` sits on the right; when it is up through the D-up toggle, LT
  does nothing (Spyglass Improvements keeps the use key "down" while its key is held), so there is no
  LT line and `Hold: Stop Spyglass [D-up]` says how to end it.
- **LB (or RB) held** for 200 ms (so a hotbar tap doesn't flash it): only that layer, each face button
  with the name of the spell it casts now (Spell Engine's spell bar; empty slots are skipped), and
  `[RB] Skill Forest` for the chord. Controlify's own A/X/Y/B entries hide meanwhile.
- **Otherwise**: `[LB] Hold: Spells` / `[RB] Hold: More spells` when that layer has a spell right now,
  then the tap/hold/chord binds whose action is worth a line at the guide's verbosity (Controlify's
  own setting):
  - Minimal: only things toggled on (`[D-down] Stop Crawl`, `[D-up] Hold: Stop Spyglass`).
  - Reduced (our profile's default): also the spell hints, `[LB+RB] Skill Forest`,
    `[Back] Hold: Guide`, `[Y] Hold: World Tier`, `Hold: Drop [D-down]` while holding an item, and
    `Hold: Spyglass [D-up]` while carrying a spyglass.
  - Full: every bind (`Map`, `Crawl`, `Hotbar row`, `Pick block` too).
  D-pad hints sit in the right column (next to Controlify's use/drop lines), the rest on the left.
- Controlify's own entry for a button whose tap we replaced (Drop on D-down) is hidden.
- **Inventory screens** (1.0.3): Controlify's container guide (bottom of the screen, "Show screen
  guides", on in our profile) is the same `GuideInstanceImpl` with domain `controlify:container`, so the
  same hook adds `Recipes [RS]` and `Hold: Uses [RS]` on the right while the cursor is on a slot with an
  item. JEI's recipe screen has no Controlify guide at all, so its Y Back isn't shown anywhere (it is
  in the README and the config screen only).

Labels come from our lang file (`gameoverse_controller_plus.guide.action.<action id with : as .>`),
falling back to the Controlify binding's name, so customized binds still get a line. "Hold: ",
"2x: " and "Stop " mark the trigger; chords show two glyphs joined by "+" (Controlify's input font
per controller type, built with its `InputFontMapper`, the same way its own glyphs are made).

Why a mixin and not Controlify's API: Controlify 3.5's guide is data-driven (`contextual/guide/*.json`
rules: one binding's glyph plus fixed text, first matching rule per binding wins, resource packs
stack), and the public API (`ContextualDomain.registerContributor`) can only add facts. A rule can't
show live spell names, two glyphs, or hide another mod's rule, and the order of our rules against
Controlify's own would depend on resource pack order. So `GuideInstanceImplMixin` hooks
`GuideInstanceImpl.update` twice, for the in-game domain (`controlify:in_game`) and since 1.0.3 the
container screen domain (`controlify:container`); other guides untouched:
a `@ModifyVariable` on the stored list of winning rules (removes the overridden ones) and an
`@Inject` at TAIL that appends our lines to its left/right `PrecomputedLines`. Both `require = 0`;
the mixin plugin logs `Controlify hook applied: in-game button guide` or a warning, and any
exception in our guide code is logged once and the guide falls back to Controlify's own entries.

### The two Controlify hooks (and the version pin)

Controlify's public events fire after bindings have read the new state, so nothing in its API can
stop a button's normal binding from firing. Two mixins do that:

1. `InputComponentMixin`: `@WrapOperation` on the `InputBinding.pushState(ControllerStateView)` call
   inside `InputComponent.pushState`. The first call of each push runs the engine on the raw state;
   every binding then gets a masked view (held-back buttons released, replayed taps pressed).
   `stateNow()` stays raw, so the virtual mouse and input-mode switching are unaffected.
2. `InputBindingImplMixin`: `@ModifyArg` on the `Float.valueOf` boxing of the analogue value in
   `InputBindingImpl.pushState`, forcing it to 1 while a hold-while or toggle action holds that
   binding. (A `@ModifyVariable` on the `analogue` local would be too late: it is already on the
   stack when the history push is called.)

Both use `require = 0`, and a mixin config plugin checks after transformation that each handler
really is called from `pushState`. If Controlify changes these methods the log shows a boxed
`Controlify hook NOT applied` error and the mod goes inactive (or hold-while falls back to short
presses) instead of crashing. On a healthy start the log has three `Controlify hook applied` lines (the
input mask one only once a controller is in use), and since 1.0.5 a fourth for stick scrolling.
`fabric.mod.json` pins `controlify` to `>=3.5.3 <3.6`.

**When Controlify updates**: recheck both targets (`javap -p -c` on `InputComponent.pushState` and
`InputBindingImpl.pushState`: one `InputBinding.pushState` call in the loop, one `Float.valueOf`
before `ResizableRingBuffer.push`), then widen the version range. Upstream's
`feature/input-pipeline-v2` branch would rewrite this area if it ever lands.

### Spell Engine slots

Spell Engine polls `KeyMapping.isDown()` for each slot's key (charged and channelled spells hold
it). The key it polls is the slot's `keybindings.spell_engine.spell_hotbar_N` only if that key is
bound to a keyboard key; unbound ones (all but slot 1 in this pack) defer to `key.hotbar.N`, and the
first spell goes on the use key. Controlify's auto-generated
`controlify_modded:keybindings.spell_engine.spell_hotbar_N` binding presses the unbound spell key,
which Spell Engine then ignores, so driving it does nothing for most slots. The spell-slot action
instead asks `SpellHotbar.INSTANCE.structuredSlots.other()` which KeyMapping casts the slot right
now and holds that one down (`setDown` only, no click, so a deferred `key.hotbar.N` never switches
the held item). Spell Engine is optional; without it the spell actions do nothing.

### Scroll steps

`scroll_up`/`scroll_down` call Minecraft's private `MouseHandler.onScroll(window, 0, ±1)` through an
`@Invoker` mixin, the method the GLFW scroll callback calls, so every mod's injection into it sees
the step exactly as a wheel notch (Controlify's own mouse hook sits one level up, on the GLFW
callback, so a synthetic step doesn't switch Controlify to keyboard/mouse mode). Spyglass
Improvements 1.5.13 injects at `ScrollWheelHandler.getNextScrollWheelSelection` inside it and, when
the player is scoping in first person, changes its zoom multiplier and cancels the hotbar change.
If nothing takes the step, the selected slot and flying speed are restored afterwards, so a step
never changes the held item.

## Config

`config/gameoverse_controller_plus.json`, edited in game through ModMenu or Controlify's
bindable "Advanced Binds Settings" action (unbound by default, can go in the radial menu). A YACL
screen: general settings (enabled, rumble on hold, Rumble While Scoped, Stick Scrolling Fix, layer tap time), then one
collapsible group per bind with where (In Game, Inventory Screens, JEI Recipe Screen), trigger
type, button, modifier/second button, hold time, tap count, tap window, action (dropdown of every
Controlify binding id plus this mod's actions, free text allowed) and mode; Add, Remove and Reset
to defaults.
While your list matches the defaults nothing is stored, so later default changes still reach you.

## Building

```
./gradlew build          # jar in build/libs/, runs the engine unit tests
```

Compiles against local jars in `reference-jars/` (not committed): copy
`controlify-3.5.3+mc26.1-universal.jar`, `yet_another_config_lib_v3-3.9.7+26.1-fabric.jar`,
`modmenu-18.0.1.jar` and `spell_engine-fabric-1.10.9+26.1.2.jar` from the Working instance's mods.

Install: client-only, into `fabric 26.1/automodpack/host-modpack/main/mods/` (server restart so
AutoModpack's manifest picks it up) and the Working test instance's `mods/`.

## Known limitations

- 50 ms tick resolution; the default times (hold 250-450 ms, tap window 250 ms, layer tap 300 ms)
  may need tuning by feel.
- LB/RB change hotbar slot on release instead of press, and not at all when held over 300 ms.
- Spell layers don't cast while sneaking: Spell Engine's `sneakingByPassSpellHotbar` is on, and RS is
  toggle sneak.
- Buttons only; triggers (LT/RT) and stick directions can't be used in advanced binds.
- Screen binds (1.0.3) are off in any screen with a focused text field, including an anvil (its name
  field has focus from the start). The JEI recipe screen needs Controlify's cursor turned on (Back) for
  the item-under-cursor binds. JEI's R/U keys must stay bound to a key or mouse button (they are, by
  default); an unbound one logs a warning once and does nothing.
- Spyglass Astronomy naming needs typing (`/sga:name <name>` in chat; Controlify's on-screen keyboard
  works there). Its mode and hints depend on reading its `editMode` field by reflection: if a future
  version renames it, the log says so once and Y/X still act but show no hints, and there is no RT
  line; RT still draws.
- No "press the buttons" capture widget in the config screen.
- Stick scrolling (1.0.5) is a fourth hook (`VirtualMouseHandler.handleScroll`); if it doesn't apply the
  log says `Controlify hook NOT applied: stick scrolling` and those screens get Controlify's own
  scrolling. Screens not in `StickScroll.RATES` are untouched; a new book-like mod screen that scrolls
  badly needs an entry there. The rates are fixed in code (no config slider).
- The button guide integration is a third hook into Controlify internals (`GuideInstanceImpl.update`,
  `PrecomputedLines`); recheck it with the other two when Controlify updates.
- Steam Input chords on top of these work but can double up.
- Ok Zoomer is not in the pack (neither mod set); 1.0.2 dropped its D-up bind. `Scoping` still
  counts its zoom key held as scoped: `KeyMapping.get("key.ok_zoomer.zoom")` is null without the mod,
  so the check is inert and was kept for the day it's installed (unverified; its toggle zoom mode
  wouldn't count). The profile's own `controlify_modded:key.ok_zoomer.zoom` entry is likewise inert.
