package za.co.neroland.nerospace.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.data.SavedDataRecovery;
import za.co.neroland.nerospace.network.ModNetwork;
import za.co.neroland.nerospace.network.StationConsoleSyncPayload;
import za.co.neroland.nerospace.registry.ModDimensions;
import za.co.neroland.nerospace.registry.ModMenuTypes;
import za.co.neroland.nerospace.rocket.PadRegistry;
import za.co.neroland.nerospace.rocket.StationRegistry;
import za.co.neroland.nerospace.rocket.StationRegistry.StationEntry;
import za.co.neroland.nerospace.rocket.StationStructure;
import za.co.neroland.nerospace.route.RouteRegistry;
import za.co.neroland.nerospace.world.OxygenFieldManager;
import za.co.neroland.nerospace.world.station.ModuleBuildRules;
import za.co.neroland.nerospace.world.station.ModuleEntry;
import za.co.neroland.nerospace.world.station.ModuleKind;
import za.co.neroland.nerospace.world.station.ModuleTemplates;
import za.co.neroland.nerospace.world.station.StationAccess;
import za.co.neroland.nerospace.world.station.StationAudit;
import za.co.neroland.nerospace.world.station.StationClass;
import za.co.neroland.nerospace.world.station.StationHooks;
import za.co.neroland.nerospace.world.station.StationModules;
import za.co.neroland.nerospace.world.station.StationObservatory;

/**
 * The Station Console: a slotless menu opened from a bound Station Core. Sixteen synced ints carry
 * the numbers; a {@link StationConsoleSyncPayload} carries the rest on open and whenever it changes.
 * Serverbound actions arrive through {@code clickMenuButton} ids (ranges 100/200/300/400 reserved) plus the rename
 * and add-member payloads; every handler re-checks {@code canManage}/{@code canUse} on the server — the client's
 * booleans only drive drawing. Module materials are taken from the player's inventory, never placed in the menu.
 */
public class StationConsoleMenu extends AbstractContainerMenu {

    // --- ContainerData indices -------------------------------------------------------
    public static final int D_SLOT = 0;
    public static final int D_CLASS = 1;
    public static final int D_BUILT_MASK = 2;
    public static final int D_POWERED_MASK = 3;
    public static final int D_MODULE_COUNT = 4;
    public static final int D_POWER_PCT = 5;
    public static final int D_OXYGEN_PCT = 6;
    public static final int D_MEMBER_COUNT = 7;
    public static final int D_ROUTE_USED = 8;
    public static final int D_ROUTE_CAP = 9;
    public static final int D_CAN_MANAGE = 10;
    public static final int D_CAN_USE = 11;
    public static final int D_ARRIVAL_KIND = 12;
    public static final int D_LAST_BUILD = 13;
    public static final int D_FOUNDED_DAY = 14;
    public static final int D_PAIRED_PADS = 15;
    public static final int DATA_COUNT = 16;

    // --- button ids -------------------------------------------
    public static final int BUTTON_CYCLE_CLASS = 1;
    public static final int BUTTON_ABANDON = 2;
    public static final int BUTTON_RENAME = 3;
    public static final int BUTTON_ADD_MEMBER = 4;
    public static final int BUTTON_CLAIM = 5;
    public static final int BUTTON_CYCLE_CLASS_BACK = 6;
    public static final int BUTTON_TOGGLE_DOCKING = 7;
    public static final int BUTTON_LEAVE = 8;
    public static final int BUTTON_SEND_VISITORS_HOME = 9;
    public static final int REMOVE_MEMBER_BASE = 100;
    public static final int SELECT_RETURN_BASE = 200;
    public static final int BUILD_BASE = 300;
    public static final int TOGGLE_MODULE_BASE = 400;

    /** Re-sync cadence while open (ticks). */
    private static final int REFRESH_TICKS = 20;
    /** Console reach (blocks, squared) from the Station Core. */
    private static final double REACH_SQ = 8.0 * 8.0;

    private final ContainerData data;
    @Nullable
    private final ServerPlayer viewer;
    @Nullable
    private final BlockPos corePos;
    private int ticks;
    @Nullable
    private StationConsoleSyncPayload lastSent;
    @Nullable
    private BlockPos lastBlocked;

    /** Client constructor (menu type). */
    public StationConsoleMenu(int containerId, Inventory playerInventory) {
        super(ModMenuTypes.STATION_CONSOLE.get(), containerId);
        this.data = new SimpleContainerData(DATA_COUNT);
        this.viewer = null;
        this.corePos = null;
        addDataSlots(this.data);
    }

