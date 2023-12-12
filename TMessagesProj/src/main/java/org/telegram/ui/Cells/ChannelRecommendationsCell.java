package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.LocaleController.getString;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.graphics.text.LineBreaker;
import android.media.Image;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.ButtonBounce;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LoadingDrawable;
import org.telegram.ui.Components.Scroller;
import org.telegram.ui.Components.StaticLayoutEx;
import org.telegram.ui.Components.Text;

import java.util.ArrayList;

public class ChannelRecommendationsCell {

    private ChatMessageCell cell;
    public ChannelRecommendationsCell(ChatMessageCell cell) {
        this.cell = cell;
        this.scroller = new Scroller(cell.getContext());
        this.closeBounce = new ButtonBounce(cell);

        loading = true;
        this.loadingAlpha = new AnimatedFloat(cell, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
    }

    private int currentAccount;
    private long dialogId;
    private MessageObject msg;
    private TLRPC.Chat currentChat;
    public long chatId;

    private final TextPaint serviceTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private StaticLayout serviceText;
    private float serviceTextLeft, serviceTextRight;
    private int serviceTextHeight;

    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path backgroundPath = new Path();
    private float lastBackgroundPathExpandT = -1;

    private int blockWidth = dp(66);
    private float scrollX;
    private float channelsScrollWidth;
    private final ArrayList<ChannelBlock> channels = new ArrayList<>();

    private final Path loadingPath = new Path();
    private LoadingDrawable loadingDrawable;

    private boolean loading;
    private final AnimatedFloat loadingAlpha;

    private Text headerText;

    private final RectF backgroundBounds = new RectF();

    private final RectF closeBounds = new RectF();
    private final ButtonBounce closeBounce;
    private final Paint closePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public void setMessageObject(MessageObject messageObject) {
        this.currentAccount = messageObject.currentAccount;
        this.msg = messageObject;
        this.dialogId = messageObject.getDialogId();
        this.currentChat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
        this.chatId = -dialogId;

        serviceTextPaint.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        serviceTextPaint.setTextSize(dp(14));
        serviceTextPaint.setColor(cell.getThemedColor(Theme.key_chat_serviceText));
        serviceText = new StaticLayout(getString(R.string.ChannelJoined), serviceTextPaint, msg.getMaxMessageTextWidth(), Layout.Alignment.ALIGN_CENTER, 1f, 0f, false);
        serviceTextLeft = serviceText.getWidth();
        serviceTextRight = 0;
        for (int i = 0; i < serviceText.getLineCount(); ++i) {
            serviceTextLeft = Math.min(serviceTextLeft, serviceText.getLineLeft(i));
            serviceTextRight = Math.max(serviceTextRight, serviceText.getLineRight(i));
        }
        serviceTextHeight = serviceText.getHeight();

        closePaint.setStyle(Paint.Style.STROKE);
        closePaint.setStrokeCap(Paint.Cap.ROUND);
        closePaint.setStrokeJoin(Paint.Join.ROUND);
        closePaint.setColor(cell.getThemedColor(Theme.key_dialogEmptyImage));

        cell.totalHeight = dp(4 + 3.33f + 3.33f + 4) + serviceTextHeight;

        if (headerText == null) {
            headerText = new Text(getString(R.string.SimilarChannels), 14, AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM)).hackClipBounds();
        }

        for (int i = 0; i < channels.size(); ++i) {
            channels.get(i).detach();
        }
        channels.clear();
        MessagesController.ChannelRecommendations rec = MessagesController.getInstance(currentAccount).getChannelRecommendations(-dialogId);
        ArrayList<TLRPC.Chat> chats = rec == null || rec.chats == null ? new ArrayList<>() : new ArrayList<>(rec.chats);
        for (int i = 0; i < chats.size(); ++i) {
            if (!ChatObject.isNotInChat(chats.get(i))) {
                chats.remove(i);
                i--;
            }
        }
        loading = chats.isEmpty() || !UserConfig.getInstance(currentAccount).isPremium() && chats.size() == 1;
        if (!loading) {
            int count = chats.size();
            if (!UserConfig.getInstance(currentAccount).isPremium() && rec.more > 0) {
                count = Math.min(count - 1, MessagesController.getInstance(currentAccount).recommendedChannelsLimitDefault);
            }
            count = Math.min(count, 10);
            for (int i = 0; i < count; ++i) {
                channels.add(new ChannelBlock(currentAccount, cell, chats.get(i)));
            }
            if (count < chats.size()) {
                TLRPC.Chat[] _chats = new TLRPC.Chat[3];
                _chats[0] = count >= 0 && count < chats.size() ? chats.get(count) : null;
                _chats[1] = count >= 0 && count + 1 < chats.size() ? chats.get(count + 1) : null;
                _chats[2] = count >= 0 && count + 2 < chats.size() ? chats.get(count + 2) : null;
                channels.add(new ChannelBlock(currentAccount, cell, _chats, (chats.size() + rec.more) - count));
            }
        }

        if (isExpanded()) {
            cell.totalHeight += dp(6 + 134 + 4);
            backgroundPaint.setColor(cell.getThemedColor(Theme.key_chat_inBubble));
        }

        channelsScrollWidth = blockWidth * channels.size() + dp(9) * (channels.size() - 1);
        scrollX = Utilities.clamp(scrollX, channelsScrollWidth, 0);
    }

