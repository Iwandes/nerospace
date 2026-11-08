package za.co.neroland.nerospace.rocket;

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

import za.co.neroland.nerospace.registry.ModDimensions;
import za.co.neroland.nerospace.registry.ModMenuTypes;

/**
 * Menu for the in-rocket UI. Carries the player inventory, a single fuel-intake slot (drop a fuel
 * bucket/canister to fuel the rocket), and five synced data values describing the rocket (fuel,
 * capacity, tier, launch-readiness, destination index and available-destination mask).
 *
 * <p>Buttons route through {@link #clickMenuButton} so no custom packet is needed: Launch, the cycle
 * button, and direct destination selection ({@code SELECT_DEST_BASE + index}) are all handled
 * server-side, where the menu holds a reference to the {@link RocketEntity}.</p>
 *
 * <p>Cross-loader note: this is a plain (non-extended) menu. The client never needs the entity — it
 * displays from the synced {@link ContainerData} and routes buttons to the server menu — so the
 * server opens it with the vanilla {@code openMenu(MenuProvider)} and we avoid the loader-divergent
 * extended-menu API. The multi-station founding rows are deferred with that subsystem.</p>
 */
public class RocketMenu extends AbstractContainerMenu {

    public static final int BUTTON_LAUNCH = 0;
    public static final int BUTTON_CYCLE_DEST = 1;
    /** Cycles which founded station the Orbital Station destination docks at (origin → each station → origin). */
    public static final int BUTTON_CYCLE_STATION = 2;
    /** Cycles the landing pad within the selected destination dimension (nearest → each pad → nearest). */
    public static final int BUTTON_CYCLE_PAD = 3;
    /**
     * 2.1: the viewer takes a seat, as crew behind the pilot, or as the pilot when nobody is aboard ({@link
     * RocketEntity#takeSeat}). Appended after every id in use; only ever seats the player who pressed it.
     */
    public static final int BUTTON_TAKE_SEAT = 4;
    /** Select destination {@code n} via button id {@code SELECT_DEST_BASE + n}. */
    public static final int SELECT_DEST_BASE = 100;
    /** Select dock {@code slot} via {@link DockSelection#buttonFor} ({@code 200 + slot + 2}; New Station 200, origin 201). */
    public static final int SELECT_STATION_BASE = DockSelection.SELECT_BASE;
    /** Select landing pad {@code i} via {@link PadSelection#buttonFor} ({@code 300 + i + 1}; nearest 300). */
    public static final int SELECT_PAD_BASE = PadSelection.SELECT_BASE;

    /**
     * Synced values: 0..13 as before, [14] = destinationGateMask, [15] = Observatory reveal count; 2.1 appends
     * [16] players aboard, [17] seats the onboard oxygen covers this flight ("O₂ for N"), [18] seats per rocket.
     */
    public static final int DATA_COUNT = 19;
    /** Data index of the number of players aboard (2.1). */
    public static final int DATA_CREW = 16;
    /** Data index of how many seats the onboard oxygen covers for this flight (2.1). */
    public static final int DATA_OXYGEN_FOR = 17;
    /** Data index of the server's seat count, {@code rockets.maxCrew} (2.1). */
    public static final int DATA_MAX_CREW = 18;
    /** Data index of the per-viewer Core-gate mask (bit per {@link Destinations} index). */
    public static final int DATA_GATE_MASK = 14;
    /** Data index of the viewer's Observatory reveal count (how many next locked worlds show as cards). */
    public static final int DATA_REVEAL = 15;
    private static final int FUEL_SLOT_INDEX = 0;
    private static final int PLAYER_INV_START = 1;

    private static final int FUEL_SLOT_X = 186;
    private static final int FUEL_SLOT_Y = 30;
    /** Player-inventory block origin (must match RocketScreen's grid). */
    public static final int INV_X = 26;
    public static final int INV_Y = 164;

    private final ContainerData data;
    private final Container fuelContainer;
    /** One past the last player-inventory slot (varies when an inventory-expanding mod is present). */
    private final int playerInvEnd;
    @Nullable
    private final RocketEntity rocket;
    /**
     * This viewer's last destination answer from the server ({@link DestinationSelection#encodeOutcome}), synced
     * after the shared data so the screen can name the real reason for a refusal. Per menu, so per viewer.
     */
    private final DataSlot lastOutcome = DataSlot.standalone();
    private int outcomeSequence;
    /** This viewer's last dock/pad answer ({@link DestinationSelection#encodeOutcome}), kept apart from destinations. */
    private final DataSlot lastPickOutcome = DataSlot.standalone();
    private int pickSequence;
    /** This viewer's Take seat state ({@link Crew.SeatAnswer#encode}), refreshed on the server every broadcast. */
    private final DataSlot seatState = DataSlot.standalone();
    /** The player this menu was opened for (the server checks the Take seat press came from them). */
    private final Player viewer;

