package za.co.neroland.nerospace.menu;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import za.co.neroland.nerospace.registry.ModMenuTypes;
import za.co.neroland.nerospace.rocket.DestinationSelection;
import za.co.neroland.nerospace.rocket.DestinationTrace;
import za.co.neroland.nerospace.rocket.Destinations;
import za.co.neroland.nerospace.rocket.DockSelection;
import za.co.neroland.nerospace.rocket.LaunchControllerBlockEntity;
import za.co.neroland.nerospace.rocket.PadSelection;
import za.co.neroland.nerospace.rocket.RocketEntity;

/**
 * Menu for the Launch Controller: three material slots (Launch Pad / Station Wall / Launch Gantry), the
 * player inventory, and synced data (target tier + needed counts + buildability). Buttons route through
 * {@link #clickMenuButton} — ids 1–4 pick the target tier, id 0 triggers the build.
 */
public class LaunchControllerMenu extends AbstractContainerMenu {

    public static final int BUTTON_BUILD = 0;
    /** Pick target tier {@code n} via button id {@code n} (1..4). */
    public static final int TIER_BASE = 0;
    /** Toggle the holographic pad preview. */
    public static final int BUTTON_TOGGLE_HOLOGRAM = 5;
    /** Switch between pad-build and launch modes. */
    public static final int BUTTON_TOGGLE_MODE = 6;
    /** Cycle the docked rocket's destination (launch mode). */
    public static final int BUTTON_CYCLE_DEST = 7;
    /** Launch the docked rocket (launch mode). */
    public static final int BUTTON_LAUNCH = 8;
    /** Step the docked rocket's destination back (launch mode). */
    public static final int BUTTON_PREV_DEST = 9;
    /** Select the docked rocket's destination {@code n} via button id {@code SELECT_DEST_BASE + n} (launch mode). */
    public static final int SELECT_DEST_BASE = 100;
    /** Cycle the docked rocket's docking target like the Rocket screen's cycler (launch mode). */
    public static final int BUTTON_CYCLE_STATION = 10;
    /** Cycle the docked rocket's landing pad like the Rocket screen's cycler (launch mode). */
    public static final int BUTTON_CYCLE_PAD = 11;
    /** Select dock {@code slot} via {@link DockSelection#buttonFor} ({@code 200 + slot + 2}; New Station 200, origin 201). */
    public static final int SELECT_STATION_BASE = DockSelection.SELECT_BASE;
    /** Select landing pad {@code i} via {@link PadSelection#buttonFor} ({@code 300 + i + 1}; nearest 300). */
    public static final int SELECT_PAD_BASE = PadSelection.SELECT_BASE;

    private static final int SLOTS = 3;
    private static final int DATA_COUNT = LaunchControllerBlockEntity.DATA_COUNT;

    private final Container container;
    private final ContainerData data;
    @Nullable
    private final LaunchControllerBlockEntity controller;
    /** This viewer's Core-gate mask (bit per destination index still closed for them), so the screen predicts steps. */
    private final DataSlot gateMask;
    /** This viewer's last destination answer from the server ({@link DestinationSelection#encodeOutcome}). */
    private final DataSlot lastOutcome = DataSlot.standalone();
    private int outcomeSequence;
    /** This viewer's last dock/pad answer ({@link DestinationSelection#encodeOutcome}), kept apart from destinations. */
    private final DataSlot lastPickOutcome = DataSlot.standalone();
    private int pickSequence;

    public LaunchControllerMenu(int id, Inventory playerInventory) {
        this(id, playerInventory, new SimpleContainer(SLOTS), new SimpleContainerData(DATA_COUNT), null);
    }

