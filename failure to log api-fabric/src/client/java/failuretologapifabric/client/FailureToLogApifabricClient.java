package failuretologapifabric.client;

import failuretologapifabric.network.FailureToLogPayloads.HelloPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class FailureToLogApifabricClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		FailureToLogClientConfig.load();
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> FailureToLogClientReporter.onJoin(client));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> FailureToLogClientReporter.onDisconnect(client));
		ClientPlayNetworking.registerGlobalReceiver(HelloPayload.TYPE, (payload, context) ->
				context.client().execute(() -> FailureToLogClientReporter.onServerHello(payload.data())));
	}
}
