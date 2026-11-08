package za.co.neroland.nerospace.link;

import java.util.List;

import za.co.neroland.nerolandcore.link.LinkModuleInfo;
import za.co.neroland.nerolandcore.link.NeroLinkRegistry;

import za.co.neroland.nerospace.NerospaceCommon;
import za.co.neroland.nerospace.config.NerospaceConfig;
import za.co.neroland.nerospace.platform.Services;

/**
 * Nerospace's NeroLink module — the declaration half. Every section id, action id and event topic the
 * mod exposes is a named constant here, and {@link #init()} is the single registration entry point that
 * {@code NerospaceCommon.init()} calls last.
 *
 * <p>Nerospace backs the companion app's "space" screen: rockets and pad readiness, the player's own
 * orbital stations, planet traits and which worlds they have reached, life support, and Star Guide
 * progress, plus the public in-app wiki. Until this module existed the bridge reported
 * {@code nerospace: absent} and that screen had nothing to draw.</p>
 *
 * <p><b>POPIA/GDPR — the visibility rule.</b> Every section is scoped to the <em>requesting</em> player's
 * UUID and nothing else:</p>
 * <ul>
 *   <li>{@link #SECTION_STATIONS} lists only stations whose stored owner UUID equals the requester's or
 *       whose member list contains it. A station the requester neither owns nor belongs to is
 *       indistinguishable from one that does not exist.</li>
 *   <li>{@link #SECTION_LIFE_SUPPORT} and {@link #SECTION_STAR_GUIDE} read the requester's own attachment
 *       and advancement state through a live {@code ServerPlayer} handle — there is no path to another
 *       player's.</li>
 *   <li>{@link #SECTION_ROCKETS} is the one section over unowned world content. Rockets and launch pads
 *       carry no owner, so instead of a server-wide roster (which would map every base on the server) it
 *       answers only what the requester could already see by standing where they are: the rocket they
 *       ride plus rockets and pads inside {@code NerospaceLinkAccess.SCAN_RADIUS} blocks of them, in
 *       their own dimension, while online. Offline it answers nothing.</li>
 *   <li>{@link #SECTION_PLANETS} is public world data (gravity, airless, hazard); only the per-planet
 *       "reached" flag is player-scoped, and it comes from the requester's own advancements.</li>
 *   <li>{@link #SECTION_ROUTE} lists only Cargo Pads whose stored owner UUID equals the requester's, and
 *       only flights they own. Pads on someone else's access list are not listed — that would name the
 *       granter's base. No new actions in this release.</li>
 *   <li>{@link #SECTION_WIKI} is public: the bundled {@code wiki/} pages, the same for every player. It
 *       ignores the requester's UUID and holds no personal data.</li>
 * </ul>
 *
 * <p>No section ever emits another player's UUID or name. Where a shared registry is involved, the module
 * answers with a count (station slots used out of the cap), never a roster. Operator status is
 * deliberately not honoured: it is a property of a live command source, not of a UUID arriving over a
 * bridge.</p>
 *
 * @see NerospaceLinkSnapshots
 * @see NerospaceLinkActions
 * @see NerospaceLinkEvents
 */
public final class NerospaceLinkModule {

    /** The link module id — the same string as the mod id, as the ecosystem convention requires. */
    public static final String MODULE_ID = NerospaceCommon.MOD_ID;

    /**
     * The snapshot schema revision. Bump on any change to a section's shape. v3 (2.1): rockets rows gain
     * {@code crew_count} (additive). v4 (2.1): the rockets section gains a {@code rovers} array — only the requester's
     * own rovers, in the same proximity scan (additive).
     */
    public static final int SCHEMA_VERSION = 4;

