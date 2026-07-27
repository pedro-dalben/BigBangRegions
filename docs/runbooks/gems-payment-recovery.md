# Gems payment recovery runbook

1. Run `/regions admin payment status`. `WAITING_FOR_DATABASE` means wait for the Essentials database retry; `API_INCOMPATIBLE` requires the exact API jar to be rebuilt and deployed; `DISABLED` is configuration, not a transient outage.
2. Inspect the operation with `/regions expansion inspect <operation-id>`.
3. Before resize, `PAYMENT_RESERVED`, `PAYMENT_RENEW_PENDING`, and `RELEASE_PENDING` may be retried. Release is idempotent and does not charge the player.
4. After resize, never release Gems and never shrink the region. `RESIZE_APPLIED_PAYMENT_CAPTURE_PENDING` retries capture with its persisted key; `BLOCKED_FOR_MANUAL_RECONCILIATION` requires checking the Gems reservation and operation ledger in BigBangEssentials.
5. Use `/regions expansion reconcile` after restoring MySQL. Do not edit balances or mark an expansion complete by hand.

## Restart and outage rules

The reserve, renew, capture, and release keys survive restart. A lost response is retried with the same key, so the provider returns the original result. A temporary MySQL outage is retryable; an idempotency conflict is terminal/manual. Player online status is irrelevant to recovery.

## Rollback

Stop the server, retain the Regions SQLite database and the Essentials MySQL data, restore the previous pair of jars, and start with paid expansion disabled until pending operations are inspected. Never delete reservation or expansion records to make startup green.
