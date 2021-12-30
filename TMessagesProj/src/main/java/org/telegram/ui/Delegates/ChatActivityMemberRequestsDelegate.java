package org.telegram.ui.Delegates;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.AvatarsImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MemberRequestsBottomSheet;

import java.util.List;

public class ChatActivityMemberRequestsDelegate {

    private final BaseFragment fragment;
    private final Callback callback;
    private final TLRPC.Chat currentChat;
    private final int currentAccount;

    private FrameLayout root;
    private AvatarsImageView avatarsView;
    private TextView requestsCountTextView;
    private ImageView closeView;

    @Nullable
    private MemberRequestsBottomSheet bottomSheet;
    @Nullable
    private TLRPC.ChatFull chatInfo;
    @Nullable
    private ValueAnimator pendingRequestsAnimator;
    private float pendingRequestsEnterOffset;
    private int pendingRequestsCount;
    private int closePendingRequestsCount = -1;

    public ChatActivityMemberRequestsDelegate(BaseFragment fragment, TLRPC.Chat currentChat, Callback callback) {
        this.fragment = fragment;
        this.currentChat = currentChat;
        this.currentAccount = fragment.getCurrentAccount();
        this.callback = callback;
    }

    public View getView() {
        if (root == null) {
            root = new FrameLayout(fragment.getParentActivity());
            root.setBackgroundResource(R.drawable.blockpanel);
            root.getBackground().mutate().setColorFilter(new PorterDuffColorFilter(fragment.getThemedColor(Theme.key_chat_topPanelBackground), PorterDuff.Mode.MULTIPLY));
            root.setVisibility(View.GONE);
            pendingRequestsEnterOffset = -getViewHeight();

            View pendingRequestsSelector = new View(fragment.getParentActivity());
            pendingRequestsSelector.setBackground(Theme.getSelectorDrawable(false));
            pendingRequestsSelector.setOnClickListener((v) -> showBottomSheet());
            root.addView(pendingRequestsSelector, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 0, 0, 0, 2));

            LinearLayout requestsDataLayout = new LinearLayout(fragment.getParentActivity());
            requestsDataLayout.setOrientation(LinearLayout.HORIZONTAL);
            root.addView(requestsDataLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP, 0, 0, 36, 0));

