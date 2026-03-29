# Rollback

Rollback is a Paper plugin for SQLite-backed action logging, lookup, rollback and restore on a Minecraft server.

It currently tracks:
- block changes
- player and container inventory changes
- living entity deaths

The plugin stores rollback history in a local SQLite database and can revert logged actions by command.

All player-facing messages can be customized from config files, so you can translate the plugin without editing code.

## Features

- SQLite-backed action log
- `lookup`, `rollback`, `restore`, `purge`, `reload` commands
- Targets: `blocks`, `entities`, `inventory`, `all`
- Time-based filtering like `30s`, `10m`, `1h`, `1d`
- Optional filters: `radius=`, `player=`, `world=`, `limit=`
- Rollback state is tracked so restored actions can be replayed

## Important limitations

This plugin is in the same category as rollback/logging tools, but it is still a smaller implementation.

Current limitations:
- Only actions logged while the plugin is enabled can be rolled back
- `radius=` only works when the command is executed by a player
- Player inventory restore only works if that player is online
- Inventory logging is still limited to inventory interactions this plugin captures
- Entity restore removes a nearby matching entity and is not as exact as a full grief-management plugin
- It does not yet log explosions, liquids, fire, pistons, or world edit style bulk changes

## Commands

### Lookup

```text
/rollbacks lookup <blocks|entities|inventory|all> <time> [radius=20] [player=Name] [world=world] [limit=10]
```

### Rollback

```text
/rollbacks rollback <blocks|entities|inventory|all> <time> [radius=20] [player=Name] [world=world]
```

### Restore

```text
/rollbacks restore <blocks|entities|inventory|all> <time> [radius=20] [player=Name] [world=world]
```

### Purge

```text
/rollbacks purge <all|time>
```

### Reload

```text
/rollbacks reload
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
/rollbacks rollback blocks 10m radius=30
```

### `player=`
Filters rollback to changes linked to a specific player where supported.

Example:
```text
/rollbacks lookup inventory 20m player=Liam limit=10
```

### `world=`
Limits rollback to a specific world.

Example:
```text
/rollbacks restore all 1h world=world_nether
```

## Example commands

```text
/rollbacks lookup all 10m
/rollbacks rollback blocks 10m
/rollbacks rollback inventory 15m player=Liam
/rollbacks rollback all 30m world=world
/rollbacks restore blocks 10m radius=20
/rollbacks restore entities 5m world=world_nether
/rollbacks lookup all 10m radius=25 player=Liam world=world limit=5
/rollbacks purge 7d
/rollbacks purge all
/rollbacks reload
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

After the plugin starts, it creates its database here:

```text
plugins/RollbackPlugin/database/rollback.db
```

The plugin also creates:

```text
plugins/RollbackPlugin/config.yml
plugins/RollbackPlugin/messages.yml
```

You can edit `messages.yml` in your panel to change all command and plugin messages to another language.

## Development notes

- Java target: `17`
- API target: Paper `1.20.1-R0.1-SNAPSHOT`

## Current project status

This is a working base for a rollback plugin, but it is still an early version.

Good next steps would be:
- explosion, liquid, fire and piston logging
- rollback batching with explicit rollback IDs
- safer exact entity restore
- offline player inventory restore
- block/container diff compression and pruning
