# AbilityCombat

> README available in Korean: [README_ko.md](README_ko.md)

AbilityCombat is a Bukkit/Spigot(Paper) plugin that provides an ability-based PvP battle mode inspired by _Ability War_.

## What is this project?

- Minecraft plugin written in Java 21
- Uses Paper API 1.21.8
- Provides a full gameplay loop:
  - Game start/stop flow
  - Player ability assignment and reroll flow
  - Countdown and invincibility/gametime phases
  - Dynamic world border shrinking via configured phases
  - Death, respawn, spectator, score, and visual/effect systems

## Project Status Snapshot

- Plugin entry: `com.abilitycombat.AbilityCombat`
- Main command: `/aw`
- Loaded abilities: defined in `src/main/resources/abilities.yml`
- Registered ability implementations: class-based registration in `AbilityCombat#registerAbilities()`
- Map settings: `src/main/resources/maps.yml` (managed by `MapManager`)

## Prerequisites

- Java Development Kit (JDK) 21
- Paper server 1.21.8
- Maven

## Build

```bash
mvn clean package
```

The output jar is created under `target/` (the project includes shading config).

## Install

1. Build with Maven.
2. Copy the generated jar from `target/` into your server `plugins/` folder.
3. Restart or reload your server.
4. Start the game with `/aw start`.

## Commands

- ` /aw info`
  - Show your current ability information.
- ` /aw abilities` or `/aw ability`
  - Show ability list / open ability list viewer (player only)
- ` /aw start`
  - Start the game (admin only)
- ` /aw stop`
  - Stop the game (admin only)
- ` /aw debug`
  - Open ability debug GUI (admin only)
- ` /aw toolkit`
  - Open starter kit setup GUI (admin only)
- ` /aw config`
  - Open game config/map/map related GUI (admin only)
- ` /aw config reload`
  - Reload config and ability registry (admin only)
- ` /aw config setspawn`
  - Set start spawn location interactively (admin only)
- ` /aw test <count>`
  - Run ability draw test tool (admin only)

Permission required for admin commands: `abilitycombat.admin` (or operator).

> Note: `plugin.yml` includes `/aw` usage metadata, but runtime command set is defined in `AbilityCombatCommand`.

## Core Config (`src/main/resources/config.yml`)

```yaml
game:
  invincibility-seconds: 70
  duration-seconds: 720
  allow-debug-during-game: false

ability:
  selection-seconds: 50
  reroll-count: 1

world-border:
  initial-radius: 200
  shrink-seconds: 3
  phases:
    - time: 60
      radius: 200
    - time: 60
      radius: 150
    - time: 60
      radius: 100
    - time: 60
      radius: 20
    - time: 60
      radius: 3

spectator:
  hide-from-alive: true
map-restore:
  enabled: true
mob-spawn:
  block-natural: true
durability:
  infinite: true
combat:
  attack-cooldown: true
crafting:
  enabled: true
lobby:
  allow-block-break: true
  allow-block-place: true
  invincible: false
  location:
    world: ""
    x: 0
    y: 0
    z: 0
    yaw: 0
    pitch: 0
visual-effects:
  enabled: true
  min-interval-ticks: 1
  max-distance: 64
```

## Architecture at a Glance

- `ability`: ability registration, metadata, handlers, and 50+ ability implementations.
- `game`: game lifecycle, players/participants, world border phases, selection timers, participant state.
- `gui`: debug/info/config/map/ability UI screens.
- `event`: event bridge and integration points for clean lifecycle cleanup.
- `entity`: custom entities and managers for plugin-managed entities.
- `effect`: reusable effect timers (Bleed, Freeze, Infection).
- `utils` / `vfx`: helpers for particles, vectors, locations, and cached entities.

## Development Notes

To add an ability:

1. Create a new class under `src/main/java/com/abilitycombat/ability/list/`.
2. Add an entry to `src/main/resources/abilities.yml` with `name`, `rank`, `icon`, and `summary`.
3. Register the class in `AbilityCombat#registerAbilities()`.
4. Add or update config usage if your ability requires tuneable values.

## Credits

Author: `Antigravity`
