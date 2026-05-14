package failuretologapifabric;

import failuretologapifabric.network.FailureToLogPayloads;
import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FailureToLogApifabric implements ModInitializer {
	public static final String MOD_ID = "failure-to-log-api-fabric";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		FailureToLogPayloads.register();
		LOGGER.info("FailureToLog Fabric API initialized.");
	}
}
