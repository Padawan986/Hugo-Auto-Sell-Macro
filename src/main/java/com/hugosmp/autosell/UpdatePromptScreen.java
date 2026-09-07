package com.hugosmp.autosell;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class UpdatePromptScreen extends Screen {
    private final HugoAutoSellUpdater.Update update;

    public UpdatePromptScreen(HugoAutoSellUpdater.Update update) {
        super(Text.literal("Hugo Auto-Sell Update"));
        this.update = update;
    }

    @Override
    protected void init() {
        int x = this.width / 2 - 105;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Ja, herunterladen"), button -> {
            MinecraftClient client = MinecraftClient.getInstance();
            client.setScreen(new UpdateStatusScreen("Update wird heruntergeladen...", false));
            HugoAutoSellUpdater.downloadAndInstall(update, error -> client.execute(() ->
                    client.setScreen(new UpdateStatusScreen(
                            error == null ? "Update installiert. Minecraft jetzt neu starten." : "Update fehlgeschlagen: " + error,
                            error == null
                    ))
            ));
        }).dimensions(x, this.height / 2 + 25, 100, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Nein"), button -> MinecraftClient.getInstance().setScreen(null))
                .dimensions(x + 110, this.height / 2 + 25, 100, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Hugo SMP Auto-Sell Update"), this.width / 2, this.height / 2 - 35, 0xFFFFAA00);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Version " + update.version() + " ist verfügbar."), this.width / 2, this.height / 2 - 10, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Jetzt herunterladen und beim Neustart verwenden?"), this.width / 2, this.height / 2 + 5, 0xFFAAAAAA);
        super.render(context, mouseX, mouseY, delta);
    }
}
