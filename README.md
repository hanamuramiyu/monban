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

monban plugin bytecode targets Java 21. Server runtime requirements are platform/version-specific; in particular, Paper `26.1+` requires Java 25 to run.

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

The backend must use the same `sync.yml` contents as Velocity. In centralized mode, whitelist, group, and permission administration is performed on Velocity. The backend does not manage a separate local whitelist. `server-groups.yml` is created automatically on the backend. If `SERVER_GROUP` permissions are used, configure the same server-to-group mapping there as on Velocity; otherwise it can remain empty. `state-revision` belongs to Velocity and must not be copied.

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

## Building

```bash
./gradlew clean build
```

## License

monban is licensed under the [Mozilla Public License 2.0](LICENSE).
