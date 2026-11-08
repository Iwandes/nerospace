package za.co.neroland.nerospace.world.planet.mars.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

import za.co.neroland.nerospace.block.rules.DriftRules;
import za.co.neroland.nerospace.block.rules.SpawnerRules;
import za.co.neroland.nerospace.world.planet.PlanetWeather;
import za.co.neroland.nerospace.world.planet.WeatherEvent;
import za.co.neroland.nerospace.world.planet.WeatherMachine;
import za.co.neroland.nerospace.world.planet.mars.MarsContent;

/**
 * A Mars dust devil: a non-living, AI-less whirl that a Dust Devil Pit releases during a dust storm. It wanders slowly
 * downwind along the ground, tosses loose item entities within two blocks into the air, never touches blocks or
 * players, and leaves after 600 ticks or as soon as the storm ends. The client draws it as a column of red dust.
 */
public class DustDevil extends Entity {

    private int age;
    private int windDirection = -1;
    /** Gallery-only: a held devil swirls on the client but never wanders, tosses items or dies out. */
    private boolean frozen;

    //? if <26.3 {
    private final InterpolationHandler interpolation = new InterpolationHandler(this);
    //?}

    @SuppressWarnings("this-escape")
    public DustDevil(EntityType<? extends DustDevil> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noPhysics = true;
    }

    /** Spawns one devil standing on {@code pit} (server side). */
    public static DustDevil spawnAt(ServerLevel level, BlockPos pit) {
        DustDevil devil = new DustDevil(MarsContent.DUST_DEVIL.get(), level);
        devil.setPos(pit.getX() + 0.5D, pit.getY() + 1.0D, pit.getZ() + 0.5D);
        level.addFreshEntity(devil);
        return devil;
    }

    /** A held devil for the creative gallery: it swirls in place and never despawns (server side). */
    public static DustDevil spawnFrozen(ServerLevel level, double x, double y, double z) {
        DustDevil devil = new DustDevil(MarsContent.DUST_DEVIL.get(), level);
        devil.frozen = true;
        devil.setPos(x, y, z);
        level.addFreshEntity(devil);
        return devil;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        // No synced data: the client draws particles from the tracked position.
    }

    //? if >=26.3 {
    /*@Override
    protected InterpolationHandler createInterpolationHandler() {
        return net.minecraft.world.entity.LinearInterpolationHandler.create(this);
    }
    *///?} else {
    @Override
    public InterpolationHandler getInterpolation() {
        return this.interpolation;
    }
    //?}
    //? if <1.21.5 {
    /*@Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        this.interpolation.interpolateTo(x, y, z, yRot, xRot, steps);
    }
    *///?}

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            //? if <26.3 {
            if (this.interpolation.hasActiveInterpolation()) {
                this.interpolation.interpolate();
            }
            //?}
            swirl();
            return;
        }
        ServerLevel server = (ServerLevel) level();
        if (this.frozen) {
            return;
        }
        this.age++;
        boolean storm = PlanetWeather.event(server) == WeatherEvent.DUST_STORM;
        if (DustDevilRules.despawns(this.age, storm)) {
            discard();
            return;
        }
        wander(server);
        if (DustDevilRules.tossTick(this.age)) {
            tossItems(server);
        }
    }

    private void wander(ServerLevel server) {
        if (this.windDirection < 0) {
            WeatherMachine weather = PlanetWeather.current(server);
            this.windDirection = weather == null ? 0 : DriftRules.stormDirection(weather.seed);
        }
        double[] step = DustDevilRules.wanderStep(this.windDirection);
        double nx = getX() + step[0];
        double nz = getZ() + step[1];
        BlockPos column = BlockPos.containing(nx, getY(), nz);
        if (!server.isLoaded(column)) {
            discard(); // never force-load a chunk; a devil that reaches unloaded ground simply dies out
            return;
        }
        int ground = server.getHeight(Heightmap.Types.MOTION_BLOCKING, column.getX(), column.getZ());
        setPos(nx, ground, nz);
    }

    private void tossItems(ServerLevel server) {
        double r = SpawnerRules.TOSS_RADIUS;
        AABB box = new AABB(getX() - r, getY() - 1.0D, getZ() - r, getX() + r, getY() + 2.5D, getZ() + r);
        RandomSource random = server.getRandom();
        for (ItemEntity item : server.getEntitiesOfClass(ItemEntity.class, box, ItemEntity::onGround)) {
            double[] v = DustDevilRules.tossVelocity(random.nextDouble(), random.nextDouble());
            item.setDeltaMovement(v[0], v[1], v[2]);
            // Push the new velocity to the client (field renamed in 26.3).
            //? if >=26.3 {
            /*item.needsSync = true;
            *///?} else {
            item.hurtMarked = true;
            //?}
        }
    }

    private void swirl() {
        RandomSource random = level().getRandom();
        BlockState dust = MarsContent.RED_REGOLITH.isPresent()
                ? MarsContent.RED_REGOLITH.get().defaultBlockState() : null;
        float spin = this.tickCount * 0.6F;
        for (int i = 0; i < 4; i++) {
            double h = random.nextDouble() * 2.5D;
            double radius = 0.25D + h * 0.3D;
            double a = spin + i * (Math.PI / 2.0D) + h;
            double x = getX() + Math.cos(a) * radius;
            double z = getZ() + Math.sin(a) * radius;
            if (dust != null) {
                level().addParticle(new BlockParticleOption(ParticleTypes.FALLING_DUST, dust), x, getY() + h, z, 0.0D, 0.05D, 0.0D);
            } else {
                level().addParticle(ParticleTypes.CLOUD, x, getY() + h, z, 0.0D, 0.02D, 0.0D);
            }
        }
    }

    /** The storm direction the devil drifts along (0..3, vanilla 2D data value), or −1 before its first tick. */
    public Direction windDirection() {
        return this.windDirection < 0 ? Direction.SOUTH : Direction.from2DDataValue(this.windDirection);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    //? if >=1.21.2 {
    @Override
    public boolean hurtServer(ServerLevel level, net.minecraft.world.damagesource.DamageSource source, float amount) {
    //?} else {
    /*// Before 1.21.2 hurt(..) runs on both sides; only the server applies it.
    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (!(this.level() instanceof ServerLevel level)) {
            return false;
        }
    *///?}
        return false;
    }

    //? if >=1.21.6 {
    @Override
    protected void readAdditionalSaveData(ValueInput input) {
    //?} else {
    /*@Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        ValueInput input = ValueInput.of(tag, this.registryAccess());
    *///?}
        this.age = input.getIntOr("Age", 0);
        this.frozen = input.getBooleanOr("Frozen", false);
    }

    //? if >=1.21.6 {
    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    //?} else {
    /*@Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        ValueOutput output = ValueOutput.of(tag, this.registryAccess());
    *///?}
        output.putInt("Age", this.age);
        output.putBoolean("Frozen", this.frozen);
    }
}
