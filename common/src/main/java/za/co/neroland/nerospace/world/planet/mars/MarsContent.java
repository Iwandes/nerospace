package za.co.neroland.nerospace.world.planet.mars;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import za.co.neroland.nerospace.block.ContactHazardBlock;
import za.co.neroland.nerospace.block.DriftingBlock;
import za.co.neroland.nerospace.block.DryIceBlock;
import za.co.neroland.nerospace.block.MagneticOreBlock;
import za.co.neroland.nerospace.block.PillarGrowthBlock;
import za.co.neroland.nerospace.block.rules.StrataRules;
import za.co.neroland.nerospace.registry.ModBlocks;
import za.co.neroland.nerospace.registry.ModEntities;
import za.co.neroland.nerospace.item.NerospaceSpawnEggItem;
import za.co.neroland.nerospace.registry.ModItems;
import za.co.neroland.nerospace.registry.RegistrationProvider.RegistryEntry;
import za.co.neroland.nerospace.world.planet.mars.block.StrataBlock;
import za.co.neroland.nerospace.world.planet.mars.entity.DustDevil;
import za.co.neroland.nerospace.world.planet.mars.entity.DustStrider;
import za.co.neroland.nerospace.world.planet.mars.entity.SalvageDrone;

/**
 * Every Mars block, item and entity, registered through the shared providers. Blocks reuse the shared behaviour bases:
 * Red Regolith drifts in dust storms ({@link DriftingBlock}), Hematite Ore pulls its drops to an iron-tool breaker
 * ({@link MagneticOreBlock}), Perchlorate Crust poisons bare boots ({@link ContactHazardBlock}), Dry Ice freezes
 * adjacent water and sublimates in bright light ({@link DryIceBlock}), Basalt Column grows to four segments
 * ({@link PillarGrowthBlock}). Martian Sandstone is cut into four horizontal-axis strata variants on the stonecutter.
 * Class loading registers everything; {@link #init()} only forces the load.
 */
public final class MarsContent {

    // --- Blocks (creative-tab order) ---------------------------------------------------------------------------
    public static final RegistryEntry<DriftingBlock> RED_REGOLITH = ModBlocks.BLOCKS.register("red_regolith",
            key -> new DriftingBlock(props(key).mapColor(MapColor.TERRACOTTA_ORANGE).strength(0.5F, 0.5F)
                    .sound(SoundType.SAND)));

    public static final RegistryEntry<Block> MARTIAN_SANDSTONE = ModBlocks.BLOCKS.register("martian_sandstone",
            key -> new Block(props(key).mapColor(MapColor.COLOR_ORANGE).strength(0.8F, 0.8F)
                    .requiresCorrectToolForDrops().sound(SoundType.STONE)));

    /** {@code martian_sandstone_strata_1..4}, named by {@link StrataRules#variantFor(int)}. */
    public static final List<RegistryEntry<StrataBlock>> STRATA = strata();