    /** Server constructor for the station in {@code slot}, opened at the core at {@code corePos}. */
    public StationConsoleMenu(int containerId, Inventory playerInventory, ServerPlayer viewer, int slot,
            BlockPos corePos) {
        super(ModMenuTypes.STATION_CONSOLE.get(), containerId);
        this.data = new SimpleContainerData(DATA_COUNT);
        this.data.set(D_SLOT, slot);
        this.data.set(D_LAST_BUILD, ModuleBuildRules.RESULT_NONE);
        this.viewer = viewer;
        this.corePos = corePos.immutable();
        addDataSlots(this.data);
        refresh(true);
    }

    public int get(int index) {
        return this.data.get(index);
    }

    public int slot() {
        return this.data.get(D_SLOT);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.corePos == null || player.distanceToSqr(this.corePos.getX() + 0.5, this.corePos.getY() + 0.5,
                this.corePos.getZ() + 0.5) <= REACH_SQ;
    }

    @Override
    public void broadcastChanges() {
        if (this.viewer != null && ++this.ticks % REFRESH_TICKS == 0) {
            refresh(false);
        }
        super.broadcastChanges();
    }

    // --- server: snapshot ----------------------------------------------------------------------

    @Nullable
    private StationEntry entry() {
        if (this.viewer == null) {
            return null;
        }
        return StationRegistry.get(this.viewer.level().getServer()).get(slot());
    }

    @Nullable
    private ServerLevel stationLevel() {
        return this.viewer == null ? null : this.viewer.level().getServer().getLevel(ModDimensions.STATION_LEVEL);
    }

    /** Recomputes the synced ints and sends the payload when it changed (or {@code force}). */
    private void refresh(boolean force) {
        ServerPlayer player = this.viewer;
        StationEntry entry = entry();
        ServerLevel level = stationLevel();
        if (player == null || entry == null || level == null) {
            return;
        }
        MinecraftServer server = level.getServer();
        boolean manage = StationRegistry.canManage(entry, player);
        boolean use = StationRegistry.canUse(entry, player);
        int built = 0;
        int powered = 0;
        int wings = 0;
        for (ModuleKind kind : ModuleKind.values()) {
            if (!entry.hasModule(kind)) {
                continue;
            }
            built |= 1 << kind.tile();
            boolean allOn = true;
            for (ModuleEntry m : entry.modules()) {
                if (m.kind() == kind && !m.enabled()) {
                    allOn = false;
                }
            }
            if (allOn) {
                powered |= 1 << kind.tile();
            }
        }
        wings = entry.countOf(ModuleKind.SOLAR_WING);
        int power = StationModules.power(level, entry);
        int maxPower = (int) Math.round(wings * 12 * 200 * entry.stationClass().traits().solarWingMultiplier());
        int[] sealed = StationModules.pressurised(level, entry);
        int stationPadId = RouteRegistry.stationPadId(entry.slot());
        RouteRegistry routes = RouteRegistry.get(server);

        this.data.set(D_CLASS, entry.stationClass().ordinal());
        this.data.set(D_BUILT_MASK, built);
        this.data.set(D_POWERED_MASK, powered);
        this.data.set(D_MODULE_COUNT, entry.capCount());
        this.data.set(D_POWER_PCT, maxPower <= 0 ? 0 : Math.min(100, power * 100 / maxPower));
        this.data.set(D_OXYGEN_PCT, sealed[1] <= 0 ? 0 : sealed[0] * 100 / sealed[1]);
        this.data.set(D_MEMBER_COUNT, entry.members().size());
        this.data.set(D_ROUTE_USED, Math.min(Short.MAX_VALUE, routes.routesTo(stationPadId)));
        this.data.set(D_ROUTE_CAP, entry.stationClass().traits().routeCapacity());
        this.data.set(D_CAN_MANAGE, manage ? 1 : 0);
        this.data.set(D_CAN_USE, use ? 1 : 0);
        this.data.set(D_ARRIVAL_KIND, entry.hasModule(ModuleKind.DOCKING_RING) ? 1 : 0);
        this.data.set(D_FOUNDED_DAY, (int) Math.max(-1L, Math.min(Short.MAX_VALUE, entry.foundedAt())));

        List<StationConsoleSyncPayload.PairedPad> paired = pairedPads(server, level, entry);
        this.data.set(D_PAIRED_PADS, paired.size());

        String me = player.getUUID().toString();
        int role;
        if (!entry.isOwned()) {
            role = StationConsoleSyncPayload.ROLE_UNOWNED;
        } else if (entry.owner().equals(me)) {
            role = StationConsoleSyncPayload.ROLE_OWNER;
        } else if (entry.members().contains(me)) {
            role = StationConsoleSyncPayload.ROLE_MEMBER;
        } else if (use) {
            role = StationConsoleSyncPayload.ROLE_OPERATOR;
        } else {
            role = StationConsoleSyncPayload.ROLE_VISITOR;
        }
        Identities ids = identities(use, entry.owner(), entry.members());
        Optional<UUID> owner = ids.owner();
        List<UUID> members = ids.members();
        long cooldown = StationAccess.classCooldownRemaining(server.overworld().getGameTime(),
                entry.classChangedAt());
        StationConsoleSyncPayload payload = new StationConsoleSyncPayload(this.containerId, entry.slot(),
                entry.name(), entry.isOwned(), owner, List.copyOf(members), role, entry.docking().ordinal(),
                (int) Math.min(Integer.MAX_VALUE, cooldown), sealed[0], sealed[1], power, wings,
                StationObservatory.revealCount(level, entry), routes.inboundCount(stationPadId), paired,
                Optional.ofNullable(this.lastBlocked));
        if (force || !payload.equals(this.lastSent)) {
            this.lastSent = payload;
            ModNetwork.sendToPlayer(player, payload);
        }
    }

