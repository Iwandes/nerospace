package za.co.neroland.nerospace.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerospace.NerospaceCommon;
import za.co.neroland.nerospace.api.Hazard;
import za.co.neroland.nerospace.api.PlanetTraits;
import za.co.neroland.nerospace.client.ui.DestinationList;
import za.co.neroland.nerospace.client.ui.LandingPicker;
import za.co.neroland.nerospace.client.ui.PlanetAccents;
import za.co.neroland.nerospace.client.ui.PlanetCard;
import za.co.neroland.nerospace.client.ui.PlanetCardData;
import za.co.neroland.nerospace.client.ui.PlanetCardFocus;
import za.co.neroland.nerospace.client.ui.PendingSelection;
import za.co.neroland.nerospace.rocket.Crew;
import za.co.neroland.nerospace.rocket.DestinationSelection;
import za.co.neroland.nerospace.rocket.DestinationTrace;
import za.co.neroland.nerospace.rocket.Destinations;
import za.co.neroland.nerospace.rocket.DockSelection;
import za.co.neroland.nerospace.rocket.PadSelection;
import za.co.neroland.nerospace.rocket.RocketEntity;
import za.co.neroland.nerospace.rocket.RocketMenu;
import za.co.neroland.nerospace.world.OxygenManager;
import za.co.neroland.nerospace.world.planet.PlanetDefinition;
import za.co.neroland.nerospace.world.planet.Planets;
import za.co.neroland.nerospace.world.planet.WeatherEvent;

/**
 * The in-rocket flight console ("list + card"): a vertical list of compact planet cards down the left
 * (three visible, scrolled by the mouse wheel or the two chevrons), the instrument well (fuel, oxygen, power,
 * status, trip cost) top-right, the selected destination's standard planet card under it, and the dock/pad
 * cycler and Launch button bottom-left. Hull 212 × 244 and every slot coordinate are unchanged.
 *
 * <p>Procedural (no PNG): the layout, fields and states here are final. The
 * player is NOT aboard when the console opens: Take seat boards them (as crew, or as the pilot of an empty hull)
 * and keeps the console open; pressing Launch on an empty rocket boards the presser as pilot and lifts off.</p>
 */
