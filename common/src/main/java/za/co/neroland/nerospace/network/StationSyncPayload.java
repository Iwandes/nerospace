package za.co.neroland.nerospace.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import za.co.neroland.nerospace.NerospaceCommon;
import za.co.neroland.nerospace.rocket.DockSelection;
import za.co.neroland.nerospace.rocket.StationDockingRule;
import za.co.neroland.nerospace.rocket.StationRegistry;

/**
 * Server → client snapshot of the founded stations' display names (slot → name), pushed to a player
 * when they open a rocket or a Launch Controller, when they found, rename or abandon a station, and when they enter the
 * station dimension, so the "Dock at" list shows the real charter names without a relog.
 * {@link net.minecraft.world.inventory.ContainerData} is int-only, so the names can't ride the menu's synced data —
 * this small parallel-array payload carries them instead.
 *
 * <p><b>Privacy (POPIA/GDPR):</b> carries only the station <em>slot</em>, its <em>display name</em> (chosen by whoever
 * founded it) and the <em>viewer's own</em> relation to it ({@link DockSelection.Relation}: yours / member / public).
 * No player identity — no names, UUIDs, founders, member lists or positions — is ever sent, and a {@code MEMBERS}
 * station the viewer cannot use is left out entirely. The relation tells a viewer only what the Station Console
 * already shows them about their own access.</p>
 *
 * <p>Cross-loader note: registered (with its client handler {@code ClientStations::accept}) in
 * {@code ModNetwork.init()}; both loaders' networking seams pick it up from the clientbound list.</p>
 */
public record StationSyncPayload(int[] slots, String[] names, byte[] relations) implements CustomPacketPayload {

    public static final Type<StationSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(NerospaceCommon.MOD_ID, "station_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StationSyncPayload> STREAM_CODEC =
            StreamCodec.of(StationSyncPayload::write, StationSyncPayload::read);

    /** 2.0 shape: names only, every relation {@link DockSelection.Relation#PUBLIC}. */
    public StationSyncPayload(int[] slots, String[] names) {
        this(slots, names, new byte[slots.length]);
    }

    /** Snapshot every station in founding order (no viewer filter — server-side tools only). */
    public static StationSyncPayload of(StationRegistry registry) {
        return of(registry.all(), e -> true);
    }

    /**
     * Snapshot for one viewer: a {@code MEMBERS}-policy station the viewer cannot {@code canUse} is left out, so its
     * name never reaches a player who may not dock there (the server cycler skips it anyway); each row carries the
     * viewer's own relation to it.
     */
    public static StationSyncPayload of(StationRegistry registry, net.minecraft.server.level.ServerPlayer viewer) {
        String me = viewer.getUUID().toString();
        return of(registry.all(), e -> StationDockingRule.mayChoose(e.docking(), StationRegistry.canUse(e, viewer)),
                e -> DockSelection.relation(e, me));
    }

    /** Pure core of {@link #of(StationRegistry, net.minecraft.server.level.ServerPlayer)}: keeps the visible rows. */
    public static StationSyncPayload of(List<StationRegistry.StationEntry> all,
            Predicate<StationRegistry.StationEntry> visible) {
        return of(all, visible, e -> DockSelection.Relation.PUBLIC);
    }

    /** Pure core with the viewer's relation per row. */
    public static StationSyncPayload of(List<StationRegistry.StationEntry> all,
            Predicate<StationRegistry.StationEntry> visible,
            Function<StationRegistry.StationEntry, DockSelection.Relation> relation) {
        List<StationRegistry.StationEntry> shown = new ArrayList<>();
        for (StationRegistry.StationEntry e : all) {
            if (visible.test(e)) {
                shown.add(e);
            }
        }
        int[] slots = new int[shown.size()];
        String[] names = new String[shown.size()];
        byte[] relations = new byte[shown.size()];
        for (int i = 0; i < shown.size(); i++) {
            slots[i] = shown.get(i).slot();
            names[i] = shown.get(i).name();
            relations[i] = (byte) relation.apply(shown.get(i)).ordinal();
        }
        return new StationSyncPayload(slots, names, relations);
    }

    /** The viewer's relation to row {@code i}. */
    public DockSelection.Relation relation(int i) {
        return i >= 0 && i < this.relations.length ? DockSelection.Relation.byOrdinal(this.relations[i])
                : DockSelection.Relation.PUBLIC;
    }

    private static void write(RegistryFriendlyByteBuf buf, StationSyncPayload payload) {
        buf.writeVarInt(payload.slots.length);
        for (int i = 0; i < payload.slots.length; i++) {
            buf.writeVarInt(payload.slots[i]);
            buf.writeUtf(payload.names[i]);
            buf.writeByte(i < payload.relations.length ? payload.relations[i] : 0);
        }
    }

    private static StationSyncPayload read(RegistryFriendlyByteBuf buf) {
        int n = buf.readVarInt();
        int[] slots = new int[n];
        String[] names = new String[n];
        byte[] relations = new byte[n];
        for (int i = 0; i < n; i++) {
            slots[i] = buf.readVarInt();
            names[i] = buf.readUtf();
            relations[i] = buf.readByte();
        }
        return new StationSyncPayload(slots, names, relations);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
