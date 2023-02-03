package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

public class RequestPeerRequirementsCell extends LinearLayout {

    public RequestPeerRequirementsCell(Context context) {
        super(context);

        setOrientation(VERTICAL);
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
    }

    private TLRPC.RequestPeerType requestPeerType;
    private ArrayList<Requirement> requirements = new ArrayList<>();

    public void set(TLRPC.RequestPeerType requestPeerType) {
        if (this.requestPeerType != requestPeerType) {
            this.requestPeerType = requestPeerType;
            removeAllViews();

            requirements.clear();

            if (requestPeerType instanceof TLRPC.TL_requestPeerTypeUser) {
                TLRPC.TL_requestPeerTypeUser type = (TLRPC.TL_requestPeerTypeUser) requestPeerType;
                checkRequirement(
                    type.premium,
                    R.string.PeerRequirementPremiumTrue,
                    R.string.PeerRequirementPremiumFalse
                );
            } else {
                boolean isChannel = requestPeerType instanceof TLRPC.TL_requestPeerTypeBroadcast;

                if (isChannel) {
                    checkRequirement(requestPeerType.has_username, R.string.PeerRequirementChannelPublicTrue, R.string.PeerRequirementChannelPublicFalse);
                    if (requestPeerType.bot_participant != null && requestPeerType.bot_participant) {
                        requirements.add(Requirement.make(AndroidUtilities.replaceTags(LocaleController.getString(R.string.PeerRequirementChannelBotParticipant))));
                    }
                    if (requestPeerType.creator != null && requestPeerType.creator) {
                        requirements.add(Requirement.make(AndroidUtilities.replaceTags(LocaleController.getString(R.string.PeerRequirementChannelCreatorTrue))));
                    }
                } else {
                    checkRequirement(requestPeerType.has_username, R.string.PeerRequirementGroupPublicTrue, R.string.PeerRequirementGroupPublicFalse);
                    checkRequirement(requestPeerType.forum, R.string.PeerRequirementForumTrue, R.string.PeerRequirementForumFalse);
                    if (requestPeerType.bot_participant != null && requestPeerType.bot_participant) {
                        requirements.add(Requirement.make(AndroidUtilities.replaceTags(LocaleController.getString(R.string.PeerRequirementGroupBotParticipant))));
                    }
                    if (requestPeerType.creator != null && requestPeerType.creator) {
                        requirements.add(Requirement.make(AndroidUtilities.replaceTags(LocaleController.getString(R.string.PeerRequirementGroupCreatorTrue))));
                    }
                }

                if (!(requestPeerType.creator != null && requestPeerType.creator)) {
                    checkAdminRights(requestPeerType.user_admin_rights, isChannel, R.string.PeerRequirementUserRights, R.string.PeerRequirementUserRight);
                }
//                checkAdminRights(requestPeerType.bot_admin_rights, isChannel, R.string.PeerRequirementBotRights, R.string.PeerRequirementBotRight);
            }

            if (!requirements.isEmpty()) {
                HeaderCell headerCell = new HeaderCell(getContext(), 20);
                headerCell.setText(LocaleController.getString("PeerRequirements", R.string.PeerRequirements));
                headerCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                addView(headerCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

                addView(emptyView(9, Theme.getColor(Theme.key_windowBackgroundWhite)), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                for (Requirement requirement : requirements) {
                    addView(new RequirementCell(getContext(), requirement), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                }
                addView(emptyView(12, Theme.getColor(Theme.key_windowBackgroundWhite)), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

                addView(emptyView(12, Theme.getThemedDrawable(getContext(), R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow)), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            }
        }
    }

    private View emptyView(int heightDp, int color) {
        return emptyView(heightDp, new ColorDrawable(color));
    }

    private View emptyView(int heightDp, Drawable background) {
        View view = new View(getContext()) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(heightDp), MeasureSpec.EXACTLY));
            }
        };
        view.setBackground(background);
        return view;
    }

    private void checkRequirement(Boolean value, String positive, String negative) {
        if (value != null) {
            if (value) {
                requirements.add(Requirement.make(AndroidUtilities.replaceTags(positive)));
            } else {
                requirements.add(Requirement.make(AndroidUtilities.replaceTags(negative)));
            }
        }
    }

    private void checkRequirement(Boolean value, int positiveResId, int negativeResId) {
        if (value != null) {
            if (value) {
                requirements.add(Requirement.make(AndroidUtilities.replaceTags(LocaleController.getString(positiveResId))));
            } else {
                requirements.add(Requirement.make(AndroidUtilities.replaceTags(LocaleController.getString(negativeResId))));
            }
        }
    }

    public static CharSequence rightsToString(TLRPC.TL_chatAdminRights rights, boolean isChannel) {
        ArrayList<Requirement> array = new ArrayList<>();
        if (rights.change_info) {
            array.add(Requirement.make(
                    1,
                    isChannel ?
                            LocaleController.getString("EditAdminChangeChannelInfo", R.string.EditAdminChangeChannelInfo) :
                            LocaleController.getString("EditAdminChangeGroupInfo", R.string.EditAdminChangeGroupInfo)
            ));
        }
        if (rights.post_messages && isChannel) {
            array.add(Requirement.make(1, LocaleController.getString("EditAdminPostMessages", R.string.EditAdminPostMessages)));
        }
        if (rights.edit_messages && isChannel) {
            array.add(Requirement.make(1, LocaleController.getString("EditAdminEditMessages", R.string.EditAdminEditMessages)));
        }
        if (rights.delete_messages) {
            array.add(Requirement.make(1, isChannel ? LocaleController.getString("EditAdminDeleteMessages", R.string.EditAdminDeleteMessages) : LocaleController.getString("EditAdminGroupDeleteMessages", R.string.EditAdminGroupDeleteMessages)));
        }
        if (rights.ban_users && !isChannel) {
            array.add(Requirement.make(1, LocaleController.getString("EditAdminBanUsers", R.string.EditAdminBanUsers)));
        }
        if (rights.invite_users) {
            array.add(Requirement.make(1, LocaleController.getString("EditAdminAddUsers", R.string.EditAdminAddUsers)));
        }
        if (rights.pin_messages && !isChannel) {
            array.add(Requirement.make(1, LocaleController.getString("EditAdminPinMessages", R.string.EditAdminPinMessages)));
        }
        if (rights.add_admins) {
            array.add(Requirement.make(1, LocaleController.getString("EditAdminAddAdmins", R.string.EditAdminAddAdmins)));
        }
        if (rights.anonymous && !isChannel) {
            array.add(Requirement.make(1, LocaleController.getString("EditAdminSendAnonymously", R.string.EditAdminSendAnonymously)));
        }
        if (rights.manage_call) {
            array.add(Requirement.make(1, LocaleController.getString("StartVoipChatPermission", R.string.StartVoipChatPermission)));
        }
        if (rights.manage_topics && !isChannel) {
            array.add(Requirement.make(1, LocaleController.getString("ManageTopicsPermission", R.string.ManageTopicsPermission)));
        }

        if (array.size() == 1) {
            return array.get(0).text.toString().toLowerCase();
        } else if (!array.isEmpty()) {
            SpannableStringBuilder string = new SpannableStringBuilder();
            for (int i = 0; i < array.size(); ++i) {
                if (i > 0) {
                    string.append(", ");
                }
                string.append(array.get(i).text.toString().toLowerCase());
            }
            return string;
        }
        return "";
    }

    private void checkAdminRights(TLRPC.TL_chatAdminRights value, boolean isChannel, CharSequence headerText, CharSequence headerSingleText) {
        if (value == null) {
            return;
        }

        ArrayList<Requirement> rights = new ArrayList<>();
        if (value.change_info) {
            rights.add(Requirement.make(
                1,
                isChannel ?
                    LocaleController.getString("EditAdminChangeChannelInfo", R.string.EditAdminChangeChannelInfo) :
                    LocaleController.getString("EditAdminChangeGroupInfo", R.string.EditAdminChangeGroupInfo)
            ));
        }
        if (value.post_messages && isChannel) {
            rights.add(Requirement.make(1, LocaleController.getString("EditAdminPostMessages", R.string.EditAdminPostMessages)));
        }
        if (value.edit_messages && isChannel) {
            rights.add(Requirement.make(1, LocaleController.getString("EditAdminEditMessages", R.string.EditAdminEditMessages)));
        }
        if (value.delete_messages) {
            rights.add(Requirement.make(1, isChannel ? LocaleController.getString("EditAdminDeleteMessages", R.string.EditAdminDeleteMessages) : LocaleController.getString("EditAdminGroupDeleteMessages", R.string.EditAdminGroupDeleteMessages)));
        }
        if (value.ban_users && !isChannel) {
            rights.add(Requirement.make(1, LocaleController.getString("EditAdminBanUsers", R.string.EditAdminBanUsers)));
        }
        if (value.invite_users) {
            rights.add(Requirement.make(1, LocaleController.getString("EditAdminAddUsers", R.string.EditAdminAddUsers)));
        }
        if (value.pin_messages && !isChannel) {
            rights.add(Requirement.make(1, LocaleController.getString("EditAdminPinMessages", R.string.EditAdminPinMessages)));
        }
        if (value.add_admins) {
            rights.add(Requirement.make(1, LocaleController.getString("EditAdminAddAdmins", R.string.EditAdminAddAdmins)));
        }
        if (value.anonymous && !isChannel) {
            rights.add(Requirement.make(1, LocaleController.getString("EditAdminSendAnonymously", R.string.EditAdminSendAnonymously)));
        }
        if (value.manage_call) {
            rights.add(Requirement.make(1, LocaleController.getString("StartVoipChatPermission", R.string.StartVoipChatPermission)));
        }
        if (value.manage_topics && !isChannel) {
            rights.add(Requirement.make(1, LocaleController.getString("ManageTopicsPermission", R.string.ManageTopicsPermission)));
        }

        if (rights.size() == 1) {
            requirements.add(Requirement.make(TextUtils.concat(headerSingleText, " ", rights.get(0).text)));
        } else if (!rights.isEmpty()) {
            SpannableStringBuilder string = SpannableStringBuilder.valueOf(headerText);
            string.append(" ");
            for (int i = 0; i < rights.size(); ++i) {
                if (i > 0) {
                    string.append(", ");
                }
                string.append(rights.get(i).text.toString().toLowerCase());
            }
            string.append(".");
            requirements.add(Requirement.make(string));
        }
    }

    private void checkAdminRights(TLRPC.TL_chatAdminRights value, boolean isChannel, int headerTextResId, int headerSingleTextResId) {
        checkAdminRights(value, isChannel, AndroidUtilities.replaceTags(LocaleController.getString(headerTextResId)), AndroidUtilities.replaceTags(LocaleController.getString(headerSingleTextResId)));
    }

    class RequirementCell extends LinearLayout {

        private ImageView imageView;
        private TextView textView;

        public RequirementCell(Context context, Requirement requirement) {
            super(context);

            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

            setOrientation(HORIZONTAL);

            imageView = new ImageView(context);
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            imageView.setImageResource(requirement.padding <= 0 ? R.drawable.list_check : R.drawable.list_circle);
            imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader), PorterDuff.Mode.MULTIPLY));
            addView(imageView, LayoutHelper.createLinear(20, 20, 0, Gravity.TOP | Gravity.LEFT, 17 + requirement.padding * 16, -1, 0, 0));

            textView = new TextView(context);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            textView.setSingleLine(false);
            textView.setText(requirement.text);
            addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1, 6, 4, 24, 4));
        }
    }
}

class Requirement {
    public int padding;
    public CharSequence text;

    private Requirement(CharSequence text, int padding) {
        this.text = text;
        this.padding = padding;
    }

    public static Requirement make(CharSequence text) {
        return new Requirement(text, 0);
    }

    public static Requirement make(int pad, CharSequence text) {
        return new Requirement(text, pad);
    }
}