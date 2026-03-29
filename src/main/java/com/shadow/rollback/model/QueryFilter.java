package com.shadow.rollback.model;

import org.bukkit.Location;

public record QueryFilter(
        String playerName,
        String worldName,
        Integer radius,
        Location center,
        Integer limit
) {
}
