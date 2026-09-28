# Hardcore Server (Fabric, Minecraft 26.3)

A **server-side only** Fabric mod for Minecraft **26.3**. Install it on the server; players join with a
plain **vanilla** client (no mods, no Fabric needed). Everything is built from vanilla blocks, entities and GUIs.

## Features

### 1. Forced hardcore
- The world always reports itself as hardcore: players see hardcore hearts and the hardcore death screen,
  and difficulty is locked to **Hard**.
- When a player dies they respawn in **spectator mode** and stay there until someone buys them back.
- Dead spectators can teleport to any online player with **`/visit <player>`** (works across dimensions).
  The command only exists while you are dead and spectating; as soon as you are bought back it disappears.
  Dead players are told about `/visit` in chat when they die, and if they try `/tp` or `/teleport`
  they get a clickable hint pointing to `/visit` instead of "Unknown command".
  (Vanilla's spectator menu — press `1` or middle-click in spectator — can also teleport to players.)
- **Follow camera:** fly to where you want the camera (within 64 blocks), look at the player, and run
  **`/follow <player>`**. The camera locks at that offset and angle and moves with them, so you always see them from
  the same direction. **`/follow <player> rotate`** also turns with their body, so you always see the same *side of
  them* (e.g. their left). Sneak or `/unfollow` to stop. Uses the vanilla camera (like `/spectate`), so no client
  mod is needed. Only available while dead.

### 2. No natural regeneration
- The `natural_health_regeneration` gamerule is forced off, so a full food bar no longer heals you.
- Food only fills your hunger bar; it never heals. Health only comes back from things like golden apples,
  Regeneration potions/beacons, or being bought back at a Respawn Shrine.

### 3. Respawn Shrines — buy back players with diamonds
- Every newly generated **village** gets a **Respawn Shrine** near its center: a charged Respawn Anchor on a
  polished blackstone platform with soul lanterns and a floating "Respawn Shrine" label.
- **Right-click the anchor** to open a chest-style menu showing the heads of all dead players.
  Click a head to pay diamonds from your inventory — the dead player is brought back to life at the shrine in
  survival with full health and food.
- **The price doubles with every buy-back** (server-wide): the 1st costs **5** diamonds, the 2nd **10**, then
  **20**, **40**, **80**, **160**, ... The current price is shown in the shrine menu and with `/hardcore price`.
  An optional cap (`maxReviveCostDiamonds`, or `/hardcore price cap <n>`) stops it from growing past a set amount.
- If the dead player is offline, they are revived at the shrine the next time they join.
- Shrines are protected from players (survival) and explosions, and the anchor never explodes or sets spawn.

### 4. Fortress Compass
A craftable compass that points toward a Nether fortress — but **never the closest one** (it always picks the
second-closest). It only works in the Nether; anywhere else the needle spins.

Recipe (crafting table):

```
[Nether Bricks] [Gold Block]    [Nether Bricks]
[Blaze Rod]     [Compass]       [Blaze Rod]
[Nether Bricks] [Diamond Block] [Nether Bricks]
```

It's a vanilla compass with custom data, driven by the vanilla lodestone-compass mechanic, so unmodded clients
see and use it normally. It can be switched off at any time (`/hardcore fortresscompass disable`, or
`fortressCompassEnabled=false` in the config — edits are picked up within a few seconds). While disabled it can't
be crafted, and compasses that already exist stop pointing (their tooltip says "DISABLED by the server");
re-enabling brings them back to life.

## Commands

| Command | Who | Description |
|---|---|---|
| `/visit <player>` | dead spectators only | Teleport to a player (gone once you're revived) |
| `/follow <player> [rotate]` | dead spectators only | Lock a follow camera at your current offset and angle |
| `/unfollow` | dead spectators only | Stop the follow camera (sneaking also works) |
| `/hardcore dead` | everyone | List dead players |
| `/hardcore shrines` | everyone | Show the nearest Respawn Shrines and their distance |
| `/hardcore price` | everyone | Show the current buy-back price |
| `/hardcore price reset` | op | Reset the doubling price back to 5 |
| `/hardcore price cap <n\|off>` | op | Cap the buy-back price at `n` diamonds, or remove the cap (saved to the config) |
| `/hardcore revive <player>` | op | Revive a dead player for free (at your position; doesn't raise the price) |
| `/hardcore fortresscompass` | everyone | Show whether the Fortress Compass is enabled |
| `/hardcore fortresscompass enable\|disable` | op | Turn the Fortress Compass on/off (saved to the config) |
| `/hardcore fortresscompass give` | op | Give yourself a Fortress Compass |
| `/hardcore shrine create` | op | Build a shrine where you are standing (e.g. for villages generated before the mod was installed, or at spawn) |
| `/hardcore shrine remove` | op | Unregister the nearest shrine within 8 blocks |

Creative-mode operators can also break a shrine's anchor to remove it.

## Configuration

`config/hardcoreserver.properties` (created on first launch; changes are picked up within a few seconds, except `forceHardcore` which needs a restart):

| Key | Default | Description |
|---|---|---|
| `forceHardcore` | `true` | Force hardcore (hearts, locked Hard difficulty) |
| `disableNaturalRegen` | `true` | Force `natural_health_regeneration` off |
| `baseReviveCostDiamonds` | `5` | Price of the first buy-back; each later one doubles |
| `maxReviveCostDiamonds` | `0` | Cap on the buy-back price (`0` = no cap) |
| `generateVillageShrines` | `true` | Build shrines in newly generated villages |
| `fortressCompassEnabled` | `true` | Fortress Compass craftable and working |

## Install
1. Run a **Fabric** server for Minecraft 26.3 (Fabric Loader 0.19+, Java 25) with **Fabric API** installed.
2. Put `hardcoreserver-fabric-<version>.jar` (from the [Releases](../../releases) page) into the server's `mods/` folder.
3. Start the server. Shrines only appear in villages generated **after** installing — use
   `/hardcore shrine create` for existing villages.

## Ideas not built yet
See [`docs/ideas/`](docs/ideas/) — e.g. [dead player bodies](docs/ideas/dead-player-bodies.md).

## Building
Requires Java 25 (Gradle itself must run on Java 25 for Fabric Loom).
```
./gradlew build
```
The jar is written to `build/libs/`. Pushing code (or a `v*` tag) builds the jar in GitHub Actions and publishes it as release `v<mod_version>` (from `gradle.properties`); bump `mod_version` for a new release.
