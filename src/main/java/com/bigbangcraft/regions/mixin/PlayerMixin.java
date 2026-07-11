package com.bigbangcraft.regions.mixin;

import com.bigbangcraft.regions.BigBangRegions;
import com.bigbangcraft.regions.protection.RegionAction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public class PlayerMixin {
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void onHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerPlayer victim) {
            // Check fall damage cancellation
            if (source.is(DamageTypes.FALL)) {
                Level level = victim.level();
                BlockPos pos = victim.blockPosition();
                if (!BigBangRegions.canWorldAction(level, pos, RegionAction.FALL_DAMAGE)) {
                    cir.setReturnValue(false);
                    return;
                }
            }

            Entity attacker = source.getEntity();
            if (attacker instanceof ServerPlayer playerAttacker) {
                // Check PvP at both victim and attacker locations
                if (!BigBangRegions.handlePlayerAction(playerAttacker, victim.blockPosition(), RegionAction.PVP) ||
                    !BigBangRegions.handlePlayerAction(playerAttacker, playerAttacker.blockPosition(), RegionAction.PVP)) {
                    cir.setReturnValue(false);
                }
            }
        }
    }

    @Inject(method = "drop", at = @At("HEAD"), cancellable = true)
    private void onDrop(ItemStack stack, boolean retainOwnership, CallbackInfoReturnable<ItemEntity> cir) {
        if ((Object) this instanceof ServerPlayer player) {
            if (player.isRemoved() || player.isDeadOrDying()) {
                return;
            }
            // Cancel at HEAD before vanilla removes item from inventory.
            // If denied, simply return null — the item stays naturally in the player's inventory.
            if (!BigBangRegions.handlePlayerAction(player, player.blockPosition(), RegionAction.ITEM_DROP)) {
                cir.setReturnValue(null);
            }
        }
    }
}
