package com.shadow.rollback.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class MessageConfig {

    private final JavaPlugin plugin;
    private File file;
    private FileConfiguration config;

    public MessageConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();

        String fileName = plugin.getConfig().getString("language-file", "messages.yml");
        file = new File(plugin.getDataFolder(), fileName);
        copyDefaultIfMissing(fileName, file);
        config = YamlConfiguration.loadConfiguration(file);
    }

    public String get(String path) {
        String value = config.getString(path, path);
        return ChatColor.translateAlternateColorCodes('&', value);
    }

    public List<String> getList(String path) {
        List<String> raw = config.getStringList(path);
        if (raw.isEmpty()) {
            raw = new ArrayList<>();
            raw.add(path);
        }

        List<String> translated = new ArrayList<>(raw.size());
        for (String line : raw) {
            translated.add(ChatColor.translateAlternateColorCodes('&', line));
        }
        return translated;
    }

    public String format(String path, Map<String, String> placeholders) {
        String message = get(path);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", Objects.toString(entry.getValue(), ""));
        }
        return message;
    }

    public List<String> formatList(String path, Map<String, String> placeholders) {
        List<String> lines = getList(path);
        List<String> formatted = new ArrayList<>(lines.size());
        for (String line : lines) {
            String value = line;
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                value = value.replace("{" + entry.getKey() + "}", Objects.toString(entry.getValue(), ""));
            }
            formatted.add(value);
        }
        return formatted;
    }

    private void copyDefaultIfMissing(String resourceName, File destination) {
        if (destination.exists()) {
            return;
        }

        if (!destination.getParentFile().exists() && !destination.getParentFile().mkdirs()) {
            throw new IllegalStateException("Could not create config folder");
        }

        try (InputStream inputStream = plugin.getResource(resourceName)) {
            if (inputStream == null) {
                throw new IllegalStateException("Missing bundled resource: " + resourceName);
            }
            Files.copy(inputStream, destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not copy default config: " + resourceName, exception);
        }
    }
}
