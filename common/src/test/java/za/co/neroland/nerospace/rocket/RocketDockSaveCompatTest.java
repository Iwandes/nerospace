package za.co.neroland.nerospace.rocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.CompoundTag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A 2.0.0 rocket's {@code StationSlot} is honoured on load. Entity NBT needs a level to decode, so this follows the
 * plain-JVM pattern of the other rocket compat tests: the same keys and defaults {@code RocketEntity} reads, through
 * the same {@link DockSelection} helpers, on a hand-built 2.0.0-shaped tag, plus a source check of the read/write.
 */
class RocketDockSaveCompatTest {

    /** The station fields a 2.0.0-beta.1 rocket writes (no {@code StationChosen}). */
    static CompoundTag rocket200(int slot) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Tier", 1);
        tag.putInt("Destination", 1);
        tag.putBoolean("GlobalDestination", true);
        tag.putInt("StationSlot", slot);
        tag.putInt("Pad", -1);
        tag.putBoolean("CharterRule", true);
        tag.putBoolean("Visitor", false);
        return tag;
    }

    static int loadSlot(CompoundTag tag) {
        return DockSelection.onLoad(tag.getIntOr("StationSlot", DockSelection.ORIGIN));
    }

    static boolean loadChosen(CompoundTag tag) {
        return tag.getBooleanOr("StationChosen", false);
    }

    @Test
    @DisplayName("a 2.0.0 rocket pointed at a station keeps it, and the default never moves it")
    void stationSlotHonoured() {
        CompoundTag tag = rocket200(3);
        assertEquals(3, loadSlot(tag));
        assertFalse(loadChosen(tag));
        assertFalse(DockSelection.shouldApplyDefault(loadSlot(tag), loadChosen(tag)));
        assertEquals(DockSelection.NEW_STATION, loadSlot(rocket200(DockSelection.NEW_STATION)));
    }

    @Test
    @DisplayName("a 2.0.0 rocket on the origin takes the owner-first default once; a 2.1 explicit origin pick stands")
    void originRules() {
        CompoundTag legacy = rocket200(DockSelection.ORIGIN);
        assertTrue(DockSelection.shouldApplyDefault(loadSlot(legacy), loadChosen(legacy)));
        CompoundTag picked = rocket200(DockSelection.ORIGIN);
        picked.putBoolean("StationChosen", true);
        assertFalse(DockSelection.shouldApplyDefault(loadSlot(picked), loadChosen(picked)));
    }

    @Test
    @DisplayName("round trip: 2.1 writes StationSlot unchanged plus an optional StationChosen; bad slots read as origin")
    void roundTrip() {
        CompoundTag tag = rocket200(5);
        CompoundTag written = new CompoundTag();
        written.putInt("StationSlot", loadSlot(tag));
        written.putBoolean("StationChosen", true);
        assertEquals(5, loadSlot(written));
        assertTrue(loadChosen(written));
        assertEquals(DockSelection.ORIGIN, loadSlot(new CompoundTag()), "absent key: the 2.0.0 default");
        assertEquals(DockSelection.ORIGIN, loadSlot(rocket200(-7)));
        assertEquals(DockSelection.ORIGIN, loadSlot(rocket200(StationRegistry.MAX_STATIONS)));
    }

    @Test
    @DisplayName("RocketEntity reads and writes exactly these keys and defaults")
    void entityUsesTheseKeys() throws Exception {
        String rocket = PodArrivalTest.source("RocketEntity.java");
        assertTrue(rocket.contains("DockSelection.onLoad(input.getIntOr(\"StationSlot\", DockSelection.ORIGIN))"));
        assertTrue(rocket.contains("input.getBooleanOr(\"StationChosen\", false)"));
        assertTrue(rocket.contains("output.putInt(\"StationSlot\", getStationSlot());"));
        assertTrue(rocket.contains("output.putBoolean(\"StationChosen\", this.stationChosen);"));
    }
}
