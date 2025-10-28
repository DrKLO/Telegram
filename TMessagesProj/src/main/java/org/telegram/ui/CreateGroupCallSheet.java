package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.messenger.MessagesController.findUpdates;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.recyclerview.widget.DefaultItemAnimator;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_phone;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Components.BottomSheetWithRecyclerListView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.Premium.boosts.cells.selector.SelectorUserCell;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.TextHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.voip.VoIPHelper;
import org.telegram.ui.Stories.DarkThemeResourceProvider;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;
import org.telegram.ui.Stories.recorder.HintView2;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;

public class CreateGroupCallSheet extends BottomSheetWithRecyclerListView {

    private final FrameLayout topView;
    private final LinearLayout topViewLayout;
    private final ImageView closeButton;
    private final FrameLayout buttonsContainer;
    private final LinearLayout buttonsLayout;
    private final ButtonWithCounterView voiceButton;
    private final ButtonWithCounterView videoButton;

    private final ArrayList<Long> participants = new ArrayList<>();
    private final HashSet<Long> selectedParticipants = new HashSet<>();

    public CreateGroupCallSheet(Context context, Collection<Long> participants) {
        super(context, null, false, false, false, new DarkThemeResourceProvider());

        this.participants.addAll(participants);
        this.selectedParticipants.addAll(participants);

        fixNavigationBar(getThemedColor(Theme.key_dialogBackground));
        drawDoubleNavigationBar = false;

        topView = new FrameLayout(context);

        topViewLayout = new LinearLayout(context);
        topViewLayout.setOrientation(LinearLayout.VERTICAL);
        topView.addView(topViewLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        closeButton = new ImageView(context);
        closeButton.setImageResource(R.drawable.ic_close_white);
        closeButton.setColorFilter(new PorterDuffColorFilter(0xFF848D94, PorterDuff.Mode.SRC_IN));
        topView.addView(closeButton, LayoutHelper.createFrame(24, 24, Gravity.RIGHT | Gravity.TOP, 0, 14, 14, 0));
        ScaleStateListAnimator.apply(closeButton);
        closeButton.setOnClickListener(v -> dismiss());

        final FrameLayout imageBackgroundView = new FrameLayout(context);
        imageBackgroundView.setBackground(Theme.createCircleDrawable(dp(80), Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider)));
        final ImageView imageView = new ImageView(context);
        imageView.setImageResource(R.drawable.filled_calls_users);
        imageBackgroundView.addView(imageView, LayoutHelper.createFrame(56, 56, Gravity.CENTER));
        topViewLayout.addView(imageBackgroundView, LayoutHelper.createLinear(80, 80, Gravity.CENTER_HORIZONTAL, 2, 21, 2, 13));

        LinkSpanDrawable.LinksTextView textView = TextHelper.makeLinkTextView(context, 20, Theme.key_windowBackgroundWhiteBlackText, true, resourcesProvider);
        textView.setText(getString(R.string.GroupCallCreateTitle));
        textView.setGravity(Gravity.CENTER);
        topViewLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 2, 0, 2, 4));

        textView = TextHelper.makeLinkTextView(context, 14, Theme.key_windowBackgroundWhiteBlackText, false, resourcesProvider);
        textView.setText(AndroidUtilities.replaceTags(getString(R.string.GroupCallCreateText)));
        textView.setGravity(Gravity.CENTER);
        textView.setMaxWidth(HintView2.cutInFancyHalf(textView.getText(), textView.getPaint()));
        topViewLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 2, 0, 2, 23));

        if (adapter != null) {
            adapter.update(false);
        }


        DefaultItemAnimator itemAnimator = new DefaultItemAnimator();
        itemAnimator.setSupportsChangeAnimations(false);
        itemAnimator.setDelayAnimations(false);
        itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        itemAnimator.setDurations(350);
        recyclerListView.setItemAnimator(itemAnimator);
        recyclerListView.setOnItemClickListener((view, position, x, y) -> {
            if (creatingCall) return;
            UItem item = adapter.getItem(position - 1);
            if (item != null && item.object != null) {
                final long did;
                if (item.object instanceof TLRPC.User) {
                    did = ((TLRPC.User) item.object).id;
                } else if (item.object instanceof TLRPC.Chat) {
                    did = ((TLRPC.Chat) item.object).id;
                } else {
                    return;
                }
                if (selectedParticipants.contains(did)) {
                    selectedParticipants.remove(did);
                } else {
                    selectedParticipants.add(did);
                }
                if (view instanceof SelectorUserCell) {
                    ((SelectorUserCell) view).setChecked(selectedParticipants.contains(did), true);
                }
            }
        });



        buttonsContainer = new FrameLayout(context);
