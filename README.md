# FailureToLog API Fabric

FailureToLog API Fabric is a client-side companion mod for the FailureToLog Paper server plugin.

It helps FailureToLog servers receive more useful client-side disconnect information. When a player disconnects or sees an error screen, the mod can save the screen reason and send it to a compatible server so staff can inspect it through the server plugin's log viewer.

## What It Does

- Detects FailureToLog-enabled servers through a custom plugin message handshake.
- Captures Minecraft's disconnect screen title and reason on the client.
- Sends client-side disconnect details to the server plugin when possible.
- Supports delayed sending on the next connection, which is the recommended mode.
- Adds a Mod Menu config screen for changing the send timing.
- Uses English as the default user-facing language.

## Requirements

### Client

- Minecraft `26.1.2`
- Fabric Loader `0.19.2` or newer
- Fabric API
- Optional: Mod Menu, for the in-game config screen

### Server

For full functionality, the server must run:

- Paper `26.1.2`
- FailureToLog Paper plugin

The mod can be installed without the server plugin, but it will only send reports when the server supports the FailureToLog plugin message channels.

## Installation

### Client Installation

1. Install Fabric Loader for Minecraft `26.1.2`.
2. Install Fabric API.
3. Put this mod jar into the client `mods` folder.
4. Optional: install Mod Menu to open the config screen from the Mods menu.

Typical client mods folder:

```text
%APPDATA%\.minecraft\mods
```

### Server Installation

1. Stop the Paper server.
2. Put the FailureToLog Paper plugin jar into the server `plugins` folder.
3. Start the server.
4. The server plugin will create its config and log files automatically.

## Configuration

The client config is stored at:

```text
config/failure-to-log-api-fabric/client.properties
```

Typical full Windows path:

```text
%APPDATA%\.minecraft\config\failure-to-log-api-fabric\client.properties
```

### Send Timing

The mod supports two error log send timing modes.

#### Next connection

Recommended.

The mod stores the disconnect report locally and sends it the next time the player connects to the same FailureToLog-enabled server.

This is the safest mode because once the client is disconnected, the network connection may already be closed.

Config value:

```properties
send-timing=next_connection
```

#### After disconnect

The mod attempts to send the report immediately after disconnect.

This can fail because the connection may already be closed by the time the disconnect screen appears. If sending fails, the mod saves the report for the next connection instead.

Config value:

```properties
send-timing=after_disconnect
```

## Pending Report Storage

When using the recommended next-connection mode, the pending client report is stored at:

```text
config/failure-to-log-api-fabric/pending-client-log.properties
```

Typical full Windows path:

```text
%APPDATA%\.minecraft\config\failure-to-log-api-fabric\pending-client-log.properties
```

The file is deleted after the report is successfully sent to the matching server.

## Mod Menu

If Mod Menu is installed, open:

```text
Mods -> FailureToLog API Fabric -> Configure
```

The config screen lets players change:

- Error log send timing

The screen also shows the warning:

```text
Recommended: Next connection. Sending after disconnect can fail because the network connection may already be closed.
```

## Server Plugin Integration

The mod communicates with the FailureToLog Paper plugin using these custom payload channels:

```text
failuretolog:hello
failuretolog:client_log
```

Flow:

1. Player joins the server.
2. Server sends `failuretolog:hello`.
3. Client marks the server as FailureToLog-compatible.
4. Client captures disconnect screen information if an error occurs.
5. Client sends the saved report through `failuretolog:client_log`.
6. Server stores the report and broadcasts a clickable error log message.

## Server Commands

The server plugin provides:

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

## Important Limitations

Minecraft clients cannot send data to a server after the network connection is fully closed.

Because of that, the recommended mode is `Next connection`. In that mode, the mod saves the error report locally and sends it when the player reconnects to the same FailureToLog-enabled server.

This mod does not upload reports to any external web service. Reports are only sent to compatible FailureToLog servers through Minecraft networking.

## Privacy Notes

The client report may include:

- Disconnect screen title
- Disconnect reason
- Minecraft version
- Player name
- Server address
- FailureToLog protocol/plugin version
- Timestamp

It does not intentionally collect full local game logs or crash reports.

## Recommended Platform Description

Short description:

```text
Client companion mod for FailureToLog servers. Captures disconnect screen details and sends them to the server plugin for easier error diagnosis.
```

Long description:

```text
FailureToLog API Fabric is a client-side companion mod for the FailureToLog Paper server plugin.

When a player disconnects or sees an error screen, the mod captures the disconnect screen title and reason. If the server supports FailureToLog, the report can be sent to the server plugin and viewed by staff through the plugin's error log commands.

The recommended send mode is Next connection, which stores the report locally and sends it when the player reconnects to the same server. This avoids the common problem where the client can no longer send packets after the connection has already closed.

Mod Menu is supported for changing the client send timing.
```

## Suggested Tags

- Fabric
- Client
- Utility
- Server utility
- Admin tools
- Logging
- Debugging
- Paper integration

## Build Output

Integrated distribution builds are copied to:

```text
C:\Users\musek\IdeaProjects\dist
```

Fabric mod jar:

```text
FailureToLog-API-Fabric-1.0.0.jar
```

Paper plugin jar:

```text
FailureToLog-Paper-1.0-SNAPSHOT.jar
```

Run the integrated build script:

```bat
C:\Users\musek\IdeaProjects\build-all-failure-to-log.bat
```

