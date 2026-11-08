package za.co.neroland.nerospace.network;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import za.co.neroland.nerospace.NerospaceCommon;
import za.co.neroland.nerospace.rocket.PadRegistry;
import za.co.neroland.nerospace.rocket.PadSelection;
import za.co.neroland.nerospace.rocket.RocketTravel;

/**
 * Server → client snapshot of the selectable landing nodes' labels (dimension id + name, in the
 * {@link PadRegistry#inDimension} order the Rocket screen's pad index addresses), pushed when a player opens a
 * rocket or a Launch Controller so the "Land at" list can say "Mare Base".
 *
 * <p><b>Privacy (POPIA/GDPR):</b> pads carry no player identity; only the dimension id, the label and — for pads in
 * the rocket's <em>own</em> dimension only — the distance from that rocket, rounded to 10 blocks
 * ({@link PadSelection#roundedDistance}), travel. No pad position is sent. The distance says no more than the trip
 * fuel cost the console already shows for each pad you select (same-dimension hops are priced by distance); pads in
 * other dimensions carry {@code -1}.</p>
 */
public record PadSyncPayload(List<String> dims, List<String> names, List<Integer> distances)
        implements CustomPacketPayload {

    public static final Type<PadSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(NerospaceCommon.MOD_ID, "pad_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PadSyncPayload> STREAM_CODEC =
            StreamCodec.of(PadSyncPayload::write, PadSyncPayload::read);

    /** Snapshot the selectable nodes (launch pads + docks, never pods) in commissioning order, without distances. */
    public static PadSyncPayload of(PadRegistry registry) {
        return of(registry, null, null);
    }

    /**
     * Snapshot with distances from a rocket at {@code from} in {@code fromDim} for the pads in that same dimension.
     */
    public static PadSyncPayload of(PadRegistry registry, @Nullable ResourceKey<Level> fromDim,
            @Nullable BlockPos from) {
        String here = fromDim == null ? null : PadRegistry.dimId(fromDim);
        List<String> dims = new ArrayList<>();
        List<String> names = new ArrayList<>();
        List<Integer> distances = new ArrayList<>();
        for (PadRegistry.PadNode node : registry.all()) {
            if (node.kind().selectable()) {
                dims.add(node.dim());
                names.add(node.name());
                distances.add(here != null && from != null && here.equals(node.dim())
                        ? PadSelection.roundedDistance(RocketTravel.distance(from, node.centerOrPos())) : -1);
            }
        }
        return new PadSyncPayload(dims, names, distances);
    }

    private static void write(RegistryFriendlyByteBuf buf, PadSyncPayload payload) {
        buf.writeVarInt(payload.dims.size());
        for (int i = 0; i < payload.dims.size(); i++) {
            buf.writeUtf(payload.dims.get(i));
            buf.writeUtf(payload.names.get(i));
            buf.writeVarInt(i < payload.distances.size() ? payload.distances.get(i) + 1 : 0);
        }
    }

    private static PadSyncPayload read(RegistryFriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<String> dims = new ArrayList<>(n);
        List<String> names = new ArrayList<>(n);
        List<Integer> distances = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            dims.add(buf.readUtf());
            names.add(buf.readUtf());
            distances.add(buf.readVarInt() - 1);
        }
        return new PadSyncPayload(dims, names, distances);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
