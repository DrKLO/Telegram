package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.formatPluralStringComma;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.exoplayer2.C;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.browser.Browser;
import org.telegram.messenger.voip.VoIPService;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_phone;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.AvatarsImageView;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.TextHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.Components.voip.VoIPHelper;
import org.telegram.ui.Stars.StarsIntroActivity;
import org.telegram.ui.Stories.DarkThemeResourceProvider;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;
import org.telegram.ui.Stories.recorder.HintView2;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class GroupCallSheet {

    public static void show(Context context, int currentAccount, long dialogId, String slug, Browser.Progress progress) {
        final TLRPC.TL_inputGroupCallSlug inputGroupCallSlug = new TLRPC.TL_inputGroupCallSlug();
        inputGroupCallSlug.slug = slug;
        show(context, currentAccount, dialogId, inputGroupCallSlug, progress);
    }

    public static void show(Context context, int currentAccount, long dialogId, TLRPC.InputGroupCall inputGroupCall, Browser.Progress progress) {
        if (VoIPService.getSharedInstance() != null) {
            final VoIPService voip = VoIPService.getSharedInstance();
            if (voip.conference != null) {
                boolean same = false;
                if (inputGroupCall instanceof TLRPC.TL_inputGroupCall) {
                    same = (
                        voip.conference.groupCall != null && inputGroupCall.id == voip.conference.groupCall.id ||
                        voip.conference.inputGroupCall instanceof TLRPC.TL_inputGroupCall && inputGroupCall.id == voip.conference.inputGroupCall.id
                    );
                } else if (inputGroupCall instanceof TLRPC.TL_inputGroupCallSlug) {
                    same = voip.conference.inputGroupCall instanceof TLRPC.TL_inputGroupCallSlug && TextUtils.equals(voip.conference.inputGroupCall.slug, inputGroupCall.slug);
                }
                if (same) {
                    if (LaunchActivity.instance != null) {
                        GroupCallActivity.create(LaunchActivity.instance, AccountInstance.getInstance(VoIPService.getSharedInstance().getAccount()), null, null, false, null);
                        return;
                    }
                }
            }
        }

        final AlertDialog progressDialog;
        if (progress == null) {
            progressDialog = new AlertDialog(context, AlertDialog.ALERT_TYPE_SPINNER);
            progressDialog.showDelayed(300);
        } else {
            progressDialog = null;
        }

        final TL_phone.getGroupCall req = new TL_phone.getGroupCall();
        req.call = inputGroupCall;
        req.limit = 10;
        final int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            if (progressDialog != null) {
                progressDialog.dismiss();
            }
            if (progress != null) {
                progress.end();
            }
            if (res instanceof TL_phone.groupCall) {
                final TL_phone.groupCall r = (TL_phone.groupCall) res;
                MessagesController.getInstance(currentAccount).putUsers(r.users, false);
                MessagesController.getInstance(currentAccount).putChats(r.chats, false);
                if (VoIPService.getSharedInstance() != null) {
                    final VoIPService voip = VoIPService.getSharedInstance();
                    if (voip.conference != null && voip.conference.groupCall != null && r.call.id == voip.conference.groupCall.id) {
                        if (LaunchActivity.instance != null) {
                            GroupCallActivity.create(LaunchActivity.instance, AccountInstance.getInstance(VoIPService.getSharedInstance().getAccount()), null, null, false, null);
                            return;
                        }
                    }
                }
                show(context, currentAccount, dialogId, inputGroupCall, r.call, r.participants);
            } else if (err != null && "GROUPCALL_INVALID".equalsIgnoreCase(err.text)) {
                final BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                if (lastFragment != null) {
                    BulletinFactory.of(lastFragment).createSimpleBulletin(R.raw.error, getString(R.string.LinkIsNoActive)).show();
                }
            } else if (err != null) {
                final BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                if (lastFragment != null) {
                    BulletinFactory.of(lastFragment).showForError(err);
                }
            }
        }));
        if (progress != null) {
            progress.onCancel(() -> {
                ConnectionsManager.getInstance(currentAccount).cancelRequest(reqId, true);
            });
            progress.init();
        }
    }

    public static void show(
        Context context,
        int currentAccount,
        long dialogId,
        TLRPC.InputGroupCall inputGroupCall,
        TLRPC.GroupCall groupCall,
        ArrayList<TLRPC.GroupCallParticipant> participants
    ) {
        final Theme.ResourcesProvider resourcesProvider = new DarkThemeResourceProvider();
        final BottomSheet.Builder b = new BottomSheet.Builder(context, false, resourcesProvider);

        final LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setPadding(dp(14), 0, dp(14), dp(8));

        final FrameLayout imageBackgroundView = new FrameLayout(context);
        imageBackgroundView.setBackground(Theme.createCircleDrawable(dp(80), Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider)));
        final ImageView imageView = new ImageView(context);
        imageView.setImageResource(R.drawable.filled_calls_users);
        imageBackgroundView.addView(imageView, LayoutHelper.createFrame(56, 56, Gravity.CENTER));
        linearLayout.addView(imageBackgroundView, LayoutHelper.createLinear(80, 80, Gravity.CENTER_HORIZONTAL, 2, 21, 2, 13));

        LinkSpanDrawable.LinksTextView textView = TextHelper.makeLinkTextView(context, 20, Theme.key_windowBackgroundWhiteBlackText, true, resourcesProvider);
        textView.setText(getString(R.string.GroupCallLinkTitle));
        textView.setGravity(Gravity.CENTER);
        linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 2, 0, 2, 4));

        final List<Long> participantIds =
            participants.stream()
                .map(p -> DialogObject.getPeerDialogId(p.peer))
                .filter(did -> did != UserConfig.getInstance(currentAccount).getClientUserId() && did != dialogId)
                .collect(Collectors.toList());
        final boolean withList = !participantIds.isEmpty();
        textView = TextHelper.makeLinkTextView(context, 14, Theme.key_windowBackgroundWhiteBlackText, false, resourcesProvider);
        textView.setText(AndroidUtilities.replaceTags(getString(R.string.GroupCallLinkText)));
        textView.setGravity(Gravity.CENTER);
        textView.setMaxWidth(HintView2.cutInFancyHalf(textView.getText(), textView.getPaint()));
        linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 2, 0, 2, 23));

        if (withList) {
            View separator = new View(context);
            separator.setBackgroundColor(0xFF2A3036);
            linearLayout.addView(separator, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0.66f, Gravity.FILL_HORIZONTAL, 0, 0, 0, 0));

            AvatarsImageView avatarsImageView = new AvatarsImageView(context, false);
            avatarsImageView.setCentered(true);
            avatarsImageView.setSize(dp(38));
