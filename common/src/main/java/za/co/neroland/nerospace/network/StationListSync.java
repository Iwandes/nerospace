package za.co.neroland.nerospace.network;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerospace.registry.ModDimensions;
import za.co.neroland.nerospace.rocket.StationRegistry;

/**
 * Keeps a player's "Dock at" list current without a relog: {@link #send} pushes a fresh viewer-filtered
 * {@link StationSyncPayload} (names and the viewer's own relation only) after they found, rename or abandon a station,
 * and {@link #tick} pushes one when they enter the station dimension.
 *
 * <p>Nerospace has no common dimension-change hook, so {@link #tick} runs from each loader's existing per-player
 * server tick and compares the player's level with the one seen last tick. The last-seen dimension is held in a
 * {@link WeakHashMap} keyed by the live player object — in memory only, never saved, no UUID stored — so it is
 * dropped with the player on logout.</p>
 */
public final class StationListSync {

    private static final Map<ServerPlayer, ResourceKey<Level>> LAST_DIM = new WeakHashMap<>();

    private StationListSync() {
    }

    /** Sends {@code player} the current station list as they may see it. Server-side; best-effort. */
    public static void send(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        ModNetwork.sendToPlayer(player, StationSyncPayload.of(StationRegistry.get(server), player));
    }

    /** Per-player server tick: re-sends the list on entering the station dimension (any way in). */
    public static void tick(ServerPlayer player) {
        ResourceKey<Level> now = player.level().dimension();
        ResourceKey<Level> before = LAST_DIM.put(player, now);
        if (entered(before, now, ModDimensions.STATION_LEVEL)) {
            send(player);
        }
    }

    /** Pure rule: the player is in {@code target} now and was not last tick (or is newly seen there). */
    public static <T> boolean entered(@Nullable T before, T now, T target) {
        return target.equals(now) && !now.equals(before);
    }
}
