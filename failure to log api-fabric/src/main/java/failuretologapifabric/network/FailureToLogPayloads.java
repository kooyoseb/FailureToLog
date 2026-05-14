package failuretologapifabric.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public final class FailureToLogPayloads {
	public static final int PROTOCOL_VERSION = 1;

	private FailureToLogPayloads() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(HelloPayload.TYPE, HelloPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ClientLogPayload.TYPE, ClientLogPayload.CODEC);
	}

	public record HelloPayload(byte[] data) implements CustomPacketPayload {
		public static final Type<HelloPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("failuretolog", "hello"));
		public static final StreamCodec<RegistryFriendlyByteBuf, HelloPayload> CODEC = rawCodec(HelloPayload::new, HelloPayload::data);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record ClientLogPayload(byte[] data) implements CustomPacketPayload {
		public static final Type<ClientLogPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("failuretolog", "client_log"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ClientLogPayload> CODEC = rawCodec(ClientLogPayload::new, ClientLogPayload::data);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	private static <T> StreamCodec<RegistryFriendlyByteBuf, T> rawCodec(RawPayloadFactory<T> factory, RawPayloadData<T> dataGetter) {
		return StreamCodec.of(
				(buffer, value) -> buffer.writeBytes(dataGetter.data(value)),
				buffer -> {
					byte[] data = new byte[buffer.readableBytes()];
					buffer.readBytes(data);
					return factory.create(data);
				}
		);
	}

	@FunctionalInterface
	private interface RawPayloadFactory<T> {
		T create(byte[] data);
	}

	@FunctionalInterface
	private interface RawPayloadData<T> {
		byte[] data(T value);
	}
}
