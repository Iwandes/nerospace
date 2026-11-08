package za.co.neroland.nerospace.rocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * Reproduction of the 2.1.0 landing-selection bug: a player who owns a station deploys a rocket and launches it
 * from a Launch Controller, and it docks at the shared origin platform ({@code StationSlot -1}) instead of their
 * station. The controller has no dock control, and nothing picks the owner's station by default.
 */
class LaunchControllerDockDefaultTest {

    static final String OWNER = "4b2b9a4e-1c2d-4e5f-8a9b-0c1d2e3f4a5b";
    static final String OTHER = "9d8c7b6a-5f4e-4d3c-8b2a-1f0e9d8c7b6a";

    @Test
    void ownersControllerLaunchDocksAtTheirStation() {
        StationRegistry registry = new StationRegistry();
        registry.found("Someone Else's", OTHER);
        StationRegistry.StationEntry home = registry.found("Home", OWNER);
        assertNotNull(home);

        // Deploy: the rocket's StationSlot starts on the default. The Launch Controller cannot change it, so this is
        // the slot RocketEntity#stationForArrival reads when the controller's Launch button fires.
        int slot = DockSelection.defaultSlot(registry.all(), OWNER);
        StationRegistry.StationEntry target = slot >= 0 ? registry.get(slot) : null;

        assertEquals(home.slot(), slot, "controller launch resolved StationSlot " + slot
                + " (origin platform) for a player who owns station slot " + home.slot());
        assertEquals(home, target);
    }

    @Test
    void controllerOffersADockSelector() throws Exception {
        String menu = menuSource("LaunchControllerMenu.java");
        assertTrue(menu.contains("BUTTON_CYCLE_STATION"),
                "LaunchControllerMenu has no dock selection: launches from it can only reach the origin platform");
    }

    private static String menuSource(String file) throws Exception {
        for (Path p = Path.of(System.getProperty("user.dir")).toAbsolutePath(); p != null; p = p.getParent()) {
            Path c = p.resolve("common/src/main/java/za/co/neroland/nerospace/menu").resolve(file);
            if (Files.isRegularFile(c)) {
                return Files.readString(c);
            }
        }
        throw new IllegalStateException("source not found: " + file);
    }
}
