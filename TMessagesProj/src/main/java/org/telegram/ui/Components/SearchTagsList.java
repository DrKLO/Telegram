package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.AndroidUtilities.runOnUIThread;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.InputType;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.AlertDialogDecor;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.Premium.PremiumFeatureBottomSheet;
import org.telegram.ui.Components.Reactions.ReactionsLayoutInBubble;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PremiumPreviewFragment;

import java.util.ArrayList;
import java.util.HashSet;

public class SearchTagsList extends BlurredFrameLayout implements NotificationCenter.NotificationCenterDelegate {

    private final int currentAccount;
    private final BaseFragment fragment;
    private final Theme.ResourcesProvider resourcesProvider;
    public final RecyclerListView listView;
    private final Adapter adapter;

    private LinearLayout premiumLayout;

    private long chosen;
    private final ArrayList<Item> oldItems = new ArrayList<>();
    private final ArrayList<Item> items = new ArrayList<>();
    private boolean shownPremiumLayout;

    private static class Item {
        ReactionsLayoutInBubble.VisibleReaction reaction;
        int count;
        String name;
        int nameHash;

        public static Item get(ReactionsLayoutInBubble.VisibleReaction reaction, int count, String name) {
            Item item = new Item();
            item.reaction = reaction;
            item.count = count;
            item.name = name;
            item.nameHash = name == null ? -233 : name.hashCode();
            return item;
        }

        public long hash() {
            return reaction.hash;
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if (!(obj instanceof Item)) {
                return false;
            }
            Item that = (Item) obj;
            return this.count == that.count && this.reaction.hash == that.reaction.hash && this.nameHash == that.nameHash;
        }
    }

    public void setChosen(ReactionsLayoutInBubble.VisibleReaction visibleReaction, boolean notify) {
        if (visibleReaction == null) {
            chosen = 0;
            if (notify) {
                setFilter(null);
            }
            adapter.notifyDataSetChanged();
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            SearchTagsList.Item item = items.get(i);
            if (visibleReaction.hash == item.reaction.hash) {
                chosen = item.hash();
                if (notify) {
                    setFilter(item.reaction);
                }
                adapter.notifyDataSetChanged();
                listView.scrollToPosition(i);
                break;
            }
        }
    }

