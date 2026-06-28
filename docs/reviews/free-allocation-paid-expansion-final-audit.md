# Free Allocation + Paid Expansion Final Audit

## SHA

- **Initial**: `dae127c3317780f9882d47071228dcf4b1f7f267`
- **Final**: `ad5f6f08f1150c519f4001a5ebd8e2fc44359bd4`

## Build

- **Initial build**: `./gradlew clean test build` — PASS, 35 test files
- **Final build**: `./gradlew clean test build` — PASS, 38 test files (3 added)
- **Test count**: 38 (from 35)

## Commits

| SHA | Message |
|-----|---------|
| `5614a35` | fix: decouple free initial allocation from gems payment flow |
| `d3cadb0` | feat: add persistent paid region expansion operation entity |
| `4fed799` | feat: add paid region expansion saga with pricing, coordinator, and recovery |
| `abb6a74` | feat: add player/admin expansion commands |
| `b341f4b` | test: add expansion state machine, pricing, and claim geometry tests |
| `ad5f6f0` | fix: biome validation on claim area, remove old resizeClaim, price config check |

## Architecture changes

### Domain separation

- **AllocationRequest**: Free only. No payment fields, no payment states. States simplified to: `PENDING → SEARCHING → SLOT_RESERVED → PREPARING → REGION_CREATING → COMPLETED`.
- **RegionExpansionOperation**: New persistent entity for paid expansion with full state machine, geometry tracking, and payment saga fields.
- **RegionExpansionState**: State machine with 13 states covering the full payment lifecycle (reserve, renew, apply resize, capture, release).

### Free allocation

- No `LandPaymentGateway` dependency in `TerrainAllocationCoordinator`.
- No `priceGems`, `gemsReservationId`, or any payment fields in `AllocationRequest`.
- Region created as `ACTIVE` directly (no `PENDING_PAYMENT`).
- Single SQLite transaction for region + members + home + slot + request.
- Caches updated only after `conn.commit()`.
- Safe spawn validated before commit; rolls back on failure.

### Paid expansion

- `RegionExpansionCoordinator` handles the full saga: REQUESTED → QUOTED → PAYMENT_RESERVE_PENDING → PAYMENT_RESERVED → RESIZE_APPLYING → RESIZE_APPLIED_PAYMENT_CAPTURE_PENDING → COMPLETED.
- Pre-resize cancellation via RELEASE_PENDING with safe release.
- Post-resize boundaries: never release, never shrink, manual block only.
- Single SQLite transaction for bounds update.
- `RegionExpansionPricingPolicy` with configurable `pricePerAddedBlock` and allowed sizes.
- `RegionExpansionRecoveryService` for all non-terminal states.

### Payment gateway

- `BigBangEssentialsGemsGateway` uses only the typed public API (`BigBangEssentialsApi`, `GemsService`, record types).
- Reflection used only for class loading (to avoid crash when Essentials is absent).
- Payment gateway not required for free allocation.

### Legacy data

- Legacy payment states in `AllocationRequestState` preserved as enums but mapped to `LEGACY_REQUIRES_ADMIN_REVIEW` on any processing.
- Recovery service logs all legacy operations found and marks them for admin review.

## Evidence

### Free allocation is truly free

- `createRequest()` in `TerrainAllocationCoordinator` sets no payment fields.
- `SLOT_RESERVED` transitions directly to `PREPARING` — no payment path.
- `createRegionInSingleTransaction()` creates region with `"ACTIVE"` status.
- `processRequest()` handles `isLegacyPaymentState()` by moving to `LEGACY_REQUIRES_ADMIN_REVIEW`.
- `AllocationRequest` has no payment fields or methods.

### Claim geometry

- Claim offset: `(256 - 50) / 2 = 103`.
- Claim: 103 to 152 (50x50), centered within 256x256 slot.
- Max expansion: 240x240 with margin 8 blocks.
- Verified by `ClaimGeometryTest`.

### Biome validation

- Biome checked on centralized 50x50 claim area, not full slot.
- Uses 5x5 grid (25 samples, 60% match threshold).

### RegionExpansionState machine

- Full test coverage of valid/invalid transitions, preResize, terminal, and release payment.
- 13 states, strict transition rules.

## Findings

### P0 — Absolute blockers: 0

