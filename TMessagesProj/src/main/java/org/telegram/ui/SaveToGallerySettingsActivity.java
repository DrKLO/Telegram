package org.telegram.ui;

import static org.telegram.messenger.SharedConfig.SAVE_TO_GALLERY_FLAG_CHANNELS;
import static org.telegram.messenger.SharedConfig.SAVE_TO_GALLERY_FLAG_GROUP;
import static org.telegram.messenger.SharedConfig.SAVE_TO_GALLERY_FLAG_PEER;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Bundle;
import android.util.LongSparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SaveToGallerySettingsHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.Cells.UserCell2;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ListView.AdapterWithDiffUtils;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SeekBarView;

import java.util.ArrayList;
import java.util.Objects;

public class SaveToGallerySettingsActivity extends BaseFragment {

    int type;
    long dialogId;
    SaveToGallerySettingsHelper.DialogException dialogException;
    boolean isNewException;

    public SaveToGallerySettingsActivity(Bundle bundle) {
        super(bundle);
    }

    @Override
    public boolean onFragmentCreate() {
        type = getArguments().getInt("type");
        exceptionsDialogs = getUserConfig().getSaveGalleryExceptions(type);
        dialogId = getArguments().getLong("dialog_id");
        if (dialogId != 0) {
            dialogException = UserConfig.getInstance(currentAccount).getSaveGalleryExceptions(type).get(dialogId);
            if (dialogException == null) {
                isNewException = true;
                dialogException = new SaveToGallerySettingsHelper.DialogException();
                SaveToGallerySettingsHelper.Settings globalSettings = SaveToGallerySettingsHelper.getSettings(type);

                dialogException.savePhoto = globalSettings.savePhoto;
                dialogException.saveVideo = globalSettings.saveVideo;
                dialogException.limitVideo = globalSettings.limitVideo;

                dialogException.dialogId = dialogId;
            }
        }
        return super.onFragmentCreate();
    }

    private final int VIEW_TYPE_ADD_EXCEPTION = 1;
    private final int VIEW_TYPE_CHAT = 2;
    private final int VIEW_TYPE_DIVIDER = 3;
    private final int VIEW_TYPE_DELETE_ALL = 4;
    private final int VIEW_TYPE_HEADER = 5;
    private final int VIEW_TYPE_TOGGLE = 6;
    private final int VIEW_TYPE_DIVIDER_INFO = 7;
    private final int VIEW_TYPE_CHOOSER = 8;
    private static final int VIEW_TYPE_USER_INFO = 9;
    private final int VIEW_TYPE_DIVIDER_LAST = 10;


    int savePhotosRow;
    int saveVideosRow;
    int videoDividerRow;

    Adapter adapter;

    RecyclerListView recyclerListView;

    ArrayList<Item> items = new ArrayList<>();
    LongSparseArray<SaveToGallerySettingsHelper.DialogException> exceptionsDialogs = new LongSparseArray<>();

