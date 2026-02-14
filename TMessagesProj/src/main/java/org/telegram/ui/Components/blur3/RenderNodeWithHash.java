package org.telegram.ui.Components.blur3;

import android.graphics.Canvas;
import android.graphics.RecordingCanvas;
import android.graphics.RenderNode;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import org.telegram.messenger.MediaDataController;

@RequiresApi(api = Build.VERSION_CODES.Q)
public class RenderNodeWithHash {
    public static final long HASH_UNSUPPORTED = -1;

    public final RenderNode renderNode;
    private final Renderer renderer;
    private final HashBuilder hashBuilder = new HashBuilder();

    private long lastHash = 0;
    private int lastWidth, lastHeight;

    public RenderNodeWithHash(RenderNode renderNode, Renderer renderer) {
        this.renderNode = renderNode;
        this.renderer = renderer;
    }

    public RenderNodeWithHash(String name, Renderer renderer) {
        this.renderNode = new RenderNode(name);
        this.renderer = renderer;
    }

    public interface Renderer {
        void renderNodeUpdateDisplayList(Canvas canvas);
        default void renderNodeCalculateHash(HashBuilder hash) { hash.unsupported(); }
    }

    public void updateDisplayListIfNeeded() {
        final int width = renderNode.getWidth();
        final int height = renderNode.getHeight();

        hashBuilder.reset();
        renderer.renderNodeCalculateHash(hashBuilder);

        final long hash = hashBuilder.get();
        boolean needUpdateDisplayList = !renderNode.hasDisplayList()
            || width != lastWidth
            || height != lastHeight
            || hash != lastHash
            || hash == HASH_UNSUPPORTED;

        lastWidth = width;
        lastHeight = height;
        lastHash = hash;

        if (needUpdateDisplayList) {
            RecordingCanvas canvas = renderNode.beginRecording();
            renderer.renderNodeUpdateDisplayList(canvas);
            renderNode.endRecording();
        }
    }

    public void invalidate() {
        lastHash = 0;
    }



    public static class HashBuilder {
        private long hash;

        private void reset() {
            hash = 0;
        }

        public long get() {
            return hash;
        }

        public void add(long value) {
            hash = MediaDataController.calcHash(hash, value);
        }

        public void addF(float value) {
            hash = MediaDataController.calcHash(hash, Float.floatToIntBits(value));
        }

        public void add(boolean value) {
            hash = MediaDataController.calcHash(hash, value ? 1 : 0);
        }

        public void unsupported() {
            hash = -1;
        }
    }
}