    private void createPremiumLayout() {
        if (premiumLayout != null) {
            return;
        }

        premiumLayout = new LinearLayout(getContext());
        premiumLayout.setOnClickListener(v -> {
            new PremiumFeatureBottomSheet(fragment, PremiumPreviewFragment.PREMIUM_FEATURE_SAVED_TAGS, true).show();
        });
        premiumLayout.setOrientation(LinearLayout.HORIZONTAL);
        ScaleStateListAnimator.apply(premiumLayout, 0.03f, 1.25f);

        TextView tagView = new TextView(getContext()) {
            private final Path path = new Path();
            private final RectF bounds = new RectF();
            private final Paint paint = new Paint();
            @Override
            protected void dispatchDraw(Canvas canvas) {
                paint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText2, resourcesProvider), 0.10f));
                bounds.set(0, 0, getWidth(), getHeight());
                ReactionsLayoutInBubble.fillTagPath(bounds, path);
                canvas.drawPath(path, paint);
                super.dispatchDraw(canvas);
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                super.onLayout(changed, l, t, r, b);
                int left = getWidth(), right = 0;
                for (int i = 0; i < getChildCount(); ++i) {
                    left = Math.min(left, getChildAt(i).getLeft());
                    right = Math.max(right, getChildAt(i).getRight());
                }
                setPivotX((left + right) / 2f);
            }
        };
        tagView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText2, resourcesProvider));
        tagView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        tagView.setTypeface(AndroidUtilities.bold());
        SpannableStringBuilder ssb = new SpannableStringBuilder();
        Drawable lockDrawable = getContext().getResources().getDrawable(R.drawable.msg_mini_lock3).mutate();
        lockDrawable.setColorFilter(new PorterDuffColorFilter(Theme.key_chat_messageLinkIn, PorterDuff.Mode.SRC_IN));
        ColoredImageSpan span = new ColoredImageSpan(lockDrawable);
        span.setTranslateY(0);
        span.setTranslateX(0);
        span.setScale(.94f, .94f);
        SpannableString lock = new SpannableString("l");
        lock.setSpan(span, 0, lock.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        ssb.append(lock);
        ssb.append(" ").append(LocaleController.getString(R.string.AddTagsToYourSavedMessages1));
        tagView.setText(ssb);
        tagView.setPadding(dp(4), dp(4), dp(9), dp(4));

        TextView textView = new TextView(getContext());
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText2, resourcesProvider));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        textView.setTypeface(AndroidUtilities.bold());
        ssb = new SpannableStringBuilder(LocaleController.getString(R.string.AddTagsToYourSavedMessages2));
        SpannableString arrow = new SpannableString(">");
        Drawable imageDrawable = getContext().getResources().getDrawable(R.drawable.msg_arrowright).mutate();
        imageDrawable.setColorFilter(new PorterDuffColorFilter(Theme.key_chat_messageLinkIn, PorterDuff.Mode.SRC_IN));
        span = new ColoredImageSpan(imageDrawable);
        span.setScale(.76f, .76f);
        span.setTranslateX(-dp(1));
        span.setTranslateY(dp(1));
        arrow.setSpan(span, 0, arrow.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        ssb.append(arrow);
        textView.setText(ssb);
        textView.setPadding(dp(5.66f), dp(4), dp(9), dp(4));

        premiumLayout.addView(tagView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT));
        premiumLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT));

        addView(premiumLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.FILL_HORIZONTAL, 16.33f, 0, 16.33f, 0));
    }

    private long topicId;

    public SearchTagsList(Context context, BaseFragment fragment, SizeNotifierFrameLayout contentView, int currentAccount, long topicId, Theme.ResourcesProvider resourcesProvider, boolean showWithCut) {
        super(context, contentView);

        this.showWithCut = showWithCut;
        this.currentAccount = currentAccount;
        this.fragment = fragment;
        this.resourcesProvider = resourcesProvider;
        this.topicId = topicId;
        ReactionsLayoutInBubble.initPaints(resourcesProvider);

        listView = new RecyclerListView(context, resourcesProvider) {
            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                if (premiumLayout != null && premiumLayout.getAlpha() > 0.5f) {
                    return false;
                }
                return super.dispatchTouchEvent(ev);
            }

            @Override
            public Integer getSelectorColor(int position) {
                return 0;
            }
        };
        listView.setPadding(dp(5.66f), 0, dp(5.66f), 0);
        listView.setClipToPadding(false);
        final LinearLayoutManager layoutManager = new LinearLayoutManager(context);
        layoutManager.setOrientation(RecyclerView.HORIZONTAL);
        listView.setLayoutManager(layoutManager);
        listView.setAdapter(adapter = new Adapter());
        listView.setOverScrollMode(OVER_SCROLL_NEVER);
        addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48));
        listView.setOnItemClickListener((view, position) -> {
            if (position < 0 || position >= items.size()) {
                return;
            }
            if (!UserConfig.getInstance(currentAccount).isPremium()) {
                new PremiumFeatureBottomSheet(fragment, PremiumPreviewFragment.PREMIUM_FEATURE_SAVED_TAGS, true).show();
                return;
            }
            long hash = items.get(position).hash();
            if (!setFilter(chosen == hash ? null : items.get(position).reaction)) {
                return;
            }
            for (int i = 0; i < listView.getChildCount(); i++) {
                View child = listView.getChildAt(i);
                if (child == view) {
                    if (i <= 1) {
                        listView.smoothScrollBy(-dp(i == 0 ? 90 : 50), 0);
                    } else if (i >= listView.getChildCount() - 2) {
                        listView.smoothScrollBy(dp((i == listView.getChildCount() - 1) ? 80 : 50), 0);
                    }
                }
            }
            listView.forAllChild(view2 -> {
                if (view2 instanceof TagButton) {
                    ((TagButton) view2).setChosen(false, true);
                }
            });
            if (chosen == hash) {
                chosen = 0;
            } else {
                chosen = hash;
                ((TagButton) view).setChosen(true, true);
            }
        });
        listView.setOnItemLongClickListener((view, position) -> {
            if (position < 0 || position >= items.size() || !UserConfig.getInstance(currentAccount).isPremium())
                return false;
            if (!UserConfig.getInstance(currentAccount).isPremium()) {
                new PremiumFeatureBottomSheet(fragment, PremiumPreviewFragment.PREMIUM_FEATURE_SAVED_TAGS, true).show();
                return true;
            }
            TagButton btn = (TagButton) view;
            if (btn.reactionButton != null) {
                btn.reactionButton.startAnimation();
            }
            final Item item = items.get(position);
            ItemOptions.makeOptions(fragment, view)
                .setGravity(Gravity.LEFT)
                .add(R.drawable.menu_tag_rename, LocaleController.getString(TextUtils.isEmpty(item.name) ? R.string.SavedTagLabelTag : R.string.SavedTagRenameTag), () -> {
                    openRenameTagAlert(getContext(), currentAccount, item.reaction.toTLReaction(), resourcesProvider, false);
                })
                .show();
            return true;
        });

        DefaultItemAnimator itemAnimator = new DefaultItemAnimator() {
            @Override
            public boolean canReuseUpdatedViewHolder(@NonNull RecyclerView.ViewHolder viewHolder) {
                return true;
            }
            @Override
            public boolean animateMove(RecyclerView.ViewHolder holder, ItemHolderInfo info, int fromX, int fromY, int toX, int toY) {
                final View view = holder.itemView;
                if (view instanceof TagButton) {
                    ((TagButton) view).startAnimate();
                }
                fromX += (int) holder.itemView.getTranslationX();
                fromY += (int) holder.itemView.getTranslationY();
                resetAnimation(holder);
                int deltaX = toX - fromX;
                int deltaY = toY - fromY;
                if (deltaX == 0 && deltaY == 0) {
                    dispatchMoveFinished(holder);
                    return false;
                }
                if (deltaX != 0) {
                    view.setTranslationX(-deltaX);
                }
                if (deltaY != 0) {
                    view.setTranslationY(-deltaY);
                }
                mPendingMoves.add(new MoveInfo(holder, fromX, fromY, toX, toY));
                checkIsRunning();
                return true;
            }
        };
        itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        itemAnimator.setDurations(320);
        listView.setItemAnimator(itemAnimator);

        MediaDataController.getInstance(currentAccount).loadSavedReactions(false);
        updateTags(false);
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (child == listView && premiumLayout != null) {
            if (premiumLayout.getAlpha() >= 1f)
                return false;
            canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), (int) (0xFF * (1f - premiumLayout.getAlpha())), Canvas.ALL_SAVE_FLAG);
            boolean r = super.drawChild(canvas, child, drawingTime);
            canvas.restore();
            return r;
        }
        return super.drawChild(canvas, child, drawingTime);
    }

    private static AlertDialog currentDialog;
    public static boolean onBackPressedRenameTagAlert() {
        if (currentDialog != null) {
            currentDialog.dismiss();
            currentDialog = null;
            return true;
        }
        return false;
    }
    public static void openRenameTagAlert(Context context, int currentAccount, TLRPC.Reaction reaction, Theme.ResourcesProvider resourcesProvider, boolean forceNotAdaptive) {
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
        String name = MessagesController.getInstance(currentAccount).getSavedTagName(reaction);
        builder.setTitle(new SpannableStringBuilder(ReactionsLayoutInBubble.VisibleReaction.fromTL(reaction).toCharSequence(20)).append("  ").append(LocaleController.getString(TextUtils.isEmpty(name) ? R.string.SavedTagLabelTag : R.string.SavedTagRenameTag)));

        final int MAX_NAME_LENGTH = 12;
        EditTextBoldCursor editText = new EditTextBoldCursor(context) {
            AnimatedColor limitColor = new AnimatedColor(this);
            private int limitCount;
            AnimatedTextView.AnimatedTextDrawable limit = new AnimatedTextView.AnimatedTextDrawable(false, true, true); {
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
        editText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    String text = editText.getText().toString();
                    if (text.length() > MAX_NAME_LENGTH) {
                        AndroidUtilities.shakeView(editText);
                        return true;
                    }
                    MessagesController.getInstance(currentAccount).renameSavedReactionTag(ReactionsLayoutInBubble.VisibleReaction.fromTL(reaction), text);
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
            }
        });
        MediaDataController.getInstance(currentAccount).fetchNewEmojiKeywords(AndroidUtilities.getCurrentKeyboardLanguage(), true);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        editText.setText(name == null ? "" : name);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        editText.setHintColor(Theme.getColor(Theme.key_groupcreate_hintText, resourcesProvider));
        editText.setHintText(LocaleController.getString(R.string.SavedTagLabelPlaceholder));
        editText.setSingleLine(true);
        editText.setFocusable(true);
        editText.setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editText.setLineColors(Theme.getColor(Theme.key_windowBackgroundWhiteInputField, resourcesProvider), Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated, resourcesProvider), Theme.getColor(Theme.key_text_RedRegular, resourcesProvider));
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        editText.setBackgroundDrawable(null);
        editText.setPadding(0, 0, dp(42), 0);
