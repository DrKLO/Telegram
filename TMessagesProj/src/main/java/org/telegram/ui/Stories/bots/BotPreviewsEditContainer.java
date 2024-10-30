package org.telegram.ui.Stories.bots;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.LocaleController.formatPluralString;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Build;
import android.text.Layout;
import android.text.SpannableString;
import android.text.SpannedString;
import android.text.TextUtils;
import android.text.style.ImageSpan;
import android.util.Log;
import android.util.LongSparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.TranslateController;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_bots;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.SharedAudioCell;
import org.telegram.ui.Cells.SharedDocumentCell;
import org.telegram.ui.Cells.SharedPhotoVideoCell;
import org.telegram.ui.Cells.SharedPhotoVideoCell2;
import org.telegram.ui.Components.BottomSheetWithRecyclerListView;
import org.telegram.ui.Components.ChatAttachAlert;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.ExtendedGridLayoutManager;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerAnimationScrollHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SharedMediaLayout;
import org.telegram.ui.Components.Size;
import org.telegram.ui.Components.StickerEmptyView;
import org.telegram.ui.Components.TranslateAlert2;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.ViewPagerFixed;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.Stars.StarsIntroActivity;
import org.telegram.ui.Stories.StoriesController;
import org.telegram.ui.Stories.StoriesListPlaceProvider;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;
import org.telegram.ui.Stories.recorder.StoryEntry;
import org.telegram.ui.Stories.recorder.StoryRecorder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

