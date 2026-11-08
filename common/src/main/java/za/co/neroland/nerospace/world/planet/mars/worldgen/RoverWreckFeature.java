package za.co.neroland.nerospace.world.planet.mars.worldgen;

//? if <26.3 {
import com.mojang.serialization.Codec;
//?}

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
//? if <26.3 {
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
//?}

import za.co.neroland.nerospace.registry.ModItems;
import za.co.neroland.nerospace.world.AlienBuild;
import za.co.neroland.nerospace.world.SettlementPlacement;
import za.co.neroland.nerospace.world.StructureRegion;
import za.co.neroland.nerospace.world.StructureSpacing;
import za.co.neroland.nerospace.world.planet.luna.LunaContent;
import za.co.neroland.nerospace.world.planet.mars.MarsContent;
import za.co.neroland.nerospace.world.station.StationContent;

/**
 * Rover Wreck (Mars) — a half-buried six-wheeled rover: iron chassis with a weathered deck, a snapped antenna
 * mast, a solar wing, red regolith drifted over its tail, a salvage chest, and a Salvage Drone hovering over it that
 * trades rover parts for raw hematite. Spaced by {@link StructureSpacing} with the Mars salt; every write stays in
 * the feature's 3×3-chunk region. Mars has no villagers and no village core.
 */
//? if >=26.3 {
/*public class RoverWreckFeature implements Feature {
*///?} else {
public class RoverWreckFeature extends Feature<NoneFeatureConfiguration> {
//?}

    //? if >=26.3 {
    /*// Minecraft 26.3+: features are data-driven values; this configuration-free type has one instance.
    public static final RoverWreckFeature INSTANCE = new RoverWreckFeature();
    public static final com.mojang.serialization.MapCodec<RoverWreckFeature> CODEC = com.mojang.serialization.MapCodec.unit(INSTANCE);

    @Override
    public com.mojang.serialization.MapCodec<? extends Feature> codec() {
        return CODEC;
    }
    *///?}
    //? if <26.3 {
    public RoverWreckFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }
    //?}

    //? if >=26.3 {
    /*@Override
    public boolean place(WorldGenLevel level, net.minecraft.world.level.chunk.ChunkGenerator generator, RandomSource rand,
            BlockPos o) {
        return generate(level, rand, o);
    }
    *///?} else {
    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        return generate(ctx.level(), ctx.random(), ctx.origin());
    }
    //?}

    /** Half-length of the chassis along its axis (the whole wreck fits in ±{@value #FOOTPRINT_R}). */
    public static final int FOOTPRINT_R = 5;

    /** Version-neutral body shared by both feature APIs. */
    private boolean generate(WorldGenLevel level, RandomSource rand, BlockPos o) {
        StructureRegion region = SettlementPlacement.beginPlain(level, o, StructureSpacing.Roi.ROVER_WRECK);
        if (region == null) {
            return false;
        }
        AlienBuild.Site site = new AlienBuild.Site(level, AlienBuild.XERTZ, region);
        boolean alongX = rand.nextBoolean();
        int baseY = o.getY();
        BlockState chassis = Blocks.IRON_BLOCK.defaultBlockState();
        BlockState deck = Blocks.SMOOTH_STONE.defaultBlockState();
        BlockState wheel = Blocks.BLACKSTONE.defaultBlockState();
        BlockState regolith = MarsContent.RED_REGOLITH.get().defaultBlockState();

        for (int a = -3; a <= 3; a++) {
            int sink = a < 0 ? 1 : 0; // the tail is buried a block deeper
            for (int b = -1; b <= 1; b++) {
                put(site, o, alongX, a, baseY - sink, b, chassis);
                if (rand.nextFloat() < 0.7F) {
                    put(site, o, alongX, a, baseY + 1 - sink, b, deck);
                }
            }
            if (a == -3 || a == 0 || a == 3) {
                put(site, o, alongX, a, baseY - sink, -2, wheel);
                put(site, o, alongX, a, baseY - sink, 2, wheel);
            }
        }
        // Snapped antenna mast.
        int mast = 1 + rand.nextInt(3);
        for (int dy = 2; dy < 2 + mast; dy++) {
            put(site, o, alongX, 2, baseY + dy, 0, Blocks.IRON_BARS.defaultBlockState());
        }
        put(site, o, alongX, 2, baseY + 2 + mast, 0, Blocks.END_ROD.defaultBlockState());
        // Solar wing, tilted off one side.
        for (int a = -1; a <= 1; a++) {
            put(site, o, alongX, a, baseY + 1, 2, Blocks.DAYLIGHT_DETECTOR.defaultBlockState());
        }
        // Red regolith drifted over the tail.
        for (int a = -FOOTPRINT_R; a <= -3; a++) {
            for (int b = -2; b <= 2; b++) {
                if (rand.nextFloat() < 0.6F) {
                    put(site, o, alongX, a, baseY + 1, b, regolith);
                }
            }
        }

        BlockPos chestPos = at(o, alongX, 1, baseY + 2, 0);
        site.set(chestPos.getX(), chestPos.getY(), chestPos.getZ(), Blocks.CHEST.defaultBlockState());
        if (level.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
            chest.setItem(4, new ItemStack(MarsContent.RAW_HEMATITE.get(), 4 + rand.nextInt(6)));
            chest.setItem(6, new ItemStack(LunaContent.TITANIUM_INGOT.get(), 1 + rand.nextInt(2)));
            chest.setItem(10, new ItemStack(StationContent.HULL_PLATING_ITEM.get(), 1 + rand.nextInt(3)));
            chest.setItem(13, new ItemStack(StationContent.TRUSS_ITEM.get(), 2 + rand.nextInt(4)));
            chest.setItem(16, new ItemStack(MarsContent.RUST_PIGMENT.get(), 2 + rand.nextInt(4)));
            if (rand.nextFloat() < 0.4F) {
                chest.setItem(20, new ItemStack(StationContent.SOLAR_WING_ITEM.get(), 1));
            }
            if (rand.nextFloat() < 0.3F) {
                chest.setItem(22, new ItemStack(ModItems.ALIEN_TECH_SCRAP.get(), 1));
            }
            // 2.1 (D17): a salvageable rover chassis. Rolled from the wreck's position, not the feature's random, so
            // every 2.0 draw (layout, loot, drone) is unchanged.
            if (za.co.neroland.nerospace.entity.RoverRules.wreckHasChassis(o.asLong())) {
                chest.setItem(24, new ItemStack(MarsContent.ROVER_CHASSIS.get(), 1));
            }
        }

        // The drone: built on the world-gen thread with the feature's own random (see MegaCityFeature's warden note).
        var drone = MarsContent.SALVAGE_DRONE.get().create(level.getLevel(), EntitySpawnReason.STRUCTURE);
        if (drone != null) {
            drone.snapTo(o.getX() + 0.5, baseY + 3.0, o.getZ() + 0.5, rand.nextFloat() * 360.0F, 0.0F);
            drone.setPersistenceRequired();
            level.addFreshEntity(drone);
        }
        return true;
    }

    private static BlockPos at(BlockPos o, boolean alongX, int a, int y, int b) {
        return alongX ? new BlockPos(o.getX() + a, y, o.getZ() + b) : new BlockPos(o.getX() + b, y, o.getZ() + a);
    }

    private static void put(AlienBuild.Site site, BlockPos o, boolean alongX, int a, int y, int b, BlockState s) {
        BlockPos p = at(o, alongX, a, y, b);
        site.set(p.getX(), p.getY(), p.getZ(), s);
    }
}
