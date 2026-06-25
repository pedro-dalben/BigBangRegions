package com.bigbangcraft.regions.protection;

import com.bigbangcraft.regions.domain.Region;

public class ProtectionResult {
    private final ProtectionDecision decision;
    private final String reason;
    private final Region region;
    private final String flag;

    public ProtectionResult(ProtectionDecision decision, String reason, Region region, String flag) {
        this.decision = decision;
        this.reason = reason;
        this.region = region;
        this.flag = flag;
    }

    public ProtectionDecision getDecision() {
        return decision;
    }

    public String getReason() {
        return reason;
    }

    public Region getRegion() {
        return region;
    }

    public String getFlag() {
        return flag;
    }

    public boolean isAllowed() {
        return decision == ProtectionDecision.ALLOW || decision == ProtectionDecision.BYPASS || decision == ProtectionDecision.NO_REGION;
    }
}
