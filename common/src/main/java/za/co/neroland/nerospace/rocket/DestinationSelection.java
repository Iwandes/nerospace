package za.co.neroland.nerospace.rocket;

import org.jetbrains.annotations.Nullable;

/**
 * Pure destination-selection rules shared by the rocket console, the Launch Controller and the server guard
 * Bit {@code i} of a mask stands for {@link Destinations} index {@code i}. No world state: the
 * screens predict with it (optimistic highlight) and the server decides with it, so both always agree on why a
 * card can or cannot be picked.
 */
public final class DestinationSelection {

    /** Highest index a 32-bit mask can describe. */
    private static final int MAX_INDEX = 30;

    /** The result of asking to select a destination; {@link #langKey()} names the player-facing reason. */
    public enum Outcome {
        /** Accepted and changed. */
        OK(null),
        /** Accepted; it was already the selection. */
        ALREADY_SELECTED(null),
        /** Not in this rocket's range from here (tier, or the world is absent on this server). */
        OUT_OF_RANGE("gui.nerospace.dest.reason.range"),
        /** The planet's Core gate is still closed for this player. */
        GATE_LOCKED("gui.nerospace.dest.reason.gate"),
        /** The world the rocket stands in, with no pad to hop to. */
        HERE("gui.nerospace.dest.reason.here"),
        /** An Observatory preview of a world a higher tier reaches. */
        PREVIEW("gui.nerospace.dest.reason.preview"),
        /** The rocket is already lifting off. */
        LAUNCHING("gui.nerospace.dest.reason.launching"),
        /** No rocket to change (Launch Controller with an empty pad, or a client-side call). */
        NO_ROCKET("gui.nerospace.dest.reason.no_rocket"),
        /** A dock pick: that station accepts only its members' rockets (MEMBERS docking policy). */
        MEMBERS_ONLY("gui.nerospace.dock.reason.members"),
        /** A dock pick: "New Station" needs a Station Charter in the inventory. */
        NO_CHARTER("gui.nerospace.dock.reason.charter"),
        /** A dock or pad pick that no longer exists (abandoned registry slot, removed pad). */
        UNKNOWN_TARGET("gui.nerospace.dock.reason.gone"),
        /** 2.1: someone else is the pilot (first aboard); only the pilot sets course, dock and pad. */
        NOT_PILOT("gui.nerospace.dest.reason.pilot");

        @Nullable
        private final String langKey;

        Outcome(@Nullable String langKey) {
            this.langKey = langKey;
        }

        /** Whether the selection stands after this outcome. */
        public boolean accepted() {
            return this == OK || this == ALREADY_SELECTED;
        }

        /** Translation key of the refusal reason, or {@code null} when accepted. */
        @Nullable
        public String langKey() {
            return this.langKey;
        }
    }

    /** "Never computed" marker for {@link #refreshDue}. */
    public static final long NEVER = Long.MIN_VALUE;

    /** Synced outcome code meaning "the server has not answered a pick on this menu yet". */
    public static final int NO_OUTCOME = 0;

    private static final Outcome[] OUTCOMES = Outcome.values();

    private DestinationSelection() {
    }

    /**
     * Whether a cached per-viewer value computed at game time {@code last} must be recomputed at {@code now}.
     * {@link #NEVER} is always due and never takes part in a subtraction ({@code now - Long.MIN_VALUE} overflows to
     * a negative number, which would read as "fresh" forever); a clock that went backwards is due too.
     */
    public static boolean refreshDue(long now, long last, long interval) {
        return last == NEVER || now < last || now - last >= interval;
    }

    /**
     * The Core-gate mask a viewer is held to: a creative player bypasses progression gates for destination
     * selection; everyone else (survival, adventure and spectators) gets {@code closed}, the gates still shut.
     */
    public static int viewerGateMask(boolean creative, int closed) {
        return creative ? 0 : closed;
    }

    /** Whether a viewer may fly through a closed gate: only a creative player bypasses it. */
    public static boolean gateOpenFor(boolean creative, boolean gateOpen) {
        return creative || gateOpen;
    }

    /**
     * Packs the server's answer to a pick into one synced menu value: a rolling 11-bit sequence (so two equal
     * answers in a row still change the value and are re-sent) and the outcome. Always non-zero and below 32767,
     * so it survives the 16-bit container-data sync.
     */
    public static int encodeOutcome(int sequence, Outcome outcome) {
        return ((sequence & 0x7FF) << 4) | (outcome.ordinal() + 1);
    }

    /**
     * Lang key naming why a pick did not stand: the outcome's own reason, "no other destination" for a step that
     * went nowhere, and the generic "kept its course" only for a pick the server never answered ({@code null}).
     */
    public static String refusalKey(@Nullable Outcome outcome) {
        if (outcome == null) {
            return "gui.nerospace.dest.reason.refused";
        }
        if (outcome == Outcome.ALREADY_SELECTED) {
            return "gui.nerospace.dest.reason.only_one";
        }
        String key = outcome.langKey();
        return key != null ? key : "gui.nerospace.dest.reason.refused";
    }

    /** The outcome packed by {@link #encodeOutcome}, or {@code null} for {@link #NO_OUTCOME} or a bad code. */
    @Nullable
    public static Outcome decodeOutcome(int code) {
        int ordinal = (code & 0xF) - 1;
        return code > 0 && ordinal >= 0 && ordinal < OUTCOMES.length ? OUTCOMES[ordinal] : null;
    }

    /**
     * The server rule: may {@code index} become the selection? Launching beats everything; then range, gate and
     * mask; selecting the current destination again is accepted as a no-op.
     */
    public static Outcome evaluate(int index, int mask, int gateMask, int selected, boolean launching) {
        if (launching) {
            return Outcome.LAUNCHING;
        }
        if (index < 0 || index > MAX_INDEX) {
            return Outcome.OUT_OF_RANGE;
        }
        int bit = 1 << index;
        if ((gateMask & bit) != 0) {
            return Outcome.GATE_LOCKED;
        }
        if ((mask & bit) == 0) {
            return Outcome.OUT_OF_RANGE;
        }
        return index == selected ? Outcome.ALREADY_SELECTED : Outcome.OK;
    }

    /**
     * The card rule used by the screens: an Observatory preview and the "you are here" card explain themselves
     * before the server rule runs.
     */
    public static Outcome evaluateCard(int index, int mask, int gateMask, int selected, boolean here,
            boolean preview) {
        if (preview) {
            return Outcome.PREVIEW;
        }
        if (here && (mask & (1 << index)) == 0) {
            return Outcome.HERE;
        }
        return evaluate(index, mask, gateMask, selected, false);
    }

    /** Whether {@code index} can be picked from {@code mask} by a viewer with {@code gateMask}. */
    public static boolean selectable(int index, int mask, int gateMask) {
        if (index < 0 || index > MAX_INDEX) {
            return false;
        }
        int bit = 1 << index;
        return (mask & bit) != 0 && (gateMask & bit) == 0;
    }

    /**
     * The next selectable destination after {@code current} in direction {@code dir} (+1 or −1) among
     * {@code count} destinations, wrapping and skipping gate-locked worlds; {@code current} when there is no other.
     */
    public static int step(int mask, int gateMask, int current, int dir, int count) {
        if (count <= 0) {
            return current;
        }
        int d = dir < 0 ? -1 : 1;
        for (int k = 1; k <= count; k++) {
            int next = Math.floorMod(current + d * k, count);
            if (next != current && selectable(next, mask, gateMask)) {
                return next;
            }
        }
        return current;
    }
}