//            avatarsImageView.setStepFactor(0.56f);
            final int count = Math.min(3, participantIds.size());
            avatarsImageView.setCount(count);
            for (int i = 0; i < count; ++i) {
                final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(participantIds.get(i));
                avatarsImageView.setObject(i, currentAccount, user);
            }
            avatarsImageView.commitTransition(false);
            linearLayout.addView(avatarsImageView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 10 + 38 + 10, 2, 11, 5, 0));

            textView = TextHelper.makeLinkTextView(context, 14, Theme.key_windowBackgroundWhiteBlackText, false, resourcesProvider);
            textView.setGravity(Gravity.CENTER);
            if (participantIds.size() == 1) {
                textView.setText(AndroidUtilities.replaceTags(formatString(R.string.GroupCallLinkText2One, DialogObject.getShortName(currentAccount, participantIds.get(0)))));
            } else if (participantIds.size() == 2) {
                textView.setText(AndroidUtilities.replaceTags(formatString(R.string.GroupCallLinkText2Two, DialogObject.getShortName(currentAccount, participantIds.get(0)), DialogObject.getShortName(currentAccount, participantIds.get(1)))));
            } else {
                textView.setText(AndroidUtilities.replaceTags(formatPluralStringComma("GroupCallLinkText2Many", participants.size() - 2, DialogObject.getShortName(currentAccount, participantIds.get(0)), DialogObject.getShortName(currentAccount, participantIds.get(1)))));
            }
            textView.setMaxWidth(HintView2.cutInFancyHalf(textView.getText(), textView.getPaint()));
            linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 2, 0, 2, 25));

//            UniversalRecyclerView listView = new UniversalRecyclerView(context, currentAccount, 0, (items, adapter) -> {
//                for (long id : participantIds) {
//                    items.add(UserView.Factory.asUser(id));
//                }
//            }, null, null, resourcesProvider);
//            listView.makeHorizontal();
//            listView.setPadding(dp(10), 0, dp(10), 0);
//            listView.setClipToPadding(false);
//            linearLayout.addView(listView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 122, Gravity.CENTER_HORIZONTAL, 2, 0, 2, 0));
        }

        final ButtonWithCounterView joinButton = new ButtonWithCounterView(context, resourcesProvider);
        joinButton.setText(getString(R.string.GroupCallLinkJoin), false);
        linearLayout.addView(joinButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 2, 0, 2, 0));

        b.setCustomView(linearLayout);
        BottomSheet sheet = b.create();

        joinButton.setOnClickListener(v -> {
            sheet.dismiss();

            final Activity activity = AndroidUtilities.findActivity(context);
            if (activity == null) return;
            VoIPHelper.joinConference(activity, currentAccount, inputGroupCall, false, null);
        });

        sheet.fixNavigationBar();
        sheet.show();
    }

    static class UserView extends FrameLayout {
        private final int currentAccount;
        private final AvatarDrawable avatarDrawable = new AvatarDrawable();
        private final BackupImageView imageView;
        private final LinkSpanDrawable.LinksTextView textView;
        public UserView(Context context, int currentAccount, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.currentAccount = currentAccount;

            imageView = new BackupImageView(context);
            imageView.setRoundRadius(dp(28));
            addView(imageView, LayoutHelper.createFrame(56, 56, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 17, 0, 0));

            textView = TextHelper.makeLinkTextView(context, 12, Theme.key_windowBackgroundWhiteBlackText, false, resourcesProvider);
            textView.setGravity(Gravity.CENTER);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 6, 77.66f, 6, 0));
        }

        public void set(long did) {
            final TLObject obj = MessagesController.getInstance(currentAccount).getUserOrChat(did);
            avatarDrawable.setInfo(obj);
            imageView.setForUserOrChat(obj, avatarDrawable);

            textView.setText(DialogObject.getName(obj));
            textView.setMaxWidth(Math.max(dp(41), HintView2.cutInFancyHalf(textView.getText(), textView.getPaint())));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(dp(82), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.EXACTLY)
            );
        }

        public static class Factory extends UItem.UItemFactory<UserView> {
            static { setup(new Factory()); }

            @Override
            public UserView createView(Context context, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new UserView(context, currentAccount, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider) {
                ((UserView) view).set(item.dialogId);
            }

            public static UItem asUser(long did) {
                final UItem item = UItem.ofFactory(Factory.class);
                item.dialogId = did;
                return item;
            }
        }
    };

}