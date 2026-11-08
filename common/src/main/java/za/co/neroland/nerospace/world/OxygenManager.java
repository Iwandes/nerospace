package za.co.neroland.nerospace.world;

import java.util.EnumSet;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import za.co.neroland.nerospace.api.Hazard;
import za.co.neroland.nerospace.api.NerospaceOxygen;
import za.co.neroland.nerospace.api.NerospaceTerraforming;
import za.co.neroland.nerospace.api.NerospaceVisits;
import za.co.neroland.nerospace.config.NerospaceConfig;
import za.co.neroland.nerospace.gas.GasResource;
import za.co.neroland.nerospace.gas.NerospaceGasStorage;
import za.co.neroland.nerospace.platform.GasLookup;
import za.co.neroland.nerospace.platform.Services;
import za.co.neroland.nerospace.registry.ModBlocks;
import za.co.neroland.nerospace.registry.ModDataComponents;
import za.co.neroland.nerospace.registry.ModItems;
import za.co.neroland.nerospace.rocket.RocketEntity;
import za.co.neroland.nerospace.world.planet.HazardOrderRules;
import za.co.neroland.nerospace.world.planet.PlanetDefinition;
import za.co.neroland.nerospace.world.planet.PlanetHazards;
import za.co.neroland.nerospace.world.planet.Planets;
import za.co.neroland.nerospace.world.planet.RadiationRules;

/**
 * Oxygen / atmosphere survival (Phase 8c, simplified cross-loader port). On airless Nerospace
 * dimensions a survival player carries a finite oxygen supply (a per-player data attachment, accessed
 * through the {@link Services#PLATFORM} seam) that drains while exposed and refills inside a breathable
 * zone — near a Rocket Launch Pad (the landing site) or an Oxygen Generator. A worn Oxygen Suit grants a
 * larger tank and far slower drain. At zero oxygen the player suffocates. Remaining oxygen is mirrored
 * onto the vanilla air-supply bar so the bubble HUD shows it for free.
 *
 * <p><b>Cross-loader port note.</b> The root drives this from a NeoForge {@code PlayerTickEvent} and a
 * full diffusion {@code OxygenFieldManager} (sealed rooms + client overlay, networking-synced), plus
 * terraform breathability, hazard shields, and gas-tank airlock refills. The multiloader ticks it from a
 * per-loader server-tick hook and keeps the self-contained survival core. The diffusion field, terraform
 * breathability, and per-planet hazard shields (heat/cold) are now wired in; advancement criteria and the
 * gas-tank airlock refill remain deferred. Values are inlined (the config seam is deferred).</p>
 *
 * <p><b>Hazards (SUIT_HAZARD_DESIGN.md).</b> Cindara runs HOT and Glacira runs COLD: an uncountered
 * hazard multiplies oxygen drain ×{@link #HAZARD_DRAIN_MULTIPLIER} (no separate damage path — lethality
 * stays with zero-O₂ suffocation). A full set of the matching {@link HazardShield} suit variant negates
 * it; a mixed set does not.</p>
 */
public final class OxygenManager {

    /** Default / bare-lungs oxygen capacity (also the attachment default — keep both loaders in sync). */
    public static final int OXYGEN_MAX = 300;
    /** A full Oxygen Suit's larger air tank. */
    public static final int OXYGEN_SUIT_MAX = 900;

    public static final int CHECK_INTERVAL_TICKS = 10;
    private static final int DAMAGE_INTERVAL_TICKS = 40;
    /** Bare-lungs drain per check (exposed) — ~a few seconds of air. */
    private static final int BARE_DRAIN_PER_CHECK = 30;
    /** Suited drain per check (the suit's tank lasts far longer). */
    private static final int SUIT_DRAIN_PER_CHECK = 3;
    /** Breathable-zone scan radius around the player (launch pad / oxygen generator). */
    private static final int SAFE_RADIUS = 6;
    private static final float SUFFOCATION_DAMAGE = 1.0F;
    /** Drain factor on a hazard dimension without the matching suit variant (SUIT_HAZARD_DESIGN.md §2). */
    private static final int HAZARD_DRAIN_MULTIPLIER = 4;

