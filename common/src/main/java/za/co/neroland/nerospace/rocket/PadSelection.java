package za.co.neroland.nerospace.rocket;

/**
 * Pure landing-pad selection rules for every destination but the Orbital Station ({@code Pad} on
 * {@link RocketEntity}): an index into {@link PadRegistry#inDimension} of the selected destination, or {@link #AUTO}
 * for the nearest pad. Shared by the rocket, both consoles and the tests.
 */
public final class PadSelection {

    /** "Nearest pad": the resolver picks. */
    public static final int AUTO = -1;
    /** Menu button id of "select pad {@code index}": {@code SELECT_BASE + index + 1} (so nearest is 300). */
    public static final int SELECT_BASE = 300;
    /** How many select ids exist: nearest plus every registry pad. */
    public static final int SELECT_SPAN = 1 + PadRegistry.MAX_PADS;
    /** Marker for "not a pad-select button". */
    public static final int NOT_A_PAD = Integer.MIN_VALUE;

    private PadSelection() {
    }

    /** The 2.0.0 cycler: nearest, each pad, nearest. */
    public static int next(int current, int count) {
        if (count <= 0) {
            return AUTO;
        }
        int next = current + 1;
        return next >= count ? AUTO : next;
    }

    /**
     * The server rule for an explicit pick of {@code index} among {@code count} pads in the destination:
     * launching beats everything; no destination is out of range; an index past the list names a pad that is gone.
     */
    public static DestinationSelection.Outcome evaluate(int index, int count, int current, boolean launching,
            boolean hasDestination) {
        if (launching) {
            return DestinationSelection.Outcome.LAUNCHING;
        }
        if (!hasDestination || index < AUTO || index >= PadRegistry.MAX_PADS) {
            return DestinationSelection.Outcome.OUT_OF_RANGE;
        }
        if (index >= count) {
            return DestinationSelection.Outcome.UNKNOWN_TARGET;
        }
        return index == current ? DestinationSelection.Outcome.ALREADY_SELECTED : DestinationSelection.Outcome.OK;
    }

    /** The button id that selects pad {@code index}. */
    public static int buttonFor(int index) {
        return SELECT_BASE + index + 1;
    }

    /** The pad index a select button id names, or {@link #NOT_A_PAD} for any other id (bounded). */
    public static int indexForButton(int id) {
        return id >= SELECT_BASE && id < SELECT_BASE + SELECT_SPAN ? id - SELECT_BASE - 1 : NOT_A_PAD;
    }

    /** {@code index} as a non-negative value for the optimistic highlight ({@code index + 1}). */
    public static int encode(int index) {
        return index + 1;
    }

    /** A distance for the list, rounded to 10 blocks so it says "how far", not "exactly where". */
    public static int roundedDistance(int blocks) {
        return blocks < 0 ? -1 : (blocks + 5) / 10 * 10;
    }
}
