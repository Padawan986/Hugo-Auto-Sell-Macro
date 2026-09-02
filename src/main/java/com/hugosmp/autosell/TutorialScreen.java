package com.hugosmp.autosell;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class TutorialScreen extends Screen {

    private static final int BG_COLOR = 0xDD111111;
    private static final int ACCENT = 0xFFFFAA00;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFFAAAAAA;
    private static final int GREEN = 0xFF55FF55;

    private final Screen parent;

    public TutorialScreen(Screen parent) {
        super(Text.literal("Hugo SMP Auto-Sell - Tutorial"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int buttonWidth = 200;
        int buttonX = (this.width - buttonWidth) / 2;

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Verstanden!"), button -> {
            AutoSellConfig cfg = AutoSellManager.getInstance().getConfig();
            cfg.hasSeenTutorial = true;
            cfg.save();
            MinecraftClient.getInstance().setScreen(parent);
        }).dimensions(buttonX, this.height - 50, buttonWidth, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int centerX = this.width / 2;
        int startY = 40;
        int lineHeight = 18;
        int y = startY;
        MinecraftClient mc = MinecraftClient.getInstance();

        // Titel
        context.drawCenteredTextWithShadow(mc.textRenderer, Text.literal("Hugo SMP Auto-Sell").copy().styled(s -> s.withColor(ACCENT).withBold(true)), centerX, y, ACCENT);
        y += lineHeight + 4;
        context.drawCenteredTextWithShadow(mc.textRenderer, Text.literal("Erste Schritte").copy().styled(s -> s.withColor(WHITE)), centerX, y, WHITE);
        y += lineHeight + 12;

        // Trennlinie
        int boxW = 320;
        context.fill(centerX - boxW / 2, y, centerX + boxW / 2, y + 1, ACCENT);
        y += 12;

        // Schritt 1
        context.drawCenteredTextWithShadow(mc.textRenderer, Text.literal("Schritt 1: Makro starten").copy().styled(s -> s.withColor(GREEN)), centerX, y, GREEN);
        y += lineHeight;
        context.drawCenteredTextWithShadow(mc.textRenderer, Text.literal("Schaue eine Kiste an und druecke [K]").copy().styled(s -> s.withColor(GRAY)), centerX, y, GRAY);
        y += lineHeight - 4;
        context.drawCenteredTextWithShadow(mc.textRenderer, Text.literal("Die Kiste wird als Ziel gespeichert & das Makro startet.").copy().styled(s -> s.withColor(GRAY)), centerX, y, GRAY);
        y += lineHeight + 8;

        // Schritt 2
        context.drawCenteredTextWithShadow(mc.textRenderer, Text.literal("Schritt 2: Makro stoppen").copy().styled(s -> s.withColor(GREEN)), centerX, y, GREEN);
        y += lineHeight;
        context.drawCenteredTextWithShadow(mc.textRenderer, Text.literal("Druecke einfach nochmal [K] um es zu deaktivieren.").copy().styled(s -> s.withColor(GRAY)), centerX, y, GRAY);
        y += lineHeight + 8;

        // Schritt 3
        context.drawCenteredTextWithShadow(mc.textRenderer, Text.literal("Schritt 3: Befehle").copy().styled(s -> s.withColor(GREEN)), centerX, y, GREEN);
        y += lineHeight;
        String[] commands = {
                "/autosell status     - Zeigt aktuellen Status",
                "/autosell interval <s> - Timer aendern (5-3600s)",
                "/autosell command <c> - Verkaufsbefehl aendern",
                "/autosell toggle      - Makro an/aus",
        };
        for (String cmd : commands) {
            context.drawCenteredTextWithShadow(mc.textRenderer, Text.literal(cmd).copy().styled(s -> s.withColor(GRAY)), centerX, y, GRAY);
            y += lineHeight - 2;
        }
        y += 8;

        // Schritt 4
        context.drawCenteredTextWithShadow(mc.textRenderer, Text.literal("Schritt 4: HUD Overlay").copy().styled(s -> s.withColor(GREEN)), centerX, y, GREEN);
        y += lineHeight;
        context.drawCenteredTextWithShadow(mc.textRenderer, Text.literal("Ueber der Hotbar siehst du Status & Timer.").copy().styled(s -> s.withColor(GRAY)), centerX, y, GRAY);
        y += lineHeight - 4;
        context.drawCenteredTextWithShadow(mc.textRenderer, Text.literal("Orange = Timer  |  Gruen = Aktion laeuft").copy().styled(s -> s.withColor(GRAY)), centerX, y, GRAY);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