    /** Oxygen (mB) a ridden rocket's life-support tank spends per check keeping the rider breathing. */
    public static final int RIDE_DRAIN_PER_CHECK = 2;

    /** Airlock refill: scan radius around a suited player for a Gas Tank / Oxygen Generator holding O2. */
    private static final int AIRLOCK_RADIUS = 4;
    /** Air units a suit's tank regains per check from an in-range oxygen store (drain offsets it nicely). */
    private static final int AIRLOCK_REFILL_PER_CHECK = 90;
    /** Gas (mB) consumed per air unit restored — the conversion rate from a tank's oxygen to suit air. */
    private static final int AIRLOCK_MB_PER_AIR = 2;

    private OxygenManager() {
    }

    /**
     * Whether {@code dimension} is an airless Nerospace world (every planet and the orbital station vacuum).
     * Read-only; surfaced as the "airless" planet trait by the public {@code za.co.neroland.nerospace.api}
     * facade so consumers needn't hard-code the dimension set.
     */
    public static boolean isAirless(ResourceKey<Level> dimension) {
        PlanetDefinition planet = Planets.get(dimension);
        return planet != null && planet.traits().airless();
    }

    /** Per-player server tick (called from each loader's server-tick hook). */
    public static void tick(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        boolean suited = isFullSuit(player);
        int max = NerospaceConfig.scale(suited ? OXYGEN_SUIT_MAX : OXYGEN_MAX,
                NerospaceConfig.oxygenCapacityMultiplier());

        // Nerospace's oxygen survival is scoped to its OWN airless dimensions. On any other world
        // (vanilla overworld/nether/end, or another mod's dimension) the mod must NOT touch the
        // player's vanilla air supply — mirroring it every tick would clobber vanilla drowning /
        // suffocation. Top the internal tank so a later trip starts full, then leave vanilla alone.
        if (!isAirless(level.dimension())) {
            Services.PLATFORM.setOxygen(player, max);
            return;
        }

        // Historical planet visits for the public API. Placed after the airless gate so it costs nothing
        // at all off-world, and throttled to the same cadence as the rest of this method's periodic work:
        // a first visit is worth recording within a few seconds, not within a single tick.
        if (player.tickCount % CHECK_INTERVAL_TICKS == 0) {
            NerospaceVisits.observeCurrentPlanet(player);
        }

        // On a Nerospace dimension but not actually exposed (creative build / spectator): keep the
        // tank full and mirror full bubbles. Mirroring is safe here — this is the mod's own world.
        boolean airless = !player.getAbilities().instabuild && !player.isSpectator();
        if (!airless) {
            Services.PLATFORM.setOxygen(player, max);
            mirrorToAirSupply(player, max, max);
            return;
        }

        // Riding the rocket on an airless world: the onboard tank is life support — keep the rider's air
        // full and trickle the rocket's oxygen down (the bulk is saved for the arrival surface reserve).
        if (player.getVehicle() instanceof RocketEntity rocket && rocket.getOxygen() > 0) {
            if (player.tickCount % CHECK_INTERVAL_TICKS == 0) {
                rocket.drainOxygen(RIDE_DRAIN_PER_CHECK);
            }
            int kept = Math.max(Services.PLATFORM.getOxygen(player), max);
            Services.PLATFORM.setOxygen(player, kept);
            mirrorToAirSupply(player, Math.min(kept, max), max);
            return;
        }

        // May exceed max while a rocket arrival reserve is still held (overfill above the normal tank).
        int oxygen = Services.PLATFORM.getOxygen(player);

        if (player.tickCount % CHECK_INTERVAL_TICKS == 0) {
            if (isBreathable(level, player.blockPosition())) {
                oxygen = Math.max(oxygen, max); // breathe ambient; a carried buffer is left untouched here
            } else {
                // Exposed: drain the tank — INCLUDING any rocket-carried arrival buffer (overfill above the
                // normal tank) — at the normal rate. A worn suit drains far slower; an uncountered planet
                // hazard (Cindara heat / Glacira cold / Nyxara radiation) multiplies it. At zero the player
                // suffocates. The hazard is resolved per player: the first one here they are not shielded from.
                HazardShield hazard = HazardShield.of(PlanetHazards.at(level, player.blockPosition(), player));
                int drain = NerospaceConfig.scale(suited ? SUIT_DRAIN_PER_CHECK : BARE_DRAIN_PER_CHECK,
                        NerospaceConfig.oxygenDrainMultiplier()) * (hazard == HazardShield.NONE ? 1 : HAZARD_DRAIN_MULTIPLIER);
                oxygen = Math.max(0, oxygen - drain);
                hazardFeedback(level, player, hazard);
                if (hazard == HazardShield.RADIATION && player.tickCount % DAMAGE_INTERVAL_TICKS == 0) {
                    radiationHit(level, player);
                }
                // Airlock: a worn suit beside a Gas Tank / Oxygen Generator holding O2 taps it to top up —
                // a tank by the base door keeps you fuelled without a breathable bubble.
                if (suited && oxygen < max) {
                    oxygen = Math.min(max, oxygen + airlockRefill(level, player, max - oxygen));
                }
            }
            Services.PLATFORM.setOxygen(player, oxygen);
            // NeroLink: push an owner-scoped oxygen_low event the first time this trip crosses into the
            // red (the publisher edge-latches, so this call is cheap and cannot spam).
            za.co.neroland.nerospace.link.NerospaceLinkEvents.oxygenChanged(
                    player, Math.min(oxygen, max), max);
        }

        mirrorToAirSupply(player, Math.min(oxygen, max), max);

        if (oxygen <= 0 && player.tickCount % DAMAGE_INTERVAL_TICKS == 0) {
            player.hurtServer(level, level.damageSources().generic(), SUFFOCATION_DAMAGE);
            if (player.tickCount % (DAMAGE_INTERVAL_TICKS * 3) == 0) {
                player.sendSystemMessage(Component.translatable("message.nerospace.greenxertz.no_air"));
            }
        }
    }

