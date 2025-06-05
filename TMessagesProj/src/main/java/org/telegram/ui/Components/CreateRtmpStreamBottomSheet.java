package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_phone;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextDetailCell;

import java.util.ArrayList;

public class CreateRtmpStreamBottomSheet extends BottomSheetWithRecyclerListView {

    private static final int CONTAINER_HEIGHT_DP = 72;

    public static void show(TLRPC.Peer peer, BaseFragment fragment, long dialogId, boolean hasFewPeers, JoinCallAlert.JoinCallAlertDelegate joinCallDelegate) {
        CreateRtmpStreamBottomSheet alert = new CreateRtmpStreamBottomSheet(fragment, peer, dialogId, hasFewPeers, joinCallDelegate);
        if (fragment != null && fragment.getParentActivity() != null) {
            fragment.showDialog(alert);
        } else {
            alert.show();
        }
    }

    private final JoinCallAlert.JoinCallAlertDelegate joinCallDelegate;
    private TLRPC.InputPeer selectAfterDismiss;
    private final boolean hasFewPeers;
    private String rtmpUrl;
    private String rtmpKey;
    private UniversalAdapter adapter;

    public CreateRtmpStreamBottomSheet(BaseFragment fragment, TLRPC.Peer selectedPeer, long dialogId, boolean hasFewPeers, JoinCallAlert.JoinCallAlertDelegate joinCallDelegate) {
        super(fragment, false, false);
        this.topPadding = 0.26f;
        this.joinCallDelegate = joinCallDelegate;
        this.hasFewPeers = hasFewPeers;
        Context context = containerView.getContext();
        TextView startBtn = new TextView(context);
        startBtn.setGravity(Gravity.CENTER);
        startBtn.setEllipsize(TextUtils.TruncateAt.END);
        startBtn.setSingleLine(true);
        startBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        startBtn.setTypeface(AndroidUtilities.bold());
        startBtn.setText(LocaleController.getString(R.string.VoipChannelStartStreaming));
        startBtn.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        startBtn.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(8), Theme.getColor(Theme.key_featuredStickers_addButton), ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_windowBackgroundWhite), 120)));
        containerView.addView(startBtn, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM, 16, 0, 16, 12));

        startBtn.setOnClickListener(view -> {
            selectAfterDismiss = MessagesController.getInstance(currentAccount).getInputPeer(MessageObject.getPeerId(selectedPeer));
            dismiss();
        });

        recyclerListView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, AndroidUtilities.dp(CONTAINER_HEIGHT_DP));
        fixNavigationBar();
        updateTitle();

        TL_phone.getGroupCallStreamRtmpUrl req = new TL_phone.getGroupCallStreamRtmpUrl();
        req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
        req.revoke = false;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (response != null) {
                if (response instanceof TL_phone.groupCallStreamRtmpUrl) {
                    TL_phone.groupCallStreamRtmpUrl rtmpUrl = (TL_phone.groupCallStreamRtmpUrl) response;
                    this.rtmpUrl = rtmpUrl.url;
                    this.rtmpKey = rtmpUrl.key;
                    adapter.update(false);
                }
            }
        }));
    }

    @Override
    public void dismissInternal() {
        super.dismissInternal();
        if (selectAfterDismiss != null) {
            joinCallDelegate.didSelectChat(selectAfterDismiss, hasFewPeers, false, true);
        }
    }

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(R.string.Streaming);
    }

    @Override
    public RecyclerListView.SelectionAdapter createAdapter(RecyclerListView listView) {
        adapter = new UniversalAdapter(listView, getContext(), currentAccount, 0, true, this::fillItems, resourcesProvider);
        return adapter;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asCustom(new TopCell(getContext())));
        items.add(UItem.asShadow(null));
        items.add(UItem.asHeader(LocaleController.getString(R.string.VoipChatStreamSettings)));
        items.add(TextDetailCellFactory.of(rtmpUrl, LocaleController.getString(R.string.VoipChatStreamServerUrl), true));
        items.add(TextDetailCellFactory.of(rtmpKey, LocaleController.getString(R.string.VoipChatStreamKey), false));
        items.add(UItem.asShadow(LocaleController.getString(R.string.VoipChatStreamWithAnotherAppDescription)));
    }

    private static class TopCell extends LinearLayout {

        public TopCell(Context context) {
            super(context);
            setOrientation(LinearLayout.VERTICAL);

            RLottieImageView imageView = new RLottieImageView(context);
            imageView.setAutoRepeat(true);
            imageView.setAnimation(R.raw.utyan_streaming, 112, 112);
            imageView.playAnimation();
            addView(imageView, LayoutHelper.createLinear(112, 112, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 24, 0, 0));

            TextView title = new TextView(context);
            title.setTypeface(AndroidUtilities.bold());
            title.setText(LocaleController.formatString(R.string.Streaming));
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
            title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            addView(title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 14, 0, 7));

            TextView description = new TextView(context);
            description.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            description.setGravity(Gravity.CENTER_HORIZONTAL);
            description.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            description.setText(LocaleController.formatString(R.string.VoipStreamStart));
            description.setLineSpacing(description.getLineSpacingExtra(), description.getLineSpacingMultiplier() * 1.1f);
            addView(description, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 28, 0, 28, 17));
        }
    }

    public static class TextDetailCellFactory extends UItem.UItemFactory<TextDetailCell> {
        static { setup(new TextDetailCellFactory()); }
        @Override
        public TextDetailCell createView(Context context, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            TextDetailCell view = new TextDetailCell(context);
            view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
            Drawable drawable = ContextCompat.getDrawable(context, R.drawable.msg_copy).mutate();
            drawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader), PorterDuff.Mode.MULTIPLY));
            view.setImage(drawable);
            view.setImageClickListener(v -> copyRtmpValue(context, view.textView.getText().toString()));
            return view;
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
            ((TextDetailCell) view).setTextAndValue(item.text, item.textValue, !item.hideDivider);
        }

        public static UItem of(String text, String value, boolean divider) {
            UItem item = UItem.ofFactory(TextDetailCellFactory.class);
            item.text = text;
            item.textValue = value;
            item.hideDivider = !divider;
            item.enabled = false;
            return item;
        }

        private void copyRtmpValue(Context context, String value) {
            AndroidUtilities.addToClipboard(value);
            if (AndroidUtilities.shouldShowClipboardToast()) {
                Toast.makeText(context, LocaleController.getString(R.string.TextCopied), Toast.LENGTH_SHORT).show();
            }
        }
    }
}