public class BotPreviewsEditContainer extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    private final BaseFragment fragment;
    private final int currentAccount;
    private final Theme.ResourcesProvider resourcesProvider;
    private final long bot_id;

    private static LongSparseArray<LongSparseArray<BotPreviewsEditContainer>> attachedContainers;
    private static LongSparseArray<LongSparseArray<StoriesController.BotPreviewsList>> cachedLists;
    private final StoriesController.BotPreviewsList mainList;
    private final ArrayList<StoriesController.BotPreviewsList> langLists = new ArrayList<>();
    private final ArrayList<String> localLangs = new ArrayList<>();

    private final ViewPagerFixed viewPager;
    private final ViewPagerFixed.TabsView tabsView;
    private Boolean shownTabs = null;

    public static void push(int currentAccount, long did, String lang_code, TL_bots.botPreviewMedia media) {
        if (cachedLists != null) {
            LongSparseArray<StoriesController.BotPreviewsList> arr = cachedLists.get(currentAccount);
            if (arr != null) {
                StoriesController.BotPreviewsList list = arr.get(did);
                if (list.currentAccount == currentAccount) {
                    if (TextUtils.equals(list.lang_code, lang_code)) {
                        list.push(media);
                    } else if (!TextUtils.isEmpty(lang_code) && !list.lang_codes.contains(lang_code)) {
                        list.lang_codes.add(lang_code);
                        list.notifyUpdate();
                    }
                }
            }
        }
        if (attachedContainers != null) {
            LongSparseArray<BotPreviewsEditContainer> arr = attachedContainers.get(currentAccount);
            if (arr != null) {
                BotPreviewsEditContainer container = arr.get(did);
                if (container != null) {
                    for (int i = 0; i < container.langLists.size(); ++i) {
                        StoriesController.BotPreviewsList list = container.langLists.get(i);
                        if (list.currentAccount == currentAccount && TextUtils.equals(list.lang_code, lang_code)) {
                            list.push(media);
                        }
                    }
                }
            }
        }
    }
    public static void edit(int currentAccount, long did, String lang_code, TLRPC.InputMedia old_media, TL_bots.botPreviewMedia media) {
        if (cachedLists != null) {
            LongSparseArray<StoriesController.BotPreviewsList> arr = cachedLists.get(currentAccount);
            if (arr != null) {
                StoriesController.BotPreviewsList list = arr.get(did);
                if (list.currentAccount == currentAccount) {
                    if (TextUtils.equals(list.lang_code, lang_code)) {
                        list.edit(old_media, media);
                    } else if (!TextUtils.isEmpty(lang_code) && !list.lang_codes.contains(lang_code)) {
                        list.lang_codes.add(lang_code);
                        list.notifyUpdate();
                    }
                }
            }
        }
        if (attachedContainers != null) {
            LongSparseArray<BotPreviewsEditContainer> arr = attachedContainers.get(currentAccount);
            if (arr != null) {
                BotPreviewsEditContainer container = arr.get(did);
                if (container != null) {
                    for (int i = 0; i < container.langLists.size(); ++i) {
                        StoriesController.BotPreviewsList list = container.langLists.get(i);
                        if (list.currentAccount == currentAccount && TextUtils.equals(list.lang_code, lang_code)) {
                            list.edit(old_media, media);
                        }
                    }
                }
            }
        }
    }

    public BotPreviewsEditContainer(
        Context context,
        BaseFragment fragment,
        long bot_id
    ) {
        super(context);

        this.fragment = fragment;
        this.currentAccount = fragment.getCurrentAccount();
        this.resourcesProvider = fragment.getResourceProvider();
        this.bot_id = bot_id;

        setBackgroundColor(Theme.blendOver(
            Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider),
            Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), 0.04f)
        ));

        if (cachedLists == null) {
            cachedLists = new LongSparseArray<>();
        }
        LongSparseArray<StoriesController.BotPreviewsList> arr = cachedLists.get(currentAccount);
        if (arr == null) cachedLists.put(currentAccount, arr = new LongSparseArray<>());
        StoriesController.BotPreviewsList list = arr.get(bot_id);
        if (list == null) {
            arr.put(bot_id, list = new StoriesController.BotPreviewsList(currentAccount, bot_id, "", null));
        }
        mainList = list;

        viewPager = new ViewPagerFixed(context) {
            @Override
            protected boolean canScroll(MotionEvent e) {
                if (isActionModeShowed()) return false;
                return super.canScroll(e);
            }
            private String lastLang;
            @Override
            protected void onTabAnimationUpdate(boolean manual) {
                String lang = getCurrentLang();
                if (!TextUtils.equals(lastLang, lang)) {
                    lastLang = lang;
                    onSelectedTabChanged();
                }
            }
            @Override
            protected void onTabPageSelected(int position) {
                String lang = getCurrentLang();
                if (!TextUtils.equals(lastLang, lang)) {
                    lastLang = lang;
                    onSelectedTabChanged();
                }
            }
            @Override
            protected void onTabScrollEnd(int position) {
                super.onTabScrollEnd(position);
                String lang = getCurrentLang();
                if (!TextUtils.equals(lastLang, lang)) {
                    lastLang = lang;
                    onSelectedTabChanged();
                }
            }
        };
        viewPager.setAllowDisallowInterceptTouch(true);
        viewPager.setAdapter(new ViewPagerFixed.Adapter() {
            @Override
            public int getItemCount() {
                return 1 + langLists.size();
            }

            @Override
            public View createView(int viewType) {
                return new BotPreviewsEditLangContainer(context);
            }
            @Override
            public int getItemId(int position) {
                if (position == 0) return 0;
                return langLists.get(position - 1).lang_code.hashCode();
            }
            @Override
            public void bindView(View view, int position, int viewType) {
                BotPreviewsEditLangContainer container = (BotPreviewsEditLangContainer) view;
                StoriesController.BotPreviewsList list = position == 0 ? mainList : langLists.get(position - 1);
                list.load(true, 0, null);
                container.setList(list);
                container.setVisibleHeight(visibleHeight);
            }
            @Override
            public String getItemTitle(int position) {
                if (position == 0) {
                    return getString(R.string.ProfileBotLanguageGeneral);
                }
                return TranslateAlert2.languageNameCapital(langLists.get(position - 1).lang_code);
            }
        });
        addView(viewPager, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        tabsView = viewPager.createTabsView(true, 9);
        tabsView.tabMarginDp = 12;
        tabsView.setPreTabClick((id, pos) -> {
            if (id == -1) {
                addTranslation();
                return true;
            }
            return false;
        });
        addView(tabsView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 42, Gravity.TOP));

        updateLangs(false);
    }

    public void addTranslation() {
        ChooseLanguageSheet sheet = new ChooseLanguageSheet(fragment, getString(R.string.ProfileBotPreviewLanguageChoose), lng -> {
            if (!localLangs.contains(lng)) {
                localLangs.add(lng);
                updateLangs(true);
            }
            AndroidUtilities.runOnUIThread(() -> {
                int index = -1;
                for (int i = 0; i < langLists.size(); ++i) {
                    if (TextUtils.equals(langLists.get(i).lang_code, lng)) {
                        index = i;
                        break;
                    }
                }
                if (index >= 0) {
                    tabsView.scrollToTab(lng.hashCode(), 1 + index);
                }
            }, 120);
        });
//        sheet.makeAttached(fragment);
        sheet.show();
    }

    public void onSelectedTabChanged() {}

    public RecyclerListView getCurrentListView() {
        View view = viewPager.getCurrentView();
        if (view instanceof BotPreviewsEditLangContainer) {
            return ((BotPreviewsEditLangContainer) view).listView;
        }
        return null;
    }

    public String getCurrentLang() {
        View[] views = viewPager.getViewPages();
        View view = Math.abs(viewPager.getCurrentPosition() - viewPager.getPositionAnimated()) < .5f && views[1] != null ? views[1] : views[0];
        if (view instanceof BotPreviewsEditLangContainer && ((BotPreviewsEditLangContainer) view).list != null) {
            return ((BotPreviewsEditLangContainer) view).list.lang_code;
        }
        return null;
    }

    public StoriesController.BotPreviewsList getCurrentList() {
        View view = viewPager.getCurrentView();
        if (view instanceof BotPreviewsEditLangContainer && ((BotPreviewsEditLangContainer) view).list != null) {
            return ((BotPreviewsEditLangContainer) view).list;
        }
        return null;
    }

    public int getItemsCount() {
        View view = viewPager.getCurrentView();
        if (view instanceof BotPreviewsEditLangContainer && ((BotPreviewsEditLangContainer) view).list != null) {
            return ((BotPreviewsEditLangContainer) view).list.getCount();
        }
        return 0;
    }

    public boolean canScroll(boolean forward) {
        if (forward)
            return viewPager.getCurrentPosition() == (1 + langLists.size()) - 1;
        return viewPager.getCurrentPosition() == 0;
    }

    public boolean isSelectedAll() {
        View view = viewPager.getCurrentView();
        if (view instanceof BotPreviewsEditLangContainer) {
            StoriesController.BotPreviewsList list = ((BotPreviewsEditLangContainer) view).list;
            if (list != null) {
                for (int i = 0; i < list.messageObjects.size(); ++i) {
                    if (!isSelected(list.messageObjects.get(i))) {
                        return false;
                    }
                }
                return true;
            }
        }
        return true;
    }

    public void selectAll() {
        View view = viewPager.getCurrentView();
        if (view instanceof BotPreviewsEditLangContainer) {
            StoriesController.BotPreviewsList list = ((BotPreviewsEditLangContainer) view).list;
            if (list != null) {
                for (int i = 0; i < list.messageObjects.size(); ++i) {
                    if (!isSelected(list.messageObjects.get(i))) {
                        select(list.messageObjects.get(i));
                    }
                }
            }
        }
    }

    public void unselectAll() {
        View view = viewPager.getCurrentView();
        if (view instanceof BotPreviewsEditLangContainer) {
            StoriesController.BotPreviewsList list = ((BotPreviewsEditLangContainer) view).list;
            if (list != null) {
                for (int i = 0; i < list.messageObjects.size(); ++i) {
                    if (isSelected(list.messageObjects.get(i))) {
                        unselect(list.messageObjects.get(i));
                    }
                }
            }
        }
    }

    public boolean checkPinchToZoom(MotionEvent ev) {
        View view = viewPager.getCurrentView();
        if (view instanceof BotPreviewsEditLangContainer) {
            return ((BotPreviewsEditLangContainer) view).checkPinchToZoom(ev);
        }
        return false;
    }

    private int visibleHeight = AndroidUtilities.displaySize.y;
    public void setVisibleHeight(int height) {
        visibleHeight = height;
        View[] views = viewPager.getViewPages();
        if (views != null) {
            for (int i = 0; i < views.length; ++i) {
                if (views[i] instanceof BotPreviewsEditLangContainer) {
                    ((BotPreviewsEditLangContainer) views[i]).setVisibleHeight(height);
                }
            }
        }
    }

    public String getBotPreviewsSubtitle() {
        StringBuilder sb = new StringBuilder();
        View view = viewPager.getCurrentView();
        if (view instanceof BotPreviewsEditLangContainer) {
            StoriesController.BotPreviewsList list = ((BotPreviewsEditLangContainer) view).list;
            int images = 0, videos = 0;
            if (list != null) {
                for (int i = 0; i < list.messageObjects.size(); ++i) {
                    MessageObject msg = list.messageObjects.get(i);
                    if (msg.storyItem != null && msg.storyItem.media != null) {
                        if (MessageObject.isVideoDocument(msg.storyItem.media.document)) {
                            videos++;
                        } else if (msg.storyItem.media.photo != null) {
                            images++;
                        }
                    }
                }
            }
            if (images == 0 && videos == 0) return getString(R.string.BotPreviewEmpty);
            if (images > 0) sb.append(formatPluralString("Images", images));
            if (videos > 0) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(formatPluralString("Videos", videos));
            }
        }
        return sb.toString();
    }

    public int getStartedTrackingX() {
        return 0;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.storiesListUpdated) {
            if (args[0] == mainList) {
                updateLangs(true);
                View[] views = viewPager.getViewPages();
                for (View view : views) {
                    if (view instanceof BotPreviewsEditLangContainer && ((BotPreviewsEditLangContainer) view).list == mainList) {
                        ((BotPreviewsEditLangContainer) view).adapter.notifyDataSetChanged();
                    }
                }
            } else if (langLists.indexOf(args[0]) >= 0) {
                View[] views = viewPager.getViewPages();
                for (View view : views) {
                    if (view instanceof BotPreviewsEditLangContainer && ((BotPreviewsEditLangContainer) view).list == args[0]) {
                        ((BotPreviewsEditLangContainer) view).adapter.notifyDataSetChanged();
                    }
                }
            }
        } else if (id == NotificationCenter.storiesUpdated) {
            updateLangs(true);
            View[] views = viewPager.getViewPages();
            for (View view : views) {
                if (view instanceof BotPreviewsEditLangContainer && view instanceof BotPreviewsEditLangContainer) {
                    ((BotPreviewsEditLangContainer) view).adapter.notifyDataSetChanged();
                }
            }
        }
    }

    private void updateLangs(boolean animated) {
        ArrayList<String> langs = new ArrayList<>(mainList.lang_codes);
        for (String l : localLangs) {
            if (!langs.contains(l)) {
                langs.add(l);
            }
        }
        ArrayList<StoriesController.UploadingStory> uploadingStories = MessagesController.getInstance(currentAccount).getStoriesController().getUploadingStories(bot_id);
        if (uploadingStories != null) {
            for (StoriesController.UploadingStory story : uploadingStories) {
                if (story != null && story.entry != null && story.entry.botId == bot_id && !TextUtils.isEmpty(story.entry.botLang) && !langs.contains(story.entry.botLang)) {
                    langs.add(story.entry.botLang);
                }
            }
        }

        ArrayList<StoriesController.BotPreviewsList> oldLangLists = new ArrayList<>(langLists);
        langLists.clear();
        for (String lang_code : langs) {
            StoriesController.BotPreviewsList list = null;
            for (int i = 0; i < oldLangLists.size(); ++i) {
                if (TextUtils.equals(oldLangLists.get(i).lang_code, lang_code)) {
                    list = oldLangLists.get(i);
                    break;
                }
            }
            if (list == null) {
                list = new StoriesController.BotPreviewsList(currentAccount, bot_id, lang_code, null);
                list.load(true, 0, null);
            }
            langLists.add(list);
        }

        viewPager.fillTabs(true);
        SpannableString tab = new SpannableString("+ " + getString(R.string.ProfileBotLanguageAdd));
        ColoredImageSpan span = new ColoredImageSpan(R.drawable.msg_filled_plus);
        span.setScale(.9f, .9f);
        span.spaceScaleX = .85f;
        tab.setSpan(span, 0, 1, SpannedString.SPAN_EXCLUSIVE_EXCLUSIVE);
        tabsView.addTab(-1, tab);
        tabsView.finishAddingTabs();

        final boolean showTabs = (1 + langLists.size()) > 1;
        updateTabs(showTabs, animated);
    }

    public void deleteLang(String lang) {
        if (TextUtils.isEmpty(lang)) return;
        mainList.lang_codes.remove(lang);
        localLangs.remove(lang);
        StoriesController.BotPreviewsList list = null;
        for (int i = 0; i < langLists.size(); ++i) {
            StoriesController.BotPreviewsList l = langLists.get(i);
            if (l != null && TextUtils.equals(l.lang_code, lang)) {
                list = l;
                break;
            }
        }
        if (list != null) {
            TL_bots.deletePreviewMedia req = new TL_bots.deletePreviewMedia();
            req.bot = MessagesController.getInstance(currentAccount).getInputUser(bot_id);
            req.lang_code = lang;
            for (int i = 0; i < list.messageObjects.size(); ++i) {
                MessageObject msg = list.messageObjects.get(i);
                if (msg.storyItem != null && msg.storyItem.media != null) {
                    req.media.add(MessagesController.toInputMedia(msg.storyItem.media));
                }
            }
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, null);
        }
        updateLangs(true);
        tabsView.scrollToTab(-1, 0);
    }

    private float tabsAlpha;
    private ValueAnimator tabsAnimator;
    private void updateTabs(boolean show, boolean animated) {
        if (shownTabs != null && shownTabs == show) {
            return;
        }
        if (tabsAnimator != null) {
            tabsAnimator.cancel();
        }
        shownTabs = show;
        if (!animated) {
            tabsAlpha = show ? 1f : 0f;
            tabsView.setTranslationY(dp(show ? 0 : -42));
            viewPager.setTranslationY(dp(show ? 42 : 0));
        } else {
            tabsAnimator = ValueAnimator.ofFloat(tabsAlpha, show ? 1f : 0f);
            tabsAnimator.addUpdateListener(anm -> {
                tabsAlpha = (float) anm.getAnimatedValue();
                tabsView.setTranslationY(lerp(-dp(42), 0, tabsAlpha));
                viewPager.setTranslationY(lerp(0, dp(42), tabsAlpha));
            });
            tabsAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    tabsAlpha = show ? 1f : 0f;
                    tabsView.setTranslationY(dp(show ? 0 : -42));
                    viewPager.setTranslationY(dp(show ? 42 : 0));
                }
            });
            tabsAnimator.setDuration(320);
            tabsAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            tabsAnimator.start();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (attachedContainers == null) attachedContainers = new LongSparseArray<>();
        LongSparseArray<BotPreviewsEditContainer> arr = attachedContainers.get(currentAccount);
        if (arr == null) attachedContainers.put(currentAccount, arr = new LongSparseArray<>());
        arr.put(bot_id, this);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.storiesListUpdated);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.storiesUpdated);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (attachedContainers == null) attachedContainers = new LongSparseArray<>();
        LongSparseArray<BotPreviewsEditContainer> arr = attachedContainers.get(currentAccount);
        if (arr != null) arr.remove(bot_id);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.storiesListUpdated);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.storiesUpdated);
    }

    protected boolean isActionModeShowed() {
        return false;
    }

    protected boolean isSelected(MessageObject messageObject) { return false; }
    protected boolean select(MessageObject messageObject) { return false; }
    protected boolean unselect(MessageObject messageObject) { return false; }

    public void updateSelection(boolean animated) {
        View view = viewPager.getCurrentView();
        if (view instanceof BotPreviewsEditLangContainer) {
            ((BotPreviewsEditLangContainer) view).updateSelection(animated);
        }
    }

    public void createStory(final String lang_code) {
        if (fragment == null || fragment.getParentActivity() == null) return;

        ChatAttachAlert chatAttachAlert = new ChatAttachAlert(fragment.getParentActivity(), fragment, false, false, false, resourcesProvider);
        chatAttachAlert.setMaxSelectedPhotos(1, false);
        chatAttachAlert.setStoryMediaPicker();
        chatAttachAlert.getPhotoLayout().loadGalleryPhotos();
        if (Build.VERSION.SDK_INT == 21 || Build.VERSION.SDK_INT == 22) {
            AndroidUtilities.hideKeyboard(fragment.getFragmentView().findFocus());
        }
        chatAttachAlert.setDelegate(new ChatAttachAlert.ChatAttachViewDelegate() {
            @Override
            public void didPressedButton(int button, boolean arg, boolean notify, int scheduleDate, long effectId, boolean invertMedia, boolean forceDocument) {
                if (!chatAttachAlert.getPhotoLayout().getSelectedPhotos().isEmpty()) {
                    HashMap<Object, Object> selectedPhotos = chatAttachAlert.getPhotoLayout().getSelectedPhotos();
                    ArrayList<Object> selectedPhotosOrder = chatAttachAlert.getPhotoLayout().getSelectedPhotosOrder();

                    if (selectedPhotos.size() != 1) {
                        return;
                    }
                    Object value = selectedPhotos.values().iterator().next();
                    if (!(value instanceof MediaController.PhotoEntry)) {
                        return;
                    }

                    MediaController.PhotoEntry entry = (MediaController.PhotoEntry) value;
                    StoryEntry storyEntry = StoryEntry.fromPhotoEntry(entry);
                    storyEntry.botId = bot_id;
                    storyEntry.botLang = lang_code;
                    storyEntry.setupMatrix();
                    StoryRecorder.getInstance(fragment.getParentActivity(), currentAccount).openBotEntry(bot_id, lang_code, storyEntry, null);
                    AndroidUtilities.runOnUIThread(chatAttachAlert::hide, 400);
                }
            }
            @Override
            public boolean selectItemOnClicking() {
                return true;
            }
        });
        chatAttachAlert.init();
        chatAttachAlert.show();
    }

    private int setColumnsCount = Utilities.clamp(SharedConfig.storiesColumnsCount, 6, 2);
    public class BotPreviewsEditLangContainer extends FrameLayout {

        private StoriesController.BotPreviewsList list;

        private boolean columnsAnimation;
        private float columnsAnimationProgress;

        private int columnsCount = Utilities.clamp(SharedConfig.storiesColumnsCount, 6, 2);
        private int animateToColumnsCount = Utilities.clamp(SharedConfig.storiesColumnsCount, 6, 2);

        private final SharedMediaLayout.SharedMediaListView listView;
        private final ExtendedGridLayoutManager layoutManager;
        private final DefaultItemAnimator itemAnimator;

        private final SharedMediaLayout.InternalListView supportingListView;
        private final GridLayoutManager supportingLayoutManager;

        private final StoriesAdapter adapter;
        private final StoriesAdapter supportingAdapter;

        private final FlickerLoadingView progressView;
        private final StickerEmptyView emptyView;
        private final TextView emptyViewOr;
        private final ButtonWithCounterView emptyViewButton2;

        private final RecyclerAnimationScrollHelper scrollHelper;
        private ItemTouchHelper reorder;

        private boolean allowStoriesSingleColumn = false;
        private boolean storiesColumnsCountSet = false;

        private final FooterView footer;

        public void setList(StoriesController.BotPreviewsList list) {
            if (this.list != list) {
                allowStoriesSingleColumn = false;
                storiesColumnsCountSet = false;
                columnsCount = setColumnsCount;
            }
            this.list = list;
            adapter.setList(list);
            supportingAdapter.setList(list);
            updateFooter();
        }

        private void updateFooter() {
            final int count = list == null ? 0 : list.getCount();
            final boolean isGeneral = list == null || TextUtils.isEmpty(list.lang_code);
            footer.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
            footer.set(
                isGeneral ?
                    LocaleController.getString(R.string.ProfileBotPreviewFooterGeneral) :
                    LocaleController.formatString(R.string.ProfileBotPreviewFooterLanguage, TranslateAlert2.languageName(list.lang_code)),
                LocaleController.getString(R.string.ProfileBotAddPreview), () -> createStory(list == null ? "" : list.lang_code),
                !isGeneral && count > 0 ? null : LocaleController.getString(isGeneral ?
                        R.string.ProfileBotPreviewFooterCreateTranslation :
                        R.string.ProfileBotPreviewFooterDeleteTranslation
                ),
                !isGeneral && count > 0 ? null : () -> {
                    if (isGeneral) {
                        addTranslation();
                    } else {
                        deleteLang(list.lang_code);
                    }
                }
            );

            if (isGeneral) {
                this.emptyView.title.setVisibility(View.VISIBLE);
                this.emptyView.title.setText(getString(R.string.ProfileBotPreviewEmptyTitle));
                this.emptyView.subtitle.setText(formatPluralString("ProfileBotPreviewEmptyText", MessagesController.getInstance(currentAccount).botPreviewMediasMax));
                this.emptyView.button.setText(getString(R.string.ProfileBotPreviewEmptyButton), false);
                this.emptyViewOr.setVisibility(View.GONE);
                this.emptyViewButton2.setVisibility(View.GONE);
            } else {
                this.emptyView.title.setVisibility(View.GONE);
                this.emptyView.subtitle.setText(LocaleController.formatString(R.string.ProfileBotPreviewFooterLanguage, TranslateAlert2.languageName(list.lang_code)));
                this.emptyView.button.setText(getString(R.string.ProfileBotPreviewEmptyButton), false);
                this.emptyViewOr.setVisibility(View.VISIBLE);
                this.emptyViewButton2.setVisibility(View.VISIBLE);
                this.emptyViewButton2.setText(getString(R.string.ProfileBotPreviewFooterDeleteTranslation), false);
                this.emptyViewButton2.setOnClickListener(v -> deleteLang(list.lang_code));
            }
            this.emptyView.button.setVisibility(adapter.getItemCount() >= MessagesController.getInstance(currentAccount).botPreviewMediasMax ? View.GONE : View.VISIBLE);
        }

        public void setVisibleHeight(int height) {
            float t = -(getMeasuredHeight() - Math.max(height, dp(280))) / 2f;
            emptyView.setTranslationY(t);
            progressView.setTranslationY(-t);
        }

        public BotPreviewsEditLangContainer(Context context) {
            super(context);

            this.layoutManager = new ExtendedGridLayoutManager(context, 100) {
                @Override
                public boolean supportsPredictiveItemAnimations() {
                    return false;
                }

                @Override
                protected void calculateExtraLayoutSpace(RecyclerView.State state, int[] extraLayoutSpace) {
                    super.calculateExtraLayoutSpace(state, extraLayoutSpace);
                    extraLayoutSpace[1] = Math.max(extraLayoutSpace[1], SharedPhotoVideoCell.getItemSize(1) * 2);
                }

                private final Size size = new Size();
                @Override
                protected Size getSizeForItem(int i) {
                    size.width = size.height = 100;
                    return size;
                }

                @Override
                protected int getFlowItemCount() {
                    return 0;
                }

                @Override
                public void onInitializeAccessibilityNodeInfoForItem(RecyclerView.Recycler recycler, RecyclerView.State state, View host, AccessibilityNodeInfoCompat info) {
                    super.onInitializeAccessibilityNodeInfoForItem(recycler, state, host, info);
                    final AccessibilityNodeInfoCompat.CollectionItemInfoCompat itemInfo = info.getCollectionItemInfo();
                    if (itemInfo != null && itemInfo.isHeading()) {
                        info.setCollectionItemInfo(AccessibilityNodeInfoCompat.CollectionItemInfoCompat.obtain(itemInfo.getRowIndex(), itemInfo.getRowSpan(), itemInfo.getColumnIndex(), itemInfo.getColumnSpan(), false));
                    }
                }

                @Override
                public void setSpanCount(int spanCount) {
                    super.setSpanCount(spanCount);
                }
            };
            layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                @Override
                public int getSpanSize(int position) {
                    if (adapter.getItemViewType(position) == 2) {
                        return columnsCount;
                    }
                    return 1;
                }
            });
            layoutManager.setSpanCount(columnsCount);
            this.itemAnimator = new DefaultItemAnimator();
            this.itemAnimator.setDurations(280);
            this.itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            this.itemAnimator.setSupportsChangeAnimations(false);

            this.listView = new SharedMediaLayout.SharedMediaListView(context) {
                @Override
                public boolean isStories() {
                    return true;
                }
                @Override
                public int getColumnsCount() {
                    return columnsCount;
                }
                @Override
                public int getAnimateToColumnsCount() {
                    return animateToColumnsCount;
                }
                @Override
                public boolean isChangeColumnsAnimation() {
                    return columnsAnimation;
                }
                @Override
                public float getChangeColumnsProgress() {
                    return columnsAnimationProgress;
                }
                @Override
                public SharedMediaLayout.InternalListView getSupportingListView() {
                    return supportingListView;
                }
                @Override
                public RecyclerListView.FastScrollAdapter getMovingAdapter() {
                    if (!reorder.isIdle() || isActionModeShowed()) return null;
                    return adapter;
                }
                @Override
                public RecyclerListView.FastScrollAdapter getSupportingAdapter() {
                    return supportingAdapter;
                }
                @Override
                protected void dispatchDraw(Canvas canvas) {
                    super.dispatchDraw(canvas);
                    float bottom = getListBottom(this);
                    if (columnsAnimation) {
                        bottom = lerp(bottom, getListBottom(supportingListView), columnsAnimationProgress);
                    }
                    footer.setVisibility(adapter.getItemCount() > 0 ? View.VISIBLE : View.GONE);
                    footer.setTranslationY(bottom);
                }
                private int getListBottom(ViewGroup listView) {
                    int bottom = 0;
                    for (int i = 0; i < listView.getChildCount(); ++i) {
                        View child = listView.getChildAt(i);
                        int childBottom = child.getBottom() - listView.getPaddingTop();
                        if (childBottom > bottom) {
                            bottom = childBottom;
                        }
                    }
                    return bottom;
                }
            };
            this.listView.setScrollingTouchSlop(RecyclerView.TOUCH_SLOP_PAGING);
            this.listView.setPinnedSectionOffsetY(-dp(2));
            this.listView.setPadding(0, 0, 0, 0);
            this.listView.setItemAnimator(null);
            this.listView.setClipToPadding(false);
            this.listView.setSectionsType(RecyclerListView.SECTIONS_TYPE_DATE);
            this.listView.setLayoutManager(layoutManager);
            addView(this.listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            this.listView.addItemDecoration(new RecyclerView.ItemDecoration() {
                @Override
                public void getItemOffsets(android.graphics.Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
                    if (view instanceof SharedPhotoVideoCell2) {
                        SharedPhotoVideoCell2 cell = (SharedPhotoVideoCell2) view;
                        final int position = listView.getChildAdapterPosition(cell), spanCount = layoutManager.getSpanCount();
                        cell.isFirst = position % spanCount == 0;
                        cell.isLast = position % spanCount == spanCount - 1;
                        outRect.left = 0;
                        outRect.top = 0;
                        outRect.bottom = 0;
                        outRect.right = 0;
                    } else {
                        outRect.left = 0;
                        outRect.top = 0;
                        outRect.bottom = 0;
                        outRect.right = 0;
                    }
                }
            });
            this.listView.setOnItemClickListener((view, position) -> {
                if (view instanceof SharedPhotoVideoCell2) {
                    SharedPhotoVideoCell2 cell = (SharedPhotoVideoCell2) view;
                    MessageObject obj = cell.getMessageObject();

                    if (isActionModeShowed()) {
                        if (BotPreviewsEditContainer.this.isSelected(obj)) {
                            BotPreviewsEditContainer.this.unselect(obj);
                        } else {
                            BotPreviewsEditContainer.this.select(obj);
                        }
                    } else {
                        fragment.getOrCreateStoryViewer().open(
                            getContext(),
                            obj.getId(),
                            list,
                            StoriesListPlaceProvider.of(listView)
                                .addBottomClip(fragment instanceof ProfileActivity && ((ProfileActivity) fragment).myProfile ? dp(68) : 0)
                        );
                    }
                }
            });
            this.listView.setOnItemLongClickListener((view, position) -> {
                if (isActionModeShowed()) return false;
                if (view instanceof SharedPhotoVideoCell2) {
                    SharedPhotoVideoCell2 cell = (SharedPhotoVideoCell2) view;
                    MessageObject obj = cell.getMessageObject();
                    if (BotPreviewsEditContainer.this.isSelected(obj)) {
                        BotPreviewsEditContainer.this.unselect(obj);
                    } else {
                        BotPreviewsEditContainer.this.select(obj);
                    }
                    return true;
                }
                return false;
            });

            this.supportingListView = new SharedMediaLayout.InternalListView(context);
            this.supportingListView.setLayoutManager(supportingLayoutManager = new GridLayoutManager(context, 3) {
                @Override
                public boolean supportsPredictiveItemAnimations() {
                    return false;
                }
                @Override
                public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler, RecyclerView.State state) {
                    if (columnsAnimation) {
                        dy = 0;
                    }
                    return super.scrollVerticallyBy(dy, recycler, state);
                }
            });
            this.supportingListView.addItemDecoration(new RecyclerView.ItemDecoration() {
                @Override
                public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                    if (view instanceof SharedPhotoVideoCell2) {
                        SharedPhotoVideoCell2 cell = (SharedPhotoVideoCell2) view;
                        final int position = supportingListView.getChildAdapterPosition(cell), spanCount = supportingLayoutManager.getSpanCount();
                        cell.isFirst = position % spanCount == 0;
                        cell.isLast = position % spanCount == spanCount - 1;
                        outRect.left = 0;
                        outRect.top = 0;
                        outRect.bottom = 0;
                        outRect.right = 0;
                    } else {
                        outRect.left = 0;
                        outRect.top = 0;
                        outRect.bottom = 0;
                        outRect.right = 0;
                    }
                }
            });
            supportingLayoutManager.setSpanCount(animateToColumnsCount);
            this.supportingListView.setVisibility(View.GONE);
            addView(this.supportingListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            this.adapter = new StoriesAdapter(context) {
                @Override
                public void notifyDataSetChanged() {
                    super.notifyDataSetChanged();
                    if (supportingListView.getVisibility() == View.VISIBLE) {
                        supportingAdapter.notifyDataSetChanged();
                    }
                    if (emptyView != null) {
                        emptyView.showProgress(storiesList != null && storiesList.isLoading());
                    }
                }
            };
            this.listView.setAdapter(adapter);
            this.supportingListView.setAdapter(this.supportingAdapter = adapter.makeSupporting());

            this.progressView = new FlickerLoadingView(context) {

                @Override
                public int getColumnsCount() {
                    return columnsCount;
                }

                @Override
                public int getViewType() {
                    setIsSingleCell(false);
                    return FlickerLoadingView.STORIES_TYPE;
                }

                private final Paint backgroundPaint = new Paint();
                @Override
                protected void onDraw(Canvas canvas) {
                    backgroundPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
                    canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), backgroundPaint);
                    super.onDraw(canvas);
                }
            };
            this.progressView.showDate(false);

            this.emptyView = new StickerEmptyView(context, this.progressView, StickerEmptyView.STICKER_TYPE_SEARCH);
            this.emptyView.setVisibility(View.GONE);
            this.emptyView.setAnimateLayoutChange(true);
            addView(this.emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            this.emptyView.setOnTouchListener((v, event) -> true);
            this.emptyView.showProgress(true, false);
            this.emptyView.stickerView.setVisibility(View.GONE);
            this.emptyView.title.setText(getString(R.string.ProfileBotPreviewEmptyTitle));
            this.emptyView.subtitle.setText(formatPluralString("ProfileBotPreviewEmptyText", MessagesController.getInstance(currentAccount).botPreviewMediasMax));
            this.emptyView.button.setText(getString(R.string.ProfileBotPreviewEmptyButton), false);
            this.emptyView.button.setVisibility(View.VISIBLE);
            this.emptyView.button.setOnClickListener(v -> {
                createStory(list == null ? "" : list.lang_code);
            });
            this.emptyViewOr = new TextView(context) {
                private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                @Override
                protected void dispatchDraw(Canvas canvas) {
                    final int cy = getHeight() / 2 + dp(1);
                    final int h = Math.max(1, dp(.66f));
                    Layout layout = getLayout();
                    if (layout != null) {
                        paint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider), .45f));
                        canvas.drawRect(0, cy - h / 2f, (getWidth() - (layout.getLineWidth(0) + dp(16))) / 2f, cy + h / 2f, paint);
                        canvas.drawRect((getWidth() + layout.getLineWidth(0) + dp(16)) / 2f, cy - h / 2f, getWidth(), cy + h / 2f, paint);
                    }
                    super.dispatchDraw(canvas);
                }
            };
            this.emptyViewOr.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
            this.emptyViewOr.setText(LocaleController.getString(R.string.ProfileBotOr));
            this.emptyViewOr.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            this.emptyViewOr.setTextAlignment(TEXT_ALIGNMENT_CENTER);
            this.emptyViewOr.setGravity(Gravity.CENTER);
            this.emptyViewOr.setTypeface(AndroidUtilities.bold());
            this.emptyView.linearLayout.addView(this.emptyViewOr, LayoutHelper.createLinear(165, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 17, 0, 12));
            this.emptyViewButton2 = new ButtonWithCounterView(context, false, resourcesProvider);
            this.emptyViewButton2.setMinWidth(dp(200));
            this.emptyView.linearLayout.addView(this.emptyViewButton2, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 44, Gravity.CENTER));

            this.emptyView.addView(this.progressView, 0,  LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            this.listView.setEmptyView(this.emptyView);
            this.listView.setAnimateEmptyView(true, RecyclerListView.EMPTY_VIEW_ANIMATION_TYPE_ALPHA);

            this.scrollHelper = new RecyclerAnimationScrollHelper(this.listView, this.layoutManager);
            this.reorder = new ItemTouchHelper(new ItemTouchHelper.Callback() {
                @Override
                public boolean isLongPressDragEnabled() {
                    return isActionModeShowed();
                }

                @Override
                public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                    if (isActionModeShowed() && adapter.canReorder(viewHolder.getAdapterPosition())) {
                        listView.setItemAnimator(itemAnimator);
                        return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN | ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT, 0);
                    } else {
                        return makeMovementFlags(0, 0);
                    }
                }

                @Override
                public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                    if (!adapter.canReorder(viewHolder.getAdapterPosition()) || !adapter.canReorder(target.getAdapterPosition())) {
                        return false;
                    }
                    adapter.swapElements(viewHolder.getAdapterPosition(), target.getAdapterPosition());
                    return true;
                }

                @Override
                public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {

                }

                @Override
                public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
                    if (viewHolder != null) {
                        listView.hideSelector(false);
                    }
                    if (actionState == ItemTouchHelper.ACTION_STATE_IDLE) {
                        adapter.reorderDone();
                        listView.setItemAnimator(null);
                    } else {
                        listView.cancelClickRunnables(false);
                        if (viewHolder != null) {
                            viewHolder.itemView.setPressed(true);
                        }
                    }
                    super.onSelectedChanged(viewHolder, actionState);
                }

                @Override
                public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                    super.clearView(recyclerView, viewHolder);
                    viewHolder.itemView.setPressed(false);
                }
            });
            this.reorder.attachToRecyclerView(listView);

            this.footer = new FooterView(context, resourcesProvider);
            addView(this.footer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            listView.setPadding(listView.getPaddingLeft(), listView.topPadding, listView.getPaddingRight(), dp(42) + footer.getMeasuredHeight());
        }

        public void updateSelection(boolean animated) {
            for (int i = 0; i < listView.getChildCount(); ++i) {
                View child = listView.getChildAt(i);
                if (child instanceof SharedPhotoVideoCell2) {
                    SharedPhotoVideoCell2 cell = (SharedPhotoVideoCell2) child;
                    cell.setChecked(BotPreviewsEditContainer.this.isSelected(cell.getMessageObject()), animated);
                }
            }
        }

        @Override
        protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
            if (child == supportingListView) return true;
            return super.drawChild(canvas, child, drawingTime);
        }

        public class StoriesAdapter extends RecyclerListView.FastScrollAdapter {

            private final Context context;
            private final ArrayList<StoriesController.UploadingStory> uploadingStories = new ArrayList<>();
            @Nullable
            public StoriesController.StoriesList storiesList;
            private StoriesAdapter supportingAdapter;

            public StoriesAdapter(Context context) {
                this.context = context;
                checkColumns();
            }

            public void setList(StoriesController.StoriesList list) {
                this.storiesList = list;
                if (this != BotPreviewsEditLangContainer.this.supportingAdapter) {
                    checkColumns();
                }
                notifyDataSetChanged();
            }

            public StoriesAdapter makeSupporting() {
                StoriesAdapter adapter = new StoriesAdapter(getContext());
                this.supportingAdapter = adapter;
                return adapter;
            }

            private void checkColumns() {
                if (storiesList == null) {
                    return;
                }
                if ((!storiesColumnsCountSet || allowStoriesSingleColumn && getItemCount() > 1) && getItemCount() > 0) {
                    if (getItemCount() < 5) {
                        columnsCount = Math.max(1, getItemCount());
                        allowStoriesSingleColumn = columnsCount == 1;
                    } else if (allowStoriesSingleColumn || columnsCount == 1) {
                        allowStoriesSingleColumn = false;
                        columnsCount = Math.max(2, SharedConfig.storiesColumnsCount);
                    }
                    layoutManager.setSpanCount(columnsCount);
                    storiesColumnsCountSet = true;
                }
            }

            @Override
            public void notifyDataSetChanged() {
                if (storiesList instanceof StoriesController.BotPreviewsList) {
                    StoriesController.BotPreviewsList botList = (StoriesController.BotPreviewsList) storiesList;
                    uploadingStories.clear();
                    ArrayList<StoriesController.UploadingStory> list = MessagesController.getInstance(storiesList.currentAccount).getStoriesController().getUploadingStories(bot_id);
                    if (list != null) {
                        for (int i = 0; i < list.size(); ++i) {
                            StoriesController.UploadingStory story = list.get(i);
                            if (story.entry != null && !story.entry.isEdit && TextUtils.equals(story.entry.botLang, botList.lang_code)) {
                                uploadingStories.add(story);
                            }
                        }
                    }
                }
                super.notifyDataSetChanged();
                if (supportingAdapter != null) {
                    supportingAdapter.notifyDataSetChanged();
                }
                if (this != BotPreviewsEditLangContainer.this.supportingAdapter) {
                    checkColumns();
                    updateFooter();
                }
            }

            @Override
            public int getItemCount() {
                if (storiesList == null) {
                    return 0;
                }
                return uploadingStories.size() + storiesList.getCount();
            }

            @Override
            public int getTotalItemsCount() {
                return getItemCount();
            }

            @Override
            public boolean isEnabled(RecyclerView.ViewHolder holder) {
                return false;
            }

            private SharedPhotoVideoCell2.SharedResources sharedResources;

            FlickerLoadingView globalGradientView;
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                if (sharedResources == null) {
                    sharedResources = new SharedPhotoVideoCell2.SharedResources(parent.getContext(), resourcesProvider);
                }
                SharedPhotoVideoCell2 cell = new SharedPhotoVideoCell2(context, sharedResources, currentAccount);
                cell.setCheck2();
                cell.setGradientView(globalGradientView);
                cell.isStory = true;
                return new RecyclerListView.Holder(cell);
            }


            private int columnsCount() {
                if (this == supportingAdapter) return animateToColumnsCount;
                return columnsCount;
            }

            @Override
            public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                if (storiesList == null) {
                    return;
                }
                int viewType = holder.getItemViewType();
                if (!(holder.itemView instanceof SharedPhotoVideoCell2)) return;
                SharedPhotoVideoCell2 cell = (SharedPhotoVideoCell2) holder.itemView;
                cell.isStory = true;
                if (position >= 0 && position < uploadingStories.size()) {
                    StoriesController.UploadingStory uploadingStory = uploadingStories.get(position);
                    cell.isStoryPinned = false;
                    if (uploadingStory.sharedMessageObject == null) {
                        final TL_stories.TL_storyItem storyItem = new TL_stories.TL_storyItem();
                        storyItem.id = storyItem.messageId = Long.hashCode(uploadingStory.random_id);
                        storyItem.attachPath = uploadingStory.firstFramePath;
                        uploadingStory.sharedMessageObject = new MessageObject(storiesList.currentAccount, storyItem) {
                            @Override
                            public float getProgress() {
                                return uploadingStory.progress;
                            }
                        };
                        uploadingStory.sharedMessageObject.uploadingStory = uploadingStory;
                    }
                    cell.setMessageObject(uploadingStory.sharedMessageObject, columnsCount());
                    cell.isStory = true;
                    cell.setReorder(false);
                    cell.setChecked(false, false);
                    return;
                }
                position -= uploadingStories.size();
                if (position < 0 || position >= storiesList.messageObjects.size()) {
                    cell.isStoryPinned = false;
                    cell.setMessageObject(null, columnsCount());
                    cell.isStory = true;
                    return;
                }
                MessageObject messageObject = storiesList.messageObjects.get(position);
                cell.isStoryPinned = messageObject != null && storiesList.isPinned(messageObject.getId());
                cell.setReorder(true);
                cell.setMessageObject(messageObject, columnsCount());
                if (isActionModeShowed() && messageObject != null) {
                    cell.setChecked(BotPreviewsEditContainer.this.isSelected(messageObject), true);
                } else {
                    cell.setChecked(false, false);
                }
            }

            public void load(boolean force) {
                if (storiesList == null) {
                    return;
                }

                final int columnCount = columnsCount();
                final int count = Math.min(100, Math.max(1, columnCount / 2) * columnCount * columnCount);
                storiesList.load(force, count);
            }

            @Override
            public int getItemViewType(int i) {
                return 19;
            }

            @Override
            public String getLetter(int position) {
                if (storiesList == null) {
                    return null;
                }
                if (position < 0 || position >= storiesList.messageObjects.size()) {
                    return null;
                }
                MessageObject messageObject = storiesList.messageObjects.get(position);
                if (messageObject == null || messageObject.storyItem == null) {
                    return null;
                }
                return LocaleController.formatYearMont(messageObject.storyItem.date, true);
            }

            @Override
            public void onFastScrollSingleTap() {

            }

            public boolean canReorder(int position) {
                if (storiesList == null) return false;
                if (storiesList instanceof StoriesController.BotPreviewsList) {
                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(bot_id);
                    return user != null && user.bot && user.bot_has_main_app && user.bot_can_edit;
                }
                if (position < 0 || position >= storiesList.messageObjects.size()) return false;
                MessageObject messageObject = storiesList.messageObjects.get(position);
                return storiesList.isPinned(messageObject.getId());
            }

            public ArrayList<Integer> lastPinnedIds = new ArrayList<>();
            public boolean applyingReorder;

            public boolean swapElements(int fromPosition, int toPosition) {
                if (storiesList == null) return false;
                if (fromPosition < 0 || fromPosition >= storiesList.messageObjects.size()) return false;
                if (toPosition < 0 || toPosition >= storiesList.messageObjects.size()) return false;

                ArrayList<Integer> pinnedIds;
                if (storiesList instanceof StoriesController.BotPreviewsList) {
                    pinnedIds = new ArrayList<>();
                    for (int i = 0; i < storiesList.messageObjects.size(); ++i) {
                        pinnedIds.add(storiesList.messageObjects.get(i).getId());
                    }
                } else {
                    pinnedIds = new ArrayList<>(storiesList.pinnedIds);
                }

                if (!applyingReorder) {
                    lastPinnedIds.clear();
                    lastPinnedIds.addAll(pinnedIds);
                    applyingReorder = true;
                }

                MessageObject from = storiesList.messageObjects.get(fromPosition);
                MessageObject to = storiesList.messageObjects.get(toPosition);

                pinnedIds.remove((Object) from.getId());
                pinnedIds.add(Utilities.clamp(toPosition, pinnedIds.size(), 0), from.getId());

                storiesList.updatePinnedOrder(pinnedIds, false);

                notifyItemMoved(fromPosition, toPosition);

                return true;
            }

            public void reorderDone() {
                if (storiesList == null) return;
                if (!applyingReorder) return;

                ArrayList<Integer> ids;
                if (storiesList instanceof StoriesController.BotPreviewsList) {
                    ids = new ArrayList<>();
                    for (int i = 0; i < storiesList.messageObjects.size(); ++i) {
                        ids.add(storiesList.messageObjects.get(i).getId());
                    }
                } else {
                    ids = storiesList.pinnedIds;
                }

                boolean changed = lastPinnedIds.size() != ids.size();
                if (!changed) {
                    for (int i = 0; i < lastPinnedIds.size(); ++i) {
                        if (lastPinnedIds.get(i) != ids.get(i)) {
                            changed = true;
                            break;
                        }
                    }
                }

                if (changed) {
                    storiesList.updatePinnedOrder(ids, true);
                }

                applyingReorder = false;
            }

            @Override
            public void getPositionForScrollProgress(RecyclerListView listView, float progress, int[] position) {
                int viewHeight = listView.getChildAt(0).getMeasuredHeight();
                int columnsCount = columnsCount();
                int totalHeight = (int) (Math.ceil(getTotalItemsCount() / (float) columnsCount) * viewHeight);
                int listHeight =  listView.getMeasuredHeight() - listView.getPaddingTop();
                if (viewHeight == 0) {
                    position[0] = position[1] = 0;
                    return;
                }
                position[0] = (int) ((progress * (totalHeight - listHeight)) / viewHeight) * columnsCount;
                position[1] = (int) (progress * (totalHeight - listHeight)) % viewHeight;
            }
        }

        boolean isInPinchToZoomTouchMode;
        boolean maybePinchToZoomTouchMode;
        boolean maybePinchToZoomTouchMode2;
        boolean isPinnedToTop;

        private int pointerId1, pointerId2;

        float pinchStartDistance;
        float pinchScale;
        boolean pinchScaleUp;
        int pinchCenterPosition;
        int pinchCenterOffset;
        int pinchCenterX;
        int pinchCenterY;
        Rect rect = new Rect();
