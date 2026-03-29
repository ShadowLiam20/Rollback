package com.shadow.rollback.listener;

import com.shadow.rollback.RollbackManager;
import com.shadow.rollback.model.EntitySnapshot;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;

public class EntityChangeListener implements Listener {

    private final RollbackManager rollbackManager;

    public EntityChangeListener(RollbackManager rollbackManager) {
        this.rollbackManager = rollbackManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        // Reserved for future spawn logging.
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player) {
            return;
        }

        String actorName = entity.getKiller() == null ? null : entity.getKiller().getName();
        rollbackManager.logEntityDeath(EntitySnapshot.from(entity, System.currentTimeMillis(), actorName));
    }
}