    /** What the payload may carry about people: identities only for a viewer who passes {@code canUse}. */
    public record Identities(Optional<UUID> owner, List<UUID> members) {
    }

    /**
     * POPIA/GDPR: the owner and member ids go only to a viewer who passes {@code canUse} (owner, member,
     * operator); a visitor gets neither — the payload then carries the {@code hasOwner} flag and the count alone.
     */
    public static Identities identities(boolean canUse, String owner, List<String> members) {
        if (!canUse) {
            return new Identities(Optional.empty(), List.of());
        }
        List<UUID> out = new ArrayList<>();
        for (String m : members) {
            parse(m).ifPresent(out::add);
        }
        return new Identities(parse(owner), List.copyOf(out));
    }

    private static Optional<UUID> parse(String uuid) {
        if (uuid == null || uuid.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(uuid));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Pads on other worlds named after this station (exact name, case-insensitive), at most 6. */
    private static List<StationConsoleSyncPayload.PairedPad> pairedPads(MinecraftServer server, ServerLevel station,
            StationEntry entry) {
        List<StationConsoleSyncPayload.PairedPad> out = new ArrayList<>();
        String stationDim = ModDimensions.STATION_LEVEL.identifier().toString();
        for (PadRegistry.PadNode node : PadRegistry.get(server).all()) {
            if (out.size() >= 6) {
                break;
            }
            if (node.dim().equals(stationDim) || !node.name().equalsIgnoreCase(entry.name())) {
                continue;
            }
            out.add(new StationConsoleSyncPayload.PairedPad(node.dim(), node.name(), true));
        }
        return out;
    }

    // --- server: actions -----------------------------------------------------------------------

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer sp) || this.viewer == null) {
            return false;
        }
        StationEntry entry = entry();
        ServerLevel level = stationLevel();
        if (entry == null || level == null) {
            return false;
        }
        StationRegistry registry = StationRegistry.get(level.getServer());
        boolean handled = handle(sp, id, entry, registry, level);
        if (handled) {
            refresh(true);
        }
        return handled;
    }

    private boolean handle(ServerPlayer sp, int id, StationEntry entry, StationRegistry registry, ServerLevel level) {
        String me = sp.getUUID().toString();
        boolean op = StationRegistry.isOp(sp);
        boolean manage = StationAccess.canManage(entry.owner(), me, op);
        boolean use = StationAccess.canUse(entry.owner(), entry.members(), me, op);
        boolean viaOp = StationAccess.manageViaOp(entry.owner(), me, op);
        MinecraftServer server = level.getServer();

        if (id == BUTTON_CYCLE_CLASS || id == BUTTON_CYCLE_CLASS_BACK) {
            if (!manage) {
                return deny(sp);
            }
            long now = server.overworld().getGameTime();
            boolean free = StationAccess.canChangeClass(now, entry.classChangedAt(), false);
            if (!free && !op) {
                sp.sendSystemMessage(Component.translatable("gui.nerospace.station_console.class_cooldown"));
                return false;
            }
            StationClass next = entry.stationClass().cycle(id == BUTTON_CYCLE_CLASS_BACK);
            StationEntry updated = registry.put(entry.withClass(next, now));
            if (viaOp) {
                StationAudit.op(StationAudit.Action.CLASS, entry.slot());
            }
            if (!free) {
                StationAudit.op(StationAudit.Action.CLASS_COOLDOWN_BYPASS, entry.slot());
            }
            if (updated != null) {
                StationObservatory.syncTelescope(level, updated);
            }
            return true;
        }
        if (id == BUTTON_ABANDON) {
            if (!manage || !entry.isOwned()) {
                return deny(sp);
            }
            registry.put(entry.withOwner("").withMembers(List.of()));
            backup(server, registry);
            if (viaOp) {
                StationAudit.op(StationAudit.Action.ABANDON, entry.slot());
            }
            sp.sendSystemMessage(Component.translatable("gui.nerospace.station_console.abandoned"));
            // The former owner's dock list drops the "yours" tag at once.
            za.co.neroland.nerospace.network.StationListSync.send(sp);
            return true;
        }
        if (id == BUTTON_CLAIM) {
            // An operator may claim an unowned station (the abuse remedy); always an op action.
            if (!op || entry.isOwned()) {
                return deny(sp);
            }
            registry.put(entry.withOwner(me));
            backup(server, registry);
            StationAudit.op(StationAudit.Action.CLAIM, entry.slot());
            return true;
        }
        if (id == BUTTON_TOGGLE_DOCKING) {
            if (!manage) {
                return deny(sp);
            }
            registry.put(entry.withDocking(entry.docking().toggled()));
            if (viaOp) {
                StationAudit.op(StationAudit.Action.DOCKING, entry.slot());
            }
            return true;
        }
        if (id == BUTTON_LEAVE) {
            if (!entry.members().contains(me)) {
                return false;
            }
            registry.removeMember(entry.slot(), me);
            backup(server, registry);
            return true;
        }
        if (id == BUTTON_SEND_VISITORS_HOME) {
            if (!use) {
                return deny(sp);
            }
            int sent = StationHooks.sendVisitorsHome(level, entry);
            sp.sendSystemMessage(sent == StationHooks.VISITOR_ABOARD
                    ? Component.translatable("gui.nerospace.station_console.visitors_aboard")
                    : sent < 0
                    ? Component.translatable("gui.nerospace.station_console.visitors_unavailable")
                    : Component.translatable("gui.nerospace.station_console.visitors_sent", sent));
            if (sent > 0 && StationAccess.useViaOp(entry.owner(), entry.members(), me, op)) {
                StationAudit.op(StationAudit.Action.VISITOR_SEND_HOME, entry.slot());
            }
            return true;
        }
        if (id >= REMOVE_MEMBER_BASE && id < REMOVE_MEMBER_BASE + StationAccess.MAX_MEMBERS) {
            int i = id - REMOVE_MEMBER_BASE;
            if (i >= entry.members().size()) {
                return false;
            }
            String target = entry.members().get(i);
            if (!manage && !target.equals(me)) {
                return deny(sp);
            }
            if (registry.removeMember(entry.slot(), target)) {
                backup(server, registry);
                if (viaOp && !target.equals(me)) {
                    StationAudit.op(StationAudit.Action.MEMBER_REMOVE, entry.slot());
                }
            }
            return true;
        }
        if (id >= BUILD_BASE && id < BUILD_BASE + ModuleKind.TILE_COUNT) {
            return build(sp, entry, registry, level, ModuleKind.byTile(id - BUILD_BASE), manage, viaOp);
        }
        if (id >= TOGGLE_MODULE_BASE && id < TOGGLE_MODULE_BASE + ModuleKind.TILE_COUNT) {
            ModuleKind kind = ModuleKind.byTile(id - TOGGLE_MODULE_BASE);
            if (kind != ModuleKind.SOLAR_WING && kind != ModuleKind.GRAVITY_DECK) {
                return false; // trays have no power switch; hull modules have nothing to switch
            }
            if (!use) {
                return deny(sp);
            }
            boolean on = false;
            for (ModuleEntry m : entry.modules()) {
                if (m.kind() == kind && !m.enabled()) {
                    on = true;
                }
            }
            registry.setModuleEnabled(entry.slot(), kind, on);
            if (StationAccess.useViaOp(entry.owner(), entry.members(), me, op)) {
                StationAudit.op(StationAudit.Action.MODULE_TOGGLE, entry.slot());
            }
            return true;
        }
        return false;
    }

    private boolean build(ServerPlayer sp, StationEntry entry, StationRegistry registry, ServerLevel level,
            @Nullable ModuleKind kind, boolean manage, boolean viaOp) {
        this.lastBlocked = null;
        if (kind == null) {
            this.data.set(D_LAST_BUILD, ModuleBuildRules.RESULT_INVALID);
            return true;
        }
        if (!manage) {
            this.data.set(D_LAST_BUILD, ModuleBuildRules.RESULT_PERMISSION);
            return true;
        }
        int index = ModuleBuildRules.nextIndex(entry.modules(), kind);
        boolean repair = index < 0;
        int result = ModuleBuildRules.RESULT_OK;
        BlockPos blocked = null;
        if (repair) {
            for (int i = 0; i < kind.maxCount(); i++) {
                StationModules.BuildOutcome out = StationModules.build(level, registry, entry, kind, i, sp);
                result = Math.max(result, out.resultCode());
            }
        } else {
            StationModules.BuildOutcome out = StationModules.build(level, registry, entry, kind, index, sp);
            result = out.resultCode();
            blocked = out.firstBlocked();
        }
        this.data.set(D_LAST_BUILD, result);
        this.lastBlocked = blocked;
        if (result == ModuleBuildRules.RESULT_OK && viaOp) {
            StationAudit.op(repair ? StationAudit.Action.MODULE_REPAIR : StationAudit.Action.MODULE_BUILD,
                    entry.slot());
        }
        if (result == ModuleBuildRules.RESULT_OK) {
            StationEntry now = registry.get(entry.slot());
            if (now != null) {
                StationObservatory.syncTelescope(level, now);
            }
        }
        return true;
    }

    private static boolean deny(ServerPlayer sp) {
        sp.sendSystemMessage(Component.translatable("gui.nerospace.station_console.members_only"));
        return false;
    }

    private static void backup(MinecraftServer server, StationRegistry registry) {
        SavedDataRecovery.backupNow(server.overworld(), StationRegistry.TYPE, registry, "nerospace:stations");
    }

    // --- server: add member (payload) ------------------------------------------------------------

    /**
     * Whether an add-member request is bound to the console the player has open: the open container is a Station
     * Console for the same slot and is still valid. Every other console action goes through {@code clickMenuButton},
     * which has both checks; the payload path must not be weaker.
     */
    public static boolean boundToOpenConsole(boolean consoleOpen, int openSlot, boolean stillValid, int slot) {
        return consoleOpen && stillValid && openSlot == slot && slot >= 0;
    }

    /** Handles {@code StationMemberPayload}: bound to the open console, online-only, UUID-only, re-checked here. */
    public static void addMember(ServerPlayer sp, int slot, UUID candidate) {
        boolean open = sp.containerMenu instanceof StationConsoleMenu;
        int openSlot = sp.containerMenu instanceof StationConsoleMenu m ? m.slot() : -1;
        boolean valid = sp.containerMenu instanceof StationConsoleMenu m2 && m2.stillValid(sp);
        if (!boundToOpenConsole(open, openSlot, valid, slot)) {
            return; // a crafted packet without the console open: refused silently
        }
        MinecraftServer server = sp.level().getServer();
        StationRegistry registry = StationRegistry.get(server);
        StationEntry entry = registry.get(slot);
        if (entry == null || !StationRegistry.canManage(entry, sp)) {
            sp.sendSystemMessage(Component.translatable("gui.nerospace.station_console.members_only"));
            return;
        }
        boolean online = server.getPlayerList().getPlayer(candidate) != null;
        StationAccess.AddVerdict verdict = StationAccess.canAddMember(entry.owner(), entry.members(),
                candidate.toString(), online);
        if (verdict != StationAccess.AddVerdict.OK) {
            sp.sendSystemMessage(Component.translatable("gui.nerospace.station_console.add_refused."
                    + verdict.name().toLowerCase(java.util.Locale.ROOT)));
            return;
        }
        if (registry.addMember(slot, candidate.toString())) {
            backup(server, registry);
            if (StationAccess.manageViaOp(entry.owner(), sp.getUUID().toString(), StationRegistry.isOp(sp))) {
                StationAudit.op(StationAudit.Action.MEMBER_ADD, slot);
            }
            if (sp.containerMenu instanceof StationConsoleMenu menu && menu.slot() == slot) {
                menu.refresh(true);
            }
        }
    }

    /** Oxygen bookkeeping used when a station is abandoned by removal (command path). */
    public static void clearOxygen(ServerLevel level, StationEntry entry) {
        OxygenFieldManager oxygen = OxygenFieldManager.get(level);
        for (BlockPos cell : StationModules.oxygenCells(entry)) {
            oxygen.removeSource(cell);
        }
    }

    /** The console's station pad centre (for the Docking tab's arrival line). */
    public static BlockPos arrivalCentre(StationEntry entry) {
        return entry.hasModule(ModuleKind.DOCKING_RING)
                ? ModuleTemplates.anchor(ModuleKind.DOCKING_RING, 0, entry.center())
                : StationStructure.padCenter(entry.center());
    }
}
