package failuretologapifabric.client;

import failuretologapifabric.FailureToLogApifabric;
import failuretologapifabric.client.FailureToLogClientConfig.SendTiming;
import failuretologapifabric.network.FailureToLogPayloads;
import failuretologapifabric.network.FailureToLogPayloads.ClientLogPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Properties;

public final class FailureToLogClientReporter {
	private static final int MAX_DETAILS_LENGTH = 24000;
	private static final Path PENDING_LOG = Path.of("config", "failure-to-log-api-fabric", "pending-client-log.properties");

	private static boolean serverSupportsFailureToLog;
	private static String currentServer = "unknown";
	private static String lastScreenTitle = "";
	private static String lastScreenReason = "";
	private static String pluginVersion = "unknown";

	private FailureToLogClientReporter() {
	}

	public static void onJoin(Minecraft client) {
		serverSupportsFailureToLog = false;
		currentServer = currentServer(client);
		lastScreenTitle = "";
		lastScreenReason = "";
	}

	public static void onServerHello(byte[] data) {
		serverSupportsFailureToLog = true;
		readHello(data);
		flushPendingReport();
	}

	public static void captureDisconnectScreen(Component title, Component reason) {
		lastScreenTitle = plain(title);
		lastScreenReason = plain(reason);
	}

	public static void onDisconnect(Minecraft client) {
		String title = lastScreenTitle.isBlank() ? "Disconnected" : lastScreenTitle;
		String reason = lastScreenReason.isBlank() ? "The client disconnected before a detailed screen reason was captured." : lastScreenReason;
		ClientReport report = new ClientReport(
				classify(reason),
				title,
				reason,
				buildDetails(client, title, reason)
		);

		if (FailureToLogClientConfig.sendTiming() == SendTiming.AFTER_DISCONNECT && serverSupportsFailureToLog) {
			try {
				sendReport(report);
				return;
			} catch (IOException | IllegalStateException exception) {
				FailureToLogApifabric.LOGGER.warn("Failed to send FailureToLog report after disconnect. Saving it for the next connection.", exception);
			}
		}

		savePendingReport(report);
	}

	private static void flushPendingReport() {
		if (!serverSupportsFailureToLog || !Files.isRegularFile(PENDING_LOG)) {
			return;
		}

		try {
			Properties properties = new Properties();
			properties.load(Files.newBufferedReader(PENDING_LOG, StandardCharsets.UTF_8));

			String server = properties.getProperty("server", "unknown");
			if (!server.equals(currentServer)) {
				return;
			}

			ClientReport report = new ClientReport(
					Integer.parseInt(properties.getProperty("code", "1")),
					properties.getProperty("title", "Disconnected"),
					properties.getProperty("reason", ""),
					new String(Base64.getDecoder().decode(properties.getProperty("details", "")), StandardCharsets.UTF_8)
			);

			sendReport(report);
			Files.deleteIfExists(PENDING_LOG);
		} catch (IOException | IllegalArgumentException exception) {
			FailureToLogApifabric.LOGGER.warn("Failed to flush pending FailureToLog client report.", exception);
		}
	}

	private static void savePendingReport(ClientReport report) {
		try {
			Files.createDirectories(PENDING_LOG.getParent());
			Properties properties = new Properties();
			properties.setProperty("server", currentServer);
			properties.setProperty("code", String.valueOf(report.code()));
			properties.setProperty("title", report.screenTitle());
			properties.setProperty("reason", report.screenReason());
			properties.setProperty("details", Base64.getEncoder().encodeToString(report.details().getBytes(StandardCharsets.UTF_8)));
			try (var writer = Files.newBufferedWriter(PENDING_LOG, StandardCharsets.UTF_8)) {
				properties.store(writer, "FailureToLog pending client report");
			}
		} catch (IOException exception) {
			FailureToLogApifabric.LOGGER.warn("Failed to save pending FailureToLog client report.", exception);
		}
	}

	private static void sendReport(ClientReport report) throws IOException {
		try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			 DataOutputStream output = new DataOutputStream(bytes)) {
			output.writeInt(FailureToLogPayloads.PROTOCOL_VERSION);
			output.writeInt(report.code());
			output.writeUTF(limit(report.screenTitle(), 240));
			output.writeUTF(limit(report.screenReason(), 2000));
			output.writeUTF(limit(report.details(), MAX_DETAILS_LENGTH));
			ClientPlayNetworking.send(new ClientLogPayload(bytes.toByteArray()));
		}
	}

	private static void readHello(byte[] data) {
		try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(data))) {
			int version = input.readInt();
			pluginVersion = input.readUTF();
			FailureToLogApifabric.LOGGER.info("Detected FailureToLog server plugin {} using protocol {}.", pluginVersion, version);
		} catch (IOException exception) {
			pluginVersion = "unknown";
			FailureToLogApifabric.LOGGER.warn("Detected FailureToLog server plugin, but failed to read hello payload.", exception);
		}
	}

	private static String buildDetails(Minecraft client, String title, String reason) {
		return limit("type=CLIENT_DISCONNECT_SCREEN\n"
				+ "time=" + Instant.now() + "\n"
				+ "server=" + currentServer + "\n"
				+ "pluginVersion=" + pluginVersion + "\n"
				+ "minecraftVersion=" + SharedConstants.getCurrentVersion().name() + "\n"
				+ "screenTitle=" + title + "\n"
				+ "screenReason=" + reason + "\n"
				+ "player=" + client.getGameProfile().name(), MAX_DETAILS_LENGTH);
	}

	private static int classify(String reason) {
		String text = reason == null ? "" : reason.toLowerCase(Locale.ROOT);
		if (text.contains("outofmemory") || text.contains("out of memory") || text.contains("heap space")) {
			return 201;
		}
		if (text.contains("whitelist")) {
			return 6;
		}
		if (text.contains("blacklist")) {
			return 9;
		}
		if (text.contains("chunk") || text.contains("region") || text.contains("world")) {
			return 105;
		}
		if (text.contains("mod") || text.contains("fabric") || text.contains("forge") || text.contains("neoforge") || text.contains("socket")) {
			return 790;
		}
		if (text.contains("packet") || text.contains("decoder") || text.contains("payload") || text.contains("protocol")) {
			return 600;
		}
		if (text.contains("server") || text.contains("internal exception") || text.contains("exception")) {
			return 500;
		}
		return 1;
	}

	private static String currentServer(Minecraft client) {
		if (client.getCurrentServer() != null) {
			return client.getCurrentServer().ip;
		}
		if (client.isSingleplayer()) {
			return "singleplayer";
		}
		return "unknown";
	}

	private static String plain(Component text) {
		return text == null ? "" : text.getString();
	}

	private static String limit(String value, int maxLength) {
		if (value == null) {
			return "";
		}
		return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
	}

	private record ClientReport(int code, String screenTitle, String screenReason, String details) {
	}
}
