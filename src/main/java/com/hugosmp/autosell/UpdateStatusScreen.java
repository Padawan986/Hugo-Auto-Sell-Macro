package com.hugosmp.autosell;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class UpdateStatusScreen extends Screen {
    private final String message;
    private final boolean canRestart;

    public UpdateStatusScreen(String message, boolean canRestart) {
        super(Text.literal("Hugo Auto-Sell Update"));
        this.message = message;
        this.canRestart = canRestart;
    }

    @Override
    protected void init() {
        if (canRestart) {
            this.addDrawableChild(ButtonWidget.builder(Text.literal("Minecraft beenden"), button -> MinecraftClient.getInstance().scheduleStop())
                    .dimensions(this.width / 2 - 100, this.height / 2 + 20, 200, 20).build());
        } else {
            this.addDrawableChild(ButtonWidget.builder(Text.literal("Schließen"), button -> MinecraftClient.getInstance().setScreen(null))
                    .dimensions(this.width / 2 - 100, this.height / 2 + 20, 200, 20).build());
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Hugo SMP Auto-Sell Update"), this.width / 2, this.height / 2 - 25, 0xFFFFAA00);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(message), this.width / 2, this.height / 2, 0xFFFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }
}
