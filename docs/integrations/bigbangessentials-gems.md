# BigBangEssentials Gems integration

Paid expansion is the only Regions feature that uses Gems. Initial terrain allocation remains free and does not load this adapter.

## Supported contract

Regions is compiled against the exact, compile-only artifact configured by `bigbangessentials_api_version` and `bigbangessentials_api_jar`. The supported public API is `BigBangEssentialsApi.gemsIntegration()` and `com.pedrodalben.bigbangessentials.api.gems`; Regions does not use Gems managers, repositories, SQL classes, or domain implementation classes.

The API reports `WAITING_FOR_DATABASE`, `READY`, `DISABLED`, `TEMPORARILY_UNAVAILABLE`, `SHUTTING_DOWN`, or `FAILED`. Reserve, renew, capture, release, balance, and lookup are asynchronous. The adapter retries readiness with bounded backoff; it never replaces an installed-but-starting provider with a permanent no-op gateway.

## Deployment

1. Build BigBangEssentials `common` and record the exact `bigbangessentials-common-<version>.jar`.
2. Set the matching `bigbangessentials_api_version` or pass `-Pbigbangessentials_api_jar=/absolute/path/to/the/exact.jar` when building Regions.
3. Install both loader jars and set `regionExpansion.paymentProvider` to `bigbangessentials`, with a positive `pricePerAddedBlock`. Configure Gems with `enabled: true`, backend `DATABASE`, and a reachable MySQL server.
4. Start the server and verify `DatabaseManager initialized successfully`, Gems `READY`, and Regions `Payment gateway status: READY`.

The Regions jar must not contain `com/pedrodalben/bigbangessentials/**`. A missing or incompatible API jar is a build/runtime error shown as `API_INCOMPATIBLE`; it is not retried as a database outage.

## Payment saga

Each expansion persists its checkpoint and stable key before submitting work:

`QUOTED -> PAYMENT_RESERVE_PENDING -> PAYMENT_RESERVED -> [PAYMENT_RENEW_PENDING] -> RESIZE_APPLYING -> RESIZE_APPLIED_PAYMENT_CAPTURE_PENDING -> COMPLETED`.

Cancellation before resize enters `RELEASE_PENDING` and releases the reservation with the same key. Capture and release replays are success-equivalent. An idempotency conflict, API incompatibility, data-integrity error, or post-resize expiry enters manual reconciliation; the region is never shrunk automatically.

Reserve uses the player UUID as owner, the expansion UUID as external reference, `bigbangregions` as source, and `player_region_expansion` as purpose. Renew, capture, and release use the player UUID as actor. Keys are bounded, deterministic, operation-specific, and persisted in SQLite.

## Diagnostics

Use `/regions admin payment status` (permission `bigbangregions.admin.expansion`). Pending operations use the existing `/regions expansion inspect <operation-id>`, `/regions expansion list`, `/regions expansion retry <operation-id>`, and `/regions expansion reconcile` commands.

## Verified paired smoke

With BigBangEssentials `1.0.2.6+build.1189`, MySQL 8.4, Gems enabled, and Regions' SQLite database, a real Fabric server completed an 80x80 → 100x100 expansion for 3,600 Gems. The wallet moved from 100,000 total to 96,400 total, held 3,600 only during reservation, and finished with no active reservation. Restarting the same server preserved `COMPLETED`, the expanded bounds, and the captured reservation.
