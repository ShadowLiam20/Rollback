package com.shadow.rollback.storage;

import com.shadow.rollback.model.EntitySnapshot;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.entity.EntityType;

public final class EntitySerialization {

    private static final String SEP = "\u001F";

    private EntitySerialization() {
    }

    public static String serialize(EntitySnapshot snapshot) {
        String customName = snapshot.customName() == null ? "" : GsonComponentSerializer.gson().serialize(snapshot.customName());
        return String.join(
                SEP,
                Long.toString(snapshot.timestamp()),
                snapshot.entityUuid().toString(),
                nullToEmpty(snapshot.actorName()),
                snapshot.worldId().toString(),
                snapshot.entityType().name(),
                Double.toString(snapshot.x()),
                Double.toString(snapshot.y()),
                Double.toString(snapshot.z()),
                Float.toString(snapshot.yaw()),
                Float.toString(snapshot.pitch()),
                customName,
                Boolean.toString(snapshot.customNameVisible()),
                Boolean.toString(snapshot.ai()),
                Boolean.toString(snapshot.glowing()),
                Boolean.toString(snapshot.silent()),
                Boolean.toString(snapshot.gravity())
        );
    }

    public static EntitySnapshot deserialize(String data) {
        String[] parts = data.split(SEP, -1);
        Component customName = parts[10].isEmpty() ? null : GsonComponentSerializer.gson().deserialize(parts[10]);
        return new EntitySnapshot(
                Long.parseLong(parts[0]),
                UUID.fromString(parts[1]),
                emptyToNull(parts[2]),
                UUID.fromString(parts[3]),
                EntityType.valueOf(parts[4]),
                Double.parseDouble(parts[5]),
                Double.parseDouble(parts[6]),
                Double.parseDouble(parts[7]),
                Float.parseFloat(parts[8]),
                Float.parseFloat(parts[9]),
                customName,
                Boolean.parseBoolean(parts[11]),
                Boolean.parseBoolean(parts[12]),
                Boolean.parseBoolean(parts[13]),
                Boolean.parseBoolean(parts[14]),
                Boolean.parseBoolean(parts[15])
        );
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String emptyToNull(String value) {
        return value.isEmpty() ? null : value;
    }
}
