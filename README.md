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
| tap D-up | spyglass (`key.spyglass-improvements.use`) | toggle |
| hold D-up 250 ms | Ok Zoomer zoom (`key.ok_zoomer.zoom`) | hold while pressed |
| tap D-left | Hotbar Slot Cycling cycle left | press |
| hold D-left 250 ms | pick block | press |
| RB while scoped | scroll up one notch (zoom in), repeats after 300 ms every 100 ms | press |
| LB while scoped | scroll down one notch (zoom out), same repeat | press |

"Scoped" means looking through a spyglass: vanilla's, or Spyglass Improvements' spyglass key (which
makes vanilla's `isScoping()` true), or holding Ok Zoomer's zoom key if that mod is installed. While
scoped, LB and RB only zoom: no hotbar change, no spell layers, no LB+RB chord. A shoulder already
held when scoping starts does nothing until it is pressed again; when scoping ends the repeats stop
at once and a still-held shoulder stays inert until released.

Unchanged: tapping LB/RB still moves the hotbar slot (on release, see below), tapping Back still
changes perspective, tapping Y still opens the inventory. A, X and B alone are never delayed.

Why these D-pad splits: our Controlify profile (`gameoverse-client-perf/config/controlify/profile-1.json`)
put Ok Zoomer zoom, Accessorify's spyglass and Spyglass Improvements' spyglass all on D-up (chat was
moved off it to the radial menu), and Hotbar Slot Cycling's cycle-left on D-left next to
Controlify's pick block. Every one of them fired together. Now D-up's tap toggles the spyglass (look
at the stars hands-free, tap again to stop) and holding it zooms for as long as it's held; Accessorify's
duplicate spyglass key is no longer reachable from D-up in game. On D-left, the pack's hotbar-row
cycling (used more often) is the tap and pick block the hold. Spell slot 9 has no default
controller input (LB+RB went to the Skill Forest instead).

"Spell slot N" is the N-th spell on Spell Engine's spell bar that is **not** on the use key. With
our Spell Engine config (`spellHotbarUseKey: true`) the first spell is cast with LT (use), so LB+A
casts the next one.

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
  - Active only in game with no screen open, on the current controller, in controller or mixed
    input mode. A screen opening drops pending taps and toggles; hold-while actions keep going
    until their button is released. Leaving the world, switching to keyboard, disconnecting or
    saving the config releases everything.
- **Actions**: any Controlify binding id (`controlify:...`, or `controlify_modded:<KeyMapping name>`
  for other mods' keys, which Controlify generates automatically) run by `InputBinding.fakePress()`,
  or held by forcing the binding's state. `gameoverse_controller_plus:spell_slot_N` holds down the
  KeyMapping Spell Engine resolved for that slot (see below). `gameoverse_controller_plus:scroll_up`
  / `scroll_down` is one mouse wheel notch (see below).
- **Feedback**: a short rumble when a hold or multi-tap bind fires (toggle in the config).

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
presses) instead of crashing. On a healthy start the log has two `Controlify hook applied` lines.
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
screen: general settings, then one collapsible group per bind with trigger type, button,
modifier/second button, hold time, tap count, tap window, action (dropdown of every Controlify
binding id plus the spell slots, free text allowed) and mode; Add, Remove and Reset to defaults.
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
- No on-screen hint showing what a layer does yet, and no "press the buttons" capture widget.
- Steam Input chords on top of these work but can double up.
- Ok Zoomer is not in the pack (neither mod set) as of 1.0.1, so its detection (zoom key held) and
  its scroll handling are unverified; the D-up hold bind to `key.ok_zoomer.zoom` does nothing
  until it is installed. Ok Zoomer's toggle zoom mode wouldn't count as scoped.
