# BigBangRegions ↔ BigBangEssentials Gems audit

## Baseline

The audit fetched both remotes on 2026-07-26. Remote master was Regions `8212290950a993e11f1b67816dfe185dcfa53e95` and Essentials `e4290b3497c3764f1b64f503770feaa55e9c1ee1`. The Regions checkout also contained the user's two clean local commits `3121e44` and `c5c42bf`; Essentials was aligned with remote master.

## Reproduced root causes

- Regions constructed the adapter during `ModInitializer.onInitialize` and treated early `isGemsEnabled() == false` as permanent unavailability.
- Essentials exposed a static service whose SQL-backed readiness depended on a later server lifecycle event.
- Regions selected an arbitrary lexicographically last local JAR and fell back to an old build.
- Regions imported manager/service/domain implementation classes instead of a supported API.
- The expansion coordinator called synchronous provider and SQLite work from tick/command/recovery paths and had no durable in-flight guard.
- `IDEMPOTENCY_CONFLICT` was mapped to transient retry; renew/capture/release used the expansion UUID as actor UUID.
- Reloading an operation discarded `renew_sequence` and `retry_count`.

## Repair

Essentials now publishes a small readiness/capability/async Gems facade and retries optional database initialization after a temporary startup failure. Regions pins one exact compile-only API JAR, keeps the adapter behind the optional class-loading boundary, refreshes readiness with bounded backoff, persists checkpoint keys, serializes SQLite expansion work off the server thread, and keeps one in-flight request per checkpoint. Conflict/linkage/data-integrity failures block for reconciliation; post-resize payment failures never release or shrink.

## Validation evidence

- Regions `./gradlew clean test build --no-daemon` passed against the exact Essentials API artifact `1.0.2.6+build.1189`.
- Essentials `./gradlew :common:test --no-daemon` and `./gradlew build --no-daemon` passed after the public API and MySQL datasource changes.
- Essentials MySQL/Testcontainers coverage passed for migrations, concurrent balances, idempotency, reservations, capture, release, expiry, and conservation.
- A real Fabric dedicated-server boot loaded both mods with MySQL 8.4. The log showed `WAITING_FOR_DATABASE`, then `DatabaseManager ... State: READY`, then Gems `READY (API v1, database=MYSQL)` and Regions `Payment gateway status: READY` without a restart.
- The paired Fabric server executed expansion `37d76e13-98f5-4d1c-8ace-a112f6160564` for player `99f0a0b2-37a3-3506-ab3f-7ba635ed7c58`: 80x80 → 100x100 for exactly 3,600 Gems. MySQL recorded 100,000 total / 3,600 held / 96,400 available during reservation, then 96,400 total / 0 held / 96,400 available after capture; the reservation ended `CAPTURED` and the SQLite operation ended `COMPLETED`.
- The same paired server was restarted with the completed SQLite and MySQL state. Startup recovered 0 pending expansions, returned the gateway to `READY`, and preserved the completed operation, 100x100 bounds (`-49..50`, `-57..42`), captured reservation, and 96,400 available Gems.
- The generated Regions jar was checked for `com/pedrodalben/bigbangessentials/**`; no Essentials implementation classes were bundled.

## Smoke-test limitation

The smoke server did not include the optional LuckPerms runtime jar, so Essentials Rankup reported its unrelated missing-class diagnostic; the Gems/database lifecycle and paired payment path still reached `READY` and completed successfully. Restart-at-every-checkpoint behavior is covered by the state-machine and provider tests; the Fabric smoke directly covered post-completion restart.
