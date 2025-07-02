package org.telegram.ui.Components;

import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.collection.LongSparseArray;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.DialogCell;

import java.util.ArrayList;

public class LinkActionView extends LinearLayout {

    TextView linkView;
    String link;
    BaseFragment fragment;
    ImageView optionsView;
    private final TextView copyView;
    private final TextView shareView;
    private final TextView removeView;
    private final FrameLayout frameLayout;

    private Delegate delegate;

    private ActionBarPopupWindow actionBarPopupWindow;
    private final AvatarsContainer avatarsContainer;
    private int usersCount;

    private boolean revoked;
    private boolean permanent;
    boolean loadingImporters;
    private QRCodeBottomSheet qrCodeBottomSheet;
    private boolean hideRevokeOption;
    private boolean canEdit = true;
    private final boolean isChannel;
    private final float[] point = new float[2];

    public LinkActionView(Context context, BaseFragment fragment, BottomSheet bottomSheet, long chatId, boolean permanent, boolean isChannel) {
        super(context);
        this.fragment = fragment;
        this.permanent = permanent;
        this.isChannel = isChannel;

        setOrientation(VERTICAL);
        frameLayout = new FrameLayout(context);
        linkView = new TextView(context);
        linkView.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(13), AndroidUtilities.dp(40), AndroidUtilities.dp(13));
        linkView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        linkView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        linkView.setSingleLine(true);

