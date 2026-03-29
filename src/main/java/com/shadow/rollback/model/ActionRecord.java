package com.shadow.rollback.model;

import java.util.UUID;

public record ActionRecord(
        long id,
        long timestamp,
        String actorName,
        UUID worldId,
        String worldName,
        double x,
        double y,
        double z,
        ActionType actionType,
        String targetName,
        String beforeData,
        String afterData,
        boolean rolledBack
) {
}
