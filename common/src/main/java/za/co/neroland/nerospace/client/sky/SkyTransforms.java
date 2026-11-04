package za.co.neroland.nerospace.client.sky;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

public final class SkyTransforms {
    public record Entry(Matrix4f matrix, Vector4f color) {
    }

    private final List<Entry> entries = new ArrayList<>();
    private int used;

    public void beginFrame() {
        this.used = 0;
    }

    public Entry next(Matrix4fc matrix, int rgb, float alpha) {
        if (this.used == this.entries.size()) {
            this.entries.add(new Entry(new Matrix4f(), new Vector4f()));
        }
        // Minecraft retains these objects for its last-uniform equality check until the next draw/frame.
        Entry entry = this.entries.get(this.used++);
        entry.matrix().set(matrix);
        entry.color().set(((rgb >> 16) & 0xFF) / 255.0F, ((rgb >> 8) & 0xFF) / 255.0F,
                (rgb & 0xFF) / 255.0F, alpha);
        return entry;
    }
}
