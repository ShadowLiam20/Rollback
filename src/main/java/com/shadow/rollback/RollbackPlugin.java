package com.shadow.rollback;

import com.shadow.rollback.command.RollbackCommand;
import com.shadow.rollback.listener.BlockChangeListener;
import com.shadow.rollback.listener.EntityChangeListener;
import com.shadow.rollback.listener.InventoryChangeListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

public class RollbackPlugin extends JavaPlugin {

    private RollbackManager rollbackManager;
    private RollbackCommand rollbackCommandExecutor;

    @Override
    public void onEnable() {
        PluginCommand rollbackCommand = getCommand("rollback");
        if (rollbackCommand == null) {
            throw new IllegalStateException("Command 'rollback' is not defined in plugin.yml");
        }

        reloadState();

        rollbackCommand.setExecutor(rollbackCommandExecutor);
        rollbackCommand.setTabCompleter(rollbackCommandExecutor);

        getLogger().info("RollbackPlugin gestart.");
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
        getLogger().info("RollbackPlugin gestopt.");
    }

    public void reloadState() {
        HandlerList.unregisterAll(this);
        rollbackManager = new RollbackManager(this);

        getServer().getPluginManager().registerEvents(new BlockChangeListener(rollbackManager), this);
        getServer().getPluginManager().registerEvents(new EntityChangeListener(rollbackManager), this);
        getServer().getPluginManager().registerEvents(new InventoryChangeListener(rollbackManager), this);

        rollbackCommandExecutor = new RollbackCommand(this, rollbackManager);
    }

    public RollbackManager rollbackManager() {
        return rollbackManager;
    }
}
