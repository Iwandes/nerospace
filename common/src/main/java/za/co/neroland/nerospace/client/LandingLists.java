package za.co.neroland.nerospace.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerospace.client.ui.LandingPicker;
import za.co.neroland.nerospace.rocket.DockSelection;
import za.co.neroland.nerospace.rocket.PadSelection;

/**
 * Builds the rows of the "Dock at" and "Land at" lists ({@link LandingPicker}) from the synced labels in
 * {@link ClientStations} and {@link ClientPads}, and the one-line labels of the rows that open them. Shared by the
 * Rocket and Launch Controller screens so both show the same candidates in the same order as the server cycler.
 */
public final class LandingLists {

    private static final int YOURS = 0xFF54D46A;
    private static final int MEMBER = 0xFF3CC8E6;
    private static final int PUBLIC = 0xFF8DA0B4;
    private static final int ORIGIN = 0xFFF5C542;
    private static final int NEW = 0xFFF0703C;
    private static final int FREE = 0xFFB48CF0;

    private LandingLists() {
    }

    /** Origin, every station the viewer may pick (founding order), the rocket's free docks (nearest first), then New Station. */
    public static List<LandingPicker.Entry> docks(boolean hasCharter) {
        List<LandingPicker.Entry> out = new ArrayList<>();
        out.add(dock(DockSelection.ORIGIN, Component.translatable("gui.nerospace.dock.origin.tooltip")));
        for (int slot : ClientStations.slots()) {
            out.add(dock(slot, null));
        }
        for (int i = 0; i < ClientStations.freeCount(); i++) {
            int value = DockSelection.freeValue(i);
            int distance = ClientStations.freeDistance(i);
            out.add(new LandingPicker.Entry(value, ClientStations.label(value),
                    Component.translatable(DockSelection.Relation.FREE.langKey()), FREE,
                    distance >= 0 ? Component.translatable("gui.nerospace.picker.free_distance", distance) : null,
                    Component.translatable("gui.nerospace.dock.free.tooltip")));
        }
        out.add(dock(DockSelection.NEW_STATION, hasCharter ? null
                : Component.translatable("item.nerospace.station_charter.needed")));
        return out;
    }

    private static LandingPicker.Entry dock(int slot, @Nullable Component tooltip) {
        DockSelection.Relation relation = ClientStations.relation(slot);
        return new LandingPicker.Entry(slot, ClientStations.label(slot), Component.translatable(relation.langKey()),
                colour(relation), null, tooltip);
    }

    private static int colour(DockSelection.Relation relation) {
        return switch (relation) {
            case YOURS -> YOURS;
            case MEMBER -> MEMBER;
            case ORIGIN -> ORIGIN;
            case NEW_STATION -> NEW;
            default -> PUBLIC;
        };
    }

    /** Nearest pad, then every selectable pad in {@code dest} (with its distance when it is in this dimension). */
    public static List<LandingPicker.Entry> pads(@Nullable ResourceKey<Level> dest) {
        List<LandingPicker.Entry> out = new ArrayList<>();
        out.add(new LandingPicker.Entry(PadSelection.AUTO, Component.translatable("gui.nerospace.picker.pad_nearest"),
                null, PUBLIC, null, null));
        if (dest == null) {
            return out;
        }
        String dimId = dest.identifier().toString();
        for (int i = 0; i < ClientPads.count(dimId); i++) {
            int distance = ClientPads.distance(dimId, i);
            out.add(new LandingPicker.Entry(i, padLabel(dest, i), null, PUBLIC,
                    distance >= 0 ? Component.translatable("gui.nerospace.picker.distance", distance) : null, null));
        }
        return out;
    }

    /** The label of pad {@code index} in {@code dest} ("Nearest pad" for {@link PadSelection#AUTO}). */
    public static Component padLabel(@Nullable ResourceKey<Level> dest, int index) {
        if (index < 0 || dest == null) {
            return Component.translatable("gui.nerospace.picker.pad_nearest");
        }
        String name = ClientPads.name(dest.identifier().toString(), index);
        return name != null ? Component.literal(name) : Component.translatable("gui.nerospace.picker.pad_n", index + 1);
    }
}
