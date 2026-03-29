package com.shadow.rollback.model;

import java.util.UUID;

public record BlockChangeRecord(
        long timestamp,
        String actorName,
        UUID worldId,
        int x,
        int y,
        int z,
        String oldBlockData,
        String newBlockData
) {
}
