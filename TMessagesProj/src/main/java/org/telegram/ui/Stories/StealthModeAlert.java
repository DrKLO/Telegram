package org.telegram.ui.Stories;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.Layout;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.PremiumButtonView;
import org.telegram.ui.Components.Premium.PremiumFeatureBottomSheet;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PremiumPreviewFragment;

import java.util.Locale;

public class StealthModeAlert extends BottomSheet {

    public interface Listener {
        void onButtonClicked(boolean isStealthModeEnabled);
    }

    public static final int TYPE_FROM_STORIES = 0;
    public static final int TYPE_FROM_DIALOGS = 1;

    private final PremiumButtonView button;
    private boolean stealthModeIsActive;
    private int type;
    private Listener listener;

    public StealthModeAlert(Context context, float topOffset, int type, Theme.ResourcesProvider resourcesProvider) {
        super(context, false, resourcesProvider);
        this.type = type;
        FrameLayout frameLayout = new FrameLayout(getContext()) {
            @Override
            protected void onAttachedToWindow() {
                super.onAttachedToWindow();
                Bulletin.addDelegate(container, new Bulletin.Delegate() {
                    @Override
                    public int getTopOffset(int tag) {
                        return (int) (topOffset + AndroidUtilities.dp(58));
                    }
                });
            }

            @Override
            protected void onDetachedFromWindow() {
                super.onDetachedFromWindow();
                Bulletin.removeDelegate(container);
            }
        };

        ImageView imageView = new ImageView(getContext());
        imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        imageView.setBackground(Theme.createCircleDrawable(AndroidUtilities.dp(80), Theme.getColor(Theme.key_featuredStickers_addButton)));
        imageView.setImageResource(R.drawable.large_stealth);
        frameLayout.addView(imageView, LayoutHelper.createFrame(80, 80, Gravity.CENTER_HORIZONTAL, 0, 18, 0, 0));

        LinearLayout linearLayout = new LinearLayout(getContext());
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        frameLayout.addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 116, 0, 0));

        TextView title = new TextView(getContext());
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        title.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        title.setText(LocaleController.getString("StealthModeTitle", R.string.StealthModeTitle));
        linearLayout.addView(title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        SimpleTextView subtitle = new SimpleTextView(getContext());
        subtitle.setTextSize(14);
        subtitle.setAlignment(Layout.Alignment.ALIGN_CENTER);
        subtitle.setMaxLines(100);
        subtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
        if (UserConfig.getInstance(currentAccount).isPremium()) {
            subtitle.setText(LocaleController.getString("StealthModeHint", R.string.StealthModeHint));
        } else {
            subtitle.setText(LocaleController.getString("StealthModePremiumHint", R.string.StealthModePremiumHint));
        }
        linearLayout.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 36, 10, 36, 0));

        ItemCell itemCell = new ItemCell(getContext());
        itemCell.imageView.setImageResource(R.drawable.msg_stealth_5min);
        itemCell.textView.setText(LocaleController.getString("HideRecentViews", R.string.HideRecentViews));
        itemCell.description.setText(LocaleController.getString("HideRecentViewsDescription", R.string.HideRecentViewsDescription));

        linearLayout.addView(itemCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,0, 0, 20, 0, 0));


        ItemCell itemCell2 = new ItemCell(getContext());
        itemCell2.imageView.setImageResource(R.drawable.msg_stealth_25min);
        itemCell2.textView.setText(LocaleController.getString("HideNextViews", R.string.HideNextViews));
        itemCell2.description.setText(LocaleController.getString("HideNextViewsDescription", R.string.HideNextViewsDescription));

        linearLayout.addView(itemCell2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,0, 0, 10, 0, 0));

        button = new PremiumButtonView(context, AndroidUtilities.dp(8), true, resourcesProvider);
        button.drawGradient = false;
        button.overlayTextView.getDrawable().setSplitByWords(false);
        button.setIcon(R.raw.unlock_icon);
        ScaleStateListAnimator.apply(button);
        TLRPC.User user = UserConfig.getInstance(currentAccount).getCurrentUser();
        if (!user.premium) {
            button.setIcon(R.raw.unlock_icon);
            button.setButton(LocaleController.getString("UnlockStealthMode", R.string.UnlockStealthMode), v -> {
                dismiss();
                BaseFragment baseFragment = LaunchActivity.getLastFragment();
                if (baseFragment != null) {
                    PremiumFeatureBottomSheet sheet = new PremiumFeatureBottomSheet(baseFragment, PremiumPreviewFragment.PREMIUM_FEATURE_STORIES, false);
                    baseFragment.showDialog(sheet);
                }
            });
        } else {
            updateButton(false);
        }
        linearLayout.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM, 14, 24, 14, 16));


        setCustomView(frameLayout);

        button.setOnClickListener(v -> {
            if (!user.premium) {
                dismiss();
                BaseFragment baseFragment = LaunchActivity.getLastFragment();
                if (baseFragment != null) {
                    PremiumFeatureBottomSheet sheet = new PremiumFeatureBottomSheet(baseFragment, PremiumPreviewFragment.PREMIUM_FEATURE_STORIES, false);
                    baseFragment.showDialog(sheet);
                }
            } else {
                if (stealthModeIsActive) {
                    dismiss();
                    if (listener != null) {
                        listener.onButtonClicked(false);
                    }
                    return;
                }
                StoriesController storiesController = MessagesController.getInstance(currentAccount).getStoriesController();
                TL_stories.TL_storiesStealthMode stealthMode = storiesController.getStealthMode();
                if (stealthMode == null || ConnectionsManager.getInstance(currentAccount).getCurrentTime() > stealthMode.cooldown_until_date) {
                    TL_stories.TL_stories_activateStealthMode req = new TL_stories.TL_stories_activateStealthMode();
                    req.future = true;
                    req.past = true;
                    stealthMode = new TL_stories.TL_storiesStealthMode();
                    stealthMode.flags |= 1 + 2;
                    stealthMode.cooldown_until_date = ConnectionsManager.getInstance(currentAccount).getCurrentTime() +MessagesController.getInstance(currentAccount).stealthModeCooldown;
                    stealthMode.active_until_date = ConnectionsManager.getInstance(currentAccount).getCurrentTime() + MessagesController.getInstance(currentAccount).stealthModeFuture;
                    storiesController.setStealthMode(stealthMode);
                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {

                    }));
                    containerView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    dismiss();
                    if (type == TYPE_FROM_STORIES) {
                        showStealthModeEnabledBulletin();
                    }
                    if (listener != null) {
                        listener.onButtonClicked(true);
                    }
                } else if (stealthModeIsActive) {
                    dismiss();
                    if (listener != null) {
                        listener.onButtonClicked(false);
                    }
                } else {
                    BulletinFactory factory = BulletinFactory.of(container, resourcesProvider);
                    if (factory != null) {
                        factory.createErrorBulletin(
                                AndroidUtilities.replaceTags(LocaleController.getString("StealthModeCooldownHint", R.string.StealthModeCooldownHint))
                        ).show(true);
                    }
                }
            }
        });
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public static void showStealthModeEnabledBulletin() {
        BaseFragment fragment = LaunchActivity.getLastFragment();
        BulletinFactory factory;
        if (fragment.getLastStoryViewer() != null) {
            factory = BulletinFactory.of(fragment.getLastStoryViewer().windowView, fragment.getLastStoryViewer().getResourceProvider());
        } else {
            factory = BulletinFactory.global();
        }
        if (factory != null) {
            factory.createSimpleLargeBulletin(R.drawable.msg_stories_stealth2,
                    LocaleController.getString("StealthModeOn", R.string.StealthModeOn),
                    LocaleController.getString("StealthModeOnHint", R.string.StealthModeOnHint)
            ).show();
        }
    }

    Runnable updateButtonRunnuble = () -> {
        if (isShowing()) {
            updateButton(true);
        }
    };

    private void updateButton(boolean animated) {
        StoriesController storiesController = MessagesController.getInstance(currentAccount).getStoriesController();
        TL_stories.TL_storiesStealthMode stealthMode = storiesController.getStealthMode();
        if (stealthMode != null && ConnectionsManager.getInstance(currentAccount).getCurrentTime() < stealthMode.active_until_date) {
            stealthModeIsActive = true;
            button.setOverlayText(LocaleController.getString("StealthModeIsActive", R.string.StealthModeIsActive), true, animated);
            button.overlayTextView.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        } else if (stealthMode == null || ConnectionsManager.getInstance(currentAccount).getCurrentTime() > stealthMode.cooldown_until_date) {
            if (type == TYPE_FROM_STORIES) {
                button.setOverlayText(LocaleController.getString("EnableStealthMode", R.string.EnableStealthMode), true, animated);
            } else if (type == TYPE_FROM_DIALOGS) {
                button.setOverlayText(LocaleController.getString(R.string.EnableStealthModeAndOpenStory), true, animated);
            }
            button.overlayTextView.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        } else {
            long timeLeft = stealthMode.cooldown_until_date - ConnectionsManager.getInstance(currentAccount).getCurrentTime();
            int s = (int) ((timeLeft) % 60);
            int m = (int) ((timeLeft / 60) % 60);
            int h = (int) ((timeLeft / 60 / 60));
            String time = String.format(Locale.ENGLISH, "%02d", h) + String.format(Locale.ENGLISH, ":%02d", m) + String.format(Locale.ENGLISH, ":%02d", s);
            button.setOverlayText(LocaleController.formatString("AvailableIn", R.string.AvailableIn, time), true, animated);
            button.overlayTextView.setTextColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_featuredStickers_buttonText), 125));
            AndroidUtilities.cancelRunOnUIThread(updateButtonRunnuble);
            AndroidUtilities.runOnUIThread(updateButtonRunnuble, 1000);
        }
    }

    private class ItemCell extends FrameLayout {

        TextView textView;
        TextView description;
        ImageView imageView;

        public ItemCell(Context context) {
            super(context);
            imageView = new ImageView(context);
            imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_featuredStickers_addButton), PorterDuff.Mode.MULTIPLY));
            addView(imageView, LayoutHelper.createFrame(28, 28, 0, 25, 12, 16, 0));

            textView = new TextView(context);
            textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 68, 8, 16, 0));

            description = new TextView(context);
            description.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
            description.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            addView(description, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 68, 28, 16, 8));
        }
    }
}
