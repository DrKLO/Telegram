package org.telegram.messenger.video;

import static org.telegram.messenger.AndroidUtilities.checkAndroidTheme;
import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.aspectj.lang.annotation.AdviceName;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.Premium.PremiumFeatureBottomSheet;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.Text;
import org.telegram.ui.DarkBlueThemeResourcesProvider;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PremiumPreviewFragment;
import org.telegram.ui.ReportBottomSheet;
import org.telegram.ui.RevenueSharingAdsInfoBottomSheet;
import org.telegram.ui.Stories.DarkThemeResourceProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;

public class VideoAds {

    private final int currentAccount;
    private final long dialogId;
    private final int msg_id;
    private BulletinFactory bulletinFactory;

    private int start_delay, between_delay;
    private final ArrayList<TLRPC.TL_sponsoredMessage> ads = new ArrayList<>();
    private long lastTime = 0;
    private boolean first = true;

    private static class VideoAdsLocation {
        public VideoAdsLocation(int currentAccount,
                                long dialogId) {
            this.currentAccount = currentAccount;
            this.dialogId = dialogId;
        }
        int currentAccount;
        long dialogId;
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            VideoAdsLocation that = (VideoAdsLocation) o;
            return currentAccount == that.currentAccount && dialogId == that.dialogId;
        }
        @Override
        public int hashCode() {
            return Objects.hash(currentAccount, dialogId);
        }
    }

