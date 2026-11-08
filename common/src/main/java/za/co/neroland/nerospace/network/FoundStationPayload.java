package za.co.neroland.nerospace.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import za.co.neroland.nerospace.NerospaceCommon;

/**
 * Client → server: the station name typed into the Station Charter naming screen. The server founds a new
 * station with that name (consuming one charter) — without teleporting the player, so the rocket remains
 * the way to actually travel there. Registered (with its server handler) in {@code ModNetwork.init()}.
 *
 * <p><b>2.1, appended and optional:</b> {@code claimHere} (the screen was opened in the station dimension, so found
 * where the player stands — the server re-checks the dimension and takes the position from the player, never from
 * the packet) and {@code buildStarter} (the "Build starter structure" box). A 2.0 packet carries neither and reads
 * as {@code false, true}: the old next-free-slot founding with the full structure.</p>
 *
 * <p><b>Privacy (POPIA/GDPR):</b> carries only the player-chosen station name and two flags — no identity, no
 * position.</p>
 */
public record FoundStationPayload(String name, boolean claimHere, boolean buildStarter) implements CustomPacketPayload {

    public static final Type<FoundStationPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(NerospaceCommon.MOD_ID, "found_station"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FoundStationPayload> STREAM_CODEC =
            StreamCodec.of(FoundStationPayload::write, FoundStationPayload::read);

    /** The 2.0 shape: auto slot, full structure. */
    public FoundStationPayload(String name) {
        this(name, false, true);
    }

    /** Wire format (public for the plain-JVM test): the 2.0 name, then the two appended flags. */
    public static void write(FriendlyByteBuf buf, FoundStationPayload payload) {
        buf.writeUtf(payload.name, 64);
        buf.writeBoolean(payload.claimHere);
        buf.writeBoolean(payload.buildStarter);
    }

    /** Reads the 2.0 name; each appended flag only when present, else its default. */
    public static FoundStationPayload read(FriendlyByteBuf buf) {
        String name = buf.readUtf(64);
        boolean claimHere = buf.isReadable() && buf.readBoolean();
        boolean buildStarter = !buf.isReadable() || buf.readBoolean();
        return new FoundStationPayload(name, claimHere, buildStarter);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
