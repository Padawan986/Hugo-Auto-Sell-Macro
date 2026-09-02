package com.hugosmp.autosell;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

@Environment(EnvType.CLIENT)
public class HotbarOverlay {

    public static void render(DrawContext drawContext, RenderTickCounter tickCounter) {
        AutoSellManager manager = AutoSellManager.getInstance();

        int screenWidth = drawContext.getScaledWindowWidth();
        int screenHeight = drawContext.getScaledWindowHeight();

        // Position: Über der Hotbar (ca. 60 Pixel über dem unteren Rand)
        int y = screenHeight - 70;
        int x = screenWidth / 2;

        // Hintergrund-Box zeichnen
        int boxHeight = 30;
        int boxWidth = 220;
        int boxX = x - (boxWidth / 2);
        int boxY = y - 5;

        // Dunkelgrauer Hintergrund mit Semi-Transparenz
        drawContext.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xAA1a1a1a);
        // Rand
        drawContext.fill(boxX, boxY, boxX + boxWidth, boxY + 1, 0xFFFFAA00); // Orange border oben
        drawContext.fill(boxX, boxY + boxHeight - 1, boxX + boxWidth, boxY + boxHeight, 0xFFFFAA00); // Orange border unten

        String statusText;
        int statusColor;

        if (!manager.isActive()) {
            // Mod inaktiv - Hinweis anzeigen (NEU: war vorher unsichtbar!)
            statusText = "[K] Starten | /autosell status";
            statusColor = 0xFFAAAAAA; // Hellgrau
        } else if (manager.getState() == AutoSellManager.State.WAITING_TIMER) {
            int remainingTicks = manager.getRemainingCooldownTicks();
            int totalSeconds = remainingTicks / 20;
            int minutes = totalSeconds / 60;
            int seconds = totalSeconds % 60;
            statusText = String.format("Nächster Verkauf: %02d:%02d", minutes, seconds);
            statusColor = 0xFFFFAA00; // Orange
        } else {
            String stepName = getStepName(manager.getState());
            statusText = ">> " + stepName;
            statusColor = 0xFF00FF00; // Gruen
        }

        // Text rendern
        drawContext.drawCenteredTextWithShadow(
                MinecraftClient.getInstance().textRenderer,
                Text.literal(statusText),
                x,
                boxY + 7,
                statusColor
        );

        // Keybind-Infos unter dem Status
        String keybindInfo = "[K] Toggle | [/autosell status] Info";
        drawContext.drawCenteredTextWithShadow(
                MinecraftClient.getInstance().textRenderer,
                Text.literal(keybindInfo),
                x,
                boxY + 18,
                0xFFAAAAAA // Hellgrau
        );
    }

    private static String getStepName(AutoSellManager.State state) {
        return switch (state) {
            case OPENING_CHEST -> "Oeffne Kiste...";
            case WAITING_FOR_CHEST_SCREEN -> "Warte auf Kiste...";
            case LOOTING_CHEST -> "Leere Kiste...";
            case CLOSING_CHEST -> "Schliesse Kiste...";
            case WAITING_FOR_INVENTORY_SYNC -> "Warte auf Sync...";
            case WAITING_BEFORE_COMMAND -> "Warte vor Befehl...";
            case SENDING_SELL_COMMAND -> "Sende /sell...";
            case WAITING_FOR_ORDER_SCREEN -> "Warte auf Order-Liste...";
            case WAITING_FOR_ORDER_DELIVERY_SCREEN -> "Liefere Inventar...";
            case WAITING_FOR_ORDER_CONFIRMATION -> "Bestaetige Order...";
            case WAITING_FOR_SELL_SCREEN -> "Warte auf Sell-GUI...";
            case FILLING_SELL_GUI -> "Lege Items...";
            case CONFIRMING_SELL -> "Bestaetige...";
            case FINISHING_CYCLE -> "Fertig!";
            case IDLE -> "Inaktiv";
            case WAITING_TIMER -> "Timer laeuft...";
        };
    }
}
