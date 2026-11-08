package za.co.neroland.nerospace.rocket;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerospace.world.station.StationAccess;

/**
 * Pure docking-target rules for the Orbital Station destination ({@code StationSlot} on {@link RocketEntity}).
 * No world state: the rocket, both consoles and the tests share these so the server and the screens always agree
 * on which docks a viewer may pick, which one a fresh rocket starts on, and why an arrival fell through.
 *
 * <p><b>Default dock.</b> A rocket whose slot is still {@link #ORIGIN} and was never set by hand docks at the first
 * station the viewer <em>owns</em>, else the first they are a <em>member</em> of, else the shared origin platform.
 * It is applied on deploy, on a destination change to the station, and when either console opens; never on a tier
 * change and never at launch, so an explicit origin pick survives.</p>
 *
 * <p><b>Privacy.</b> Viewer UUIDs are compared in memory only; nothing here logs, stores or sends them.</p>
 */
public final class DockSelection {

    /** The shared origin platform at (0, 64, 0). Matches the {@code StationSlot} default on a fresh rocket. */
    public static final int ORIGIN = -1;
    /** "New Station": founds one on arrival with the rider's charter. */
    public static final int NEW_STATION = -2;
    /**
     * The landing flight's slot for a free dock (never stored in {@code StationSlot}, which stays {@link #ORIGIN}):
     * tells the landing adapter there is no station structure to ensure.
     */
    public static final int FREE_DOCK_SLOT = -3;

    /** Menu button id of "select dock {@code slot}": {@code SELECT_BASE + slot + 2} (so -2 is 200, -1 is 201). */
    public static final int SELECT_BASE = 200;
    /** How many select ids exist: New Station, origin and every registry slot. */
    public static final int SELECT_SPAN = 2 + StationRegistry.MAX_STATIONS;
    /** Marker for "not a dock-select button". */
    public static final int NOT_A_SLOT = Integer.MIN_VALUE;

    /**
     * Free docks ({@link FreeDocks}) live in their own value range so they never collide with a station slot:
     * value {@code FREE_VALUE_BASE + i} is the {@code i}-th free dock in the rocket's current list. The value is what
     * the menus sync as the dock selection and what the lists carry; the rocket itself saves the dock's pad-node id.
     */
    public static final int FREE_VALUE_BASE = 1000;
    /** Menu button id of "select free dock {@code i}": {@code 600 + i}, bounded by {@link FreeDocks#MAX_VISIBLE}. */
    public static final int FREE_SELECT_BASE = 600;

    /** How a list entry relates to the viewer (append-only: the ordinal travels in {@code StationSyncPayload}). */
    public enum Relation {
        /** Neither owner nor member: an open (or unowned) station. */
        PUBLIC,
        /** The viewer founded it. */
        YOURS,
        /** The viewer is on its member list. */
        MEMBER,
        /** The shared origin platform. */
        ORIGIN,
        /** "New Station" (needs a charter). */
        NEW_STATION,
        /** A hand-placed Docking Ring outside every claim box (2.1). */
        FREE;

        private static final Relation[] ALL = values();

        /** The relation for a synced ordinal; unknown ordinals read as {@link #PUBLIC}. */
        public static Relation byOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < ALL.length ? ALL[ordinal] : PUBLIC;
        }

