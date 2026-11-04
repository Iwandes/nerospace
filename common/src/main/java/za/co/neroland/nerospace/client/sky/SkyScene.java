package za.co.neroland.nerospace.client.sky;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.resources.Identifier;

/**
 * What one sky frame shows, worked out from a {@link SkyProfile} with no rendering classes, so it is the same on every
 * loader and can be unit-tested. A loader's sky hook turns it into draw calls (NeoForge 26.3 today).
 *
 * <p><b>Directions</b> follow Minecraft: yaw 0 = south (+Z), 90 = west (-X), 180 = north, 270 = east (+X); pitch is
 * the elevation above the horizon (negative = below it).</p>
 *
 * <p><b>Body angle</b>: a body with {@code orbitPeriodDays > 0} has
 * {@code bodyPhase = frac(days / orbitPeriodDays + phaseOffset)}, where {@code days} is the continuous day count
 * (whole days plus the clock phase), so no body jumps when the phase wraps at sunrise; it rises in the east at bodyPhase 0, culminates at
 * 0.25 and sets in the west at 0.5 (a body with a period under half a day rises in the west instead, like Phobos).
 * Its elevation is {@code sin(2 pi bodyPhase) * 90 + inclination} at the horizon crossings; above the horizon the
 * path leans south (positive inclination) or north (negative) and culminates at {@code 90 - |inclination|}, so an
 * inclined body passes beside the zenith instead of stalling on it. {@code orbitPeriodDays == 0} or a {@code fixedAngle}
 * pins the body at that yaw / pitch.</p>
 *
 * <p><b>Draw order</b> (the renderer follows the lists in this order): zenith disc, horizon ramp, stars, galaxy,
 * {@link #auroraLayers aurora curtains}, {@link #fixedQuads fixed decals}, {@link #quads bodies and their glows,
 * shades, lights, eclipse corona and anchored decals}, {@link #cloud the cloud layer}, {@link #shootingStars shooting
 * stars}, haze. A {@link Quad} is a textured square (or {@code aspect}-wide rectangle) facing the camera at
 * {@code yaw / pitch}, {@code size} degrees tall, turned by {@code roll} and slid along its own x by {@code offsetX}
 * half-sizes; additive quads use the celestial (src-alpha additive) blend, the others the translucent one.</p>
 *
 * <p><b>Night shade</b>: a body with a {@code nightShade} gets one frame of a 16-frame phase sheet
 * ({@code <texture>_00 … _15}), frame {@code round(k · 15)} for the lit fraction {@code k = (1 − cos)/2}, centred on
 * the body and turned so its local +x faces the first sun; each frame bakes the elliptical terminator for its
 * {@code k}. City lights fade with {@code 1 − k}.</p>
 *
 * <p><b>Aurora time</b> is game time ({@code timeTicks / 20} seconds), so the curtains never jump at sunrise, when an
 * operator sets the clock, or when the clock freezes or thaws; game time is the same on every client.</p>
 *
 * <p><b>Reuse</b>: a scene is a mutable, reusable result. {@link #update} recomputes it in place from pooled quads and
 * scratch arrays and allocates nothing once its pools have grown to the profile's size (the renderer keeps one scene
 * for the session); {@link #compute} returns a fresh scene (tests, one-off callers). The lists are only valid until
 * the next {@link #update} of the same scene.</p>
 */
public final class SkyScene {

    /** One textured body to draw (a sun, moon, planet or the homeworld disk). Pooled: read, do not keep. */
    public static final class BodyDraw {
        private String id;
        private Optional<Identifier> texture;
        private float yaw;
        private float pitch;
        private float size;
        private int rgb;
        private float alpha;
        private float glow;
        private boolean emissive;
        private float roll;

        public BodyDraw(String id, Optional<Identifier> texture, float yaw, float pitch, float size, int rgb,
                float alpha, float glow, boolean emissive, float roll) {
            set(id, texture, yaw, pitch, size, rgb, alpha, glow, emissive, roll);
        }

        BodyDraw set(String id, Optional<Identifier> texture, float yaw, float pitch, float size, int rgb, float alpha,
                float glow, boolean emissive, float roll) {
            this.id = id;
            this.texture = texture;
            this.yaw = yaw;
            this.pitch = pitch;
            this.size = size;
            this.rgb = rgb;
            this.alpha = alpha;
            this.glow = glow;
            this.emissive = emissive;
            this.roll = roll;
            return this;
        }

        public String id() {
            return this.id;
        }

        public Optional<Identifier> texture() {
            return this.texture;
        }

        public float yaw() {
            return this.yaw;
        }

        public float pitch() {
            return this.pitch;
        }

        public float size() {
            return this.size;
        }

        public int rgb() {
            return this.rgb;
        }

        public float alpha() {
            return this.alpha;
        }

        public float glow() {
            return this.glow;
        }

        public boolean emissive() {
            return this.emissive;
        }

        public float roll() {
            return this.roll;
        }

        @Override
        public String toString() {
            return "BodyDraw[" + this.id + " " + this.yaw + "/" + this.pitch + "]";
        }
    }

    /** A textured quad on the sky dome (see the class note). Pooled: read, do not keep. */
    public static final class Quad {
        private Identifier texture;
        private float yaw;
        private float pitch;
        private float size;
        private float aspect;
        private float roll;
        private float offsetX;
        private int rgb;
        private float alpha;
        private boolean additive;

        public Quad(Identifier texture, float yaw, float pitch, float size, float aspect, float roll, float offsetX,
                int rgb, float alpha, boolean additive) {
            set(texture, yaw, pitch, size, aspect, roll, offsetX, rgb, alpha, additive);
        }

        Quad set(Identifier texture, float yaw, float pitch, float size, float aspect, float roll, float offsetX,
                int rgb, float alpha, boolean additive) {
            this.texture = texture;
            this.yaw = yaw;
            this.pitch = pitch;
            this.size = size;
            this.aspect = aspect;
            this.roll = roll;
            this.offsetX = offsetX;
            this.rgb = rgb;
            this.alpha = alpha;
            this.additive = additive;
            return this;
        }

        Quad copy() {
            return new Quad(this.texture, this.yaw, this.pitch, this.size, this.aspect, this.roll, this.offsetX,
                    this.rgb, this.alpha, this.additive);
        }

        public Identifier texture() {
            return this.texture;
        }

        public float yaw() {
            return this.yaw;
        }

        public float pitch() {
            return this.pitch;
        }

        public float size() {
            return this.size;
        }

        public float aspect() {
            return this.aspect;
        }

        public float roll() {
            return this.roll;
        }

        public float offsetX() {
            return this.offsetX;
        }

        public int rgb() {
            return this.rgb;
        }

        public float alpha() {
            return this.alpha;
        }

        public boolean additive() {
            return this.additive;
        }

        @Override
        public String toString() {
            return "Quad[" + this.texture + " " + this.yaw + "/" + this.pitch + " a=" + this.alpha + "]";
        }
    }

    /** One aurora curtain layer: its texture, layer index (for the fold model), alpha and tint. Pooled. */
    public static final class AuroraLayer {
        private Identifier texture;
        private int layer;
        private float alpha;
        private int tint;

        public AuroraLayer(Identifier texture, int layer, float alpha, int tint) {
            set(texture, layer, alpha, tint);
        }

        AuroraLayer set(Identifier texture, int layer, float alpha, int tint) {
            this.texture = texture;
            this.layer = layer;
            this.alpha = alpha;
            this.tint = tint;
            return this;
        }

        public Identifier texture() {
            return this.texture;
        }

        public int layer() {
            return this.layer;
        }

        public float alpha() {
            return this.alpha;
        }

        public int tint() {
            return this.tint;
        }
    }

    /** The cloud deck: tinted tiled texture up to {@code altitudeDeg}, turned by {@code rotationDeg}, with underglow. */
    public static final class Cloud {
        private Identifier texture;
        private int rgb;
        private float alpha;
        private float altitudeDeg;
        private int tiles;
        private float rotationDeg;
        private int underglowRgb;
        private float underglowAlpha;

