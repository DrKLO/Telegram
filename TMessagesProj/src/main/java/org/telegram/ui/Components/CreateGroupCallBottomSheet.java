package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.GroupCreateUserCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;

import java.util.ArrayList;
import java.util.List;

public class CreateGroupCallBottomSheet extends BottomSheetWithRecyclerListView {
    private static final int
            HOLDER_TYPE_HEADER = 0,
            HOLDER_TYPE_DIVIDER = 1,
            HOLDER_TYPE_SUBTITLE = 2,
            HOLDER_TYPE_USER = 3;

    private static final int CONTENT_VIEWS_COUNT = 3;
    private static final int CONTAINER_HEIGHT_DP = 120;

    public static void show(ArrayList<TLRPC.Peer> peers, BaseFragment fragment, long dialogId, JoinCallAlert.JoinCallAlertDelegate joinCallDelegate) {
        if (peers.isEmpty()) {
            return;
        }
        CreateGroupCallBottomSheet alert = new CreateGroupCallBottomSheet(fragment, peers, dialogId, joinCallDelegate);
        if (fragment != null && fragment.getParentActivity() != null) {
            fragment.showDialog(alert);
        } else {
            alert.show();
        }
    }

    private final JoinCallAlert.JoinCallAlertDelegate joinCallDelegate;
    private final List<TLRPC.Peer> chats;
    private final boolean needSelector;
    private final boolean isChannelOrGiga;
    private boolean isScheduleSelected;
    private TLRPC.Peer selectedPeer;
    private TLRPC.InputPeer selectAfterDismiss;

