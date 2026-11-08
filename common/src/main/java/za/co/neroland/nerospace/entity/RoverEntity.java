package za.co.neroland.nerospace.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.nerolandcore.energy.NeroEnergyStorage;
import za.co.neroland.nerospace.config.NerospaceConfig;
import za.co.neroland.nerospace.data.NerospaceErasure;
import za.co.neroland.nerospace.menu.MenuOpener;
import za.co.neroland.nerospace.platform.EnergyLookup;
import za.co.neroland.nerospace.registry.ModDataComponents;
import za.co.neroland.nerospace.registry.ModSounds;
import za.co.neroland.nerospace.rocket.StationRegistry;
import za.co.neroland.nerospace.telemetry.NerospaceTelemetry;
import za.co.neroland.nerospace.world.gravity.GravityManager;
import za.co.neroland.nerospace.world.planet.mars.MarsContent;

/**
 * The Mars rover (2.1, D16–D19): a server-driven wheeled vehicle with two seats, a nine-slot cargo bay and a Core-FE
 * battery. It works in any dimension.
 *
 * <ul>
 *   <li><b>Driving.</b> The server reads the driver's movement keys from the vanilla vehicle input path
 *       ({@link ServerPlayer#getLastClientInput()}, the same {@code ServerboundPlayerInputPacket} every ridden entity
 *       uses) — forward/back throttle, left/right steer, jump hops (or honks at a standstill). The rover has no
 *       controlling passenger in vanilla's sense, so the client never simulates it: the server moves it inside the
 *       normal entity tick and clients interpolate. Speed and hop height scale with the local gravity
 *       ({@link GravityManager#factorAt}, re-read every half second) through {@link RoverRules}.</li>
 *   <li><b>Limits.</b> No drive on fluids or in the air (it cannot swim or fly), a one-block step, and a hop that never
 *       reaches a second block. It loads no chunks. The rover gives its riders no life support: the planet's hazards
 *       reach them exactly as they would on foot.</li>
 *   <li><b>Energy.</b> {@code rover.drainPerTick} FE per tick of throttle; parked, it draws up to
 *       {@code rover.chargeRate} FE/t from any Neroland energy block next to it through {@link EnergyLookup} (a
 *       station's Solar Wing included).</li>
 *   <li><b>Seats and cargo.</b> The vanilla horse convention: right-click to get in (the first aboard drives, one
 *       more may ride); sneak + right-click opens the cargo bay through {@link MenuOpener}.</li>
 *   <li><b>Owner (D18).</b> The placer's UUID, optional in NBT, decides only who may pack the rover up (sneak + punch;
 *       owner or operator, anyone when unowned) and in whose NeroLink list it shows. Anyone may ride. Erased with the
 *       player through {@link NerospaceErasure}.</li>
 * </ul>
 */
public class RoverEntity extends Entity {

    /** Battery charge (FE), synced for the glow pass and the cargo title. */
    private static final EntityDataAccessor<Integer> DATA_ENERGY =
            SynchedEntityData.defineId(RoverEntity.class, EntityDataSerializers.INT);

    public static final int CARGO_SLOTS = 9;
    public static final int SEATS = 2;
    /** NBT keys. Every one is optional: an absent key is the default (no owner, flat battery, empty bay). */
    public static final String OWNER_KEY = "Owner";
    public static final String ENERGY_KEY = "Energy";
    public static final String CARGO_KEY = "Cargo";

    private static final int GRAVITY_CHECK_TICKS = 10;
    private static final int CHARGE_INTERVAL = 20;
    private static final int IDLE_MOVE_INTERVAL = 10;
    private static final int MOTOR_SOUND_TICKS = 20;
    private static final int IDLE_SOUND_TICKS = 60;
    private static final int HORN_COOLDOWN = 20;
    private static final int HOP_COOLDOWN = 10;
    private static final int FLAT_NOTICE_TICKS = 60;
    private static final int MAX_CHARGE_SOURCES = 4;
    private static final double PARKED_SPEED = 0.01D;
    private static final double FLUID_SPEED_DAMPING = 0.8D;
    private static final double AIR_SPEED_DAMPING = 0.99D;
    private static final double WHEEL_RADIUS = 0.25D;
    private static final float TWO_PI = (float) (Math.PI * 2.0D);
    /** Seat attachment points (blocks, entity-local at yaw 0), from the rover model spec: driver left, passenger right. */
    private static final Vec3 DRIVER_SEAT = new Vec3(0.3125D, 0.79D, 0.3125D);
    private static final Vec3 PASSENGER_SEAT = new Vec3(-0.3125D, 0.79D, 0.3125D);

