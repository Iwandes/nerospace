package za.co.neroland.nerospace.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerospace.network.PadSyncPayload;

/**
 * Client-side holder for the selectable landing nodes' labels per dimension (and, for the rocket's own dimension, the
 * rounded distance from it), fed by {@link PadSyncPayload} when the player opens a rocket or a Launch Controller.
 * Pure data — safe to reference from the common handler registration.
 */
public final class ClientPads {

    private static final Map<String, List<String>> NAMES = new HashMap<>();
    private static final Map<String, List<Integer>> DISTANCES = new HashMap<>();

    private ClientPads() {
    }

    public static void accept(PadSyncPayload payload) {
        NAMES.clear();
        DISTANCES.clear();
        for (int i = 0; i < payload.dims().size(); i++) {
            String dim = payload.dims().get(i);
            NAMES.computeIfAbsent(dim, k -> new ArrayList<>()).add(payload.names().get(i));
            DISTANCES.computeIfAbsent(dim, k -> new ArrayList<>())
                    .add(i < payload.distances().size() ? payload.distances().get(i) : -1);
        }
    }

    /** The label of pad {@code index} in {@code dimId}, or {@code null} when unknown. */
    @Nullable
    public static String name(String dimId, int index) {
        List<String> list = NAMES.get(dimId);
        return list != null && index >= 0 && index < list.size() ? list.get(index) : null;
    }

    /** The rounded distance from the rocket to pad {@code index} in {@code dimId}, or −1 when not meaningful. */
    public static int distance(String dimId, int index) {
        List<Integer> list = DISTANCES.get(dimId);
        return list != null && index >= 0 && index < list.size() ? list.get(index) : -1;
    }

    /** How many selectable pads the client knows in {@code dimId}. */
    public static int count(String dimId) {
        List<String> list = NAMES.get(dimId);
        return list == null ? 0 : list.size();
    }
}
