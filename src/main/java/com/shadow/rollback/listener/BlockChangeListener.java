package com.shadow.rollback.listener;

import com.shadow.rollback.RollbackManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public class BlockChangeListener implements Listener {

    private final RollbackManager rollbackManager;

    public BlockChangeListener(RollbackManager rollbackManager) {
        this.rollbackManager = rollbackManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        BlockState replacedState = event.getBlockReplacedState();

        rollbackManager.logBlockChange(
                event.getPlayer().getName(),
                block.getWorld(),
                block.getX(),
                block.getY(),
                block.getZ(),
                replacedState.getBlockData().getAsString(),
                block.getBlockData().getAsString()
        );
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();

        rollbackManager.logBlockChange(
                event.getPlayer().getName(),
                block.getWorld(),
                block.getX(),
                block.getY(),
                block.getZ(),
                block.getBlockData().getAsString(),
                Bukkit.createBlockData(Material.AIR).getAsString()
        );
    }
}
