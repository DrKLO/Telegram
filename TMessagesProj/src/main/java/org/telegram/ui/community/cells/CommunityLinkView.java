package org.telegram.ui.community.cells;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.formatPluralString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.utils.DrawableUtils;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;

@SuppressLint("ViewConstructor")
public class CommunityLinkView extends FrameLayout implements Theme.Colorable {

    public final Theme.ResourcesProvider resourcesProvider;
    public final BackupImageView avatarView;
    public final TextView titleView;
    public final TextView subtitleView;
    private final ImageView arrowView;

    public CommunityLinkView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        avatarView = new BackupImageView(context);
        avatarView.setRoundRadius(dp(9));
        addView(avatarView, LayoutHelper.createFrame(32, 32, Gravity.LEFT | Gravity.CENTER_VERTICAL, 20, 0, 0, 0));

        final LinearLayout textBlock = new LinearLayout(context);
        textBlock.setOrientation(LinearLayout.VERTICAL);
        textBlock.setGravity(Gravity.CENTER_VERTICAL);

        titleView = new TextView(context);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        textBlock.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        subtitleView = new TextView(context);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subtitleView.setSingleLine(true);
        subtitleView.setEllipsize(TextUtils.TruncateAt.END);
        textBlock.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        addView(textBlock, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.LEFT | Gravity.CENTER_VERTICAL, 67, 0, 48, 1));

        arrowView = new ImageView(context);
        arrowView.setImageResource(R.drawable.msg_inputarrow);

        arrowView.setScaleType(ImageView.ScaleType.CENTER);
        addView(arrowView, LayoutHelper.createFrame(24, 24, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 11, 0));

        updateColors();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(60), MeasureSpec.EXACTLY));
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        DrawableUtils.drawCommunityCardDrawable(canvas, Theme.dialogs_communityCardsDrawable, avatarView.getLeft() + avatarView.getWidth() / 2f, avatarView.getTop() + avatarView.getHeight() / 2f, avatarView.getHeight());
        super.dispatchDraw(canvas);
    }

    public void setTitle(CharSequence text) {
        titleView.setText(text);
    }

    public void setSubtitle(CharSequence text) {
        subtitleView.setText(text);
    }

    public void setChat(int currentAccount, TLRPC.Chat chat) {
        if (chat == null) {
            return;
        }

        TLRPC.ChatFull chatFull = MessagesController.getInstance(currentAccount).getChatFull(chat.id);
        setTitle(DialogObject.getShortName(chat));
        setSubtitle(formatPluralString("CommunityWithChats", chatFull != null ? chatFull.linked_peers.size() : 0));
        avatarView.setForUserOrChat(chat, new AvatarDrawable(chat));
    }

    @Override
    public void updateColors() {
        arrowView.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
    }
}