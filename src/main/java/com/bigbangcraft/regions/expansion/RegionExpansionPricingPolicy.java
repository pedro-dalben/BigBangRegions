package com.bigbangcraft.regions.expansion;

import com.bigbangcraft.regions.config.Config;
import com.bigbangcraft.regions.domain.RegionBounds;
import java.util.List;

public class RegionExpansionPricingPolicy {
    private static final int POLICY_VERSION = 1;

    private final Config.RegionExpansionConfig config;

    public RegionExpansionPricingPolicy(Config.RegionExpansionConfig config) {
        this.config = config;
    }

    public boolean isSizeAllowed(int targetSize) {
        List<Integer> allowed = config.getAllowedSizes();
        return allowed.contains(targetSize);
    }

    public RegionExpansionQuote calculateQuote(int currentSize, int targetSize) {
        if (targetSize <= currentSize) {
            return RegionExpansionQuote.reject(
                "Tamanho alvo (" + targetSize + ") deve ser maior que o tamanho atual (" + currentSize + ")",
                "EXPANSION_TARGET_NOT_LARGER"
            );
        }

        if (!isSizeAllowed(targetSize)) {
            return RegionExpansionQuote.reject(
                "Tamanho " + targetSize + " nao esta na lista de tamanhos permitidos",
                "EXPANSION_SIZE_NOT_ALLOWED"
            );
        }

        long additionalBlocks = (long) targetSize * targetSize - (long) currentSize * currentSize;
        long pricePerBlock = config.getPricePerAddedBlock();
        long priceGems = additionalBlocks * pricePerBlock;

        return RegionExpansionQuote.accept(additionalBlocks, priceGems, pricePerBlock, POLICY_VERSION);
    }

    public RegionExpansionQuote calculateQuote(RegionBounds current, RegionBounds target) {
        long currentArea = area(current);
        long targetArea = area(target);
        if (targetArea <= currentArea) {
            return RegionExpansionQuote.reject("A nova área deve ser maior que a atual", "EXPANSION_TARGET_NOT_LARGER");
        }
        long additionalBlocks = targetArea - currentArea;
        return RegionExpansionQuote.accept(additionalBlocks,
            additionalBlocks * config.getPricePerAddedBlock(),
            config.getPricePerAddedBlock(), POLICY_VERSION);
    }

    private static long area(RegionBounds bounds) {
        return ((long) bounds.getMaxX() - bounds.getMinX() + 1)
            * ((long) bounds.getMaxZ() - bounds.getMinZ() + 1);
    }
}
