package com.shadow.rollback.listener;

import com.shadow.rollback.RollbackManager;
import com.shadow.rollback.model.InventoryChangeRecord;
import java.util.UUID;
import org.bukkit.Location;
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player actor)) {
            return;
        }

        recordInventory(event.getView().getTopInventory(), actor, actor.getName());
        recordInventory(actor.getInventory(), actor, actor.getName());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player actor)) {
            return;
        }

        recordInventory(event.getView().getTopInventory(), actor, actor.getName());
        recordInventory(actor.getInventory(), actor, actor.getName());
    }

    private void recordInventory(Inventory inventory, Player actor, String actorName) {
        if (inventory == null || inventory.getHolder() == null) {
            return;
        }

        InventoryTarget target = resolveTarget(inventory);
        if (target == null || target.worldId() == null) {
            return;
        }

        rollbackManager.logInventoryChange(new InventoryChangeRecord(
                System.currentTimeMillis(),
                actorName,
                target.worldId(),
                target.x(),
                target.y(),
                target.z(),
                target.blockInventory(),
                target.targetPlayerUuid(),
                target.targetPlayerName(),
                cloneContents(inventory.getContents())
        ));
    }

    private InventoryTarget resolveTarget(Inventory inventory) {
        InventoryHolder holder = inventory.getHolder();
        if (holder == null) {
            return null;
        }

        if (holder instanceof Player player) {
            Location location = player.getLocation();
            return new InventoryTarget(
                    location.getWorld().getUID(),
                    location.getX(),
                    location.getY(),
                    location.getZ(),
                    false,
                    player.getUniqueId(),
                    player.getName()
            );
        }

        if (holder instanceof HumanEntity humanEntity) {
            Location location = humanEntity.getLocation();
            if (location.getWorld() == null) {
                return null;
            }

            return new InventoryTarget(
                    location.getWorld().getUID(),
                    location.getX(),
                    location.getY(),
                    location.getZ(),
                    false,
                    humanEntity.getUniqueId(),
                    humanEntity.getName()
            );
        }

        Location location = inventory.getLocation();
        if (location == null || location.getWorld() == null) {
            return null;
        }

        return new InventoryTarget(
                location.getWorld().getUID(),
                location.getX(),
                location.getY(),
                location.getZ(),
                true,
                null,
                null
        );

    }

    private ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            copy[i] = contents[i] == null ? null : contents[i].clone();
        }
        return copy;
    }

    private record InventoryTarget(
            UUID worldId,
            double x,
            double y,
            double z,
            boolean blockInventory,
            UUID targetPlayerUuid,
            String targetPlayerName
    ) {
    }
}
