package za.co.neroland.nerospace.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.network.chat.Component;

import za.co.neroland.nerospace.network.FreeDockSyncPayload;
import za.co.neroland.nerospace.network.StationSyncPayload;
import za.co.neroland.nerospace.rocket.DockSelection;

/**
 * Client-side holder for the founded stations' display names (slot → name) and the viewer's own relation to each,
 * fed by {@link StationSyncPayload} (the clientbound handler registered in {@code ModNetwork.init()}) and read by the
 * Rocket and Launch Controller screens for the "Dock at" row and list. Pure data — no client-only imports — so it
 * loads safely even where the handler is registered from common code.
 */
public final class ClientStations {

    private static final Map<Integer, String> NAMES = new HashMap<>();
    private static final Map<Integer, DockSelection.Relation> RELATIONS = new HashMap<>();
    private static final List<Integer> ORDER = new ArrayList<>();
    /** The opened rocket's free docks, in its list order (index i is dock value {@code FREE_VALUE_BASE + i}). */
    private static final List<FreeDockSyncPayload.Row> FREE = new ArrayList<>();

    private ClientStations() {
    }

    public static void accept(StationSyncPayload payload) {
        NAMES.clear();
        RELATIONS.clear();
        ORDER.clear();
        int[] slots = payload.slots();
        String[] names = payload.names();
        for (int i = 0; i < slots.length; i++) {
            NAMES.put(slots[i], names[i]);
            RELATIONS.put(slots[i], payload.relation(i));
            ORDER.add(slots[i]);
        }
    }

    public static void acceptFreeDocks(FreeDockSyncPayload payload) {
        FREE.clear();
        FREE.addAll(payload.rows());
    }

    /** How many free docks the opened rocket may pick. */
    public static int freeCount() {
        return FREE.size();
    }

    /** The rounded distance of free dock {@code index} from the rocket's last station stop (−1 unknown). */
    public static int freeDistance(int index) {
        return index >= 0 && index < FREE.size() ? FREE.get(index).distance() : -1;
    }

    /** The synced station slots the viewer may pick, in founding order. */
    public static List<Integer> slots() {
        return List.copyOf(ORDER);
    }

    /** The viewer's relation to {@code slot} (origin / new for the two special targets). */
    public static DockSelection.Relation relation(int slot) {
        if (slot == DockSelection.NEW_STATION) {
            return DockSelection.Relation.NEW_STATION;
        }
        if (DockSelection.isFree(slot)) {
            return DockSelection.Relation.FREE;
        }
        if (slot < 0) {
            return DockSelection.Relation.ORIGIN;
        }
        return RELATIONS.getOrDefault(slot, DockSelection.Relation.PUBLIC);
    }

    /**
     * The docking target's label: the shared origin platform for {@code slot < 0}, the synced charter name when
     * known, else the stable founding-order fallback ("Station N").
     */
    public static Component label(int slot) {
        if (slot == DockSelection.NEW_STATION) {
            return Component.translatable("gui.nerospace.dock.new_station");
        }
        if (DockSelection.isFree(slot)) {
            int i = DockSelection.freeIndex(slot);
            if (i >= FREE.size()) {
                return Component.translatable("gui.nerospace.dock.free");
            }
            FreeDockSyncPayload.Row row = FREE.get(i);
            return row.named() ? Component.literal(row.name())
                    : Component.translatable("gui.nerospace.dock.free_n", i + 1, Math.max(0, row.distance()));
        }
        if (slot < 0) {
            return Component.translatable("gui.nerospace.dock.origin");
        }
        String name = NAMES.get(slot);
        return name != null ? Component.literal(name) : Component.translatable("gui.nerospace.dock.station_n", slot + 1);
    }

    /** {@link #label} as a plain string (kept for 2.0 callers). */
    public static String name(int slot) {
        return label(slot).getString();
    }
}