    public CreateGroupCallBottomSheet(BaseFragment fragment, ArrayList<TLRPC.Peer> arrayList, long dialogId, JoinCallAlert.JoinCallAlertDelegate joinCallDelegate) {
        super(fragment, false, false);
        TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
        this.topPadding = 0.26f;
        this.chats = new ArrayList<>(arrayList);
        this.joinCallDelegate = joinCallDelegate;
        this.isChannelOrGiga = ChatObject.isChannelOrGiga(chat);
        this.selectedPeer = chats.get(0);
        this.needSelector = chats.size() > 1;

        Context context = containerView.getContext();
        View divider = new View(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                if (needSelector) {
                    canvas.drawRect(backgroundPaddingLeft, 0, getMeasuredWidth() - backgroundPaddingLeft, 1, Theme.dividerPaint);
                }
            }
        };
        containerView.addView(divider, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, CONTAINER_HEIGHT_DP, Gravity.BOTTOM, 0, 0, 0, 0));

        TextView startBtn = new TextView(context);
        startBtn.setGravity(Gravity.CENTER);
        startBtn.setEllipsize(TextUtils.TruncateAt.END);
        startBtn.setSingleLine(true);
        startBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        startBtn.setTypeface(AndroidUtilities.bold());
        startBtn.setText(isChannelOrGiga
                ? LocaleController.formatString("VoipChannelStartVoiceChat", R.string.VoipChannelStartVoiceChat)
                : LocaleController.formatString("VoipGroupStartVoiceChat", R.string.VoipGroupStartVoiceChat)
        );
        startBtn.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        startBtn.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(8), Theme.getColor(Theme.key_featuredStickers_addButton), ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_windowBackgroundWhite), 120)));
        containerView.addView(startBtn, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM, 16, 0, 16, 6 + 48 + 6));

        TextView scheduleBtn = new TextView(context);
        scheduleBtn.setGravity(Gravity.CENTER);
        scheduleBtn.setEllipsize(TextUtils.TruncateAt.END);
        scheduleBtn.setSingleLine(true);
        scheduleBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        scheduleBtn.setTypeface(AndroidUtilities.bold());
        scheduleBtn.setText(isChannelOrGiga
                ? LocaleController.formatString("VoipChannelScheduleVoiceChat", R.string.VoipChannelScheduleVoiceChat)
                : LocaleController.formatString("VoipGroupScheduleVoiceChat", R.string.VoipGroupScheduleVoiceChat)
        );
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            scheduleBtn.setLetterSpacing(0.025f);
        }
        scheduleBtn.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton));
        scheduleBtn.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(8), Color.TRANSPARENT, ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_featuredStickers_addButton), 120)));
        containerView.addView(scheduleBtn, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM, 16, 0, 16, 6));

        startBtn.setOnClickListener(view -> {
            selectAfterDismiss = MessagesController.getInstance(currentAccount).getInputPeer(MessageObject.getPeerId(selectedPeer));
            dismiss();
        });
        scheduleBtn.setOnClickListener(view -> {
            selectAfterDismiss = MessagesController.getInstance(currentAccount).getInputPeer(MessageObject.getPeerId(selectedPeer));
            isScheduleSelected = true;
            dismiss();
        });

        recyclerListView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, AndroidUtilities.dp(CONTAINER_HEIGHT_DP));
        recyclerListView.setOnItemClickListener((view, position) -> {
            if (position <= CONTENT_VIEWS_COUNT) {
                return;
            }
            selectedPeer = chats.get(position - CONTENT_VIEWS_COUNT - 1);
            if (view instanceof GroupCreateUserCell) {
                ((GroupCreateUserCell) view).setChecked(true, true);
            }
            for (int i = 0; i < recyclerListView.getChildCount(); i++) {
                View child = recyclerListView.getChildAt(i);
                if (child != view) {
                    if (child instanceof GroupCreateUserCell) {
                        ((GroupCreateUserCell) child).setChecked(false, true);
                    }
                }
            }
        });

        fixNavigationBar();
        updateTitle();
    }

    @Override
    public void dismissInternal() {
        super.dismissInternal();
        if (selectAfterDismiss != null) {
            joinCallDelegate.didSelectChat(selectAfterDismiss, chats.size() > 1, isScheduleSelected);
        }
    }

    @Override
    protected CharSequence getTitle() {
        if (isChannelOrGiga) {
            return LocaleController.getString(R.string.StartVoipChannelTitle);
        } else {
            return LocaleController.getString(R.string.StartVoipChatTitle);
        }
    }

    @Override
    public RecyclerListView.SelectionAdapter createAdapter(RecyclerListView listView) {
        return new RecyclerListView.SelectionAdapter() {
            @Override
            public boolean isEnabled(RecyclerView.ViewHolder holder) {
                return holder.getItemViewType() == HOLDER_TYPE_USER;
            }

            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view;
                Context context = parent.getContext();
                switch (viewType) {
                    default:
                    case HOLDER_TYPE_HEADER:
                        view = new TopCell(context, isChannelOrGiga);
                        break;
                    case HOLDER_TYPE_DIVIDER:
                        view = new ShadowSectionCell(context, 12, Theme.getColor(Theme.key_windowBackgroundGray));
                        break;
                    case HOLDER_TYPE_SUBTITLE:
                        view = new HeaderCell(context, 22);
                        break;
                    case HOLDER_TYPE_USER:
                        view = new GroupCreateUserCell(context, 1, 0, false);
                        break;
                }
                view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return new RecyclerListView.Holder(view);
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                if (holder.getItemViewType() == HOLDER_TYPE_USER) {
                    TLRPC.Peer peer = chats.get(position - CONTENT_VIEWS_COUNT);
                    long did = MessageObject.getPeerId(peer);
                    TLObject object;
                    String status;
                    if (did > 0) {
                        object = MessagesController.getInstance(currentAccount).getUser(did);
                        status = LocaleController.getString(R.string.VoipGroupPersonalAccount);
                    } else {
                        object = MessagesController.getInstance(currentAccount).getChat(-did);
                        status = null;
                    }
                    GroupCreateUserCell cell = (GroupCreateUserCell) holder.itemView;
                    cell.setObject(object, null, status, position != getItemCount() - 1);
                    cell.setChecked(peer == selectedPeer, false);
                } else if (holder.getItemViewType() == HOLDER_TYPE_SUBTITLE) {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    cell.setTextSize(15);
                    cell.setPadding(0, 0, 0, AndroidUtilities.dp(2));
                    cell.setText(LocaleController.getString(R.string.VoipChatDisplayedAs).replace(":", ""));
                }
            }

            @Override
            public int getItemViewType(int position) {
                switch (position) {
                    case 0:
                        return HOLDER_TYPE_HEADER;
                    case 1:
                        return HOLDER_TYPE_DIVIDER;
                    case 2:
                        return HOLDER_TYPE_SUBTITLE;
                    default:
                        return HOLDER_TYPE_USER;
                }
            }

            @Override
            public int getItemCount() {
                return needSelector ? CONTENT_VIEWS_COUNT + chats.size() : 1;
            }
        };
    }

    private static class TopCell extends LinearLayout {

        public TopCell(Context context, boolean isChannelOrGiga) {
            super(context);
            setOrientation(LinearLayout.VERTICAL);

            RLottieImageView imageView = new RLottieImageView(context);
            imageView.setAutoRepeat(true);
            imageView.setAnimation(R.raw.utyan_schedule, 112, 112);
            imageView.playAnimation();
            addView(imageView, LayoutHelper.createLinear(112, 112, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 24, 0, 0));

            TextView title = new TextView(context);
            title.setTypeface(AndroidUtilities.bold());
            title.setText(isChannelOrGiga
                    ? LocaleController.formatString("StartVoipChannelTitle", R.string.StartVoipChannelTitle)
                    : LocaleController.formatString("StartVoipChatTitle", R.string.StartVoipChatTitle)
            );
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
            title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            addView(title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 14, 0, 7));

            TextView description = new TextView(context);
            description.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            description.setGravity(Gravity.CENTER_HORIZONTAL);
            description.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            description.setText(isChannelOrGiga
                    ? LocaleController.formatString("VoipChannelStart2", R.string.VoipChannelStart2)
                    : LocaleController.formatString("VoipGroupStart2", R.string.VoipGroupStart2)
            );
            description.setLineSpacing(description.getLineSpacingExtra(), description.getLineSpacingMultiplier() * 1.1f);
            addView(description, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 28, 0, 28, 17));
        }
    }
}
