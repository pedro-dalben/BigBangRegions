package com.bigbangcraft.regions.flag;

import java.util.*;

public class FlagRegistry {
    private static final Map<String, RegionFlag> flags = new LinkedHashMap<>();

    static {
        // Supported flags
        register(new RegionFlag("player-build", "BOOLEAN", FlagPolicy.ALLOW, true, true, "Allows block breaking and placing.", "protection", true));
        register(new RegionFlag("player-interact", "BOOLEAN", FlagPolicy.ALLOW, true, true, "Allows basic interaction with blocks.", "protection", true));
        register(new RegionFlag("container-access", "BOOLEAN", FlagPolicy.ALLOW, true, true, "Allows accessing containers and inventories.", "protection", true));
        register(new RegionFlag("door-use", "BOOLEAN", FlagPolicy.ALLOW, true, true, "Allows using doors, trapdoors, and gates.", "protection", true));
        register(new RegionFlag("redstone-use", "BOOLEAN", FlagPolicy.ALLOW, true, true, "Allows using buttons, levers, and plates.", "protection", true));
        register(new RegionFlag("entity-interact", "BOOLEAN", FlagPolicy.ALLOW, true, true, "Allows interacting with entities (frames, stands, mounts).", "protection", true));
        register(new RegionFlag("pvp", "BOOLEAN", FlagPolicy.ALLOW, true, true, "Allows player-versus-player combat.", "combat", true));
        register(new RegionFlag("item-pickup", "BOOLEAN", FlagPolicy.ALLOW, true, true, "Allows picking up items on the ground.", "utility", true));
        register(new RegionFlag("item-drop", "BOOLEAN", FlagPolicy.ALLOW, true, true, "Allows dropping items on the ground.", "utility", true));

        // Unsupported/Planned flags
        register(new RegionFlag("hostile-mob-spawn", "BOOLEAN", FlagPolicy.ALLOW, false, true, "Allows hostile mobs to spawn. (Planned)", "environment", false));
        register(new RegionFlag("passive-mob-spawn", "BOOLEAN", FlagPolicy.ALLOW, false, true, "Allows passive mobs to spawn. (Planned)", "environment", false));
        register(new RegionFlag("fire-spread", "BOOLEAN", FlagPolicy.ALLOW, false, true, "Allows fire to spread. (Planned)", "environment", false));
        register(new RegionFlag("fluid-flow", "BOOLEAN", FlagPolicy.ALLOW, false, true, "Allows fluid flow (water/lava). (Planned)", "environment", false));
        register(new RegionFlag("explosion-block-damage", "BOOLEAN", FlagPolicy.ALLOW, false, true, "Allows explosions to damage blocks. (Planned)", "environment", false));
        register(new RegionFlag("piston-move", "BOOLEAN", FlagPolicy.ALLOW, false, true, "Allows pistons to push/pull blocks. (Planned)", "environment", false));
        register(new RegionFlag("projectile-use", "BOOLEAN", FlagPolicy.ALLOW, false, true, "Allows launching projectiles. (Planned)", "combat", false));
        register(new RegionFlag("mob-griefing", "BOOLEAN", FlagPolicy.ALLOW, false, true, "Allows mobs to grief (e.g. Endermen, Creepers). (Planned)", "environment", false));
        register(new RegionFlag("crop-trample", "BOOLEAN", FlagPolicy.ALLOW, false, true, "Allows players/entities to trample crops. (Planned)", "environment", false));
        register(new RegionFlag("teleport-in", "BOOLEAN", FlagPolicy.ALLOW, false, true, "Allows teleporting into the region. (Planned)", "movement", false));
        register(new RegionFlag("teleport-out", "BOOLEAN", FlagPolicy.ALLOW, false, true, "Allows teleporting out of the region. (Planned)", "movement", false));
    }

    public static void register(RegionFlag flag) {
        flags.put(flag.getId().toLowerCase(), flag);
    }

    public static Optional<RegionFlag> get(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(flags.get(id.toLowerCase()));
    }

    public static Collection<RegionFlag> getAll() {
        return Collections.unmodifiableCollection(flags.values());
    }

    public static boolean isRegistered(String id) {
        if (id == null) return false;
        return flags.containsKey(id.toLowerCase());
    }
}
