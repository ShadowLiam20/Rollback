# Rollback

Rollback is a Paper plugin for simple time-based rollback actions on a Minecraft server.

It currently supports rollback for:
- blocks
- entities
- inventories
- all of the above together

The plugin is built as a lightweight in-memory rollback system. It logs actions while the plugin is running and can revert them by command.

## Features

- Separate rollback targets: `blocks`, `entities`, `inventory`, `all`
- Time-based rollback like `30s`, `10m`, `1h`, `1d`
- Optional filters:
  - `radius=`
  - `player=`
  - `world=`
- `reload` subcommand to reset plugin state safely
- Tab completion for command arguments

## Important limitations

This plugin is not a full CoreProtect-style rollback system.

Current limitations:
- History is only stored in memory
- All rollback data is lost after server restart or plugin reload
- Only actions logged while the plugin is enabled can be rolled back
- `radius=` only works when the command is executed by a player
- Player inventory restore only works if that player is online
- Inventory logging is a base implementation and does not yet cover every possible item movement source
- Entity rollback is basic and does not restore every possible entity property or interaction

## Supported rollback types

### `blocks`
Rolls back logged block place and block break changes.

### `entities`
Rolls back logged living entity spawns and deaths.

### `inventory`
Rolls back logged player inventory and container inventory changes, such as chests.

### `all`
Runs block, entity, and inventory rollback together.

## Commands

### Main command

```text
/rollback <blocks|entities|inventory|all> <time> [radius=20] [player=Name] [world=world]
```

### Reload command

```text
/rollback reload
```

## Time format

Supported time suffixes:
- `s` = seconds
- `m` = minutes
- `h` = hours
- `d` = days

Examples:
- `30s`
- `10m`
- `1h`
- `2d`

## Filters

All filters are optional.

### `radius=`
Limits rollback to a radius around the executing player's current location.

Example:
```text
/rollback blocks 10m radius=30
```

### `player=`
Filters rollback to changes linked to a specific player where supported.

Example:
```text
/rollback inventory 20m player=Liam
```

### `world=`
Limits rollback to a specific world.

Example:
```text
/rollback all 1h world=world_nether
```

## Example commands

```text
/rollback blocks 10m
/rollback entities 5m
/rollback inventory 15m
/rollback all 30m
/rollback blocks 10m player=Liam
/rollback inventory 20m world=world
/rollback all 5m radius=50
/rollback all 10m radius=25 player=Liam world=world
/rollback reload
```

## Permission

```text
rollback.use
```

Default:
- `op`

## Build

This project uses Maven.

Build the plugin with:

```powershell
mvn clean package
```

The built jar will normally be created in:

```text
target/rollbackplugin-1.0.jar
```

## Install on server

1. Build the project into a jar.
2. Copy the generated jar into your Paper server `plugins` folder.
3. Start or restart the server.
4. Check the console for plugin load messages or errors.

## Development notes

- Java target: `17`
- API target: Paper `1.20.1-R0.1-SNAPSHOT`

## Current project status

This is a working base for a rollback plugin, but it is still an early version.

Good next steps would be:
- persistent storage with SQLite
- better inventory tracking
- offline player inventory restore
- more complete entity rollback
- support for explosions, liquids, fire, and other world changes