    public boolean isExpanded() {
        return msg.channelJoinedExpanded && channels.size() > 0;
    }

    public void update() {
        if (msg == null) {
            return;
        }
        setMessageObject(msg);
        cell.invalidateOutbounds();
    }

    public void onAttachedToWindow() {
        for (int i = 0; i < channels.size(); ++i) {
            channels.get(i).attach();
        }
    }

    public void onDetachedFromWindow() {
        for (int i = 0; i < channels.size(); ++i) {
            channels.get(i).detach();
        }
    }

//    public void drawText(Canvas canvas) {
//        if (msg == null || cell == null) return;
//
//        float y = 0;
//        if (serviceText != null) {
//            y += dp(4 + 3.33f + 3.33f) + serviceTextHeight;
//        }
//
//        float expandT;
//        if (cell.transitionParams.animateRecommendationsExpanded) {
//            if (isExpanded()) {
//                expandT = cell.transitionParams.animateChangeProgress;
//            } else {
//                expandT = 1f - cell.transitionParams.animateChangeProgress;
//            }
//        } else {
//            expandT = isExpanded() ? 1f : 0f;
//        }
//        expandT = Utilities.clamp((expandT - .3f) / .7f, 1, 0);
//        if (expandT > 0) {
//            int width = (int) Math.min(cell.getWidth() - dp(18), blockWidth * 6.5f);
//            backgroundBounds.set(
//                    (cell.getWidth() - width) / 2f,
//                    y + dp(4 + 6),
//                    (cell.getWidth() + width) / 2f,
//                    y + dp(4 + 134)
//            );
//            checkBackgroundPath(expandT);
//
//            canvas.save();
//            final float s = .4f + .6f * expandT;
//            canvas.scale(s, s, backgroundBounds.centerX(), backgroundBounds.top - dp(6));
//
//            canvas.clipPath(backgroundPath);
//
//            if (headerText != null) {
//                headerText.draw(canvas, backgroundBounds.left + dp(17), backgroundBounds.top + dp( 20), cell.getThemedColor(Theme.key_windowBackgroundWhiteBlackText), expandT);
//            }
//
//            final float loadingAlpha = this.loadingAlpha.set(loading);
//
//            final float xstart = backgroundBounds.left + dp(7) - scrollX;
//            final float xi = blockWidth + dp(9);
//            int from = (int) Math.floor((backgroundBounds.left - width - xstart) / xi);
//            int to = (int) Math.ceil((backgroundBounds.right - xstart) / xi);
//
//            if (loadingAlpha < 1) {
//                for (int i = Math.max(0, from); i < Math.min(to + 1, channels.size()); ++i) {
//                    ChannelBlock block = channels.get(i);
//
//                    canvas.save();
//                    canvas.translate(xstart + i * xi, backgroundBounds.bottom - ChannelBlock.height());
//                    block.drawText(canvas, blockWidth, expandT * (1f - loadingAlpha));
//                    canvas.restore();
//                }
//            }
//
//            canvas.restore();
//        }
//    }

