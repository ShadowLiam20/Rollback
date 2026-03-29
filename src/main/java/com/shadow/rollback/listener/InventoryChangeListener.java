package com.shadow.rollback.listener;

import com.shadow.rollback.RollbackManager;
import com.shadow.rollback.model.ActionType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public class InventoryChangeListener implements Listener {

    private final RollbackManager rollbackManager;

    public InventoryChangeListener(RollbackManager rollbackManager) {
        this.rollbackManager = rollbackManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player actor)) {
            return;
        }

        trackInventoryChange(event.getView().getTopInventory(), actor);
        trackInventoryChange(actor.getInventory(), actor);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player actor)) {
            return;
        }

        trackInventoryChange(event.getView().getTopInventory(), actor);
        trackInventoryChange(actor.getInventory(), actor);
    }

    private void trackInventoryChange(Inventory inventory, Player actor) {
        InventoryTarget target = resolveTarget(inventory);
        if (inventory == null || target == null || target.world() == null) {
            return;
        }

        ItemStack[] before = cloneContents(inventory.getContents());
        Bukkit.getScheduler().runTask(rollbackManager.plugin(), () -> {
            ItemStack[] after = cloneContents(inventory.getContents());
            if (sameContents(before, after)) {
                return;
            }

            rollbackManager.logInventoryChange(
                    target.actionType(),
                    actor.getName(),
                    target.world(),
                    target.x(),
                    target.y(),
                    target.z(),
                    target.targetName(),
                    before,
                    after
            );
        });
    }

    private InventoryTarget resolveTarget(Inventory inventory) {
        if (inventory == null) {
            return null;
        }

        InventoryHolder holder = inventory.getHolder();
        if (holder instanceof Player player) {
            Location location = player.getLocation();
            return new InventoryTarget(location.getWorld(), location.getX(), location.getY(), location.getZ(), ActionType.PLAYER_INVENTORY, player.getName());
        }

        if (holder instanceof HumanEntity humanEntity) {
            Location location = humanEntity.getLocation();
            return new InventoryTarget(location.getWorld(), location.getX(), location.getY(), location.getZ(), ActionType.PLAYER_INVENTORY, humanEntity.getName());
        }

        Location location = inventory.getLocation();
        if (location == null) {
            return null;
        }

        return new InventoryTarget(location.getWorld(), location.getX(), location.getY(), location.getZ(), ActionType.CONTAINER_INVENTORY, null);
    }

    private ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            copy[i] = contents[i] == null ? null : contents[i].clone();
        }
        return copy;
    }

    private boolean sameContents(ItemStack[] left, ItemStack[] right) {
        if (left.length != right.length) {
            return false;
        }
        for (int i = 0; i < left.length; i++) {
            ItemStack a = left[i];
            ItemStack b = right[i];
            if (a == null && b == null) {
                continue;
            }
            if (a == null || b == null) {
                return false;
            }
            if (!a.equals(b)) {
                return false;
            }
        }
        return true;
    }

    private record InventoryTarget(World world, double x, double y, double z, ActionType actionType, String targetName) {
    }
}
