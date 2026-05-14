# FailureToLog Integrated Deployment

This folder contains the integrated build output for the FailureToLog server plugin and Fabric client companion mod.

## Build

Run:

```bat
C:\Users\musek\IdeaProjects\build-all-failure-to-log.bat
```

The script builds both projects and copies the release jars into:

```text
C:\Users\musek\IdeaProjects\dist
```

## Dist Files

- `FailureToLog-Paper-1.0-SNAPSHOT.jar`
  - Install on the Paper server in the `plugins` folder.
- `FailureToLog-API-Fabric-1.0.0.jar`
  - Install on the client in the Fabric `mods` folder.

## Server Setup

1. Stop the server.
2. Copy `FailureToLog-Paper-1.0-SNAPSHOT.jar` into the server `plugins` folder.
3. Start the server once.
4. The plugin creates its config under:

```text
plugins/FailureToLog/config.yml
```

Default config:

```yaml
enabled: true
save-logs: true
```

## Client Setup

1. Install Fabric Loader and Fabric API for Minecraft `26.1.2`.
2. Optional but supported: install Mod Menu to access the config screen.
3. Copy `FailureToLog-API-Fabric-1.0.0.jar` into the client `mods` folder.

Client config is saved under:

```text
config/failure-to-log-api-fabric/client.properties
```

## Client Log Send Timing

The Fabric mod supports two send modes:

- `Next connection` Recommended. The mod saves the disconnect/error screen details and sends them after reconnecting to the same FailureToLog server.
- `After disconnect` Attempts to send immediately after disconnect. This can fail because the network connection may already be closed.

## Commands

Use these commands on the server:

```text
/failuretolog toggle
/failuretolog savelogs <on|off>
/failuretolog codes
/failuretolog log <id>
```

Alias:

```text
/ftl
```

## Notes

A server plugin cannot directly read a disconnected client's local logs. The Fabric companion mod captures client-side disconnect screen details and sends them to the server when possible.
