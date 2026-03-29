package com.shadow.rollback;

import com.shadow.rollback.model.BlockChangeRecord;
import com.shadow.rollback.model.EntitySnapshot;
import com.shadow.rollback.model.InventoryChangeRecord;
import com.shadow.rollback.model.RollbackFilter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

public class RollbackManager {

    private final JavaPlugin plugin;
    private final List<BlockChangeRecord> blockChanges = new ArrayList<>();
    private final List<EntitySnapshot> entitySpawns = new ArrayList<>();
    private final List<EntitySnapshot> entityDeaths = new ArrayList<>();
    private final List<InventoryChangeRecord> inventoryChanges = new ArrayList<>();

    public RollbackManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void logBlockChange(BlockChangeRecord record) {
        synchronized (blockChanges) {
            blockChanges.add(record);
        }
    }

    public void logEntitySpawn(EntitySnapshot snapshot) {
        synchronized (entitySpawns) {
            entitySpawns.add(snapshot);
        }
    }

    public void logEntityDeath(EntitySnapshot snapshot) {
        synchronized (entityDeaths) {
            entityDeaths.add(snapshot);
        }
    }

    public void logInventoryChange(InventoryChangeRecord record) {
        synchronized (inventoryChanges) {
            inventoryChanges.add(record);
        }
    }

    public void clearHistory() {
        synchronized (blockChanges) {
            blockChanges.clear();
        }
        synchronized (entitySpawns) {
            entitySpawns.clear();
        }
        synchronized (entityDeaths) {
            entityDeaths.clear();
        }
        synchronized (inventoryChanges) {
            inventoryChanges.clear();
        }
    }

    public RollbackResult rollbackBlocks(long sinceEpochMillis, RollbackFilter filter) {
        int changed = 0;
        synchronized (blockChanges) {
            for (int i = blockChanges.size() - 1; i >= 0; i--) {
                BlockChangeRecord record = blockChanges.get(i);
                if (!matchesBlock(record, sinceEpochMillis, filter)) {
                    continue;
                }

                World world = Bukkit.getWorld(record.worldId());
                if (world == null) {
                    continue;
                }

                Location location = new Location(world, record.x(), record.y(), record.z());
                BlockData oldData = Bukkit.createBlockData(record.oldBlockData());
                world.getBlockAt(location).setBlockData(oldData, false);
                changed++;
            }

            Iterator<BlockChangeRecord> iterator = blockChanges.iterator();
            while (iterator.hasNext()) {
                if (matchesBlock(iterator.next(), sinceEpochMillis, filter)) {
                    iterator.remove();
                }
            }
        }

        return new RollbackResult(changed, 0, 0, 0);
    }

    public RollbackResult rollbackInventories(long sinceEpochMillis, RollbackFilter filter) {
        int inventoriesRestored = 0;

        synchronized (inventoryChanges) {
            for (int i = inventoryChanges.size() - 1; i >= 0; i--) {
                InventoryChangeRecord record = inventoryChanges.get(i);
                if (!matchesInventory(record, sinceEpochMillis, filter)) {
                    continue;
                }

                if (restoreInventory(record)) {
                    inventoriesRestored++;
                }
            }

            inventoryChanges.removeIf(record -> matchesInventory(record, sinceEpochMillis, filter));
        }

        return new RollbackResult(0, 0, 0, inventoriesRestored);
    }

    public RollbackResult rollbackEntities(long sinceEpochMillis, RollbackFilter filter) {
        int removed = 0;
        int respawned = 0;

        synchronized (entitySpawns) {
            for (int i = entitySpawns.size() - 1; i >= 0; i--) {
                EntitySnapshot snapshot = entitySpawns.get(i);
                if (!matchesEntity(snapshot, sinceEpochMillis, filter)) {
                    continue;
                }

                World world = Bukkit.getWorld(snapshot.worldId());
                if (world == null) {
                    continue;
                }

                Entity entity = world.getEntity(snapshot.entityUuid());
                if (entity != null && entity.isValid()) {
                    entity.remove();
                    removed++;
                }
            }

            entitySpawns.removeIf(snapshot -> matchesEntity(snapshot, sinceEpochMillis, filter));
        }

        synchronized (entityDeaths) {
            for (int i = entityDeaths.size() - 1; i >= 0; i--) {
                EntitySnapshot snapshot = entityDeaths.get(i);
                if (!matchesEntity(snapshot, sinceEpochMillis, filter)) {
                    continue;
                }

                World world = Bukkit.getWorld(snapshot.worldId());
                if (world == null) {
                    continue;
                }

                Location location = snapshot.location(world);
                EntityType type = snapshot.entityType();
                Entity spawned = world.spawnEntity(location, type);
                if (spawned instanceof LivingEntity livingEntity) {
                    snapshot.applyTo(livingEntity);
                }
                respawned++;
            }

            entityDeaths.removeIf(snapshot -> matchesEntity(snapshot, sinceEpochMillis, filter));
        }

        return new RollbackResult(0, removed, respawned, 0);
    }

