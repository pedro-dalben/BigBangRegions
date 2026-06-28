package com.bigbangcraft.regions.expansion;

public class RegionExpansionQuote {
    private final boolean accepted;
    private final long additionalBlocks;
    private final long priceGems;
    private final long pricePerBlock;
    private final int policyVersion;
    private final String rejectionReason;
    private final String failureCode;

    private RegionExpansionQuote(boolean accepted, long additionalBlocks, long priceGems,
                                  long pricePerBlock, int policyVersion,
                                  String rejectionReason, String failureCode) {
        this.accepted = accepted;
        this.additionalBlocks = additionalBlocks;
        this.priceGems = priceGems;
        this.pricePerBlock = pricePerBlock;
        this.policyVersion = policyVersion;
        this.rejectionReason = rejectionReason;
        this.failureCode = failureCode;
    }

    public static RegionExpansionQuote accept(long additionalBlocks, long priceGems,
                                               long pricePerBlock, int policyVersion) {
        return new RegionExpansionQuote(true, additionalBlocks, priceGems,
            pricePerBlock, policyVersion, null, null);
    }

    public static RegionExpansionQuote reject(String reason, String failureCode) {
        return new RegionExpansionQuote(false, 0, 0, 0, 0, reason, failureCode);
    }

    public boolean isAccepted() { return accepted; }
    public long getAdditionalBlocks() { return additionalBlocks; }
    public long getPriceGems() { return priceGems; }
    public long getPricePerBlock() { return pricePerBlock; }
    public int getPolicyVersion() { return policyVersion; }
    public String getRejectionReason() { return rejectionReason; }
    public String getFailureCode() { return failureCode; }
}
