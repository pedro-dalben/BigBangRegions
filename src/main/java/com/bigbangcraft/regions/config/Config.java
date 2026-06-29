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
    private int schemaVersion = 1;
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
            Arrays.asList("minecraft:forest", "minecraft:birch_forest", "minecraft:old_growth_birch_forest"),
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
        biomeOptions.put("oceano", new BiomeOptionConfig("Oceano",
            Arrays.asList("ocean", "oceano", "mar"),
            Arrays.asList("minecraft:ocean", "minecraft:deep_ocean", "minecraft:cold_ocean",
                "minecraft:deep_cold_ocean", "minecraft:lukewarm_ocean", "minecraft:deep_lukewarm_ocean",
                "minecraft:warm_ocean", "minecraft:frozen_ocean", "minecraft:deep_frozen_ocean"),
            "minecraft:water_bucket"
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
                "minecraft:snowy_taiga", "minecraft:grove", "minecraft:jagged_peaks"),
            "minecraft:snow_block"
        ));
        biomeOptions.put("cerejeira", new BiomeOptionConfig("Cerejeira",
            Arrays.asList("cherry", "cerejeira", "cereja", "cherry_grove"),
            Arrays.asList("minecraft:cherry_grove", "minecraft:meadow"),
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
        private int initialClaimSize = 128;
        private int slotSize = 512;
        private int futureMaximumClaimSize = 240;
        private int slotInternalMargin = 8;
        private int maxRegionsPerOwner = 1;
        private ExplorationExclusionConfig explorationExclusion = new ExplorationExclusionConfig();
        private BiomeSearchConfig biomeSearch = new BiomeSearchConfig();
        private SchedulerConfig scheduler = new SchedulerConfig();
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
        public ExplorationExclusionConfig getExplorationExclusion() { return explorationExclusion; }
        public BiomeSearchConfig getBiomeSearch() { return biomeSearch; }
        public SchedulerConfig getScheduler() { return scheduler; }
        public NotificationsConfig getNotifications() { return notifications; }
        public PaymentConfig getPayment() { return payment; }
        public BorderConfig getBorder() { return border; }

        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public void setTargetDimension(String targetDimension) { this.targetDimension = targetDimension; }
        public void setInitialClaimSize(int initialClaimSize) { this.initialClaimSize = initialClaimSize; }
        public void setSlotSize(int slotSize) { this.slotSize = slotSize; }
        public void setFutureMaximumClaimSize(int futureMaximumClaimSize) { this.futureMaximumClaimSize = futureMaximumClaimSize; }
        public void setSlotInternalMargin(int slotInternalMargin) { this.slotInternalMargin = slotInternalMargin; }
        public void setMaxRegionsPerOwner(int maxRegionsPerOwner) { this.maxRegionsPerOwner = maxRegionsPerOwner; }
    }

    public static class NotificationsConfig {
        private boolean entryExitEnabled = true;
        private boolean otherPlayerEntryEnabled = false;

        public boolean isEntryExitEnabled() { return entryExitEnabled; }
        public boolean isOtherPlayerEntryEnabled() { return otherPlayerEntryEnabled; }

        public void setEntryExitEnabled(boolean val) { this.entryExitEnabled = val; }
        public void setOtherPlayerEntryEnabled(boolean val) { this.otherPlayerEntryEnabled = val; }
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
        private int minX = -10000;
        private int maxX = 10000;
        private int minZ = -10000;
        private int maxZ = 10000;
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
        private int maxCandidateEvaluationsPerTick = 256;
        private int maxPreparationChunksPerTick = 1;
        private int requestTimeoutSeconds = 180;
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
    public DefaultPriorities getDefaultPriorities() { return defaultPriorities; }
    public Permissions getPermissions() { return permissions; }
    public Defaults getDefaults() { return defaults; }
    public PlayerRegionsConfig getPlayerRegions() { return playerRegions; }
    public PlayerLandAllocationConfig getPlayerLandAllocation() { return playerLandAllocation; }
    public RegionExpansionConfig getRegionExpansion() { return regionExpansion; }
    public Map<String, BiomeOptionConfig> getBiomeOptions() { return biomeOptions; }
    public JourneyMapConfig getJourneyMap() { return journeyMap; }
}
