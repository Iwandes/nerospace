package za.co.neroland.nerospace.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

import za.co.neroland.nerospace.NerospaceCommon;
import za.co.neroland.nerospace.entity.RoverEntity;

/**
 * Entity renderer for the Mars rover (2.1). Two passes over one skeleton: the lit cutout hull, then the emissive layer
 * (headlights, tail lights, dash, battery bar, mast beacon) full-bright, skipped when the battery is flat. Culls on the
 * model envelope ({@link RoverEntity#visualBounds()}) so the mast and bull bar never pop at the screen edge.
 */
public class RoverRenderer extends EntityRenderer<RoverEntity, RoverRenderState> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(NerospaceCommon.MOD_ID, "textures/entity/rover/rover.png");
    private static final Identifier GLOW =
            Identifier.fromNamespaceAndPath(NerospaceCommon.MOD_ID, "textures/entity/rover/rover_glow.png");
    private static final int FULL_BRIGHT = 0x00F000F0;

    private final RoverModel hull;
    private final RoverModel glow;

    public RoverRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.hull = new RoverModel(RoverModel.createBodyLayer().bakeRoot(), RenderTypes::entityCutout);
        this.glow = new RoverModel(RoverModel.createBodyLayer().bakeRoot(), RenderTypes::entityTranslucentEmissive);
    }

    @Override
    public RoverRenderState createRenderState() {
        return new RoverRenderState();
    }

    @Override
    public void extractRenderState(RoverEntity rover, RoverRenderState state, float partialTick) {
        super.extractRenderState(rover, state, partialTick);
        state.yRot = Mth.rotLerp(partialTick, rover.yRotO, rover.getYRot());
        state.wheelAngle = rover.wheelAngle(partialTick);
        state.lit = rover.getEnergy() > 0;
    }

    @Override
    protected float getShadowRadius(RoverRenderState state) {
        return 0.9F;
    }

    //? if >=26.3 {
    /*@Override
    protected AABB getBoundingBoxForCulling(RoverEntity rover, float partialTick) {
        return rover.visualBounds();
    }
    *///?} else {
    @Override
    protected AABB getBoundingBoxForCulling(RoverEntity rover) {
        return rover.visualBounds();
    }
    //?}

    @Override
    public void submit(RoverRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                       CameraRenderState cameraState) {
        poseStack.pushPose();
        // The standard living-entity transform (model spec): face the heading, flip into model space, plant y 24.
        //? if >=26.3 {
        /*poseStack.rotate(Axis.YP.rotationDegrees(180.0F - state.yRot));
        *///?} else {
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - state.yRot));
        //?}
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -1.501F, 0.0F);

        this.hull.setupAnim(state);
        //? if >=1.21.9 {
        int light = state.lightCoords;
        //?} else {
        /*int light = this.light();
        *///?}
        //? if >=26.3 {
        /*collector.order(0).submitModel(this.hull, state, poseStack, this.hull.renderType(TEXTURE), light, OverlayTexture.NO_OVERLAY, -1);
        *///?} else {
        collector.order(0).submitModel(this.hull, state, poseStack, this.hull.renderType(TEXTURE),
                light, OverlayTexture.NO_OVERLAY, -1, null, 0, null);
        //?}
        if (state.lit) {
            this.glow.setupAnim(state);
            //? if >=26.3 {
            /*collector.order(1).submitModel(this.glow, state, poseStack, this.glow.renderType(GLOW), FULL_BRIGHT, OverlayTexture.NO_OVERLAY, -1);
            *///?} else {
            collector.order(1).submitModel(this.glow, state, poseStack, this.glow.renderType(GLOW),
                    FULL_BRIGHT, OverlayTexture.NO_OVERLAY, -1, null, 0, null);
            //?}
        }

        poseStack.popPose();
        super.submit(state, poseStack, collector, cameraState);
    }
}
