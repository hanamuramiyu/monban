# Changelog

All notable user-facing changes to monban will be documented in this file.

## 3.4.0 — 2026-11-23

Security and reliability update. No new gameplay features beyond opt-out bStats metrics and the update notification.

### Breaking changes

- **Backend synchronization protocol is now version 4.** 3.4.0 and 3.3.0 builds reject each other's snapshots. Update the Velocity proxy and every synchronized backend together; mixed installations fail closed without monban permissions. Rolling the whole network back to 3.3.0 remains possible.
- A partial `identity.floodgate` section without `enabled` now means **disabled** (was enabled). Set `enabled: true` explicitly for Geyser/Floodgate networks.
- 3.4.0 always writes a `metrics` section to `config.yml`. Downgrading to 3.3.0 requires deleting that section first.

### Security

- Bukkit backend identity is fail-closed by default: startup is refused when authenticated Velocity forwarding cannot be verified. `backend-permissions.allow-unverified-identity` is the explicit, warning-producing opt-in.
- Paper refuses the combination of Velocity modern forwarding and legacy `settings.bungeecord: true`; legacy forwarding is not trusted.
- Backend synchronization now persists an epoch and a watermark before applying a snapshot. Replayed or stale snapshots are rejected after restarts.
- `state-revision` and the sync watermark use durable writes (fsync). A failed watermark write denies the snapshot; a failed revision write does not block broadcast but is logged.
- Velocity below 3.5.1 is rejected at startup with a clear message instead of a `NoSuchMethodError`.

### Configuration

- New `metrics.enabled` (default `true`) controls the bundled bStats integration. It can also be disabled globally in `plugins/bStats/config.yml`.
- `identity.floodgate.enabled` is the only switch that enables Floodgate; `require-provider` is inert while disabled.
- Version matrix: minimum Spigot/Paper/Folia `1.21`, Velocity `3.5.1`, Java `21`; recommended line `1.21.11`. Paper 26.x compiles against the API but requires Java 25 and remains runtime-unverified. Purpur and drop-in Bukkit/Paper-compatible forks are supported; hybrid forks are not.

### Reliability and performance

- State revision and durable `state-revision` writes now happen only when the synchronized state actually changes.
- Equal-revision snapshots are handled idempotently: identical content is a no-op, a missing/diverged backend state is repaired.
- Removed dead `ConnectionIdentityEnvelope` and `FileBackendServerConfigLoader`.
- Player-group deletion removes assignments before the group, avoiding orphaned assignments on failure.

### Diagnostics

- `/monban` help uses the real plugin version on every platform.
- Rate-limited WARN messages for rejected snapshots, with reason codes and counters.
- A newer release is reported after full server startup using Modrinth, with GitHub Releases as fallback; the message links to `https://modrinth.com/plugin/monban`.
- bStats reports anonymous usage data by default. No player names, UUIDs, IPs, chat, commands, permissions, or configuration files are sent.

## 3.3.0 — 2026-10-22

Geyser and Floodgate update.

### Highlights

- Velocity resolves authenticated Bedrock identities through Floodgate and treats them as `FLOODGATE` rather than Java `ONLINE` or `OFFLINE` identities.
- Floodgate names use the normalized Java username reported by the Floodgate API, including the configured prefix and space replacement, so default `.Name` identities work correctly.
- Floodgate is an optional Velocity dependency, ensuring correct plugin load order when installed without making it mandatory for Java-only networks.
- New and upgraded Velocity configurations show `identity.floodgate` explicitly instead of relying on hidden defaults.
- Floodgate integration is disabled by default and must be explicitly enabled for Geyser networks.
- Floodgate administration is available for whitelist, scoped access, groups, and direct permissions, with XUID retained as optional metadata.
- Once a provider has been discovered, provider failures deny the connection instead of silently downgrading a Bedrock login to a Java identity.
- With `require-provider: false`, an undiscovered Floodgate API leaves Java resolution active; `require-provider: true` turns that condition into a startup failure.

## 3.2.0 — 2026-08-28

Player groups and permissions update.

### Highlights

- Built-in player groups separate from Velocity `SERVER_GROUP` definitions.
- Group and direct player permissions with `NETWORK`, `SERVER_GROUP`, and `SERVER` scopes.
- Group ACL grants combined with the existing direct whitelist and scoped access grants.
- Effective access lookup showing groups, permissions, and their origins.
- Velocity administration commands for groups, assignments, group permissions, and direct permissions.
- Centralized state synchronization from Velocity to Bukkit and Paper backends.
- Local Bukkit/Paper permission attachments without requiring LuckPerms.
- Fail-closed synchronized permission state until the first verified snapshot is received.
- Automatic `sync.yml` creation on Velocity when backend permissions are enabled.
- Correct ONLINE/OFFLINE identity detection on forwarded hybrid backend connections.
- Backend permission attachments are applied before command visibility is sent to players, with command-tree refresh after permission updates.

## 3.1.0 — 2026-08-22

Administration & UX update.

### Highlights

- Shared Adventure presentation layer for whitelist, access, help, and status output.
- Interactive scoped-access pagination with filter-preserving navigation.
- Automatic ONLINE profile resolution through the official Mojang profile endpoint.
- Explicit UUID input remains available and bypasses profile lookup.
- Permission-aware `/monban` help on supported management commands.
- Runtime `/monban whitelist enable` and `/monban whitelist disable` on Bukkit/Spigot, Paper/Folia, and Velocity.
- Atomic persistence of runtime whitelist policy changes with runtime state updated only after a successful save.
- Velocity `/monban lookup <player>` with ONLINE and OFFLINE identity and whitelist status details.

## 3.0.0 — 2026-08-12

Major rewrite of monban with redesigned access control and restored Velocity support.

### Highlights

- Standalone whitelist support for Bukkit, Spigot, Paper, and Folia.
- Network-wide access control for Velocity.
- Explicit `ONLINE` and `OFFLINE` Minecraft identities.
- Unified `/monban whitelist` administration across supported platforms.
- `SERVER_GROUP` and `SERVER` access grants on Velocity.
- Backend access policies for Velocity networks.
- Velocity `/monban access` administration.
- Velocity `/monban status` operator summary.
- Optional hybrid `ONLINE` / `OFFLINE` authentication on Velocity.
- Fail-closed access enforcement and native whitelist conflict protection.
