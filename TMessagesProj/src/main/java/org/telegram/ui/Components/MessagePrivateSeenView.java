package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.Premium.PremiumButtonView;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PremiumPreviewFragment;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.Date;

public class MessagePrivateSeenView extends FrameLayout {

    public static final int TYPE_SEEN = 0;
    public static final int TYPE_EDIT = 1;
    public static final int TYPE_FORWARD = 2;

    private final int currentAccount;
    private final int type;
    private final Theme.ResourcesProvider resourcesProvider;

    private final LinearLayout valueLayout;
    private final TextView valueTextView;
    private final TextView premiumTextView;
    private final TextView loadingView;

    private final long dialogId;
    private final int messageId;
    private final int edit_date;
    private final int fwd_date;
    private final Runnable dismiss;

    private final int messageDiff;

    public MessagePrivateSeenView(Context context, int type, @NonNull MessageObject messageObject, Runnable dismiss, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.type = type;

        currentAccount = messageObject.currentAccount;
        this.resourcesProvider = resourcesProvider;
        this.dismiss = dismiss;
        messageDiff = ConnectionsManager.getInstance(currentAccount).getCurrentTime() - messageObject.messageOwner.date;

        dialogId = messageObject.getDialogId();
        messageId = messageObject.getId();
        edit_date = messageObject.messageOwner == null ? 0 : messageObject.messageOwner.edit_date;
        fwd_date = messageObject.messageOwner == null || messageObject.messageOwner.fwd_from == null ? 0 : messageObject.messageOwner.fwd_from.date;

        ImageView iconView = new ImageView(context);
        addView(iconView, LayoutHelper.createFrame(24, 24, Gravity.LEFT | Gravity.CENTER_VERTICAL, 11, 0, 0, 0));
        int icon;
        if (type == TYPE_EDIT) {
            icon = R.drawable.menu_edited_stamp;
        } else if (type == TYPE_FORWARD) {
            icon = R.drawable.menu_forward_stamp;
        } else if (messageObject.isVoice()) {
            icon = R.drawable.msg_played;
        } else {
            icon = R.drawable.msg_seen;
        }
        Drawable drawable = ContextCompat.getDrawable(context, icon).mutate();
        drawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultSubmenuItemIcon, resourcesProvider), PorterDuff.Mode.MULTIPLY));
        iconView.setImageDrawable(drawable);

        loadingView = new TextView(context);
        SpannableStringBuilder text = new SpannableStringBuilder("loading text ");
        text.setSpan(new LoadingSpan(loadingView, dp(96), dp(2), resourcesProvider), 0, text.length() - 1, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        loadingView.setTextColor(Theme.multAlpha(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider), .7f));
        loadingView.setText(text);
        loadingView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        addView(loadingView, LayoutHelper.createFrame(96, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 40, -1, 8, 0));

        valueLayout = new LinearLayout(context);
        valueLayout.setOrientation(LinearLayout.HORIZONTAL);
        valueLayout.setAlpha(0f);
        addView(valueLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 38, 0, 8, 0));

        valueTextView = new TextView(context);
        valueTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        valueLayout.addView(valueTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 0, -1, 0, 0));

        premiumTextView = new TextView(context);
        premiumTextView.setBackground(Theme.createRoundRectDrawable(dp(20), Theme.multAlpha(Theme.getColor(Theme.key_divider, resourcesProvider), .75f)));
        premiumTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        premiumTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        premiumTextView.setPadding(dp(5.33f), dp(2), dp(5.33f), dp(2.33f));
        valueLayout.addView(premiumTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 4, 0, 0, 0));

        request();
    }

    private void request() {
        if (type == TYPE_EDIT) {
            valueLayout.setAlpha(1f);
            loadingView.setAlpha(0f);
            premiumTextView.setVisibility(View.GONE);
            valueTextView.setText(LocaleController.formatPmEditedDate(edit_date));
            return;
        } else if (type == TYPE_FORWARD) {
            valueLayout.setAlpha(1f);
            loadingView.setAlpha(0f);
            premiumTextView.setVisibility(View.GONE);
            valueTextView.setText(LocaleController.formatPmFwdDate(fwd_date));
            return;
        }
        setOnClickListener(null);
        valueLayout.setAlpha(0f);
        loadingView.setAlpha(1f);
        premiumTextView.setVisibility(View.VISIBLE);

        TLRPC.TL_messages_getOutboxReadDate req = new TLRPC.TL_messages_getOutboxReadDate();
        req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
        req.msg_id = messageId;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            if (err != null) {
                if ("USER_PRIVACY_RESTRICTED".equals(err.text)) {
                    valueTextView.setText(LocaleController.getString(R.string.PmReadUnknown));
                    premiumTextView.setVisibility(View.GONE);
                } else if ("YOUR_PRIVACY_RESTRICTED".equals(err.text)) {
                    isPremiumLocked = true;
                    valueTextView.setText(LocaleController.getString(R.string.PmRead));
                    premiumTextView.setText(LocaleController.getString(R.string.PmReadShowWhen));
                } else {
                    valueTextView.setText(LocaleController.getString("UnknownError"));
                    premiumTextView.setVisibility(View.GONE);
                    BulletinFactory.of(Bulletin.BulletinWindow.make(getContext()), resourcesProvider).showForError(err);
                }
            } else if (res instanceof TLRPC.TL_outboxReadDate) {
                TLRPC.TL_outboxReadDate r = (TLRPC.TL_outboxReadDate) res;
                valueTextView.setText(LocaleController.formatPmSeenDate(r.date));
                premiumTextView.setVisibility(View.GONE);
            }
            valueLayout.animate().alpha(1f).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).setDuration(320).start();
            loadingView.animate().alpha(0f).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).setDuration(320).start();

            if (isPremiumLocked) {
                setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider), 6, 0));
                setOnClickListener(v -> showSheet(getContext(), currentAccount, dialogId, false, dismiss, this::request, resourcesProvider));
            } else {
                setBackground(null);
                setOnClickListener(null);
            }
        }));
    }

    public boolean isPremiumLocked = false;

    public static void showSheet(Context context, int currentAccount, long dialogId, boolean lastSeen, Runnable dismiss, Runnable updated, Theme.ResourcesProvider resourcesProvider) {
        BottomSheet sheet = new BottomSheet(context, false, resourcesProvider);
        sheet.fixNavigationBar(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));

        final boolean premiumLocked = MessagesController.getInstance(currentAccount).premiumFeaturesBlocked();

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(16), 0, dp(16), 0);

        RLottieImageView imageView = new RLottieImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setAnimation(lastSeen ? R.raw.large_lastseen : R.raw.large_readtime, 70, 70);
        imageView.playAnimation();
        imageView.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
        imageView.setBackground(Theme.createCircleDrawable(dp(80), Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider)));
        layout.addView(imageView, LayoutHelper.createLinear(80, 80, Gravity.CENTER_HORIZONTAL, 0, 16, 0, 16));

        TextView headerView = new TextView(context);
        headerView.setTypeface(AndroidUtilities.bold());
        headerView.setGravity(Gravity.CENTER);
        headerView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        headerView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        headerView.setText(LocaleController.getString(lastSeen ? R.string.PremiumLastSeenHeader1 : R.string.PremiumReadHeader1));
        layout.addView(headerView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 12, 0, 12, 0));

        TextView descriptionView = new TextView(context);
        descriptionView.setGravity(Gravity.CENTER);
        descriptionView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        descriptionView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        String username = "";
        if (dialogId > 0) {
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
            username = UserObject.getFirstName(user);
        }
        descriptionView.setText(AndroidUtilities.replaceTags(LocaleController.formatString(lastSeen ? (premiumLocked ? R.string.PremiumLastSeenText1Locked : R.string.PremiumLastSeenText1) : (premiumLocked ? R.string.PremiumReadText1Locked : R.string.PremiumReadText1), username)));
        layout.addView(descriptionView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 32, 9, 32, 19));

        ButtonWithCounterView button1 = new ButtonWithCounterView(context, resourcesProvider);
        button1.setText(LocaleController.getString(lastSeen ? R.string.PremiumLastSeenButton1 : R.string.PremiumReadButton1), false);
        layout.addView(button1, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.CENTER_HORIZONTAL));
        button1.setOnClickListener(v -> {
            button1.setLoading(true);
            if (lastSeen) {
                TL_account.setPrivacy req = new TL_account.setPrivacy();
                req.key = new TLRPC.TL_inputPrivacyKeyStatusTimestamp();
                req.rules.add(new TLRPC.TL_inputPrivacyValueAllowAll());
                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                    if (err != null) {
                        BulletinFactory.global().showForError(err);
                        return;
                    }

                    button1.setLoading(false);
                    sheet.dismiss();

                    BulletinFactory.global().createSimpleBulletin(R.raw.chats_infotip, LocaleController.getString(R.string.PremiumLastSeenSet)).show();
                    if (updated != null) {
                        updated.run();
                    }
                }));
            } else {
                TL_account.setGlobalPrivacySettings req = new TL_account.setGlobalPrivacySettings();
                req.settings = ContactsController.getInstance(currentAccount).getGlobalPrivacySettings();
                if (req.settings == null) {
                    req.settings = new TLRPC.TL_globalPrivacySettings();
                }
                req.settings.hide_read_marks = false;
                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                    if (err != null) {
                        BulletinFactory.of(Bulletin.BulletinWindow.make(context), resourcesProvider).showForError(err);
                        return;
                    }

                    button1.setLoading(false);
                    sheet.dismiss();

                    BulletinFactory.of(Bulletin.BulletinWindow.make(context), resourcesProvider).createSimpleBulletin(R.raw.chats_infotip, LocaleController.getString(R.string.PremiumReadSet)).show();
                    if (updated != null) {
                        updated.run();
                    }
                }));
            }
        });

        if (!premiumLocked) {
            SimpleTextView or = new SimpleTextView(context) {
                private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

                @Override
                protected void dispatchDraw(Canvas canvas) {
                    paint.setColor(Theme.getColor(Theme.key_dialogGrayLine, resourcesProvider));
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(1);
                    final float cy = getHeight() / 2f;
                    canvas.drawLine(0, cy, getWidth() / 2f - getTextWidth() / 2f - dp(8), cy, paint);
                    canvas.drawLine(getWidth() / 2f + getTextWidth() / 2f + dp(8), cy, getWidth(), cy, paint);

                    super.dispatchDraw(canvas);
                }
            };
            or.setGravity(Gravity.CENTER);
            or.setAlignment(Layout.Alignment.ALIGN_CENTER);
            or.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
            or.setText(" " + LocaleController.getString(R.string.PremiumOr) + " ");
            or.setTextSize(14);
            layout.addView(or, LayoutHelper.createLinear(270, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 12, 17, 12, 17));

            TextView headerView2 = new TextView(context);
            headerView2.setTypeface(AndroidUtilities.bold());
            headerView2.setGravity(Gravity.CENTER);
            headerView2.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
            headerView2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
            headerView2.setText(LocaleController.getString(lastSeen ? R.string.PremiumLastSeenHeader2 : R.string.PremiumReadHeader2));
            layout.addView(headerView2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 12, 0, 12, 0));

            TextView descriptionView2 = new TextView(context);
            descriptionView2.setGravity(Gravity.CENTER);
            descriptionView2.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
            descriptionView2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            descriptionView2.setText(AndroidUtilities.replaceTags(LocaleController.formatString(lastSeen ? R.string.PremiumLastSeenText2 : R.string.PremiumReadText2, username)));
            layout.addView(descriptionView2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 32, 9, 32, 19));

            PremiumButtonView button2 = new PremiumButtonView(context, true, resourcesProvider);
            button2.setOnClickListener(v2 -> {
                BaseFragment lastFragment = LaunchActivity.getLastFragment();
                if (lastFragment != null) {
                    lastFragment.presentFragment(new PremiumPreviewFragment(lastSeen ? "lastseen" : "readtime"));
                    sheet.dismiss();
                    if (dismiss != null) {
                        dismiss.run();
                    }
                }
            });
            button2.setOverlayText(LocaleController.getString(lastSeen ? R.string.PremiumLastSeenButton2 : R.string.PremiumReadButton2), false, false);
            layout.addView(button2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 4));
        }

        sheet.setCustomView(layout);
        sheet.show();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (Bulletin.getVisibleBulletin() != null) {
            Bulletin bulletin = Bulletin.getVisibleBulletin();
            if (bulletin.getLayout() != null && bulletin.getLayout().getParent() != null && bulletin.getLayout().getParent().getParent() instanceof Bulletin.BulletinWindow.BulletinWindowLayout) {
                bulletin.hide();
            }
        }
    }

    float minWidth = -1;

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        View parent = (View) getParent();
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);

        if (minWidth < 0) {
            minWidth = 0;
            if (type == TYPE_SEEN) {
                final long date = System.currentTimeMillis();
                minWidth = Math.max(minWidth, dp(40 + 96 + 8));
                minWidth = Math.max(minWidth, dp(40 + 8) + valueTextView.getPaint().measureText(LocaleController.getString(R.string.PmReadUnknown)));
                minWidth = Math.max(minWidth, dp(40 + 16 + 8) + valueTextView.getPaint().measureText(LocaleController.getString(R.string.PmRead) + premiumTextView.getPaint().measureText(LocaleController.getString(R.string.PmReadShowWhen))));
                minWidth = Math.max(minWidth, dp(40 + 8) + valueTextView.getPaint().measureText(LocaleController.formatString(R.string.PmReadTodayAt, LocaleController.getInstance().getFormatterDay().format(new Date(date)))));
                if (messageDiff > 60 * 60 * 24) {
                    minWidth = Math.max(minWidth, dp(40 + 8) + valueTextView.getPaint().measureText(LocaleController.formatString(R.string.PmReadYesterdayAt, LocaleController.getInstance().getFormatterDay().format(new Date(date)))));
                }
                if (messageDiff > 60 * 60 * 24 * 2) {
                    minWidth = Math.max(minWidth, dp(40 + 8) + valueTextView.getPaint().measureText(LocaleController.formatString(R.string.PmReadDateTimeAt, LocaleController.getInstance().getFormatterDayMonth().format(new Date(date)), LocaleController.getInstance().getFormatterDay().format(new Date(date)))));
                    minWidth = Math.max(minWidth, dp(40 + 8) + valueTextView.getPaint().measureText(LocaleController.formatString(R.string.PmReadDateTimeAt, LocaleController.getInstance().getFormatterYear().format(new Date(date)), LocaleController.getInstance().getFormatterDay().format(new Date(date)))));
                }
            } else {
                minWidth = dp(40 + 8) + valueTextView.getPaint().measureText(valueTextView.getText().toString());
            }
        }

        if (parent != null && parent.getWidth() > 0) {
            width = parent.getWidth();
            widthMode = MeasureSpec.EXACTLY;
        }
        if (width < minWidth || widthMode == MeasureSpec.AT_MOST) {
            width = (int) minWidth;
            widthMode = MeasureSpec.EXACTLY;
        }

        super.onMeasure(MeasureSpec.makeMeasureSpec(width, widthMode), heightMeasureSpec);
    }
}
