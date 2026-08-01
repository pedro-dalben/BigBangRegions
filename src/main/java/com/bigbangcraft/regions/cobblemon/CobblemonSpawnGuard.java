package com.bigbangcraft.regions.cobblemon;

import com.bigbangcraft.regions.BigBangRegions;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.world.entity.Entity;

public final class CobblemonSpawnGuard {
    private CobblemonSpawnGuard() {
    }

    public static void register() {
        CobblemonEvents.ENTITY_SPAWN.subscribe(event -> {
            Entity entity = event.getEntity();
            if (entity instanceof PokemonEntity
                && BigBangRegions.isSpawnBlockedInSlotBuffer(entity.level(), entity.blockPosition())) {
                event.cancel();
            }
        });
    }
}