//        View buttonShadow = new View(context);
//        buttonShadow.setBackgroundColor(0xFF000000);
//        buttonsContainer.addView(buttonShadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1f / AndroidUtilities.density, Gravity.TOP | Gravity.FILL_HORIZONTAL, 0, 0, 0, 0));

        buttonsLayout = new LinearLayout(context);
        buttonsLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonsLayout.setPadding(backgroundPaddingLeft + dp(14), dp(14), backgroundPaddingLeft + dp(14), dp(14));
        buttonsContainer.addView(buttonsLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL));

        voiceButton = new ButtonWithCounterView(context, resourcesProvider);
        SpannableStringBuilder sb = new SpannableStringBuilder();
        sb.append("x  ");
        sb.setSpan(new ColoredImageSpan(R.drawable.profile_phone), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append(getString(R.string.GroupCallCreateVoice));
        voiceButton.setText(sb, false);
        buttonsLayout.addView(voiceButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 1, Gravity.FILL, 0, 0, 6, 0));
        voiceButton.setOnClickListener(v -> createCall(false));

        videoButton = new ButtonWithCounterView(context, resourcesProvider);
        sb = new SpannableStringBuilder();
        sb.append("x  ");
        sb.setSpan(new ColoredImageSpan(R.drawable.profile_video), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append(getString(R.string.GroupCallCreateVideo));
        videoButton.setText(sb, false);
        buttonsLayout.addView(videoButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 1, Gravity.FILL, 6, 0, 0, 0));
        videoButton.setOnClickListener(v -> createCall(true));

        containerView.addView(buttonsContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL));
        recyclerListView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, dp(76));
    }

    private boolean creatingCall;
    private void createCall(boolean video) {
        if (creatingCall) return;
        creatingCall = true;

        final ButtonWithCounterView button = video ? videoButton : voiceButton;
        button.setLoading(true);

        final HashSet<Long> inviteUsers = new HashSet<>();
        inviteUsers.addAll(selectedParticipants);

        final TL_phone.createConferenceCall req = new TL_phone.createConferenceCall();
        req.random_id = Utilities.random.nextInt();
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            if (res instanceof TLRPC.Updates) {
                final TLRPC.Updates updates = (TLRPC.Updates) res;
                MessagesController.getInstance(currentAccount).putUsers(updates.users, false);
                MessagesController.getInstance(currentAccount).putChats(updates.chats, false);

                TLRPC.GroupCall groupCall = null;
                for (TLRPC.TL_updateGroupCall upd : findUpdates(updates, TLRPC.TL_updateGroupCall.class)) {
                    groupCall = upd.call;
                }

                Utilities.stageQueue.postRunnable(() -> {
                    MessagesController.getInstance(currentAccount).processUpdates(updates, false);
                });

                if (groupCall == null || LaunchActivity.instance == null) {
                    creatingCall = false;
                    button.setLoading(false);
                    return;
                }
                final TLRPC.TL_inputGroupCall inputGroupCall = new TLRPC.TL_inputGroupCall();
                inputGroupCall.id = groupCall.id;
                inputGroupCall.access_hash = groupCall.access_hash;
                dismiss();
                VoIPHelper.joinConference(LaunchActivity.instance, currentAccount, inputGroupCall, video, groupCall, inviteUsers);
            } else if (res instanceof TL_phone.groupCall) {
                final TL_phone.groupCall r = (TL_phone.groupCall) res;
                MessagesController.getInstance(currentAccount).putUsers(r.users, false);
                MessagesController.getInstance(currentAccount).putChats(r.chats, false);
                if (LaunchActivity.instance == null) {
                    creatingCall = false;
                    button.setLoading(false);
                    return;
                }
                final TLRPC.TL_inputGroupCall inputGroupCall = new TLRPC.TL_inputGroupCall();
                inputGroupCall.id = r.call.id;
                inputGroupCall.access_hash = r.call.access_hash;
                dismiss();
                VoIPHelper.joinConference(LaunchActivity.instance, currentAccount, inputGroupCall, video, r.call, inviteUsers);
            } else if (err != null) {
                BulletinFactory.of(topBulletinContainer, resourcesProvider)
                    .showForError(err);
            }
        }));
    }

    private UniversalAdapter adapter;

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.GroupCallCreateTitle);
    }

    @Override
    protected RecyclerListView.SelectionAdapter createAdapter(RecyclerListView listView) {
        return adapter = new UniversalAdapter(listView, getContext(), currentAccount, 0, true, this::fillItems, resourcesProvider);
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asCustom(topView));
        items.add(UItem.asShadow(null));
        if (participants != null && !participants.isEmpty()) {
            items.add(UItem.asHeader(getString(R.string.GroupCallCreateAddMembers)));
            for (int i = 0; i < participants.size(); ++i) {
                final long did = participants.get(i);
                final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(did);
                if (user == null) return;
                items.add(SelectorUserCell.Factory.make(user).setChecked(selectedParticipants.contains(did)));
            }
        }
    }
}
