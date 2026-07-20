package org.telegram.ui.community.cells;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.utils.RenderNodeEffects;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceColor;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceRenderNode;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

@SuppressLint("ViewConstructor")
public class CommunityPendingRequestCell extends FrameLayout implements Theme.Colorable {
    private final Theme.ResourcesProvider resourcesProvider;
    private final int currentAccount;

    public final BackupImageView avatarView;
    public final TextView membersCountView;
    public final BackupImageView requesterAvatarView;
    public final TextView titleView;
    public final TextView subtitleView;
    public final TextView hiddenLabelView;
    private final ButtonWithCounterView declineButton;
    private final ButtonWithCounterView addButton;
    private final ColoredImageSpan span;

    private final BlurredBackgroundSourceRenderNode sourceRenderNode;
    private BlurredBackgroundDrawable blurredBackgroundDrawable;
    private boolean needDivider;

    public CommunityPendingRequestCell(Context context, Theme.ResourcesProvider resourcesProvider, int currentAccount) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        this.currentAccount = currentAccount;

        final BlurredBackgroundDrawableViewFactory factory;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            sourceRenderNode = new BlurredBackgroundSourceRenderNode(null);
            sourceRenderNode.setBlur(dp(7), RenderNodeEffects.createSaturationXRenderEffect(1.125f));
            sourceRenderNode.noClip();
            factory = new BlurredBackgroundDrawableViewFactory(sourceRenderNode);
        } else {
            sourceRenderNode = null;
            final BlurredBackgroundSourceColor sourceColor = new BlurredBackgroundSourceColor();
            sourceColor.setColor(Color.BLACK);
            factory = new BlurredBackgroundDrawableViewFactory(sourceColor);
        }

        final FrameLayout avatarWrapper = new FrameLayout(context);

        avatarView = new BackupImageView(context);
        avatarView.setRoundRadius(dp(52) / 2);
        addView(avatarView, LayoutHelper.createFrame(52, 52, Gravity.TOP | Gravity.LEFT, 11, 9, 0, 0));


        span = new ColoredImageSpan(R.drawable.mini_user_channels_10);
        span.setTranslateX(dp(2));

        membersCountView = new TextView(context);
        membersCountView.setTypeface(AndroidUtilities.bold());
        membersCountView.setVisibility(GONE);
        membersCountView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 9.33f);
        membersCountView.setTextColor(0xFFFFFFFF);
        membersCountView.setGravity(Gravity.CENTER);
        membersCountView.setPadding(dp(1), 0, dp(5), 0);
        avatarWrapper.addView(membersCountView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
        addView(avatarWrapper, LayoutHelper.createLinear(52, 14.33f, Gravity.TOP, 11, 48, 0, 0));

        blurredBackgroundDrawable = factory.create(membersCountView)
            .setColorProvider(BlurredBackgroundProviderImpl.counterMini(resourcesProvider))
            .setRadius(dp(7));
        membersCountView.setBackground(blurredBackgroundDrawable);

        final LinearLayout rightBlock = new LinearLayout(context);
        rightBlock.setOrientation(LinearLayout.VERTICAL);
        rightBlock.setClipChildren(false);

        titleView = new TextView(context);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        rightBlock.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                0, 10, 0, 1.33f));



        requesterAvatarView = new BackupImageView(context);
        requesterAvatarView.setRoundRadius(dp(8));
        requesterAvatarView.setOnClickListener(v -> {
            if (delegate != null) {
                delegate.onClickGroupOwner(userDialogId);
            }
        });
        addView(requesterAvatarView, LayoutHelper.createFrame(16, 16, Gravity.LEFT | Gravity.TOP, 75, 35, 0, 0));

        subtitleView = new TextView(context);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subtitleView.setSingleLine(true);
        subtitleView.setEllipsize(TextUtils.TruncateAt.END);
        subtitleView.setOnClickListener(v -> {
            if (delegate != null) {
                delegate.onClickGroupOwner(userDialogId);
            }
        });
        rightBlock.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 20, 0, 0, 1.33f));

        hiddenLabelView = new TextView(context);
        hiddenLabelView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        hiddenLabelView.setBackground(Theme.createRoundRectDrawable(dp(12), Theme.multAlpha(
                Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6, resourcesProvider), 0.14f)));
        hiddenLabelView.setPadding(dp(4), dp(1), dp(8), dp(1.66f));
        hiddenLabelView.setSingleLine(true);
        {
            SpannableStringBuilder ssb = new SpannableStringBuilder();
            ssb.append("* ");
            ssb.setSpan(new ColoredImageSpan(R.drawable.mini_ephemeral_hidden_14), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.append(getString(R.string.CommunityPendingRequestOnlyVisibleToMembers));
            hiddenLabelView.setText(ssb);
        }

        hiddenLabelView.setVisibility(GONE);
        rightBlock.addView(hiddenLabelView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                0, 7, 0, 1.33f));

        final LinearLayout buttonsRow = new LinearLayout(context);
        buttonsRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonsRow.setClipChildren(false);

        declineButton = new ButtonWithCounterView(context, resourcesProvider);
        declineButton.setUseWrapContent(true);
        declineButton.setPadding(dp(15), 0, dp(15), 0);
        declineButton.setRound();
        declineButton.setNeutral();
        declineButton.setColor(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider), 0.14f));
        declineButton.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        declineButton.setText(getString(R.string.Decline), false);
        declineButton.setOnClickListener(v -> {
            if (delegate != null) {
                delegate.onClickDecline(groupDialogId);
            }
        });
        buttonsRow.addView(declineButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 30, 0,
                Gravity.CENTER_VERTICAL, 0, 0, 4, 0));

        addButton = new ButtonWithCounterView(context, resourcesProvider);
        addButton.setUseWrapContent(true);
        addButton.setPadding(dp(15), 0, dp(15), 0);
        addButton.setRound();
        addButton.setText(getString(R.string.Add), false);
        addButton.setOnClickListener(v -> {
            if (delegate != null) {
                delegate.onClickApprove(groupDialogId);
            }
        });
        buttonsRow.addView(addButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 30, 0,
                Gravity.CENTER_VERTICAL, 4, 0, 0, 0));

        rightBlock.addView(buttonsRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.NO_GRAVITY, 0, 10, 0, 0));

        addView(rightBlock, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 75, 0, 0, 13));

        updateColors();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        blurredBackgroundDrawable.setSourceOffset(membersCountView.getLeft() + dp(9), dp(48));
    }

    @Override
    protected boolean drawChild(@NonNull Canvas canvas, View child, long drawingTime) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && sourceRenderNode != null && child == avatarView) {
            final int p = dp(9);
            final int x = avatarView.getLeft() - p;
            final int y = avatarView.getTop() - p;
            final int s = dp(52) + p * 2;

            Canvas c = sourceRenderNode.beginRecording(s, s);
            c.translate(-x, -y);
            c.drawColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
            c.save();
            c.scale(1.125f, 1.125f, s / 2f, s / 2f);
            super.drawChild(c, child, drawingTime);
            c.restore();
            c.drawColor(0x20000000);

            sourceRenderNode.endRecording();
        }

        return super.drawChild(canvas, child, drawingTime);
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        if (needDivider) {
            canvas.drawLine(dp(76), getHeight() - 1, getMeasuredWidth(), getHeight() - 1, Theme.dividerPaint);
        }
        super.dispatchDraw(canvas);
    }

    ClickDelegate delegate;
    long groupDialogId;
    long userDialogId;

    private void set(
            long dialogId,
            TLRPC.User user,
            ClickDelegate delegate,
            boolean isHidden,
            boolean needDivider) {
        this.delegate = delegate;
        groupDialogId = dialogId;
        userDialogId = user.id;

        final TLRPC.Chat chatToAdd = MessagesController.getInstance(currentAccount).getChat(-dialogId);
        final TLRPC.User userToAdd = MessagesController.getInstance(currentAccount).getUser(dialogId);

        titleView.setText(DialogObject.getName(dialogId));
        subtitleView.setText(AndroidUtilities.replaceSingleLink(
            formatString(userToAdd != null ?
                R.string.CommunityPendingRequestSuggestedBot : ChatObject.isChannelAndNotMegaGroup(chatToAdd) ?
                R.string.CommunityPendingRequestSuggestedChannel :
                R.string.CommunityPendingRequestSuggestedGroup, DialogObject.getShortName(user)),
            Theme.getColor(Theme.key_telegram_color_text), () -> {
                //if (delegate != null) {
                //    delegate.onClickGroupOwner(userDialogId);
                //}
            }
        ));

        if (userToAdd != null) {
            membersCountView.setVisibility(GONE);
        } else if (chatToAdd != null && chatToAdd.participants_count > 0) {
            SpannableStringBuilder ssb = new SpannableStringBuilder("* ");
            ssb.setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.append(LocaleController.formatNumberWithMillion(chatToAdd.participants_count, ','));
            membersCountView.setText(ssb);
            membersCountView.setVisibility(VISIBLE);
        } else {
            membersCountView.setVisibility(GONE);
        }

        if (isHidden) {
            hiddenLabelView.setVisibility(VISIBLE);
        } else {
            hiddenLabelView.setVisibility(GONE);
        }


        this.needDivider = needDivider;
        if (userToAdd != null) {
            avatarView.setForUserOrChat(userToAdd, new AvatarDrawable(userToAdd));
        } else {
            avatarView.setForUserOrChat(chatToAdd, new AvatarDrawable(chatToAdd));
        }
        requesterAvatarView.setForUserOrChat(user, new AvatarDrawable(user));
    }

    @Override
    public void updateColors() {
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
        hiddenLabelView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
    }

    public interface ClickDelegate {
        void onClickApprove(long dialogId);
        void onClickDecline(long dialogId);
        void onClickGroupOwner(long dialogId);
    }

    public static class Data {
        public final long dialogToAdd;
        public final TLRPC.User requestFromUser;
        public final boolean isHidden;

        private Data(long dialogToToAdd, TLRPC.User requestFromUser, boolean isHidden) {
            this.dialogToAdd = dialogToToAdd;
            this.requestFromUser = requestFromUser;
            this.isHidden = isHidden;
        }
    }

    public static class Factory extends UItem.UItemFactory<CommunityPendingRequestCell> {
        static {
            setup(new Factory());
        }

        @Override
        public CommunityPendingRequestCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            CommunityPendingRequestCell communityPendingRequestCell = new CommunityPendingRequestCell(context, resourcesProvider, currentAccount);
            communityPendingRequestCell.setLayoutParams(new RecyclerView.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            communityPendingRequestCell.setClickable(false);
            return communityPendingRequestCell;
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
            final CommunityPendingRequestCell communityPendingRequestCell = (CommunityPendingRequestCell) view;
            Data d = (Data) item.object;

            final TLRPC.User user = d.requestFromUser;
            communityPendingRequestCell.set(d.dialogToAdd, user, (ClickDelegate) item.object2, d.isHidden, !item.hideDivider);
        }

        @Override
        public boolean equals(UItem a, UItem b) {
            Data da = (Data) a.object;
            Data db = (Data) b.object;

            return da.dialogToAdd == db.dialogToAdd
                && DialogObject.getDialogId(da.requestFromUser) == DialogObject.getDialogId(db.requestFromUser);
        }

        public static UItem asPendingRequest(
            long dialogToAdd,
            TLRPC.User requestFromUser,
            boolean isHidden,
            ClickDelegate delegate,
            boolean divider
        ) {
            final UItem item = UItem.ofFactory(Factory.class);
            item.object = new Data(dialogToAdd, requestFromUser, isHidden);
            item.object2 = delegate;
            item.hideDivider = !divider;
            return item;
        }
    }
}