package com.shadow.rollback.command;

import com.shadow.rollback.RollbackPlugin;
import com.shadow.rollback.RollbackManager;
import com.shadow.rollback.model.RollbackFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.ChatColor;
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

    public RollbackCommand(RollbackPlugin plugin, RollbackManager rollbackManager) {
        this.plugin = plugin;
        this.rollbackManager = rollbackManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("rollback.use")) {
            sender.sendMessage(ChatColor.RED + "Je hebt geen permissie voor dit command.");
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            plugin.reloadState();
            sender.sendMessage(ChatColor.GREEN + "RollbackPlugin state opnieuw geladen.");
            sender.sendMessage(ChatColor.GRAY + "De opgeslagen rollback-geschiedenis van deze sessie is gewist.");
            return true;
        }

        if (args.length < 2) {
            sendUsage(sender);
            return true;
        }

        String target = args[0].toLowerCase(Locale.ROOT);
        Long durationMillis = parseDurationMillis(args[1]);
        if (durationMillis == null || durationMillis <= 0L) {
            sender.sendMessage(ChatColor.RED + "Ongeldige tijd. Gebruik bijvoorbeeld 30s, 10m, 2h of 1d.");
            return true;
        }

        ParsedOptions options = parseOptions(sender, args);
        if (options == null) {
            return true;
        }

        long sinceEpochMillis = System.currentTimeMillis() - durationMillis;
        RollbackManager.RollbackResult result;
        RollbackFilter filter = new RollbackFilter(
                options.playerName(),
                options.worldName(),
                options.radius(),
                options.center()
        );

        switch (target) {
            case "blocks" -> result = rollbackManager.rollbackBlocks(sinceEpochMillis, filter);
            case "entities" -> result = rollbackManager.rollbackEntities(sinceEpochMillis, filter);
            case "inventory" -> result = rollbackManager.rollbackInventories(sinceEpochMillis, filter);
            case "all" -> result = rollbackManager.rollbackAll(sinceEpochMillis, filter);
            default -> {
                sendUsage(sender);
                return true;
            }
        }

        sender.sendMessage(ChatColor.GREEN + "Rollback uitgevoerd voor " + args[1] + ".");
        sender.sendMessage(ChatColor.GRAY + describeFilter(options));
        if (target.equals("blocks") || target.equals("all")) {
            sender.sendMessage(ChatColor.YELLOW + "Blocks teruggezet: " + result.blocksChanged());
        }
        if (target.equals("entities") || target.equals("all")) {
            sender.sendMessage(ChatColor.YELLOW + "Entities verwijderd: " + result.entitiesRemoved());
            sender.sendMessage(ChatColor.YELLOW + "Entities teruggespawned: " + result.entitiesRespawned());
        }
        if (target.equals("inventory") || target.equals("all")) {
            sender.sendMessage(ChatColor.YELLOW + "Inventories hersteld: " + result.inventoriesRestored());
        }
        sender.sendMessage(ChatColor.GRAY + "Alleen acties gelogd sinds plugin-start worden meegenomen.");
        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "Gebruik: /rollback <blocks|entities|inventory|all> <tijd> [radius=20] [player=Naam] [world=world]");
        sender.sendMessage(ChatColor.RED + "Of: /rollback reload");
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

    private ParsedOptions parseOptions(CommandSender sender, String[] args) {
        Integer radius = null;
        String playerName = null;
        String worldName = null;
        Location center = null;

        for (int i = 2; i < args.length; i++) {
            String arg = args[i];
            int separator = arg.indexOf('=');
            if (separator <= 0 || separator == arg.length() - 1) {
                sender.sendMessage(ChatColor.RED + "Gebruik optionele filters als radius=20, player=Naam of world=world.");
                return null;
            }

            String key = arg.substring(0, separator).toLowerCase(Locale.ROOT);
            String value = arg.substring(separator + 1);

            switch (key) {
                case "radius" -> {
                    try {
                        radius = Integer.parseInt(value);
                    } catch (NumberFormatException exception) {
                        sender.sendMessage(ChatColor.RED + "Radius moet een heel getal zijn.");
                        return null;
                    }

                    if (radius <= 0) {
                        sender.sendMessage(ChatColor.RED + "Radius moet groter zijn dan 0.");
                        return null;
                    }
                }
                case "player" -> playerName = value;
                case "world" -> {
                    World world = rollbackManager.plugin().getServer().getWorld(value);
                    if (world == null) {
                        sender.sendMessage(ChatColor.RED + "Wereld niet gevonden: " + value);
                        return null;
                    }
                    worldName = world.getName();
                }
                default -> {
                    sender.sendMessage(ChatColor.RED + "Onbekende filter: " + key);
                    return null;
                }
            }
        }

        if (radius != null) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Radius kan alleen gebruikt worden door een speler.");
                return null;
            }

            center = player.getLocation();
            if (worldName == null) {
                worldName = player.getWorld().getName();
            }
        }

        return new ParsedOptions(radius, playerName, worldName, center);
    }

    private String describeFilter(ParsedOptions options) {
        List<String> parts = new ArrayList<>();
        parts.add("Filters:");
        parts.add("radius=" + (options.radius() == null ? "uit" : options.radius()));
        parts.add("player=" + (options.playerName() == null ? "uit" : options.playerName()));
        parts.add("world=" + (options.worldName() == null ? "alle" : options.worldName()));
        return String.join(" ", parts);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            for (String option : List.of("blocks", "entities", "inventory", "all", "reload")) {
                if (option.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    completions.add(option);
                }
            }
        } else if (args.length == 2) {
            completions.add("30s");
            completions.add("5m");
            completions.add("1h");
            completions.add("1d");
        } else {
            for (String option : List.of("radius=", "player=", "world=")) {
                if (option.startsWith(args[args.length - 1].toLowerCase(Locale.ROOT))) {
                    completions.add(option);
                }
            }
        }

        return completions;
    }

    private record ParsedOptions(Integer radius, String playerName, String worldName, Location center) {
    }
}