    /** Rockets the player can see from where they stand, with tier/fuel/state + pad readiness. */
    public static final String SECTION_ROCKETS = "rockets";
    /** Orbital stations the requester owns or is a member of. Never one they have no part in. */
    public static final String SECTION_STATIONS = "stations";
    /** Planet traits (public) plus which of them the requester has reached (player-scoped). */
    public static final String SECTION_PLANETS = "planets";
    /** The requester's own oxygen tank, suit tier and hazard shield. */
    public static final String SECTION_LIFE_SUPPORT = "life_support";
    /** The requester's own Star Guide chapter/step progress. */
    public static final String SECTION_STAR_GUIDE = "star_guide";
    /** The requester's own Cargo Pads and cargo flights, by owner UUID. Never another player's pad. */
    public static final String SECTION_ROUTE = "route";
    /** PUBLIC: the wiki page index, or one page's markdown with {@code page=<slug>}. Ignores the requester. */
    public static final String SECTION_WIKI = "wiki";

    /** Rename a station the requester owns. Owner-gated, online-required, no world mutation beyond the name. */
    public static final String ACTION_RENAME_STATION = "rename_station";
    /** Acknowledge one of the requester's own Core alerts. Safe offline. */
    public static final String ACTION_ACKNOWLEDGE_ALERT = "acknowledge_alert";

    /** Owner-scoped: the player's air is running out on an airless world. */
    public static final String TOPIC_OXYGEN_LOW = "oxygen_low";
    /** Owner-scoped: the player's rocket completed a trip and set down. */
    public static final String TOPIC_ROCKET_LANDED = "rocket_landed";
    /** BROADCAST: a station slot changed state. Carries a slot number, a dimension and a state — nothing else. */
    public static final String TOPIC_STATION_STATE = "station_state";

    private NerospaceLinkModule() {
    }

    /**
     * Registers the snapshot provider and the action handler with Core's link registry, and arms the event
     * publisher. Wrapped end to end: a link module that cannot register must never take Nerospace down with
     * it — the worst outcome is a companion app that reports Nerospace as absent.
     */
    public static void init() {
        try {
            if (!NerospaceConfig.linkModuleEnabled()) {
                NerospaceCommon.LOGGER.info(
                        "[Nerospace] The NeroLink module is disabled by config; companion clients will not "
                                + "see Nerospace data.");
                return;
            }
            LinkModuleInfo info = new LinkModuleInfo(MODULE_ID, modVersion(), SCHEMA_VERSION,
                    List.of(SECTION_ROCKETS, SECTION_STATIONS, SECTION_PLANETS, SECTION_LIFE_SUPPORT,
                            SECTION_STAR_GUIDE, SECTION_ROUTE, SECTION_WIKI),
                    List.of(ACTION_RENAME_STATION, ACTION_ACKNOWLEDGE_ALERT));
            // One provider and one handler cover the whole module; Core keys both on the module id.
            NeroLinkRegistry.registerSnapshotProvider(new NerospaceLinkSnapshots(), info);
            NeroLinkRegistry.registerActionHandler(new NerospaceLinkActions(), info);
            NerospaceLinkEvents.init();
            NerospaceCommon.LOGGER.info("[Nerospace] NeroLink module registered (schema v{}).",
                    SCHEMA_VERSION);
        } catch (RuntimeException | LinkageError e) {
            // LinkageError too: an older Neroland Core without the link package would otherwise abort mod
            // construction with NoClassDefFoundError rather than merely losing the companion integration.
            NerospaceCommon.LOGGER.warn(
                    "[Nerospace] Could not register the NeroLink module; companion clients will not see "
                            + "Nerospace data. Rockets, stations and life support are unaffected.", e);
        }
    }

    /**
     * Captures the running server so snapshots and actions can resolve players and SavedData. Core's SPI
     * hands a provider nothing but a UUID, so the module needs its own handle; each loader's server-tick
     * hook calls this beside the other per-tick drivers. Cheap (one volatile write) and idempotent.
     */
    public static void rememberServer(net.minecraft.server.MinecraftServer runningServer) {
        NerospaceLinkAccess.rememberServer(runningServer);
    }

    /** This mod's public version string for discovery, or {@code "unknown"} if the seam is unhappy. */
    private static String modVersion() {
        try {
            String version = Services.PLATFORM.getModVersion();
            return version == null || version.isBlank() ? "unknown" : version;
        } catch (RuntimeException e) {
            return "unknown";
        }
    }
}
