package za.co.neroland.nerospace.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.nerospace.platform.Services;

/**
 * Cross-loader networking registry. Subsystems declare their payloads here once (type + stream codec +
 * handler); each loader iterates these lists and wires them to its own networking API (NeoForge
 * {@code PayloadRegistrar} during {@code RegisterPayloadHandlersEvent}; Fabric
 * {@code PayloadTypeRegistry.clientboundPlay()/serverboundPlay()} + {@code Server|ClientPlayNetworking}
 * receivers). Sending goes through the {@link Services#NETWORK} seam.
 *
 * <p><b>Client-safety contract.</b> A clientbound {@link Clientbound#handler()} runs on the physical
 * client; register it from client-reachable code only and do not let its method statically load a
 * client-only class on a dedicated server before the handler actually runs. (No payloads are registered
 * yet — the consuming subsystems — oxygen field, meteors, pipe modes — add theirs here as they are ported.)</p>
 */
public final class ModNetwork {

    /** A server → client payload + the client-side handler that consumes it. */
    public record Clientbound<T extends CustomPacketPayload>(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            Consumer<T> handler) {
    }

    /** A client → server payload + the server-side handler (with the sending player). */
    public record Serverbound<T extends CustomPacketPayload>(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            BiConsumer<T, ServerPlayer> handler) {
    }

    private static final List<Clientbound<?>> CLIENTBOUND = new ArrayList<>();
    private static final List<Serverbound<?>> SERVERBOUND = new ArrayList<>();

    private ModNetwork() {
    }

    public static <T extends CustomPacketPayload> void clientbound(
            CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, Consumer<T> handler) {
        CLIENTBOUND.add(new Clientbound<>(type, codec, handler));
    }

    public static <T extends CustomPacketPayload> void serverbound(
            CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, BiConsumer<T, ServerPlayer> handler) {
        SERVERBOUND.add(new Serverbound<>(type, codec, handler));
    }

    public static List<Clientbound<?>> clientbound() {
        return CLIENTBOUND;
    }

    public static List<Serverbound<?>> serverbound() {
        return SERVERBOUND;
    }

    /** Server → one client. */
    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        Services.NETWORK.sendToPlayer(player, payload);
    }

    /** Client → server (call only on the physical client). */
    public static void sendToServer(CustomPacketPayload payload) {
        Services.NETWORK.sendToServer(payload);
    }

    /** Called from common init so the payload lists are populated before each loader registers them. */
    public static void init() {
        // Meteor Tracker: server → tracker-holders nearest-site snapshot. The handler runs only on the
        // physical client; ClientMeteorTracker is a pure data holder (no client-only imports), so the
        // method reference is safe to register from common code.
        clientbound(MeteorSyncPayload.TYPE, MeteorSyncPayload.STREAM_CODEC,
                za.co.neroland.nerospace.client.ClientMeteorTracker::accept);
        // Oxygen field: server → nearby clients range-limited concentration snapshot for the visual layers.
        clientbound(OxygenFieldSyncPayload.TYPE, OxygenFieldSyncPayload.STREAM_CODEC,
                za.co.neroland.nerospace.client.ClientOxygenField::accept);
        // Founded-station names: server → a player opening a rocket, so the "Dock:" cycler shows real names.
        clientbound(StationSyncPayload.TYPE, StationSyncPayload.STREAM_CODEC,
                za.co.neroland.nerospace.client.ClientStations::accept);
        // Free docks: server → a player opening a rocket's console, the hand-built rings that rocket may pick.
        clientbound(FreeDockSyncPayload.TYPE, FreeDockSyncPayload.STREAM_CODEC,
                za.co.neroland.nerospace.client.ClientStations::acceptFreeDocks);
        // Landing-node labels: server → a player opening a rocket, so the planet card names the chosen pad.
        clientbound(PadSyncPayload.TYPE, PadSyncPayload.STREAM_CODEC,
                za.co.neroland.nerospace.client.ClientPads::accept);
        // Cargo Pad: server → the viewer of one open pad menu, the destination labels they may pick from.
        clientbound(CargoPadSyncPayload.TYPE, CargoPadSyncPayload.STREAM_CODEC,
                za.co.neroland.nerospace.client.ClientCargoPads::accept);
        // Planet clock + weather: server → the player's client, render-only (sky, HUD dial). Pure data holders.
        clientbound(PlanetClockPayload.TYPE, PlanetClockPayload.STREAM_CODEC,
                za.co.neroland.nerospace.client.ClientPlanetClock::accept);
        clientbound(PlanetWeatherPayload.TYPE, PlanetWeatherPayload.STREAM_CODEC,
                za.co.neroland.nerospace.client.ClientPlanetWeather::accept);
        // Station Charter: client (naming screen) → server, founds the named station without teleporting.
        serverbound(FoundStationPayload.TYPE, FoundStationPayload.STREAM_CODEC,
                (payload, player) -> za.co.neroland.nerospace.item.StationCharterItem.foundFromUi(player, payload.name(),
                        payload.claimHere(), payload.buildStarter()));
        // Station Core rename: client (rename screen) → server, renames the station by slot.
        serverbound(RenameStationPayload.TYPE, RenameStationPayload.STREAM_CODEC,
                (payload, player) -> za.co.neroland.nerospace.item.StationCharterItem.renameStation(
                        player, payload.slot(), payload.name()));
        // Station Console: server -> the console viewer (UUIDs only to canUse viewers; names resolved client-side).
        clientbound(StationConsoleSyncPayload.TYPE, StationConsoleSyncPayload.STREAM_CODEC,
                za.co.neroland.nerospace.client.ClientStationConsole::accept);
        // Station Console: client -> server, add an online player (picked from the tab list) as a member.
        serverbound(StationMemberPayload.TYPE, StationMemberPayload.STREAM_CODEC,
                (payload, player) -> za.co.neroland.nerospace.menu.StationConsoleMenu.addMember(
                        player, payload.slot(), payload.uuid()));
    }
}
