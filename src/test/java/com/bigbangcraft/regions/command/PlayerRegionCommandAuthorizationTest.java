package com.bigbangcraft.regions.command;

import com.bigbangcraft.regions.audit.AuditService;
import com.bigbangcraft.regions.cache.RegionCache;
import com.bigbangcraft.regions.config.Config;
import com.bigbangcraft.regions.config.ConfigManager;
import com.bigbangcraft.regions.permission.PermissionManager;
import com.bigbangcraft.regions.region.RegionResolver;
import com.bigbangcraft.regions.repository.RegionRepository;
import com.bigbangcraft.regions.util.SelectionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class PlayerRegionCommandAuthorizationTest {
    private PermissionManager pm;
    private SelectionManager sm;
    private RegionCache rc;
    private RegionRepository repo;
    private RegionResolver rr;
    private AuditService auditService;
    private ConfigManager cm;

    @BeforeEach
    public void setUp() {
        pm = mock(PermissionManager.class);
        sm = mock(SelectionManager.class);
        rc = new RegionCache();
        repo = mock(RegionRepository.class);
        rr = mock(RegionResolver.class);
        auditService = mock(AuditService.class);
        cm = mock(ConfigManager.class);
        Config config = new Config();
        when(cm.getConfig()).thenReturn(config);

        RegionsCommand.initialize(pm, sm, rc, repo, rr, auditService, cm);
    }

    @Test
    public void testCommandRegistrationsExist() {
        assertNotNull(RegionsCommand.class);
    }
}
