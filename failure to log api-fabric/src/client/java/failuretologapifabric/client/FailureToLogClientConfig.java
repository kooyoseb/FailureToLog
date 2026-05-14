package failuretologapifabric.client;

import failuretologapifabric.FailureToLogApifabric;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

public final class FailureToLogClientConfig {
	private static final Path CONFIG_FILE = Path.of("config", "failure-to-log-api-fabric", "client.properties");
	private static SendTiming sendTiming = SendTiming.NEXT_CONNECTION;

	private FailureToLogClientConfig() {
	}

	public static void load() {
		if (!Files.isRegularFile(CONFIG_FILE)) {
			save();
			return;
		}

		try {
			Properties properties = new Properties();
			properties.load(Files.newBufferedReader(CONFIG_FILE, StandardCharsets.UTF_8));
			sendTiming = SendTiming.fromConfigValue(properties.getProperty("send-timing"));
		} catch (IOException exception) {
			FailureToLogApifabric.LOGGER.warn("Failed to load FailureToLog client config.", exception);
		}
	}

	public static void save() {
		try {
			Files.createDirectories(CONFIG_FILE.getParent());
			Properties properties = new Properties();
			properties.setProperty("send-timing", sendTiming.configValue());
			try (var writer = Files.newBufferedWriter(CONFIG_FILE, StandardCharsets.UTF_8)) {
				properties.store(writer, "FailureToLog API Fabric client settings");
			}
		} catch (IOException exception) {
			FailureToLogApifabric.LOGGER.warn("Failed to save FailureToLog client config.", exception);
		}
	}

	public static SendTiming sendTiming() {
		return sendTiming;
	}

	public static void setSendTiming(SendTiming value) {
		sendTiming = value;
		save();
	}

	public static SendTiming nextSendTiming() {
		return sendTiming == SendTiming.NEXT_CONNECTION ? SendTiming.AFTER_DISCONNECT : SendTiming.NEXT_CONNECTION;
	}

	public enum SendTiming {
		NEXT_CONNECTION("next_connection", "Next connection"),
		AFTER_DISCONNECT("after_disconnect", "After disconnect");

		private final String configValue;
		private final String label;

		SendTiming(String configValue, String label) {
			this.configValue = configValue;
			this.label = label;
		}

		public String configValue() {
			return configValue;
		}

		public String label() {
			return label;
		}

		private static SendTiming fromConfigValue(String value) {
			if (value == null) {
				return NEXT_CONNECTION;
			}

			String normalized = value.toLowerCase(Locale.ROOT);
			for (SendTiming timing : values()) {
				if (timing.configValue.equals(normalized)) {
					return timing;
				}
			}
			return NEXT_CONNECTION;
		}
	}
}