public class RocketScreen extends TexturedContainerScreen<RocketMenu> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(NerospaceCommon.MOD_ID, "textures/gui/rocket.png");
    private static final int ACCENT = 0xFFE0506A; // rocket red
    private static final int FUEL = 0xFFF0703C;   // fuel orange-red
    private static final int O2 = 0xFF3CC8E6;     // oxygen cyan
    private static final int POWER = 0xFFF5C542;  // power amber
    private static final int GOOD = 0xFF54D46A;   // ready green
    private static final int HULL = 0xFF0D1119;
    private static final int WELL = 0xFF0A1018;

    private static final int W = 212;
    /** Base hull height with the vanilla 3 main-inventory rows; grows 18px per extra modded row. */
    private static final int BASE_H = 244;

    // Destination list column (panel-relative): three compact cards at y 20 / 52 / 84.
    private static final int LIST_X = 8;
    private static final int LIST_Y = 20;
    private static final int VISIBLE = 3;
    private static final int CHEVRON_Y = 118;
    // Instrument well.
    private static final int RX = 76;
    private static final int GW = 92;
    // Selected destination's standard card.
    private static final int CARD_X = 108;
    private static final int CARD_Y = 106;
    // Stacked control rows (values kept from 1.x).
    private static final int DOCK_ROW_Y = 128;
    private static final int LAUNCH_ROW_Y = 144;
    private static final int ROW_W = 96;
    /** The Launch row is shared (2.1): Launch on the left, Take seat beside it. */
    private static final int LAUNCH_W = 40;
    private static final int SEAT_W = ROW_W - LAUNCH_W - 2;

    private SpaceButton launchButton;
    /** 2.1: the viewer takes a seat (crew, or pilot of an empty hull) without launching. */
    private SpaceButton seatButton;
    /** The seat state and crew count the seat button's label and tooltip were last made for. */
    @Nullable
    private Crew.SeatAnswer seatShown;
    private int seatCrewShown = -1;
    private SpaceButton stationButton;
    private SpaceButton padButton;
    /** The 2.0 cyclers, kept beside the rows (same ids, same behaviour). */
    private SpaceButton stationCycleButton;
    private SpaceButton padCycleButton;
    /** The "Dock at" / "Land at" list; {@link #pickerDock} says which one is open. */
    private final LandingPicker picker = new LandingPicker();
    private boolean pickerDock;
    /** Optimistic dock / pad picks ({@link DockSelection#encode} / {@link PadSelection#encode} values). */
    private final PendingSelection dockPending = new PendingSelection();
    private final PendingSelection padPending = new PendingSelection();
    private SpaceButton upButton;
    private SpaceButton downButton;
    /** One focusable, narrated stand-in per visible card row. */
    private final PlanetCardFocus[] rowFocus = new PlanetCardFocus[VISIBLE];

    /** Index of the first visible list entry. */
    private int scroll;
    /** The selection last scrolled into view (−1 = scroll on the next frame). */
    private int ensuredIndex = -1;
    /** The optimistic highlight: a click shows at once, the synced index settles it. */
    private final PendingSelection pending = new PendingSelection();
    /** A brief "can't select: …" notice and when it expires. */
    @Nullable
    private Component status;
    private long statusUntil;
    private int mouseX;
    private int mouseY;
    /** Destination indices shown in the list this frame, in {@link Destinations} order. */
    private final List<Integer> entries = new ArrayList<>();
    /** Locked destinations a station Observatory reveals this frame (shown as LOCKED_TIER cards). */
    private final List<Integer> revealed = new ArrayList<>();
    /** A revealed locked destination the player clicked to preview in the standard card, or −1. */
    private int previewIndex = -1;

    public RocketScreen(RocketMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, TEXTURE, ACCENT, W,
                BASE_H + (RocketMenu.mainRows(playerInventory) - 3) * 18);
        this.titleLabelY = -100;        // header drawn manually in extractForeground
        this.inventoryLabelY = 10_000;  // hide the redundant inventory label
    }

    @Override
    protected void init() {
        super.init();
        this.upButton = new SpaceButton(this.leftPos + LIST_X, this.topPos + CHEVRON_Y, 28, 8,
                Component.literal("▲"), ACCENT, b -> scrollBy(-1));
        this.addRenderableWidget(this.upButton);
        this.downButton = new SpaceButton(this.leftPos + LIST_X + 32, this.topPos + CHEVRON_Y, 28, 8,
                Component.literal("▼"), ACCENT, b -> scrollBy(1));
        this.addRenderableWidget(this.downButton);
        for (int row = 0; row < VISIBLE; row++) {
            final int r = row;
            this.rowFocus[row] = new PlanetCardFocus(this.leftPos + LIST_X, this.topPos + LIST_Y + row * PlanetCard.COMPACT_H,
                    b -> {
                        int index = DestinationList.indexAt(this.entries, this.scroll, r, VISIBLE);
                        if (index >= 0) {
                            clickCard(index);
                        }
                    });
            this.addRenderableWidget(this.rowFocus[row]);
        }

        // "Dock at" (Orbital Station) and "Land at" (every other destination) share one row: the labelled row opens
        // the list, the small » beside it is the 2.0 cycler (same button ids, same behaviour).
        this.stationButton = new SpaceButton(this.leftPos + 8, this.topPos + DOCK_ROW_Y, ROW_W - 14, 12,
                Component.empty(), ACCENT, b -> openPicker(true));
        this.addRenderableWidget(this.stationButton);
        this.padButton = new SpaceButton(this.leftPos + 8, this.topPos + DOCK_ROW_Y, ROW_W - 14, 12,
                Component.empty(), O2, b -> openPicker(false));
        this.addRenderableWidget(this.padButton);
        this.stationCycleButton = new SpaceButton(this.leftPos + 8 + ROW_W - 12, this.topPos + DOCK_ROW_Y, 12, 12,
                Component.literal("»"), ACCENT, b -> sendButton(RocketMenu.BUTTON_CYCLE_STATION));
        this.addRenderableWidget(this.stationCycleButton);
        this.padCycleButton = new SpaceButton(this.leftPos + 8 + ROW_W - 12, this.topPos + DOCK_ROW_Y, 12, 12,
                Component.literal("»"), O2, b -> sendButton(RocketMenu.BUTTON_CYCLE_PAD));
        this.addRenderableWidget(this.padCycleButton);
        this.padCycleButton.setTooltip(Tooltip.create(Component.translatable("gui.nerospace.picker.cycle")));
        // Client hint only: "New Station" is skipped without a charter; the server re-checks.
        this.stationCycleButton.setTooltip(Tooltip.create(hasCharter()
                ? Component.translatable("gui.nerospace.picker.cycle")
                : Component.translatable("item.nerospace.station_charter.needed")));
        this.picker.close();

        this.launchButton = new SpaceButton(this.leftPos + 8, this.topPos + LAUNCH_ROW_Y, LAUNCH_W, 16,
                Component.translatable("gui.nerospace.rocket.launch"), ACCENT, b -> sendButton(RocketMenu.BUTTON_LAUNCH));
        this.addRenderableWidget(this.launchButton);
        this.seatButton = new SpaceButton(this.leftPos + 8 + LAUNCH_W + 2, this.topPos + LAUNCH_ROW_Y, SEAT_W, 16,
                Component.translatable("gui.nerospace.rocket.take_seat"), O2,
                b -> sendButton(RocketMenu.BUTTON_TAKE_SEAT));
        this.addRenderableWidget(this.seatButton);
        this.seatShown = null;
        this.seatCrewShown = -1;
        this.ensuredIndex = -1;
    }

    // --- Procedural hull -----------------------------------------------------

    @Override
    protected void drawPanel(GuiGraphicsExtractor g) {
        int l = this.leftPos;
        int t = this.topPos;
        int h = this.imageHeight; // grows with extra modded inventory rows
        g.fill(l - 2, t - 2, l + W + 2, t + h + 2, INK);     // outer rim
        g.fill(l, t, l + W, t + h, HULL);                    // hull body
        g.fill(l, t, l + W, t + 16, 0xFF14283C);             // header band
        g.fill(l, t + 16, l + W, t + 17, ACCENT);            // accent rule under the header
        well(g, l + LIST_X, t + LIST_Y, 64, 96);             // destination list column
        well(g, l + RX, t + 20, 96, 84);                     // instrument well
        for (int i = 0; i < this.menu.slots.size(); i++) {
            Slot slot = this.menu.slots.get(i);
            if (i == 0) {
                fuelSlotWell(g, slot.x, slot.y);
            } else {
                slotWell(g, slot.x, slot.y);
            }
        }
        rivet(g, l + 3, t + 3);
        rivet(g, l + W - 5, t + 3);
        rivet(g, l + 3, t + h - 5);
        rivet(g, l + W - 5, t + h - 5);
    }

    private void well(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, INK);
        g.fill(x, y, x + w, y + h, WELL);
    }

    private void rivet(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x, y, x + 2, y + 2, 0xFF35506A);
    }

    // --- Live readouts -------------------------------------------------------

    @Override
    public void extractContents(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        super.extractContents(extractor, mouseX, mouseY, partialTick);
    }

    @Override
    protected void extractForeground(GuiGraphicsExtractor g) {
        long now = System.currentTimeMillis();
        label(g, Component.translatable("gui.nerospace.rocket.title", this.menu.getTier().level()), 10, 4, TITLE);
        // 2.1 crew: players aboard of the seats, and how many seats the onboard oxygen covers for this flight.
        Component crew = SpaceButton.fit(this.font, Component.translatable("gui.nerospace.rocket.crew_line",
                this.menu.getCrew(), this.menu.getMaxCrew(), this.menu.getOxygenFor()), 96);
        label(g, crew, W - 8 - this.font.width(crew), 4, this.menu.isOxygenShort() ? 0xFFF0A83C : 0xFFBFEFFF);

        // Instrument well: three compact gauges, status and trip cost.
        int fpct = this.menu.getFuelPercent();
        label(g, Component.translatable("gui.nerospace.rocket.fuel_line", fpct), RX + 2, 22, 0xFFFFC9B0);
        fluidGauge(g, RX + 2, 31, GW, 6, fpct / 100f, FUEL);
        int opct = this.menu.getOxygenPercent();
        label(g, Component.translatable("gui.nerospace.rocket.oxygen_line", opct), RX + 2, 44, 0xFFBFEFFF);
        fluidGauge(g, RX + 2, 53, GW, 6, opct / 100f, O2);
        int ppct = this.menu.getPowerPercent();
        label(g, Component.translatable("gui.nerospace.rocket.power_line", ppct), RX + 2, 66, 0xFFF6DC8A);
        segGauge(g, RX + 2, 75, GW, 6, ppct / 100f, POWER);

        boolean padValid = this.menu.isPadValid();
        boolean ready = this.menu.isLaunchable();
        String statusKey;
        int statusColor;
        if (!padValid) {
            statusKey = "gui.nerospace.rocket.pad_blocked";
            statusColor = ACCENT;
        } else if (!ready && this.menu.isOxygenShort()) {
            statusKey = "gui.nerospace.rocket.needs_oxygen"; // 2.1: the shared reserve does not cover every seat
            statusColor = 0xFFF0A83C;
        } else if (!ready) {
            statusKey = "gui.nerospace.rocket.needs_fuel";
            statusColor = 0xFFF0A83C;
        } else {
            statusKey = "gui.nerospace.rocket.pad_ready";
            statusColor = GOOD;
        }
        label(g, Component.translatable(statusKey), RX + 2, 86, statusColor);
        label(g, Component.translatable("gui.nerospace.rocket.cost", this.menu.getFuelCost()), RX + 2, 95, 0xFFCFE7FF);

        // Destination list: reachable destinations in Destinations order, plus the "you are here" node.
        rebuildEntries();
        int synced = this.menu.getDestinationIndex();
        if (this.pending.reconcile(synced, this.menu.getLastOutcomeCode(), now) == PendingSelection.Result.REFUSED) {
            showStatus(refusalReason(this.pending.refusal(), synced), now);
        }
        int selected = this.pending.shown(synced);
        if (selected != this.ensuredIndex && this.entries.contains(selected)) {
            // Keep the selection in view (on open, and after every pick by click, key or server).
            this.scroll = DestinationList.scrollToShow(this.entries, this.scroll, selected, VISIBLE);
            this.ensuredIndex = selected;
        }
        this.scroll = DestinationList.clampScroll(this.scroll, this.entries.size(), VISIBLE);
        PlanetCardData hovered = null;
        for (int row = 0; row < VISIBLE; row++) {
            int index = DestinationList.indexAt(this.entries, this.scroll, row, VISIBLE);
            PlanetCardFocus focus = this.rowFocus[row];
            if (index < 0) {
                if (focus != null) {
                    focus.show(null);
                }
                continue;
            }
            int x = this.leftPos + LIST_X;
            int y = this.topPos + LIST_Y + row * PlanetCard.COMPACT_H;
            PlanetCardData data = cardData(index);
            boolean hover = PlanetCard.hit(x, y, true, this.mouseX, this.mouseY);
            PlanetCard.draw(g, this.font, x, y, data, true, hover, now);
            if (focus != null) {
                focus.show(data);
            }
            if (hover) {
                hovered = data;
            }
        }
        boolean scrollable = this.entries.size() > VISIBLE;
        if (this.upButton != null && this.downButton != null) {
            this.upButton.visible = scrollable;
            this.downButton.visible = scrollable;
            this.upButton.active = scrollable && this.scroll > 0;
            this.downButton.active = scrollable && this.scroll + VISIBLE < this.entries.size();
        }

        // The selected destination's standard card (or a clicked Observatory preview).
        ResourceKey<Level> selectedKey = Destinations.byIndex(selected);
        if (this.previewIndex >= 0 && this.revealed.contains(this.previewIndex)) {
            PlanetCard.draw(g, this.font, this.leftPos + CARD_X, this.topPos + CARD_Y, cardData(this.previewIndex),
                    false, false, now);
        } else if (selectedKey != null && (this.menu.getDestinationMask() & (1 << selected)) != 0) {
            PlanetCard.draw(g, this.font, this.leftPos + CARD_X, this.topPos + CARD_Y, cardData(selected), false,
                    false, now);
        }

        // "Dock at" / "Land at" rows (reconciled with the server like the destination list).
        reconcilePicks(now);
        boolean stationDest = this.menu.isStationDestination();
        boolean padDest = this.menu.isPadDestination();
        boolean listOpen = this.picker.isOpen();
        if (this.stationButton != null && this.stationCycleButton != null) {
            this.stationButton.visible = stationDest && !listOpen;
            this.stationButton.active = stationDest;
            this.stationCycleButton.visible = stationDest && !listOpen;
            this.stationCycleButton.active = stationDest;
            if (stationDest) {
                this.stationButton.setMessage(Component.translatable("gui.nerospace.rocket.dock_at",
                        ClientStations.label(shownDock())));
            }
        }
        if (this.padButton != null && this.padCycleButton != null) {
            this.padButton.visible = padDest && !listOpen;
            this.padButton.active = padDest;
            this.padCycleButton.visible = padDest && !listOpen;
            this.padCycleButton.active = padDest;
            if (padDest) {
                this.padButton.setMessage(Component.translatable("gui.nerospace.rocket.land_at",
                        LandingLists.padLabel(selectedKey, shownPad())));
            }
        }
        if (listOpen && !(this.pickerDock ? stationDest : padDest)) {
            this.picker.close(); // the destination changed under the list
            listOpen = false;
        }

        if (this.launchButton != null) {
            this.launchButton.visible = !listOpen;
            this.launchButton.active = ready;
            this.launchButton.setSelected(ready && ((now / 500L) & 1L) == 0L);
        }
        if (this.seatButton != null) {
            this.seatButton.visible = !listOpen;
            updateSeatButton();
        }
        if (listOpen) {
            // The list covers the cards and controls: nothing under it may take a click or the keyboard focus.
            for (PlanetCardFocus focus : this.rowFocus) {
                if (focus != null) {
                    focus.visible = false;
                    focus.active = false;
                }
            }
            if (this.upButton != null && this.downButton != null) {
                this.upButton.visible = false;
                this.downButton.visible = false;
            }
            this.picker.setEntries(this.pickerDock ? LandingLists.docks(hasCharter())
                    : LandingLists.pads(Destinations.byIndex(shownSelection())));
            this.picker.draw(g, this.font, this.mouseX, this.mouseY, this.pickerDock ? shownDock() : shownPad(), ACCENT);
            hovered = null;
        }
        if (this.status != null && now < this.statusUntil) {
            drawNotice(g, this.status);
        }
        if (hovered != null) {
            g.setComponentTooltipForNextFrame(this.font, PlanetCard.tooltip(hovered, true), this.mouseX, this.mouseY);
        }
    }

    // --- Dock / pad lists ----------------------------------------------------

    private boolean hasCharter() {
        return this.minecraft != null && this.minecraft.player != null && RocketEntity.hasCharter(this.minecraft.player);
    }

    /** The docking slot to show: the optimistic pick while pending, else the synced slot. */
    private int shownDock() {
        return this.dockPending.shown(DockSelection.encode(this.menu.getStationSlot())) - 2;
    }

    /** The pad index to show: the optimistic pick while pending, else the synced index. */
    private int shownPad() {
        return this.padPending.shown(PadSelection.encode(this.menu.getPadIndex())) - 1;
    }

    /** Settles the optimistic dock / pad picks; a refusal shows the amber banner with the server's reason. */
    private void reconcilePicks(long now) {
        int code = this.menu.getLastPickOutcomeCode();
        if (this.dockPending.reconcile(DockSelection.encode(this.menu.getStationSlot()), code, now)
                == PendingSelection.Result.REFUSED) {
            showStatus(Component.translatable(DestinationSelection.refusalKey(this.dockPending.refusal())), now);
        }
        if (this.padPending.reconcile(PadSelection.encode(this.menu.getPadIndex()), code, now)
                == PendingSelection.Result.REFUSED) {
            showStatus(Component.translatable(DestinationSelection.refusalKey(this.padPending.refusal())), now);
        }
    }

    private void openPicker(boolean dock) {
        this.pickerDock = dock;
        playClick();
        int rows = 9;
        this.picker.open(Component.translatable(dock ? "gui.nerospace.picker.dock_title" : "gui.nerospace.picker.land_title"),
                dock ? LandingLists.docks(hasCharter()) : LandingLists.pads(Destinations.byIndex(shownSelection())),
                dock ? shownDock() : shownPad(), this.leftPos + LIST_X, this.topPos + LIST_Y, 170, rows);
    }

    /** Picks a row: highlighted at once, sent to the server, and reconciled with the synced value. */
    private void pick(LandingPicker.Entry entry) {
        long now = System.currentTimeMillis();
        this.picker.close();
        playClick();
        int code = this.menu.getLastPickOutcomeCode();
        if (this.pickerDock) {
            if (entry.value() == DockSelection.NEW_STATION && !hasCharter()) {
                showStatus(Component.translatable(DestinationSelection.refusalKey(
                        DestinationSelection.Outcome.NO_CHARTER)), now);
                return;
            }
            this.dockPending.begin(DockSelection.encode(entry.value()), DockSelection.encode(this.menu.getStationSlot()),
                    code, now);
            sendButton(DockSelection.buttonFor(entry.value()));
        } else {
            this.padPending.begin(PadSelection.encode(entry.value()), PadSelection.encode(this.menu.getPadIndex()),
                    code, now);
            sendButton(PadSelection.buttonFor(entry.value()));
        }
        this.status = null;
    }

    private void showStatus(Component reason, long now) {
        this.status = Component.translatable("gui.nerospace.dest.cant_select", reason);
        this.statusUntil = now + 2500L;
    }

    /** The player-facing reason for a refused pick of {@code index}. */
    private Component reasonFor(DestinationSelection.Outcome outcome, int index) {
        if (outcome == DestinationSelection.Outcome.PREVIEW) {
            PlanetDefinition planet = Planets.get(Destinations.byIndex(index));
            return Component.translatable("gui.nerospace.card.tier_tooltip", planet != null ? planet.unlockTier() : 1);
        }
        return Component.translatable(DestinationSelection.refusalKey(outcome));
    }

    /** The server's reason for a refused pick; the generic "kept its course" only when the server never answered. */
    private Component refusalReason(@Nullable DestinationSelection.Outcome outcome, int index) {
        return outcome == null ? Component.translatable(DestinationSelection.refusalKey(null))
                : reasonFor(outcome, index);
    }

    /** The displayed selection: the optimistic pick while it is pending, else the synced index. */
    private int shownSelection() {
        return this.pending.shown(this.menu.getDestinationIndex());
    }

    private void rebuildEntries() {
        int mask = this.menu.getDestinationMask();
        int here = hereIndex();
        this.revealed.clear();
        this.revealed.addAll(revealedLocked(mask, here, this.menu.getTier().level(), this.menu.getRevealCount()));
        this.entries.clear();
        this.entries.addAll(DestinationList.entries(mask, here, Destinations.all().size(), this.revealed));
    }

    /**
     * The next {@code count} locked destinations in tier order (unlock tier, then destination index): planets this
     * rocket's tier cannot reach yet, never the current world. A gate-locked world (Nyxara) is included and stays
     * unselectable. Planet data only.
     */
    static List<Integer> revealedLocked(int mask, int here, int rocketTier, int count) {
        List<Integer> out = new ArrayList<>();
        if (count <= 0) {
            return out;
        }
        List<int[]> locked = new ArrayList<>(); // {unlockTier, index}
        List<ResourceKey<Level>> all = Destinations.all();
        for (int i = 0; i < all.size(); i++) {
            PlanetDefinition planet = Planets.get(all.get(i));
            if ((mask & (1 << i)) == 0 && i != here && planet != null && planet.unlockTier() > rocketTier) {
                locked.add(new int[] { planet.unlockTier(), i });
            }
        }
        locked.sort((a, b) -> a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(a[1], b[1]));
        for (int k = 0; k < Math.min(count, locked.size()); k++) {
            out.add(locked.get(k)[1]);
        }
        return out;
    }

    private int hereIndex() {
        return this.minecraft != null && this.minecraft.level != null
                ? Destinations.indexOf(this.minecraft.level.dimension()) : -1;
    }

    /** Fills a card from client state only: planet traits, the synced clock/weather, the pad name. */
    private PlanetCardData cardData(int index) {
        ResourceKey<Level> key = Destinations.byIndex(index);
        if (key == null) {
            key = Level.OVERWORLD;
        }
        PlanetDefinition planet = Planets.get(key);
        PlanetTraits traits = planet != null ? planet.traits() : null;
        Level level = this.minecraft != null ? this.minecraft.level : null;
        boolean current = level != null && level.dimension().equals(key);
        int mask = this.menu.getDestinationMask();
        boolean reachable = (mask & (1 << index)) != 0;
        boolean here = current && !reachable;
        boolean gate = (this.menu.getGateMask() & (1 << index)) != 0;
        boolean tierLocked = this.revealed.contains(index); // an Observatory preview: full LOCKED_TIER card
        PlanetCardData.State state = PlanetCardData.stateFor(here, gate && !tierLocked, tierLocked,
                index == shownSelection());
        double gravity = traits != null ? traits.defaultGravity() : 1.0D;
        Hazard hazard = traits != null ? traits.hazard() : Hazard.NONE;
        boolean rad = traits != null && planet != null
                && PlanetCardData.needsRadLining(traits.radiation(), hazard, planet.weather().event());
        boolean fixed = traits != null && traits.fixedClock();
        boolean hasDay = traits == null || !fixed;
        float phase = current ? (float) ClientPlanetClock.phase(level)
                : (planet != null && fixed ? (float) planet.pinnedPhase() : 0.5F);
        WeatherEvent weather = current ? ClientPlanetWeather.event(level) : WeatherEvent.NONE;
        float intensity = current ? ClientPlanetWeather.intensity(level) : 0.0F;
        OxygenManager.SuitTier suit = traits == null ? OxygenManager.SuitTier.NONE
                : PlanetCardData.requiredSuit(traits.airless(), hazard, traits.temperatureBand());
        return new PlanetCardData(key, Destinations.displayName(key), gravity, hazard, suit, rad, weather, intensity,
                phase, fixed, hasDay, chosenPad(key), state, planet != null ? planet.unlockTier() : 1);
    }

    private Optional<Component> chosenPad(ResourceKey<Level> key) {
        if (key.equals(za.co.neroland.nerospace.registry.ModDimensions.STATION_LEVEL)) {
            return Optional.of(Component.translatable("gui.nerospace.card.dock", ClientStations.label(shownDock())));
        }
        String dimId = key.identifier().toString();
        if (ClientPads.count(dimId) == 0) {
            return Optional.empty(); // no pad there: a Landing Pod will be placed
        }
        return Optional.of(Component.translatable("gui.nerospace.card.pad", padLabel(key)));
    }

    private String padLabel(@Nullable ResourceKey<Level> key) {
        int idx = shownPad();
        if (idx < 0 || key == null) {
            return Component.translatable("gui.nerospace.card.pad_auto").getString();
        }
        String name = ClientPads.name(key.identifier().toString(), idx);
        return name != null ? name : this.menu.getPadName();
    }

    // --- Input ---------------------------------------------------------------

    //? if >=1.21.9 {
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
    //?} else {
    /*@Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        MouseButtonEvent event = new MouseButtonEvent(mouseX, mouseY, button);
    *///?}
        // Never compare against a literal button number: 26.3 moved input to SDL, where the left button is 1
        // (GLFW, up to 26.2, used 0). InputConstants carries the right value for each version.
        if (this.picker.isOpen()) {
            // The open list owns the mouse: a row picks, anywhere else closes it.
            LandingPicker.Entry row = event.button() == InputConstants.MOUSE_BUTTON_LEFT
                    ? this.picker.at(event.x(), event.y()) : null;
            if (row != null) {
                pick(row);
            } else if (!this.picker.contains(event.x(), event.y())) {
                this.picker.close();
            }
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            for (int row = 0; row < VISIBLE; row++) {
                int index = DestinationList.indexAt(this.entries, this.scroll, row, VISIBLE);
                if (index < 0) {
                    break;
                }
                int x = this.leftPos + LIST_X;
                int y = this.topPos + LIST_Y + row * PlanetCard.COMPACT_H;
                if (PlanetCard.hit(x, y, true, event.x(), event.y())) {
                    clickCard(index);
                    return true;
                }
            }
        }
        //? if >=1.21.9 {
        return super.mouseClicked(event, doubleClick);
        //?} else {
        /*return super.mouseClicked(event.x(), event.y(), event.button());
        *///?}
    }

    private void clickCard(int index) {
        if (this.revealed.contains(index)) {
            // An Observatory preview: toggles the standard card, never selectable — say why.
            this.previewIndex = this.previewIndex == index ? -1 : index;
            playClick();
            showStatus(reasonFor(DestinationSelection.Outcome.PREVIEW, index), System.currentTimeMillis());
            return;
        }
        this.previewIndex = -1;
        select(index);
    }

    /** Picks {@code index}: highlighted at once, sent to the server, and reconciled with the synced value. */
    private void select(int index) {
        long now = System.currentTimeMillis();
        int synced = this.menu.getDestinationIndex();
        int mask = this.menu.getDestinationMask();
        int gate = this.menu.getGateMask();
        DestinationSelection.Outcome outcome = DestinationSelection.evaluateCard(index, mask, gate,
                this.pending.shown(synced), index == hereIndex(), false);
        DestinationTrace.log("rocket screen {} click index {} mask {} gate {} -> {}", this.menu.containerId, index,
                mask, gate, outcome);
        if (outcome == DestinationSelection.Outcome.OK) {
            this.pending.begin(index, synced, this.menu.getLastOutcomeCode(), now);
            this.status = null;
            playClick();
            sendButton(RocketMenu.SELECT_DEST_BASE + index);
        } else if (!outcome.accepted()) {
            showStatus(reasonFor(outcome, index), now);
        }
    }

    /** Up/Down move the selection through the selectable cards; Enter launches when ready. */
    //? if >=1.21.9 {
    @Override
    public boolean keyPressed(KeyEvent event) {
    //?} else {
    /*@Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        KeyEvent event = new KeyEvent(keyCode, scanCode, modifiers);
    *///?}
        if (this.picker.isOpen()) {
            // The open list owns the keyboard: Up/Down move, Enter picks, Escape closes the list (not the screen).
            if (event.isUp() || event.isDown()) {
                this.picker.moveFocus(event.isUp() ? -1 : 1);
                return true;
            }
            if (event.isConfirmation()) {
                LandingPicker.Entry row = this.picker.focused();
                if (row != null) {
                    pick(row);
                }
                return true;
            }
            if (event.isEscape()) {
                this.picker.close();
                return true;
            }
        }
        if (event.isUp() || event.isDown()) {
            int current = shownSelection();
            int next = nextSelectable(current, event.isUp() ? -1 : 1);
            if (next >= 0 && next != current) {
                this.previewIndex = -1;
                select(next);
            }
            return true;
        }
        if (event.isConfirmation() && this.launchButton != null && this.launchButton.active
                && !this.pending.active()) {
            playClick();
            sendButton(RocketMenu.BUTTON_LAUNCH);
            return true;
        }
        //? if >=1.21.9 {
        return super.keyPressed(event);
        //?} else {
        /*return super.keyPressed(event.key(), event.scancode(), event.modifiers());
        *///?}
    }

    /** The next selectable list entry after {@code current} in direction {@code dir}, or −1 (no wrap). */
    private int nextSelectable(int current, int dir) {
        int mask = this.menu.getDestinationMask();
        int gate = this.menu.getGateMask();
        int at = this.entries.indexOf(current);
        int i = at < 0 ? (dir > 0 ? -1 : this.entries.size()) : at;
        for (i += dir; i >= 0 && i < this.entries.size(); i += dir) {
            int index = this.entries.get(i);
            if (!this.revealed.contains(index) && DestinationSelection.selectable(index, mask, gate)) {
                return index;
            }
        }
        return -1;
    }

    /**
     * The Take seat button follows the server's per-viewer answer: pressable when a seat is free, "Seated" once
     * aboard, "Full n/n" when every seat is taken, and greyed out with the reason while the hull is sealed or out of
     * reach. The label and tooltip are rebuilt only when the state or crew count changes.
     */
    private void updateSeatButton() {
        Crew.SeatAnswer answer = this.menu.getSeatAnswer();
        int crew = this.menu.getCrew();
        if (answer == this.seatShown && crew == this.seatCrewShown) {
            return;
        }
        this.seatShown = answer;
        this.seatCrewShown = crew;
        int seats = this.menu.getMaxCrew();
        Component label = Component.translatable("gui.nerospace.rocket.take_seat");
        Component tip;
        boolean active = false;
        switch (answer) {
            case ALREADY_ABOARD -> {
                label = Component.translatable("gui.nerospace.rocket.seated");
                tip = Component.translatable("gui.nerospace.rocket.seated.tooltip", crew, seats);
            }
            case FULL -> {
                label = Component.translatable("gui.nerospace.rocket.seats_full", crew, seats);
                tip = Component.translatable("gui.nerospace.rocket.seats_full.tooltip");
            }
            case IN_FLIGHT -> tip = Component.translatable("gui.nerospace.rocket.seat_in_flight");
            case OUT_OF_REACH -> tip = Component.translatable("gui.nerospace.rocket.seat_reach");
            case UNCREWED -> tip = Component.translatable("gui.nerospace.rocket.seat_uncrewed");
            default -> { // OK; the server re-checks every press (and the viewer and sneak) anyway
                active = true;
                tip = crew == 0 ? Component.translatable("gui.nerospace.rocket.take_seat.pilot")
                        : Component.translatable("gui.nerospace.rocket.take_seat.crew", crew, seats);
            }
        }
        this.seatButton.active = active;
        this.seatButton.setSelected(answer == Crew.SeatAnswer.ALREADY_ABOARD);
        this.seatButton.setMessage(label);
        this.seatButton.setTooltip(Tooltip.create(tip));
    }

    private void playClick() {
        if (this.minecraft != null) {
            AbstractWidget.playButtonClickSound(this.minecraft.getSoundManager());
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.picker.isOpen()) {
            if (scrollY != 0.0D && this.picker.contains(mouseX, mouseY)) {
                this.picker.scrollBy(scrollY > 0.0D ? -1 : 1);
            }
            return true;
        }
        int x = this.leftPos + LIST_X;
        int y = this.topPos + LIST_Y;
        if (mouseX >= x && mouseX < x + PlanetCard.COMPACT_W && mouseY >= y
                && mouseY < y + VISIBLE * PlanetCard.COMPACT_H && scrollY != 0.0D) {
            scrollBy(scrollY > 0.0D ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void scrollBy(int delta) {
        this.scroll = Math.max(0, Math.min(this.scroll + delta, Math.max(0, this.entries.size() - VISIBLE)));
    }

    // --- Slot wells ----------------------------------------------------------

    private void slotWell(GuiGraphicsExtractor g, int dx, int dy) {
        int x = this.leftPos + dx;
        int y = this.topPos + dy;
        g.fill(x - 1, y - 1, x + 17, y + 17, INK);
        g.fill(x, y, x + 16, y + 16, WELL);
    }

    /** The fuel-intake recess: framed in fuel orange; pulses with a small arrow while empty. */
    private void fuelSlotWell(GuiGraphicsExtractor g, int dx, int dy) {
        int x = this.leftPos + dx;
        int y = this.topPos + dy;
        boolean empty = !this.menu.slots.get(0).hasItem();
        boolean lit = !empty || ((System.currentTimeMillis() / 500L) & 1L) == 0L;
        int rim = lit ? FUEL : dim(FUEL, 0.45F);
        g.fill(x - 2, y - 2, x + 18, y + 18, rim);
        g.fill(x - 1, y - 1, x + 17, y + 17, INK);
        g.fill(x, y, x + 16, y + 16, 0xFF1A1208);
        if (empty) {
            int ax = x + 8;
            g.fill(ax - 3, y - 7, ax + 3, y - 5, rim);
            g.fill(ax - 2, y - 5, ax + 2, y - 3, rim);
            g.fill(ax - 1, y - 3, ax + 1, y - 2, rim);
        }
    }

    private static int dim(int argb, float f) {
        int a = (argb >>> 24) & 0xFF;
        int r = (int) (((argb >> 16) & 0xFF) * f);
        int gg = (int) (((argb >> 8) & 0xFF) * f);
        int b = (int) ((argb & 0xFF) * f);
        return (a << 24) | (r << 16) | (gg << 8) | b;
    }

    /** Accent of the selected destination (kept for callers that tint by planet). */
    public static int accentFor(@Nullable ResourceKey<Level> key) {
        return PlanetAccents.of(key);
    }

    private void sendButton(int id) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
        }
    }
}
