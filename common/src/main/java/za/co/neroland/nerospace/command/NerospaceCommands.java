package za.co.neroland.nerospace.command;

import java.util.ArrayList;
import java.util.List;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.AABB;

import za.co.neroland.nerospace.NerospaceCommon;
import za.co.neroland.nerospace.config.NerospaceConfig;
import za.co.neroland.nerospace.meteor.FallingMeteorEntity;
import za.co.neroland.nerospace.meteor.MeteorCoreBlockEntity;
import za.co.neroland.nerospace.machine.CombustionGeneratorBlockEntity;
import za.co.neroland.nerospace.machine.FuelRefineryBlockEntity;
import za.co.neroland.nerospace.machine.HydrationModuleBlockEntity;
import za.co.neroland.nerospace.machine.NerosiumGrinderBlockEntity;
import za.co.neroland.nerospace.machine.quarry.QuarryControllerBlockEntity;
import za.co.neroland.nerospace.pipe.PipeIoMode;
import za.co.neroland.nerospace.pipe.PipeResourceType;
import za.co.neroland.nerospace.pipe.UniversalPipeBlockEntity;
import za.co.neroland.nerospace.registry.ModBlocks;
import za.co.neroland.nerospace.registry.ModEntities;
import za.co.neroland.nerospace.registry.ModItems;
import za.co.neroland.nerospace.registry.ModDimensions;
import za.co.neroland.nerospace.registry.ModTags;
import za.co.neroland.nerospace.world.gravity.GravityManager;
import za.co.neroland.nerospace.rocket.LaunchControllerBlock;
import za.co.neroland.nerospace.rocket.LaunchControllerBlockEntity;
import za.co.neroland.nerospace.rocket.PadRegistry;
import za.co.neroland.nerospace.rocket.RocketEntity;
import za.co.neroland.nerospace.rocket.RocketLaunchPadBlock;
import za.co.neroland.nerospace.rocket.RocketTier;
import za.co.neroland.nerospace.rocket.StationRegistry;
import za.co.neroland.nerospace.rocket.StationStructure;
import za.co.neroland.nerolandcore.storage.CreativeItemStoreBlockEntity;
import za.co.neroland.nerospace.telemetry.NerospaceTelemetry;

/**
 * Creative-only debug commands (cheats / op level 2). {@code /nerospace gallery} builds a showcase
 * platform near the player: every Nerospace block floating two blocks above the floor (so all faces
 * are visible) on a ~3-block grid, every machine RUNNING the way it is meant to be wired in
 * survival (fuelled, powered and fed — except the Terraformer and Oxygen Generator, which sit
 * behind an off lever so the world-changing machines only run when deliberately switched on), all
 * four rocket tiers standing on their required pad formations, every suit variant on a stand, and
 * each creature spawned twice — once with AI and once frozen (NoAI) — for inspection.
 * {@code /nerospace gallery clear} wipes that footprint (blocks + spawned entities) so a rebuild —
 * or the screenshot harness — doesn't stack duplicates.
 */
public final class NerospaceCommands {

    private static final int SPACING = 3;     // blocks between display cells
    private static final int FLOAT_ABOVE = 3; // display sits this many blocks above the floor (2 air gap)
    private static final int SUIT_SPACING = 3; // blocks between suit stands (roomier than the old 2)
    private static final float SUIT_YAW = -10.0f; // every suit stand faces one way, angled a few degrees left

    private NerospaceCommands() {
    }

    /**
     * Cross-loader registration: each loader calls this from its command hook (NeoForge
     * {@code RegisterCommandsEvent}, Fabric {@code CommandRegistrationCallback}) with the dispatcher.
     */
    public static void register(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher) {
        // gallery / gravity / station are player-only (the requirement sits on each of them, not on the shared
        // root, so the ops clock/weather commands below stay usable from the console and command blocks); the
        // executors further restrict to creative.
        dispatcher.register(
                Commands.literal("nerospace")
                        .then(Commands.literal("gallery")
                                .requires(src -> src.getPlayer() != null)
                                .executes(ctx -> runSafely(ctx.getSource(), "gallery",
                                        () -> buildGallery(ctx.getSource())))
                                .then(Commands.literal("clear")
                                        .executes(ctx -> runSafely(ctx.getSource(), "gallery clear",
                                                () -> clearGallery(ctx.getSource()))))
                                // Operator-only sky showcase (permission level 2 on its own node).
                                .then(PlanetCommands.gallerySky()))
                        .then(Commands.literal("gravity")
                                .requires(src -> src.getPlayer() != null)
                                .executes(ctx -> runSafely(ctx.getSource(), "gravity",
                                        () -> reportGravity(ctx.getSource()))))
                        .then(Commands.literal("station")
                                .requires(src -> src.getPlayer() != null)
                                .then(Commands.literal("list")
                                        .executes(ctx -> runSafely(ctx.getSource(), "station list",
                                                () -> listStations(ctx.getSource()))))
                                .then(Commands.literal("remove")
                                        .executes(ctx -> runSafely(ctx.getSource(), "station remove",
                                                () -> removeStation(ctx.getSource(), -1)))
                                        .then(Commands.argument("slot", IntegerArgumentType.integer(0))
                                                .executes(ctx -> runSafely(ctx.getSource(), "station remove",
                                                        () -> removeStation(ctx.getSource(),
                                                                IntegerArgumentType.getInteger(ctx, "slot")))))))
                        // POPIA s23 / GDPR Art. 15: any player may export what Nerospace stores about THEM.
                        .then(Commands.literal("privacy")
                                .requires(src -> src.getPlayer() != null)
                                .then(Commands.literal("export")
                                        .executes(ctx -> runSafely(ctx.getSource(), "privacy export",
                                                () -> exportPrivacy(ctx.getSource()))))));
        // Ops-only planet clock + weather controls (permission level 2, consoles and command blocks too).
        dispatcher.register(PlanetCommands.build());
        // Dev-environment-only measurement tools (operator, refused outside a dev run).
        dispatcher.register(DevCommands.build());
    }

    /**
     * {@code /nerospace gravity} — report the resolved gravity factor at the player's feet: the value, its
     * source (biome tag override vs the dimension default), the config multiplier, and the player's live
     * {@code GRAVITY} attribute. A Phase-4 tuning aid for GRAVITY_DESIGN.md.
     */
    private static int reportGravity(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.nerospace.player_only"));
            return 0;
        }
        ServerLevel level = player.level();
        BlockPos pos = player.blockPosition();
        Holder<Biome> biome = level.getBiome(pos);

        String src;
        if (biome.is(ModTags.Biomes.GRAVITY_MICRO)) {
            src = "command.nerospace.gravity.source.micro";
        } else if (biome.is(ModTags.Biomes.GRAVITY_LOW)) {
            src = "command.nerospace.gravity.source.low";
        } else if (biome.is(ModTags.Biomes.GRAVITY_HIGH)) {
            src = "command.nerospace.gravity.source.high";
        } else {
            src = "command.nerospace.gravity.source.dimension";
        }

        double factor = GravityManager.factorAt(level, pos);
        double mult = NerospaceConfig.gravityMultiplier();
        Identifier biomeId = biome.unwrapKey().map(k -> k.identifier()).orElse(null);
        AttributeInstance gravityAttr = player.getAttribute(Attributes.GRAVITY);
        Component attrText = gravityAttr != null
                ? Component.translatable("command.nerospace.gravity.attribute",
                        String.format(java.util.Locale.ROOT, "%.4f", gravityAttr.getValue()))
                : Component.translatable("command.nerospace.gravity.attribute_absent");

