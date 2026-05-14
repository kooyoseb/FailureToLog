package failuretologapifabric.client;

import failuretologapifabric.client.FailureToLogClientConfig.SendTiming;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class FailureToLogConfigScreen extends Screen {
	private final Screen parent;
	private Button timingButton;

	public FailureToLogConfigScreen(Screen parent) {
		super(Component.literal("FailureToLog API Fabric"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int centerX = this.width / 2;
		int top = Math.max(32, this.height / 6);

		addRenderableWidget(new StringWidget(centerX - 150, top, 300, 20, Component.literal("FailureToLog API Fabric"), this.font));

		timingButton = addRenderableWidget(Button.builder(timingMessage(), button -> {
			FailureToLogClientConfig.setSendTiming(FailureToLogClientConfig.nextSendTiming());
			timingButton.setMessage(timingMessage());
		}).bounds(centerX - 100, top + 38, 200, 20).build());

		addRenderableWidget(new MultiLineTextWidget(
				centerX - 150,
				top + 72,
				Component.literal("Recommended: Next connection. Sending after disconnect can fail because the network connection may already be closed."),
				this.font
		).setMaxWidth(300).setCentered(true));

		addRenderableWidget(Button.builder(Component.literal("Done"), button -> this.minecraft.setScreen(parent))
				.bounds(centerX - 100, this.height - 34, 200, 20)
				.build());
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(parent);
	}

	private Component timingMessage() {
		SendTiming timing = FailureToLogClientConfig.sendTiming();
		return Component.literal("Error log send timing: " + timing.label());
	}
}
