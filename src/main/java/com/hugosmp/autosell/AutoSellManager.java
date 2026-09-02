package com.hugosmp.autosell;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.registry.Registries;
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
        WAITING_FOR_ORDER_SCREEN,
        WAITING_FOR_ORDER_DELIVERY_SCREEN,
        WAITING_FOR_ORDER_CONFIRMATION,
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
    private BlockPos targetChestPos;

    {
        // Beim Instanziieren geladene Position aus Config übernehmen
        targetChestPos = config.getChestPos();
    }

    private State state = State.IDLE;
    private int stateTicks = 0;
    private int remainingCooldownTicks = 0;
    private int currentSlotIndex = 0;
    private int fillPlayerSlotIndex = 0;
    private int fillPass = 0;
    private String orderedItemId;

    private AutoSellManager() {}

    public AutoSellConfig getConfig() {
        return config;
    }

    /** Switches between selling and ordering the first item taken from the chest. */
    public boolean toggleCommandMode() {
        config.orderMode = !config.orderMode;
        config.save();
        return config.orderMode;
    }

    public boolean isActive() {
        return active;
    }

    public BlockPos getTargetChestPos() {
        return targetChestPos;
    }

    public State getState() {
        return state;
    }

    public int getRemainingCooldownTicks() {
        return remainingCooldownTicks;
    }

    public void toggle() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        if (active) {
            stop();
            actionBar(mc, "§6[HugoAutoSell] §c✖ Makro DEAKTIVIERT!");
            return;
        }

        // Versuche 1: Kiste anvisiert?
        HitResult hit = mc.crosshairTarget;
        if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) hit;
            BlockPos pos = blockHit.getBlockPos();
            if (!isStorageBlock(mc, pos)) {
                actionBar(mc, "§6[HugoAutoSell] §cDer Block ist keine Kiste/Truhe! §7Siehe /autosell status");
                return;
            }
            this.targetChestPos = pos;
            config.setChestPos(pos);
            start();
            actionBar(mc, "§6[HugoAutoSell] §a✔ Makro AKTIVIERT! §7Zielkiste: §e"
                    + pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
            chat(mc, "§6[HugoAutoSell] §7Intervall: §e" + config.intervalSeconds
                    + "s §7| Starte ersten Verkauf...");
            return;
        }

        // Versuche 2: Zuvor gespeicherte Kiste vorhanden?
        if (this.targetChestPos != null) {
            start();
            actionBar(mc, "§6[HugoAutoSell] §a✔ AKTIVIERT! §7(angepasste Position: §e"
                    + targetChestPos.getX() + ", " + targetChestPos.getY() + ", " + targetChestPos.getZ() + "§a)");
            chat(mc, "§6[HugoAutoSell] §7Intervall: §e" + config.intervalSeconds + "s §7| Starte ersten Verkauf...");
            return;
        }

        // Weder noch: Fehler
        actionBar(mc, "§6[HugoAutoSell] §cBitte schaue eine Kiste/Truhe an und drücke [K] erneut!");
        chat(mc, "§6[HugoAutoSell] §7Oder nutze §e/autosell status§7 um die aktuelle Lage zu sehen.");
    }

    public void start() {
        this.active = true;
        this.remainingCooldownTicks = 0;
        this.state = State.OPENING_CHEST;
        this.stateTicks = 0;
        this.fillPlayerSlotIndex = 0;
        this.fillPass = 0;
        this.orderedItemId = null;
    }

    public void stop() {
        this.active = false;
        this.state = State.IDLE;
        this.stateTicks = 0;
        this.remainingCooldownTicks = 0;
        this.fillPlayerSlotIndex = 0;
        this.fillPass = 0;
        this.orderedItemId = null;
    }

    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) {
            return;
        }

        if (active) {
            renderActionBar(mc);
            stateTicks++;

            switch (state) {
                case WAITING_TIMER:
                    if (remainingCooldownTicks > 0) {
                        if (remainingCooldownTicks == 1200) {
                            chat(mc, "§6[HugoAutoSell] §e⏳ Nächster Verkauf in 1 Minute...");
                        } else if (remainingCooldownTicks == 200) {
                            chat(mc, "§6[HugoAutoSell] §e⏳ Nächster Verkauf in 10 Sekunden...");
                        }
                        remainingCooldownTicks--;
                    } else {
                        chat(mc, "§6[HugoAutoSell] §b🔄 Timer abgelaufen: Öffne Kiste und verkaufe...");
                        state = State.OPENING_CHEST;
                        stateTicks = 0;
                    }
                    break;

                case OPENING_CHEST:
                    if (targetChestPos == null) {
                        stop();
                        chat(mc, "§c[AutoSell] Keine Kiste ausgewählt! §7Siehe /autosell status");
                        return;
                    }

                    double distSq = mc.player.getBlockPos().getSquaredDistance(targetChestPos);
                    if (distSq > 36.0) {
                        chat(mc, "§c[AutoSell] §7Kiste zu weit entfernt (max. 6 Blöcke)! Makro gestoppt.");
                        stop();
                        return;
                    }

                    if (!isStorageBlock(mc, targetChestPos)) {
                        chat(mc, "§c[AutoSell] §7Gespeicherte Position ist keine Kiste mehr. Makro gestoppt.");
                        config.setChestPos(null);
                        targetChestPos = null;
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
                                if (config.orderMode && orderedItemId == null) {
                                    orderedItemId = Registries.ITEM.getId(slot.getStack().getItem()).getPath();
                                }
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
                        if (config.orderMode && orderedItemId == null) {
                            chat(mc, "§6[HugoAutoSell] §eOrder-Modus: Kiste war leer, kein Item zum Bestellen.");
                            finishCycle();
                            break;
                        }
                        String cmd = config.orderMode
                                ? "order " + orderedItemId
                                : (config.sellCommand.startsWith("/") ? config.sellCommand.substring(1) : config.sellCommand);
                        mc.player.networkHandler.sendChatCommand(cmd);
                        chat(mc, "§6[HugoAutoSell] §7Sende §e/" + cmd);
                    }
                    state = config.orderMode ? State.WAITING_FOR_ORDER_SCREEN : State.WAITING_FOR_SELL_SCREEN;
                    stateTicks = 0;
                    break;

                case WAITING_FOR_ORDER_SCREEN:
                    if (mc.currentScreen instanceof HandledScreen<?> orderScreen && stateTicks >= 8) {
                        ScreenHandler orderHandler = orderScreen.getScreenHandler();
                        // HugoSMP sortiert die Ergebnisse absteigend nach Preis: Slot 0 ist das beste Angebot.
                        if (getTopContainerSlotCount(orderHandler) > 0 && orderHandler.getSlot(0).hasStack()) {
                            chat(mc, "§6[HugoAutoSell] §7Wähle beste Order (erster Slot)...");
                            mc.interactionManager.clickSlot(orderHandler.syncId, 0, 0, SlotActionType.PICKUP, mc.player);
                            state = State.WAITING_FOR_ORDER_DELIVERY_SCREEN;
                            stateTicks = 0;
                        } else {
                            chat(mc, "§6[HugoAutoSell] §cKeine offene Order für " + orderedItemId + " gefunden.");
                            finishCycle();
                        }
                    } else if (stateTicks > 80) {
                        chat(mc, "§6[HugoAutoSell] §cOrder-Menü wurde nicht geöffnet, überspringe...");
                        finishCycle();
                    }
                    break;

                case WAITING_FOR_ORDER_DELIVERY_SCREEN:
                    if (mc.currentScreen instanceof HandledScreen<?> deliveryScreen && stateTicks >= 8) {
                        ScreenHandler deliveryHandler = deliveryScreen.getScreenHandler();
                        int deliverSlot = findSlotByName(deliveryHandler, "ganzes inventar liefern");
                        if (deliverSlot >= 0) {
                            chat(mc, "§6[HugoAutoSell] §7Liefere gesamtes Inventar...");
                            mc.interactionManager.clickSlot(deliveryHandler.syncId, deliverSlot, 0, SlotActionType.PICKUP, mc.player);
                            state = State.WAITING_FOR_ORDER_CONFIRMATION;
                            stateTicks = 0;
                        } else if (stateTicks > 80) {
                            chat(mc, "§6[HugoAutoSell] §cSchaltfläche 'Ganzes Inventar liefern' nicht gefunden.");
                            finishCycle();
                        }
                    } else if (stateTicks > 80) {
                        chat(mc, "§6[HugoAutoSell] §cOrder-Liefermenü wurde nicht geöffnet, überspringe...");
                        finishCycle();
                    }
                    break;

                case WAITING_FOR_ORDER_CONFIRMATION:
                    if (mc.currentScreen instanceof HandledScreen<?> confirmationScreen && stateTicks >= 8) {
                        ScreenHandler confirmationHandler = confirmationScreen.getScreenHandler();
                        int confirmSlot = findSlotByName(confirmationHandler, "bestätigen");
                        if (confirmSlot >= 0) {
                            chat(mc, "§6[HugoAutoSell] §7Bestätige Order...");
                            mc.interactionManager.clickSlot(confirmationHandler.syncId, confirmSlot, 0, SlotActionType.PICKUP, mc.player);
                            state = State.FINISHING_CYCLE;
                            stateTicks = 0;
                        } else if (stateTicks > 80) {
                            chat(mc, "§6[HugoAutoSell] §cBestätigungs-Schaltfläche nicht gefunden.");
                            finishCycle();
                        }
                    } else if (stateTicks > 80) {
                        chat(mc, "§6[HugoAutoSell] §cOrder-Bestätigung wurde nicht geöffnet, überspringe...");
                        finishCycle();
                    }
                    break;

                case WAITING_FOR_SELL_SCREEN:
                    if (mc.currentScreen instanceof HandledScreen<?>) {
                        state = State.FILLING_SELL_GUI;
                        stateTicks = 0;
                        fillPlayerSlotIndex = 0;
                        fillPass = 0;
                    } else if (stateTicks > 80) {
                        chat(mc, "§6[HugoAutoSell] §cSell-GUI wurde nicht geöffnet, überspringe...");
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
                            chat(mc, "§6[HugoAutoSell] §7Bestätige Verkauf (Slot §e" + confirmSlot + "§7)...");
                            mc.interactionManager.clickSlot(confirmHandler.syncId, confirmSlot, 0, SlotActionType.PICKUP, mc.player);
                        } else {
                            chat(mc, "§6[HugoAutoSell] §cKein Bestätigungs-Button gefunden!");
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
            String action = config.orderMode ? "Bestellung" : "Verkauf";
            chat(mc, "§6[HugoAutoSell] §a✔ " + action + " abgeschlossen! Nächster Durchlauf in §e" + timeStr + "§7.");
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
                net.minecraft.item.ItemStack stack = slot.getStack();
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

    /** Finds a HugoSMP menu button by its visible label, only in the menu area. */
    private int findSlotByName(ScreenHandler handler, String expectedName) {
        int topSlots = getTopContainerSlotCount(handler);
        String expected = expectedName.toLowerCase(java.util.Locale.ROOT);
        for (int i = 0; i < topSlots; i++) {
            Slot slot = handler.getSlot(i);
            if (slot != null && slot.hasStack()
                    && slot.getStack().getName().getString().toLowerCase(java.util.Locale.ROOT).contains(expected)) {
                return i;
            }
        }
        return -1;
    }

    private void renderActionBar(MinecraftClient mc) {
        if (state == State.WAITING_TIMER) {
            int totalSeconds = remainingCooldownTicks / 20;
            int minutes = totalSeconds / 60;
            int seconds = totalSeconds % 60;
            String timeFormatted = String.format("%02d:%02d", minutes, seconds);
            actionBar(mc, "§6[AutoSell] §aAktiv §7| Nächster Verkauf: §e" + timeFormatted);
        } else {
            String stepName = switch (state) {
                case OPENING_CHEST -> "Öffne Kiste...";
                case WAITING_FOR_CHEST_SCREEN -> "Warte auf Kiste...";
                case LOOTING_CHEST -> "Leere Kiste...";
                case CLOSING_CHEST -> "Schließe Kiste...";
                case WAITING_FOR_INVENTORY_SYNC -> "Sync Inventar...";
                case WAITING_BEFORE_COMMAND -> "Warte vor Befehl...";
                case SENDING_SELL_COMMAND -> config.orderMode ? "Sende /order..." : "Sende /" + config.sellCommand + "...";
                case WAITING_FOR_ORDER_SCREEN -> "Warte auf Order-Liste...";
                case WAITING_FOR_ORDER_DELIVERY_SCREEN -> "Wähle Inventar-Lieferung...";
                case WAITING_FOR_ORDER_CONFIRMATION -> "Bestätige Order...";
                case WAITING_FOR_SELL_SCREEN -> "Warte auf Sell-GUI...";
                case FILLING_SELL_GUI -> "Lege Items in Verkauf...";
                case CONFIRMING_SELL -> "Bestätige Verkauf (✅)...";
                case FINISHING_CYCLE -> "Fertiggestellt!";
                default -> "In Arbeit...";
            };
            actionBar(mc, "§6[AutoSell] §b" + stepName);
        }
    }

    private void actionBar(MinecraftClient mc, String text) {
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal(text), true);
        }
    }

    private void chat(MinecraftClient mc, String text) {
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal(text), false);
        }
    }

    /** Prüft ob Block an Position eine Lager-Kiste ist (Truhe, Fass, Shulker, etc.) */
    public static boolean isStorageBlock(MinecraftClient mc, BlockPos pos) {
        if (mc.world == null) return false;
        BlockState state = mc.world.getBlockState(pos);
        Block block = state.getBlock();
        String id = Registries.BLOCK.getId(block).toString();
        return id.contains("chest")
            || id.contains("barrel")
            || id.contains("shulker_box")
            || id.contains("hopper")
            || id.contains("dispenser")
            || id.contains("dropper")
            || id.contains("crate")
            || id.contains("chiseled_bookshelf");
    }
}