        Cloud set(Identifier texture, int rgb, float alpha, float altitudeDeg, int tiles, float rotationDeg,
                int underglowRgb, float underglowAlpha) {
            this.texture = texture;
            this.rgb = rgb;
            this.alpha = alpha;
            this.altitudeDeg = altitudeDeg;
            this.tiles = tiles;
            this.rotationDeg = rotationDeg;
            this.underglowRgb = underglowRgb;
            this.underglowAlpha = underglowAlpha;
            return this;
        }

        public Identifier texture() {
            return this.texture;
        }

        public int rgb() {
            return this.rgb;
        }

        public float alpha() {
            return this.alpha;
        }

        public float altitudeDeg() {
            return this.altitudeDeg;
        }

        public int tiles() {
            return this.tiles;
        }

        public float rotationDeg() {
            return this.rotationDeg;
        }

        public int underglowRgb() {
            return this.underglowRgb;
        }

        public float underglowAlpha() {
            return this.underglowAlpha;
        }
    }

    /**
     * The client sky switches ({@code sky.aurora}, {@code sky.shootingStars}, {@code sky.quality=lite}). Lite halves the
     * aurora layers (at least one) and its segments, drops the cloud layer and shrinks stars by 15 %.
     */
    public record Config(boolean aurora, boolean shootingStars, boolean lite) {
        public static final Config FULL = new Config(true, true, false);
        private static final Config[] ALL = new Config[8];

        static {
            for (int i = 0; i < ALL.length; i++) {
                ALL[i] = new Config((i & 1) != 0, (i & 2) != 0, (i & 4) != 0);
            }
        }

        /** The shared instance for these switches (no allocation per frame). */
        public static Config of(boolean aurora, boolean shootingStars, boolean lite) {
            return ALL[(aurora ? 1 : 0) | (shootingStars ? 2 : 0) | (lite ? 4 : 0)];
        }
    }

    /**
     * Everything a frame depends on besides the profile. Mutable so the renderer can refill one instance per frame
     * ({@link #set}); the accessors keep the record-style names.
     *
     * <p>{@code phase} planet clock phase 0..1 (0 sunrise, 0.25 noon, 0.5 sunset); {@code days} continuous day count
     * whose fractional part is {@code phase} (body angles only); {@code daylight} 0..1 from the planet's daylight
     * curve; {@code weather} current weather event name ({@code "NONE"} when clear); {@code intensity} smoothed weather
     * intensity 0..1; {@code recoveryEvent} the event whose after-image {@code recovery} describes; {@code recovery} 1
     * just after {@code recoveryEvent} cleared, falling to 0 a minute later; {@code position} player coordinate on the
     * profile's {@code positionBlend} axis; {@code bandWidth} full band width for {@code positionBlend} (blocks);
     * {@code timeTicks} game time plus partial tick (aurora, drift, shooting stars, disk roll, twinkle);
     * {@code dayLengthTicks} the planet's day length (0 for a fixed clock), scales the aurora drift period;
     * {@code frozen} whether the planet clock is frozen (informational: the sky reads game time); {@code seed}
     * per-planet seed for the shooting-star schedule; {@code config} the client sky switches.</p>
     */
    public static final class Inputs {
        double phase;
        double days;
        double daylight;
        String weather;
        float intensity;
        String recoveryEvent;
        float recovery;
        double position;
        int bandWidth;
        double timeTicks;
        long dayLengthTicks;
        boolean frozen;
        long seed;
        Config config;

        public Inputs(double phase, double days, double daylight, String weather, float intensity,
                String recoveryEvent, float recovery, double position, int bandWidth, double timeTicks,
                long dayLengthTicks, boolean frozen, long seed, Config config) {
            set(phase, days, daylight, weather, intensity, recoveryEvent, recovery, position, bandWidth, timeTicks,
                    dayLengthTicks, frozen, seed, config);
        }

        public Inputs set(double phase, double days, double daylight, String weather, float intensity,
                String recoveryEvent, float recovery, double position, int bandWidth, double timeTicks,
                long dayLengthTicks, boolean frozen, long seed, Config config) {
            this.phase = phase;
            this.days = days;
            this.daylight = daylight;
            this.weather = weather;
            this.intensity = intensity;
            this.recoveryEvent = recoveryEvent;
            this.recovery = recovery;
            this.position = position;
            this.bandWidth = bandWidth;
            this.timeTicks = timeTicks;
            this.dayLengthTicks = dayLengthTicks;
            this.frozen = frozen;
            this.seed = seed;
            this.config = config;
            return this;
        }

        public double phase() {
            return this.phase;
        }

        public double days() {
            return this.days;
        }

        public double daylight() {
            return this.daylight;
        }

        public String weather() {
            return this.weather;
        }

        public float intensity() {
            return this.intensity;
        }

        public String recoveryEvent() {
            return this.recoveryEvent;
        }

        public float recovery() {
            return this.recovery;
        }

        public double position() {
            return this.position;
        }

        public int bandWidth() {
            return this.bandWidth;
        }

        public double timeTicks() {
            return this.timeTicks;
        }

        public long dayLengthTicks() {
            return this.dayLengthTicks;
        }

        public boolean frozen() {
            return this.frozen;
        }

        public long seed() {
            return this.seed;
        }

        public Config config() {
            return this.config;
        }
    }

    /** Aurora segments along the arc ({@link Config#lite} halves them). */
    public static final int AURORA_SEGMENTS = 48;
    public static final int AURORA_SEGMENTS_LITE = 24;
    /** Curtain layers a profile may ask for; the weather fringe is drawn on top as one more layer. */
    public static final int AURORA_MAX_LAYERS = 4;
    /** Frames in a night-shade phase sheet ({@code <texture>_00 … _15}). */
    public static final int SHADE_FRAMES = 16;
    private static final Identifier AURORA_0 = SkyProfile.sky("aurora_0");
    private static final Identifier AURORA_1 = SkyProfile.sky("aurora_1");
    private static final Identifier STARS = SkyProfile.sky("stars");
    private static final Identifier STARS_NATURAL = SkyProfile.sky("stars_natural");
    private static final Identifier DEFAULT_SHADE = SkyProfile.sky("terminator");
    private static final Identifier[] DEFAULT_SHADE_SHEET = shadeSheet(DEFAULT_SHADE);
    // Phase sheets of resource-pack shade textures, resolved once per texture.
    private static final ConcurrentHashMap<Identifier, Identifier[]> SHADE_SHEETS = new ConcurrentHashMap<>();

    public int zenith;
    public int horizon;
    public int fog;
    /** Star alpha 0..1 and tint. */
    public float starAlpha;
    public int starTint;
    public int starCount;
    public float galaxyAlpha;
    public Optional<Identifier> galaxyTexture = Optional.empty();
    public float galaxyYaw;
    public float galaxyPitch;
    /** Aurora alpha 0..1 and its current colour (cycled through the profile's list). */
    public float auroraAlpha;
    public int auroraColor = 0xFFFFFF;
    public float auroraShift;
    public float hazeAlpha;
    public int hazeColor;
    /** Draw order: suns (emissive) first, then planets and moons (they may cover a sun: the eclipse). */
    public final List<BodyDraw> bodies = new ArrayList<>();

    /** Elevation where the horizon ramp starts, and its texture. */
    public float rampTopDeg = 55.0F;
    public Identifier rampTexture = SkyProfile.DEFAULT_RAMP;
    /** Star sheet, quad size scale, and the alphas of the two twinkle halves (equal when twinkle is off). */
    public Identifier starTexture = STARS;
    public float starSizeScale = 1.0F;
    public float starAlphaA;
    public float starAlphaB;
    /** The aurora the curtains come from (shape parameters), its layers, centre yaw, clock and segment count. */
    public Optional<SkyProfile.Aurora> aurora = Optional.empty();
    public final List<AuroraLayer> auroraLayers = new ArrayList<>();
    public float auroraYaw = 180.0F;
    public double auroraSeconds;
    public int auroraSegments = AURORA_SEGMENTS;
    /** Fixed decals (after the aurora, before the bodies). */
    public final List<Quad> fixedQuads = new ArrayList<>();
    /** Bodies with their glows, shades and lights, the eclipse corona and ring, then the anchored decals. */
    public final List<Quad> quads = new ArrayList<>();
    public Optional<Cloud> cloud = Optional.empty();
    public final List<Quad> shootingStars = new ArrayList<>();

