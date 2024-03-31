package org.telegram.ui;


import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Spannable;
import android.text.style.ReplacementSpan;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.ImageReceiver;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.AvatarDrawable;

public class AvatarSpan extends ReplacementSpan {

    private final Paint shadowPaint;
    private final ImageReceiver imageReceiver;
    private final AvatarDrawable avatarDrawable;
    private final int sz;
    private final int currentAccount;

    private View parent;

    public AvatarSpan(View parent, int currentAccount, int sz) {
        this.currentAccount = currentAccount;
        this.imageReceiver = new ImageReceiver(parent);
        this.avatarDrawable = new AvatarDrawable();
        imageReceiver.setRoundRadius(dp(sz));
        this.sz = sz;

        this.shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setShadowLayer(dp(1), 0, dp(.66f), 0x33000000);

        setParent(parent);
    }

    public void setParent(View parent) {
        if (this.parent == parent) return;
        if (this.parent != null) {
           this.parent.removeOnAttachStateChangeListener(parentAttachListener);
           if (this.parent.isAttachedToWindow() && !parent.isAttachedToWindow()) {
               imageReceiver.onDetachedFromWindow();
           }
        }
        if ((this.parent == null || !this.parent.isAttachedToWindow()) && parent != null && parent.isAttachedToWindow()) {
            imageReceiver.onAttachedToWindow();
        }
        this.parent = parent;
        imageReceiver.setParentView(parent);
        if (parent != null) {
            parent.addOnAttachStateChangeListener(parentAttachListener);
        }
    }

    public static void checkSpansParent(CharSequence cs, View parent) {
        if (cs == null) return;
        if (!(cs instanceof Spannable)) return;
        Spannable spannable = (Spannable) cs;
        AvatarSpan[] spans = spannable.getSpans(0, spannable.length(), AvatarSpan.class);
        for (AvatarSpan span : spans) {
            span.setParent(parent);
        }
    }

    private final View.OnAttachStateChangeListener parentAttachListener = new View.OnAttachStateChangeListener() {
        @Override
        public void onViewAttachedToWindow(@NonNull View v) {
            imageReceiver.onAttachedToWindow();
        }
        @Override
        public void onViewDetachedFromWindow(@NonNull View v) {
            imageReceiver.onDetachedFromWindow();
        }
    };

    public void setChat(TLRPC.Chat chat) {
        avatarDrawable.setInfo(currentAccount, chat);
        imageReceiver.setForUserOrChat(chat, avatarDrawable);
    }

    public void setUser(TLRPC.User user) {
        avatarDrawable.setInfo(currentAccount, user);
        imageReceiver.setForUserOrChat(user, avatarDrawable);
    }

    @Override
    public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm) {
        return dp(sz);
    }

    private float translateX, translateY;

    @Override
    public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
        canvas.drawCircle(translateX + x + dp(sz) / 2f, translateY + (top + bottom) / 2f, dp(sz) / 2f, shadowPaint);
        imageReceiver.setImageCoords(translateX + x, translateY + (top + bottom) / 2f - dp(sz) / 2f, dp(sz), dp(sz));
        imageReceiver.draw(canvas);
    }

    public void translate(float x, float y) {
        this.translateX = x;
        this.translateY = y;
    }
}