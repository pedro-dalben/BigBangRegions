# Virtual Pastures

When VirtualLoot is installed, BigBangRegions limits the registered block
`virtualloot:virtual_pasture`. The feature is inactive without that mod or when
the configured identifier is not registered.

```json
"virtualPasture": {
  "enabled": true,
  "blockId": "virtualloot:virtual_pasture",
  "maxPerRegion": 2,
  "maxPerPlayer": 2,
  "maxPerChunk": 1,
  "adminBypassPermission": "bigbangregions.virtualpasture.bypass",
  "limits": { "default": 2, "vip": 3 }
}
```

`maxPerPlayer` is attributed to the **region owner**, not the player who
placed the block. Members therefore share the owner's quota and cannot bypass
it by placing blocks themselves. A tier uses the permission
`bigbangregions.virtualpasture.limit.<tier>`; the `default` entry is the
fallback.

The durable index is reconciled only from chunks Minecraft has already loaded;
it never loads chunks merely to count blocks. Existing blocks are indexed as
their chunks load. Administrators can inspect or reconcile loaded chunks with:

- `/regions pastagemvirtual regiao <regionId>`
- `/regions pastagemvirtual jogador <player>`
- `/regions pastagemvirtual reconciliar`

Those commands require `bigbangregions.admin.virtualpasture`.
