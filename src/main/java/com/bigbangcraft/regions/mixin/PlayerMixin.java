package com.bigbangcraft.regions.mixin;

import com.bigbangcraft.regions.BigBangRegions;
import com.bigbangcraft.regions.protection.RegionAction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public class PlayerMixin {
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void onHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerPlayer victim) {
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
    private void onDrop(ItemStack stack, boolean throwRandomly, boolean retainOwnership, CallbackInfoReturnable<ItemEntity> cir) {
        if ((Object) this instanceof ServerPlayer player) {
            if (!BigBangRegions.handlePlayerAction(player, player.blockPosition(), RegionAction.ITEM_DROP)) {
                // Return the item back to the player's inventory
                player.getInventory().add(stack);
                cir.setReturnValue(null);
            }
        }
    }
}
