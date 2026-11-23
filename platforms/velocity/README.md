# Velocity

This module is monban's network access-authority plugin for Velocity.

## Responsibilities

- create a Velocity-specific first-run configuration with `deployment.mode: VELOCITY`;
- resolve authenticated player identity after Velocity authentication;
- enforce the network whitelist before a player connects to a backend;
- enforce scoped backend access before server connection;
- manage player groups, group ACL, and group or direct player permissions;
- provide proxy permissions from effective monban player state;
- resolve authenticated Bedrock players through an optional Floodgate provider;
- distribute signed access and permission state to configured Bukkit/Paper backends;
- support optional hybrid `ONLINE` / `OFFLINE` authentication-flow selection;
- fail closed when access verification cannot be completed safely.

Minecraft identity forwarding remains the responsibility of Velocity and the backend forwarding configuration. The monban state channel is separate from identity forwarding and uses its own shared HMAC secret configured in `sync.yml`.

Backends that need to apply permissions must also install the matching monban Bukkit or Paper build. Enable `backend-permissions` in the backend's `config.yml` and set its `server-name` to the exact registered-server name from Velocity.

When `backend-permissions.enabled` is true, this plugin creates `plugins/monban/sync.yml` automatically. Copy that file to each enabled backend. Keep whitelist, group, and permission administration on Velocity; centralized backends do not provide a second local administration path. The proxy keeps `state-revision` and `state-epoch` (both created automatically); do not copy them to backends. Update Velocity and all synchronized backends together: mixed monban versions reject each other's state snapshots and backends fail closed without monban permissions.

## Geyser and Floodgate

The Velocity build declares Floodgate as an optional dependency, so Java-only networks do not need it and Floodgate is loaded first when installed. Enable the integration in `plugins/monban/config.yml`:

```yaml
identity:
  mode: AUTO
  floodgate:
    enabled: true
    require-provider: true
```

Floodgate is disabled by default. When enabled, monban asks the Floodgate API whether the connecting UUID belongs to a Bedrock player. A matching player becomes a `FLOODGATE` identity whose normalized Floodgate Java username is the durable access key; XUID is optional metadata. For unlinked accounts this is normally the server-visible name with the configured prefix.

After discovery, a provider failure denies the connection instead of falling back to a Java identity. With `require-provider: false`, an API that cannot be discovered at startup leaves Java resolution active. Set `require-provider: true` when Bedrock admission must not run without the provider.

The optional dependency uses plugin ID `floodgate`. `require-provider: true` additionally makes startup fail if the Floodgate API cannot be discovered. This is the recommended setting for a network whose admission policy depends on Bedrock identity.

Use the name reported to monban by Floodgate, including any configured prefix such as the default `.`:

```text
/monban whitelist add floodgate .BedrockName
```

The same `floodgate` identity selector is available in scoped access, group assignments, and direct permission commands. Paper and Bukkit backends do not resolve Floodgate identities. Install a backend monban build only when that backend must apply synchronized state or permissions to Java identities; Floodgate grants remain on Velocity.

## Velocity version requirement

This build requires Velocity `3.5.1` or newer. Access verification calls `LoginEvent.getServerIdHash()`, which was added in the Velocity 3.5 API line. At startup, monban verifies that the method is available; on an older proxy it refuses to initialize with a clear error and keeps login access denied, rather than failing later with `NoSuchMethodError` during a player login.

## Java baseline

This module targets Java 21 bytecode, matching the rest of monban. Platform runtime requirements remain separate from the plugin bytecode target.

## bStats metrics

This build reports anonymous usage statistics through bStats service ID `35533`. Metrics are enabled by default. Disable them with `metrics.enabled: false` in `config.yml`, or globally in `plugins/bStats/config.yml`. bStats is shaded and relocated into the JAR; no player names, UUIDs, IP addresses, chat, or configuration files are collected. See the [metric page](https://bstats.org/plugin/velocity/monban/35533) and the [bStats privacy policy](https://bstats.org/privacy-policy). Bundled license notices are in [`THIRD_PARTY_NOTICES.md`](../../THIRD_PARTY_NOTICES.md) and inside the JAR at `META-INF/THIRD_PARTY_NOTICES.md`.
