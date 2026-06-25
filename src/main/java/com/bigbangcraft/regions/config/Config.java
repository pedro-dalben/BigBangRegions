package com.bigbangcraft.regions.config;

import java.util.HashMap;
import java.util.Map;

public class Config {
    private int schemaVersion = 1;
    private DefaultPriorities defaultPriorities = new DefaultPriorities();
    private Permissions permissions = new Permissions();
    private Defaults defaults = new Defaults();

    public static class DefaultPriorities {
        private int systemRegion = 10000;
        private int adminRegion = 1000;
        private int playerRegion = 100;

        public int getSystemRegion() { return systemRegion; }
        public int getAdminRegion() { return adminRegion; }
        public int getPlayerRegion() { return playerRegion; }
    }

    public static class Permissions {
        private int operatorFallbackLevel = 2;

        public int getOperatorFallbackLevel() { return operatorFallbackLevel; }
    }

    public static class Defaults {
        private Map<String, String> global = new HashMap<>();
        private Map<String, String> adminRegion = new HashMap<>();

        public Defaults() {
            // Global default policies
            global.put("player-build", "ALLOW");
            global.put("player-interact", "ALLOW");
            global.put("container-access", "ALLOW");
            global.put("door-use", "ALLOW");
            global.put("redstone-use", "ALLOW");
            global.put("entity-interact", "ALLOW");
            global.put("pvp", "ALLOW");
            global.put("item-pickup", "ALLOW");
            global.put("item-drop", "ALLOW");

            // Admin Region default policies
            adminRegion.put("player-build", "DENY");
            adminRegion.put("player-interact", "DENY");
            adminRegion.put("container-access", "DENY");
            adminRegion.put("door-use", "DENY");
            adminRegion.put("redstone-use", "DENY");
            adminRegion.put("entity-interact", "DENY");
            adminRegion.put("pvp", "DENY");
            adminRegion.put("item-pickup", "ALLOW");
            adminRegion.put("item-drop", "ALLOW");
        }

        public Map<String, String> getGlobal() { return global; }
        public Map<String, String> getAdminRegion() { return adminRegion; }
    }

    public int getSchemaVersion() { return schemaVersion; }
    public DefaultPriorities getDefaultPriorities() { return defaultPriorities; }
    public Permissions getPermissions() { return permissions; }
    public Defaults getDefaults() { return defaults; }
}
