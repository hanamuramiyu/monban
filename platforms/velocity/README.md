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

When `backend-permissions.enabled` is true, this plugin creates `plugins/monban/sync.yml` automatically. Copy that file to each enabled backend. Keep whitelist, group, and permission administration on Velocity; centralized backends do not provide a second local administration path.

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

## Java baseline

This module targets Java 21 bytecode, matching the rest of monban. Platform runtime requirements remain separate from the plugin bytecode target.
