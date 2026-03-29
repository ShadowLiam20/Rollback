package com.shadow.rollback.model;

import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public record EntitySnapshot(
        long timestamp,
        UUID entityUuid,
        String actorName,
        UUID worldId,
        EntityType entityType,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        Component customName,
        boolean customNameVisible,
        boolean ai,
        boolean glowing,
        boolean silent,
        boolean gravity
) {
    public static EntitySnapshot from(LivingEntity entity, long timestamp) {
        return from(entity, timestamp, null);
    }

    public static EntitySnapshot from(LivingEntity entity, long timestamp, String actorName) {
        return new EntitySnapshot(
                timestamp,
                entity.getUniqueId(),
                actorName,
                entity.getWorld().getUID(),
                entity.getType(),
                entity.getLocation().getX(),
                entity.getLocation().getY(),
                entity.getLocation().getZ(),
                entity.getLocation().getYaw(),
                entity.getLocation().getPitch(),
                entity.customName(),
                entity.isCustomNameVisible(),
                entity.hasAI(),
                entity.isGlowing(),
                entity.isSilent(),
                entity.hasGravity()
        );
    }

    public Location location(World world) {
        return new Location(world, x, y, z, yaw, pitch);
    }

    public void applyTo(LivingEntity entity) {
        if (entity instanceof Player) {
            return;
        }

        entity.customName(customName);
        entity.setCustomNameVisible(customNameVisible);
        entity.setAI(ai);
        entity.setGlowing(glowing);
        entity.setSilent(silent);
        entity.setGravity(gravity);
    }
}
