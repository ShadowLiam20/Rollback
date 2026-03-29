package com.shadow.rollback;

import com.shadow.rollback.database.RollbackDatabase;
import com.shadow.rollback.model.ActionRecord;
import com.shadow.rollback.model.ActionType;
import com.shadow.rollback.model.EntitySnapshot;
import com.shadow.rollback.model.QueryFilter;
import com.shadow.rollback.storage.EntitySerialization;
import com.shadow.rollback.storage.InventorySerialization;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class RollbackManager {

    private final JavaPlugin plugin;
    private final RollbackDatabase database;

    public RollbackManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.database = new RollbackDatabase(plugin);
        this.database.open();
    }

    public void logBlockChange(String actorName, World world, int x, int y, int z, String beforeData, String afterData) {
        database.insert(new ActionRecord(
                0L,
                System.currentTimeMillis(),
                actorName,
                world.getUID(),
                world.getName(),
                x,
                y,
                z,
                ActionType.BLOCK,
                null,
                beforeData,
                afterData,
                false
        ));
    }

    public void logEntityDeath(EntitySnapshot snapshot) {
        database.insert(new ActionRecord(
                0L,
                snapshot.timestamp(),
                snapshot.actorName(),
                snapshot.worldId(),
                Bukkit.getWorld(snapshot.worldId()) == null ? "unknown" : Bukkit.getWorld(snapshot.worldId()).getName(),
                snapshot.x(),
                snapshot.y(),
                snapshot.z(),
                ActionType.ENTITY_DEATH,
                snapshot.entityType().name(),
                EntitySerialization.serialize(snapshot),
                "",
                false
        ));
    }

    public void logInventoryChange(ActionType actionType, String actorName, World world, double x, double y, double z, String targetName, ItemStack[] beforeContents, ItemStack[] afterContents) {
        database.insert(new ActionRecord(
                0L,
                System.currentTimeMillis(),
                actorName,
                world.getUID(),
                world.getName(),
                x,
                y,
                z,
                actionType,
                targetName,
                InventorySerialization.serialize(beforeContents),
                InventorySerialization.serialize(afterContents),
                false
        ));
    }

    public void clearHistory() {
        database.purgeAll();
    }

    public void shutdown() {
        database.close();
    }

    public LookupResult lookup(long sinceEpochMillis, QueryFilter filter, Set<ActionType> types) {
        List<ActionRecord> matches = filter(database.findSince(sinceEpochMillis, filter.limit()), sinceEpochMillis, filter, types, null);
        int blockCount = 0;
        int inventoryCount = 0;
        int entityCount = 0;
        for (ActionRecord record : matches) {
            switch (record.actionType()) {
                case BLOCK -> blockCount++;
                case CONTAINER_INVENTORY, PLAYER_INVENTORY -> inventoryCount++;
                case ENTITY_DEATH -> entityCount++;
            }
        }
        return new LookupResult(matches, blockCount, inventoryCount, entityCount);
    }

    public RollbackResult rollback(long sinceEpochMillis, QueryFilter filter, Set<ActionType> types) {
        List<ActionRecord> matches = filter(database.findSince(sinceEpochMillis, 10000), sinceEpochMillis, filter, types, false);
        return apply(matches, true);
    }

    public RollbackResult restore(long sinceEpochMillis, QueryFilter filter, Set<ActionType> types) {
        List<ActionRecord> matches = filter(database.findSince(sinceEpochMillis, 10000), sinceEpochMillis, filter, types, true);
        return apply(matches, false);
    }

    public int purge(long sinceEpochMillis) {
        return database.purgeSince(sinceEpochMillis);
    }

    public int purgeAll() {
        return database.purgeAll();
    }

    public JavaPlugin plugin() {
        return plugin;
    }

    public static Set<ActionType> resolveTypes(String target) {
        return switch (target) {
            case "blocks" -> EnumSet.of(ActionType.BLOCK);
            case "entities" -> EnumSet.of(ActionType.ENTITY_DEATH);
            case "inventory", "inventories" -> EnumSet.of(ActionType.CONTAINER_INVENTORY, ActionType.PLAYER_INVENTORY);
            case "all" -> EnumSet.allOf(ActionType.class);
            default -> EnumSet.noneOf(ActionType.class);
        };
    }

    private List<ActionRecord> filter(List<ActionRecord> records, long sinceEpochMillis, QueryFilter filter, Set<ActionType> types, Boolean rolledBack) {
        List<ActionRecord> matches = new ArrayList<>();
        for (ActionRecord record : records) {
            if (record.timestamp() < sinceEpochMillis) {
                continue;
            }
            if (!types.contains(record.actionType())) {
                continue;
            }
            if (rolledBack != null && record.rolledBack() != rolledBack) {
                continue;
            }
            if (filter.worldName() != null && !record.worldName().equalsIgnoreCase(filter.worldName())) {
                continue;
            }
            if (filter.playerName() != null) {
                boolean actorMatches = record.actorName() != null && record.actorName().equalsIgnoreCase(filter.playerName());
                boolean targetMatches = record.targetName() != null && record.targetName().equalsIgnoreCase(filter.playerName());
                if (!actorMatches && !targetMatches) {
                    continue;
                }
            }
            if (filter.radius() != null && filter.center() != null) {
                World world = Bukkit.getWorld(record.worldId());
                if (world == null || !world.getUID().equals(filter.center().getWorld().getUID())) {
                    continue;
                }
                Location recordLocation = new Location(world, record.x(), record.y(), record.z());
                if (recordLocation.distanceSquared(filter.center()) > (double) filter.radius() * filter.radius()) {
                    continue;
                }
            }
            matches.add(record);
        }
        return matches;
    }

    private RollbackResult apply(List<ActionRecord> records, boolean rollback) {
        int blockCount = 0;
        int inventoryCount = 0;
        int entityCount = 0;
        List<Long> updatedIds = new ArrayList<>();

        for (ActionRecord record : records) {
            boolean success = switch (record.actionType()) {
                case BLOCK -> applyBlock(record, rollback);
                case CONTAINER_INVENTORY, PLAYER_INVENTORY -> applyInventory(record, rollback);
                case ENTITY_DEATH -> applyEntity(record, rollback);
            };

            if (!success) {
                continue;
            }

            updatedIds.add(record.id());
            switch (record.actionType()) {
                case BLOCK -> blockCount++;
                case CONTAINER_INVENTORY, PLAYER_INVENTORY -> inventoryCount++;
                case ENTITY_DEATH -> entityCount++;
            }
        }

        database.setRolledBack(updatedIds, rollback);
        return new RollbackResult(blockCount, inventoryCount, entityCount);
    }

    private boolean applyBlock(ActionRecord record, boolean rollback) {
        World world = Bukkit.getWorld(record.worldId());
        if (world == null) {
            return false;
        }

        String blockDataString = rollback ? record.beforeData() : record.afterData();
        if (blockDataString == null || blockDataString.isEmpty()) {
            return false;
        }

        BlockData blockData = Bukkit.createBlockData(blockDataString);
        world.getBlockAt((int) Math.floor(record.x()), (int) Math.floor(record.y()), (int) Math.floor(record.z())).setBlockData(blockData, false);
        return true;
    }

    private boolean applyInventory(ActionRecord record, boolean rollback) {
        World world = Bukkit.getWorld(record.worldId());
        if (world == null) {
            return false;
        }

        String data = rollback ? record.beforeData() : record.afterData();
        if (data == null || data.isEmpty()) {
            return false;
        }

        ItemStack[] contents = InventorySerialization.deserialize(data);
        if (record.actionType() == ActionType.CONTAINER_INVENTORY) {
            BlockState blockState = world.getBlockAt((int) Math.floor(record.x()), (int) Math.floor(record.y()), (int) Math.floor(record.z())).getState();
            if (!(blockState instanceof Container container)) {
                return false;
            }
            container.getInventory().setContents(contents);
            container.update(true, false);
            return true;
        }

        Player player = record.targetName() == null ? null : Bukkit.getPlayerExact(record.targetName());
        if (player == null) {
            return false;
        }
        player.getInventory().setContents(contents);
        player.updateInventory();
        return true;
    }

    private boolean applyEntity(ActionRecord record, boolean rollback) {
        World world = Bukkit.getWorld(record.worldId());
        if (world == null || record.beforeData() == null || record.beforeData().isEmpty()) {
            return false;
        }

        EntitySnapshot snapshot = EntitySerialization.deserialize(record.beforeData());
        if (rollback) {
            Entity spawned = world.spawnEntity(snapshot.location(world), snapshot.entityType());
            if (spawned instanceof LivingEntity livingEntity) {
                snapshot.applyTo(livingEntity);
            }
            return true;
        }

        for (Entity entity : world.getNearbyEntities(snapshot.location(world), 1.5, 1.5, 1.5)) {
            if (entity.getType() == snapshot.entityType()) {
                entity.remove();
                return true;
            }
        }
        return false;
    }

    public record RollbackResult(int blocksAffected, int inventoriesAffected, int entitiesAffected) {
    }

    public record LookupResult(List<ActionRecord> records, int blocks, int inventories, int entities) {
    }
}
