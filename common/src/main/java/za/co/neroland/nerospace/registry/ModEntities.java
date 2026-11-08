package za.co.neroland.nerospace.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import za.co.neroland.nerospace.NerospaceCommon;
import za.co.neroland.nerospace.entity.AlienVillager;
import za.co.neroland.nerospace.entity.CinderStalker;
import za.co.neroland.nerospace.entity.EmberStrutter;
import za.co.neroland.nerospace.entity.FrostStrider;
import za.co.neroland.nerospace.entity.Greenling;
import za.co.neroland.nerospace.entity.MeadowLoper;
import za.co.neroland.nerospace.entity.QuartzCrawler;
import za.co.neroland.nerospace.entity.RuinWarden;
import za.co.neroland.nerospace.entity.WoollyDrift;
import za.co.neroland.nerospace.entity.XertzStalker;
import za.co.neroland.nerospace.meteor.FallingMeteorEntity;
import za.co.neroland.nerospace.rocket.RocketEntity;
import za.co.neroland.nerospace.registry.RegistrationProvider.RegistryEntry;

/**
 * Entity types, ported cross-loader through {@link RegistrationProvider} over the vanilla
 * {@code ENTITY_TYPE} registry (the root used NeoForge's {@code DeferredRegister.Entities}). The
 * builder's {@code build(ResourceKey)} consumes the key the provider hands the factory. Attributes
 * are applied per-loader from {@link ModEntityAttributes}; natural-spawn placement rules from
 * {@link ModSpawnPlacements}; renderers from {@code client/ClientEntityRenderers}.
 */
public final class ModEntities {

    public static final RegistrationProvider<EntityType<?>> ENTITY_TYPES =
            RegistrationProvider.get(Registries.ENTITY_TYPE, NerospaceCommon.MOD_ID);

    public static final RegistryEntry<EntityType<XertzStalker>> XERTZ_STALKER = ENTITY_TYPES.register(
            "xertz_stalker",
            key -> EntityType.Builder.of(XertzStalker::new, MobCategory.MONSTER)
                    .sized(0.7F, 1.9F).eyeHeight(1.6F).clientTrackingRange(8).build(key));

    public static final RegistryEntry<EntityType<QuartzCrawler>> QUARTZ_CRAWLER = ENTITY_TYPES.register(
            "quartz_crawler",
            key -> EntityType.Builder.of(QuartzCrawler::new, MobCategory.CREATURE)
                    .sized(0.9F, 0.8F).eyeHeight(0.6F).clientTrackingRange(8).build(key));

    public static final RegistryEntry<EntityType<Greenling>> GREENLING = ENTITY_TYPES.register(
            "greenling",
            key -> EntityType.Builder.of(Greenling::new, MobCategory.AMBIENT)
                    .sized(0.5F, 0.6F).eyeHeight(0.45F).clientTrackingRange(8).build(key));

    public static final RegistryEntry<EntityType<RuinWarden>> RUIN_WARDEN = ENTITY_TYPES.register(
            "ruin_warden",
            key -> EntityType.Builder.of(RuinWarden::new, MobCategory.MONSTER)
                    .sized(1.4F, 3.0F).eyeHeight(2.6F).clientTrackingRange(10).build(key));

    public static final RegistryEntry<EntityType<CinderStalker>> CINDER_STALKER = ENTITY_TYPES.register(
            "cinder_stalker",
            key -> EntityType.Builder.of(CinderStalker::new, MobCategory.MONSTER)
                    .sized(0.8F, 2.0F).eyeHeight(1.7F).fireImmune().clientTrackingRange(8).build(key));

    public static final RegistryEntry<EntityType<FrostStrider>> FROST_STRIDER = ENTITY_TYPES.register(
            "frost_strider",
            key -> EntityType.Builder.of(FrostStrider::new, MobCategory.MONSTER)
                    .sized(0.8F, 2.4F).eyeHeight(2.1F).clientTrackingRange(8).build(key));

    public static final RegistryEntry<EntityType<MeadowLoper>> MEADOW_LOPER = ENTITY_TYPES.register(
            "meadow_loper",
            key -> EntityType.Builder.of(MeadowLoper::new, MobCategory.CREATURE)
                    .sized(1.1F, 1.3F).eyeHeight(1.1F).clientTrackingRange(8).build(key));

    public static final RegistryEntry<EntityType<EmberStrutter>> EMBER_STRUTTER = ENTITY_TYPES.register(
            "ember_strutter",
            key -> EntityType.Builder.of(EmberStrutter::new, MobCategory.CREATURE)
                    .sized(0.5F, 0.9F).eyeHeight(0.7F).fireImmune().clientTrackingRange(8).build(key));

    public static final RegistryEntry<EntityType<WoollyDrift>> WOOLLY_DRIFT = ENTITY_TYPES.register(
            "woolly_drift",
            key -> EntityType.Builder.of(WoollyDrift::new, MobCategory.CREATURE)
                    .sized(0.9F, 1.2F).eyeHeight(1.0F).clientTrackingRange(8).build(key));

    public static final RegistryEntry<EntityType<AlienVillager>> ALIEN_VILLAGER = ENTITY_TYPES.register(
            "alien_villager",
            key -> EntityType.Builder.of(AlienVillager::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.95F).eyeHeight(1.7F).clientTrackingRange(10).build(key));

    public static final RegistryEntry<EntityType<RocketEntity>> ROCKET = ENTITY_TYPES.register(
            "rocket",
            key -> EntityType.Builder.<RocketEntity>of(RocketEntity::new, MobCategory.MISC)
                    .sized(1.0F, 3.0F).clientTrackingRange(10).build(key));

    /** The uncrewed cargo rocket (a RocketEntity subclass; rendered with the tier-2 hull). */
    public static final RegistryEntry<EntityType<za.co.neroland.nerospace.route.CargoRocketEntity>> CARGO_ROCKET =
            ENTITY_TYPES.register("cargo_rocket",
                    key -> EntityType.Builder.<za.co.neroland.nerospace.route.CargoRocketEntity>of(
                            za.co.neroland.nerospace.route.CargoRocketEntity::new, MobCategory.MISC)
                            .sized(1.0F, 3.0F).clientTrackingRange(10).build(key));

    public static final RegistryEntry<EntityType<FallingMeteorEntity>> FALLING_METEOR = ENTITY_TYPES.register(
            "falling_meteor",
            key -> EntityType.Builder.<FallingMeteorEntity>of(FallingMeteorEntity::new, MobCategory.MISC)
                    .sized(1.6F, 1.6F).clientTrackingRange(12).build(key));

    /**
     * 2.1: the Mars rover (D16), a server-driven two-seat vehicle. Hitbox per the rover model spec; updated every
     * other tick so the server-driven motion interpolates smoothly for the riders.
     */
    public static final RegistryEntry<EntityType<za.co.neroland.nerospace.entity.RoverEntity>> ROVER =
            ENTITY_TYPES.register("rover",
                    key -> EntityType.Builder.<za.co.neroland.nerospace.entity.RoverEntity>of(
                            za.co.neroland.nerospace.entity.RoverEntity::new, MobCategory.MISC)
                            .sized(1.8F, 1.45F).clientTrackingRange(10).updateInterval(2).build(key));

    private ModEntities() {
    }

    public static void init() {
    }
}