    public static final RegistryEntry<PillarGrowthBlock> BASALT_COLUMN = ModBlocks.BLOCKS.register("basalt_column",
            key -> new PillarGrowthBlock(props(key).mapColor(MapColor.TERRACOTTA_BROWN).strength(1.5F, 6.0F)
                    .requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE)));

    public static final RegistryEntry<Block> DUST_DEVIL_PIT = ModBlocks.BLOCKS.register("dust_devil_pit",
            key -> new Block(props(key).mapColor(MapColor.TERRACOTTA_ORANGE).strength(0.5F, 0.5F)
                    .sound(SoundType.SAND)));

    public static final RegistryEntry<DryIceBlock> DRY_ICE = ModBlocks.BLOCKS.register("dry_ice",
            key -> new DryIceBlock(props(key).mapColor(MapColor.ICE).strength(0.5F, 0.5F).randomTicks()
                    .sound(SoundType.GLASS).noOcclusion()));

    public static final RegistryEntry<MagneticOreBlock> HEMATITE_ORE = ModBlocks.BLOCKS.register("hematite_ore",
            key -> new MagneticOreBlock(props(key).mapColor(MapColor.TERRACOTTA_RED).strength(3.0F, 3.0F)
                    .requiresCorrectToolForDrops().sound(SoundType.STONE)));

    public static final RegistryEntry<ContactHazardBlock> PERCHLORATE_CRUST = ModBlocks.BLOCKS.register("perchlorate_crust",
            key -> new ContactHazardBlock(props(key).mapColor(MapColor.TERRACOTTA_WHITE).strength(0.7F, 0.7F)
                    .sound(SoundType.SAND), ContactHazardBlock.Hazard.POISON));

    // --- Block items ---------------------------------------------------------------------------------------
    public static final RegistryEntry<BlockItem> RED_REGOLITH_ITEM = blockItem("red_regolith", RED_REGOLITH);
    public static final RegistryEntry<BlockItem> MARTIAN_SANDSTONE_ITEM = blockItem("martian_sandstone", MARTIAN_SANDSTONE);
    public static final List<RegistryEntry<BlockItem>> STRATA_ITEMS = strataItems();
    public static final RegistryEntry<BlockItem> BASALT_COLUMN_ITEM = blockItem("basalt_column", BASALT_COLUMN);
    public static final RegistryEntry<BlockItem> DUST_DEVIL_PIT_ITEM = blockItem("dust_devil_pit", DUST_DEVIL_PIT);
    public static final RegistryEntry<BlockItem> DRY_ICE_ITEM = blockItem("dry_ice", DRY_ICE);
    public static final RegistryEntry<BlockItem> HEMATITE_ORE_ITEM = blockItem("hematite_ore", HEMATITE_ORE);
    public static final RegistryEntry<BlockItem> PERCHLORATE_CRUST_ITEM = blockItem("perchlorate_crust", PERCHLORATE_CRUST);

    // --- Items ---------------------------------------------------------------------------------------------
    public static final RegistryEntry<Item> RAW_HEMATITE = item("raw_hematite");
    public static final RegistryEntry<Item> RUST_PIGMENT = item("rust_pigment");
    /** Fuel Refinery oxidiser (+15 % output, one per four buckets). */
    public static final RegistryEntry<Item> PERCHLORATE_OXIDISER = item("perchlorate_oxidiser");

    // --- Mars rover (2.1, D16–D17) -------------------------------------------------------------------------
    /** Crafting intermediate: a wheel, strut and hub motor. */
    public static final RegistryEntry<Item> ROVER_PARTS = item("rover_parts");
    /** Rover Wreck salvage: a stripped frame that shortcuts the rover recipe. */
    public static final RegistryEntry<Item> ROVER_CHASSIS = item("rover_chassis");
    /** Places a Mars rover; packing a rover up returns it with its charge. */
    public static final RegistryEntry<Item> ROVER = ModItems.ITEMS.register("rover",
            key -> new za.co.neroland.nerospace.item.RoverItem(new Item.Properties().stacksTo(1).setId(key)));

    // --- Entities ------------------------------------------------------------------------------------------
    public static final RegistryEntry<EntityType<DustDevil>> DUST_DEVIL = ModEntities.ENTITY_TYPES.register("dust_devil",
            key -> EntityType.Builder.<DustDevil>of(DustDevil::new, MobCategory.MISC)
                    .sized(0.9F, 2.5F).clientTrackingRange(8).updateInterval(2).build(key));

    /** The Rover Wreck merchant: rover parts for raw hematite, no reputation, no player record. */
    public static final RegistryEntry<EntityType<SalvageDrone>> SALVAGE_DRONE = ModEntities.ENTITY_TYPES.register(
            "salvage_drone", key -> EntityType.Builder.<SalvageDrone>of(SalvageDrone::new, MobCategory.CREATURE)
                    .sized(0.7F, 0.7F).clientTrackingRange(8).build(key));

    /** The red-steppe herd grazer; seeded by TerraformFauna and spawned by terraformed_mars_living. */
    public static final RegistryEntry<EntityType<DustStrider>> DUST_STRIDER = ModEntities.ENTITY_TYPES.register(
            "dust_strider", key -> EntityType.Builder.<DustStrider>of(DustStrider::new, MobCategory.CREATURE)
                    .sized(0.9F, 1.6F).eyeHeight(1.4F).clientTrackingRange(8).build(key));
    public static final RegistryEntry<Item> DUST_STRIDER_SPAWN_EGG = ModItems.ITEMS.register("dust_strider_spawn_egg",
            key -> new NerospaceSpawnEggItem(new Item.Properties().setId(key), DUST_STRIDER));

    private MarsContent() {
    }

    /** Forces class loading (and so registration). Called once from the shared registry init. */
    public static void init() {
    }

    /** The Mars run of the planets tab, in tab order (ground, stone, derived, features, ores, items). */
    public static List<Supplier<? extends ItemLike>> creativeContents() {
        List<Supplier<? extends ItemLike>> out = new ArrayList<>();
        out.add(RED_REGOLITH_ITEM);
        out.add(MARTIAN_SANDSTONE_ITEM);
        out.addAll(STRATA_ITEMS);
        out.add(BASALT_COLUMN_ITEM);
        out.add(DUST_DEVIL_PIT_ITEM);
        out.add(DRY_ICE_ITEM);
        out.add(HEMATITE_ORE_ITEM);
        out.add(PERCHLORATE_CRUST_ITEM);
        out.add(RAW_HEMATITE);
        out.add(RUST_PIGMENT);
        out.add(PERCHLORATE_OXIDISER);
        out.add(ROVER_PARTS);
        out.add(ROVER_CHASSIS);
        out.add(ROVER);
        return Collections.unmodifiableList(out);
    }

    // --- helpers -------------------------------------------------------------------------------------------

    private static BlockBehaviour.Properties props(net.minecraft.resources.ResourceKey<Block> key) {
        return BlockBehaviour.Properties.of().setId(key);
    }

    private static List<RegistryEntry<StrataBlock>> strata() {
        List<RegistryEntry<StrataBlock>> out = new ArrayList<>(StrataRules.COUNT);
        for (int i = 0; i < StrataRules.COUNT; i++) {
            out.add(ModBlocks.BLOCKS.register(StrataRules.variantFor(i),
                    key -> new StrataBlock(props(key).mapColor(MapColor.COLOR_ORANGE).strength(0.8F, 0.8F)
                            .requiresCorrectToolForDrops().sound(SoundType.STONE))));
        }
        return Collections.unmodifiableList(out);
    }

    private static List<RegistryEntry<BlockItem>> strataItems() {
        List<RegistryEntry<BlockItem>> out = new ArrayList<>(StrataRules.COUNT);
        for (int i = 0; i < StrataRules.COUNT; i++) {
            out.add(blockItem(StrataRules.variantFor(i), STRATA.get(i)));
        }
        return Collections.unmodifiableList(out);
    }

    private static RegistryEntry<BlockItem> blockItem(String name, RegistryEntry<? extends Block> block) {
        // 26.x block items resolve their name from item.nerospace.<id>; the lang fragment mirrors every block key.
        return ModItems.ITEMS.register(name, key -> new BlockItem(block.get(), new Item.Properties().setId(key)));
    }

    private static RegistryEntry<Item> item(String name) {
        return item(name, p -> p);
    }

    private static RegistryEntry<Item> item(String name, UnaryOperator<Item.Properties> cfg) {
        return ModItems.ITEMS.register(name, key -> new Item(cfg.apply(new Item.Properties().setId(key))));
    }
}
