package com.shadow.rollback.model;

import org.bukkit.Location;

public record RollbackFilter(
        String playerName,
        String worldName,
        Integer radius,
        Location center
) {
}