//
//        private int startedTrackingPointerId;
//        private boolean startedTracking;
//        private boolean maybeStartTracking;
//        private int startedTrackingX;
//        private int startedTrackingY;
//        private VelocityTracker velocityTracker;

        public boolean checkPinchToZoom(MotionEvent ev) {
            if (list == null || getParent() == null) {
                return false;
            }
            if (columnsAnimation && !isInPinchToZoomTouchMode) {
                return true;
            }

            if (ev.getActionMasked() == MotionEvent.ACTION_DOWN || ev.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
                if (maybePinchToZoomTouchMode && !isInPinchToZoomTouchMode && ev.getPointerCount() == 2 /*&& finishZoomTransition == null*/) {
                    pinchStartDistance = (float) Math.hypot(ev.getX(1) - ev.getX(0), ev.getY(1) - ev.getY(0));

                    pinchScale = 1f;

                    pointerId1 = ev.getPointerId(0);
                    pointerId2 = ev.getPointerId(1);

                    listView.cancelClickRunnables(false);
                    listView.cancelLongPress();
                    listView.dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0, 0, 0));

                    View view = (View) getParent();
                    pinchCenterX = (int) ((int) ((ev.getX(0) + ev.getX(1)) / 2.0f) - view.getX() - getX());
                    pinchCenterY = (int) ((int) ((ev.getY(0) + ev.getY(1)) / 2.0f) - view.getY() - getY());

                    selectPinchPosition(pinchCenterX, pinchCenterY);
                    maybePinchToZoomTouchMode2 = true;
                }
                if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    View view = (View) getParent();
                    float y = ev.getY() - view.getY() - getY();
                    if (y > 0) {
                        maybePinchToZoomTouchMode = true;
                    }
                }

            } else if (ev.getActionMasked() == MotionEvent.ACTION_MOVE && (isInPinchToZoomTouchMode || maybePinchToZoomTouchMode2)) {
                int index1 = -1;
                int index2 = -1;
                for (int i = 0; i < ev.getPointerCount(); i++) {
                    if (pointerId1 == ev.getPointerId(i)) {
                        index1 = i;
                    }
                    if (pointerId2 == ev.getPointerId(i)) {
                        index2 = i;
                    }
                }
                if (index1 == -1 || index2 == -1) {
                    maybePinchToZoomTouchMode = false;
                    maybePinchToZoomTouchMode2 = false;
                    isInPinchToZoomTouchMode = false;
                    finishPinchToMediaColumnsCount();
                    return false;
                }
                pinchScale = (float) Math.hypot(ev.getX(index2) - ev.getX(index1), ev.getY(index2) - ev.getY(index1)) / pinchStartDistance;
                if (!isInPinchToZoomTouchMode && (pinchScale > 1.01f || pinchScale < 0.99f)) {
                    isInPinchToZoomTouchMode = true;
                    pinchScaleUp = pinchScale > 1f;

                    startPinchToMediaColumnsCount(pinchScaleUp);
                }
                if (isInPinchToZoomTouchMode) {
                    if ((pinchScaleUp && pinchScale < 1f) || (!pinchScaleUp && pinchScale > 1f)) {
                        columnsAnimationProgress = 0;
                    } else {
                        columnsAnimationProgress = Math.max(0, Math.min(1, pinchScaleUp ? (1f - (2f - pinchScale) / 1f) : ((1f - pinchScale) / 0.5f)));
                    }
                    if (columnsAnimationProgress == 1f || columnsAnimationProgress == 0f) {
                        if (columnsAnimationProgress == 1f) {
                            int newRow = (int) Math.ceil(pinchCenterPosition / (float) animateToColumnsCount);
                            int columnWidth = (int) (listView.getMeasuredWidth() / (float) animateToColumnsCount);
                            int newColumn = (int) ((getStartedTrackingX() / (float) (listView.getMeasuredWidth() - columnWidth)) * (animateToColumnsCount - 1));
                            int newPosition = newRow * animateToColumnsCount + newColumn;
                            if (newPosition >= adapter.getItemCount()) {
                                newPosition = adapter.getItemCount() - 1;
                            }
                            pinchCenterPosition = newPosition;
                        }

                        finishPinchToMediaColumnsCount();
                        if (columnsAnimationProgress == 0) {
                            pinchScaleUp = !pinchScaleUp;
                        }

                        startPinchToMediaColumnsCount(pinchScaleUp);
                        pinchStartDistance = (float) Math.hypot(ev.getX(1) - ev.getX(0), ev.getY(1) - ev.getY(0));
                    }

                    listView.invalidate();
//                    if (mediaPages[0].fastScrollHintView != null) {
//                        mediaPages[0].invalidate();
//                    }
                }
            } else if ((ev.getActionMasked() == MotionEvent.ACTION_UP || (ev.getActionMasked() == MotionEvent.ACTION_POINTER_UP && checkPointerIds(ev)) || ev.getActionMasked() == MotionEvent.ACTION_CANCEL) && isInPinchToZoomTouchMode) {
                maybePinchToZoomTouchMode2 = false;
                maybePinchToZoomTouchMode = false;
                isInPinchToZoomTouchMode = false;
                finishPinchToMediaColumnsCount();
            }

            return isInPinchToZoomTouchMode;
        }

        private void selectPinchPosition(int pinchCenterX, int pinchCenterY) {
            pinchCenterPosition = -1;
            int y = pinchCenterY + listView.blurTopPadding;
//            if (getY() != 0 && viewType == VIEW_TYPE_PROFILE_ACTIVITY) {
//                y = 0;
//            }
            for (int i = 0; i < listView.getChildCount(); i++) {
                View child = listView.getChildAt(i);
                child.getHitRect(rect);
                if (rect.contains(pinchCenterX, y)) {
                    pinchCenterPosition = listView.getChildLayoutPosition(child);
                    pinchCenterOffset = child.getTop();
                }
            }
//            if (delegate.canSearchMembers()) {
//                if (pinchCenterPosition == -1) {
//                    float x = Math.min(1, Math.max(pinchCenterX / (float) mediaPages[0].listView.getMeasuredWidth(), 0));
//                    final int ci = mediaPages[0].selectedType == TAB_STORIES || mediaPages[0].selectedType == TAB_ARCHIVED_STORIES ? 1 : 0;
//                    pinchCenterPosition = (int) (mediaPages[0].layoutManager.findFirstVisibleItemPosition() + (columnsCount - 1) * x);
//                    pinchCenterOffset = 0;
//                }
//            }
        }

        private boolean checkPointerIds(MotionEvent ev) {
            if (ev.getPointerCount() < 2) {
                return false;
            }
            if (pointerId1 == ev.getPointerId(0) && pointerId2 == ev.getPointerId(1)) {
                return true;
            }
            if (pointerId1 == ev.getPointerId(1) && pointerId2 == ev.getPointerId(0)) {
                return true;
            }
            return false;
        }


        public int getNextMediaColumnsCount(int mediaColumnsCount, boolean up) {
            int newColumnsCount = mediaColumnsCount + (!up ? 1 : -1);
            if (newColumnsCount > 6) {
                newColumnsCount = !up ? 9 : 6;
            }
            return Utilities.clamp(newColumnsCount, 6, allowStoriesSingleColumn ? 1 : 2);
        }

        private void startPinchToMediaColumnsCount(boolean pinchScaleUp) {
            if (columnsAnimation) {
                return;
            }
            if (isActionModeShowed()) {
                return;
            }
            int newColumnsCount = getNextMediaColumnsCount(columnsCount, pinchScaleUp);
            animateToColumnsCount = newColumnsCount;
            if (animateToColumnsCount == columnsCount || allowStoriesSingleColumn) {
                return;
            }
            supportingListView.setVisibility(View.VISIBLE);
            supportingListView.setAdapter(supportingAdapter);
            supportingListView.setPadding(
                    supportingListView.getPaddingLeft(),
                    0, // changeColumnsTab == TAB_ARCHIVED_STORIES ? dp(64) : 0,
                    supportingListView.getPaddingRight(),
                    dp(42) + footer.getMeasuredHeight() // isStoriesView() ? dp(72) : 0
            );
//            mediaPage.buttonView.setVisibility(changeColumnsTab == TAB_STORIES && isStoriesView() ? View.VISIBLE : View.GONE);

            supportingLayoutManager.setSpanCount(newColumnsCount);
            supportingListView.invalidateItemDecorations();
            supportingLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                @Override
                public int getSpanSize(int position) {
                    if (adapter.getItemViewType(position) == 2) {
                        return columnsCount;
                    }
                    return 1;
                }
            });
            AndroidUtilities.updateVisibleRows(listView);

            columnsAnimation = true;
