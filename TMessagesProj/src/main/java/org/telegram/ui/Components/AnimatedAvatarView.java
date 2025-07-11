/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

/**
 * Custom avatar view that integrates with AvatarAnimationHelper to provide
 * black circle transformation effect during collapsing header animations.
 */
public class AnimatedAvatarView extends BackupImageView {

    private AvatarAnimationHelper animationHelper;

    public AnimatedAvatarView(Context context) {
        super(context);
    }

    /**
     * Set the animation helper for black circle effect
     */
    public void setAnimationHelper(AvatarAnimationHelper helper) {
        this.animationHelper = helper;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // First draw the normal avatar image
        super.onDraw(canvas);

        // Then draw the black circle overlay if animation helper indicates it should
        if (animationHelper != null && animationHelper.isInBlackCircleState()) {
            animationHelper.drawBlackCircleOverlay(canvas, this);
        }
    }

    /**
     * Get the animation helper for external access
     */
    public AvatarAnimationHelper getAnimationHelper() {
        return animationHelper;
    }
}