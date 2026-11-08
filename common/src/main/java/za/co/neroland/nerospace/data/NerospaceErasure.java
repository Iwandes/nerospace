package za.co.neroland.nerospace.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.data.PlayerDataErasure;
import za.co.neroland.nerolandcore.data.SavedDataRecovery;

import za.co.neroland.nerospace.NerospaceCommon;
import za.co.neroland.nerospace.entity.AlienVillager;
import za.co.neroland.nerospace.entity.RoverEntity;
import za.co.neroland.nerospace.link.NerospaceLinkEvents;
import za.co.neroland.nerospace.platform.Services;
import za.co.neroland.nerospace.progression.PlanetVisitState;
import za.co.neroland.nerospace.rocket.LaunchCooldowns;
import za.co.neroland.nerospace.rocket.StationRegistry;
import za.co.neroland.nerospace.route.RouteRegistry;
import za.co.neroland.nerospace.village.VillageCoreBlockEntity;
import za.co.neroland.nerospace.village.VillageCoreLedger;
import za.co.neroland.nerospace.world.OxygenManager;
import za.co.neroland.nerospace.world.planet.Planets;

/**
 * Registers Nerospace's player-keyed stores with Neroland Core's shared {@link PlayerDataErasure} hook, so
 * one {@code /neroland data eraseme} (or Core's retention sweep) purges Nerospace together with every
 * other Nero mod. Keyed only by UUID; player identity is never logged.
 *
 * <h2>The stores, in erasure order</h2>
 * <ol>
 *   <li><b>Station ownership</b> ({@link StationRegistry}) — the owner UUID is anonymised to {@code ""},
 *       keeping the physical station as shared world content, and the UUID is removed from every station's member
 *       list (2.0, up to 16 members per station). The last-known-good backup file is rewritten
 *       immediately so the erased UUID is not retained there until the next periodic backup pass.</li>
 *   <li><b>Historical planet visits</b> ({@link PlanetVisitState}) — the player's whole row is dropped, and
 *       like stations the change is pushed into the backup file at once. This store holds only planet ids,
 *       never timestamps or coordinates, so there is nothing left to anonymise once the row is gone.</li>
 *   <li><b>Alien Villager reputation</b> — each {@link AlienVillager} keeps a {@code UUID -> score} map in
 *       its own entity NBT. This is a genuine player-data store: loaded villagers are swept below, and a villager
 *       that loads later drops the entry itself ({@link #villagerReputationFilter}).</li>
 *   <li><b>The oxygen attachment</b> — reset to a full tank.</li>
 *   <li><b>The Star Guide "seen" attachment</b> — reset to empty.</li>
 *   <li><b>Cargo routes</b> ({@link RouteRegistry}) — the player's routes are removed, their Cargo Pads
 *       anonymised (the station rule), they are dropped from every pad access list, and their flights are
 *       anonymised but keep flying: cargo is delivered or crated, never deleted, because items are not
 *       personal data — the ownership record is. Backup rewritten immediately.</li>
 *   <li><b>Village Core ownership</b> ({@link VillageCoreBlockEntity}) — the owner UUID in the core's block-entity
 *       data is cleared on every loaded core; the core keeps its stockpile, buildings and quest and becomes
 *       unclaimed. The 1.x owner display name is no longer stored at all. The {@link VillageCoreLedger}
 *       ({@code nerospace:village_cores}) turns every indexed core of the player that is in an unloaded chunk
 *       into a position-only pending clear, applied (and dropped) the moment that core loads again — across
 *       restarts, because the ledger is saved. Backup rewritten immediately.</li>
 *   <li><b>Launch cooldown</b> ({@link LaunchCooldowns}) — the transient 100-tick per-rider entry (memory only).</li>
 *   <li><b>Crew rejoin spot</b> ({@code CrewLanding}, 2.1) — memory only.</li>
 *   <li><b>Mars rover owner</b> ({@link RoverEntity}, 2.1) — the placer's UUID in the rover's entity NBT. Loaded
 *       rovers are swept now and become unowned (anyone may pack them up); a rover in an unloaded chunk drops the
 *       owner when it loads, through {@link #ownerErasedFilter} (the villager rule: this session's latch or the
 *       core ledger's bounded salted key). So that the filter also works after a restart, the ledger keeps the
 *       player's salted key (at most {@value VillageCoreLedger#MAX_LEGACY}, {@value VillageCoreLedger#LEGACY_TTL_DAYS}
 *       days) when they owned a loaded rover or had reached Mars ({@link #keepsRoverKey}). No counter or index of
 *       rovers exists, so there is nothing else to clear.</li>
 * </ol>
 *
 * <p>The two API-facing stores added alongside the visit history — external oxygen contributions and
 * terraforming overlays — are deliberately <em>not</em> listed: neither records a player, not even
 * transiently, so neither has anything to erase.</p>
 * <p>The NeroLink module's in-memory event bookkeeping (an edge latch and an alert cooldown, both keyed by
 * UUID, neither persisted) is dropped too.</p>
 *
 * <h2>What this can and cannot reach — stated honestly</h2>
 * <p>Several stores are only fully reachable while the player is online or the world is loaded, and
 * pretending otherwise would be worse than saying so:</p>
 * <ul>
 *   <li><b>Alien Villagers in unloaded chunks.</b> The sweep walks every <em>loaded</em> level. A villager
 *       whose chunk is not loaded keeps the UUID in its NBT until it loads again; when it does, its load-time
 *       filter drops the entry if the player was erased this session ({@link #erasedThisSession}) or if the
 *       player's salted key is still in the {@link VillageCoreLedger}'s bounded erasure-key list (the same
 *       pseudonymised key the core ledger keeps, at most {@value VillageCoreLedger#MAX_LEGACY} entries,
 *       {@value VillageCoreLedger#LEGACY_TTL_DAYS} days, only for a player who had visited a planet with
 *       villages). There is <em>no raw-UUID suppression list</em>, and no store was added for villagers: they
 *       reuse that one bounded, salted, time-limited key, which exists only to finish an erasure a loaded-only
 *       sweep cannot reach. A villager first loaded after the key expired keeps the entry; re-running erasure
 *       after visiting the region clears it.</li>
 *   <li><b>Village Cores in unloaded chunks.</b> Reached through the saved {@link VillageCoreLedger}: a core
 *       that was claimed or loaded on 2.x is indexed, and its owner is cleared when its chunk next loads, even
 *       after a restart. A 1.x core that has never loaded on 2.x is in no index; for those the ledger keeps a
 *       salted hash of the erased owner (pseudonymised, not anonymous; at most
 *       {@value VillageCoreLedger#MAX_LEGACY} entries, dropped after {@value VillageCoreLedger#LEGACY_TTL_DAYS}
 *       days, only for a player who had visited a planet with villages) and clears a matching core when it
 *       loads.</li>
 *   <li><b>An offline player's attachment data.</b> Both attachments are declared {@code .serialize(...)}
 *       with {@code copyOnDeath()}, so they persist in the player's own save file and do <em>not</em>
 *       "reset on their own" — the previous comment here was wrong. There is no cross-loader seam that
 *       reaches an offline player's attachments (NeoForge and Fabric both expose them only through a live
 *       {@code Player}), so for an offline player {@link #applyOnJoin} resets them the next time they log
 *       in during the same server session. Across a restart that latch is gone and the values stay until
 *       Core's account-level purge removes the player file itself.</li>
 * </ul>
 *
 * <p><b>POPIA/GDPR:</b> nothing on this path logs who was erased — the summary logs counts only.</p>
 */
