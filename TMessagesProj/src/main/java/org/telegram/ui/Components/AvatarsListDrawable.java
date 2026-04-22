package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.animator.ListAnimator;
import me.vkryl.core.lambda.Destroyable;

public class AvatarsListDrawable extends Drawable {

    private final View parent;
    private final int currentAccount;
    private final ListAnimator<AvatarItem> animator = new ListAnimator<>(new ListAnimator.Callback() {
        @Override
        public void onItemsChanged(ListAnimator<?> animator) {
            parent.invalidate();
        }
    }, CubicBezierInterpolator.EASE_OUT_QUINT, 380L);
    private boolean attached;

    private final int avatarSize;
    private final int avatarOffset;
    private final float avatarStroke;

    public AvatarsListDrawable(int currentAccount, View parent, int avatarSize, int avatarOffset, float avatarStroke) {
        this.currentAccount = currentAccount;
        this.parent = parent;

        this.avatarSize = avatarSize;
        this.avatarOffset = avatarOffset;
        this.avatarStroke = avatarStroke;
    }

    public void set(List<TLRPC.Peer> peers, boolean animated) {
        if (peers == null || peers.isEmpty()) {
            animator.clear(animated);
            return;
        }

        if (!animated) {
            animator.clear(false);
        }

        final ArrayList<AvatarItem> list = new ArrayList<>(peers.size());
        for (TLRPC.Peer peer : peers) {
            final long dialogId = DialogObject.getPeerDialogId(peer);
            AvatarItem item = find(dialogId);
            if (item == null) {
                item = find(0);
            }
            if (item == null) {
                item = new AvatarItem(parent);
                avatarItemsPool.add(item);
            }
            item.set(currentAccount, dialogId);
            list.add(item);
            if (attached) {
                item.attach();
            }
        }

        animator.reset(list, animated);
    }

    public void attach() {
        if (!attached) {
            attached = true;
            for (AvatarItem item : avatarItemsPool) {
                if (item.dialogId != 0) {
                    item.attach();
                }
            }
        }
    }

    public void detach() {
        if (attached) {
            attached = false;
            for (AvatarItem item : avatarItemsPool) {
                item.detach();
            }
        }
    }


    private final ArrayList<AvatarItem> avatarItemsPool = new ArrayList<>();

    private AvatarItem find(long dialogId) {
        for (AvatarItem item : avatarItemsPool) {
            if (item.dialogId == dialogId) {
                return item;
            }
        }
        return null;
    }



    private class AvatarItem implements ListAnimator.Measurable, Destroyable {
        private final ImageReceiver imageReceiver;
        private final AvatarDrawable avatarDrawable;
        private long dialogId;
        private boolean attached;

        private AvatarItem(View parent) {
            imageReceiver = new ImageReceiver(parent);
            imageReceiver.setRoundRadius(avatarSize / 2);
            avatarDrawable = new AvatarDrawable();
            avatarDrawable.setTextSize(dp(22));
        }

        public void set(int currentAccount, long dialogId) {
            if (this.dialogId == dialogId) {
                return;
            }
            this.dialogId = dialogId;

            final TLObject user = MessagesController.getInstance(currentAccount).getUserOrChat(dialogId);
            if (user != null) {
                avatarDrawable.setInfo(currentAccount, user);
                imageReceiver.setForUserOrChat(user, avatarDrawable);
            } else {
                avatarDrawable.setInfo(dialogId, "", "");
                imageReceiver.clearImage();
            }
        }

        public void attach() {
            if (!attached) {
                attached = true;
                imageReceiver.onAttachedToWindow();
            }
        }

        public void detach() {
            if (attached) {
                attached = false;
                imageReceiver.onDetachedFromWindow();
            }
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if (!(obj instanceof AvatarItem)) {
                return false;
            }

            return dialogId == ((AvatarItem) obj).dialogId;
        }

        @Override
        public void performDestroy() {
            detach();
            dialogId = 0;
        }

        @Override
        public int getSpacingStart(boolean isFirst) {
            return isFirst ? 0 : -avatarOffset;
        }

        @Override
        public int getWidth() {
            return avatarSize;
        }

        @Override
        public int getHeight() {
            return avatarSize;
        }
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        draw(canvas, null);
    }

    public void draw(@NonNull Canvas canvas, @Nullable Paint paint) {
        final Rect bounds = getBounds();
        if (bounds.isEmpty() || alpha == 0) {
            return;
        }

        final float startX = bounds.left;
        final float startY = bounds.top;

        canvas.saveLayer(startX, startY, startX + animator.getMetadata().getTotalWidth(), startY + avatarSize, null);

        // final int oldAlpha = paint != null ? paint.getAlpha() : 255;
        for (int N = animator.size() - 1, a = N; a >= 0; a--) {
            ListAnimator.Entry<AvatarItem> entry = animator.getEntry(a);
            final RectF rect = entry.getRectF();
            final float spacing = entry.getSpacingStart();
            final float visibility = entry.getVisibility();
            float x = rect.left + spacing;
            float w = rect.width() - spacing;

            final float cx = startX + x + w / 2;
            final float cy = startY + w / 2;

            canvas.save();
            canvas.scale(visibility, visibility, cx, cy);
            //if (paint != null) {
                final float r = (w / 2 + avatarStroke);
             //   paint.setAlpha((int)(alpha * visibility));
                canvas.drawCircle(cx, cy, r, Theme.PAINT_CLEAR);
            //}

            entry.item.imageReceiver.setImageCoords(startX + x, startY, w, w);
            entry.item.imageReceiver.setAlpha(entry.getVisibility() * (alpha / 255f));
            entry.item.imageReceiver.draw(canvas);
            canvas.restore();
        }

        canvas.restore();

        //if (paint != null) {
        //    paint.setAlpha(oldAlpha);
        //}
    }

    public float getAnimatedWidth() {
        return animator.getMetadata().getTotalWidth();
    }

    public float getTotalVisibility() {
        return animator.getMetadata().getTotalVisibility();
    }


    private int alpha = 255;

    @Override
    public void setAlpha(int alpha) {
        this.alpha = alpha;
    }

    @Override
    public int getAlpha() {
        return alpha;
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {

    }

    @Override
    public int getOpacity() {
        return PixelFormat.UNKNOWN;
    }
}
