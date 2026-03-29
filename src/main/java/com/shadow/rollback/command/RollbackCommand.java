package com.shadow.rollback.command;

import com.shadow.rollback.RollbackManager;
import com.shadow.rollback.RollbackPlugin;
import com.shadow.rollback.config.MessageConfig;
import com.shadow.rollback.model.ActionRecord;
import com.shadow.rollback.model.ActionType;
import com.shadow.rollback.model.QueryFilter;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class RollbackCommand implements CommandExecutor, TabCompleter {

    private final RollbackPlugin plugin;
    private final RollbackManager rollbackManager;
    private final MessageConfig messages;

    public RollbackCommand(RollbackPlugin plugin, RollbackManager rollbackManager) {
        this.plugin = plugin;
        this.rollbackManager = rollbackManager;
        this.messages = plugin.messages();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("rollback.use")) {
            sender.sendMessage(messages.get("command.no-permission"));
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        return switch (subcommand) {
            case "reload" -> handleReload(sender);
            case "lookup" -> handleLookup(sender, args);
            case "rollback" -> handleRollback(sender, args);
            case "restore" -> handleRestore(sender, args);
            case "purge" -> handlePurge(sender, args);
            default -> {
                sendUsage(sender);
                yield true;
            }
        };
    }

    private boolean handleReload(CommandSender sender) {
        plugin.reloadState();
        sender.sendMessage(messages.get("command.reload-success"));
        return true;
    }

    private boolean handleLookup(CommandSender sender, String[] args) {
        ParsedRequest request = parseTargetedRequest(sender, args, false);
        if (request == null) {
            return true;
        }

        RollbackManager.LookupResult result = rollbackManager.lookup(request.sinceEpochMillis(), request.filter(), request.types());
        sender.sendMessage(messages.format("command.lookup-header", Map.of("target", args[1], "time", args[2])));
        sender.sendMessage(describeFilter(request.filter()));
        sender.sendMessage(messages.format("command.blocks-line", Map.of("count", Integer.toString(result.blocks()))));
        sender.sendMessage(messages.format("command.inventories-line", Map.of("count", Integer.toString(result.inventories()))));
        sender.sendMessage(messages.format("command.entities-line", Map.of("count", Integer.toString(result.entities()))));

        int shown = Math.min(5, result.records().size());
        for (int i = 0; i < shown; i++) {
            ActionRecord record = result.records().get(i);
            sender.sendMessage(messages.format("command.lookup-entry", Map.of(
                    "id", Long.toString(record.id()),
                    "type", record.actionType().name().toLowerCase(Locale.ROOT),
                    "actor", fallback(record.actorName(), "-"),
                    "world", record.worldName(),
                    "x", Integer.toString((int) record.x()),
                    "y", Integer.toString((int) record.y()),
                    "z", Integer.toString((int) record.z()),
                    "age", formatAge(record.timestamp())
            )));
        }
        return true;
    }

    private boolean handleRollback(CommandSender sender, String[] args) {
        ParsedRequest request = parseTargetedRequest(sender, args, false);
        if (request == null) {
            return true;
        }

        RollbackManager.RollbackResult result = rollbackManager.rollback(request.sinceEpochMillis(), request.filter(), request.types());
        sendApplyResult(sender, "Rollback", args[2], request.filter(), result);
        return true;
    }

    private boolean handleRestore(CommandSender sender, String[] args) {
        ParsedRequest request = parseTargetedRequest(sender, args, false);
        if (request == null) {
            return true;
        }

        RollbackManager.RollbackResult result = rollbackManager.restore(request.sinceEpochMillis(), request.filter(), request.types());
        sendApplyResult(sender, "Restore", args[2], request.filter(), result);
        return true;
    }

    private boolean handlePurge(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(messages.get("command.purge-usage"));
            return true;
        }

        if (args[1].equalsIgnoreCase("all")) {
            int deleted = rollbackManager.purgeAll();
            sender.sendMessage(messages.format("command.purge-all-success", Map.of("count", Integer.toString(deleted))));
            return true;
        }

        Long durationMillis = parseDurationMillis(args[1]);
        if (durationMillis == null || durationMillis <= 0L) {
            sender.sendMessage(messages.get("command.invalid-time"));
            return true;
        }

        int deleted = rollbackManager.purge(System.currentTimeMillis() - durationMillis);
        sender.sendMessage(messages.format("command.purge-success", Map.of("time", args[1], "count", Integer.toString(deleted))));
        return true;
    }

    private ParsedRequest parseTargetedRequest(CommandSender sender, String[] args, boolean allowAllTime) {
        if (args.length < 3) {
            sendUsage(sender);
            return null;
        }

        String target = args[1].toLowerCase(Locale.ROOT);
        Set<ActionType> types = RollbackManager.resolveTypes(target);
        if (types.isEmpty()) {
            sender.sendMessage(messages.format("command.unknown-target", Map.of("target", target)));
            return null;
        }

        Long durationMillis = parseDurationMillis(args[2]);
        if (!allowAllTime && (durationMillis == null || durationMillis <= 0L)) {
            sender.sendMessage(messages.get("command.invalid-time"));
            return null;
        }

        ParsedOptions options = parseOptions(sender, args, 3);
        if (options == null) {
            return null;
        }

        long sinceEpochMillis = System.currentTimeMillis() - durationMillis;
        QueryFilter filter = new QueryFilter(options.playerName(), options.worldName(), options.radius(), options.center(), options.limit());
        return new ParsedRequest(types, sinceEpochMillis, filter);
    }

    private ParsedOptions parseOptions(CommandSender sender, String[] args, int startIndex) {
        Integer radius = null;
        Integer limit = null;
        String playerName = null;
        String worldName = null;
        Location center = null;

        for (int i = startIndex; i < args.length; i++) {
            String arg = args[i];
            int separator = arg.indexOf('=');
            if (separator <= 0 || separator == arg.length() - 1) {
                sender.sendMessage(messages.get("command.invalid-filter-format"));
                return null;
            }

            String key = arg.substring(0, separator).toLowerCase(Locale.ROOT);
            String value = arg.substring(separator + 1);
            switch (key) {
                case "radius" -> radius = parsePositiveInt(sender, value, "Radius");
                case "limit" -> limit = parsePositiveInt(sender, value, "Limit");
                case "player" -> playerName = value;
                case "world" -> {
                    World world = rollbackManager.plugin().getServer().getWorld(value);
                    if (world == null) {
                        sender.sendMessage(messages.format("command.world-not-found", Map.of("world", value)));
                        return null;
                    }
                    worldName = world.getName();
                }
                default -> {
                    sender.sendMessage(messages.format("command.unknown-filter", Map.of("filter", key)));
                    return null;
                }
            }
            if ((key.equals("radius") || key.equals("limit")) && ((key.equals("radius") ? radius : limit) == null)) {
                return null;
            }
        }

        if (radius != null) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(messages.get("command.radius-player-only"));
                return null;
            }
            center = player.getLocation();
            if (worldName == null) {
                worldName = player.getWorld().getName();
            }
        }

        return new ParsedOptions(radius, playerName, worldName, center, limit);
    }

    private Integer parsePositiveInt(CommandSender sender, String value, String fieldName) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                sender.sendMessage(messages.format("command.integer-positive", Map.of("field", fieldName)));
                return null;
            }
            return parsed;
        } catch (NumberFormatException exception) {
            sender.sendMessage(messages.format("command.integer-required", Map.of("field", fieldName)));
            return null;
        }
    }

    private void sendApplyResult(CommandSender sender, String actionName, String timeArg, QueryFilter filter, RollbackManager.RollbackResult result) {
        sender.sendMessage(messages.format("command.action-success", Map.of("action", actionName, "time", timeArg)));
        sender.sendMessage(describeFilter(filter));
        sender.sendMessage(messages.format("command.blocks-line", Map.of("count", Integer.toString(result.blocksAffected()))));
        sender.sendMessage(messages.format("command.inventories-line", Map.of("count", Integer.toString(result.inventoriesAffected()))));
        sender.sendMessage(messages.format("command.entities-line", Map.of("count", Integer.toString(result.entitiesAffected()))));
    }

    private void sendUsage(CommandSender sender) {
        for (String line : messages.getList("command.usage")) {
            sender.sendMessage(line);
        }
    }

    private Long parseDurationMillis(String input) {
        if (input == null || input.length() < 2) {
            return null;
        }

        char unit = Character.toLowerCase(input.charAt(input.length() - 1));
        String numberPart = input.substring(0, input.length() - 1);
        long value;
        try {
            value = Long.parseLong(numberPart);
        } catch (NumberFormatException exception) {
            return null;
        }

        return switch (unit) {
            case 's' -> value * 1000L;
            case 'm' -> value * 60_000L;
            case 'h' -> value * 3_600_000L;
            case 'd' -> value * 86_400_000L;
            default -> null;
        };
    }

    private String describeFilter(QueryFilter filter) {
        return messages.format("command.filters-line", Map.of(
                "radius", filter.radius() == null ? "off" : Integer.toString(filter.radius()),
                "player", fallback(filter.playerName(), "off"),
                "world", fallback(filter.worldName(), "all"),
                "limit", filter.limit() == null ? "default" : Integer.toString(filter.limit())
        ));
    }

    private String formatAge(long timestamp) {
        Duration age = Duration.between(Instant.ofEpochMilli(timestamp), Instant.now());
        long seconds = Math.max(0L, age.getSeconds());
        if (seconds < 60) {
            return seconds + "s ago";
        }
        if (seconds < 3600) {
            return (seconds / 60) + "m ago";
        }
        if (seconds < 86400) {
            return (seconds / 3600) + "h ago";
        }
        return (seconds / 86400) + "d ago";
    }

    private String fallback(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            for (String option : List.of("lookup", "rollback", "restore", "purge", "reload")) {
                if (option.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    completions.add(option);
                }
            }
        } else if (args.length == 2 && !args[0].equalsIgnoreCase("purge")) {
            for (String option : List.of("blocks", "entities", "inventory", "all")) {
                if (option.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    completions.add(option);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("purge")) {
            for (String option : List.of("all", "30s", "5m", "1h")) {
                if (option.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    completions.add(option);
                }
            }
        } else if (args.length == 3 && !args[0].equalsIgnoreCase("purge")) {
            completions.addAll(List.of("30s", "5m", "1h", "1d"));
        } else {
            for (String option : List.of("radius=", "player=", "world=", "limit=")) {
                if (option.startsWith(args[args.length - 1].toLowerCase(Locale.ROOT))) {
                    completions.add(option);
                }
            }
        }
        return completions;
    }

    private record ParsedOptions(Integer radius, String playerName, String worldName, Location center, Integer limit) {
    }

    private record ParsedRequest(Set<ActionType> types, long sinceEpochMillis, QueryFilter filter) {
    }
}