//        editText.addTextChangedListener(new TextWatcher() {
//            boolean ignoreTextChange;
//            @Override
//            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
//            @Override
//            public void onTextChanged(CharSequence s, int start, int before, int count) {}
//            @Override
//            public void afterTextChanged(Editable s) {
//                if (ignoreTextChange) {
//                    return;
//                }
//                if (s.length() > MAX_NAME_LENGTH) {
//                    ignoreTextChange = true;
//                    s.delete(MAX_NAME_LENGTH, s.length());
//                    AndroidUtilities.shakeView(editText);
//                    editText.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
//                    ignoreTextChange = false;
//                }
//            }
//        });

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);

        TextView textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setText(LocaleController.getString(R.string.SavedTagLabelTagText));
        container.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 5, 24, 12));

        container.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 0, 24, 10));
        builder.setView(container);
        builder.setWidth(dp(292));

        builder.setPositiveButton(LocaleController.getString(R.string.Save), (dialogInterface, i) -> {
            String text = editText.getText().toString();
            if (text.length() > MAX_NAME_LENGTH) {
                AndroidUtilities.shakeView(editText);
                return;
            }
            MessagesController.getInstance(currentAccount).renameSavedReactionTag(ReactionsLayoutInBubble.VisibleReaction.fromTL(reaction), text);
            dialogInterface.dismiss();
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), (dialogInterface, i) -> {
            dialogInterface.dismiss();
        });
        if (adaptive) {
            dialog[0] = currentDialog = builder.create();
            currentDialog.setOnDismissListener(d -> {
                currentDialog = null;
                currentFocus.requestFocus();
            });
            currentDialog.setOnShowListener(d -> {
                editText.requestFocus();
                AndroidUtilities.showKeyboard(editText);
            });
            currentDialog.showDelayed(250);
        } else {
            dialog[0] = builder.create();
            dialog[0].setOnDismissListener(d -> {
                AndroidUtilities.hideKeyboard(editText);
            });
            dialog[0].setOnShowListener(d -> {
                editText.requestFocus();
                AndroidUtilities.showKeyboard(editText);
            });
            dialog[0].show();
        }
        dialog[0].setDismissDialogByButtons(false);
        editText.setSelection(editText.getText().length());
    }

    public boolean hasFilters() {
        return !items.isEmpty() || shownPremiumLayout;
    }

    public void clear() {
        listView.forAllChild(view2 -> {
            if (view2 instanceof TagButton) {
                ((TagButton) view2).setChosen(false, true);
            }
        });
        chosen = 0;
    }

    protected boolean setFilter(ReactionsLayoutInBubble.VisibleReaction reaction) {
        return true;
    }

    public void attach() {
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.savedReactionTagsUpdate);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.emojiLoaded);
    }

    public void detach() {
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.savedReactionTagsUpdate);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.emojiLoaded);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.savedReactionTagsUpdate) {
            final long thisTopicId = (long) args[0];
            if (thisTopicId == 0 || thisTopicId == topicId) {
                updateTags(true);
            }
        } else if (id == NotificationCenter.emojiLoaded) {
            invalidate();
            AndroidUtilities.forEachViews(listView, View::invalidate);
        }
    }

    public void updateTags(boolean notify) {
        HashSet<Long> hashes = new HashSet<>();

        oldItems.clear();
        oldItems.addAll(items);
        items.clear();

        final MessagesController ms = MessagesController.getInstance(currentAccount);
        TLRPC.TL_messages_savedReactionsTags savedReactionsTags = ms.getSavedReactionTags(topicId);
        boolean hasChosen = false;

        if (savedReactionsTags != null) {
            for (int i = 0; i < savedReactionsTags.tags.size(); ++i) {
                TLRPC.TL_savedReactionTag tag = savedReactionsTags.tags.get(i);
                ReactionsLayoutInBubble.VisibleReaction r = ReactionsLayoutInBubble.VisibleReaction.fromTL(tag.reaction);
                if (!hashes.contains(r.hash)) {
                    if (topicId != 0 && tag.count <= 0)
                        continue;
                    Item item = Item.get(r, tag.count, topicId != 0 ? ms.getSavedTagName(tag.reaction) : tag.title);
                    if (item.hash() == chosen) {
                        hasChosen = true;
                    }
                    items.add(item);
                    hashes.add(r.hash);
                }
            }
        }

        if (!hasChosen && chosen != 0) {
            chosen = 0;
            setFilter(null);
        }

        if (notify) {
            DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override
                public int getOldListSize() {
                    return oldItems.size();
                }

                @Override
                public int getNewListSize() {
                    return items.size();
                }

                @Override
                public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                    return oldItems.get(oldItemPosition).hash() == items.get(newItemPosition).hash();
                }

                @Override
                public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                    return oldItems.get(oldItemPosition).equals(items.get(newItemPosition));
                }
            }).dispatchUpdatesTo(adapter);
        } else {
            adapter.notifyDataSetChanged();
        }

        if (shownPremiumLayout = !UserConfig.getInstance(currentAccount).isPremium()) {
            createPremiumLayout();
            if (!notify) {
                premiumLayout.setVisibility(View.VISIBLE);
                premiumLayout.setAlpha(0f);
                premiumLayout.animate().alpha(1f).start();
            }
        } else if (premiumLayout != null) {
            if (notify) {
                premiumLayout.animate().alpha(0f).withEndAction(() -> {
                    premiumLayout.setVisibility(View.GONE);
                }).start();
            } else {
                premiumLayout.setAlpha(1f);
                premiumLayout.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(40), MeasureSpec.EXACTLY));
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (shownT < .5f) {
            return false;
        }
        return super.dispatchTouchEvent(ev);
    }

    public float shownT;
    public boolean showWithCut = true;
    public void setShown(float shownT) {
        this.shownT = shownT;
        listView.setPivotX(listView.getWidth() / 2f);
        listView.setPivotY(0);
        listView.setScaleX(lerp(0.8f, 1, shownT));
        listView.setScaleY(lerp(0.8f, 1, shownT));
        if (showWithCut) {
            listView.setAlpha(shownT);
        } else {
            setAlpha(shownT);
        }
        invalidate();
    }

    protected void onShownUpdate(boolean finish) {

    }

    private float actionBarTagsT;
    private ValueAnimator actionBarTagsAnimator;
    public void show(boolean show) {
        if (actionBarTagsAnimator != null) {
            Animator a = actionBarTagsAnimator;
            actionBarTagsAnimator = null;
            a.cancel();
        }
        if (show) {
            setVisibility(View.VISIBLE);
        }
        actionBarTagsAnimator = ValueAnimator.ofFloat(actionBarTagsT, show ? 1f : 0f);
        actionBarTagsAnimator.addUpdateListener(valueAnimator1 -> {
            actionBarTagsT = (float) valueAnimator1.getAnimatedValue();
            setShown(actionBarTagsT);
            onShownUpdate(false);
        });
        actionBarTagsAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        actionBarTagsAnimator.setDuration(320);
        actionBarTagsAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (animation != actionBarTagsAnimator) return;
                actionBarTagsT = show ? 1f : 0f;
                setShown(actionBarTagsT);
                if (!show) {
                    setVisibility(View.GONE);
                }
                onShownUpdate(true);
            }
        });
        actionBarTagsAnimator.start();
    }

    public boolean shown() {
        return shownT > 0.5f;
    }
    public int getCurrentHeight() {
        return (int) (getMeasuredHeight() * shownT);
    }

    private Paint backgroundPaint2;
    @Override
    public void setBackgroundColor(int color) {
        if (SharedConfig.chatBlurEnabled() && super.sizeNotifierFrameLayout != null) {
            super.setBackgroundColor(color);
        } else {
            backgroundPaint2 = new Paint(Paint.ANTI_ALIAS_FLAG);
            backgroundPaint2.setColor(color);
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        canvas.save();
        if (showWithCut) {
            canvas.clipRect(0, 0, getWidth(), getCurrentHeight());
        }
        if (backgroundPaint2 != null) {
            canvas.drawRect(0, 0, getWidth(), getCurrentHeight(), backgroundPaint2);
        }
        super.dispatchDraw(canvas);
        canvas.restore();
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {

        public Adapter() {
//            setHasStableIds(true);
        }

//        @Override
//        public long getItemId(int position) {
//            if (position < 0 || position >= items.size()) return position;
//            return items.get(position).hash();
//        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = new TagButton(getContext());
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (position < 0 || position >= items.size()) return;
            final Item item = items.get(position);
            ((TagButton) holder.itemView).set(item);
            ((TagButton) holder.itemView).setChosen(item.hash() == chosen, false);
        }

        @Override
        public void onViewAttachedToWindow(@NonNull RecyclerView.ViewHolder holder) {
            super.onViewAttachedToWindow(holder);
            int position = holder.getAdapterPosition();
            if (position < 0 || position >= items.size()) return;
            final Item item = items.get(position);
            ((TagButton) holder.itemView).setChosen(item.hash() == chosen, false);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }
    }

    private class TagButton extends View {
        public ReactionsLayoutInBubble.ReactionButton reactionButton;
        private final AnimatedFloat progress = new AnimatedFloat(this, 0, 260, CubicBezierInterpolator.EASE_OUT_QUINT);
        private int count;

        public TagButton(Context context) {
            super(context);
            ScaleStateListAnimator.apply(this);
        }

        private ReactionsLayoutInBubble.VisibleReaction lastReaction;
        public void set(Item item) {
            boolean newReactionButton = lastReaction == null || !lastReaction.equals(item.reaction);
            if (newReactionButton) {
                TLRPC.TL_reactionCount reactionCount = new TLRPC.TL_reactionCount();
                reactionCount.reaction = item.reaction.toTLReaction();
                reactionCount.count = item.count;

                reactionButton = new ReactionsLayoutInBubble.ReactionButton(null, currentAccount, this, reactionCount, false, true, resourcesProvider) {
                    @Override
                    protected void updateColors(float progress) {
                        lastDrawnTextColor = ColorUtils.blendARGB(fromTextColor, chosen ? Theme.getColor(Theme.key_chat_inReactionButtonTextSelected, resourcesProvider) : Theme.getColor(Theme.key_actionBarActionModeReactionText, resourcesProvider), progress);
                        lastDrawnBackgroundColor = ColorUtils.blendARGB(fromBackgroundColor, chosen ? Theme.getColor(Theme.key_chat_inReactionButtonBackground, resourcesProvider) : Theme.getColor(Theme.key_actionBarActionModeReaction, resourcesProvider), progress);
                        lastDrawnTextColor = Theme.blendOver(lastDrawnBackgroundColor, lastDrawnTextColor);
                        lastDrawnTagDotColor = ColorUtils.blendARGB(fromTagDotColor, chosen ? 0x5affffff : Theme.getColor(Theme.key_actionBarActionModeReactionDot, resourcesProvider), progress);
                    }

                    @Override
                    protected boolean drawTagDot() {
                        return !drawCounter();
                    }

                    @Override
                    protected int getCacheType() {
                        return AnimatedEmojiDrawable.CACHE_TYPE_SAVED_REACTION;
                    }

                    @Override
                    protected boolean drawCounter() {
                        return count > 0 || hasName || counterDrawable.countChangeProgress != 1f;
                    }

                    @Override
                    protected boolean drawTextWithCounter() {
                        return true;
                    }
                };
                reactionButton.counterDrawable.setSize(dp(29), dp(100));
                reactionButton.isTag = true;
            } else {
                reactionButton.count = item.count;
            }
            lastReaction = item.reaction;
            if (!newReactionButton) {
                reactionButton.animateFromWidth = reactionButton.width;
            }
            reactionButton.width = dp(44.33f);
            reactionButton.hasName = !TextUtils.isEmpty(item.name);
            if (reactionButton.hasName) {
                reactionButton.textDrawable.setText(Emoji.replaceEmoji(item.name, reactionButton.textDrawable.getPaint().getFontMetricsInt(), false), !newReactionButton);
            } else if (reactionButton.textDrawable != null) {
                reactionButton.textDrawable.setText("", !newReactionButton);
            }
            reactionButton.countText = Integer.toString(item.count);
            reactionButton.counterDrawable.setCount(item.count, !newReactionButton);
            if (reactionButton.counterDrawable != null && (reactionButton.count > 0 || reactionButton.hasName)) {
                reactionButton.width += reactionButton.counterDrawable.getCurrentWidth() + dp(reactionButton.hasName ? 4 : 0) + reactionButton.textDrawable.getAnimateToWidth();
            }
            if (newReactionButton) {
                reactionButton.animateFromWidth = reactionButton.width;
            }
            reactionButton.height = dp(28);
            reactionButton.choosen = chosen;
            if (attached) {
                reactionButton.attach();
            }

            if (!newReactionButton) {
                requestLayout();
            }
        }

        public void startAnimate() {
            if (reactionButton == null) return;
            reactionButton.fromTextColor = reactionButton.lastDrawnTextColor;
            reactionButton.fromBackgroundColor = reactionButton.lastDrawnBackgroundColor;
            reactionButton.fromTagDotColor = reactionButton.lastDrawnTagDotColor;
            progress.set(0, true);
            invalidate();
        }

        private boolean chosen;
        public boolean setChosen(boolean value, boolean animated) {
            if (chosen == value) return false;
            chosen = value;
            if (reactionButton != null) {
                reactionButton.choosen = value;

                if (animated) {
                    reactionButton.fromTextColor = reactionButton.lastDrawnTextColor;
                    reactionButton.fromBackgroundColor = reactionButton.lastDrawnBackgroundColor;
                    reactionButton.fromTagDotColor = reactionButton.lastDrawnTagDotColor;
                    progress.set(0, true);
                } else {
                    progress.set(1, true);
                }
                invalidate();
            }
            return true;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(dp(8.67f) + (reactionButton != null ? reactionButton.width : dp(44.33f)), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(40), MeasureSpec.EXACTLY));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            reactionButton.draw(canvas, (getWidth() - reactionButton.width) / 2f, (getHeight() - reactionButton.height) / 2f, progress.set(1f), 1f, false);
        }

        private boolean attached;

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (!attached) {
                if (reactionButton != null) {
                    reactionButton.attach();
                }
                attached = true;
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (attached) {
                if (reactionButton != null) {
                    reactionButton.detach();
                }
                attached = false;
            }
        }
    }
}
