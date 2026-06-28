package com.bigbangcraft.regions.allocation;

import java.util.List;

public class BiomeOption {
    private final String key;
    private final String displayName;
    private final List<String> aliases;
    private final List<String> acceptedBiomeIds;

    private final String icon;

    public BiomeOption(String key, String displayName, List<String> aliases, List<String> acceptedBiomeIds, String icon) {
        this.key = key;
        this.displayName = displayName;
        this.aliases = aliases;
        this.acceptedBiomeIds = acceptedBiomeIds;
        this.icon = icon;
    }

    public String getKey() { return key; }
    public String getDisplayName() { return displayName; }
    public List<String> getAliases() { return aliases; }
    public List<String> getAcceptedBiomeIds() { return acceptedBiomeIds; }
    public String getIcon() { return icon; }

    public boolean matches(String query) {
        if (query == null) return false;
        String q = query.toLowerCase();
        if (key.equalsIgnoreCase(q)) return true;
        for (String alias : aliases) {
            if (alias.equalsIgnoreCase(q)) return true;
        }
        return false;
    }
}
