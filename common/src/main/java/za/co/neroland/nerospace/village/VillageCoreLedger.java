package za.co.neroland.nerospace.village;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import za.co.neroland.nerolandcore.data.SavedDataCompat;
import za.co.neroland.nerolandcore.data.SavedDataRecovery;

import za.co.neroland.nerospace.NerospaceCommon;

/**
 * Where the claimed Village Cores are, so a POPIA/GDPR erasure can reach a core whose chunk is not loaded
 * ({@code nerospace:village_cores}, overworld data folder).
 *
 * <h2>What it holds</h2>
 * <ul>
 *   <li><b>{@code cores}</b> — one row per <em>claimed</em> core seen on this world since 2.0: dimension id, block
 *       position, and an <b>owner key</b>. The key is a salted SHA-256 of the owner's UUID (first 16 bytes, hex)
 *       with a random per-world salt, so this file never holds a raw UUID or a name. That makes the key
 *       <b>pseudonymised, not anonymous</b>: the salt sits in this same file, so whoever runs this server can test
 *       whether a known UUID matches a key (nobody can recover a UUID from a key). The file is therefore personal
 *       data and is treated as such (listed in PRIVACY.md, erased, backed up on erasure). A row is removed when
 *       the core is unclaimed, broken or erased.</li>
 *   <li><b>{@code pending}</b> — positions of cores whose owner was erased while the chunk was unloaded. Position
 *       only: no owner, no key. The owner is cleared the moment the core loads again and the entry is dropped at
 *       once. Bounded to {@value #MAX_PENDING} entries (oldest dropped first).</li>
 *   <li><b>{@code legacy}</b> — owner keys (the same pseudonymised hash) of erased players, kept only to finish
 *       their erasure where a loaded-only sweep cannot reach: a 1.x core that has not loaded even once on 2.0 (so
 *       it is in no row), and an Alien Villager in an unloaded chunk whose reputation map still holds the player
 *       ({@link #isErased}), or a Mars rover in an unloaded chunk that still records the player as its owner. Kept only
 *       for a player who had visited a planet with villages, or (2.1) who had reached Mars or still owned a loaded
 *       rover when erased ({@link #keepErasureKey}). Bounded to
 *       {@value #MAX_LEGACY} entries and expires after {@value #LEGACY_TTL_DAYS} days. Never exported, never
 *       logged.</li>
 * </ul>
 *
 * <p>Cap evictions (index, pending, legacy) and a regenerated salt are logged as a count-only WARN, never with a
 * position or key, so an operator can see when erasure coverage has degraded.</p>
 *
 * <p>Every method here is pure bookkeeping (no world access), so the erasure path is unit-testable.</p>
 */
public final class VillageCoreLedger extends SavedData {

    private static final Logger LOGGER = LoggerFactory.getLogger("Nerospace");

    public static final Identifier ID = Identifier.fromNamespaceAndPath(NerospaceCommon.MOD_ID, "village_cores");
    public static final String RECOVERY_NAME = "nerospace:village_cores";

    /** Hard cap on indexed cores; a world with more claimed cores than this stops indexing new ones. */
    public static final int MAX_CORES = 8192;
    /** Hard cap on pending position-only clears. */
    public static final int MAX_PENDING = 4096;
    /** Hard cap on legacy owner keys. */
    public static final int MAX_LEGACY = 256;
    /** A legacy owner key is dropped after this many days. */
    public static final long LEGACY_TTL_DAYS = 30;

    public static final SavedDataType<VillageCoreLedger> TYPE =
            SavedDataCompat.type(ID, VillageCoreLedger::new, codec());

