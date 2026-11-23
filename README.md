<div align="center">

<img src="logo.png" alt="monban">

# monban

Minecraft access control for standalone servers and Velocity networks.

[Modrinth](https://modrinth.com/plugin/monban) · [Documentation](https://monban.miyu.pw/) · [GitHub Releases](https://github.com/hanamuramiyu/monban/releases)

</div>

monban is a whitelist, access-control, and permissions plugin with explicit `ONLINE`, `OFFLINE`, and `FLOODGATE` player identities. It supports standalone Bukkit/Spigot and Paper/Folia servers, and can act as the central access authority for a Velocity network with Geyser/Floodgate support, player groups, scoped backend access, and synchronized backend permissions.

## Supported platforms

| Platform | Deployment | Global whitelist | Player permissions | Scoped backend access | Hybrid auth | Floodgate identity |
| --- | --- | :---: | :---: | :---: | :---: | :---: |
| Bukkit / Spigot | Standalone or backend | ✓ | ✓ | — | — | — |
| Paper / Folia | Standalone or backend | ✓ | ✓ | — | — | — |
| Velocity | Network authority | ✓ | ✓ | ✓ | ✓ | ✓ |

The Paper/Folia artifact is a Paper plugin and is separate from the Bukkit/Spigot compatibility artifact. Hybrid authentication-flow selection belongs to Velocity; the Paper/Folia plugin does not implement `deployment.mode: VELOCITY`.

## Features

- Explicit `ONLINE`, `OFFLINE`, and `FLOODGATE` identities instead of inferring trust from the presence of a UUID.
- Optional Geyser/Floodgate integration on Velocity for authenticated Bedrock players.
- Player groups separate from Velocity server groups.
- Group and direct player permissions for proxy and backend plugins.
- `NETWORK`, `SERVER_GROUP`, and `SERVER` scopes for ACL and permissions.
- One `/monban whitelist` administration model across supported platforms.
- Network-wide whitelist enforcement on Velocity.
- Positive `SERVER_GROUP` and `SERVER` access grants on Velocity.
- Per-backend `OPEN` and `GRANT_REQUIRED` policies.
- Centralized Velocity state synchronized to configured Bukkit/Paper backends.
- Local Bukkit/Paper permissions without requiring LuckPerms.
- Effective access inspection with group and direct-grant origins.
- Optional Velocity hybrid `ONLINE` / `OFFLINE` authentication-flow selection.
- Floodgate administration across whitelist, scoped access, groups, and direct permissions.
- Native Folia-compatible scheduling at the Paper platform boundary.
- Strict, versioned YAML configuration and persistent access state.
- Fail-closed startup and access verification for security-sensitive initialization failures.

## Requirements

monban plugin bytecode targets Java 21. Minimum supported versions: Spigot/Paper/Folia `1.21`, Velocity `3.5.1`, Java `21`. The recommended 3.4.0 line is `1.21.11` (Java 21). Paper `26.x` is API-compatible and compiles, but requires Java 25 and has not been runtime-verified; it is not officially recommended until the release runtime checklist passes.

On Velocity older than `3.5.1`, monban refuses to initialize during proxy startup and keeps login access denied, instead of failing later during player login.

Spigot, Paper, Purpur and other drop-in Bukkit/Paper-compatible forks are supported; Spigot and Paper are tested individually, forks are "API-compatible, no individual guarantees". Hybrid forks (Mohist, Arclight, Cardboard), Fabric/NeoForge, BungeeCord, and Minecraft/API below 1.21 are unsupported. Velocity 4.x is officially unsupported in 3.4.0; it may be tried, but no guarantees.

## Install

1. Download the build for your platform from [Modrinth](https://modrinth.com/plugin/monban) or [GitHub Releases](https://github.com/hanamuramiyu/monban/releases).
2. Put the JAR in the platform's `plugins/` directory.
3. For standalone Bukkit/Spigot or Paper/Folia, set the native Minecraft whitelist to `white-list=false`.
4. Start the server once, configure `plugins/monban/config.yml`, then restart.
5. Manage the global whitelist with `/monban whitelist ...`; `enable` and `disable` can be used at runtime. Velocity additionally provides `/monban lookup ...`, `/monban access ...`, `/monban group ...`, `/monban user ...`, and `/monban status`.

## Geyser and Floodgate

Geyser and Floodgate support is available on Velocity. Geyser accepts Bedrock connections, Floodgate supplies the authenticated Bedrock identity, and monban evaluates that connection as a `FLOODGATE` identity. The Bukkit and Paper builds continue to use `ONLINE` and `OFFLINE` identities for Java players; they do not resolve Floodgate identities themselves.

Install Geyser, Floodgate, and monban on the proxy. Enable Floodgate in the generated Velocity configuration:

```yaml
identity:
  mode: AUTO
  floodgate:
    enabled: true
    require-provider: true
```

Floodgate integration is disabled by default. `require-provider: true` is recommended when Bedrock access control is part of the production network: monban will fail visibly at startup instead of running without the expected Floodgate provider.

Use the normalized Floodgate Java username reported to monban, including its configured prefix, when adding a Bedrock player. For an unlinked Bedrock account this is normally the server-visible name:

```text
/monban whitelist add floodgate .BedrockName
```

The normalized name is the durable access key. An optional XUID may be supplied with `/monban whitelist add floodgate <name> <xuid>`, but it remains metadata and does not change identity matching. A Floodgate name change therefore changes the access key.

For a Velocity network with backend permissions, install monban on Velocity and on every Bukkit/Paper backend that must apply local permissions. Enable `backend-permissions` on the proxy; monban creates `plugins/monban/sync.yml` automatically. Copy that file to each enabled backend and set the backend's `server-name` in `config.yml` to the exact Velocity server name.

Velocity:

```yaml
backend-permissions:
  enabled: true
```

Backend `lobby`:

```yaml
backend-permissions:
  enabled: true
  server-name: lobby
```

The backend must use the same `sync.yml` contents as Velocity. In centralized mode, whitelist, group, and permission administration is performed on Velocity. The backend does not manage a separate local whitelist. `server-groups.yml` is created automatically on the backend. If `SERVER_GROUP` permissions are used, configure the same server-to-group mapping there as on Velocity; otherwise it can remain empty. `state-revision` and `state-epoch` belong to Velocity and must not be copied.

State synchronization uses a versioned payload protocol. monban 3.4.0 speaks protocol version 4 and adds `state-epoch` on Velocity and `sync-watermark.yml` on each backend (both created automatically). **This is a breaking protocol change:** 3.4.0 and 3.3.0 builds reject each other's snapshots by protocol version, so mixed installations are incompatible in both directions. Update the Velocity proxy and all synchronized backends together; until then a backend receives no state and fails closed without monban permissions. Rolling the whole network back to 3.3.0 works: `state-epoch` and `sync-watermark.yml` are ignored by older builds.

Synchronized backend permissions are attached to the Java `ONLINE` or `OFFLINE` identity that the backend can resolve. Floodgate groups and direct permissions are evaluated by Velocity for admission and proxy permissions; they are not re-matched as `FLOODGATE` identities on a backend.

For a basic backend permission check, grant Chunky permissions from Velocity:

```text
/monban group create moderator
/monban group moderator permission add server lobby chunky.command.start
/monban group moderator permission add server lobby chunky.command.cancel
/monban user hanamuramiyu offline group add moderator
```

Reconnect the player and check `/chunky ` tab completion, then run `/chunky start` or `/chunky cancel` as appropriate.

Full setup, configuration, command, storage, Geyser/Floodgate, and Velocity documentation is available at **[monban.miyu.pw](https://monban.miyu.pw/)**.

## Backend permissions

Backend permissions apply permission nodes for the player identity that the backend server sees. That identity is trustworthy only when the backend accepts it from Velocity through authenticated forwarding, and when the backend port is reachable from nowhere else.

Network isolation is a security requirement, not a recommendation. A backend port that accepts direct connections lets a client present any player UUID, and monban would attach that player's permission nodes to the attacker. For this reason monban refuses to start with `backend-permissions.enabled: true` when it cannot confirm a trusted identity source.

| Build and server | `backend-permissions.enabled: true` |
| --- | --- |
| `monban-paper-folia` on Paper with Velocity modern forwarding | Supported. monban verifies that Velocity forwarding is enabled. |
| `monban-paper-folia` on Paper with Velocity modern forwarding and `settings.bungeecord: true` | Refused. Paper must not accept the legacy BungeeCord handshake while Velocity modern forwarding is enabled, so the combination cannot be trusted. Remove `settings.bungeecord: true` from `spigot.yml`. |
| `monban-bukkit-spigot` on Paper with Velocity modern forwarding | Compatibility fallback only; prefer `monban-paper-folia`. monban verifies that Velocity forwarding is enabled. |
| `monban-bukkit-spigot` on Paper with Velocity modern forwarding and `settings.bungeecord: true` | Refused unless `allow-unverified-identity: true`. monban cannot confirm that Paper rejects the legacy handshake while modern forwarding is enabled, so the combination is untrusted. Remove the legacy flag. |
| `monban-bukkit-spigot` on Spigot with `settings.bungeecord: true` | Refused unless `allow-unverified-identity: true`. Legacy BungeeCord forwarding carries an unauthenticated UUID from the connection handshake. |
| `monban-bukkit-spigot` on Spigot without forwarding | Refused unless `allow-unverified-identity: true`. The Bukkit/Spigot API cannot confirm that the identity came from the proxy. |
| `monban-velocity` on the proxy | Not applicable. `allow-unverified-identity` is rejected in `deployment.mode: VELOCITY`. |

A Spigot backend has no shared secret with the proxy and therefore cannot prove the identity source. If the backend port is firewalled so that only Velocity can reach it, acknowledge the remaining risk explicitly:

```yaml
backend-permissions:
  enabled: true
  server-name: lobby
  # WARNING: monban cannot verify proxy identity on Bukkit/Spigot.
  # Enable only while this backend is reachable only from the proxy.
  allow-unverified-identity: true
```

`allow-unverified-identity` only allows the server to start; it does not make the identity verified. monban writes a warning on every start when it cannot confirm authenticated proxy forwarding and the option is enabled. When Velocity modern forwarding is confirmed, monban logs an informational message instead. Keep the backend port closed to everything except the proxy, for example with a firewall rule or a loopback-only `server-port` that only Velocity connects to.

## Metrics and privacy

monban includes [bStats](https://bstats.org), an anonymous usage-statistics service. Metrics are enabled by default and are collected by the shaded, relocated copy of bStats bundled in the JAR. bStats reports only aggregated, non-identifying data:

- monban (plugin) version;
- server software name and version (Bukkit/Spigot, Paper/Folia, or Velocity);
- Java version and operating-system name, architecture and version;
- CPU core count;
- online player count (a number);
- the server `online-mode` flag;
- number of managed backend servers (Velocity only);
- a random, persistent installation UUID that bStats assigns to the server and stores in `plugins/bStats/config.yml`.

bStats does **not** collect player names, player UUIDs, player IP addresses, chat, commands, permissions, `config.yml`, `sync.yml`, any other configuration or state file, or server names. The only network traffic is a single HTTPS POST to bStats, so the service sees the source IP of the server as part of ordinary HTTP transport. See the [bStats privacy policy](https://bstats.org/privacy-policy).

Disable metrics in either of two ways:

- set `metrics.enabled: false` in `plugins/monban/config.yml`;
- disable bStats globally in `plugins/bStats/config.yml`.

The public metric pages are [Bukkit/Paper](https://bstats.org/plugin/bukkit/monban/35532) and [Velocity](https://bstats.org/plugin/velocity/monban/35533). bStats is MIT-licensed; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for the bundled third-party license notices.

> Rollback note: 3.4.0 always writes a `metrics` section to `config.yml`. Older monban versions (3.3.0 and earlier) reject unknown root fields, so if you downgrade to 3.3.0 you must delete the `metrics` section from `config.yml` first. Otherwise monban refuses to start (`Unknown field at root.metrics`) and denies all logins until the section is removed.

## Update check

After the server or proxy has finished starting, monban performs a single asynchronous version check and logs a message when a newer release is available. The check queries the [Modrinth API](https://api.modrinth.com/v2/project/monban/version) first and falls back to the [GitHub Releases API](https://api.github.com/repos/hanamuramiyu/monban/releases/latest). Requests use a five-second timeout and the `monban/<version>` user agent; no server, player, or configuration data is sent. If neither source is reachable or returns a usable version, the check is skipped silently. When a newer version exists, the log includes the version and the installation link: <https://modrinth.com/plugin/monban>.

## Building

```bash
./gradlew clean build
```

## License

monban is licensed under the [Mozilla Public License 2.0](LICENSE).
