package za.co.neroland.nerospace.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.entity.EntityTypeTest;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerospace.entity.AlienVillager;
import za.co.neroland.nerospace.entity.RoverEntity;
import za.co.neroland.nerospace.platform.Services;
import za.co.neroland.nerospace.progression.PlanetVisitState;
import za.co.neroland.nerospace.registry.ModDimensions;
import za.co.neroland.nerospace.rocket.StationRegistry;
import za.co.neroland.nerospace.route.FlightRecord;
import za.co.neroland.nerospace.route.PadRecord;
import za.co.neroland.nerospace.route.RouteRecord;
import za.co.neroland.nerospace.route.RouteRegistry;
import za.co.neroland.nerospace.village.VillageCoreBlockEntity;
import za.co.neroland.nerospace.village.VillageCoreLedger;

/**
 * {@code /nerospace privacy export} — POPIA s23 / GDPR Art. 15 access: everything Nerospace stores about the
 * requesting player, as JSON, shown only to that player.
 *
 * <p>Nothing about another player is included: a station the requester owns shows how many members it has, never
 * who they are; a station the requester is a member of shows no owner; pads show an access-list size only.
 * Positions and dimension ids of the requester's own things are included (they are the requester's data).</p>
 *
 * <p>Scope, stated honestly: Village Cores come from the loaded cores plus the saved core index (a 1.x core that has
 * never loaded on 2.x is not indexed yet); Alien Villager reputation and Mars rovers (2.1) come from loaded entities
 * only.</p>
 */
public final class NerospacePrivacyExport {

    /** Export format version. */
    public static final int FORMAT = 1;

    private NerospacePrivacyExport() {
    }

    /** One Alien Villager's score for the requester (loaded villagers only). */
    public record Reputation(String dimension, int score) {
    }

    /** One Mars rover the requester placed (2.1, loaded rovers only): where it is and its charge. */
    public record Rover(String dimension, BlockPos pos, int chargePercent) {
    }