        Component message = Component.translatable("command.nerospace.gravity.report",
                String.format(java.util.Locale.ROOT, "%.3f", factor), Component.translatable(src),
                String.format(java.util.Locale.ROOT, "%.2f", mult), level.dimension().identifier().toString(),
                biomeId == null ? Component.translatable("command.nerospace.gravity.biome_unknown")
                        : Component.literal(biomeId.toString()),
                attrText);
        source.sendSuccess(() -> message, false);
        return Command.SINGLE_SUCCESS;
    }

    /** {@code /nerospace station list} — show founded stations (slot + name). */
    private static int listStations(CommandSourceStack source) {
        java.util.List<StationRegistry.StationEntry> all = StationRegistry.get(source.getServer()).all();
        if (all.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.nerospace.station.list_empty"), false);
            return 1;
        }
        source.sendSuccess(() -> Component.translatable("command.nerospace.station.list_header"), false);
        for (StationRegistry.StationEntry e : all) {
            source.sendSuccess(() -> Component.translatable("command.nerospace.station.list_entry",
                    e.slot(), e.name()), false);
        }
        return 1;
    }

    /**
     * {@code /nerospace station remove [slot]} — decommission a station (only its founder or a server
     * op). With no slot it targets the station whose Station Core you're standing on. Unregisters the
     * station, removes the (otherwise unbreakable) Core, and drops its landing-pad travel node.
     */
    private static int removeStation(CommandSourceStack source, int slot) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.nerospace.player_only"));
            return 0;
        }
        StationRegistry registry = StationRegistry.get(source.getServer());
        StationRegistry.StationEntry entry = slot >= 0 ? registry.get(slot) : stationAtPlayer(source, player);
        if (entry == null) {
            source.sendFailure(Component.translatable("command.nerospace.station.none"));
            return 0;
        }
        // The founder may remove their own station; a creative-mode admin may remove any.
        if (!StationRegistry.canManage(entry, player) && !player.getAbilities().instabuild) {
            source.sendFailure(Component.translatable("item.nerospace.station_charter.not_owner"));
            return 0;
        }
        if (za.co.neroland.nerospace.world.station.StationAccess.manageViaOp(entry.owner(),
                player.getUUID().toString(), StationRegistry.isOp(player))) {
            // Operator override: action + slot only — never who, never the station's name.
            za.co.neroland.nerospace.world.station.StationAudit.op(
                    za.co.neroland.nerospace.world.station.StationAudit.Action.REMOVE, entry.slot());
        }
        ServerLevel station = source.getServer().getLevel(ModDimensions.STATION_LEVEL);
        if (station != null) {
            za.co.neroland.nerospace.menu.StationConsoleMenu.clearOxygen(station, entry);
            BlockPos centre = entry.center();
            station.getChunk(centre.getX() >> 4, centre.getZ() >> 4);
            station.removeBlock(centre, false); // bypasses the unbreakable Core
        }
        registry.unregister(entry.slot());
        PadRegistry.get(source.getServer())
                .unregisterAt(ModDimensions.STATION_LEVEL, StationStructure.padCenter(entry.center()));
        // Feedback to the invoker only: a broadcast would write the player's and the station's name to the server log.
        source.sendSuccess(() -> Component.translatable("item.nerospace.station_charter.removed", entry.name()), false);
        return 1;
    }

    /** The founded station whose Core the player is standing on/near (within 8 blocks, station dim). */
    private static StationRegistry.StationEntry stationAtPlayer(CommandSourceStack source, ServerPlayer player) {
        if (!player.level().dimension().equals(ModDimensions.STATION_LEVEL)) {
            return null;
        }
        BlockPos at = player.blockPosition();
        StationRegistry.StationEntry best = null;
        double bestSq = 64.0; // within 8 blocks of the Core
        for (StationRegistry.StationEntry e : StationRegistry.get(source.getServer()).all()) {
            double sq = e.center().distSqr(at);
            if (sq <= bestSq) {
                bestSq = sq;
                best = e;
            }
        }
        return best;
    }

    /**
     * {@code /nerospace privacy export} — the invoker's own Nerospace data as JSON, sent only to the invoker (never
     * broadcast, never logged). See {@link za.co.neroland.nerospace.data.NerospacePrivacyExport}.
     */
    private static int exportPrivacy(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.nerospace.player_only"));
            return 0;
        }
        com.google.gson.JsonObject doc =
                za.co.neroland.nerospace.data.NerospacePrivacyExport.collect(source.getServer(), player);
        String json = doc.toString();
        source.sendSuccess(() -> Component.translatable("command.nerospace.privacy.export.summary",
                doc.getAsJsonArray("planets_visited").size(), doc.getAsJsonArray("stations_owned").size(),
                doc.getAsJsonArray("station_memberships").size(), doc.getAsJsonArray("village_cores_owned").size(),
                doc.getAsJsonArray("cargo_routes_owned").size()), false);
        source.sendSuccess(() -> Component.translatable("command.nerospace.privacy.export.copy")
                .withStyle(st -> st.withUnderlined(true)
                        .withClickEvent(new net.minecraft.network.chat.ClickEvent.CopyToClipboard(json))), false);
        source.sendSuccess(() -> Component.literal(json).withStyle(net.minecraft.ChatFormatting.GRAY), false);
        return 1;
    }

    private static int runSafely(CommandSourceStack source, String commandName, CommandBody body) {
        try {
            return body.run();
        } catch (RuntimeException ex) {
            NerospaceTelemetry.captureHandledException(ex, "command", "/nerospace " + commandName);
            NerospaceCommon.LOGGER.error("[Nerospace] /nerospace {} failed", commandName, ex);
            source.sendFailure(Component.translatable("command.nerospace.failed", commandName));
            return 0;
        }
    }

    @FunctionalInterface
    private interface CommandBody {
        int run();
    }

    private static int buildGallery(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.nerospace.player_only"));
            return 0;
        }
        if (!player.getAbilities().instabuild) {
            source.sendFailure(Component.translatable("command.nerospace.gallery.creative_only"));
            return 0;
        }
        ServerLevel level = player.level();
        BlockPos origin = player.blockPosition();

        // The cross-loader RegistrationProvider has no entry iteration, so walk the vanilla block
        // registry filtered to this mod's namespace (same effect as the root's BLOCKS.getEntries()).
        List<Block> blocks = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            Identifier bid = BuiltInRegistries.BLOCK.getKey(block);
            if (!NerospaceCommon.MOD_ID.equals(bid.getNamespace())) {
                continue;
            }
            if (block != ModBlocks.ROCKET_FUEL_BLOCK.get()) { // skip the fluid block (renders oddly free-standing)
                blocks.add(block);
            }
        }

        int cols = (int) Math.ceil(Math.sqrt(Math.max(1, blocks.size())));
        int rows = (int) Math.ceil(blocks.size() / (double) cols);
        // ROTUNDA: each cluster sits on a ring ~48 blocks out on its own compass bearing, so a camera
        // near the centre shoots each one outward against empty ground with no other display in frame.
        // The /nsgallery capture harness mirrors these bearings. Bases are placed so the body centres
        // on the ring. Tune distances together with the harness if reframing.
        int ox = origin.getX() + 38; // block grid → EAST
        int oz = origin.getZ() - 9;
        int fy = origin.getY();

        BlockState floor = ModBlocks.STATION_FLOOR.get().defaultBlockState();

        // Floor slab under the whole grid (with a 1-block margin).
        for (int gx = -1; gx <= cols * SPACING; gx++) {
            for (int gz = -1; gz <= rows * SPACING; gz++) {
                level.setBlockAndUpdate(new BlockPos(ox + gx, fy, oz + gz), floor);
            }
        }
        // Floating block displays (2 air blocks below each → visible from all angles).
        for (int i = 0; i < blocks.size(); i++) {
            int col = i % cols;
            int row = i / cols;
            level.setBlockAndUpdate(
                    new BlockPos(ox + col * SPACING, fy + FLOAT_ABOVE, oz + row * SPACING),
                    blocks.get(i).defaultBlockState());
        }

        // MACHINES, ALL RUNNING (one strip, four wired clusters — each exactly the survival hookup):
        //   A. Combustion Generator (coal) → pipe → Grinder (raw nerosium), Passive Generator feeding in.
        //   B. Creative Battery → pipe → Fuel Refinery (coal + blaze powder) → pipe → Fuel Tank.
        //   C. Creative Battery → pipe → Oxygen Generator — parked behind an OFF lever.
        //   D. Creative Battery → pipe → Terraformer + touching Hydration Module (glacite) and
        //      Terraform Monitor — parked behind an OFF lever (it WILL reshape the area when on).
        int sx = origin.getX() - 13; // machine strip → SOUTH
        int sz = origin.getZ() + 48;
        for (int dx = -1; dx <= 27; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                level.setBlockAndUpdate(new BlockPos(sx + dx, fy, sz + dz), floor);
            }
        }
        BlockState lever = Blocks.LEVER.defaultBlockState()
                .setValue(LeverBlock.FACE, AttachFace.FLOOR)
                .setValue(LeverBlock.FACING, Direction.EAST);

        // A: the classic first power line.
        level.setBlockAndUpdate(new BlockPos(sx, fy + 1, sz), ModBlocks.COMBUSTION_GENERATOR.get().defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(sx + 1, fy + 1, sz), ModBlocks.UNIVERSAL_PIPE.get().defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(sx + 2, fy + 1, sz), ModBlocks.NEROSIUM_GRINDER.get().defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(sx + 1, fy + 1, sz + 1), ModBlocks.PASSIVE_GENERATOR.get().defaultBlockState());
        if (level.getBlockEntity(new BlockPos(sx, fy + 1, sz)) instanceof CombustionGeneratorBlockEntity gen) {
            gen.setItem(CombustionGeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
        }
        if (level.getBlockEntity(new BlockPos(sx + 2, fy + 1, sz)) instanceof NerosiumGrinderBlockEntity grinder) {
            grinder.setItem(NerosiumGrinderBlockEntity.INPUT_SLOT, new ItemStack(ModItems.RAW_NEROSIUM.get(), 64));
        }

        // B: refining line — power in from the endless battery, fuel out into a Fuel Tank.
        int bx = sx + 6;
        level.setBlockAndUpdate(new BlockPos(bx, fy + 1, sz), za.co.neroland.nerolandcore.registry.ModBlocks.CREATIVE_BATTERY.get().defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(bx + 1, fy + 1, sz), ModBlocks.UNIVERSAL_PIPE.get().defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(bx + 2, fy + 1, sz), ModBlocks.FUEL_REFINERY.get().defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(bx + 3, fy + 1, sz), ModBlocks.UNIVERSAL_PIPE.get().defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(bx + 4, fy + 1, sz), ModBlocks.FUEL_TANK.get().defaultBlockState());
        setAllModes(level, new BlockPos(bx + 1, fy + 1, sz), Direction.WEST, PipeIoMode.IN);
        setAllModes(level, new BlockPos(bx + 1, fy + 1, sz), Direction.EAST, PipeIoMode.OUT);
        setAllModes(level, new BlockPos(bx + 3, fy + 1, sz), Direction.WEST, PipeIoMode.IN);
        setAllModes(level, new BlockPos(bx + 3, fy + 1, sz), Direction.EAST, PipeIoMode.OUT);
        if (level.getBlockEntity(new BlockPos(bx + 2, fy + 1, sz)) instanceof FuelRefineryBlockEntity refinery) {
            refinery.setItem(FuelRefineryBlockEntity.CARBON_SLOT, new ItemStack(Items.COAL, 64));
            refinery.setItem(FuelRefineryBlockEntity.CATALYST_SLOT, new ItemStack(Items.BLAZE_POWDER, 64));
        }

        // C: oxygen generator behind its lever (off until flipped — then the bubble forms).
        int cx = sx + 13;
        level.setBlockAndUpdate(new BlockPos(cx, fy + 1, sz), za.co.neroland.nerolandcore.registry.ModBlocks.CREATIVE_BATTERY.get().defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(cx + 1, fy + 1, sz), ModBlocks.UNIVERSAL_PIPE.get().defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(cx + 2, fy + 1, sz), ModBlocks.OXYGEN_GENERATOR.get().defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(cx + 3, fy + 1, sz), lever);
        setAllModes(level, new BlockPos(cx + 1, fy + 1, sz), Direction.WEST, PipeIoMode.IN);
        setAllModes(level, new BlockPos(cx + 1, fy + 1, sz), Direction.EAST, PipeIoMode.OUT);

        // D: terraformer cluster behind its lever, with the full deeper-terraform support crew.
        int tx = sx + 19;
        level.setBlockAndUpdate(new BlockPos(tx, fy + 1, sz), za.co.neroland.nerolandcore.registry.ModBlocks.CREATIVE_BATTERY.get().defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(tx + 1, fy + 1, sz), ModBlocks.UNIVERSAL_PIPE.get().defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(tx + 2, fy + 1, sz), ModBlocks.TERRAFORMER.get().defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(tx + 2, fy + 1, sz + 1), ModBlocks.HYDRATION_MODULE.get().defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(tx + 2, fy + 1, sz - 1), ModBlocks.TERRAFORM_MONITOR.get().defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(tx + 3, fy + 1, sz), lever);
        setAllModes(level, new BlockPos(tx + 1, fy + 1, sz), Direction.WEST, PipeIoMode.IN);
        setAllModes(level, new BlockPos(tx + 1, fy + 1, sz), Direction.EAST, PipeIoMode.OUT);
        if (level.getBlockEntity(new BlockPos(tx + 2, fy + 1, sz + 1)) instanceof HydrationModuleBlockEntity module) {
            module.setItem(HydrationModuleBlockEntity.INPUT_SLOT, new ItemStack(ModItems.GLACITE.get(), 64));
        }

        // FOUR LIVE PIPE SCENARIOS: creative source → 3 pipes → sink, one row per resource layer.
        // The source-touching face is set IN (pull-only — otherwise the pipe would void its buffer
        // back into the endless source) and the sink-touching face OUT, mirroring real Configurator use.
        int px = origin.getX() - 50; // pipe scenarios → WEST (rows run north-south → broadside from the centre)
        int pz = origin.getZ() + 5;
        Block[][] scenarioRows = {
                {za.co.neroland.nerolandcore.registry.ModBlocks.CREATIVE_BATTERY.get(), za.co.neroland.nerolandcore.registry.ModBlocks.BATTERY.get()},
                {za.co.neroland.nerolandcore.registry.ModBlocks.CREATIVE_FLUID_TANK.get(), za.co.neroland.nerolandcore.registry.ModBlocks.FLUID_TANK.get()},
                {za.co.neroland.nerolandcore.registry.ModBlocks.CREATIVE_GAS_TANK.get(), za.co.neroland.nerolandcore.registry.ModBlocks.GAS_TANK.get()},
                {za.co.neroland.nerolandcore.registry.ModBlocks.CREATIVE_ITEM_STORE.get(), za.co.neroland.nerolandcore.registry.ModBlocks.ITEM_STORE.get()},
        };
        for (int row = 0; row < scenarioRows.length; row++) {
            int rz = pz - row * 3;
            for (int dx = -1; dx <= 5; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    level.setBlockAndUpdate(new BlockPos(px + dx, fy, rz + dz), floor);
                }
            }
            level.setBlockAndUpdate(new BlockPos(px, fy + 1, rz), scenarioRows[row][0].defaultBlockState());
            for (int dx = 1; dx <= 3; dx++) {
                level.setBlockAndUpdate(new BlockPos(px + dx, fy + 1, rz),
                        ModBlocks.UNIVERSAL_PIPE.get().defaultBlockState());
            }
            level.setBlockAndUpdate(new BlockPos(px + 4, fy + 1, rz), scenarioRows[row][1].defaultBlockState());
            setAllModes(level, new BlockPos(px + 1, fy + 1, rz), Direction.WEST, PipeIoMode.IN);
            setAllModes(level, new BlockPos(px + 3, fy + 1, rz), Direction.EAST, PipeIoMode.OUT);
        }
        // Pre-configure the endless sources so the rows run on arrival.
        // (The multiloader Creative Fluid Tank is a fixed endless rocket_fuel source — it has no setSource.)
        if (level.getBlockEntity(new BlockPos(px, fy + 1, pz - 9)) instanceof CreativeItemStoreBlockEntity store) {
            store.setSource(new ItemStack(ModItems.NEROSIUM_INGOT.get()));
        }

        // Suit displays (every variant) + a LOADED Star Guide pedestal (book installed → hologram runs).
        int ax = origin.getX() - 40; // suits + Star Guide → NORTH-WEST (well clear of the pipes spoke)
        int az = origin.getZ() - 34;
        int suit0 = 0;
        int suit1 = SUIT_SPACING;
        int suit2 = SUIT_SPACING * 2;
        int suit3 = SUIT_SPACING * 3;
        int guideX = SUIT_SPACING * 4;
        for (int dx = -1; dx <= guideX + 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setBlockAndUpdate(new BlockPos(ax + dx, fy, az + dz), floor);
            }
        }
        spawnSuitStand(level, new BlockPos(ax + suit0, fy + 1, az), Component.translatable("command.nerospace.gallery.label.oxygen_suit"), SUIT_YAW,
                ModItems.OXYGEN_SUIT_HELMET.get(), ModItems.OXYGEN_SUIT_CHESTPLATE.get(),
                ModItems.OXYGEN_SUIT_LEGGINGS.get(), ModItems.OXYGEN_SUIT_BOOTS.get());
        spawnSuitStand(level, new BlockPos(ax + suit1, fy + 1, az), Component.translatable("command.nerospace.gallery.label.oxygen_suit_t2"), SUIT_YAW,
                ModItems.OXYGEN_SUIT_T2_HELMET.get(), ModItems.OXYGEN_SUIT_T2_CHESTPLATE.get(),
                ModItems.OXYGEN_SUIT_T2_LEGGINGS.get(), ModItems.OXYGEN_SUIT_T2_BOOTS.get());
        spawnSuitStand(level, new BlockPos(ax + suit2, fy + 1, az), Component.translatable("command.nerospace.gallery.label.thermal_suit"), SUIT_YAW,
                ModItems.OXYGEN_SUIT_HEAT_HELMET.get(), ModItems.OXYGEN_SUIT_HEAT_CHESTPLATE.get(),
                ModItems.OXYGEN_SUIT_HEAT_LEGGINGS.get(), ModItems.OXYGEN_SUIT_HEAT_BOOTS.get());
        spawnSuitStand(level, new BlockPos(ax + suit3, fy + 1, az), Component.translatable("command.nerospace.gallery.label.cryo_suit"), SUIT_YAW,
                ModItems.OXYGEN_SUIT_COLD_HELMET.get(), ModItems.OXYGEN_SUIT_COLD_CHESTPLATE.get(),
                ModItems.OXYGEN_SUIT_COLD_LEGGINGS.get(), ModItems.OXYGEN_SUIT_COLD_BOOTS.get());
        BlockPos guidePos = new BlockPos(ax + guideX, fy + 1, az);
        level.setBlockAndUpdate(guidePos, ModBlocks.STAR_GUIDE.get().defaultBlockState());
        if (level.getBlockEntity(guidePos)
                instanceof za.co.neroland.nerospace.progression.StarGuideBlockEntity guide) {
            guide.installBook(new ItemStack(ModItems.STAR_GUIDE_BOOK.get()));
        }

        // ROCKET ROW: every tier on the distinct pad footprint it now requires (the Phase-1 pad-tier
        // progression): T1 a SINGLE pad, T2 a full 3x3, T3 a 3x3 ringed with Station Wall, T4 the Heavy
        // Launch Complex (full 5x5 + a Launch Gantry on the ring — which renders as the animated service
        // tower). Each rocket arrives with full fuel AND a full onboard oxygen tank (open the console to
        // see the dual gauges).
        int rx = origin.getX() - 14; // rocket row → NORTH (the hero spoke)
        int rz0 = origin.getZ() - 49;
        for (int dx = -2; dx <= 31; dx++) {
            for (int dz = -3; dz <= 5; dz++) {
                level.setBlockAndUpdate(new BlockPos(rx + dx, fy, rz0 + dz), floor);
            }
        }
        BlockState pad = ModBlocks.ROCKET_LAUNCH_PAD.get().defaultBlockState();
        // T1 (a single pad — the entry tier — with the classic pad-side Fuel Tank).
        level.setBlockAndUpdate(new BlockPos(rx + 1, fy + 1, rz0 + 1), pad);
        level.setBlockAndUpdate(new BlockPos(rx + 3, fy + 1, rz0 + 1), ModBlocks.FUEL_TANK.get().defaultBlockState());
        spawnRocket(level, rx + 1, fy + 1, rz0 + 1, RocketTier.TIER_1);
        spawnLabelStand(level, new BlockPos(rx + 1, fy + 5, rz0 + 1), Component.translatable("command.nerospace.gallery.label.rocket_tier_1"));
        // T2 (3x3).
        fillPad(level, new BlockPos(rx + 8, fy + 1, rz0), 3, pad);
        spawnRocket(level, rx + 9, fy + 1, rz0 + 1, RocketTier.TIER_2);
        spawnLabelStand(level, new BlockPos(rx + 9, fy + 6, rz0 + 1), Component.translatable("command.nerospace.gallery.label.rocket_tier_2"));
        // T3 (3x3 ringed with Station Wall).
        fillPad(level, new BlockPos(rx + 16, fy + 1, rz0), 3, pad);
        BlockState wall = ModBlocks.STATION_WALL.get().defaultBlockState();
        for (int dx = -1; dx <= 3; dx++) {
            for (int dz = -1; dz <= 3; dz++) {
                if (dx == -1 || dx == 3 || dz == -1 || dz == 3) {
                    level.setBlockAndUpdate(new BlockPos(rx + 16 + dx, fy + 1, rz0 + dz), wall);
                }
            }
        }
        spawnRocket(level, rx + 17, fy + 1, rz0 + 1, RocketTier.TIER_3);
        spawnLabelStand(level, new BlockPos(rx + 17, fy + 6, rz0 + 1), Component.translatable("command.nerospace.gallery.label.rocket_tier_3"));
        // T4 (Heavy Launch Complex: 5x5 + gantry + fuel tank). The gantry renders as the animated
        // service tower — it reclines to release the rocket on launch, then swings back upright.
        fillPad(level, new BlockPos(rx + 24, fy + 1, rz0 - 1), 5, pad);
        level.setBlockAndUpdate(new BlockPos(rx + 23, fy + 1, rz0 + 1),
                ModBlocks.LAUNCH_GANTRY.get().defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(rx + 29, fy + 1, rz0 + 1),
                ModBlocks.FUEL_TANK.get().defaultBlockState());
        spawnRocket(level, rx + 26, fy + 1, rz0 + 1, RocketTier.TIER_4);
        spawnLabelStand(level, new BlockPos(rx + 26, fy + 7, rz0 + 1),
                Component.translatable("command.nerospace.gallery.label.rocket_tier_4"));

        // ROCKET O2 FUELLING (Phase 2): an endless Gas Tank → gas pipe → launch pad charges a docked
        // rocket's onboard oxygen tank live (the rocket starts with empty O2 so the line visibly fills it).
        int o2x = rx + 4;
        int o2z = rz0 + 4;
        level.setBlockAndUpdate(new BlockPos(o2x, fy + 1, o2z), za.co.neroland.nerolandcore.registry.ModBlocks.CREATIVE_GAS_TANK.get().defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(o2x + 1, fy + 1, o2z), ModBlocks.UNIVERSAL_PIPE.get().defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(o2x + 2, fy + 1, o2z), pad);
        setAllModes(level, new BlockPos(o2x + 1, fy + 1, o2z), Direction.WEST, PipeIoMode.IN);
        setAllModes(level, new BlockPos(o2x + 1, fy + 1, o2z), Direction.EAST, PipeIoMode.OUT);
        spawnRocket(level, o2x + 2, fy + 1, o2z, RocketTier.TIER_1, RocketTier.TIER_1.fuelCapacity(), 0);
        spawnLabelStand(level, new BlockPos(o2x + 1, fy + 4, o2z), Component.translatable("command.nerospace.gallery.label.rocket_oxygen"));

        // TRAVEL NODE (Phase 5): a launch pad doubles as a named pad-to-pad landing node — apply a Name
        // Tag to commission it (a rocket then lands here). Shown as a labelled standalone pad.
        int tnx = rx + 30;
        int tnz = rz0 + 4;
        level.setBlockAndUpdate(new BlockPos(tnx, fy + 1, tnz), pad);
        spawnLabelStand(level, new BlockPos(tnx, fy + 3, tnz),
                Component.translatable("command.nerospace.gallery.label.travel_node"));

        // FREIGHT (cargo rockets, 1.3.0): two Cargo Pads on 3x3 footprints, one strip north of the rocket
        // row. Pad A carries a docked, fuelled Cargo Rocket with a loaded hold, an adjacent Fuel Tank and a
        // rocket-fuel pipe into the pad's own buffer; pad B is the destination. Both are registered to the
        // player and A is routed to B (manual schedule), so opening A's GUI and pressing Launch flies the
        // cargo across the gallery and lands it on B. Pipes on B's far side show delivered cargo leaving.
        buildFreight(level, floor, player, rx, rz0 - 12, fy);

        // Creatures: one frozen (NoAI) row, each with its name on a floating label. The Mars natives (Salvage
        // Drone, Dust Strider) stand with the rover in the Mars Expedition cluster instead.
        int mx = origin.getX() + 18; // creatures → SOUTH-EAST
        int mz = origin.getZ() + 33;
        List<EntityType<? extends Mob>> creatures = List.of(
                ModEntities.XERTZ_STALKER.get(), ModEntities.QUARTZ_CRAWLER.get(),
                ModEntities.GREENLING.get(), ModEntities.CINDER_STALKER.get(),
                ModEntities.FROST_STRIDER.get(), ModEntities.ALIEN_VILLAGER.get(),
                // Terraform livestock (DEEPER_TERRAFORM_DESIGN.md §5).
                ModEntities.MEADOW_LOPER.get(), ModEntities.EMBER_STRUTTER.get(),
                ModEntities.WOOLLY_DRIFT.get(),
                // Megacity guardian (summon-only) and the 2.0 world natives.
                ModEntities.RUIN_WARDEN.get(),
                za.co.neroland.nerospace.world.planet.luna.LunaContent.MOON_HOPPER.get(),
                za.co.neroland.nerospace.world.planet.nyxara.NyxaraContent.DUSK_MOTH.get());
        for (int dx = -1; dx <= creatures.size() * 4; dx++) {
            for (int dz = -1; dz <= 3; dz++) {
                level.setBlockAndUpdate(new BlockPos(mx + dx, fy, mz + dz), floor);
            }
        }
        // One frozen (NoAI) row only — AI mobs wander, which breaks reproducible screenshots.
        for (int i = 0; i < creatures.size(); i++) {
            spawnShowcase(level, creatures.get(i), new BlockPos(mx + i * 4, fy + 1, mz + 1), true);
            spawnLabelStand(level, new BlockPos(mx + i * 4, fy + 3, mz + 1), creatures.get(i).getDescription());
        }

        // METEOR SITE (meteor-events-design.md): a small crater of meteor_rock around a loot-bearing
        // meteor_core, with a frozen meteor hovering above it (spins + trails for the shot). SW spoke.
        buildMeteorSite(level, floor, origin.getX() - 28, origin.getZ() + 30, fy);

        // QUARRY (MINER_DESIGN): two NE displays.
        //  1. Landmark-only — three landmarks in an L (shows the projected marker lasers).
        //  2. Fully operating — a powered quarry mid-dig: frame ring, drill head, a real pit forming.
        BlockState landmark = ModBlocks.QUARRY_LANDMARK.get().defaultBlockState();
        int lx = origin.getX() + 28; // landmark-only display (NE, nearer the centre)
        int lz = origin.getZ() - 40;
        for (int dx = -1; dx <= 7; dx++) {
            for (int dz = -1; dz <= 7; dz++) {
                level.setBlockAndUpdate(new BlockPos(lx + dx, fy, lz + dz), floor);
            }
        }
        level.setBlockAndUpdate(new BlockPos(lx, fy + 1, lz), landmark);
        level.setBlockAndUpdate(new BlockPos(lx + 6, fy + 1, lz), landmark);
        level.setBlockAndUpdate(new BlockPos(lx, fy + 1, lz + 6), landmark);

        // Live quarry claim/build displays: three sizes with landmarks + adjacent controllers. The
        // frames are intentionally NOT pre-placed; watch them build at the real animation speed.
        buildGalleryQuarry(level, floor, origin.getX() + 42, origin.getZ() - 40, fy, 8,
                Component.translatable("command.nerospace.gallery.label.miner", 1, 9, 9));
        buildGalleryQuarry(level, floor, origin.getX() + 58, origin.getZ() - 56, fy, 16,
                Component.translatable("command.nerospace.gallery.label.miner", 2, 17, 17));
        buildGalleryQuarry(level, floor, origin.getX() + 86, origin.getZ() - 72, fy, 32,
                Component.translatable("command.nerospace.gallery.label.miner", 3, 33, 33));

        // SOLAR ARRAYS (SOLAR_PANEL_DESIGN, SW bearing): one unit per tier, then a multi-unit seam-joined
        // field per tier (so the per-cell trackers reading as one surface is visible), plus a
        // battery → universal cable → panel hookup that lights the panel's power connector.
        buildSolarArrays(level, floor, origin.getX() - 50, origin.getZ() + 36, fy);

        // LAUNCH CONTROLLER (Launch Controller slices): the glowing "computer brain" console that builds
        // the pad from loaded blocks and previews it. Shown with materials loaded + the hologram ON (a
        // Tier-3 ghost footprint projects in front), facing south over open floor.
        int lcx = origin.getX() - 30;
        int lcz = origin.getZ() - 18;
        for (int dx = -1; dx <= 8; dx++) {
            for (int dz = -1; dz <= 9; dz++) {
                level.setBlockAndUpdate(new BlockPos(lcx + dx, fy, lcz + dz), floor);
            }
        }
        BlockPos ctrlPos = new BlockPos(lcx + 3, fy + 1, lcz);
        level.setBlockAndUpdate(ctrlPos, ModBlocks.LAUNCH_CONTROLLER.get().defaultBlockState()
                .setValue(LaunchControllerBlock.FACING, Direction.SOUTH));
        LaunchControllerBlock.assemble(level, ctrlPos, Direction.SOUTH);
        if (level.getBlockEntity(ctrlPos) instanceof LaunchControllerBlockEntity controller) {
            controller.getInputs().setItem(0, new ItemStack(ModBlocks.ROCKET_LAUNCH_PAD.get().asItem(), 64));
            controller.getInputs().setItem(1, new ItemStack(ModBlocks.STATION_WALL.get().asItem(), 64));
            controller.getInputs().setItem(2, new ItemStack(ModBlocks.LAUNCH_GANTRY.get().asItem(), 4));
            controller.setTargetTier(3);
            if (!controller.isHologram()) {
                controller.toggleHologram();
            }
        }
        spawnLabelStand(level, new BlockPos(lcx + 3, fy + 4, lcz),
                Component.translatable("command.nerospace.gallery.label.launch_controller"));

        // MARS EXPEDITION (EAST-SOUTH-EAST, past the end of the creature row): the rover on Martian ground with
        // the Mars natives and a held dust devil.
        buildMarsExpedition(level, origin.getX() + 84, origin.getZ() + 38, fy);

        // CREW + DOCKING (NORTH-NORTH-EAST, beyond the rocket row): a Tier 2 rocket with all four seats taken,
        // beside a full 3x3 Docking Ring.
        buildCrewAndDock(level, floor, origin.getX() + 16, origin.getZ() - 64, fy);

        // ITEM WALL (WEST-SOUTH-WEST, between the pipe rows and the solar arrays): every non-block item in
        // glow item frames, grouped by category.
        int wallItems = buildItemWall(level, floor, origin.getX() - 54, origin.getZ() + 8, fy);

        source.sendSuccess(() -> Component.translatable("command.nerospace.gallery.built", blocks.size()), false);
        source.sendSuccess(() -> Component.translatable("command.nerospace.gallery.built_extras", wallItems), false);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Wipe the gallery built at the player's feet so a rebuild (or the screenshot harness) doesn't
     * stack duplicates. Clears the whole footprint to air from the floor layer ({@code origin.y}) up,
     * leaving the natural ground at {@code origin.y - 1} intact, and removes every non-player entity
     * in the box (rockets, suit stands, creatures). Run it standing where you ran {@code gallery}.
     */
    private static int clearGallery(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.nerospace.player_only"));
            return 0;
        }
        if (!player.getAbilities().instabuild) {
            source.sendFailure(Component.translatable("command.nerospace.gallery.creative_only"));
            return 0;
        }
        ServerLevel level = player.level();
        BlockPos origin = player.blockPosition();
        int ox = origin.getX();
        int oy = origin.getY();
        int oz = origin.getZ();

        // Footprint of the ROTUNDA buildGallery() (clusters sit ~48 out on N/S/E/W/SE/NW bearings, plus the
        // Mars Expedition ESE, crew + docking NNE and the item wall WSW) plus margin, so the clear covers every
        // cluster — else reruns stack creatures/rockets/stands/item frames.
        // The floor sits at oy, so clearing oy..topY to air restores the original flat ground at oy-1.
        int minX = ox - 56;
        int maxX = ox + 126;
        int minZ = oz - 84;
        int maxZ = oz + 56;
        int topY = oy + 16;

        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int cleared = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = oy; y <= topY; y++) {
                    cursor.set(x, y, z);
                    if (!level.getBlockState(cursor).isAir()) {
                        level.setBlock(cursor, air, 2); // flag 2 = notify clients, skip neighbour cascade
                        cleared++;
                    }
                }
            }
        }

        // Remove the spawned entities (rockets, armour stands, creatures) — everything but players.
        AABB box = new AABB(minX, oy - 1, minZ, maxX + 1, topY + 4, maxZ + 1);
        int removed = 0;
        for (Entity entity : level.getEntitiesOfClass(Entity.class, box, e -> !(e instanceof Player))) {
            entity.discard();
            removed++;
        }

        int clearedBlocks = cleared;
        int removedEntities = removed;
        source.sendSuccess(() -> Component.translatable("command.nerospace.gallery.cleared",
                clearedBlocks, removedEntities), false);
        return Command.SINGLE_SUCCESS;
    }

    /** Item-frame rows on the item wall (frames fill each group's column top-down). */
    private static final int WALL_ROWS = 6;
    /** Empty columns between two item groups, so the floating group labels don't run together. */
    private static final int WALL_GAP = 2;

    /**
     * The Mars Expedition: a red-regolith pad (sandstone rim) centred on {@code (cx, cz)} with a fully charged rover
     * carrying some Mars cargo, a frozen Salvage Drone and Dust Strider, and a held Dust Devil on its pit. The rover
     * has no owner (the gallery records no player), so anyone may ride it or pack it up.
     */
    private static void buildMarsExpedition(ServerLevel level, int cx, int cz, int fy) {
        BlockState rim = za.co.neroland.nerospace.world.planet.mars.MarsContent.MARTIAN_SANDSTONE.get().defaultBlockState();
        BlockState regolith = za.co.neroland.nerospace.world.planet.mars.MarsContent.RED_REGOLITH.get().defaultBlockState();
        for (int dx = -12; dx <= 12; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                boolean edge = Math.abs(dx) == 12 || Math.abs(dz) == 6;
                level.setBlockAndUpdate(new BlockPos(cx + dx, fy, cz + dz), edge ? rim : regolith);
            }
        }

        // The rover, parked facing west (towards the gallery centre), full charge, cargo aboard.
        za.co.neroland.nerospace.entity.RoverEntity rover =
                ModEntities.ROVER.get().create(level, EntitySpawnReason.COMMAND);
        if (rover != null) {
            rover.snapTo(cx - 6 + 0.5D, fy + 1, cz + 0.5D, 90.0F, 0.0F);
            rover.setEnergy(za.co.neroland.nerospace.entity.RoverEntity.capacity());
            rover.cargo().setItem(0, new ItemStack(za.co.neroland.nerospace.world.planet.mars.MarsContent.RAW_HEMATITE.get(), 32));
            rover.cargo().setItem(1, new ItemStack(za.co.neroland.nerospace.world.planet.mars.MarsContent.RUST_PIGMENT.get(), 16));
            rover.cargo().setItem(2, new ItemStack(za.co.neroland.nerospace.world.planet.mars.MarsContent.PERCHLORATE_OXIDISER.get(), 8));
            level.addFreshEntity(rover);
        }
        spawnLabelStand(level, new BlockPos(cx - 6, fy + 3, cz),
                Component.translatable("command.nerospace.gallery.label.rover"));

        // Mars natives, frozen like the creature row.
        EntityType<? extends Mob> drone = za.co.neroland.nerospace.world.planet.mars.MarsContent.SALVAGE_DRONE.get();
        EntityType<? extends Mob> strider = za.co.neroland.nerospace.world.planet.mars.MarsContent.DUST_STRIDER.get();
        spawnShowcase(level, drone, new BlockPos(cx, fy + 1, cz - 2), true);
        spawnLabelStand(level, new BlockPos(cx, fy + 3, cz - 2), drone.getDescription());
        spawnShowcase(level, strider, new BlockPos(cx + 3, fy + 1, cz + 2), true);
        spawnLabelStand(level, new BlockPos(cx + 3, fy + 3, cz + 2), strider.getDescription());

        // A dust devil held over its pit: it swirls but never wanders, tosses items or dies out.
        level.setBlockAndUpdate(new BlockPos(cx + 8, fy, cz),
                za.co.neroland.nerospace.world.planet.mars.MarsContent.DUST_DEVIL_PIT.get().defaultBlockState());
        za.co.neroland.nerospace.world.planet.mars.entity.DustDevil.spawnFrozen(level, cx + 8.5D, fy + 1, cz + 0.5D);
        spawnLabelStand(level, new BlockPos(cx + 8, fy + 4, cz),
                za.co.neroland.nerospace.world.planet.mars.MarsContent.DUST_DEVIL.get().getDescription());

        spawnLabelStand(level, new BlockPos(cx, fy + 5, cz - 5),
                Component.translatable("command.nerospace.gallery.label.mars_expedition"));
    }

    /**
     * A crewed Tier 2 rocket on its 3x3 pad — the four seats taken by suited armour stands (pilot first), so the
     * side-by-side seating reads through the hull — and a full 3x3 Docking Ring beside it (an empty dock). The
     * stands are forced aboard: a rocket only admits players, and Crew counts players, so the console reads an
     * empty crew and nobody can launch it. {@code (x, z)} is the pad's north-west corner.
     */
    private static void buildCrewAndDock(ServerLevel level, BlockState floor, int x, int z, int fy) {
        for (int dx = -3; dx <= 13; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                level.setBlockAndUpdate(new BlockPos(x + dx, fy, z + dz), floor);
            }
        }
        BlockState pad = ModBlocks.ROCKET_LAUNCH_PAD.get().defaultBlockState();
        fillPad(level, new BlockPos(x, fy + 1, z - 1), 3, pad);
        RocketEntity rocket = new RocketEntity(level,
                x + 1.5D, fy + 1 + RocketLaunchPadBlock.SURFACE_HEIGHT, z + 0.5D, RocketTier.TIER_2);
        rocket.addFuel(RocketTier.TIER_2.fuelCapacity());
        rocket.addOxygen(RocketTier.TIER_2.oxygenCapacity());
        level.addFreshEntity(rocket);
        Item[][] suits = {
                {ModItems.OXYGEN_SUIT_HELMET.get(), ModItems.OXYGEN_SUIT_CHESTPLATE.get(),
                        ModItems.OXYGEN_SUIT_LEGGINGS.get(), ModItems.OXYGEN_SUIT_BOOTS.get()},
                {ModItems.OXYGEN_SUIT_T2_HELMET.get(), ModItems.OXYGEN_SUIT_T2_CHESTPLATE.get(),
                        ModItems.OXYGEN_SUIT_T2_LEGGINGS.get(), ModItems.OXYGEN_SUIT_T2_BOOTS.get()},
                {ModItems.OXYGEN_SUIT_HEAT_HELMET.get(), ModItems.OXYGEN_SUIT_HEAT_CHESTPLATE.get(),
                        ModItems.OXYGEN_SUIT_HEAT_LEGGINGS.get(), ModItems.OXYGEN_SUIT_HEAT_BOOTS.get()},
                {ModItems.OXYGEN_SUIT_COLD_HELMET.get(), ModItems.OXYGEN_SUIT_COLD_CHESTPLATE.get(),
                        ModItems.OXYGEN_SUIT_COLD_LEGGINGS.get(), ModItems.OXYGEN_SUIT_COLD_BOOTS.get()},
        };
        for (Item[] suit : suits) {
            ArmorStand crew = new ArmorStand(level, rocket.getX(), rocket.getY(), rocket.getZ());
            crew.setItemSlot(EquipmentSlot.HEAD, new ItemStack(suit[0]));
            crew.setItemSlot(EquipmentSlot.CHEST, new ItemStack(suit[1]));
            crew.setItemSlot(EquipmentSlot.LEGS, new ItemStack(suit[2]));
            crew.setItemSlot(EquipmentSlot.FEET, new ItemStack(suit[3]));
            makeInvulnerable(crew);
            level.addFreshEntity(crew);
            //? if >=1.21.9 {
            crew.startRiding(rocket, true, false);
            //?} else {
            /*crew.startRiding(rocket, true);
            *///?}
        }
        spawnLabelStand(level, new BlockPos(x + 1, fy + 6, z),
                Component.translatable("command.nerospace.gallery.label.crew_rocket"));

        // A full Docking Ring, empty: placed by hand in the station dimension it is a free dock any rocket may pick.
        BlockPos ringCentre = new BlockPos(x + 9, fy + 1, z);
        za.co.neroland.nerospace.rocket.DockingRingBlock.placeRing(level, ringCentre, Direction.SOUTH, null);
        spawnLabelStand(level, ringCentre.above(2),
                Component.translatable("command.nerospace.gallery.label.docking_ring"));
    }

    /**
     * The item wall: a Station Wall facing east at {@code x = wx}, starting at {@code z = wz0} and running south,
     * with glow item frames holding every Nerospace item that is not a block (blocks are on the block grid; the
     * suit pieces are worn on the suit stands). Groups run left to right — materials, food, tools and gear,
     * vehicles, creative travel devices, spawn eggs — each under a floating label. Food is anything edible; anything
     * not named in a group lands in materials, so a new item still appears. Returns how many items it shows.
     */
    private static int buildItemWall(ServerLevel level, BlockState floor, int wx, int wz0, int fy) {
        java.util.Set<Item> wornOnStands = java.util.Set.of(
                ModItems.OXYGEN_SUIT_HELMET.get(), ModItems.OXYGEN_SUIT_CHESTPLATE.get(),
                ModItems.OXYGEN_SUIT_LEGGINGS.get(), ModItems.OXYGEN_SUIT_BOOTS.get(),
                ModItems.OXYGEN_SUIT_T2_HELMET.get(), ModItems.OXYGEN_SUIT_T2_CHESTPLATE.get(),
                ModItems.OXYGEN_SUIT_T2_LEGGINGS.get(), ModItems.OXYGEN_SUIT_T2_BOOTS.get(),
                ModItems.OXYGEN_SUIT_HEAT_HELMET.get(), ModItems.OXYGEN_SUIT_HEAT_CHESTPLATE.get(),
                ModItems.OXYGEN_SUIT_HEAT_LEGGINGS.get(), ModItems.OXYGEN_SUIT_HEAT_BOOTS.get(),
                ModItems.OXYGEN_SUIT_COLD_HELMET.get(), ModItems.OXYGEN_SUIT_COLD_CHESTPLATE.get(),
                ModItems.OXYGEN_SUIT_COLD_LEGGINGS.get(), ModItems.OXYGEN_SUIT_COLD_BOOTS.get());
        java.util.Set<Item> tools = java.util.Set.of(ModItems.NEROSIUM_PICKAXE.get(), ModItems.ROCKET_FUEL_BUCKET.get(),
                ModItems.XERTZ_RESONATOR.get(), ModItems.CONFIGURATOR.get(), ModItems.PIPE_FILTER.get(),
                ModItems.ADVANCED_PIPE_FILTER.get(), ModItems.SPEED_UPGRADE.get(), ModItems.CAPACITY_UPGRADE.get(),
                ModItems.SPEED_MODULE.get(), ModItems.EFFICIENCY_MODULE.get(), ModItems.FORTUNE_MODULE.get(),
                ModItems.SILK_TOUCH_MODULE.get(), ModItems.EVAPORATOR_MODULE.get(), ModItems.STAR_GUIDE_BOOK.get(),
                ModItems.STATION_CHARTER.get(), ModItems.METEOR_TRACKER.get(), ModItems.RAD_LINING.get(),
                za.co.neroland.nerospace.world.planet.greenxertz.GreenxertzContent.SPORE_BOMB.get());
        java.util.Set<Item> vehicles = java.util.Set.of(ModItems.ROCKET_TIER_1.get(), ModItems.ROCKET_TIER_2.get(),
                ModItems.ROCKET_TIER_3.get(), ModItems.ROCKET_TIER_4.get(), ModItems.CARGO_ROCKET.get(),
                ModItems.CARGO_CRATE.get(), za.co.neroland.nerospace.world.planet.mars.MarsContent.ROVER.get());
        java.util.Set<Item> creative = java.util.Set.of(ModItems.STATION_COMPASS.get(), ModItems.GREENXERTZ_COMPASS.get(),
                ModItems.CINDARA_COMPASS.get(), ModItems.GLACIRA_COMPASS.get(), ModItems.LUNA_COMPASS.get(),
                ModItems.MARS_COMPASS.get(), ModItems.NYXARA_COMPASS.get(), ModItems.METEOR_CALLER.get());

        List<Item> materialsGroup = new ArrayList<>();
        List<Item> foodGroup = new ArrayList<>();
        List<Item> toolsGroup = new ArrayList<>();
        List<Item> vehiclesGroup = new ArrayList<>();
        List<Item> creativeGroup = new ArrayList<>();
        List<Item> eggsGroup = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            if (!NerospaceCommon.MOD_ID.equals(id.getNamespace()) || item instanceof BlockItem
                    || wornOnStands.contains(item)) {
                continue;
            }
            if (id.getPath().endsWith("_spawn_egg")) {
                eggsGroup.add(item);
            } else if (item.components().has(net.minecraft.core.component.DataComponents.FOOD)) {
                foodGroup.add(item);
            } else if (tools.contains(item)) {
                toolsGroup.add(item);
            } else if (vehicles.contains(item)) {
                vehiclesGroup.add(item);
            } else if (creative.contains(item)) {
                creativeGroup.add(item);
            } else {
                materialsGroup.add(item);
            }
        }
        List<List<Item>> groups = List.of(materialsGroup, foodGroup, toolsGroup, vehiclesGroup, creativeGroup, eggsGroup);
        String[] labels = {"items_materials", "items_food", "items_tools", "items_vehicles", "items_creative",
                "items_spawn_eggs"};

        int width = 0;
        for (List<Item> group : groups) {
            if (!group.isEmpty()) {
                width += (group.size() + WALL_ROWS - 1) / WALL_ROWS + WALL_GAP;
            }
        }
        width = Math.max(1, width - WALL_GAP);
        for (int dx = -1; dx <= 5; dx++) {
            for (int dz = -2; dz <= width + 1; dz++) {
                level.setBlockAndUpdate(new BlockPos(wx + dx, fy, wz0 + dz), floor);
            }
        }
        BlockState wall = ModBlocks.STATION_WALL.get().defaultBlockState();
        for (int dz = -1; dz <= width; dz++) {
            for (int dy = 1; dy <= WALL_ROWS + 1; dy++) {
                level.setBlockAndUpdate(new BlockPos(wx, fy + dy, wz0 + dz), wall);
            }
        }

        int shown = 0;
        int z = wz0;
        int labelIndex = 0;
        for (int g = 0; g < groups.size(); g++) {
            List<Item> group = groups.get(g);
            if (group.isEmpty()) {
                continue;
            }
            int cols = (group.size() + WALL_ROWS - 1) / WALL_ROWS;
            for (int i = 0; i < group.size(); i++) {
                BlockPos at = new BlockPos(wx + 1, fy + WALL_ROWS - i % WALL_ROWS, z + i / WALL_ROWS);
                GlowItemFrame frame = new GlowItemFrame(level, at, Direction.EAST);
                frame.setItem(new ItemStack(group.get(i)));
                makeInvulnerable(frame);
                level.addFreshEntity(frame);
                shown++;
            }
            // Alternate the label heights so neighbouring group names never overlap.
            spawnLabelStand(level, new BlockPos(wx + 2, fy + WALL_ROWS + (labelIndex % 2), z + (cols - 1) / 2),
                    Component.translatable("command.nerospace.gallery.label." + labels[g]));
            labelIndex++;
            z += cols + WALL_GAP;
        }
        spawnLabelStand(level, new BlockPos(wx + 3, fy + WALL_ROWS + 3, wz0 + width / 2),
                Component.translatable("command.nerospace.gallery.label.item_wall"));
        return shown;
    }

    /**
     * Solar showcase (SW). Front row: one of each tier as a single unit — a 1×1 T1, a 2×2 T2 (one big
     * panel) and a 3×3 T3 (one big panel). Behind it: several units of each tier side by side — nine T1
     * panels (a seam-joined 3×3 field), four T2 units and two T3 units — so multiple arrays tiling is
     * visible. A Tier 3 panel → Universal Pipe → Battery line shows live charging and the dynamic cable
     * connector (the panel grows a stub toward the cable so the hookup butts up with no gap). Built at {@code (baseX,
     * baseZ)}, extending east (+X) and south (+Z); panels sit on the floor with the tracking deck above.
     */
    private static void buildSolarArrays(ServerLevel level, BlockState floor, int baseX, int baseZ, int fy) {
        int sy = fy + 1;
        for (int dx = -2; dx <= 20; dx++) {
            for (int dz = -2; dz <= 10; dz++) {
                level.setBlockAndUpdate(new BlockPos(baseX + dx, fy, baseZ + dz), floor);
            }
        }

        // Front row: one of each tier (multiblock anchors auto-fill their N×N footprint via onPlace).
        placeSolar(level, ModBlocks.SOLAR_PANEL.get(), baseX, sy, baseZ);
        placeSolar(level, ModBlocks.SOLAR_PANEL_T2.get(), baseX + 2, sy, baseZ); // fills +2..3
        placeSolar(level, ModBlocks.SOLAR_PANEL_T3.get(), baseX + 5, sy, baseZ); // fills +5..7

        // Cable hookup: T3 panel -> Universal Pipe -> Battery (solar output charges the battery).
        level.setBlockAndUpdate(new BlockPos(baseX + 10, sy, baseZ),
                za.co.neroland.nerolandcore.registry.ModBlocks.BATTERY.get().defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(baseX + 11, sy, baseZ),
                ModBlocks.UNIVERSAL_PIPE.get().defaultBlockState());
        placeSolar(level, ModBlocks.SOLAR_PANEL_T3.get(), baseX + 12, sy, baseZ);
        setAllModes(level, new BlockPos(baseX + 11, sy, baseZ), Direction.EAST, PipeIoMode.IN);
        setAllModes(level, new BlockPos(baseX + 11, sy, baseZ), Direction.WEST, PipeIoMode.OUT);

        // Multi-unit seam-joined fields, set back (+Z) so footprints don't touch the front row.
        // T1: a 3x3 field of nine single panels → one continuous tracking surface.
        for (int dx = 0; dx <= 2; dx++) {
            for (int dz = 4; dz <= 6; dz++) {
                placeSolar(level, ModBlocks.SOLAR_PANEL.get(), baseX + dx, sy, baseZ + dz);
            }
        }
        // T2: four 2x2 units → a 4x4 field.
        placeSolar(level, ModBlocks.SOLAR_PANEL_T2.get(), baseX + 5, sy, baseZ + 4);
        placeSolar(level, ModBlocks.SOLAR_PANEL_T2.get(), baseX + 7, sy, baseZ + 4);
        placeSolar(level, ModBlocks.SOLAR_PANEL_T2.get(), baseX + 5, sy, baseZ + 6);
        placeSolar(level, ModBlocks.SOLAR_PANEL_T2.get(), baseX + 7, sy, baseZ + 6);
        // T3: two 3x3 units → a 6x3 field.
        placeSolar(level, ModBlocks.SOLAR_PANEL_T3.get(), baseX + 11, sy, baseZ + 4); // fills +11..13
        placeSolar(level, ModBlocks.SOLAR_PANEL_T3.get(), baseX + 14, sy, baseZ + 4); // fills +14..16
    }

    /** Place a solar panel anchor; multiblock tiers auto-expand their footprint in {@code onPlace}. */
    private static void placeSolar(ServerLevel level, Block block, int x, int y, int z) {
        level.setBlockAndUpdate(new BlockPos(x, y, z), block.defaultBlockState());
    }

    /**
     * Build one powered gallery quarry claim: landmarks form a {@code (side+1) x (side+1)} region,
     * with the controller one block outside the west frame edge and enough casings to build the ring.
     */
    private static void buildGalleryQuarry(ServerLevel level, BlockState floor, int qx, int qz, int fy,
            int side, Component label) {
        int refY = fy + 1;
        int mid = side / 2;
        for (int dx = -5; dx <= side + 1; dx++) {   // ground: power pad (west) + under the region
            for (int dz = -1; dz <= side; dz++) {
                level.setBlockAndUpdate(new BlockPos(qx + dx, fy, qz + dz), floor);
            }
        }
        BlockState landmark = ModBlocks.QUARRY_LANDMARK.get().defaultBlockState();
        level.setBlockAndUpdate(new BlockPos(qx, refY, qz), landmark);
        level.setBlockAndUpdate(new BlockPos(qx + side, refY, qz), landmark);
        level.setBlockAndUpdate(new BlockPos(qx, refY, qz + side), landmark);

        BlockPos quarryPos = new BlockPos(qx - 1, refY, qz + mid);
        BlockPos pipePos = new BlockPos(qx - 2, refY, qz + mid);
        level.setBlockAndUpdate(new BlockPos(qx - 3, refY, qz + mid),
                za.co.neroland.nerolandcore.registry.ModBlocks.CREATIVE_BATTERY.get().defaultBlockState());
        level.setBlockAndUpdate(pipePos, ModBlocks.UNIVERSAL_PIPE.get().defaultBlockState());
        level.setBlockAndUpdate(quarryPos, ModBlocks.QUARRY_CONTROLLER.get().defaultBlockState());
        setAllModes(level, pipePos, Direction.WEST, PipeIoMode.IN);
        setAllModes(level, pipePos, Direction.EAST, PipeIoMode.OUT);
        // Keep the showcase quarry digging forever instead of clogging its 12-slot output buffer: the
        // quarry-facing (EAST) face also PULLS the mined items into the pipe (energy still pushes IN on
        // that same face — modes are per-resource-layer), and a Trash Can on the pipe's south face
        // receives and voids them. Without an item sink the quarry pauses "buffer_full" mid-dig.
        BlockPos trashPos = new BlockPos(qx - 2, refY, qz + mid + 1);
        level.setBlockAndUpdate(trashPos, za.co.neroland.nerolandcore.registry.ModBlocks.TRASH_CAN.get().defaultBlockState());
        setMode(level, pipePos, Direction.EAST, PipeResourceType.ITEM, PipeIoMode.IN);
        setMode(level, pipePos, Direction.SOUTH, PipeResourceType.ITEM, PipeIoMode.OUT);
        if (level.getBlockEntity(quarryPos) instanceof QuarryControllerBlockEntity quarry) {
            for (int i = 0; i < QuarryControllerBlockEntity.FRAME_SLOTS; i++) {
                quarry.setItem(i, new ItemStack(ModItems.FRAME_CASING.get(), 64));
            }
        }
        spawnLabelStand(level, new BlockPos(qx + mid, fy + 1, qz - 1), label);
    }

    /**
     * A showcase meteor crash site: a 7x7 floor pad, a 5x5 {@code meteor_rock} crater floor with a
     * raised rim, a loot-pre-rolled {@code meteor_core} nestled in the centre, and a frozen
     * {@link FallingMeteorEntity} hovering above (spins + trails, but never falls — gallery only).
     */
    private static void buildMeteorSite(ServerLevel level, BlockState floor, int cx, int cz, int fy) {
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                level.setBlockAndUpdate(new BlockPos(cx + dx, fy, cz + dz), floor);
            }
        }
        BlockState rock = ModBlocks.METEOR_ROCK.get().defaultBlockState();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                level.setBlockAndUpdate(new BlockPos(cx + dx, fy + 1, cz + dz), rock); // crater floor
                if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
                    level.setBlockAndUpdate(new BlockPos(cx + dx, fy + 2, cz + dz), rock); // raised rim
                }
            }
        }
        BlockPos corePos = new BlockPos(cx, fy + 2, cz);
        level.setBlockAndUpdate(corePos, ModBlocks.METEOR_CORE.get().defaultBlockState());
        if (level.getBlockEntity(corePos) instanceof MeteorCoreBlockEntity core) {
            core.generateLoot(level.getRandom().nextLong());
        }
        FallingMeteorEntity.spawnFrozen(level, cx + 0.5D, fy + 11, cz + 0.5D);
    }

    /** A full {@code size x size} square of launch pads with min-corner {@code corner}. */
    /**
     * The cargo-rocket exhibit: pad A (origin) at {@code (ax, az)} and pad B (destination) 16 blocks east,
     * each a Cargo Pad in the centre of a 3x3 launch-pad footprint. A gets a fuelled Cargo Rocket, a hold
     * full of nerosteel, a Fuel Tank on the ring and a rocket-fuel pipe into its buffer; B gets an
     * extraction pipe into a chest so delivered cargo visibly leaves. Both pads are registered to the
     * running player (so they appear in the GUI's destination list) and A is routed to B.
     */
    private static void buildFreight(ServerLevel level, BlockState floor, ServerPlayer player, int ax, int az, int fy) {
        for (int dx = -2; dx <= 22; dx++) {
            for (int dz = -3; dz <= 4; dz++) {
                level.setBlockAndUpdate(new BlockPos(ax + dx, fy, az + dz), floor);
            }
        }
        BlockState pad = ModBlocks.ROCKET_LAUNCH_PAD.get().defaultBlockState();
        BlockState cargoPad = ModBlocks.CARGO_PAD.get().defaultBlockState();
        int bx = ax + 16;

        // Pad A: 3x3 with the Cargo Pad in the centre, Fuel Tank on the west edge, fuel pipe from the north.
        fillPad(level, new BlockPos(ax, fy + 1, az), 3, pad);
        BlockPos aPos = new BlockPos(ax + 1, fy + 1, az + 1);
        level.setBlockAndUpdate(aPos, cargoPad);
        level.setBlockAndUpdate(new BlockPos(ax - 1, fy + 1, az + 1), ModBlocks.FUEL_TANK.get().defaultBlockState());
        if (level.getBlockEntity(new BlockPos(ax - 1, fy + 1, az + 1))
                instanceof za.co.neroland.nerospace.machine.FuelTankBlockEntity tank) {
            tank.getTank().fill(za.co.neroland.nerospace.fluid.ModFluids.ROCKET_FUEL.get(),
                    za.co.neroland.nerospace.machine.FuelTankBlockEntity.CAPACITY, false);
        }
        // Rocket fuel piped straight into the Cargo Pad's own buffer: an endless Core fluid tank of rocket
        // fuel → pipe → pad (the pipe runs over the pad ring at Y+2 so the 3x3 stays intact).
        BlockPos srcPos = new BlockPos(ax + 1, fy + 2, az - 2);
        level.setBlockAndUpdate(srcPos, za.co.neroland.nerolandcore.registry.ModBlocks.CREATIVE_FLUID_TANK.get().defaultBlockState());
        if (level.getBlockEntity(srcPos) instanceof za.co.neroland.nerolandcore.storage.CreativeFluidTankBlockEntity src) {
            src.setSource(za.co.neroland.nerospace.fluid.ModFluids.ROCKET_FUEL.get());
        }
        BlockPos pipeA = new BlockPos(ax + 1, fy + 2, az - 1);
        BlockPos pipeB = new BlockPos(ax + 1, fy + 2, az);
        level.setBlockAndUpdate(pipeA, ModBlocks.UNIVERSAL_PIPE.get().defaultBlockState());
        level.setBlockAndUpdate(pipeB, ModBlocks.UNIVERSAL_PIPE.get().defaultBlockState());
        setAllModes(level, pipeA, Direction.NORTH, PipeIoMode.IN);
        setAllModes(level, pipeB, Direction.DOWN, PipeIoMode.OUT);

        // Pad B: 3x3 + Cargo Pad, with an extraction pipe on its east side into a chest.
        fillPad(level, new BlockPos(bx, fy + 1, az), 3, pad);
        BlockPos bPos = new BlockPos(bx + 1, fy + 1, az + 1);
        level.setBlockAndUpdate(bPos, cargoPad);
        BlockPos outPipe = new BlockPos(bx + 1, fy + 2, az + 1);
        BlockPos outPipe2 = new BlockPos(bx + 1, fy + 2, az + 2);
        level.setBlockAndUpdate(outPipe, ModBlocks.UNIVERSAL_PIPE.get().defaultBlockState());
        level.setBlockAndUpdate(outPipe2, ModBlocks.UNIVERSAL_PIPE.get().defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(bx + 1, fy + 2, az + 3), Blocks.CHEST.defaultBlockState());
        setAllModes(level, outPipe, Direction.DOWN, PipeIoMode.IN);
        setAllModes(level, outPipe2, Direction.SOUTH, PipeIoMode.OUT);

        // Registry: both pads owned by the player, A → B, manual schedule. The block entities tick and
        // adopt these records by position, exactly as a reloaded world would.
        za.co.neroland.nerospace.route.RouteRegistry registry =
                za.co.neroland.nerospace.route.RouteRegistry.get(level.getServer());
        za.co.neroland.nerospace.route.PadRecord a = registry.registerPad(level.dimension(), aPos, player.getUUID(), "Gallery Dock A");
        za.co.neroland.nerospace.route.PadRecord b = registry.registerPad(level.dimension(), bPos, player.getUUID(), "Gallery Dock B");
        if (a != null && b != null) {
            registry.setRoute(a.id(), b.id());
        }

        // The cargo rocket, fuelled, and a hold of nerosteel so the quote line shows a real trip.
        za.co.neroland.nerospace.route.CargoRocketEntity rocket =
                za.co.neroland.nerospace.route.CargoRocketEntity.standOn(level, aPos);
        rocket.addFuel(za.co.neroland.nerospace.route.CargoRocketEntity.FUEL_CAPACITY);
        level.addFreshEntity(rocket);
        if (level.getBlockEntity(aPos) instanceof za.co.neroland.nerospace.route.CargoPadBlockEntity padA) {
            for (int slot = 0; slot < 6; slot++) {
                padA.setItem(slot, new ItemStack(ModItems.NEROSTEEL_INGOT.get(), 64));
            }
        }

        spawnLabelStand(level, new BlockPos(ax + 1, fy + 6, az + 1),
                Component.translatable("command.nerospace.gallery.label.cargo_pad_a"));
        spawnLabelStand(level, new BlockPos(bx + 1, fy + 5, az + 1),
                Component.translatable("command.nerospace.gallery.label.cargo_pad_b"));
        spawnLabelStand(level, new BlockPos(ax + 8, fy + 4, az + 1),
                Component.translatable("command.nerospace.gallery.label.freight"));
    }

    private static void fillPad(ServerLevel level, BlockPos corner, int size, BlockState pad) {
        for (int dx = 0; dx < size; dx++) {
            for (int dz = 0; dz < size; dz++) {
                level.setBlockAndUpdate(corner.offset(dx, 0, dz), pad);
            }
        }
    }

    /** A rocket standing on the pad surface of the pad block at {@code (x, y, z)}, tanks topped off. */
    private static void spawnRocket(ServerLevel level, int x, int y, int z, RocketTier tier) {
        spawnRocket(level, x, y, z, tier, tier.fuelCapacity(), tier.oxygenCapacity());
    }

    /** A rocket with explicit fuel + onboard oxygen (mB) — used to showcase the dual-tank console. */
    private static void spawnRocket(ServerLevel level, int x, int y, int z, RocketTier tier, int fuel, int oxygen) {
        RocketEntity rocket = new RocketEntity(level,
                x + 0.5D, y + RocketLaunchPadBlock.SURFACE_HEIGHT, z + 0.5D, tier);
        if (fuel > 0) {
            rocket.addFuel(fuel);
        }
        if (oxygen > 0) {
            rocket.addOxygen(oxygen);
        }
        level.addFreshEntity(rocket);
    }

    /** An invulnerable, named armor stand wearing the given four-piece suit. */
    private static void spawnSuitStand(ServerLevel level, BlockPos pos, Component name, float yaw,
            Item helmet, Item chestplate, Item leggings, Item boots) {
        // Build the stand via its constructor (the de-obf EntityType.ARMOR_STAND constant isn't on the
        // 26.2 classpath) and add it to the world directly.
        ArmorStand stand = new ArmorStand(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        stand.setItemSlot(EquipmentSlot.HEAD, new ItemStack(helmet));
        stand.setItemSlot(EquipmentSlot.CHEST, new ItemStack(chestplate));
        stand.setItemSlot(EquipmentSlot.LEGS, new ItemStack(leggings));
        stand.setItemSlot(EquipmentSlot.FEET, new ItemStack(boots));
        stand.setCustomName(name);
        stand.setCustomNameVisible(true);
        //? if >=26.3 {
        /*stand.setPermanentlyInvulnerable(true);
        *///?} else {
        stand.setInvulnerable(true);
        //?}
        stand.setYRot(yaw); // uniform facing so the row reads as a clean line, angled a few degrees off straight-on
        stand.setYBodyRot(yaw);
        stand.setYHeadRot(yaw);
        level.addFreshEntity(stand);
    }

    /** Small floating label for gallery display clusters. */
    private static void spawnLabelStand(ServerLevel level, BlockPos pos, Component name) {
        ArmorStand stand = new ArmorStand(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        stand.setCustomName(name);
        stand.setCustomNameVisible(true);
        stand.setInvisible(true);
        stand.setNoGravity(true);
        //? if >=26.3 {
        /*stand.setPermanentlyInvulnerable(true);
        *///?} else {
        stand.setInvulnerable(true);
        //?}
        level.addFreshEntity(stand);
    }

    /** Gallery fixtures shrug off hits (the flag was renamed in 26.3). */
    private static void makeInvulnerable(Entity entity) {
        //? if >=26.3 {
        /*entity.setPermanentlyInvulnerable(true);
        *///?} else {
        entity.setInvulnerable(true);
        //?}
    }

    /** Set one face of the pipe at {@code pos} to {@code mode} for ALL four resource layers. */
    private static void setAllModes(ServerLevel level, BlockPos pos, Direction face, PipeIoMode mode) {
        if (level.getBlockEntity(pos) instanceof UniversalPipeBlockEntity pipe) {
            for (PipeResourceType type : PipeResourceType.VALUES) {
                pipe.setMode(face, type, mode);
            }
        }
    }

    /** Set ONE resource layer of one pipe face to {@code mode}, leaving the other layers untouched. */
    private static void setMode(ServerLevel level, BlockPos pos, Direction face,
            PipeResourceType type, PipeIoMode mode) {
        if (level.getBlockEntity(pos) instanceof UniversalPipeBlockEntity pipe) {
            pipe.setMode(face, type, mode);
        }
    }

    private static void spawnShowcase(ServerLevel level, EntityType<? extends Mob> type, BlockPos pos, boolean noAi) {
        Mob mob = type.spawn(level, pos, EntitySpawnReason.COMMAND);
        if (mob != null) {
            mob.setNoAi(noAi);
            mob.setPersistenceRequired();
        }
    }
}
