package org.telegram.ui.Stories.recorder;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.AbstractSerializedData;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.ButtonBounce;
import org.telegram.ui.Components.CubicBezierInterpolator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class StoryPrivacySelector extends View {

    private final Theme.ResourcesProvider resourcesProvider;
    private final int currentAccount;

    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Drawable rippleDrawable;

    private final AnimatedTextView.AnimatedTextDrawable textDrawable = new AnimatedTextView.AnimatedTextDrawable(true, true, false);

    @NonNull
    private StoryPrivacyBottomSheet.StoryPrivacy value = new StoryPrivacyBottomSheet.StoryPrivacy();

    public StoryPrivacySelector(Context context, int currentAccount, Theme.ResourcesProvider resourcesProvider) {
        super(context);

        this.currentAccount = currentAccount;
        this.resourcesProvider = resourcesProvider;

        backgroundPaint.setColor(0x4d000000);

        textDrawable.setCallback(this);
        textDrawable.setAnimationProperties(.55f, 0, 460, CubicBezierInterpolator.EASE_OUT_QUINT);
        textDrawable.setTextColor(0xffffffff);
        textDrawable.setTextSize(dpf2(14));
        textDrawable.setTypeface(AndroidUtilities.bold());
        textDrawable.setGravity(Gravity.CENTER);

        textDrawable.setText(value.toString());
    }

    private final RectF rect = new RectF();
    private final RectF clickRect = new RectF();

    @Override
    protected void dispatchDraw(Canvas canvas) {
        final float width = textDrawable.getCurrentWidth() + dp(13 + 13);
        rect.set(
            (getWidth() - width) / 2f,
            dp(13),
            (getWidth() + width) / 2f,
            dp(13 + 30)
        );
        clickRect.set(rect);
        clickRect.inset(-dp(28), -dp(14));
        canvas.drawRoundRect(rect, dp(15), dp(15), backgroundPaint);

        if (rippleDrawable != null) {
            rippleDrawable.setBounds((int) rect.left, (int) rect.top, (int) rect.right, (int) rect.bottom);
            rippleDrawable.draw(canvas);
        }

        textDrawable.setBounds(0, -dp(1), getWidth(), getHeight() - dp(1));
        textDrawable.draw(canvas);
    }

    private long tapTime;
    private Runnable longPressRunnable;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean hit = clickRect.contains(event.getX(), event.getY());
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (longPressRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(longPressRunnable);
                longPressRunnable = null;
            }
            if (hit) {
                rippleDrawable = Theme.createRadSelectorDrawable(234881023, dp(15), dp(15));
                rippleDrawable.setBounds((int) rect.left, (int) rect.top, (int) rect.right, (int) rect.bottom);
                rippleDrawable.setState(new int[]{android.R.attr.state_pressed, android.R.attr.state_enabled});
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    rippleDrawable.setHotspot(event.getX(), event.getY());
                }
                rippleDrawable.setCallback(this);
                tapTime = System.currentTimeMillis();
                AndroidUtilities.runOnUIThread(longPressRunnable = () -> {
                    if (rippleDrawable != null) {
                        rippleDrawable.setState(new int[]{});
                    }
                    tapTime = -1;
                    open();
                    try {
                        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                    } catch (Exception ignore) {}
                }, ViewConfiguration.getLongPressTimeout());
                invalidate();
                return true;
            } else {
                tapTime = -1;
            }
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            if (longPressRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(longPressRunnable);
                longPressRunnable = null;
            }
            if (hit && rippleDrawable != null && System.currentTimeMillis() - tapTime <= ViewConfiguration.getTapTimeout()) {
                open();
            }
            if (rippleDrawable != null) {
                rippleDrawable.setState(new int[]{});
            }
        } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
            if (longPressRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(longPressRunnable);
                longPressRunnable = null;
            }
            if (rippleDrawable != null) {
                rippleDrawable.setState(new int[]{});
            }
        }
        return super.onTouchEvent(event);
    }

    private boolean edited;
    public boolean isEdited() {
        return edited;
    }

    public void open() {
        onPopupOpen(() -> {
            StoryPrivacyBottomSheet sheet = new StoryPrivacyBottomSheet(getContext(), storyPeriod, resourcesProvider);
            sheet.setValue(getStoryPrivacy());
            sheet.isEdit(false);
            sheet.whenSelectedRules((privacy, allowComments, a, b, isRtmpStream, sendAs, pricePerComment, whenDone, cancelled) -> {
                edited = true;
                CharSequence text = (value = privacy).toString();
                textDrawable.setText(text);
                setContentDescription(text);
                save(currentAccount, value);
                if (whenDone != null) {
                    whenDone.run();
                }
            }, true);
            sheet.setOnDismissListener(d -> onPopupClose());
            sheet.show();
        });
    }

    protected void onPopupOpen(Runnable after) {}
    protected void onPopupClose() {}

    public void set(StoryEntry entry, boolean animated) {
        edited = false;
        if (entry.privacy != null) {
            value = entry.privacy;
        } else {
            value = new StoryPrivacyBottomSheet.StoryPrivacy(entry.privacyRules);
        }
        textDrawable.setText(value.toString(), animated && !LocaleController.isRTL);
    }

    private int storyPeriod = 86400;
    public void setStoryPeriod(int period) {
        this.storyPeriod = period;
    }

    @NonNull
    public StoryPrivacyBottomSheet.StoryPrivacy getStoryPrivacy() {
        return value;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(56), MeasureSpec.EXACTLY));
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return who == textDrawable || who == rippleDrawable || super.verifyDrawable(who);
    }

    public static class StoryPrivacyHint extends View {

        private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

        private final StaticLayout layout;
        private final float layoutWidth, layoutLeft;

        private final Drawable closeDrawable;
        private final Rect clickCloseRect = new Rect();
        private final ButtonBounce closeBounce = new ButtonBounce(this);

        private final Path path = new Path();

        public StoryPrivacyHint(Context context) {
            super(context);

            backgroundPaint.setColor(0x4d000000);
            backgroundPaint.setPathEffect(new CornerPathEffect(dp(6)));

            textPaint.setColor(0xffffffff);
            textPaint.setTextSize(dp(14));

            CharSequence text = LocaleController.getString(R.string.StoryPrivacyHint);
            layout = new StaticLayout(text, textPaint, AndroidUtilities.displaySize.x, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
            layoutLeft = layout.getLineCount() > 0 ? layout.getLineLeft(0) : 0;
            layoutWidth = layout.getLineCount() > 0 ? layout.getLineWidth(0) : 0;

            closeDrawable = getContext().getResources().getDrawable(R.drawable.msg_mini_close_tooltip);
            closeDrawable.setColorFilter(new PorterDuffColorFilter(Theme.multAlpha(0xffffffff, .6f), PorterDuff.Mode.MULTIPLY));

            setAlpha(0f);
            setTranslationY(dp(6));
            setVisibility(View.GONE);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            final int width = dp(11 + 8 + 11) + (int) layoutWidth + closeDrawable.getIntrinsicWidth();
            final int height = dp(8 + 8 + 6) + layout.getHeight();
            setMeasuredDimension(width, height);

            path.rewind();
            path.moveTo(0, dp(6));
            path.lineTo(0, height);
            path.lineTo(width, height);
            path.lineTo(width, dp(6));
            path.lineTo(width / 2f + dp(7), dp(6));
            path.lineTo(width / 2f, -dp(2));
            path.lineTo(width / 2f - dp(7), dp(6));
            path.close();
        }

        @Override
        public void draw(Canvas canvas) {
            super.draw(canvas);

            canvas.drawPath(path, backgroundPaint);

            canvas.save();
            canvas.translate(dp(11) - layoutLeft, dp(6 + 8));
            layout.draw(canvas);
            canvas.restore();

            closeDrawable.setBounds(
                getWidth() - dp(11) - closeDrawable.getIntrinsicWidth(),
                dp(6) + (getHeight() - dp(6) - closeDrawable.getIntrinsicHeight()) / 2,
                getWidth() - dp(11),
                dp(6) + (getHeight() - dp(6) + closeDrawable.getIntrinsicHeight()) / 2
            );
            clickCloseRect.set(closeDrawable.getBounds());
            clickCloseRect.inset(-dp(10), -dp(6));
            canvas.save();
            float scale = closeBounce.getScale(.12f);
            canvas.scale(scale, scale, closeDrawable.getBounds().centerX(), closeDrawable.getBounds().centerY());
            closeDrawable.draw(canvas);
            canvas.restore();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (getAlpha() < 1 || getVisibility() != View.VISIBLE) {
                return false;
            }
            boolean hitClose = clickCloseRect.contains((int) event.getX(), (int) event.getY());
            if (event.getAction() == MotionEvent.ACTION_DOWN && hitClose) {
                closeBounce.setPressed(true);
                if (hideRunnable != null) {
                    AndroidUtilities.cancelRunOnUIThread(hideRunnable);
                    hideRunnable = null;
                }
                return true;
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                closeBounce.setPressed(false);
                hide();
                neverShow();
            } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                closeBounce.setPressed(false);
                if (hideRunnable != null) {
                    AndroidUtilities.cancelRunOnUIThread(hideRunnable);
                    hideRunnable = null;
                }
                AndroidUtilities.runOnUIThread(hideRunnable = this::hide, 5000);
            }
            return super.onTouchEvent(event);
        }

        private Runnable hideRunnable;

        public void show(boolean force) {
            if (!shouldShow() && !force) {
                return;
            }

            clearAnimation();
            setVisibility(View.VISIBLE);
            setAlpha(1f);
            setTranslationY(dp(6));
            animate().alpha(1f).translationY(0).setInterpolator(CubicBezierInterpolator.EASE_OUT_BACK).start();

            if (hideRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(hideRunnable);
            }
//            AndroidUtilities.runOnUIThread(hideRunnable = this::hide, 5000);
        }

        public void hide() {
            if (hideRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(hideRunnable);
                hideRunnable = null;
            }
            clearAnimation();
            animate().alpha(0).translationY(-dp(8)).withEndAction(() -> setVisibility(View.GONE)).start();
        }

        private boolean shouldShow() {
            return !MessagesController.getGlobalMainSettings().getBoolean("storyprvhint", false);
        }

        public static void neverShow() {
            MessagesController.getGlobalMainSettings().edit().putBoolean("storyprvhint", true).apply();
        }
    }

    private static StoryPrivacyBottomSheet.StoryPrivacy read(AbstractSerializedData stream) {
        final int type = stream.readInt32(true);

        if (stream.readInt32(true) != 0x1cb5c415) {
            throw new RuntimeException("wrong Vector magic in TL_StoryPrivacy");
        }
        int count = stream.readInt32(true);
        final ArrayList<TLRPC.InputUser> inputUsers = new ArrayList<>(count);
        for (int i = 0; i < count; ++i) {
            inputUsers.add(TLRPC.InputUser.TLdeserialize(stream, stream.readInt32(true), true));
        }

        if (stream.readInt32(true) != 0x1cb5c415) {
            throw new RuntimeException("wrong Vector magic in TL_StoryPrivacy (2)");
        }
        count = stream.readInt32(true);
        final ArrayList<Long> userIds = new ArrayList<>(count);
        for (int i = 0; i < count; ++i) {
            userIds.add(stream.readInt64(true));
        }

        if (stream.readInt32(true) != 0x1cb5c415) {
            throw new RuntimeException("wrong Vector magic in TL_StoryPrivacy (3)");
        }
        count = stream.readInt32(true);
        final HashMap<Long, ArrayList<Long>> userIdsByGroup = new HashMap<>();
        for (int i = 0; i < count; ++i) {
            long key = stream.readInt64(true);
            if (stream.readInt32(true) != 0x1cb5c415) {
                throw new RuntimeException("wrong Vector magic in TL_StoryPrivacy (4)");
            }
            final int count2 = stream.readInt32(true);
            final ArrayList<Long> groupUserIds = new ArrayList<>(count2);
            for (int j = 0; j < count2; ++j) {
                groupUserIds.add(stream.readInt64(true));
            }
            userIdsByGroup.put(key, groupUserIds);
        }

        HashSet<Long> allUserIds = new HashSet<>();
        allUserIds.addAll(userIds);
        for (ArrayList<Long> groupUserIds : userIdsByGroup.values()) {
            allUserIds.addAll(groupUserIds);
        }

        StoryPrivacyBottomSheet.StoryPrivacy privacy = new StoryPrivacyBottomSheet.StoryPrivacy(type, inputUsers, 0);
        privacy.selectedUserIds.clear();
        privacy.selectedUserIds.addAll(userIds);
        privacy.selectedUserIdsByGroup.clear();
        privacy.selectedUserIdsByGroup.putAll(userIdsByGroup);
        return privacy;
    }

    private static void write(AbstractSerializedData stream, StoryPrivacyBottomSheet.StoryPrivacy privacy) {
        stream.writeInt32(privacy.type);
        stream.writeInt32(0x1cb5c415);
        stream.writeInt32(privacy.selectedInputUsers.size());
        for (TLRPC.InputUser inputUser : privacy.selectedInputUsers) {
            inputUser.serializeToStream(stream);
        }
        stream.writeInt32(0x1cb5c415);
        stream.writeInt32(privacy.selectedUserIds.size());
        for (long id : privacy.selectedUserIds) {
            stream.writeInt64(id);
        }
        stream.writeInt32(0x1cb5c415);
        stream.writeInt32(privacy.selectedUserIdsByGroup.size());
        for (Map.Entry<Long, ArrayList<Long>> entry : privacy.selectedUserIdsByGroup.entrySet()) {
            stream.writeInt64(entry.getKey());
            stream.writeInt32(0x1cb5c415);
            stream.writeInt32(entry.getValue().size());
            for (long id : entry.getValue()) {
                stream.writeInt64(id);
            }
        }
    }

    public static void save(int currentAccount, StoryPrivacyBottomSheet.StoryPrivacy privacy) {
        if (privacy == null) {
            MessagesController.getInstance(currentAccount).getMainSettings().edit().remove("story_privacy2").apply();
            return;
        }
        SerializedData calc = new SerializedData(true);
        write(calc, privacy);
        SerializedData data = new SerializedData(calc.length());
        calc.cleanup();
        write(data, privacy);
        SharedPreferences prefs = MessagesController.getInstance(currentAccount).getMainSettings();
        prefs.edit().putString("story_privacy2", Utilities.bytesToHex(data.toByteArray())).apply();
        data.cleanup();
    }

    private static StoryPrivacyBottomSheet.StoryPrivacy getSaved(int currentAccount) {
        try {
            SharedPreferences prefs = MessagesController.getInstance(currentAccount).getMainSettings();
            String hex = prefs.getString("story_privacy2", null);
            if (hex == null) {
                return new StoryPrivacyBottomSheet.StoryPrivacy();
            }
            SerializedData data = new SerializedData(Utilities.hexToBytes(hex));
            StoryPrivacyBottomSheet.StoryPrivacy privacy = read(data);
            data.cleanup();
            if (privacy.isNone()) {
                return new StoryPrivacyBottomSheet.StoryPrivacy();
            }
            HashSet<Long> userIds = new HashSet<>();
            userIds.addAll(privacy.selectedUserIds);
            for (ArrayList<Long> groupUserIds : privacy.selectedUserIdsByGroup.values()) {
                userIds.addAll(groupUserIds);
            }
            if (!userIds.isEmpty()) {
                MessagesStorage storage = MessagesStorage.getInstance(currentAccount);
                storage.getStorageQueue().postRunnable(() -> {
                    ArrayList<TLRPC.User> users = storage.getUsers(new ArrayList<>(userIds));
                    AndroidUtilities.runOnUIThread(() -> {
                        MessagesController.getInstance(currentAccount).putUsers(users, true);
                    });
                });
            }
            return privacy;
        } catch (Exception e) {
            FileLog.e(e);
        }
        return new StoryPrivacyBottomSheet.StoryPrivacy();
    }

    public static void applySaved(int currentAccount, StoryEntry entry) {
        if (entry == null) {
            return;
        }
        entry.privacy = getSaved(currentAccount);
        entry.privacyRules.clear();
        entry.privacyRules.addAll(entry.privacy.rules);
        if (UserConfig.getInstance(currentAccount).isPremium()) {
            entry.period = MessagesController.getInstance(currentAccount).getMainSettings().getInt("story_period", 86400);
        } else {
            entry.period = 86400;
        }
    }
}
