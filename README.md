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
  (Vanilla's spectator menu — press `1` or middle-click in spectator — can also teleport to players.)

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
- If the dead player is offline, they are revived at the shrine the next time they join.
- Shrines are protected from players (survival) and explosions, and the anchor never explodes or sets spawn.

## Commands

| Command | Who | Description |
|---|---|---|
| `/visit <player>` | dead spectators only | Teleport to a player (gone once you're revived) |
| `/hardcore dead` | everyone | List dead players |
| `/hardcore shrines` | everyone | Show the nearest Respawn Shrines and their distance |
| `/hardcore price` | everyone | Show the current buy-back price |
| `/hardcore price reset` | op | Reset the doubling price back to 5 |
| `/hardcore revive <player>` | op | Revive a dead player for free (at your position; doesn't raise the price) |
| `/hardcore shrine create` | op | Build a shrine where you are standing (e.g. for villages generated before the mod was installed, or at spawn) |
| `/hardcore shrine remove` | op | Unregister the nearest shrine within 8 blocks |

Creative-mode operators can also break a shrine's anchor to remove it.

## Configuration

`config/hardcoreserver.properties` (created on first launch; restart after editing):

| Key | Default | Description |
|---|---|---|
| `forceHardcore` | `true` | Force hardcore (hearts, locked Hard difficulty) |
| `disableNaturalRegen` | `true` | Force `natural_health_regeneration` off |
| `baseReviveCostDiamonds` | `5` | Price of the first buy-back; each later one doubles |
| `generateVillageShrines` | `true` | Build shrines in newly generated villages |

## Install
1. Run a **Fabric** server for Minecraft 26.3 (Fabric Loader 0.19+, Java 25) with **Fabric API** installed.
2. Put `hardcoreserver-fabric-<version>.jar` (from the [Releases](../../releases) page) into the server's `mods/` folder.
3. Start the server. Shrines only appear in villages generated **after** installing — use
   `/hardcore shrine create` for existing villages.

## Building
Requires Java 25 (Gradle itself must run on Java 25 for Fabric Loom).
```
./gradlew build
```
The jar is written to `build/libs/`. Pushing code (or a `v*` tag) builds the jar in GitHub Actions and publishes it as release `v<mod_version>` (from `gradle.properties`); bump `mod_version` for a new release.