            avatarsView = new AvatarsImageView(fragment.getParentActivity(), false) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    int width = avatarsDarawable.count == 0 ? 0 : (20 * (avatarsDarawable.count - 1) + 24);
                    super.onMeasure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(width), MeasureSpec.EXACTLY), heightMeasureSpec);
                }
            };
            avatarsView.reset();
            requestsDataLayout.addView(avatarsView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP, 8, 0, 10, 0));

            requestsCountTextView = new TextView(fragment.getParentActivity());
            requestsCountTextView.setEllipsize(TextUtils.TruncateAt.END);
            requestsCountTextView.setGravity(Gravity.CENTER_VERTICAL);
            requestsCountTextView.setSingleLine();
            requestsCountTextView.setText(null);
            requestsCountTextView.setTextColor(fragment.getThemedColor(Theme.key_chat_topPanelTitle));
            requestsCountTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            requestsDataLayout.addView(requestsCountTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP, 0, 0, 0, 0));

            closeView = new ImageView(fragment.getParentActivity());
            if (Build.VERSION.SDK_INT >= 21) {
                closeView.setBackground(Theme.createSelectorDrawable(fragment.getThemedColor(Theme.key_inappPlayerClose) & 0x19ffffff, 1, AndroidUtilities.dp(14)));
            }
            closeView.setColorFilter(new PorterDuffColorFilter(fragment.getThemedColor(Theme.key_chat_topPanelClose), PorterDuff.Mode.MULTIPLY));
            closeView.setContentDescription(LocaleController.getString("Close", R.string.Close));
            closeView.setImageResource(R.drawable.miniplayer_close);
            closeView.setScaleType(ImageView.ScaleType.CENTER);
            closeView.setOnClickListener((v) -> {
                fragment.getMessagesController().setChatPendingRequestsOnClose(currentChat.id, pendingRequestsCount);
                closePendingRequestsCount = pendingRequestsCount;
                animatePendingRequests(false, true);
            });
            root.addView(closeView, LayoutHelper.createFrame(36, LayoutHelper.MATCH_PARENT, Gravity.RIGHT | Gravity.TOP, 0, 0, 2, 0));
            if (chatInfo != null) {
                setPendingRequests(chatInfo.requests_pending, chatInfo.recent_requesters, false);
            }
        }
        return root;
    }

    public void setChatInfo(@Nullable TLRPC.ChatFull chatInfo, boolean animated) {
        this.chatInfo = chatInfo;
        if (chatInfo != null) {
            setPendingRequests(chatInfo.requests_pending, chatInfo.recent_requesters, animated);
        }
    }

    public int getViewHeight() {
        return AndroidUtilities.dp(40);
    }

    public float getViewEnterOffset() {
        return pendingRequestsEnterOffset;
    }

    public void onBackToScreen() {
        if (bottomSheet != null && bottomSheet.isNeedRestoreDialog()) {
            showBottomSheet();
        }
    }

    private void showBottomSheet() {
        if (bottomSheet == null) {
            bottomSheet = new MemberRequestsBottomSheet(fragment, currentChat.id) {
                @Override
                public void dismiss() {
                    if (bottomSheet != null && !bottomSheet.isNeedRestoreDialog()) {
                        bottomSheet = null;
                    }
                    super.dismiss();
                }
            };
        }
        fragment.showDialog(bottomSheet);
    }

    private void setPendingRequests(int count, List<Long> recentRequestersIdList, boolean animated) {
        if (root == null) {
            return;
        }
        if (count <= 0) {
            if (currentChat != null) {
                fragment.getMessagesController().setChatPendingRequestsOnClose(currentChat.id, 0);
                closePendingRequestsCount = 0;
            }
            animatePendingRequests(false, animated);
            pendingRequestsCount = 0;
            return;
        }
        if (pendingRequestsCount != count) {
            pendingRequestsCount = count;
            requestsCountTextView.setText(LocaleController.formatPluralString("JoinUsersRequests", count));
            animatePendingRequests(true, animated);

            if (recentRequestersIdList != null && !recentRequestersIdList.isEmpty()) {
                int usersCount = Math.min(3, recentRequestersIdList.size());
                for (int i = 0; i < usersCount; ++i) {
                    TLRPC.User user = fragment.getMessagesController().getUser(recentRequestersIdList.get(i));
                    if (user != null) {
                        avatarsView.setObject(i, currentAccount, user);
                    }
                }
                avatarsView.setCount(usersCount);
                avatarsView.commitTransition(true);
            }
        }
    }

    private void animatePendingRequests(boolean appear, boolean animated) {
        boolean isVisibleNow = root.getVisibility() == View.VISIBLE;
        if (appear == isVisibleNow) {
            return;
        }
        if (appear) {
            if (closePendingRequestsCount == -1 && currentChat != null) {
                closePendingRequestsCount = fragment.getMessagesController().getChatPendingRequestsOnClosed(currentChat.id);
            }
            if (pendingRequestsCount == closePendingRequestsCount) {
                return;
            }
            if (closePendingRequestsCount != 0 && currentChat != null) {
                fragment.getMessagesController().setChatPendingRequestsOnClose(currentChat.id, 0);
            }
        }
        if (pendingRequestsAnimator != null) {
            pendingRequestsAnimator.cancel();
        }
        if (animated) {
            pendingRequestsAnimator = ValueAnimator.ofFloat(appear ? 0f : 1f, appear ? 1f : 0f);
            pendingRequestsAnimator.addUpdateListener(animation -> {
                float progress = (float) animation.getAnimatedValue();
                pendingRequestsEnterOffset = -getViewHeight() * (1f - progress);
                if (callback != null) {
                    callback.onEnterOffsetChanged();
                }
            });
            pendingRequestsAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    if (appear) {
                        root.setVisibility(View.VISIBLE);
                    }
                }
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (!appear) {
                        root.setVisibility(View.GONE);
                    }
                }
            });
            pendingRequestsAnimator.setDuration(200);
            pendingRequestsAnimator.start();
        } else {
            root.setVisibility(appear ? View.VISIBLE : View.GONE);
            pendingRequestsEnterOffset = appear ? 0 : -getViewHeight();
            if (callback != null) {
                callback.onEnterOffsetChanged();
            }
        }
    }

    public void fillThemeDescriptions(List<ThemeDescription> themeDescriptions) {
        themeDescriptions.add(new ThemeDescription(root, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_chat_topPanelBackground));
        themeDescriptions.add(new ThemeDescription(requestsCountTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_chat_topPanelTitle));
        themeDescriptions.add(new ThemeDescription(closeView, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_chat_topPanelClose));
    }

    public interface Callback {

        void onEnterOffsetChanged();
    }
}