public final class NerospaceErasure {

    /**
     * Players erased during THIS server session. Session-scoped and in-memory only, so it is not a
     * persistent tombstone of the accounts we were asked to forget; it exists purely to finish the job on
     * data that was out of reach at the moment of the request (an unloaded villager, an offline player).
     */
    private static final Set<UUID> ERASED_THIS_SESSION = ConcurrentHashMap.newKeySet();

    private NerospaceErasure() {
    }

    /** Registers the eraser with Core. Called once from {@code NerospaceCommon.init()}, ahead of the stores. */
    public static void init() {
        PlayerDataErasure.register(NerospaceErasure::erasePlayer);
    }

    /** Core's {@code PlayerDataEraser} body: purge one player from every Nerospace store. */
    public static void erasePlayer(MinecraftServer server, UUID uuid) {
        if (server == null || uuid == null) {
            return;
        }
        ERASED_THIS_SESSION.add(uuid);
        // Read from the visit store BEFORE step 2 erases it: only a player who reached a planet with villages can
        // own a Village Core or hold villager reputation, so only then may the ledger keep a salted erasure key.
        java.util.Set<String> visited = PlanetVisitState.get(server).export(uuid);
        boolean visitedVillagePlanet = anyVillagePlanet(visited);
        // 2.1: a rover is built from Martian parts, so a player who reached Mars may own a rover in an unloaded chunk.
        boolean visitedRoverPlanet = visited.contains(ROVER_PLANET);

        // 1. Stations — anonymise the owner and strip the UUID from every member list, then push the
        // result into the backup file at once.
        StationRegistry stations = StationRegistry.get(server);
        stations.forgetPlayer(uuid);
        SavedDataRecovery.backupNow(server.overworld(), StationRegistry.TYPE, stations, "nerospace:stations");

        // 2. Historical planet visits — a UUID -> planet-id set, dropped whole. Like stations, the
        // anonymisation is pushed straight into the last-known-good backup so the erased UUID is not
        // retained there until the next periodic backup pass.
        PlanetVisitState visits = PlanetVisitState.get(server);
        visits.forget(uuid);
        SavedDataRecovery.backupNow(server.overworld(), PlanetVisitState.TYPE, visits,
                PlanetVisitState.RECOVERY_NAME);

        // 3. Alien Villager reputation — held in entity NBT.
        int forgotten = sweepAlienVillagers(server, uuid);
        if (forgotten > 0) {
            // Count only — never who was erased, and never which villagers (POPIA/GDPR).
            NerospaceCommon.LOGGER.info(
                    "[Nerospace] Erasure: cleared reputation from {} loaded alien villager(s).", forgotten);
        }

        // 4 + 5. The two per-player attachments, reachable only through a live player handle.
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player != null) {
            resetAttachments(player);
        }

