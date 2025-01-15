package org.telegram.ui.bots;

import static android.graphics.PorterDuff.Mode.SRC_IN;
import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.getDataColumn;
import static org.telegram.messenger.LocaleController.getCurrencyExpDivider;
import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.checkerframework.checker.units.qual.A;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_bots;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedFileDrawable;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AttachableDrawable;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.ChatAttachAlert;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.PhotoViewerCaptionEnterView;
import org.telegram.ui.Components.Premium.PremiumFeatureBottomSheet;
import org.telegram.ui.Components.Premium.PremiumPreviewBottomSheet;
import org.telegram.ui.Components.Text;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PhotoViewer;
import org.telegram.ui.PremiumPreviewFragment;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.ArrayList;

public class SetupEmojiStatusSheet {

    public static void show(int currentAccount, TLRPC.User bot, long document_id, int duration, Utilities.Callback2<String, TLRPC.Document> whenDone) {
        if (whenDone == null) return;

        final TLRPC.Document emoji_document = AnimatedEmojiDrawable.findDocument(currentAccount, document_id);
        if (emoji_document != null) {
            show(currentAccount, bot, emoji_document, duration, err -> whenDone.run(err, emoji_document));
            return;
        }

        AnimatedEmojiDrawable.getDocumentFetcher(currentAccount).fetchDocument(document_id, document -> {
            AndroidUtilities.runOnUIThread(() -> {
                show(currentAccount, bot, document, duration, err -> whenDone.run(err, document));
            });
        });
    }

