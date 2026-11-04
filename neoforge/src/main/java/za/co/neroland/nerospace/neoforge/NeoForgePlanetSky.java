package za.co.neroland.nerospace.neoforge;

// NeoForge 26.3 planet sky: the CustomSkyboxRenderer registered as nerospace:planet. Every dimension type selects it
// through the neoforge:custom_skybox attribute in neoforge/src/main/resources-26.3 (tools/gen_sky_overlays.py). It
// turns PlanetSkyRenderer.scene(...) into draw calls with the vanilla sky pipelines and returns false (vanilla sky)
// for a level without a planet profile. Only this cell has a sky hook; the others show the dimension colours.
// Layers, in order: zenith disc, horizon ramp, stars (two twinkle halves), galaxy, aurora curtains (persistent
// buffers rewritten from the fold model when the aurora clock moves), fixed decals, bodies with their glows, night
// shades, city lights, eclipse corona and anchored decals, cloud deck + underglow, shooting stars, haze.
// Pooled per-draw matrices and tints, primitive-keyed mesh maps, the scene is the
// renderer's reused instance. GPU meshes are released on a sky-profile reload and when the sky disables itself.
//? if >=26.3 {
/*import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Optional;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import it.unimi.dsi.fastutil.floats.Float2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import za.co.neroland.nerospace.NerospaceCommon;
import za.co.neroland.nerospace.client.sky.PlanetSkyRenderer;
import za.co.neroland.nerospace.client.sky.SkyProfile;
import za.co.neroland.nerospace.client.sky.SkyProfiles;
import za.co.neroland.nerospace.client.sky.SkyScene;
import za.co.neroland.nerospace.client.sky.SkyTransforms;

public final class NeoForgePlanetSky implements net.neoforged.neoforge.client.CustomSkyboxRenderer {

    private static final float R = 100.0F;
    private static final Identifier RAMP = tex("horizon_ramp");
    // Aurora: up to AURORA_MAX_LAYERS layers plus the weather fringe, each a persistent vertex buffer.
    private static final int AURORA_SLOTS = SkyScene.AURORA_MAX_LAYERS + 1;
    private static final int AURORA_MAX_QUADS = SkyScene.AURORA_SEGMENTS * 2 * 2;
    private static final int POS_TEX_BYTES = 20;

    private GpuBuffer disc;
    private final Float2ObjectOpenHashMap<GpuBuffer> ramps = new Float2ObjectOpenHashMap<>();
    private static final int RAMP_QUADS = 32 * 2 * 2;
    private GpuBuffer quadTex;
    private GpuBuffer quadTexColor;
    private GpuBuffer galaxy;
    private int galaxyQuads;
    // Star field: two buffers (twinkle halves) per (count, size scale).
    private final Long2ObjectOpenHashMap<GpuBuffer[]> stars = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<int[]> starQuads = new Long2ObjectOpenHashMap<>();
    // Cloud deck: one translucent band mesh plus two additive underglow rings, per (altitude, tiles).
    private final Long2ObjectOpenHashMap<GpuBuffer[]> clouds = new Long2ObjectOpenHashMap<>();
    private final GpuBuffer[] auroraBuffers = new GpuBuffer[AURORA_SLOTS];
    private final ByteBuffer[] auroraBytes = new ByteBuffer[AURORA_SLOTS];
    private final int[] auroraQuads = new int[AURORA_SLOTS];
    private final double[] curtain = new double[4];
    private final Matrix4f scratch = new Matrix4f();
    private final Matrix4f view = new Matrix4f();
    private final SkyTransforms transforms = new SkyTransforms();
    private ProjectionMatrixBuffer skyProjection;
    // What the aurora buffers hold, so an unchanged scene (paused, frozen gallery sky) skips the rewrite.
    private boolean auroraValid;
    private SkyProfile.Aurora auroraShape;
    private double auroraSecondsWritten = Double.NaN;
    private float auroraYawWritten = Float.NaN;
    private int auroraSegmentsWritten;
    private final int[] auroraLayerWritten = new int[AURORA_SLOTS];
    private int auroraSlotsWritten;
    private int profileGeneration = Integer.MIN_VALUE;
    private boolean failed;

    private static Identifier tex(String name) {
        return Identifier.fromNamespaceAndPath(NerospaceCommon.MOD_ID, "textures/sky/" + name + ".png");
    }

    @Override
    public boolean renderSky(LevelRenderState levelRenderState, SkyRenderState skyRenderState, Matrix4fc modelViewMatrix,
            GpuBufferSlice skyFog) {
        if (this.failed) {
            return false;
        }
        // Meshes keyed on profile values are stale after a resource reload: drop them, build() recreates lazily.
        int generation = SkyProfiles.generation();
        if (generation != this.profileGeneration) {
            release();
            this.profileGeneration = generation;
        }
        Minecraft mc = Minecraft.getInstance();
        PlanetSkyRenderer.Frame frame = PlanetSkyRenderer.frame(mc.level, levelRenderState.worldPartialTicks);
        if (frame == null) {
            return false;
        }
        Vec3 cam = levelRenderState.cameraRenderState.pos;
        double time = levelRenderState.gameTime + (double) levelRenderState.worldPartialTicks;
        SkyScene scene = PlanetSkyRenderer.scene(frame, PlanetSkyRenderer.blendPosition(frame, cam.x, cam.z), time);
        try {
            build();
            this.transforms.beginFrame();
            RenderSystem.backupProjectionMatrix();
            try {
                RenderSystem.setProjectionMatrix(this.skyProjection.getBuffer(levelRenderState.cameraRenderState.projectionMatrix),
                        ProjectionType.PERSPECTIVE);
                draw(mc, scene, this.view.set(modelViewMatrix), skyFog, !"station".equals(frame.planet().langId()));
            } finally {
                RenderSystem.restoreProjectionMatrix();
            }
            return true;
        } catch (RuntimeException ex) {
            // Never crash a frame over the sky: fall back to the vanilla sky for the rest of the session.
            this.failed = true;
            NerospaceCommon.LOGGER.warn("[Nerospace] planet sky disabled after a render error", ex);
            try {
                release();
            } catch (RuntimeException cleanupError) {
                NerospaceCommon.LOGGER.warn("[Nerospace] could not release disabled sky resources", cleanupError);
            }
            return false;
        }
    }

    // Closes every GPU buffer and forgets the meshes; build() and the lazy getters recreate what a later frame needs.
    private void release() {
        if (this.skyProjection != null) {
            this.skyProjection.close();
            this.skyProjection = null;
        }
        close(this.disc);
        this.disc = null;
        close(this.quadTex);
        this.quadTex = null;
        close(this.quadTexColor);
        this.quadTexColor = null;
        close(this.galaxy);
        this.galaxy = null;
        for (GpuBuffer b : this.ramps.values()) {
            close(b);
        }
        this.ramps.clear();
        for (GpuBuffer[] pair : this.stars.values()) {
            for (GpuBuffer b : pair) {
                close(b);
            }
        }
        this.stars.clear();
        this.starQuads.clear();
        for (GpuBuffer[] mesh : this.clouds.values()) {
            for (GpuBuffer b : mesh) {
                close(b);
            }
        }
        this.clouds.clear();
        for (int i = 0; i < AURORA_SLOTS; i++) {
            close(this.auroraBuffers[i]);
            this.auroraBuffers[i] = null;
        }
        this.auroraValid = false;
    }

    private static void close(GpuBuffer buffer) {
        if (buffer != null) {
            buffer.close();
        }
    }

    private void draw(Minecraft mc, SkyScene s, Matrix4f view, GpuBufferSlice skyFog, boolean terrainHorizon) {
        build();
        // Buffer writes are not allowed inside a render pass: rewrite the aurora curtains first.
        int auroraSlots = uploadAurora(s);
        prepareTextures(mc, s);
        RenderSystem.setShaderFog(skyFog);
        RenderTarget target = mc.gameRenderer.mainRenderTarget();
        GpuSampler repeat = RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR);
        GpuSampler clamp = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Nerospace sky",
                target.getColorTextureView(), Optional.empty())) {
            RenderSystem.bindDefaultUniforms(pass);
            // Zenith colour disc (the vanilla sky shader fades it into the dimension's fog colour at the horizon).
            pass.setPipeline(RenderSystem.getCompiledPipeline(RenderPipelines.SKY));
            pass.setUniform("DynamicTransforms", transform(view, s.zenith, 1.0F));
            pass.setVertexBuffer(0, this.disc.slice());
            pass.draw(10, 1, 0, 0);
            // Zenith-to-horizon gradient.
            drawTextured(pass, RenderPipelines.END_SKY, ramp(s.rampTopDeg), RAMP_QUADS, s.rampTexture, clamp, view,
                    s.horizon, 1.0F, mc);
            if (s.starAlpha > 0.01F) {
                GpuBuffer[] buf = starBuffers(s.starCount, s.starSizeScale);
                int[] q = this.starQuads.get(starKey(s.starCount, s.starSizeScale));
                if (s.starAlphaA > 0.005F) {
                    drawTextured(pass, RenderPipelines.CELESTIAL, buf[0], q[0], s.starTexture, null, view, s.starTint,
                            s.starAlphaA, mc);
                }
                if (s.starAlphaB > 0.005F) {
                    drawTextured(pass, RenderPipelines.CELESTIAL, buf[1], q[1], s.starTexture, null, view, s.starTint,
                            s.starAlphaB, mc);
                }
            }
            // Galaxy band (translucent, faded towards its far side).
            if (s.galaxyAlpha > 0.01F && s.galaxyTexture.isPresent()) {
                this.scratch.set(view).rotateY((float) Math.toRadians(-s.galaxyYaw))
                        .rotateX((float) Math.toRadians(-s.galaxyPitch));
                drawTextured(pass, RenderPipelines.END_SKY, this.galaxy, this.galaxyQuads, s.galaxyTexture.get(), null,
                        this.scratch, 0xFFFFFF, s.galaxyAlpha, mc);
            }
            // Aurora curtains (vertices already in world directions).
            for (int i = 0; i < auroraSlots; i++) {
                SkyScene.AuroraLayer layer = s.auroraLayers.get(i);
                drawTextured(pass, RenderPipelines.CELESTIAL, this.auroraBuffers[i], this.auroraQuads[i], layer.texture(),
                        repeat, view, layer.tint(), layer.alpha(), mc);
            }
            drawQuads(pass, s.fixedQuads, view, mc);
            drawQuads(pass, s.quads, view, mc);
            if (s.cloud.isPresent()) {
                drawCloud(pass, s.cloud.get(), view, repeat, mc);
            }
            drawQuads(pass, s.shootingStars, view, mc);
            if (s.hazeAlpha > 0.01F) {
                this.scratch.set(view).scale(1.0F, 0.45F, 1.0F);
                drawTextured(pass, RenderPipelines.END_SKY, ramp(55.0F), RAMP_QUADS, RAMP, clamp, this.scratch,
                        s.hazeColor, s.hazeAlpha, mc);
            }
            // Match the ground fog at the horizon.
            // The station keeps its homeworld visible below the horizon.
            if (terrainHorizon) {
                drawTextured(pass, RenderPipelines.END_SKY, ramp(12.0F), RAMP_QUADS, s.rampTexture, clamp, view,
                        s.fog, 1.0F, mc);
            }
        }
    }

    private static void prepareTextures(Minecraft mc, SkyScene s) {
        prepareTexture(mc, s.rampTexture);
        prepareTexture(mc, s.starTexture);
        s.galaxyTexture.ifPresent(texture -> prepareTexture(mc, texture));
        for (SkyScene.AuroraLayer layer : s.auroraLayers) {
            prepareTexture(mc, layer.texture());
        }
        prepareQuads(mc, s.fixedQuads);
        prepareQuads(mc, s.quads);
        prepareQuads(mc, s.shootingStars);
        s.cloud.ifPresent(cloud -> prepareTexture(mc, cloud.texture()));
        if (s.hazeAlpha > 0.01F) {
            prepareTexture(mc, RAMP);
        }
    }

    private static void prepareQuads(Minecraft mc, List<SkyScene.Quad> quads) {
        for (SkyScene.Quad quad : quads) {
            prepareTexture(mc, quad.texture());
        }
    }

    private static void prepareTexture(Minecraft mc, Identifier texture) {
        mc.getTextureManager().getTexture(texture).getTextureView();
    }

    // Indexed (no iterator per list per frame).
    private void drawQuads(RenderPass pass, List<SkyScene.Quad> quads, Matrix4f view, Minecraft mc) {
        for (int i = 0; i < quads.size(); i++) {
            drawQuad(pass, quads.get(i), view, mc);
        }
    }

    // Sizes are clamped to 170 degrees: the station's 110-degree homeworld reaches R * tan(55) = 143 units on the
    // 100-unit dome. That is intended - the sky pass writes no depth, so a quad past the dome radius draws exactly as
    // one on it. Do not "fix" R for it.
    private void drawQuad(RenderPass pass, SkyScene.Quad q, Matrix4f view, Minecraft mc) {
        if (q.alpha() <= 0.003F) {
            return;
        }
        float half = (float) (R * Math.tan(Math.toRadians(Math.min(170.0F, Math.max(0.1F, q.size())) / 2.0)));
        float aspect = Math.max(0.05F, q.aspect());
        this.scratch.set(view).rotateY((float) Math.toRadians(-q.yaw())).rotateX((float) Math.toRadians(-q.pitch()))
                .rotateZ((float) Math.toRadians(q.roll())).scale(half * aspect, half, R)
                .translate(q.offsetX() / aspect, 0.0F, 0.0F);
        if (q.additive()) {
            drawTextured(pass, RenderPipelines.CELESTIAL, this.quadTex, 2, q.texture(), null, this.scratch, q.rgb(),
                    q.alpha(), mc);
        } else {
            drawTextured(pass, RenderPipelines.END_SKY, this.quadTexColor, 2, q.texture(), null, this.scratch, q.rgb(),
                    q.alpha(), mc);
        }
    }

    private void drawCloud(RenderPass pass, SkyScene.Cloud c, Matrix4f view, GpuSampler repeat, Minecraft mc) {
        GpuBuffer[] mesh = cloudMeshes(c.altitudeDeg(), c.tiles());
        this.scratch.set(view).rotateY((float) Math.toRadians(-c.rotationDeg()));
        drawTextured(pass, RenderPipelines.END_SKY, mesh[0], 48 * 3 * 2, c.texture(), repeat, this.scratch, c.rgb(),
                c.alpha(), mc);
        if (c.underglowAlpha() > 0.005F) {
            // The additive pipeline has no vertex colour: two rings (0-8 degrees full, 8-15 degrees at 40 %).
            drawTextured(pass, RenderPipelines.CELESTIAL, mesh[1], 48 * 2, c.texture(), repeat, this.scratch,
                    c.underglowRgb(), c.underglowAlpha(), mc);
            drawTextured(pass, RenderPipelines.CELESTIAL, mesh[2], 48 * 2, c.texture(), repeat, this.scratch,
                    c.underglowRgb(), c.underglowAlpha() * 0.4F, mc);
        }
    }

    private GpuBufferSlice transform(Matrix4f m, int rgb, float alpha) {
        SkyTransforms.Entry entry = this.transforms.next(m, rgb, alpha);
        return RenderSystem.getDynamicUniforms().writeTransform(entry.matrix(), entry.color());
    }

    private void drawTextured(RenderPass pass, RenderPipeline pipeline, GpuBuffer vertices, int quads, Identifier texture,
            GpuSampler sampler, Matrix4f m, int rgb, float alpha, Minecraft mc) {
        if (quads <= 0) {
            return;
        }
        AbstractTexture t = mc.getTextureManager().getTexture(texture);
        RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        int count = quads * 6;
        pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
        RenderSystem.bindDefaultUniforms(pass);
        pass.setUniform("DynamicTransforms", transform(m, rgb, alpha));
        pass.setUniform("Sampler0", t.getTextureView(), sampler != null ? sampler : t.getSampler());
        pass.setVertexBuffer(0, vertices.slice());
        pass.setIndexBuffer(indices.getBuffer(count), indices.type());
        pass.drawIndexed(count, 1, 0, 0, 0);
    }

    // ------------------------------------------------------------------------------------------------ aurora

    // Rewrites one persistent buffer per visible layer from the fold model; no per-frame allocation (the byte
    // buffers and GPU buffers are created once, on the first aurora frame). Skipped when the buffers already hold
    // this aurora clock, yaw, segment count and layer list (a paused game or a frozen sky at a fixed game time).
    private int uploadAurora(SkyScene s) {
        if (s.aurora.isEmpty() || s.auroraLayers.isEmpty()) {
            return 0;
        }
        SkyProfile.Aurora a = s.aurora.get();
        int n = Math.min(AURORA_SLOTS, s.auroraLayers.size());
        int seg = Math.max(2, Math.min(SkyScene.AURORA_SEGMENTS, s.auroraSegments));
        if (auroraUnchanged(s, a, n, seg)) {
            return n;
        }
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        for (int i = 0; i < n; i++) {
            if (this.auroraBuffers[i] == null) {
                int size = AURORA_MAX_QUADS * 4 * POS_TEX_BYTES;
                this.auroraBytes[i] = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder());
                this.auroraBuffers[i] = RenderSystem.getDevice().createBuffer(() -> "Nerospace aurora",
                        GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, this.auroraBytes[i]);
            }
            ByteBuffer b = this.auroraBytes[i];
            b.clear();
            fillCurtain(b, a, s.auroraLayers.get(i).layer(), s.auroraSeconds, s.auroraYaw, seg);
            this.auroraQuads[i] = seg * 2 * 2;
            b.clear();
            encoder.writeToBuffer(this.auroraBuffers[i].slice(), b);
            this.auroraLayerWritten[i] = s.auroraLayers.get(i).layer();
        }
        this.auroraValid = true;
        this.auroraShape = a;
        this.auroraSecondsWritten = s.auroraSeconds;
        this.auroraYawWritten = s.auroraYaw;
        this.auroraSegmentsWritten = seg;
        this.auroraSlotsWritten = n;
        return n;
    }

    private boolean auroraUnchanged(SkyScene s, SkyProfile.Aurora a, int n, int seg) {
        if (!this.auroraValid || this.auroraShape != a || this.auroraSecondsWritten != s.auroraSeconds
                || this.auroraYawWritten != s.auroraYaw || this.auroraSegmentsWritten != seg
                || this.auroraSlotsWritten < n) {
            return false;
        }
        for (int i = 0; i < n; i++) {
            if (this.auroraBuffers[i] == null || this.auroraLayerWritten[i] != s.auroraLayers.get(i).layer()) {
                return false;
            }
        }
        return true;
    }

    private void fillCurtain(ByteBuffer b, SkyProfile.Aurora a, int layer, double t, float yaw0, int seg) {
        float arc = Math.max(1.0F, Math.min(360.0F, a.arcDeg()));
        float uA = column(this.colA, a, layer, 0.0, t, yaw0, arc);
        for (int j = 0; j < seg; j++) {
            float uB = column(this.colB, a, layer, (double) (j + 1) / seg, t, yaw0, arc);
            // Rows: top (v 0) to mid (v 0.45), mid to base (v 1).
            putQuad(b, this.colA, 0, this.colB, 0, this.colB, 3, this.colA, 3, uA, uB, 0.0F, 0.45F);
            putQuad(b, this.colA, 3, this.colB, 3, this.colB, 6, this.colA, 6, uA, uB, 0.45F, 1.0F);
            System.arraycopy(this.colB, 0, this.colA, 0, 9);
            uA = uB;
        }
    }

    private final float[] colA = new float[9];
    private final float[] colB = new float[9];

    // One vertex column of the curtain in world space, {top xyz, mid xyz, base xyz}; returns its texture u.
    private float column(float[] out, SkyProfile.Aurora a, int layer, double un, double t, float yaw0, float arc) {
        SkyScene.curtain(a, layer, un, t, this.curtain);
        double base = this.curtain[0];
        double top = this.curtain[1];
        double shear = this.curtain[2];
        // Taper the ends so the curtain fades in instead of stopping on a hard edge (no vertex alpha on CELESTIAL).
        double edge = Math.min(un, 1.0 - un) / 0.08;
        double k = edge >= 1.0 ? 1.0 : edge * edge * (3.0 - 2.0 * edge);
        top = base + (top - base) * k;
        double mid = top - 0.45 * (top - base);
        double rel = (un - 0.5) * arc;
        dome(out, 0, yaw0 + rel + shear, top);
        dome(out, 3, yaw0 + rel + 0.55 * shear, mid);
        dome(out, 6, yaw0 + rel, base);
        return (float) this.curtain[3];
    }

    private static void dome(float[] out, int at, double yawDeg, double elevationDeg) {
        double y = Math.toRadians(yawDeg);
        double e = Math.toRadians(elevationDeg);
        double h = Math.cos(e) * R;
        out[at] = (float) (-Math.sin(y) * h);
        out[at + 1] = (float) (Math.sin(e) * R);
        out[at + 2] = (float) (Math.cos(y) * h);
    }

    // Both windings, as every other sky quad.
    private static void putQuad(ByteBuffer b, float[] p0, int i0, float[] p1, int i1, float[] p2, int i2, float[] p3,
            int i3, float u0, float u1, float v0, float v1) {
        vertex(b, p0, i0, u0, v0);
        vertex(b, p1, i1, u1, v0);
        vertex(b, p2, i2, u1, v1);
        vertex(b, p3, i3, u0, v1);
        vertex(b, p3, i3, u0, v1);
        vertex(b, p2, i2, u1, v1);
        vertex(b, p1, i1, u1, v0);
        vertex(b, p0, i0, u0, v0);
    }

    private static void vertex(ByteBuffer b, float[] p, int i, float u, float v) {
        b.putFloat(p[i]).putFloat(p[i + 1]).putFloat(p[i + 2]).putFloat(u).putFloat(v);
    }

    // ------------------------------------------------------------------------------------------------ meshes

    private void build() {
        if (this.disc != null) {
            return;
        }
        this.skyProjection = new ProjectionMatrixBuffer("Nerospace stable sky");
        try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(10 * DefaultVertexFormat.POSITION.getVertexSize())) {
            BufferBuilder b = new BufferBuilder(bytes, PrimitiveTopology.TRIANGLE_FAN, DefaultVertexFormat.POSITION);
            b.addVertex(0.0F, 16.0F, 0.0F);
            for (int i = -180; i <= 180; i += 45) {
                b.addVertex(512.0F * (float) Math.cos(Math.toRadians(i)), 16.0F, 512.0F * (float) Math.sin(Math.toRadians(i)));
            }
            this.disc = upload("Nerospace sky disc", b);
        }
        // Unit quads at z = 1 facing the camera, both windings.
        this.quadTex = mesh("Nerospace sky body", DefaultVertexFormat.POSITION_TEX, 2, b -> {
            float[] a = {-1.0F, -1.0F, 1.0F};
            float[] c = {1.0F, -1.0F, 1.0F};
            float[] d = {1.0F, 1.0F, 1.0F};
            float[] e = {-1.0F, 1.0F, 1.0F};
            quadTex(b, a, c, d, e, 0.0F, 1.0F, 1.0F, 0.0F);
        });
        this.quadTexColor = mesh("Nerospace sky solid body", DefaultVertexFormat.POSITION_TEX_COLOR, 2, b -> {
            float[] a = {-1.0F, -1.0F, 1.0F};
            float[] c = {1.0F, -1.0F, 1.0F};
            float[] d = {1.0F, 1.0F, 1.0F};
            float[] e = {-1.0F, 1.0F, 1.0F};
            quadColor(b, a, c, d, e, 0.0F, 1.0F, 1.0F, 0.0F, 0xFFFFFFFF, 0xFFFFFFFF);
        });
        // Galaxy band: a great circle centred on local +Z, 36 degrees wide, fading to nothing 150 degrees away.
        int gseg = 64;
        this.galaxyQuads = gseg * 2;
        this.galaxy = mesh("Nerospace galaxy band", DefaultVertexFormat.POSITION_TEX_COLOR, this.galaxyQuads, b -> {
            for (int i = 0; i < gseg; i++) {
                double a0 = -Math.PI + Math.PI * 2.0 * i / gseg;
                double a1 = -Math.PI + Math.PI * 2.0 * (i + 1) / gseg;
                int c0 = fadeColor(a0);
                int c1 = fadeColor(a1);
                float u0 = (float) i / gseg;
                float u1 = (float) (i + 1) / gseg;
                float[] p00 = band(a0, 18.0);
                float[] p10 = band(a1, 18.0);
                float[] p11 = band(a1, -18.0);
                float[] p01 = band(a0, -18.0);
                quadColorPerColumn(b, p00, p10, p11, p01, u0, u1, c0, c1);
            }
        });
    }

    // Horizon ramp: a ring of quads from rampTopDeg down to the horizon (texture ramp), then to the nadir (solid).
    // One mesh per distinct top elevation (the shipped profiles use two values: 72 and the haze's 55).
    private GpuBuffer ramp(float topDeg) {
        GpuBuffer existing = this.ramps.get(topDeg);
        if (existing != null) {
            return existing;
        }
        int seg = 32;
        float[][] rows = {{topDeg, 0.0F}, {0.0F, 1.0F}, {-90.0F, 1.0F}};
        GpuBuffer buf = mesh("Nerospace sky ramp", DefaultVertexFormat.POSITION_TEX_COLOR, RAMP_QUADS, b -> {
            for (int r = 0; r < 2; r++) {
                for (int i = 0; i < seg; i++) {
                    double a0 = Math.PI * 2.0 * i / seg;
                    double a1 = Math.PI * 2.0 * (i + 1) / seg;
                    float[] top = rows[r];
                    float[] bot = rows[r + 1];
                    float u0 = (float) i / seg;
                    float u1 = (float) (i + 1) / seg;
                    float[] p00 = ring(a0, top[0]);
                    float[] p10 = ring(a1, top[0]);
                    float[] p11 = ring(a1, bot[0]);
                    float[] p01 = ring(a0, bot[0]);
                    quadColor(b, p00, p10, p11, p01, u0, u1, top[1], bot[1], 0xFFFFFFFF, 0xFFFFFFFF);
                }
            }
        });
        this.ramps.put(topDeg, buf);
        return buf;
    }

    // Cloud deck: rows at 0, 4, 0.65 x altitude and the altitude with alpha 0, 1, 1, 0 (bottom and top fades), u
    // tiling around the horizon, v = 1 - elevation / altitude; plus two POSITION_TEX underglow rings (0-8, 8-15).
    private GpuBuffer[] cloudMeshes(float altitude, int tiles) {
        long key = ((long) Float.floatToIntBits(altitude) << 32) | (tiles & 0xFFFFFFFFL);
        GpuBuffer[] existing = this.clouds.get(key);
        if (existing != null) {
            return existing;
        }
        int seg = 48;
        float[] el = {0.0F, Math.min(4.0F, altitude * 0.3F), altitude * 0.65F, altitude};
        int[] alpha = {0x00FFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0x00FFFFFF};
        GpuBuffer band = mesh("Nerospace cloud layer", DefaultVertexFormat.POSITION_TEX_COLOR, seg * 3 * 2, b -> {
            for (int r = 0; r < 3; r++) {
                for (int i = 0; i < seg; i++) {
                    double a0 = Math.PI * 2.0 * i / seg;
                    double a1 = Math.PI * 2.0 * (i + 1) / seg;
                    float u0 = (float) i / seg * tiles;
                    float u1 = (float) (i + 1) / seg * tiles;
                    float vTop = 1.0F - el[r + 1] / altitude;
                    float vBot = 1.0F - el[r] / altitude;
                    quadColor(b, ring(a0, el[r + 1]), ring(a1, el[r + 1]), ring(a1, el[r]), ring(a0, el[r]), u0, u1,
                            vTop, vBot, alpha[r + 1], alpha[r]);
                }
            }
        });
        GpuBuffer low = underglowRing(seg, tiles, altitude, 0.0F, Math.min(8.0F, altitude));
        GpuBuffer mid = underglowRing(seg, tiles, altitude, Math.min(8.0F, altitude), Math.min(15.0F, altitude));
        GpuBuffer[] made = {band, low, mid};
        this.clouds.put(key, made);
        return made;
    }

    private static GpuBuffer underglowRing(int seg, int tiles, float altitude, float from, float to) {
        return mesh("Nerospace cloud underglow", DefaultVertexFormat.POSITION_TEX, seg * 2, b -> {
            for (int i = 0; i < seg; i++) {
                double a0 = Math.PI * 2.0 * i / seg;
                double a1 = Math.PI * 2.0 * (i + 1) / seg;
                float u0 = (float) i / seg * tiles;
                float u1 = (float) (i + 1) / seg * tiles;
                quadTex(b, ring(a0, to), ring(a1, to), ring(a1, from), ring(a0, from), u0, u1, 1.0F - to / altitude,
                        1.0F - from / altitude);
            }
        });
    }

    private static long starKey(int count, float sizeScale) {
        return ((long) count << 32) | (Float.floatToIntBits(sizeScale) & 0xFFFFFFFFL);
    }

    // Two star buffers split by a per-star parity bit, so twinkle is two draws with opposite alphas.
    private GpuBuffer[] starBuffers(int count, float sizeScale) {
        long key = starKey(count, sizeScale);
        GpuBuffer[] existing = this.stars.get(key);
        if (existing != null) {
            return existing;
        }
        GpuBuffer[] made = new GpuBuffer[2];
        int[] quads = new int[2];
        for (int half = 0; half < 2; half++) {
            int want = half;
            RandomSource random = RandomSource.createThreadLocalInstance(10842L + count);
            int[] n = {0};
            made[half] = mesh("Nerospace stars", DefaultVertexFormat.POSITION_TEX, count * 2, b -> {
                for (int i = 0; i < count; i++) {
                    double y = random.nextDouble() * 2.0 - 1.0;
                    double longitude = random.nextDouble() * Math.PI * 2.0;
                    double latitudeRadius = Math.sqrt(1.0 - y * y);
                    double x = Math.cos(longitude) * latitudeRadius;
                    double z = Math.sin(longitude) * latitudeRadius;
                    float magnitude = (float) Math.pow(random.nextFloat(), 5.0);
                    float size = (0.24F + magnitude * 0.7F) * sizeScale;
                    int cell = random.nextFloat() < 0.92F ? random.nextInt(4) * 4 + random.nextInt(2)
                            : random.nextInt(16);
                    if ((i & 1) != want) {
                        continue;
                    }
                    double dx = x;
                    double dy = y;
                    double dz = z;
                    // Two axes perpendicular to the star direction.
                    double ax = Math.abs(dy) < 0.9 ? 0.0 : 1.0;
                    double ay = Math.abs(dy) < 0.9 ? 1.0 : 0.0;
                    double ux = ay * dz;
                    double uy = -ax * dz;
                    double uz = ax * dy - ay * dx;
                    double ul = Math.sqrt(ux * ux + uy * uy + uz * uz);
                    ux /= ul;
                    uy /= ul;
                    uz /= ul;
                    double vx = dy * uz - dz * uy;
                    double vy = dz * ux - dx * uz;
                    double vz = dx * uy - dy * ux;
                    float cx = (float) (dx * R);
                    float cy = (float) (dy * R);
                    float cz = (float) (dz * R);
                    float[] p0 = {cx + (float) ((-ux - vx) * size), cy + (float) ((-uy - vy) * size), cz + (float) ((-uz - vz) * size)};
                    float[] p1 = {cx + (float) ((ux - vx) * size), cy + (float) ((uy - vy) * size), cz + (float) ((uz - vz) * size)};
                    float[] p2 = {cx + (float) ((ux + vx) * size), cy + (float) ((uy + vy) * size), cz + (float) ((uz + vz) * size)};
                    float[] p3 = {cx + (float) ((-ux + vx) * size), cy + (float) ((-uy + vy) * size), cz + (float) ((-uz + vz) * size)};
                    float u0 = (cell % 4) / 4.0F;
                    float v0 = (cell / 4) / 4.0F;
                    quadTex(b, p0, p1, p2, p3, u0, u0 + 0.25F, v0 + 0.25F, v0);
                    n[0]++;
                }
            });
            quads[half] = n[0] * 2;
        }
        this.stars.put(key, made);
        this.starQuads.put(key, quads);
        return made;
    }

    private static int fadeColor(double angle) {
        double f = Math.max(0.0, 1.0 - Math.pow(Math.abs(Math.toDegrees(angle)) / 150.0, 2.0));
        int a = (int) Math.round(255.0 * f);
        return (a << 24) | 0xFFFFFF;
    }

    private static float[] ring(double azimuth, float elevationDeg) {
        double e = Math.toRadians(elevationDeg);
        return new float[] {(float) (Math.sin(azimuth) * Math.cos(e) * R), (float) (Math.sin(e) * R),
                (float) (Math.cos(azimuth) * Math.cos(e) * R)};
    }

    private static float[] band(double along, double acrossDeg) {
        double w = Math.toRadians(acrossDeg);
        return new float[] {(float) (Math.sin(along) * Math.cos(w) * R), (float) (Math.sin(w) * R),
                (float) (Math.cos(along) * Math.cos(w) * R)};
    }

    private interface Filler {
        void fill(BufferBuilder b);
    }

    private static GpuBuffer mesh(String name, VertexFormat format, int quads, Filler filler) {
        try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(Math.max(1, quads) * 4 * format.getVertexSize())) {
            BufferBuilder b = new BufferBuilder(bytes, PrimitiveTopology.QUADS, format);
            filler.fill(b);
            return upload(name, b);
        }
    }

    private static GpuBuffer upload(String name, BufferBuilder b) {
        try (MeshData mesh = b.buildOrThrow()) {
            return RenderSystem.getDevice().createBuffer(() -> name, GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
        }
    }

    // Each quad is written twice (both windings) so face culling never hides a sky part.
    private static void quadTex(BufferBuilder b, float[] p0, float[] p1, float[] p2, float[] p3, float u0, float u1,
            float v0, float v1) {
        b.addVertex(p0[0], p0[1], p0[2]).setUv(u0, v0);
        b.addVertex(p1[0], p1[1], p1[2]).setUv(u1, v0);
        b.addVertex(p2[0], p2[1], p2[2]).setUv(u1, v1);
        b.addVertex(p3[0], p3[1], p3[2]).setUv(u0, v1);
        b.addVertex(p3[0], p3[1], p3[2]).setUv(u0, v1);
        b.addVertex(p2[0], p2[1], p2[2]).setUv(u1, v1);
        b.addVertex(p1[0], p1[1], p1[2]).setUv(u1, v0);
        b.addVertex(p0[0], p0[1], p0[2]).setUv(u0, v0);
    }

    private static void quadColor(BufferBuilder b, float[] p0, float[] p1, float[] p2, float[] p3, float u0, float u1,
            float v0, float v1, int cTop, int cBottom) {
        b.addVertex(p0[0], p0[1], p0[2]).setUv(u0, v0).setColor(cTop);
        b.addVertex(p1[0], p1[1], p1[2]).setUv(u1, v0).setColor(cTop);
        b.addVertex(p2[0], p2[1], p2[2]).setUv(u1, v1).setColor(cBottom);
        b.addVertex(p3[0], p3[1], p3[2]).setUv(u0, v1).setColor(cBottom);
        b.addVertex(p3[0], p3[1], p3[2]).setUv(u0, v1).setColor(cBottom);
        b.addVertex(p2[0], p2[1], p2[2]).setUv(u1, v1).setColor(cBottom);
        b.addVertex(p1[0], p1[1], p1[2]).setUv(u1, v0).setColor(cTop);
        b.addVertex(p0[0], p0[1], p0[2]).setUv(u0, v0).setColor(cTop);
    }

    private static void quadColorPerColumn(BufferBuilder b, float[] p0, float[] p1, float[] p2, float[] p3, float u0,
            float u1, int cLeft, int cRight) {
        b.addVertex(p0[0], p0[1], p0[2]).setUv(u0, 0.0F).setColor(cLeft);
        b.addVertex(p1[0], p1[1], p1[2]).setUv(u1, 0.0F).setColor(cRight);
        b.addVertex(p2[0], p2[1], p2[2]).setUv(u1, 1.0F).setColor(cRight);
        b.addVertex(p3[0], p3[1], p3[2]).setUv(u0, 1.0F).setColor(cLeft);
        b.addVertex(p3[0], p3[1], p3[2]).setUv(u0, 1.0F).setColor(cLeft);
        b.addVertex(p2[0], p2[1], p2[2]).setUv(u1, 1.0F).setColor(cRight);
        b.addVertex(p1[0], p1[1], p1[2]).setUv(u1, 0.0F).setColor(cRight);
        b.addVertex(p0[0], p0[1], p0[2]).setUv(u0, 0.0F).setColor(cLeft);
    }
}
*///?}
