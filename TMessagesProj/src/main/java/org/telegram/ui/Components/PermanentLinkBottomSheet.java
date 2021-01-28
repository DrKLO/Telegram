package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;
import androidx.core.widget.NestedScrollView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.ManageLinksActivity;

import java.util.ArrayList;

public class PermanentLinkBottomSheet extends BottomSheet {

    TLRPC.ChatFull info;
    RLottieDrawable linkIcon;
    private final TextView titleView;
    private final TextView subtitle;
  //  private final TextView manage;
    private final RLottieImageView imageView;
    private final LinkActionView linkActionView;
    private int chatId;

    public PermanentLinkBottomSheet(Context context, boolean needFocus, BaseFragment fragment, TLRPC.ChatFull info, int chatId) {
        super(context, needFocus);
        this.info = info;
        this.chatId = chatId;

        setAllowNestedScroll(true);
        setApplyBottomPadding(false);

        linkActionView = new LinkActionView(context, fragment, this, chatId, true);
        linkActionView.setPermanent(true);
        imageView = new RLottieImageView(context);
        linkIcon = new RLottieDrawable(R.raw.shared_link_enter, "" + R.raw.shared_link_enter, AndroidUtilities.dp(90), AndroidUtilities.dp(90), false, null);
        linkIcon.setCustomEndFrame(42);
        imageView.setAnimation(linkIcon);
        linkActionView.setUsers(0, null);
        linkActionView.setPublic(true);

        titleView = new TextView(context);
        titleView.setText(LocaleController.getString("InviteLink", R.string.InviteLink));
        titleView.setTextSize(24);
        titleView.setGravity(Gravity.CENTER_HORIZONTAL);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));

        subtitle = new TextView(context);
        subtitle.setText(LocaleController.getString("LinkInfo", R.string.LinkInfo));
        subtitle.setTextSize(14);
        subtitle.setGravity(Gravity.CENTER_HORIZONTAL);
        subtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));

        /*
        manage = new TextView(context);
        manage.setText(LocaleController.getString("ManageInviteLinks", R.string.ManageInviteLinks));
        manage.setTextSize(14);
        manage.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        manage.setBackground(Theme.createRadSelectorDrawable(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText), (int) (255 * 0.3f)), AndroidUtilities.dp(4), AndroidUtilities.dp(4)));
        manage.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(4), AndroidUtilities.dp(12), AndroidUtilities.dp(4));

          manage.setOnClickListener(view -> {
            ManageLinksActivity manageFragment = new ManageLinksActivity(info.id);
            manageFragment.setInfo(info, info.exported_invite);
            fragment.presentFragment(manageFragment);
            dismiss();
        });
*/
        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.addView(imageView, LayoutHelper.createLinear(90, 90, Gravity.CENTER_HORIZONTAL, 0, 24, 0, 0));
        linearLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 60, 16, 60, 0));
        linearLayout.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 60, 16, 60, 0));
        linearLayout.addView(linkActionView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
      //  linearLayout.addView(manage, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 60, 26, 60, 26));

        NestedScrollView scrollView = new NestedScrollView(context);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.addView(linearLayout);

        setCustomView(scrollView);

        if (info != null && info.exported_invite != null) {
            linkActionView.setLink(info.exported_invite.link);
        } else {
            generateLink();
        }

        updateColors();
    }

    boolean linkGenerating;
    TLRPC.TL_chatInviteExported invite;

    private void generateLink() {
        if (linkGenerating) {
            return;
        }
        linkGenerating = true;
        TLRPC.TL_messages_exportChatInvite req = new TLRPC.TL_messages_exportChatInvite();
        //req.legacy_revoke_permanent = true; TODO layer 124
        req.peer = MessagesController.getInstance(currentAccount).getInputPeer(-chatId);
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error == null) {
                invite = (TLRPC.TL_chatInviteExported) response;

                TLRPC.ChatFull chatInfo = MessagesController.getInstance(currentAccount).getChatFull(chatId);
                if (chatInfo != null) {
                    chatInfo.exported_invite = invite;
                }

                linkActionView.setLink(invite.link);
            }
            linkGenerating = false;
        }));
    }

    @Override
    public void show() {
        super.show();
        AndroidUtilities.runOnUIThread(() -> {
            linkIcon.start();
        }, 50);
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> arrayList = new ArrayList<>();
        ThemeDescription.ThemeDescriptionDelegate descriptionDelegate = () -> {
            updateColors();
        };
        arrayList.add(new ThemeDescription(titleView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        arrayList.add(new ThemeDescription(subtitle, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText));
       // arrayList.add(new ThemeDescription(manage, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlueText));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, descriptionDelegate, Theme.key_featuredStickers_addButton));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, descriptionDelegate, Theme.key_featuredStickers_buttonText));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, descriptionDelegate, Theme.key_windowBackgroundWhiteBlueText));
        return arrayList;
    }

    private void updateColors() {
        imageView.setBackground(Theme.createCircleDrawable(AndroidUtilities.dp(90), Theme.getColor(Theme.key_featuredStickers_addButton)));
      //  manage.setBackground(Theme.createRadSelectorDrawable(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText), (int) (255 * 0.3f)), AndroidUtilities.dp(4), AndroidUtilities.dp(4)));
        int color = Theme.getColor(Theme.key_featuredStickers_buttonText);
        linkIcon.setLayerColor("Top.**", color);
        linkIcon.setLayerColor("Bottom.**", color);
        linkIcon.setLayerColor("Center.**", color);
        linkActionView.updateColors();
        setBackgroundColor(Theme.getColor(Theme.key_dialogBackground));
    }
}