    @Override
    public View createView(Context context) {
        FrameLayout frameLayout = new FrameLayout(context);
        fragmentView = frameLayout;

        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                    return;
                }
            }
        });
        if (dialogException != null) {
            if (isNewException) {
                actionBar.setTitle(LocaleController.getString(R.string.NotificationsNewException));
            } else {
                actionBar.setTitle(LocaleController.getString(R.string.SaveToGalleryException));
            }
        } else {
            if (type == SAVE_TO_GALLERY_FLAG_PEER) {
                actionBar.setTitle(LocaleController.getString(R.string.SaveToGalleryPrivate));
            } else if (type == SAVE_TO_GALLERY_FLAG_GROUP) {
                actionBar.setTitle(LocaleController.getString(R.string.SaveToGalleryGroups));
            } else {
                actionBar.setTitle(LocaleController.getString(R.string.SaveToGalleryChannels));
            }
        }

        recyclerListView = new RecyclerListView(context);
        DefaultItemAnimator defaultItemAnimator = new DefaultItemAnimator();
        defaultItemAnimator.setDurations(400);
        defaultItemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        defaultItemAnimator.setDelayAnimations(false);
        defaultItemAnimator.setSupportsChangeAnimations(false);
        recyclerListView.setItemAnimator(defaultItemAnimator);
        recyclerListView.setLayoutManager(new LinearLayoutManager(context));
        recyclerListView.setAdapter(adapter = new Adapter());
        recyclerListView.setOnItemClickListener((view, position, x, y) -> {
            if (position == savePhotosRow) {
                SaveToGallerySettingsHelper.Settings settings = getSettings();
                settings.savePhoto = !settings.savePhoto;
                onSettingsUpdated();
                updateRows();
            } else if (position == saveVideosRow) {
                SaveToGallerySettingsHelper.Settings settings = getSettings();
                settings.saveVideo = !settings.saveVideo;
                onSettingsUpdated();
                updateRows();
            } else if (items.get(position).viewType == VIEW_TYPE_ADD_EXCEPTION) {
                Bundle args = new Bundle();
                args.putBoolean("onlySelect", true);
                args.putBoolean("checkCanWrite", false);
                if (type == SAVE_TO_GALLERY_FLAG_GROUP) {
                    args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_GROUPS_ONLY);
                } else if (type == SAVE_TO_GALLERY_FLAG_CHANNELS) {
                    args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_CHANNELS_ONLY);
                } else {
                    args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_USERS_ONLY);
                }
                args.putBoolean("allowGlobalSearch", false);
                DialogsActivity activity = new DialogsActivity(args);
                activity.setDelegate((fragment, dids, message, param, notify, scheduleDate, scheduleRepeatPeriod, topicsFragment) -> {
                    Bundle args2 = new Bundle();
                    args2.putLong("dialog_id", dids.get(0).dialogId);
                    args2.putInt("type", type);
                    SaveToGallerySettingsActivity addExceptionActivity = new SaveToGallerySettingsActivity(args2);
                    presentFragment(addExceptionActivity, true);
                    return true;
                });
                presentFragment(activity);
            } else if (items.get(position).viewType == VIEW_TYPE_CHAT) {
                Bundle args2 = new Bundle();
                args2.putLong("dialog_id", items.get(position).exception.dialogId);
                args2.putInt("type", type);
                SaveToGallerySettingsActivity addExceptionActivity = new SaveToGallerySettingsActivity(args2);
                presentFragment(addExceptionActivity);
            } else if (items.get(position).viewType == VIEW_TYPE_DELETE_ALL) {
                AlertDialog alertDialog = AlertsCreator.createSimpleAlert(getContext(),
                        LocaleController.getString(R.string.NotificationsDeleteAllExceptionTitle),
                        LocaleController.getString(R.string.NotificationsDeleteAllExceptionAlert),
                        LocaleController.getString(R.string.Delete),
                        () -> {
                            exceptionsDialogs.clear();
                            getUserConfig().updateSaveGalleryExceptions(type, exceptionsDialogs);
                            updateRows();
                        }, null).create();
                alertDialog.show();
                alertDialog.redPositive();
            }
        });
        recyclerListView.setOnItemLongClickListener((view, position, x, y) -> {
            if (items.get(position).viewType == VIEW_TYPE_CHAT) {

                SaveToGallerySettingsHelper.DialogException exception = items.get(position).exception;
                ActionBarPopupWindow.ActionBarPopupWindowLayout actionBarPopupWindowLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(getContext());
                ActionBarMenuSubItem edit = ActionBarMenuItem.addItem(actionBarPopupWindowLayout, R.drawable.msg_customize, LocaleController.getString(R.string.EditException), false, null);
                ActionBarMenuSubItem delete = ActionBarMenuItem.addItem(actionBarPopupWindowLayout, R.drawable.msg_delete, LocaleController.getString(R.string.DeleteException), false, null);
                delete.setColors(Theme.getColor(Theme.key_text_RedRegular), Theme.getColor(Theme.key_text_RedRegular));
                ActionBarPopupWindow popupWindow = AlertsCreator.createSimplePopup(SaveToGallerySettingsActivity.this, actionBarPopupWindowLayout, view, x, y);
                actionBarPopupWindowLayout.setParentWindow(popupWindow);

                edit.setOnClickListener(v -> {
                    popupWindow.dismiss();
                    Bundle args2 = new Bundle();
                    args2.putLong("dialog_id", items.get(position).exception.dialogId);
                    args2.putInt("type", type);
                    SaveToGallerySettingsActivity addExceptionActivity = new SaveToGallerySettingsActivity(args2);
                    presentFragment(addExceptionActivity);
                });
                delete.setOnClickListener(v -> {
                    popupWindow.dismiss();
                    LongSparseArray<SaveToGallerySettingsHelper.DialogException> allExceptions = getUserConfig().getSaveGalleryExceptions(type);
                    allExceptions.remove(exception.dialogId);
                    getUserConfig().updateSaveGalleryExceptions(type, allExceptions);
                    updateRows();
                });
                return true;
            }
            return false;
        });
        frameLayout.addView(recyclerListView);
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        if (dialogException != null) {
            //  ((ViewGroup.MarginLayoutParams)recyclerListView.getLayoutParams()).bottomMargin = AndroidUtilities.dp()

            FrameLayout button = new FrameLayout(getContext());
            button.setBackground(Theme.AdaptiveRipple.filledRectByKey(Theme.key_featuredStickers_addButton, 8));

            TextView textView = new TextView(getContext());
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            textView.setText(isNewException ? LocaleController.getString(R.string.AddException) : LocaleController.getString(R.string.SaveException));
            textView.setGravity(Gravity.CENTER);
            textView.setTypeface(AndroidUtilities.bold());
            textView.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
            button.addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
            button.setOnClickListener(v -> {
                if (isNewException) {
                    LongSparseArray<SaveToGallerySettingsHelper.DialogException> allExceptions = getUserConfig().getSaveGalleryExceptions(type);
                    allExceptions.put(dialogException.dialogId, dialogException);
                    getUserConfig().updateSaveGalleryExceptions(type, allExceptions);
                }
                finishFragment();
            });
            frameLayout.addView(button, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM, 16, 16, 16, 16));

        }
        updateRows();
        return fragmentView;
    }

    private void updateRows() {
        boolean animated = !isPaused && adapter != null;
        ArrayList<Item> oldItems = null;
        if (animated) {
            oldItems = new ArrayList();
            oldItems.addAll(items);
        }

        items.clear();

        if (dialogException != null) {
            items.add(new Item(VIEW_TYPE_USER_INFO));
            items.add(new Item(VIEW_TYPE_DIVIDER));
        }
        items.add(new Item(VIEW_TYPE_HEADER, LocaleController.getString(R.string.SaveToGallery)));
        savePhotosRow = items.size();
        items.add(new Item(VIEW_TYPE_TOGGLE));
        saveVideosRow = items.size();
        items.add(new Item(VIEW_TYPE_TOGGLE));
        String text = null;
        if (dialogException != null) {
            text = LocaleController.getString(R.string.SaveToGalleryHintCurrent);
        } else if (type == SAVE_TO_GALLERY_FLAG_PEER) {
            text = LocaleController.getString(R.string.SaveToGalleryHintUser);
        } else if (type == SAVE_TO_GALLERY_FLAG_CHANNELS) {
            text = LocaleController.getString(R.string.SaveToGalleryHintChannels);
        } else if (type == SAVE_TO_GALLERY_FLAG_GROUP) {
            text = LocaleController.getString(R.string.SaveToGalleryHintGroup);
        }
        items.add(new Item(VIEW_TYPE_DIVIDER_INFO, text));

        if (getSettings().saveVideo) {
            items.add(new Item(VIEW_TYPE_HEADER, LocaleController.getString(R.string.MaxVideoSize)));
            items.add(new Item(VIEW_TYPE_CHOOSER));
            videoDividerRow = items.size();
            items.add(new Item(VIEW_TYPE_DIVIDER_INFO));
        } else {
            videoDividerRow = -1;
        }

        if (dialogException == null) {
            exceptionsDialogs = getUserConfig().getSaveGalleryExceptions(type);
            items.add(new Item(VIEW_TYPE_ADD_EXCEPTION));
            boolean added = false;
            for (int i = 0; i < exceptionsDialogs.size(); i++) {
                items.add(new Item(VIEW_TYPE_CHAT, exceptionsDialogs.valueAt(i)));
                added = true;
            }

            if (added) {
                items.add(new Item(VIEW_TYPE_DIVIDER));
                items.add(new Item(VIEW_TYPE_DELETE_ALL));
            }
            items.add(new Item(VIEW_TYPE_DIVIDER_LAST));
        }

        if (adapter != null) {
            if (oldItems != null) {
                adapter.setItems(oldItems, items);
            } else {
                adapter.notifyDataSetChanged();
            }
        }
    }

    private class Adapter extends AdapterWithDiffUtils {
        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = null;
            switch (viewType) {
                case VIEW_TYPE_USER_INFO:
                    UserCell2 userCell2 = new UserCell2(getContext(), 4, 0, getResourceProvider());
                    TLObject object;
                    if (DialogObject.isUserDialog(dialogId)) {
                        object = MessagesController.getInstance(currentAccount).getUser(dialogId);
                    } else {
                        object = MessagesController.getInstance(currentAccount).getChat(-dialogId);
                    }
                    userCell2.setData(object, null, null, 0);
                    view = userCell2;
                    view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_CHAT:
                    view = new UserCell(parent.getContext(), 4, 0, false, false);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_ADD_EXCEPTION:
                    TextCell textCell = new TextCell(parent.getContext());
                    textCell.setTextAndIcon(LocaleController.getString(R.string.NotificationsAddAnException), R.drawable.msg_contact_add, true);
                    textCell.setColors(Theme.key_windowBackgroundWhiteBlueIcon, Theme.key_windowBackgroundWhiteBlueButton);
                    view = textCell;
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_DIVIDER_LAST:
                    view = new ShadowSectionCell(parent.getContext());
                    view.setBackgroundDrawable(Theme.getThemedDrawable(getContext(), R.drawable.greydivider_bottom, Theme.getColor(Theme.key_windowBackgroundGrayShadow, getResourceProvider())));
                    break;
                case VIEW_TYPE_DIVIDER:
                    view = new ShadowSectionCell(parent.getContext());
                    break;
                case VIEW_TYPE_DELETE_ALL:
                    textCell = new TextCell(parent.getContext());
                    textCell.setText(LocaleController.getString(R.string.NotificationsDeleteAllException), false);
                    textCell.setColors(-1, Theme.key_text_RedRegular);
                    view = textCell;
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_HEADER:
                    HeaderCell headerCell = new HeaderCell(parent.getContext());
                    view = headerCell;
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_TOGGLE:
                    TextCheckCell textCheckCell = new TextCheckCell(parent.getContext());
                    view = textCheckCell;
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_DIVIDER_INFO:
                    TextInfoPrivacyCell textInfoPrivacyCell = new TextInfoPrivacyCell(parent.getContext());
                    view = textInfoPrivacyCell;
                    break;
                case VIEW_TYPE_CHOOSER:
                    LinearLayout linearLayout = new LinearLayout(getContext());
                    linearLayout.setOrientation(LinearLayout.VERTICAL);
                    SeekBarView slideChooseView = new SeekBarView(getContext());
                    FrameLayout textContainer = new FrameLayout(getContext());

                    SelectableAnimatedTextView lowerTextView = new SelectableAnimatedTextView(getContext());
                    lowerTextView.setTextSize(AndroidUtilities.dp(13));
                    lowerTextView.setText(AndroidUtilities.formatFileSize(1024 * 512, true, false));
                    textContainer.addView(lowerTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM));

                    SelectableAnimatedTextView midTextView = new SelectableAnimatedTextView(getContext());
                    midTextView.setTextSize(AndroidUtilities.dp(13));
                    // midTextView.setText(AndroidUtilities.formatFileSize(1024 * 512, true));
                    textContainer.addView(midTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM));

                    SelectableAnimatedTextView topTextView = new SelectableAnimatedTextView(getContext());
                    topTextView.setTextSize(AndroidUtilities.dp(13));
                    topTextView.setText(AndroidUtilities.formatFileSize(SaveToGallerySettingsHelper.MAX_VIDEO_LIMIT, true, false));
                    textContainer.addView(topTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.BOTTOM));


                    linearLayout.addView(textContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 20, 0, 21, 10, 21, 0));
                    linearLayout.addView(slideChooseView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 38, 0, 5, 0, 5, 4));
                    SaveToGallerySettingsHelper.Settings settings = getSettings();
                    long currentValue = settings.limitVideo;
                    long maxValue = 4L * 1000 * 1024 * 1024;
                    if (currentValue < 0 || currentValue > SaveToGallerySettingsHelper.MAX_VIDEO_LIMIT) {
                        currentValue = SaveToGallerySettingsHelper.MAX_VIDEO_LIMIT;
                    }
                    slideChooseView.setReportChanges(true);
                    slideChooseView.setDelegate(new SeekBarView.SeekBarViewDelegate() {
                        @Override
                        public void onSeekBarDrag(boolean stop, float progress) {
                            boolean animated = slideChooseView.isAttachedToWindow();
                            long limitExtremum = 100 * 1024 * 1024;
                            float limitExtremumF = 0.7f;
                            float limitExtremumK = 1f - limitExtremumF;
                            long value;
                            if (progress > limitExtremumF) {
                                float p = (progress - limitExtremumF) / limitExtremumK;
                                value = (long) (limitExtremum + (SaveToGallerySettingsHelper.MAX_VIDEO_LIMIT - limitExtremum) * p);
                            } else {
                                float p = progress / limitExtremumF;
                                value = (long) (1024 * 512 + (limitExtremum - 1024 * 512) * p);
                            }
                            if (progress >= 1f) {
                                lowerTextView.setSelectedInternal(false, animated);
                                midTextView.setSelectedInternal(false, animated);
                                topTextView.setSelectedInternal(true, animated);
                                AndroidUtilities.updateViewVisibilityAnimated(midTextView, false, 0.8f, animated);
                            } else if (progress == 0f) {
                                lowerTextView.setSelectedInternal(true, animated);
                                midTextView.setSelectedInternal(false, animated);
                                topTextView.setSelectedInternal(false, animated);
                                AndroidUtilities.updateViewVisibilityAnimated(midTextView, false, 0.8f, animated);
                            } else {
                                midTextView.setText(
                                        LocaleController.formatString("UpToFileSize", R.string.UpToFileSize,
                                                AndroidUtilities.formatFileSize(value, true, false)
                                        ), false);
                                lowerTextView.setSelectedInternal(false, animated);
                                midTextView.setSelectedInternal(true, animated);
                                topTextView.setSelectedInternal(false, animated);
                                AndroidUtilities.updateViewVisibilityAnimated(midTextView, true, 0.8f, animated);
                            }
                            if (stop) {
                                getSettings().limitVideo = value;
                                onSettingsUpdated();
                            }

                        }

                        @Override
                        public void onSeekBarPressed(boolean pressed) {

                        }
                    });

                    long limitExtremum = 100 * 1024 * 1024;
                    float limitExtremumF = 0.7f;
                    float limitExtremumK = 1f - limitExtremumF;
                    long mimValue = 1024 * 512;
                    float currentProgress;
                    if (currentValue > limitExtremum * limitExtremumF) {
                        float p = (currentValue - limitExtremum) / (float) (maxValue - limitExtremum);
                        currentProgress = limitExtremumF + limitExtremumK * p;
                    } else {
                        float p = (currentValue - mimValue) / (float) (limitExtremum - mimValue);
                        currentProgress = limitExtremumF * p;
                    }
                    slideChooseView.setProgress(currentProgress);
                    slideChooseView.delegate.onSeekBarDrag(false, slideChooseView.getProgress());

                    view = linearLayout;
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (items.get(position).viewType == VIEW_TYPE_ADD_EXCEPTION) {
                TextCell cell = (TextCell) holder.itemView;
                cell.setNeedDivider(exceptionsDialogs.size() > 0);
            } else if (items.get(position).viewType == VIEW_TYPE_TOGGLE) {
                TextCheckCell cell = (TextCheckCell) holder.itemView;
                SaveToGallerySettingsHelper.Settings settings = getSettings();
                if (position == savePhotosRow) {
                    cell.setTextAndCheck(LocaleController.getString(R.string.SaveToGalleryPhotos), settings.savePhoto, true);
                    cell.setColorfullIcon(getThemedColor(Theme.key_statisticChartLine_lightblue), R.drawable.msg_filled_data_photos);
                } else {
                    cell.setTextAndCheck(LocaleController.getString(R.string.SaveToGalleryVideos), settings.saveVideo, false);
                    cell.setColorfullIcon(getThemedColor(Theme.key_statisticChartLine_green), R.drawable.msg_filled_data_videos);
                }

            } else if (items.get(position).viewType == VIEW_TYPE_DIVIDER_INFO) {
                TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                if (position == videoDividerRow) {
                    long limit = getSettings().limitVideo;
                    if (limit == -1) {
                        limit = 4L * 1000 * 1024 * 1024;
                    }
                    if (dialogException != null) {
                        cell.setText(LocaleController.formatString("SaveToGalleryVideoHintCurrent", R.string.SaveToGalleryVideoHintCurrent));
                    } else if (type == SAVE_TO_GALLERY_FLAG_PEER) {
                        cell.setText(LocaleController.formatString("SaveToGalleryVideoHintUser", R.string.SaveToGalleryVideoHintUser));
                    } else if (type == SAVE_TO_GALLERY_FLAG_CHANNELS) {
                        cell.setText(LocaleController.formatString("SaveToGalleryVideoHintChannels", R.string.SaveToGalleryVideoHintChannels));
                    } else if (type == SAVE_TO_GALLERY_FLAG_GROUP) {
                        cell.setText(LocaleController.formatString("SaveToGalleryVideoHintGroup", R.string.SaveToGalleryVideoHintGroup));
                    }
                } else {
                    cell.setText(items.get(position).title);
                }
            } else if (items.get(position).viewType == VIEW_TYPE_HEADER) {
                HeaderCell cell = (HeaderCell) holder.itemView;
                cell.setText(items.get(position).title);
            } else if (items.get(position).viewType == VIEW_TYPE_CHAT) {
                UserCell cell = (UserCell) holder.itemView;
                SaveToGallerySettingsHelper.DialogException exception = items.get(position).exception;
                TLObject object = getMessagesController().getUserOrChat(exception.dialogId);
                String title = null;
                if (object instanceof TLRPC.User) {
                    TLRPC.User user = (TLRPC.User) object;
                    if (user.self) {
                        title = LocaleController.getString(R.string.SavedMessages);
                    } else {
                        title = ContactsController.formatName(user.first_name, user.last_name);
                    }
                } else if (object instanceof TLRPC.Chat) {
                    TLRPC.Chat chat = (TLRPC.Chat) object;
                    title = chat.title;
                }
                cell.setSelfAsSavedMessages(true);
                cell.setData(object, title, exception.createDescription(currentAccount), 0, !(position != items.size() - 1 && items.get(position + 1).viewType != VIEW_TYPE_CHAT));
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position).viewType;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == VIEW_TYPE_ADD_EXCEPTION || holder.getItemViewType() == VIEW_TYPE_CHAT
                    || holder.getItemViewType() == VIEW_TYPE_DELETE_ALL || holder.getItemViewType() == VIEW_TYPE_TOGGLE;
        }
    }

    private class Item extends AdapterWithDiffUtils.Item {
        final SaveToGallerySettingsHelper.DialogException exception;
        String title;


        private Item(int viewType) {
            super(viewType, false);
            exception = null;
        }

        private Item(int viewType, SaveToGallerySettingsHelper.DialogException exception) {
            super(viewType, false);
            this.exception = exception;
        }

        private Item(int viewType, String title) {
            super(viewType, false);
            this.title = title;
            exception = null;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Item item = (Item) o;
            if (viewType != item.viewType) {
                return false;
            }
            if (title != null) {
                return Objects.equals(title, item.title);
            }
            if (exception != null && item.exception != null) {
                return exception.dialogId == item.exception.dialogId;
            }
            return true;
        }
    }

    SaveToGallerySettingsHelper.Settings getSettings() {
        if (dialogException != null) {
            return dialogException;
        }
        return SaveToGallerySettingsHelper.getSettings(type);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateRows();
    }

    private void onSettingsUpdated() {
        if (isNewException) {
            return;
        }
        if (dialogException != null) {
            LongSparseArray<SaveToGallerySettingsHelper.DialogException> allExceptions = getUserConfig().getSaveGalleryExceptions(type);
            allExceptions.put(dialogException.dialogId, dialogException);
            getUserConfig().updateSaveGalleryExceptions(type, allExceptions);
            return;
        } else {
            SaveToGallerySettingsHelper.saveSettings(type);
        }
    }

    private class SelectableAnimatedTextView extends AnimatedTextView {

        boolean selected;
        AnimatedFloat progressToSelect = new AnimatedFloat(this);

        public SelectableAnimatedTextView(Context context) {
            super(context, true, true, false);
            getDrawable().setAllowCancel(true);
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            progressToSelect.set(selected ? 1f : 0);
            setTextColor(ColorUtils.blendARGB(getThemedColor(Theme.key_windowBackgroundWhiteGrayText), getThemedColor(Theme.key_windowBackgroundWhiteBlueText), progressToSelect.get()));
            super.dispatchDraw(canvas);
        }

        public void setSelectedInternal(boolean selected, boolean animated) {
            if (this.selected != selected) {
                this.selected = selected;
                progressToSelect.set(selected ? 1f : 0, animated);
                invalidate();
            }
        }
    }
}