    public void draw(Canvas canvas) {
        if (msg == null || cell == null) return;

        computeScroll();

        float y = 0;
        if (serviceText != null) {
            canvas.save();
            final float ox = (cell.getWidth() - serviceText.getWidth()) / 2f;
            AndroidUtilities.rectTmp.set(ox + serviceTextLeft - dp(8.66f), dp(4), ox + serviceTextRight + dp(8.66f), dp(4 + 3.33f + 3.33f) + serviceTextHeight);
            cell.drawServiceBackground(canvas, AndroidUtilities.rectTmp, dp(11), 1f);
            canvas.translate(ox, dp(4 + 3.33f));
            serviceText.draw(canvas);
            canvas.restore();

            y += dp(4 + 3.33f + 3.33f) + serviceTextHeight;
        }

        float expandT;
        if (cell.transitionParams.animateRecommendationsExpanded) {
            if (isExpanded()) {
                expandT = cell.transitionParams.animateChangeProgress;
            } else {
                expandT = 1f - cell.transitionParams.animateChangeProgress;
            }
        } else {
            expandT = isExpanded() ? 1f : 0f;
        }
        expandT = Utilities.clamp((expandT - .3f) / .7f, 1, 0);

        if (expandT > 0) {
            final int cellWidth = cell.getWidth() - dp(18);
            blockWidth = (int) (cellWidth > dp(66 * 6 + 9 * 5) ? dp(66) : Math.max(cellWidth / 4.5f - dp(9), dp(66)));
            channelsScrollWidth = blockWidth * channels.size() + dp(9) * (channels.size() - 1);
            final int width = (int) Math.min(cellWidth, blockWidth * 6.5f);
            backgroundBounds.set(
                (cell.getWidth() - width) / 2f,
                y + dp(4 + 6),
                (cell.getWidth() + width) / 2f,
                y + dp(4 + 134)
            );
            scrollX = Utilities.clamp(scrollX, channelsScrollWidth - (backgroundBounds.width() - dp(14)), 0);
            checkBackgroundPath(expandT);

            canvas.save();
            final float s = .4f + .6f * expandT;
            canvas.scale(s, s, backgroundBounds.centerX(), backgroundBounds.top - dp(6));

            backgroundPaint.setAlpha((int) (0xFF * expandT));
            backgroundPaint.setShadowLayer(dpf2(1), 0, dpf2(0.33f), ColorUtils.setAlphaComponent(Color.BLACK, (int) (27 * expandT)));
            canvas.drawPath(backgroundPath, backgroundPaint);

            canvas.clipPath(backgroundPath);

            if (headerText != null) {
                headerText.draw(canvas, backgroundBounds.left + dp(17), backgroundBounds.top + dp( 20), cell.getThemedColor(Theme.key_windowBackgroundWhiteBlackText), expandT);
            }

            final float loadingAlpha = this.loadingAlpha.set(loading);

            final float xstart = backgroundBounds.left + dp(7) - scrollX;
            final float xi = blockWidth + dp(9);
            int from = (int) Math.floor((backgroundBounds.left - width - xstart) / xi);
            int to = (int) Math.ceil((backgroundBounds.right - xstart) / xi);

            if (loadingAlpha < 1) {
                for (int i = Math.max(0, from); i < Math.min(to + 1, channels.size()); ++i) {
                    ChannelBlock block = channels.get(i);

                    canvas.save();
                    canvas.translate(xstart + i * xi, backgroundBounds.bottom - ChannelBlock.height());
                    block.draw(canvas, blockWidth, expandT * (1f - loadingAlpha));
                    block.drawText(canvas, blockWidth, expandT * (1f - loadingAlpha));
                    canvas.restore();
                }
            }
            if (loadingAlpha > 0) {
                loadingPath.rewind();
                for (int i = Math.max(0, from); i < to; ++i) {
                    ChannelBlock.fillPath(loadingPath, blockWidth, xstart + i * xi);
                }

                if (loadingDrawable == null) {
                    loadingDrawable = new LoadingDrawable();
                    loadingDrawable.usePath(loadingPath);
                    loadingDrawable.setAppearByGradient(false);
                }
                final int color = cell.getThemedColor(Theme.key_windowBackgroundWhiteBlackText);
                loadingDrawable.setColors(
                    Theme.multAlpha(color, .05f),
                    Theme.multAlpha(color, .15f),
                    Theme.multAlpha(color, .1f),
                    Theme.multAlpha(color, .3f)
                );
                loadingDrawable.setGradientScale(1.5f);
                loadingDrawable.setAlpha((int) (0xFF * loadingAlpha));
                canvas.save();
                canvas.translate(0, backgroundBounds.bottom - ChannelBlock.height());
                loadingDrawable.draw(canvas);
                canvas.restore();

//                cell.invalidate();
            }

            final float cs = closeBounce.getScale(0.02f);

            final float cx = backgroundBounds.right - dp(16 + 4);
            final float cy = backgroundBounds.top + dp(16 + 4);
            canvas.save();
            canvas.scale(cs, cs, cx, cy);
            closePaint.setStrokeWidth(dp(1.33f));
            canvas.drawLine(cx - dp(4), cy - dp(4), cx + dp(4), cy + dp(4), closePaint);
            canvas.drawLine(cx - dp(4), cy + dp(4), cx + dp(4), cy - dp(4), closePaint);
            closeBounds.set(cx - dp(12), cy - dp(12), cx + dp(12), cy + dp(12));
            canvas.restore();

            canvas.restore();
        }
    }

