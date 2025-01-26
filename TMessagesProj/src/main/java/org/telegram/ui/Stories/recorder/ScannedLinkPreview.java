package org.telegram.ui.Stories.recorder;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.RenderNode;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserNameResolver;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.AvatarSpan;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.ButtonBounce;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.Point;
import org.telegram.ui.Components.Text;
import org.telegram.ui.ProfileActivity;

import java.util.ArrayList;
import java.util.List;

public class ScannedLinkPreview extends View {

    private final int currentAccount;
    private final Runnable resolvedListener;
    private Utilities.Callback<Utilities.Callback<BaseFragment>> clickListener;

    private final AnimatedFloat animatedAlpha = new AnimatedFloat(this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);

    private final RectF bounds = new RectF();
    private final RectF clipBounds = new RectF();
    private final ImageReceiver imageReceiver = new ImageReceiver(this);
    private boolean hasImage;
    private Text title, subtitle;
    private final Path clipPath = new Path();
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ButtonBounce bounce = new ButtonBounce(this);

    private boolean hasResolved;
    private ResolvedLink resolved;

    public ScannedLinkPreview(Context context, int currentAccount, Runnable resolvedListener) {
        super(context);
        this.currentAccount = currentAccount;
        this.resolvedListener = resolvedListener;
    }

    public void whenClicked(Utilities.Callback<Utilities.Callback<BaseFragment>> clickListener) {
        this.clickListener = clickListener;
    }

    private View blurView;
    private Object blurRenderNode;
    public void setBlurRenderNode(View renderNodeView, Object renderNode) {
        this.blurView = renderNodeView;
        this.blurRenderNode = renderNode;
        invalidate();
    }

    public boolean isResolved() {
        return hasResolved;
    }

    private Runnable currentCancel;
    private String currentLink;
    public void setLink(String link) {
        if (TextUtils.isEmpty(link)) {
            if (currentCancel != null) {
                currentCancel.run();
                currentCancel = null;
            }
            if (hasResolved) invalidate();
            hasResolved = false;
            currentLink = null;
            if (resolvedListener != null) {
                resolvedListener.run();
            }
        } else if (resolved == null && currentCancel == null || resolved != null && !TextUtils.equals(resolved.sourceLink, link) && !TextUtils.equals(currentLink, link)) {
            if (currentCancel != null) {
                currentCancel.run();
                currentCancel = null;
            }
            resolved = null;
            currentLink = link;
            currentCancel = ResolvedLink.resolve(currentAccount, link, result -> {
                currentCancel = null;
                resolved = result;
                hasResolved = result != null;
                setup();
                invalidate();
                if (resolvedListener != null) {
                    resolvedListener.run();
                }
            });
        } else if (resolved != null && !hasResolved && TextUtils.equals(resolved.sourceLink, link)) {
            hasResolved = true;
            setup();
            invalidate();
            if (resolvedListener != null) {
                resolvedListener.run();
            }
        }
    }

    public void cancel() {
        if (currentCancel != null) {
            currentCancel.run();
            currentCancel = null;
        }
        resolved = null;
        hasResolved = false;
        invalidate();
        if (resolvedListener != null) {
            resolvedListener.run();
        }
    }

