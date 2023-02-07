package org.telegram.ui;

import android.content.Context;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.CacheByChatsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarsDrawable;
import org.telegram.ui.Components.AvatarsImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;

import java.util.ArrayList;

public class KeepMediaPopupView extends ActionBarPopupWindow.ActionBarPopupWindowLayout {

    private final TextView description;
    ActionBarMenuSubItem delete;
    ActionBarMenuSubItem forever;
    ActionBarMenuSubItem oneMonth;
    ActionBarMenuSubItem oneWeek;
    ActionBarMenuSubItem oneDay;
    ActionBarMenuSubItem oneMinute;
    ArrayList<CheckItem> checkItems = new ArrayList<>();

    ExceptionsView exceptionsView;
    int time;
    int currentType;
    private final CacheByChatsController cacheByChatsController;
    Callback callback;
    private ArrayList<CacheByChatsController.KeepMediaException> exceptions;
    BaseFragment parentFragment;
    FrameLayout gap;

    public KeepMediaPopupView(BaseFragment baseFragment, Context context) {
        super(context, null);
        parentFragment = baseFragment;
        cacheByChatsController = baseFragment.getMessagesController().getCacheByChatsController();
        setFitItems(true);

//        if (BuildVars.DEBUG_PRIVATE_VERSION) {
//            oneMinute = ActionBarMenuItem.addItem(this, R.drawable.msg_autodelete, LocaleController.formatPluralString("Minutes", 1), false, null);
//            checkItems.add(new CheckItem(oneMinute, CacheByChatsController.KEEP_MEDIA_ONE_MINUTE));
//        }
        oneDay = ActionBarMenuItem.addItem(this, R.drawable.msg_autodelete_1d, LocaleController.formatPluralString("Days", 1), false, null);
        oneWeek = ActionBarMenuItem.addItem(this, R.drawable.msg_autodelete_1w, LocaleController.formatPluralString("Weeks", 1), false, null);
        oneMonth = ActionBarMenuItem.addItem(this, R.drawable.msg_autodelete_1m, LocaleController.formatPluralString("Months", 1), false, null);
        forever = ActionBarMenuItem.addItem(this, R.drawable.msg_cancel, LocaleController.getString("AutoDeleteMediaNever", R.string.AutoDeleteMediaNever), false, null);
        delete = ActionBarMenuItem.addItem(this, R.drawable.msg_delete, LocaleController.getString("DeleteException", R.string.DeleteException), false, null);
        delete.setColors(Theme.getColor(Theme.key_windowBackgroundWhiteRedText), Theme.getColor(Theme.key_windowBackgroundWhiteRedText));
        checkItems.add(new CheckItem(oneDay, CacheByChatsController.KEEP_MEDIA_ONE_DAY));
        checkItems.add(new CheckItem(oneWeek, CacheByChatsController.KEEP_MEDIA_ONE_WEEK));
        checkItems.add(new CheckItem(oneMonth, CacheByChatsController.KEEP_MEDIA_ONE_MONTH));
        checkItems.add(new CheckItem(forever, CacheByChatsController.KEEP_MEDIA_FOREVER));
        checkItems.add(new CheckItem(delete, CacheByChatsController.KEEP_MEDIA_DELETE));


        gap = new FrameLayout(context);
        gap.setBackgroundColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuSeparator));
        View gapShadow = new View(context);
        gapShadow.setBackground(Theme.getThemedDrawable(context, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow, null));
        gap.addView(gapShadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        gap.setTag(R.id.fit_width_tag, 1);
        addView(gap, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 8));

        exceptionsView = new ExceptionsView(context);
        addView(exceptionsView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));

        exceptionsView.setOnClickListener(v -> {
            window.dismiss();

            if (exceptions.isEmpty()) {

                Bundle args = new Bundle();
                args.putBoolean("onlySelect", true);
                args.putBoolean("onlySelect", true);
                args.putBoolean("checkCanWrite", false);
                if (currentType == CacheControlActivity.KEEP_MEDIA_TYPE_GROUP) {
                    args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_GROUPS_ONLY);
                } else if (currentType == CacheControlActivity.KEEP_MEDIA_TYPE_CHANNEL) {
                    args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_CHANNELS_ONLY);
                } else {
                    args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_USERS_ONLY);
                }
                args.putBoolean("allowGlobalSearch", false);
                DialogsActivity activity = new DialogsActivity(args);
                activity.setDelegate((fragment, dids, message, param, topicsFragment) -> {
                    CacheByChatsController.KeepMediaException newException = null;
                    for (int i = 0; i < dids.size(); i++) {
                        exceptions.add(newException = new CacheByChatsController.KeepMediaException(dids.get(i).dialogId, CacheByChatsController.KEEP_MEDIA_ONE_DAY));
                    }
                    cacheByChatsController.saveKeepMediaExceptions(currentType, exceptions);

                    Bundle bundle = new Bundle();
                    bundle.putInt("type", currentType);
                    CacheByChatsController.KeepMediaException finalNewException = newException;
                    CacheChatsExceptionsFragment cacheChatsExceptionsFragment = new CacheChatsExceptionsFragment(bundle) {
                        @Override
                        public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
                            super.onTransitionAnimationEnd(isOpen, backward);
                            if (isOpen && !backward) {
                                activity.removeSelfFromStack();
                            }
                        }
                    };
                    cacheChatsExceptionsFragment.setExceptions(exceptions);
                    parentFragment.presentFragment(cacheChatsExceptionsFragment);
                    AndroidUtilities.runOnUIThread(() -> cacheChatsExceptionsFragment.showPopupFor(finalNewException), 150);
                    return true;
                });
                baseFragment.presentFragment(activity);
            } else {
                Bundle bundle = new Bundle();
                bundle.putInt("type", currentType);
                CacheChatsExceptionsFragment cacheChatsExceptionsFragment = new CacheChatsExceptionsFragment(bundle);
                cacheChatsExceptionsFragment.setExceptions(exceptions);
                baseFragment.presentFragment(cacheChatsExceptionsFragment);
            }
        });

        for (int i = 0; i < checkItems.size(); i++) {
            int keepMedia = checkItems.get(i).type;
            checkItems.get(i).item.setOnClickListener(v -> {
                window.dismiss();
                if (currentType >= 0) {
                    cacheByChatsController.setKeepMedia(currentType, keepMedia);
                    if (callback != null) {
                        callback.onKeepMediaChange(currentType, keepMedia);
                    }
                } else {
                    if (callback != null) {
                        callback.onKeepMediaChange(currentType, keepMedia);
                    }
                }
            });
        }

        description = new LinkSpanDrawable.LinksTextView(context);
        description.setTag(R.id.fit_width_tag, 1);
        description.setPadding(AndroidUtilities.dp(13), 0, AndroidUtilities.dp(13), AndroidUtilities.dp(8));
        description.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        description.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem));
        description.setMovementMethod(LinkMovementMethod.getInstance());
        description.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkText));
        description.setText(LocaleController.getString("KeepMediaPopupDescription", R.string.KeepMediaPopupDescription));
        addView(description, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8, 0, 0));

    }


    public void update(int type) {
        currentType = type;
        exceptions = cacheByChatsController.getKeepMediaExceptions(type);
        if (exceptions.isEmpty()) {
            exceptionsView.titleView.setText(LocaleController.getString("AddAnException", R.string.AddAnException));
            exceptionsView.titleView.setRightPadding(AndroidUtilities.dp(8));
            exceptionsView.avatarsImageView.setObject(0, parentFragment.getCurrentAccount(), null);
            exceptionsView.avatarsImageView.setObject(1, parentFragment.getCurrentAccount(), null);
            exceptionsView.avatarsImageView.setObject(2, parentFragment.getCurrentAccount(), null);
            exceptionsView.avatarsImageView.commitTransition(false);
        } else {
            int count = Math.min(3, exceptions.size());
            exceptionsView.titleView.setRightPadding(AndroidUtilities.dp(64 + Math.max(0, count - 1) * 12));
            exceptionsView.titleView.setText(LocaleController.formatPluralString("ExceptionShort", exceptions.size(), exceptions.size()));
            for (int i = 0; i < count; i++) {
                exceptionsView.avatarsImageView.setObject(i, parentFragment.getCurrentAccount(), parentFragment.getMessagesController().getUserOrChat(exceptions.get(i).dialogId));
            }
            exceptionsView.avatarsImageView.commitTransition(false);
        }
        delete.setVisibility(View.GONE);
        description.setVisibility(View.GONE);
        updateAvatarsPosition();
    }

    public void updateForDialog(boolean addedRecently) {
        currentType = -1;
        gap.setVisibility(View.VISIBLE);
        delete.setVisibility(addedRecently ? View.GONE : View.VISIBLE);
        description.setVisibility(View.VISIBLE);
        exceptionsView.setVisibility(View.GONE);
    }


    private class ExceptionsView extends FrameLayout {

        SimpleTextView titleView;
        AvatarsImageView avatarsImageView;

        public ExceptionsView(Context context) {
            super(context);
            titleView = new SimpleTextView(context);
            titleView.setTextSize(16);
            titleView.setEllipsizeByGradient(true);
            titleView.setRightPadding(AndroidUtilities.dp(68));
            titleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));

            addView(titleView, LayoutHelper.createFrame(0, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 19, 0, 19, 0));

            avatarsImageView = new AvatarsImageView(context, false);
            avatarsImageView.avatarsDrawable.setShowSavedMessages(true);
            avatarsImageView.setStyle(AvatarsDrawable.STYLE_MESSAGE_SEEN);
            avatarsImageView.setAvatarsTextSize(AndroidUtilities.dp(22));
            addView(avatarsImageView, LayoutHelper.createFrame(24 + 12 + 12 + 8, LayoutHelper.MATCH_PARENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 4, 0));

            setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_listSelector), 0, 4));
        }

        boolean ignoreLayout;

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            View parent = (View) getParent();
            if (parent != null && parent.getWidth() > 0) {
                widthMeasureSpec = MeasureSpec.makeMeasureSpec(parent.getWidth(), MeasureSpec.EXACTLY);
            }
            ignoreLayout = true;
            titleView.setVisibility(View.GONE);
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            titleView.setVisibility(View.VISIBLE);
            titleView.getLayoutParams().width = getMeasuredWidth();//- AndroidUtilities.dp(40);
            ignoreLayout = false;
            updateAvatarsPosition();
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }

        @Override
        public void requestLayout() {
            if (ignoreLayout) {
                return;
            }
            super.requestLayout();
        }
    }

    private void updateAvatarsPosition() {
        if (exceptions != null) {
            exceptionsView.avatarsImageView.setTranslationX(AndroidUtilities.dp(12) * (3 - Math.min(3, exceptions.size())));
        }
    }

    private static class CheckItem {
        final ActionBarMenuSubItem item;
        final int type;

        private CheckItem(ActionBarMenuSubItem item, int type) {
            this.item = item;
            this.type = type;
        }
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public interface Callback {
        void onKeepMediaChange(int type, int keepMedia);
    }
}
