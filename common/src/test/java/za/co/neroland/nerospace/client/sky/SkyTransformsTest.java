package za.co.neroland.nerospace.client.sky;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

class SkyTransformsTest {
    @Test
    void laterDrawsCannotChangeTheCachedTransformOrTintOfAnEarlierDraw() {
        SkyTransforms transforms = new SkyTransforms();
        Matrix4f scratch = new Matrix4f();
        SkyTransforms.Entry background = transforms.next(scratch, 0x000000, 1.0F);
        Matrix4f savedMatrix = new Matrix4f(background.matrix());
        Vector4f savedColor = new Vector4f(background.color());
        scratch.rotateY(1.4F).scale(20.0F);
        SkyTransforms.Entry sun = transforms.next(scratch, 0xFFE080, 0.8F);
        assertNotSame(background.matrix(), sun.matrix());
        assertNotSame(background.color(), sun.color());
        assertEquals(savedMatrix, background.matrix());
        assertEquals(savedColor, background.color());
        assertNotEquals(background, sun);
        scratch.identity();
        assertNotEquals(scratch, sun.matrix());
    }

    @Test
    void aNewFrameReusesStorageAndReplacesAllColorChannels() {
        SkyTransforms transforms = new SkyTransforms();
        SkyTransforms.Entry old = transforms.next(new Matrix4f(), 0xFFFFFF, 1.0F);
        transforms.beginFrame();
        SkyTransforms.Entry next = transforms.next(new Matrix4f().scale(2), 0x4080FF, 0.25F);
        assertSame(old, next);
        assertEquals(new Vector4f(64 / 255.0F, 128 / 255.0F, 1.0F, 0.25F), next.color());
        assertEquals(new Matrix4f().scale(2), next.matrix());
    }
}
