package org.telegram.ui.Business;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.formatPluralString;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.AlertDialogDecor;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AnimatedColor;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.URLSpanCopyToClipboard;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;
import org.telegram.ui.Components.spoilers.SpoilersTextView;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PrivacyControlActivity;

import java.util.ArrayList;
import java.util.Objects;

public class BusinessLinksActivity extends UniversalFragment implements NotificationCenter.NotificationCenterDelegate {

    private final static int BUTTON_ADD = 1;

    private static AlertDialog currentDialog;

    public static void openRenameAlert(Context context, int currentAccount, TLRPC.TL_businessChatLink link, Theme.ResourcesProvider resourcesProvider, boolean forceNotAdaptive) {
        BaseFragment fragment = LaunchActivity.getLastFragment();
        Activity activity = AndroidUtilities.findActivity(context);
        View currentFocus = activity != null ? activity.getCurrentFocus() : null;
        final boolean isKeyboardVisible = fragment != null && fragment.getFragmentView() instanceof SizeNotifierFrameLayout && ((SizeNotifierFrameLayout) fragment.getFragmentView()).measureKeyboardHeight() > dp(20);
        final boolean adaptive = isKeyboardVisible && !forceNotAdaptive;
        AlertDialog[] dialog = new AlertDialog[1];
        AlertDialog.Builder builder;
        if (adaptive) {
            builder = new AlertDialogDecor.Builder(context, resourcesProvider);
        } else {
            builder = new AlertDialog.Builder(context, resourcesProvider);
        }
        builder.setTitle(getString(R.string.BusinessLinksRenameTitle));

        final int MAX_NAME_LENGTH = 32;
        EditTextBoldCursor editText = new EditTextBoldCursor(context) {
            AnimatedColor limitColor = new AnimatedColor(this);
            private int limitCount;
            AnimatedTextView.AnimatedTextDrawable limit = new AnimatedTextView.AnimatedTextDrawable(false, true, true);

            {
                limit.setAnimationProperties(.2f, 0, 160, CubicBezierInterpolator.EASE_OUT_QUINT);
                limit.setTextSize(dp(15.33f));
                limit.setCallback(this);
                limit.setGravity(Gravity.RIGHT);
            }

            @Override
            protected boolean verifyDrawable(@NonNull Drawable who) {
                return who == limit || super.verifyDrawable(who);
            }

            @Override
            protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
                super.onTextChanged(text, start, lengthBefore, lengthAfter);

                if (limit != null) {
                    limitCount = MAX_NAME_LENGTH - text.length();
                    limit.cancelAnimation();
                    limit.setText(limitCount > 4 ? "" : "" + limitCount);
                }
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);

                limit.setTextColor(limitColor.set(Theme.getColor(limitCount < 0 ? Theme.key_text_RedRegular : Theme.key_dialogSearchHint, resourcesProvider)));
                limit.setBounds(getScrollX(), 0, getScrollX() + getWidth(), getHeight());
                limit.draw(canvas);
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(36), MeasureSpec.EXACTLY));
            }
        };
        MediaDataController.getInstance(currentAccount).fetchNewEmojiKeywords(AndroidUtilities.getCurrentKeyboardLanguage(), true);
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        editText.setText(link.title);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        editText.setHintColor(Theme.getColor(Theme.key_groupcreate_hintText, resourcesProvider));
        editText.setCursorColor(Theme.getColor(Theme.key_chat_messagePanelCursor));
        editText.setHintText(LocaleController.getString(R.string.BusinessLinksNamePlaceholder));
        editText.setSingleLine(true);
        editText.setFocusable(true);
        editText.setLineColors(Theme.getColor(Theme.key_windowBackgroundWhiteInputField, resourcesProvider), Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated, resourcesProvider), Theme.getColor(Theme.key_text_RedRegular, resourcesProvider));
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        editText.setBackgroundDrawable(null);
        editText.setPadding(0, 0, dp(42), 0);

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);

        final TextView textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setText(LocaleController.getString(R.string.BusinessLinksRenameMessage));

        container.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 5, 24, 12));

        container.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 0, 24, 10));
        builder.setView(container);
        builder.setWidth(dp(292));

        editText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                String text = editText.getText().toString();
                if (text.length() > MAX_NAME_LENGTH) {
                    AndroidUtilities.shakeView(editText);
                    return true;
                }
                BusinessLinksController.getInstance(currentAccount).editLinkTitle(link.link, text);
                if (dialog[0] != null) {
                    dialog[0].dismiss();
                }
                if (dialog[0] == currentDialog) {
                    currentDialog = null;
                }
                if (currentFocus != null) {
                    currentFocus.requestFocus();
                }
                return true;
            }
            return false;
        });
        builder.setPositiveButton(LocaleController.getString(R.string.Done), (dialogInterface, i) -> {
            String text = editText.getText().toString();
            if (text.length() > MAX_NAME_LENGTH) {
                AndroidUtilities.shakeView(editText);
                return;
            }
            BusinessLinksController.getInstance(currentAccount).editLinkTitle(link.link, text);
            dialogInterface.dismiss();
        });
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), (dialogInterface, i) -> {
            dialogInterface.dismiss();
        });
        if (adaptive) {
            dialog[0] = currentDialog = builder.create();
            currentDialog.setOnDismissListener(d -> {
                currentDialog = null;
                if (currentFocus != null) {
                    currentFocus.requestFocus();
                }
            });
            currentDialog.setOnShowListener(d -> {
                editText.requestFocus();
                AndroidUtilities.showKeyboard(editText);
            });
            currentDialog.showDelayed(250);
        } else {
            builder.overrideDismissListener(dismiss -> {
                if (currentFocus != null) {
                    currentFocus.requestFocus();
                }
                AndroidUtilities.hideKeyboard(editText);
                AndroidUtilities.runOnUIThread(dismiss, 80);
            });
            dialog[0] = builder.create();
            dialog[0].setOnDismissListener(d -> {
                AndroidUtilities.hideKeyboard(editText);
            });
            dialog[0].setOnShowListener(d -> {
                if (currentFocus != null) {
                    currentFocus.clearFocus();
                }
                editText.requestFocus();
                AndroidUtilities.showKeyboard(editText);
            });
            dialog[0].show();
        }
        dialog[0].setDismissDialogByButtons(false);
        editText.setSelection(editText.getText().length());
    }

    public static boolean closeRenameAlert() {
        if (currentDialog != null && currentDialog.isShowing()) {
            currentDialog.dismiss();
            return true;
        }
        return false;
    }

    @Override
    public boolean onFragmentCreate() {
        getNotificationCenter().addObserver(this, NotificationCenter.businessLinksUpdated);
        getNotificationCenter().addObserver(this, NotificationCenter.businessLinkCreated);
        getNotificationCenter().addObserver(this, NotificationCenter.needDeleteBusinessLink);
        getNotificationCenter().addObserver(this, NotificationCenter.privacyRulesUpdated);
        BusinessLinksController.getInstance(currentAccount).load(true);
        ContactsController.getInstance(currentAccount).loadPrivacySettings();
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        getNotificationCenter().removeObserver(this, NotificationCenter.businessLinksUpdated);
        getNotificationCenter().removeObserver(this, NotificationCenter.businessLinkCreated);
        getNotificationCenter().removeObserver(this, NotificationCenter.needDeleteBusinessLink);
        getNotificationCenter().removeObserver(this, NotificationCenter.privacyRulesUpdated);
        Bulletin.hideVisible();
        super.onFragmentDestroy();
    }

    @Override
    public boolean onBackPressed() {
        if (closeRenameAlert()) {
            return false;
        }
        return super.onBackPressed();
    }

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(R.string.BusinessLinks);
    }

    private static int getPrivacyType(ArrayList<TLRPC.PrivacyRule> privacyRules) {
        int type = -1;
        boolean currentPlus = false;
        boolean currentMinus = false;
        boolean premium;
        for (int a = 0; a < privacyRules.size(); a++) {
            TLRPC.PrivacyRule rule = privacyRules.get(a);
            if (rule instanceof TLRPC.TL_privacyValueAllowChatParticipants) {
                currentPlus = true;
            } else if (rule instanceof TLRPC.TL_privacyValueDisallowChatParticipants) {
                currentMinus = true;
            } else if (rule instanceof TLRPC.TL_privacyValueAllowUsers) {
                currentPlus = true;
            } else if (rule instanceof TLRPC.TL_privacyValueDisallowUsers) {
                currentMinus = true;
            } else if (rule instanceof TLRPC.TL_privacyValueAllowPremium) {
                premium = true;
            } else if (type == -1) {
                if (rule instanceof TLRPC.TL_privacyValueAllowAll) {
                    type = 0;
                } else if (rule instanceof TLRPC.TL_privacyValueDisallowAll) {
                    type = 1;
                } else {
                    type = 2;
                }
            }
        }
        if (type == PrivacyControlActivity.TYPE_EVERYBODY || type == -1 && currentMinus) {
            return PrivacyControlActivity.TYPE_EVERYBODY;
        } else if (type == PrivacyControlActivity.TYPE_CONTACTS) {
            return PrivacyControlActivity.TYPE_CONTACTS;
        } else if (type == PrivacyControlActivity.TYPE_NOBODY || currentPlus) {
            return PrivacyControlActivity.TYPE_NOBODY;
        }
        return PrivacyControlActivity.TYPE_NOBODY;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asTopView(getString(R.string.BusinessLinksInfo), R.raw.biz_links));
        adapter.whiteSectionStart();
        if (BusinessLinksController.getInstance(currentAccount).canAddNew()) {
            items.add(UItem.asButton(BUTTON_ADD, R.drawable.menu_link_create, getString(R.string.BusinessLinksAdd)).accent());
        }
        for (TLRPC.TL_businessChatLink businessLink : BusinessLinksController.getInstance(currentAccount).links) {
            UItem item = UItem.asBusinessChatLink(new BusinessLinkWrapper(businessLink));
            items.add(item);
        }
        adapter.whiteSectionEnd();

        TLRPC.User user = UserConfig.getInstance(currentAccount).getCurrentUser();
        String linkPrefix = MessagesController.getInstance(currentAccount).linkPrefix + "/";
        ArrayList<String> links = new ArrayList<>(2);
        String publicUsername = UserObject.getPublicUsername(user);
        if (publicUsername != null) {
            links.add(linkPrefix + publicUsername);
        }
        ArrayList<TLRPC.PrivacyRule> phoneRules = ContactsController.getInstance(currentAccount).getPrivacyRules(ContactsController.PRIVACY_RULES_TYPE_PHONE);
        ArrayList<TLRPC.PrivacyRule> addedByPhoneRules = ContactsController.getInstance(currentAccount).getPrivacyRules(ContactsController.PRIVACY_RULES_TYPE_ADDED_BY_PHONE);
        if (!TextUtils.isEmpty(user.phone) && phoneRules != null && addedByPhoneRules != null) {
            if (getPrivacyType(phoneRules) != PrivacyControlActivity.TYPE_NOBODY || getPrivacyType(addedByPhoneRules) != PrivacyControlActivity.TYPE_CONTACTS) {
                links.add(linkPrefix + "+" + user.phone);
            }
        }
        if (!links.isEmpty()) {
            String text;
            if (links.size() == 2) {
                text = formatString(R.string.BusinessLinksFooterTwoLinks, links.get(0), links.get(1));
            } else {
                text = formatString(R.string.BusinessLinksFooterOneLink, links.get(0));
            }
            SpannableString spanned = new SpannableString(text);
            for (String link : links) {
                int index = text.indexOf(link);
                if (index > -1) {
                    spanned.setSpan(new URLSpanCopyToClipboard("https://" + link, this), index, index + link.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            items.add(UItem.asShadow(spanned));
        }
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == BUTTON_ADD) {
            BusinessLinksController.getInstance(currentAccount).createEmptyLink();
        } else if (item.viewType == UniversalAdapter.VIEW_TYPE_BUSINESS_LINK && item.object instanceof BusinessLinkWrapper) {
            BusinessLinkWrapper wrapper = (BusinessLinkWrapper) item.object;
            Bundle args = new Bundle();
            args.putInt("chatMode", ChatActivity.MODE_EDIT_BUSINESS_LINK);
            args.putString("business_link", wrapper.link.link);
            ChatActivity chatActivity = new ChatActivity(args);
            presentFragment(chatActivity);
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        if (item.viewType == UniversalAdapter.VIEW_TYPE_BUSINESS_LINK && item.object instanceof BusinessLinkWrapper) {
            TLRPC.TL_businessChatLink link = ((BusinessLinkWrapper) item.object).link;

            ItemOptions options = ItemOptions.makeOptions(this, view);
            options.add(R.drawable.msg_copy, getString(R.string.Copy), () -> {
                AndroidUtilities.addToClipboard(link.link);
                BulletinFactory.of(LaunchActivity.getLastFragment()).createCopyLinkBulletin().show();
            });
            options.add(R.drawable.msg_share, getString(R.string.LinkActionShare), () -> {
                Intent intent = new Intent(getContext(), LaunchActivity.class);
                intent.setAction(Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_TEXT, link.link);
                startActivityForResult(intent, 500);
            });
            options.add(R.drawable.msg_edit, getString(R.string.Rename), () -> {
                openRenameAlert(getContext(), currentAccount, link, resourceProvider, false);
            });
            options.add(R.drawable.msg_delete, getString(R.string.Delete), true, () -> {
                AlertDialog dialog = new AlertDialog.Builder(getContext(), getResourceProvider())
                        .setTitle(getString(R.string.BusinessLinksDeleteTitle))
                        .setMessage(getString(R.string.BusinessLinksDeleteMessage))
                        .setPositiveButton(getString(R.string.Remove), (di, w) -> {
                            BusinessLinksController.getInstance(currentAccount).deleteLinkUndoable(this, link.link);
                        })
                        .setNegativeButton(getString(R.string.Cancel), null)
                        .create();
                showDialog(dialog);
                TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                if (button != null) {
                    button.setTextColor(getThemedColor(Theme.key_text_RedBold));
                }
            });
            options.show();
            return true;
        }
        return false;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.businessLinksUpdated || id == NotificationCenter.privacyRulesUpdated) {
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        } else if (id == NotificationCenter.businessLinkCreated) {
            TLRPC.TL_businessChatLink link = (TLRPC.TL_businessChatLink) args[0];
            Bundle activityArgs = new Bundle();
            activityArgs.putInt("chatMode", ChatActivity.MODE_EDIT_BUSINESS_LINK);
            activityArgs.putString("business_link", link.link);
            ChatActivity chatActivity = new ChatActivity(activityArgs);
            presentFragment(chatActivity);
        } else if (id == NotificationCenter.needDeleteBusinessLink) {
            TLRPC.TL_businessChatLink link = (TLRPC.TL_businessChatLink) args[0];
            BusinessLinksController.getInstance(currentAccount).deleteLinkUndoable(this, link.link);
        }
    }

    public static class BusinessLinkWrapper {
        TLRPC.TL_businessChatLink link;

        public BusinessLinkWrapper(TLRPC.TL_businessChatLink link) {
            this.link = link;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BusinessLinkWrapper that = (BusinessLinkWrapper) o;
            return link.views == that.link.views &&
                    TextUtils.equals(link.link, that.link.link) &&
                    TextUtils.equals(link.title, that.link.title) &&
                    TextUtils.equals(link.message, that.link.message) &&
                    MediaDataController.entitiesEqual(link.entities, that.link.entities);
        }
    }

    @SuppressLint("ViewConstructor")
    public static class BusinessLinkView extends FrameLayout {

        private final ImageView imageView;
        private final SimpleTextView titleTextView;
        private final SpoilersTextView messagePreviewTextView;
        private final SimpleTextView clicksCountTextView;

        private final Theme.ResourcesProvider resourcesProvider;

        private boolean needDivider;

        private TLRPC.TL_businessChatLink businessLink;

        public BusinessLinkView(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);

            this.resourcesProvider = resourcesProvider;

            setWillNotDraw(false);

            imageView = new ImageView(context);
            imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            imageView.setImageResource(R.drawable.msg_limit_links);
            imageView.setPadding(dp(9), dp(9), dp(9), dp(9));
            imageView.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
            imageView.setBackground(Theme.createCircleDrawable(dp(36), Theme.getColor(Theme.key_featuredStickers_addButton)));
            imageView.setOnClickListener(view -> {
                if (businessLink != null) {
                    AndroidUtilities.addToClipboard(businessLink.link);
                    BulletinFactory.of(LaunchActivity.getLastFragment()).createCopyLinkBulletin().show();
                }
            });
            addView(imageView, LayoutHelper.createFrameRelatively(36, 36, Gravity.START | Gravity.CENTER_VERTICAL, 14, 0, 14, 0));

            titleTextView = new SimpleTextView(context);
            titleTextView.setTextSize(15);
            titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            titleTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            addView(titleTextView, LayoutHelper.createFrameRelatively(LayoutHelper.MATCH_PARENT, 20, Gravity.TOP | Gravity.FILL_HORIZONTAL, 64, 10, 14, 0));

            clicksCountTextView = new SimpleTextView(context);
            clicksCountTextView.setTextSize(14);
            clicksCountTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            clicksCountTextView.setGravity(LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT);
            addView(clicksCountTextView, LayoutHelper.createFrameRelatively(LayoutHelper.MATCH_PARENT, 18, Gravity.TOP | Gravity.FILL_HORIZONTAL, 64, 10.66f, 14, 0));

            messagePreviewTextView = new SpoilersTextView(context);
            messagePreviewTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            messagePreviewTextView.setMaxLines(1);
            messagePreviewTextView.setEllipsize(TextUtils.TruncateAt.END);
            messagePreviewTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
            messagePreviewTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            messagePreviewTextView.allowClickSpoilers = false;
            messagePreviewTextView.setUseAlphaForEmoji(false);
            NotificationCenter.listenEmojiLoading(messagePreviewTextView);
            addView(messagePreviewTextView, LayoutHelper.createFrameRelatively(LayoutHelper.MATCH_PARENT, 20, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL, 64, 0, 14, 6));
        }

        public void set(BusinessLinkWrapper linkWrapper, boolean needDivider) {
            this.needDivider = needDivider;
            this.businessLink = linkWrapper.link;

            if (!TextUtils.isEmpty(businessLink.title)) {
                titleTextView.setText(businessLink.title);
            } else {
                titleTextView.setText(BusinessLinksController.stripHttps(businessLink.link));
            }

            CharSequence text = new SpannableStringBuilder(businessLink.message);
            MediaDataController.addTextStyleRuns(businessLink.entities, businessLink.message, (Spannable) text);
            text = Emoji.replaceEmoji(text, messagePreviewTextView.getPaint().getFontMetricsInt(), false);
            MessageObject.replaceAnimatedEmoji(text, businessLink.entities, messagePreviewTextView.getPaint().getFontMetricsInt());
            messagePreviewTextView.setText(text);

            if (businessLink.views == 0) {
                clicksCountTextView.setText(formatString(R.string.NoClicks));
            } else {
                clicksCountTextView.setText(formatPluralString("Clicks", businessLink.views));
            }
            clicksCountTextView.requestLayout();

            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (needDivider) {
                Paint dividerPaint = Theme.getThemePaint(Theme.key_paint_divider, resourcesProvider);
                if (dividerPaint == null)
                    dividerPaint = Theme.dividerPaint;
                canvas.drawRect(dp(LocaleController.isRTL ? 0 : 64), getMeasuredHeight() - 1, getWidth() - dp(LocaleController.isRTL ? 64 : 0), getMeasuredHeight(), dividerPaint);
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                    MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(dp(56) + (needDivider ? 1 : 0), MeasureSpec.EXACTLY)
            );
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            if (LocaleController.isRTL) {
                titleTextView.setPadding(clicksCountTextView.getTextWidth(), 0, 0, 0);
            } else {
                titleTextView.setPadding(0, 0, clicksCountTextView.getTextWidth(), 0);
            }
        }
    }
}
