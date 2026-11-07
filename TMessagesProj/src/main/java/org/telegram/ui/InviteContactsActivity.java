/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.net.Uri;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleActionBarMenu;
import org.telegram.ui.ActionBar.SimpleActionBarMenuItem;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.InviteUserCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.FragmentFloatingButton;
import org.telegram.ui.Components.GroupCreateSpan;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.StickerEmptyView;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl;
import org.telegram.ui.Components.inset.WindowAnimatedInsetsProvider;
import org.telegram.utils.glass.BaseFragmentSimpleGlass;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Timer;
import java.util.TimerTask;

import me.vkryl.android.animator.FactorAnimator;

public class InviteContactsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, View.OnClickListener, FactorAnimator.Target, WindowAnimatedInsetsProvider.Listener {
    private static final int search_button = 1;

    private static final int ANIMATOR_ID_SELECTED_STRIP_HEIGHT = 3;
    /** Visible height of the strip with the selected contacts under the header, 0 while nothing is selected. */
    private final FactorAnimator animatorSelectedStripHeight = new FactorAnimator(ANIMATOR_ID_SELECTED_STRIP_HEIGHT,
            this, CubicBezierInterpolator.EASE_OUT_QUINT, 350, 0);

    private FragmentFloatingButton floatingButton;
    private SimpleActionBarMenuItem searchItem;

    private SelectedStrip selectedStrip;
    private ScrollView scrollView;
    private SpansContainer spansContainer;
    private RecyclerListView listView;
    private LinearLayoutManager layoutManager;
    private StickerEmptyView emptyView;
    private InviteAdapter adapter;
    private ArrayList<ContactsController.Contact> phoneBookContacts;
    private int maxSize;

    private boolean searchWas;
    private boolean searching;
    private final HashMap<String, GroupCreateSpan> selectedContacts = new HashMap<>();
    private final ArrayList<GroupCreateSpan> allSpans = new ArrayList<>();
    private GroupCreateSpan currentDeletingSpan;

    /** Chips of the selected contacts, wrapped into lines. */
    private class SpansContainer extends ViewGroup {

        private AnimatorSet currentAnimation;
        private boolean animationStarted;
        private final ArrayList<Animator> animators = new ArrayList<>();
        private View removingSpan;
        private int containerHeight;

        public SpansContainer(Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            final int count = getChildCount();
            final int width = MeasureSpec.getSize(widthMeasureSpec);
            final int padding = dp(8);
            final int maxWidth = width - padding * 2;
            int currentLineWidth = 0;
            int y = padding;
            int allCurrentLineWidth = 0;
            int allY = padding;
            int maxTy = 0;
            for (int a = 0; a < count; a++) {
                View child = getChildAt(a);
                if (!(child instanceof GroupCreateSpan)) {
                    continue;
                }
                child.measure(MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(dp(28), MeasureSpec.EXACTLY));
                if (child != removingSpan && currentLineWidth + child.getMeasuredWidth() > maxWidth) {
                    y += dp(28 + 6);
                    currentLineWidth = 0;
                }
                if (allCurrentLineWidth + child.getMeasuredWidth() > maxWidth) {
                    allY += dp(28 + 6);
                    allCurrentLineWidth = 0;
                }
                final int x = padding + currentLineWidth;
                if (!animationStarted) {
                    if (child == removingSpan) {
                        child.setTranslationX(padding + allCurrentLineWidth);
                        child.setTranslationY(allY);
                    } else if (removingSpan != null) {
                        if (child.getTranslationX() != x) {
                            animators.add(ObjectAnimator.ofFloat(child, View.TRANSLATION_X, x));
                        }
                        if (child.getTranslationY() != y) {
                            animators.add(ObjectAnimator.ofFloat(child, View.TRANSLATION_Y, y));
                        }
                    } else {
                        child.setTranslationX(x);
                        child.setTranslationY(y);
                    }
                }
                if (child != removingSpan) {
                    currentLineWidth += child.getMeasuredWidth() + dp(6);
                    maxTy = Math.max(maxTy, y);
                }
                allCurrentLineWidth += child.getMeasuredWidth() + dp(6);
            }
            final int contentHeight = maxTy + dp(28) + padding;
            if (!animationStarted) {
                if (currentAnimation != null) {
                    containerHeight = contentHeight;
                    currentAnimation.playTogether(animators);
                    currentAnimation.start();
                    animationStarted = true;
                } else {
                    containerHeight = Math.max(contentHeight, allY + dp(28) + padding);
                }
            }
            animatorSelectedStripHeight.animateTo(allSpans.isEmpty() ? 0 : Math.min(contentHeight, maxSize));
            setMeasuredDimension(width, containerHeight);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            for (int a = 0, N = getChildCount(); a < N; a++) {
                final View child = getChildAt(a);
                child.layout(0, 0, child.getMeasuredWidth(), child.getMeasuredHeight());
            }
        }