    private final Cargo cargo = new Cargo(this);
    /** The placer, or {@code null}. The only personal data a rover holds. */
    @Nullable
    private UUID owner;

    // --- Server-side drive state (never saved: a reloaded rover starts at rest) ---------------------------------
    private double speed;
    private double gravity = 1.0D;
    private int hornCooldown;
    private int soundTick;
    private int flatNoticeCooldown;

    // --- Client-side presentation (never saved or synced) --------------------------------------------------------
    private float wheelAngle;
    private float wheelAngleO;
    private float yawDelta;

    /** Client-side position interpolation so the server-driven motion is smooth (the rocket's pattern). */
    //? if <26.3 {
    private final net.minecraft.world.entity.InterpolationHandler interpolation =
            new net.minecraft.world.entity.InterpolationHandler(this);
    //?}

    @SuppressWarnings("this-escape") // idiomatic Minecraft constructor wiring
    public RoverEntity(EntityType<? extends RoverEntity> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
    }

    //? if >=26.3 {
    /*@Override
    protected net.minecraft.world.entity.InterpolationHandler createInterpolationHandler() {
        return net.minecraft.world.entity.LinearInterpolationHandler.create(this);
    }
    *///?} else {
    @Override
    public net.minecraft.world.entity.InterpolationHandler getInterpolation() {
        return this.interpolation;
    }
    //?}

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_ENERGY, 0);
    }

    // --- Energy ---------------------------------------------------------------------------------------------------

    public static int capacity() {
        return NerospaceConfig.roverCapacity();
    }

    public int getEnergy() {
        return this.entityData.get(DATA_ENERGY);
    }

    public void setEnergy(int energy) {
        this.entityData.set(DATA_ENERGY, Math.max(0, Math.min(capacity(), energy)));
    }

    public int energyPercent() {
        return RoverRules.percent(getEnergy(), capacity());
    }

    // --- Owner (D18) ------------------------------------------------------------------------------------------------

    @Nullable
    public UUID owner() {
        return this.owner;
    }

    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
    }

    public boolean isOwnedBy(@Nullable UUID player) {
        return player != null && player.equals(this.owner);
    }

    /** Erasure: clears the owner if it is {@code player}; the rover stays, unowned, so anyone may pack it up. */
    public boolean forgetOwner(@Nullable UUID player) {
        UUID before = this.owner;
        this.owner = RoverRules.forget(before, player);
        return before != this.owner;
    }

    public boolean mayPickUp(ServerPlayer player) {
        return RoverRules.mayPickUp(this.owner, player.getUUID(), StationRegistry.isOp(player));
    }

    // --- Cargo ------------------------------------------------------------------------------------------------------

    public SimpleContainer cargo() {
        return this.cargo;
    }

    /** Occupied cargo slots, 0..9 (the link module's fill figure). */
    public int cargoUsed() {
        int used = 0;
        for (int i = 0; i < this.cargo.getContainerSize(); i++) {
            if (!this.cargo.getItem(i).isEmpty()) {
                used++;
            }
        }
        return used;
    }

    // --- Movement ---------------------------------------------------------------------------------------------------

    @Override
    public float maxUpStep() {
        return RoverRules.STEP_HEIGHT;
    }

    @Override
    public void tick() {
        super.tick();
        //? if <26.3 {
        if (level().isClientSide() && this.interpolation.hasActiveInterpolation()) {
            this.interpolation.interpolate();
        }
        //?}
        if (level().isClientSide()) {
            tickWheels();
            return;
        }
        if (level() instanceof ServerLevel serverLevel) {
            tickServer(serverLevel);
        }
    }

    /** Client: spin the wheels from the synced motion (position change along the facing), D19. */
    private void tickWheels() {
        this.wheelAngleO = this.wheelAngle;
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        double forward = -Mth.sin(yaw) * (getX() - this.xo) + Mth.cos(yaw) * (getZ() - this.zo);
        this.wheelAngle += (float) (forward / WHEEL_RADIUS);
        if (this.wheelAngle > TWO_PI || this.wheelAngle < -TWO_PI) {
            float wrap = this.wheelAngle > 0.0F ? TWO_PI : -TWO_PI;
            this.wheelAngle -= wrap;
            this.wheelAngleO -= wrap;
        }
        this.yawDelta = Mth.wrapDegrees(getYRot() - this.yRotO);
    }

    /** Wheel spin (radians) for the renderer, partial-tick smoothed. */
    public float wheelAngle(float partialTick) {
        return Mth.lerp(partialTick, this.wheelAngleO, this.wheelAngle);
    }

    private Input driverInput() {
        return getFirstPassenger() instanceof ServerPlayer driver ? driver.getLastClientInput() : Input.EMPTY;
    }

    private void tickServer(ServerLevel level) {
        if (this.tickCount % GRAVITY_CHECK_TICKS == 1) {
            this.gravity = RoverRules.clampGravity(GravityManager.factorAt(level, blockPosition()));
        }
        boolean idle = getPassengers().isEmpty() && this.speed == 0.0D && onGround();
        if (idle && this.tickCount % IDLE_MOVE_INTERVAL != 0) {
            return; // parked and empty: settle (and charge) every tenth tick only, no per-tick collision work
        }

        Input input = driverInput();
        boolean inFluid = isInWater() || isInLava();
        int drain = NerospaceConfig.roverDrainPerTick();
        boolean powered = RoverRules.canDrive(getEnergy(), drain);
        boolean wantsThrottle = input.forward() != input.backward();
        boolean throttle = wantsThrottle && powered && !inFluid;

        if (input.left() != input.right() && !inFluid) {
            setYRot(Mth.wrapDegrees(getYRot() + (input.left() ? -RoverRules.TURN_DEGREES : RoverRules.TURN_DEGREES)));
        }
        if (inFluid) {
            this.speed *= FLUID_SPEED_DAMPING; // no paddling: a rover cannot swim
        } else if (onGround()) {
            double target = throttle ? RoverRules.throttleTarget(input.forward(), input.backward(), this.gravity) : 0.0D;
            this.speed = RoverRules.approachSpeed(this.speed, target, throttle);
        } else {
            this.speed *= AIR_SPEED_DAMPING; // no drive in the air: a rover cannot fly
        }
        if (Math.abs(this.speed) < 1.0E-4D) {
            this.speed = 0.0D;
        }

        double vy = getDeltaMovement().y;
        if (input.jump() && onGround() && !inFluid && this.hornCooldown <= 0) {
            if (Math.abs(this.speed) < PARKED_SPEED) {
                playSound(level, ModSounds.ROVER_HORN.get(), 0.8F, 1.0F);
                this.hornCooldown = HORN_COOLDOWN;
            } else if (drain <= 0 || getEnergy() >= RoverRules.JUMP_COST) {
                vy = RoverRules.jumpVelocity(this.gravity);
                if (drain > 0) {
                    setEnergy(getEnergy() - RoverRules.JUMP_COST);
                }
                this.hornCooldown = HOP_COOLDOWN;
            }
        }
        if (this.hornCooldown > 0) {
            this.hornCooldown--;
        }
        if (inFluid) {
            vy = Math.max(-0.15D, vy - 0.02D) * 0.9D; // sinks slowly
        } else {
            vy = (vy - RoverRules.gravityAccel(this.gravity)) * RoverRules.AIR_DRAG;
        }

        float yaw = getYRot() * Mth.DEG_TO_RAD;
        setDeltaMovement(-Mth.sin(yaw) * this.speed, vy, Mth.cos(yaw) * this.speed);
        move(MoverType.SELF, getDeltaMovement());
        if (this.horizontalCollision) {
            this.speed *= 0.5D;
        }
        if (throttle) {
            setEnergy(RoverRules.drain(getEnergy(), true, drain));
        }
        if (this.tickCount % CHARGE_INTERVAL == 0 && Math.abs(this.speed) < PARKED_SPEED) {
            charge(level);
        }
        tickSounds(level, powered);
        if (this.flatNoticeCooldown > 0) {
            this.flatNoticeCooldown--;
        } else if (wantsThrottle && !powered && getFirstPassenger() instanceof ServerPlayer driver) {
            driver.sendOverlayMessage(Component.translatable("entity.nerospace.rover.flat"));
            this.flatNoticeCooldown = FLAT_NOTICE_TICKS;
        }
    }

    private void tickSounds(ServerLevel level, boolean powered) {
        if (!(getFirstPassenger() instanceof Player)) {
            this.soundTick = 0;
            return;
        }
        this.soundTick++;
        double moving = Math.abs(this.speed);
        if (moving > 0.02D) {
            if (this.soundTick % MOTOR_SOUND_TICKS == 0) {
                float load = (float) Math.min(1.0D, moving / RoverRules.maxSpeed(this.gravity));
                playSound(level, ModSounds.ROVER_MOTOR.get(), 0.4F, 0.8F + 0.4F * load);
            }
        } else if (powered && this.soundTick % IDLE_SOUND_TICKS == 0) {
            playSound(level, ModSounds.ROVER_IDLE.get(), 0.3F, 1.0F);
        }
    }

    private void playSound(ServerLevel level, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, getX(), getY(), getZ(), sound, SoundSource.NEUTRAL, volume, pitch);
    }

    /**
     * Parked charging: pulls from at most four Neroland energy blocks touching the rover's footprint (below or
     * beside it), through Core's shared lookup. Reads loaded chunks only; bounded to the box around the hitbox.
     */
    private void charge(ServerLevel level) {
        int room = RoverRules.chargeRoom(getEnergy(), capacity(), NerospaceConfig.roverChargeRate(), CHARGE_INTERVAL);
        if (room <= 0) {
            return;
        }
        AABB box = getBoundingBox();
        int sources = 0;
        for (BlockPos pos : BlockPos.betweenClosed(Mth.floor(box.minX) - 1, Mth.floor(box.minY) - 1,
                Mth.floor(box.minZ) - 1, Mth.floor(box.maxX) + 1, Mth.floor(box.maxY), Mth.floor(box.maxZ) + 1)) {
            if (!level.isLoaded(pos) || level.getBlockEntity(pos) == null) {
                continue;
            }
            NeroEnergyStorage store = EnergyLookup.INSTANCE.find(level, pos, null);
            if (store == null) {
                continue;
            }
            long got = store.extract(room, false);
            if (got > 0L) {
                setEnergy(getEnergy() + (int) got);
                room -= (int) got;
            }
            if (room <= 0 || ++sources >= MAX_CHARGE_SOURCES) {
                return;
            }
        }
    }

    // --- Seats ------------------------------------------------------------------------------------------------------

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return passenger instanceof Player && getPassengers().size() < SEATS;
    }

    /** Seat 0 (the driver) on the left cushion, seat 1 on the right, turned with the rover (model spec). */
    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity entity, EntityDimensions dimensions, float scale) {
        int index = getPassengers().indexOf(entity);
        Vec3 seat = index == 1 ? PASSENGER_SEAT : DRIVER_SEAT;
        return seat.yRot(-getYRot() * Mth.DEG_TO_RAD);
    }

    /** The local rider turns with the rover, as in a boat. */
    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction moveFunction) {
        super.positionRider(passenger, moveFunction);
        if (passenger.isLocalInstanceAuthoritative() && this.yawDelta != 0.0F) {
            passenger.setYRot(passenger.getYRot() + this.yawDelta);
            passenger.setYHeadRot(passenger.getYHeadRot() + this.yawDelta);
        }
    }

    /** Step off to the side the rider faces, on a safe floor (the boat's rule), else vanilla's default. */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        Vec3 direction = getCollisionHorizontalEscapeVector(getBbWidth() * Mth.SQRT_OF_TWO, passenger.getBbWidth(),
                passenger.getYRot());
        double x = getX() + direction.x;
        double z = getZ() + direction.z;
        BlockPos target = BlockPos.containing(x, getBoundingBox().maxY, z);
        BlockPos below = target.below();
        List<Vec3> candidates = new ArrayList<>(2);
        double floor = level().getBlockFloorHeight(target);
        if (DismountHelper.isBlockFloorValid(floor)) {
            candidates.add(new Vec3(x, target.getY() + floor, z));
        }
        double belowFloor = level().getBlockFloorHeight(below);
        if (DismountHelper.isBlockFloorValid(belowFloor)) {
            candidates.add(new Vec3(x, below.getY() + belowFloor, z));
        }
        for (Pose pose : passenger.getDismountPoses()) {
            for (Vec3 candidate : candidates) {
                if (DismountHelper.canDismountTo(level(), candidate, passenger, pose)) {
                    passenger.setPose(pose);
                    return candidate;
                }
            }
        }
        return super.getDismountLocationForPassenger(passenger);
    }

    // --- Interaction ------------------------------------------------------------------------------------------------

    /**
     * The vanilla horse convention: right-click gets in (anyone; the first aboard drives, one passenger rides along);
     * sneak + right-click opens the cargo bay. Vanilla never seats a sneaking player, so riding is the plain click.
     * Both are only ever the clicking player's own action.
     */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 hitLocation) {
        if (player.isSecondaryUseActive()) {
            if (!level().isClientSide() && player instanceof ServerPlayer viewer) {
                return MenuOpener.openOrConsume(viewer, new SimpleMenuProvider(
                        (id, inventory, who) -> new ChestMenu(MenuType.GENERIC_9x1, id, inventory, this.cargo, 1),
                        Component.translatable("container.nerospace.rover", energyPercent())));
            }
            return InteractionResult.SUCCESS;
        }
        if (player.getVehicle() == this) {
            return InteractionResult.PASS;
        }
        if (!level().isClientSide() && player instanceof ServerPlayer rider) {
            if (!canAddPassenger(rider)) {
                rider.sendOverlayMessage(Component.translatable("entity.nerospace.rover.seats_full"));
            } else if (rider.startRiding(this)) {
                rider.sendOverlayMessage(getFirstPassenger() == rider
                        ? Component.translatable("entity.nerospace.rover.driving", energyPercent())
                        : Component.translatable("entity.nerospace.rover.riding", energyPercent()));
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    @Nullable
    public ItemStack getPickResult() {
        return new ItemStack(MarsContent.ROVER.get());
    }

    /**
     * Sneak + punch packs the rover up (owner, operator, or anyone when unowned; nobody may be aboard): the cargo
     * spills, the item keeps the charge. Nothing else damages a rover.
     */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        if (this.isRemoved() || !(damageSource.getEntity() instanceof ServerPlayer player)) {
            return false;
        }
        if (!player.isSecondaryUseActive()) {
            player.sendOverlayMessage(Component.translatable("entity.nerospace.rover.pack_hint"));
            return false;
        }
        if (isVehicle()) {
            player.sendOverlayMessage(Component.translatable("entity.nerospace.rover.occupied"));
            return false;
        }
        if (!mayPickUp(player)) {
            player.sendOverlayMessage(Component.translatable("entity.nerospace.rover.not_yours"));
            return false;
        }
        packUp(level, player);
        return true;
    }

    private void packUp(ServerLevel level, ServerPlayer player) {
        Containers.dropContents(level, this, this.cargo);
        if (!player.getAbilities().instabuild) {
            ItemStack item = toItem();
            if (!player.getInventory().add(item)) {
                this.spawnAtLocation(level, item);
            }
        }
        playSound(level, ModSounds.ROVER_PICKUP.get(), 1.0F, 1.0F);
        // Ints only (POPIA/GDPR): no player, no owner, no position.
        NerospaceTelemetry.breadcrumb("rover", "packed charge_pct=" + energyPercent());
        this.discard();
    }

    /** The {@code rover} item this rover packs into: its charge rides along on a data component. */
    public ItemStack toItem() {
        ItemStack stack = new ItemStack(MarsContent.ROVER.get());
        if (getEnergy() > 0) {
            stack.set(ModDataComponents.ROVER_ENERGY.get(), getEnergy());
        }
        return stack;
    }

    // --- Presentation -------------------------------------------------------------------------------------------

    /** The renderer culls on the model's envelope: the bull bar, mast and dish reach past the hitbox. */
    public AABB visualBounds() {
        AABB box = getBoundingBox();
        return new AABB(box.minX - 0.5D, box.minY, box.minZ - 0.5D, box.maxX + 0.5D, box.maxY + 0.75D,
                box.maxZ + 0.5D);
    }

    // --- Persistence ------------------------------------------------------------------------------------------------

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        UUID stored = RoverRules.readOwner(input.getStringOr(OWNER_KEY, ""));
        // A rover that was unloaded when its owner was erased drops the owner the moment it loads.
        this.owner = stored != null && NerospaceErasure.ownerErasedFilter(level()).test(stored) ? null : stored;
        setEnergy(input.getIntOr(ENERGY_KEY, 0));
        List<ItemStack> items = input.read(CARGO_KEY, ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of());
        for (int i = 0; i < CARGO_SLOTS; i++) {
            this.cargo.setItem(i, i < items.size() ? items.get(i) : ItemStack.EMPTY);
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        if (this.owner != null) {
            output.putString(OWNER_KEY, RoverRules.writeOwner(this.owner));
        }
        output.putInt(ENERGY_KEY, getEnergy());
        List<ItemStack> items = new ArrayList<>(CARGO_SLOTS);
        for (int i = 0; i < CARGO_SLOTS; i++) {
            items.add(this.cargo.getItem(i));
        }
        output.store(CARGO_KEY, ItemStack.OPTIONAL_CODEC.listOf(), items);
    }

    // --- Server-wide helpers (placement cap, erasure) ---------------------------------------------------------------

    /**
     * Loaded rovers this player placed, across every level (D19). Placement is rare and explicit, so a pass over loaded
     * entities is cheap enough; nothing on a tick path calls this.
     */
    public static int countOwned(MinecraftServer server, UUID player) {
        int owned = 0;
        for (ServerLevel level : server.getAllLevels()) {
            List<RoverEntity> rovers = new ArrayList<>();
            level.getEntities(EntityTypeTest.forClass(RoverEntity.class), rover -> rover.isOwnedBy(player), rovers);
            owned += rovers.size();
        }
        return owned;
    }

    /** Erasure sweep: clears this owner from every loaded rover. @return how many rovers held it */
    public static int forgetOwnerEverywhere(MinecraftServer server, UUID player) {
        int cleared = 0;
        for (ServerLevel level : server.getAllLevels()) {
            List<RoverEntity> rovers = new ArrayList<>();
            level.getEntities(EntityTypeTest.forClass(RoverEntity.class), rover -> rover.isOwnedBy(player), rovers);
            for (RoverEntity rover : rovers) {
                if (rover.forgetOwner(player)) {
                    cleared++;
                }
            }
        }
        return cleared;
    }

    /** The nine-slot cargo bay; valid while the rover exists and the viewer is aboard or within eight blocks. */
    static final class Cargo extends SimpleContainer {

        private final RoverEntity rover;

        Cargo(RoverEntity rover) {
            super(CARGO_SLOTS);
            this.rover = rover;
        }

        @Override
        public boolean stillValid(Player player) {
            return !this.rover.isRemoved()
                    && (player.getVehicle() == this.rover || player.distanceToSqr(this.rover) <= 64.0D);
        }
    }
}