//            if (changeColumnsTab == TAB_PHOTOVIDEO) {
//                sharedMediaData[0].setListFrozen(true);
//            }
            columnsAnimationProgress = 0;
            if (pinchCenterPosition >= 0) {
                supportingLayoutManager.scrollToPositionWithOffset(pinchCenterPosition, pinchCenterOffset - supportingListView.getPaddingTop());
            } else {
                saveScrollPosition();
            }
        }

        private void finishPinchToMediaColumnsCount() {
            if (columnsAnimation) {
                if (columnsAnimationProgress == 1f) {
                    columnsAnimation = false;
                    setColumnsCount = columnsCount = animateToColumnsCount;
                    SharedConfig.setStoriesColumnsCount(animateToColumnsCount);

                    int oldItemCount = adapter.getItemCount();
//                    if (i == TAB_PHOTOVIDEO) {
//                        sharedMediaData[0].setListFrozen(false);
//                    }
                    supportingListView.setVisibility(View.GONE);
                    layoutManager.setSpanCount(columnsCount);
                    listView.invalidateItemDecorations();
                    listView.invalidate();
                    if (adapter.getItemCount() == oldItemCount) {
                        AndroidUtilities.updateVisibleRows(listView);
                    } else {
                        adapter.notifyDataSetChanged();
                    }
                    if (pinchCenterPosition >= 0) {
                        View view = supportingLayoutManager.findViewByPosition(pinchCenterPosition);
                        if (view != null) {
                            pinchCenterOffset = view.getTop();
                        }
                        layoutManager.scrollToPositionWithOffset(pinchCenterPosition, -listView.getPaddingTop() + pinchCenterOffset);
                    } else {
                        saveScrollPosition();
                    }
                    return;
                }
                if (columnsAnimationProgress == 0) {
                    columnsAnimation = false;
//                    if (changeColumnsTab == TAB_PHOTOVIDEO) {
//                        sharedMediaData[0].setListFrozen(false);
//                    }
                    supportingListView.setVisibility(View.GONE);
                    listView.invalidate();
                    return;
                }
                boolean forward = columnsAnimationProgress > 0.2f;
                ValueAnimator animator = ValueAnimator.ofFloat(columnsAnimationProgress, forward ? 1f : 0);
                animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator valueAnimator) {
                        columnsAnimationProgress = (float) valueAnimator.getAnimatedValue();
                        listView.invalidate();
                    }
                });
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        columnsAnimation = false;
                        if (forward) {
                            columnsCount = animateToColumnsCount;
                            setColumnsCount = columnsCount;
                            SharedConfig.setStoriesColumnsCount(animateToColumnsCount);
                        }
                        int oldItemCount = adapter.getItemCount();
