package za.co.neroland.nerospace.rocket;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.core.BlockPos;

import org.jetbrains.annotations.Nullable;

/**
 * Pure rules for <b>free docks</b>: Docking Rings placed by hand in the station dimension outside every station's
 * claim box (and outside the origin platform's). They are offered in the "Dock at" list of a rocket whose last
 * station position (or the origin, before its first station flight) is within {@code stations.freeDockRadius}
 * blocks, nearest first, at most {@link #MAX_VISIBLE}. Never a server-wide list.
 *
 * <p>Discovery reads only {@link PadRegistry} and {@link StationRegistry} (both in memory) and loads no chunk; the
 * ring block itself is checked by the landing resolver at arrival, which reads that one chunk.</p>
 *
 * <p><b>Privacy.</b> A free dock has no owner: the ring records no placer (Docking Rings never did), so there is no
 * second ownership model. A rocket remembers its pick by the dock's pad-node id only.</p>
 */
public final class FreeDocks {

    /** Most free docks one list shows (bounds the select ids: {@link DockSelection#FREE_SELECT_BASE} + 0..15). */
    public static final int MAX_VISIBLE = 16;
    /** The default visibility radius (blocks, horizontal) around the rocket's last station position. */
    public static final int DEFAULT_RADIUS = 2048;

    /**
     * One visible free dock. {@code named} is false for a ring nobody named (anvil or name tag), which the screens show
     * as "Free dock N (D m)" (no position is sent to the client; the arrival line names "Dock @ x,z" to the crew who
     * have just landed there). {@code distance} is horizontal blocks from the anchor, rounded to 10.
     */
    public record Dock(int padId, String name, boolean named, BlockPos centre, int distance) {
    }

    private FreeDocks() {
    }

    /** The point visibility is measured from: the rocket's last station position, else the origin platform. */
    public static BlockPos anchor(@Nullable BlockPos lastStationPos) {
        return lastStationPos != null ? lastStationPos : new BlockPos(0, StationRegistry.PLATFORM_Y, 0);
    }

    /** Whether {@code centre} lies in a station's claim box or the origin platform's. */
    public static boolean claimed(BlockPos centre, List<StationRegistry.StationEntry> stations) {
        if (StationFounding.chebyshev(centre, BlockPos.ZERO) <= StationRegistry.CLAIM_RADIUS) {
            return true;
        }
        for (StationRegistry.StationEntry e : stations) {
            if (StationRegistry.inClaimBox(e, centre)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The free docks visible from {@code anchor}: {@link PadKind#DOCK} nodes among {@code stationNodes} (the station
     * dimension's nodes) outside every claim box, within {@code radius} (horizontal Euclidean), nearest first (ties by
     * node id), capped at {@link #MAX_VISIBLE}.
     */
    public static List<Dock> visible(List<PadRegistry.PadNode> stationNodes,
            List<StationRegistry.StationEntry> stations, BlockPos anchor, int radius) {
        List<Dock> out = new ArrayList<>();
        long r2 = (long) radius * radius;
        for (PadRegistry.PadNode node : stationNodes) {
            if (node.kind() != PadKind.DOCK) {
                continue;
            }
            BlockPos centre = node.centerOrPos();
            if (claimed(centre, stations)) {
                continue;
            }
            long dx = centre.getX() - anchor.getX();
            long dz = centre.getZ() - anchor.getZ();
            long d2 = dx * dx + dz * dz;
            if (d2 > r2) {
                continue;
            }
            int distance = PadSelection.roundedDistance((int) Math.round(Math.sqrt(d2)));
            out.add(new Dock(node.id(), node.name(), !PadRegistry.isDefaultLabel(node), centre, distance));
        }
        out.sort(Comparator.comparingLong((Dock d) -> sq(d.centre(), anchor)).thenComparingInt(Dock::padId));
        return out.size() > MAX_VISIBLE ? List.copyOf(out.subList(0, MAX_VISIBLE)) : List.copyOf(out);
    }

    private static long sq(BlockPos a, BlockPos b) {
        long dx = a.getX() - b.getX();
        long dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    /** The list index of pad node {@code padId}, or −1 when it is not (or no longer) visible. */
    public static int indexOf(List<Dock> docks, int padId) {
        for (int i = 0; i < docks.size(); i++) {
            if (docks.get(i).padId() == padId) {
                return i;
            }
        }
        return -1;
    }
}
