package za.co.neroland.nerospace.item;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import za.co.neroland.nerospace.config.NerospaceConfig;
import za.co.neroland.nerospace.menu.MenuOpener;
import za.co.neroland.nerospace.menu.StationCharterMenu;
import za.co.neroland.nerospace.progression.StarGuideGrants;
import za.co.neroland.nerospace.registry.ModDimensions;
import za.co.neroland.nerospace.registry.ModItems;
import za.co.neroland.nerospace.rocket.PadRegistry;
import za.co.neroland.nerospace.rocket.StationCoreBlockEntity;
import za.co.neroland.nerospace.rocket.StationFounding;
import za.co.neroland.nerospace.rocket.StationRegistry;
import za.co.neroland.nerospace.rocket.StationStructure;
import za.co.neroland.nerospace.telemetry.NerospaceTelemetry;

/**
 * The Station Charter — founds a player station. Right-click opens a naming console; type a name and
 * confirm to allocate the next station slot in the {@code nerospace:station} void dimension and build the
 * station there (enclosed room, airlock, Tier-2 landing pad, and an anchored unbreakable Station Core
 * bound to the name). It does <b>not</b> teleport you — fly a rocket to the Orbital Station to visit, so
 * the rocket stays the means of travel. The name persists with the Station Core.
 *
 * <p>The naming screen sends the name back as a {@code FoundStationPayload}; {@link #foundFromUi} does the
 * server-side founding (consuming one charter).</p>
 */
public class StationCharterItem extends Item {

    public StationCharterItem(Properties properties) {
        super(properties);
    }

    //? if >=1.21.2 {
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
    //?} else {
    /*// Before 1.21.2 Item.use returns the held stack with the result.
    @Override
    public net.minecraft.world.InteractionResultHolder<net.minecraft.world.item.ItemStack> use(Level level,
            Player player, InteractionHand hand) {
        InteractionResult result = useResult(level, player, hand);
        return new net.minecraft.world.InteractionResultHolder<>(result, player.getItemInHand(hand));
    }

    private InteractionResult useResult(Level level, Player player, InteractionHand hand) {
    *///?}
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            return MenuOpener.openOrConsume(serverPlayer, new SimpleMenuProvider(
                    (id, inv, p) -> new StationCharterMenu(id, inv),
                    Component.translatable("gui.nerospace.station_charter.title")));
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Server-side founding from the naming screen: allocate the station slot, build it, consume one
     * charter from the player's hands. No teleport. (The 2.0 entry point: next free grid slot, full structure.)
     */
    public static void foundFromUi(ServerPlayer player, String name) {
        foundFromUi(player, name, false, true);
    }

    /**
     * Server-side founding from the naming screen. With {@code claimHere} and the player in the station dimension
     * the station is founded where they stand ({@link StationFounding#snap}), subject to
     * {@link StationFounding#checkPlacement}; a refusal names its reason and <b>keeps the charter</b>. Anywhere else
     * the next free 4 096-grid slot is used, exactly as in 2.0. {@code buildStarter = false} (placed stations only)
     * founds the registry entry and its claim with just a Docking Ring; otherwise the starter structure is built.
     * The position always comes from the server-side player, never from the packet.
     */
    public static void foundFromUi(ServerPlayer player, String name, boolean claimHere, boolean buildStarter) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        ServerLevel station = server.getLevel(ModDimensions.STATION_LEVEL);
        if (station == null) {
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (!held.is(ModItems.STATION_CHARTER.get())) {
            held = player.getOffhandItem();
            if (!held.is(ModItems.STATION_CHARTER.get())) {
                return; // no charter to spend
            }
        }
        StationRegistry registry = StationRegistry.get(server);
        String trimmed = name == null ? "" : name.trim();
        String stationName = trimmed.isBlank() ? null : trimmed;
        boolean placed = claimHere && ModDimensions.STATION_LEVEL.equals(player.level().dimension());
        StationRegistry.StationEntry entry;
        if (placed) {
            BlockPos centre = StationFounding.snap(player.blockPosition());
            StationFounding.PlaceResult verdict = StationFounding.checkPlacement(centre, registry.all(),
                    NerospaceConfig.stationsMinSpacing(), NerospaceConfig.stationsMaxRadius(), registry.isFull());
            if (!StationFounding.consumesCharter(verdict.outcome())) {
                player.sendSystemMessage(refusal(verdict, player));
                NerospaceTelemetry.breadcrumb("station", "placement refused=" + verdict.outcome().name());
                return; // refused: the charter stays in hand
            }
            entry = registry.foundAt(stationName, player.getUUID().toString(), verdict.centre());
        } else {
            if (registry.isFull()) {
                player.sendSystemMessage(Component.translatable("item.nerospace.station_charter.full"));
                return;
            }
            entry = registry.found(stationName, player.getUUID().toString());
        }
        if (entry == null) {
            player.sendSystemMessage(Component.translatable("item.nerospace.station_charter.full"));
            return;
        }
        held.shrink(1);
        if (placed && !buildStarter) {
            StationStructure.buildFrame(station, entry);
        } else {
            StationStructure.build(station, entry.center());
        }
        NerospaceTelemetry.breadcrumb("station", "founded slot=" + entry.slot() + " placed=" + (placed ? 1 : 0));
        // NeroLink: a thin broadcast (slot + dimension + state only — no name, owner or position).
        za.co.neroland.nerospace.link.NerospaceLinkEvents.stationStateChanged(server, entry.slot(), "FOUNDED");
        StarGuideGrants.grant(player, "guide/station_charter");
        // Founding an off-world base opens Neroland Core's shared FIRST_COLONY gate (one-directional).
        StarGuideGrants.driveFirstColony(player);
        player.sendSystemMessage(Component.translatable("item.nerospace.station_charter.founded", entry.name()));
        // The founder's dock list shows the new station at once, without a relog (names + their relation only).
        za.co.neroland.nerospace.network.StationListSync.send(player);
    }

