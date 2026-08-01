package com.bigbangcraft.regions.mixin;

import com.bigbangcraft.regions.BigBangRegions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(net.minecraft.server.level.ServerLevel.class)
public class ServerLevelMixin {
    private static final String COBBLEMON_POKEMON_ENTITY = "com.cobblemon.mod.common.entity.pokemon.PokemonEntity";

    @Inject(method = "addFreshEntity", at = @At("HEAD"), cancellable = true)
    private void bigbangregions$blockSpawnInPlayerBuffer(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity == null) return;
        Level level = entity.level();
        if (level == null || level.isClientSide()) return;

        if (!(entity instanceof Mob)) return;
        if (isCobblemonPokemon(entity.getClass().getName())) return;

        if (BigBangRegions.isSpawnBlockedInSlotBuffer(level, entity.blockPosition())) {
            cir.setReturnValue(false);
        }
    }

    private static boolean isCobblemonPokemon(String entityClassName) {
        return COBBLEMON_POKEMON_ENTITY.equals(entityClassName);
    }
}