    private void checkBackgroundPath(float t) {
        if (Math.abs(t - lastBackgroundPathExpandT) < 0.001f) {
            return;
        }

        final float r = dp(16.66f);
        final float d = r * 2;

        final float bottom = backgroundBounds.bottom;

        backgroundPath.rewind();
        AndroidUtilities.rectTmp.set(backgroundBounds.left, backgroundBounds.top, backgroundBounds.left + d, backgroundBounds.top + d);
        backgroundPath.arcTo(AndroidUtilities.rectTmp, -90, -90);
        AndroidUtilities.rectTmp.set(backgroundBounds.left, bottom - d, backgroundBounds.left + d, bottom);
        backgroundPath.arcTo(AndroidUtilities.rectTmp, -180, -90);
        AndroidUtilities.rectTmp.set(backgroundBounds.right - d, bottom - d, backgroundBounds.right, bottom);
        backgroundPath.arcTo(AndroidUtilities.rectTmp, -270, -90);
        AndroidUtilities.rectTmp.set(backgroundBounds.right - d, backgroundBounds.top, backgroundBounds.right, backgroundBounds.top + d);
        backgroundPath.arcTo(AndroidUtilities.rectTmp, 0, -90);
        backgroundPath.lineTo(backgroundBounds.centerX() + dp(8), backgroundBounds.top);
        backgroundPath.lineTo(backgroundBounds.centerX(), backgroundBounds.top - dp(6));
        backgroundPath.lineTo(backgroundBounds.centerX() - dp(8), backgroundBounds.top);
        backgroundPath.close();
    }

    private static class ChannelBlock {
        public static int height() { return dp(99); };
        public static int avatarSize() { return dp(54); };

        private final ChatMessageCell cell;
        public final AvatarDrawable[] avatarDrawable;
        public final ImageReceiver[] avatarImageReceiver;

        private final TextPaint nameTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final CharSequence name;
        private StaticLayout nameText;

        public final boolean isLock;
        private final Drawable subscribersDrawable;
        private final Paint subscribersStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint subscribersBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint subscribersBackgroundDimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private BitmapShader subscribersBackgroundPaintShader;
        private int subscribersBackgroundPaintBitmapWidth, subscribersBackgroundPaintBitmapHeight;
        private Matrix subscribersBackgroundPaintMatrix;
        private final Text subscribersText;

        private boolean subscribersColorSetFromThumb;
        private boolean subscribersColorSet;

        public final ButtonBounce bounce;
        public final TLRPC.Chat chat;

