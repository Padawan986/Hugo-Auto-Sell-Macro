package com.hugosmp.autosell;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class AutoSellManager {
    public enum State {
        IDLE,
        WAITING_TIMER,
        OPENING_CHEST,
        WAITING_FOR_CHEST_SCREEN,
        LOOTING_CHEST,
        CLOSING_CHEST,
        WAITING_FOR_INVENTORY_SYNC,
        WAITING_BEFORE_COMMAND,
        SENDING_SELL_COMMAND,
        WAITING_FOR_SELL_SCREEN,
        FILLING_SELL_GUI,
        CONFIRMING_SELL,
        FINISHING_CYCLE
    }

    private static final AutoSellManager INSTANCE = new AutoSellManager();
    public static AutoSellManager getInstance() {
        return INSTANCE;
    }

    private final AutoSellConfig config = AutoSellConfig.load();
    private boolean active = false;
    private BlockPos targetChestPos = null;

    private State state = State.IDLE;
    private int stateTicks = 0;
    private int remainingCooldownTicks = 0;
    private int currentSlotIndex = 0;
    private int fillPlayerSlotIndex = 0;
    private int fillPass = 0;

    private AutoSellManager() {}

    public AutoSellConfig getConfig() {
        return config;
    }

    public boolean isActive() {
        return active;
    }

    public BlockPos getTargetChestPos() {
        return targetChestPos;
    }

    public void toggle() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        if (active) {
            stop();
            mc.player.sendMessage(Text.literal("§6[HugoAutoSell] §c✖ Makro DEAKTIVIERT!"), false);
        } else {
            HitResult hit = mc.crosshairTarget;
            if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
                BlockHitResult blockHit = (BlockHitResult) hit;
                this.targetChestPos = blockHit.getBlockPos();
                start();
                mc.player.sendMessage(Text.literal("§6[HugoAutoSell] §a✔ Makro AKTIVIERT!"), false);
                mc.player.sendMessage(Text.literal("§6[HugoAutoSell] §7Zielkiste gespeichert bei: §e" 
                        + targetChestPos.getX() + ", " + targetChestPos.getY() + ", " + targetChestPos.getZ()), false);
                mc.player.sendMessage(Text.literal("§6[HugoAutoSell] §7Intervall: §e" + config.intervalSeconds 
                        + "s (" + (config.intervalSeconds / 60) + " Min) §7| Starte ersten Verkauf..."), false);
            } else if (this.targetChestPos != null) {
                start();
                mc.player.sendMessage(Text.literal("§6[HugoAutoSell] §a✔ Makro AKTIVIERT! (Vorherige Kiste bei: §e" 
                        + targetChestPos.getX() + ", " + targetChestPos.getY() + ", " + targetChestPos.getZ() + "§a)"), false);
                mc.player.sendMessage(Text.literal("§6[HugoAutoSell] §7Intervall: §e" + config.intervalSeconds 
                        + "s (" + (config.intervalSeconds / 60) + " Min) §7| Starte ersten Verkauf..."), false);
            } else {
                mc.player.sendMessage(Text.literal("§6[HugoAutoSell] §cBitte schaue eine Kiste an und drücke die Taste erneut!"), false);
            }
        }
    }

    public void start() {
        this.active = true;
        this.remainingCooldownTicks = 0;
        this.state = State.OPENING_CHEST;
        this.stateTicks = 0;
        this.fillPlayerSlotIndex = 0;
        this.fillPass = 0;
    }

    public void stop() {
        this.active = false;
        this.state = State.IDLE;
        this.stateTicks = 0;
        this.remainingCooldownTicks = 0;
        this.fillPlayerSlotIndex = 0;
        this.fillPass = 0;
    }

    public void onTick(MinecraftClient mc) {
        if (!active || mc.player == null || mc.world == null) {
            return;
        }

        renderActionBar(mc);

        stateTicks++;

        switch (state) {
            case WAITING_TIMER:
                if (remainingCooldownTicks > 0) {
                    if (remainingCooldownTicks == 1200) {
                        mc.player.sendMessage(Text.literal("§6[HugoAutoSell] §e⏳ Nächster Verkauf in 1 Minute (60 Sekunden)..."), false);
                    } else if (remainingCooldownTicks == 200) {
                        mc.player.sendMessage(Text.literal("§6[HugoAutoSell] §e⏳ Nächster Verkauf in 10 Sekunden..."), false);
                    }
                    remainingCooldownTicks--;
                } else {
                    mc.player.sendMessage(Text.literal("§6[HugoAutoSell] §b🔄 Timer abgelaufen: Öffne Kiste und verkaufe Items..."), false);
                    state = State.OPENING_CHEST;
                    stateTicks = 0;
                }
                break;

            case OPENING_CHEST:
                if (targetChestPos == null) {
                    stop();
                    mc.player.sendMessage(Text.literal("§c[AutoSell] Keine Kiste ausgewählt!"), false);
                    return;
                }

                double distSq = mc.player.getBlockPos().getSquaredDistance(targetChestPos);
                if (distSq > 36.0) {
                    mc.player.sendMessage(Text.literal("§c[AutoSell] Kiste ist zu weit entfernt!"), false);
                    stop();
                    return;
                }

                BlockHitResult hitResult = new BlockHitResult(
                        Vec3d.ofCenter(targetChestPos),
                        Direction.UP,
                        targetChestPos,
                        false
                );
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
                mc.player.swingHand(Hand.MAIN_HAND);

                state = State.WAITING_FOR_CHEST_SCREEN;
                stateTicks = 0;
                break;

            case WAITING_FOR_CHEST_SCREEN:
                if (mc.currentScreen instanceof HandledScreen<?>) {
                    state = State.LOOTING_CHEST;
                    stateTicks = 0;
                    currentSlotIndex = 0;
                } else if (stateTicks > 40) {
                    state = State.OPENING_CHEST;
                    stateTicks = 0;
                }
                break;

            case LOOTING_CHEST:
                if (!(mc.currentScreen instanceof HandledScreen<?> handledScreen)) {
                    state = State.OPENING_CHEST;
                    stateTicks = 0;
                    return;
                }

                ScreenHandler handler = handledScreen.getScreenHandler();
                int containerSlots = getTopContainerSlotCount(handler);

                if (stateTicks % Math.max(1, config.clickDelayTicks) == 0) {
                    boolean itemMoved = false;
                    while (currentSlotIndex < containerSlots) {
                        Slot slot = handler.getSlot(currentSlotIndex);
                        if (slot != null && slot.hasStack() && !slot.getStack().isEmpty()) {
                            mc.interactionManager.clickSlot(handler.syncId, currentSlotIndex, 0, SlotActionType.QUICK_MOVE, mc.player);
                            currentSlotIndex++;
                            itemMoved = true;
                            break;
                        }
                        currentSlotIndex++;
                    }

                    if (!itemMoved && currentSlotIndex >= containerSlots) {
                        state = State.CLOSING_CHEST;
                        stateTicks = 0;
                    }
                }
                break;

            case CLOSING_CHEST:
                if (mc.player != null) {
                    mc.player.closeHandledScreen();
                }
                mc.setScreen(null);
                state = State.WAITING_FOR_INVENTORY_SYNC;
                stateTicks = 0;
                break;

            case WAITING_FOR_INVENTORY_SYNC:
                if (stateTicks >= 10) {
                    state = State.WAITING_BEFORE_COMMAND;
                    stateTicks = 0;
                }
                break;

            case WAITING_BEFORE_COMMAND:
                if (stateTicks >= 6) {
                    state = State.SENDING_SELL_COMMAND;
                    stateTicks = 0;
                }
                break;

            case SENDING_SELL_COMMAND:
                if (mc.player != null && mc.player.networkHandler != null) {
                    String cmd = config.sellCommand.startsWith("/") ? config.sellCommand.substring(1) : config.sellCommand;
                    mc.player.networkHandler.sendChatCommand(cmd);
                }
                state = State.WAITING_FOR_SELL_SCREEN;
                stateTicks = 0;
                break;

            case WAITING_FOR_SELL_SCREEN:
                if (mc.currentScreen instanceof HandledScreen<?>) {
                    state = State.FILLING_SELL_GUI;
                    stateTicks = 0;
                    fillPlayerSlotIndex = 0;
                    fillPass = 0;
                } else if (stateTicks > 80) {
                    mc.player.sendMessage(Text.literal("§6[HugoAutoSell] §cSell-GUI wurde nicht geöffnet, überspringe..."), false);
                    finishCycle();
                }
                break;

            case FILLING_SELL_GUI:
                if (!(mc.currentScreen instanceof HandledScreen<?> sellScreen)) {
                    finishCycle();
                    return;
                }

                if (stateTicks < 6) {
                    return;
                }

                ScreenHandler sellHandler = sellScreen.getScreenHandler();
                int totalSlots = sellHandler.slots.size();
                int playerStart = Math.max(0, totalSlots - 36);
                int totalPlayerSlots = totalSlots - playerStart;

                if (stateTicks % Math.max(1, config.clickDelayTicks) == 0) {
                    if (fillPlayerSlotIndex < totalPlayerSlots) {
                        int slotIndex = playerStart + fillPlayerSlotIndex;
                        Slot slot = sellHandler.getSlot(slotIndex);
                        if (slot != null && slot.hasStack() && !slot.getStack().isEmpty()) {
                            mc.interactionManager.clickSlot(sellHandler.syncId, slotIndex, 0, SlotActionType.QUICK_MOVE, mc.player);
                        }
                        fillPlayerSlotIndex++;
                    } else {
                        fillPass++;
                        fillPlayerSlotIndex = 0;
                        if (fillPass >= 2) {
                            state = State.CONFIRMING_SELL;
                            stateTicks = 0;
                        }
                    }
                }
                break;

            case CONFIRMING_SELL:
                if (!(mc.currentScreen instanceof HandledScreen<?> confirmScreen)) {
                    finishCycle();
                    return;
                }

                if (stateTicks >= 8) {
                    ScreenHandler confirmHandler = confirmScreen.getScreenHandler();
                    int confirmTopSlots = getTopContainerSlotCount(confirmHandler);

                    int confirmSlot = findConfirmButtonSlot(confirmHandler, confirmTopSlots);
                    if (confirmSlot >= 0) {
                        mc.player.sendMessage(Text.literal("§6[HugoAutoSell] §7Bestätige Verkauf (Slot §e" + confirmSlot + "§7)..."), false);
                        mc.interactionManager.clickSlot(confirmHandler.syncId, confirmSlot, 0, SlotActionType.PICKUP, mc.player);
                    } else {
                        mc.player.sendMessage(Text.literal("§6[HugoAutoSell] §cKein Bestätigungs-Button gefunden!"), false);
                    }

                    state = State.FINISHING_CYCLE;
                    stateTicks = 0;
                }
                break;

            case FINISHING_CYCLE:
                if (stateTicks >= 8) {
                    if (mc.player != null) {
                        mc.player.closeHandledScreen();
                    }
                    mc.setScreen(null);
                    finishCycle();
                }
                break;

            case IDLE:
            default:
                break;
        }
    }

    private void finishCycle() {
        this.state = State.WAITING_TIMER;
        this.stateTicks = 0;
        this.remainingCooldownTicks = config.intervalSeconds * 20;
        this.fillPlayerSlotIndex = 0;
        this.fillPass = 0;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            int minutes = config.intervalSeconds / 60;
            int seconds = config.intervalSeconds % 60;
            String timeStr = (minutes > 0) ? (minutes + " Min " + (seconds > 0 ? seconds + "s" : "")) : (seconds + "s");
            mc.player.sendMessage(Text.literal("§6[HugoAutoSell] §a✔ Verkauf abgeschlossen! Nächster Durchlauf in §e" + timeStr + "§7."), false);
        }
    }

    private int getTopContainerSlotCount(ScreenHandler handler) {
        if (handler instanceof GenericContainerScreenHandler genericHandler) {
            return genericHandler.getRows() * 9;
        }
        int total = handler.slots.size();
        if (total >= 90) return 54;
        if (total >= 63) return 27;
        return Math.max(0, total - 36);
    }

    private int findConfirmButtonSlot(ScreenHandler handler, int topSlots) {
        for (int i = 0; i < topSlots; i++) {
            Slot slot = handler.getSlot(i);
            if (slot != null && slot.hasStack()) {
                ItemStack stack = slot.getStack();
                String name = stack.getName().getString().toLowerCase();
                String itemId = stack.getItem().toString().toLowerCase();
                if (name.contains("bestätig") || name.contains("verkauf") || name.contains("confirm")
                        || name.contains("sell") || name.contains("ja") || name.contains("accept")
                        || itemId.contains("lime") || itemId.contains("green")
                        || itemId.contains("emerald") || itemId.contains("check")) {
                    return i;
                }
            }
        }

        int total = handler.slots.size();
        if (total > 53) return 53;
        if (topSlots > 0) return topSlots - 1;
        return 0;
    }

    private void renderActionBar(MinecraftClient mc) {
        if (state == State.WAITING_TIMER) {
            int totalSeconds = remainingCooldownTicks / 20;
            int minutes = totalSeconds / 60;
            int seconds = totalSeconds % 60;
            String timeFormatted = String.format("%02d:%02d", minutes, seconds);
            mc.player.sendMessage(Text.literal("§6[AutoSell] §aAktiv §7| Nächster Verkauf in: §e" + timeFormatted), true);
        } else {
            String stepName = switch (state) {
                case OPENING_CHEST -> "Öffne Kiste...";
                case WAITING_FOR_CHEST_SCREEN -> "Warte auf Kiste...";
                case LOOTING_CHEST -> "Leere Kiste...";
                case CLOSING_CHEST -> "Schließe Kiste...";
                case WAITING_FOR_INVENTORY_SYNC -> "Warte auf Inventar-Sync...";
                case WAITING_BEFORE_COMMAND -> "Warte vor Befehl...";
                case SENDING_SELL_COMMAND -> "Sende /sell...";
                case WAITING_FOR_SELL_SCREEN -> "Warte auf Sell-GUI...";
                case FILLING_SELL_GUI -> "Lege Items in Verkauf...";
                case CONFIRMING_SELL -> "Bestätige Verkauf (✅)...";
                case FINISHING_CYCLE -> "Fertiggestellt!";
                default -> "In Arbeit...";
            };
            mc.player.sendMessage(Text.literal("§6[AutoSell] §b" + stepName), true);
        }
    }
}
