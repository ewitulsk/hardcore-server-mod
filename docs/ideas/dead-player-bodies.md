# Idea: dead player bodies (not built yet)

Brainstorm notes, 2026-09-28. **Hard rule: players must never need to install mods.**
Everything below is checked against the Minecraft 26.3 code unless marked *untested*.

## 1. A lying-down body — possible, no resource pack needed

Minecraft 26.x has a vanilla **Mannequin** entity (`minecraft:mannequin`, class
`net.minecraft.world.entity.decoration.Mannequin`). It's a player-shaped figure that unmodded
clients render. Relevant 26.3 fields:

| Field | Use |
|---|---|
| `profile` | The dead player's skin, so it looks exactly like them |
| `pose` | `standing`, `crouching`, `swimming`, `fall_flying`, or **`sleeping`** (lies flat) |
| `immovable` | Can't be pushed around |
| `description` / `hide_description` | Text under it, e.g. "Steve (dead)" |

On death the server could spawn a Mannequin with the player's skin, in the sleeping pose, where
they died. It could also wear their armor or hold their items.

## 2. Making it "mineable" — possible, with a trade-off

It's an entity, not a block, so there are no vanilla mining cracks on it. Options:

- **Hit it to break it.** Catch punches (Fabric `AttackEntityCallback`) and count hits, e.g. 5
  punches or fewer with a pickaxe or axe. Sound and particles on each hit; the last hit harvests
  the body. Simple.
- **A real "grave" block under it** (slab, soul soil, ...). Mining the block uses the normal crack
  animation and timing, and breaking it collects the body. Feels most like real mining, but
  you're really mining the grave.
- Either way the mod decides who can take it: anyone, teammates only, or only after X minutes.
- The sleeping hitbox is thin, so it may be fiddly to punch. That's another point for the grave block.

## 3. An item that *is* the body — this is where the limit is

Clients draw item icons only from models they already have. Vanilla 26.3 has no item renderer
for a full player body; the closest is the skinned **player head** renderer
(`PlayerHeadSpecialRenderer`). So:

- **No resource pack:** the item looks like something vanilla, e.g. their player head named
  "Steve's Body" with lore. It still *acts* like a body: right-click places the lying Mannequin
  back down.
- **With a server resource pack** (the server pushes it and players click "accept"; not a mod):
  a composite item model with their real skinned head plus a lying-body model. The body part uses
  a generic texture, because item icons can't apply an arbitrary player's skin to a full body.
- **Carrying the body** (*untested*): while someone holds the body item, a sleeping Mannequin
  rides on their shoulders (vanilla lets entities ride players), so others see the corpse being
  carried.

## Gameplay ideas it unlocks

- Carry the body to a Respawn Shrine to revive them cheaper, or make it *required* along with diamonds.
- Bodies rot: after a real-time limit the Mannequin disappears, and the revive gets more expensive
  or impossible.
- The body holds their loot: the dead player's items go into the body instead of scattering, and
  you get them by harvesting it.
- When someone is revived, their body disappears wherever it is.

## Risks / things to design for

- Bodies in unloaded chunks can't be found or cleaned up until someone goes back.
- Track bodies in saved data so the same person can't end up with two.
- Remove bodies when players are revived.

## Open questions

- Visual-only body, body that holds loot, or carry-to-shrine mechanic?
- Is a server resource pack acceptable, or must it stay 100% pack-free?