        public ChannelBlock(int currentAccount, ChatMessageCell cell, TLRPC.Chat[] chats, int moreCount) {
            this.cell = cell;
            this.chat = chats[0];
            this.bounce = new ButtonBounce(cell) {
                @Override
                public void invalidate() {
                    cell.invalidateOutbounds();
                }
            };
            final int count = 3;
            avatarImageReceiver = new ImageReceiver[count];
            avatarDrawable = new AvatarDrawable[count];
            for (int i = 0; i < count; ++i) {
                avatarImageReceiver[i] = new ImageReceiver(cell);
                avatarImageReceiver[i].setParentView(cell);
                avatarImageReceiver[i].setRoundRadius(avatarSize());
                avatarDrawable[i] = new AvatarDrawable();
                if (i < chats.length && chats[i] != null) {
                    avatarDrawable[i].setInfo(currentAccount, chats[i]);
                    avatarImageReceiver[i].setForUserOrChat(chats[i], avatarDrawable[i]);
                } else {
//                    int resId = i == 1 ? R.drawable.widget_avatar_5 : R.drawable.widget_avatar_4;
//                    Drawable avatar = cell.getContext().getResources().getDrawable(resId).mutate();
//                    avatarImageReceiver[i].setImageBitmap(avatar);
                    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    final int color = Theme.blendOver(cell.getThemedColor(Theme.key_chat_inBubble), Theme.multAlpha(cell.getThemedColor(Theme.key_windowBackgroundWhiteGrayText), .50f));
                    paint.setColor(color);
                    avatarImageReceiver[i].setImageBitmap(new Drawable() {
                        @Override
                        public void draw(@NonNull Canvas canvas) {
                            canvas.drawCircle(getBounds().centerX(), getBounds().centerY(), getBounds().width() / 2f, paint);
                        }
                        @Override
                        public void setAlpha(int alpha) {
                            paint.setAlpha(Theme.multAlpha(color, alpha / 255f));
                        }
                        @Override
                        public void setColorFilter(@Nullable ColorFilter colorFilter) {
                            paint.setColorFilter(colorFilter);
                        }
                        @Override
                        public int getOpacity() {
                            return PixelFormat.TRANSPARENT;
                        }
                    });
                }
            }
            if (cell.isCellAttachedToWindow()) {
                attach();
            }

            nameTextPaint.setTextSize(dp(11));
            final boolean isPremium = UserConfig.getInstance(cell.currentAccount).isPremium();
            name = LocaleController.getString(isPremium ? R.string.MoreSimilar : R.string.UnlockSimilar);

            subscribersStrokePaint.setStyle(Paint.Style.STROKE);
            isLock = true;
            subscribersDrawable = isPremium ? null : cell.getContext().getResources().getDrawable(R.drawable.mini_switch_lock).mutate();
            if (chat == null || chat.participants_count <= 1) {
                subscribersText = null;
            } else {
                subscribersText = new Text("+" + moreCount, 9.33f, AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
            }
        }

        private void checkNameText(int width) {
            if (nameText != null && nameText.getWidth() == width)
                return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                nameText = StaticLayout.Builder.obtain(name, 0, name.length(), nameTextPaint, width)
                        .setMaxLines(2)
                        .setEllipsize(TextUtils.TruncateAt.END)
                        .setBreakStrategy(LineBreaker.BREAK_STRATEGY_SIMPLE)
                        .setAlignment(Layout.Alignment.ALIGN_CENTER)
                        .build();
            } else {
                nameText = StaticLayoutEx.createStaticLayout(name, nameTextPaint, width, Layout.Alignment.ALIGN_CENTER, 1f, 0f, false, TextUtils.TruncateAt.END, width - dp(16), 2, false);
            }
        }