    // Pools and scratch: grown on the first frames, then reused (no per-frame allocation).
    private final ArrayList<Quad> quadPool = new ArrayList<>();
    private int quadsUsed;
    private final ArrayList<BodyDraw> bodyPool = new ArrayList<>();
    private int bodiesUsed;
    private final ArrayList<AuroraLayer> layerPool = new ArrayList<>();
    private int layersUsed;
    private final Cloud cloudValue = new Cloud();
    private final Optional<Cloud> cloudPresent = Optional.of(this.cloudValue);
    private final int[] colors = new int[3];
    private final float[] sides = new float[3];
    private final float[] yp = new float[2];
    private final float[] sunAngles = new float[2];
    private final float[] sunDir = new float[3];
    private final float[] dir = new float[3];
    private final float[] at = new float[2];
    private final float[] shadeOut = new float[2];
    private final float[] ex = new float[3];
    private final float[] ey = new float[3];
    private final float[] moonYp = new float[2];
    private final float[] moonDir = new float[3];
    private final float[] local = new float[3];
    // Gradient keys sorted once when a profile lists them out of order (the shipped ones are in order).
    private List<SkyProfile.GradientKey> unsortedKeys;
    private List<SkyProfile.GradientKey> sortedKeys;

    /** An empty scene, to {@link #update} every frame. */
    public SkyScene() {
    }

    /**
     * The 2.0.0 entry point: no recovery, full config, no day length.
     *
     * @param phase       planet clock phase 0..1 (0 sunrise, 0.25 noon, 0.5 sunset)
     * @param days        continuous day count whose fractional part is {@code phase} (body angles only)
     * @param daylight    0..1 from the planet's daylight curve
     * @param weather     current weather event name ({@code "NONE"} when clear)
     * @param intensity   weather intensity 0..1
     * @param position    player coordinate on the profile's {@code positionBlend} axis
     * @param bandWidth   full band width for {@code positionBlend} (blocks)
     * @param timeTicks   game time plus partial tick, for slow animation (aurora drift, disk roll)
     */
    public static SkyScene compute(SkyProfile p, double phase, double days, double daylight, String weather,
            float intensity, double position, int bandWidth, double timeTicks) {
        return compute(p, new Inputs(phase, days, daylight, weather, intensity, "NONE", 0.0F, position, bandWidth,
                timeTicks, 0L, false, 0L, Config.FULL));
    }

    /** A fresh scene for {@code in} (allocates; the renderer reuses one scene through {@link #update}). */
    public static SkyScene compute(SkyProfile p, Inputs in) {
        return new SkyScene().update(p, in);
    }

    private void reset() {
        this.quadsUsed = 0;
        this.bodiesUsed = 0;
        this.layersUsed = 0;
        this.bodies.clear();
        this.auroraLayers.clear();
        this.fixedQuads.clear();
        this.quads.clear();
        this.shootingStars.clear();
        this.galaxyTexture = Optional.empty();
        this.galaxyYaw = 0.0F;
        this.galaxyPitch = 0.0F;
        this.galaxyAlpha = 0.0F;
        this.aurora = Optional.empty();
        this.auroraAlpha = 0.0F;
        this.auroraColor = 0xFFFFFF;
        this.auroraShift = 0.0F;
        this.auroraYaw = 180.0F;
        this.auroraSeconds = 0.0;
        this.auroraSegments = AURORA_SEGMENTS;
        this.hazeAlpha = 0.0F;
        this.hazeColor = 0;
        this.cloud = Optional.empty();
    }

    private Quad quad(Identifier texture, float yaw, float pitch, float size, float aspect, float roll, float offsetX,
            int rgb, float alpha, boolean additive) {
        if (this.quadsUsed < this.quadPool.size()) {
            return this.quadPool.get(this.quadsUsed++).set(texture, yaw, pitch, size, aspect, roll, offsetX, rgb,
                    alpha, additive);
        }
        Quad q = new Quad(texture, yaw, pitch, size, aspect, roll, offsetX, rgb, alpha, additive);
        this.quadPool.add(q);
        this.quadsUsed++;
        return q;
    }

    private void body(String id, Optional<Identifier> texture, float yaw, float pitch, float size, int rgb,
            float alpha, float glow, boolean emissive, float roll) {
        BodyDraw b;
        if (this.bodiesUsed < this.bodyPool.size()) {
            b = this.bodyPool.get(this.bodiesUsed).set(id, texture, yaw, pitch, size, rgb, alpha, glow, emissive, roll);
        } else {
            b = new BodyDraw(id, texture, yaw, pitch, size, rgb, alpha, glow, emissive, roll);
            this.bodyPool.add(b);
        }
        this.bodiesUsed++;
        this.bodies.add(b);
    }

    private void layer(Identifier texture, int layer, float alpha, int tint) {
        AuroraLayer l;
        if (this.layersUsed < this.layerPool.size()) {
            l = this.layerPool.get(this.layersUsed).set(texture, layer, alpha, tint);
        } else {
            l = new AuroraLayer(texture, layer, alpha, tint);
            this.layerPool.add(l);
        }
        this.layersUsed++;
        this.auroraLayers.add(l);
    }

