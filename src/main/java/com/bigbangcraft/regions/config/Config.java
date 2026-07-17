package com.bigbangcraft.regions.config;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Config {
    public static final int BIOME_SEARCH_VALIDATION_SCHEMA_VERSION = 1;

    private int schemaVersion = 2;
    private DefaultPriorities defaultPriorities = new DefaultPriorities();
    private Permissions permissions = new Permissions();
    private Defaults defaults = new Defaults();
    private PlayerRegionsConfig playerRegions = new PlayerRegionsConfig();
    private PlayerLandAllocationConfig playerLandAllocation = new PlayerLandAllocationConfig();
    private RegionExpansionConfig regionExpansion = new RegionExpansionConfig();
    private Map<String, BiomeOptionConfig> biomeOptions = new HashMap<>();
    private Set<String> disabledCommands = new HashSet<>();

    public boolean isCommandDisabled(String command) {
        return disabledCommands.contains(command);
    }

    public Set<String> getDisabledCommands() {
        return Collections.unmodifiableSet(disabledCommands);
    }

    public Config() {
        biomeOptions.put("planicies", new BiomeOptionConfig("Planícies",
            Arrays.asList("plains", "planicie", "planicies"),
            Arrays.asList("minecraft:plains", "minecraft:sunflower_plains"),
            "minecraft:grass_block"
        ));
        biomeOptions.put("floresta", new BiomeOptionConfig("Floresta",
            Arrays.asList("forest", "floresta"),
            Arrays.asList("minecraft:forest", "minecraft:flower_forest", "minecraft:birch_forest",
                "minecraft:old_growth_birch_forest", "minecraft:dark_forest"),
            "minecraft:oak_log"
        ));
        biomeOptions.put("taiga", new BiomeOptionConfig("Taiga",
            Arrays.asList("taiga"),
            Arrays.asList("minecraft:taiga", "minecraft:old_growth_pine_taiga", "minecraft:old_growth_spruce_taiga"),
            "minecraft:spruce_log"
        ));
        biomeOptions.put("deserto", new BiomeOptionConfig("Deserto",
            Arrays.asList("desert", "deserto"),
            Arrays.asList("minecraft:desert"),
            "minecraft:sand"
        ));
        biomeOptions.put("savana", new BiomeOptionConfig("Savana",
            Arrays.asList("savanna", "savana"),
            Arrays.asList("minecraft:savanna", "minecraft:savanna_plateau", "minecraft:windswept_savanna"),
            "minecraft:acacia_log"
        ));
        biomeOptions.put("selva", new BiomeOptionConfig("Selva",
            Arrays.asList("jungle", "selva"),
            Arrays.asList("minecraft:jungle", "minecraft:sparse_jungle", "minecraft:bamboo_jungle"),
            "minecraft:jungle_log"
        ));
        biomeOptions.put("praia", new BiomeOptionConfig("Praia",
            Arrays.asList("beach", "praia", "costa"),
            Arrays.asList("minecraft:beach", "minecraft:snowy_beach", "minecraft:stony_shore"),
            "minecraft:sand"
        ));
        biomeOptions.put("montanha", new BiomeOptionConfig("Montanha",
            Arrays.asList("mountain", "montanha", "serra", "windswept", "hills"),
            Arrays.asList("minecraft:windswept_hills", "minecraft:windswept_gravelly_hills",
                "minecraft:windswept_forest", "minecraft:stony_peaks", "minecraft:windswept_peaks",
                "minecraft:windswept_savanna"),
            "minecraft:stone"
        ));
        biomeOptions.put("pantano", new BiomeOptionConfig("Pântano",
            Arrays.asList("swamp", "pantano", "mangue"),
            Arrays.asList("minecraft:swamp", "minecraft:mangrove_swamp"),
            "minecraft:lily_pad"
        ));
        biomeOptions.put("neve", new BiomeOptionConfig("Neve",
            Arrays.asList("snow", "neve", "snowy", "gelo"),
            Arrays.asList("minecraft:snowy_plains", "minecraft:ice_spikes", "minecraft:snowy_slopes",
                "minecraft:snowy_taiga", "minecraft:grove", "minecraft:jagged_peaks",
                "minecraft:frozen_peaks"),
            "minecraft:snow_block"
        ));
        biomeOptions.put("cerejeira", new BiomeOptionConfig("Cerejeira",
            Arrays.asList("cherry", "cerejeira", "cereja", "cherry_grove"),
            Arrays.asList("minecraft:cherry_grove"),
            "minecraft:cherry_sapling"
        ));
        biomeOptions.put("cogumelo", new BiomeOptionConfig("Cogumelo",
            Arrays.asList("mushroom", "cogumelo", "mushroom_fields"),
            Arrays.asList("minecraft:mushroom_fields"),
            "minecraft:red_mushroom"
        ));
        biomeOptions.put("rio", new BiomeOptionConfig("Rio",
            Arrays.asList("river", "rio"),
            Arrays.asList("minecraft:river", "minecraft:frozen_river"),
            "minecraft:seagrass"
        ));
        biomeOptions.put("costapedra", new BiomeOptionConfig("Costa de Pedra",
            Arrays.asList("stony_shore", "costapedra", "costa_pedra"),
            Arrays.asList("minecraft:stony_shore", "minecraft:stony_peaks"),
            "minecraft:stone"
        ));
        biomeOptions.put("terrasasperas", new BiomeOptionConfig("Terras Ásperas",
            Arrays.asList("badlands", "terrasasperas", "mesa", "terras_asperas"),
            Arrays.asList("minecraft:badlands", "minecraft:wooded_badlands", "minecraft:eroded_badlands"),
            "minecraft:terracotta"
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
            global.put("visitor-build", "DENY");
            global.put("visitor-usage", "DENY");
            global.put("visitor-item-frames", "DENY");
            global.put("visitor-armor-stands", "DENY");
            global.put("pvp", "ALLOW");
            global.put("fire-spread", "ALLOW");
            global.put("fire-block-damage", "ALLOW");
            global.put("water-flow", "ALLOW");
            global.put("lava-flow", "ALLOW");
            global.put("explosion-block-damage", "ALLOW");
            global.put("piston-move", "ALLOW");
            global.put("mob-griefing", "ALLOW");
            global.put("fall-damage", "ALLOW");
            global.put("leaf-decay", "ALLOW");
            global.put("ice-melt", "ALLOW");
            global.put("visitor-pickup-items", "ALLOW");
            global.put("visitor-drop-items", "ALLOW");

            // Admin Region default policies
            adminRegion.put("visitor-build", "DENY");
            adminRegion.put("visitor-usage", "DENY");
            adminRegion.put("visitor-item-frames", "DENY");
            adminRegion.put("visitor-armor-stands", "DENY");
            adminRegion.put("pvp", "DENY");
            adminRegion.put("fire-spread", "DENY");
            adminRegion.put("fire-block-damage", "DENY");
            adminRegion.put("water-flow", "DENY");
            adminRegion.put("lava-flow", "DENY");
            adminRegion.put("explosion-block-damage", "DENY");
            adminRegion.put("piston-move", "DENY");
            adminRegion.put("mob-griefing", "DENY");
            adminRegion.put("fall-damage", "DENY");
            adminRegion.put("leaf-decay", "DENY");
            adminRegion.put("ice-melt", "DENY");
            adminRegion.put("visitor-pickup-items", "ALLOW");
            adminRegion.put("visitor-drop-items", "ALLOW");

            // Player Region default policies
            playerRegion.put("visitor-build", "DENY");
            playerRegion.put("visitor-usage", "DENY");
            playerRegion.put("visitor-item-frames", "DENY");
            playerRegion.put("visitor-armor-stands", "DENY");
            playerRegion.put("pvp", "DENY");
            playerRegion.put("fire-spread", "DENY");
            playerRegion.put("fire-block-damage", "DENY");
            playerRegion.put("water-flow", "DENY");
            playerRegion.put("lava-flow", "DENY");
            playerRegion.put("explosion-block-damage", "DENY");
            playerRegion.put("piston-move", "DENY");
            playerRegion.put("mob-griefing", "DENY");
            playerRegion.put("fall-damage", "DENY");
            playerRegion.put("leaf-decay", "ALLOW");
            playerRegion.put("ice-melt", "ALLOW");
            playerRegion.put("visitor-pickup-items", "ALLOW");
            playerRegion.put("visitor-drop-items", "ALLOW");
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
        private int initialClaimSize = 80;
        private int slotSize = 512;
        private int futureMaximumClaimSize = 240;
        private int slotInternalMargin = 8;
        private int maxRegionsPerOwner = 1;
        private ExplorationExclusionConfig explorationExclusion = new ExplorationExclusionConfig();
        private BiomeSearchConfig biomeSearch = new BiomeSearchConfig();
        private WorldgenSearchConfig worldgenSearch = new WorldgenSearchConfig();
        private SchedulerConfig scheduler = new SchedulerConfig();
        private RegionPreparationConfig regionPreparation = new RegionPreparationConfig();
        private NotificationsConfig notifications = new NotificationsConfig();
        private PaymentConfig payment = new PaymentConfig();
        private BorderConfig border = new BorderConfig();

        public boolean isEnabled() { return enabled; }
        public String getTargetDimension() { return targetDimension; }
        public int getInitialClaimSize() { return initialClaimSize; }
        public int getSlotSize() { return slotSize; }
        public int getFutureMaximumClaimSize() { return futureMaximumClaimSize; }
        public int getSlotInternalMargin() { return slotInternalMargin; }
        public int getMaxRegionsPerOwner() { return maxRegionsPerOwner; }
        public ExplorationExclusionConfig getExplorationExclusion() {
            if (explorationExclusion == null) explorationExclusion = new ExplorationExclusionConfig();
            return explorationExclusion;
        }
        public BiomeSearchConfig getBiomeSearch() {
            if (biomeSearch == null) biomeSearch = new BiomeSearchConfig();
            return biomeSearch;
        }
        public WorldgenSearchConfig getWorldgenSearch() {
            if (worldgenSearch == null) worldgenSearch = new WorldgenSearchConfig();
            return worldgenSearch;
        }
        public SchedulerConfig getScheduler() {
            if (scheduler == null) scheduler = new SchedulerConfig();
            return scheduler;
        }
        public RegionPreparationConfig getRegionPreparation() {
            if (regionPreparation == null) regionPreparation = new RegionPreparationConfig();
            return regionPreparation;
        }
        public NotificationsConfig getNotifications() {
            if (notifications == null) notifications = new NotificationsConfig();
            return notifications;
        }
        public PaymentConfig getPayment() {
            if (payment == null) payment = new PaymentConfig();
            return payment;
        }
        public BorderConfig getBorder() {
            if (border == null) border = new BorderConfig();
            return border;
        }

        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public void setTargetDimension(String targetDimension) { this.targetDimension = targetDimension; }
        public void setInitialClaimSize(int initialClaimSize) { this.initialClaimSize = initialClaimSize; }
        public void setSlotSize(int slotSize) { this.slotSize = slotSize; }
        public void setFutureMaximumClaimSize(int futureMaximumClaimSize) { this.futureMaximumClaimSize = futureMaximumClaimSize; }
        public void setSlotInternalMargin(int slotInternalMargin) { this.slotInternalMargin = slotInternalMargin; }
        public void setMaxRegionsPerOwner(int maxRegionsPerOwner) { this.maxRegionsPerOwner = maxRegionsPerOwner; }
        public void setWorldgenSearch(WorldgenSearchConfig worldgenSearch) { this.worldgenSearch = worldgenSearch; }
    }

    public static class RegionPreparationConfig {
        private int maxConcurrentPreparations = 1;
        private int maxChunksPerPreparation = 9;
        private int timeoutSeconds = 45;

        public int getMaxConcurrentPreparations() { return maxConcurrentPreparations; }
        public int getMaxChunksPerPreparation() { return maxChunksPerPreparation; }
        public int getTimeoutSeconds() { return timeoutSeconds; }

        public void setMaxConcurrentPreparations(int maxConcurrentPreparations) { this.maxConcurrentPreparations = maxConcurrentPreparations; }
        public void setMaxChunksPerPreparation(int maxChunksPerPreparation) { this.maxChunksPerPreparation = maxChunksPerPreparation; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    }

    public static class NotificationsConfig {
        private boolean entryExitEnabled = true;
        private boolean otherPlayerEntryEnabled = false;
        private boolean allocationProgressEnabled = true;
        private int allocationProgressIntervalSeconds = 10;

        public boolean isEntryExitEnabled() { return entryExitEnabled; }
        public boolean isOtherPlayerEntryEnabled() { return otherPlayerEntryEnabled; }
        public boolean isAllocationProgressEnabled() { return allocationProgressEnabled; }
        public int getAllocationProgressIntervalSeconds() { return allocationProgressIntervalSeconds <= 0 ? 10 : allocationProgressIntervalSeconds; }

        public void setEntryExitEnabled(boolean val) { this.entryExitEnabled = val; }
        public void setOtherPlayerEntryEnabled(boolean val) { this.otherPlayerEntryEnabled = val; }
        public void setAllocationProgressEnabled(boolean allocationProgressEnabled) { this.allocationProgressEnabled = allocationProgressEnabled; }
        public void setAllocationProgressIntervalSeconds(int allocationProgressIntervalSeconds) { this.allocationProgressIntervalSeconds = allocationProgressIntervalSeconds; }
    }

    public static class PaymentConfig {
        private String provider = "none";
        private long reservationLeaseSeconds = 900;
        private long renewBeforeExpirySeconds = 300;
        private int maxCaptureRetriesBeforeManualBlock = 10;
        private long retryBackoffSeconds = 30;
        
        public String getProvider() { return provider; }
        public long getReservationLeaseSeconds() { return reservationLeaseSeconds; }
        public long getRenewBeforeExpirySeconds() { return renewBeforeExpirySeconds; }
        public int getMaxCaptureRetriesBeforeManualBlock() { return maxCaptureRetriesBeforeManualBlock; }
        public long getRetryBackoffSeconds() { return retryBackoffSeconds; }
        
        public void setProvider(String provider) { this.provider = provider; }
        public void setReservationLeaseSeconds(long reservationLeaseSeconds) { this.reservationLeaseSeconds = reservationLeaseSeconds; }
        public void setRenewBeforeExpirySeconds(long renewBeforeExpirySeconds) { this.renewBeforeExpirySeconds = renewBeforeExpirySeconds; }
        public void setMaxCaptureRetriesBeforeManualBlock(int maxCaptureRetriesBeforeManualBlock) { this.maxCaptureRetriesBeforeManualBlock = maxCaptureRetriesBeforeManualBlock; }
        public void setRetryBackoffSeconds(long retryBackoffSeconds) { this.retryBackoffSeconds = retryBackoffSeconds; }
        
        public boolean isPaymentRequired() {
            return false;
        }
    }

    public static class ExplorationExclusionConfig {
        private int minX = -2000;
        private int maxX = 2000;
        private int minZ = -2000;
        private int maxZ = 2000;
        private int safetyBuffer = 0;

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
        private boolean requireFullBorderMatch = false;
        private int minimumBorderMatchPercentage = 60;
        private boolean relaxedFallbackEnabled = true;
        private int relaxedMinimumMatchPercentage = 30;
        private int relaxedMinimumBorderMatchPercentage = 0;

        public int getMinimumMatchPercentage() { return minimumMatchPercentage; }
        public int getSampleGridSize() { return sampleGridSize; }
        public int getMaximumCandidateSlots() { return maximumCandidateSlots; }
        public int getMaximumSearchRadiusBlocks() { return maximumSearchRadiusBlocks; }
        public boolean isRequireFullBorderMatch() { return requireFullBorderMatch; }
        public int getMinimumBorderMatchPercentage() { return minimumBorderMatchPercentage; }
        public boolean isRelaxedFallbackEnabled() { return relaxedFallbackEnabled; }
        public int getRelaxedMinimumMatchPercentage() { return relaxedMinimumMatchPercentage; }
        public int getRelaxedMinimumBorderMatchPercentage() { return relaxedMinimumBorderMatchPercentage; }

        public void setMinimumMatchPercentage(int minimumMatchPercentage) { this.minimumMatchPercentage = minimumMatchPercentage; }
        public void setSampleGridSize(int sampleGridSize) { this.sampleGridSize = sampleGridSize; }
        public void setMaximumCandidateSlots(int maximumCandidateSlots) { this.maximumCandidateSlots = maximumCandidateSlots; }
        public void setMaximumSearchRadiusBlocks(int maximumSearchRadiusBlocks) { this.maximumSearchRadiusBlocks = maximumSearchRadiusBlocks; }
        public void setRequireFullBorderMatch(boolean requireFullBorderMatch) { this.requireFullBorderMatch = requireFullBorderMatch; }
        public void setMinimumBorderMatchPercentage(int minimumBorderMatchPercentage) { this.minimumBorderMatchPercentage = minimumBorderMatchPercentage; }
        public void setRelaxedFallbackEnabled(boolean relaxedFallbackEnabled) { this.relaxedFallbackEnabled = relaxedFallbackEnabled; }
        public void setRelaxedMinimumMatchPercentage(int relaxedMinimumMatchPercentage) { this.relaxedMinimumMatchPercentage = relaxedMinimumMatchPercentage; }
        public void setRelaxedMinimumBorderMatchPercentage(int relaxedMinimumBorderMatchPercentage) { this.relaxedMinimumBorderMatchPercentage = relaxedMinimumBorderMatchPercentage; }
    }

    public static class WorldgenSearchConfig {
        private int sampleBlockY = 64;
        private List<Integer> sampleBlockYs = List.of();
        private Integer minSampleY;
        private Integer maxSampleY;
        private int verticalCheckInterval = 0;
        private int virtualBiomeCacheMaxEntries = 50000;
        private int virtualBiomeCacheTtlSeconds = 300;
        private int sectorSizeBlocks = 2048;
        private int locateRadiusBlocks = 1300;
        private int blockCheckInterval = 64;
        private int maxLocateCallsPerSearchStep = 1;
        private int maxLocateCallsPerTick = 1;
        private long maxSearchWorkNanosPerTick = 750000L;
        private int maxSearchStepsPerTick = 1;
        private int maxSectorsPerRequest = 24;
        private int maxCandidateSlotsPerAnchor = 25;
        private boolean fallbackSpiralEnabled = false;
        private int fallbackSpiralMaxCandidates = 8;
        private List<AllocationBandConfig> allocationBands = List.of(
            new AllocationBandConfig("primary", 2000, 30000, true)
        );

        public int getSampleBlockY() { return sampleBlockY == 0 ? 64 : sampleBlockY; }
        public List<Integer> getSampleBlockYs() {
            if (sampleBlockYs != null && !sampleBlockYs.isEmpty()) {
                return sampleBlockYs;
            }
            if (minSampleY != null && maxSampleY != null && minSampleY <= maxSampleY) {
                int interval = verticalCheckInterval > 0 ? verticalCheckInterval : Math.max(1, (maxSampleY - minSampleY) / 4);
                List<Integer> ys = new java.util.ArrayList<>();
                for (int y = minSampleY; y <= maxSampleY; y += interval) {
                    ys.add(y);
                }
                if (ys.isEmpty() || ys.getLast() < maxSampleY) {
                    ys.add(maxSampleY);
                }
                return ys;
            }
            return java.util.List.of(getSampleBlockY());
        }
        public Integer getMinSampleY() { return minSampleY; }
        public Integer getMaxSampleY() { return maxSampleY; }
        public int getVerticalCheckInterval() { return verticalCheckInterval; }
        public int getVirtualBiomeCacheMaxEntries() { return virtualBiomeCacheMaxEntries <= 0 ? 50000 : virtualBiomeCacheMaxEntries; }
        public int getVirtualBiomeCacheTtlSeconds() { return virtualBiomeCacheTtlSeconds <= 0 ? 300 : virtualBiomeCacheTtlSeconds; }
        public int getSectorSizeBlocks() { return sectorSizeBlocks <= 0 ? 2048 : sectorSizeBlocks; }
        public int getLocateRadiusBlocks() { return locateRadiusBlocks <= 0 ? 1300 : locateRadiusBlocks; }
        public int getBlockCheckInterval() { return blockCheckInterval <= 0 ? 64 : blockCheckInterval; }
        public int getMaxLocateCallsPerSearchStep() { return maxLocateCallsPerSearchStep <= 0 ? 1 : maxLocateCallsPerSearchStep; }
        public int getMaxLocateCallsPerTick() { return maxLocateCallsPerTick <= 0 ? 1 : maxLocateCallsPerTick; }
        public long getMaxSearchWorkNanosPerTick() { return maxSearchWorkNanosPerTick <= 0L ? 2_500_000L : maxSearchWorkNanosPerTick; }
        public int getMaxSearchStepsPerTick() { return maxSearchStepsPerTick <= 0 ? 1 : maxSearchStepsPerTick; }
        public int getMaxSectorsPerRequest() { return maxSectorsPerRequest <= 0 ? 24 : maxSectorsPerRequest; }
        public int getMaxCandidateSlotsPerAnchor() { return maxCandidateSlotsPerAnchor <= 0 ? 25 : maxCandidateSlotsPerAnchor; }
        public boolean isFallbackSpiralEnabled() { return fallbackSpiralEnabled; }
        public int getFallbackSpiralMaxCandidates() { return fallbackSpiralMaxCandidates <= 0 ? 8 : fallbackSpiralMaxCandidates; }
        public List<AllocationBandConfig> getAllocationBands() {
            if (allocationBands == null || allocationBands.isEmpty()) {
                allocationBands = List.of(new AllocationBandConfig("primary", 2000, 30000, true));
            }
            return allocationBands;
        }

        public void setSampleBlockY(int sampleBlockY) { this.sampleBlockY = sampleBlockY; }
        public void setSampleBlockYs(List<Integer> sampleBlockYs) { this.sampleBlockYs = sampleBlockYs; }
        public void setMinSampleY(Integer minSampleY) { this.minSampleY = minSampleY; }
        public void setMaxSampleY(Integer maxSampleY) { this.maxSampleY = maxSampleY; }
        public void setVerticalCheckInterval(int verticalCheckInterval) { this.verticalCheckInterval = verticalCheckInterval; }
        public void setVirtualBiomeCacheMaxEntries(int virtualBiomeCacheMaxEntries) { this.virtualBiomeCacheMaxEntries = virtualBiomeCacheMaxEntries; }
        public void setVirtualBiomeCacheTtlSeconds(int virtualBiomeCacheTtlSeconds) { this.virtualBiomeCacheTtlSeconds = virtualBiomeCacheTtlSeconds; }
        public void setSectorSizeBlocks(int sectorSizeBlocks) { this.sectorSizeBlocks = sectorSizeBlocks; }
        public void setLocateRadiusBlocks(int locateRadiusBlocks) { this.locateRadiusBlocks = locateRadiusBlocks; }
        public void setBlockCheckInterval(int blockCheckInterval) { this.blockCheckInterval = blockCheckInterval; }
        public void setMaxLocateCallsPerSearchStep(int maxLocateCallsPerSearchStep) { this.maxLocateCallsPerSearchStep = maxLocateCallsPerSearchStep; }
        public void setMaxLocateCallsPerTick(int maxLocateCallsPerTick) { this.maxLocateCallsPerTick = maxLocateCallsPerTick; }
        public void setMaxSearchWorkNanosPerTick(long maxSearchWorkNanosPerTick) { this.maxSearchWorkNanosPerTick = maxSearchWorkNanosPerTick; }
        public void setMaxSearchStepsPerTick(int maxSearchStepsPerTick) { this.maxSearchStepsPerTick = maxSearchStepsPerTick; }
        public void setMaxSectorsPerRequest(int maxSectorsPerRequest) { this.maxSectorsPerRequest = maxSectorsPerRequest; }
        public void setMaxCandidateSlotsPerAnchor(int maxCandidateSlotsPerAnchor) { this.maxCandidateSlotsPerAnchor = maxCandidateSlotsPerAnchor; }
        public void setFallbackSpiralEnabled(boolean fallbackSpiralEnabled) { this.fallbackSpiralEnabled = fallbackSpiralEnabled; }
        public void setFallbackSpiralMaxCandidates(int fallbackSpiralMaxCandidates) { this.fallbackSpiralMaxCandidates = fallbackSpiralMaxCandidates; }
        public void setAllocationBands(List<AllocationBandConfig> allocationBands) { this.allocationBands = allocationBands; }
    }

    public static class AllocationBandConfig {
        private String id = "primary";
        private int minRadiusBlocks = 2000;
        private int maxRadiusBlocks = 30000;
        private boolean enabled = true;

        public AllocationBandConfig() {
        }

        public AllocationBandConfig(String id, int minRadiusBlocks, int maxRadiusBlocks, boolean enabled) {
            this.id = id;
            this.minRadiusBlocks = minRadiusBlocks;
            this.maxRadiusBlocks = maxRadiusBlocks;
            this.enabled = enabled;
        }

        public String getId() { return id; }
        public int getMinRadiusBlocks() { return minRadiusBlocks; }
        public int getMaxRadiusBlocks() { return maxRadiusBlocks; }
        public boolean isEnabled() { return enabled; }

        public void setId(String id) { this.id = id; }
        public void setMinRadiusBlocks(int minRadiusBlocks) { this.minRadiusBlocks = minRadiusBlocks; }
        public void setMaxRadiusBlocks(int maxRadiusBlocks) { this.maxRadiusBlocks = maxRadiusBlocks; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    public static class SchedulerConfig {
        private int maxActiveRequests = 1;
        private int maxCandidateEvaluationsPerTick = 256;
        private int maxPreparationChunksPerTick = 1;
        private int requestTimeoutSeconds = 300;
        private int reservationLeaseSeconds = 300;
        private int creationCooldownSeconds = 60;
        private int homeTeleportCooldownSeconds = 30;
        private int maxBiomeSearchMillisPerTick = 25;

        public int getMaxActiveRequests() { return maxActiveRequests; }
        public int getMaxCandidateEvaluationsPerTick() { return maxCandidateEvaluationsPerTick; }
        public int getMaxPreparationChunksPerTick() { return maxPreparationChunksPerTick; }
        public int getRequestTimeoutSeconds() { return requestTimeoutSeconds; }
        public int getReservationLeaseSeconds() { return reservationLeaseSeconds; }
        public int getCreationCooldownSeconds() { return creationCooldownSeconds; }
        public int getHomeTeleportCooldownSeconds() { return homeTeleportCooldownSeconds; }
        public int getMaxBiomeSearchMillisPerTick() { return maxBiomeSearchMillisPerTick; }

        public void setMaxActiveRequests(int maxActiveRequests) { this.maxActiveRequests = maxActiveRequests; }
        public void setMaxCandidateEvaluationsPerTick(int maxCandidateEvaluationsPerTick) { this.maxCandidateEvaluationsPerTick = maxCandidateEvaluationsPerTick; }
        public void setMaxPreparationChunksPerTick(int maxPreparationChunksPerTick) { this.maxPreparationChunksPerTick = maxPreparationChunksPerTick; }
        public void setRequestTimeoutSeconds(int requestTimeoutSeconds) { this.requestTimeoutSeconds = requestTimeoutSeconds; }
        public void setReservationLeaseSeconds(int reservationLeaseSeconds) { this.reservationLeaseSeconds = reservationLeaseSeconds; }
        public void setCreationCooldownSeconds(int creationCooldownSeconds) { this.creationCooldownSeconds = creationCooldownSeconds; }
        public void setHomeTeleportCooldownSeconds(int homeTeleportCooldownSeconds) { this.homeTeleportCooldownSeconds = homeTeleportCooldownSeconds; }
        public void setMaxBiomeSearchMillisPerTick(int maxBiomeSearchMillisPerTick) { this.maxBiomeSearchMillisPerTick = maxBiomeSearchMillisPerTick; }
    }

    public static class BiomeOptionConfig {
        private String displayName;
        private List<String> aliases;
        private List<String> acceptedBiomeIds;
        private String icon = "minecraft:map";

        public BiomeOptionConfig() {}

        public BiomeOptionConfig(String displayName, List<String> aliases, List<String> acceptedBiomeIds) {
            this.displayName = displayName;
            this.aliases = aliases;
            this.acceptedBiomeIds = acceptedBiomeIds;
        }

        public BiomeOptionConfig(String displayName, List<String> aliases, List<String> acceptedBiomeIds, String icon) {
            this.displayName = displayName;
            this.aliases = aliases;
            this.acceptedBiomeIds = acceptedBiomeIds;
            this.icon = icon;
        }

        public String getDisplayName() { return displayName; }
        public List<String> getAliases() { return aliases; }
        public List<String> getAcceptedBiomeIds() { return acceptedBiomeIds; }
        public String getIcon() { return icon; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public void setAliases(List<String> aliases) { this.aliases = aliases; }
        public void setAcceptedBiomeIds(List<String> acceptedBiomeIds) { this.acceptedBiomeIds = acceptedBiomeIds; }
        public void setIcon(String icon) { this.icon = icon; }
    }

    public static class BorderConfig {
        private String material = "minecraft:glass";
        private int thickness = 1;
        private boolean protect = true;
        private boolean createCeiling = false;
        private boolean restoreOnDelete = true;

        public String getMaterial() { return material; }
        public int getThickness() { return thickness; }
        public boolean isProtect() { return protect; }
        public boolean isCreateCeiling() { return createCeiling; }
        public boolean isRestoreOnDelete() { return restoreOnDelete; }

        public void setMaterial(String material) { this.material = material; }
        public void setThickness(int thickness) { this.thickness = thickness; }
        public void setProtect(boolean protect) { this.protect = protect; }
        public void setCreateCeiling(boolean createCeiling) { this.createCeiling = createCeiling; }
        public void setRestoreOnDelete(boolean restoreOnDelete) { this.restoreOnDelete = restoreOnDelete; }
    }



    public static class RegionExpansionConfig {
        private boolean enabled = false;
        private List<Integer> allowedSizes = java.util.List.of(75, 100, 125, 150, 175, 200, 225, 240);
        private long pricePerAddedBlock = 0;
        private String paymentProvider = "bigbangessentials";
        private long reservationLeaseSeconds = 900;
        private long renewBeforeExpirySeconds = 300;
        private long retryBackoffSeconds = 30;
        private int maxPaymentRetriesBeforeManualBlock = 10;

        public boolean isEnabled() { return enabled; }
        public List<Integer> getAllowedSizes() { return allowedSizes; }
        public long getPricePerAddedBlock() { return pricePerAddedBlock; }
        public String getPaymentProvider() { return paymentProvider; }
        public long getReservationLeaseSeconds() { return reservationLeaseSeconds; }
        public long getRenewBeforeExpirySeconds() { return renewBeforeExpirySeconds; }
        public long getRetryBackoffSeconds() { return retryBackoffSeconds; }
        public int getMaxPaymentRetriesBeforeManualBlock() { return maxPaymentRetriesBeforeManualBlock; }

        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public void setAllowedSizes(List<Integer> allowedSizes) { this.allowedSizes = allowedSizes; }
        public void setPricePerAddedBlock(long pricePerAddedBlock) { this.pricePerAddedBlock = pricePerAddedBlock; }
        public void setPaymentProvider(String paymentProvider) { this.paymentProvider = paymentProvider; }
        public void setReservationLeaseSeconds(long reservationLeaseSeconds) { this.reservationLeaseSeconds = reservationLeaseSeconds; }
        public void setRenewBeforeExpirySeconds(long renewBeforeExpirySeconds) { this.renewBeforeExpirySeconds = renewBeforeExpirySeconds; }
        public void setRetryBackoffSeconds(long retryBackoffSeconds) { this.retryBackoffSeconds = retryBackoffSeconds; }
        public void setMaxPaymentRetriesBeforeManualBlock(int maxPaymentRetriesBeforeManualBlock) { this.maxPaymentRetriesBeforeManualBlock = maxPaymentRetriesBeforeManualBlock; }

        public boolean isPaymentRequired() {
            return pricePerAddedBlock > 0;
        }
    }

    public static class JourneyMapConfig {
        private boolean enabled = true;

        private RegionStyle playerRegion = new RegionStyle(0xFF4CAF50, 0xFF4CAF50, 0.16f, 0.85f);
        private RegionStyle adminRegion = new RegionStyle(0xFFE53935, 0xFFE53935, 0.20f, 0.95f);
        private RegionStyle blockedRegion = new RegionStyle(0xFF757575, 0xFF757575, 0.12f, 0.70f);
        private RegionStyle maintenanceRegion = new RegionStyle(0xFFFF9800, 0xFFFF9800, 0.14f, 0.80f);

        private PublicRegionsConfig publicRegions = new PublicRegionsConfig();
        private AdminRegionVisibility adminRegionVisibility = AdminRegionVisibility.STAFF_ONLY;

        public enum AdminRegionVisibility {
            PUBLIC, STAFF_ONLY, HIDDEN, PERMISSION
        }

        public static class RegionStyle {
            private int fillColor = 0xFF4CAF50;
            private int strokeColor = 0xFF4CAF50;
            private float fillOpacity = 0.16f;
            private float strokeOpacity = 0.85f;

            public RegionStyle() {}

            public RegionStyle(int fillColor, int strokeColor, float fillOpacity, float strokeOpacity) {
                this.fillColor = fillColor;
                this.strokeColor = strokeColor;
                this.fillOpacity = fillOpacity;
                this.strokeOpacity = strokeOpacity;
            }

            public int getFillColor() { return fillColor; }
            public int getStrokeColor() { return strokeColor; }
            public float getFillOpacity() { return fillOpacity; }
            public float getStrokeOpacity() { return strokeOpacity; }
        }

        public static class PublicRegionsConfig {
            private boolean showOnMap = true;

            public boolean isShowOnMap() { return showOnMap; }
            public void setShowOnMap(boolean showOnMap) { this.showOnMap = showOnMap; }
        }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public RegionStyle getPlayerRegion() { return playerRegion; }
        public RegionStyle getAdminRegion() { return adminRegion; }
        public RegionStyle getBlockedRegion() { return blockedRegion; }
        public RegionStyle getMaintenanceRegion() { return maintenanceRegion; }
        public PublicRegionsConfig getPublicRegions() { return publicRegions; }
        public AdminRegionVisibility getAdminRegionVisibility() { return adminRegionVisibility; }
        public void setAdminRegionVisibility(AdminRegionVisibility v) { this.adminRegionVisibility = v; }
    }

    private JourneyMapConfig journeyMap = new JourneyMapConfig();

    public int getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(int schemaVersion) { this.schemaVersion = schemaVersion; }
    public DefaultPriorities getDefaultPriorities() { return defaultPriorities; }
    public Permissions getPermissions() { return permissions; }
    public Defaults getDefaults() { return defaults; }
    public PlayerRegionsConfig getPlayerRegions() { return playerRegions; }
    public PlayerLandAllocationConfig getPlayerLandAllocation() {
        if (playerLandAllocation == null) playerLandAllocation = new PlayerLandAllocationConfig();
        return playerLandAllocation;
    }
    public RegionExpansionConfig getRegionExpansion() { return regionExpansion; }
    public Map<String, BiomeOptionConfig> getBiomeOptions() { return biomeOptions; }
    public JourneyMapConfig getJourneyMap() { return journeyMap; }
}