        public ChannelBlock(int currentAccount, ChatMessageCell cell, TLRPC.Chat chat) {
            this.cell = cell;
            this.chat = chat;
            this.bounce = new ButtonBounce(cell) {
                @Override
                public void invalidate() {
                    cell.invalidateOutbounds();
                }
            };
            avatarImageReceiver = new ImageReceiver[1];
            avatarImageReceiver[0] = new ImageReceiver(cell);
            avatarImageReceiver[0].setParentView(cell);
            avatarImageReceiver[0].setRoundRadius(avatarSize());
            if (cell.isCellAttachedToWindow()) {
                attach();
            }

            avatarDrawable = new AvatarDrawable[1];
            avatarDrawable[0] = new AvatarDrawable();
            avatarDrawable[0].setInfo(currentAccount, chat);
            avatarImageReceiver[0].setForUserOrChat(chat, avatarDrawable[0]);

            nameTextPaint.setTextSize(dp(11));
            CharSequence title = chat != null ? chat.title : "";
            try {
                title = Emoji.replaceEmoji(title, nameTextPaint.getFontMetricsInt(), false);
            } catch (Exception ignore) {}
            name = title;

            subscribersStrokePaint.setStyle(Paint.Style.STROKE);
            isLock = false;
            subscribersDrawable = cell.getContext().getResources().getDrawable(R.drawable.mini_reply_user).mutate();
            if (chat == null || chat.participants_count <= 1) {
                subscribersText = null;
            } else {
                subscribersText = new Text(chat != null ? LocaleController.formatShortNumber(chat.participants_count, null) : "", 9.33f, AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
            }
        }

        public void drawText(Canvas canvas, int width, float alpha) {
            canvas.save();
            final float s = bounce.getScale(0.075f);
            canvas.scale(s, s, width / 2f, height() / 2f);

            checkNameText(width);
            if (nameText != null) {
                canvas.save();
                canvas.translate((width - nameText.getWidth()) / 2f, dp(66.33f));
                if (avatarImageReceiver.length <= 1) {
                    nameTextPaint.setColor(cell.getThemedColor(Theme.key_chat_messageTextIn));
                } else {
                    nameTextPaint.setColor(cell.getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
                }
                nameTextPaint.setAlpha((int) (nameTextPaint.getAlpha() * alpha));
                nameText.draw(canvas);
                canvas.restore();
            }

            if (subscribersText != null) {
                subscribersText.ellipsize(width - dp(32));
                final float subscribersTextWidth = dp(subscribersDrawable != null ? 17 : 8) + subscribersText.getWidth();
                float left = (width - subscribersTextWidth) / 2f;
                final float cy = dp(11 - 14.33f / 2f + .33f) + avatarSize();
                final float sc = .625f;
                if (subscribersDrawable != null) {
                    subscribersDrawable.setBounds(
                        (int) (left + (isLock ? subscribersText.getWidth() + dp(1.33f) : 0) + dp(3)),
                        (int) (cy - subscribersDrawable.getIntrinsicHeight() / 2f * sc),
                        (int) (left + (isLock ? subscribersText.getWidth() + dp(1.33f) : 0) + dp(3) + subscribersDrawable.getIntrinsicWidth() * sc),
                        (int) (cy + subscribersDrawable.getIntrinsicHeight() / 2f * sc)
                    );
                    subscribersDrawable.draw(canvas);
                }
                subscribersText.draw(canvas, left + dp(!isLock ? 12.66f : 4), cy, Color.WHITE, alpha);
            }

            canvas.restore();
        }

        private Path subscribersBackgroundPath;

        public void draw(Canvas canvas, int width, float alpha) {
            canvas.save();
            final float s = bounce.getScale(0.075f);
            canvas.scale(s, s, width / 2f, height() / 2f);

            subscribersStrokePaint.setStrokeWidth(dp(2.66f));
            subscribersStrokePaint.setColor(cell.getThemedColor(Theme.key_chat_inBubble));
            for (int i = avatarImageReceiver.length - 1; i >= 0; --i) {
                final float x = width / 2f - dp(7) * (avatarImageReceiver.length - 1) / 2f + i * dp(7);
                final float y = dp(10) + avatarSize() / 2f;
                if (avatarImageReceiver.length > 1) {
                    canvas.drawCircle(x, y, avatarSize() / 2f, subscribersStrokePaint);
                }
                avatarImageReceiver[i].setImageCoords(
                    x - avatarSize() / 2f,
                    y - avatarSize() / 2f,
                    avatarSize(),
                    avatarSize()
                );
                avatarImageReceiver[i].setAlpha(alpha);
                avatarImageReceiver[i].draw(canvas);
            }

            if (subscribersText != null) {
                subscribersText.ellipsize(width - dp(32));
                final float subscribersTextWidth = dp(subscribersDrawable != null ? 17 : 8) + subscribersText.getWidth();

                final float bottom = dp(10) + avatarSize() + dp(1);
                AndroidUtilities.rectTmp.set((width - subscribersTextWidth) / 2f, bottom - dp(14.33f), (width + subscribersTextWidth) / 2f, bottom);

                if (!subscribersColorSet && isLock) {
                    subscribersBackgroundPaint.setColor(Theme.blendOver(cell.getThemedColor(Theme.key_chat_inBubble), Theme.multAlpha(cell.getThemedColor(Theme.key_windowBackgroundWhiteGrayText), .85f)));
                    subscribersColorSet = true;
                } else if (!subscribersColorSet && avatarImageReceiver[0].getStaticThumb() instanceof BitmapDrawable) {
                    final Bitmap bitmap = ((BitmapDrawable) avatarImageReceiver[0].getStaticThumb()).getBitmap();
                    try {
                        final int bitmapColor = bitmap.getPixel(bitmap.getWidth() / 2, bitmap.getHeight() - 2);
                        float[] hsl = new float[3];
                        ColorUtils.colorToHSL(bitmapColor, hsl);
                        if (hsl[1] <= .05f || hsl[1] >= .95f || hsl[2] <= .02f || hsl[2] >= .98f) {
                            hsl[1] = 0;
                            hsl[2] = Theme.isCurrentThemeDark() ? .38f : .70f;
                        } else {
                            hsl[1] = .25f;
                            hsl[2] = Theme.isCurrentThemeDark() ? .35f : .65f;
                        }
                        subscribersBackgroundPaint.setColor(ColorUtils.HSLToColor(hsl));
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    subscribersColorSet = true;
                } else if (!subscribersColorSet && !subscribersColorSetFromThumb) {
                    try {
                        final int color = ColorUtils.blendARGB(avatarDrawable[0].getColor(), avatarDrawable[0].getColor2(), .5f);
                        float[] hsl = new float[3];
                        ColorUtils.colorToHSL(color, hsl);
                        if (hsl[1] <= .05f || hsl[1] >= .95f) {
                            hsl[2] = Utilities.clamp(hsl[2] - .1f, .6f, .3f);
                        } else {
                            hsl[1] = Utilities.clamp(hsl[1] - .06f, .4f, 0);
                            hsl[2] = Utilities.clamp(hsl[2] - .08f, .5f, .2f);
                        }
                        subscribersBackgroundPaint.setColor(ColorUtils.HSLToColor(hsl));
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    subscribersColorSetFromThumb = true;
                }
                if (subscribersBackgroundPaintShader != null) {
                    subscribersBackgroundPaintMatrix.reset();
                    subscribersBackgroundPaintMatrix.postScale(avatarSize() / (float) subscribersBackgroundPaintBitmapWidth, avatarSize() / (float) subscribersBackgroundPaintBitmapHeight);
                    subscribersBackgroundPaintMatrix.postTranslate(width / 2f - avatarSize() / 2f, AndroidUtilities.rectTmp.bottom - avatarSize());
                    subscribersBackgroundPaintShader.setLocalMatrix(subscribersBackgroundPaintMatrix);
                    canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(8), dp(8), subscribersBackgroundPaint);
                    canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(8), dp(8), subscribersBackgroundDimPaint);
                } else {
                    canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(8), dp(8), subscribersBackgroundPaint);
                }

                AndroidUtilities.rectTmp.inset(-dp(1) / 2f, -dp(1) / 2f);
                subscribersStrokePaint.setStrokeWidth(dp(1));
                canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(8), dp(8), subscribersStrokePaint);
            }

            canvas.restore();
        }

