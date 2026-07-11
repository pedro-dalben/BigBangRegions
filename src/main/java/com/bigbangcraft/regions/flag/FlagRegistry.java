package com.bigbangcraft.regions.flag;

import java.util.*;

public class FlagRegistry {
    private static final Map<String, RegionFlag> flags = new LinkedHashMap<>();
    private static final Map<String, String> FLAG_ALIASES = Map.ofEntries(
            java.util.Map.entry("piston-movement", "piston-move"),
            java.util.Map.entry("visitor-pcs", "visitor-usage"),
            java.util.Map.entry("visitor-containers", "visitor-usage"),
            java.util.Map.entry("visitor-buttons", "visitor-usage"),
            java.util.Map.entry("visitor-levers", "visitor-usage"),
            java.util.Map.entry("visitor-redstone", "visitor-usage")
    );

    static {
        // Supported flags (new visitor-* flags from RegionFlagRegistry)
        register(new RegionFlag("visitor-build", "BOOLEAN", FlagPolicy.DENY, true, true, "Allows visitors to build (break/place blocks).", "protection", true));
        register(new RegionFlag("visitor-usage", "BOOLEAN", FlagPolicy.DENY, true, true, "Unified flag: containers, doors, redstone, interact.", "protection", true));
        register(new RegionFlag("visitor-item-frames", "BOOLEAN", FlagPolicy.DENY, true, true, "Allows visitors to interact with item frames.", "protection", true));
        register(new RegionFlag("visitor-armor-stands", "BOOLEAN", FlagPolicy.DENY, true, true, "Allows visitors to interact with armor stands.", "protection", true));
        register(new RegionFlag("visitor-pickup-items", "BOOLEAN", FlagPolicy.ALLOW, true, true, "Allows visitors to pick up items.", "utility", true));
        register(new RegionFlag("visitor-drop-items", "BOOLEAN", FlagPolicy.ALLOW, true, true, "Allows visitors to drop items.", "utility", true));
        register(new RegionFlag("pvp", "BOOLEAN", FlagPolicy.DENY, true, true, "Allows player-versus-player combat.", "combat", true));

        // Unsupported/Planned flags
        register(new RegionFlag("hostile-mob-spawn", "BOOLEAN", FlagPolicy.ALLOW, false, true, "Allows hostile mobs to spawn. (Planned)", "environment", false));
        register(new RegionFlag("passive-mob-spawn", "BOOLEAN", FlagPolicy.ALLOW, false, true, "Allows passive mobs to spawn. (Planned)", "environment", false));
        register(new RegionFlag("fire-spread", "BOOLEAN", FlagPolicy.ALLOW, true, true, "Allows fire to spread and be ignited.", "environment", true));
        register(new RegionFlag("fire-block-damage", "BOOLEAN", FlagPolicy.ALLOW, true, true, "Allows fire to burn blocks.", "environment", true));
        register(new RegionFlag("water-flow", "BOOLEAN", FlagPolicy.ALLOW, true, true, "Allows water to flow and be placed.", "environment", true));
        register(new RegionFlag("lava-flow", "BOOLEAN", FlagPolicy.ALLOW, true, true, "Allows lava to flow and be placed.", "environment", true));
        register(new RegionFlag("explosion-block-damage", "BOOLEAN", FlagPolicy.ALLOW, true, true, "Allows explosions to damage blocks.", "environment", true));
        register(new RegionFlag("piston-move", "BOOLEAN", FlagPolicy.ALLOW, true, true, "Allows pistons to push/pull blocks.", "environment", true));
        register(new RegionFlag("projectile-use", "BOOLEAN", FlagPolicy.ALLOW, false, true, "Allows launching projectiles. (Planned)", "combat", false));
        register(new RegionFlag("mob-griefing", "BOOLEAN", FlagPolicy.ALLOW, true, true, "Allows mobs to grief blocks (e.g. Endermen).", "environment", true));
        register(new RegionFlag("fall-damage", "BOOLEAN", FlagPolicy.ALLOW, true, true, "Allows fall damage to players.", "combat", true));
        register(new RegionFlag("crop-trample", "BOOLEAN", FlagPolicy.ALLOW, false, true, "Allows players/entities to trample crops. (Planned)", "environment", false));
        register(new RegionFlag("enter", "BOOLEAN", FlagPolicy.ALLOW, true, true, "Allows visitors to enter and stay in the region.", "access", true));
        register(new RegionFlag("teleport-in", "BOOLEAN", FlagPolicy.ALLOW, false, true, "Allows teleporting into the region. (Planned)", "movement", false));
        register(new RegionFlag("teleport-out", "BOOLEAN", FlagPolicy.ALLOW, false, true, "Allows teleporting out of the region. (Planned)", "movement", false));
    }

    public static void register(RegionFlag flag) {
        flags.put(flag.getId().toLowerCase(), flag);
    }

    public static Optional<RegionFlag> get(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(flags.get(canonicalId(id)));
    }

    public static Collection<RegionFlag> getAll() {
        return Collections.unmodifiableCollection(flags.values());
    }

    public static boolean isRegistered(String id) {
        if (id == null) return false;
        return flags.containsKey(canonicalId(id));
    }

    private static String canonicalId(String id) {
        if (id == null) {
            return null;
        }
        String normalized = id.toLowerCase(java.util.Locale.ROOT);
        return FLAG_ALIASES.getOrDefault(normalized, normalized);
    }
}