    public RollbackResult rollbackAll(long sinceEpochMillis, RollbackFilter filter) {
        RollbackResult blocks = rollbackBlocks(sinceEpochMillis, filter);
        RollbackResult entities = rollbackEntities(sinceEpochMillis, filter);
        RollbackResult inventories = rollbackInventories(sinceEpochMillis, filter);
        return new RollbackResult(
                blocks.blocksChanged(),
                entities.entitiesRemoved(),
                entities.entitiesRespawned(),
                inventories.inventoriesRestored()
        );
    }

    private boolean matchesBlock(BlockChangeRecord record, long sinceEpochMillis, RollbackFilter filter) {
        if (record.timestamp() < sinceEpochMillis) {
            return false;
        }

        World world = Bukkit.getWorld(record.worldId());
        if (world == null) {
            return false;
        }

        if (filter.worldName() != null && !world.getName().equalsIgnoreCase(filter.worldName())) {
            return false;
        }

        if (filter.playerName() != null) {
            String actorName = record.actorName();
            if (actorName == null || !actorName.equalsIgnoreCase(filter.playerName())) {
                return false;
            }
        }

        if (filter.radius() != null && filter.center() != null) {
            if (!world.getUID().equals(filter.center().getWorld().getUID())) {
                return false;
            }

            Location blockLocation = new Location(world, record.x(), record.y(), record.z());
            if (blockLocation.distanceSquared(filter.center()) > (double) filter.radius() * filter.radius()) {
                return false;
            }
        }

        return true;
    }

    private boolean matchesEntity(EntitySnapshot snapshot, long sinceEpochMillis, RollbackFilter filter) {
        if (snapshot.timestamp() < sinceEpochMillis) {
            return false;
        }

        World world = Bukkit.getWorld(snapshot.worldId());
        if (world == null) {
            return false;
        }

        if (filter.worldName() != null && !world.getName().equalsIgnoreCase(filter.worldName())) {
            return false;
        }

        if (filter.playerName() != null) {
            String actorName = snapshot.actorName();
            if (actorName == null || !actorName.equalsIgnoreCase(filter.playerName())) {
                return false;
            }
        }

        if (filter.radius() != null && filter.center() != null) {
            if (!world.getUID().equals(filter.center().getWorld().getUID())) {
                return false;
            }

            Location entityLocation = snapshot.location(world);
            if (entityLocation.distanceSquared(filter.center()) > (double) filter.radius() * filter.radius()) {
                return false;
            }
        }

        return true;
    }

    private boolean matchesInventory(InventoryChangeRecord record, long sinceEpochMillis, RollbackFilter filter) {
        if (record.timestamp() < sinceEpochMillis) {
            return false;
        }

        World world = Bukkit.getWorld(record.worldId());
        if (world == null) {
            return false;
        }

        if (filter.worldName() != null && !world.getName().equalsIgnoreCase(filter.worldName())) {
            return false;
        }

        if (filter.playerName() != null) {
            boolean actorMatches = record.actorName() != null && record.actorName().equalsIgnoreCase(filter.playerName());
            boolean targetMatches = record.targetPlayerName() != null && record.targetPlayerName().equalsIgnoreCase(filter.playerName());
            if (!actorMatches && !targetMatches) {
                return false;
            }
        }

        if (filter.radius() != null && filter.center() != null) {
            if (!world.getUID().equals(filter.center().getWorld().getUID())) {
                return false;
            }

            Location inventoryLocation = record.location(world);
            if (inventoryLocation.distanceSquared(filter.center()) > (double) filter.radius() * filter.radius()) {
                return false;
            }
        }

        return true;
    }

    private boolean restoreInventory(InventoryChangeRecord record) {
        World world = Bukkit.getWorld(record.worldId());
        if (world == null) {
            return false;
        }

        Inventory inventory;
        if (record.blockInventory()) {
            BlockState blockState = world.getBlockAt((int) Math.floor(record.x()), (int) Math.floor(record.y()), (int) Math.floor(record.z())).getState();
            if (!(blockState instanceof Container container)) {
                return false;
            }
            inventory = container.getInventory();
            inventory.setContents(record.clonedContents());
            container.update(true, false);
            return true;
        }

        if (record.targetPlayerUuid() == null) {
            return false;
        }

        Player player = Bukkit.getPlayer(record.targetPlayerUuid());
        if (player == null) {
            return false;
        }

        player.getInventory().setContents(record.clonedContents());
        player.updateInventory();
        return true;
    }

    public JavaPlugin plugin() {
        return plugin;
    }

    public record RollbackResult(int blocksChanged, int entitiesRemoved, int entitiesRespawned, int inventoriesRestored) {
    }
}