    /** Client constructor (referenced by the menu type); rocket state arrives via the synced data. */
    public RocketMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, null, new SimpleContainerData(DATA_COUNT));
    }

    @SuppressWarnings("this-escape") // idiomatic Minecraft constructor wiring
    public RocketMenu(int containerId, Inventory playerInventory, @Nullable RocketEntity rocket, ContainerData data) {
        super(ModMenuTypes.ROCKET.get(), containerId);
        checkContainerDataCount(data, DATA_COUNT);
        this.rocket = rocket;
        this.data = data;
        this.fuelContainer = rocket != null ? rocket.getFuelInput() : new SimpleContainer(1);
        this.viewer = playerInventory.player;

        this.addSlot(new FuelSlot(this.fuelContainer, 0, FUEL_SLOT_X, FUEL_SLOT_Y));

        // Hand-rolled player-inventory slots (instead of the vanilla addStandardInventorySlots helper) so
        // extra main-inventory rows added by mods like Inventory Extended are reachable AND laid out under
        // our control. Detection is generic — any mod that grows the main list past 36 gets its rows shown.
        int rows = mainRows(playerInventory);
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, 9 + row * 9 + col, INV_X + col * 18, INV_Y + row * 18));
            }
        }
        int hotbarY = INV_Y + rows * 18 + 4;
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, INV_X + col * 18, hotbarY));
        }
        this.playerInvEnd = PLAYER_INV_START + rows * 9 + 9;

        this.addDataSlots(data);
        this.addDataSlot(this.lastOutcome);
        this.addDataSlot(this.lastPickOutcome);
        this.addDataSlot(this.seatState);
    }

    @Override
    public void broadcastChanges() {
        RocketEntity current = this.rocket;
        if (current != null) {
            this.seatState.set(current.seatAnswer(this.viewer, true).encode());
        }
        super.broadcastChanges();
    }

    /** Records the server's answer to a dock or pad pick for this viewer's screen. */
    private void reportPick(DestinationSelection.Outcome outcome) {
        this.pickSequence++;
        this.lastPickOutcome.set(DestinationSelection.encodeOutcome(this.pickSequence, outcome));
    }

    /** Records the server's answer to a pick so the next broadcast carries it to this viewer's screen. */
    private void report(DestinationSelection.Outcome outcome) {
        this.outcomeSequence++;
        this.lastOutcome.set(DestinationSelection.encodeOutcome(this.outcomeSequence, outcome));
    }

    /**
     * Rows of main player inventory above the hotbar: 3 in vanilla, more when an inventory-expanding mod
     * (e.g. Inventory Extended) has grown the main item list. Hotbar is indices 0–8; main rows follow.
     */
    public static int mainRows(Inventory playerInventory) {
        return Math.max(3, (playerInventory.getNonEquipmentItems().size() - 9) / 9);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        RocketEntity current = this.rocket; // local copy so the null check holds for the analyzer
        if (current == null) {
            DestinationTrace.log("rocket menu {} button {} rejected: no rocket", this.containerId, id);
            return false;
        }
        if (id == BUTTON_LAUNCH) {
            // Pressing Launch is what boards the player; if the ascent starts, close the console so they
            // watch the lift-off from inside the rocket.
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                    && current.boardAndLaunch(serverPlayer)) {
                serverPlayer.closeContainer();
            }
            return true;
        }
        if (id == BUTTON_TAKE_SEAT) {
            // The presser's own seat only, and only from their own open console. The menu stays open so the
            // button reads "Seated" and the crew count updates; the pilot can press Launch from here.
            if (player instanceof net.minecraft.server.level.ServerPlayer seating) {
                Crew.SeatAnswer answer = current.takeSeat(seating, isViewer(seating));
                DestinationTrace.log("rocket menu {} take seat {}", this.containerId, answer);
            }
            return true;
        }
        if (id == BUTTON_CYCLE_DEST) {
            DestinationSelection.Outcome outcome = current.stepDestination(1, player);
            report(outcome);
            DestinationTrace.log("rocket menu {} cycle -> dest {} mask {} {}", this.containerId,
                    current.getDestinationIndex(), current.destinationMask(), outcome);
            return true;
        }
        if (id == BUTTON_CYCLE_STATION) {
            current.cycleStation(player); // pilot-only (2.1): a seated crew member's press is ignored
            return true;
        }
        if (id == BUTTON_CYCLE_PAD) {
            current.cyclePad(player);
            return true;
        }
        if (id >= SELECT_DEST_BASE && id < SELECT_DEST_BASE + Destinations.all().size()) {
            int index = id - SELECT_DEST_BASE;
            DestinationSelection.Outcome outcome = current.selectDestination(index, player);
            report(outcome);
            DestinationTrace.log("rocket menu {} button {} -> index {} mask {} gate {} {}", this.containerId, id,
                    index, current.destinationMask(), RocketEntity.gateMask(player), outcome);
            return true; // always broadcast so the screen reconciles its optimistic highlight
        }
        int slot = DockSelection.slotForButton(id);
        if (slot != DockSelection.NOT_A_SLOT) {
            DestinationSelection.Outcome outcome = current.selectStation(slot, player);
            reportPick(outcome);
            DestinationTrace.log("rocket menu {} dock pick {} {}", this.containerId, slot, outcome);
            return true; // always broadcast so the screen reconciles its optimistic highlight
        }
        int pad = PadSelection.indexForButton(id);
        if (pad != PadSelection.NOT_A_PAD) {
            DestinationSelection.Outcome outcome = current.selectPad(pad, player);
            reportPick(outcome);
            DestinationTrace.log("rocket menu {} pad pick {} {}", this.containerId, pad, outcome);
            return true;
        }
        DestinationTrace.log("rocket menu {} ignored button {}", this.containerId, id);
        return false;
    }

    /** Whether {@code player} is this console's own viewer, with this console open. */
    boolean isViewer(Player player) {
        return player == this.viewer && player.containerMenu == this;
    }

    @Override
    public boolean stillValid(Player player) {
        RocketEntity current = this.rocket;
        if (current == null) {
            return true;
        }
        return current.isAlive() && !current.isRemoved() && player.distanceTo(current) < 8.0F;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack moved = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack raw = slot.getItem();
            moved = raw.copy();
            if (index == FUEL_SLOT_INDEX) {
                if (!this.moveItemStackTo(raw, PLAYER_INV_START, this.playerInvEnd, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (RocketEntity.isFuelContainer(raw)) {
                if (!this.moveItemStackTo(raw, FUEL_SLOT_INDEX, FUEL_SLOT_INDEX + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                return ItemStack.EMPTY;
            }

            if (raw.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            if (raw.getCount() == moved.getCount()) {
                return ItemStack.EMPTY;
            }
            slot.onTake(player, raw);
        }
        return moved;
    }

    // --- Screen helpers -----------------------------------------------------

    public int getFuel() {
        return this.data.get(0);
    }

    public int getCapacity() {
        return this.data.get(1);
    }

    public RocketTier getTier() {
        return RocketTier.byOrdinal(this.data.get(2));
    }

    public boolean isLaunchable() {
        return this.data.get(3) != 0;
    }

    /** Whether the rocket is standing on a pad that meets its tier (independent of fuel/destination). */
    public boolean isPadValid() {
        return this.data.get(9) != 0;
    }

    public int getDestinationIndex() {
        return this.data.get(4);
    }

    /** Current fuel as a 0–100 percentage of the tier capacity. */
    public int getFuelPercent() {
        int capacity = getCapacity();
        return capacity == 0 ? 0 : Math.min(100, getFuel() * 100 / capacity);
    }

    /** Onboard oxygen (life-support) in millibuckets. */
    public int getOxygen() {
        return this.data.get(7);
    }

    /** Onboard oxygen tank capacity for the current tier, in millibuckets. */
    public int getOxygenCapacity() {
        return this.data.get(8);
    }

    /** Current oxygen as a 0–100 percentage of the tier capacity. */
    public int getOxygenPercent() {
        int capacity = getOxygenCapacity();
        return capacity == 0 ? 0 : Math.min(100, getOxygen() * 100 / capacity);
    }

    /** Onboard power (FE). */
    public int getPower() {
        return this.data.get(10);
    }

    /** Onboard power buffer capacity for the current tier, in FE. */
    public int getPowerCapacity() {
        return this.data.get(11);
    }

    /** Current power as a 0–100 percentage of the tier capacity. */
    public int getPowerPercent() {
        int capacity = getPowerCapacity();
        return capacity == 0 ? 0 : Math.min(100, getPower() * 100 / capacity);
    }

    /** Display name of the currently selected destination. */
    public String getDestinationName() {
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> key =
                Destinations.byIndex(getDestinationIndex());
        return key == null ? "—" : Destinations.name(key);
    }

    public boolean hasMultipleDestinations() {
        return Integer.bitCount(getDestinationMask()) > 1;
    }

    public int getDestinationMask() {
        return this.data.get(6);
    }

    // --- Orbital-station selection (shown only when the Orbital Station is the destination) ----------

    /** Selected founded-station slot, or {@code -1} for the shared origin platform. */
    public int getStationSlot() {
        return this.data.get(5);
    }

    /** Whether the currently selected destination is the Orbital Station dimension. */
    public boolean isStationDestination() {
        return ModDimensions.STATION_LEVEL.equals(Destinations.byIndex(getDestinationIndex()));
    }

    /** Whether the destination is a normal dimension (shows the pad cycler instead of the dock cycler). */
    public boolean isPadDestination() {
        return Destinations.byIndex(getDestinationIndex()) != null && !isStationDestination();
    }

    /** Selected landing-pad index within the destination dimension ({@code -1} = nearest/auto). */
    public int getPadIndex() {
        return this.data.get(12);
    }

    /** Display label for the selected landing pad (stable, int-only — real names arrive by {@code PadSyncPayload}). */
    public String getPadName() {
        int idx = getPadIndex();
        return idx < 0 ? "Nearest pad" : "Pad " + (idx + 1);
    }

    /** Bit per destination index whose Core gate is closed for this viewer (LOCKED_GATE cards). */
    public int getGateMask() {
        return this.data.get(DATA_GATE_MASK);
    }

    /** The server's last answer to this viewer's pick ({@link DestinationSelection#encodeOutcome}); 0 = none yet. */
    public int getLastOutcomeCode() {
        return this.lastOutcome.get();
    }

    /** The server's last answer to this viewer's dock or pad pick; 0 = none yet. */
    public int getLastPickOutcomeCode() {
        return this.lastPickOutcome.get();
    }

    /** How many of the next locked destinations the viewer's station Observatory reveals. */
    public int getRevealCount() {
        return this.data.get(DATA_REVEAL);
    }

    /** Fuel this launch will burn (millibuckets), computed from the trip distance + dimension surcharge. */
    /** This viewer's Take seat state (2.1); {@link Crew.SeatAnswer#OK} until the server has said otherwise. */
    public Crew.SeatAnswer getSeatAnswer() {
        return Crew.SeatAnswer.decode(this.seatState.get());
    }

    /** Players aboard (2.1). */
    public int getCrew() {
        return this.data.get(DATA_CREW);
    }

    /** Seats the onboard oxygen covers for this flight ("O₂ for N", 2.1). */
    public int getOxygenFor() {
        return this.data.get(DATA_OXYGEN_FOR);
    }

    /** Seats per rocket on this server ({@code rockets.maxCrew}, 2.1); at least 1. */
    public int getMaxCrew() {
        return Math.max(1, this.data.get(DATA_MAX_CREW));
    }

    /** Whether a full-enough crew is short of oxygen: two or more aboard and the reserve covers fewer (2.1). */
    public boolean isOxygenShort() {
        return getCrew() >= 2 && getOxygenFor() < getCrew();
    }

    public int getFuelCost() {
        return this.data.get(13);
    }

    /**
     * Display label for the selected docking target (kept for 2.0 callers): the same lang-keyed label the dock row
     * shows — the shared origin platform, New Station, a free dock, or the synced station name.
     */
    public String getStationName() {
        return za.co.neroland.nerospace.client.ClientStations.name(getStationSlot());
    }

    /** Fuel-intake slot: only rocket fuel buckets/canisters may be placed. */
    private static class FuelSlot extends Slot {
        FuelSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return RocketEntity.isFuelContainer(stack);
        }

        @Override
        public int getMaxStackSize() {
            return 16;
        }
    }
}
