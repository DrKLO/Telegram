package org.telegram.ui.iv;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.DefaultItemAnimator;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.RichMessageLayout;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_aicompose;
import org.telegram.tgnet.tl.TL_iv;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.EditTextCell;
import org.telegram.ui.Components.BottomSheetWithRecyclerListView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.ArrayList;

public class RichAIComposeSheet extends BottomSheetWithRecyclerListView {

    private final int currentAccount;
    private final Utilities.Callback<TL_iv.RichMessage> onAddToPage;

    private UniversalAdapter adapter;

    private final FrameLayout topView;
    private final FrameLayout previewBox;
    private final RichMessageLayout.PreviewView previewView;

    private final FrameLayout promptBox;
    private final EditTextCell promptCell;
    private final ButtonWithCounterView button;

    private boolean loading;
    private int reqId;
    private TL_iv.RichMessage result;

    public RichAIComposeSheet(Context context, int currentAccount, Theme.ResourcesProvider resourcesProvider, Utilities.Callback<TL_iv.RichMessage> onAddToPage) {
        super(context, null, true, false, false, resourcesProvider);
        this.currentAccount = currentAccount;
        this.onAddToPage = onAddToPage;

        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));
        fixNavigationBar(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));

        topView = new FrameLayout(context);

        final TextView titleView = new TextView(context);
        titleView.setText(getString(R.string.ArticleAICreate));
        titleView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        titleView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        topView.addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 56, Gravity.LEFT | Gravity.TOP, 22, 6, 56, 0));

        headerPaddingTop = dp(-8);

        final ImageView closeButton = new ImageView(context);
        closeButton.setImageResource(R.drawable.ic_close_white);
        closeButton.setScaleType(ImageView.ScaleType.CENTER);
        closeButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), PorterDuff.Mode.SRC_IN));
        ScaleStateListAnimator.apply(closeButton);
        closeButton.setOnClickListener(v -> dismiss());
        topView.addView(closeButton, LayoutHelper.createFrame(48, 48, Gravity.RIGHT | Gravity.TOP, 0, 10, 12, 0));

        previewBox = new FrameLayout(context);
        previewView = new RichMessageLayout.PreviewView(context, currentAccount, resourcesProvider);
        previewView.setPadding(dp(14), dp(12), dp(14), dp(12));
        previewView.setBackground(Theme.createRoundRectDrawable(dp(12), Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider)));
        previewBox.addView(previewView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        previewBox.setPadding(dp(12), dp(12), dp(12), 0);

        promptBox = new FrameLayout(context);
        promptCell = new EditTextCell(context, getString(R.string.ArticleAIPrompt), true, false, MessagesController.getInstance(currentAccount).config.aicomposeTonePromptLengthMax.get(), resourcesProvider);
        promptCell.editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        promptCell.editText.setMaxLines(5);
        promptCell.setBackground(Theme.createRoundRectDrawable(dp(20), Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider)));
        promptCell.editText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (result != null) {
                    resetResult();
                }
                updateButtonEnabled();
            }
        });
        promptBox.addView(promptCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        promptBox.setPadding(dp(12), 0, dp(12), 0);

        button = new ButtonWithCounterView(context, resourcesProvider).setRound();
        button.setText(getString(R.string.ArticleAIGenerate), false);
        button.setOnClickListener(v -> onButtonClick());
        containerView.addView(button, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL, 12, 12, 12, 12));
        ((ViewGroup.MarginLayoutParams) button.getLayoutParams()).leftMargin += backgroundPaddingLeft;
        ((ViewGroup.MarginLayoutParams) button.getLayoutParams()).rightMargin += backgroundPaddingLeft;

        final DefaultItemAnimator itemAnimator = new DefaultItemAnimator();
        itemAnimator.setSupportsChangeAnimations(false);
        itemAnimator.setDelayAnimations(false);
        itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        itemAnimator.setDurations(350);
        recyclerListView.setItemAnimator(itemAnimator);
        recyclerListView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, dp(12 + 48 + 12));
        recyclerListView.setClipToPadding(false);

        adapter.update(false);

        updateButtonEnabled();
    }

    @Override
    public void show() {
        super.show();
        AndroidUtilities.runOnUIThread(() -> {
            AndroidUtilities.showKeyboard(promptCell.editText);
        }, 200);
    }

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.ArticleAICreate);
    }

    @Override
    protected RecyclerListView.SelectionAdapter createAdapter(RecyclerListView listView) {
        return adapter = new UniversalAdapter(listView, getContext(), currentAccount, 0, true, this::fillItems, resourcesProvider);
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asCustom(1, topView));
        items.add(UItem.asCustom(3, promptBox));
        if (result != null) {
            items.add(UItem.asCustom(2, previewBox));
        }
    }

    private void updateButtonEnabled() {
        if (result != null) {
            button.setEnabled(true);
            return;
        }
        button.setEnabled(!TextUtils.isEmpty(promptCell.editText.getText().toString().trim()));
    }

    private void onButtonClick() {
        if (loading) return;
        if (result != null) {
            if (onAddToPage != null) onAddToPage.run(result);
            dismiss();
            return;
        }
        final String prompt = promptCell.editText.getText().toString().trim();
        if (TextUtils.isEmpty(prompt)) return;
        loading = true;
        button.setLoading(true);

        final TLRPC.TL_messages_composeRichMessageWithAI req = new TLRPC.TL_messages_composeRichMessageWithAI();
        final TL_aicompose.inputAiComposeToneSingleUse tone = new TL_aicompose.inputAiComposeToneSingleUse();
        tone.custom_prompt = prompt;
        req.tone = tone;
        reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            loading = false;
            reqId = 0;
            button.setLoading(false);
            if (res instanceof TLRPC.TL_composedRichMessageWithAI) {
                showResult(((TLRPC.TL_composedRichMessageWithAI) res).result);
            } else {
                AndroidUtilities.shakeViewSpring(button, 4);
            }
        }));

        AndroidUtilities.hideKeyboard(promptCell.editText);
    }

    private void showResult(TL_iv.RichMessage richMessage) {
        if (richMessage == null) {
            AndroidUtilities.shakeViewSpring(button, 4);
            return;
        }
        result = richMessage;
        previewView.set(richMessage);
        button.setText(getString(R.string.ArticleAIAddToPage), true);
        updateButtonEnabled();
        if (adapter != null) adapter.update(true);
    }

    private void resetResult() {
        if (result == null) return;
        result = null;
        button.setText(getString(R.string.ArticleAIGenerate), true);
        if (adapter != null) adapter.update(true);
    }

    @Override
    public void dismiss() {
        if (reqId != 0) {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(reqId, true);
            reqId = 0;
        }
        AndroidUtilities.hideKeyboard(promptCell.editText);
        super.dismiss();
    }
}