    /** Gathers the requester's data from the live server. */
    public static JsonObject collect(MinecraftServer server, ServerPlayer player) {
        UUID me = player.getUUID();
        VillageCoreBlockEntity.syncLedger(server);
        Set<VillageCoreLedger.CoreLoc> cores = new LinkedHashSet<>(VillageCoreBlockEntity.loadedOwnedBy(me));
        cores.addAll(VillageCoreLedger.get(server).ownedBy(me));
        RouteRegistry routes = RouteRegistry.get(server);
        List<Reputation> reputation = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            List<AlienVillager> villagers = new ArrayList<>();
            level.getEntities(EntityTypeTest.forClass(AlienVillager.class), v -> v.remembers(me), villagers);
            for (AlienVillager v : villagers) {
                reputation.add(new Reputation(level.dimension().identifier().toString(), v.getReputation(player)));
            }
        }
        List<Rover> rovers = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            List<RoverEntity> mine = new ArrayList<>();
            level.getEntities(EntityTypeTest.forClass(RoverEntity.class), r -> r.isOwnedBy(me), mine);
            for (RoverEntity r : mine) {
                rovers.add(new Rover(level.dimension().identifier().toString(), r.blockPosition(), r.energyPercent()));
            }
        }
        JsonObject doc = build(me, PlanetVisitState.get(server).export(me), StationRegistry.get(server).all(),
                ModDimensions.STATION_LEVEL.identifier().toString(), cores, routes.allPads(),
                routes.routesOwnedBy(me), routes.flightsOwnedBy(me), Services.PLATFORM.getOxygen(player),
                Services.PLATFORM.getStarGuideSeen(player), reputation);
        doc.add("rovers_owned_loaded", rovers(rovers));
        return doc;
    }

    /** Pure (2.1): the requester's own rovers. Nothing about riders or other owners exists to include. */
    public static JsonArray rovers(Collection<Rover> rovers) {
        JsonArray rows = new JsonArray();
        for (Rover r : rovers) {
            JsonObject row = new JsonObject();
            row.addProperty("dimension", r.dimension());
            row.add("pos", pos(r.pos()));
            row.addProperty("charge_percent", r.chargePercent());
            rows.add(row);
        }
        return rows;
    }

    /** Pure: builds the export document. Unit-tested for "nothing about other players". */
    public static JsonObject build(UUID me, Collection<String> planets, Collection<StationRegistry.StationEntry> stations,
            String stationDimension, Collection<VillageCoreLedger.CoreLoc> cores, Collection<PadRecord> pads,
            Collection<RouteRecord> routes, Collection<FlightRecord> flights, @Nullable Integer oxygen, @Nullable List<Integer> starGuideSeen,
            Collection<Reputation> reputation) {
        String id = me.toString();
        JsonObject root = new JsonObject();
        root.addProperty("mod", "nerospace");
        root.addProperty("format", FORMAT);

        JsonArray visited = new JsonArray();
        planets.stream().sorted().forEach(visited::add);
        root.add("planets_visited", visited);

        JsonArray owned = new JsonArray();
        JsonArray memberOf = new JsonArray();
        for (StationRegistry.StationEntry e : stations) {
            boolean isOwner = id.equals(e.owner());
            boolean isMember = !isOwner && e.members().contains(id);
            if (!isOwner && !isMember) {
                continue;
            }
            JsonObject row = new JsonObject();
            row.addProperty("slot", e.slot());
            row.addProperty("name", e.name());
            row.addProperty("dimension", stationDimension);
            row.add("center", pos(e.center()));
            row.addProperty("class", e.stationClass().name());
            if (isOwner) {
                row.addProperty("founded_at_epoch_day", e.foundedAt());
                row.addProperty("docking", e.docking().name());
                row.addProperty("member_count", e.members().size());
                owned.add(row);
            } else {
                memberOf.add(row);
            }
        }
        root.add("stations_owned", owned);
        root.add("station_memberships", memberOf);

        JsonArray coreRows = new JsonArray();
        for (VillageCoreLedger.CoreLoc c : cores) {
            JsonObject row = new JsonObject();
            row.addProperty("dimension", c.dim());
            row.add("pos", pos(BlockPos.of(c.pos())));
            coreRows.add(row);
        }
        root.add("village_cores_owned", coreRows);

        JsonArray padRows = new JsonArray();
        int sharedWithMe = 0;
        for (PadRecord p : pads) {
            if (id.equals(p.owner())) {
                JsonObject row = new JsonObject();
                row.addProperty("id", p.id());
                row.addProperty("name", p.name());
                row.addProperty("dimension", p.dim());
                row.add("pos", pos(p.pos()));
                row.addProperty("public", p.isPublic());
                row.addProperty("access_list_size", p.access().size());
                padRows.add(row);
            } else if (p.access().contains(id)) {
                sharedWithMe++;
            }
        }
        root.add("cargo_pads_owned", padRows);
        root.addProperty("cargo_pads_shared_with_you", sharedWithMe);

        JsonArray routeRows = new JsonArray();
        for (RouteRecord r : routes) {
            if (!id.equals(r.owner())) {
                continue;
            }
            JsonObject row = new JsonObject();
            row.addProperty("id", r.id());
            row.addProperty("origin_pad", r.originPadId());
            row.addProperty("destination_pad", r.destinationPadId());
            row.addProperty("schedule", r.mode().name());
            routeRows.add(row);
        }
        root.add("cargo_routes_owned", routeRows);

        // Flights the requester owns (kept until pruned after cargoFlightRetentionDays). Ids and state only: pad
        // owners, cargo and the other endpoint's owner are not the requester's data.
        JsonArray flightRows = new JsonArray();
        for (FlightRecord f : flights) {
            if (!id.equals(f.owner())) {
                continue;
            }
            JsonObject row = new JsonObject();
            row.addProperty("id", f.id());
            row.addProperty("route", f.routeId());
            row.addProperty("state", f.state().name());
            row.addProperty("return_leg", f.returnLeg());
            flightRows.add(row);
        }
        root.add("cargo_flights_owned", flightRows);

        if (oxygen != null) {
            root.addProperty("oxygen", oxygen);
        }
        if (starGuideSeen != null) {
            JsonArray seen = new JsonArray();
            starGuideSeen.forEach(seen::add);
            root.add("star_guide_seen", seen);
        }

        JsonArray rep = new JsonArray();
        for (Reputation r : reputation) {
            JsonObject row = new JsonObject();
            row.addProperty("dimension", r.dimension());
            row.addProperty("score", r.score());
            rep.add(row);
        }
        root.add("alien_villager_reputation_loaded", rep);
        return root;
    }

    private static JsonArray pos(BlockPos p) {
        JsonArray a = new JsonArray();
        a.add(p.getX());
        a.add(p.getY());
        a.add(p.getZ());
        return a;
    }
}
