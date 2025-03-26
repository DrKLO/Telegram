package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getCountryWithFlag;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_bots;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.AvatarsDrawable;
import org.telegram.ui.Components.ButtonBounce;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.Text;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.ProfileActivity;

import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

public class UserInfoCell extends View implements NotificationCenter.NotificationCenterDelegate {

    private final int currentAccount;
    private final Theme.ResourcesProvider resourcesProvider;
    private long dialogId;

    private Text title;
    private Text subtitle;
    private Text footer;
    private final ArrayList<Row> rows = new ArrayList<>();
    private Row groupsRow;
    private float rowsWidth;
    private float rowsKeysWidth;
    private float rowsValuesWidth;

    private final RectF fullBounds = new RectF();
    private final ButtonBounce fullBounce = new ButtonBounce(this);

    private final RectF groupsBounds = new RectF();
    private final Drawable groupsRipple;
    private final ButtonBounce groupsBounce = new ButtonBounce(this);
    private final AvatarsDrawable groupsAvatars = new AvatarsDrawable(this, false);
    private final Drawable groupsArrow;
    private MessagesController.CommonChatsList commonChats;

    private float height;
    private float width;

    private class Row {
        public Text key;
        public Text value;
        public boolean avatars;

        public final RectF bounds = new RectF();

        public Row(CharSequence key, CharSequence value, boolean avatars) {
            this.key = new Text(key, 12);
            this.value = new Text(value, 12, AndroidUtilities.bold());
            this.avatars = avatars;
        }
    }
    private Row addRow(CharSequence key, CharSequence value, boolean withAvatars) {
        if (!rows.isEmpty()) {
            height += dp(7);
        }
        final Row row = new Row(key, value, withAvatars);
        rows.add(row);
        height += dp(14);

        rowsKeysWidth = Math.max(rowsKeysWidth, row.key.getCurrentWidth());
        rowsValuesWidth = Math.max(rowsValuesWidth, row.value.getCurrentWidth() + (withAvatars ? dp(38) : 0));
        return row;
    }