//    private static LruCache<VideoAdsLocation, VideoAds> cached = new LruCache<>(3);
    private static HashMap<VideoAdsLocation, VideoAds> cached = new HashMap<>();
    public static VideoAds make(
        int currentAccount,
        long dialogId,
        int msg_id,
        BulletinFactory bulletinFactory
    ) {
        final VideoAdsLocation key = new VideoAdsLocation(currentAccount, dialogId);
        VideoAds ads = cached.get(key);
        if (ads == null || (ads.msg_id != msg_id || System.currentTimeMillis() - ads.lastTime > 3 * 60 * 1000) && ads.ads.isEmpty()) {
            cached.put(key, ads = new VideoAds(currentAccount, dialogId, msg_id, bulletinFactory));
        }
        ads.init(bulletinFactory);
        return ads;
    }

    private VideoAds(
        int currentAccount,
        long dialogId,
        int msg_id,
        BulletinFactory bulletinFactory
    ) {
        this.currentAccount = currentAccount;
        this.dialogId = dialogId;
        this.msg_id = msg_id;
        this.lastTime = System.currentTimeMillis();
        init(bulletinFactory);
    }

    public boolean videoWasPlaying;
    private boolean lastPopupShown;
    private Runnable onPopupCallback;
    public void setPauseOnPopupCallback(Runnable onPopupCallback) {
        this.onPopupCallback = onPopupCallback;
    }
    private void checkPopupShownCallback() {
        if (lastPopupShown != isPopupShown()) {
            lastPopupShown = isPopupShown();
            if (onPopupCallback != null) {
                onPopupCallback.run();
            }
        }
    }
    public boolean isPopupShown() {
        return currentMenu != null && currentMenu.isShown() || premiumSheet != null && premiumSheet.isShown();
    }

    private void init(BulletinFactory bulletinFactory) {
        this.bulletinFactory = bulletinFactory;
        if (currentBulletinPassedTime <= 0) {
            this.lastTime = System.currentTimeMillis();
            if (waitingPaused) {
                waitingTimeSince = System.currentTimeMillis();
            }
            this.first = true;
        }
        if (!loaded) {
            load();
        } else {
            schedule();
        }
    }

    private int requestId;
    private boolean loading, loaded;
    private void load() {
        if (loading || loaded) return;

        if (UserConfig.getInstance(currentAccount).isPremium() && MessagesController.getInstance(currentAccount).isSponsoredDisabled()) {
            return;
        }

        loading = true;

        TLRPC.TL_messages_getSponsoredMessages req = new TLRPC.TL_messages_getSponsoredMessages();
        req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
        req.flags |= 1;
        req.msg_id = msg_id;
        requestId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            if (!loading) return;

            if (res instanceof TLRPC.TL_messages_sponsoredMessages) {
                final TLRPC.TL_messages_sponsoredMessages r = (TLRPC.TL_messages_sponsoredMessages) res;
                MessagesController.getInstance(currentAccount).putUsers(r.users, false);
                MessagesController.getInstance(currentAccount).putChats(r.chats, false);
                ads.addAll(r.messages);
                start_delay = r.start_delay;
                between_delay = r.between_delay;
            }

            loaded = true;
            loading = false;

            schedule();
        }));
    }

    private void schedule() {
        AndroidUtilities.cancelRunOnUIThread(showRunnable);
        if (!loaded || ads.isEmpty()) return;
        final int delay = first ? start_delay : between_delay;
        final long timePassed = System.currentTimeMillis() - lastTime;
        AndroidUtilities.runOnUIThread(showRunnable, Math.max(0, delay * 1000L - timePassed));
    }

    private long waitingTimeSince;
    private boolean waitingPaused;
    public void setWaitingPaused(boolean paused) {
        if (waitingPaused == paused) return;
        waitingPaused = paused;
        AndroidUtilities.cancelRunOnUIThread(showRunnable);
        if (paused) {
            waitingTimeSince = System.currentTimeMillis();
        } else {
            final long pausedTime = System.currentTimeMillis() - waitingTimeSince;
            lastTime += pausedTime;
            if (bulletin == null) {
                schedule();
            }
        }
    }

    private Bulletin bulletin;
    private long bulletinShowTime;
    private long currentBulletinPassedTime;
    private final Runnable showRunnable = this::show;

    private ItemOptions currentMenu;
    private float currentMenuTranslationY;

    private void show() {
        if (ads.isEmpty()) return;
        final TLRPC.TL_sponsoredMessage ad = ads.get(0);
        final long showTime = System.currentTimeMillis() - currentBulletinPassedTime;
        bulletinShowTime = showTime;

        if (bulletin != null) {
            bulletin.hide();
            bulletin = null;
        }

        final Context context = bulletinFactory.getContext();
        final Theme.ResourcesProvider resourcesProvider = bulletinFactory.getResourcesProvider();

        final AdLayout layout = new AdLayout(context, resourcesProvider) {
            @Override
            public void updatePosition() {
                super.updatePosition();
                if (currentMenu != null) {
                    currentMenu.setTranslationY(getTranslationY() - currentMenuTranslationY);
                }
            }
        };

        layout.titleTextView.setText(ad.title);
        layout.titleTextView.setRightDrawable(new AdOptionsDrawable(bulletinFactory.getContext(), Theme.getColor(Theme.key_featuredStickers_addButton, bulletinFactory.getResourcesProvider())));
        layout.subtitleTextView.setText(ad.message);
        boolean hasMedia = false;
        if (ad.media != null) {
            if (ad.media.document != null) {
                TLRPC.PhotoSize thumbSize = FileLoader.getClosestPhotoSizeWithSize(ad.media.document.thumbs, 48);
                layout.imageView.setImage(
                    ImageLocation.getForDocument(ad.media.document), "48_48",
                    ImageLocation.getForDocument(thumbSize, ad.media.document), "48_48",
                    null, 0, 0, null
                );
            } else if (ad.media.photo != null) {
                TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(ad.media.photo.sizes, 48, true, null, true);
                TLRPC.PhotoSize thumbSize = FileLoader.getClosestPhotoSizeWithSize(ad.media.photo.sizes, 48, true, photoSize, false);
                layout.imageView.setImage(
                    ImageLocation.getForPhoto(photoSize, ad.media.photo), "48_48",
                    ImageLocation.getForPhoto(thumbSize, ad.media.photo), "48_48",
                    null, 0, 0, null
                );
            }
            hasMedia = true;
        } else if (ad.photo != null) {
            TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(ad.photo.sizes, 48, true, null, true);
            TLRPC.PhotoSize thumbSize = FileLoader.getClosestPhotoSizeWithSize(ad.photo.sizes, 48, true, photoSize, false);
            layout.imageView.setImage(
                ImageLocation.getForPhoto(photoSize, ad.photo), "48_48",
                ImageLocation.getForPhoto(thumbSize, ad.photo), "48_48",
                null, 0, 0, null
            );
            hasMedia = true;
        }
        if (!hasMedia) {
            layout.hideImage();
        }
        final CloseDrawable closeDrawable = new CloseDrawable(layout.buttonView, ad.min_display_duration, ad.max_display_duration, currentBulletinPassedTime);
        closeDrawable.setColor(Theme.getColor(Theme.key_featuredStickers_addButton, bulletinFactory.getResourcesProvider()));
        layout.buttonView.setImageDrawable(closeDrawable);
        layout.buttonView.setOnClickListener(v -> {
            if (closeDrawable.isCrossAvailable()) {
                if (bulletin != null) {
                    bulletin.hide();
                }
            } else {
                if (UserConfig.getInstance(currentAccount).isPremium()) {
                    if (bulletin != null) {
                        bulletin.hide();
                        bulletin = null;
                    }
                    MessagesController.getInstance(currentAccount).disableAds(true);
                    bulletinFactory
                        .createAdReportedBulletin(LocaleController.getString(R.string.AdHidden))
                        .show();
                } else {
                    showPremium();
                }
            }
        });

        final Bulletin thisBulletin = bulletin = bulletinFactory.create(layout, ad.max_display_duration * 1000);
        bulletin.setCanHide(bulletin.setCanHideOnShow = false);
        Runnable canHideCallback = () -> {
            if (bulletin != null && bulletin == thisBulletin) {
                bulletin.setDuration((ad.max_display_duration - ad.min_display_duration) * 1000);
                bulletin.setCanHide(true);
            }
        };
        final long[] timeWhilePaused = new long[1];
        final long[] lastPausedTime = new long[1];
        final boolean[] paused = new boolean[1];
        Utilities.Callback<Boolean> setPaused = newPaused -> {
            if (bulletin == null || bulletin != thisBulletin || newPaused == paused[0]) return;
            paused[0] = newPaused;
            closeDrawable.setPaused(paused[0]);
            if (paused[0]) {
                bulletin.setCanHide(false);
                lastPausedTime[0] = System.currentTimeMillis();
                AndroidUtilities.cancelRunOnUIThread(canHideCallback);
            } else {
                AndroidUtilities.cancelRunOnUIThread(canHideCallback);
                timeWhilePaused[0] += System.currentTimeMillis() - lastPausedTime[0];
                final long passedTime = System.currentTimeMillis() - showTime - timeWhilePaused[0];
                final long min = ad.min_display_duration * 1000L - passedTime;
                final long max = ad.max_display_duration * 1000L - passedTime;
                if (max <= 0) {
                    if (bulletin != null) {
                        bulletin.hide();
                        bulletin = null;
                    }
                } else if (min <= 0) {
                    bulletin.setDuration((int) max);
                    bulletin.setCanHide(true);
                } else {
                    AndroidUtilities.runOnUIThread(canHideCallback, min);
                }
            }
        };
        AndroidUtilities.runOnUIThread(canHideCallback, ad.min_display_duration * 1000L);
        final boolean[] caughtHidden = new boolean[1];
        bulletin.hideAfterBottomSheet = false;
        bulletin.setOnHideListener(() -> {
            if (bulletin != null && bulletin == thisBulletin) {
                if (caughtHidden[0]) return;
                caughtHidden[0] = true;
                if (currentMenu != null) {
                    currentMenu.dismiss();
                    currentMenu = null;
                }
                bulletin = null;
                currentBulletinPassedTime = 0;
                lastTime = System.currentTimeMillis();
                if (waitingPaused) {
                    waitingTimeSince = System.currentTimeMillis();
                }
                if (!ads.isEmpty()) {
                    ads.remove(0);
                }
                first = false;
                schedule();
            }
        });
        layout.titleTextView.setRightDrawableOnClick(v -> {
            if (bulletin == null || bulletin != thisBulletin) return;
            ViewGroup container = null;
            try {
                container = (ViewGroup) bulletin.getLayout().getParent().getParent();
            } catch (Exception e) {}
            if (container == null) return;

            final Theme.ResourcesProvider resourcesProvider1 = new DarkBlueThemeResourcesProvider();
            ItemOptions o = ItemOptions.makeOptions(container, resourcesProvider1, bulletin.getLayout(), true, false);
            o.setSwipebackGravity(true, true);
            o.setScaleOut(true);
            o.setDimAlpha(0);
            o.setDrawScrim(false);
            o.setDismissWithButtons(false);

            if (ad.sponsor_info != null || ad.additional_info != null || ad.url != null && !ad.url.startsWith("https://" + MessagesController.getInstance(currentAccount).linkPrefix)) {
                ItemOptions info = o.makeSwipeback();

                ActionBarMenuSubItem backCell = new ActionBarMenuSubItem(context, true, false, resourcesProvider1);
                backCell.setItemHeight(44);
                backCell.setTextAndIcon(getString(R.string.Back), R.drawable.msg_arrow_back);
                backCell.getTextView().setPadding(LocaleController.isRTL ? 0 : AndroidUtilities.dp(40), 0, LocaleController.isRTL ? AndroidUtilities.dp(40) : 0, 0);
                backCell.setOnClickListener(v1 -> o.closeSwipeback());
                info.addView(backCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

                info.addView(new ActionBarPopupWindow.GapView(context, resourcesProvider1), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 8));

                ArrayList<View> sections = new ArrayList<>();

                if (ad.url != null && !TextUtils.equals(AndroidUtilities.getHostAuthority(ad.url), MessagesController.getInstance(currentAccount).linkPrefix)) {
                    TextView textView = new TextView(context);
                    textView.setTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider1));
                    textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                    textView.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(10), AndroidUtilities.dp(18), AndroidUtilities.dp(10));
                    textView.setMaxWidth(AndroidUtilities.dp(300));
                    Uri uri = Uri.parse(ad.url);
                    textView.setText(Browser.replaceHostname(uri, Browser.IDN_toUnicode(uri.getHost()), null));
                    textView.setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_dialogButtonSelector, resourcesProvider1), 0, ad.additional_info == null ? 6 : 0));
                    textView.setOnClickListener(e -> {
                        o.dismiss();
                        logSponsoredClicked(ad);
                        Browser.openUrl(context, Uri.parse(ad.url), true, false, false, null, null, false, MessagesController.getInstance(currentAccount).sponsoredLinksInappAllow, false);
                    });
                    textView.setOnLongClickListener(e -> {
                        AndroidUtilities.addToClipboard(ad.url);
                        return true;
                    });
                    sections.add(textView);
                }

                if (ad.sponsor_info != null) {
                    TextView textView = new TextView(context);
                    textView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem, resourcesProvider1));
                    textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                    textView.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(10), AndroidUtilities.dp(18), AndroidUtilities.dp(10));
                    textView.setMaxWidth(AndroidUtilities.dp(300));
                    textView.setText(ad.sponsor_info);
                    textView.setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_dialogButtonSelector, resourcesProvider1), 0, ad.additional_info == null ? 6 : 0));
                    textView.setOnClickListener(e -> {
                        if (AndroidUtilities.addToClipboard(ad.sponsor_info)) {
//                            BulletinFactory.of(Bulletin.BulletinWindow.make(context), resourcesProvider1).createCopyBulletin(LocaleController.getString(R.string.TextCopied)).show();
                        }
                    });
                    sections.add(textView);
                }

                if (ad.additional_info != null) {
                    TextView textView = new TextView(context);
                    textView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem, resourcesProvider1));
                    textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                    textView.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(10), AndroidUtilities.dp(18), AndroidUtilities.dp(10));
                    textView.setMaxWidth(AndroidUtilities.dp(300));
                    textView.setText(ad.additional_info);
                    textView.setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_dialogButtonSelector, resourcesProvider), 0, 6));
                    textView.setOnClickListener(e -> {
                        if (AndroidUtilities.addToClipboard(ad.additional_info)) {
//                            BulletinFactory.of(Bulletin.BulletinWindow.make(activityContext), resourcesProvider1).createCopyBulletin(LocaleController.getString(R.string.TextCopied)).show();
                        }
                    });
                    sections.add(textView);
                }

                for (int i = 0; i < sections.size(); ++i) {
                    View section = sections.get(i);
                    if (i > 0) {
                        FrameLayout separator = new FrameLayout(context);
                        separator.setBackgroundColor(Theme.getColor(Theme.key_divider, resourcesProvider1));
                        LinearLayout.LayoutParams params = LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1);
                        params.height = 1;
                        info.addView(separator, params);
                    }
                    info.addView(section, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                }
                o.add(R.drawable.msg_channel, getString(R.string.SponsoredMessageSponsorReportable), () -> o.openSwipeback(info));
            }
            if (!UserConfig.getInstance(currentAccount).isPremium() && !MessagesController.getInstance(currentAccount).premiumFeaturesBlocked() && !ad.can_report) {
                o.add(R.drawable.msg_block2, getString(R.string.HideAd), () -> {
                    if (UserConfig.getInstance(currentAccount).isPremium()) {
                        o.dismiss();
                        if (bulletin != null) {
                            bulletin.setCanHide(true);
                            bulletin.hide();
                        }
                        bulletinFactory
                            .createAdReportedBulletin(LocaleController.getString(R.string.AdHidden))
                            .show();
                        MessagesController.getInstance(currentAccount).disableAds(true);
                    } else {
                        showPremium();
                    }
                });
            }
            if (ad.can_report) {
                o.add(R.drawable.msg_info, getString(R.string.AboutRevenueSharingAds), () -> {
                    RevenueSharingAdsInfoBottomSheet.showAlert(context, null, false, resourcesProvider1);
                });
                o.add(R.drawable.msg_block2, getString(R.string.ReportAd), () -> {
                    ReportBottomSheet.openSponsored(currentAccount, context, dialogId, ad, bulletinFactory, new DarkBlueThemeResourcesProvider(), this::showPremium, o::dismiss);
                });
                if (!MessagesController.getInstance(currentAccount).premiumFeaturesBlocked()) {
                    o.addGap();
                    o.add(R.drawable.msg_cancel, getString(R.string.RemoveAds), () -> {
                        if (UserConfig.getInstance(currentAccount).isPremium()) {
                            o.dismiss();
                            if (bulletin != null) {
                                bulletin.setCanHide(true);
                                bulletin.hide();
                            }
                            bulletinFactory
                                .createAdReportedBulletin(LocaleController.getString(R.string.AdHidden))
                                .show();
                            MessagesController.getInstance(currentAccount).disableAds(true);
                        } else {
                            showPremium();
                        }
                    });
                }
            }
            if (o.getItemsCount() <= 0)
                return;

            currentMenu = o;
            currentMenuTranslationY = layout.getTranslationY();
            setPaused.run(true);
            o.setOnDismiss(() -> {
                setPaused.run(false);
                currentMenu = null;
                checkPopupShownCallback();
            });
            o.show();
            checkPopupShownCallback();
        });
        bulletin.setOnClickListener(v -> {
            logSponsoredClicked(ad);
            Browser.openUrl(v.getContext(), Uri.parse(ad.url), true, false, false, null, null, false, MessagesController.getInstance(UserConfig.selectedAccount).sponsoredLinksInappAllow, false);
        });
        bulletin.show();
        logSponsoredShown(ad);
    }

    public void stop() {
        if (bulletin != null) {
            currentBulletinPassedTime = System.currentTimeMillis() - bulletinShowTime;
            if (!ads.isEmpty()) {
                final TLRPC.TL_sponsoredMessage ad = ads.get(0);
                if (currentBulletinPassedTime > ad.min_display_duration * 1000L) {
                    currentBulletinPassedTime = 0;
                    ads.remove(0);
                    first = false;
                }
            }
            bulletin.hide();
            bulletin = null;
        } else {
            currentBulletinPassedTime = 0;
        }
        if (currentMenu != null) {
            currentMenu.dismiss();
            currentMenu = null;
        }
        bulletin = null;
        if (loading) {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(requestId, true);
            requestId = 0;
            loading = false;
        }
        AndroidUtilities.cancelRunOnUIThread(showRunnable);
        setWaitingPaused(true);
    }

    public static class AdOptionsDrawable extends Drawable {

        public final int color;
        public final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        public final Text text = new Text(LocaleController.getString(R.string.SponsoredMessageAd), 11, AndroidUtilities.bold());
        public final Drawable icon;

        public AdOptionsDrawable(Context context, int color) {
            this.color = color;
            icon = context.getResources().getDrawable(R.drawable.ic_ab_other).mutate();
            icon.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            AndroidUtilities.rectTmp.set(getBounds());
            AndroidUtilities.rectTmp.left += dp(4);
            backgroundPaint.setColor(Theme.multAlpha(color, .20f * alpha));
            canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.rectTmp.height(), AndroidUtilities.rectTmp.height(), backgroundPaint);

            text.draw(canvas, AndroidUtilities.rectTmp.left + dp(5), AndroidUtilities.rectTmp.centerY(), color, alpha);

            icon.setBounds(getBounds().right - dp(1.66f + 11.33f), getBounds().centerY() - dp(11.33f/2), getBounds().right - dp(1.66f), getBounds().centerY() + dp(11.33f/2));
            icon.setAlpha((int) (0xFF * alpha));
            icon.draw(canvas);
        }

        private float alpha = 1.0f;
        @Override
        public void setAlpha(int alpha) {
            this.alpha = alpha / 255f;
        }
        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {}
        @Override
        public int getOpacity() {
            return PixelFormat.TRANSPARENT;
        }
        @Override
        public int getIntrinsicHeight() {
            return dp(16);
        }

        @Override
        public int getIntrinsicWidth() {
            return (int) (dp(4) + text.getWidth() + dp(18));
        }
    }

    public static class CloseDrawable extends Drawable {

        private final View parentView;
        private final AnimatedTextView.AnimatedTextDrawable timer = new AnimatedTextView.AnimatedTextDrawable(false, true, true);
        private final AnimatedFloat showCrossAnimated;
        private final AnimatedFloat showTimerAnimated;
        private final AnimatedFloat timerScaleAnimated;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final long startTime;
        private final long min_display_duration;
        private final long max_display_duration;

        public CloseDrawable(View view, int min_display_duration, int max_display_duration, long alreadyPassedTime) {
            this.parentView = view;
            this.startTime = System.currentTimeMillis() - alreadyPassedTime;
            this.min_display_duration = min_display_duration * 1000L;
            this.max_display_duration = max_display_duration * 1000L;

            this.paint.setStyle(Paint.Style.STROKE);
            this.paint.setStrokeCap(Paint.Cap.ROUND);
            this.paint.setStrokeJoin(Paint.Join.ROUND);
            this.paint.setColor(0xFFFFFFFF);

            this.timer.setCallback(view);
            this.timer.setGravity(Gravity.CENTER);
            this.timer.setTextSize(dp(12));
            this.timer.setTypeface(AndroidUtilities.getTypeface("fonts/num.otf"));
            this.timer.setOverrideFullWidth(AndroidUtilities.displaySize.x);
            this.timer.setTextColor(0xFFFFFFFF);

            this.showCrossAnimated = new AnimatedFloat(parentView, 0, 420, CubicBezierInterpolator.EASE_OUT_QUINT);
            this.showTimerAnimated = new AnimatedFloat(parentView, 0, 420, CubicBezierInterpolator.EASE_OUT_QUINT);
            this.timerScaleAnimated = new AnimatedFloat(parentView, 0, 420, CubicBezierInterpolator.EASE_OUT_QUINT);
        }

        public boolean isCrossAvailable() {
            final long now = paused ? pausedTime : System.currentTimeMillis() - minusTime;
            final long passed = now - startTime;
            return passed > min_display_duration;
        }

        private long minusTime;
        @Override
        public void draw(@NonNull Canvas canvas) {
            final float cx = getBounds().centerX();
            final float cy = getBounds().centerY();

            final long now = (paused ? pausedTime : System.currentTimeMillis()) - minusTime;
            final long passed = now - startTime;
            final long timeLeft = Math.max(0, min_display_duration - passed);
            final float timeLeftT = timeLeft / (float) min_display_duration;

            final boolean showTimer = passed < min_display_duration;
            final float timerAlpha = showTimerAnimated.set(showTimer);

            final String timerText = "" + (int) Math.ceil(timeLeft / 1000.0);
            final float timerScale = timerScaleAnimated.set(timerText.length() >= 3 ? .825f : timerText.length() >= 2 ? .875f : 1.0f);
            canvas.save();
            canvas.scale(timerScale, timerScale, cx, cy);
            timer.setText(timerText);
            timer.setBounds(cx - 1, cy - 1, cx + 1, cy + 1);
            timer.setAlpha((int) (alpha * timerAlpha));
            timer.draw(canvas);
            canvas.restore();

            paint.setAlpha((int) (alpha * timerAlpha));
            paint.setStrokeWidth(dp(2));
            AndroidUtilities.rectTmp.set(cx - dp(9), cy - dp(9), cx + dp(9), cy + dp(9));
            canvas.drawArc(AndroidUtilities.rectTmp, -90, -360 * timeLeftT, false, paint);

            final float crossAlpha = showCrossAnimated.set(360 * (1f - timeLeftT) > 75f);
            final float crossCx = lerp(cx, cx + dp(8), timerAlpha);
            final float crossCy = lerp(cy, cy - dp(8), timerAlpha);
            final float crossR = lerp(dp(5), dp(3), timerAlpha) * lerp(0.35f, 1.0f, crossAlpha);

            paint.setAlpha((int) (alpha * crossAlpha));
            canvas.drawLine(crossCx - crossR, crossCy - crossR, crossCx + crossR, crossCy + crossR, paint);
            canvas.drawLine(crossCx - crossR, crossCy + crossR, crossCx + crossR, crossCy - crossR, paint);

            if (timerAlpha > 0) {
                parentView.invalidate();
            }
        }

        private long pausedTime;
        private boolean paused = false;
        public void setPaused(boolean paused) {
            if (this.paused == paused) return;
            this.paused = paused;
            if (paused) {
                pausedTime = System.currentTimeMillis();
            } else {
                final long timeWhilePaused = System.currentTimeMillis() - pausedTime;
                minusTime += timeWhilePaused;
            }
        }

        private int alpha = 0xFF;
        @Override
        public void setAlpha(int alpha) {
            this.alpha = alpha;
        }
        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            timer.setColorFilter(colorFilter);
            paint.setColorFilter(colorFilter);
        }
        public void setColor(int color) {
            timer.setTextColor(color);
            paint.setColor(color);
        }
        @Override
        public int getOpacity() {
            return PixelFormat.TRANSPARENT;
        }

        @Override
        public int getIntrinsicWidth() {
            return dp(24);
        }
        @Override
        public int getIntrinsicHeight() {
            return dp(24);
        }
    }

    private PremiumFeatureBottomSheet premiumSheet;
    private void showPremium() {
        if (premiumSheet != null) {
            premiumSheet.dismiss();
            premiumSheet = null;
        }
        final BaseFragment dummyFragment = new BaseFragment() {
            @Override
            public int getCurrentAccount() {
                return VideoAds.this.currentAccount;
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
        };
        final PremiumFeatureBottomSheet sheet = new PremiumFeatureBottomSheet(dummyFragment, PremiumPreviewFragment.PREMIUM_FEATURE_ADS, true);
        premiumSheet = sheet;
        sheet.setOnDismissListener(() -> {
            if (sheet == premiumSheet) {
                premiumSheet = null;
                checkPopupShownCallback();
            }
        });
        sheet.show();
        checkPopupShownCallback();
    }

    public static class AdLayout extends Bulletin.ButtonLayout {

        public final BackupImageView imageView;
        public final SimpleTextView titleTextView;
        public final LinkSpanDrawable.LinksTextView subtitleTextView;
        public final ImageView buttonView;
        private final LinearLayout linearLayout;

        public AdLayout(@NonNull Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context, resourcesProvider);
            setBackground(getThemedColor(Theme.key_undo_background));

            imageView = new BackupImageView(context);
            imageView.setRoundRadius(dp(48));
            addView(imageView, LayoutHelper.createFrameRelatively(36, 36, Gravity.START | Gravity.CENTER_VERTICAL, 9, 0, 0, 0));

            final int undoInfoColor = getThemedColor(Theme.key_undo_infoColor);
            final int undoLinkColor = getThemedColor(Theme.key_undo_cancelColor);

            linearLayout = new LinearLayout(context);
            linearLayout.setOrientation(LinearLayout.VERTICAL);
            addView(linearLayout, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL, 52, 8, 54, 8));

            titleTextView = new SimpleTextView(context);
            titleTextView.setPadding(dp(4), 0, dp(4), 0);
            titleTextView.setTextColor(undoInfoColor);
            titleTextView.setTextSize(14);
            titleTextView.setTypeface(AndroidUtilities.bold());
            linearLayout.addView(titleTextView);

            subtitleTextView = new LinkSpanDrawable.LinksTextView(context);
            subtitleTextView.setPadding(dp(4), 0, dp(4), 0);
            subtitleTextView.setTextColor(undoInfoColor);
            subtitleTextView.setLinkTextColor(undoLinkColor);
            subtitleTextView.setTypeface(Typeface.SANS_SERIF);
            subtitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            linearLayout.addView(subtitleTextView);

            buttonView = new ImageView(context);
            buttonView.setScaleType(ImageView.ScaleType.CENTER);
            buttonView.setBackground(Theme.createSelectorDrawable(Theme.multAlpha(getThemedColor(Theme.key_featuredStickers_addButton), .15f), Theme.RIPPLE_MASK_ROUNDRECT_6DP));
            addView(buttonView, LayoutHelper.createFrameRelatively(32, 32, Gravity.END | Gravity.CENTER_VERTICAL, 0, 0, 11, 0));
        }

        @Override
        protected void onShow() {
            super.onShow();
//            imageView.playAnimation();
        }


        public CharSequence getAccessibilityText() {
            return titleTextView.getText() + ".\n" + subtitleTextView.getText();
        }

        public void hideImage() {
            imageView.setVisibility(GONE);
            ((MarginLayoutParams) linearLayout.getLayoutParams()).setMarginStart(dp(10));
        }
    }

    public void logSponsoredShown(TLRPC.TL_sponsoredMessage ad) {
        if (ad == null) return;
        final TLRPC.TL_messages_viewSponsoredMessage req = new TLRPC.TL_messages_viewSponsoredMessage();
        req.random_id = ad.random_id;
        if (!BuildVars.DEBUG_PRIVATE_VERSION) {
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, null);
        }
    }

    public void logSponsoredClicked(TLRPC.TL_sponsoredMessage ad) {
        if (ad == null) return;
        final TLRPC.TL_messages_clickSponsoredMessage req = new TLRPC.TL_messages_clickSponsoredMessage();
        req.random_id = ad.random_id;
        req.media = false;
        req.fullscreen = false;
        if (!BuildVars.DEBUG_PRIVATE_VERSION) {
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, null);
        }
    }

}