        // 6. Cargo routes — routes removed, pads anonymised, access lists purged, flights anonymised (cargo
        // keeps flying). Straight into Core's backup file as well, like the other two SavedData stores.
        RouteRegistry routes = RouteRegistry.get(server);
        routes.forgetPlayer(uuid);
        RouteRegistry.backupNow(server, routes);

        // 7. Village Core owner — held in block-entity data. Loaded cores are cleared now; every indexed core in an
        // unloaded chunk becomes a saved, position-only pending clear applied when it loads (any session).
        VillageCoreBlockEntity.syncLedger(server);
        int cores = VillageCoreBlockEntity.forgetOwnerEverywhere(uuid);
        VillageCoreLedger ledger = VillageCoreLedger.get(server);
        java.util.Set<VillageCoreLedger.CoreLoc> loaded = VillageCoreBlockEntity.loadedLocations();
        int pending = ledger.forgetOwner(uuid, loaded::contains, today(), visitedVillagePlanet);
        VillageCoreLedger.backupNow(server, ledger);
        if (cores > 0 || pending > 0) {
            // Counts only (POPIA/GDPR).
            NerospaceCommon.LOGGER.info("[Nerospace] Erasure: cleared ownership of {} loaded village core(s); "
                    + "{} unloaded core(s) will be cleared when they load.", cores, pending);
        }

        // 8. The transient launch cooldown (memory only).
        LaunchCooldowns.forget(uuid);

        // 9. The transient crew rejoin spot of a crewed flight (memory only, 2.1).
        za.co.neroland.nerospace.rocket.CrewLanding.forget(uuid);

        // 10. Mars rover owner — held in entity NBT (2.1). Loaded rovers now; unloaded ones on load.
        int rovers = RoverEntity.forgetOwnerEverywhere(server, uuid);
        if (rovers > 0) {
            // Count only (POPIA/GDPR).
            NerospaceCommon.LOGGER.info("[Nerospace] Erasure: cleared the owner of {} loaded rover(s).", rovers);
        }
        if (keepsRoverKey(rovers, visitedRoverPlanet)) {
            // An unloaded rover may still hold the UUID: keep the player's salted key (bounded, 30 days) so the rover's
            // load-time filter works after a restart too. Not for anyone who never reached Mars or held a rover.
            ledger.keepErasureKey(uuid, today());
            VillageCoreLedger.backupNow(server, ledger);
        }

