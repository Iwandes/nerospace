package za.co.neroland.nerospace.client;

import java.util.function.Function;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

/**
 * The Mars rover: a six-wheeled open-cage chassis with two seats, a strapped cargo rack under a solar deck, a rear
 * battery pack and an antenna mast. Transcribed part for part from the rover model spec (24 parts, 30 cubes, 128×128
 * box-UV atlas, 1 texel per unit). Model space is vanilla's: +Y down, ground at y 24, front at −Z, +X the rover's left.
 * Only the six wheels move: they spin about their axles from the render state; the whole entity turns, so there is
 * no steering articulation. Baked directly ({@code createBodyLayer().bakeRoot()}), no model-layer registry.
 */
public class RoverModel extends EntityModel<RoverRenderState> {

    private static final String[] WHEELS = {"wheel_fl", "wheel_fr", "wheel_ml", "wheel_mr", "wheel_rl", "wheel_rr"};

    private final ModelPart[] wheels = new ModelPart[WHEELS.length];

    public RoverModel(ModelPart root, Function<Identifier, RenderType> renderType) {
        super(root, renderType);
        for (int i = 0; i < WHEELS.length; i++) {
            this.wheels[i] = root.getChild(WHEELS[i]);
        }
    }

    @Override
    public void setupAnim(RoverRenderState state) {
        super.setupAnim(state);
        // Positive xRot rolls the wheel toward -Z (forward) under the renderer's transform (model spec).
        for (ModelPart wheel : this.wheels) {
            wheel.xRot = state.wheelAngle;
        }
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartPose origin = PartPose.offset(0.0F, 0.0F, 0.0F);

        root.addOrReplaceChild("chassis",
                CubeListBuilder.create().texOffs(0, 0).addBox(-10F, 11F, -12F, 20F, 6F, 24F), origin);
        root.addOrReplaceChild("nose",
                CubeListBuilder.create().texOffs(0, 32).addBox(-8F, 13F, -18F, 16F, 4F, 6F), origin);
        root.addOrReplaceChild("bull_bar", CubeListBuilder.create()
                .texOffs(44, 32).addBox(-9F, 12F, -19F, 18F, 1F, 1F)
                .texOffs(84, 32).addBox(-9F, 13F, -19F, 1F, 4F, 1F)
                .texOffs(84, 32).addBox(8F, 13F, -19F, 1F, 4F, 1F), origin);
        root.addOrReplaceChild("cargo_rack",
                CubeListBuilder.create().texOffs(44, 38).addBox(-8F, 7F, 2F, 16F, 4F, 10F), origin);
        root.addOrReplaceChild("solar_deck",
                CubeListBuilder.create().texOffs(0, 100).addBox(-9F, 6F, 1F, 18F, 1F, 12F), origin);
        root.addOrReplaceChild("battery",
                CubeListBuilder.create().texOffs(96, 48).addBox(-6F, 12F, 12F, 12F, 5F, 2F), origin);
        root.addOrReplaceChild("dash",
                CubeListBuilder.create().texOffs(88, 0).addBox(-8F, 8F, -11F, 16F, 3F, 2F), origin);
        CubeListBuilder seat = CubeListBuilder.create()
                .texOffs(0, 64).addBox(-3F, 9F, -8F, 6F, 2F, 6F)
                .texOffs(24, 64).addBox(-3F, 3F, -2F, 6F, 6F, 2F);
        root.addOrReplaceChild("seat_l", seat, PartPose.offset(5.0F, 0.0F, 0.0F));   // driver
        root.addOrReplaceChild("seat_r", seat, PartPose.offset(-5.0F, 0.0F, 0.0F));  // passenger
        root.addOrReplaceChild("post_fl",
                CubeListBuilder.create().texOffs(40, 64).addBox(8F, 1F, -9F, 1F, 10F, 1F), origin);
        root.addOrReplaceChild("post_fr",
                CubeListBuilder.create().texOffs(40, 64).addBox(-9F, 1F, -9F, 1F, 10F, 1F), origin);
        root.addOrReplaceChild("post_rl",
                CubeListBuilder.create().texOffs(40, 64).addBox(8F, 1F, 1F, 1F, 10F, 1F), origin);
        root.addOrReplaceChild("post_rr",
                CubeListBuilder.create().texOffs(40, 64).addBox(-9F, 1F, 1F, 1F, 10F, 1F), origin);
        root.addOrReplaceChild("rail_l",
                CubeListBuilder.create().texOffs(44, 64).addBox(8F, 0F, -9F, 1F, 1F, 11F), origin);
        root.addOrReplaceChild("rail_r",
                CubeListBuilder.create().texOffs(44, 64).addBox(-9F, 0F, -9F, 1F, 1F, 11F), origin);
        root.addOrReplaceChild("cross_f",
                CubeListBuilder.create().texOffs(68, 64).addBox(-9F, 0F, -9F, 18F, 1F, 1F), origin);
        root.addOrReplaceChild("cross_r",
                CubeListBuilder.create().texOffs(68, 64).addBox(-9F, 0F, 1F, 18F, 1F, 1F), origin);
        root.addOrReplaceChild("mast", CubeListBuilder.create()
                .texOffs(108, 64).addBox(0F, -14F, 0F, 1F, 14F, 1F)
                .texOffs(112, 64).addBox(-1F, -16F, -1F, 3F, 2F, 3F), PartPose.offset(7.0F, 6.0F, 11.0F));
        float[][] axles = {{12F, -12F}, {-12F, -12F}, {12F, 0F}, {-12F, 0F}, {12F, 12F}, {-12F, 12F}};
        for (int i = 0; i < WHEELS.length; i++) {
            root.addOrReplaceChild(WHEELS[i],
                    CubeListBuilder.create().texOffs(0, 80).addBox(-2F, -4F, -4F, 4F, 8F, 8F),
                    PartPose.offset(axles[i][0], 20.0F, axles[i][1]));
        }
        return LayerDefinition.create(mesh, 128, 128);
    }
}