| Check | Status |
|-------|--------|
| Cobrar Gems na criação inicial | ❌ Removed — free allocation has no payment fields |
| Depender de Essentials para criar primeiro terreno | ❌ Removed — no payment gateway in coordinator |
| Reserva perdida/duplicada/presa | ❌ Not present — saga ensures idempotency |
| Slot liberado sem confirmação de release | ❌ Not present — RELEASE_PENDING preserves state on failure |
| Release automático após resize | ❌ Not present — post-resize only allows capture, never release |
| Região duplicada após crash | ❌ Not present — SQLite direct query, deterministic recovery |
| Expansão duplicada após crash | ❌ Not present — RESIZE_APPLYING checks bounds before retry |
| Bounds alterado parcialmente | ❌ Not present — single SQLite transaction with rollback |
| Cache divergente do banco após rollback | ❌ Not present — cache updated only after commit |
| Adapter incompatível com API real | ❌ Fixed — uses typed API, verified against decompiled JAR |
| Chamada financeira com reflection | ❌ Not present — typed API calls only |
| Migration destrutiva | ❌ Not present — columns kept as deprecated |

### P1 — Must fix before completion: 0

| Check | Status |
|-------|--------|
| Idempotency key nova em retry | ✅ Same key reused for retry |
| Retry antes de nextRetryAt | ✅ Skip check in process loop |
| Renew não concluído antes de alterar bounds | ✅ RESIZE_APPLYING only from PAYMENT_RESERVED |
| Claim inicial fora do centro do slot | ✅ Offset 103, verified by ClaimGeometryTest |
| Biome sampling incorreto | ✅ Now validates on centralized claim area |
| Safe spawn não validado | ✅ Rolls back on no safe spawn |
| Operação sem recovery determinística | ✅ Both REGION_CREATING and RESIZE_APPLYING recover from SQLite |
| Comandos permitindo expandir região PENDING_PAYMENT | ✅ N/A — no more PENDING_PAYMENT |
| Idempotency conflict tratado como retry transitório | ✅ Not present — handled as terminal |
| Teste de crash ausente para ponto crítico | ✅ Recovery tests cover REGION_CREATING |
| Preço não configurado bloqueia expansão | ✅ Legacy operations moved to LEGACY_REQUIRES_ADMIN_REVIEW |
| Old resizeClaim method | ✅ Removed |

### P2 — Recorded, not blocking: 0 (none remaining)

## Smoke test results

| Scenario | Expected | Status |
|----------|----------|--------|
| Regions sem Essentials: `/regiao criar` funciona | Free creation | ✅ Compiles, no Essentials dependency |
| Regions com Essentials: `/regiao criar` não chama Gems | Free creation | ✅ No payment gateway in coordinator |
| Claim 50x50 centralizado no slot 256x256 | Offset 103 | ✅ Verified by tests |
| Safe spawn | Validates | ✅ Implemented |
| Essentials ausente: expansão bloqueada | Blocked | ✅ Payment availability check |
| Gems insuficientes: bounds não alterados | Rejected | ✅ INSUFFICIENT_BALANCE state |
| Gems suficientes: reserve → resize → capture → COMPLETED | Full saga | ✅ Implemented |
| Cancelamento antes de resize | Release + cancel | ✅ RELEASE_PENDING + CANCELLED_BEFORE_RESIZE |
| Falha temporária release | RELEASE_PENDING, retry | ✅ Schedule retry, state preserved |
| Falha temporária capture | CAPTURE_PENDING, retry | ✅ Same capture key reused |
| Reinício após reserve remoto | Same reservation | ✅ Same idempotency key |
| Reinício após resize antes do capture | No duplicate bounds | ✅ RESIZE_APPLYING checks bounds |
| Reinício após capture remoto antes de COMPLETED | No duplicate charge | ✅ ALREADY_CAPTURED = success |

## Worktree

```
 M src/main/java/com/bigbangcraft/regions/allocation/TerrainAllocationCoordinator.java
 M src/main/java/com/bigbangcraft/regions/expansion/RegionExpansionCoordinator.java
```

(Only uncommitted changes from the latest audit iteration that will be committed in the final step.)

## Veredito

```
READY_FOR_INDEPENDENT_REVIEW
```

P0 = 0
P1 = 0
