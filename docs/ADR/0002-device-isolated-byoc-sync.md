# ADR 0002: Device-Isolated Append-Only Log BYOC Storage Protocol

**Status**: Accepted  
**Date**: 2026-10-14  
**Context**: ClassFlow implements a Bring Your Own Cloud (BYOC) model where users configure personal object storage (Qiniu Cloud Kodo, AWS S3, Cloudflare R2, MinIO). Commodity object storage lacks distributed locking, atomic conditional multi-file commits, and ACID transactions.

---

## Decision

We reject naive single-file overwrite sync (`/sync/data.json`) and centralized Last-Write-Wins (LWW) file replacement.

Instead:
1. Each registered device writes exclusively to its own namespaced path: `devices/{deviceId}/revisions/{seq}_{uuid}.json`.
2. Devices maintain an append-only sequence of immutable change operations (`SyncOperation`).
3. Each device maintains a local `device_manifest.json` pointing to its latest revision head and optional compacted snapshot.
4. Peer synchronization proceeds by listing device folders under `devices/`, fetching unread peer revisions, and executing deterministic local 3-way merge or vector clock reconciliation.
5. In the event of conflicting simultaneous edits on the same attribute, Hybrid Logical Clocks (HLC) pick a deterministic winner while preserving a conflict tag for optional user review.

---

## Consequences

### Positive
- Immune to multi-device write collisions on object storage buckets.
- Zero risk of a slow device silently overwriting a newer device's database snapshot.
- Fully supports offline modification, incremental delta transfers, and audit logs.

### Negative / Trade-offs
- Reading remote state requires listing device prefixes and pulling multiple delta objects.
- Requires periodic snapshot compaction (`snapshot_seqN.json`) to prevent revision file sprawl.