    /** Recomputes this scene in place for {@code in}; returns {@code this}. */
    public SkyScene update(SkyProfile p, Inputs in) {
        reset();
        Config cfg = in.config == null ? Config.FULL : in.config;
        String weather = in.weather == null ? "NONE" : in.weather;
        String recoveryEvent = in.recoveryEvent == null ? "NONE" : in.recoveryEvent;
        float intensity = clamp01(in.intensity);
        double d = clamp01(in.daylight);
        double ph = frac(in.phase);
        double seconds = in.timeTicks / 20.0;
        int[] c = this.colors;
        if (p.positionBlend().isPresent()) {
            positionColorsInto(p.positionBlend().get(), in.position, in.bandWidth, c);
        } else {
            baseColorsInto(p, ph, d, c);
        }
        float bodyFade = 0.0F;
        float starFade = 0.0F;
        float hazeBoost = 0.0F;
        String hazeTo = null;
        SkyProfile.WeatherTint tint = p.weatherTints().get(weather);
        if (tint != null && intensity > 0.0F) {
            float s = intensity * tint.strengthAtPeak();
            if (s > 0.0F) {
                int sky = SkyProfile.rgb(tint.sky());
                c[0] = lerpRgb(c[0], sky, s);
                c[1] = lerpRgb(c[1], sky, s);
                c[2] = lerpRgb(c[2], SkyProfile.rgb(tint.fog()), s);
            }
            bodyFade = clamp01(tint.bodyFade() * intensity);
            starFade = clamp01(tint.starFade() * intensity);
            hazeBoost = tint.hazeBoost() * intensity;
            hazeTo = tint.hazeColor().isPresent() ? tint.hazeColor().get() : null;
        }
        float eclipseLight = eclipseLight(p, ph);
        float totality = 0.0F;
        if (eclipseLight < 1.0F) {
            float k = 1.0F - (1.0F - eclipseLight) * 0.85F;
            c[0] = scaleRgb(c[0], k);
            c[1] = scaleRgb(c[1], k);
            c[2] = scaleRgb(c[2], k);
            float min = p.eclipse().isPresent() ? p.eclipse().get().minLight() : 0.1F;
            totality = clamp01((1.0F - eclipseLight) / Math.max(1e-6F, 1.0F - min));
        }
        this.zenith = c[0];
        this.horizon = c[1];
        this.fog = c[2];
        this.rampTopDeg = Math.max(5.0F, Math.min(89.0F, p.background().rampTopDeg()));
        this.rampTexture = p.background().rampTexture().orElse(SkyProfile.DEFAULT_RAMP);

        // Stars: weather fade, palette, size and the two twinkle halves.
        double night = 1.0 - d;
        SkyProfile.Stars st = p.stars();
        float starAlpha = st.alwaysVisible() ? st.brightness() : (float) (st.brightness() * night * night);
        starAlpha = clamp01(starAlpha) * (1.0F - starFade);
        this.starAlpha = starAlpha;
        this.starTint = SkyProfile.rgb(st.tint());
        this.starCount = Math.max(0, Math.min(8000, st.count()));
        this.starTexture = "natural".equals(st.palette()) ? STARS_NATURAL : STARS;
        this.starSizeScale = Math.max(0.05F, st.sizeScale()) * (cfg.lite() ? 0.85F : 1.0F);
        float tw = clamp01(st.twinkle());
        double wave = Math.sin(2.0 * Math.PI * seconds / 1.7);
        this.starAlphaA = clamp01((float) (starAlpha * (1.0 - tw * 0.5 * (1.0 - wave))));
        this.starAlphaB = clamp01((float) (starAlpha * (1.0 - tw * 0.5 * (1.0 + wave))));

        if (p.galaxy().isPresent()) {
            SkyProfile.Galaxy g = p.galaxy().get();
            this.galaxyTexture = g.texture();
            this.galaxyYaw = g.yaw();
            this.galaxyPitch = g.pitch();
            this.galaxyAlpha = clamp01(g.opacity()
                    * (float) ("constant".equals(p.background().blend()) ? 1.0 : Math.pow(night, 1.5)))
                    * (1.0F - starFade);
        }

        sideWeightsInto(p.positionBlend().isPresent(), in.position, in.bandWidth, this.sides);
        aurora(p, in, cfg, weather, recoveryEvent, intensity, d);

        // Bodies: suns first, then planets, then moons; each body's glows, shade and lights travel with it.
        float bodyAlpha = 1.0F - bodyFade;
        float roll = 0.0F;
        if (p.terminatorScroll().isPresent() && p.terminatorScroll().get().periodTicks() > 0) {
            roll = (float) (frac(in.timeTicks / p.terminatorScroll().get().periodTicks()) * 360.0);
        }
        boolean haveSun = false;
        List<SkyProfile.Body> suns = p.sun();
        for (int i = 0; i < suns.size(); i++) {
            SkyProfile.Body b = suns.get(i);
            bodyAnglesInto(b, in.days, this.yp);
            float yaw = this.yp[0];
            float pitch = this.yp[1];
            boolean first = !haveSun;
            if (first) {
                haveSun = true;
                directionInto(yaw, pitch, this.sunDir);
                this.sunAngles[0] = yaw;
                this.sunAngles[1] = pitch;
            }
            body(b.id(), b.texture(), yaw, pitch, b.size(), SkyProfile.rgb(b.color()), bodyAlpha, b.glow(), true, 0.0F);
            sun(b, yaw, pitch, bodyAlpha);
            if (first) {
                eclipseDecals(p, b, yaw, pitch, totality, bodyAlpha, in.days);
            }
        }
        float earthshine = 0.0F;
        earthshine += addSolid(p.bodies(), in.days, haveSun, roll, bodyAlpha);
        earthshine += addSolid(p.moons(), in.days, haveSun, roll, bodyAlpha);

        // Decals: fixed ones go behind the bodies; anchored ones after them.
        List<SkyProfile.Decal> decals = p.decals();
        for (int i = 0; i < decals.size(); i++) {
            SkyProfile.Decal dc = decals.get(i);
            boolean fixed = !"sun".equals(dc.anchor()) && !"antisun".equals(dc.anchor()) && !"body".equals(dc.anchor());
            if (!anchorInto(dc, haveSun, this.at)) {
                continue;
            }
            float vis = visibility(dc.visibility(), d, weather, intensity, recoveryEvent, in.recovery, this.sides,
                    fixed ? Float.NaN : this.at[1], totality);
            float alpha = clamp01(dc.opacity() * vis * bodyAlpha);
            if (alpha <= 0.005F) {
                continue;
            }
            Quad q = quad(dc.texture(), this.at[0], this.at[1], dc.size(), dc.aspect(),
                    (float) (dc.roll() + dc.spinDegPerSecond() * seconds), 0.0F, SkyProfile.rgb(dc.color()), alpha,
                    dc.additive());
            (fixed ? this.fixedQuads : this.quads).add(q);
        }

        // Haze: day / night pair, weather boost and earthshine.
        if (p.horizonHaze().isPresent()) {
            SkyProfile.Haze h = p.horizonHaze().get();
            int hcol = SkyProfile.rgb(h.color());
            float hstr = h.strength();
            if (h.nightColor().isPresent()) {
                hcol = lerpRgb(SkyProfile.rgb(h.nightColor().get()), hcol, (float) d);
                float nightStrength = h.nightStrength().isPresent() ? h.nightStrength().get() : hstr;
                hstr = lerp(nightStrength, hstr, (float) d);
            }
            hstr = Math.min(1.0F, hstr + hazeBoost + earthshine * 0.25F);
            if (hazeTo != null && hazeBoost > 0.0F) {
                hcol = lerpRgb(hcol, SkyProfile.rgb(hazeTo), hazeBoost);
            }
            this.hazeAlpha = clamp01(hstr);
            this.hazeColor = hcol;
        }

        // Cloud deck (dropped in lite).
        if (p.cloudLayer().isPresent() && !cfg.lite()) {
            SkyProfile.CloudLayer cl = p.cloudLayer().get();
            float op = cl.opacity();
            Float w = cl.weatherOpacity().get(weather);
            if (w != null) {
                op = lerp(op, w, intensity);
            }
            int rgb = lerpRgb(SkyProfile.rgb(cl.nightColor().isPresent() ? cl.nightColor().get() : cl.color()),
                    SkyProfile.rgb(cl.color()), (float) d);
            float rot = (float) ((cl.driftDegPerSecond() * seconds) % 360.0);
            boolean glow = cl.underglow().isPresent();
            int under = glow ? SkyProfile.rgb(cl.underglow().get()) : 0;
            float underAlpha = glow ? clamp01(op * cl.underglowStrength()) : 0.0F;
            this.cloudValue.set(cl.texture(), rgb, clamp01(op), Math.max(1.0F, Math.min(89.0F, cl.altitudeDeg())),
                    Math.max(1, cl.tilesAround()), rot, under, underAlpha);
            this.cloud = this.cloudPresent;
        }

        // Shooting stars (local flavour; deterministic per planet seed).
        if (p.shootingStars().isPresent() && cfg.shootingStars()) {
            shootingStarsInto(p.shootingStars().get(), in.timeTicks, in.seed, d);
        }
        return this;
    }

    // ------------------------------------------------------------------------------------------------ aurora

    private void aurora(SkyProfile p, Inputs in, Config cfg, String weather, String recoveryEvent, float intensity,
            double d) {
        if (p.aurora().isEmpty()) {
            return;
        }
        SkyProfile.Aurora a = p.aurora().get();
        this.aurora = p.aurora();
        float vis = a.nightOnly() ? nightWeight(d) : 1.0F;
        float boost = a.boostOnWeather().isPresent() && a.boostOnWeather().get().equals(weather)
                ? 1.0F + 0.4F * intensity : 1.0F;
        float gate = weatherGate(a.weather(), weather, intensity, recoveryEvent, in.recovery);
        float side = a.positionSides().isEmpty() ? 1.0F : sum(this.sides, a.positionSides());
        float alpha = Math.min(1.0F, a.opacity() * boost) * vis * gate * side;
        if (!cfg.aurora()) {
            alpha = 0.0F;
        }
        this.auroraAlpha = clamp01(alpha);
        double t = auroraSeconds(in.timeTicks);
        double cyc = t * 0.05 * a.speed();
        this.auroraColor = cycle(a.colors(), cyc);
        this.auroraShift = (float) frac(cyc * 0.25);
        this.auroraSeconds = t;
        this.auroraYaw = auroraYaw(a, in.timeTicks, in.dayLengthTicks);
        this.auroraSegments = cfg.lite() ? AURORA_SEGMENTS_LITE : AURORA_SEGMENTS;
        if (this.auroraAlpha <= 0.005F) {
            return;
        }
        boolean legacy = a.textures().isEmpty();
        int n = Math.max(1, Math.min(AURORA_MAX_LAYERS, a.layers()));
        if (cfg.lite()) {
            n = Math.max(1, n / 2);
        }
        for (int layer = 0; layer < n; layer++) {
            Identifier tex = legacy ? (layer % 2 == 0 ? AURORA_0 : AURORA_1) : a.textures().get(layer % a.textures().size());
            layer(tex, layer, this.auroraAlpha * (1.0F - 0.18F * layer), legacy ? this.auroraColor : 0xFFFFFF);
        }
        if (a.fringe().isPresent() && a.fringe().get().weather().equals(weather) && intensity > 0.0F) {
            float fa = clamp01(this.auroraAlpha * a.fringe().get().strength() * intensity);
            if (fa > 0.005F) {
                layer(a.fringe().get().texture(), 0, fa, 0xFFFFFF);
            }
        }
    }