    /** The player's normal oxygen tank size (suit or bare lungs), config-scaled. */
    public static int maxOxygenFor(Player player) {
        return NerospaceConfig.scale(isFullSuit(player) ? OXYGEN_SUIT_MAX : OXYGEN_MAX,
                NerospaceConfig.oxygenCapacityMultiplier());
    }

    /**
     * Grants a rocket arrival buffer: tops the rider's personal/suit oxygen to full and adds the rocket's
     * remaining onboard oxygen as overfill on top. That buffer then drains at the <em>normal</em> exposed
     * rate (see {@link #tick}) — a finite head-start that gets spent, after which the player must reach a
     * breathable zone (launch pad / Oxygen Generator / terraformed ground) or suffocate. Server-side.
     */
    public static void grantArrivalReserve(ServerPlayer player, int oxygenMb) {
        if (oxygenMb <= 0) {
            return;
        }
        Services.PLATFORM.setOxygen(player, maxOxygenFor(player) + oxygenMb);
    }

    /**
     * Tops the player's personal / suit oxygen up to its normal maximum ({@link #maxOxygenFor}). Never
     * lowers an arrival overfill. Server-side; used by the {@code nerospace:hook/oxygen_top_up} hook gate.
     */
    public static void topUp(ServerPlayer player) {
        int max = maxOxygenFor(player);
        if (Services.PLATFORM.getOxygen(player) < max) {
            Services.PLATFORM.setOxygen(player, max);
        }
    }

    /** Maps oxygen onto the vanilla air-supply bar (full oxygen → no bubbles shown). */
    private static void mirrorToAirSupply(Player player, int oxygen, int max) {
        int airMax = player.getMaxAirSupply();
        int air = (int) ((long) oxygen * airMax / Math.max(1, max));
        player.setAirSupply(Math.min(airMax, Math.max(0, air)));
    }

