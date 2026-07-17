-- Version: 14
-- Description: Reset visitor flags to clean up leaked permissions from old broken policy service.
--               With the fixed role-gate, visitor-* flags only gate VISITOR role.
--               Owner/member always bypass via RegionRolePolicy.isAllowed().
--               Existing explicit ALLOW values from the old era would still leak to visitors,
--               so we clear them. Also cleans up old flag aliases that map to visitor-usage.
DELETE FROM region_flags WHERE flag IN (
    'visitor-build', 'visitor-usage', 'visitor-item-frames', 'visitor-armor-stands',
    'visitor-pcs', 'visitor-containers', 'visitor-buttons', 'visitor-levers',
    'visitor-redstone', 'piston-movement'
);