        public static void fillPath(Path path, int width, float x) {
            path.addCircle(x + width / 2f, dp(10) + avatarSize() / 2f, avatarSize() / 2f, Path.Direction.CW);

            final float nameWidth = width * .4f;
            AndroidUtilities.rectTmp.set(x + (width - nameWidth) / 2f, dp(74 - 5), x + (width + nameWidth) / 2f, dp(74 + 5));
            path.addRoundRect(AndroidUtilities.rectTmp, dp(3), dp(3), Path.Direction.CW);

            final float subWidth = width * .35f;
            AndroidUtilities.rectTmp.set(x + (width - subWidth) / 2f, dp(87 - 4), x + (width + subWidth) / 2f, dp(87 + 4));
            path.addRoundRect(AndroidUtilities.rectTmp, dp(2.5f), dp(2.5f), Path.Direction.CW);
        }

        public void attach() {
            for (int i = 0; i < avatarImageReceiver.length; ++i) {
                avatarImageReceiver[i].onAttachedToWindow();
            }
        }

        public void detach() {
            for (int i = 0; i < avatarImageReceiver.length; ++i) {
                avatarImageReceiver[i].onDetachedFromWindow();
            }
        }
    }

    private boolean maybeScrolling;
    private boolean scrolling;
    private float lx, ly;
    private VelocityTracker velocityTracker;
    private final Scroller scroller;

    private ChannelBlock longPressedBlock;
    private Runnable longPressRunnable;

