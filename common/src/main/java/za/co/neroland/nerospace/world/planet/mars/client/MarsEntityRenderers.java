package za.co.neroland.nerospace.world.planet.mars.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

import za.co.neroland.nerospace.NerospaceCommon;
import za.co.neroland.nerospace.client.ClientEntityRenderers;
import za.co.neroland.nerospace.client.GreenxertzCreatureRenderer;
import za.co.neroland.nerospace.client.MeadowLoperModel;
import za.co.neroland.nerospace.world.planet.mars.MarsContent;

/**
 * Client renderers for Mars entities, called from the single {@link ClientEntityRenderers} call site on every loader:
 * the Dust Devil's spinning vortex core ({@link DustDevilRenderer}), the Salvage Drone on its own hover chassis
 * ({@link SalvageDroneModel}, lifted 0.3 blocks off the ground) and the Dust Strider on the Meadow Loper body.
 */
public final class MarsEntityRenderers {

    /** How far the Salvage Drone hovers above its feet (blocks). */
    static final float DRONE_HOVER = 0.3F;

    private MarsEntityRenderers() {
    }

    private static Identifier tex(String name) {
        return Identifier.fromNamespaceAndPath(NerospaceCommon.MOD_ID, "textures/entity/" + name + ".png");
    }

    public static void registerAll(ClientEntityRenderers.Sink sink) {
        sink.register(MarsContent.DUST_DEVIL.get(), DustDevilRenderer::new);
        sink.register(MarsContent.SALVAGE_DRONE.get(), context -> new GreenxertzCreatureRenderer(context,
                new SalvageDroneModel(SalvageDroneModel.createBodyLayer().bakeRoot()),
                tex("salvage_drone"), 0.7F, 0.7F, 0.7F, 0.3F, tex("salvage_drone_glow")) {
            @Override
            protected void scale(LivingEntityRenderState state, PoseStack poseStack) {
                // Model space is flipped (y down) here, so a negative y lifts the drone.
                poseStack.translate(0.0F, -DRONE_HOVER, 0.0F);
                super.scale(state, poseStack);
            }
        });
        sink.register(MarsContent.DUST_STRIDER.get(), context -> new GreenxertzCreatureRenderer(context,
                new MeadowLoperModel(MeadowLoperModel.createBodyLayer().bakeRoot()),
                tex("dust_strider"), 0.85F, 1.2F, 0.85F, 0.5F));
        // 2.1: the Mars rover (a vehicle, but Mars-native art; works in every dimension).
        sink.register(za.co.neroland.nerospace.registry.ModEntities.ROVER.get(),
                za.co.neroland.nerospace.client.RoverRenderer::new);
    }
}
