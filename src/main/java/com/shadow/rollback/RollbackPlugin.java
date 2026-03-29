package com.shadow.rollback;

import com.shadow.rollback.command.RollbackCommand;
import com.shadow.rollback.config.MessageConfig;
import com.shadow.rollback.listener.BlockChangeListener;
import com.shadow.rollback.listener.EntityChangeListener;
import com.shadow.rollback.listener.InventoryChangeListener;
import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

public class RollbackPlugin extends JavaPlugin {

    private RollbackManager rollbackManager;
    private RollbackCommand rollbackCommandExecutor;
    private MessageConfig messageConfig;

    @Override
    public void onEnable() {
        messageConfig = new MessageConfig(this);
        messageConfig.load();

        PluginCommand rollbackCommand = getCommand("rollbacks");
        if (rollbackCommand == null) {
            throw new IllegalStateException("Command 'rollbacks' is not defined in plugin.yml");
        }

        reloadState();

        rollbackCommand.setExecutor(rollbackCommandExecutor);
        rollbackCommand.setTabCompleter(rollbackCommandExecutor);

        getLogger().info(ChatColor.stripColor(messageConfig.get("plugin.enabled")));
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
        if (rollbackManager != null) {
            rollbackManager.shutdown();
        }
        if (messageConfig != null) {
            getLogger().info(ChatColor.stripColor(messageConfig.get("plugin.disabled")));
        }
    }

    public void reloadState() {
        HandlerList.unregisterAll(this);
        if (rollbackManager != null) {
            rollbackManager.shutdown();
        }
        if (messageConfig != null) {
            messageConfig.load();
        }
        rollbackManager = new RollbackManager(this);

        getServer().getPluginManager().registerEvents(new BlockChangeListener(rollbackManager), this);
        getServer().getPluginManager().registerEvents(new EntityChangeListener(rollbackManager), this);
        getServer().getPluginManager().registerEvents(new InventoryChangeListener(rollbackManager), this);

        rollbackCommandExecutor = new RollbackCommand(this, rollbackManager);
        PluginCommand rollbackCommand = getCommand("rollbacks");
        if (rollbackCommand != null) {
            rollbackCommand.setExecutor(rollbackCommandExecutor);
            rollbackCommand.setTabCompleter(rollbackCommandExecutor);
        }
    }

    public RollbackManager rollbackManager() {
        return rollbackManager;
    }

    public MessageConfig messages() {
        return messageConfig;
    }
}
