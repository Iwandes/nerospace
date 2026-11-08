package za.co.neroland.nerospace.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.Dynamic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.data.ErasureConformance;
import za.co.neroland.nerolandcore.data.PlayerDataEraser;
import za.co.neroland.nerolandcore.data.PlayerDataErasure;
import za.co.neroland.nerospace.api.PlanetId;
import za.co.neroland.nerospace.entity.RoverRules;
import za.co.neroland.nerospace.progression.PlanetVisitState;
import za.co.neroland.nerospace.rocket.CrewLanding;
import za.co.neroland.nerospace.rocket.LaunchCooldowns;
import za.co.neroland.nerospace.rocket.StationRegistry;
import za.co.neroland.nerospace.route.RouteRegistry;
import za.co.neroland.nerospace.village.VillageCoreLedger;
import za.co.neroland.nerospace.village.VillageCoreOwnership;

/**
 * At unit level: Core's {@link ErasureConformance} over every player-keyed Nerospace store, with
 * the station store decoded from the <b>1.6.0 shape</b> (the migrated fixture) plus a 2.0 entry carrying members.
 * The eraser applies the same store operations, in the same order, as {@code NerospaceErasure.erasePlayer}.
 */
class NerospaceErasureConformanceTest {

    private static final String OWNER = "00000000-0000-4000-8000-00000000f1c5";
    private static final String OTHER = "00000000-0000-4000-8000-0000000000b2";
    private static final ResourceKey<Level> OVERWORLD = ResourceKey.create(Registries.DIMENSION, Identifier.parse("minecraft:overworld"));
    private static final PlanetId CINDARA = new PlanetId(Identifier.fromNamespaceAndPath("nerospace", "cindara"));
    private static final long TODAY = 20_000L;

    static CompoundTag legacyStations() {
        ListTag stations = new ListTag();
        stations.add(entry(0, "Station A", 4096, OWNER));
        stations.add(entry(1, "Legacy Station", 8192, null));
        CompoundTag root = new CompoundTag();
        root.put("stations", stations);
        root.putInt("next_slot", 2);
        return root;
    }

    private static CompoundTag entry(int slot, String name, int x, String owner) {
        CompoundTag t = new CompoundTag();
        t.putInt("slot", slot);
        t.putString("name", name);
        t.put("center", new IntArrayTag(new int[] { x, 64, 0 }));
        if (owner != null) {
            t.putString("owner", owner);
        }
        return t;
    }

    @Test
    void everyPlayerKeyedStorePassesConformanceOnTheMigratedShape() {
        UUID owner = UUID.fromString(OWNER);
        StationRegistry stations = StationRegistry.codec()
                .parse(new Dynamic<>(NbtOps.INSTANCE, legacyStations())).getOrThrow();
        int foreign = stations.found("Erasure Seed", OTHER).slot();
        assertTrue(stations.addMember(foreign, OWNER));
        // 2.1: a station claimed where the player stood (D6) is the same entry shape with a chosen centre.
        int placed = stations.foundAt("Claimed Here", OWNER, new BlockPos(20_000, 120, -9_000)).slot();

        PlanetVisitState visits = new PlanetVisitState();
        visits.record(owner, CINDARA);

        RouteRegistry routes = new RouteRegistry();
        routes.registerPad(OVERWORLD, new BlockPos(0, 64, 0), owner, "Home Dock");
        int shared = routes.registerPad(OVERWORLD, new BlockPos(9, 64, 9), UUID.fromString(OTHER), null).id();
        routes.grantAccess(shared, owner);

        VillageCoreOwnership loadedCore = new VillageCoreOwnership();
        loadedCore.claim(owner);
        VillageCoreLedger ledger = new VillageCoreLedger();
        VillageCoreLedger.CoreLoc loaded = new VillageCoreLedger.CoreLoc("nerospace:greenxertz", new BlockPos(1, 70, 1).asLong());
        VillageCoreLedger.CoreLoc unloaded = new VillageCoreLedger.CoreLoc("nerospace:cindara", new BlockPos(5, 70, 5).asLong());
        ledger.record(loaded, owner);
        ledger.record(unloaded, owner);

        LaunchCooldowns.tryStart(owner, 0L, 100L);
        CrewLanding.remember(owner, OVERWORLD, new BlockPos(0, 65, 0));
        // 2.1 rovers: one the player placed, one a stranger placed (stays theirs).
        java.util.concurrent.atomic.AtomicReference<UUID> myRover = new java.util.concurrent.atomic.AtomicReference<>(owner);
        java.util.concurrent.atomic.AtomicReference<UUID> theirRover =
                new java.util.concurrent.atomic.AtomicReference<>(UUID.fromString(OTHER));

        PlayerDataEraser eraser = (server, uuid) -> {
            stations.forgetPlayer(uuid);                                  // 1 + 1b
            visits.forget(uuid);                                          // 2
            routes.forgetPlayer(uuid);                                    // 6
            loadedCore.forget(uuid);                                      // 7 (live sweep)
            ledger.forgetOwner(uuid, Set.of(loaded)::contains, TODAY, true);    // 7 (unloaded -> pending)
            LaunchCooldowns.forget(uuid);                                 // 8
            CrewLanding.forget(uuid);                                     // 9
            myRover.set(RoverRules.forget(myRover.get(), uuid));          // 10 (RoverEntity.forgetOwner)
            theirRover.set(RoverRules.forget(theirRover.get(), uuid));
        };
        PlayerDataErasure.register(eraser);
        try {
            ErasureConformance.create()
                    .probe("nerospace:station_owner",
                            u -> stations.all().stream().anyMatch(e -> u.toString().equals(e.owner())))
                    .probe("nerospace:station_members",
                            u -> stations.all().stream().anyMatch(e -> e.members().contains(u.toString())))
                    .probe("nerospace:planet_visits", u -> !visits.export(u).isEmpty())
                    .probe("nerospace:cargo_routes", routes::retainsData)
                    .probe("nerospace:village_core_owner", u -> loadedCore.isOwner(u) || ledger.retainsData(u))
                    .probe("nerospace:launch_cooldown", LaunchCooldowns::holds)
                    .probe("nerospace:crew_landing", CrewLanding::holds)
                    .probe("nerospace:rover_owner", u -> u.equals(myRover.get()) || u.equals(theirRover.get()))
                    .verify(null, owner);
        } finally {
            PlayerDataErasure.unregister(eraser);
        }

        // The station stays as shared world content, the other owner keeps theirs.
        assertEquals("", stations.get(0).owner());
        assertEquals("Station A", stations.get(0).name());
        assertEquals(OTHER, stations.get(foreign).owner());
        assertEquals(List.of(), stations.get(foreign).members());
        // The placed station keeps its chosen centre as world content but is no longer tied to the player.
        assertEquals("", stations.get(placed).owner());
        assertEquals(new BlockPos(20_000, 120, -9_000), stations.get(placed).center());
        // The player's rover is unowned (anyone may pack it up); the stranger's rover keeps its owner.
        assertEquals(null, myRover.get());
        assertEquals(UUID.fromString(OTHER), theirRover.get());
        // The unloaded core became a position-only pending clear.
        assertTrue(ledger.isPending(unloaded));
        assertFalse(ledger.isPending(loaded), "the live sweep already cleared the loaded core");
    }

