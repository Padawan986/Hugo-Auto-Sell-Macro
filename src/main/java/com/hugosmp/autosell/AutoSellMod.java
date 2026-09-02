package com.hugosmp.autosell;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

public class AutoSellMod implements ClientModInitializer {
    public static final String MOD_ID = "hugo_autosell";
    public static final String MOD_VERSION = "1.0.6";

    // Echter Minecraft-Keybinding statt raw GLFW - löst Konflikte mit anderen Mods
    private static KeyBinding toggleKey;
    private static KeyBinding commandModeKey;
    private static KeyBinding.Category keyCategory;
    private static boolean loadedMessageShown = false;
    private static boolean tutorialQueued = false;

    @Override
    public void onInitializeClient() {
        // Keybinding registrieren - erscheint in Minecraft's "Controls" Menü
        keyCategory = KeyBinding.Category.create(Identifier.of(MOD_ID, "main"));
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.hugo_autosell.toggle",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            keyCategory
        ));
        commandModeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.hugo_autosell.switch_command",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            keyCategory
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // "Mod geladen" Nachricht beim ersten Join anzeigen
            if (!loadedMessageShown && client.player != null && client.world != null) {
                loadedMessageShown = true;
                client.player.sendMessage(Text.literal(
                    "§6[HugoAutoSell] §aMod v" + MOD_VERSION + " geladen. §7Drücke §e[K]§7 beim Anvisieren einer Kiste zum Starten."
                ), false);
                client.player.sendMessage(Text.literal(
                    "§6[HugoAutoSell] §7Befehl: §e/autosell status§7 | Tutorial: §e/autosell tutorial"
                ), false);

                // Tutorial nur beim ersten Start anzeigen
                if (!tutorialQueued) {
                    tutorialQueued = true;
                    AutoSellConfig cfg = AutoSellManager.getInstance().getConfig();
                    if (!cfg.hasSeenTutorial) {
                        MinecraftClient.getInstance().setScreen(new TutorialScreen(null));
                    }
                }
            }

            // Keybinding abfragen (Minecraft's offizieller Weg)
            if (client.player != null && client.currentScreen == null) {
                while (toggleKey.wasPressed()) {
                    AutoSellManager.getInstance().toggle();
                }
                while (commandModeKey.wasPressed()) {
                    boolean orderMode = AutoSellManager.getInstance().toggleCommandMode();
                    client.player.sendMessage(Text.literal(
                        "§6[HugoAutoSell] §aModus gewechselt zu §e" + (orderMode ? "/order <Item>" : "/sell")
                    ), false);
                }
            }
            AutoSellManager.getInstance().onTick(client);
        });

        // HUD Overlay registrieren
        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            HotbarOverlay.render(drawContext, tickCounter);
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
                            .then(ClientCommandManager.literal("toggle")
                                    .executes(context -> {
                                        boolean orderMode = AutoSellManager.getInstance().toggleCommandMode();
                                        context.getSource().sendFeedback(Text.literal("§a[AutoSell] Modus gewechselt zu §e" + (orderMode ? "/order <Item>" : "/sell")));
                                        return 1;
                                    }))
                            .then(ClientCommandManager.argument("befehl", StringArgumentType.greedyString())
                                    .executes(context -> {
                                        String cmd = StringArgumentType.getString(context, "befehl");
                                        AutoSellConfig cfg = AutoSellManager.getInstance().getConfig();
                                        cfg.sellCommand = cmd;
                                        cfg.orderMode = false;
                                        cfg.save();
                                        context.getSource().sendFeedback(Text.literal("§a[AutoSell] Verkaufsbefehl auf §e/" + cmd + " §agesetzt."));
                                        return 1;
                                    })))
                    .then(ClientCommandManager.literal("mode")
                            .executes(context -> {
                                boolean orderMode = AutoSellManager.getInstance().toggleCommandMode();
                                context.getSource().sendFeedback(Text.literal("§a[AutoSell] Modus gewechselt zu §e" + (orderMode ? "/order <Item>" : "/sell")));
                                return 1;
                            }))
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
                    .then(ClientCommandManager.literal("clearchest")
                            .executes(context -> {
                                AutoSellConfig cfg = AutoSellManager.getInstance().getConfig();
                                cfg.setChestPos(null);
                                context.getSource().sendFeedback(Text.literal("§a[AutoSell] Gespeicherte Kisten-Position gelöscht."));
                                return 1;
                            }))
                    .then(ClientCommandManager.literal("tutorial")
                            .executes(context -> {
                                MinecraftClient.getInstance().setScreen(new TutorialScreen(null));
                                return 1;
                            }))
                    .then(ClientCommandManager.literal("status")
                            .executes(context -> {
                                AutoSellManager mgr = AutoSellManager.getInstance();
                                AutoSellConfig cfg = mgr.getConfig();
                                context.getSource().sendFeedback(Text.literal("§6=== Hugo SMP Auto-Sell Status ==="));
                                context.getSource().sendFeedback(Text.literal("§7Status: " + (mgr.isActive() ? "§aAktiv" : "§cInaktiv")));
                                context.getSource().sendFeedback(Text.literal("§7Intervall: §e" + cfg.intervalSeconds + "s (" + (cfg.intervalSeconds / 60) + " Min)"));
                                context.getSource().sendFeedback(Text.literal("§7Befehl: §e/" + cfg.sellCommand));
                                context.getSource().sendFeedback(Text.literal("§7Modus: " + (cfg.orderMode ? "§b/order <Item>" : "§a/sell")));
                                context.getSource().sendFeedback(Text.literal("§7Hotbar-Schutz: " + (cfg.protectHotbar ? "§aAn" : "§cAus")));
                                if (mgr.getTargetChestPos() != null) {
                                    var pos = mgr.getTargetChestPos();
                                    context.getSource().sendFeedback(Text.literal("§7Zielkiste: §e" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()));
                                } else {
                                    context.getSource().sendFeedback(Text.literal("§7Zielkiste: §cNicht gesetzt (Kiste ansehen & K drücken)"));
                                }
                                context.getSource().sendFeedback(Text.literal("§7Tasten: §e[K]§7 Makro | §e[J]§7 /sell ↔ /order <Item> (frei umbindbar)"));
                                return 1;
                            }))
            );
        });
    }
}