    /** Aurora seconds: game time, continuous across sunrise, clock sets and freezes. */
    public static double auroraSeconds(double timeTicks) {
        return timeTicks / 20.0;
    }

    /**
     * Curtain centre yaw: {@code yaw + drift.amplitude · sin(2π · d / drift.periodDays)}, where {@code d} counts planet
     * days of game time ({@code timeTicks / dayLengthTicks}, or vanilla days on a fixed clock).
     */
    public static float auroraYaw(SkyProfile.Aurora a, double timeTicks, long dayLengthTicks) {
        float yaw = a.yaw();
        if (a.drift().isPresent()) {
            SkyProfile.Drift dr = a.drift().get();
            double dayTicks = dayLengthTicks > 0L ? dayLengthTicks : 24000.0;
            yaw += (float) (dr.amplitudeDeg() * Math.sin(2.0 * Math.PI * (timeTicks / dayTicks)
                    / Math.max(1e-3, dr.periodDays())));
        }
        return yaw;
    }

    /**
     * The fold model for aurora layer {@code layer} at normalised arc position {@code un} (0..1) and aurora seconds
     * {@code t}: writes {base elevation, top elevation, top-row azimuth shear, texture u} into {@code out} (length ≥ 4).
     * Allocation-free; the renderer calls it per vertex column.
     */
    public static void curtain(SkyProfile.Aurora a, int layer, double un, double t, double[] out) {
        SkyProfile.Fold fold = a.fold();
        double amp = fold.amplitudeDeg();
        double f = fold.frequency();
        double sp = fold.speed() * a.speed();
        double fl = amp * (0.6 * Math.sin(2.0 * Math.PI * (f * un + 0.011 * sp * t + 0.37 * layer))
                + 0.4 * Math.sin(2.0 * Math.PI * (1.9 * f * un - 0.017 * sp * t + 1.3 + 0.61 * layer)));
        out[0] = a.baseDeg() + fl + 2.5 * layer;
        out[1] = a.topDeg() + 0.5 * fl + 3.0 * Math.sin(2.0 * Math.PI * (0.004 * sp * t + 0.2 * layer)) - 4.0 * layer;
        out[2] = 6.0 * Math.sin(2.0 * Math.PI * (0.7 * f * un + 0.008 * sp * t + 0.5 * layer));
        double scroll = t * 0.0025 * sp * (1.0 + 0.35 * layer) * ((layer & 1) == 1 ? -1.0 : 1.0);
        out[3] = un * 1.5 + scroll;
    }

    // ------------------------------------------------------------------------------------------------ bodies

    private void sun(SkyProfile.Body b, float yaw, float pitch, float alpha) {
        int rgb = SkyProfile.rgb(b.color());
        if (b.limbGlow().isPresent()) {
            SkyProfile.Glow g = b.limbGlow().get();
            this.quads.add(quad(g.texture(), yaw, pitch, b.size() * g.scale(), 1.0F, 0.0F, 0.0F,
                    SkyProfile.rgb(g.color()), clamp01(g.strength() * alpha), true));
        }
        if (b.texture().isEmpty()) {
            return;
        }
        Identifier tex = b.texture().get();
        if (b.glow() > 0.0F) {
            this.quads.add(quad(b.glowTexture().isPresent() ? b.glowTexture().get() : tex, yaw, pitch,
                    b.size() * (1.0F + b.glow() * 0.12F), 1.0F, 0.0F, 0.0F, rgb, 0.35F * alpha, true));
        }
        this.quads.add(quad(tex, yaw, pitch, b.size(), 1.0F, 0.0F, 0.0F, rgb, alpha, true));
        if (b.horizonGlow().isPresent()) {
            SkyProfile.HorizonGlow hg = b.horizonGlow().get();
            float w = horizonGlowWeight(hg, pitch);
            if (w > 0.0F) {
                this.quads.add(quad(hg.texture(), yaw, pitch, hg.size(), 1.0F, 0.0F, 0.0F, SkyProfile.rgb(hg.color()),
                        clamp01(hg.strength() * w * alpha), true));
            }
        }
    }

    /** {@code clamp01(1 − pitch / pitchBelow)} while the sun is within {@code ±pitchBelow} of the horizon, else 0. */
    public static float horizonGlowWeight(SkyProfile.HorizonGlow hg, float pitch) {
        float pb = Math.max(1e-3F, hg.pitchBelow());
        return pitch > -pb && pitch < pb ? clamp01(1.0F - pitch / pb) : 0.0F;
    }

    private void eclipseDecals(SkyProfile p, SkyProfile.Body sun, float yaw, float pitch, float totality, float alpha,
            double days) {
        if (p.eclipse().isEmpty() || totality <= 0.0F) {
            return;
        }
        SkyProfile.Eclipse e = p.eclipse().get();
        if (e.corona().isPresent()) {
            SkyProfile.Corona co = e.corona().get();
            float a = clamp01(co.strength() * totality * totality * alpha);
            if (a > 0.005F) {
                this.quads.add(quad(co.texture(), yaw, pitch, sun.size() * co.size(), 1.0F, 0.0F, 0.0F,
                        SkyProfile.rgb(co.color()), a, true));
            }
        }
        if (e.diamondRing().isPresent()) {
            SkyProfile.DiamondRing dr = e.diamondRing().get();
            float win = Math.max(1e-3F, dr.window());
            float flash = clamp01(1.0F - Math.abs(totality - (1.0F - win)) / win);
            SkyProfile.Body moon = findBody(p, e.body());
            if (flash > 0.0F && moon != null) {
                bodyAnglesInto(moon, days, this.moonYp);
                directionInto(this.moonYp[0], this.moonYp[1], this.moonDir);
                axesInto(yaw, pitch, this.ex, this.ey);
                double mx = dot(this.moonDir, this.ex);
                double my = dot(this.moonDir, this.ey);
                float ang = (float) Math.toDegrees(Math.atan2(my, mx));
                float off = (float) (-Math.tan(Math.toRadians(sun.size() / 2.0))
                        / Math.tan(Math.toRadians(Math.max(0.1F, dr.size()) / 2.0)));
                this.quads.add(quad(dr.texture(), yaw, pitch, dr.size(), 1.0F, ang, off, SkyProfile.rgb(dr.color()),
                        flash * alpha, true));
            }
        }
    }

    private static SkyProfile.Body findBody(SkyProfile p, String id) {
        SkyProfile.Body b = findIn(p.moons(), id);
        if (b == null) {
            b = findIn(p.bodies(), id);
        }
        return b != null ? b : findIn(p.sun(), id);
    }