        // The link module's in-memory event bookkeeping (never persisted).
        NerospaceLinkEvents.forgetPlayer(uuid);
    }

    /**
     * Walks every loaded level for Alien Villagers still holding this UUID and drops the entry.
     *
     * <p>Erasure is a rare, explicit operation (a command or a retention sweep), so a full pass over loaded
     * entities is an acceptable cost for being certain. Nothing on a tick path calls this.</p>
     *
     * @return how many villagers actually held an entry for the player
     */
    private static int sweepAlienVillagers(MinecraftServer server, UUID uuid) {
        int forgotten = 0;
        for (ServerLevel level : server.getAllLevels()) {
            List<AlienVillager> villagers = new ArrayList<>();
            level.getEntities(EntityTypeTest.forClass(AlienVillager.class),
                    villager -> villager.remembers(uuid), villagers);
            for (AlienVillager villager : villagers) {
                if (villager.forgetPlayer(uuid)) {
                    forgotten++;
                }
            }
        }
        return forgotten;
    }

    /**
     * Finishes an erasure that landed while the player was offline: resets the two attachments the moment
     * they rejoin. Called from the join handler on every loader. A no-op for everyone else, and a no-op
     * across a restart (see the class javadoc — this latch is deliberately not persisted).
     */
    public static void applyOnJoin(ServerPlayer player) {
        if (player == null || !ERASED_THIS_SESSION.contains(player.getUUID())) {
            return;
        }
        resetAttachments(player);
        NerospaceCommon.LOGGER.info(
                "[Nerospace] Erasure: reset the oxygen + Star Guide attachments for a player who was "
                        + "offline when their erasure request was processed.");
    }

    /**
     * Whether this player was erased during the current server session. Read by {@link AlienVillager} when
     * it loads from NBT, so a villager whose chunk was unloaded at erasure time is still cleaned up.
     */
    public static boolean erasedThisSession(UUID uuid) {
        return uuid != null && ERASED_THIS_SESSION.contains(uuid);
    }

    /** The planet whose materials build a rover (2.1): reaching it is the trigger for keeping a rover erasure key. */
    static final String ROVER_PLANET = "nerospace:mars";

    /**
     * Whether erasure keeps the player's salted key for rovers (2.1): they owned a loaded rover just now, or they had
     * reached Mars (where rover parts come from), so a rover of theirs may sit in an unloaded chunk.
     */
    static boolean keepsRoverKey(int loadedRoversCleared, boolean visitedRoverPlanet) {
        return loadedRoversCleared > 0 || visitedRoverPlanet;
    }

    /** Whether any of these visited planet ids is a planet with a native villager culture. */
    static boolean anyVillagePlanet(Collection<String> planetIds) {
        for (String raw : planetIds) {
            Identifier id = Identifier.tryParse(raw);
            if (id != null && Planets.byId(id).map(p -> p.traits().villagerCulture().isPresent()).orElse(false)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The load-time reputation filter for an {@link AlienVillager} in {@code level}: true for a player erased this
     * session, or whose salted key is in the core ledger's bounded erasure-key list. Reads only; stores nothing.
     * The ledger is consulted only on the server thread (a villager deserialised off-thread, e.g. by worldgen,
     * falls back to the session latch).
     */
    public static Predicate<UUID> villagerReputationFilter(@Nullable Level level) {
        VillageCoreLedger ledger = null;
        if (level instanceof ServerLevel serverLevel) {
            MinecraftServer server = serverLevel.getServer();
            if (server != null && server.isSameThread() && server.overworld() != null) {
                ledger = VillageCoreLedger.get(server);
            }
        }
        VillageCoreLedger found = ledger;
        long day = today();
        return id -> erasedThisSession(id) || (found != null && found.isErased(id, day));
    }

    /**
     * The load-time owner filter for a {@link RoverEntity} in {@code level}: the same rule as
     * {@link #villagerReputationFilter} (this session's latch, or the core ledger's bounded salted key). Reads only.
     */
    public static Predicate<UUID> ownerErasedFilter(@Nullable Level level) {
        return villagerReputationFilter(level);
    }

    /** UTC epoch day, the ledger's clock for its bounded retention. */
    static long today() {
        return java.time.LocalDate.now(java.time.ZoneOffset.UTC).toEpochDay();
    }

    private static void resetAttachments(ServerPlayer player) {
        Services.PLATFORM.setOxygen(player, OxygenManager.OXYGEN_MAX);
        Services.PLATFORM.setStarGuideSeen(player, List.of());
    }
}
