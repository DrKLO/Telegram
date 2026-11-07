package org.telegram.ui.Components.Premium.GLIcon;

import android.content.Context;

final class DiamondProgram {
    static int create(Context context, String vertexSource, String fragmentSource) {
        return GLProgramCache.create(context, "diamond", vertexSource, fragmentSource, "position", "normal");
    }
}