    /**
     * A breathable zone: the diffusion {@link OxygenFieldManager} reads breathable at {@code center}
     * (sealed rooms fill completely; an Oxygen Generator pressurises a bubble / its sealed room), or the
     * player is within {@link #SAFE_RADIUS} of a Rocket Launch Pad — a permanent pressurised safe zone at
     * the landing site (the pad is not a field source, so it stays a simple radius check).
     */
    private static boolean isBreathable(ServerLevel level, BlockPos center) {
        // Terraformed ground is permanently breathable (the Terraformer flags the chunk).
        if (Services.PLATFORM.isTerraformed(level.getChunkAt(center))) {
            return true;
        }
        if (OxygenFieldManager.get(level).isBreathable(center)) {
            return true;
        }
        // A reversible terraforming overlay makes a region breathable without rewriting the chunk, so it
        // has to be checked separately from the physical isTerraformed flag above. Explicit branch to
        // keep an Optional map + Boolean box off this path.
        var overlay = NerospaceTerraforming.at(level, center);
        if (overlay.isPresent() && overlay.get().stage() >= 1) {
            return true;
        }
        // External contributions (a NeroAgriculture greenhouse, say) are pressure like any other.
        if (NerospaceOxygen.pressureAt(level, center) >= NerospaceOxygen.BREATHABLE_PRESSURE) {
            return true;
        }
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-SAFE_RADIUS, -SAFE_RADIUS, -SAFE_RADIUS),
                center.offset(SAFE_RADIUS, SAFE_RADIUS, SAFE_RADIUS))) {
            BlockState state = level.getBlockState(pos);
            if (state.is(ModBlocks.ROCKET_LAUNCH_PAD.get())
                    || state.is(ModBlocks.LANDING_POD.get())
                    || state.is(ModBlocks.DOCKING_PORT.get())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Airlock refill: a worn suit within {@link #AIRLOCK_RADIUS} of a Gas Tank / Creative Gas Tank /
     * Oxygen Generator holding Oxygen draws gas from it to refill the suit's air tank — so a tank at the
     * base entrance acts as an airlock. {@link #AIRLOCK_MB_PER_AIR} mB of oxygen restores one air unit.
     *
     * @param need air units missing from the suit tank
     * @return air units actually restored (0 when no usable oxygen store is in range)
     */
    private static int airlockRefill(ServerLevel level, Player player, int need) {
        if (need <= 0) {
            return 0;
        }
        int want = Math.min(need, AIRLOCK_REFILL_PER_CHECK);
        BlockPos center = player.blockPosition();
        int restored = 0;
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-AIRLOCK_RADIUS, -AIRLOCK_RADIUS, -AIRLOCK_RADIUS),
                center.offset(AIRLOCK_RADIUS, AIRLOCK_RADIUS, AIRLOCK_RADIUS))) {
            if (want <= 0) {
                break;
            }
            BlockState state = level.getBlockState(pos);
            if (!state.is(za.co.neroland.nerolandcore.registry.ModBlocks.GAS_TANK.get())
                    && !state.is(za.co.neroland.nerolandcore.registry.ModBlocks.CREATIVE_GAS_TANK.get())
                    && !state.is(ModBlocks.OXYGEN_GENERATOR.get())) {
                continue;
            }
            NerospaceGasStorage store = GasLookup.INSTANCE.find(level, pos.immutable(), null);
            if (store == null) {
                continue;
            }
            int gained = drawOxygen(store, want);
            restored += gained;
            want -= gained;
        }
        if (restored > 0) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundSource.PLAYERS, 0.3F, 1.4F);
        }
        return restored;
    }

    /** Extract whole air units of Oxygen from {@code store}: simulate, floor to whole units, then commit. */
    private static int drawOxygen(NerospaceGasStorage store, int wantAir) {
        if (store.getGas() != GasResource.OXYGEN || store.getAmount() <= 0) {
            return 0;
        }
        // The mB-of-oxygen cost per restored air unit is a consumable running cost: scale by fuelCost.
        int mbPerAir = NerospaceConfig.scale(AIRLOCK_MB_PER_AIR, NerospaceConfig.fuelCostMultiplier());
        long peeked = store.drain((long) wantAir * mbPerAir, true);
        int units = (int) (peeked / mbPerAir);
        if (units <= 0) {
            return 0;
        }
        store.drain((long) units * mbPerAir, false);
        return units;
    }

    /**
     * Whether the player is sealed off from the planet's atmosphere: a full suit of any variant, or standing in
     * a breathable zone (oxygen field, terraformed ground, launch pad). Weather effects that act on an exposed
     * player (spore nausea, storm radiation) check this. Server-side.
     */
    public static boolean isSealedFromAtmosphere(ServerLevel level, Player player) {
        return isFullSuit(player) || isBreathable(level, player.blockPosition());
    }

    /** Whether {@code pos} is inside a breathable zone (oxygen field, terraformed ground, pad radius). */
    public static boolean isBreathableAt(ServerLevel level, BlockPos pos) {
        return level.hasChunkAt(pos) && isBreathable(level, pos);
    }

    /** Whether the player wears a full set of Oxygen Suit pieces (any tier / hazard variant counts). */
    private static boolean isFullSuit(Player player) {
        return isSuitPiece(player.getItemBySlot(EquipmentSlot.HEAD),
                        ModItems.OXYGEN_SUIT_HELMET.get(), ModItems.OXYGEN_SUIT_T2_HELMET.get(),
                        ModItems.OXYGEN_SUIT_HEAT_HELMET.get(), ModItems.OXYGEN_SUIT_COLD_HELMET.get())
                && isSuitPiece(player.getItemBySlot(EquipmentSlot.CHEST),
                        ModItems.OXYGEN_SUIT_CHESTPLATE.get(), ModItems.OXYGEN_SUIT_T2_CHESTPLATE.get(),
                        ModItems.OXYGEN_SUIT_HEAT_CHESTPLATE.get(), ModItems.OXYGEN_SUIT_COLD_CHESTPLATE.get())
                && isSuitPiece(player.getItemBySlot(EquipmentSlot.LEGS),
                        ModItems.OXYGEN_SUIT_LEGGINGS.get(), ModItems.OXYGEN_SUIT_T2_LEGGINGS.get(),
                        ModItems.OXYGEN_SUIT_HEAT_LEGGINGS.get(), ModItems.OXYGEN_SUIT_COLD_LEGGINGS.get())
                && isSuitPiece(player.getItemBySlot(EquipmentSlot.FEET),
                        ModItems.OXYGEN_SUIT_BOOTS.get(), ModItems.OXYGEN_SUIT_T2_BOOTS.get(),
                        ModItems.OXYGEN_SUIT_HEAT_BOOTS.get(), ModItems.OXYGEN_SUIT_COLD_BOOTS.get());
    }

    private static boolean isSuitPiece(ItemStack worn, Item... options) {
        for (Item option : options) {
            if (worn.is(option)) {
                return true;
            }
        }
        return false;
    }

    // --- Hazard shields (SUIT_HAZARD_DESIGN.md) -----------------------------

    /** Per-planet environmental hazard a suit variant must counter (NONE elsewhere). */
    public enum HazardShield {
        NONE, HEAT, COLD, RADIATION;

        /** The internal mirror of a public {@link Hazard}. */
        public static HazardShield of(Hazard hazard) {
            return switch (hazard) {
                case HEAT -> HEAT;
                case COLD -> COLD;
                case RADIATION -> RADIATION;
                case NONE -> NONE;
            };
        }
    }

    /** Oxygen-suit capacity tier worn (orthogonal to the heat/cold {@link HazardShield} variant). */
    public enum SuitTier {
        NONE, TIER_1, TIER_2
    }

    /**
     * The worn suit's capacity tier for the HUD badge: a full Tier-2 set reads TIER_2, any other full
     * Oxygen Suit set (base / heat / cold) reads TIER_1, otherwise NONE. Client-safe (armour slots only).
     */
    public static SuitTier suitTier(Player player) {
        if (isFullSet(player, ModItems.OXYGEN_SUIT_T2_HELMET.get(), ModItems.OXYGEN_SUIT_T2_CHESTPLATE.get(),
                ModItems.OXYGEN_SUIT_T2_LEGGINGS.get(), ModItems.OXYGEN_SUIT_T2_BOOTS.get())) {
            return SuitTier.TIER_2;
        }
        return isFullSuit(player) ? SuitTier.TIER_1 : SuitTier.NONE;
    }

    /** Whether all four armour slots hold exactly the given pieces. */
    private static boolean isFullSet(Player player, Item head, Item chest, Item legs, Item feet) {
        return player.getItemBySlot(EquipmentSlot.HEAD).is(head)
                && player.getItemBySlot(EquipmentSlot.CHEST).is(chest)
                && player.getItemBySlot(EquipmentSlot.LEGS).is(legs)
                && player.getItemBySlot(EquipmentSlot.FEET).is(feet);
    }

    /**
     * The primary hazard a dimension carries, from its planet traits (Cindara runs hot, Glacira cold; NONE off
     * a planet). Public for the HUD. Per-position overlays (biome tags, weather) are {@link #hazardAt}.
     */
    public static HazardShield hazardFor(ResourceKey<Level> dimension) {
        PlanetDefinition planet = Planets.get(dimension);
        return planet == null ? HazardShield.NONE : HazardShield.of(planet.traits().hazard());
    }

    /** The hazard at an exact position (biome and weather overlays included). Server-side. */
    public static HazardShield hazardAt(ServerLevel level, BlockPos pos) {
        return HazardShield.of(PlanetHazards.at(level, pos));
    }

    /** The per-player hazard here (first unshielded of RADIATION, HEAT, COLD), for the HUD and the damage tick. */
    public static HazardShield hazardAt(ServerLevel level, BlockPos pos, Player player) {
        return HazardShield.of(PlanetHazards.at(level, pos, player));
    }

    /**
     * Graded radiation damage for an exposed player without a full Rad-Lined suit: nothing below level 2, 1 at
     * level 2, 2 at level 3 ({@link RadiationRules#damageFor}); a flare adds its +1 through the effective level.
     */
    private static void radiationHit(ServerLevel level, ServerPlayer player) {
        float damage = RadiationRules.damageFor(PlanetHazards.radiationLevel(level, player.blockPosition()));
        if (damage > 0.0F) {
            player.hurtServer(level, level.damageSources().magic(), damage);
        }
    }

    /**
     * The worn suit for hazard purposes: a full matching Thermal or Cryo set, else a full set of Tier 2 class pieces
     * (Tier 2, Thermal or Cryo, mixed), else any full Oxygen Suit (Tier 1), else nothing. Client-safe (armour slots
     * only).
     */
    public static HazardOrderRules.SuitClass suitClass(Player player) {
        HazardShield variant = hazardShield(player);
        if (variant == HazardShield.HEAT) {
            return HazardOrderRules.SuitClass.THERMAL;
        }
        if (variant == HazardShield.COLD) {
            return HazardOrderRules.SuitClass.CRYO;
        }
        if (isSuitPiece(player.getItemBySlot(EquipmentSlot.HEAD), ModItems.OXYGEN_SUIT_T2_HELMET.get(),
                        ModItems.OXYGEN_SUIT_HEAT_HELMET.get(), ModItems.OXYGEN_SUIT_COLD_HELMET.get())
                && isSuitPiece(player.getItemBySlot(EquipmentSlot.CHEST), ModItems.OXYGEN_SUIT_T2_CHESTPLATE.get(),
                        ModItems.OXYGEN_SUIT_HEAT_CHESTPLATE.get(), ModItems.OXYGEN_SUIT_COLD_CHESTPLATE.get())
                && isSuitPiece(player.getItemBySlot(EquipmentSlot.LEGS), ModItems.OXYGEN_SUIT_T2_LEGGINGS.get(),
                        ModItems.OXYGEN_SUIT_HEAT_LEGGINGS.get(), ModItems.OXYGEN_SUIT_COLD_LEGGINGS.get())
                && isSuitPiece(player.getItemBySlot(EquipmentSlot.FEET), ModItems.OXYGEN_SUIT_T2_BOOTS.get(),
                        ModItems.OXYGEN_SUIT_HEAT_BOOTS.get(), ModItems.OXYGEN_SUIT_COLD_BOOTS.get())) {
            return HazardOrderRules.SuitClass.T2;
        }
        return isFullSuit(player) ? HazardOrderRules.SuitClass.T1 : HazardOrderRules.SuitClass.NONE;
    }

    /**
     * The hazards the worn suit's variant shields regardless of position: the full-set heat / cryo variant, plus
     * RADIATION when all four worn suit pieces carry the Rad Lining upgrade. A Tier 2 class suit also keeps out a
     * cold world's milder cold, which depends on where the player stands: use
     * {@link PlanetHazards#suitShields(Level, BlockPos, Player)} for that. A fresh mutable set. Client-safe.
     */
    public static Set<Hazard> shields(Player player) {
        Set<Hazard> out = EnumSet.noneOf(Hazard.class);
        HazardShield variant = hazardShield(player);
        if (variant == HazardShield.HEAT) {
            out.add(Hazard.HEAT);
        } else if (variant == HazardShield.COLD) {
            out.add(Hazard.COLD);
        }
        if (radLined(player)) {
            out.add(Hazard.RADIATION);
        }
        return out;
    }

    /**
     * Whether the player wears a full Oxygen Suit (any tier / variant) whose four pieces all carry the Rad Lining
     * upgrade (applied at a smithing table to a T2, heat or cryo piece; the item id is unchanged).
     */
    public static boolean radLined(Player player) {
        return isFullSuit(player)
                && isRadLined(player.getItemBySlot(EquipmentSlot.HEAD))
                && isRadLined(player.getItemBySlot(EquipmentSlot.CHEST))
                && isRadLined(player.getItemBySlot(EquipmentSlot.LEGS))
                && isRadLined(player.getItemBySlot(EquipmentSlot.FEET));
    }

    /** Whether one stack carries the Rad Lining upgrade. */
    public static boolean isRadLined(ItemStack stack) {
        return !stack.isEmpty() && stack.has(ModDataComponents.RAD_LINING.get());
    }

    /**
     * The worn hazard shield — requires ALL FOUR pieces of the SAME variant (a heat helmet on a cryo
     * suit grants the suit's air tank but no shield), orthogonal to the base oxygen-suit set.
     */
    public static HazardShield hazardShield(Player player) {
        HazardShield head = pieceVariant(player.getItemBySlot(EquipmentSlot.HEAD));
        if (head == HazardShield.NONE) {
            return HazardShield.NONE;
        }
        if (pieceVariant(player.getItemBySlot(EquipmentSlot.CHEST)) == head
                && pieceVariant(player.getItemBySlot(EquipmentSlot.LEGS)) == head
                && pieceVariant(player.getItemBySlot(EquipmentSlot.FEET)) == head) {
            return head;
        }
        return HazardShield.NONE;
    }

    /** Which hazard variant a single worn piece belongs to (NONE for plain/T2/non-suit items). */
    private static HazardShield pieceVariant(ItemStack worn) {
        if (worn.is(ModItems.OXYGEN_SUIT_HEAT_HELMET.get())
                || worn.is(ModItems.OXYGEN_SUIT_HEAT_CHESTPLATE.get())
                || worn.is(ModItems.OXYGEN_SUIT_HEAT_LEGGINGS.get())
                || worn.is(ModItems.OXYGEN_SUIT_HEAT_BOOTS.get())) {
            return HazardShield.HEAT;
        }
        if (worn.is(ModItems.OXYGEN_SUIT_COLD_HELMET.get())
                || worn.is(ModItems.OXYGEN_SUIT_COLD_CHESTPLATE.get())
                || worn.is(ModItems.OXYGEN_SUIT_COLD_LEGGINGS.get())
                || worn.is(ModItems.OXYGEN_SUIT_COLD_BOOTS.get())) {
            return HazardShield.COLD;
        }
        return HazardShield.NONE;
    }

    /**
     * Thematic feedback for an exposed, unprotected player (no extra damage — the O₂ bar is the cost):
     * a building frost vignette on the cold world (capped below fully-frozen so vanilla freeze damage
     * never double-dips), sparse smoke shimmer on the hot one.
     */
    private static void hazardFeedback(ServerLevel level, Player player, HazardShield hazard) {
        if (hazard == HazardShield.NONE) {
            return;
        }
        if (hazard == HazardShield.COLD) {
            int cap = player.getTicksRequiredToFreeze() - 2; // never "fully frozen" => no freeze damage
            player.setTicksFrozen(Math.min(cap, player.getTicksFrozen() + CHECK_INTERVAL_TICKS * 2 + 15));
        } else if (hazard == HazardShield.HEAT && player.tickCount % (CHECK_INTERVAL_TICKS * 4) == 0) {
            level.sendParticles(ParticleTypes.SMOKE,
                    player.getX(), player.getY() + 1.2D, player.getZ(), 3, 0.25D, 0.4D, 0.25D, 0.01D);
        }
    }
}
