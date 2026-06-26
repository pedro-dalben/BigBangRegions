package com.bigbangcraft.regions.config;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Config {
    private int schemaVersion = 1;
    private DefaultPriorities defaultPriorities = new DefaultPriorities();
    private Permissions permissions = new Permissions();
    private Defaults defaults = new Defaults();
    private PlayerRegionsConfig playerRegions = new PlayerRegionsConfig();
    private PlayerLandAllocationConfig playerLandAllocation = new PlayerLandAllocationConfig();
    private Map<String, BiomeOptionConfig> biomeOptions = new HashMap<>();

    public Config() {
        biomeOptions.put("planicies", new BiomeOptionConfig("Planícies",
            Arrays.asList("plains", "planicie", "planicies"),
            Arrays.asList("minecraft:plains", "minecraft:sunflower_plains")
        ));
        biomeOptions.put("floresta", new BiomeOptionConfig("Floresta",
            Arrays.asList("forest", "floresta"),
            Arrays.asList("minecraft:forest", "minecraft:birch_forest", "minecraft:old_growth_birch_forest")
        ));
        biomeOptions.put("taiga", new BiomeOptionConfig("Taiga",
            Arrays.asList("taiga"),
            Arrays.asList("minecraft:taiga", "minecraft:old_growth_pine_taiga", "minecraft:old_growth_spruce_taiga")
        ));
        biomeOptions.put("deserto", new BiomeOptionConfig("Deserto",
            Arrays.asList("desert", "deserto"),
            Arrays.asList("minecraft:desert")
        ));
        biomeOptions.put("savana", new BiomeOptionConfig("Savana",
            Arrays.asList("savanna", "savana"),
            Arrays.asList("minecraft:savanna", "minecraft:savanna_plateau", "minecraft:windswept_savanna")
        ));
        biomeOptions.put("selva", new BiomeOptionConfig("Selva",
            Arrays.asList("jungle", "selva"),
            Arrays.asList("minecraft:jungle", "minecraft:sparse_jungle", "minecraft:bamboo_jungle")
        ));
    }

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
        private Map<String, String> playerRegion = new HashMap<>();
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

            // Player Region default policies
            playerRegion.put("player-build", "ALLOW");
            playerRegion.put("player-interact", "ALLOW");
            playerRegion.put("container-access", "ALLOW");
            playerRegion.put("door-use", "ALLOW");
            playerRegion.put("redstone-use", "ALLOW");
            playerRegion.put("entity-interact", "ALLOW");
            playerRegion.put("pvp", "DENY");
            playerRegion.put("item-pickup", "ALLOW");
            playerRegion.put("item-drop", "ALLOW");
        }

        public Map<String, String> getGlobal() { return global; }
        public Map<String, String> getAdminRegion() { return adminRegion; }
        public Map<String, String> getPlayerRegion() { return playerRegion; }
    }

    public static class PlayerRegionsConfig {
        private int maxRegionsPerOwner = 1;
        private boolean rejectOverlapWithAdminRegions = true;
        private boolean rejectOverlapWithSystemRegions = true;
        private boolean rejectOverlapWithPlayerRegions = true;

        public int getMaxRegionsPerOwner() {
            return maxRegionsPerOwner;
        }

        public void setMaxRegionsPerOwner(int maxRegionsPerOwner) {
            this.maxRegionsPerOwner = maxRegionsPerOwner;
        }

        public boolean isRejectOverlapWithAdminRegions() {
            return rejectOverlapWithAdminRegions;
        }

        public void setRejectOverlapWithAdminRegions(boolean val) {
            this.rejectOverlapWithAdminRegions = val;
        }

        public boolean isRejectOverlapWithSystemRegions() {
            return rejectOverlapWithSystemRegions;
        }

        public void setRejectOverlapWithSystemRegions(boolean val) {
            this.rejectOverlapWithSystemRegions = val;
        }

        public boolean isRejectOverlapWithPlayerRegions() {
            return rejectOverlapWithPlayerRegions;
        }

        public void setRejectOverlapWithPlayerRegions(boolean val) {
            this.rejectOverlapWithPlayerRegions = val;
        }
    }

    public static class PlayerLandAllocationConfig {
        private boolean enabled = true;
        private String targetDimension = "minecraft:overworld";
        private int initialClaimSize = 50;
        private int slotSize = 256;
        private int futureMaximumClaimSize = 240;
        private int slotInternalMargin = 8;
        private int maxRegionsPerOwner = 1;
        private ExplorationExclusionConfig explorationExclusion = new ExplorationExclusionConfig();
        private BiomeSearchConfig biomeSearch = new BiomeSearchConfig();
        private SchedulerConfig scheduler = new SchedulerConfig();

        public boolean isEnabled() { return enabled; }
        public String getTargetDimension() { return targetDimension; }
        public int getInitialClaimSize() { return initialClaimSize; }
        public int getSlotSize() { return slotSize; }
        public int getFutureMaximumClaimSize() { return futureMaximumClaimSize; }
        public int getSlotInternalMargin() { return slotInternalMargin; }
        public int getMaxRegionsPerOwner() { return maxRegionsPerOwner; }
        public ExplorationExclusionConfig getExplorationExclusion() { return explorationExclusion; }
        public BiomeSearchConfig getBiomeSearch() { return biomeSearch; }
        public SchedulerConfig getScheduler() { return scheduler; }

        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public void setTargetDimension(String targetDimension) { this.targetDimension = targetDimension; }
        public void setInitialClaimSize(int initialClaimSize) { this.initialClaimSize = initialClaimSize; }
        public void setSlotSize(int slotSize) { this.slotSize = slotSize; }
        public void setFutureMaximumClaimSize(int futureMaximumClaimSize) { this.futureMaximumClaimSize = futureMaximumClaimSize; }
        public void setSlotInternalMargin(int slotInternalMargin) { this.slotInternalMargin = slotInternalMargin; }
        public void setMaxRegionsPerOwner(int maxRegionsPerOwner) { this.maxRegionsPerOwner = maxRegionsPerOwner; }
    }

    public static class ExplorationExclusionConfig {
        private int minX = -20000;
        private int maxX = 20000;
        private int minZ = -20000;
        private int maxZ = 20000;
        private int safetyBuffer = 1000;

        public int getMinX() { return minX; }
        public int getMaxX() { return maxX; }
        public int getMinZ() { return minZ; }
        public int getMaxZ() { return maxZ; }
        public int getSafetyBuffer() { return safetyBuffer; }

        public void setMinX(int minX) { this.minX = minX; }
        public void setMaxX(int maxX) { this.maxX = maxX; }
        public void setMinZ(int minZ) { this.minZ = minZ; }
        public void setMaxZ(int maxZ) { this.maxZ = maxZ; }
        public void setSafetyBuffer(int safetyBuffer) { this.safetyBuffer = safetyBuffer; }
    }

    public static class BiomeSearchConfig {
        private int minimumMatchPercentage = 60;
        private int sampleGridSize = 5;
        private int maximumCandidateSlots = 100;
        private int maximumSearchRadiusBlocks = 120000;

        public int getMinimumMatchPercentage() { return minimumMatchPercentage; }
        public int getSampleGridSize() { return sampleGridSize; }
        public int getMaximumCandidateSlots() { return maximumCandidateSlots; }
        public int getMaximumSearchRadiusBlocks() { return maximumSearchRadiusBlocks; }

        public void setMinimumMatchPercentage(int minimumMatchPercentage) { this.minimumMatchPercentage = minimumMatchPercentage; }
        public void setSampleGridSize(int sampleGridSize) { this.sampleGridSize = sampleGridSize; }
        public void setMaximumCandidateSlots(int maximumCandidateSlots) { this.maximumCandidateSlots = maximumCandidateSlots; }
        public void setMaximumSearchRadiusBlocks(int maximumSearchRadiusBlocks) { this.maximumSearchRadiusBlocks = maximumSearchRadiusBlocks; }
    }

    public static class SchedulerConfig {
        private int maxActiveRequests = 1;
        private int maxCandidateEvaluationsPerTick = 1;
        private int maxPreparationChunksPerTick = 1;
        private int requestTimeoutSeconds = 180;
        private int reservationLeaseSeconds = 300;
        private int creationCooldownSeconds = 60;
        private int homeTeleportCooldownSeconds = 30;

        public int getMaxActiveRequests() { return maxActiveRequests; }
        public int getMaxCandidateEvaluationsPerTick() { return maxCandidateEvaluationsPerTick; }
        public int getMaxPreparationChunksPerTick() { return maxPreparationChunksPerTick; }
        public int getRequestTimeoutSeconds() { return requestTimeoutSeconds; }
        public int getReservationLeaseSeconds() { return reservationLeaseSeconds; }
        public int getCreationCooldownSeconds() { return creationCooldownSeconds; }
        public int getHomeTeleportCooldownSeconds() { return homeTeleportCooldownSeconds; }

        public void setMaxActiveRequests(int maxActiveRequests) { this.maxActiveRequests = maxActiveRequests; }
        public void setMaxCandidateEvaluationsPerTick(int maxCandidateEvaluationsPerTick) { this.maxCandidateEvaluationsPerTick = maxCandidateEvaluationsPerTick; }
        public void setMaxPreparationChunksPerTick(int maxPreparationChunksPerTick) { this.maxPreparationChunksPerTick = maxPreparationChunksPerTick; }
        public void setRequestTimeoutSeconds(int requestTimeoutSeconds) { this.requestTimeoutSeconds = requestTimeoutSeconds; }
        public void setReservationLeaseSeconds(int reservationLeaseSeconds) { this.reservationLeaseSeconds = reservationLeaseSeconds; }
        public void setCreationCooldownSeconds(int creationCooldownSeconds) { this.creationCooldownSeconds = creationCooldownSeconds; }
        public void setHomeTeleportCooldownSeconds(int homeTeleportCooldownSeconds) { this.homeTeleportCooldownSeconds = homeTeleportCooldownSeconds; }
    }

    public static class BiomeOptionConfig {
        private String displayName;
        private List<String> aliases;
        private List<String> acceptedBiomeIds;

        public BiomeOptionConfig() {}

        public BiomeOptionConfig(String displayName, List<String> aliases, List<String> acceptedBiomeIds) {
            this.displayName = displayName;
            this.aliases = aliases;
            this.acceptedBiomeIds = acceptedBiomeIds;
        }

        public String getDisplayName() { return displayName; }
        public List<String> getAliases() { return aliases; }
        public List<String> getAcceptedBiomeIds() { return acceptedBiomeIds; }

        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public void setAliases(List<String> aliases) { this.aliases = aliases; }
        public void setAcceptedBiomeIds(List<String> acceptedBiomeIds) { this.acceptedBiomeIds = acceptedBiomeIds; }
    }

    public int getSchemaVersion() { return schemaVersion; }
    public DefaultPriorities getDefaultPriorities() { return defaultPriorities; }
    public Permissions getPermissions() { return permissions; }
    public Defaults getDefaults() { return defaults; }
    public PlayerRegionsConfig getPlayerRegions() { return playerRegions; }
    public PlayerLandAllocationConfig getPlayerLandAllocation() { return playerLandAllocation; }
    public Map<String, BiomeOptionConfig> getBiomeOptions() { return biomeOptions; }
}
