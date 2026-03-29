package com.shadow.rollback.storage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

public final class InventorySerialization {

    private InventorySerialization() {
    }

    public static String serialize(ItemStack[] contents) {
        try {
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            try (BukkitObjectOutputStream outputStream = new BukkitObjectOutputStream(byteStream)) {
                outputStream.writeInt(contents.length);
                for (ItemStack itemStack : contents) {
                    outputStream.writeObject(itemStack);
                }
            }
            return Base64.getEncoder().encodeToString(byteStream.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to serialize inventory contents", exception);
        }
    }

    public static ItemStack[] deserialize(String data) {
        try {
            byte[] bytes = Base64.getDecoder().decode(data);
            try (BukkitObjectInputStream inputStream = new BukkitObjectInputStream(new ByteArrayInputStream(bytes))) {
                int size = inputStream.readInt();
                ItemStack[] contents = new ItemStack[size];
                for (int i = 0; i < size; i++) {
                    contents[i] = (ItemStack) inputStream.readObject();
                }
                return contents;
            }
        } catch (IOException | ClassNotFoundException exception) {
            throw new IllegalStateException("Failed to deserialize inventory contents", exception);
        }
    }
}
