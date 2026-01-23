package org.telegram.ui.Stories.recorder;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;

public class SelectPeerView extends FrameLayout {

    private final int currentAccount;
    private final AvatarDrawable avatarDrawable;
    private final BackupImageView imageView;
    private final TextView titleView;
    private final TextView subtitleView;

    public SelectPeerView(Context context, int currentAccount) {
        super(context);

        this.currentAccount = currentAccount;

        avatarDrawable = new AvatarDrawable();
        imageView = new BackupImageView(context);
        imageView.setRoundRadius(dp(15));
        addView(imageView, LayoutHelper.createFrame(30, 30, Gravity.CENTER_VERTICAL | Gravity.LEFT, 14, 0, 0, 0));

        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        titleView.setTextColor(0xFFFFFFFF);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setSingleLine();
        titleView.setLines(1);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 53, 11.33f, 12, 0));

        subtitleView = new TextView(context);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        subtitleView.setTextColor(Theme.multAlpha(0xFFFFFFFF, 0.85f));
        addView(subtitleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 53, 29.33f, 12, 0));
        subtitleView.setText(AndroidUtilities.replaceArrows(getString(R.string.LiveStoryPeerChange), false, dp(8f / 3f), dp(0.33f), 1.0f));

        set(null);
    }

    public void set(TLRPC.InputPeer peer) {
        final long did = peer == null ?
            UserConfig.getInstance(currentAccount).getClientUserId() :
            DialogObject.getPeerDialogId(peer);

        if (did >= 0) {
            final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(did);
            avatarDrawable.setInfo(user);
            imageView.setForUserOrChat(user, avatarDrawable);

            titleView.setText(UserObject.getUserName(user));
        } else {
            final TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-did);
            avatarDrawable.setInfo(chat);
            imageView.setForUserOrChat(chat, avatarDrawable);

            titleView.setText(chat == null ? "" : chat.title);
        }
    }

    private ViewPropertyAnimator showAnimator;
    public void setShowing(boolean show, boolean animated) {
        if (showAnimator != null) {
            showAnimator.cancel();
            showAnimator = null;
        }
        if (animated) {
            setVisibility(View.VISIBLE);
            showAnimator = animate()
                .alpha(show ? 1.0f : 0.0f)
                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                .setDuration(320);
            showAnimator.start();
        } else {
            setVisibility(show ? View.VISIBLE : View.GONE);
            setAlpha(show ? 1.0f : 0.0f);
        }
    }

}