    private void setup() {
        if (resolved == null) return;
        title = new Text(resolved.getTitle(), 16, AndroidUtilities.bold());
        final SpannableStringBuilder sb = new SpannableStringBuilder(resolved.getSubtitle());
        if (sb.toString().contains(">")) {
            sb.clear();
            sb.append(AndroidUtilities.replaceArrows(resolved.getSubtitle(), false));
        } else {
            sb.append(" ");
            sb.append(">");
            final ColoredImageSpan span = new ColoredImageSpan(R.drawable.settings_arrow);
            span.setScale(1.25f, 1.25f);
            sb.setSpan(span, sb.length() - 1, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        subtitle = new Text(sb, 14);
        hasImage = resolved.setImage(imageReceiver);
    }

    private final int[] thisLocation = new int[2];
    private final int[] blurLocation = new int[2];

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        final float alpha = animatedAlpha.set(hasResolved);
        if (title == null || subtitle == null) return;
        if (alpha <= 0) return;

        title.ellipsize(getWidth() * .7f);
        subtitle.ellipsize(getWidth() * .7f);

        final float px = dp(5), py = dp(10);
        final float imgsize = dp(32);
        final float pph = dp(2), pimg = dp(11);

        final float width = Math.max(Math.min(dp(200), getWidth() * .8f), px + (hasImage ? pimg + imgsize + pimg : 0) + Math.max(title.getCurrentWidth(), subtitle.getCurrentWidth()) + dp(15) + px);
        final float height = py + Math.max(hasImage ? imgsize : 0, title.getHeight() + pph + subtitle.getHeight()) + py;

        final float scale = bounce.getScale(0.05f) * lerp(0.6f, 1.0f, alpha);
        final float ty = dp(15) * (1.0f - alpha);

        bounds.set((getWidth() - width) / 2.0f, (getHeight() - height) / 2.0f, (getWidth() + width) / 2.0f, (getHeight() + height) / 2.0f);
        clipBounds.set(bounds);
        AndroidUtilities.scaleRect(clipBounds, scale);
        clipBounds.offset(0, ty);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && blurRenderNode != null && blurView != null) {
            final RenderNode node = (RenderNode) blurRenderNode;
            clipPath.rewind();
            clipPath.addRoundRect(clipBounds, dp(12), dp(12), Path.Direction.CW);
            getLocationOnScreen(thisLocation);
            blurView.getLocationOnScreen(blurLocation);
            canvas.saveLayerAlpha(clipBounds, (int) (0xFF * alpha), Canvas.ALL_SAVE_FLAG);
            canvas.clipPath(clipPath);
            canvas.translate(blurLocation[0] - thisLocation[0], blurLocation[1] - thisLocation[1]);
            final float s = Math.max((float) blurView.getWidth() / node.getWidth(), (float) blurView.getHeight() / node.getHeight());
            canvas.scale(s, s);
            canvas.drawRenderNode(node);
            canvas.restore();
            backgroundPaint.setColor(Theme.multAlpha(0x70000000, alpha));
            canvas.drawRoundRect(clipBounds, dp(12), dp(12), backgroundPaint);
        } else {
            backgroundPaint.setColor(Theme.multAlpha(0xdd000000, alpha));
            canvas.drawRoundRect(clipBounds, dp(12), dp(12), backgroundPaint);
        }

        canvas.save();
        canvas.translate(0, ty);
        canvas.scale(scale, scale, bounds.centerX(), bounds.centerY());

        if (hasImage) {
            imageReceiver.setRoundRadius((int) (imgsize / 2.0f));
            imageReceiver.setImageCoords(bounds.left + px + pimg, bounds.centerY() - imgsize / 2.0f, imgsize, imgsize);
            imageReceiver.setAlpha(alpha);
            imageReceiver.draw(canvas);
        }
        final float textTop = bounds.centerY() - (title.getHeight() + pph + subtitle.getHeight()) / 2.0f;
        title.draw(canvas, bounds.left + (hasImage ? pimg + imgsize + pimg : 0) + px, textTop + title.getHeight() / 2.0f, 0xFFFFFFFF, alpha);
        subtitle.draw(canvas, bounds.left + (hasImage ? pimg + imgsize + pimg : 0) + px, textTop + title.getHeight() + pph + subtitle.getHeight() / 2.0f, Theme.blendOver(0xFF000000, 0x9FFFFFFF), alpha);

        canvas.restore();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        imageReceiver.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        imageReceiver.onDetachedFromWindow();
    }

