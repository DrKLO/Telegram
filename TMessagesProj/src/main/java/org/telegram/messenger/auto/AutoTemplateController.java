package org.telegram.messenger.auto;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.car.app.Screen;
import androidx.car.app.model.Template;

final class AutoTemplateController {

    interface LoadingTemplateBuilder {
        @NonNull Template buildLoadingTemplate();
    }

    interface ContentTemplateBuilder {
        @NonNull Template buildTemplate();
    }

    private final Screen screen;
    private final LoadingTemplateBuilder loadingTemplateBuilder;
    private final ContentTemplateBuilder contentTemplateBuilder;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private Template currentTemplate;
    private Template loadingTemplate;
    private String activeListKey;
    private long renderedVersion = Long.MIN_VALUE;
    private long requestedVersion = Long.MIN_VALUE;
    private boolean buildScheduled;
    private boolean buildInProgress;
    private boolean invalidatePosted;
    private boolean destroyed;

    AutoTemplateController(@NonNull Screen screen,
                           @NonNull LoadingTemplateBuilder loadingTemplateBuilder,
                           @NonNull ContentTemplateBuilder contentTemplateBuilder) {
        this.screen = screen;
        this.loadingTemplateBuilder = loadingTemplateBuilder;
        this.contentTemplateBuilder = contentTemplateBuilder;
    }

    @NonNull
    Template getTemplate() {
        if (currentTemplate == null) {
            if (loadingTemplate == null) {
                loadingTemplate = loadingTemplateBuilder.buildLoadingTemplate();
            }
            scheduleBuildIfNeeded();
            return loadingTemplate;
        }
        scheduleBuildIfNeeded();
        return currentTemplate;
    }

    void onVisibleModelChanged(@NonNull String listKey, long version) {
        if (destroyed) {
            return;
        }
        activeListKey = listKey;
        requestedVersion = version;
        scheduleBuildIfNeeded();
    }

    void onTabsChanged(@NonNull String listKey, long version) {
        if (destroyed) {
            return;
        }
        activeListKey = listKey;
        requestedVersion = version;
        renderedVersion = Long.MIN_VALUE;
        scheduleBuildIfNeeded();
    }

    void onForceRebuild(@NonNull String listKey, long version) {
        if (destroyed) {
            return;
        }
        activeListKey = listKey;
        requestedVersion = version;
        renderedVersion = Long.MIN_VALUE;
        scheduleBuildIfNeeded();
    }

    void destroy() {
        destroyed = true;
        handler.removeCallbacksAndMessages(null);
    }

    private void scheduleBuildIfNeeded() {
        if (destroyed || buildScheduled || buildInProgress || activeListKey == null) {
            return;
        }
        if (currentTemplate != null && renderedVersion == requestedVersion) {
            return;
        }
        buildScheduled = true;
        handler.post(this::runBuild);
    }

    private void runBuild() {
        buildScheduled = false;
        if (destroyed || activeListKey == null) {
            return;
        }
        if (currentTemplate != null && renderedVersion == requestedVersion) {
            return;
        }
        buildInProgress = true;
        long targetVersion = requestedVersion;
        Template builtTemplate = contentTemplateBuilder.buildTemplate();
        buildInProgress = false;
        if (destroyed) {
            return;
        }
        currentTemplate = builtTemplate;
        boolean versionAdvanced = renderedVersion != targetVersion;
        renderedVersion = targetVersion;
        if (versionAdvanced) {
            postInvalidateOnce();
        }
        if (requestedVersion > renderedVersion) {
            scheduleBuildIfNeeded();
        }
    }

    private void postInvalidateOnce() {
        if (destroyed || invalidatePosted) {
            return;
        }
        invalidatePosted = true;
        handler.post(() -> {
            invalidatePosted = false;
            if (!destroyed) {
                screen.invalidate();
            }
        });
    }
}