        int containerPadding = 4;
        frameLayout.addView(linkView);
        optionsView = new ImageView(context);
        optionsView.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_ab_other));
        optionsView.setContentDescription(LocaleController.getString(R.string.AccDescrMoreOptions));
        optionsView.setScaleType(ImageView.ScaleType.CENTER);
        frameLayout.addView(optionsView, LayoutHelper.createFrame(40, 48, Gravity.RIGHT | Gravity.CENTER_VERTICAL));
        addView(frameLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, containerPadding, 0, containerPadding, 0));

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(HORIZONTAL);

        copyView = new TextView(context);
        copyView.setGravity(Gravity.CENTER);
        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
        spannableStringBuilder.append("..").setSpan(new ColoredImageSpan(ContextCompat.getDrawable(context, R.drawable.msg_copy_filled)), 0, 1, 0);
        spannableStringBuilder.setSpan(new DialogCell.FixedWidthSpan(AndroidUtilities.dp(6)), 1, 2, 0);
        spannableStringBuilder.append(LocaleController.getString(R.string.LinkActionCopy));
        copyView.setText(spannableStringBuilder);
        copyView.setContentDescription(LocaleController.getString(R.string.LinkActionCopy));
        copyView.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), 0);
        copyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        copyView.setTypeface(AndroidUtilities.bold());
        copyView.setSingleLine(true);
        linearLayout.addView(copyView, LayoutHelper.createLinear(0, 42, 1f, 0, containerPadding, 0, 4, 0));

        shareView = new TextView(context);
        shareView.setGravity(Gravity.CENTER);
        spannableStringBuilder = new SpannableStringBuilder();
        spannableStringBuilder.append("..").setSpan(new ColoredImageSpan(ContextCompat.getDrawable(context, R.drawable.msg_share_filled)), 0, 1, 0);
        spannableStringBuilder.setSpan(new DialogCell.FixedWidthSpan(AndroidUtilities.dp(6)), 1, 2, 0);
        spannableStringBuilder.append(LocaleController.getString(R.string.LinkActionShare));
        shareView.setText(spannableStringBuilder);
        shareView.setContentDescription(LocaleController.getString(R.string.LinkActionShare));
        shareView.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), 0);
        shareView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        shareView.setTypeface(AndroidUtilities.bold());
        shareView.setSingleLine(true);
        linearLayout.addView(shareView, LayoutHelper.createLinear(0, 42, 1f, 4, 0, containerPadding, 0));


        removeView = new TextView(context);
        removeView.setGravity(Gravity.CENTER);
        spannableStringBuilder = new SpannableStringBuilder();
        spannableStringBuilder.append("..").setSpan(new ColoredImageSpan(ContextCompat.getDrawable(context, R.drawable.msg_delete_filled)), 0, 1, 0);
        spannableStringBuilder.setSpan(new DialogCell.FixedWidthSpan(AndroidUtilities.dp(8)), 1, 2, 0);
        spannableStringBuilder.append(LocaleController.getString(R.string.DeleteLink));
        spannableStringBuilder.append(".").setSpan(new DialogCell.FixedWidthSpan(AndroidUtilities.dp(5)), spannableStringBuilder.length() - 1, spannableStringBuilder.length(), 0);
        removeView.setText(spannableStringBuilder);
        removeView.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), 0);
        removeView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        removeView.setTypeface(AndroidUtilities.bold());
        removeView.setSingleLine(true);
        linearLayout.addView(removeView, LayoutHelper.createLinear(0, 42, 1f, containerPadding, 0, containerPadding, 0));
        removeView.setVisibility(View.GONE);

        addView(linearLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 0));

        avatarsContainer = new AvatarsContainer(context);
        avatarsContainer.avatarsImageView.setAvatarsTextSize(AndroidUtilities.dp(18));
        addView(avatarsContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 28 + 16, 0, 12, 0, 0));
        copyView.setOnClickListener(view -> {
            try {
                if (link == null) {
                    return;
                }
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("label", link);
                clipboard.setPrimaryClip(clip);
                if (bottomSheet != null && bottomSheet.getContainer() != null) {
                    BulletinFactory.createCopyLinkBulletin(bottomSheet.getContainer()).show();
                } else {
                    BulletinFactory.createCopyLinkBulletin(fragment).show();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });

        if (permanent) {
            avatarsContainer.setOnClickListener(view -> {
                delegate.showUsersForPermanentLink();
            });
        }

        shareView.setOnClickListener(view -> {
            try {
                if (link == null) {
                    return;
                }
                fragment.showDialog(new ShareAlert(getContext(), null, link, false, link, false, fragment.getResourceProvider()) {
                    @Override
                    protected void onSend(LongSparseArray<TLRPC.Dialog> dids, int count, TLRPC.TL_forumTopic topic, boolean showToast) {
                        if (!showToast) return;
                        final String str;
                        if (dids != null && dids.size() == 1) {
                            long did = dids.valueAt(0).id;
                            if (did == 0 || did == UserConfig.getInstance(currentAccount).getClientUserId()) {
                                str = getString(R.string.InvLinkToSavedMessages);
                            } else {
                                str = formatString(R.string.InvLinkToUser, MessagesController.getInstance(currentAccount).getPeerName(did, true));
                            }
                        } else {
                            str = formatString(R.string.InvLinkToChats, LocaleController.formatPluralString("Chats", count));
                        }
                        showBulletin(R.raw.forward, AndroidUtilities.replaceTags(str));
                    }
                });
            } catch (Exception e) {
                FileLog.e(e);
            }
        });

        removeView.setOnClickListener(view -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity());
            builder.setTitle(LocaleController.getString(R.string.DeleteLink));
            builder.setMessage(LocaleController.getString(R.string.DeleteLinkHelp));
            builder.setPositiveButton(LocaleController.getString(R.string.Delete), (dialogInterface2, i2) -> {
                if (delegate != null) {
                    delegate.removeLink();
                }
            });
            builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
            fragment.showDialog(builder.create());
        });

        optionsView.setOnClickListener(view -> {
            if (actionBarPopupWindow != null) {
                return;
            }
            ActionBarPopupWindow.ActionBarPopupWindowLayout layout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(context);

            ActionBarMenuSubItem subItem;
            if (!this.permanent && canEdit) {
                subItem = new ActionBarMenuSubItem(context, true, false);
                subItem.setTextAndIcon(LocaleController.getString(R.string.Edit), R.drawable.msg_edit);
                layout.addView(subItem, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
                subItem.setOnClickListener(view12 -> {
                    if (actionBarPopupWindow != null) {
                        actionBarPopupWindow.dismiss();
                    }
                    delegate.editLink();
                });
            }

            subItem = new ActionBarMenuSubItem(context, true, false);
            subItem.setTextAndIcon(LocaleController.getString(R.string.GetQRCode), R.drawable.msg_qrcode);
            layout.addView(subItem, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
            subItem.setOnClickListener(view12 -> {
                showQrCode();
            });

            if (!hideRevokeOption) {
                subItem = new ActionBarMenuSubItem(context, false, true);
                subItem.setTextAndIcon(LocaleController.getString(R.string.RevokeLink), R.drawable.msg_delete);
                subItem.setColors(Theme.getColor(Theme.key_text_RedRegular), Theme.getColor(Theme.key_text_RedRegular));
                subItem.setOnClickListener(view1 -> {
                    if (actionBarPopupWindow != null) {
                        actionBarPopupWindow.dismiss();
                    }
                    revokeLink();
                });
                layout.addView(subItem, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
            }

            FrameLayout container;
            if (bottomSheet == null) {
                container = (FrameLayout) fragment.getParentLayout().getOverlayContainerView();
            } else {
                container = bottomSheet.getContainer();
            }


            if (container != null) {
                float x = 0;
                float y;
                getPointOnScreen(frameLayout, container, point);
                y = point[1];

                final FrameLayout finalContainer = container;
                View dimView = new View(context) {

                    @Override
                    protected void onDraw(Canvas canvas) {
                        canvas.drawColor(0x33000000);
                        getPointOnScreen(frameLayout, finalContainer, point);
                        canvas.save();
                        float clipTop = ((View) frameLayout.getParent()).getY() + frameLayout.getY();
                        if (clipTop < 1) {
                            canvas.clipRect(0, point[1] - clipTop + 1, getMeasuredWidth(), getMeasuredHeight());
                        }
                        canvas.translate(point[0], point[1]);

                        frameLayout.draw(canvas);
                        canvas.restore();
                    }
                };

                ViewTreeObserver.OnPreDrawListener preDrawListener = new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        dimView.invalidate();
                        return true;
                    }
                };
                finalContainer.getViewTreeObserver().addOnPreDrawListener(preDrawListener);
                container.addView(dimView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                dimView.setAlpha(0);
                dimView.animate().alpha(1f).setDuration(150);
                layout.measure(MeasureSpec.makeMeasureSpec(container.getMeasuredWidth(), MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(container.getMeasuredHeight(), MeasureSpec.UNSPECIFIED));


                actionBarPopupWindow = new ActionBarPopupWindow(layout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
                actionBarPopupWindow.setOnDismissListener(new PopupWindow.OnDismissListener() {
                    @Override
                    public void onDismiss() {
                        actionBarPopupWindow = null;
                        dimView.animate().cancel();
                        dimView.animate().alpha(0).setDuration(150).setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                if (dimView.getParent() != null) {
                                    finalContainer.removeView(dimView);
                                }
                                finalContainer.getViewTreeObserver().removeOnPreDrawListener(preDrawListener);
                            }
                        });
                    }
                });
                actionBarPopupWindow.setOutsideTouchable(true);
                actionBarPopupWindow.setFocusable(true);
                actionBarPopupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                actionBarPopupWindow.setAnimationStyle(R.style.PopupContextAnimation);
                actionBarPopupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
                actionBarPopupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);

                layout.setDispatchKeyEventListener(keyEvent -> {
                    if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_BACK && keyEvent.getRepeatCount() == 0 && actionBarPopupWindow.isShowing()) {
                        actionBarPopupWindow.dismiss(true);
                    }
                });

                if (AndroidUtilities.isTablet()) {
                    y += container.getPaddingTop();
                    x -= container.getPaddingLeft();
                }
                actionBarPopupWindow.showAtLocation(container, 0, (int) (container.getMeasuredWidth() - layout.getMeasuredWidth() - AndroidUtilities.dp(16) + container.getX() + x), (int) (y + frameLayout.getMeasuredHeight() + container.getY()));
            }

        });

        frameLayout.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                copyView.callOnClick();
            }
        });
        updateColors();
    }

    public void showBulletin(int resId, CharSequence str) {
        Bulletin b = BulletinFactory.of(fragment).createSimpleBulletin(resId, str);
        b.hideAfterBottomSheet = false;
        b.show(true);
    }

    private void getPointOnScreen(FrameLayout frameLayout, FrameLayout finalContainer, float[] point) {
        float x = 0;
        float y = 0;
        View v = frameLayout;
        while (v != finalContainer) {
            y += v.getY();
            x += v.getX();
            if (v instanceof ScrollView) {
                y -= v.getScrollY();
            }
            if (!(v.getParent() instanceof View)) {
                break;
            }
            v = (View) v.getParent();
            if (!(v instanceof ViewGroup)) {
                return;
            }
        }
        x -= finalContainer.getPaddingLeft();
        y -= finalContainer.getPaddingTop();
        point[0] = x;
        point[1] = y;
    }

    private String qrText;
    public void setQrText(String text) {
        qrText = text;
    }

    private void showQrCode() {
        qrCodeBottomSheet = new QRCodeBottomSheet(getContext(), LocaleController.getString(R.string.InviteByQRCode), link, qrText == null ? (isChannel ? LocaleController.getString(R.string.QRCodeLinkHelpChannel) : LocaleController.getString(R.string.QRCodeLinkHelpGroup)) : qrText, false) {
            @Override
            public void dismiss() {
                super.dismiss();
                qrCodeBottomSheet = null;
            }
        };
        qrCodeBottomSheet.setCenterAnimation(R.raw.qr_code_logo);
        qrCodeBottomSheet.show();
        if (actionBarPopupWindow != null) {
            actionBarPopupWindow.dismiss();
        }
    }

    public void updateColors() {
        copyView.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        shareView.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        removeView.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        copyView.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(8), Theme.getColor(Theme.key_featuredStickers_addButton), Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
        shareView.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(8), Theme.getColor(Theme.key_featuredStickers_addButton), Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
        removeView.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(8), Theme.getColor(Theme.key_chat_attachAudioBackground), ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_windowBackgroundWhite), 120)));
        frameLayout.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(8), Theme.getColor(Theme.key_graySection), ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_listSelector), (int) (255 * 0.3f))));
        linkView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        optionsView.setColorFilter(Theme.getColor(Theme.key_dialogTextGray3));
        //optionsView.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 1));
        avatarsContainer.countTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        avatarsContainer.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(6), 0, ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText), (int) (255 * 0.3f))));

        if (qrCodeBottomSheet != null) {
            qrCodeBottomSheet.updateColors();
        }
    }


    public void setLink(String link) {
        this.link = link;
        if (link == null) {
            linkView.setText(LocaleController.getString(R.string.Loading));
        } else if (link.startsWith("https://")) {
            linkView.setText(link.substring("https://".length()));
        } else {
            linkView.setText(link);
        }
    }

    public void setRevoke(boolean revoked) {
        this.revoked = revoked;
        if (revoked) {
            optionsView.setVisibility(View.GONE);
            shareView.setVisibility(View.GONE);
            copyView.setVisibility(View.GONE);
            removeView.setVisibility(View.VISIBLE);
        } else {
            optionsView.setVisibility(View.VISIBLE);
            shareView.setVisibility(View.VISIBLE);
            copyView.setVisibility(View.VISIBLE);
            removeView.setVisibility(View.GONE);
        }
    }

    public void showOptions(boolean b) {
        optionsView.setVisibility(b ? View.VISIBLE : View.GONE);
    }

    public void hideRevokeOption(boolean b) {
        if (hideRevokeOption != b) {
            hideRevokeOption = b;
            optionsView.setVisibility(View.VISIBLE);
            optionsView.setImageDrawable(ContextCompat.getDrawable(optionsView.getContext(), R.drawable.ic_ab_other));
        }
    }

    public void hideOptions() {
        optionsView.setVisibility(View.GONE);
        linkView.setGravity(Gravity.CENTER);
        removeView.setVisibility(View.GONE);
        avatarsContainer.setVisibility(View.GONE);
    }

    private class AvatarsContainer extends FrameLayout {

        TextView countTextView;
        AvatarsImageView avatarsImageView;

        public AvatarsContainer(@NonNull Context context) {
            super(context);
            avatarsImageView = new AvatarsImageView(context, false) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    int N = Math.min(3, usersCount);
                    int x = N == 0 ? 0 : (20 * (N - 1) + 24 + 8);
                    super.onMeasure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(x), MeasureSpec.EXACTLY), heightMeasureSpec);
                }
            };

            LinearLayout linearLayout = new LinearLayout(context);
            linearLayout.setOrientation(HORIZONTAL);

            addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER_HORIZONTAL));

            countTextView = new TextView(context);
            countTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            countTextView.setTypeface(AndroidUtilities.bold());

            linearLayout.addView(avatarsImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT));
            linearLayout.addView(countTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

            setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));
            avatarsImageView.commitTransition(false);
        }
    }

    private void revokeLink() {
        if (fragment.getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity());
        builder.setTitle(LocaleController.getString(R.string.RevokeLink));
        builder.setMessage(LocaleController.getString(R.string.RevokeAlert));
        builder.setPositiveButton(LocaleController.getString(R.string.RevokeButton), (dialogInterface, i) -> {
            if (delegate != null) {
                delegate.revokeLink();
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        AlertDialog dialog = builder.create();
        TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
        }
        builder.show();
    }

    public void setDelegate(Delegate delegate) {
        this.delegate = delegate;
    }

    public void setUsers(int usersCount, ArrayList<TLRPC.User> importers) {
        setUsers(usersCount, importers, false);
    }

    public void setUsers(int usersCount, ArrayList<TLRPC.User> importers, boolean animated) {
        this.usersCount = usersCount;
        if (usersCount == 0) {
            avatarsContainer.setVisibility(View.GONE);
            setPadding(AndroidUtilities.dp(19), AndroidUtilities.dp(18), AndroidUtilities.dp(19), AndroidUtilities.dp(18));
        } else {
            avatarsContainer.setVisibility(View.VISIBLE);
            setPadding(AndroidUtilities.dp(19), AndroidUtilities.dp(18), AndroidUtilities.dp(19), AndroidUtilities.dp(10));
            avatarsContainer.countTextView.setText(LocaleController.formatPluralString("PeopleJoined", usersCount));
            avatarsContainer.requestLayout();
        }
        if (importers != null) {
            for (int i = 0; i < importers.size(); ++i) {
                MessagesController.getInstance(UserConfig.selectedAccount).putUser(importers.get(i), false);
            }

            final int count = Math.min(3, Math.min(usersCount, importers.size()));
            avatarsContainer.avatarsImageView.setCount(count);
            for (int i = 0; i < count; ++i) {
                avatarsContainer.avatarsImageView.setObject(i, UserConfig.selectedAccount, importers.get(i));
            }
        } else {
            avatarsContainer.avatarsImageView.setCount(0);
        }
        avatarsContainer.avatarsImageView.commitTransition(animated);
    }

    private String loadedInviteLink;

    public void loadUsers(TLRPC.TL_chatInviteExported invite, long chatId) {
        if (invite == null) {
            setUsers(0, null, false);
            return;
        }
        if (!TextUtils.equals(loadedInviteLink, invite.link)) {
            setUsers(invite.usage, invite.importers, false);
            if (invite.usage > 0 && invite.importers == null && !loadingImporters) {
                TLRPC.TL_messages_getChatInviteImporters req = new TLRPC.TL_messages_getChatInviteImporters();
                if (invite.link != null) {
                    req.flags |= 2;
                    req.link = invite.link;
                }
                req.peer = MessagesController.getInstance(UserConfig.selectedAccount).getInputPeer(-chatId);
                req.offset_user = new TLRPC.TL_inputUserEmpty();
                req.limit = Math.min(invite.usage, 3);

                loadingImporters = true;
                ConnectionsManager.getInstance(UserConfig.selectedAccount).sendRequest(req, (response, error) -> {
                    AndroidUtilities.runOnUIThread(() -> {
                        loadingImporters = false;
                        loadedInviteLink = invite.link;
                        if (error == null) {
                            TLRPC.TL_messages_chatInviteImporters inviteImporters = (TLRPC.TL_messages_chatInviteImporters) response;
                            if (invite.importers == null) {
                                invite.importers = new ArrayList<>(3);
                            }
                            invite.importers.clear();
                            for (int i = 0; i < inviteImporters.users.size(); i++) {
                                invite.importers.addAll(inviteImporters.users);
                            }
                            setUsers(invite.usage, invite.importers, true);
                        }
                    });
                });
            }
        }
    }

    public interface Delegate {
        void revokeLink();

        default void editLink() {
        }

        default void removeLink() {
        }

        default void showUsersForPermanentLink() {
        }
    }

    public void setPermanent(boolean permanent) {
        this.permanent = permanent;
    }

    public void setCanEdit(boolean canEdit) {
        this.canEdit = canEdit;
    }
}