    private boolean touch;
    public boolean inTouch() {
        return bounce.isPressed() || touch;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        if (!hasResolved || resolved == null) {
            bounce.setPressed(touch = false);
            return false;
        }
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            if (bounds.contains(e.getX(), e.getY())) {
                bounce.setPressed(touch = true);
            }
        } else if (e.getAction() == MotionEvent.ACTION_MOVE) {
            if (bounce.isPressed() && !bounds.contains(e.getX(), e.getY())) {
                bounce.setPressed(false);
            }
        } else if (e.getAction() == MotionEvent.ACTION_UP) {
            if (bounce.isPressed()) {
                if (clickListener != null && resolved != null) {
                    clickListener.run(fragment -> {
                        if (resolved != null || fragment == null) {
                            resolved.open(fragment);
                        }
                    });
                }
            }
            bounce.setPressed(false);
            touch = false;
        } else if (e.getAction() == MotionEvent.ACTION_CANCEL) {
            bounce.setPressed(false);
            touch = false;
        }
        return touch || bounce.isPressed();
    }

    public static class ResolvedLink {

        public final String sourceLink;
        public ResolvedLink(String sourceLink) {
            this.sourceLink = sourceLink;
        }

        public static Runnable resolve(int currentAccount, String link, Utilities.Callback<ResolvedLink> whenResolved) {
            if (whenResolved == null) return null;
            try {
                final MessagesController mc = MessagesController.getInstance(currentAccount);
                final String prefix = mc.linkPrefix;

                final Uri uri = Uri.parse(link);
                if (!TextUtils.equals(uri.getHost(), prefix)) {
                    return null;
                }

                final List<String> segments = uri.getPathSegments();

                if (segments.isEmpty()) {
                    return null;
                }

                final String first = segments.get(0);
                final String referrer = uri.getQueryParameter("ref");

                if (TextUtils.isEmpty(referrer)) {
                    TLObject obj = mc.getUserOrChat(first);
                    if (obj instanceof TLRPC.User) {
                        whenResolved.run(fromUser(link, (TLRPC.User) obj));
                        return null;
                    } else if (obj instanceof TLRPC.Chat) {
                        whenResolved.run(fromChat(link, (TLRPC.Chat) obj));
                        return null;
                    }
                }

                return mc.getUserNameResolver().resolve(first, referrer, did -> {
                    if (did == null) {
                        whenResolved.run(null);
                    } else {
                        TLObject obj = mc.getUserOrChat(did);
                        if (obj instanceof TLRPC.User) {
                            whenResolved.run(fromUser(link, (TLRPC.User) obj));
                        } else if (obj instanceof TLRPC.Chat) {
                            whenResolved.run(fromChat(link, (TLRPC.Chat) obj));
                        }
                    }
                });
            } catch (Exception e) {
                FileLog.e(e);
            }
            whenResolved.run(null);
            return null;
        }

        public static ResolvedLink fromUser(String link, TLRPC.User user) {
            if (user == null) return null;
            return new ResolvedLink(link) {
                @Override
                public String getTitle() {
                    return UserObject.getUserName(user);
                }
                @Override
                public String getSubtitle() {
                    return getString(R.string.ViewProfile);
                }
                @Override
                public boolean setImage(ImageReceiver imageReceiver) {
                    AvatarDrawable avatarDrawable = new AvatarDrawable();
                    avatarDrawable.setInfo(user);
                    imageReceiver.setForUserOrChat(user, avatarDrawable);
                    return true;
                }
                @Override
                public void open(BaseFragment fragment) {
                    if (user.id == UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId()) {
                        Bundle args = new Bundle();
                        args.putLong("user_id", user.id);
                        args.putBoolean("my_profile", true);
                        fragment.presentFragment(new ProfileActivity(args, null));
                    } else {
                        fragment.presentFragment(ProfileActivity.of(user.id));
                    }
                }
            };
        }

        public static ResolvedLink fromChat(String link, TLRPC.Chat chat) {
            if (chat == null) return null;
            return new ResolvedLink(link) {
                @Override
                public String getTitle() {
                    return chat.title;
                }
                @Override
                public String getSubtitle() {
                    return getString(R.string.AccDescrOpenChat);
                }
                @Override
                public boolean setImage(ImageReceiver imageReceiver) {
                    AvatarDrawable avatarDrawable = new AvatarDrawable();
                    avatarDrawable.setInfo(chat);
                    imageReceiver.setForUserOrChat(chat, avatarDrawable);
                    return true;
                }
                @Override
                public void open(BaseFragment fragment) {
                    fragment.presentFragment(ChatActivity.of(-chat.id));
                }
            };
        }


        public String getTitle() {
            return null;
        }

        public String getSubtitle() {
            return null;
        }

        public boolean setImage(ImageReceiver imageReceiver) {
            return false;
        }

        public void open(BaseFragment fragment) {

        }

    }


}