    public boolean checkTouchEvent(MotionEvent ev) {
        if (msg == null || cell == null) return false;

        final int a = ev.getAction();
        ChannelBlock block = null;
        float x = backgroundBounds.left + dp(7) - scrollX;
        for (int i = 0; i < channels.size(); ++i) {
            ChannelBlock b = channels.get(i);
            if (ev.getX() >= x && ev.getX() <= x + blockWidth && ev.getY() >= backgroundBounds.bottom - ChannelBlock.height() && ev.getY() < backgroundBounds.bottom) {
                block = b;
                break;
            }
            x += blockWidth + dp(9);
        }

        final boolean clickClose = closeBounds.contains(ev.getX(), ev.getY());

        if (a == MotionEvent.ACTION_DOWN) {
            scroller.abortAnimation();
            maybeScrolling = !loading && backgroundBounds.contains(lx = ev.getX(), ly = ev.getY());
            if (maybeScrolling && cell.getParent() != null) {
                cell.getParent().requestDisallowInterceptTouchEvent(true);
            }
            scrolling = false;
            if (velocityTracker != null) {
                velocityTracker.recycle();
                velocityTracker = null;
            }
            velocityTracker = VelocityTracker.obtain();
            if (block != null) {
                block.bounce.setPressed(true);
            }
            if (clickClose) {
                closeBounce.setPressed(true);
            }
            if (longPressRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(longPressRunnable);
                longPressRunnable = null;
            }
            final ChannelBlock finalBlock = block;
            longPressedBlock = block;
            if (longPressedBlock != null) {
                AndroidUtilities.runOnUIThread(longPressRunnable = () -> {
                    if (finalBlock == longPressedBlock) {
                        longPressedBlock.bounce.setPressed(false);
                        if (longPressedBlock.isLock) {
                            if (cell.getDelegate() != null) {
                                cell.getDelegate().didPressMoreChannelRecommendations(cell);
                            }
                        } else {
                            didClickChannel(longPressedBlock.chat, true);
                        }
                    }
                    longPressedBlock = null;
                    longPressRunnable = null;
                    scrolling = false;
                    maybeScrolling = false;
                    closeBounce.setPressed(false);
                    if (velocityTracker != null) {
                        velocityTracker.recycle();
                        velocityTracker = null;
                    }
                }, ViewConfiguration.getLongPressTimeout());
            }
            return maybeScrolling;
        } else if (a == MotionEvent.ACTION_MOVE) {
            if (velocityTracker != null) {
                velocityTracker.addMovement(ev);
            }
            if (maybeScrolling && Math.abs(ev.getX() - lx) >= AndroidUtilities.touchSlop || scrolling) {
                if (longPressRunnable != null) {
                    AndroidUtilities.cancelRunOnUIThread(longPressRunnable);
                    longPressRunnable = null;
                }
                scrolling = true;
                scroll(lx - ev.getX());
                lx = ev.getX();
                unselectBlocks();
                return true;
            }
        } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
            if (longPressRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(longPressRunnable);
                longPressRunnable = null;
            }

            if (velocityTracker != null) {
                velocityTracker.addMovement(ev);
            }
            final boolean wasScrolling = scrolling;
            scrolling = false;
            if (a == MotionEvent.ACTION_UP) {
                if (!wasScrolling && block != null && block.bounce.isPressed()) {
                    if (block.isLock) {
                        if (cell.getDelegate() != null) {
                            cell.getDelegate().didPressMoreChannelRecommendations(cell);
                        }
                    } else {
                        didClickChannel(block.chat, false);
                    }
                } else if (wasScrolling && velocityTracker != null) {
                    velocityTracker.computeCurrentVelocity(500);
                    int velocity = (int) -velocityTracker.getXVelocity();
                    scroller.fling((int) scrollX, 0, velocity, 0, -Integer.MAX_VALUE, Integer.MAX_VALUE, 0, 0);
                } else if (closeBounce.isPressed()) {
                    didClickClose();
                }
            }

            closeBounce.setPressed(false);

            maybeScrolling = false;
            if (velocityTracker != null) {
                velocityTracker.recycle();
                velocityTracker = null;
            }
            unselectBlocks();
            return wasScrolling;
        }
        return false;
    }

    public void didClickClose() {
        if (cell.getDelegate() != null) {
            cell.getDelegate().didPressChannelRecommendationsClose(cell);
        }
    }

    public void didClickChannel(TLRPC.Chat chat, boolean longPress) {
        if (cell.getDelegate() != null) {
            cell.getDelegate().didPressChannelRecommendation(cell, chat, longPress);
        }
    }

    private void unselectBlocks() {
        for (int i = 0; i < channels.size(); ++i) {
            channels.get(i).bounce.setPressed(false);
        }
    }

    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollX = scroller.getCurrX();
            scrollX = Utilities.clamp(scrollX, channelsScrollWidth - (backgroundBounds.width() - dp(14)), 0);
            cell.invalidateOutbounds();
        }
    }

    private void scroll(float dx) {
        scrollX = Utilities.clamp(scrollX + dx, channelsScrollWidth - (backgroundBounds.width() - dp(14)), 0);
        cell.invalidateOutbounds();
    }
}
