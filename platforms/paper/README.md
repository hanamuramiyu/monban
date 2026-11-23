# Paper / Folia

This module provides monban's native integration for Paper and Folia servers.

## Packaging

- The artifact is packaged as a Paper plugin using `paper-plugin.yml`.
- It is intentionally Paper/Folia-only and is separate from the Bukkit/Spigot compatibility artifact.
- Bukkit/Spigot servers must use `monban-bukkit-spigot` instead.
- The plugin descriptor declares `folia-supported: true`.

## Folia compatibility

Paper/Folia platform code must remain safe under Folia's regionized execution model:

- do not assume a single global main thread for world or entity work;
- run world and location work in the correct region context;
- run entity work in the correct entity context;
- use the global scheduler for global work;
- use the async scheduler for tick-independent asynchronous work.

Declaring `folia-supported: true` only allows Folia to load the plugin; the implementation must still obey the platform's region and threading rules.

## Deployment

The Paper/Folia plugin supports `deployment.mode: STANDALONE`. It can run on backend servers behind Velocity using the server's normal Velocity forwarding configuration, but network-wide monban access control is handled by `monban-velocity`.

Configuring `deployment.mode: VELOCITY` on the Paper/Folia plugin is rejected.

When the server is a backend in a Velocity network, monban can apply synchronized player-group and direct permissions locally. Install the Velocity monban build as the network authority, install this build on the backend, enable `backend-permissions` in `config.yml`, set the backend name, and copy the generated `sync.yml` from Velocity. In centralized mode local whitelist administration is disabled.

Floodgate identity resolution belongs to the Velocity build. This Paper/Folia plugin does not inspect Floodgate state or create a `FLOODGATE` identity. Synchronized state applies to the Java `ONLINE` or `OFFLINE` identity that the backend can resolve; Floodgate grants are evaluated on Velocity and are not re-matched as `FLOODGATE` on the backend.

For a backend registered in Velocity as `lobby`, use:

```yaml
backend-permissions:
  enabled: true
  server-name: lobby
```

Use the same `plugins/monban/sync.yml` contents on Velocity and the backend. `server-groups.yml` is created automatically. If `SERVER_GROUP` permissions are used, configure the same server-to-group mapping there as on Velocity. `state-revision` and `state-epoch` are Velocity files and are not needed on the backend; the backend keeps its own `sync-watermark.yml`, created automatically. Velocity and all synchronized backends must run the same monban version: mixed versions reject each other's state snapshots and the backend fails closed without monban permissions.

## Backend identity forwarding

With `backend-permissions.enabled: true` this build requires Velocity modern forwarding and refuses to start when the identity source cannot be trusted:

- `settings.bungeecord: true` in `spigot.yml` (legacy BungeeCord forwarding) is refused even when modern forwarding is enabled. Paper must not accept the legacy BungeeCord handshake while modern forwarding is enabled, and a direct connection on the legacy path can present any UUID. Remove `settings.bungeecord: true` and restart the server.
- When `spigot.yml` cannot be read, monban cannot exclude legacy forwarding and refuses to start as well. Make `spigot.yml` readable with `settings.bungeecord: false`.

Both refusals are fail-closed: the backend port must stay reachable only from Velocity, and monban never applies permissions from an identity it cannot trust. There is no opt-in that bypasses this check on Paper.

Backend permissions can be checked with Chunky:

```text
/monban group moderator permission add server lobby chunky.command.start
/monban group moderator permission add server lobby chunky.command.cancel
```

After assigning the group to a player and reconnecting, `/chunky ` should show the permitted subcommands and `/chunky start` should execute.

Whitelist administration uses Paper/Folia schedulers directly at the platform boundary; there is no generic scheduler abstraction.

## bStats metrics

This build reports anonymous usage statistics through bStats service ID `35532` (shared with the Bukkit/Spigot build). Metrics are enabled by default. Disable them with `metrics.enabled: false` in `config.yml`, or globally in `plugins/bStats/config.yml`. bStats is shaded and relocated into the JAR; no player names, UUIDs, IP addresses, chat, or configuration files are collected. See the [metric page](https://bstats.org/plugin/bukkit/monban/35532) and the [bStats privacy policy](https://bstats.org/privacy-policy). Bundled license notices are in [`THIRD_PARTY_NOTICES.md`](../../THIRD_PARTY_NOTICES.md) and inside the JAR at `META-INF/THIRD_PARTY_NOTICES.md`.
