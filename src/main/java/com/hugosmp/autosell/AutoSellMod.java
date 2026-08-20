package com.hugosmp.autosell;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class AutoSellMod implements ClientModInitializer {
    public static final String MOD_ID = "hugo_autosell";
    private static final int TOGGLE_KEY = GLFW.GLFW_KEY_K;
    private static boolean keyWasDown = false;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null && client.currentScreen == null) {
                long window = GLFW.glfwGetCurrentContext();
                boolean keyDown = window != 0L && GLFW.glfwGetKey(window, TOGGLE_KEY) == GLFW.GLFW_PRESS;
                if (keyDown && !keyWasDown) {
                    AutoSellManager.getInstance().toggle();
                }
                keyWasDown = keyDown;
            } else {
                keyWasDown = false;
            }
            AutoSellManager.getInstance().onTick(client);
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("autosell")
                    .then(ClientCommandManager.literal("toggle")
                            .executes(context -> {
                                AutoSellManager.getInstance().toggle();
                                return 1;
                            }))
                    .then(ClientCommandManager.literal("interval")
                            .then(ClientCommandManager.argument("sekunden", IntegerArgumentType.integer(5, 3600))
                                    .executes(context -> {
                                        int sek = IntegerArgumentType.getInteger(context, "sekunden");
                                        AutoSellConfig cfg = AutoSellManager.getInstance().getConfig();
                                        cfg.intervalSeconds = sek;
                                        cfg.save();
                                        context.getSource().sendFeedback(Text.literal("§a[AutoSell] Intervall auf §e" + sek + " Sekunden §agesetzt."));
                                        return 1;
                                    })))
                    .then(ClientCommandManager.literal("command")
                            .then(ClientCommandManager.argument("befehl", StringArgumentType.greedyString())
                                    .executes(context -> {
                                        String cmd = StringArgumentType.getString(context, "befehl");
                                        AutoSellConfig cfg = AutoSellManager.getInstance().getConfig();
                                        cfg.sellCommand = cmd;
                                        cfg.save();
                                        context.getSource().sendFeedback(Text.literal("§a[AutoSell] Verkaufsbefehl auf §e/" + cmd + " §agesetzt."));
                                        return 1;
                                    })))
                    .then(ClientCommandManager.literal("protecthotbar")
                            .then(ClientCommandManager.argument("aktiv", BoolArgumentType.bool())
                                    .executes(context -> {
                                        boolean val = BoolArgumentType.getBool(context, "aktiv");
                                        AutoSellConfig cfg = AutoSellManager.getInstance().getConfig();
                                        cfg.protectHotbar = val;
                                        cfg.save();
                                        context.getSource().sendFeedback(Text.literal("§a[AutoSell] Hotbar-Schutz: " + (val ? "§2Aktiviert" : "§cDeaktiviert")));
                                        return 1;
                                    })))
                    .then(ClientCommandManager.literal("status")
                            .executes(context -> {
                                AutoSellManager mgr = AutoSellManager.getInstance();
                                AutoSellConfig cfg = mgr.getConfig();
                                context.getSource().sendFeedback(Text.literal("§6=== Hugo SMP Auto-Sell Status ==="));
                                context.getSource().sendFeedback(Text.literal("§7Status: " + (mgr.isActive() ? "§aAktiv" : "§cInaktiv")));
                                context.getSource().sendFeedback(Text.literal("§7Intervall: §e" + cfg.intervalSeconds + "s (" + (cfg.intervalSeconds / 60) + " Min)"));
                                context.getSource().sendFeedback(Text.literal("§7Befehl: §e/" + cfg.sellCommand));
                                context.getSource().sendFeedback(Text.literal("§7Hotbar-Schutz: " + (cfg.protectHotbar ? "§aAn" : "§cAus")));
                                if (mgr.getTargetChestPos() != null) {
                                    var pos = mgr.getTargetChestPos();
                                    context.getSource().sendFeedback(Text.literal("§7Zielkiste: §e" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()));
                                } else {
                                    context.getSource().sendFeedback(Text.literal("§7Zielkiste: §cNicht gesetzt (Kiste ansehen & K drücken)"));
                                }
                                return 1;
                            }))
            );
        });
    }
}
