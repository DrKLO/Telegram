package org.telegram.utils.twidget;

import android.graphics.Bitmap;

/**
 * A session's way back from the isolated engine to the client (WidgetSandboxBackend there): what it paints with and
 * what it asks. Calls are synchronous, so frames keep their order and the engine waits for a slow painter as it does
 * in-process. Everything here comes from untrusted code: the client checks it as it would a message from the network.
 */
interface IWidgetEngineCallback {
    /**
     * A font the client paints with (WidgetSandboxBackend#createFont); its metrics as createFont answers them, so that
     * layout and painting agree.
     */
    float[] defineFont(int id, in byte[] families, float size, int weight, boolean italic, in byte[] features);
    /**
     * The width of `text` in a font, CSS px, for an engine that cannot make the font itself: MIUI's Typeface reads
     * Settings, which an isolated process may not (WidgetEngineBackend#textWidth).
     */
    float textWidth(int id, in byte[] text);
    void deleteFont(int id);
    /** An image the engine decoded from a data: URL (WidgetSandboxBackend#loadImage). */
    void defineImage(int id, in Bitmap image);

    /** A part of the next call's large argument, which then comes as null (WidgetEngineBackend#large). */
    void part(in byte[] value);

    void record(in byte[] list, in byte[] composition, double clock, int width, int height, float density,
            float layoutMs, int passes, int builds);
    void recordLayer(int index, int version, in byte[] list);
    void selectableText(in byte[] runs);
    void signal(int kind, in byte[] message);
    void canvasRun(int id, int width, int height, in int[] ops);
    byte[] canvasRead(int id, int x, int y, int width, int height);
    void canvasRelease(int id);
}