    public static String displayDate(String date) {
        final String[] parts = date.split("\\.");
        if (parts.length != 2) return date;
        final int month = Integer.parseInt(parts[0]);
        final int year = Integer.parseInt(parts[1]);

        final Calendar calendar = Calendar.getInstance();
        calendar.set(year, month - 1, 1, 0, 0, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        return LocaleController.formatYearMont(calendar.getTimeInMillis() / 1000L, true);
    }

    public UserInfoCell(Context context, int currentAccount, Theme.ResourcesProvider resourcesProvider) {
        super(context);

        this.currentAccount = currentAccount;
        this.resourcesProvider = resourcesProvider;

        groupsRipple = Theme.createRadSelectorDrawable(0x30FFFFFF, 8, 8);
        groupsRipple.setCallback(this);

        groupsAvatars.width = dp(50);
        groupsAvatars.height = dp(13);
        groupsAvatars.drawStoriesCircle = false;
        groupsAvatars.setSize(dp(13));
        groupsAvatars.setAvatarsTextSize(dp(18));

        groupsArrow = context.getResources().getDrawable(R.drawable.msg_mini_forumarrow).mutate();
        groupsArrow.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return who == groupsRipple ||  super.verifyDrawable(who);
    }

    public void set(long dialogId, TLRPC.PeerSettings settings) {
        this.dialogId = dialogId;

        width = 0;
        height = 0;
        rowsKeysWidth = 0;
        rowsValuesWidth = 0;
        rows.clear();

        final int maxWidth = (int) (AndroidUtilities.displaySize.x * .95f);

        height += dp(14);
        title = new Text(DialogObject.getName(dialogId), 14, AndroidUtilities.bold());
        height += title.getHeight() + dp(3);
        subtitle = new Text(getString(ContactsController.getInstance(currentAccount).isContact(dialogId) ? R.string.ContactInfoIsContact : R.string.ContactInfoIsNotContact), 14);
        height += subtitle.getHeight() + dp(11);

        if (settings != null && settings.phone_country != null) {
            addRow(getString(R.string.ContactInfoPhone), getCountryWithFlag(settings.phone_country, 12, R.string.ContactInfoPhoneFragment), false);
        }
        if (settings != null && settings.registration_month != null) {
            addRow(getString(R.string.ContactInfoRegistration), displayDate(settings.registration_month), false);
        }
//        if (settings != null && settings.location_country != null) {
//            addRow(LocaleController.getString(R.string.ContactInfoLocation), countryText(settings.location_country));
//        }

        final TLRPC.User user = dialogId < 0 ? null : MessagesController.getInstance(currentAccount).getUser(dialogId);
        final TLRPC.UserFull userFull = dialogId < 0 ? null : MessagesController.getInstance(currentAccount).getUserFull(dialogId);
        if (userFull == null && dialogId > 0) {
            MessagesController.getInstance(currentAccount).loadUserInfo(MessagesController.getInstance(currentAccount).getUser(dialogId), true, 0);
        }

        if (userFull != null) {
            commonChats = MessagesController.getInstance(currentAccount).getCommonChats(dialogId);
            final int count = Math.max(userFull.common_chats_count, commonChats.getCount());
            if (count > 0) {
                groupsRow = addRow(getString(R.string.ContactInfoCommonGroups), LocaleController.formatPluralString("Groups", count), true);
                groupsAvatars.setCount(Math.min(3, commonChats.chats.size()));
                for (int i = 0; i < Math.min(3, commonChats.chats.size()); ++i) {
                    groupsAvatars.setObject(i, currentAccount, commonChats.chats.get(i));
                }
                groupsAvatars.commitTransition(true);
            } else {
                commonChats = null;
                groupsRow = null;
            }
        } else {
            commonChats = null;
            groupsRow = null;
        }

        rowsWidth = rowsKeysWidth + dp(7.66f) + rowsValuesWidth;
        if (user != null && !user.verified && !UserObject.isService(user.id)) {
            if (user.bot_verification_icon != 0) {
                if (userFull != null && userFull.bot_verification != null) {
                    final TL_bots.botVerification verification = userFull.bot_verification;
                    final SpannableStringBuilder sb = new SpannableStringBuilder("i  ");
                    footer = new Text(sb, 12);
                    sb.setSpan(new AnimatedEmojiSpan(verification.icon, footer.getFontMetricsInt()), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    sb.append(verification.description);
                    footer = new Text(sb, 12).align(Layout.Alignment.ALIGN_CENTER).multiline(5).setMaxWidth(Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * .5f).supportAnimatedEmojis(this);
                    height += dp(12) + footer.getHeight() + dp(15.33f);
                } else {
                    footer = null;
                    height += dp(14);
                }
            } else {
                final SpannableStringBuilder sb = new SpannableStringBuilder("i  ");
                final ColoredImageSpan span = new ColoredImageSpan(R.drawable.filled_info);
                span.setScale(0.55f, -0.55f);
                span.translate(dp(1), dp(-1));
                sb.setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.append(getString(R.string.ContactInfoNotVerified));
                footer = new Text(sb, 12);
                height += dp(12) + footer.getHeight() + dp(15.33f);
            }
        } else {
            footer = null;
            height += dp(14);
        }

        width = Math.max(width, title.getWidth());
        width = Math.max(width, subtitle.getWidth());
        width = Math.max(width, rowsWidth);

        width = Math.min(width + dp(32), maxWidth);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.userInfoDidLoad) {
            if ((long) args[0] == dialogId) {
                set(dialogId, MessagesController.getInstance(currentAccount).getPeerSettings(dialogId));
            }
        } else if (id == NotificationCenter.commonChatsLoaded) {
            if ((long) args[0] == dialogId) {
                commonChats = MessagesController.getInstance(currentAccount).getCommonChats(dialogId);
                final int count = commonChats.getCount();
                if (groupsRow == null || count <= 0) {
                    set(dialogId, MessagesController.getInstance(currentAccount).getPeerSettings(dialogId));
                    requestLayout();
                } else {
                    groupsRow.value = new Text(LocaleController.formatPluralString("Groups", count), 12, AndroidUtilities.bold());
                    groupsAvatars.setCount(Math.min(3, commonChats.chats.size()));
                    for (int i = 0; i < Math.min(3, commonChats.chats.size()); ++i) {
                        groupsAvatars.setObject(i, currentAccount, commonChats.chats.get(i));
                    }
                    groupsAvatars.commitTransition(true);
                }
                invalidate();
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.userInfoDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.commonChatsLoaded);
        groupsAvatars.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.userInfoDidLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.commonChatsLoaded);
        groupsAvatars.onDetachedFromWindow();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(width, Math.max(0, (int) height + dp(16)));
    }

    public static boolean isEmpty(TLRPC.PeerSettings settings) {
        return settings == null || settings.phone_country == null && settings.registration_month == null;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        canvas.save();

        float Y = 0;
        final float cx = getWidth() / 2.0f;
        final float l = cx - width / 2.0f;
        fullBounds.set((getWidth() - width) / 2.0f, (getHeight() - height) / 2.0f, (getWidth() + width) / 2.0f, (getHeight() + height) / 2.0f);

        final float s2 = fullBounce.getScale(0.025f);
        canvas.scale(s2, s2, fullBounds.centerX(), fullBounds.centerY());
        applyServiceShaderMatrix();
        Paint backgroundPaint = Theme.getThemePaint(Theme.key_paint_chatActionBackground, resourcesProvider);
        canvas.drawRoundRect(fullBounds, dp(16), dp(16), backgroundPaint);
        if (hasGradientService()) {
            canvas.drawRoundRect(fullBounds, dp(16), dp(16), Theme.getThemePaint(Theme.key_paint_chatActionBackgroundDarken, resourcesProvider));
        }

        canvas.translate(0, (getHeight() - height) / 2.0f);
        Y += (getHeight() - height) / 2.0f;
        canvas.translate(0, dp(14));
        Y += dp(14);
        title
            .ellipsize(width - dp(32))
            .draw(canvas, cx - title.getWidth() / 2.0f, title.getHeight() / 2.0f, Color.WHITE, 1.0f);
        canvas.translate(0, title.getHeight() + dp(3));
        Y += title.getHeight() + dp(3);
        subtitle
            .ellipsize(width - dp(32))
            .draw(canvas, cx - subtitle.getWidth() / 2.0f, subtitle.getHeight() / 2.0f, Color.WHITE, 0.7f);
        canvas.translate(0, subtitle.getHeight() + dp(11));
        Y += subtitle.getHeight() + dp(11);

        for (int i = 0; i < rows.size(); ++i) {
            if (i > 0) {
                canvas.translate(0, dp(7));
                Y += dp(7);
            }
            canvas.save();
            final Row row = rows.get(i);
            final float keyX = cx - width / 2.0f + dp(16) + rowsKeysWidth - row.key.getCurrentWidth();
            final float valueX = cx - width / 2.0f + dp(16) + rowsKeysWidth + dp(7.66f);
            row.key
                .ellipsize(valueX - keyX - dp(7.66f))
                .draw(canvas, keyX, row.key.getHeight() / 2.0f, Color.WHITE, 0.7f);
            row.bounds.set(
                cx - width / 2.0f + dp(16) + rowsKeysWidth + dp(7.66f),
                Y,
                cx - width / 2.0f + dp(16) + rowsKeysWidth + dp(7.66f) + row.value.getCurrentWidth() + (row.avatars ? dp(5) + groupsArrow.getIntrinsicWidth() * 0.8f + groupsAvatars.getMaxX() : 0),
                Y + row.value.getHeight()
            );
            if (groupsRow == row) {
                groupsBounds.set(row.bounds);
                groupsBounds.inset(-dp(4), -dp(2));
                final float s = groupsBounce.getScale(0.025f);
                canvas.scale(s, s, groupsBounds.centerX(), row.value.getHeight() / 2.0f);
                if (groupsRipple != null) {
                    groupsRipple.setBounds((int) groupsBounds.left, (int) (groupsBounds.top - Y), (int) groupsBounds.right, (int) (groupsBounds.bottom - Y));
                    groupsRipple.draw(canvas);
                }
            }
            row.value
                .ellipsize(cx + width / 2.0f - dp(8) - valueX)
                .draw(canvas, valueX, row.value.getHeight() / 2.0f, Color.WHITE, 1.0f);
            if (row.avatars) {
                canvas.save();
                canvas.translate(cx - width / 2.0f + dp(16) + rowsKeysWidth + dp(7.66f) + row.value.getCurrentWidth() + dp(4), dp(1));
                groupsAvatars.onDraw(canvas);
                canvas.translate(groupsAvatars.getMaxX() + dp(1), dp(13) / 2.0f);
                final float s = 0.8f;
                groupsArrow.setBounds(0, (int) (-groupsArrow.getIntrinsicHeight() * s / 2), (int) (groupsArrow.getIntrinsicWidth() * s), (int) (groupsArrow.getIntrinsicHeight() * s / 2));
                groupsArrow.draw(canvas);
                canvas.restore();
            }
            canvas.restore();
            canvas.translate(0, dp(14));
            Y += dp(14);
        }

        if (footer != null) {
            canvas.translate(0, dp(12));
            if (footer.isMultiline()) {
                footer.draw(canvas, cx - footer.getWidth() / 2.0f, 0, Color.WHITE, 0.7f);
            } else {
                footer
                    .ellipsize(width - dp(32))
                    .draw(canvas, cx - footer.getWidth() / 2.0f, footer.getHeight() / 2.0f, Color.WHITE, 0.7f);
            }
        }

        canvas.restore();
    }

    private float viewTop;
    private int backgroundHeight;

    public void setVisiblePart(float visibleTop, int parentH) {
        if (Math.abs(viewTop - visibleTop) > 0.01f || parentH != backgroundHeight) {
            invalidate();
        }
        backgroundHeight = parentH;
        viewTop = visibleTop;
    }

    public void applyServiceShaderMatrix() {
        applyServiceShaderMatrix(getMeasuredWidth(), backgroundHeight, getX(), viewTop);
    }

    public void applyServiceShaderMatrix(int measuredWidth, int backgroundHeight, float x, float viewTop) {
        if (resourcesProvider != null) {
            resourcesProvider.applyServiceShaderMatrix(measuredWidth, backgroundHeight, x, viewTop);
        } else {
            Theme.applyServiceShaderMatrix(measuredWidth, backgroundHeight, x, viewTop);
        }
    }

    public boolean hasGradientService() {
        return resourcesProvider != null ? resourcesProvider.hasGradientService() : Theme.hasGradientService();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final boolean groupsHit = groupsRow != null && groupsBounds.contains(event.getX(), event.getY());
        final boolean fullHit = !groupsHit && fullBounds.contains(event.getX(), event.getY());
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            fullBounce.setPressed(fullHit);
            groupsBounce.setPressed(groupsHit);
            groupsRipple.setState(groupsHit ? new int[] { android.R.attr.state_pressed, android.R.attr.state_enabled } : new int[] {});
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            if (fullBounce.isPressed()) {
                final BaseFragment fragment = LaunchActivity.getSafeLastFragment();
                if (fragment instanceof ChatActivity) {
                    ((ChatActivity) fragment).openThisProfile();
                }
            } else if (groupsBounce.isPressed()) {
                final BaseFragment fragment = LaunchActivity.getSafeLastFragment();
                if (fragment != null) {
                    Bundle args = new Bundle();
                    if (dialogId >= 0) {
                        args.putLong("user_id", dialogId);
                    } else {
                        args.putLong("chat_id", -dialogId);
                    }
                    args.putBoolean("open_common", true);
                    fragment.presentFragment(new ProfileActivity(args));
                }
                invalidate();
            }
            groupsBounce.setPressed(false);
            fullBounce.setPressed(false);
            groupsRipple.setState(new int[] {});
        } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
            groupsBounce.setPressed(false);
            fullBounce.setPressed(false);
            groupsRipple.setState(new int[] {});
        }
        return groupsBounce.isPressed() || fullBounce.isPressed();
    }

    private boolean animating;

    public boolean animating() {
        return animating;
    }

    public void setAnimating(boolean animating) {
        this.animating = animating;
    }
}