    @SuppressWarnings("this-escape")
    public LaunchControllerMenu(int id, Inventory playerInventory, Container container, ContainerData data,
            @Nullable LaunchControllerBlockEntity controller) {
        super(ModMenuTypes.LAUNCH_CONTROLLER.get(), id);
        checkContainerSize(container, SLOTS);
        checkContainerDataCount(data, DATA_COUNT);
        this.container = container;
        this.data = data;
        this.controller = controller;

        this.addSlot(new MaterialSlot(container, LaunchControllerBlockEntity.SLOT_PAD, 44, 44));
        this.addSlot(new MaterialSlot(container, LaunchControllerBlockEntity.SLOT_WALL, 70, 44));
        this.addSlot(new MaterialSlot(container, LaunchControllerBlockEntity.SLOT_GANTRY, 96, 44));

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 128 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 186));
        }
        this.addDataSlots(data);
        this.gateMask = controller != null ? viewerGateSlot(playerInventory.player) : DataSlot.standalone();
        this.addDataSlot(this.gateMask);
        this.addDataSlot(this.lastOutcome);
        this.addDataSlot(this.lastPickOutcome);
    }

    /** The server side of the per-viewer gate mask, recomputed at most once a second. */
    private static DataSlot viewerGateSlot(Player player) {
        return new DataSlot() {
            private long refreshedAt = DestinationSelection.NEVER;
            private int mask;

            @Override
            public int get() {
                long now = player.level().getGameTime();
                if (DestinationSelection.refreshDue(now, this.refreshedAt, RocketEntity.VIEWER_DATA_REFRESH_TICKS)) {
                    this.refreshedAt = now;
                    this.mask = RocketEntity.gateMask(player);
                }
                return this.mask;
            }

            @Override
            public void set(int value) {
                // Server-computed; read-only from the client.
            }
        };
    }

    /** Records the server's answer to a pick so the next broadcast carries it to this viewer's screen. */
    private void report(DestinationSelection.Outcome outcome) {
        this.outcomeSequence++;
        this.lastOutcome.set(DestinationSelection.encodeOutcome(this.outcomeSequence, outcome));
    }

    /** Records the server's answer to a dock or pad pick for this viewer's screen. */
    private void reportPick(DestinationSelection.Outcome outcome) {
        this.pickSequence++;
        this.lastPickOutcome.set(DestinationSelection.encodeOutcome(this.pickSequence, outcome));
    }

    /** The server's last answer to this viewer's dock or pad pick; 0 = none yet. */
    public int lastPickOutcomeCode() {
        return this.lastPickOutcome.get();
    }

    /** Bit per destination index whose Core gate is still closed for this viewer (0 for a creative player). */
    public int gateMask() {
        return this.gateMask.get();
    }

    /** The server's last answer to this viewer's pick ({@link DestinationSelection#encodeOutcome}); 0 = none yet. */
    public int lastOutcomeCode() {
        return this.lastOutcome.get();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        LaunchControllerBlockEntity current = this.controller;
        if (current == null) {
            return false;
        }
        if (id == BUTTON_BUILD) {
            current.build(player);
            return true;
        }
        if (id == BUTTON_TOGGLE_HOLOGRAM) {
            current.toggleHologram();
            return true;
        }
        if (id == BUTTON_TOGGLE_MODE) {
            current.toggleMode();
            return true;
        }
        if (id == BUTTON_CYCLE_DEST || id == BUTTON_PREV_DEST) {
            DestinationSelection.Outcome outcome = current.stepRocketDestination(id == BUTTON_CYCLE_DEST ? 1 : -1,
                    player);
            report(outcome);
            DestinationTrace.log("controller menu {} button {} -> {}", this.containerId, id, outcome);
            return true;
        }
        if (id >= SELECT_DEST_BASE && id < SELECT_DEST_BASE + Destinations.all().size()) {
            DestinationSelection.Outcome outcome = current.selectRocketDestination(id - SELECT_DEST_BASE, player);
            report(outcome);
            DestinationTrace.log("controller menu {} button {} -> index {} {}", this.containerId, id,
                    id - SELECT_DEST_BASE, outcome);
            return true;
        }
        if (id == BUTTON_LAUNCH) {
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                    && current.launchRocket(serverPlayer)) {
                serverPlayer.closeContainer(); // watch the ascent
            }
            return true;
        }
        if (id >= 1 && id <= 4) {
            current.setTargetTier(id);
            return true;
        }
        if (id == BUTTON_CYCLE_STATION) {
            reportPick(current.cycleRocketStation(player));
            return true;
        }
        if (id == BUTTON_CYCLE_PAD) {
            reportPick(current.cycleRocketPad(player));
            return true;
        }
        int slot = DockSelection.slotForButton(id);
        if (slot != DockSelection.NOT_A_SLOT) {
            DestinationSelection.Outcome outcome = current.selectRocketStation(slot, player);
            reportPick(outcome);
            DestinationTrace.log("controller menu {} dock pick {} {}", this.containerId, slot, outcome);
            return true; // always broadcast so the screen reconciles its optimistic highlight
        }
        int pad = PadSelection.indexForButton(id);
        if (pad != PadSelection.NOT_A_PAD) {
            DestinationSelection.Outcome outcome = current.selectRocketPad(pad, player);
            reportPick(outcome);
            DestinationTrace.log("controller menu {} pad pick {} {}", this.containerId, pad, outcome);
            return true;
        }
        return false;
    }

    public int targetTier() {
        return this.data.get(0);
    }

    /** The tier the pad in front of the controller actually forms (0 = no pad there). */
    public int builtTier() {
        return this.data.get(21);
    }

    public int neededPads() {
        return this.data.get(1);
    }

    public int neededWall() {
        return this.data.get(2);
    }

    public int neededGantry() {
        return this.data.get(3);
    }

    public boolean canBuild() {
        return this.data.get(4) != 0;
    }

    public boolean isHologram() {
        return this.data.get(5) != 0;
    }

    public float fuelFrac() {
        return frac(6, 7);
    }

    public float oxygenFrac() {
        return frac(8, 9);
    }

    public float powerFrac() {
        return frac(10, 11);
    }

    private float frac(int amount, int cap) {
        int c = this.data.get(cap);
        return c <= 0 ? 0f : Math.min(1f, this.data.get(amount) / (float) c);
    }

    // --- Launch mode ---------------------------------------------------------

    public boolean isLaunchMode() {
        return this.data.get(12) == 1;
    }

    public boolean rocketPresent() {
        return this.data.get(13) != 0;
    }

    public int rocketTier() {
        return this.data.get(14) + 1; // ordinal → 1-based tier
    }

    public float rocketFuelFrac() {
        return this.data.get(15) / 100f;
    }

    public float rocketOxygenFrac() {
        return this.data.get(16) / 100f;
    }

    public float rocketPowerFrac() {
        return this.data.get(17) / 100f;
    }

    public boolean rocketLaunchable() {
        return this.data.get(20) != 0;
    }

    /** The docked rocket's selected {@link Destinations} index. */
    public int rocketDestIndex() {
        return this.data.get(18);
    }

    /** The docked rocket's docking slot ({@link DockSelection#ORIGIN} = the shared origin platform). */
    public int rocketStationSlot() {
        return this.data.get(LaunchControllerBlockEntity.DATA_STATION);
    }

    /** The docked rocket's landing-pad index ({@link PadSelection#AUTO} = nearest). */
    public int rocketPadIndex() {
        return this.data.get(LaunchControllerBlockEntity.DATA_PAD);
    }

    /** Players aboard the docked rocket (2.1). */
    public int rocketCrew() {
        return this.data.get(LaunchControllerBlockEntity.DATA_CREW);
    }

    /** Seats the docked rocket's oxygen covers for its flight ("O₂ for N", 2.1). */
    public int rocketOxygenFor() {
        return this.data.get(LaunchControllerBlockEntity.DATA_OXYGEN_FOR);
    }

    /** Seats per rocket on this server (2.1); at least 1. */
    public int maxCrew() {
        return Math.max(1, this.data.get(LaunchControllerBlockEntity.DATA_MAX_CREW));
    }

    /** Bit per {@link Destinations} index the docked rocket can reach from here (server-computed). */
    public int rocketDestMask() {
        return this.data.get(19);
    }

    /** Display name of the docked rocket's selected destination. */
    public String rocketDestName() {
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> key =
                za.co.neroland.nerospace.rocket.Destinations.byIndex(this.data.get(18));
        return key == null ? "—" : za.co.neroland.nerospace.rocket.Destinations.name(key);
    }

    /** Localised display name of the docked rocket's selected destination (for the GUI). */
    public net.minecraft.network.chat.Component rocketDestDisplayName() {
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> key =
                za.co.neroland.nerospace.rocket.Destinations.byIndex(this.data.get(18));
        return key == null ? net.minecraft.network.chat.Component.literal("—")
                : za.co.neroland.nerospace.rocket.Destinations.displayName(key);
    }

    @Override
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack moved = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack raw = slot.getItem();
            moved = raw.copy();
            int invStart = SLOTS;
            int invEnd = invStart + 36;
            if (index < invStart) {
                if (!this.moveItemStackTo(raw, invStart, invEnd, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(raw, 0, invStart, false)) {
                return ItemStack.EMPTY;
            }
            if (raw.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return moved;
    }

    /** A material slot that only accepts the pad block its index expects. */
    private class MaterialSlot extends Slot {
        private final int slotIndex;

        MaterialSlot(Container container, int slotIndex, int x, int y) {
            super(container, slotIndex, x, y);
            this.slotIndex = slotIndex;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            LaunchControllerBlockEntity c = controller;
            return c == null || c.accepts(this.slotIndex, stack);
        }
    }
}