    /**
     * The chat line for a refused placement. A station that is too close is named only when the player could see it
     * in their dock list anyway (not a MEMBERS-only station they cannot use); otherwise it is "another station".
     */
    private static Component refusal(StationFounding.PlaceResult verdict, ServerPlayer player) {
        return switch (verdict.outcome()) {
            case TOO_CLOSE -> {
                StationRegistry.StationEntry blocker = verdict.blocker();
                Component who;
                if (blocker == null) {
                    who = Component.translatable("gui.nerospace.dock.origin");
                } else if (za.co.neroland.nerospace.rocket.StationDockingRule.mayChoose(blocker.docking(),
                        StationRegistry.canUse(blocker, player))) {
                    who = Component.literal(blocker.name());
                } else {
                    who = Component.translatable("item.nerospace.station_charter.another_station");
                }
                yield Component.translatable("item.nerospace.station_charter.too_close", who,
                        NerospaceConfig.stationsMinSpacing());
            }
            case TOO_FAR -> Component.translatable("item.nerospace.station_charter.too_far",
                    NerospaceConfig.stationsMaxRadius());
            default -> Component.translatable("item.nerospace.station_charter.full");
        };
    }

    /** Server-side rename (from a Station Core's rename screen): updates registry + Core + landing pad. */
    public static void renameStation(ServerPlayer player, int slot, String name) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isBlank()) {
            return;
        }
        StationRegistry registry = StationRegistry.get(server);
        if (!StationRegistry.canManage(registry.get(slot), player)) {
            player.sendSystemMessage(Component.translatable("item.nerospace.station_charter.not_owner"));
            return;
        }
        StationRegistry.StationEntry before = registry.get(slot);
        StationRegistry.StationEntry entry = registry.rename(slot, trimmed.length() > 32 ? trimmed.substring(0, 32)
                : trimmed);
        if (entry == null) {
            return;
        }
        if (za.co.neroland.nerospace.world.station.StationAccess.manageViaOp(before == null ? "" : before.owner(),
                player.getUUID().toString(), StationRegistry.isOp(player))) {
            za.co.neroland.nerospace.world.station.StationAudit.op(
                    za.co.neroland.nerospace.world.station.StationAudit.Action.RENAME, slot);
        }
        ServerLevel station = server.getLevel(ModDimensions.STATION_LEVEL);
        if (station != null) {
            if (station.getBlockEntity(entry.center()) instanceof StationCoreBlockEntity core) {
                core.bindStation(slot, entry.name());
            }
            boolean dock = entry.hasModule(za.co.neroland.nerospace.world.station.ModuleKind.DOCKING_RING);
            PadRegistry.get(server).register(entry.name() + (dock ? " Dock" : " Landing"),
                    ModDimensions.STATION_LEVEL, StationStructure.padCenter(entry.center()));
        }
        // NeroLink: same thin broadcast as founding, fired here rather than at the call sites so the
        // in-game naming console and the companion app's rename action cannot drift apart.
        za.co.neroland.nerospace.link.NerospaceLinkEvents.stationStateChanged(server, slot, "RENAMED");
        player.sendSystemMessage(Component.translatable("item.nerospace.station_charter.renamed", entry.name()));
        za.co.neroland.nerospace.network.StationListSync.send(player);
    }
}