    /**
     * 2.1 review fix: an unloaded rover first loaded after a restart. The session latch is gone, so the rover's
     * load-time filter must find the player's salted key in the saved ledger. Kept for a rover owner or a Mars
     * visitor only; anyone else leaves nothing behind.
     */
    @Test
    void roverOwnerFilterSurvivesARestart() {
        UUID owner = UUID.fromString(OWNER);
        UUID stranger = UUID.fromString(OTHER);
        VillageCoreLedger ledger = new VillageCoreLedger();
        ledger.forgetOwner(owner, l -> false, TODAY, false); // never visited a village planet
        assertFalse(ledger.isErased(owner, TODAY), "no key from the village rule alone");
        assertTrue(NerospaceErasure.keepsRoverKey(0, true), "reached Mars");
        assertTrue(NerospaceErasure.keepsRoverKey(2, false), "held loaded rovers");
        assertFalse(NerospaceErasure.keepsRoverKey(0, false), "no rover reason: nothing kept");
        assertTrue(java.util.Set.of("nerospace:mars").contains(NerospaceErasure.ROVER_PLANET));
        ledger.keepErasureKey(owner, TODAY);

        // "Restart": save and decode the ledger; the in-memory latch is not involved.
        net.minecraft.nbt.Tag saved = VillageCoreLedger.storeCodec().encodeStart(NbtOps.INSTANCE, ledger).getOrThrow();
        VillageCoreLedger reloaded = VillageCoreLedger.storeCodec()
                .parse(new Dynamic<>(NbtOps.INSTANCE, saved)).getOrThrow();
        assertFalse(saved.toString().contains(OWNER), "the ledger never holds the raw UUID");
        assertTrue(reloaded.isErased(owner, TODAY + 1));
        assertFalse(reloaded.isErased(stranger, TODAY + 1));
        // The rover's load path: RoverRules.forget under the filter drops the owner, a stranger's rover keeps theirs.
        java.util.function.Predicate<UUID> filter = id -> reloaded.isErased(id, TODAY + 1);
        assertEquals(null, filter.test(owner) ? null : owner);
        assertEquals(stranger, filter.test(stranger) ? null : stranger);
        // Bounded in time: the key expires with the village rule's TTL.
        assertFalse(reloaded.isErased(owner, TODAY + VillageCoreLedger.LEGACY_TTL_DAYS + 1));
    }

    @Test
    void pendingClearIsAppliedOnLoadAndDropped() {
        UUID owner = UUID.fromString(OWNER);
        VillageCoreLedger ledger = new VillageCoreLedger();
        VillageCoreLedger.CoreLoc far = new VillageCoreLedger.CoreLoc("minecraft:overworld", 123L);
        ledger.record(far, owner);
        assertEquals(1, ledger.forgetOwner(owner, l -> false, TODAY, true));
        assertTrue(ledger.onLoaded(far, owner, TODAY + 400), "pending applies even after the legacy TTL");
        assertEquals(0, ledger.pendingCount(), "applied entry dropped");
        assertFalse(ledger.retainsData(owner));
        assertFalse(ledger.onLoaded(far, null, TODAY + 400), "second load: nothing left to do");
    }
}