//                        if (i == TAB_PHOTOVIDEO) {
//                            sharedMediaData[0].setListFrozen(false);
//                        }
                        if (forward) {
                            layoutManager.setSpanCount(columnsCount);
                            listView.invalidateItemDecorations();
                            if (adapter.getItemCount() == oldItemCount) {
                                AndroidUtilities.updateVisibleRows(listView);
                            } else {
                                adapter.notifyDataSetChanged();
                            }
                        }
                        supportingListView.setVisibility(View.GONE);
                        if (pinchCenterPosition >= 0) {
                            if (forward) {
                                View view = supportingLayoutManager.findViewByPosition(pinchCenterPosition);
                                if (view != null) {
                                    pinchCenterOffset = view.getTop();
                                }
                            }
                            layoutManager.scrollToPositionWithOffset(pinchCenterPosition, -listView.getPaddingTop() + pinchCenterOffset);
                        } else {
                            saveScrollPosition();
                        }
                        super.onAnimationEnd(animation);
                    }
                });
                animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
                animator.setDuration(200);
                animator.start();
            }
        }

        private void saveScrollPosition() {
//            for (int k = 0; k < mediaPages.length; k++) {
//                RecyclerListView listView = mediaPages[k].listView;
//                if (listView != null) {
//                    int messageId = 0;
//                    int offset = 0;
//                    for (int i = 0; i < listView.getChildCount(); i++) {
//                        View child = listView.getChildAt(i);
//                        if (child instanceof SharedPhotoVideoCell2) {
//                            SharedPhotoVideoCell2 cell = (SharedPhotoVideoCell2) child;
//                            messageId = cell.getMessageId();
//                            offset = cell.getTop();
//                        }
//                        if (child instanceof SharedDocumentCell) {
//                            SharedDocumentCell cell = (SharedDocumentCell) child;
//                            messageId = cell.getMessage().getId();
//                            offset = cell.getTop();
//                        }
//                        if (child instanceof SharedAudioCell) {
//                            SharedAudioCell cell = (SharedAudioCell) child;
//                            messageId = cell.getMessage().getId();
//                            offset = cell.getTop();
//                        }
//                        if (messageId != 0) {
//                            break;
//                        }
//                    }
//                    if (messageId != 0) {
//                        int index = -1, position = -1;
//                        final int type = mediaPages[k].selectedType;
//                        if (type == TAB_STORIES || type == TAB_ARCHIVED_STORIES) {
//                            SharedMediaLayout.StoriesAdapter adapter = type == TAB_STORIES ? storiesAdapter : archivedStoriesAdapter;
//                            if (adapter.storiesList != null) {
//                                for (int i = 0; i < adapter.storiesList.messageObjects.size(); ++i) {
//                                    if (messageId == adapter.storiesList.messageObjects.get(i).getId()) {
//                                        index = i;
//                                        break;
//                                    }
//                                }
//                            }
//                            position = index;
//                        } else if (type >= 0 && type < sharedMediaData.length) {
//                            for (int i = 0; i < sharedMediaData[type].messages.size(); i++) {
//                                if (messageId == sharedMediaData[type].messages.get(i).getId()) {
//                                    index = i;
//                                    break;
//                                }
//                            }
//                            position = sharedMediaData[type].startOffset + index;
//                        } else {
//                            continue;
//                        }
//                        if (index >= 0) {
//                            ((LinearLayoutManager) listView.getLayoutManager()).scrollToPositionWithOffset(position, -mediaPages[k].listView.getPaddingTop() + offset);
//                            if (photoVideoChangeColumnsAnimation) {
//                                mediaPages[k].animationSupportingLayoutManager.scrollToPositionWithOffset(position, -mediaPages[k].listView.getPaddingTop() + offset);
//                            }
//                        }
//                    }
//                }
//            }
        }

        public class FooterView extends LinearLayout {

            private final TextView textView;
            private final ButtonWithCounterView buttonView;
            private final TextView orTextView;
            private final ButtonWithCounterView button2View;

            public FooterView(Context context, Theme.ResourcesProvider resourcesProvider) {
                super(context);

                setPadding(dp(24), dp(21), dp(24), dp(21));
                setOrientation(VERTICAL);

                textView = new TextView(context);
                textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                textView.setGravity(Gravity.CENTER);
                textView.setTextAlignment(TEXT_ALIGNMENT_CENTER);
                addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 19));

                buttonView = new ButtonWithCounterView(context, resourcesProvider) {
                    @Override
                    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                    }
                };
                buttonView.setMinWidth(dp(200));
                buttonView.setText(LocaleController.getString(R.string.ProfileBotAddPreview), false);
                addView(buttonView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 44, Gravity.CENTER));

                orTextView = new TextView(context) {
                    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    @Override
                    protected void dispatchDraw(Canvas canvas) {
                        final int cy = getHeight() / 2 + dp(1);
                        final int h = Math.max(1, dp(.66f));
                        Layout layout = getLayout();
                        if (layout != null) {
                            paint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider), .45f));
                            canvas.drawRect(0, cy - h / 2f, (getWidth() - (layout.getLineWidth(0) + dp(16))) / 2f, cy + h / 2f, paint);
                            canvas.drawRect((getWidth() + layout.getLineWidth(0) + dp(16)) / 2f, cy - h / 2f, getWidth(), cy + h / 2f, paint);
                        }
                        super.dispatchDraw(canvas);
                    }
                };
                orTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
                orTextView.setText(LocaleController.getString(R.string.ProfileBotOr));
                orTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                orTextView.setTextAlignment(TEXT_ALIGNMENT_CENTER);
                orTextView.setGravity(Gravity.CENTER);
                orTextView.setTypeface(AndroidUtilities.bold());
                addView(orTextView, LayoutHelper.createLinear(165, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 17, 0, 12));

                button2View = new ButtonWithCounterView(context, false, resourcesProvider);
                button2View.setMinWidth(dp(200));
                addView(button2View, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 44, Gravity.CENTER));
            }

            public void set(
                CharSequence text,
                CharSequence buttonText, Runnable buttonListener
            ) {
                set(text, buttonText, buttonListener, null, null);
            }

            public void set(
                CharSequence text,
                CharSequence buttonText, Runnable buttonListener,
                CharSequence button2Text, Runnable button2Listener
            ) {
                textView.setText(text);
                buttonView.setText(buttonText, false);
                buttonView.setOnClickListener(v -> buttonListener.run());
                if (button2Text == null) {
                    orTextView.setVisibility(View.GONE);
                    button2View.setVisibility(View.GONE);
                } else {
                    orTextView.setVisibility(View.VISIBLE);
                    button2View.setVisibility(View.VISIBLE);
                    button2View.setText(button2Text, false);
                    button2View.setOnClickListener(v -> button2Listener.run());
                }
            }
        }

    }

    private static class ChooseLanguageSheet extends BottomSheetWithRecyclerListView {

        private final int currentAccount;
        private final CharSequence title;
        private UniversalAdapter adapter;

        private FrameLayout searchContainer;
        private ImageView searchImageView;
        private EditTextBoldCursor searchEditText;

        public ChooseLanguageSheet(BaseFragment fragment, CharSequence title, Utilities.Callback<String> whenSelected) {
            super(fragment, true, false, false, fragment.getResourceProvider());

            searchContainer = new FrameLayout(getContext());
            searchImageView = new ImageView(getContext());

            this.currentAccount = fragment.getCurrentAccount();
            this.title = title;
            updateTitle();
            topPadding = .6f;
            setShowHandle(true);
            handleOffset = true;
            fixNavigationBar();
            setSlidingActionBar();

            recyclerListView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);
            recyclerListView.setOnItemClickListener((view, position) -> {
                if (adapter == null) return;
                position -= 1;
                UItem item = adapter.getItem(position);
                if (item != null && item.object instanceof TranslateController.Language) {
                    whenSelected.run(((TranslateController.Language) item.object).code);
                    dismiss();
                }
            });
        }

        @Override
        protected CharSequence getTitle() {
            return title;
        }

        @Override
        protected RecyclerListView.SelectionAdapter createAdapter(RecyclerListView listView) {
            adapter = new UniversalAdapter(listView, getContext(), currentAccount, 0, this::fillItems, resourcesProvider);
            adapter.setApplyBackground(false);
            return adapter;
        }

        private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
            ArrayList<TranslateController.Language> languages = TranslateController.getLanguages();
            for (TranslateController.Language lng : languages) {
                items.add(LanguageView.Factory.of(lng));
            }
        }

        public static class LanguageView extends LinearLayout {

            private final TextView title;
            private final TextView subtitle;

            public LanguageView(Context context) {
                super(context);

                setPadding(dp(22), 0, dp(22), 0);
                setOrientation(VERTICAL);

                title = new TextView(context);
                title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                title.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                title.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
                addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 7, 0, 0));

                subtitle = new TextView(context);
                subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
                subtitle.setTextColor(Theme.getColor(Theme.key_dialogTextGray2));
                subtitle.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
                addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 4, 0, 0));
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(
                    MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(dp(56), MeasureSpec.EXACTLY)
                );
            }

            private boolean needDivider;
            public void set(TranslateController.Language lng, boolean divider) {
                title.setText(lng.displayName);
                subtitle.setText(lng.ownDisplayName);

                if (needDivider != divider) invalidate();
                setWillNotDraw(!(needDivider = divider));
            }

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                if (needDivider) {
                    canvas.drawRect(getPaddingLeft(), getHeight() - 1, getWidth(), getHeight(), Theme.dividerPaint);
                }
            }

            public static class Factory extends UItem.UItemFactory<LanguageView> {
                static { setup(new Factory()); }
                @Override
                public LanguageView createView(Context context, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                    return new LanguageView(context);
                }
                @Override
                public void bindView(View view, UItem item, boolean divider) {
                    ((LanguageView) view).set((TranslateController.Language) item.object, divider);
                }
                public static UItem of(TranslateController.Language l) {
                    UItem item = UItem.ofFactory(LanguageView.Factory.class);
                    item.object = l;
                    return item;
                }
            }
        }
    }
}
