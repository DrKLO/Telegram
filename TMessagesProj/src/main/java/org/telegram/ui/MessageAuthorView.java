package org.Tajgram.ui;

import static org.Tajgram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.Pair;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.Tajgram.messenger.AndroidUtilities;
import org.Tajgram.messenger.ChatObject;
import org.Tajgram.messenger.ContactsController;
import org.Tajgram.messenger.ImageLocation;
import org.Tajgram.messenger.LocaleController;
import org.Tajgram.messenger.MessageObject;
import org.Tajgram.messenger.MessagesController;
import org.Tajgram.messenger.NotificationCenter;
import org.Tajgram.messenger.R;
import org.Tajgram.messenger.UserConfig;
import org.Tajgram.messenger.UserObject;
import org.Tajgram.tgnet.ConnectionsManager;
import org.Tajgram.tgnet.TLObject;
import org.Tajgram.tgnet.TLRPC;
import org.Tajgram.tgnet.Vector;
import org.Tajgram.ui.ActionBar.SimpleTextView;
import org.Tajgram.ui.ActionBar.Theme;
import org.Tajgram.ui.Components.AvatarDrawable;
import org.Tajgram.ui.Components.AvatarsDrawable;
import org.Tajgram.ui.Components.AvatarsImageView;
import org.Tajgram.ui.Components.BackupImageView;
import org.Tajgram.ui.Components.FlickerLoadingView;
import org.Tajgram.ui.Components.HideViewAfterAnimation;
import org.Tajgram.ui.Components.LayoutHelper;
import org.Tajgram.ui.Components.LinkSpanDrawable;
import org.Tajgram.ui.Components.MessageSeenCheckDrawable;
import org.Tajgram.ui.Components.RecyclerListView;
import org.Tajgram.ui.Components.StatusBadgeComponent;

import java.util.ArrayList;
import java.util.HashMap;

public class MessageAuthorView extends FrameLayout {

    public TLRPC.User user = null;
    LinkSpanDrawable.LinksTextView titleView;
    int currentAccount;
    boolean isVoice;

    FlickerLoadingView flickerLoadingView;

    public MessageAuthorView(@NonNull Context context, int currentAccount, MessageObject messageObject, TLRPC.Chat chat) {
        super(context);
        this.currentAccount = currentAccount;
        isVoice = (messageObject.isRoundVideo() || messageObject.isVoice());
        flickerLoadingView = new FlickerLoadingView(context);
        flickerLoadingView.setColors(Theme.key_actionBarDefaultSubmenuBackground, Theme.key_listSelector, -1);
        flickerLoadingView.setViewType(FlickerLoadingView.MESSAGE_SEEN_TYPE);
        flickerLoadingView.setIsSingleCell(false);
        addView(flickerLoadingView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT));

        titleView = new LinkSpanDrawable.LinksTextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        titleView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        titleView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem));
        titleView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn));
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setSingleLine();
        titleView.setLines(1);
        titleView.setMaxLines(1);
        addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 12, 0, 12, 0));

        TLRPC.TL_channels_getMessageAuthor req = new TLRPC.TL_channels_getMessageAuthor();
        req.channel = MessagesController.getInstance(currentAccount).getInputChannel(-messageObject.getDialogId());
        req.id = messageObject.getId();

        titleView.setAlpha(0);
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (response instanceof TLRPC.User) {
                user = (TLRPC.User) response;
                MessagesController.getInstance(currentAccount).putUser(user, false);
            }
            updateView();
        }));
        setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_dialogButtonSelector), 6, 0));
        setEnabled(false);
    }

    boolean ignoreLayout;

    @Override
    public void requestLayout() {
        if (ignoreLayout) {
            return;
        }
        super.requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        View parent = (View) getParent();
        if (parent != null && parent.getWidth() > 0) {
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(parent.getWidth(), MeasureSpec.EXACTLY);
        }
        heightMeasureSpec = MeasureSpec.makeMeasureSpec(dp(36), MeasureSpec.EXACTLY);
        ignoreLayout = true;
        boolean measureFlicker = flickerLoadingView.getVisibility() == View.VISIBLE;
        titleView.setVisibility(View.GONE);
        if (measureFlicker) {
            flickerLoadingView.setVisibility(View.GONE);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (measureFlicker) {
            flickerLoadingView.getLayoutParams().width = getMeasuredWidth();
            flickerLoadingView.setVisibility(View.VISIBLE);
        }
        titleView.setVisibility(View.VISIBLE);
        titleView.getLayoutParams().width = getMeasuredWidth() - dp(24);
        ignoreLayout = false;

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private void updateView() {
        setEnabled(user != null);
        if (user != null) {
            final long userId = user.id;
            titleView.setText(AndroidUtilities.premiumText(LocaleController.formatString(R.string.MessageAuthorSentBy, UserObject.getUserName(user)), () -> openUser(userId)));
        }
        titleView.animate().alpha(1f).setDuration(220).start();
        flickerLoadingView.animate().alpha(0f).setDuration(220).setListener(new HideViewAfterAnimation(flickerLoadingView)).start();
    }

    protected void openUser(long userId) {

    }
}