    /** A core's location: dimension id + {@code BlockPos.asLong()}. */
    public record CoreLoc(String dim, long pos) {
        static final Codec<CoreLoc> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("dim").forGetter(CoreLoc::dim),
                Codec.LONG.fieldOf("pos").forGetter(CoreLoc::pos)
        ).apply(i, CoreLoc::new));
    }

    private record Row(CoreLoc loc, String owner) {
        static final Codec<Row> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("dim").forGetter(r -> r.loc().dim()),
                Codec.LONG.fieldOf("pos").forGetter(r -> r.loc().pos()),
                Codec.STRING.fieldOf("owner_key").forGetter(Row::owner)
        ).apply(i, (d, p, o) -> new Row(new CoreLoc(d, p), o)));
    }

    private record Pending(CoreLoc loc, long since) {
        static final Codec<Pending> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("dim").forGetter(r -> r.loc().dim()),
                Codec.LONG.fieldOf("pos").forGetter(r -> r.loc().pos()),
                Codec.LONG.optionalFieldOf("since", 0L).forGetter(Pending::since)
        ).apply(i, (d, p, s) -> new Pending(new CoreLoc(d, p), s)));
    }

    private record Legacy(String owner, long since) {
        static final Codec<Legacy> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("owner_key").forGetter(Legacy::owner),
                Codec.LONG.optionalFieldOf("since", 0L).forGetter(Legacy::since)
        ).apply(i, Legacy::new));
    }

    private long salt;
    private final Map<CoreLoc, String> cores = new LinkedHashMap<>();
    private final Map<CoreLoc, Long> pending = new LinkedHashMap<>();
    private final Map<String, Long> legacy = new LinkedHashMap<>();
    /** Latch: the index-cap WARN is logged once per load of this file, not on every core load. */
    private boolean indexCapWarned;

    public VillageCoreLedger() {
        this.salt = newSalt();
    }

    public static VillageCoreLedger get(MinecraftServer server) {
        return SavedDataRecovery.get(server.overworld(), TYPE, VillageCoreLedger::new, RECOVERY_NAME);
    }

    /** Pushes the current content into Core's last-known-good backup at once (erasure path). */
    public static void backupNow(MinecraftServer server, VillageCoreLedger ledger) {
        SavedDataRecovery.backupNow(server.overworld(), TYPE, ledger, RECOVERY_NAME);
    }

    private static Codec<VillageCoreLedger> codec() {
        return RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.optionalFieldOf("salt", 0L).forGetter(l -> l.salt),
                Row.CODEC.listOf().optionalFieldOf("cores", List.of()).forGetter(VillageCoreLedger::rows),
                Pending.CODEC.listOf().optionalFieldOf("pending", List.of()).forGetter(VillageCoreLedger::pendingRows),
                Legacy.CODEC.listOf().optionalFieldOf("legacy", List.of()).forGetter(VillageCoreLedger::legacyRows)
        ).apply(i, VillageCoreLedger::of));
    }

    private static VillageCoreLedger of(long salt, List<Row> rows, List<Pending> pending, List<Legacy> legacy) {
        VillageCoreLedger l = new VillageCoreLedger();
        pending.forEach(p -> l.pending.put(p.loc(), p.since()));
        if (salt != 0L) {
            l.salt = salt;
            rows.forEach(r -> l.cores.put(r.loc(), r.owner()));
            legacy.forEach(g -> l.legacy.put(g.owner(), g.since()));
        } else {
            // A fresh salt was generated: keys made with the lost salt can never match again, so they are dead
            // pseudonymised data. Drop them; rows rebuild themselves as cores load. Pending entries are
            // position-only and stay valid.
            l.setDirty();
            if (!rows.isEmpty() || !legacy.isEmpty()) {
                LOGGER.warn("[Nerospace] Village Core ledger had no salt; regenerated it and dropped {} stale "
                        + "index row(s) and {} stale erasure key(s).", rows.size(), legacy.size());
            }
        }
        return l;
    }

    private List<Row> rows() {
        List<Row> out = new ArrayList<>(this.cores.size());
        this.cores.forEach((loc, key) -> out.add(new Row(loc, key)));
        return out;
    }

    private List<Pending> pendingRows() {
        List<Pending> out = new ArrayList<>(this.pending.size());
        this.pending.forEach((loc, since) -> out.add(new Pending(loc, since)));
        return out;
    }

    private List<Legacy> legacyRows() {
        List<Legacy> out = new ArrayList<>(this.legacy.size());
        this.legacy.forEach((key, since) -> out.add(new Legacy(key, since)));
        return out;
    }

    private static long newSalt() {
        long s;
        do {
            s = new SecureRandom().nextLong();
        } while (s == 0L);
        return s;
    }

    /** The salted owner key for {@code player} on this world. Never logged, never shown. */
    public String keyOf(UUID player) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            for (int i = 7; i >= 0; i--) {
                sha.update((byte) (this.salt >>> (i * 8)));
            }
            byte[] d = sha.digest(player.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(32);
            for (int i = 0; i < 16; i++) {
                hex.append(Character.forDigit((d[i] >> 4) & 0xF, 16)).append(Character.forDigit(d[i] & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every JVM", e);
        }
    }

    // --- Index upkeep ---------------------------------------------------------------------------------

    /** Records the core at {@code loc} as claimed by {@code owner}, or forgets the row when {@code owner} is null. */
    public void record(CoreLoc loc, @Nullable UUID owner) {
        if (owner == null) {
            if (this.cores.remove(loc) != null) {
                setDirty();
            }
            return;
        }
        String key = keyOf(owner);
        if (key.equals(this.cores.get(loc))) {
            return;
        }
        if (!this.cores.containsKey(loc) && this.cores.size() >= MAX_CORES) {
            // bounded: the session latch and the loaded-core sweep still cover this core
            if (!this.indexCapWarned) {
                this.indexCapWarned = true;
                LOGGER.warn("[Nerospace] Village Core index is full ({} cores); new claims are not indexed, so "
                        + "erasure reaches them only while loaded.", MAX_CORES);
            }
            return;
        }
        this.cores.put(loc, key);
        setDirty();
    }

    /** The core at {@code loc} was broken: drop its row and any pending clear. */
    public void remove(CoreLoc loc) {
        boolean changed = this.cores.remove(loc) != null;
        changed |= this.pending.remove(loc) != null;
        if (changed) {
            setDirty();
        }
    }

    /**
     * A core has just loaded (or was found loaded). Applies a pending clear (and drops that entry), or a legacy
     * owner-key match, then refreshes the row.
     *
     * @return {@code true} when the caller must clear the core's owner now
     */
    public boolean onLoaded(CoreLoc loc, @Nullable UUID owner, long today) {
        prune(today);
        boolean clear = false;
        if (this.pending.remove(loc) != null) {
            clear = owner != null;
            setDirty();
        } else if (owner != null && !this.cores.containsKey(loc) && this.legacy.containsKey(keyOf(owner))) {
            clear = true;
        }
        record(loc, clear ? null : owner);
        return clear;
    }

    // --- Erasure --------------------------------------------------------------------------------------

    /**
     * Erasure: drops every row owned by {@code player}. A row whose core was NOT cleared just now (unloaded chunk)
     * becomes a position-only pending clear. A legacy owner key is kept (bounded, TTL) for 1.x cores never seen on
     * 2.0.
     *
     * @param clearedNow which locations were loaded and already cleared by the live sweep
     * @param visitedVillagePlanet whether the player had visited a planet with villages (read from the visit store
     *        before it is erased): only then is a legacy owner key kept, because only such a player can own a 1.x
     *        core or hold villager reputation; anyone else (Core's isolation canary, the retention sweep, a player
     *        who never reached a village planet) leaves nothing behind
     * @return how many pending clears were added
     */
    public int forgetOwner(UUID player, Predicate<CoreLoc> clearedNow, long today, boolean visitedVillagePlanet) {
        String key = keyOf(player);
        int added = 0;
        Iterator<Map.Entry<CoreLoc, String>> it = this.cores.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<CoreLoc, String> e = it.next();
            if (!key.equals(e.getValue())) {
                continue;
            }
            it.remove();
            if (!clearedNow.test(e.getKey())) {
                this.pending.put(e.getKey(), today);
                added++;
            }
        }
        int evicted = 0;
        while (this.pending.size() > MAX_PENDING) {
            Iterator<CoreLoc> oldest = this.pending.keySet().iterator();
            oldest.next();
            oldest.remove();
            evicted++;
        }
        if (evicted > 0) {
            LOGGER.warn("[Nerospace] Village Core pending-erasure list is full ({}); dropped the {} oldest "
                    + "entries.", MAX_PENDING, evicted);
        }
        this.legacy.remove(key);
        if (visitedVillagePlanet) {
            this.legacy.put(key, today);
        }
        prune(today);
        setDirty();
        return added;
    }

    /**
     * Erasure (2.1, rovers): keeps {@code player}'s salted key in the bounded, TTL'd legacy set so a rover that is
     * unloaded now and first loads after a restart still drops its owner ({@link #isErased}). The same key, cap and
     * 30-day expiry as the village rule; nothing else is stored. Call after {@link #forgetOwner}.
     */
    public void keepErasureKey(UUID player, long today) {
        if (player == null) {
            return;
        }
        this.legacy.put(keyOf(player), today);
        prune(today);
        setDirty();
    }

    /** Drops expired legacy keys and enforces the legacy cap. */
    public void prune(long today) {
        boolean changed = this.legacy.values().removeIf(since -> today - since > LEGACY_TTL_DAYS);
        int evicted = 0;
        while (this.legacy.size() > MAX_LEGACY) {
            Iterator<String> oldest = this.legacy.keySet().iterator();
            oldest.next();
            oldest.remove();
            evicted++;
        }
        if (evicted > 0) {
            changed = true;
            LOGGER.warn("[Nerospace] Village Core erasure-key list is full ({}); dropped the {} oldest "
                    + "entries.", MAX_LEGACY, evicted);
        }
        if (changed) {
            setDirty();
        }
    }

    /**
     * Whether {@code player} is an erased player whose salted key is still in the bounded, TTL'd legacy set. Read
     * by an Alien Villager as it loads, to drop that player's reputation entry. Read-only: no new data is stored.
     */
    public boolean isErased(@Nullable UUID player, long today) {
        if (player == null || this.legacy.isEmpty()) {
            return false;
        }
        Long since = this.legacy.get(keyOf(player));
        return since != null && today - since <= LEGACY_TTL_DAYS;
    }

    /** Conformance probe: whether any indexed row still points at {@code player}. */
    public boolean retainsData(UUID player) {
        return this.cores.containsValue(keyOf(player));
    }

    /** Privacy export: the indexed cores {@code player} owns (positions only). */
    public List<CoreLoc> ownedBy(UUID player) {
        String key = keyOf(player);
        List<CoreLoc> out = new ArrayList<>();
        this.cores.forEach((loc, k) -> {
            if (key.equals(k)) {
                out.add(loc);
            }
        });
        return out;
    }

    public boolean isPending(CoreLoc loc) {
        return this.pending.containsKey(loc);
    }

    public int pendingCount() {
        return this.pending.size();
    }

    public int legacyCount() {
        return this.legacy.size();
    }

    public int indexedCount() {
        return this.cores.size();
    }

    /** Round-trip helper for tests. */
    public static Codec<VillageCoreLedger> storeCodec() {
        return codec();
    }
}