    private static SkyProfile.Body findIn(List<SkyProfile.Body> list, String id) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id().equals(id)) {
                return list.get(i);
            }
        }
        return null;
    }

    /** Adds planets or moons (disc, limb glow, night shade, lights); returns their earthshine sum. */
    private float addSolid(List<SkyProfile.Body> list, double days, boolean haveSun, float roll, float alpha) {
        float earthshine = 0.0F;
        for (int i = 0; i < list.size(); i++) {
            SkyProfile.Body b = list.get(i);
            bodyAnglesInto(b, days, this.yp);
            float yaw = this.yp[0];
            float pitch = this.yp[1];
            float lit = 1.0F;
            if (b.phases() && haveSun && b.nightShade().isEmpty()) {
                // Full when opposite the sun, a thin crescent next to it.
                directionInto(yaw, pitch, this.dir);
                double cos = dot(this.dir, this.sunDir);
                lit = (float) (0.3 + 0.7 * (1.0 - cos) / 2.0);
            }
            boolean pinned = b.orbitPeriodDays() == 0.0F || b.fixedAngle().isPresent();
            float bodyRoll = pinned ? roll : 0.0F;
            int rgb = scaleRgb(SkyProfile.rgb(b.color()), lit);
            body(b.id(), b.texture(), yaw, pitch, b.size(), rgb, alpha, b.glow(), false, bodyRoll);
            if (b.limbGlow().isPresent()) {
                SkyProfile.Glow g = b.limbGlow().get();
                this.quads.add(quad(g.texture(), yaw, pitch, b.size() * g.scale(), 1.0F, 0.0F, 0.0F,
                        SkyProfile.rgb(g.color()), clamp01(g.strength() * alpha), true));
            }
            if (b.texture().isPresent()) {
                this.quads.add(quad(b.texture().get(), yaw, pitch, b.size(), 1.0F, bodyRoll, 0.0F, rgb, alpha, false));
            }
            if (b.nightShade().isPresent() && haveSun) {
                SkyProfile.NightShade ns = b.nightShade().get();
                shadeInto(yaw, pitch, this.sunDir, this.shadeOut, this.dir, this.ex, this.ey);
                float k = this.shadeOut[1];
                this.quads.add(quad(shadeTexture(ns.texture(), shadeFrame(k)), yaw, pitch, b.size(), 1.0F,
                        this.shadeOut[0], 0.0F, 0x000000, clamp01(ns.strength() * alpha), false));
                if (ns.lightsTexture().isPresent()) {
                    // City lights fade out as the disc fills with daylight (they are additive over the whole disc).
                    this.quads.add(quad(ns.lightsTexture().get(), yaw, pitch, b.size(), 1.0F, bodyRoll, 0.0F,
                            SkyProfile.rgb(ns.lightsColor()), clamp01(ns.lightsStrength() * (1.0F - k) * alpha), true));
                }
            }
            if (b.earthshine() > 0.0F && pitch > 0.0F) {
                earthshine += b.earthshine() * clamp01((float) (3.0 * Math.sin(Math.toRadians(pitch))));
            }
        }
        return earthshine;
    }

    /**
     * The night shade of a body at {@code yaw / pitch} lit from {@code sunDir}: {shade roll in degrees (local +x
     * toward the sun), lit fraction {@code k = (1 − cos)/2}}. The renderer draws phase-sheet frame
     * {@link #shadeFrame shadeFrame(k)} centred on the body, turned by the roll.
     */
    public static float[] shade(float yaw, float pitch, float[] sunDir) {
        float[] out = new float[2];
        shadeInto(yaw, pitch, sunDir, out, new float[3], new float[3], new float[3]);
        return out;
    }

    static void shadeInto(float yaw, float pitch, float[] sunDir, float[] out, float[] dir, float[] ex, float[] ey) {
        directionInto(yaw, pitch, dir);
        double cos = dot(dir, sunDir);
        double k = (1.0 - cos) / 2.0;
        axesInto(yaw, pitch, ex, ey);
        double sx = dot(sunDir, ex);
        double sy = dot(sunDir, ey);
        out[0] = (float) Math.toDegrees(Math.atan2(sy, sx));
        out[1] = (float) clamp01(k);
    }

    /** The phase-sheet frame for lit fraction {@code k}: {@code round(k · 15)}, 0 beside the sun, 15 opposite it. */
    public static int shadeFrame(double k) {
        return (int) Math.round(clamp01(k) * (SHADE_FRAMES - 1));
    }

    /** Frame {@code frame} of the phase sheet whose base texture is {@code base} ({@code <base>_NN.png}). */
    public static Identifier shadeTexture(Identifier base, int frame) {
        Identifier[] sheet = DEFAULT_SHADE.equals(base) ? DEFAULT_SHADE_SHEET : SHADE_SHEETS.get(base);
        if (sheet == null) {
            sheet = shadeSheet(base);
            SHADE_SHEETS.put(base, sheet);
        }
        return sheet[Math.max(0, Math.min(SHADE_FRAMES - 1, frame))];
    }

    private static Identifier[] shadeSheet(Identifier base) {
        String path = base.getPath();
        String stem = path.endsWith(".png") ? path.substring(0, path.length() - 4) : path;
        Identifier[] sheet = new Identifier[SHADE_FRAMES];
        for (int i = 0; i < SHADE_FRAMES; i++) {
            sheet[i] = Identifier.fromNamespaceAndPath(base.getNamespace(), stem + (i < 10 ? "_0" : "_") + i + ".png");
        }
        return sheet;
    }

    /**
     * The local x and y axes (world directions) of a body quad at {@code yaw / pitch}, the frame the renderer builds
     * with {@code rotateY(-yaw).rotateX(-pitch)} (the quad sits on local +z).
     */
    public static float[][] axes(float yaw, float pitch) {
        float[] ex = new float[3];
        float[] ey = new float[3];
        axesInto(yaw, pitch, ex, ey);
        return new float[][] {ex, ey};
    }

    static void axesInto(float yaw, float pitch, float[] ex, float[] ey) {
        double y = Math.toRadians(yaw);
        double p = Math.toRadians(pitch);
        ex[0] = (float) Math.cos(y);
        ex[1] = 0.0F;
        ex[2] = (float) Math.sin(y);
        ey[0] = (float) (Math.sin(p) * Math.sin(y));
        ey[1] = (float) Math.cos(p);
        ey[2] = (float) (-Math.sin(p) * Math.cos(y));
    }

    private static double dot(float[] a, float[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    /** Writes the decal's {yaw, pitch} into {@code out}; false when its anchor is missing this frame. */
    private boolean anchorInto(SkyProfile.Decal dc, boolean haveSun, float[] out) {
        switch (dc.anchor()) {
            case "sun":
                if (!haveSun) {
                    return false;
                }
                out[0] = this.sunAngles[0] + dc.offsetYaw();
                out[1] = this.sunAngles[1] + dc.offsetPitch();
                return true;
            case "antisun":
                if (!haveSun) {
                    return false;
                }
                out[0] = (this.sunAngles[0] + 180.0F) % 360.0F + dc.offsetYaw();
                out[1] = -this.sunAngles[1] + dc.offsetPitch();
                return true;
            case "body":
                for (int i = 0; i < this.bodies.size(); i++) {
                    BodyDraw b = this.bodies.get(i);
                    if (b.id().equals(dc.bodyId())) {
                        out[0] = b.yaw() + dc.offsetYaw();
                        out[1] = b.pitch() + dc.offsetPitch();
                        return true;
                    }
                }
                return false;
            default:
                out[0] = dc.yaw();
                out[1] = dc.pitch();
                return true;
        }
    }

    // ------------------------------------------------------------------------------------------------ visibility

    /** Night visibility from daylight: full below 0.12, gone at 0.37 (the 2.0.0 aurora rule). */
    public static float nightWeight(double d) {
        if (d >= 0.37) {
            return 0.0F;
        }
        return (float) clamp01(1.0 - Math.max(0.0, d - 0.12) / 0.25);
    }

    /** Day visibility from daylight: 0 below 0.05, full from 0.30. */
    public static float dayWeight(double d) {
        return (float) clamp01((d - 0.05) / 0.25);
    }

    /**
     * A weather gate's weight: the smoothed intensity while its event is active (when {@code active}) plus the
     * recovery after-image (when {@code recovery}), clamped; an absent gate is 1.
     */
    public static float weatherGate(Optional<SkyProfile.WeatherGate> gate, String weather, float intensity,
            String recoveryEvent, float recovery) {
        if (gate.isEmpty()) {
            return 1.0F;
        }
        SkyProfile.WeatherGate g = gate.get();
        float w = 0.0F;
        if (g.active() && g.event().equals(weather)) {
            w += intensity;
        }
        if (g.recovery() && g.event().equals(recoveryEvent)) {
            w += recovery;
        }
        return clamp01(w);
    }

    /** The product of every factor a decal's visibility block names; {@code anchorPitch} NaN skips the pitch rule. */
    static float visibility(Optional<SkyProfile.Visibility> visibility, double d, String weather, float intensity,
            String recoveryEvent, float recovery, float[] sides, float anchorPitch, float totality) {
        if (visibility.isEmpty()) {
            return 1.0F;
        }
        SkyProfile.Visibility v = visibility.get();
        float w = 1.0F;
        if (v.night()) {
            w *= nightWeight(d);
        }
        if (v.day()) {
            w *= dayWeight(d);
        }
        if (v.weather().isPresent()) {
            w *= weatherGate(v.weather(), weather, intensity, recoveryEvent, recovery);
        }
        if (v.weatherBoost().isPresent() && v.weatherBoost().get().event().equals(weather)) {
            w *= 1.0F + v.weatherBoost().get().strength() * intensity;
        }
        if (!v.positionSides().isEmpty()) {
            w *= sum(sides, v.positionSides());
        }
        if (v.pitchBelow() > 0.0F && !Float.isNaN(anchorPitch)) {
            w *= anchorPitch > -v.pitchBelow() ? clamp01(1.0F - anchorPitch / v.pitchBelow()) : 0.0F;
        }
        if (v.eclipse()) {
            w *= totality;
        }
        return w;
    }

    /**
     * {west, band, east} weights for the player's position, with the same 128-block edge fades as the position
     * colours; a profile without a position blend is all band.
     */
    public static float[] sideWeights(boolean blend, double position, int bandWidth) {
        float[] out = new float[3];
        sideWeightsInto(blend, position, bandWidth, out);
        return out;
    }

    static void sideWeightsInto(boolean blend, double position, int bandWidth, float[] out) {
        if (!blend) {
            out[0] = 0.0F;
            out[1] = 1.0F;
            out[2] = 0.0F;
            return;
        }
        double half = Math.max(1, bandWidth) / 2.0;
        double fade = 128.0;
        if (position < 0.0) {
            float t = (float) clamp01((position + half + fade / 2.0) / fade);
            out[0] = 1.0F - t;
            out[1] = t;
            out[2] = 0.0F;
            return;
        }
        float t = (float) clamp01((position - half + fade / 2.0) / fade);
        out[0] = 0.0F;
        out[1] = 1.0F - t;
        out[2] = t;
    }

    private static float sum(float[] sides, List<String> names) {
        float s = 0.0F;
        for (int i = 0; i < names.size(); i++) {
            switch (names.get(i)) {
                case "west" -> s += sides[0];
                case "band" -> s += sides[1];
                case "east" -> s += sides[2];
                default -> {
                }
            }
        }
        return s;
    }

    // ------------------------------------------------------------------------------------------------ shooting stars

    /** 64-bit finaliser shared with the art previewer. */
    static long mix64(long x) {
        x ^= x >>> 31;
        x *= 0x94D049BB133111EBL;
        x ^= x >>> 29;
        return x;
    }

    /**
     * The deterministic schedule: slot {@code i} fires once per {@code period = 1200 / rate × maxConcurrent} ticks at a
     * hashed offset; a visible streak starts at a hashed yaw / pitch, moves along a hashed heading by
     * {@code 2.2 × lengthDeg × progress} and fades in and out with {@code sin(π · progress)}. Adds fresh quads to
     * {@code out} (tests); the scene itself uses pooled ones.
     */
    static void shootingStars(SkyProfile.ShootingStars s, double t, long seed, double d, List<Quad> out) {
        SkyScene tmp = new SkyScene();
        tmp.shootingStarsInto(s, t, seed, d);
        for (int i = 0; i < tmp.shootingStars.size(); i++) {
            out.add(tmp.shootingStars.get(i).copy());
        }
    }

    private void shootingStarsInto(SkyProfile.ShootingStars s, double t, long seed, double d) {
        float nw = s.nightOnly() ? nightWeight(d) : 1.0F;
        int maxc = Math.max(0, Math.min(8, s.maxConcurrent()));
        if (nw <= 0.0F || s.ratePerMinute() <= 0.0F || maxc == 0) {
            return;
        }
        double dur = Math.max(1, s.durationTicks());
        double period = 1200.0 / s.ratePerMinute() * maxc;
        int rgb = SkyProfile.rgb(s.color());
        for (int i = 0; i < maxc; i++) {
            double shifted = t + i * period / maxc;
            long cycle = (long) Math.floor(shifted / period);
            long h = mix64(cycle * 0x9E3779B97F4A7C15L + i * 0xBF58476D1CE4E5B9L + seed);
            double f0 = ((h) & 0x7FF) / 2047.0;
            double f1 = ((h >>> 11) & 0x7FF) / 2047.0;
            double f2 = ((h >>> 22) & 0x7FF) / 2047.0;
            double f3 = ((h >>> 33) & 0x7FF) / 2047.0;
            double start = cycle * period - i * period / maxc + f0 * (period - dur);
            double progress = (t - start) / dur;
            if (progress < 0.0 || progress > 1.0) {
                continue;
            }
            float yaw0 = (float) (f1 * 360.0);
            float pitch0 = (float) (s.minPitch() + f2 * (70.0 - s.minPitch()));
            float heading = (float) (f3 * 360.0);
            double travel = Math.toRadians(s.lengthDeg() * 2.2 * progress);
            localInto(yaw0, pitch0, heading, Math.tan(travel), 0.0, 1.0, this.local);
            anglesOfInto(this.local[0], this.local[1], this.local[2], this.yp);
            float alpha = (float) (Math.sin(Math.PI * progress) * nw);
            this.shootingStars.add(quad(s.texture(), this.yp[0], this.yp[1], s.lengthDeg() / 8.0F, 8.0F, heading, 0.0F,
                    rgb, clamp01(alpha), true));
        }
    }

    /** {@code Ry(−yaw) Rx(−pitch) Rz(roll)} applied to a local vector, normalised, into {@code out}. */
    static void localInto(float yaw, float pitch, float roll, double x, double y, double z, float[] out) {
        double r = Math.toRadians(roll);
        double x1 = x * Math.cos(r) - y * Math.sin(r);
        double y1 = x * Math.sin(r) + y * Math.cos(r);
        double p = Math.toRadians(-pitch);
        double y2 = y1 * Math.cos(p) - z * Math.sin(p);
        double z2 = y1 * Math.sin(p) + z * Math.cos(p);
        double a = Math.toRadians(-yaw);
        double x3 = x1 * Math.cos(a) + z2 * Math.sin(a);
        double z3 = -x1 * Math.sin(a) + z2 * Math.cos(a);
        double len = Math.sqrt(x3 * x3 + y2 * y2 + z3 * z3);
        out[0] = (float) (x3 / len);
        out[1] = (float) (y2 / len);
        out[2] = (float) (z3 / len);
    }

    // ------------------------------------------------------------------------------------------------ angles

    /**
     * {yaw, pitch} in degrees for a body at the continuous day count {@code days} (see the class note). Within one day
     * a one-day body only needs the phase, but any other period must be given the unwrapped count.
     */
    public static float[] bodyAngles(SkyProfile.Body b, double days) {
        float[] out = new float[2];
        bodyAnglesInto(b, days, out);
        return out;
    }

    static void bodyAnglesInto(SkyProfile.Body b, double days, float[] out) {
        if (b.orbitPeriodDays() == 0.0F || b.fixedAngle().isPresent()) {
            if (b.fixedAngle().isPresent()) {
                out[0] = b.fixedAngle().get().yaw();
                out[1] = b.fixedAngle().get().pitch();
            } else {
                out[0] = 0.0F;
                out[1] = 90.0F;
            }
            return;
        }
        double bodyPhase = frac(days / b.orbitPeriodDays() + b.phaseOffset());
        double theta = 2.0 * Math.PI * bodyPhase;
        double sin = Math.sin(theta);
        double incl = b.inclination();
        // Contract elevation at the horizon crossings (inclination), capped at 90 - |inclination| at culmination so
        // an inclined body passes beside the zenith instead of parking on it.
        double elevation = sin >= 0.0 ? sin * (90.0 - Math.abs(incl)) + incl * (1.0 - sin) : sin * 90.0 + incl;
        double el = Math.toRadians(Math.max(-90.0, Math.min(90.0, elevation)));
        // East-west component: +1 = east at bodyPhase 0, -1 = west at 0.5; fast moons run the other way.
        double ew = Math.cos(theta);
        if (b.orbitPeriodDays() < 0.5F) {
            ew = -ew;
        }
        // Inclined paths lean south (positive) or north (negative) while above the horizon.
        double ns = Math.signum(incl) * Math.max(0.0, sin);
        double len = Math.sqrt(ew * ew + ns * ns);
        double horiz = Math.cos(el);
        double x = len < 1e-9 ? 0.0 : ew / len * horiz;
        double z = len < 1e-9 ? 0.0 : ns / len * horiz;
        double y = Math.sin(el);
        anglesOfInto(x, y, z, out);
    }

    /** Unit direction (x east, y up, z south) for a yaw / pitch in degrees. */
    public static float[] direction(float yaw, float pitch) {
        float[] out = new float[3];
        directionInto(yaw, pitch, out);
        return out;
    }

    static void directionInto(float yaw, float pitch, float[] out) {
        double ry = Math.toRadians(yaw);
        double rp = Math.toRadians(pitch);
        double h = Math.cos(rp);
        out[0] = (float) (-Math.sin(ry) * h);
        out[1] = (float) Math.sin(rp);
        out[2] = (float) (Math.cos(ry) * h);
    }

    private static void anglesOfInto(double x, double y, double z, float[] out) {
        double h = Math.sqrt(x * x + z * z);
        float pitch = (float) Math.toDegrees(Math.atan2(y, h));
        float yaw = h < 1e-9 ? 0.0F : (float) Math.toDegrees(Math.atan2(-x, z));
        if (yaw < 0.0F) {
            yaw += 360.0F;
        }
        out[0] = yaw;
        out[1] = pitch;
    }

    // ------------------------------------------------------------------------------------------------ colours

    /** Eclipse light factor 1 (none) .. minLight (totality, the middle of the window). */
    static float eclipseLight(SkyProfile p, double phase) {
        if (p.eclipse().isEmpty()) {
            return 1.0F;
        }
        SkyProfile.Eclipse e = p.eclipse().get();
        if (e.phaseEnd() <= e.phaseStart() || phase < e.phaseStart() || phase > e.phaseEnd()) {
            return 1.0F;
        }
        double mid = (e.phaseStart() + e.phaseEnd()) / 2.0;
        double half = (e.phaseEnd() - e.phaseStart()) / 2.0;
        double t = 1.0 - Math.abs(phase - mid) / half;
        double k = t * t * (3.0 - 2.0 * t);
        return (float) (1.0 - (1.0 - e.minLight()) * k);
    }

    /** {zenith, horizon, fog} from the gradient keyframes, or from the background / fog day-night pair. */
    private void baseColorsInto(SkyProfile p, double phase, double daylight, int[] out) {
        List<SkyProfile.GradientKey> g = p.gradient();
        if (!g.isEmpty()) {
            gradientAtInto(sortedKeys(g), phase, out);
            return;
        }
        SkyProfile.Background bg = p.background();
        SkyProfile.Fog fog = p.fog();
        if ("constant".equals(bg.blend())) {
            out[0] = SkyProfile.rgb(bg.zenith());
            out[1] = SkyProfile.rgb(bg.horizon());
            out[2] = SkyProfile.rgb(fog.color());
            return;
        }
        float d = (float) daylight;
        out[0] = lerpRgb(SkyProfile.rgb(bg.nightZenith()), SkyProfile.rgb(bg.zenith()), d);
        out[1] = lerpRgb(SkyProfile.rgb(bg.nightHorizon()), SkyProfile.rgb(bg.horizon()), d);
        out[2] = lerpRgb(SkyProfile.rgb(fog.nightColor().isPresent() ? fog.nightColor().get() : fog.color()),
                SkyProfile.rgb(fog.color()), d);
    }

    // The keys in phase order: the list itself when sorted (the shipped profiles), else a copy made once per list.
    private List<SkyProfile.GradientKey> sortedKeys(List<SkyProfile.GradientKey> keys) {
        if (sorted(keys)) {
            return keys;
        }
        if (this.unsortedKeys != keys) {
            this.unsortedKeys = keys;
            this.sortedKeys = sortedCopy(keys);
        }
        return this.sortedKeys;
    }

    /** Piecewise-linear keyframe lookup that wraps from the last key back to the first. */
    static int[] gradientAt(List<SkyProfile.GradientKey> keys, double phase) {
        int[] out = new int[3];
        gradientAtInto(sorted(keys) ? keys : sortedCopy(keys), phase, out);
        return out;
    }

    private static void gradientAtInto(List<SkyProfile.GradientKey> k, double phase, int[] out) {
        if (k.size() == 1) {
            lerpKeys(k.get(0), k.get(0), 0.0F, out);
            return;
        }
        SkyProfile.GradientKey prev = k.get(k.size() - 1);
        double prevPhase = prev.phase() - 1.0;
        for (int i = 0; i < k.size(); i++) {
            SkyProfile.GradientKey next = k.get(i);
            if (phase <= next.phase()) {
                double span = next.phase() - prevPhase;
                float t = span <= 1e-9 ? 0.0F : (float) ((phase - prevPhase) / span);
                lerpKeys(prev, next, t, out);
                return;
            }
            prev = next;
            prevPhase = next.phase();
        }
        SkyProfile.GradientKey first = k.get(0);
        double span = first.phase() + 1.0 - prevPhase;
        float t = span <= 1e-9 ? 0.0F : (float) ((phase - prevPhase) / span);
        lerpKeys(prev, first, t, out);
    }

    private static void lerpKeys(SkyProfile.GradientKey a, SkyProfile.GradientKey b, float t, int[] out) {
        out[0] = lerpRgb(SkyProfile.rgb(a.zenith()), SkyProfile.rgb(b.zenith()), t);
        out[1] = lerpRgb(SkyProfile.rgb(a.horizon()), SkyProfile.rgb(b.horizon()), t);
        out[2] = lerpRgb(SkyProfile.rgb(a.fog()), SkyProfile.rgb(b.fog()), t);
    }

    /** Whether the keys are already in phase order (the shipped profiles are), so a frame copies nothing. */
    private static boolean sorted(List<SkyProfile.GradientKey> keys) {
        for (int i = 1; i < keys.size(); i++) {
            if (keys.get(i - 1).phase() > keys.get(i).phase()) {
                return false;
            }
        }
        return true;
    }

    private static List<SkyProfile.GradientKey> sortedCopy(List<SkyProfile.GradientKey> keys) {
        List<SkyProfile.GradientKey> k = new ArrayList<>(keys);
        k.sort((a, b) -> Float.compare(a.phase(), b.phase()));
        return k;
    }

    /** Colours by position: west below -half, band inside, east above +half; each edge fades over 128 blocks. */
    static void positionColorsInto(SkyProfile.PositionBlend b, double position, int bandWidth, int[] out) {
        double half = Math.max(1, bandWidth) / 2.0;
        double fade = 128.0;
        if (position < 0.0) {
            float t = (float) clamp01((position + half + fade / 2.0) / fade);
            lerpColors(b.west(), b.band(), t, out);
            return;
        }
        float t = (float) clamp01((position - half + fade / 2.0) / fade);
        lerpColors(b.band(), b.east(), t, out);
    }

    private static void lerpColors(SkyProfile.SkyColors a, SkyProfile.SkyColors b, float t, int[] out) {
        out[0] = lerpRgb(SkyProfile.rgb(a.zenith()), SkyProfile.rgb(b.zenith()), t);
        out[1] = lerpRgb(SkyProfile.rgb(a.horizon()), SkyProfile.rgb(b.horizon()), t);
        out[2] = lerpRgb(SkyProfile.rgb(a.fog()), SkyProfile.rgb(b.fog()), t);
    }

    private static int cycle(List<String> colors, double t) {
        if (colors.isEmpty()) {
            return 0x60E0A0;
        }
        double f = frac(t) * colors.size();
        int i = (int) Math.floor(f);
        return lerpRgb(SkyProfile.rgb(colors.get(i % colors.size())), SkyProfile.rgb(colors.get((i + 1) % colors.size())),
                (float) (f - i));
    }

    public static int lerpRgb(int a, int b, float t) {
        float k = Math.max(0.0F, Math.min(1.0F, t));
        int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * k);
        int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * k);
        int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * k);
        return (r << 16) | (g << 8) | bl;
    }

    static int scaleRgb(int c, float k) {
        int r = Math.min(255, Math.round(((c >> 16) & 0xFF) * k));
        int g = Math.min(255, Math.round(((c >> 8) & 0xFF) * k));
        int b = Math.min(255, Math.round((c & 0xFF) * k));
        return (r << 16) | (g << 8) | b;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * clamp01(t);
    }

    static double frac(double v) {
        return v - Math.floor(v);
    }

    static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    static float clamp01(float v) {
        return Math.max(0.0F, Math.min(1.0F, v));
    }
}
