package com.shadow.rollback.model;

import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

public record InventoryChangeRecord(
        long timestamp,
        String actorName,
        UUID worldId,
        double x,
        double y,
        double z,
        boolean blockInventory,
        UUID targetPlayerUuid,
        String targetPlayerName,
        ItemStack[] contents
) {
    public Location location(World world) {
        return new Location(world, x, y, z);
    }

    public ItemStack[] clonedContents() {
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            copy[i] = contents[i] == null ? null : contents[i].clone();
        }
        return copy;
    }
}
