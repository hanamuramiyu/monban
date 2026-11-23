# Bukkit / Spigot

This module provides monban's compatibility build for Bukkit and Spigot servers.

## Packaging

- The artifact is packaged as a classic Bukkit plugin using `plugin.yml`.
- It is the compatibility artifact: Paper and Folia servers should use `monban-paper-folia` instead.
- The plugin descriptor is generated from `src/main/resources/plugin.yml` with the project version substituted at build time.

## Standalone

The build supports `deployment.mode: STANDALONE`. It enforces the monban whitelist on player pre-login and provides `/monban whitelist ...` administration. Set the native Minecraft whitelist to `white-list=false`; monban refuses to run while the vanilla whitelist is enabled.

Configuring `deployment.mode: VELOCITY` on this build is rejected. Network-wide access control belongs to `monban-velocity`.

## Backend mode

With `backend-permissions.enabled: true`, the server applies synchronized player-group and direct permissions locally instead of enforcing its own whitelist. Install `monban-velocity` as the network authority, copy the generated `sync.yml` from Velocity, and set `server-name` to the exact Velocity server name:

```yaml
backend-permissions:
  enabled: true
  server-name: lobby
```

The backend keeps its own `sync-watermark.yml`, created automatically. Velocity and all synchronized backends must run the same monban version: mixed versions reject each other's state snapshots and the backend fails closed without monban permissions.

## Identity trust

Bukkit and Spigot APIs cannot prove that a player identity came from the proxy. Before applying backend permissions, monban therefore checks whether the server actually runs on Paper with Velocity modern forwarding enabled.

- Paper with Velocity modern forwarding: compatibility fallback only; prefer `monban-paper-folia`. monban verifies that forwarding is enabled.
- Paper with Velocity modern forwarding **and** `settings.bungeecord: true`: refused unless `allow-unverified-identity: true`; monban cannot confirm that Paper rejects the legacy handshake while modern forwarding is enabled, so the combination is untrusted. Remove the legacy flag.
- `settings.bungeecord: true` (legacy BungeeCord forwarding): refused unless `allow-unverified-identity: true`; the UUID is unauthenticated. BungeeCord is not a supported proxy.
- No forwarding declared, or the configuration cannot be read: refused unless `allow-unverified-identity: true`.

A Spigot backend can only start with backend permissions after the operator states that the port is reachable only from the proxy:

```yaml
backend-permissions:
  enabled: true
  server-name: lobby
  # WARNING: monban cannot verify proxy identity on Bukkit/Spigot.
  # Enable only while this backend is reachable only from the proxy.
  allow-unverified-identity: true
```

The option is an acknowledgement, not a verification: monban writes a warning on every start when it cannot confirm authenticated proxy forwarding and the option is enabled, and an informational message when authenticated proxy forwarding is confirmed. Keep the backend port closed to everything except the proxy. The option is rejected in `deployment.mode: VELOCITY` and has no effect while `backend-permissions.enabled` is `false`.

## bStats metrics

This build reports anonymous usage statistics through bStats service ID `35532` (shared with the Paper/Folia build). Metrics are enabled by default. Disable them with `metrics.enabled: false` in `config.yml`, or globally in `plugins/bStats/config.yml`. bStats is shaded and relocated into the JAR; no player names, UUIDs, IP addresses, chat, or configuration files are collected. See the [metric page](https://bstats.org/plugin/bukkit/monban/35532) and the [bStats privacy policy](https://bstats.org/privacy-policy). Bundled license notices are in [`THIRD_PARTY_NOTICES.md`](../../THIRD_PARTY_NOTICES.md) and inside the JAR at `META-INF/THIRD_PARTY_NOTICES.md`.
