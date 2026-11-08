package za.co.neroland.nerospace.client;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

/** Render state for the Mars rover: heading, wheel spin and whether the lamps are lit. */
public class RoverRenderState extends EntityRenderState {

    /** Heading (degrees), partial-tick smoothed. */
    public float yRot;
    /** Wheel spin (radians), driven client-side from the synced motion. */
    public float wheelAngle;
    /** Battery not flat: headlights, tail lights and the charge bar glow. */
    public boolean lit;
}
