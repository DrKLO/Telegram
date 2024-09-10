/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.EmojiData;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.IdenticonDrawable;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.URLSpanReplacement;

import java.util.ArrayList;

public class IdenticonActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private TextView textView;
    private TextView codeTextView;
    private TextView emojiTextView;
    private FrameLayout container;
    private LinearLayout linearLayout1;
    private LinearLayout linearLayout;

    private int chat_id;

    private AnimatorSet animatorSet;
    private AnimatorSet hintAnimatorSet;

    private String emojiText;
    private boolean emojiSelected;
    private int textWidth;

    private static class LinkMovementMethodMy extends LinkMovementMethod {
        @Override
        public boolean onTouchEvent(TextView widget, Spannable buffer, MotionEvent event) {
            try {
                return super.onTouchEvent(widget, buffer, event);
            } catch (Exception e) {
                FileLog.e(e);
            }
            return false;
        }
    }

    public IdenticonActivity(Bundle args) {
        super(args);
    }

    @Override
    public boolean onFragmentCreate() {
        chat_id = getArguments().getInt("chat_id");
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString(R.string.EncryptionKey));

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        fragmentView = new FrameLayout(context);
        FrameLayout parentFrameLayout = (FrameLayout) fragmentView;
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView.setOnTouchListener((v, event) -> true);

        linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setWeightSum(100);
        parentFrameLayout.addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(20), AndroidUtilities.dp(20), AndroidUtilities.dp(20));
        linearLayout.addView(frameLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 50.0f));

        ImageView identiconView = new ImageView(context);
        identiconView.setScaleType(ImageView.ScaleType.FIT_XY);
        frameLayout.addView(identiconView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        container = new FrameLayout(context) {
            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                if (codeTextView != null) {
                    int x = codeTextView.getLeft() + codeTextView.getMeasuredWidth() / 2 - emojiTextView.getMeasuredWidth() / 2;
                    int y = (codeTextView.getMeasuredHeight() - emojiTextView.getMeasuredHeight()) / 2 + linearLayout1.getTop() - AndroidUtilities.dp(16);
                    emojiTextView.layout(x, y, x + emojiTextView.getMeasuredWidth(), y + emojiTextView.getMeasuredHeight());
                }
            }
        };
        container.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        linearLayout.addView(container, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 50.0f));

        linearLayout1 = new LinearLayout(context);
        linearLayout1.setOrientation(LinearLayout.VERTICAL);
        linearLayout1.setPadding(AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10), 0);
        container.addView(linearLayout1, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        codeTextView = new TextView(context);
        codeTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4));
        codeTextView.setGravity(Gravity.CENTER);
        codeTextView.setTypeface(Typeface.MONOSPACE);
        codeTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        /*codeTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                emojiSelected = !emojiSelected;
                updateEmojiButton(true);
                showHint(false);
            }
        });*/
        linearLayout1.addView(codeTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4));
        textView.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkText));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setLinksClickable(true);
        textView.setClickable(true);
        textView.setGravity(Gravity.CENTER);
        textView.setMovementMethod(new LinkMovementMethodMy());
        linearLayout1.addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        emojiTextView = new TextView(context);
        emojiTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4));
        emojiTextView.setGravity(Gravity.CENTER);
        emojiTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 32);
        container.addView(emojiTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        TLRPC.EncryptedChat encryptedChat = MessagesController.getInstance(currentAccount).getEncryptedChat(chat_id);
        if (encryptedChat != null) {
            IdenticonDrawable drawable = new IdenticonDrawable();
            identiconView.setImageDrawable(drawable);
            drawable.setEncryptedChat(encryptedChat);
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(encryptedChat.user_id);
            SpannableStringBuilder hash = new SpannableStringBuilder();
            StringBuilder emojis = new StringBuilder();
            if (encryptedChat.key_hash.length > 16) {
                String hex = Utilities.bytesToHex(encryptedChat.key_hash);
                for (int a = 0; a < 32; a++) {
                    if (a != 0) {
                        if (a % 8 == 0) {
                            hash.append('\n');
                        } else if (a % 4 == 0) {
                            hash.append(' ');
                        }
                    }
                    hash.append(hex.substring(a * 2, a * 2 + 2));
                    hash.append(' ');
                }
                hash.append("\n");
                for (int a = 0; a < 5; a++) {
                    int num = ((encryptedChat.key_hash[16 + a * 4] & 0x7f) << 24) | ((encryptedChat.key_hash[16 + a * 4 + 1] & 0xff) << 16) | ((encryptedChat.key_hash[16 + a * 4 + 2] & 0xff) << 8) | (encryptedChat.key_hash[16 + a * 4 + 3] & 0xff);
                    if (a != 0) {
                        emojis.append(" ");
                    }
                    emojis.append(EmojiData.emojiSecret[num % EmojiData.emojiSecret.length]);
                }
                emojiText = emojis.toString();
            }
            codeTextView.setText(hash.toString());
            hash.clear();
            hash.append(AndroidUtilities.replaceTags(LocaleController.formatString("EncryptionKeyDescription", R.string.EncryptionKeyDescription, user.first_name, user.first_name)));
            final String url = "telegram.org";
            int index = hash.toString().indexOf(url);
            if (index != -1) {
                hash.setSpan(new URLSpanReplacement(LocaleController.getString(R.string.EncryptionKeyLink)), index, index + url.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            textView.setText(hash);
        }

        updateEmojiButton(false);

        return fragmentView;
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        fixLayout();
    }

    @Override
    public void onResume() {
        super.onResume();
        fixLayout();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.emojiLoaded) {
            if (emojiTextView != null) {
                emojiTextView.invalidate();
            }
        }
    }

    private void updateEmojiButton(boolean animated) {
        if (animatorSet != null) {
            animatorSet.cancel();
            animatorSet = null;
        }
        if (animated) {
            animatorSet = new AnimatorSet();
            animatorSet.playTogether(ObjectAnimator.ofFloat(emojiTextView, "alpha", emojiSelected ? 1.0f : 0.0f),
                    ObjectAnimator.ofFloat(codeTextView, "alpha", emojiSelected ? 0.0f : 1.0f),
                    ObjectAnimator.ofFloat(emojiTextView, "scaleX", emojiSelected ? 1.0f : 0.0f),
                    ObjectAnimator.ofFloat(emojiTextView, "scaleY", emojiSelected ? 1.0f : 0.0f),
                    ObjectAnimator.ofFloat(codeTextView, "scaleX", emojiSelected ? 0.0f : 1.0f),
                    ObjectAnimator.ofFloat(codeTextView, "scaleY", emojiSelected ? 0.0f : 1.0f));
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (animation.equals(animatorSet)) {
                        animatorSet = null;
                    }
                }
            });
            animatorSet.setInterpolator(new DecelerateInterpolator());
            animatorSet.setDuration(150);
            animatorSet.start();
        } else {
            emojiTextView.setAlpha(emojiSelected ? 1.0f : 0.0f);
            codeTextView.setAlpha(emojiSelected ? 0.0f : 1.0f);
            emojiTextView.setScaleX(emojiSelected ? 1.0f : 0.0f);
            emojiTextView.setScaleY(emojiSelected ? 1.0f : 0.0f);
            codeTextView.setScaleX(emojiSelected ? 0.0f : 1.0f);
            codeTextView.setScaleY(emojiSelected ? 0.0f : 1.0f);
        }
        emojiTextView.setTag(!emojiSelected ? Theme.key_chat_emojiPanelIcon : Theme.key_chat_emojiPanelIconSelected);
    }

    private void fixLayout() {
        ViewTreeObserver obs = fragmentView.getViewTreeObserver();
        obs.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (fragmentView == null) {
                    return true;
                }
                fragmentView.getViewTreeObserver().removeOnPreDrawListener(this);
                WindowManager manager = (WindowManager) ApplicationLoader.applicationContext.getSystemService(Context.WINDOW_SERVICE);
                int rotation = manager.getDefaultDisplay().getRotation();

                if (rotation == Surface.ROTATION_270 || rotation == Surface.ROTATION_90) {
                    linearLayout.setOrientation(LinearLayout.HORIZONTAL);
                } else {
                    linearLayout.setOrientation(LinearLayout.VERTICAL);
                }

                fragmentView.setPadding(fragmentView.getPaddingLeft(), 0, fragmentView.getPaddingRight(), fragmentView.getPaddingBottom());
                return true;
            }
        });
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        if (isOpen && !backward && emojiText != null) {
            emojiTextView.setText(Emoji.replaceEmoji(emojiText, emojiTextView.getPaint().getFontMetricsInt(), AndroidUtilities.dp(32), false));
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(container, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        themeDescriptions.add(new ThemeDescription(textView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));
        themeDescriptions.add(new ThemeDescription(codeTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));
        themeDescriptions.add(new ThemeDescription(textView, ThemeDescription.FLAG_LINKCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteLinkText));

        return themeDescriptions;
    }
}
