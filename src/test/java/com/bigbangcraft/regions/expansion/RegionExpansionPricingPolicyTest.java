package com.bigbangcraft.regions.expansion;

import com.bigbangcraft.regions.config.Config;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class RegionExpansionPricingPolicyTest {
    private Config.RegionExpansionConfig config;
    private RegionExpansionPricingPolicy policy;

    @BeforeEach
    public void setUp() {
        config = new Config.RegionExpansionConfig();
        config.setEnabled(true);
        config.setPricePerAddedBlock(5);
        config.setAllowedSizes(java.util.List.of(75, 100, 125));
        policy = new RegionExpansionPricingPolicy(config);
    }

    @Test
    public void testSizeAllowed() {
        assertTrue(policy.isSizeAllowed(75));
        assertTrue(policy.isSizeAllowed(100));
        assertTrue(policy.isSizeAllowed(125));
        assertFalse(policy.isSizeAllowed(50));
        assertFalse(policy.isSizeAllowed(150));
        assertFalse(policy.isSizeAllowed(240));
    }

    @Test
    public void testCalculateQuoteAccepted() {
        RegionExpansionQuote quote = policy.calculateQuote(50, 75);
        assertTrue(quote.isAccepted());
        // additionalBlocks = 75^2 - 50^2 = 5625 - 2500 = 3125
        // price = 3125 * 5 = 15625
        assertEquals(3125, quote.getAdditionalBlocks());
        assertEquals(15625, quote.getPriceGems());
        assertEquals(5, quote.getPricePerBlock());
        assertEquals(1, quote.getPolicyVersion());
    }

    @Test
    public void testCalculateQuoteLargerExpansion() {
        RegionExpansionQuote quote = policy.calculateQuote(50, 100);
        assertTrue(quote.isAccepted());
        // additionalBlocks = 100^2 - 50^2 = 10000 - 2500 = 7500
        // price = 7500 * 5 = 37500
        assertEquals(7500, quote.getAdditionalBlocks());
        assertEquals(37500, quote.getPriceGems());
    }

    @Test
    public void testCalculateQuoteZeroPrice() {
        config.setPricePerAddedBlock(0);
        policy = new RegionExpansionPricingPolicy(config);
        RegionExpansionQuote quote = policy.calculateQuote(50, 75);
        assertTrue(quote.isAccepted());
        assertEquals(0, quote.getPriceGems());
    }

    @Test
    public void testCalculateQuoteRejectedSameSize() {
        RegionExpansionQuote quote = policy.calculateQuote(50, 50);
        assertFalse(quote.isAccepted());
        assertNotNull(quote.getRejectionReason());
        assertEquals("EXPANSION_TARGET_NOT_LARGER", quote.getFailureCode());
    }

    @Test
    public void testCalculateQuoteRejectedSmallerSize() {
        RegionExpansionQuote quote = policy.calculateQuote(75, 50);
        assertFalse(quote.isAccepted());
        assertEquals("EXPANSION_TARGET_NOT_LARGER", quote.getFailureCode());
    }

    @Test
    public void testCalculateQuoteRejectedSizeNotAllowed() {
        RegionExpansionQuote quote = policy.calculateQuote(50, 150);
        assertFalse(quote.isAccepted());
        assertEquals("EXPANSION_SIZE_NOT_ALLOWED", quote.getFailureCode());
    }
}