        public void addSpan(final GroupCreateSpan span) {
            allSpans.add(span);
            selectedContacts.put(span.getKey(), span);

            if (currentAnimation != null) {
                currentAnimation.setupEndValues();
                currentAnimation.cancel();
            }
            animationStarted = false;
            currentAnimation = new AnimatorSet();
            currentAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animator) {
                    currentAnimation = null;
                    animationStarted = false;
                }
            });
            currentAnimation.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            currentAnimation.setDuration(320);
            animators.clear();
            animators.add(ObjectAnimator.ofFloat(span, View.SCALE_X, 0.75f, 1.0f));
            animators.add(ObjectAnimator.ofFloat(span, View.SCALE_Y, 0.75f, 1.0f));
            animators.add(ObjectAnimator.ofFloat(span, View.ALPHA, 0.0f, 1.0f));
            addView(span);
            // the newest chip is at the end: keep it in view
            scrollView.post(() -> scrollView.smoothScrollTo(0, getHeight()));
        }

        public void removeSpan(final GroupCreateSpan span) {
            selectedContacts.remove(span.getKey());
            allSpans.remove(span);
            span.setOnClickListener(null);

            if (currentAnimation != null && currentAnimation.isRunning()) {
                currentAnimation.setupEndValues();
                currentAnimation.cancel();
            }
            animationStarted = false;
            currentAnimation = new AnimatorSet();
            currentAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animator) {
                    removeView(span);
                    removingSpan = null;
                    currentAnimation = null;
                    animationStarted = false;
                }
            });
            currentAnimation.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            currentAnimation.setDuration(320);
            removingSpan = span;
            animators.clear();
            animators.add(ObjectAnimator.ofFloat(removingSpan, View.SCALE_X, 1.0f, 0.75f));
            animators.add(ObjectAnimator.ofFloat(removingSpan, View.SCALE_Y, 1.0f, 0.75f));
            animators.add(ObjectAnimator.ofFloat(removingSpan, View.ALPHA, 1.0f, 0.0f));
            requestLayout();
        }
    }

    /**
     * Glass strip under the header with the chips of the selected contacts. It grows from nothing with the first
     * selection, up to {@link #maxSize}, then the chips scroll inside it.
     */
    private class SelectedStrip extends FrameLayout {
        private final BlurredBackgroundDrawable glass;
        private final Path clipPath = new Path();
        private final RectF rect = new RectF();
        private float glassRadius = -1;

        public SelectedStrip(Context context) {
            super(context);
            glass = getBaseSimpleGlass().factory.create(this)
                .setColorProvider(BlurredBackgroundProviderImpl.simpleActionBar(getResourceProvider()))
                .setPadding(dp(6));
            setWillNotDraw(false);
            addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        }

        private float getVisibleHeight() {
            return animatorSelectedStripHeight.getFactor();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(maxSize, MeasureSpec.EXACTLY));
        }

        @Override
        protected void dispatchDraw(@NonNull Canvas canvas) {
            final float h = getVisibleHeight();
            if (h < 1) {
                return;
            }
            // a strip shorter than one line of chips grows as a pill: scale it in with the height
            final float k = Math.min(1f, h / dp(44));
            final float inset = (1f - k) * dp(24);
            rect.set(inset, 0, getWidth() - inset, h);
            final float radius = Math.min(h / 2f, dp(22));
            if (Math.abs(radius - glassRadius) > 0.5f) {
                glassRadius = radius;
                glass.setRadius(radius);
            }
            final int p = dp(6);
            glass.setBounds((int) rect.left - p, (int) rect.top - p, (int) rect.right + p, (int) rect.bottom + p);
            glass.setAlpha((int) (255 * k));
            glass.draw(canvas);

            canvas.save();
            clipPath.rewind();
            clipPath.addRoundRect(rect, radius, radius, Path.Direction.CW);
            canvas.clipPath(clipPath);
            super.dispatchDraw(canvas);
            canvas.restore();
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent ev) {
            if (ev.getAction() == MotionEvent.ACTION_DOWN && ev.getY() > getVisibleHeight()) {
                return false; // below the visible strip: the list gets the touch
            }
            return super.dispatchTouchEvent(ev);
        }
    }

    public InviteContactsActivity() {
        super();
    }

    @Override
    protected boolean useSimpleActionBar() {
        return true;
    }

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getInstance(currentAccount).addWeakObserver(this, NotificationCenter.contactsImported);
        NotificationCenter.getInstance(currentAccount).addWeakObserver(this, NotificationCenter.contactsDidLoad);
        fetchContacts();
        if (!UserConfig.getInstance(currentAccount).contactsReimported) {
            ContactsController.getInstance(currentAccount).forceImportContacts();
            UserConfig.getInstance(currentAccount).contactsReimported = true;
            UserConfig.getInstance(currentAccount).saveConfig(false);
        }
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.contactsImported);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.contactsDidLoad);
    }

    @Override
    public void onClick(View v) {
        GroupCreateSpan span = (GroupCreateSpan) v;
        if (span.isDeleting()) {
            currentDeletingSpan = null;
            spansContainer.removeSpan(span);
            updateHint();
            checkVisibleRows();
        } else {
            if (currentDeletingSpan != null) {
                currentDeletingSpan.cancelDeleteAnimation();
            }
            currentDeletingSpan = span;
            span.startDeleteAnimation();
        }
    }

    @Override
    public View createView(Context context) {
        setHasOwnBackground(true);

        searching = false;
        searchWas = false;
        allSpans.clear();
        selectedContacts.clear();
        currentDeletingSpan = null;

        simpleActionBar.setBackButtonImage(R.drawable.ic_ab_back);
        simpleActionBar.setTitle(getString(R.string.InviteFriends));
        simpleActionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        final SimpleActionBarMenu menu = simpleActionBar.createMenu();
        searchItem = menu.addItem(search_button, R.drawable.outline_header_search);
        searchItem.setContentDescription(getString(R.string.Search));
        searchItem.setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
            @Override
            public void onSearchCollapse() {
                closeSearch();
            }

            @Override
            public void onTextChanged(EditText editText) {
                if (editText.length() != 0) {
                    searching = true;
                    searchWas = true;
                    adapter.setSearching(true);
                    adapter.searchDialogs(editText.getText().toString());
                    listView.setFastScrollVisible(false);
                    listView.setVerticalScrollBarEnabled(true);
                    emptyView.showProgress(true);
                    emptyView.setStickerType(StickerEmptyView.STICKER_TYPE_SEARCH);
                    emptyView.title.setText(getString(R.string.NoResult));
                    emptyView.subtitle.setText(getString(R.string.SearchEmptyViewFilteredSubtitle2));
                } else {
                    closeSearch();
                }
            }
        });
        searchItem.setSearchFieldHint(getString(R.string.Search));

        FrameLayout contentView;
        fragmentView = contentView = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                final int width = MeasureSpec.getSize(widthMeasureSpec);
                final int height = MeasureSpec.getSize(heightMeasureSpec);
                maxSize = AndroidUtilities.isTablet() || height > width ? dp(144) : dp(78);
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        };

        scrollView = new ScrollView(context);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setClipToPadding(false);

        spansContainer = new SpansContainer(context);
        scrollView.addView(spansContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        selectedStrip = new SelectedStrip(context);

        FlickerLoadingView flickerLoadingView = new FlickerLoadingView(context);
        flickerLoadingView.setViewType(FlickerLoadingView.USERS_TYPE);
        flickerLoadingView.showDate(false);

        emptyView = new StickerEmptyView(context, flickerLoadingView, StickerEmptyView.STICKER_TYPE_NO_CONTACTS);
        emptyView.addView(flickerLoadingView, 0);
        emptyView.setAnimateLayoutChange(true);
        emptyView.title.setText(getString(R.string.NoContacts));
        emptyView.subtitle.setText("");
        emptyView.showProgress(ContactsController.getInstance(currentAccount).isLoadingContacts());

        layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false);

        adapter = new InviteAdapter(context);

        listView = new RecyclerListView(context);
        listView.setSections();
        listView.setSectionsDrawBackground(true);
        listView.setEmptyView(emptyView);
        listView.setAdapter(adapter);
        listView.setLayoutManager(layoutManager);
        listView.setVerticalScrollBarEnabled(false);
        listView.setVerticalScrollbarPosition(LocaleController.isRTL ? View.SCROLLBAR_POSITION_LEFT : View.SCROLLBAR_POSITION_RIGHT);
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        contentView.addView(emptyView);
        listView.setOnItemClickListener((view, position) -> {
            if (position == 0 && !searching) {
                try {
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType("text/plain");
                    String text = ContactsController.getInstance(currentAccount).getInviteText(0);
                    intent.putExtra(Intent.EXTRA_TEXT, text);
                    getParentActivity().startActivityForResult(Intent.createChooser(intent, text), 500);
                } catch (Exception e) {
                    FileLog.e(e);
                }
                return;
            }
            if (!(view instanceof InviteUserCell)) {
                return;
            }
            InviteUserCell cell = (InviteUserCell) view;
            ContactsController.Contact contact = cell.getContact();
            if (contact == null) {
                return;
            }
            final GroupCreateSpan selectedSpan = selectedContacts.get(contact.key);
            if (selectedSpan != null) {
                spansContainer.removeSpan(selectedSpan);
            } else {
                GroupCreateSpan span = new GroupCreateSpan(getContext(), null, contact, true, resourceProvider);
                spansContainer.addSpan(span);
                span.setOnClickListener(InviteContactsActivity.this);
            }
            updateHint();
            cell.setChecked(selectedSpan == null, true);
        });
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING && simpleActionBar.isSearchFieldVisible()) {
                    AndroidUtilities.hideKeyboard(simpleActionBar.getSearchField());
                }
            }
        });

        BackDrawable backDrawable = new BackDrawable(false);
        backDrawable.setArrowRotation(180);
        floatingButton = new FragmentFloatingButton(context, resourceProvider);
        floatingButton.imageView.setImageDrawable(backDrawable);
        floatingButton.setButtonVisible(false, false);
        floatingButton.setContentDescription(getString(R.string.Next));
        floatingButton.setOnClickListener(v -> {
            try {
                StringBuilder builder = new StringBuilder();
                int num = 0;
                for (int a = 0; a < allSpans.size(); a++) {
                    ContactsController.Contact contact = allSpans.get(a).getContact();
                    if (builder.length() != 0) {
                        builder.append(';');
                    }
                    builder.append(contact.phones.get(0));
                    if (a == 0 && allSpans.size() == 1) {
                        num = contact.imported;
                    }
                }
                Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + builder.toString()));
                intent.putExtra("sms_body", ContactsController.getInstance(currentAccount).getInviteText(num));
                getParentActivity().startActivityForResult(intent, 500);
            } catch (Exception e) {
                FileLog.e(e);
            }
            finishFragment();
        });

        checkUi_emptyViewVisible();

        contentView.addView(selectedStrip, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 6, 0, 6, 0));
        contentView.addView(floatingButton, FragmentFloatingButton.createDefaultLayoutParams());

        if (LaunchActivity.instance != null) {
            LaunchActivity.instance.getRootAnimatedInsetsListener().subscribeToWindowInsetsAnimation(this);
        }
        return fragmentView;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.contactsImported) {
            fetchContacts();
        } else if (id == NotificationCenter.contactsDidLoad) {
            if (emptyView != null) {
                emptyView.showProgress(false);
            }
        }
    }

    private void checkVisibleRows() {
        int count = listView.getChildCount();
        for (int a = 0; a < count; a++) {
            View child = listView.getChildAt(a);
            if (child instanceof InviteUserCell) {
                InviteUserCell cell = (InviteUserCell) child;
                ContactsController.Contact contact = cell.getContact();
                if (contact != null) {
                    cell.setChecked(selectedContacts.containsKey(contact.key), true);
                }
            }
        }
    }

    private void updateHint() {
        floatingButton.setButtonVisible(!allSpans.isEmpty(), true);
    }

    private void closeSearch() {
        searching = false;
        searchWas = false;
        adapter.setSearching(false);
        adapter.searchDialogs(null);
        listView.setFastScrollVisible(true);
        listView.setVerticalScrollBarEnabled(false);
        emptyView.showProgress(false);
        emptyView.setStickerType(StickerEmptyView.STICKER_TYPE_NO_CONTACTS);
        emptyView.title.setText(getString(R.string.NoContacts));
        emptyView.subtitle.setText("");
    }

    private void fetchContacts() {
        phoneBookContacts = new ArrayList<>(ContactsController.getInstance(currentAccount).phoneBookContacts);
        Collections.sort(phoneBookContacts, (o1, o2) -> {
            if (o1.imported > o2.imported) {
                return -1;
            } else if (o1.imported < o2.imported) {
                return 1;
            }
            return 0;
        });
        if (emptyView != null) {
            emptyView.showProgress(false);
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    public class InviteAdapter extends RecyclerListView.SelectionAdapter {

        private final Context context;
        private ArrayList<ContactsController.Contact> searchResult = new ArrayList<>();
        private ArrayList<CharSequence> searchResultNames = new ArrayList<>();
        private Timer searchTimer;
        private boolean searching;

        public InviteAdapter(Context ctx) {
            context = ctx;
        }

        public void setSearching(boolean value) {
            if (searching == value) {
                return;
            }
            searching = value;
            notifyDataSetChanged();
        }

        @Override
        public int getItemCount() {
            if (searching) {
                return searchResult.size();
            }
            return phoneBookContacts.size() + 2;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            if (viewType == 1) {
                final TextCell textCell = new TextCell(context);
                textCell.setColors(Theme.key_windowBackgroundWhiteBlackText, Theme.key_windowBackgroundWhiteBlackText);
                textCell.setTextAndValueAndIcon(LocaleController.getString(R.string.ShareTelegram2), "", R.drawable.msg_shareout, false);
                view = textCell;
            } else if (viewType == 2) {
                view = new ShadowSectionCell(context, 12);
            } else {
                view = new InviteUserCell(context, true);
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (holder.getItemViewType() == 0) {
                InviteUserCell cell = (InviteUserCell) holder.itemView;
                ContactsController.Contact contact;
                CharSequence name;
                if (searching) {
                    contact = searchResult.get(position);
                    name = searchResultNames.get(position);
                } else {
                    contact = phoneBookContacts.get(position - 2);
                    name = null;
                }
                cell.setUser(contact, name);
                cell.setChecked(selectedContacts.containsKey(contact.key), false);
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (!searching) {
                if (position == 0) {
                    return 1;
                }
                if (position == 1) {
                    return 2;
                }
            }
            return 0;
        }

        @Override
        public void onViewRecycled(RecyclerView.ViewHolder holder) {
            if (holder.itemView instanceof InviteUserCell) {
                ((InviteUserCell) holder.itemView).recycle();
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() != 2;
        }

        public void searchDialogs(final String query) {
            try {
                if (searchTimer != null) {
                    searchTimer.cancel();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            if (query == null) {
                searchResult.clear();
                searchResultNames.clear();
                notifyDataSetChanged();
            } else {
                searchTimer = new Timer();
                searchTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        try {
                            searchTimer.cancel();
                            searchTimer = null;
                        } catch (Exception e) {
                            FileLog.e(e);
                        }

                        AndroidUtilities.runOnUIThread(() -> Utilities.searchQueue.postRunnable(() -> {
                            String search1 = query.trim().toLowerCase();
                            if (search1.isEmpty()) {
                                updateSearchResults(new ArrayList<>(), new ArrayList<>());
                                return;
                            }
                            String search2 = LocaleController.getInstance().getTranslitString(search1);
                            if (search1.equals(search2) || search2.isEmpty()) {
                                search2 = null;
                            }
                            String[] search = new String[1 + (search2 != null ? 1 : 0)];
                            search[0] = search1;
                            if (search2 != null) {
                                search[1] = search2;
                            }

                            ArrayList<ContactsController.Contact> resultArray = new ArrayList<>();
                            ArrayList<CharSequence> resultArrayNames = new ArrayList<>();

                            for (int a = 0; a < phoneBookContacts.size(); a++) {
                                ContactsController.Contact contact = phoneBookContacts.get(a);

                                String name = ContactsController.formatName(contact.first_name, contact.last_name).toLowerCase();
                                String tName = LocaleController.getInstance().getTranslitString(name);
                                if (name.equals(tName)) {
                                    tName = null;
                                }

                                int found = 0;
                                for (String q : search) {
                                    if (name.startsWith(q) || name.contains(" " + q) || tName != null && (tName.startsWith(q) || tName.contains(" " + q))) {
                                        found = 1;
                                    }

                                    if (found != 0) {
                                        resultArrayNames.add(AndroidUtilities.generateSearchName(contact.first_name, contact.last_name, q));
                                        resultArray.add(contact);
                                        break;
                                    }
                                }
                            }
                            updateSearchResults(resultArray, resultArrayNames);
                        }));

                    }
                }, 200, 300);
            }
        }

        private void updateSearchResults(final ArrayList<ContactsController.Contact> users, final ArrayList<CharSequence> names) {
            AndroidUtilities.runOnUIThread(() -> {
                if (!searching) {
                    return;
                }
                searchResult = users;
                searchResultNames = names;
                notifyDataSetChanged();
                emptyView.showProgress(false);
            });
        }

        @Override
        public void notifyDataSetChanged() {
            super.notifyDataSetChanged();
            checkUi_emptyViewVisible();
        }
    }

    private void checkUi_emptyViewVisible() {
        if (adapter == null) {
            return;
        }

        if (!searching) {
            final int count = adapter.getItemCount();
            emptyView.setVisibility(count == 2 ? View.VISIBLE : View.INVISIBLE);
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_FASTSCROLL, null, null, null, null, Theme.key_fastScrollActive));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_FASTSCROLL, null, null, null, null, Theme.key_fastScrollInactive));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_FASTSCROLL, null, null, null, null, Theme.key_fastScrollText));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{InviteUserCell.class}, new String[]{"textView"}, null, null, null, Theme.key_groupcreate_sectionText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{InviteUserCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_checkbox));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{InviteUserCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_checkboxCheck));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{InviteUserCell.class}, new String[]{"statusTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueText));

        themeDescriptions.add(new ThemeDescription(spansContainer, 0, new Class[]{GroupCreateSpan.class}, null, null, null, Theme.key_groupcreate_spanBackground));
        themeDescriptions.add(new ThemeDescription(spansContainer, 0, new Class[]{GroupCreateSpan.class}, null, null, null, Theme.key_groupcreate_spanDelete));

        return themeDescriptions;
    }

    /* Insets */

    private int statusBarHeight;
    private int navigationBarHeight;
    private int imeInsetAnimatedHeight;

    @Override
    public View getAnimatedInsetsTargetView() {
        return fragmentView;
    }

    @Override
    public void onAnimatedInsetsChanged(View view, WindowInsetsCompat insets) {
        imeInsetAnimatedHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
        checkUi_floatingButton();
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        statusBarHeight = top;
        navigationBarHeight = bottom;

        checkUi_listViewPadding();
        checkUi_selectedStripY();
        checkUi_floatingButton();
    }

    @Override
    public void onFactorChanged(int id, float factor, float fraction, FactorAnimator callee) {
        if (id == ANIMATOR_ID_SELECTED_STRIP_HEIGHT) {
            final int oldPaddingTop = listView.getPaddingTop();

            selectedStrip.invalidate();
            checkUi_listViewPadding();

            // the content stays in place while the strip grows over it
            final int newPaddingTop = listView.getPaddingTop();
            if (newPaddingTop != oldPaddingTop) {
                listView.scrollBy(0, oldPaddingTop - newPaddingTop);
            }
        }
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Nullable
    @Override
    protected RecyclerListView getListViewForSimpleGlass() {
        return listView;
    }

    private int getSelectedStripTop() {
        return statusBarHeight + ActionBar.getCurrentActionBarHeight() + dp(4);
    }

    private void checkUi_selectedStripY() {
        if (selectedStrip != null) {
            selectedStrip.setTranslationY(getSelectedStripTop());
        }
    }

    private void checkUi_listViewPadding() {
        if (listView == null) {
            return;
        }
        final float stripHeight = animatorSelectedStripHeight.getFactor();
        final int top = ActionBar.getCurrentActionBarHeight() + (stripHeight > 0 ? (int) stripHeight + dp(4) : 0);
        BaseFragmentSimpleGlass.setListViewParams(listView, statusBarHeight, navigationBarHeight, top, 0);
        emptyView.setPadding(0, statusBarHeight + ActionBar.getCurrentActionBarHeight(), 0, navigationBarHeight);
    }

    private void checkUi_floatingButton() {
        if (floatingButton != null) {
            floatingButton.setTranslationY(-Math.max(navigationBarHeight, imeInsetAnimatedHeight));
        }
    }

    @Override
    public void onThemeColorsChanged() {
        try {
            super.onThemeColorsChanged();
            if (listView != null) {
                listView.updateFastScrollColors();
            }
            if (spansContainer != null) {
                for (int a = 0; a < spansContainer.getChildCount(); a++) {
                    View child = spansContainer.getChildAt(a);
                    if (child instanceof GroupCreateSpan) {
                        child.invalidate();
                    }
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    /** theme editor: colors this page shows (former ThemeDescription keys) */
    @Override
    public void addThemeColorKeys(HashSet<Integer> keys) {
        super.addThemeColorKeys(keys);
        keys.add(Theme.key_avatar_backgroundBlue);
        keys.add(Theme.key_groupcreate_spanText);
        keys.add(Theme.key_avatar_backgroundPink);
        keys.add(Theme.key_avatar_backgroundCyan);
        keys.add(Theme.key_avatar_backgroundGreen);
        keys.add(Theme.key_avatar_backgroundViolet);
        keys.add(Theme.key_avatar_backgroundOrange);
        keys.add(Theme.key_avatar_backgroundRed);
        keys.add(Theme.key_avatar_text);
        keys.add(Theme.key_windowBackgroundWhiteGrayText);
        keys.add(Theme.key_windowBackgroundWhiteBlackText);
        keys.add(Theme.key_groupcreate_sectionText);
        keys.add(Theme.key_divider);
        keys.add(Theme.key_listSelector);
    }
}