    public static void show(int currentAccount, TLRPC.User bot, TLRPC.Document document, int duration, Utilities.Callback<String> whenDone) {
        if (whenDone == null) return;
        if (document == null || document instanceof TLRPC.TL_documentEmpty) {
            whenDone.run("SUGGESTED_EMOJI_INVALID");
            return;
        }

        Context context = AndroidUtilities.findActivity(LaunchActivity.instance);
        if (context == null) context = ApplicationLoader.applicationContext;

        final int now = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
        final TLRPC.User currentUser = UserConfig.getInstance(currentAccount).getCurrentUser();

        final boolean[] sentDone = new boolean[1];
        final boolean[] setting = new boolean[1];

        final CharSequence message;
        if (duration > 0) {
            final int MINUTE = 60;
            final int HOUR = 60 * MINUTE;
            final int DAY = 24 * HOUR;
            int total = duration;
            final int d = total / DAY; total -= d * DAY;
            final int h = total / HOUR; total -= h * HOUR;
            final int m = Math.round((float) total / MINUTE);
            StringBuilder durationString = new StringBuilder();
            if (d > 0) {
                if (durationString.length() > 0) durationString.append(" ");
                durationString.append(LocaleController.formatPluralString("BotEmojiStatusSetRequestForDay", d));
            }
            if (h > 0) {
                if (durationString.length() > 0) durationString.append(" ");
                durationString.append(LocaleController.formatPluralString("BotEmojiStatusSetRequestForHour", h));
            }
            if (m > 0) {
                if (durationString.length() > 0) durationString.append(" ");
                durationString.append(LocaleController.formatPluralString("BotEmojiStatusSetRequestForMinute", m));
            }
            message = AndroidUtilities.replaceTags(LocaleController.formatString(R.string.BotEmojiStatusSetRequestFor, UserObject.getUserName(bot), durationString));
        } else {
            message = AndroidUtilities.replaceTags(LocaleController.formatString(R.string.BotEmojiStatusSetRequest, UserObject.getUserName(bot)));
        }

        AlertDialog dialog = new AlertDialog.Builder(context, null)
            .setTopImage(new UserEmojiStatusDrawable(currentUser, document), Theme.getColor(Theme.key_dialogTopBackground))
            .setMessage(message)
            .setPositiveButton(LocaleController.getString(R.string.BotEmojiStatusConfirm), (dialogInterface, i) -> {
                if (!UserConfig.getInstance(currentAccount).isPremium()) {
                    new PremiumFeatureBottomSheet(new BaseFragment() {
                        @Override
                        public int getCurrentAccount() {
                            return currentAccount;
                        }
                        @Override
                        public Context getContext() {
                            return AndroidUtilities.findActivity(LaunchActivity.instance);
                        }
                        @Override
                        public Activity getParentActivity() {
                            Activity activity = AndroidUtilities.findActivity(ApplicationLoader.applicationContext);
                            if (activity == null) activity = LaunchActivity.instance;
                            return activity;
                        }
                    }, PremiumPreviewFragment.PREMIUM_FEATURE_EMOJI_STATUS, false).show();
                    return;
                }
                setting[0] = true;

                TLRPC.TL_account_updateEmojiStatus req = new TLRPC.TL_account_updateEmojiStatus();
                if (duration > 0) {
                    TLRPC.TL_emojiStatusUntil status = new TLRPC.TL_emojiStatusUntil();
                    status.until = ConnectionsManager.getInstance(currentAccount).getCurrentTime() + duration;
                    status.document_id = document.id;
                    req.emoji_status = status;
                } else {
                    TLRPC.TL_emojiStatus status = new TLRPC.TL_emojiStatus();
                    status.document_id = document.id;
                    req.emoji_status = status;
                }
                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                    if (!(res instanceof TLRPC.TL_boolTrue)) {
                        if (!sentDone[0]) {
                            sentDone[0] = true;
                            whenDone.run("SERVER_ERROR");
                        }
                    } else {
                        TLRPC.User user = UserConfig.getInstance(currentAccount).getCurrentUser();
                        if (user != null) {
                            user.emoji_status = req.emoji_status;
                            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.userEmojiStatusUpdated, user);
                            MessagesController.getInstance(currentAccount).updateEmojiStatusUntilUpdate(user.id, user.emoji_status);
                        }
                        if (!sentDone[0]) {
                            sentDone[0] = true;
                            whenDone.run(null);
                        }
                    }
                }));
            })
            .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
            .create();
        dialog.show();
        dialog.setOnDismissListener(d -> {
            if (!setting[0] && !sentDone[0]) {
                sentDone[0] = true;
                whenDone.run("USER_DECLINED");
            }
        });
    }

    public static void askPermission(int currentAccount, long botId, Utilities.Callback2<Boolean, String> whenDone) {
        final TLRPC.User bot = MessagesController.getInstance(currentAccount).getUser(botId);
        final TLRPC.UserFull botFull = MessagesController.getInstance(currentAccount).getUserFull(botId);
        if (botFull == null) {
            MessagesController.getInstance(currentAccount).loadFullUser(bot, 0, true, (userFull2) -> {
                AndroidUtilities.runOnUIThread(() -> {
                    if (userFull2 == null) {
                        whenDone.run(false, "cancelled");
                        return;
                    }
                    askPermission(currentAccount, bot, userFull2, whenDone);
                });
            });
        } else {
            askPermission(currentAccount, bot, botFull, whenDone);
        }
    }

    public static void askPermission(int currentAccount, TLRPC.User bot, TLRPC.UserFull botFull, Utilities.Callback2<Boolean, String> whenDone) {
        if (whenDone == null) return;

        if (botFull.bot_can_manage_emoji_status) {
            whenDone.run(false, "allowed");
            return;
        }

        Context context = AndroidUtilities.findActivity(LaunchActivity.instance);
        if (context == null) context = ApplicationLoader.applicationContext;
        final Context finalContext = context;

        final TLRPC.User currentUser = UserConfig.getInstance(currentAccount).getCurrentUser();

        boolean[] sentDone = new boolean[1];
        boolean[] setting = new boolean[1];
        AlertDialog dialog = new AlertDialog.Builder(context, null)
            .setTopImage(new UserEmojiStatusDrawable(currentUser), Theme.getColor(Theme.key_dialogTopBackground))
            .setMessage(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.BotEmojiStatusPermissionRequest, UserObject.getUserName(bot), UserObject.getUserName(bot))))
            .setPositiveButton(LocaleController.getString(R.string.BotEmojiStatusPermissionAllow), (dialogInterface, i) -> {
                if (!UserConfig.getInstance(currentAccount).isPremium()) {
                    new PremiumFeatureBottomSheet(new BaseFragment() {
                        @Override
                        public int getCurrentAccount() {
                            return currentAccount;
                        }
                        @Override
                        public Context getContext() {
                            return AndroidUtilities.findActivity(LaunchActivity.instance);
                        }
                        @Override
                        public Activity getParentActivity() {
                            Activity activity = AndroidUtilities.findActivity(ApplicationLoader.applicationContext);
                            if (activity == null) activity = LaunchActivity.instance;
                            return activity;
                        }
                    }, PremiumPreviewFragment.PREMIUM_FEATURE_EMOJI_STATUS, false).show();
                    if (!setting[0] && !sentDone[0]) {
                        sentDone[0] = true;
                        whenDone.run(true, "cancelled");
                    }
                    return;
                }
                setting[0] = true;
                saveAccessRequested(finalContext, currentAccount, bot.id);

                TL_bots.toggleUserEmojiStatusPermission req = new TL_bots.toggleUserEmojiStatusPermission();
                req.bot = MessagesController.getInstance(currentAccount).getInputUser(bot);
                req.enabled = true;
                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                    if (!(res instanceof TLRPC.TL_boolTrue)) {
                        if (!sentDone[0]) {
                            sentDone[0] = true;
                            whenDone.run(true, "cancelled");
                        }
                    } else {
                        botFull.bot_can_manage_emoji_status = true;
                        if (!sentDone[0]) {
                            sentDone[0] = true;
                            whenDone.run(true, "allowed");
                        }
                    }
                }));
            })
            .setNegativeButton(LocaleController.getString(R.string.BotEmojiStatusPermissionDecline), null)
            .create();
        dialog.show();
        dialog.setOnDismissListener(d -> {
            if (!setting[0] && !sentDone[0]) {
                sentDone[0] = true;
                saveAccessRequested(finalContext, currentAccount, bot.id);
                whenDone.run(true, "cancelled");
            }
        });
    }

    public static class UserEmojiStatusDrawable extends Drawable implements AttachableDrawable, NotificationCenter.NotificationCenterDelegate {

        private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint backgroundPaint2 = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final ImageReceiver userImageReceiver = new ImageReceiver();
        private final ImageReceiver statusImageReceiver = new ImageReceiver();
        private int currentStatus = 1;
        private final AnimatedEmojiDrawable[] emojis = new AnimatedEmojiDrawable[2];
        private final Text text;
        private final RectF rect = new RectF();
        private final boolean highlight;
        private final AnimatedFloat animatedSwap = new AnimatedFloat(this::invalidateSelf, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);

        public UserEmojiStatusDrawable(TLRPC.User user) {
            this.highlight = false;

            backgroundPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            backgroundPaint2.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            backgroundPaint2.setShadowLayer(dp(2.33f), 0, dp(2), Theme.multAlpha(0xFF000000, .18f));

            AvatarDrawable avatarDrawable = new AvatarDrawable();
            avatarDrawable.setInfo(user);
            userImageReceiver.setForUserOrChat(user, avatarDrawable);
            userImageReceiver.setRoundRadius(dp(16));

//            final TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(emojiStatus.thumbs, 120);
//            final SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(emojiStatus.thumbs, Theme.key_windowBackgroundGray, 0.35f);
//            statusImageReceiver.setImage(
//                    ImageLocation.getForDocument(emojiStatus), "120_120",
//                    ImageLocation.getForDocument(photoSize, emojiStatus), "120_120",
//                    svgThumb, 0, null, null, 0
//            );
            setRandomStatus();

            text = new Text(UserObject.getUserName(user), 14);
        }

        @Override
        public void didReceivedNotification(int id, int account, Object... args) {
            if (id == NotificationCenter.groupStickersDidLoad) {
                if (waitingForStatuses && attached) {
                    waitingForStatuses = false;
                    setRandomStatus();
                }
            }
        }

        private boolean waitingForStatuses;
        public void setRandomStatus() {
            final TLRPC.TL_messages_stickerSet defaultSet = MediaDataController.getInstance(UserConfig.selectedAccount).getStickerSet(new TLRPC.TL_inputStickerSetEmojiDefaultStatuses(), false);
            if (defaultSet == null || defaultSet.documents.isEmpty()) {
                waitingForStatuses = true;
                return;
            }
            final int randomIndex = (int) Math.floor(Math.random() * defaultSet.documents.size());
            final TLRPC.Document status = defaultSet.documents.get(randomIndex);

            currentStatus = 1 - currentStatus;
            if (emojis[currentStatus] != null) {
                emojis[currentStatus].removeView(view);
            }
            emojis[currentStatus] = AnimatedEmojiDrawable.make(UserConfig.selectedAccount, AnimatedEmojiDrawable.CACHE_TYPE_ALERT_EMOJI_STATUS, status);
            emojis[currentStatus].setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_featuredStickers_addButton), SRC_IN));
            if (attached && emojis[currentStatus] != null) {
                emojis[currentStatus].addView(view);
            }

            AndroidUtilities.runOnUIThread(() -> {
                if (!attached) return;
                setRandomStatus();
            }, 2500);
        }

        public UserEmojiStatusDrawable(TLRPC.User user, TLRPC.Document emojiStatus) {
            this.highlight = true;

            backgroundPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            backgroundPaint2.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            backgroundPaint2.setShadowLayer(dp(2.33f), 0, dp(2), Theme.multAlpha(0xFF000000, .18f));

            AvatarDrawable avatarDrawable = new AvatarDrawable();
            avatarDrawable.setInfo(user);
            userImageReceiver.setForUserOrChat(user, avatarDrawable);
            userImageReceiver.setRoundRadius(dp(16));

            final TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(emojiStatus.thumbs, 120);
            final SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(emojiStatus.thumbs, Theme.key_windowBackgroundGray, 0.35f);
            statusImageReceiver.setImage(
                ImageLocation.getForDocument(emojiStatus), "120_120",
                ImageLocation.getForDocument(photoSize, emojiStatus), "120_120",
                svgThumb, 0, null, null, 0
            );

            text = new Text(UserObject.getUserName(user), 14);
        }

        private boolean attached;
        private View view;

        @Override
        public void onAttachedToWindow(ImageReceiver parent) {
            attached = true;
            userImageReceiver.onAttachedToWindow();
            statusImageReceiver.onAttachedToWindow();
            NotificationCenter.getInstance(UserConfig.selectedAccount).addObserver(this, NotificationCenter.recentEmojiStatusesUpdate);
            if (emojis[0] != null) {
                emojis[0].addView(view);
            }
            if (emojis[1] != null) {
                emojis[1].addView(view);
            }
        }

        @Override
        public void onDetachedFromWindow(ImageReceiver parent) {
            attached = false;
            userImageReceiver.onDetachedFromWindow();
            statusImageReceiver.onDetachedFromWindow();
            NotificationCenter.getInstance(UserConfig.selectedAccount).removeObserver(this, NotificationCenter.recentEmojiStatusesUpdate);
            if (emojis[0] != null) {
                emojis[0].removeView(view);
            }
            if (emojis[1] != null) {
                emojis[1].removeView(view);
            }
        }

        @Override
        public void setParent(View view) {
            this.view = view;
            statusImageReceiver.setParentView(view);
            userImageReceiver.setParentView(view);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            final Rect bounds = getBounds();

            final float width = dp(32 + 6 + (highlight ? 48 : 28) + 6.66f) + text.getCurrentWidth();
            final float height = dp(32);

            rect.set(
                bounds.centerX() - width / 2f,
                bounds.centerY() - height / 2f,
                bounds.centerX() + width / 2f,
                bounds.centerY() + height / 2f
            );
            canvas.drawRoundRect(
                rect,
                height / 2f, height / 2f,
                backgroundPaint
            );
            userImageReceiver.setImageCoords(rect.left, rect.top, dp(32), dp(32));
            userImageReceiver.draw(canvas);
            text.draw(canvas, rect.left + dp(32 + 4), rect.centerY(), Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), 1.0f);

            if (highlight) {
                final float cx = rect.right - dp(6.66f + 16), r = dp(24);
                canvas.drawCircle(cx, rect.centerY(), r, backgroundPaint2);

                statusImageReceiver.setImageCoords(cx - dp(16), rect.centerY() - dp(16), dp(32), dp(32));
                statusImageReceiver.draw(canvas);
            } else {
                float index = animatedSwap.set(currentStatus);
                canvas.save();
                canvas.translate((int) (rect.right - dp(6.66f + 24)), (int) (rect.centerY() - dp(12)));
                if (index < 1) {
                    AnimatedEmojiDrawable emoji = emojis[0];
                    if (emoji != null) {
                        canvas.save();
                        canvas.translate(0, (currentStatus == 0 ? -1 : +1) * dp(9) * index);
                        final float s = .6f + .4f * (1.0f - index);
                        canvas.scale(s, s, dp(12), dp(12));
                        emoji.setBounds(0, 0, dp(24), dp(24));
                        emoji.setAlpha((int) (0xFF * (1.0f - index)));
                        emoji.draw(canvas);
                        canvas.restore();
                    }
                }
                if (index > 0) {
                    AnimatedEmojiDrawable emoji = emojis[1];
                    if (emoji != null) {
                        canvas.save();
                        canvas.translate(0, (currentStatus == 1 ? -1 : +1) * dp(9) * (1.0f - index));
                        final float s = .6f + .4f * index;
                        canvas.scale(s, s, dp(12), dp(12));
                        emoji.setBounds(0, 0, dp(24), dp(24));
                        emoji.setAlpha((int) (0xFF * index));
                        emoji.draw(canvas);
                        canvas.restore();
                    }
                }
                canvas.restore();
            }
        }

        @Override
        public void setAlpha(int alpha) {

        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {

        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSPARENT;
        }
    }

    public static final String PREF = "botemojistatus_";
    public static boolean getAccessRequested(Context context, int currentAccount, long botId) {
        if (context == null) return false;
        SharedPreferences prefs = context.getSharedPreferences(PREF + currentAccount, Activity.MODE_PRIVATE);
        return prefs.getBoolean("requested_" + botId, false);
    }

    public static void saveAccessRequested(Context context, int currentAccount, long botId) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREF + currentAccount, Activity.MODE_PRIVATE);
        prefs.edit().putBoolean("requested_" + botId, true).apply();
    }

    public static void clear() {
        Context context = ApplicationLoader.applicationContext;
        if (context == null) return;
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; ++i) {
            final SharedPreferences prefs = context.getSharedPreferences(PREF + i, Activity.MODE_PRIVATE);
            prefs.edit().clear().apply();
        }
    }

}
