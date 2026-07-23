package com.bigbangcraft.regions.protection;

import com.pedrodalben.bigbangessentials.shop.ShopManager;
import com.pedrodalben.bigbangessentials.shop.model.ShopData;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bridge between BigBangRegions protection and BigBangEssentials ChestShop.
 * All Essentials class references are guarded by {@link #isAvailable()} so
 * the JVM never resolves them when the Essentials mod is absent.
 */
public final class ShopIntegration {

    private static final Logger LOGGER = LoggerFactory.getLogger("BigBangRegions-Shop");
    private static boolean checked;
    private static boolean available;

    private ShopIntegration() {}

    private static boolean isAvailable() {
        if (!checked) {
            available = FabricLoader.getInstance().isModLoaded("bigbangessentials");
            checked = true;
            LOGGER.info("BigBangEssentials ChestShop integration: {}", available ? "enabled" : "disabled");
        }
        return available;
    }

    /**
     * True when {@code state} is a sign block registered as a ChestShop.
     * Any player (owner, member, visitor) may interact — the Essentials
     * handler takes over and executes the buy/sell/info flow.
     */
    public static boolean isShopSign(Level world, BlockPos pos, BlockState state) {
        if (!isAvailable() || !(state.getBlock() instanceof SignBlock)) return false;
        try {
            return ShopManager.getInstance().getShopBySign(
                    world.dimension().location().toString(), pos) != null;
        } catch (Exception e) {
            LOGGER.warn("ChestShop sign lookup failed at {}: {}", pos, e.toString());
            return false;
        }
    }

    /**
     * True when {@code pos} holds a chest linked to any ChestShop.
     */
    public static boolean isShopChest(Level world, BlockPos pos) {
        if (!isAvailable()) return false;
        try {
            return ShopManager.getInstance().getShopByChest(
                    world.dimension().location().toString(), pos) != null;
        } catch (Exception e) {
            LOGGER.warn("ChestShop chest lookup failed at {}: {}", pos, e.toString());
            return false;
        }
    }

    /**
     * True when {@code player} is the registered owner of the shop whose sign or
     * chest sits at {@code pos}.
     */
    public static boolean isShopOwner(ServerPlayer player, Level world, BlockPos pos) {
        if (!isAvailable()) return false;
        try {
            String dim = world.dimension().location().toString();
            ShopData shop = ShopManager.getInstance().getShopBySign(dim, pos);
            if (shop == null) shop = ShopManager.getInstance().getShopByChest(dim, pos);
            return shop != null
                    && shop.ownerUUID != null
                    && shop.ownerUUID.equals(player.getUUID());
        } catch (Exception e) {
            LOGGER.warn("ChestShop owner lookup failed at {}: {}", pos, e.toString());
            return false;
        }
    }
}
