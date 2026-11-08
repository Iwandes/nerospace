package za.co.neroland.nerospace.network;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import za.co.neroland.nerospace.NerospaceCommon;
import za.co.neroland.nerospace.rocket.FreeDocks;

/**
 * Server → client: the free docks the opened rocket may pick, in its list order (the index is what a
 * {@code 600 + i} select button names). Sent with the station and pad labels when a console opens.
 *
 * <p><b>Privacy (POPIA/GDPR).</b> No player identity: free docks have no owner. <b>No position is ever sent</b>: a
 * ring someone named sends its name, a ring nobody named sends nothing but its list place, and the screens show it as
 * "Free dock N (D m)". The list only holds rings within {@code stations.freeDockRadius} of where this rocket last
 * stood in the station dimension (never a server-wide list); distances are rounded to 10 blocks. (2.1 review: the
 * first draft sent an unnamed ring's X and Z, which let anyone with a rocket list the exact coordinates of hand-built
 * docks up to 2 048 blocks away, far past render distance, without flying there.)</p>
 */
public record FreeDockSyncPayload(List<Row> rows) implements CustomPacketPayload {

    /** One list row: a name (empty when unnamed) and the rounded distance from the rocket's anchor. No position. */
    public record Row(String name, boolean named, int distance) {
    }

    public static final Type<FreeDockSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(NerospaceCommon.MOD_ID, "free_dock_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FreeDockSyncPayload> STREAM_CODEC =
            StreamCodec.of(FreeDockSyncPayload::write, FreeDockSyncPayload::read);

    public FreeDockSyncPayload {
        rows = rows == null ? List.of() : List.copyOf(rows.subList(0, Math.min(rows.size(), FreeDocks.MAX_VISIBLE)));
    }

    /** The rows for {@code docks} (already capped and ordered by {@link FreeDocks#visible}). */
    public static FreeDockSyncPayload of(List<FreeDocks.Dock> docks) {
        List<Row> rows = new ArrayList<>(docks.size());
        for (FreeDocks.Dock d : docks) {
            rows.add(d.named() ? new Row(d.name(), true, d.distance()) : new Row("", false, d.distance()));
        }
        return new FreeDockSyncPayload(rows);
    }

    /** Wire format (public for the plain-JVM test): count, then per row named, [name if named], distance + 1. */
    public static void write(FriendlyByteBuf buf, FreeDockSyncPayload payload) {
        buf.writeVarInt(payload.rows.size());
        for (Row row : payload.rows) {
            buf.writeBoolean(row.named());
            if (row.named()) {
                buf.writeUtf(row.name(), 64);
            }
            buf.writeVarInt(Math.max(-1, row.distance()) + 1);
        }
    }

    public static FreeDockSyncPayload read(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), FreeDocks.MAX_VISIBLE);
        List<Row> rows = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            boolean named = buf.readBoolean();
            String name = named ? buf.readUtf(64) : "";
            rows.add(new Row(name, named, buf.readVarInt() - 1));
        }
        return new FreeDockSyncPayload(rows);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