        public String langKey() {
            return "gui.nerospace.picker.relation." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /**
     * Why a station arrival did not dock where the rocket was pointed. The key is the existing chat line prepended
     * to the arrival message. Append-only.
     */
    public enum FallThrough {
        NONE(null),
        /** The chosen station no longer exists (removed from the registry). */
        DOCK_GONE("entity.nerospace.rocket.pad_missing"),
        /** The station switched to MEMBERS docking and the pilot is not a member. */
        MEMBERS_ONLY("entity.nerospace.rocket.station_members_only"),
        /** "New Station" without a Station Charter. */
        NO_CHARTER("entity.nerospace.rocket.no_charter"),
        /** "New Station" with the registry full. */
        REGISTRY_FULL("item.nerospace.station_charter.full");

        @Nullable
        private final String langKey;

        FallThrough(@Nullable String langKey) {
            this.langKey = langKey;
        }

        @Nullable
        public String langKey() {
            return this.langKey;
        }
    }

    private DockSelection() {
    }

    /** {@code viewer}'s relation to {@code entry} (in-memory UUID compare only). */
    public static Relation relation(StationRegistry.StationEntry entry, @Nullable String viewerUuid) {
        if (viewerUuid != null && !entry.owner().isEmpty() && entry.owner().equals(viewerUuid)) {
            return Relation.YOURS;
        }
        if (viewerUuid != null && entry.members().contains(viewerUuid)) {
            return Relation.MEMBER;
        }
        return Relation.PUBLIC;
    }

    /** Whether a non-op {@code viewerUuid} may choose {@code entry} (MEMBERS docking policy). */
    public static boolean mayChoose(StationRegistry.StationEntry entry, @Nullable String viewerUuid) {
        return StationDockingRule.mayChoose(entry.docking(),
                StationAccess.canUse(entry.owner(), entry.members(), viewerUuid, false));
    }

    /**
     * The docking slot a rocket should hold for {@code viewerUuid} when nothing has been chosen yet: the first
     * station they own, else the first they are a member of, else {@link #ORIGIN}. A station the viewer may not
     * choose (MEMBERS policy) is skipped.
     *
     * @param stations   every founded station, in founding order ({@link StationRegistry#all()})
     * @param viewerUuid the deploying / viewing player's UUID string (compared in memory only, never logged)
     */
    public static int defaultSlot(List<StationRegistry.StationEntry> stations, @Nullable String viewerUuid) {
        if (viewerUuid == null) {
            return ORIGIN;
        }
        for (StationRegistry.StationEntry e : stations) {
            if (relation(e, viewerUuid) == Relation.YOURS && mayChoose(e, viewerUuid)) {
                return e.slot();
            }
        }
        for (StationRegistry.StationEntry e : stations) {
            if (relation(e, viewerUuid) == Relation.MEMBER && mayChoose(e, viewerUuid)) {
                return e.slot();
            }
        }
        return ORIGIN;
    }

    /**
     * Whether the default rule may move the selection: only while it is still the origin and was never picked by
     * hand. A tier change or a launch never calls this, so they never reset a choice.
     */
    public static boolean shouldApplyDefault(int currentSlot, boolean chosen) {
        return currentSlot == ORIGIN && !chosen;
    }

    /**
     * The pickable docking targets in cycle order: origin, every station {@code canDock} allows (founding order),
     * then New Station when {@code allowNew}.
     */
    public static List<Integer> choices(List<StationRegistry.StationEntry> stations,
            Predicate<StationRegistry.StationEntry> canDock, boolean allowNew) {
        List<Integer> out = new ArrayList<>();
        out.add(ORIGIN);
        for (StationRegistry.StationEntry e : stations) {
            if (canDock.test(e)) {
                out.add(e.slot());
            }
        }
        if (allowNew) {
            out.add(NEW_STATION);
        }
        return out;
    }

    /**
     * The cycler's next target after {@code current} (the 2.0.0 order: origin, each station, New Station, origin).
     * A selection no longer in the list moves to New Station when allowed, else the origin.
     */
    public static int next(List<Integer> choices, int current) {
        int at = choices.indexOf(current);
        if (at < 0) {
            return choices.contains(NEW_STATION) ? NEW_STATION : ORIGIN;
        }
        return choices.get((at + 1) % choices.size());
    }

    /**
     * The server rule for an explicit pick of {@code slot}: launching beats everything; ids outside
     * {@code [NEW_STATION, MAX_STATIONS)} are out of range; New Station needs a charter; a station must exist and
     * be choosable by the viewer.
     *
     * @param entry     the registry entry for {@code slot} ({@code null} when absent or not a station slot)
     * @param mayChoose whether the viewer passes the station's docking policy (ignored for origin / new)
     */
    public static DestinationSelection.Outcome evaluate(int slot, int current, boolean launching,
            @Nullable StationRegistry.StationEntry entry, boolean mayChoose, boolean hasCharter) {
        if (launching) {
            return DestinationSelection.Outcome.LAUNCHING;
        }
        if (slot < NEW_STATION || slot >= StationRegistry.MAX_STATIONS) {
            return DestinationSelection.Outcome.OUT_OF_RANGE;
        }
        if (slot == NEW_STATION && !hasCharter) {
            return DestinationSelection.Outcome.NO_CHARTER;
        }
        if (slot >= 0 && entry == null) {
            return DestinationSelection.Outcome.UNKNOWN_TARGET;
        }
        if (slot >= 0 && !mayChoose) {
            return DestinationSelection.Outcome.MEMBERS_ONLY;
        }
        return slot == current ? DestinationSelection.Outcome.ALREADY_SELECTED : DestinationSelection.Outcome.OK;
    }

    /** The button id that selects {@code value} (a station slot, or a free-dock value). */
    public static int buttonFor(int value) {
        if (isFree(value)) {
            return FREE_SELECT_BASE + freeIndex(value);
        }
        return SELECT_BASE + value + 2;
    }

    /**
     * The dock value a select button id names — a station slot for 200..265, a free-dock value for 600..615 — or
     * {@link #NOT_A_SLOT} for any other id (bounded).
     */
    public static int slotForButton(int id) {
        if (id >= FREE_SELECT_BASE && id < FREE_SELECT_BASE + FreeDocks.MAX_VISIBLE) {
            return freeValue(id - FREE_SELECT_BASE);
        }
        return id >= SELECT_BASE && id < SELECT_BASE + SELECT_SPAN ? id - SELECT_BASE - 2 : NOT_A_SLOT;
    }

    /** Whether a dock value names a free dock rather than a station slot. */
    public static boolean isFree(int value) {
        return value >= FREE_VALUE_BASE && value < FREE_VALUE_BASE + FreeDocks.MAX_VISIBLE;
    }

    /** The free-dock list index of a free value. */
    public static int freeIndex(int value) {
        return value - FREE_VALUE_BASE;
    }

    /** The dock value of the {@code index}-th free dock. */
    public static int freeValue(int index) {
        return FREE_VALUE_BASE + index;
    }

    /**
     * The server rule for picking free dock {@code index} out of the {@code visible} the rocket currently sees:
     * launching beats everything; an index the list no longer has is an unknown target.
     */
    public static DestinationSelection.Outcome evaluateFree(int index, int visible, int currentValue,
            boolean launching) {
        if (launching) {
            return DestinationSelection.Outcome.LAUNCHING;
        }
        if (index < 0 || index >= FreeDocks.MAX_VISIBLE) {
            return DestinationSelection.Outcome.OUT_OF_RANGE;
        }
        if (index >= visible) {
            return DestinationSelection.Outcome.UNKNOWN_TARGET;
        }
        return freeValue(index) == currentValue ? DestinationSelection.Outcome.ALREADY_SELECTED
                : DestinationSelection.Outcome.OK;
    }

    /** {@code slot} as a non-negative value for the optimistic highlight ({@code slot + 2}). */
    public static int encode(int slot) {
        return slot + 2;
    }

    /** Bounds a {@code StationSlot} read from a save: anything outside the id space reads as {@link #ORIGIN}. */
    public static int onLoad(int stored) {
        return stored < NEW_STATION || stored >= StationRegistry.MAX_STATIONS ? ORIGIN : stored;
    }

    /**
     * Why an arrival aimed at registry slot {@code >= 0} cannot dock there: the station is gone, or its docking
     * policy no longer admits the pilot. {@link FallThrough#NONE} when it can.
     */
    public static FallThrough arrivalFallThrough(@Nullable StationRegistry.StationEntry entry,
            boolean pilotMayChoose) {
        if (entry == null) {
            return FallThrough.DOCK_GONE;
        }
        return pilotMayChoose ? FallThrough.NONE : FallThrough.MEMBERS_ONLY;
    }

    /** The fall-through reason for a "New Station" founding outcome. */
    public static FallThrough foundingFallThrough(StationFounding.Outcome outcome) {
        return switch (outcome) {
            case REDIRECT -> FallThrough.NO_CHARTER;
            case REDIRECT_FULL -> FallThrough.REGISTRY_FULL;
            default -> FallThrough.NONE;
        };
    }
}
