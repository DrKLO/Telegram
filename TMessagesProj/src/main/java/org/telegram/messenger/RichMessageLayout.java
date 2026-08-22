package org.telegram.messenger;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.tgnet.TLObject.hasFlag;
import static org.telegram.tgnet.TLObject.setFlag;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.app.Activity;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.Xfermode;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.ClickableSpan;
import android.text.style.MetricAffectingSpan;
import android.text.style.ReplacementSpan;
import android.text.style.URLSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.OverScroller;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.utils.DrawableUtils;
import org.telegram.messenger.utils.tlutils.TLKeyboardHelper;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_iv;
import org.telegram.tgnet.tl.TL_keyboard;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ArticleViewer;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Cells.TextSelectionHelper;
import org.telegram.ui.Components.AnimatedArrowDrawable;
import org.telegram.ui.Components.AnimatedFileDrawable;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.ButtonBounce;
import org.telegram.ui.Components.CheckBoxBase;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.ForegroundColorSpanThemable;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LoadingDrawable;
import org.telegram.ui.Components.Premium.PremiumFeatureBottomSheet;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ReplyMessageLine;
import org.telegram.ui.Components.TornEdge;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.URLSpanBotCommand;
import org.telegram.ui.Components.URLSpanMono;
import org.telegram.ui.Components.URLSpanUserMention;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.Components.UnsupportedBlockDrawable;
import org.telegram.ui.GradientClip;
import org.telegram.ui.LinkManager;
import org.telegram.ui.MultiLayoutTypingAnimator;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.FormattedDateSpan;
import org.telegram.ui.Components.LinkPath;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.MediaActionDrawable;
import org.telegram.ui.Components.RadialProgress2;
import org.telegram.ui.Components.SeekBar;
import org.telegram.ui.Components.SquigglyLinesSpan;
import org.telegram.ui.Components.TableLayout;
import org.telegram.ui.Components.TextPaintImageReceiverSpan;
import org.telegram.ui.Components.TextStyleSpan;
import org.telegram.ui.Components.TypefaceSpan;
import org.telegram.ui.Components.URLSpanNoUnderline;
import org.telegram.ui.Components.URLSpanReplacement;
import org.telegram.ui.Components.spoilers.SpoilerEffect;
import org.telegram.ui.Components.spoilers.SpoilerEffect2;
import org.telegram.ui.iv.RichHtml;
import org.telegram.ui.iv.RichTextStyle;
import org.telegram.ui.iv.Latex;
import org.telegram.ui.PremiumPreviewFragment;
import org.telegram.ui.web.WebInstantView;

import java.io.File;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicReference;
import me.vkryl.android.util.ClickHelper;
import me.vkryl.core.BitwiseUtils;

public class RichMessageLayout {

    public final int currentAccount;
    public final MessageObject messageObject;
    public TL_iv.RichMessage richMessage;
    public boolean isPart;

    public static final int PART_MAX_HEIGHT_DP = 900;
    public static final int QUOTE_NEST_VPAD = 3;
    private static final int ORDERED_LIST_MARKER_WIDTH_DP = 28;
    private static final int ORDERED_LIST_MARKER_START_DP = 6;

    public final ArrayList<RichUnsupportedBlock> unsupportedBlocks = new ArrayList<>();
    public final ArrayList<RichBlock> blocks = new ArrayList<>();
    public final ArrayList<QuoteBackground> quotes = new ArrayList<>();
    private Drawable pullquoteIcon;
    public final HashMap<String, Integer> anchors = new HashMap<>();
    public final HashMap<String, TL_iv.textAnchor> textAnchors = new HashMap<>();

    public final ArrayList<MessageObject> audioMessages = new ArrayList<>();
    public final HashMap<TL_iv.pageBlockAudio, MessageObject> audioBlocks = new HashMap<>();

    public final ArrayList<TextSelectionHelper.TextLayoutBlock> textBlocks = new ArrayList<>();
    public final ArrayList<Integer> textBlockCharOffsets = new ArrayList<>();
    public final ArrayList<Integer> textBlockBlockIndex = new ArrayList<>();
    public CharSequence joinedText = "";

    public MultiLayoutTypingAnimator typingAnimator;

    public boolean detailsAnimating;
    private float detailsAnimationProgress = 1f;
    public boolean blockquoteAnimating;

    public boolean invalidateAnimatedEmojiInParent;

    public final TextPaint textPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    public final TextPaint numTextPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    public final ReplyMessageLine quoteLine = new ReplyMessageLine(null);
    public final GradientClip clip = new GradientClip();

    protected int height;
    protected int maxWidth;
    protected int minWidth;
    protected Theme.ResourcesProvider resourcesProvider;
    private ChatMessageCell cell;
    private ChatMessageCell.ChatMessageCellDelegate delegate;

    private ButtonBounce showMoreBounce;
    private Paint showMorePaint;
    private org.telegram.ui.Components.Text showMoreText;
    private LoadingDrawable showMoreLoading;
    private final RectF showMoreRect = new RectF();
    private boolean showMorePressed;

    private int fontSize;
    private float density;

    public boolean isRtl() { return richMessage != null && richMessage.rtl; }
    public boolean isOut() { return messageObject != null && messageObject.isOutOwner(); }
    public boolean forceTranslationLoading;
    public boolean isTranslating() { return forceTranslationLoading || (messageObject != null && MessagesController.getInstance(currentAccount).getTranslateController().isTranslating(messageObject)); }

    public boolean isPinnedTop() { return cell != null && cell.isPinnedTop(); }
    public boolean hasNameOffset() { return cell != null && cell.namesOffset > 0; }

    public RichMessageLayout(MessageObject messageObject, int maxWidth, RichMessageLayout prev) {
        this.messageObject = messageObject;
        this.maxWidth = maxWidth;
        this.currentAccount = messageObject.currentAccount;
        layout(prev);
    }

    private RichMessageLayout(int currentAccount, int maxWidth, Theme.ResourcesProvider resourcesProvider) {
        this.messageObject = null;
        this.currentAccount = currentAccount;
        this.maxWidth = maxWidth;
        this.resourcesProvider = resourcesProvider;
        fontSize = SharedConfig.fontSize;
        density = AndroidUtilities.density;
        textPaint.setTextSize(dp(fontSize));
        numTextPaint.setTextSize(dp(fontSize));
    }

    /** Uses the message renderer for editable inline buttons without laying out a message. */
    public static RichButtonSpan createEditorButtonSpan(int currentAccount, int maxWidth,
                                                        Theme.ResourcesProvider resourcesProvider,
                                                        TL_iv.textButton textButton) {
        return new RichButtonSpan(
            new RichMessageLayout(currentAccount, maxWidth, resourcesProvider),
            maxWidth,
            textButton,
            false
        );
    }

    /** Uses the page-button renderer with incoming theme colors inside the rich editor. */
    public static RichButton createEditorPageButton(int currentAccount, int maxWidth,
                                                    Theme.ResourcesProvider resourcesProvider,
                                                    TL_keyboard.PageButton pageButton,
                                                    Runnable invalidateRunnable) {
        final RichMessageLayout layout = new RichMessageLayout(currentAccount, maxWidth, resourcesProvider);
        return new RichButton(
            layout,
            maxWidth,
            layout.formatText(pageButton.text, setBlockFlags(TEXT_FLAG_BOLD, TEXT_FLAG_BLOCK_BUTTON)),
            pageButton,
            pageButton.type,
            pageButton.style,
            pageButton.type instanceof TL_keyboard.TL_inlineButtonTypeDisabled,
            true,
            false,
            false,
            false,
            false,
            false,
            invalidateRunnable
        );
    }

    public boolean needsUpdate(TL_iv.RichMessage newRichMessage, int maxWidth) {
        return (
            richMessage != newRichMessage ||
            fontSize != SharedConfig.fontSize ||
            Math.abs(density - AndroidUtilities.density) > 0.1f ||
            maxWidth != this.maxWidth
        );
    }

    public void setResourcesProvider(Theme.ResourcesProvider resourcesProvider) {
        this.resourcesProvider = resourcesProvider;
    }

    public void checkQuoteLine(TLRPC.User currentUser, TLRPC.Chat currentChat) {
        quoteLine.check(messageObject, currentUser, currentChat, resourcesProvider, ReplyMessageLine.TYPE_QUOTE);
        if (messageObject == null || messageObject.isOutOwner() || messageObject.shouldDrawWithoutBackground()
                || hasCustomIncomingQuoteColor(currentUser, currentChat)) {
            return;
        }
        final boolean dark = resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark();
        quoteLine.setSimpleColor(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider), dark);
    }

    private boolean hasCustomIncomingQuoteColor(TLRPC.User currentUser, TLRPC.Chat currentChat) {
        if (messageObject.overrideLinkColor >= 0 || messageObject.overrideLinkPeerColor != null) {
            return true;
        }
        if (messageObject.isSponsored() && messageObject.sponsoredColor != null
                && messageObject.sponsoredColor.color != -1) {
            return true;
        }
        if (messageObject.messageOwner != null && messageObject.messageOwner.fwd_from != null
                && messageObject.messageOwner.fwd_from.from_id != null) {
            final long dialogId = DialogObject.getPeerDialogId(messageObject.messageOwner.fwd_from.from_id);
            if (dialogId < 0) {
                final TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
                return chat != null && hasCustomPeerColor(chat.color);
            } else {
                final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
                return user != null && hasCustomPeerColor(user.color);
            }
        }
        if (DialogObject.isEncryptedDialog(messageObject.getDialogId()) || messageObject.isFromUser()) {
            return currentUser != null && hasCustomPeerColor(currentUser.color);
        }
        if (messageObject.isFromChannel() && currentChat != null) {
            if (currentChat.signature_profiles) {
                final long dialogId = messageObject.getFromChatId();
                if (dialogId >= 0) {
                    final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
                    return user != null && hasCustomPeerColor(user.color);
                } else {
                    final TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
                    return chat != null && hasCustomPeerColor(chat.color);
                }
            }
            return hasCustomPeerColor(currentChat.color);
        }
        return false;
    }

    private static boolean hasCustomPeerColor(TLRPC.PeerColor color) {
        return color instanceof TLRPC.TL_peerColorCollectible
                || color instanceof TLRPC.TL_peerColor && (color.flags & 1) != 0;
    }

    public void setChatMessageCellDelegate(ChatMessageCell cell, ChatMessageCell.ChatMessageCellDelegate delegate) {
        this.cell = cell;
        this.delegate = delegate;
    }

    public int getGap() {
        return 0;
    }

    public boolean startsWithMedia() {
        if (blocks.isEmpty()) return false;
        final RichBlock first = blocks.get(0);
        return first instanceof RichPhotoBlock || first instanceof RichVideoBlock || first instanceof RichCollageBlock || first instanceof RichSlideshowBlock;
    }

    private RichMessageLayout prev;
    public void layout(RichMessageLayout prev) {
        height = 0;
        minWidth = 0;
        final View attachedView = this.view;
        if (attachedView != null) {
            for (int i = 0; i < blocks.size(); ++i) {
                blocks.get(i).detach(attachedView);
            }
        }

        unsupportedBlocks.clear();
        blocks.clear();
        quotes.clear();
        anchors.clear();
        textAnchors.clear();
        audioMessages.clear();
        audioBlocks.clear();
        textBlocks.clear();
        textBlockCharOffsets.clear();
        textBlockBlockIndex.clear();
        joinedText = "";
        fontSize = SharedConfig.fontSize;
        density = AndroidUtilities.density;
        textPaint.setTextSize(dp(SharedConfig.fontSize));
        numTextPaint.setTextSize(dp(SharedConfig.fontSize));
        isPart = false;

        richMessage = null;
        if (messageObject == null || messageObject.messageOwner == null || messageObject.getDisplayRichMessage() == null) return;
        richMessage = messageObject.getDisplayRichMessage();
        isPart = richMessage.part;

        this.prev = prev;
        for (int i = 0; i < richMessage.blocks.size(); ++i) {
            final TL_iv.PageBlock pageBlock = richMessage.blocks.get(i);
            final RichBlock block = emitBlock(pageBlock, 0, new Rect(), 0, previousBlockIsParagraph(richMessage.blocks, i));
            if (block instanceof RichTextBlock && (pageBlock instanceof TL_iv.pageBlockParagraph || ArticleViewer.isHeadingBlock(pageBlock))) {
                final RichTextBlock textBlock = (RichTextBlock) block;
                textBlock.setContentPadding(
                    i == 0 && pageBlock instanceof TL_iv.pageBlockParagraph ? 0 : textBlock.contentPaddingTop,
                    i == richMessage.blocks.size() - 1 ? 0 : textBlock.contentPaddingBottom
                );
            }
        }
        applyListPaddingFromBlocks();
        this.prev = null;

        if (typingAnimator != null) {
            for (int i = 0; i < blocks.size(); ++i) {
                blocks.get(i).typingAnimator = typingAnimator;
            }
            typingAnimator.setBlocks(getAnimatorBlocks());
        }

        if (attachedView != null) {
            for (int i = 0; i < blocks.size(); ++i) {
                blocks.get(i).attach(attachedView);
            }
        }

        reposition();
        snapshotForDetailsAnimation();
    }

    private boolean prefixEquals(String oldText, String newText) {
        if (oldText == null || newText == null) return false;
        if (oldText.length() > newText.length()) return false;
        if (newText.length() <= 0) return false;
        return newText.startsWith(oldText);
    }

    private <T extends RichBlock> T findPrevBlock(TL_iv.PageBlock pageBlock, Class<T> clazz) {
        if (prev == null) return null;
        for (final RichBlock block : prev.blocks) {
            if (!clazz.isInstance(block)) continue;
            if (block instanceof RichPreformattedBlock) {
                if (prefixEquals(((RichPreformattedBlock) block).plain, getString(pageBlock.text)))
                    return clazz.cast(block);
            }
        }
        return null;
    }

    public void reposition() {
        height = 0;
        minWidth = 0;
        textBlocks.clear();
        textBlockCharOffsets.clear();
        textBlockBlockIndex.clear();

        for (int i = 0; i < blocks.size(); ++i) {
            minWidth = Math.max(minWidth, blocks.get(i).getMinWidth());
        }
        for (int i = 0; i < blocks.size(); ++i) {
            final RichBlock block = blocks.get(i);
            if (block instanceof RichTableBlock) {
                ((RichTableBlock) block).resolveWidth(minWidth);
            }
        }

        final StringBuilder joined = new StringBuilder();
        int y = 0;
        boolean lastVisible = false;
        for (int i = 0; i < blocks.size(); ++i) {
            final RichBlock block = blocks.get(i);

            final boolean visible = block.isVisible();
            if (visible && lastVisible) y += getGap();

            block.currY = y;
            block.currH = 0;
            block.currVisible = visible;

            block.placeTexts(block.padding.left, y + block.padding.top, i);

            if (visible) {
                final TextSelectionHelper.TextLayoutBlock[] childTextBlocks = block.getText();
                if (childTextBlocks != null) {
                    for (TextSelectionHelper.TextLayoutBlock tb : childTextBlocks) {
                        if (tb == null || tb.getLayout() == null) continue;
                        if (joined.length() > 0) {
                            joined.append('\n');
                        }
                        textBlockCharOffsets.add(joined.length());
                        textBlockBlockIndex.add(i);
                        textBlocks.add(tb);
                        final CharSequence t = tb.getLayout().getText();
                        if (t != null) joined.append(t);
                    }
                }

                final int height = block.getHeight();
                block.currH = height;
                y += height;
                lastVisible = true;
            }
        }
        height = y;
        joinedText = joined;
    }

    public String getSelectionHtml(int selStart, int selEnd) {
        if (textBlocks.isEmpty()) return null;
        final int s = Math.min(selStart, selEnd);
        final int e = Math.max(selStart, selEnd);
        if (e <= s) return null;
        final StringBuilder out = new StringBuilder();
        final ArrayList<QuoteBackground> openQuotes = new ArrayList<>();
        final ArrayList<Boolean> openLists = new ArrayList<>();
        int emittedTableBlock = -1;
        for (int i = 0; i < textBlocks.size(); i++) {
            final Layout layout = textBlocks.get(i).getLayout();
            if (layout == null || layout.getText() == null) continue;
            final CharSequence text = layout.getText();
            final int blockStart = i < textBlockCharOffsets.size() ? textBlockCharOffsets.get(i) : 0;
            final int blockEnd = blockStart + text.length();
            final int a = Math.max(s, blockStart);
            final int b = Math.min(e, blockEnd);
            if (b <= a) continue;
            final int blockIndex = i < textBlockBlockIndex.size() ? textBlockBlockIndex.get(i) : -1;
            final RichBlock rb = blockIndex >= 0 && blockIndex < blocks.size() ? blocks.get(blockIndex) : null;

            if (rb instanceof RichTableBlock) {
                if (blockIndex == emittedTableBlock) continue;
                emittedTableBlock = blockIndex;
                closeLists(out, openLists);
                syncQuotes(out, openQuotes, quotesFor(blockIndex));
                out.append(RichHtml.tableToHtml(((RichTableBlock) rb).pageBlock));
                continue;
            }

            final ArrayList<QuoteBackground> wantQuotes = quotesFor(blockIndex);
            if (!sameQuotes(openQuotes, wantQuotes)) {
                closeLists(out, openLists);
                syncQuotes(out, openQuotes, wantQuotes);
            }

            final int la = a - blockStart, lb = b - blockStart;
            final int authorStart = rb instanceof RichTextBlock ? ((RichTextBlock) rb).quoteAuthorStart : -1;
            final boolean separateAuthor = rb instanceof RichTextWithAuthorBlock
                && ((RichTextWithAuthorBlock) rb).author == textBlocks.get(i);
            final int listLevel = rb != null ? rb.listLevel : 0;

            if (listLevel > 0 && authorStart < 0) {
                syncLists(out, openLists, listLevel, rb.listOrdered);
                out.append("<li");
                if (rb.listCheckbox) {
                    out.append(" data-checkbox=\"1\"");
                    if (rb.listChecked) out.append(" data-checked=\"1\"");
                }
                out.append('>');
                out.append(RichHtml.inlineToHtml(toRichHtmlSpannable(text.subSequence(la, lb))));
                out.append("</li>");
                continue;
            }

            closeLists(out, openLists);
            if (rb instanceof RichPreformattedBlock) {
                out.append(RichHtml.preToHtml(toRichHtmlSpannable(text.subSequence(la, lb)), ((RichPreformattedBlock) rb).language));
            } else if (separateAuthor) {
                appendSelectionPiece(out, text, la, lb, true);
            } else if (authorStart < 0) {
                appendSelectionPiece(out, text, la, lb, false);
            } else {
                appendSelectionPiece(out, text, la, Math.min(lb, authorStart > 0 ? authorStart - 1 : 0), false);
                appendSelectionPiece(out, text, Math.max(la, authorStart), lb, true);
            }
        }
        closeLists(out, openLists);
        while (!openQuotes.isEmpty()) { out.append("</blockquote>"); openQuotes.remove(openQuotes.size() - 1); }
        return out.length() == 0 ? null : out.toString();
    }

    private void appendSelectionPiece(StringBuilder out, CharSequence text, int from, int to, boolean author) {
        if (to <= from) return;
        final String inner = RichHtml.inlineToHtml(toRichHtmlSpannable(text.subSequence(from, to)));
        if (inner.isEmpty()) return;
        out.append(author ? "<cite>" : "<p>").append(inner).append(author ? "</cite>" : "</p>");
    }

    private ArrayList<QuoteBackground> quotesFor(int blockIndex) {
        final ArrayList<QuoteBackground> want = new ArrayList<>();
        if (blockIndex >= 0) {
            for (QuoteBackground q : quotes) {
                if (blockIndex >= q.startBlockIndex && blockIndex <= q.endBlockIndex) want.add(q);
            }
            java.util.Collections.sort(want, (x, y) -> x.level - y.level);
        }
        return want;
    }

    private static boolean sameQuotes(ArrayList<QuoteBackground> a, ArrayList<QuoteBackground> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (a.get(i) != b.get(i)) return false;
        return true;
    }

    private void syncQuotes(StringBuilder out, ArrayList<QuoteBackground> open, ArrayList<QuoteBackground> want) {
        int common = 0;
        while (common < open.size() && common < want.size() && open.get(common) == want.get(common)) common++;
        while (open.size() > common) { out.append("</blockquote>"); open.remove(open.size() - 1); }
        while (open.size() < want.size()) { out.append("<blockquote>"); open.add(want.get(open.size())); }
    }

    private void syncLists(StringBuilder out, ArrayList<Boolean> open, int level, boolean ordered) {
        while (open.size() > level) { out.append(open.remove(open.size() - 1) ? "</ol>" : "</ul>"); }
        while (open.size() < level) { out.append(ordered ? "<ol>" : "<ul>"); open.add(ordered); }
        if (!open.isEmpty() && open.get(open.size() - 1) != ordered) {
            out.append(open.remove(open.size() - 1) ? "</ol>" : "</ul>");
            out.append(ordered ? "<ol>" : "<ul>");
            open.add(ordered);
        }
    }

    private void closeLists(StringBuilder out, ArrayList<Boolean> open) {
        while (!open.isEmpty()) { out.append(open.remove(open.size() - 1) ? "</ol>" : "</ul>"); }
    }

    private SpannableStringBuilder toRichHtmlSpannable(CharSequence cs) {
        final SpannableStringBuilder sb = new SpannableStringBuilder(cs);
        final StyleSpan[] spans = sb.getSpans(0, sb.length(), StyleSpan.class);
        for (StyleSpan span : spans) {
            final int st = sb.getSpanStart(span), en = sb.getSpanEnd(span);
            if (en <= st) continue;
            final int f = toTextStyleFlags(span.flags);
            if (f == 0) continue;
            final TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
            run.flags = f;
            sb.setSpan(new TextStyleSpan(run), st, en, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return sb;
    }

    private static int toTextStyleFlags(int f) {
        int r = 0;
        if ((f & TEXT_FLAG_BOLD) != 0) r |= TextStyleSpan.FLAG_STYLE_BOLD;
        if ((f & TEXT_FLAG_ITALIC) != 0) r |= TextStyleSpan.FLAG_STYLE_ITALIC;
        if ((f & TEXT_FLAG_UNDERLINE) != 0) r |= TextStyleSpan.FLAG_STYLE_UNDERLINE;
        if ((f & TEXT_FLAG_STRIKETHROUGH) != 0) r |= TextStyleSpan.FLAG_STYLE_STRIKE;
        if ((f & TEXT_FLAG_MONO) != 0) r |= TextStyleSpan.FLAG_STYLE_MONO;
        if ((f & TEXT_FLAG_SUBSCRIPT) != 0) r |= TextStyleSpan.FLAG_STYLE_SUBSCRIPT;
        if ((f & TEXT_FLAG_SUPERSCRIPT) != 0) r |= TextStyleSpan.FLAG_STYLE_SUPERSCRIPT;
        if ((f & TEXT_FLAG_MARKED) != 0) r |= TextStyleSpan.FLAG_STYLE_MARKED;
        return r;
    }

    public void snapshotForDetailsAnimation() {
        for (int i = 0; i < blocks.size(); ++i) {
            blocks.get(i).snapshot();
        }
    }

    public void snapshotForBlockquoteAnimation() {
        snapshotForDetailsAnimation();
    }


    public View view;
    public void attach(View view) {
        if (view == this.view) return;
        if (this.view != null) {
            detach(this.view);
        }
        this.view = view;
        for (int i = 0; i < blocks.size(); ++i)
            blocks.get(i).attach(view);
    }
    public void detach(View view) {
        if (this.view != view) return;
        if (this.view == null) return;
        if (spoilerEffect2 != null) {
            spoilerEffect2.detach(view);
            spoilerEffect2 = null;
        }
        this.view = null;
        for (int i = 0; i < blocks.size(); ++i)
            blocks.get(i).detach(view);
        if (view == cell) {
            cell = null;
            delegate = null;
        }
    }
    public boolean isAttached() {
        return this.view != null;
    }

    private SpoilerEffect2 spoilerEffect2;

    public SpoilerEffect2 getMediaSpoilerEffect() {
        if (view == null || !SpoilerEffect2.supports()) return null;
        if (spoilerEffect2 != null && spoilerEffect2.destroyed) spoilerEffect2 = null;
        if (spoilerEffect2 == null) spoilerEffect2 = SpoilerEffect2.getInstance(view);
        return spoilerEffect2;
    }

    public static class QuoteBackground {
        int startBlockIndex;
        int endBlockIndex;
        int padding;
        int level;
        int outerTopVpad;
        int outerBottomVpad;

        public QuoteBackground(int startBlockIndex, int endBlockIndex, int padding, int level) {
            this(startBlockIndex, endBlockIndex, padding, level, 0, 0);
        }

        public QuoteBackground(int startBlockIndex, int endBlockIndex, int padding, int level, int outerVpad) {
            this(startBlockIndex, endBlockIndex, padding, level, outerVpad, outerVpad);
        }

        public QuoteBackground(int startBlockIndex, int endBlockIndex, int padding, int level, int outerTopVpad, int outerBottomVpad) {
            this.startBlockIndex = startBlockIndex;
            this.endBlockIndex = endBlockIndex;
            this.padding = padding;
            this.level = level;
            this.outerTopVpad = outerTopVpad;
            this.outerBottomVpad = outerBottomVpad;
        }
    }

    public TLRPC.Photo getPhoto(long photoId) {
        if (richMessage == null) return null;
        for (final TLRPC.Photo photo : richMessage.photos)
            if (photo.id == photoId)
                return photo;
        return null;
    }
    public TLRPC.Document getDocument(long documentId) {
        if (richMessage == null) return null;
        for (final TLRPC.Document document : richMessage.documents)
            if (document.id == documentId)
                return document;
        return null;
    }

    public void collectMediaBlocks(List<TL_iv.PageBlock> out) {
        for (int i = 0; i < blocks.size(); ++i) {
            final RichBlock block = blocks.get(i);
            if (block instanceof RichPhotoBlock) {
                out.add(((RichPhotoBlock) block).block);
            } else if (block instanceof RichVideoBlock) {
                out.add(((RichVideoBlock) block).block);
            } else if (block instanceof RichCollageBlock) {
                for (MediaCell c : ((RichCollageBlock) block).cells) out.add(c.pageBlock);
            } else if (block instanceof RichSlideshowBlock) {
                for (MediaCell c : ((RichSlideshowBlock) block).cells) out.add(c.pageBlock);
            }
        }
    }

    public boolean setSlideshowPage(TL_iv.PageBlock target) {
        if (target == null) return false;
        for (int i = 0; i < blocks.size(); ++i) {
            final RichBlock block = blocks.get(i);
            if (!(block instanceof RichSlideshowBlock)) continue;
            final RichSlideshowBlock slideshow = (RichSlideshowBlock) block;
            for (int page = 0; page < slideshow.cells.size(); ++page) {
                if (slideshow.cells.get(page).pageBlock == target) {
                    slideshow.setCurrentPage(page);
                    return true;
                }
            }
        }
        return false;
    }

    public ImageReceiver findMediaImageReceiver(TL_iv.PageBlock target, int[] outOffset) {
        for (int i = 0; i < blocks.size(); ++i) {
            final RichBlock block = blocks.get(i);
            TL_iv.PageBlock blockData = null;
            ImageReceiver ir = null;
            int extraX = 0, extraY = 0;
            if (block instanceof RichPhotoBlock) {
                blockData = ((RichPhotoBlock) block).block;
                ir = ((RichPhotoBlock) block).imageReceiver;
            } else if (block instanceof RichVideoBlock) {
                blockData = ((RichVideoBlock) block).block;
                ir = ((RichVideoBlock) block).imageReceiver;
            } else if (block instanceof RichCollageBlock) {
                final RichCollageBlock cb = (RichCollageBlock) block;
                for (MediaCell c : cb.cells) {
                    if (c.pageBlock == target) {
                        if (outOffset != null && outOffset.length >= 2) {
                            outOffset[0] = block.padding.left;
                            outOffset[1] = block.layoutY + block.padding.top;
                        }
                        return c.imageReceiver;
                    }
                }
                continue;
            } else if (block instanceof RichSlideshowBlock) {
                final RichSlideshowBlock sb = (RichSlideshowBlock) block;
                final int cp = sb.getCurrentPage();
                if (cp >= 0 && cp < sb.cells.size() && sb.cells.get(cp).pageBlock == target) {
                    if (outOffset != null && outOffset.length >= 2) {
                        outOffset[0] = block.padding.left;
                        outOffset[1] = block.layoutY + block.padding.top;
                    }
                    return sb.cells.get(cp).imageReceiver;
                }
                continue;
            }
            if (blockData == target && ir != null) {
                if (outOffset != null && outOffset.length >= 2) {
                    outOffset[0] = block.padding.left + extraX;
                    outOffset[1] = block.layoutY + block.padding.top + extraY;
                }
                return ir;
            }
        }
        return null;
    }

    private static void markListItem(RichBlock block, int level, boolean ordered, boolean checkbox, boolean checked) {
        if (block == null) return;
        block.listLevel = level;
        block.listOrdered = ordered;
        block.listCheckbox = checkbox;
        block.listChecked = checked;
    }

    public static int setBlockFlags(int textFlags, int blockFlags) {
        if (blockFlags == 0) return textFlags;
        return (textFlags &~ TEXT_FLAG_BLOCKS) | blockFlags;
    }

    private void emitCaption(TL_iv.PageCaption caption, Rect padding, int textFlags) {
        if (caption == null) return;
        final boolean hasText = caption.text != null && !(caption.text instanceof TL_iv.textEmpty);
        final boolean hasCredit = caption.credit != null && !(caption.credit instanceof TL_iv.textEmpty);
        if (!hasText && !hasCredit) return;
        final int flags = setBlockFlags(textFlags, TEXT_FLAG_BLOCK_CAPTION);
        final CharSequence text = hasText ? formatText(caption.text, flags) : null;
        final CharSequence credit = hasCredit ? formatText(caption.credit, flags) : null;
        blocks.add(new RichCaptionBlock(this, padding, maxWidth, text, credit));
    }

    private static boolean previousBlockIsParagraph(List<TL_iv.PageBlock> blocks, int index) {
        return index > 0 && blocks.get(index - 1) instanceof TL_iv.pageBlockParagraph;
    }

    private RichBlock emitBlock(TL_iv.PageBlock pageBlock, int level, Rect padding, int textFlags, boolean previousSiblingParagraph) {
        if (padding.left + padding.right >= maxWidth) return null;
        if (pageBlock instanceof TL_iv.pageBlockThinking) {
            final RichThinkingBlock block = new RichThinkingBlock(this, new Rect(), maxWidth, formatText(pageBlock.text));
            blocks.add(block);
            return block;
        } else if (
            ArticleViewer.isHeadingBlock(pageBlock) ||
            pageBlock instanceof TL_iv.pageBlockFooter ||
            pageBlock instanceof TL_iv.pageBlockParagraph
        ) {
            final boolean isHeading = ArticleViewer.isHeadingBlock(pageBlock);
            final int flags = setBlockFlags(textFlags, getBlockTextFlag(pageBlock));
            final CharSequence text = formatText(pageBlock.text, flags);
            final RichTextBlock block = new RichTextBlock(this, padding, maxWidth, text);
            if (isHeading) {
                block.setContentPadding(dp(10), dp(6));
            } else if (pageBlock instanceof TL_iv.pageBlockParagraph) {
                block.setContentPadding(previousSiblingParagraph ? 0 : dp(5), dp(4.66f));
            }
            block.accessibilityLabelResId = getBlockAccessibilityLabel(pageBlock);
            blocks.add(block);
            return block;
        } else if (pageBlock instanceof TL_iv.pageBlockPreformatted) {
            final RichBlock block = new RichPreformattedBlock(this, padding, maxWidth, (TL_iv.pageBlockPreformatted) pageBlock, findPrevBlock(pageBlock, RichPreformattedBlock.class));
            block.accessibilityLabelResId = R.string.ArticleCode;
            blocks.add(block);
            return block;
        } else if (pageBlock instanceof TL_iv.pageBlockList) {
            final TL_iv.pageBlockList list = (TL_iv.pageBlockList) pageBlock;

            level++;
            numTextPaint.setTextSize(dp(SharedConfig.fontSize));
            int maxNumWidth = dp(18);
            for (int i = 0; i < list.items.size(); ++i) {
                final TL_iv.PageListItem item = list.items.get(i);
                int numWidth;
                if (item.checkbox) {
                    numWidth = dp(26);
                } else {
                    numWidth = dp(18);
                }
                maxNumWidth = Math.max(maxNumWidth, numWidth);
            }
            final Rect listPadding = new Rect(padding);
            if (isRtl()) {
                listPadding.right += maxNumWidth;
            } else {
                listPadding.left += maxNumWidth;
            }

            for (int i = 0; i < list.items.size(); ++i) {
                final TL_iv.PageListItem item = list.items.get(i);
                if (item instanceof TL_iv.TL_pageListItemText) {
                    final TL_iv.TL_pageListItemText itemText = (TL_iv.TL_pageListItemText) item;

                    final RichBlock block = new RichTextBlock(this, new Rect(listPadding), maxWidth, formatText(itemText.text, textFlags));
                    block.setListMarkerWidth(maxNumWidth);
                    if (itemText.checkbox) {
                        block.setCheckbox(itemText.checked, itemText);
                    } else {
                        block.setNum("•◦▪".charAt((level - 1) % 3) + "");
                    }
                    markListItem(block, level, false, itemText.checkbox, itemText.checked);
                    blocks.add(block);
                } else if (item instanceof TL_iv.TL_pageListItemBlocks) {
                    final TL_iv.TL_pageListItemBlocks itemBlocks = (TL_iv.TL_pageListItemBlocks) item;
                    if (itemBlocks.blocks.isEmpty()) continue;

                    boolean gotFirstBlock = false;
                    final int itemStart = blocks.size();
                    for (int j = 0; j < itemBlocks.blocks.size(); ++j) {
                        final RichBlock block = emitBlock(itemBlocks.blocks.get(j), level, new Rect(listPadding), textFlags, previousBlockIsParagraph(itemBlocks.blocks, j));
                        if (block != null && !gotFirstBlock) {
                            block.setListMarkerWidth(maxNumWidth);
                            if (itemBlocks.checkbox) {
                                block.setCheckbox(itemBlocks.checked, itemBlocks);
                            } else {
                                block.setNum("•◦▪".charAt((level - 1) % 3) + "");
                            }
                            markListItem(block, level, false, itemBlocks.checkbox, itemBlocks.checked);
                            gotFirstBlock = true;
                        }
                    }
                    markListMembership(itemStart, blocks.size(), level, false);
                }
            }
            return null;
        } else if (pageBlock instanceof TL_iv.pageBlockOrderedList) {
            final TL_iv.pageBlockOrderedList list = (TL_iv.pageBlockOrderedList) pageBlock;

            level++;
            numTextPaint.setTextSize(dp(SharedConfig.fontSize));
            final TextPaint markerMeasurePaint = new TextPaint(numTextPaint);
            markerMeasurePaint.setTypeface(AndroidUtilities.bold());
            int maxNumWidth = dp(ORDERED_LIST_MARKER_WIDTH_DP);
            for (int i = 0; i < list.items.size(); i++) {
                final TL_iv.PageListOrderedItem item = list.items.get(i);
                final String marker = orderedListMarker(list, item, i);
                final int checkboxWidth = item.checkbox ? dp(26) : 0;
                maxNumWidth = Math.max(maxNumWidth, (int) Math.ceil(markerMeasurePaint.measureText(marker))
                    + dp(ORDERED_LIST_MARKER_START_DP + 4) + checkboxWidth);
            }
            final Rect listPadding = new Rect(padding);
            if (isRtl()) {
                listPadding.right += maxNumWidth;
            } else {
                listPadding.left += maxNumWidth;
            }

            for (int i = 0; i < list.items.size(); ++i) {
                final TL_iv.PageListOrderedItem item = list.items.get(i);
                if (item instanceof TL_iv.TL_pageListOrderedItemText) {
                    final TL_iv.TL_pageListOrderedItemText itemText = (TL_iv.TL_pageListOrderedItemText) item;

                    final RichBlock block = new RichTextBlock(this, new Rect(listPadding), maxWidth, formatText(itemText.text, textFlags));
                    block.setListMarkerWidth(maxNumWidth);
                    block.setNum(orderedListMarker(list, itemText, i));
                    if (itemText.checkbox) {
                        block.setCheckbox(itemText.checked, itemText);
                    }
                    markListItem(block, level, true, itemText.checkbox, itemText.checked);
                    blocks.add(block);
                } else if (item instanceof TL_iv.TL_pageListOrderedItemBlocks) {
                    final TL_iv.TL_pageListOrderedItemBlocks itemBlocks = (TL_iv.TL_pageListOrderedItemBlocks) item;
                    if (itemBlocks.blocks.isEmpty()) continue;

                    boolean gotFirstBlock = false;
                    final int itemStart = blocks.size();
                    for (int j = 0; j < itemBlocks.blocks.size(); ++j) {
                        final RichBlock block = emitBlock(itemBlocks.blocks.get(j), level, new Rect(listPadding), textFlags, previousBlockIsParagraph(itemBlocks.blocks, j));
                        if (block != null && !gotFirstBlock) {
                            block.setListMarkerWidth(maxNumWidth);
                            if (itemBlocks.checkbox) {
                                block.setCheckbox(itemBlocks.checked, itemBlocks);
                            }
                            block.setNum(orderedListMarker(list, itemBlocks, i));
                            if (itemBlocks.checkbox) {
                                block.setCheckbox(itemBlocks.checked, itemBlocks);
                            }
                            markListItem(block, level, true, itemBlocks.checkbox, itemBlocks.checked);
                            gotFirstBlock = true;
                        }
                    }
                    markListMembership(itemStart, blocks.size(), level, true);
                }
            }
            return null;
        } else if (pageBlock instanceof TL_iv.pageBlockBlockquote) {
            final int quotePadding = padding.left;
            final int quoteLevel = level;
            level++;

            final int startIndex = blocks.size();
            final TL_iv.pageBlockBlockquote blockquote = (TL_iv.pageBlockBlockquote) pageBlock;
            final CharSequence text = formatText(pageBlock.text, setBlockFlags(textFlags, getBlockTextFlag(pageBlock)));
            CharSequence author = null;
            if (blockquote.caption != null && !TextUtils.isEmpty(getString(blockquote.caption))) {
                author = formatText(blockquote.caption, setBlockFlags(textFlags, TEXT_FLAG_BLOCK_QUOTE_CAPTION));
            }
            final RichBlock block;
            if (blockquote.collapsed) {
                final SpannableStringBuilder sb = new SpannableStringBuilder(text);
                int authorStart = -1;
                if (author != null) {
                    sb.append('\n');
                    authorStart = sb.length();
                    sb.append(author);
                }
                final RichTextBlockQuote collapsedBlock = new RichTextBlockQuote(this, new Rect(padding.left + dp(12), padding.top + dp(4), padding.right + dp(20), padding.bottom + dp(4)), maxWidth, blockquote, sb);
                collapsedBlock.quoteAuthorStart = authorStart;
                collapsedBlock.setContentPadding(dp(8), dp(8));
                block = collapsedBlock;
            } else {
                block = new RichQuoteBlock(this, new Rect(padding.left + dp(12), padding.top + dp(12), padding.right + dp(12), padding.bottom + dp(14)), maxWidth, text, author);
            }
            block.accessibilityLabelResId = R.string.ArticleQuote;
            blocks.add(block);
            quotes.add(new QuoteBackground(
                startIndex, blocks.size() - 1, quotePadding, quoteLevel,
                dp(8), blockquote.collapsed ? dp(8) : 0
            ));

            return block;
        } else if (pageBlock instanceof TL_iv.pageBlockBlockquoteBlocks) {
            final int quotePadding = padding.left;
            final int quoteLevel = level;
            level++;

            final int startIndex = blocks.size();
            final TL_iv.pageBlockBlockquoteBlocks quote = (TL_iv.pageBlockBlockquoteBlocks) pageBlock;
            final boolean hasCaption = quote.caption != null && !TextUtils.isEmpty(getString(quote.caption));
            for (int i = 0; i < quote.blocks.size(); ++i) {
                final boolean first = i == 0;
                final boolean last = i == quote.blocks.size() - 1;
                emitBlock(quote.blocks.get(i), level, new Rect(padding.left + dp(12), padding.top + (first ? dp(4) : 0), padding.right + dp(12), padding.bottom + (last && !hasCaption ? dp(4) : 0)), setBlockFlags(textFlags, TEXT_FLAG_BLOCK_QUOTE), previousBlockIsParagraph(quote.blocks, i));
            }
            if (blocks.size() > startIndex) {
                blocks.get(startIndex).accessibilityParentLabelResId = R.string.ArticleQuote;
            }
            if (hasCaption) {
                final CharSequence caption = formatText(quote.caption, setBlockFlags(textFlags, TEXT_FLAG_BLOCK_QUOTE_CAPTION));
                final RichTextBlock captionBlock = new RichTextBlock(this, new Rect(padding.left + dp(12), padding.top, padding.right + dp(12), padding.bottom + dp(6)), maxWidth, new SpannableStringBuilder(caption));
                captionBlock.quoteAuthorStart = 0;
                captionBlock.setContentPadding(dp(2), 0);
                blocks.add(captionBlock);
            }
            quotes.add(new QuoteBackground(startIndex, blocks.size() - 1, quotePadding, quoteLevel));

            return null;
        } else if (pageBlock instanceof TL_iv.pageBlockPullquote) {
            final TL_iv.pageBlockPullquote pullquote = (TL_iv.pageBlockPullquote) pageBlock;

            final CharSequence text = formatText(pageBlock.text, setBlockFlags(textFlags, getBlockTextFlag(pageBlock)));
            CharSequence author = null;
            if (pullquote.caption != null && !TextUtils.isEmpty(getString(pullquote.caption))) {
                author = formatText(pullquote.caption, setBlockFlags(textFlags, TEXT_FLAG_BLOCK_QUOTE_CAPTION));
            }
            final RichPullquoteBlock block = new RichPullquoteBlock(this, new Rect(padding.left + dp(30), padding.top + dp(16), padding.right + dp(30), padding.bottom + dp(16)), maxWidth, text, author);
            block.accessibilityLabelResId = R.string.ArticlePullquote;
            blocks.add(block);

            return block;
        } else if (pageBlock instanceof TL_iv.pageBlockButtonRow) {
            final RichBlock block = new RichButtonRowBlock(this, padding, maxWidth, (TL_iv.pageBlockButtonRow) pageBlock);
            block.accessibilityLabelResId = R.string.AccDescrIVButtons;
            blocks.add(block);
            return block;
        } else if (pageBlock instanceof TL_iv.pageBlockTable) {
            final RichBlock block = new RichTableBlock(this, padding, maxWidth, (TL_iv.pageBlockTable) pageBlock);
            block.accessibilityLabelResId = R.string.AccDescrIVTable;
            blocks.add(block);
            return block;
        } else if (pageBlock instanceof TL_iv.pageBlockMath) {
            final RichBlock block = new RichMathBlock(this, padding, maxWidth, (TL_iv.pageBlockMath) pageBlock);
            blocks.add(block);
            return block;
        } else if (pageBlock instanceof TL_iv.pageBlockDivider) {
            final RichBlock block = new RichDividerBlock(this, padding, maxWidth);
            blocks.add(block);
            return block;
        } else if (pageBlock instanceof TL_iv.pageBlockPhoto) {
            final TL_iv.pageBlockPhoto photo = (TL_iv.pageBlockPhoto) pageBlock;
            final RichBlock block = new RichPhotoBlock(this, padding, maxWidth, photo, blocks.isEmpty());
            blocks.add(block);
            emitCaption(photo.caption, padding, textFlags);
            return block;
        } else if (pageBlock instanceof TL_iv.pageBlockVideo) {
            final TL_iv.pageBlockVideo video = (TL_iv.pageBlockVideo) pageBlock;
            final RichBlock block = new RichVideoBlock(this, padding, maxWidth, video, blocks.isEmpty());
            blocks.add(block);
            emitCaption(video.caption, padding, textFlags);
            return block;
        } else if (pageBlock instanceof TL_iv.pageBlockCollage) {
            final TL_iv.pageBlockCollage collage = (TL_iv.pageBlockCollage) pageBlock;
            final RichBlock block = new RichCollageBlock(this, padding, maxWidth, collage, blocks.isEmpty());
            blocks.add(block);
            emitCaption(collage.caption, padding, textFlags);
            return block;
        } else if (pageBlock instanceof TL_iv.pageBlockSlideshow) {
            final TL_iv.pageBlockSlideshow slideshow = (TL_iv.pageBlockSlideshow) pageBlock;
            final RichBlock block = new RichSlideshowBlock(this, padding, maxWidth, slideshow, blocks.isEmpty());
            blocks.add(block);
            emitCaption(slideshow.caption, padding, textFlags);
            return block;
        } else if (pageBlock instanceof TL_iv.pageBlockMap) {
            final TL_iv.pageBlockMap map = (TL_iv.pageBlockMap) pageBlock;
            final RichBlock block = new RichMapBlock(this, padding, maxWidth, map);
            blocks.add(block);
            emitCaption(map.caption, padding, textFlags);
            return block;
        } else if (pageBlock instanceof TL_iv.pageBlockAudio) {
            final TL_iv.pageBlockAudio blockAudio = (TL_iv.pageBlockAudio) pageBlock;
            MessageObject mo = audioBlocks.get(blockAudio);
            if (mo == null) {
                final TLRPC.Document document = getDocument(blockAudio.audio_id);
                if (document != null) {
                    final TLRPC.TL_message message = new TLRPC.TL_message();
                    message.out = true;
                    message.id = blockAudio.mid = -((Long) blockAudio.audio_id).hashCode();
                    message.realId = messageObject.getRealId();
                    message.dialog_id = messageObject.getDialogId();
                    message.peer_id = messageObject.messageOwner.peer_id;
                    if (message.peer_id == null) {
                        message.peer_id = new TLRPC.TL_peerUser();
                        message.peer_id.user_id = UserConfig.getInstance(currentAccount).getClientUserId();
                    }
                    message.from_id = new TLRPC.TL_peerUser();
                    message.from_id.user_id = UserConfig.getInstance(currentAccount).getClientUserId();
                    message.date = (int) (System.currentTimeMillis() / 1000);
                    message.message = "";
                    message.media = new TLRPC.TL_messageMediaDocument();
                    message.media.flags |= 3;
                    message.media.document = document;
                    message.flags |= TLRPC.MESSAGE_FLAG_HAS_MEDIA | TLRPC.MESSAGE_FLAG_HAS_FROM_ID;
                    mo = new MessageObject(currentAccount, message, false, true);
                    audioMessages.add(mo);
                    audioBlocks.put(blockAudio, mo);
                }
            }
            final RichBlock block = new RichAudioBlock(this, padding, maxWidth, blockAudio);
            blocks.add(block);
            emitCaption(blockAudio.caption, padding, textFlags);
            return block;
        } else if (pageBlock instanceof TL_iv.pageBlockDocument) {
            final TL_iv.pageBlockDocument document = (TL_iv.pageBlockDocument) pageBlock;
            final RichBlock block = new RichDocumentBlock(this, padding, maxWidth, document);
            blocks.add(block);
            emitCaption(document.caption, padding, textFlags);
            return block;
        } else if (pageBlock instanceof TL_iv.pageBlockCover) {
            final TL_iv.pageBlockCover cover = (TL_iv.pageBlockCover) pageBlock;
            return emitBlock(cover.cover, level, padding, textFlags, false);
        } else if (pageBlock instanceof TL_iv.pageBlockAnchor) {
            final TL_iv.pageBlockAnchor anchor = (TL_iv.pageBlockAnchor) pageBlock;
            if (anchor.name != null) {
                anchors.put(anchor.name.toLowerCase(), blocks.size());
            }
            return null;
        } else if (pageBlock instanceof TL_iv.pageBlockUnsupported) {
            final RichUnsupportedBlock block = new RichUnsupportedBlock(this, new Rect(
                -dp(7), Math.max(dp(14), padding.top),
                -dp(7), Math.max(dp(14), padding.bottom)
            ), maxWidth, blocks.size());
            unsupportedBlocks.add(block);
            blocks.add(block);
            return block;
        } else if (pageBlock instanceof TL_iv.pageBlockDetails) {
            final TL_iv.pageBlockDetails details = (TL_iv.pageBlockDetails) pageBlock;
            final RichDetailsBlock header = new RichDetailsBlock(this, padding, maxWidth, details, formatText(details.title, textFlags & ~TEXT_FLAG_BOLD));
            blocks.add(header);
            final int childStart = blocks.size();
            for (int i = 0; i < details.blocks.size(); ++i) {
                emitBlock(details.blocks.get(i), level + 1, padding, textFlags, previousBlockIsParagraph(details.blocks, i));
            }
            blocks.add(new RichDetailsEndBlock(this, new Rect(padding.left, 0, padding.right, 0), maxWidth));
            for (int i = childStart; i < blocks.size(); ++i) {
                final RichBlock child = blocks.get(i);
                if (child.parentDetails == null) child.parentDetails = header;
            }
            return header;
        }

        if (BuildVars.DEBUG_PRIVATE_VERSION) {
            final RichBlock block = new RichTextBlock(this, padding, maxWidth, "unsupported block " + pageBlock);
            blocks.add(block);
            return block;
        }
        return null;
    }

    private static String orderedListMarker(TL_iv.pageBlockOrderedList list, TL_iv.PageListOrderedItem item, int index) {
        if (!TextUtils.isEmpty(item.num)) {
            return item.num.endsWith(".") ? item.num : item.num + ".";
        } else if (hasFlag(item.flags, TLObject.FLAG_3)) {
            return item.value + ".";
        } else if (hasFlag(list.flags, TLObject.FLAG_0)) {
            return list.start + (list.reversed ? -index : index) + ".";
        }
        return (index + 1) + ".";
    }

    private void markListMembership(int start, int end, int level, boolean ordered) {
        for (int i = start; i < end; i++) {
            final RichBlock block = blocks.get(i);
            if (block.listLevel == 0) {
                block.listLevel = level;
                block.listOrdered = ordered;
            }
        }
    }

    private void applyListPaddingFromBlocks() {
        for (int i = 0; i < blocks.size(); i++) {
            final RichBlock block = blocks.get(i);
            if (!(block instanceof RichTextBlock) || block.listLevel <= 0) continue;
            final boolean previousIsList = i > 0 && blocks.get(i - 1).listLevel > 0;
            final boolean nextIsList = i + 1 < blocks.size() && blocks.get(i + 1).listLevel > 0;
            ((RichTextBlock) block).setContentPadding(dp(previousIsList ? 2 : 6), dp(nextIsList ? 5 : 9));
        }
    }

    public boolean hasUnsupportedBlocks() {
        return !unsupportedBlocks.isEmpty();
    }

    public ArrayList<RichUnsupportedBlock> getUnsupportedHoles() {
        return unsupportedBlocks;
    }

    public int getMinWidth() {
        return minWidth;
    }

    public int getHeight() {
        if (isPart && height > dp(PART_MAX_HEIGHT_DP))
            return dp(PART_MAX_HEIGHT_DP + 4 + 42 + 4);
        return height + (isPart ? dp(4 + 42 + 4) : 0);
    }

    public int getLastLineWidth() {
        if (blocks.isEmpty() || isPart || isRtl()) return getMinWidth();
        if (!quotes.isEmpty()) {
            for (QuoteBackground q : quotes) {
                if (q.endBlockIndex >= blocks.size() - 1)
                    return getMinWidth();
            }
        }
        final RichBlock last = blocks.get(blocks.size() - 1);
        if (last.forcesTimeToNewLine()) return getMinWidth();
        return last.getLastLineWidth();
    }

    public boolean forceNewLineForTime() {
        if (blocks.isEmpty() || isPart || isRtl()) return true;
        if (!quotes.isEmpty()) {
            for (QuoteBackground q : quotes) {
                if (q.endBlockIndex >= blocks.size() - 1)
                    return true;
            }
        }
        return blocks.get(blocks.size() - 1).forcesTimeToNewLine();
    }

    public void setTypingAnimator(MultiLayoutTypingAnimator animator) {
        this.typingAnimator = animator;
        final StringBuilder sb = new StringBuilder();
        int preCount = 0;
        for (int i = 0; i < blocks.size(); ++i) {
            blocks.get(i).typingAnimator = animator;
            if (blocks.get(i) instanceof RichPreformattedBlock) preCount++;
            if (i > 0) sb.append(',');
            sb.append(blocks.get(i).getClass().getSimpleName());
        }
    }

    public List<MultiLayoutTypingAnimator.Block> getAnimatorBlocks() {
        final ArrayList<MultiLayoutTypingAnimator.Block> out = new ArrayList<>(blocks.size());
        for (int i = 0; i < blocks.size(); ++i) {
            blocks.get(i).collectAnimatorBlocks(out);
        }
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < out.size(); ++i) {
            if (i > 0) sb.append(',');
            sb.append(out.get(i).getClass().getSimpleName());
        }
        return out;
    }

    private void drawBackground(Canvas canvas, ChatMessageCell.TransitionParams tp) {
        if (!quotes.isEmpty()) {
            for (QuoteBackground q : quotes) {
                int quoteTop = getBlockTop(q.startBlockIndex, tp);
                int quoteBottom = getBlockBottom(q.endBlockIndex, tp);
                final float scale = getBlockBackgroundScale(q.startBlockIndex, q.endBlockIndex);
                final int nestedInset = q.level * dp(QUOTE_NEST_VPAD);
                final int topInset = nestedInset + q.outerTopVpad;
                final int bottomInset = nestedInset + q.outerBottomVpad;
                if (quoteBottom - quoteTop > topInset + bottomInset) {
                    quoteTop += topInset;
                    quoteBottom -= bottomInset;
                }
                AndroidUtilities.rectTmp.set(q.padding, quoteTop, getMinWidth() - dp(12 * q.level), quoteBottom);
                canvas.save();
                canvas.scale(scale, scale, AndroidUtilities.rectTmp.centerX(), AndroidUtilities.rectTmp.centerY());
//                if (q.level == 0) {
                    float rad = (float) Math.floor(SharedConfig.bubbleRadius / 3f);
                    quoteLine.drawBackground(canvas, AndroidUtilities.rectTmp, rad, rad, rad, 1.0f, false, false);
//                }
                quoteLine.drawLine(canvas, AndroidUtilities.rectTmp);
                canvas.restore();
            }
        }
        for (int i = 0; i < blocks.size(); ++i) {
            final RichBlock block = blocks.get(i);
            if (!(block instanceof RichPullquoteBlock) || (!block.currVisible && !block.prevVisible)) continue;
            drawPullquoteBackground(canvas, (RichPullquoteBlock) block, tp);
        }
    }

    private void drawPullquoteBackground(Canvas canvas, RichPullquoteBlock block, ChatMessageCell.TransitionParams tp) {
        final int textWidth = block.getTextWidth();
        if (textWidth <= 0) return;

        final boolean animated = tp != null && (detailsAnimating || blockquoteAnimating);
        final float progress = animated ? Math.max(0f, Math.min(1f, tp.animateChangeProgress)) : 1f;
        final float alpha = animated
            ? AndroidUtilities.lerp(block.prevVisible ? 1f : 0f, block.currVisible ? 1f : 0f, progress)
            : (block.currVisible ? 1f : 0f);
        if (alpha <= 0f) return;

        final float center = (getMinWidth() + padRight - padLeft) / 2f;
        final float left = center - textWidth / 2f - dp(30);
        final float right = center + textWidth / 2f + dp(30);

        final float blockTop = animated ? AndroidUtilities.lerp(block.prevY, block.currY, progress) : block.currY;
        final float blockHeight = animated ? AndroidUtilities.lerp(block.prevH, block.currH, progress) : block.getHeight();
        final float top = blockTop + dp(8);
        final float bottom = blockTop + blockHeight - dp(8);
        if (bottom <= top) return;

        AndroidUtilities.rectTmp.set(left, top, right, bottom);
        final float rad = (float) Math.floor(SharedConfig.bubbleRadius / 2f);
        quoteLine.drawBackground(canvas, AndroidUtilities.rectTmp, rad, rad, rad, alpha, false, false);

        if (pullquoteIcon == null) {
            pullquoteIcon = ContextCompat.getDrawable(ApplicationLoader.applicationContext, R.drawable.mini_quote).mutate();
        }
        pullquoteIcon.setColorFilter(quoteLine.getColor(), PorterDuff.Mode.SRC_IN);
        pullquoteIcon.setAlpha((int) (0xFF * alpha));
        final int iw = pullquoteIcon.getIntrinsicWidth();
        final int ih = pullquoteIcon.getIntrinsicHeight();

        canvas.save();
        canvas.clipRect(left, top, right, bottom);
        pullquoteIcon.setBounds((int) left + dp(8), (int) top + dp(7), (int) left + dp(8) + iw, (int) top + dp(7) + ih);
        canvas.scale(-1.0f, -1.0f, pullquoteIcon.getBounds().centerX(), pullquoteIcon.getBounds().centerY());
        pullquoteIcon.draw(canvas);
        canvas.restore();

        canvas.save();
        canvas.clipRect(left, top, right, bottom);
        pullquoteIcon.setBounds((int) right - dp(8) - iw, (int) bottom - dp(7) - ih, (int) right - dp(8), (int) bottom - dp(7));
        canvas.scale(1.0f, -1.0f, pullquoteIcon.getBounds().centerX(), pullquoteIcon.getBounds().centerY());
        pullquoteIcon.draw(canvas);
        canvas.restore();
        pullquoteIcon.setAlpha(0xFF);
    }

    private int getBlockTop(int index, ChatMessageCell.TransitionParams tp) {
        if (index >= 0 && index < blocks.size() && tp != null && (detailsAnimating || blockquoteAnimating)) {
            final float prog = Math.max(0f, Math.min(1f, tp.animateChangeProgress));
            final RichBlock block = blocks.get(index);
            return Math.round(AndroidUtilities.lerp(block.prevY, block.currY, prog));
        }
        int y = 0;
        boolean lastVisible = false;
        for (int i = 0; i < blocks.size(); ++i) {
            final RichBlock block = blocks.get(i);
            final boolean visible = block.isVisible();
            if (visible && lastVisible) y += getGap();
            if (i == index) return y;
            if (visible) {
                if (tp != null && (detailsAnimating || blockquoteAnimating)) {
                    final float prog = Math.max(0f, Math.min(1f, tp.animateChangeProgress));
                    y += AndroidUtilities.lerp(block.prevH, block.currH, prog);
                } else {
                    y += block.getHeight();
                }
                lastVisible = true;
            }
        }
        return height;
    }
    private int getBlockBottom(int index, ChatMessageCell.TransitionParams tp) {
        if (index >= 0 && index < blocks.size() && tp != null && (detailsAnimating || blockquoteAnimating)) {
            final float prog = Math.max(0f, Math.min(1f, tp.animateChangeProgress));
            final RichBlock block = blocks.get(index);
            final int bottomInset = Math.max(0, block.padding.bottom - dp(4));
            final float prevBottom = block.prevY + block.prevH - (block.prevVisible ? bottomInset : 0);
            final float currBottom = block.currY + block.currH - (block.currVisible ? bottomInset : 0);
            return Math.round(AndroidUtilities.lerp(prevBottom, currBottom, prog));
        }
        int y = 0;
        boolean lastVisible = false;
        for (int i = 0; i < blocks.size(); ++i) {
            final RichBlock block = blocks.get(i);
            final boolean visible = block.isVisible();
            if (visible && lastVisible) y += getGap();
            if (visible) {
                if (tp != null && (detailsAnimating || blockquoteAnimating)) {
                    final float prog = Math.max(0f, Math.min(1f, tp.animateChangeProgress));
                    y += AndroidUtilities.lerp(block.prevH, block.currH, prog);
                } else {
                    y += block.getHeight();
                }
                if (i == index && block.padding.bottom > dp(4)) {
                    y -= block.padding.bottom - dp(4);
                }
            }
            if (i == index) return y;
            if (visible) lastVisible = true;
        }
        return height;
    }
    private float getBlockBackgroundScale(int start, int end) {
        float result = 1;
        for (int i = start; i < Math.min(end + 1, blocks.size()); ++i) {
            final RichBlock block = blocks.get(i);
            final boolean visible = block.isVisible();
            if (visible) {
                result *= block.getBackgroundScale();
            }
        }
        return result;
    }

    public int padLeft;
    public int padRight;

    private void drawInternal(Canvas canvas, ChatMessageCell.TransitionParams tp) {
        float clipTop = 0f, clipBottom = 0f;
        final boolean hasClip = cell != null && cell.visibleHeight > 0;
        if (hasClip) {
            clipTop = cell.childPosition - cell.textY;
            clipBottom = clipTop + cell.visibleHeight;
        }
        drawInternal(canvas, tp, hasClip, clipTop, clipBottom);
    }

    private AnimatedFloat translationLoadingFloat;
    public float translationLoadingValue;

    private void updateTranslationLoading() {
        final boolean translating = isTranslating();
        if (!translating && translationLoadingFloat == null) {
            translationLoadingValue = 0;
            return;
        }
        if (translationLoadingFloat == null) {
            translationLoadingFloat = new AnimatedFloat(0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
        }
        translationLoadingValue = translationLoadingFloat.set(translating ? 1 : 0);
        if (translationLoadingValue > 0 && view != null) {
            view.invalidate();
        }
    }

    private void drawInternal(Canvas canvas, ChatMessageCell.TransitionParams tp, boolean hasClip, float clipTop, float clipBottom) {
        drawBackground(canvas, tp);
        updateTranslationLoading();

        final float prog = (tp != null && (detailsAnimating || blockquoteAnimating)) ? Math.max(0f, Math.min(1f, tp.animateChangeProgress)) : 1f;
        detailsAnimationProgress = prog;
        if (prog >= 1f) {
            detailsAnimating = false;
            blockquoteAnimating = false;
        }
        if (detailsAnimating || blockquoteAnimating) {
            hasClip = false;
        }

        final boolean clipDetails = detailsAnimating && prog < 1f;
        if (clipDetails) computeDetailsClips(prog);

        computeBlockquoteClips(prog);

        for (int i = 0; i < blocks.size(); ++i) {
            final RichBlock block = blocks.get(i);
            if (!block.currVisible && !block.prevVisible) continue;

            final float y = AndroidUtilities.lerp(block.prevY, block.currY, prog);
            final float alpha = block instanceof RichDetailsEndBlock
                ? 1f
                : AndroidUtilities.lerp(block.prevVisible ? 1f : 0f, block.currVisible ? 1f : 0f, prog);
            if (alpha <= 0f) continue;

            final int h = block.getHeight();
            if (hasClip && (y + h <= clipTop || y >= clipBottom)) continue;
            canvas.save();
            if (clipDetails && block.parentDetails != null && !(block instanceof RichDetailsEndBlock)) {
                float top = -Float.MAX_VALUE, bottom = Float.MAX_VALUE;
                for (RichDetailsBlock p = block.parentDetails; p != null; p = p.parentDetails) {
                    top = Math.max(top, p.animClipTop);
                    bottom = Math.min(bottom, p.animClipBottom);
                }
                if (bottom <= top) {
                    canvas.restore();
                    continue;
                }
                canvas.clipRect(-padLeft, top, getMinWidth() + padRight, bottom);
            }
            canvas.translate(0, y);
            if (alpha < 1f) {
                final int sc = canvas.saveLayerAlpha(-padLeft, 0, getMinWidth() + padRight, h, (int) (alpha * 255), Canvas.ALL_SAVE_FLAG);
                block.drawWithTyping(canvas);
                canvas.restoreToCount(sc);
            } else {
                block.drawWithTyping(canvas);
            }
            canvas.restore();
        }

        if (prog >= 1f) snapshotForDetailsAnimation();
    }

    private void computeDetailsClips(float prog) {
        for (int i = 0; i < blocks.size(); ++i) {
            final RichBlock b = blocks.get(i);
            if (!(b instanceof RichDetailsBlock)) continue;
            final RichDetailsBlock header = (RichDetailsBlock) b;
            final float headerY = AndroidUtilities.lerp(header.prevY, header.currY, prog);
            header.animClipTop = headerY + header.getHeight();
            float bottom = Float.MAX_VALUE;
            for (int j = i + 1; j < blocks.size(); ++j) {
                if (isDescendantOf(blocks.get(j), header)) continue;
                bottom = AndroidUtilities.lerp(blocks.get(j).prevY, blocks.get(j).currY, prog);
                break;
            }
            header.animClipBottom = bottom;
        }
    }

    private void computeBlockquoteClips(float prog) {
        for (int i = 0; i < blocks.size(); ++i) {
            final RichBlock b = blocks.get(i);
            if (!(b instanceof RichTextBlockQuote)) continue;
            final RichTextBlockQuote quote = (RichTextBlockQuote) b;
            quote.collapsedProgress = AndroidUtilities.lerp(quote.prevCollapsed ? 1f : 0f, quote.currentCollapsed ? 1f : 0f, prog);
            quote.collapsedHeightToDraw = AndroidUtilities.lerp(quote.prevH, quote.currH, prog);
        }
    }

    private static boolean isDescendantOf(RichBlock b, RichDetailsBlock header) {
        for (RichDetailsBlock p = b.parentDetails; p != null; p = p.parentDetails) {
            if (p == header) return true;
        }
        return false;
    }

    public void draw(Canvas canvas, int padLeft, int padRight, ChatMessageCell.TransitionParams tp) {
        setBubblePaddings(padLeft, padRight);
        textPaint.linkColor = getThemedColor(isOut() ? Theme.key_chat_messageLinkOut : Theme.key_chat_messageLinkIn);

        final boolean part = isPart;
        final int bottom = Math.min(height, dp(PART_MAX_HEIGHT_DP));
        if (part) {
            canvas.saveLayerAlpha(-padLeft, 0, getMinWidth() + padRight, bottom, 0xFF, Canvas.ALL_SAVE_FLAG);
        }
        drawInternal(canvas, tp);
        if (part) {
            AndroidUtilities.rectTmp.set(-padLeft, bottom - dp(32), getMinWidth() + padRight, bottom);
            clip.draw(canvas, AndroidUtilities.rectTmp, GradientClip.BOTTOM, 1.0f);
            canvas.restore();

            drawShowMoreButton(canvas, bottom);
        }
    }

    public void draw(Canvas canvas, int padLeft, int padRight, ChatMessageCell.TransitionParams tp, float clipTop, float clipBottom) {
        setBubblePaddings(padLeft, padRight);
        textPaint.linkColor = getThemedColor(isOut() ? Theme.key_chat_messageLinkOut : Theme.key_chat_messageLinkIn);
        drawInternal(canvas, tp, clipBottom > clipTop, clipTop, clipBottom);
    }

    private void setBubblePaddings(int padLeft, int padRight) {
        if (this.padLeft == padLeft && this.padRight == padRight) return;
        this.padLeft = padLeft;
        this.padRight = padRight;
        for (int i = 0; i < blocks.size(); i++) {
            final RichBlock block = blocks.get(i);
            if (block instanceof RichDetailsBlock) {
                ((RichDetailsBlock) block).updateBubbleInsets();
            }
        }
    }

    private void drawShowMoreButton(Canvas canvas, int contentBottom) {
        final int color = getThemedColor(isOut() ? Theme.key_chat_outPreviewInstantText : Theme.key_chat_inPreviewInstantText);

        if (showMoreText == null) {
            showMoreText = new org.telegram.ui.Components.Text(LocaleController.getString(R.string.ShowMore), 16, AndroidUtilities.bold());
        }
        if (showMoreBounce == null) {
            showMoreBounce = new ButtonBounce(view, 1.5f, 2.0f);
        } else if (showMoreBounce.getView() != view) {
            showMoreBounce.setView(view);
        }
        if (showMorePaint == null) {
            showMorePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        }
        showMorePaint.setColor(Theme.multAlpha(color, 0.10f));

        final float buttonHeight = dp(42);
        final float textWidth = showMoreText.getCurrentWidth();
        final float buttonWidth = getMinWidth() + padLeft + padRight - dp(24);
        final float cx = (getMinWidth() + padLeft + padRight) / 2f - padLeft;
        final float top = contentBottom + dp(4);
        showMoreRect.set(cx - buttonWidth / 2f, top, cx + buttonWidth / 2f, top + buttonHeight);

        final boolean loading = cell != null && delegate != null && delegate.isProgressLoading(cell, ChatActivity.PROGRESS_FULL_ARTICLE);
        if (showMoreLoading != null && !loading && !showMoreLoading.isDisappeared() && !showMoreLoading.isDisappearing()) {
            showMoreLoading.disappear();
        }
        if (showMoreLoading == null && loading) {
            showMoreLoading = new LoadingDrawable();
            showMoreLoading.strokePaint.setStrokeWidth(dp(1.25f));
            showMoreLoading.setAppearByGradient(true);
        } else if (showMoreLoading != null && loading && (showMoreLoading.isDisappeared() || showMoreLoading.isDisappearing())) {
            showMoreLoading.reset();
            showMoreLoading.resetDisappear();
        }
        if (showMoreLoading != null) {
            showMoreLoading.setColors(
                Theme.multAlpha(color, 0.10f),
                Theme.multAlpha(color, 0.30f),
                Theme.multAlpha(color, 0.30f),
                Theme.multAlpha(color, 1.20f)
            );
        }

        final float scale = showMoreBounce.getScale(0.075f);
        final boolean scaleRestore = scale != 1f;
        if (scaleRestore) {
            canvas.save();
            canvas.scale(scale, scale, showMoreRect.centerX(), showMoreRect.centerY());
        }
        canvas.drawRoundRect(showMoreRect, dp(8), dp(8), showMorePaint);
        if (showMoreLoading != null && !showMoreLoading.isDisappeared()) {
            showMoreLoading.setBounds(showMoreRect);
            showMoreLoading.setRadiiDp(8);
            showMoreLoading.draw(canvas);
            if (view != null) view.invalidate();
        }
        showMoreText.draw(canvas, showMoreRect.centerX() - textWidth / 2f, showMoreRect.centerY(), color, 1f);
        if (scaleRestore) {
            canvas.restore();
        }
    }

    public boolean isOverlayActive() {
        return typingAnimator == null || !typingAnimator.isRunning();
    }

    public boolean hasOverlay() {
        if (!isOverlayActive()) return false;
        for (int i = 0; i < textBlocks.size(); ++i) {
            final TextSelectionHelper.TextLayoutBlock tb = textBlocks.get(i);
            if (tb instanceof Text) {
                final Text t = (Text) tb;
                if (t.animatedEmojiStack != null && !t.animatedEmojiStack.holders.isEmpty()) return true;
            }
        }
        return false;
    }

    public boolean drawOverlay(Canvas canvas) {
        return drawOverlay(canvas, null);
    }

    public boolean drawOverlay(Canvas canvas, ColorFilter colorFilter) {
        if (!isOverlayActive()) return false;
        boolean drew = false;
        for (int i = 0; i < blocks.size(); ++i) {
            final RichBlock block = blocks.get(i);
            if (!block.currVisible) continue;
            canvas.save();
            canvas.translate(0, block.currY);
            if (block.drawOverlay(canvas, colorFilter)) drew = true;
            canvas.restore();
        }
        return drew;
    }

    public void updateAnimatedEmojis(int cacheType) {
        for (int i = 0; i < textBlocks.size(); ++i) {
            final TextSelectionHelper.TextLayoutBlock tb = textBlocks.get(i);
            if (tb instanceof Text) {
                ((Text) tb).refreshAnimatedEmoji(cacheType);
            }
        }
    }

    private RichBlock pressedBlock;
    private int pressedBlockY;

    public boolean isHorizontallyDragging() {
        return pressedBlock != null && pressedBlock.isHorizontallyDragging();
    }

    public boolean isPressingLink() {
        return pressedBlock != null && pressedBlock.isPressingLink();
    }

    public ChatMessageCell getCell() { return cell; }
    public ChatMessageCell.ChatMessageCellDelegate getDelegate() { return delegate; }

    private boolean handleAnchorClick(String url) {
        if (url == null || !url.startsWith("#")) return false;
        String name;
        try {
            name = URLDecoder.decode(url.substring(1), "UTF-8");
        } catch (Exception e) {
            name = url.substring(1);
        }
        if (TextUtils.isEmpty(name)) return false;
        name = name.toLowerCase();

        final TL_iv.textAnchor textAnchor = textAnchors.get(name);
        if (textAnchor != null) {
            return showFootnoteSheet(textAnchor);
        }

        final Integer blockIndex = anchors.get(name);
        if (blockIndex != null) {
            return scrollToPageBlockAnchor(blockIndex);
        }
        return true;
    }

    private boolean scrollToPageBlockAnchor(int blockIndex) {
        if (cell == null) return false;
        if (blockIndex < 0 || blockIndex >= blocks.size()) return false;

        ViewParent p = cell.getParent();
        RecyclerView list = null;
        while (p != null) {
            if (p instanceof RecyclerView) {
                list = (RecyclerView) p;
                break;
            }
            p = p.getParent();
        }
        if (list == null) return false;

        final int blockY = getBlockTop(blockIndex, null);
        final int targetInList = cell.getTop() + cell.textY + blockY;
        final int delta = targetInList - list.getPaddingTop() - dp(8);
        list.smoothScrollBy(0, delta);
        return true;
    }

    private boolean showFootnoteSheet(TL_iv.textAnchor textAnchor) {
        if (view == null) return false;
        final android.content.Context context = view.getContext();
        if (context == null) return false;
        if (textAnchor.text == null || textAnchor.text instanceof TL_iv.textEmpty) return false;

        final String anchorName = textAnchor.name == null ? "" : textAnchor.name.toLowerCase();
        final CharSequence content = formatText(WebInstantView.filterRecursiveAnchorLinks(textAnchor.text, "", anchorName));

        final BottomSheet.Builder builder = new BottomSheet.Builder(context, true, resourcesProvider);
        builder.setApplyTopPadding(false);
        builder.setApplyBottomPadding(false);

        final LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        final TextView header = new TextView(context);
        header.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        header.setTypeface(AndroidUtilities.bold());
        header.setText(LocaleController.getString(R.string.InstantViewReference));
        header.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        header.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        header.setPadding(dp(22), 0, dp(22), 0);
        linearLayout.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        final LinkSpanDrawable.LinksTextView body = new LinkSpanDrawable.LinksTextView(context, resourcesProvider);
        body.setTextSize(TypedValue.COMPLEX_UNIT_DIP, SharedConfig.fontSize);
        body.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        body.setLinkTextColor(getThemedColor(Theme.key_dialogTextLink));
        body.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        body.setPadding(dp(22), 0, dp(22), dp(16));
        body.setText(content);
        linearLayout.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        builder.setCustomView(frameLayout);
        builder.show();
        return true;
    }

    public static class FoundLink {
        public StaticLayout layout;
        public int start, end;
        public int originalWidth;
        public float x, y;
    }

    public FoundLink findLink(CharacterStyle link) {
        if (link == null) return null;
        final FoundLink out = new FoundLink();
        int y = 0;
        boolean lastVisible = false;
        for (int i = 0; i < blocks.size(); ++i) {
            final RichBlock block = blocks.get(i);
            if (!block.isVisible()) continue;
            if (lastVisible) y += getGap();
            if (block.findLink(link, y, out)) return out;
            y += block.getHeight();
            lastVisible = true;
        }
        return null;
    }

    public boolean onTouchEvent(MotionEvent event) {
        final int act = event.getActionMasked();

        if (act == MotionEvent.ACTION_DOWN && (event.getX() < padLeft || event.getX() > getMinWidth() + padRight)) {
            return false;
        }

        if (isPart) {
            final float x = event.getX();
            final float y = event.getY();
            if (act == MotionEvent.ACTION_DOWN) {
                if (showMoreRect.contains(x, y)) {
                    showMorePressed = true;
                    if (showMoreBounce != null) showMoreBounce.setPressed(true);
                    return true;
                }
            } else if (showMorePressed) {
                if (act == MotionEvent.ACTION_MOVE) {
                    if (!showMoreRect.contains(x, y)) {
                        if (showMoreBounce != null) showMoreBounce.setPressed(false);
                        showMorePressed = false;
                    }
                    return true;
                }
                if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
                    if (showMoreBounce != null) showMoreBounce.setPressed(false);
                    final boolean hit = act == MotionEvent.ACTION_UP && showMoreRect.contains(x, y);
                    showMorePressed = false;
                    if (hit && delegate != null && cell != null) {
                        if (view != null) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                        delegate.didPressShowMore(cell);
                    }
                    return true;
                }
            }
        }

        if (act == MotionEvent.ACTION_DOWN) {
            pressedBlock = null;
            final float ey = event.getY();
            int yAcc = 0;
            boolean lastVisible = false;
            for (int i = 0; i < blocks.size(); i++) {
                final RichBlock block = blocks.get(i);
                if (!block.isVisible()) continue;
                if (lastVisible) yAcc += getGap();
                final int h = block.getHeight();
                if (ey >= yAcc && ey < yAcc + h) {
                    event.offsetLocation(0, -yAcc);
                    final boolean handled = block.touchEvent(event);
                    event.offsetLocation(0, yAcc);
                    if (handled) {
                        pressedBlock = block;
                        pressedBlockY = yAcc;
                        return true;
                    }
                    break;
                }
                yAcc += h;
                lastVisible = true;
            }
            return false;
        }
        if (pressedBlock == null) return false;
        event.offsetLocation(0, -pressedBlockY);
        final boolean handled = pressedBlock.touchEvent(event);
        event.offsetLocation(0, pressedBlockY);
        if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
            pressedBlock = null;
        }
        return handled;
    }

    public static final int TEXT_FLAG_BLOCKS         = 0b1111;
    public static final int TEXT_FLAG_BLOCK_HEADING1 = 1;
    public static final int TEXT_FLAG_BLOCK_HEADING2 = 2;
    public static final int TEXT_FLAG_BLOCK_HEADING3 = 3;
    public static final int TEXT_FLAG_BLOCK_HEADING4 = 4;
    public static final int TEXT_FLAG_BLOCK_HEADING5 = 5;
    public static final int TEXT_FLAG_BLOCK_HEADING6 = 6;
    public static final int TEXT_FLAG_BLOCK_FOOTER   = 7;
    public static final int TEXT_FLAG_BLOCK_CODE     = 8;
    public static final int TEXT_FLAG_BLOCK_QUOTE    = 9;
    public static final int TEXT_FLAG_BLOCK_CAPTION  = 10;
    public static final int TEXT_FLAG_BLOCK_QUOTE_CAPTION = 11;
    public static final int TEXT_FLAG_BLOCK_PULLQUOTE = 12;
    public static final int TEXT_FLAG_BLOCK_BUTTON = 13;
    public static final int TEXT_FLAG_BLOCK_TABLE = 14;
    public static final int TEXT_FLAG_BLOCK_TABLE_TITLE = 15;

    public static final int TEXT_FLAG_BOLD           = 1 << 4;
    public static final int TEXT_FLAG_ITALIC         = 1 << 5;
    public static final int TEXT_FLAG_UNDERLINE      = 1 << 6;
    public static final int TEXT_FLAG_STRIKETHROUGH  = 1 << 7;
    public static final int TEXT_FLAG_MONO           = 1 << 8;
    public static final int TEXT_FLAG_URL            = 1 << 9;
    public static final int TEXT_FLAG_WEBPAGE_URL    = 1 << 10;
    public static final int TEXT_FLAG_SUBSCRIPT      = 1 << 11;
    public static final int TEXT_FLAG_SUPERSCRIPT    = 1 << 12;
    public static final int TEXT_FLAG_MARKED         = 1 << 13;

    private static int getBlockTextFlag(TL_iv.PageBlock block) {
        if (block instanceof TL_iv.pageBlockHeading1) return TEXT_FLAG_BLOCK_HEADING1;
        if (block instanceof TL_iv.pageBlockHeading2) return TEXT_FLAG_BLOCK_HEADING2;
        if (block instanceof TL_iv.pageBlockHeading3) return TEXT_FLAG_BLOCK_HEADING3;
        if (block instanceof TL_iv.pageBlockHeading4) return TEXT_FLAG_BLOCK_HEADING4;
        if (block instanceof TL_iv.pageBlockHeading5) return TEXT_FLAG_BLOCK_HEADING5;
        if (block instanceof TL_iv.pageBlockHeading6) return TEXT_FLAG_BLOCK_HEADING6;

        if (block instanceof TL_iv.pageBlockBlockquote) return TEXT_FLAG_BLOCK_QUOTE;
        if (block instanceof TL_iv.pageBlockBlockquoteBlocks) return TEXT_FLAG_BLOCK_QUOTE;
        if (block instanceof TL_iv.pageBlockPullquote) return TEXT_FLAG_BLOCK_PULLQUOTE;

        if (block instanceof TL_iv.pageBlockFooter)   return TEXT_FLAG_BLOCK_FOOTER;

        return 0;
    }

    private static int getBlockAccessibilityLabel(TL_iv.PageBlock block) {
        if (block instanceof TL_iv.pageBlockHeading1) return R.string.ArticleHeading1;
        if (block instanceof TL_iv.pageBlockHeading2) return R.string.ArticleHeading2;
        if (block instanceof TL_iv.pageBlockHeading3) return R.string.ArticleHeading3;
        if (block instanceof TL_iv.pageBlockHeading4) return R.string.ArticleHeading4;
        if (block instanceof TL_iv.pageBlockHeading5) return R.string.ArticleHeading5;
        if (block instanceof TL_iv.pageBlockHeading6) return R.string.ArticleHeading6;
        if (block instanceof TL_iv.pageBlockFooter) return R.string.ArticleFooter;
        return 0;
    }

    public CharSequence formatText(TL_iv.RichText text) {
        return formatText(text, new SpannableStringBuilder(), 0);
    }
    public CharSequence formatText(TL_iv.RichText text, int flags) {
        if (flags == 0)
            return formatText(text, new SpannableStringBuilder(), 0);
        else
            return formatTextAndSetSpan(text, new SpannableStringBuilder(), flags, new StyleSpan(this, flags));
    }
    private void setSpansWithoutClash(Object span, SpannableStringBuilder out, int start, int end) {
        if (span instanceof StyleSpan) {
            final StyleSpan styleSpan = (StyleSpan) span;
            final StyleSpan[] spans = out.getSpans(start, end, StyleSpan.class);
            if (spans != null && spans.length > 0) {
                Arrays.sort(spans, Comparator.comparingInt(out::getSpanStart));
                for (int i = 0; i < spans.length; ++i) {
                    final int spanStart = out.getSpanStart(spans[i]);
                    final int spanEnd   = out.getSpanEnd(spans[i]);
                    if (spanStart > start) {
                        setStyleRange(out, start, spanStart, styleSpan.flags);
                    }
                    start = Math.max(start, spanEnd);
                }
                if (start < end) {
                    setStyleRange(out, start, end, styleSpan.flags);
                }
                return;
            }
            setStyleRange(out, start, end, styleSpan.flags);
            return;
        }
        out.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void setStyleRange(SpannableStringBuilder out, int from, int to, int flags) {
        int pos = from;
        while (pos < to) {
            final int next = out.nextSpanTransition(pos, to, URLSpan.class);
            final boolean link = out.getSpans(pos, next, URLSpan.class).length > 0;
            out.setSpan(new StyleSpan(this, flags, link), pos, next, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            pos = next;
        }
    }
    private CharSequence formatTextAndSetSpan(TL_iv.RichText text, SpannableStringBuilder out, int flags, Object span) {
        final int start = out.length();
        formatText(text, out, flags);
        if (out.length() > start) {
            setSpansWithoutClash(span, out, start, out.length());
        }
        return out;
    }
    private CharSequence formatTextAndSetSpan(TL_iv.RichText text, SpannableStringBuilder out, int flags, Object span, Object span2) {
        final int start = out.length();
        formatText(text, out, flags);
        if (out.length() > start) {
            setSpansWithoutClash(span, out, start, out.length());
            setSpansWithoutClash(span2, out, start, out.length());
        }
        return out;
    }
    private static TextStyleSpan.TextStyleRun getTextStyleRun(int flags) {
        final TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
        run.flags = flags;
        return run;
    }
    public CharSequence formatText(TL_iv.RichText text, SpannableStringBuilder out, int flags) {
        if (text instanceof TL_iv.textEmpty) {

        } else if (text instanceof TL_iv.textPlain) {
            out.append(((TL_iv.textPlain) text).text);
        } else if (text instanceof TL_iv.textDiff) {
            final TL_iv.textDiff textDiff = (TL_iv.textDiff) text;
            final boolean textEmpty = RichTextStyle.isEmpty(textDiff.text);
            final boolean oldTextEmpty = RichTextStyle.isEmpty(textDiff.old_text);
            if (textEmpty && !oldTextEmpty) {
                formatTextAndSetSpan(textDiff.old_text, out, flags, new TextStyleSpan(getTextStyleRun(TextStyleSpan.FLAG_STYLE_STRIKE_RED)));
            } else if (!textEmpty && oldTextEmpty) {
                formatTextAndSetSpan(textDiff.text, out, flags, new TextStyleSpan(getTextStyleRun(TextStyleSpan.FLAG_STYLE_ACCENT)));
            } else if (!textEmpty) {
                formatTextAndSetSpan(textDiff.text, out, flags, new SquigglyLinesSpan());
            }
        } else if (text instanceof TL_iv.textBold) {
            flags |= TEXT_FLAG_BOLD;
            formatTextAndSetSpan(text.text, out, flags, new StyleSpan(this, flags));
        } else if (text instanceof TL_iv.textItalic) {
            flags |= TEXT_FLAG_ITALIC;
            formatTextAndSetSpan(text.text, out, flags, new StyleSpan(this, flags));
        } else if (text instanceof TL_iv.textUnderline) {
            flags |= TEXT_FLAG_UNDERLINE;
            formatTextAndSetSpan(text.text, out, flags, new StyleSpan(this, flags));
        } else if (text instanceof TL_iv.textStrike) {
            flags |= TEXT_FLAG_STRIKETHROUGH;
            formatTextAndSetSpan(text.text, out, flags, new StyleSpan(this, flags));
        } else if (text instanceof TL_iv.textFixed) {
            flags |= TEXT_FLAG_MONO;
            formatTextAndSetSpan(text.text, out, flags, new StyleSpan(this, flags));
        } else if (text instanceof TL_iv.textUrl) {
            final TL_iv.textUrl textUrl = (TL_iv.textUrl) text;
            formatTextAndSetSpan(text.text, out, flags,
                new URLSpanReplacement(textUrl.url, getTextStyleRun(TextStyleSpan.FLAG_STYLE_TEXT_URL)));
        } else if (text instanceof TL_iv.textEmail) {
            final TL_iv.textEmail textEmail = (TL_iv.textEmail) text;
            formatTextAndSetSpan(text.text, out, flags,
                new URLSpanReplacement("mailto:" + textEmail.email, getTextStyleRun(TextStyleSpan.FLAG_STYLE_TEXT_URL)));
        } else if (text instanceof TL_iv.textConcat) {
            for (int i = 0; i < text.texts.size(); ++i) {
                formatText(text.texts.get(i), out, flags);
            }
        } else if (text instanceof TL_iv.textSubscript) {
            flags |= TEXT_FLAG_SUBSCRIPT;
            formatTextAndSetSpan(text.text, out, flags, new StyleSpan(this, flags));
        } else if (text instanceof TL_iv.textSuperscript) {
            flags |= TEXT_FLAG_SUPERSCRIPT;
            formatTextAndSetSpan(text.text, out, flags, new StyleSpan(this, flags));
        } else if (text instanceof TL_iv.textMarked) {
            flags |= TEXT_FLAG_MARKED;
            formatTextAndSetSpan(text.text, out, flags, new StyleSpan(this, flags));
        } else if (text instanceof TL_iv.textPhone) {
            final TL_iv.textPhone textPhone = (TL_iv.textPhone) text;
            String tel = PhoneFormat.stripExceptNumbers(textPhone.phone);
            if (textPhone.phone.startsWith("+")) {
                tel = "+" + tel;
            }
            formatTextAndSetSpan(text.text, out, flags, new URLSpanReplacement("tel:" + tel, getTextStyleRun(TextStyleSpan.FLAG_STYLE_TEXT_URL)));
        } else if (text instanceof TL_iv.textAnchor) {
            final TL_iv.textAnchor anchor = (TL_iv.textAnchor) text;
            if (anchor.name != null) {
                final String key = anchor.name.toLowerCase();
                if (!(anchor.text instanceof TL_iv.textEmpty)) {
                    textAnchors.put(key, anchor);
                } else if (!anchors.containsKey(key)) {
                    anchors.put(key, blocks.size());
                }
            }
            formatTextAndSetSpan(text.text, out, flags, new AnchorSpan(anchor.name == null ? "" : anchor.name.toLowerCase()));
        } else if (text instanceof TL_iv.textMath) {
            final TL_iv.textMath textLatex = (TL_iv.textMath) text;
            if (textLatex.bitmap == null && !textLatex.tried) {
                textLatex.tried = true;
                final Latex r = Latex.render(textLatex.source, dp(4 + fontSize), true);
                if (r != null) {
                    textLatex.w = r.width;
                    textLatex.h = r.height;
                    textLatex.depth = r.depth;
                    textLatex.bitmap = r.bitmap;
                }
            }
            if (textLatex.bitmap == null) {
                return textLatex.source == null ? "" : textLatex.source;
            }

            final int start = out.length();
            out.append(" ");
            final int end = out.length();

            out.setSpan(new TextPaintImageReceiverSpan(null, textLatex.bitmap, textLatex.w, textLatex.h, Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), textLatex.depth), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (textLatex.source != null && !textLatex.source.isEmpty()) {
                out.setSpan(new TextSelectionHelper.ReplaceCopyTextSpannable(textLatex.source), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        } else if (text instanceof TL_iv.textCustomEmoji) {
            final TL_iv.textCustomEmoji customEmoji = (TL_iv.textCustomEmoji) text;
            final String alt = TextUtils.isEmpty(customEmoji.alt) ? "😀" : customEmoji.alt;

            final int start = out.length();
            out.append(alt);
            final int end = out.length();
            final boolean inButton = BitwiseUtils.hasFlag(flags, TEXT_FLAG_BLOCK_BUTTON);
            final int block = flags & TEXT_FLAG_BLOCKS;
            final AnimatedEmojiSpan emojiSpan;
            if (block >= TEXT_FLAG_BLOCK_HEADING1 && block <= TEXT_FLAG_BLOCK_HEADING6) {
                final TextPaint headingPaint = new TextPaint(textPaint);
                new StyleSpan(this, flags, true).applyStyle(headingPaint);
                emojiSpan = new AnimatedEmojiSpan(customEmoji.document_id, .85f, headingPaint.getFontMetricsInt());
            } else {
                emojiSpan = new AnimatedEmojiSpan(customEmoji.document_id, inButton ? 1 : 1.2f, null)
                    .setSize(dp(4 + fontSize + (inButton ? -2 : 4)));
            }
            out.setSpan(emojiSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else if (text instanceof TL_iv.textSpoiler) {
            formatTextAndSetSpan(text.text, out, flags, new TextStyleSpan(getTextStyleRun(TextStyleSpan.FLAG_STYLE_SPOILER)));
        } else if (text instanceof TL_iv.textMention) {
            final TLRPC.TL_messageEntityMention entity = new TLRPC.TL_messageEntityMention();
            final TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
            run.urlEntity = entity;
            formatTextAndSetSpan(text.text, out, flags, new URLSpanNoUnderline(getString(text), run));
        } else if (text instanceof TL_iv.textHashtag) {
            final TLRPC.TL_messageEntityHashtag entity = new TLRPC.TL_messageEntityHashtag();
            final TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
            run.urlEntity = entity;
            formatTextAndSetSpan(text.text, out, flags, new URLSpanNoUnderline(getString(text), run));
        } else if (text instanceof TL_iv.textBotCommand) {
            formatTextAndSetSpan(text.text, out, flags, new URLSpanBotCommand(getString(text), isOut() ? 1 : 0));
        } else if (text instanceof TL_iv.textCashtag) {
            final TLRPC.TL_messageEntityCashtag entity = new TLRPC.TL_messageEntityCashtag();
            final TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
            run.urlEntity = entity;
            formatTextAndSetSpan(text.text, out, flags, new URLSpanNoUnderline(getString(text), run));
        } else if (text instanceof TL_iv.textAutoUrl) {
            formatTextAndSetSpan(text.text, out, flags, new URLSpanReplacement(getString(text), getTextStyleRun(TextStyleSpan.FLAG_STYLE_TEXT_URL)));
        } else if (text instanceof TL_iv.textAutoEmail) {
            final String email = getString(text);
            formatTextAndSetSpan(text.text, out, flags, new URLSpanReplacement("mailto:" + email, getTextStyleRun(TextStyleSpan.FLAG_STYLE_TEXT_URL)));
        } else if (text instanceof TL_iv.textAutoPhone) {
            final String phone = getString(text);
            String tel = PhoneFormat.stripExceptNumbers(phone);
            if (phone.startsWith("+")) {
                tel = "+" + tel;
            }
            formatTextAndSetSpan(text.text, out, flags, new URLSpanReplacement("tel:" + tel, getTextStyleRun(TextStyleSpan.FLAG_STYLE_TEXT_URL)));
        } else if (text instanceof TL_iv.textBankCard) {
            final String bankCard = getString(text);
            formatTextAndSetSpan(text.text, out, flags, new URLSpanNoUnderline("card:" + bankCard));
        } else if (text instanceof TL_iv.textMentionName) {
            final TL_iv.textMentionName mentionName = (TL_iv.textMentionName) text;
            formatTextAndSetSpan(text.text, out, flags, new URLSpanUserMention("" + mentionName.user_id, isOut() ? 1 : 0));
        } else if (text instanceof TL_iv.textDate) {
            final TL_iv.textDate textDate = (TL_iv.textDate) text;
            final TLRPC.TL_messageEntityFormattedDate entity = new TLRPC.TL_messageEntityFormattedDate();
            entity.relative = textDate.relative;
            entity.short_time = textDate.short_time;
            entity.long_time = textDate.long_time;
            entity.short_date = textDate.short_date;
            entity.long_date = textDate.long_date;
            entity.day_of_week = textDate.day_of_week;
            entity.date = textDate.date;
            flags |= TEXT_FLAG_URL;
            formatTextAndSetSpan(text.text, out, flags, new StyleSpan(this, flags), new FormattedDateSpan(getString(text), null, entity));
        } else if (text instanceof TL_iv.textButton) {
            final TL_iv.textButton textButton = (TL_iv.textButton) text;
            final int start = out.length();
            //formatText(text.text, out, flags);
            out.append("*");
            RichButtonSpan span = new RichButtonSpan(this, maxWidth, textButton);
            span.scale = 1.2f;
            out.setSpan(span, start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return out;
    }
    public static String getString(TL_iv.RichText text) {
        final StringBuilder sb = new StringBuilder();
        getString(text, sb);
        return sb.toString();
    }
    public static void getString(TL_iv.RichText text, StringBuilder out) {
        if (text instanceof TL_iv.textPlain) {
            out.append(((TL_iv.textPlain) text).text);
        } else if (text instanceof TL_iv.textDiff) {
            final TL_iv.textDiff textDiff = (TL_iv.textDiff) text;
            if (!RichTextStyle.isEmpty(textDiff.text)) {
                getString(textDiff.text, out);
            } else if (!RichTextStyle.isEmpty(textDiff.old_text)) {
                getString(textDiff.old_text, out);
            }
        } else if (text instanceof TL_iv.textConcat) {
            for (int i = 0; i < text.texts.size(); ++i) {
                getString(text.texts.get(i), out);
            }
        } else if (text.text != null) {
            getString(text.text, out);
        }
    }

    public static class AnchorSpan extends CharacterStyle {
        public final String name;
        public AnchorSpan(String name) {
            this.name = name;
        }
        @Override
        public void updateDrawState(TextPaint tp) {}
    }

    public static class StyleSpan extends MetricAffectingSpan {
        public final RichMessageLayout root;
        public final int flags;
        public final boolean metricsOnly;
        private boolean fullSizeTableEmoji;
        public StyleSpan(RichMessageLayout root, int flags) {
            this(root, flags, false);
        }
        public StyleSpan(RichMessageLayout root, int flags, boolean metricsOnly) {
            this.root = root;
            this.flags = flags;
            this.metricsOnly = metricsOnly;
        }

        public void applyStyle(TextPaint p) {
            final Typeface typeface = getTypeface();
            if (typeface != null) {
                p.setTypeface(typeface);
            }

            int textSize = getTextSize();
            if (hasFlag(flags, TEXT_FLAG_SUBSCRIPT | TEXT_FLAG_SUPERSCRIPT))
                textSize -= dp(4);
            p.setTextSize(textSize);

            if (!metricsOnly) {
                int paintFlags = p.getFlags();
                paintFlags = setFlag(paintFlags, Paint.UNDERLINE_TEXT_FLAG,   hasFlag(flags, TEXT_FLAG_UNDERLINE));
                paintFlags = setFlag(paintFlags, Paint.STRIKE_THRU_TEXT_FLAG, hasFlag(flags, TEXT_FLAG_STRIKETHROUGH));
                p.setFlags(paintFlags);

                if ((flags & TEXT_FLAG_BLOCKS) != TEXT_FLAG_BLOCK_CODE)
                    p.setColor(getTextColor());
            }

            if (hasFlag(flags, TEXT_FLAG_SUPERSCRIPT)) {
                p.baselineShift -= dp(6.0f);
            } else if (hasFlag(flags, TEXT_FLAG_SUBSCRIPT)) {
                p.baselineShift += dp(2.0f);
            }
        }

        public int getTextSize() {
            final int block = flags & TEXT_FLAG_BLOCKS;
            final int baseSize = SharedConfig.fontSize;
            if (block == TEXT_FLAG_BLOCK_TABLE && fullSizeTableEmoji) {
                return dp(baseSize);
            }
            switch (block) {
                case TEXT_FLAG_BLOCK_HEADING1: return dp(baseSize + 3);
                case TEXT_FLAG_BLOCK_HEADING2: return dp(baseSize + 2);
                case TEXT_FLAG_BLOCK_HEADING3: return dp(baseSize + 1);
                case TEXT_FLAG_BLOCK_HEADING4: return dp(baseSize);
                case TEXT_FLAG_BLOCK_HEADING5: return dp(baseSize - 1);
                case TEXT_FLAG_BLOCK_HEADING6: return dp(baseSize - 2);

                case TEXT_FLAG_BLOCK_BUTTON:
                case TEXT_FLAG_BLOCK_FOOTER:   return dp(baseSize - 2);
                case TEXT_FLAG_BLOCK_TABLE:    return dp(Math.max(8, baseSize - 2));
                case TEXT_FLAG_BLOCK_TABLE_TITLE:
                                               return dp(baseSize - 2);
                case TEXT_FLAG_BLOCK_CODE:     return dp(baseSize - 1);
                case TEXT_FLAG_BLOCK_QUOTE:
                case TEXT_FLAG_BLOCK_QUOTE_CAPTION:
                case TEXT_FLAG_BLOCK_PULLQUOTE:
                                               return dp(baseSize - 2);
                case TEXT_FLAG_BLOCK_CAPTION:  return dp(baseSize - 2);
            }
            return dp(baseSize);
        }

        public int getTextColor() {
            final int block = flags & TEXT_FLAG_BLOCKS;
            if (block == TEXT_FLAG_BLOCK_QUOTE_CAPTION) {
                return root.getThemedColor(root.isOut() ? Theme.key_chat_outReplyNameText : Theme.key_chat_inReplyNameText);
            }
            if (block == TEXT_FLAG_BLOCK_CAPTION) {
                return Theme.multAlpha(root.getThemedColor(root.isOut() ? Theme.key_chat_messageTextOut : Theme.key_chat_messageTextIn), .5f);
            }
            return root.getThemedColor(root.isOut() ? Theme.key_chat_messageTextOut : Theme.key_chat_messageTextIn);
        }

        public Typeface getTypeface() {
            final int block = flags & TEXT_FLAG_BLOCKS;
            if (block == TEXT_FLAG_BLOCK_CODE) {
                return Typeface.MONOSPACE;
            } else if (block == TEXT_FLAG_BLOCK_QUOTE_CAPTION) {
                return AndroidUtilities.bold();
            } else if (block >= 1 && block <= 6) {
                if (hasFlag(flags, TEXT_FLAG_ITALIC)) {
                    return AndroidUtilities.getTypeface("fonts/mw_bolditalic.ttf");
                }
                return AndroidUtilities.getTypeface("fonts/mw_bold.ttf");
            } else if (hasFlag(flags, TEXT_FLAG_MONO)) {
                return Typeface.MONOSPACE;
            }
            final boolean bold = hasFlag(flags, TEXT_FLAG_BOLD);
            final boolean italic = hasFlag(flags, TEXT_FLAG_ITALIC) || block == TEXT_FLAG_BLOCK_PULLQUOTE;
            if (bold && italic) {
                return AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM_ITALIC);
            } else if (bold) {
                return AndroidUtilities.bold();
            } else if (italic) {
                return AndroidUtilities.getTypeface("fonts/ritalic.ttf");
            } else {
                return null;
            }
        }

        @Override
        public void updateMeasureState(@NonNull TextPaint paint) {
            applyStyle(paint);
        }
        @Override
        public void updateDrawState(TextPaint paint) {
            applyStyle(paint);
        }
    }

    public static class Text implements TextSelectionHelper.TextLayoutBlock, TableLayout.CellText {

        private static final int EMOJI_LINE_HEIGHT_MIN_PERCENT = 70;

        public final RichMessageLayout root;
        public final StaticLayout layout;
        public int blockX, blockY;
        public int x, y, row;
        public int left, right;
        public int lastLineRight;
        private boolean drawAtOrigin;
        private int emojiOnlyCount;
        public boolean doNotInvalidateEmojiInParent;

        public final List<SpoilerEffect> spoilers = new ArrayList<>();
        public final Stack<SpoilerEffect> spoilersPool = new Stack<>();
        public final AtomicReference<Layout> spoilersPatchedTextLayout = new AtomicReference<>();
        public AnimatedEmojiSpan.EmojiGroupedSpans animatedEmojiStack;

        public LinkPath markPath;
        private static Paint markPaint;

        private LinkPath translationLoadingPath;
        private LoadingDrawable translationLoadingDrawable;

        public LinkSpanDrawable.LinkCollector linkCollector;
        private LinkSpanDrawable<CharacterStyle> pressedLinkDrawable;
        private Runnable longPressRunnable;
        private boolean longPressFired;

        public boolean isPressingLink() { return pressedLink != null || pressedButtonSpan != null; }

        public boolean fillFoundLink(CharacterStyle link, FoundLink out) {
            if (!(layout.getText() instanceof Spanned)) return false;
            final Spanned spanned = (Spanned) layout.getText();
            final int s = spanned.getSpanStart(link);
            final int e = spanned.getSpanEnd(link);
            if (s < 0 || e <= s) return false;
            out.layout = layout;
            out.start = s;
            out.end = e;
            out.originalWidth = layout.getWidth();
            return true;
        }

        public Text(RichMessageLayout root, CharSequence text, int width) {
            this(root, text, width, Layout.Alignment.ALIGN_NORMAL);
        }

        public Text(RichMessageLayout root, CharSequence text, int width, Layout.Alignment alignment) {
            this(root, text, width, alignment, 1f);
        }

        public Text(RichMessageLayout root, CharSequence text, int width, Layout.Alignment alignment, float lineSpacingMultiplier) {
            this.root = root;
            Paint.FontMetricsInt emojiMetrics = root.textPaint.getFontMetricsInt();
            boolean heading = false;
            if (text instanceof Spanned) {
                for (StyleSpan span : ((Spanned) text).getSpans(0, text.length(), StyleSpan.class)) {
                    final int block = span.flags & TEXT_FLAG_BLOCKS;
                    if (block >= TEXT_FLAG_BLOCK_HEADING1 && block <= TEXT_FLAG_BLOCK_HEADING6) {
                        final TextPaint headingPaint = new TextPaint(root.textPaint);
                        span.applyStyle(headingPaint);
                        emojiMetrics = headingPaint.getFontMetricsInt();
                        heading = true;
                        break;
                    }
                }
            }
            text = Emoji.replaceEmoji(text, emojiMetrics, false, heading ? .85f : 1f);
            text = configureEmojiLineHeights(text, root.textPaint);
            emojiOnlyCount = RichTextStyle.emojiOnlyCount(text);
            if (emojiOnlyCount == 0 && text instanceof Spanned) {
                final Spanned spanned = (Spanned) text;
                final RichButtonSpan[] buttonSpans = spanned.getSpans(0, text.length(), RichButtonSpan.class);
                if (buttonSpans.length > 0) {
                    Arrays.sort(buttonSpans, (a, b) -> spanned.getSpanStart(b) - spanned.getSpanStart(a));
                    final SpannableStringBuilder expandedText = new SpannableStringBuilder(text);
                    boolean emojiOnlyButtons = true;
                    for (RichButtonSpan buttonSpan : buttonSpans) {
                        final int start = spanned.getSpanStart(buttonSpan);
                        final int end = spanned.getSpanEnd(buttonSpan);
                        if (start < 0 || end <= start || buttonSpan.getButton().text.getEmojiOnlyCount() == 0) {
                            emojiOnlyButtons = false;
                            break;
                        }
                        expandedText.replace(start, end, buttonSpan.getButton().text.layout.getText());
                    }
                    if (emojiOnlyButtons) {
                        emojiOnlyCount = RichTextStyle.emojiOnlyCount(expandedText);
                    }
                }
            }
            if (emojiOnlyCount > 0 && text instanceof Spanned) {
                for (StyleSpan span : ((Spanned) text).getSpans(0, text.length(), StyleSpan.class)) {
                    if ((span.flags & TEXT_FLAG_BLOCKS) == TEXT_FLAG_BLOCK_TABLE) {
                        span.fullSizeTableEmoji = true;
                    }
                }
            }
            boolean paragraph = true;
            if (text instanceof Spanned) {
                for (StyleSpan span : ((Spanned) text).getSpans(0, text.length(), StyleSpan.class)) {
                    if ((span.flags & TEXT_FLAG_BLOCKS) != 0) {
                        paragraph = false;
                        break;
                    }
                }
            }
            layout = MessageObject.makeStaticLayout(text, root.textPaint, width, lineSpacingMultiplier, 0, false, alignment);

            left = width; right = 0;
            for (int i = 0; i < layout.getLineCount(); ++i) {
                left = Math.min(left, (int) Math.floor(layout.getLineLeft(i)));
                right = Math.max(right, (int) Math.ceil(layout.getLineRight(i)));
            }
            lastLineRight = 0;
            if (layout.getLineCount() > 0) {
                lastLineRight = (int) Math.ceil(layout.getLineRight(layout.getLineCount() - 1));
            }

            SpoilerEffect.addSpoilers(null, layout, spoilersPool, spoilers);

            if (layout.getText() instanceof Spanned) {
                final Spanned spanned = (Spanned) layout.getText();
                final StyleSpan[] styleSpans = spanned.getSpans(0, spanned.length(), StyleSpan.class);
                LinkPath path = null;
                for (StyleSpan span : styleSpans) {
                    if (!hasFlag(span.flags, TEXT_FLAG_MARKED)) continue;
                    final int start = spanned.getSpanStart(span);
                    final int end = spanned.getSpanEnd(span);
                    if (start < 0 || end <= start) continue;
                    if (path == null) {
                        path = new LinkPath(true);
                        path.setAllowReset(false);
                    }
                    path.setCurrentLayout(layout, start, 0);
                    int shift = 0;
                    if (hasFlag(span.flags, TEXT_FLAG_SUPERSCRIPT)) shift = -dp(6);
                    else if (hasFlag(span.flags, TEXT_FLAG_SUBSCRIPT)) shift = dp(2);
                    path.setBaselineShift(shift != 0 ? shift + dp(shift > 0 ? 5 : -2) : 0);
                    layout.getSelectionPath(start, end, path);
                }
                if (path != null) {
                    path.setAllowReset(true);
                    markPath = path;
                }
            }
        }

        private static CharSequence configureEmojiLineHeights(CharSequence text, TextPaint paint) {
            if (!(text instanceof Spanned)) {
                return text;
            }

            final SpannableStringBuilder result = new SpannableStringBuilder(text);
            for (AnimatedEmojiSpan span : result.getSpans(0, result.length(), AnimatedEmojiSpan.class)) {
                span.setPreserveFontMetrics(true);
            }
            for (Emoji.EmojiSpan span : result.getSpans(0, result.length(), Emoji.EmojiSpan.class)) {
                span.setPreserveFontMetrics(true);
            }
            for (RichButtonSpan span : result.getSpans(0, result.length(), RichButtonSpan.class)) {
                if (span.getButton().text.getEmojiOnlyCount() > 0) {
                    span.preserveFontMetrics = true;
                }
            }
            int lineStart = 0;
            while (lineStart < result.length()) {
                int lineEnd = TextUtils.indexOf(result, '\n', lineStart);
                final boolean hasNewline = lineEnd >= 0;
                if (!hasNewline) {
                    lineEnd = result.length();
                }

                final EmojiLineMetrics metrics = measureEmojiLine(
                    result, lineStart, lineEnd, paint
                );
                if (metrics.allowsEmojiLineHeight() && metrics.emojiSide > 0) {
                    allowEmojiLineHeight(result, lineStart, lineEnd, metrics.emojiSide);
                }

                if (!hasNewline) {
                    break;
                }
                lineStart = lineEnd + 1;
            }
            return result;
        }

        private static void allowEmojiLineHeight(
                Spanned text, int start, int end, int minimumLineHeight) {
            for (AnimatedEmojiSpan span : text.getSpans(start, end, AnimatedEmojiSpan.class)) {
                final int spanStart = text.getSpanStart(span);
                if (spanStart >= start && spanStart < end) {
                    span.setMinimumLineHeight(minimumLineHeight);
                }
            }
            for (Emoji.EmojiSpan span : text.getSpans(start, end, Emoji.EmojiSpan.class)) {
                final int spanStart = text.getSpanStart(span);
                if (spanStart >= start && spanStart < end) {
                    span.setMinimumLineHeight(minimumLineHeight);
                }
            }
            for (RichButtonSpan span : text.getSpans(start, end, RichButtonSpan.class)) {
                final int spanStart = text.getSpanStart(span);
                if (span.button.text.getEmojiOnlyCount() > 0
                        && spanStart >= start && spanStart < end) {
                    span.minimumLineHeight = minimumLineHeight;
                }
            }
        }

        // Span ranges are collected once, then the row is scanned once. Whitespace is layout
        // separation rather than content, and link-button labels are expanded into their inner
        // emoji/text counts. This keeps detection O(characters + spans) for each logical row.
        private static EmojiLineMetrics measureEmojiLine(
                Spanned text, int start, int end, TextPaint paint) {
            final EmojiLineMetrics result = new EmojiLineMetrics();
            final int length = end - start;
            if (length <= 0) {
                return result;
            }

            final AnimatedEmojiSpan[] animated = text.getSpans(start, end, AnimatedEmojiSpan.class);
            final Emoji.EmojiSpan[] standard = text.getSpans(start, end, Emoji.EmojiSpan.class);
            final int[] emojiEnds = animated.length == 0 && standard.length == 0
                ? null : new int[length];
            for (AnimatedEmojiSpan span : animated) {
                final int spanStart = text.getSpanStart(span);
                final int spanEnd = Math.min(end, text.getSpanEnd(span));
                if (spanStart >= start && spanStart < end && spanEnd > spanStart) {
                    emojiEnds[spanStart - start] = Math.max(emojiEnds[spanStart - start], spanEnd);
                    result.emojiSide = Math.max(result.emojiSide,
                        span.getSize(paint, text, spanStart, spanEnd, null) + 1);
                }
            }
            for (Emoji.EmojiSpan span : standard) {
                final int spanStart = text.getSpanStart(span);
                final int spanEnd = Math.min(end, text.getSpanEnd(span));
                if (spanStart >= start && spanStart < end && spanEnd > spanStart) {
                    emojiEnds[spanStart - start] = Math.max(emojiEnds[spanStart - start], spanEnd);
                    result.emojiSide = Math.max(result.emojiSide,
                        span.getSize(paint, text, spanStart, spanEnd, null));
                }
            }

            RichButtonSpan[] buttonsAt = null;
            final RichButtonSpan[] buttons = text.getSpans(start, end, RichButtonSpan.class);
            if (buttons.length > 0) {
                for (RichButtonSpan span : buttons) {
                    final int spanStart = text.getSpanStart(span);
                    if (span.button.text.getEmojiOnlyCount() > 0
                            && spanStart >= start && spanStart < end) {
                        if (buttonsAt == null) {
                            buttonsAt = new RichButtonSpan[length];
                        }
                        buttonsAt[spanStart - start] = span;
                    }
                }
            }

            // The common mixed-text row has neither emoji spans nor link buttons. It can never
            // meet the threshold, so avoid both the character scan and line-sized allocations.
            if (emojiEnds == null && buttonsAt == null) {
                return result;
            }

            int offset = start;
            while (offset < end) {
                final int relativeOffset = offset - start;
                final RichButtonSpan buttonSpan = buttonsAt == null ? null : buttonsAt[relativeOffset];
                if (buttonSpan != null && buttonSpan.button.text.layout.getText() instanceof Spanned) {
                    final Spanned label = (Spanned) buttonSpan.button.text.layout.getText();
                    final EmojiLineMetrics labelMetrics = measureEmojiLine(
                        label, 0, label.length(), buttonSpan.button.text.layout.getPaint()
                    );
                    result.emojiCount += labelMetrics.emojiCount;
                    result.contentCount += Math.max(1, labelMetrics.contentCount);
                    result.emojiSide = Math.max(result.emojiSide, labelMetrics.emojiSide);
                    offset = Math.min(end, text.getSpanEnd(buttonSpan));
                    continue;
                }

                final int emojiEnd = emojiEnds == null ? 0 : emojiEnds[relativeOffset];
                if (emojiEnd > offset) {
                    result.emojiCount++;
                    result.contentCount++;
                    offset = emojiEnd;
                    continue;
                }

                final int codePoint = Character.codePointAt(text, offset);
                if (!Character.isWhitespace(codePoint)) {
                    result.contentCount++;
                }
                offset += Character.charCount(codePoint);
            }
            return result;
        }

        private static class EmojiLineMetrics {
            private int emojiCount;
            private int contentCount;
            private int emojiSide;

            private boolean allowsEmojiLineHeight() {
                return contentCount > 0
                    && (long) emojiCount * 100
                        >= (long) contentCount * EMOJI_LINE_HEIGHT_MIN_PERCENT;
            }
        }

        // inline buttons are ReplacementSpans: they are wider than the single character they
        // occupy, so they are hit-tested by their drawn bounds instead of by text offset
        private RichButtonSpan[] getButtonSpans() {
            if (!(layout.getText() instanceof Spanned)) {
                return null;
            }
            final Spanned spanned = (Spanned) layout.getText();
            return spanned.getSpans(0, spanned.length(), RichButtonSpan.class);
        }

        public void setDrawAtOrigin(boolean value) {
            drawAtOrigin = value;
        }

        @Override
        public int getEmojiOnlyCount() {
            return emojiOnlyCount;
        }

        public int drawLeft() {
            return drawAtOrigin ? 0 : left;
        }

        public int getBaseline() {
            if (layout != null && layout.getLineCount() > 0) {
                return layout.getLineBaseline(0);
            }
            return -1;
        }

        public void draw(Canvas canvas) {
            draw(canvas, view);
        }

        @Override
        public void draw(Canvas canvas, View viewArg) {
            canvas.save();
            canvas.translate(-drawLeft(), 0);
            final int color = root.getThemedColor(root.isOut() ? Theme.key_chat_messageTextOut : Theme.key_chat_messageTextIn);
            root.textPaint.setColor(color);
            root.textPaint.linkColor = root.getThemedColor(root.isOut() ? Theme.key_chat_messageLinkOut : Theme.key_chat_messageLinkIn);
            if (markPath != null) {
                if (markPaint == null) {
                    markPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    markPaint.setPathEffect(LinkPath.getRoundedEffect());
                }
                final int markColor = root.quoteLine.getColor();
                markPaint.setColor((markColor & 0x00ffffff) | 0x33000000);
                canvas.drawPath(markPath, markPaint);
            }
            final View v = viewArg != null ? viewArg : view;
            if (linkCollector != null && linkCollector.draw(canvas) && v != null) {
                v.invalidate();
            }
            SpoilerEffect.renderWithRipple(v, false, color, 0, spoilersPatchedTextLayout, 0, layout, spoilers, canvas, false);
            SquigglyLinesSpan.drawOnText(canvas, layout);
            if (!root.isOverlayActive()) {
                AnimatedEmojiSpan.drawAnimatedEmojis(canvas, layout, animatedEmojiStack, 0, spoilers, 0, 0, 0, 1.0f);
            }
            drawTranslationLoading(canvas);
            canvas.restore();
        }

        private void drawTranslationLoading(Canvas canvas) {
            final float translationLoading = root.translationLoadingValue;
            if (translationLoading <= 0) return;
            final boolean translating = root.isTranslating();

            if (translationLoadingDrawable == null) {
                translationLoadingDrawable = new LoadingDrawable();
                translationLoadingDrawable.setAppearByGradient(true);
                translationLoadingPath = new LinkPath(true);
                translationLoadingPath.setUseCornerPathImplementation(true);
                translationLoadingDrawable.usePath(translationLoadingPath);
                translationLoadingDrawable.setRadiiDp(5);
                translationLoadingDrawable.reset();

                translationLoadingPath.reset();
                translationLoadingPath.setCurrentLayout(layout, 0, 0);
                translationLoadingPath.setAllowReset(false);
                layout.getSelectionPath(0, layout.getText().length(), translationLoadingPath);
                translationLoadingPath.setAllowReset(true);
                translationLoadingPath.closeRects();
                translationLoadingDrawable.updateBounds();
            }

            if (translating && (translationLoadingDrawable.isDisappearing() || translationLoadingDrawable.isDisappeared())) {
                translationLoadingDrawable.reset();
                translationLoadingDrawable.resetDisappear();
            } else if (!translating && !translationLoadingDrawable.isDisappearing() && !translationLoadingDrawable.isDisappeared()) {
                translationLoadingDrawable.disappear();
            }

            final int linkColor = root.getThemedColor(root.isOut() ? Theme.key_chat_messageLinkOut : Theme.key_chat_messageLinkIn);
            translationLoadingDrawable.setColors(
                Theme.multAlpha(linkColor, .05f),
                Theme.multAlpha(linkColor, .15f),
                Theme.multAlpha(linkColor, .1f),
                Theme.multAlpha(linkColor, .3f)
            );
            translationLoadingDrawable.setAlpha((int) (0xFF * translationLoading));
            translationLoadingDrawable.draw(canvas);
        }

        public void drawFade(Canvas canvas, int lineIndex, float xPosition) {
            canvas.save();
            canvas.translate(-drawLeft(), 0);
            final int color = root.getThemedColor(root.isOut() ? Theme.key_chat_messageTextOut : Theme.key_chat_messageTextIn);
            root.textPaint.setColor(color);
            root.textPaint.linkColor = root.getThemedColor(root.isOut() ? Theme.key_chat_messageLinkOut : Theme.key_chat_messageLinkIn);
            final View v = view;
            MultiLayoutTypingAnimator.drawLayoutWithLastLineFade(canvas, layout, lineIndex, xPosition, c -> {
                SpoilerEffect.renderWithRipple(v, false, color, 0, spoilersPatchedTextLayout, 0, layout, spoilers, c, false);
                SquigglyLinesSpan.drawOnText(c, layout);
                AnimatedEmojiSpan.drawAnimatedEmojis(c, layout, animatedEmojiStack, 0, spoilers, 0, 0, 0, 1f);
            });
            canvas.restore();
        }

        private SpoilerEffect pressedSpoiler;
        private CharacterStyle pressedLink;
        private int pressedLinkStart, pressedLinkEnd;
        private AnimatedEmojiSpan pressedEmoji;
        private RichButtonSpan pressedButtonSpan;

        public boolean onTouchEvent(MotionEvent event) {
            final int act = event.getActionMasked();
            final int lx = (int) event.getX() + drawLeft();
            final int ly = (int) event.getY();

            if (act == MotionEvent.ACTION_DOWN) {
                pressedSpoiler = null;
                pressedLink = null;
                pressedEmoji = null;
                pressedButtonSpan = null;

                for (SpoilerEffect eff : spoilers) {
                    if (eff.getBounds().contains(lx, ly)) {
                        pressedSpoiler = eff;
                        return true;
                    }
                }

                // checked before ClickableSpan: a link inside the label must not steal the press
                final RichButtonSpan[] buttonSpans = getButtonSpans();
                if (buttonSpans != null) {
                    for (RichButtonSpan span : buttonSpans) {
                        if (span.contains(lx, ly)) {
                            if (span.isDisabled()) {
                                return true;
                            }
                            pressedButtonSpan = span;
                            longPressFired = false;
                            span.setPressed(true);
                            if (view != null) view.invalidate();
                            scheduleLongPress();
                            return true;
                        }
                    }
                }

                if (layout.getText() instanceof Spannable && ly >= 0 && ly < layout.getHeight()) {
                    final int line = layout.getLineForVertical(ly);
                    final float lineLeft = layout.getLineLeft(line);
                    final float lineRight = lineLeft + layout.getLineWidth(line);
                    if (lx >= lineLeft && lx <= lineRight) {
                        final int off = layout.getOffsetForHorizontal(line, lx);
                        final Spannable buffer = (Spannable) layout.getText();
                        final ClickableSpan[] clickables = buffer.getSpans(off, off, ClickableSpan.class);
                        if (clickables != null && clickables.length > 0) {
                            pressedLink = clickables[0];
                            pressedLinkStart = buffer.getSpanStart(pressedLink);
                            pressedLinkEnd = buffer.getSpanEnd(pressedLink);
                            longPressFired = false;
                            final LinkSpanDrawable<CharacterStyle> drawable =
                                new LinkSpanDrawable<>(pressedLink, root.resourcesProvider, lx, ly, false);
                            final LinkPath path = drawable.obtainNewPath();
                            path.setCurrentLayout(layout, pressedLinkStart, 0);
                            layout.getSelectionPath(pressedLinkStart, pressedLinkEnd, path);
                            pressedLinkDrawable = drawable;
                            if (linkCollector == null)
                                linkCollector = new LinkSpanDrawable.LinkCollector(view);
                            linkCollector.addLink(drawable);
                            if (view != null) view.invalidate();
                            scheduleLongPress();
                            return true;
                        }
                        StyleSpan monoSpan = null;
                        final StyleSpan[] styles = buffer.getSpans(off, off, StyleSpan.class);
                        if (styles != null) {
                            for (StyleSpan ss : styles) {
                                if (hasFlag(ss.flags, TEXT_FLAG_MONO)) {
                                    monoSpan = ss;
                                    break;
                                }
                            }
                        }
                        if (monoSpan != null) {
                            int monoStart = buffer.getSpanStart(monoSpan);
                            int monoEnd = buffer.getSpanEnd(monoSpan);
                            while (monoStart > 0) {
                                StyleSpan prev = null;
                                for (StyleSpan ss : buffer.getSpans(monoStart - 1, monoStart - 1, StyleSpan.class)) {
                                    if (hasFlag(ss.flags, TEXT_FLAG_MONO)) { prev = ss; break; }
                                }
                                if (prev == null) break;
                                final int ps = buffer.getSpanStart(prev);
                                if (ps >= monoStart) break;
                                monoStart = ps;
                            }
                            while (monoEnd < buffer.length()) {
                                StyleSpan next = null;
                                for (StyleSpan ss : buffer.getSpans(monoEnd, monoEnd, StyleSpan.class)) {
                                    if (hasFlag(ss.flags, TEXT_FLAG_MONO)) { next = ss; break; }
                                }
                                if (next == null) break;
                                final int ne = buffer.getSpanEnd(next);
                                if (ne <= monoEnd) break;
                                monoEnd = ne;
                            }
                            pressedLink = monoSpan;
                            pressedLinkStart = monoStart;
                            pressedLinkEnd = monoEnd;
                            longPressFired = false;
                            final LinkSpanDrawable<CharacterStyle> drawable =
                                new LinkSpanDrawable<>(monoSpan, root.resourcesProvider, lx, ly, true);
                            final LinkPath path = drawable.obtainNewPath();
                            path.setCurrentLayout(layout, monoStart, 0);
                            layout.getSelectionPath(monoStart, monoEnd, path);
                            pressedLinkDrawable = drawable;
                            if (linkCollector == null)
                                linkCollector = new LinkSpanDrawable.LinkCollector(view);
                            linkCollector.addLink(drawable);
                            if (view != null) view.invalidate();
                            scheduleLongPress();
                            return true;
                        }
                        final AnimatedEmojiSpan[] emojis = buffer.getSpans(off, off, AnimatedEmojiSpan.class);
                        if (emojis != null && emojis.length > 0) {
                            pressedEmoji = emojis[0];
                            return true;
                        }
                    }
                }
                return false;
            }
            if (act == MotionEvent.ACTION_MOVE) {
                if (pressedButtonSpan != null) {
                    // drop the press once the finger leaves the pill, same as a bot button row
                    if (!pressedButtonSpan.contains(lx, ly)) {
                        cancelLongPress();
                        pressedButtonSpan.setPressed(false);
                        pressedButtonSpan = null;
                        longPressFired = false;
                    }
                    return true;
                }
                return false;
            }
            if (act == MotionEvent.ACTION_UP) {
                if (pressedButtonSpan != null) {
                    cancelLongPress();
                    final RichButtonSpan span = pressedButtonSpan;
                    pressedButtonSpan = null;
                    span.setPressed(false);
                    if (!longPressFired) {
                        if (view != null) {
                            view.playSoundEffect(SoundEffectConstants.CLICK);
                        }
                        span.didPress(root.getCell(), root.getDelegate(), false);
                    }
                    longPressFired = false;
                    return true;
                }
                if (pressedSpoiler != null) {
                    revealSpoilers(lx, ly);
                    pressedSpoiler = null;
                    return true;
                }
                if (pressedLink != null) {
                    cancelLongPress();
                    if (!longPressFired) {
                        dispatchLinkClick(pressedLink, false);
                    }
                    if (linkCollector != null) {
                        linkCollector.clear();
                    }
                    pressedLink = null;
                    pressedLinkDrawable = null;
                    longPressFired = false;
                    return true;
                }
                if (pressedEmoji != null) {
                    final AnimatedEmojiSpan e = pressedEmoji;
                    pressedEmoji = null;
                    final ChatMessageCell cell = root.getCell();
                    final ChatMessageCell.ChatMessageCellDelegate dele = root.getDelegate();
                    if (cell != null && dele != null) {
                        if (view != null) {
                            view.playSoundEffect(SoundEffectConstants.CLICK);
                        }
                        dele.didPressAnimatedEmoji(cell, e);
                    }
                    return true;
                }
                return false;
            }
            if (act == MotionEvent.ACTION_CANCEL) {
                pressedSpoiler = null;
                pressedEmoji = null;
                if (pressedButtonSpan != null) {
                    cancelLongPress();
                    pressedButtonSpan.setPressed(false);
                    pressedButtonSpan = null;
                    longPressFired = false;
                }
                if (pressedLink != null) {
                    cancelLongPress();
                    if (linkCollector != null) {
                        linkCollector.clear();
                    }
                    pressedLink = null;
                    pressedLinkDrawable = null;
                    longPressFired = false;
                }
            }
            return false;
        }

        private void scheduleLongPress() {
            cancelLongPress();
            longPressRunnable = () -> {
                longPressRunnable = null;
                if (pressedButtonSpan != null) {
                    longPressFired = true;
                    if (view != null) {
                        try {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                        } catch (Exception ignore) {}
                    }
                    pressedButtonSpan.didPress(root.getCell(), root.getDelegate(), true);
                    return;
                }
                if (pressedLink == null) return;
                longPressFired = true;
                if (view != null) {
                    try {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    } catch (Exception ignore) {}
                }
                dispatchLinkClick(pressedLink, true);
                if (linkCollector != null) {
                    linkCollector.clear();
                }
                pressedLinkDrawable = null;
            };
            AndroidUtilities.runOnUIThread(longPressRunnable, ViewConfiguration.getLongPressTimeout());
        }

        private void cancelLongPress() {
            if (longPressRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(longPressRunnable);
                longPressRunnable = null;
            }
        }

        private void dispatchLinkClick(CharacterStyle span, boolean longPress) {
            if (span == null) return;
            if (!longPress && span instanceof URLSpan) {
                final String url = ((URLSpan) span).getURL();
                if (url != null && url.startsWith("#") && root.handleAnchorClick(url)) {
                    if (view != null) {
                        view.playSoundEffect(SoundEffectConstants.CLICK);
                    }
                    return;
                }
            }
            CharacterStyle dispatched = span;
            if (span instanceof StyleSpan && hasFlag(((StyleSpan) span).flags, TEXT_FLAG_MONO)) {
                dispatched = new URLSpanMono(layout.getText(), pressedLinkStart, pressedLinkEnd, (byte) (root.isOut() ? 1 : 0));
            }
            final ChatMessageCell.ChatMessageCellDelegate delegate = root.getDelegate();
            final ChatMessageCell cell = root.getCell();
            if (delegate != null && cell != null) {
                if (view != null && !longPress) {
                    view.playSoundEffect(SoundEffectConstants.CLICK);
                }
                delegate.didPressUrl(cell, dispatched, longPress);
            } else if (!longPress) {
                if (view != null && span instanceof ClickableSpan) {
                    view.playSoundEffect(SoundEffectConstants.CLICK);
                    ((ClickableSpan) span).onClick(view);
                }
            }
        }

        private void revealSpoilers(int x, int y) {
            if (pressedSpoiler == null) return;
            final float w = layout.getWidth();
            final float h = layout.getHeight();
            final float rad = (float) Math.sqrt(w * w + h * h);
            final View v = view;
            final RichMessageLayout r = root;

            pressedSpoiler.setOnRippleEndCallback(() -> {
                if (v == null) return;
                v.post(() -> {
                    if (r != null) {
                        if (r.messageObject != null) {
                            r.messageObject.isSpoilersRevealed = true;
                        }
                        for (TextSelectionHelper.TextLayoutBlock tb : r.textBlocks) {
                            if (tb instanceof Text) {
                                ((Text) tb).spoilers.clear();
                            }
                        }
                    } else {
                        spoilers.clear();
                    }
                    v.invalidate();
                });
            });

            for (SpoilerEffect eff : spoilers) {
                eff.startRipple(x, y, rad);
            }
            if (v != null) {
                v.playSoundEffect(SoundEffectConstants.CLICK);
            }
        }

        public int getHeight() {
            return layout.getHeight();
        }

        public int getMinWidth() {
            return Math.max(0, right - left);
        }

        public int getLastLineWidth() {
            return Math.max(0, lastLineRight - left);
        }

        public void onAttachedToWindow() {
            animatedEmojiStack = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, view, root.invalidateAnimatedEmojiInParent && !doNotInvalidateEmojiInParent, animatedEmojiStack, layout);
            if (linkCollector != null) {
                linkCollector.setParent(view);
            }
            final RichButtonSpan[] buttonSpans = getButtonSpans();
            if (buttonSpans != null) {
                for (RichButtonSpan span : buttonSpans) {
                    span.attach(view);
                }
            }
        }
        public void onDetachedFromWindow() {
            AnimatedEmojiSpan.release(view, animatedEmojiStack);
            animatedEmojiStack = null;
            if (linkCollector != null) {
                linkCollector.setParent(null);
            }
            final RichButtonSpan[] buttonSpans = getButtonSpans();
            if (buttonSpans != null) {
                for (RichButtonSpan span : buttonSpans) {
                    span.detach(view);
                }
            }
        }

        public void refreshAnimatedEmoji(int cacheType) {
            if (view == null) return;
            AnimatedEmojiSpan.release(view, animatedEmojiStack);
            animatedEmojiStack = null;
            animatedEmojiStack = AnimatedEmojiSpan.update(cacheType, view, root.invalidateAnimatedEmojiInParent && !doNotInvalidateEmojiInParent, animatedEmojiStack, layout);
        }

        public void setBlockX(int x) {
            this.blockX = x;
        }
        public void setBlockY(int y) {
            this.blockY = y;
        }
        @Override
        public void setX(int x) {
            this.x = x;
        }
        @Override
        public void setY(int y) {
            this.y = y;
        }
        @Override
        public void setRow(int row) {
            this.row = row;
        }
        public Text offset(int x, int y) {
            this.x += x;
            this.y += y;
            return this;
        }

        @Override
        public Layout getLayout() {
            return layout;
        }
        @Override
        public int getX() {
            return blockX + x;
        }
        @Override
        public int getY() {
            return blockY + y;
        }
        @Override
        public int getRow() {
            return row;
        }

        public View view;
        public void attach(View view) {
            if (view == this.view) return;
            if (this.view != null) {
                detach(this.view);
            }
            this.view = view;
            onAttachedToWindow();
        }
        public void detach(View view) {
            if (this.view != view) return;
            if (this.view == null) return;
            this.view = null;
            onDetachedFromWindow();
        }
        public boolean isAttached() {
            return this.view != null;
        }
    }

    public static class RichTextBlock extends RichBlock {

        public final Text text;
        public final Text[] texts;

        @Override
        public void appendAccessibilityText(SpannableStringBuilder sb) {
            appendText(sb, text, texts);
        }

        private final boolean centered;
        protected int contentPaddingTop;
        protected int contentPaddingBottom;
        public int quoteAuthorStart = -1;

        public RichTextBlock(
            RichMessageLayout root,
            Rect padding, int maxWidth,
            CharSequence text
        ) {
            this(root, padding, maxWidth, text, Layout.Alignment.ALIGN_NORMAL);
        }

        public RichTextBlock(
            RichMessageLayout root,
            Rect padding, int maxWidth,
            CharSequence text, Layout.Alignment alignment
        ) {
            super(root, padding, maxWidth);

            this.centered = alignment == Layout.Alignment.ALIGN_CENTER;
            this.text = new Text(root, text, this.maxWidth, alignment);

            this.texts = new Text[1];
            this.texts[0] = this.text;
        }

        public void setContentPadding(int top, int bottom) {
            contentPaddingTop = top;
            contentPaddingBottom = bottom;
            updateListMarkerY();
        }

        @Override
        protected int getContentPaddingTop() {
            return contentPaddingTop;
        }

        @Override
        public boolean forcesTimeToNewLine() {
            return centered;
        }

        private int rtlOffset() {
            if (centered) {
                return (root.getMinWidth() + root.padRight - root.padLeft - text.getMinWidth()) / 2 - padding.left;
            }
            if (!root.isRtl()) return 0;
            return root.getMinWidth() + root.padRight - dp(14) - padding.right - padding.left - text.getMinWidth();
        }

        @Override
        public void onDraw(Canvas canvas) {
            canvas.save();
            canvas.translate(0, contentPaddingTop);
            final int off = rtlOffset();
            if (off != 0) {
                text.setX(padding.left + off - text.left);
                canvas.save();
                canvas.translate(off, 0);
                text.draw(canvas);
                canvas.restore();
            } else {
                text.draw(canvas);
            }
            canvas.restore();
        }

        @Override
        protected void onDrawFaded(Canvas canvas, int lineIndex, float xPosition) {
            canvas.save();
            canvas.translate(0, contentPaddingTop);
            final int off = rtlOffset();
            if (off != 0) {
                text.setX(padding.left + off - text.left);
                canvas.save();
                canvas.translate(off, 0);
                text.drawFade(canvas, lineIndex, xPosition);
                canvas.restore();
            } else {
                text.drawFade(canvas, lineIndex, xPosition);
            }
            canvas.restore();
        }

        @Override
        public Layout getLayout() {
            return text.layout;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            final int off = rtlOffset();
            event.offsetLocation(-off, -contentPaddingTop);
            final boolean h = text.onTouchEvent(event);
            event.offsetLocation(off, contentPaddingTop);
            return h;
        }

        @Override
        public boolean findLink(CharacterStyle link, int blockY, FoundLink out) {
            if (text.fillFoundLink(link, out)) {
                out.x = padding.left + rtlOffset() - text.left;
                out.y = blockY + padding.top + contentPaddingTop;
                return true;
            }
            return false;
        }

        @Override
        public int getHeight() {
            return padding.top + contentPaddingTop + text.getHeight() + contentPaddingBottom + padding.bottom;
        }

        @Override
        public int getMinWidth() {
            return padding.left + text.getMinWidth() + padding.right;
        }

        @Override
        public int getLastLineWidth() {
            return padding.left + text.getLastLineWidth() + padding.right;
        }

        @Override
        protected TextSelectionHelper.TextLayoutBlock[] getText() {
            return this.texts;
        }

        @Override
        protected void placeTexts(int blockX, int blockY, int row) {
            super.placeTexts(blockX, blockY + contentPaddingTop, row);
            final int off = rtlOffset();
            if (off != 0) {
                text.setX(blockX + off - text.left);
            }
        }

        @Override
        protected void onAttachedToWindow() {
            text.attach(view);
        }
        @Override
        protected void onDetachedFromWindow() {
            text.detach(view);
        }
    }

    public static class RichTextWithAuthorBlock extends RichBlock {

        public final Text text;
        public final Text author;
        private final Text[] texts;
        private final boolean centered;

        public RichTextWithAuthorBlock(
            RichMessageLayout root,
            Rect padding, int maxWidth,
            CharSequence text,
            CharSequence author,
            Layout.Alignment alignment
        ) {
            super(root, padding, maxWidth);
            this.centered = alignment == Layout.Alignment.ALIGN_CENTER;
            this.text = new Text(root, text, this.maxWidth, alignment);
            this.author = !TextUtils.isEmpty(author) ? new Text(root, author, this.maxWidth, alignment) : null;
            this.texts = this.author == null ? new Text[] { this.text } : new Text[] { this.text, this.author };
        }

        private int gap() {
            return author != null ? dp(2) : 0;
        }

        private int offset(Text value) {
            if (centered) {
                return (root.getMinWidth() + root.padRight - root.padLeft - value.getMinWidth()) / 2 - padding.left;
            }
            if (!root.isRtl()) return 0;
            return root.getMinWidth() + root.padRight - dp(14) - padding.right - padding.left - value.getMinWidth();
        }

        public int getTextWidth() {
            return Math.max(text.getMinWidth(), author != null ? author.getMinWidth() : 0);
        }

        @Override
        public void appendAccessibilityText(SpannableStringBuilder sb) {
            appendText(sb, null, texts);
        }

        @Override
        public int getHeight() {
            return padding.top + text.getHeight() + gap() + (author != null ? author.getHeight() : 0) + padding.bottom;
        }

        @Override
        public int getMinWidth() {
            return padding.left + getTextWidth() + padding.right;
        }

        @Override
        public int getLastLineWidth() {
            final Text last = author != null ? author : text;
            return padding.left + last.getLastLineWidth() + padding.right;
        }

        @Override
        public boolean forcesTimeToNewLine() {
            return centered;
        }

        @Override
        public Layout getLayout() {
            return text.layout;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.save();
            canvas.translate(offset(text), 0);
            text.draw(canvas);
            canvas.restore();
            if (author != null) {
                canvas.save();
                canvas.translate(offset(author), text.getHeight() + gap());
                author.draw(canvas);
                canvas.restore();
            }
        }

        @Override
        protected boolean onTouchEvent(MotionEvent event) {
            final int textHeight = text.getHeight();
            final float y = event.getY();
            final Text target;
            final int dx;
            final int dy;
            if (y >= 0 && y < textHeight) {
                target = text;
                dx = offset(text);
                dy = 0;
            } else if (author != null && y >= textHeight + gap()) {
                target = author;
                dx = offset(author);
                dy = textHeight + gap();
            } else {
                return false;
            }
            event.offsetLocation(-dx, -dy);
            final boolean handled = target.onTouchEvent(event);
            event.offsetLocation(dx, dy);
            return handled;
        }

        @Override
        public boolean findLink(CharacterStyle link, int blockY, FoundLink out) {
            if (text.fillFoundLink(link, out)) {
                out.x = padding.left + offset(text) - text.left;
                out.y = blockY + padding.top;
                return true;
            }
            if (author != null && author.fillFoundLink(link, out)) {
                out.x = padding.left + offset(author) - author.left;
                out.y = blockY + padding.top + text.getHeight() + gap();
                return true;
            }
            return false;
        }

        @Override
        protected TextSelectionHelper.TextLayoutBlock[] getText() {
            return texts;
        }

        @Override
        protected void placeTexts(int blockX, int blockY, int row) {
            text.setX(blockX + offset(text) - text.left);
            text.setY(blockY);
            text.setRow(row);
            if (author != null) {
                author.setX(blockX + offset(author) - author.left);
                author.setY(blockY + text.getHeight() + gap());
                author.setRow(row);
            }
        }

        @Override
        protected void onAttachedToWindow() {
            text.attach(view);
            if (author != null) author.attach(view);
        }

        @Override
        protected void onDetachedFromWindow() {
            text.detach(view);
            if (author != null) author.detach(view);
        }
    }

    public static class RichPullquoteBlock extends RichTextWithAuthorBlock {
        public RichPullquoteBlock(RichMessageLayout root, Rect padding, int maxWidth, CharSequence text, CharSequence author) {
            super(root, padding, maxWidth, text, author, Layout.Alignment.ALIGN_CENTER);
        }
    }

    public static class RichQuoteBlock extends RichTextWithAuthorBlock {
        public RichQuoteBlock(RichMessageLayout root, Rect padding, int maxWidth, CharSequence text, CharSequence author) {
            super(root, padding, maxWidth, text, author, Layout.Alignment.ALIGN_NORMAL);
        }
    }

    public static class RichTextBlockQuote extends RichTextBlock {
        public final TL_iv.pageBlockBlockquote block;
        private GradientClip clip;

        public RichTextBlockQuote(RichMessageLayout root, Rect padding, int maxWidth, TL_iv.pageBlockBlockquote block, CharSequence text) {
            super(root, padding, maxWidth, text);
            this.block = block;
            quoteArrow = ApplicationLoader.applicationContext.getResources().getDrawable(R.drawable.arrow_more).mutate();
            prevCollapsed = currentCollapsed = block.collapsed;
        }

        private ButtonBounce bounce;
        private boolean pressed;
        private boolean capturedByParent;

        private boolean currentCollapsed;
        private boolean prevCollapsed;
        public float collapsedProgress;
        public int collapsedHeightToDraw;

        public final Drawable quoteArrow;
        private int quoteArrowColor;

        @Override
        public int getHeight() {
            return currentCollapsed ? getCollapsedHeight() : super.getHeight();
        }

        public int getCollapsedHeight() {
            return (int) Math.min(padding.top + contentPaddingTop + text.layout.getPaint().getTextSize() * 1.4f * 3 + padding.bottom, super.getHeight());
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            final int act = event.getActionMasked();
            if (capturedByParent) {
                if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
                    capturedByParent = false;
                }
                return super.onTouchEvent(event);
            }

            if (act == MotionEvent.ACTION_DOWN) {
                capturedByParent = super.onTouchEvent(event);
                if (capturedByParent) {
                    return true;
                }

                pressed = true;
                ensureBounce();
                if (bounce != null) bounce.setPressed(true);
                return true;
            }
            if (act == MotionEvent.ACTION_UP) {
                if (pressed) {
                    pressed = false;
                    if (bounce != null) bounce.setPressed(false);
                    if (root.view != null) root.view.playSoundEffect(SoundEffectConstants.CLICK);
                    toggle();
                    return true;
                }
                return false;
            }
            if (act == MotionEvent.ACTION_CANCEL) {
                pressed = false;
                if (bounce != null) bounce.setPressed(false);
            }
            return pressed;
        }

        private void ensureBounce() {
            if (bounce == null && root.view != null) {
                bounce = new ButtonBounce(root.view);
            }
        }

        @Override
        public void onDraw(Canvas canvas) {
            final float scale = bounce != null ? bounce.getScale(0.01f) : 1f;
            if (scale != 1f) {
                canvas.save();
                canvas.scale(scale, scale, maxWidth / 2f, getHeight() / 2f);
            }

            final boolean needCollapse = collapsedProgress > 0;
            final int collapsedH = collapsedHeightToDraw - padding.bottom - contentPaddingBottom;
            canvas.save();
            canvas.clipRect(0, 0, maxWidth, collapsedH);

            if (needCollapse) {
                canvas.saveLayer(0, 0, maxWidth, collapsedH, null);
            }
            super.onDraw(canvas);
            if (needCollapse) {
                if (clip == null) {
                    clip = new GradientClip();
                }
                AndroidUtilities.rectTmp.set(0, collapsedH - dp(24), maxWidth, collapsedH + 1);
                clip.draw(canvas, AndroidUtilities.rectTmp, GradientClip.BOTTOM, collapsedProgress);
                canvas.restore();
            }
            canvas.restore();

            if (root.quoteLine.getColor() != quoteArrowColor) {
                quoteArrow.setColorFilter(new PorterDuffColorFilter(quoteArrowColor = root.quoteLine.getColor(), PorterDuff.Mode.SRC_IN));
            }

            final int arrowX = root.getMinWidth() - dp(24); // + dp(8);
            final int arrowY = collapsedH - dp(16) - dp(2) + dp(8);
            DrawableUtils.setBounds(quoteArrow, arrowX, arrowY, dp(16), dp(16), Gravity.CENTER);
            canvas.save();
            canvas.rotate(AndroidUtilities.lerp(180, 0, collapsedProgress), quoteArrow.getBounds().exactCenterX(), quoteArrow.getBounds().exactCenterY());
            quoteArrow.draw(canvas);
            canvas.restore();

            if (scale != 1f) {
                canvas.restore();
            }
        }

        private void toggle() {
            root.snapshotForBlockquoteAnimation();
            currentCollapsed = !currentCollapsed;
            root.blockquoteAnimating = true;
            root.reposition();
            if (root.view != null) {
                root.view.invalidate();
            }

            final ChatMessageCell cell = root.getCell();
            final ChatMessageCell.ChatMessageCellDelegate delegate = root.getDelegate();
            if (cell != null && delegate != null) {
                delegate.forceUpdate(cell, true, true);
            }
        }

        @Override
        public void snapshot() {
            super.snapshot();
            prevCollapsed = currentCollapsed;
        }

        @Override
        public float getBackgroundScale() {
            return bounce != null ? bounce.getScale(0.01f) : 1f;
        }
    }

    public static class RichCaptionBlock extends RichBlock {

        public final Text caption;
        public final Text credit;

        @Override
        public void appendAccessibilityText(SpannableStringBuilder sb) {
            appendText(sb, caption, null);
            if (credit != null && credit.layout != null && !TextUtils.isEmpty(credit.layout.getText())) {
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
                    sb.append('\n');
                }
                sb.append(credit.layout.getText());
            }
        }

        public final boolean rtl;
        private final TextSelectionHelper.TextLayoutBlock[] texts;

        public RichCaptionBlock(
            RichMessageLayout root,
            Rect padding, int maxWidth,
            CharSequence captionText,
            CharSequence creditText
        ) {
            super(root, padding, maxWidth);

            this.caption = !TextUtils.isEmpty(captionText) ? new Text(root, captionText, this.maxWidth) : null;
            this.credit  = !TextUtils.isEmpty(creditText)  ? new Text(root, creditText,  this.maxWidth) : null;
            this.rtl = root.isRtl();

            final ArrayList<TextSelectionHelper.TextLayoutBlock> list = new ArrayList<>(2);
            if (caption != null) list.add(caption);
            if (credit  != null) list.add(credit);
            this.texts = list.toArray(new TextSelectionHelper.TextLayoutBlock[0]);
        }

        private int captionHeight() { return caption != null ? caption.getHeight() : 0; }
        private int creditHeight()  { return credit  != null ? credit.getHeight()  : 0; }
        private int gap() { return (caption != null && credit != null) ? dp(4) : 0; }

        private int creditDrawX() {
            if (credit == null || !rtl) return 0;
            final int totalWidth = root.getMinWidth();
            return Math.max(0, totalWidth - padding.left - padding.right - credit.getMinWidth());
        }

        @Override
        public int getHeight() {
            return padding.top + captionHeight() + gap() + creditHeight() + padding.bottom;
        }

        @Override
        public int getMinWidth() {
            int w = 0;
            if (caption != null) w = Math.max(w, caption.getMinWidth());
            if (credit  != null) w = Math.max(w, credit.getMinWidth());
            return padding.left + w + padding.right;
        }

        @Override
        public int getLastLineWidth() {
            if (credit  != null) return padding.left + credit.getLastLineWidth() + padding.right;
            if (caption != null) return padding.left + caption.getLastLineWidth() + padding.right;
            return padding.left + padding.right;
        }

        @Override
        public boolean forcesTimeToNewLine() {
            return false;
        }

        @Override
        public void onDraw(Canvas canvas) {
            if (caption != null) {
                caption.draw(canvas);
            }
            if (credit != null) {
                canvas.save();
                canvas.translate(creditDrawX(), captionHeight() + gap());
                credit.draw(canvas);
                canvas.restore();
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            final int captionH = captionHeight();
            final int g = gap();
            final float y = event.getY() - padding.top;
            if (caption != null && y >= 0 && y < captionH) {
                event.offsetLocation(0, -padding.top);
                final boolean h = caption.onTouchEvent(event);
                event.offsetLocation(0, padding.top);
                return h;
            }
            if (credit != null && y >= captionH + g) {
                final int dy = padding.top + captionH + g;
                final int dx = creditDrawX();
                event.offsetLocation(-dx, -dy);
                final boolean h = credit.onTouchEvent(event);
                event.offsetLocation(dx, dy);
                return h;
            }
            return false;
        }

        @Override
        protected TextSelectionHelper.TextLayoutBlock[] getText() {
            return texts;
        }

        @Override
        protected void placeTexts(int blockX, int blockY, int row) {
            this.layoutX = blockX;
            this.layoutY = blockY;
            this.layoutRow = row;
            if (caption != null) {
                caption.setX(blockX - caption.left);
                caption.setY(blockY);
                caption.setRow(row);
            }
            if (credit != null) {
                credit.setX(blockX + creditDrawX() - credit.left);
                credit.setY(blockY + captionHeight() + gap());
                credit.setRow(row);
            }
        }

        @Override
        protected void onAttachedToWindow() {
            if (caption != null) caption.attach(view);
            if (credit  != null) credit.attach(view);
        }
        @Override
        protected void onDetachedFromWindow() {
            if (caption != null) caption.detach(view);
            if (credit  != null) credit.detach(view);
        }
    }

    public static class RichDetailsBlock extends RichBlock {

        public final TL_iv.pageBlockDetails block;
        public final Text title;
        public final Text[] texts;

        @Override
        public void appendAccessibilityText(SpannableStringBuilder sb) {
            appendText(sb, title, texts);
        }

        @Override
        protected int getBlockAccessibilityElementCount() {
            return 1;
        }

        @Override
        protected CharSequence getBlockAccessibilityElementText(int element) {
            final CharSequence titleText = title != null && title.layout != null ? withReplacements(title.layout.getText()) : null;
            return TextUtils.concat(
                LocaleController.getString(R.string.ArticleToggleBlock),
                ", ",
                LocaleController.getString(isOpen() ? R.string.AccDescrExpanded : R.string.AccDescrCollapsed),
                TextUtils.isEmpty(titleText) ? "" : TextUtils.concat(", ", titleText)
            );
        }

        @Override
        protected boolean isBlockAccessibilityElementText(int element) {
            return true;
        }

        @Override
        protected CharSequence getBlockAccessibilityElementStateDescription(int element) {
            return LocaleController.getString(isOpen() ? R.string.AccDescrExpanded : R.string.AccDescrCollapsed);
        }

        @Override
        protected void getBlockAccessibilityElementBounds(int element, Rect out) {
            final int top = (int) currY + padding.top;
            out.set(padding.left - root.padLeft, top, root.getMinWidth() + root.padRight - padding.right, top + getContentHeight());
        }

        @Override
        protected boolean onBlockAccessibilityElementClick(int element, View host) {
            toggle();
            return true;
        }

        public final AnimatedArrowDrawable arrow;
        private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private ButtonBounce bounce;
        private boolean pressed;

        public float animClipTop, animClipBottom;

        private static final float TEXT_LEFT_DP = 53f;
        private static final float TEXT_RIGHT_DP = 16f;
        private static final float TEXT_TOP_DP = 14f;
        private static final float TEXT_BOTTOM_DP = 12.66f;
        private static final float ARROW_LEFT_DP = 22.6f;
        private static final float ARROW_TOP_DP = 21.66f;
        private static final float ARROW_WIDTH_DP = 12.66f;
        private static final float ARROW_HEIGHT_DP = 6.16f;
        private static final float ARROW_STROKE_DP = 1.66f;

        public RichDetailsBlock(
            RichMessageLayout root,
            Rect padding, int maxWidth,
            TL_iv.pageBlockDetails block,
            CharSequence title
        ) {
            super(root, padding, maxWidth);
            this.block = block;

            final int textW = Math.max(0, this.maxWidth - dp(TEXT_LEFT_DP) - dp(TEXT_RIGHT_DP));
            this.title = new Text(root, title, textW);
            this.texts = new Text[] { this.title };

            final int color = root.getThemedColor(root.isOut()
                ? Theme.key_chat_outArticleDetailsArrow
                : Theme.key_chat_inArticleDetailsArrow);
            this.arrow = new AnimatedArrowDrawable(color, ARROW_WIDTH_DP, ARROW_HEIGHT_DP, ARROW_STROKE_DP);
            this.arrow.setAnimationProgress(block.open ? 0f : 1f);
        }

        public boolean isOpen() { return block.open; }

        @Override
        protected void onDraw(Canvas canvas) {
            final float scale = bounce != null ? bounce.getScale(0.02f) : 1f;
            final float contentLeft = -root.padLeft;
            final float contentRight = root.getMinWidth() + root.padRight - padding.left - padding.right;

            if (scale != 1f) {
                canvas.save();
                canvas.scale(scale, scale, (contentLeft + contentRight) / 2f, getContentHeight() / 2f);
            }
            final int color = root.getThemedColor(root.isOut()
                ? Theme.key_chat_outArticleDetailsArrow
                : Theme.key_chat_inArticleDetailsArrow);
            arrow.setColor(color);

            canvas.save();
            canvas.translate(contentLeft + dpf2(ARROW_LEFT_DP), dpf2(ARROW_TOP_DP));
            arrow.draw(canvas);
            canvas.restore();

            canvas.save();
            canvas.translate(contentLeft + dp(TEXT_LEFT_DP), dp(TEXT_TOP_DP));
            title.draw(canvas);
            canvas.restore();

            if (scale != 1f) {
                canvas.restore();
            }

            if (!isOpen() && !root.detailsAnimating) {
                linePaint.setColor(root.getThemedColor(root.isOut()
                    ? Theme.key_chat_outArticleDetailsLine
                    : Theme.key_chat_inArticleDetailsLine));
                canvas.drawRect(contentLeft, getContentHeight() - 1f, contentRight, getContentHeight(), linePaint);
            }
        }

        private int getContentHeight() {
            return Math.max(
                dp(ARROW_TOP_DP + ARROW_HEIGHT_DP),
                dp(TEXT_TOP_DP) + title.getHeight() + dp(TEXT_BOTTOM_DP)
            );
        }

        @Override
        public int getHeight() {
            return padding.top + getContentHeight() + padding.bottom;
        }

        @Override
        public int getMinWidth() {
            return padding.left + dp(TEXT_LEFT_DP) + title.getMinWidth() + dp(TEXT_RIGHT_DP) + padding.right;
        }

        @Override
        public int getLastLineWidth() {
            return getMinWidth();
        }

        @Override
        protected TextSelectionHelper.TextLayoutBlock[] getText() {
            return texts;
        }

        @Override
        protected void placeTexts(int blockX, int blockY, int row) {
            this.layoutX = blockX;
            this.layoutY = blockY;
            this.layoutRow = row;
            title.setX(blockX - root.padLeft + dp(TEXT_LEFT_DP) - title.left);
            title.setY(blockY + dp(TEXT_TOP_DP));
            title.setRow(row);
        }

        private void updateBubbleInsets() {
            title.setX(layoutX - root.padLeft + dp(TEXT_LEFT_DP) - title.left);
        }

        @Override
        public boolean findLink(CharacterStyle link, int blockY, FoundLink out) {
            if (title.fillFoundLink(link, out)) {
                out.x = padding.left - root.padLeft + dp(TEXT_LEFT_DP) - title.left;
                out.y = blockY + padding.top + dp(TEXT_TOP_DP);
                return true;
            }
            return false;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            final int act = event.getActionMasked();
            if (act == MotionEvent.ACTION_DOWN) {
                pressed = true;
                ensureBounce();
                if (bounce != null) bounce.setPressed(true);
                return true;
            }
            if (act == MotionEvent.ACTION_UP) {
                if (pressed) {
                    pressed = false;
                    if (bounce != null) bounce.setPressed(false);
                    if (root.view != null) root.view.playSoundEffect(SoundEffectConstants.CLICK);
                    toggle();
                    return true;
                }
                return false;
            }
            if (act == MotionEvent.ACTION_CANCEL) {
                pressed = false;
                if (bounce != null) bounce.setPressed(false);
            }
            return pressed;
        }

        private void ensureBounce() {
            if (bounce == null && root.view != null) {
                bounce = new ButtonBounce(root.view);
            }
        }

        @Override
        protected void onAttachedToWindow() {
            title.attach(view);
        }
        @Override
        protected void onDetachedFromWindow() {
            title.detach(view);
        }

        private void toggle() {
            root.snapshotForDetailsAnimation();
            block.open = !block.open;
            arrow.setAnimationProgressAnimated(block.open ? 0f : 1f);

            root.detailsAnimating = true;
            root.reposition();
            if (root.view != null) {
                root.view.invalidate();
            }

            final ChatMessageCell cell = root.getCell();
            final ChatMessageCell.ChatMessageCellDelegate delegate = root.getDelegate();
            if (cell != null && delegate != null) {
                delegate.forceUpdate(cell, true, true);
            }
        }
    }

    public static class RichDetailsEndBlock extends RichBlock {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public RichDetailsEndBlock(RichMessageLayout root, Rect padding, int maxWidth) {
            super(root, padding, maxWidth);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            paint.setColor(root.getThemedColor(root.isOut()
                ? Theme.key_chat_outArticleDetailsLine
                : Theme.key_chat_inArticleDetailsLine));
            final float left = -root.padLeft;
            final float right = root.getMinWidth() + root.padRight - padding.left - padding.right;
            final float visibleProgress = root.detailsAnimating
                ? AndroidUtilities.lerp(prevVisible ? 1f : 0f, currVisible ? 1f : 0f, root.detailsAnimationProgress)
                : (currVisible ? 1f : 0f);
            final float collapsedTop = -1f - (parentDetails != null ? parentDetails.padding.bottom : 0);
            final float top = AndroidUtilities.lerp(collapsedTop, dp(6), visibleProgress);
            canvas.drawRect(left, top, right, top + 1f, paint);
        }

        @Override
        public int getMinWidth() {
            return padding.left + dp(32) + padding.right;
        }

        @Override
        public int getHeight() {
            return padding.top + dp(6) + 1 + padding.bottom;
        }
    }

    public static class RichTableBlock extends RichBlock implements TableLayout.TableLayoutDelegate {

        private static final int VERTICAL_PADDING_DP = 10;

        public final TL_iv.pageBlockTable pageBlock;
        public final TableLayout tableLayout;
        private final ArrayList<Text> cellTexts = new ArrayList<>();

        @Override
        public void appendAccessibilityText(SpannableStringBuilder sb) {
            appendText(sb, title, null);
            for (int i = 0; i < cellTexts.size(); i++) {
                final Text t = cellTexts.get(i);
                if (t != null && t.layout != null && !TextUtils.isEmpty(t.layout.getText())) {
                    if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
                        sb.append(", ");
                    }
                    sb.append(t.layout.getText());
                }
            }
        }

        private final TextSelectionHelper.TextLayoutBlock[] textsArr;

        private final Text title;
        private final int titleHeight;

        private final ArrayList<CellBlock> cellBlocks = new ArrayList<>();

        private static final class CellBlock implements MultiLayoutTypingAnimator.Block {
            final TableLayout.Child child;
            CellBlock(TableLayout.Child c) { child = c; }
            @Override public Layout getLayout() {
                return child.textLayout == null ? null : child.textLayout.getLayout();
            }
            @Override public View getParentView() { return null; }
        }

        private final int viewportWidth;
        private final int intrinsicContentWidth;
        private final int intrinsicTableWidth;
        private int resolvedTableWidth;
        private int contentHeight;
        private final int contentMeasuredWidth;
        private final int maxScrollX;
        private int scrollX;

        private Paint linePaint, halfLinePaint, headerPaint, stripPaint;
        private void ensurePaints() {
            if (linePaint == null) {
                linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                linePaint.setStyle(Paint.Style.STROKE);
                linePaint.setStrokeWidth(dpf2(0.66f));
                halfLinePaint = new Paint();
                halfLinePaint.setStyle(Paint.Style.STROKE);
                halfLinePaint.setStrokeWidth(dpf2(0.66f));
                headerPaint = new Paint();
                stripPaint = new Paint();
            }
            final int lineColor = root.getThemedColor(
                root.isOut() ? Theme.key_chat_outTableBorder : Theme.key_chat_inTableBorder
            );
            linePaint.setColor(lineColor);
            halfLinePaint.setColor(lineColor);
            headerPaint.setColor(root.getThemedColor(
                root.isOut() ? Theme.key_chat_outTableBackground : Theme.key_chat_inTableBackground
            ));
            stripPaint.setColor(0x0a000000);
        }

        public RichTableBlock(
            RichMessageLayout root,
            Rect padding, int maxWidth,
            TL_iv.pageBlockTable block
        ) {
            super(root, padding, maxWidth);
            this.padding.top += dp(VERTICAL_PADDING_DP);
            this.pageBlock = block;
            this.viewportWidth = this.maxWidth;

            tableLayout = new TableLayout(ApplicationLoader.applicationContext, this, null);
            tableLayout.setOrientation(TableLayout.HORIZONTAL);
            tableLayout.setRowOrderPreserved(true);
            tableLayout.setDrawLines(block.bordered);
            tableLayout.setStriped(block.striped);
            tableLayout.setRtl(root.isRtl());
            tableLayout.setFillWidth(false);
            if (block.compact) {
                tableLayout.setCellPadding(dp(5), dp(5), dp(5));
            }
            tableLayout.setMinimumCellHeight(dp(block.compact ? 18 : 36));

            int maxCols = 0;
            if (!block.rows.isEmpty()) {
                final TL_iv.pageTableRow row0 = block.rows.get(0);
                for (int c = 0; c < row0.cells.size(); ++c) {
                    final TL_iv.pageTableCell cell = row0.cells.get(c);
                    maxCols += (cell.colspan != 0 ? cell.colspan : 1);
                }
            }
            for (int r = 0; r < block.rows.size(); ++r) {
                final TL_iv.pageTableRow row = block.rows.get(r);
                int cols = 0;
                for (int c = 0; c < row.cells.size(); ++c) {
                    final TL_iv.pageTableCell cell = row.cells.get(c);
                    final int colspan = (cell.colspan != 0 ? cell.colspan : 1);
                    final int rowspan = (cell.rowspan != 0 ? cell.rowspan : 1);
                    if (cell.text != null) {
                        tableLayout.addChild(cell, cols, r, colspan);
                    } else {
                        tableLayout.addChild(cols, r, colspan, rowspan);
                    }
                    cols += colspan;
                }
            }
            tableLayout.setColumnCount(maxCols);

            tableLayout.measure(
                View.MeasureSpec.makeMeasureSpec(this.maxWidth, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            );
            contentMeasuredWidth = tableLayout.getMeasuredWidth();
            intrinsicContentWidth = contentMeasuredWidth;
            contentHeight = tableLayout.getMeasuredHeight();
            maxScrollX = Math.max(0, contentMeasuredWidth - viewportWidth);

            final boolean hasTitle = block.title != null && !(block.title instanceof TL_iv.textEmpty)
                && !TextUtils.isEmpty(getString(block.title));
            if (hasTitle) {
                final int titleFlags = setBlockFlags(0, TEXT_FLAG_BLOCK_TABLE_TITLE);
                title = new Text(root, root.formatText(block.title, titleFlags), viewportWidth);
                title.setDrawAtOrigin(true);
                titleHeight = title.getHeight() + dp(9);
            } else {
                title = null;
                titleHeight = 0;
            }

            final int titleWidth = title != null ? title.getMinWidth() : 0;
            intrinsicTableWidth = Math.max(0, Math.min(viewportWidth, Math.max(intrinsicContentWidth, titleWidth)));
            resolvedTableWidth = intrinsicTableWidth;

            for (int i = 0; i < tableLayout.getChildCount(); ++i) {
                final TableLayout.Child child = tableLayout.getChildAt(i);
                if (child.textLayout instanceof Text) {
                    cellTexts.add((Text) child.textLayout);
                    cellBlocks.add(new CellBlock(child));
                }
            }
            final ArrayList<TextSelectionHelper.TextLayoutBlock> selectable = new ArrayList<>();
            if (title != null) selectable.add(title);
            selectable.addAll(cellTexts);
            textsArr = selectable.toArray(new TextSelectionHelper.TextLayoutBlock[0]);
        }

        private void resolveWidth(int rootWidth) {
            resolvedTableWidth = Math.max(intrinsicTableWidth,
                Math.min(viewportWidth, Math.max(0, rootWidth - padding.left - padding.right)));
            tableLayout.setRenderWidth(resolvedTableWidth);
            contentHeight = tableLayout.getRenderHeight();
        }

        @Override
        public void collectAnimatorBlocks(List<MultiLayoutTypingAnimator.Block> out) {
            if (cellBlocks.isEmpty()) {
                super.collectAnimatorBlocks(out);
                return;
            }
            out.addAll(cellBlocks);
        }

        @Override
        public void drawWithTyping(Canvas canvas) {
            final MultiLayoutTypingAnimator anim = typingAnimator;
            if (anim == null || !anim.isRunning() || cellBlocks.isEmpty() || anim.indexOf(cellBlocks.get(0)) < 0) {
                draw(canvas);
                return;
            }
            final float chromeAlpha = anim.getBlockAlpha(cellBlocks.get(0));
            if (chromeAlpha <= 0f) return;
            canvas.save();
            canvas.translate(padding.left, padding.top);
            drawTitle(canvas);
            canvas.translate(0, titleHeight);
            drawCellsWithTyping(canvas, anim, chromeAlpha);
            canvas.restore();
        }

        private void drawCellsWithTyping(Canvas canvas, MultiLayoutTypingAnimator anim, float chromeAlpha) {
            final int sc = canvas.saveLayerAlpha(
                -root.padLeft, 0,
                root.getMinWidth() + root.padRight - padding.left - padding.right, contentHeight,
                (int) (chromeAlpha * 255), Canvas.ALL_SAVE_FLAG
            );
            canvas.save();
            canvas.translate(-scrollX, 0);

            int bi = 0;
            for (int i = 0, N = tableLayout.getChildCount(); i < N; i++) {
                final TableLayout.Child c = tableLayout.getChildAt(i);
                CellBlock cb = null;
                if (bi < cellBlocks.size() && cellBlocks.get(bi).child == c) {
                    cb = cellBlocks.get(bi);
                    bi++;
                }
                if (cb == null) {
                    c.draw(canvas, view);
                    continue;
                }
                if (!anim.needDraw(cb)) {
                    c.draw(canvas, view, false);
                    continue;
                }
                if (anim.isFadeBlock(cb)) {
                    c.draw(canvas, view, false);
                    if (c.textLayout instanceof Text) {
                        canvas.save();
                        canvas.translate(c.getTextX(), c.getTextY());
                        ((Text) c.textLayout).drawFade(canvas, anim.getFadeLineIndex(cb), anim.getFadeXPosition(cb));
                        canvas.restore();
                    }
                    continue;
                }
                final float a = anim.getBlockAlpha(cb);
                if (a >= 1f) {
                    c.draw(canvas, view);
                } else if (a > 0f && c.textLayout != null) {
                    c.draw(canvas, view, false);
                    canvas.save();
                    canvas.translate(c.getTextX(), c.getTextY());
                    final int tsc = canvas.saveLayerAlpha(
                        0, 0, c.getMeasuredWidth(), c.getMeasuredHeight(),
                        (int) (a * 255), Canvas.ALL_SAVE_FLAG
                    );
                    c.textLayout.draw(canvas, view);
                    canvas.restoreToCount(tsc);
                    canvas.restore();
                } else {
                    c.draw(canvas, view, false);
                }
            }
            canvas.restore();

            AndroidUtilities.rectTmp.set(-root.padLeft, 0, -root.padLeft + dp(12), contentHeight);
            root.clip.draw(canvas, AndroidUtilities.rectTmp, GradientClip.LEFT, 1.0f);

            final int right = root.getMinWidth() + root.padRight - padding.left - padding.right;
            AndroidUtilities.rectTmp.set(right - dp(12), 0, right, contentHeight);
            root.clip.draw(canvas, AndroidUtilities.rectTmp, GradientClip.RIGHT, 1.0f);

            canvas.restoreToCount(sc);
        }

        @Override
        public Text createTextLayout(TL_iv.pageTableCell cell, int maxWidth) {
            if (cell == null) return null;
            final int flags = setBlockFlags(0, TEXT_FLAG_BLOCK_TABLE);
            final CharSequence formatted = root.formatText(cell.text, flags);
            final Layout.Alignment alignment;
            if (cell.align_right) {
                alignment = Layout.Alignment.ALIGN_OPPOSITE;
            } else if (cell.align_center) {
                alignment = Layout.Alignment.ALIGN_CENTER;
            } else {
                alignment = Layout.Alignment.ALIGN_NORMAL;
            }
            final Text text = new Text(root, formatted, maxWidth, alignment);
            text.setDrawAtOrigin(true);
            return text;
        }
        @Override public Paint getLinePaint() {
            ensurePaints();
            return linePaint;
        }
        @Override public Paint getHalfLinePaint() {
            ensurePaints();
            return halfLinePaint;
        }
        @Override public Paint getHeaderPaint() {
            ensurePaints();
            return headerPaint;
        }
        @Override public Paint getStripPaint() {
            ensurePaints();
            return stripPaint;
        }

        @Override
        public void onDraw(Canvas canvas) {
            drawTitle(canvas);
            canvas.save();
            canvas.translate(0, titleHeight);
            canvas.saveLayerAlpha(
                -root.padLeft, 0,
                root.getMinWidth() + root.padRight - padding.left - padding.right, contentHeight,
                0xFF, Canvas.ALL_SAVE_FLAG
            );
            canvas.save();
            canvas.translate(-scrollX, 0);
            for (int i = 0, N = tableLayout.getChildCount(); i < N; i++) {
                final TableLayout.Child c = tableLayout.getChildAt(i);
                c.draw(canvas, view);
            }
            canvas.restore();

            AndroidUtilities.rectTmp.set(-root.padLeft, 0, -root.padLeft + dp(12), contentHeight);
            root.clip.draw(canvas, AndroidUtilities.rectTmp, GradientClip.LEFT, 1.0f);

            final int right = root.getMinWidth() + root.padRight - padding.left - padding.right;
            AndroidUtilities.rectTmp.set(right - dp(12), 0, right, contentHeight);
            root.clip.draw(canvas, AndroidUtilities.rectTmp, GradientClip.RIGHT, 1.0f);

            canvas.restore();
            canvas.restore();
        }

        private void drawTitle(Canvas canvas) {
            if (title == null) return;
            canvas.save();
            canvas.translate(titleDrawX(), 0);
            title.draw(canvas);
            canvas.restore();
        }

        @Override public int getHeight() {
            return padding.top + titleHeight + contentHeight + dp(VERTICAL_PADDING_DP) + padding.bottom;
        }
        @Override public int getMinWidth() { return padding.left + intrinsicTableWidth + padding.right; }
        @Override public int getLastLineWidth() { return getMinWidth(); }

        private float downX, downY;
        private int downScrollX;
        private boolean dragging;
        private Text pressedCellText;
        private float cellDx, cellDy;
        private boolean textHandlingTouch;
        private int touchSlop;
        private int minFlingVelocity, maxFlingVelocity;
        private VelocityTracker velocityTracker;
        private OverScroller scroller;
        private final Runnable flingTick = new Runnable() {
            @Override public void run() {
                if (scroller == null || view == null) return;
                if (scroller.computeScrollOffset()) {
                    int next = scroller.getCurrX();
                    if (next < 0) next = 0;
                    if (next > maxScrollX) next = maxScrollX;
                    if (next != scrollX) {
                        scrollX = next;
                        placeTexts(layoutX, layoutY, layoutRow);
                        view.invalidate();
                    }
                    if (!scroller.isFinished()) {
                        view.postOnAnimation(this);
                    }
                }
            }
        };

        private void ensureTouchConfig() {
            if (touchSlop == 0 && view != null) {
                final android.view.ViewConfiguration vc = android.view.ViewConfiguration.get(view.getContext());
                touchSlop = vc.getScaledTouchSlop();
                minFlingVelocity = vc.getScaledMinimumFlingVelocity();
                maxFlingVelocity = vc.getScaledMaximumFlingVelocity();
            }
            if (scroller == null && view != null) {
                scroller = new android.widget.OverScroller(view.getContext());
            }
        }

        private TableLayout.Child findCellChildAt(float ex, float ey) {
            final float cx = ex + scrollX;
            final float cy = ey - titleHeight;
            for (int i = 0, N = tableLayout.getChildCount(); i < N; i++) {
                final TableLayout.Child c = tableLayout.getChildAt(i);
                if (!(c.textLayout instanceof Text)) continue;
                if (cx >= c.x && cx < c.x + c.getMeasuredWidth() && cy >= c.y && cy < c.y + c.getMeasuredHeight()) {
                    return c;
                }
            }
            return null;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            final int act = event.getActionMasked();
            if (act == MotionEvent.ACTION_DOWN) {
                ensureTouchConfig();
                if (scroller != null && !scroller.isFinished()) {
                    scroller.forceFinished(true);
                }
                downX = event.getX();
                downY = event.getY();
                downScrollX = scrollX;
                dragging = false;
                if (velocityTracker == null) velocityTracker = VelocityTracker.obtain();
                else velocityTracker.clear();
                velocityTracker.addMovement(event);

                pressedCellText = null;
                textHandlingTouch = false;
                if (title != null && event.getY() < titleHeight) {
                    final float tdx = titleDrawX();
                    event.offsetLocation(-tdx, 0);
                    final boolean h = title.onTouchEvent(event);
                    event.offsetLocation(tdx, 0);
                    if (h) {
                        pressedCellText = title;
                        cellDx = tdx;
                        cellDy = 0;
                        textHandlingTouch = true;
                    }
                }
                if (!textHandlingTouch) {
                    final TableLayout.Child cell = findCellChildAt(event.getX(), event.getY());
                    if (cell != null) {
                        pressedCellText = (Text) cell.textLayout;
                        cellDx = cell.getTextX() - scrollX;
                        cellDy = titleHeight + cell.getTextY();
                        event.offsetLocation(-cellDx, -cellDy);
                        textHandlingTouch = pressedCellText.onTouchEvent(event);
                        event.offsetLocation(cellDx, cellDy);
                    }
                }
                return textHandlingTouch || maxScrollX > 0;
            }
            if (act == MotionEvent.ACTION_MOVE) {
                if (velocityTracker != null) velocityTracker.addMovement(event);
                final float dx = event.getX() - downX;
                if (!dragging && maxScrollX > 0 && Math.abs(dx) > touchSlop) {
                    dragging = true;
                    requestDisallowParentIntercept(true);
                    if (textHandlingTouch && pressedCellText != null) {
                        final MotionEvent cancel = MotionEvent.obtain(event);
                        cancel.setAction(MotionEvent.ACTION_CANCEL);
                        cancel.offsetLocation(-cellDx, -cellDy);
                        pressedCellText.onTouchEvent(cancel);
                        cancel.recycle();
                        textHandlingTouch = false;
                    }
                }
                if (dragging) {
                    int next = (int) (downScrollX - dx);
                    if (next < 0) next = 0;
                    if (next > maxScrollX) next = maxScrollX;
                    if (next != scrollX) {
                        scrollX = next;
                        placeTexts(layoutX, layoutY, layoutRow);
                        if (view != null) view.invalidate();
                    }
                    return true;
                }
                return textHandlingTouch;
            }
            if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
                final boolean wasDragging = dragging;
                dragging = false;
                if (wasDragging) {
                    requestDisallowParentIntercept(false);
                    if (act == MotionEvent.ACTION_UP && velocityTracker != null && scroller != null && view != null) {
                        velocityTracker.addMovement(event);
                        velocityTracker.computeCurrentVelocity(1000, maxFlingVelocity);
                        final float xv = -velocityTracker.getXVelocity();
                        if (Math.abs(xv) > minFlingVelocity) {
                            scroller.fling(scrollX, 0, (int) xv, 0, 0, maxScrollX, 0, 0);
                            view.postOnAnimation(flingTick);
                        }
                    }
                }
                if (!wasDragging && textHandlingTouch && pressedCellText != null) {
                    event.offsetLocation(-cellDx, -cellDy);
                    pressedCellText.onTouchEvent(event);
                    event.offsetLocation(cellDx, cellDy);
                }
                final boolean handledText = textHandlingTouch;
                textHandlingTouch = false;
                pressedCellText = null;
                if (velocityTracker != null) {
                    velocityTracker.recycle();
                    velocityTracker = null;
                }
                return wasDragging || handledText;
            }
            return false;
        }

        @Override
        public boolean isHorizontallyDragging() {
            return dragging || (scroller != null && !scroller.isFinished());
        }

        @Override
        public boolean findLink(CharacterStyle link, int blockY, FoundLink out) {
            if (title != null && title.fillFoundLink(link, out)) {
                out.x = padding.left + titleDrawX() - title.drawLeft();
                out.y = blockY + padding.top;
                return true;
            }
            for (int i = 0, N = tableLayout.getChildCount(); i < N; i++) {
                final TableLayout.Child c = tableLayout.getChildAt(i);
                if (!(c.textLayout instanceof Text)) continue;
                final Text t = (Text) c.textLayout;
                if (t.fillFoundLink(link, out)) {
                    out.x = padding.left + c.getTextX() - scrollX - t.drawLeft();
                    out.y = blockY + padding.top + titleHeight + c.getTextY();
                    return true;
                }
            }
            return false;
        }

        private int titleDrawX() {
            if (title == null) return 0;
            return Math.round((resolvedTableWidth - (title.right - title.left)) / 2f - title.left);
        }

        @Override
        protected TextSelectionHelper.TextLayoutBlock[] getText() {
            return textsArr;
        }

        @Override
        protected void placeTexts(int blockX, int blockY, int row) {
            this.layoutX = blockX;
            this.layoutY = blockY;
            this.layoutRow = row;
            if (title != null) {
                title.setX(blockX + titleDrawX() - title.drawLeft());
                title.setY(blockY);
                title.setRow(row);
            }
            for (int i = 0, N = tableLayout.getChildCount(); i < N; i++) {
                final TableLayout.Child c = tableLayout.getChildAt(i);
                if (c.textLayout instanceof Text) {
                    final Text t = (Text) c.textLayout;
                    t.setX(blockX + c.getTextX() - scrollX - t.drawLeft());
                    t.setY(blockY + titleHeight + c.getTextY());
                    t.setRow(row);
                }
            }
        }

        @Override
        public boolean drawOverlay(Canvas canvas, ColorFilter colorFilter) {
            boolean drew = false;
            canvas.save();
            canvas.translate(padding.left, padding.top);
            if (title != null && title.animatedEmojiStack != null && !title.animatedEmojiStack.holders.isEmpty()) {
                canvas.save();
                canvas.translate(titleDrawX(), 0);
                AnimatedEmojiSpan.drawAnimatedEmojis(canvas, title.layout, title.animatedEmojiStack, 0, title.spoilers, 0, 0, 0, 1.0f, colorFilter);
                canvas.restore();
                drew = true;
            }
            canvas.translate(0, titleHeight);
            boolean hasCellOverlay = false;
            for (int i = 0, N = cellTexts.size(); i < N; i++) {
                final Text t = cellTexts.get(i);
                if (t.animatedEmojiStack != null && !t.animatedEmojiStack.holders.isEmpty()) {
                    hasCellOverlay = true;
                    break;
                }
            }
            if (hasCellOverlay) {
                final int layer = canvas.saveLayerAlpha(
                    -root.padLeft, 0,
                    root.getMinWidth() + root.padRight - padding.left - padding.right,
                    contentHeight, 0xFF, Canvas.ALL_SAVE_FLAG
                );
                canvas.save();
                canvas.translate(-scrollX, 0);
                for (int i = 0, N = tableLayout.getChildCount(); i < N; i++) {
                    final TableLayout.Child c = tableLayout.getChildAt(i);
                    if (!(c.textLayout instanceof Text)) continue;
                    final Text t = (Text) c.textLayout;
                    if (t.animatedEmojiStack == null || t.animatedEmojiStack.holders.isEmpty()) continue;
                    canvas.save();
                    canvas.translate(c.getTextX(), c.getTextY());
                    AnimatedEmojiSpan.drawAnimatedEmojis(canvas, t.layout, t.animatedEmojiStack, 0, t.spoilers, 0, 0, 0, 1.0f, colorFilter);
                    canvas.restore();
                }
                canvas.restore();

                AndroidUtilities.rectTmp.set(-root.padLeft, 0, -root.padLeft + dp(12), contentHeight);
                root.clip.draw(canvas, AndroidUtilities.rectTmp, GradientClip.LEFT, 1.0f);

                final int right = root.getMinWidth() + root.padRight - padding.left - padding.right;
                AndroidUtilities.rectTmp.set(right - dp(12), 0, right, contentHeight);
                root.clip.draw(canvas, AndroidUtilities.rectTmp, GradientClip.RIGHT, 1.0f);

                canvas.restoreToCount(layer);
                drew = true;
            }
            canvas.restore();
            return drew;
        }

        @Override
        protected void onAttachedToWindow() {
            if (title != null) title.attach(view);
            for (Text t : cellTexts) t.attach(view);
        }
        @Override
        protected void onDetachedFromWindow() {
            if (title != null) title.detach(view);
            for (Text t : cellTexts) t.detach(view);
        }
    }

    public static class RichDividerBlock extends RichBlock {

        public final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public RichDividerBlock(RichMessageLayout root, Rect padding, int maxWidth) {
            super(root, padding, maxWidth);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final int fullWidth = root.getMinWidth() + root.padLeft + root.padRight - padding.left - padding.right;
            final int barWidth = fullWidth / 2;
            final float left = (fullWidth - barWidth) / 2f - root.padLeft + padding.left;
            final float top = (dp(12) - dp(1)) / 2f;
            paint.setColor(root.getThemedColor(root.isOut() ? Theme.key_chat_outDivider : Theme.key_chat_inDivider));
            AndroidUtilities.rectTmp.set(left, top, left + barWidth, top + dp(1));
            canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(.5f), dp(.5f), paint);
        }

        @Override
        public int getMinWidth() {
            return dp(32);
        }

        @Override
        public int getHeight() {
            return padding.top + dp(12) + padding.bottom;
        }
    }

    public static class RichUnsupportedBlock extends RichBlock implements Drawable.Callback {
        public final UnsupportedBlockDrawable unsupportedBlockDrawable;
        public final int unsupportedBlockWidth;
        public final int unsupportedBlockHeight;
        public final int index;

        public TornEdge.Params tornParams;
        public Bitmap tornBitmap;

        public RichUnsupportedBlock(RichMessageLayout root, Rect padding, int maxWidth, int index) {
            super(root, padding, maxWidth);
            this.index = index;

            unsupportedBlockDrawable = new UnsupportedBlockDrawable(root.resourcesProvider);
            unsupportedBlockDrawable.setCallback(this);
            unsupportedBlockDrawable.setTitle(LocaleController.getString(R.string.UnsupportedBlockTitle));
            unsupportedBlockDrawable.setSubtitle(LocaleController.getString(R.string.UnsupportedBlockMessage));
            unsupportedBlockDrawable.setButtonText(LocaleController.getString(R.string.UnsupportedUpdate));
            unsupportedBlockDrawable.setOnClickListener(() -> {
                if (root.delegate != null) {
                    root.delegate.didPressAppUpdateButton();
                }
            });
            unsupportedBlockWidth = this.maxWidth;
            unsupportedBlockHeight = unsupportedBlockDrawable.measure(unsupportedBlockWidth);
        }

        public float getY(ChatMessageCell.TransitionParams tp) {
            if (tp != null && (root.detailsAnimating || root.blockquoteAnimating)) {
                final float prog = Math.max(0f, Math.min(1f, tp.animateChangeProgress));
                return AndroidUtilities.lerp(prevY, currY, prog);
            } else {
                return currY;
            }
        }

        public float getHeight(ChatMessageCell.TransitionParams tp) {
            if (tp != null && (root.detailsAnimating || root.blockquoteAnimating)) {
                final float prog = Math.max(0f, Math.min(1f, tp.animateChangeProgress));
                return AndroidUtilities.lerp(prevH, currH, prog);
            } else {
                return currH;
            }
        }

        @Override
        public int getHeight() {
            return padding.top + unsupportedBlockHeight + padding.bottom;
        }

        @Override
        public void draw(Canvas canvas) {
            unsupportedBlockDrawable.setBounds(
                padding.left, padding.top,
                padding.left + unsupportedBlockWidth,
                padding.top + unsupportedBlockHeight);
            unsupportedBlockDrawable.draw(canvas);
        }

        @Override
        public void invalidateDrawable(@NonNull Drawable who) {
            if (view != null) {
                view.invalidate();
            }
        }

        @Override
        public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) {

        }

        @Override
        public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) {

        }

        @Override
        protected boolean onTouchEvent(MotionEvent event) {
            if (view != null) {
                return unsupportedBlockDrawable.onTouchEvent(view, event);
            }
            return super.onTouchEvent(event);
        }
    }

    /* * */

    public static class RichButtonRowBlock extends RichBlock {
        private enum Align {
            LEFT, RIGHT, CENTER, FILL;
        }

        private static final int GAP = 7;

        private final RichButton[] buttons;
        private final Align align;
        private int layoutWidth = -1;

        public RichButtonRowBlock(RichMessageLayout root, Rect padding, int maxWidth, TL_iv.pageBlockButtonRow pageBlockButtonRow) {
            super(root, padding, maxWidth);
            buttons = new RichButton[pageBlockButtonRow.buttons.size()];
            for (int a = 0, N = pageBlockButtonRow.buttons.size(); a < N; a++) {
                buttons[a] = new RichButton(root, maxWidth, pageBlockButtonRow.buttons.get(a), this::invalidate);
            }
            if (pageBlockButtonRow.align_left) {
                align = Align.LEFT;
            } else if (pageBlockButtonRow.align_center) {
                align = Align.CENTER;
            } else if (pageBlockButtonRow.align_right) {
                align = Align.RIGHT;
            } else {
                align = Align.FILL;
            }

            layout(getIntrinsicWidth());
        }

        @Override
        public void draw(Canvas canvas) {
            updateLayout();
            for (RichButton button : buttons) {
                canvas.save();
                canvas.translate(button.x, Math.round((getHeight() - button.getHeight()) / 2f));
                button.draw(canvas);
                canvas.restore();
            }
        }

        private int getIntrinsicWidth() {
            final int count = buttons.length;
            if (count == 0) {
                return 0;
            }
            int width = dp(GAP) * (count - 1);
            for (RichButton button : buttons) {
                width += button.getPreferredWidth();
            }
            return Math.min(maxWidth, width);
        }

        private void updateLayout() {
            final int bubbleWidth = root.getMinWidth() + root.padLeft + root.padRight;
            // padRight may also contain width made available by the bubble/preview. Keep the
            // original (symmetric) bubble inset, while allowing FILL rows to consume that width.
            final int width = Math.max(0, bubbleWidth
                - root.padLeft * 2 - padding.left - padding.right);
            if (layoutWidth != width) {
                layout(width);
            }
        }

        private void layout(int width) {
            layoutWidth = width;
            final int count = buttons.length;
            if (count == 0) {
                return;
            }
            final int gap = dp(GAP);
            final int gaps = gap * (count - 1);
            final int available = Math.max(0, width - gaps);

            int preferred = 0;
            for (RichButton button : buttons) {
                button.width = button.getPreferredWidth();
                preferred += button.width;
            }

            if (preferred > available) {
                squeeze(available, preferred);
            } else if (align == Align.FILL) {
                stretch(available);
            }

            int rowWidth = gaps;
            for (RichButton button : buttons) {
                rowWidth += button.width;
            }

            int x;
            switch (align) {
                case RIGHT:
                    x = width - rowWidth;
                    break;
                case CENTER:
                    x = (width - rowWidth) / 2;
                    break;
                default:
                    x = 0;
                    break;
            }
            for (RichButton button : buttons) {
                button.x = x;
                x += button.width + gap;
            }
        }

        // grow buttons to fill the row, keeping widths as equal as possible:
        // buttons wider than the even share keep their preferred width, the rest split what is left
        private void stretch(int available) {
            final boolean[] fixed = new boolean[buttons.length];
            int flexible = buttons.length;
            int rest = available;
            boolean changed = true;
            while (changed && flexible > 0) {
                changed = false;
                final int share = rest / flexible;
                for (int a = 0; a < buttons.length; a++) {
                    if (!fixed[a] && buttons[a].width > share) {
                        fixed[a] = true;
                        rest -= buttons[a].width;
                        flexible--;
                        changed = true;
                    }
                }
            }
            if (flexible <= 0) {
                return;
            }
            final int share = rest / flexible;
            int extra = rest - share * flexible;
            for (int a = 0; a < buttons.length; a++) {
                if (!fixed[a]) {
                    buttons[a].width = share + (extra-- > 0 ? 1 : 0);
                }
            }
        }

        // take the overflow away proportionally to how much each button can give up
        private void squeeze(int available, int preferred) {
            int shrinkable = 0;
            for (RichButton button : buttons) {
                shrinkable += button.width - button.getMinWidth();
            }
            if (shrinkable <= 0) {
                for (RichButton button : buttons) {
                    button.width = button.getMinWidth();
                }
                return;
            }
            final int total = Math.min(preferred - available, shrinkable);
            int taken = 0;
            for (int a = 0; a < buttons.length; a++) {
                final RichButton button = buttons[a];
                final int room = button.width - button.getMinWidth();
                int cut = a == buttons.length - 1 ? total - taken : (int) ((long) total * room / shrinkable);
                cut = Math.min(cut, room);
                button.width -= cut;
                taken += cut;
            }
        }

        @Override
        public int getHeight() {
            final int buttonHeight = buttons.length == 0 ? dp(18 + root.fontSize) : buttons[0].getHeight();
            return padding.top + buttonHeight + dp(4.333f) + padding.bottom;
        }

        @Override
        public int getMinWidth() {
            return padding.left + getIntrinsicWidth() + padding.right;
        }


        private RichButton pressedButton;
        // button the current gesture started on; kept until UP/CANCEL even if the finger slides off
        private RichButton touchButton;

        private RichButton getButtonAt(float x, float y) {
            updateLayout();
            final int buttonHeight = buttons.length == 0 ? dp(18 + root.fontSize) : buttons[0].getHeight();
            final float top = (getHeight() - buttonHeight) / 2f;
            if (y < top || y > top + buttonHeight) {
                return null;
            }
            for (RichButton button : buttons) {
                if (button.contains(x)) {
                    return button;
                }
            }
            return null;
        }

        private void invalidate() {
            if (view != null) {
                view.invalidate();
            }
        }

        private void setPressedButton(RichButton button) {
            if (pressedButton == button) {
                return;
            }
            if (pressedButton != null) {
                pressedButton.setPressed(false);
            }
            pressedButton = button;
            if (pressedButton != null) {
                pressedButton.setPressed(true);
            }
        }

        private void onButtonClick(RichButton button) {
            if (root.delegate != null) {
                root.delegate.didPressBotButton(root.cell, button.pageButton);
            }
        }

        private void onButtonLongClick(RichButton button) {
            if (root.delegate != null) {
                root.delegate.didLongPressBotButton(root.cell, button.pageButton);
            }
        }

        private final ClickHelper clickHelper = new ClickHelper(new ClickHelper.Delegate() {
            @Override
            public boolean needClickAt(View view, float x, float y) {
                // the button picked here is the one the whole gesture belongs to
                final RichButton button = getButtonAt(x, y);
                touchButton = button != null && !button.isDisabled ? button : null;
                return touchButton != null;
            }

            @Override
            public void onClickTouchDown(View view, float x, float y) {
                setPressedButton(touchButton);
            }

            @Override
            public void onClickTouchMove(View view, float x, float y) {
                // release the visual press while the finger is off the button, without dropping the gesture
                setPressedButton(getButtonAt(x, y) == touchButton ? touchButton : null);
            }

            @Override
            public void onClickTouchUp(View view, float x, float y) {
                setPressedButton(null);
                touchButton = null;
            }

            @Override
            public void onClickAt(View view, float x, float y) {
                if (touchButton != null) {
                    onButtonClick(touchButton);
                }
            }

            @Override
            public boolean needLongPress(float x, float y) {
                return true;
            }

            @Override
            public boolean onLongPressRequestedAt(View view, float x, float y) {
                if (touchButton == null || getButtonAt(x, y) != touchButton) {
                    return false;
                }
                onButtonLongClick(touchButton);
                return true;
            }
        });

        @Override
        protected boolean onTouchEvent(MotionEvent event) {
            if (view == null) {
                return false;
            }
            return clickHelper.onTouchEvent(view, event);
        }

        @Override
        public boolean isHorizontallyDragging() {
            return touchButton != null;
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            for (RichButton button : buttons) {
                button.attach(view);
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            for (RichButton button : buttons) {
                button.detach(view);
            }
        }

    }

    public static class RichButton {
        private static final Xfermode SRC_OUT = new PorterDuffXfermode(PorterDuff.Mode.SRC_OUT);
        private static final Paint SRC_OUT_PAINT = new Paint();

        static {
            SRC_OUT_PAINT.setXfermode(SRC_OUT);
        }

        // inline buttons are compact: the pill hugs the label instead of using the block paddings
        public static final int INLINE_PADDING_HORIZONTAL = 7;
        // how far the pill shrinks while pressed; inline pills are small, so they need a bigger
        // relative drop to read as a bounce at all
        private static final float PRESS_SCALE = 0.04f;
        private static final float PRESS_SCALE_INLINE = 0.09f;
        private static final int PADDING = 20;
        private static final int PADDING_WITH_ICON = 26;
        private static final int MIN_PADDING = 8;
        private static final float ICON_OFFSET_X = 12.33f;
        private static final float ICON_OFFSET_Y = 11.66f;
        private static final float ICON_OFFSET_Y_INLINE = 9.33f;

        public final Text text;
        public final TL_keyboard.InlineButtonType type;
        public final TL_keyboard.RichButtonStyle style;
        public final boolean isDisabled;

        // only set for buttons that came from a page block; inline buttons have no proto to report
        @Nullable
        public final TL_keyboard.PageButton pageButton;

        public int x;
        public int width;

        // inline buttons override the block metrics with their own paddings
        private final boolean inline;
        private final boolean out;

        public final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final ForegroundColorSpanThemable colorSpan;
        private boolean needSaveLayer;
        private Drawable iconDrawable;
        public int backgroundColor;
        public int backgroundPressedColor;
        public int textColor;
        public int textColorKey;
        private ColorFilter textColorFilter;
        private ColorFilter linkColorFilter;
        private int lastLinkColorFilterColor;

        private final RichMessageLayout layout;
        private final boolean emojiFirst;
        private final boolean emojiLast;
        private final boolean link;
        public final Runnable invalidateRunnable;

        public boolean pressed;
        public float pressT;
        public ValueAnimator pressAnimator;

        public LoadingDrawable loadingDrawable;
        private boolean loading;
        private final RectF loadingRect = new RectF();
        private final RectF textFadeRect = new RectF();
        private final Theme.IvButtonColors styleKeys;
        private final int maxWidth;

        public RichButton(RichMessageLayout layout, int maxWidth, TL_keyboard.PageButton button, Runnable invalidateRunnable) {
            this(layout, maxWidth, layout.formatText(button.text, setBlockFlags(TEXT_FLAG_BOLD, TEXT_FLAG_BLOCK_BUTTON)), button, button.type, button.style, TLKeyboardHelper.isType(button, TL_keyboard.TL_inlineButtonTypeDisabled.class), true, false, false, false, false, null, invalidateRunnable);
        }

        public RichButton(RichMessageLayout layout, int maxWidth,
                          CharSequence formattedText,
                          @Nullable TL_keyboard.PageButton pageButton,
                          TL_keyboard.InlineButtonType type,
                          TL_keyboard.RichButtonStyle style,
                          boolean isDisabled, boolean allowIcon,
                          boolean emojiFirst,
                          boolean emojiLast,
                          boolean link,
                          boolean inline,
                          @Nullable Boolean outOverride,
                          Runnable invalidateRunnable) {
            this.layout = layout;
            this.maxWidth = Math.max(1, maxWidth);
            this.invalidateRunnable = invalidateRunnable;
            this.pageButton = pageButton;
            this.type = type;
            this.style = style;
            this.isDisabled = isDisabled;
            this.emojiFirst = emojiFirst;
            this.emojiLast = emojiLast;
            this.inline = inline;
            this.link = link;
            this.out = outOverride != null ? outOverride : layout.isOut();

            Theme.IvButtonColors styleKeys = Theme.IvButtonColors.of(style);
            if (styleKeys == Theme.IvButtonColors.DEFAULT && inline) {
                styleKeys = Theme.IvButtonColors.DEFAULT_IN_TEXT;
            }
            this.styleKeys = styleKeys;

            colorSpan = new ForegroundColorSpanThemable(styleKeys.getTextKey(out));
            colorSpan.setAlpha(isDisabled ? 0.5f : 1f);
            final SpannableStringBuilder formatted = new SpannableStringBuilder(formattedText);
            // applied last so it wins over whatever coloring formatText produced
            if (link) {
                formatted.setSpan(new URLSpanNoUnderline(""), 0, formatted.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else  {
                formatted.setSpan(colorSpan, 0, formatted.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            AndroidUtilities.replaceNewLines(formatted);
            // Give the label its natural width so StaticLayout never wraps it. The button itself
            // is still capped to maxWidth and fades the clipped end when it has to be narrower.
            final int oneLineWidth = Math.max(1,
                (int) Math.ceil(Layout.getDesiredWidth(formatted, layout.textPaint)) + dp(2));
            text = new Text(layout, formatted, oneLineWidth, Layout.Alignment.ALIGN_CENTER);
            text.setDrawAtOrigin(true);
            text.doNotInvalidateEmojiInParent = true;

            final int iconRes = allowIcon ? getButtonIcon(type) : 0;
            if (iconRes != 0) {
                iconDrawable = ApplicationLoader.applicationContext.getResources().getDrawable(iconRes).mutate();
            }

            width = getPreferredWidth();
            updateColors();
        }

        public void updateColors() {
            final Theme.ResourcesProvider resourcesProvider = layout.resourcesProvider;
            final boolean isDark = resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark();
            final boolean isOutInDarkWithGradient;

            if (out && isDark) {
                final int gradientColor1 = layout.getThemedColor(Theme.key_chat_outBubbleGradient1);
                final int gradientColor2 = layout.getThemedColor(Theme.key_chat_outBubbleGradient2);
                final int gradientColor3 = layout.getThemedColor(Theme.key_chat_outBubbleGradient3);
                if (gradientColor1 != 0 || gradientColor2 != 0 || gradientColor3 != 0) {
                    isOutInDarkWithGradient = true;
                } else {
                    isOutInDarkWithGradient = false;
                }
            } else {
                isOutInDarkWithGradient = false;
            }

            Theme.IvButtonColors colors = styleKeys;
            if (isOutInDarkWithGradient && colors != Theme.IvButtonColors.PRIMARY) {
                colors = Theme.IvButtonColors.DEFAULT;
            }

            final int backgroundColorKey = colors.getBackgroundKey(out);
            final int backgroundPressedColorKey = colors.getBackgroundPressedKey(out);
            final int textColorKey = colors.getTextKey(out);

            final int textColor = layout.getThemedColor(textColorKey);

            needSaveLayer = false;
            if (isOutInDarkWithGradient && colors == Theme.IvButtonColors.PRIMARY) {
                backgroundColor = backgroundPressedColor = layout.getThemedColor(textColorKey);
                needSaveLayer = true;
            } else if (Theme.hasThemeKey(backgroundColorKey) || colors == Theme.IvButtonColors.PRIMARY) {
                backgroundColor = layout.getThemedColor(backgroundColorKey);
                if (Theme.hasThemeKey(backgroundPressedColorKey)) {
                    backgroundPressedColor = layout.getThemedColor(backgroundPressedColorKey);
                } else {
                    backgroundPressedColor = ColorUtils.blendARGB(backgroundColor, textColor, 0.1f);
                }
            } else {
                backgroundColor = Theme.multAlpha(layout.getThemedColor(textColorKey), colors == Theme.IvButtonColors.DEFAULT ? 0.08f : 0.1f);
                backgroundPressedColor = Theme.multAlpha(layout.getThemedColor(textColorKey), 0.16f);
            }
            setTextColorKey(textColorKey);
        }

        // the span resolves the key itself on every draw; color does not affect metrics, so no re-layout is needed
        public void setTextColorKey(int colorKey) {
            textColorKey = colorKey;
            int textColorToSet = layout.getThemedColor(colorKey);
            if (isDisabled) {
                textColorToSet = Theme.multAlpha(textColorToSet, 0.5f);
            }
            if (textColorToSet != textColor || textColorFilter == null) {
                textColor = textColorToSet;
                textColorFilter = new PorterDuffColorFilter(textColor, PorterDuff.Mode.SRC_IN);
            }
            colorSpan.setColorKey(colorKey);
            invalidateRunnable.run();
            if (iconDrawable != null) {
                iconDrawable.setColorFilter(new PorterDuffColorFilter(textColor, PorterDuff.Mode.MULTIPLY));
            }
        }

        public int getTextWidth() {
            return text.right - text.left;
        }

        public int getHeight() {
            // no vertical padding: the pill is exactly as tall as the laid-out label
            return inline ? dp(layout.fontSize * 1.166666f) : dp(18 + layout.fontSize);
        }

        // wider padding when there is an icon, so the icon lives inside the right padding

        public int getPaddingLeft() {
            if (link) {
                return 0;
            }
            if (emojiFirst && inline) {
                return dp(4);
            }
            if (inline) {
                return dp(INLINE_PADDING_HORIZONTAL);
            }
            return dp(iconDrawable != null ? PADDING_WITH_ICON : PADDING);
        }

        public int getPaddingRight() {
            if (link) {
                return 0;
            }
            if (emojiLast && inline) {
                return dp(4 + (iconDrawable != null ? 14 : 0));
            }
            if (inline) {
                return dp(INLINE_PADDING_HORIZONTAL + (iconDrawable != null ? 14 : 0));
            }
            return dp(iconDrawable != null ? PADDING_WITH_ICON : PADDING);
        }

        // horizontal strip on the right the text must never run into:
        // the icon is centered at width - ICON_OFFSET, so its left edge is half a size before that
        public int getIconReserve() {
            return iconDrawable != null ? dp(ICON_OFFSET_X) + dp(ICON_OFFSET_Y) / 2 : 0;
        }

        // centered; only icon buttons give up the left padding to keep clear of the icon
        public float getTextX() {
            if (inline) {
                return getPaddingLeft();
            }

            if (getTextWidth() > getTextAvailableWidth()) {
                return getTextViewportLeft();
            }

            if (iconDrawable == null) {
                return (width - getTextWidth()) / 2f;
            }
            float x = (width - getTextWidth()) / 2f;
            x = Math.min(x, width - getIconReserve() - getTextWidth());
            return Math.max(x, dp(MIN_PADDING));
        }

        private int getTextViewportLeft() {
            return link ? 0 : inline ? getPaddingLeft() : dp(MIN_PADDING);
        }

        private int getTextViewportRight() {
            if (link) {
                return width;
            }
            if (inline) {
                return width - getPaddingRight();
            }
            return width - Math.max(dp(MIN_PADDING), getIconReserve());
        }

        private int getTextAvailableWidth() {
            return Math.max(1, getTextViewportRight() - getTextViewportLeft());
        }

        // width the button wants: text plus both paddings
        public int getPreferredWidth() {
            return Math.min(maxWidth, getTextWidth() + getPaddingLeft() + getPaddingRight());
        }

        // lower bound when the row has to be squeezed; keeps the pill shape and the icon strip
        public int getMinWidth() {
            return Math.min(getPreferredWidth(), getHeight() + getIconReserve());
        }

        public void draw(Canvas canvas) {
            final float s = getPressScale();
            canvas.save();

            if (s != 1) {
                canvas.scale(s, s, width / 2f, getHeight() / 2f);
            }
            final boolean drawBackground = /*!onlyEmoji &&*/ !link;
            final boolean saveLayer = needSaveLayer && drawBackground;
            if (saveLayer) {
                canvas.saveLayer(0, 0, width, getHeight(), null);
            } else {
                backgroundPaint.setColor(pressed ? backgroundPressedColor : backgroundColor);
                if (drawBackground) {
                    canvas.drawRoundRect(0, 0, width, getHeight(), getHeight() / 2f, getHeight() / 2f, backgroundPaint);
                }
                drawLoading(canvas);
            }

            canvas.save();
            final int textViewportLeft = getTextViewportLeft();
            final int textViewportRight = getTextViewportRight();
            final int textAvailableWidth = getTextAvailableWidth();
            final boolean fadeText = getTextWidth() > textAvailableWidth;
            if (fadeText) {
                canvas.saveLayer(textViewportLeft, -dp(10), textViewportRight, getHeight() + dp(10), null);
            }
            canvas.clipRect(textViewportLeft, -dp(10), textViewportRight, getHeight() + dp(10));
            canvas.save();

            final int textBaseLine = text.getBaseline();
            final boolean emojiOnly = text.getEmojiOnlyCount() > 0;
            if (emojiOnly) {
                // The emoji's drawn square is taller than this compact inline button. Center its
                // complete inner line box instead of aligning its unusually tall font metrics to
                // the text baseline; otherwise emoji-only buttons sit below direct-emoji rows.
                canvas.translate(getTextX() - text.left, (getHeight() - text.getHeight()) / 2f);
            } else if (textBaseLine > 0) {
                if (inline) {
                    final int baselineY = getHeight() * 43 / 56;
                    canvas.translate(getTextX() - text.left, - textBaseLine + baselineY);
                } else {
                    final int baselineY = getHeight() * 65 / 102;
                    canvas.translate(getTextX() - text.left, - textBaseLine + baselineY);
                }
            } else {
                canvas.translate(getTextX() - text.left, (getHeight() - text.getHeight()) / 2f - dp(1));
            }
            text.draw(canvas);
            if (layout.isOverlayActive()) {
                canvas.translate(0, dp(emojiOnly ? 0 : link ? 0.66f : 2));

                if (link) {
                    final int linkColor = text.layout.getPaint().linkColor;
                    if (lastLinkColorFilterColor != linkColor || linkColorFilter == null) {
                        lastLinkColorFilterColor = linkColor;
                        linkColorFilter = new PorterDuffColorFilter(linkColor, PorterDuff.Mode.SRC_IN);
                    }
                    AnimatedEmojiSpan.drawAnimatedEmojis(canvas, text.layout, text.animatedEmojiStack, 0, text.spoilers, 0, 0, 0, 1, linkColorFilter);
                } else {
                    AnimatedEmojiSpan.drawAnimatedEmojis(canvas, text.layout, text.animatedEmojiStack, 0, text.spoilers, 0, 0, 0, 1, textColorFilter);
                }
            }

            canvas.restore();
            if (fadeText) {
                textFadeRect.set(textViewportRight - dp(8), 0, textViewportRight, getHeight());
                layout.clip.draw(canvas, textFadeRect, GradientClip.RIGHT, 1.0f);
                canvas.restore();
            }
            canvas.restore();
            if (iconDrawable != null) {
                DrawableUtils.setBounds(iconDrawable, width - dp(ICON_OFFSET_X), dp(inline ? ICON_OFFSET_Y_INLINE : ICON_OFFSET_Y), Gravity.CENTER);
                iconDrawable.draw(canvas);
            }
            if (saveLayer) {
                SRC_OUT_PAINT.setColor(pressed ? backgroundPressedColor : backgroundColor);
                canvas.drawRoundRect(0, 0, width, getHeight(), getHeight() / 2f, getHeight() / 2f, SRC_OUT_PAINT);
                canvas.restore();
                drawLoading(canvas);
            }
            canvas.restore();
        }

        private void drawLoading(Canvas canvas) {
            final boolean drawProgress = pageButton != null && layout.cell != null && layout.cell.drawButtonProgress(pageButton);
            setLoading(drawProgress);
            if (loadingDrawable != null && (drawProgress || loadingDrawable.isDisappearing())) {
                // half the stroke sits outside the path, so keep it inside the pill
                final float sw = loadingDrawable.strokePaint.getStrokeWidth();
                loadingRect.set(0, 0, width, getHeight());
                loadingRect.inset(sw / 2f, sw / 2f);
                loadingDrawable.setRadii(getHeight() / 2f - sw / 2f);
                loadingDrawable.setBounds(loadingRect);
                loadingDrawable.setColors(
                        Theme.multAlpha(textColor, 0.07f),
                        Theme.multAlpha(textColor, 0.175f),
                        Theme.multAlpha(textColor, 0.175f),
                        Theme.multAlpha(textColor, 0.42f)
                );
                loadingDrawable.setAlpha(0xFF);
                loadingDrawable.draw(canvas);
                invalidateRunnable.run();
            }
        }

        public void setLoading(boolean loading) {
            if (this.loading == loading) {
                return;
            }
            this.loading = loading;
            if (loading) {
                if (loadingDrawable == null) {
                    loadingDrawable = new LoadingDrawable();
                    loadingDrawable.setAppearByGradient(true);
                    loadingDrawable.strokePaint.setStrokeWidth(AndroidUtilities.dpf2(1.25f));
                } else {
                    loadingDrawable.reset();
                    loadingDrawable.resetDisappear();
                }
            } else if (loadingDrawable != null) {
                loadingDrawable.disappear();
            }
            invalidateRunnable.run();
        }

        public boolean isLoading() {
            return loading;
        }

        public void setPressed(boolean pressed) {
            if (this.pressed == pressed) {
                return;
            }
            this.pressed = pressed;
            invalidateRunnable.run();
            if (pressed) {
                if (pressAnimator != null) {
                    pressAnimator.removeAllListeners();
                    pressAnimator.cancel();
                    pressAnimator = null;
                }
            } else if (pressT != 0) {
                pressAnimator = ValueAnimator.ofFloat(pressT, 0);
                pressAnimator.addUpdateListener(animation -> {
                    pressT = (float) animation.getAnimatedValue();
                    invalidateRunnable.run();
                });
                pressAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        pressAnimator = null;
                    }
                });
                pressAnimator.setInterpolator(new OvershootInterpolator(inline ? 3.5f : 2.0f));
                pressAnimator.setDuration(inline ? 420 : 350);
                pressAnimator.start();
            }
        }

        // ramps up frame by frame while pressed, springs back through the animator on release
        public float getPressScale() {
            if (pressed && pressT != 1f) {
                pressT += (float) Math.min(40, 1000f / AndroidUtilities.screenRefreshRate) / 100f;
                pressT = Utilities.clamp(pressT, 1f, 0);
                invalidateRunnable.run();
            }
            final float depth = inline ? PRESS_SCALE_INLINE : PRESS_SCALE;
            return 1f - depth + depth * (1f - pressT);
        }

        public boolean contains(float x) {
            return x >= this.x && x <= this.x + width;
        }

        public void attach(View view) {
            text.attach(view);
        }

        public void detach(View view) {
            text.detach(view);
        }

        @DrawableRes
        private static int getButtonIcon(TL_keyboard.InlineButtonType type) {
            if (type instanceof TL_keyboard.TL_inlineButtonTypeCopy) {
                return R.drawable.mini_inline_copy_16;
            } else if (type instanceof TL_keyboard.TL_inlineButtonTypeUrlAuth) {
                return R.drawable.mini_inline_arrow_16;
            } else if (type instanceof TL_keyboard.TL_inlineButtonTypeUrl) {
                final String url = ((TL_keyboard.TL_inlineButtonTypeUrl) type).url;
                if (LinkManager.isWebAppLink(url)) {
                    return R.drawable.bot_webview;
                } /*else if (isInviteButton) {
                drawable = Theme.getThemeDrawable(Theme.key_drawable_botInvite, resourcesProvider);
            } */ else {
                    return R.drawable.mini_inline_arrow_16;
                }
            } else if (type instanceof TL_keyboard.TL_inlineButtonTypeWebView) {
                return R.drawable.bot_webview;
            } else if (type instanceof TL_keyboard.TL_inlineButtonTypeSwitchInline) {
                return R.drawable.mini_inline_switch_16;
            } else if (type instanceof TL_keyboard.TL_inlineButtonTypeBuy) {
                return R.drawable.bot_card;
            } else if (type instanceof TL_keyboard.TL_inlineButtonTypeUserProfile) {
                return R.drawable.mini_inline_profile_16;
            }
            return 0;
        }
    }

    public static class RichButtonSpan extends ReplacementSpan {
        // outer gap between the pill and the surrounding text
        private static final int MARGIN_HORIZONTAL = 1;

        private final RichButton button;
        private final TL_iv.textButton textButton;
        // where the pill was last drawn, in the coordinate space Text.onTouchEvent works in
        private final RectF bounds = new RectF();

        public RichButtonSpan(RichMessageLayout layout, int maxWidth, TL_iv.textButton textButton) {
            this(layout, maxWidth, textButton, null);
        }

        private RichButtonSpan(RichMessageLayout layout, int maxWidth, TL_iv.textButton textButton,
                               @Nullable Boolean outOverride) {
            this.textButton = textButton;

            boolean emojiFirst = false;
            boolean emojiLast = false;
            if (textButton.text instanceof TL_iv.textConcat) {
                TL_iv.textConcat textConcat = (TL_iv.textConcat) textButton.text;
                if (!textConcat.texts.isEmpty()) {
                    emojiFirst = textConcat.texts.get(0) instanceof TL_iv.textCustomEmoji;
                    emojiLast = textConcat.texts.get(textConcat.texts.size() - 1) instanceof TL_iv.textCustomEmoji;
                }
            }

            final boolean isLink = textButton.style != null && textButton.style.link;
            final int flags = isLink ?
                    setBlockFlags(TEXT_FLAG_URL, 0) :
                    setBlockFlags(TEXT_FLAG_BOLD, TEXT_FLAG_BLOCK_BUTTON);

            button = new RichButton(
                    layout,
                    maxWidth,
                    layout.formatText(textButton.text, flags),
                    null,
                    textButton.type,
                    textButton.style,
                    textButton.type instanceof TL_keyboard.TL_inlineButtonTypeDisabled,
                    true,
                    emojiFirst,
                    emojiLast,
                    isLink,
                    true,
                    outOverride,
                    this::invalidate
            );
            button.width = button.getPreferredWidth();
        }

        private void invalidate() {
            if (v != null) {
                v.invalidate();
            }
        }

        public RichButton getButton() {
            return button;
        }

        private float scale;
        private boolean preserveFontMetrics;
        private int minimumLineHeight;

        @Override
        public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            final boolean preserveMetrics = preserveFontMetrics && fm != null;
            final int originalTop = preserveMetrics ? fm.top : 0;
            final int originalAscent = preserveMetrics ? fm.ascent : 0;
            final int originalDescent = preserveMetrics ? fm.descent : 0;
            final int originalBottom = preserveMetrics ? fm.bottom : 0;
            final int originalLeading = preserveMetrics ? fm.leading : 0;
            int offset = AndroidUtilities.dp(8);
            int w = AndroidUtilities.dp(10);
            if (fm != null && button.link) {
                fm.top = (int) ((-w - offset) * scale);
                fm.bottom = (int) ((w - offset) * scale);
                fm.ascent = (int) ((-w - offset) * scale);
                fm.descent = (int) ((w - offset) * scale);
                fm.leading = 0;
            }
            if (preserveMetrics) {
                fm.top = originalTop;
                fm.ascent = originalAscent;
                fm.descent = originalDescent;
                fm.bottom = originalBottom;
                fm.leading = originalLeading;
                expandFontMetrics(fm, minimumLineHeight);
            }
            return button.width + (button.link ? 0 : (dp(MARGIN_HORIZONTAL) * 2));
        }

        private static void expandFontMetrics(Paint.FontMetricsInt fm, int minimumHeight) {
            final int currentHeight = fm.descent - fm.ascent;
            if (minimumHeight <= currentHeight) {
                return;
            }
            final int extra = minimumHeight - currentHeight;
            final int above = (extra + 1) / 2;
            final int below = extra - above;
            fm.ascent -= above;
            fm.descent += below;
            fm.top = Math.min(fm.top, fm.ascent);
            fm.bottom = Math.max(fm.bottom, fm.descent);
        }

        @Override
        public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
            boolean centerEmojiInLineBox = false;
            if (button.text.getEmojiOnlyCount() > 0) {
                centerEmojiInLineBox = minimumLineHeight > 0;
            }
            if (!centerEmojiInLineBox && button.text.getEmojiOnlyCount() > 0
                    && text instanceof Spanned) {
                final Spanned spanned = (Spanned) text;
                for (StyleSpan span : spanned.getSpans(start, end, StyleSpan.class)) {
                    if ((span.flags & TEXT_FLAG_BLOCKS) == TEXT_FLAG_BLOCK_TABLE) {
                        centerEmojiInLineBox = true;
                        break;
                    }
                }
            }
            float center;
            if (centerEmojiInLineBox) {
                // Direct animated emojis are centered in StaticLayout's complete line box. Match
                // that center for emoji-only buttons in table cells and expanded emoji-grid rows.
                center = (top + bottom) / 2f;
            } else {
                final Paint.FontMetricsInt fm = paint.getFontMetricsInt();
                center = y + (fm.ascent + fm.descent) / 2f;
            }
            canvas.save();
            // nudged up by OFFSET_VERTICAL: the pill reads low when centered on the text metrics
            final int pillLeft = Math.round(x + (button.link ? 0 : dp(MARGIN_HORIZONTAL)));
            final int pillTop = (int) Math.ceil(
                center - button.getHeight() / 2f + (centerEmojiInLineBox ? 0 : 1)
            );
            bounds.set(pillLeft, pillTop, pillLeft + button.width, pillTop + button.getHeight());
            canvas.translate(pillLeft, pillTop);
            button.draw(canvas);
            canvas.restore();
        }

        public boolean isDisabled() {
            return button.isDisabled;
        }

        public boolean contains(float x, float y) {
            return bounds.contains(x, y);
        }

        public void setPressed(boolean pressed) {
            button.setPressed(pressed);
        }

        public void didPress(ChatMessageCell cell, ChatMessageCell.ChatMessageCellDelegate delegate, boolean longPress) {
            if (cell == null || delegate == null) {
                return;
            }
            if (longPress) {
                delegate.didLongPressBotButton(cell, textButton);
            } else {
                delegate.didPressBotButton(cell, textButton);
            }
        }

        private View v;
        public void attach(View view) {
            v = view;
            button.attach(view);
        }

        public void detach(View view) {
            v = null;
            button.detach(view);
        }
    }

    /* * */

    public static class RichPreformattedBlock extends RichBlock {

        public final Text text;
        public final Text[] texts;

        @Override
        public void appendAccessibilityText(SpannableStringBuilder sb) {
            appendText(sb, text, texts);
        }

        private final int viewportWidth;
        private final int contentWidth;
        private final int maxScrollX;
        private int scrollX;

        private float downX;
        private int downScrollX;
        private boolean dragging;
        private boolean textHandlingTouch;
        private int touchSlop;

        private static final int HPAD = 0;
        private static final int VPAD = 8;
        private static final int BACKGROUND_OUTER_VPAD = 7;
        private static final int SCROLLBAR_HEIGHT = 5;
        private static final int SCROLLBAR_HPAD = 6;
        private static final int SCROLLBAR_VPAD = 7;

        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        public String plain;
        public SpannableString content;
        public final String language;

        @Override
        public CharSequence getAccessibilityLabel() {
            final CharSequence label = super.getAccessibilityLabel();
            if (TextUtils.isEmpty(language)) return label;
            final String languageName = MessageObject.TextLayoutBlock.capitalizeLanguage(language);
            if (TextUtils.isEmpty(languageName)) return label;
            return TextUtils.concat(label, " (", languageName, ")");
        }

        public RichPreformattedBlock(
            RichMessageLayout root,
            Rect padding, int maxWidth,
            TL_iv.pageBlockPreformatted block,
            RichPreformattedBlock prevBlock
        ) {
            super(root, padding, maxWidth);
            this.viewportWidth = this.maxWidth;
            this.language = block.language;

            plain = root.getString(block.text);
            if (plain == null) plain = "";
            content = new SpannableString(plain);
            if (content.length() > 0) {
                content.setSpan(new StyleSpan(root, TEXT_FLAG_BLOCK_CODE), 0, content.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                if (prevBlock != null) {
                    final SpannableString oldContent = prevBlock.content;
                    final boolean prevIsLocked = oldContent instanceof CodeHighlighting.LockedWithFallbackSpannableString;
                    final boolean prevReady = !prevIsLocked || ((CodeHighlighting.LockedWithFallbackSpannableString) oldContent).ready;
                    final CharSequence source = prevReady
                        ? oldContent
                        : ((CodeHighlighting.LockedWithFallbackSpannableString) oldContent).fallback;
                    if (source != null && source.length() > 0 && plain.length() >= source.length()) {
                        if (source instanceof CodeHighlighting.LockedWithFallbackSpannableString)
                            ((CodeHighlighting.LockedWithFallbackSpannableString) source).fallback = null;
                        final SpannableStringBuilder fallback = new SpannableStringBuilder(source).append(plain.substring(source.length()));
                        final StyleSpan[] spans = fallback.getSpans(0, fallback.length(), StyleSpan.class);
                        for (int i = 0; i < spans.length; ++i) {
                            fallback.removeSpan(spans[i]);
                        }
                        fallback.setSpan(new StyleSpan(root, TEXT_FLAG_BLOCK_CODE), 0, fallback.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        final CodeHighlighting.Span[] codeSpans = fallback.getSpans(0, fallback.length(), CodeHighlighting.Span.class);
                        for (int i = 0; i < codeSpans.length; ++i) {
                            final int start = fallback.getSpanStart(codeSpans[i]);
                            final int end   = fallback.getSpanStart(codeSpans[i]);
                            fallback.removeSpan(codeSpans[i]);
                            fallback.setSpan(codeSpans[i], start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }
                        content = new CodeHighlighting.LockedWithFallbackSpannableString(content, fallback);
                    }
                }
                if (!TextUtils.isEmpty(block.language)) {
                    CodeHighlighting.highlight(content, 0, content.length(), block.language, 0, null, false);
                }
            }

            this.text = new Text(root, content, dp(5000), Layout.Alignment.ALIGN_NORMAL, 1.30f);
            this.texts = new Text[] { this.text };

            contentWidth = Math.max(0, text.right - text.left) + dp(HPAD) * 2;
            maxScrollX = Math.max(0, contentWidth - viewportWidth);
            if (prevBlock != null) {
                scrollX = Utilities.clamp(prevBlock.scrollX, maxScrollX, 0);
            }
        }

        private void drawBackground(Canvas canvas) {
            bgPaint.setColor(root.getThemedColor(root.isOut()
                ? Theme.key_chat_outArticleCodeBackground
                : Theme.key_chat_inArticleCodeBackground));
            if (padding.left > 0) {
                canvas.drawRect(0, 0, root.getMinWidth() - padding.left - padding.right, getBackgroundHeight(), bgPaint);
            } else {
                canvas.drawRect(-root.padLeft, 0, root.getMinWidth() + root.padRight, getBackgroundHeight(), bgPaint);
            }
        }

        private int getBackgroundHeight() {
            return text.getHeight() + dp(VPAD * 2) + (maxScrollX > 0 ? dp(SCROLLBAR_HEIGHT + SCROLLBAR_VPAD * 2) : 0);
        }

        private void drawScrollbar(Canvas canvas) {
            if (maxScrollX <= 0) return;

            final float backgroundLeft;
            final float backgroundRight;
            if (padding.left > 0) {
                backgroundLeft = 0;
                backgroundRight = root.getMinWidth() - padding.left - padding.right;
            } else {
                backgroundLeft = -root.padLeft;
                backgroundRight = root.getMinWidth() + root.padRight;
            }

            final float trackLeft = backgroundLeft + dp(SCROLLBAR_HPAD);
            final float trackRight = backgroundRight - dp(SCROLLBAR_HPAD);
            if (trackRight <= trackLeft) return;

            final float trackTop = text.getHeight() + dp(VPAD * 2 + SCROLLBAR_VPAD);
            final float trackBottom = trackTop + dp(SCROLLBAR_HEIGHT);
            final float radius = dp(SCROLLBAR_HEIGHT / 2f);

            bgPaint.setColor(root.getThemedColor(root.isOut()
                ? Theme.key_chat_outArticleCodeScrollbarBackground
                : Theme.key_chat_inArticleCodeScrollbarBackground));
            canvas.drawRoundRect(trackLeft, trackTop, trackRight, trackBottom, radius, radius, bgPaint);

            final float trackWidth = trackRight - trackLeft;
            final float thumbWidth = Math.min(trackWidth, Math.max(dp(SCROLLBAR_HEIGHT), trackWidth * viewportWidth / (float) contentWidth));
            final float thumbLeft = trackLeft + (trackWidth - thumbWidth) * scrollX / (float) maxScrollX;
            bgPaint.setColor(root.getThemedColor(root.isOut()
                ? Theme.key_chat_outArticleCodeScrollbar
                : Theme.key_chat_inArticleCodeScrollbar));
            canvas.drawRoundRect(thumbLeft, trackTop, thumbLeft + thumbWidth, trackBottom, radius, radius, bgPaint);
        }

        private void drawTextContent(Canvas canvas, boolean faded, int lineIndex, float xPosition) {
            final int bgWidth = Math.min(viewportWidth, contentWidth);
            final int bgHeight = text.getHeight() + dp(VPAD) * 2;

            if (padding.left > 0) {
                canvas.save();
                canvas.clipRect(0, 0, bgWidth, bgHeight);
                canvas.translate(dp(HPAD) - scrollX, dp(VPAD));
                if (faded) {
                    text.drawFade(canvas, lineIndex, xPosition);
                } else {
                    text.draw(canvas);
                }
                canvas.restore();
            } else {
                canvas.saveLayerAlpha(-root.padLeft, 0, bgWidth + root.padRight, bgHeight, 0xFF, Canvas.ALL_SAVE_FLAG);
                canvas.save();
                canvas.translate(dp(HPAD) - scrollX, dp(VPAD));
                if (faded) {
                    text.drawFade(canvas, lineIndex, xPosition);
                } else {
                    text.draw(canvas);
                }
                canvas.restore();

                AndroidUtilities.rectTmp.set(-root.padLeft, 0, -root.padLeft + dp(12), bgHeight);
                root.clip.draw(canvas, AndroidUtilities.rectTmp, GradientClip.LEFT, 1.0f);

                final int right = root.getMinWidth() + root.padRight - padding.left - padding.right;
                AndroidUtilities.rectTmp.set(right - dp(12), 0, right, bgHeight);
                root.clip.draw(canvas, AndroidUtilities.rectTmp, GradientClip.RIGHT, 1.0f);

                canvas.restore();
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.save();
            canvas.translate(0, dp(BACKGROUND_OUTER_VPAD));
            drawBackground(canvas);
            drawTextContent(canvas, false, 0, 0f);
            drawScrollbar(canvas);
            canvas.restore();
        }

        @Override
        protected void onDrawFaded(Canvas canvas, int lineIndex, float xPosition) {
            final Layout layout = text.layout;
            if (layout == null || lineIndex < 0 || lineIndex >= layout.getLineCount()) {
                onDraw(canvas);
                return;
            }
            canvas.save();
            canvas.translate(0, dp(BACKGROUND_OUTER_VPAD));
            drawBackground(canvas);
            drawTextContent(canvas, true, lineIndex, xPosition);
            drawScrollbar(canvas);
            canvas.restore();
        }

        @Override
        public Layout getLayout() {
            return text.layout;
        }

        @Override
        public int getHeight() {
            return padding.top + dp(BACKGROUND_OUTER_VPAD) * 2 + getBackgroundHeight() + padding.bottom;
        }

        @Override
        public int getMinWidth() {
            return padding.left + Math.min(viewportWidth, contentWidth) + padding.right;
        }

        @Override
        public int getLastLineWidth() {
            return getMinWidth();
        }

        private int minFlingVelocity, maxFlingVelocity;
        private VelocityTracker velocityTracker;
        private OverScroller scroller;
        private final Runnable flingTick = new Runnable() {
            @Override public void run() {
                if (scroller == null || view == null) return;
                if (scroller.computeScrollOffset()) {
                    int next = scroller.getCurrX();
                    if (next < 0) next = 0;
                    if (next > maxScrollX) next = maxScrollX;
                    if (next != scrollX) {
                        scrollX = next;
                        placeTexts(layoutX, layoutY, layoutRow);
                        view.invalidate();
                    }
                    if (!scroller.isFinished()) {
                        view.postOnAnimation(this);
                    }
                }
            }
        };

        private void ensureTouchConfig() {
            if (touchSlop == 0 && view != null) {
                final ViewConfiguration vc = ViewConfiguration.get(view.getContext());
                touchSlop = vc.getScaledTouchSlop();
                minFlingVelocity = vc.getScaledMinimumFlingVelocity();
                maxFlingVelocity = vc.getScaledMaximumFlingVelocity();
            }
            if (scroller == null && view != null) {
                scroller = new android.widget.OverScroller(view.getContext());
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            final int act = event.getActionMasked();
            final float dxOffset = dp(HPAD) - scrollX;
            final float dyOffset = dp(BACKGROUND_OUTER_VPAD + VPAD);

            if (act == MotionEvent.ACTION_DOWN) {
                ensureTouchConfig();
                if (scroller != null && !scroller.isFinished()) {
                    scroller.forceFinished(true);
                }
                downX = event.getX();
                downScrollX = scrollX;
                dragging = false;
                if (velocityTracker == null) velocityTracker = VelocityTracker.obtain();
                else velocityTracker.clear();
                velocityTracker.addMovement(event);
                event.offsetLocation(-dxOffset, -dyOffset);
                textHandlingTouch = text.onTouchEvent(event);
                event.offsetLocation(dxOffset, dyOffset);
                return true;
            }
            if (act == MotionEvent.ACTION_MOVE) {
                if (velocityTracker != null) velocityTracker.addMovement(event);
                final float dx = event.getX() - downX;
                if (!dragging && maxScrollX > 0 && Math.abs(dx) > touchSlop) {
                    dragging = true;
                    requestDisallowParentIntercept(true);
                    if (textHandlingTouch) {
                        final MotionEvent cancel = MotionEvent.obtain(event);
                        cancel.setAction(MotionEvent.ACTION_CANCEL);
                        cancel.offsetLocation(-dxOffset, -dyOffset);
                        text.onTouchEvent(cancel);
                        cancel.recycle();
                        textHandlingTouch = false;
                    }
                }
                if (dragging) {
                    int next = (int) (downScrollX - dx);
                    if (next < 0) next = 0;
                    if (next > maxScrollX) next = maxScrollX;
                    if (next != scrollX) {
                        scrollX = next;
                        placeTexts(layoutX, layoutY, layoutRow);
                        if (view != null) view.invalidate();
                    }
                    return true;
                }
                return textHandlingTouch;
            }
            if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
                final boolean wasDragging = dragging;
                dragging = false;
                if (wasDragging) {
                    requestDisallowParentIntercept(false);
                    if (act == MotionEvent.ACTION_UP && velocityTracker != null && scroller != null && view != null) {
                        velocityTracker.addMovement(event);
                        velocityTracker.computeCurrentVelocity(1000, maxFlingVelocity);
                        final float xv = -velocityTracker.getXVelocity();
                        if (Math.abs(xv) > minFlingVelocity) {
                            scroller.fling(scrollX, 0, (int) xv, 0, 0, maxScrollX, 0, 0);
                            view.postOnAnimation(flingTick);
                        }
                    }
                }
                if (!wasDragging && textHandlingTouch) {
                    event.offsetLocation(-dxOffset, -dyOffset);
                    text.onTouchEvent(event);
                    event.offsetLocation(dxOffset, dyOffset);
                }
                textHandlingTouch = false;
                if (velocityTracker != null) {
                    velocityTracker.recycle();
                    velocityTracker = null;
                }
                return wasDragging || act == MotionEvent.ACTION_UP;
            }
            return false;
        }

        @Override
        public boolean isHorizontallyDragging() {
            return dragging || (scroller != null && !scroller.isFinished());
        }

        @Override
        public boolean findLink(CharacterStyle link, int blockY, FoundLink out) {
            if (text.fillFoundLink(link, out)) {
                out.x = padding.left + dp(HPAD) - scrollX - text.left;
                out.y = blockY + padding.top + dp(BACKGROUND_OUTER_VPAD + VPAD);
                return true;
            }
            return false;
        }

        @Override
        protected TextSelectionHelper.TextLayoutBlock[] getText() {
            return texts;
        }

        @Override
        protected void placeTexts(int blockX, int blockY, int row) {
            this.layoutX = blockX;
            this.layoutY = blockY;
            this.layoutRow = row;
            text.setX(blockX + dp(HPAD) - scrollX - text.left);
            text.setY(blockY + dp(BACKGROUND_OUTER_VPAD + VPAD));
            text.setRow(row);
        }

        @Override
        protected void onAttachedToWindow() {
            text.attach(view);
        }
        @Override
        protected void onDetachedFromWindow() {
            text.detach(view);
        }
    }

    public static class RichMathBlock extends RichBlock {

        private final TL_iv.pageBlockMath block;

        @Override
        public void appendAccessibilityText(SpannableStringBuilder sb) {
            if (block != null && !TextUtils.isEmpty(block.source)) {
                sb.append(block.source);
            }
        }

        private Bitmap bitmap;
        private int contentW, contentH;

        private final int viewportWidth;
        private final int contentWidth;
        private final int maxScrollX;
        private int scrollX;

        private float downX;
        private int downScrollX;
        private boolean dragging;
        private int touchSlop;

        private static final int HPAD = 0;
        private static final int VPAD = 8;

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        public RichMathBlock(
            RichMessageLayout root,
            Rect padding, int maxWidth,
            TL_iv.pageBlockMath block
        ) {
            super(root, padding, maxWidth);
            this.block = block;
            this.viewportWidth = this.maxWidth;

            final Latex r = block != null && !TextUtils.isEmpty(block.source)
                ? Latex.render(block.source, dp(4 + root.fontSize), false) : null;
            if (r != null) {
                bitmap = r.bitmap;
                contentW = r.width;
                contentH = r.height;
            }
            contentWidth = contentW + dp(HPAD) * 2;
            maxScrollX = Math.max(0, contentWidth - viewportWidth);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (bitmap == null) return;
            paint.setColor(root.getThemedColor(root.isOut() ? Theme.key_chat_messageTextOut : Theme.key_chat_messageTextIn));

            final int bgHeight = contentH + dp(VPAD) * 2;
            if (maxScrollX > 0) {
                canvas.saveLayerAlpha(-root.padLeft, 0, root.getMinWidth() + root.padRight - padding.left - padding.right, bgHeight, 0xFF, Canvas.ALL_SAVE_FLAG);
                canvas.save();
                canvas.translate(dp(HPAD) - scrollX, dp(VPAD));
            } else {
                final float cx = root.getMinWidth() / 2f - padding.left - contentW / 2f;
                canvas.save();
                canvas.translate(cx, dp(VPAD));
            }
            canvas.drawBitmap(bitmap, 0, 0, paint);
            if (maxScrollX > 0) {
                canvas.restore();

                AndroidUtilities.rectTmp.set(-root.padLeft, 0, -root.padLeft + dp(12), bgHeight);
                root.clip.draw(canvas, AndroidUtilities.rectTmp, GradientClip.LEFT, 1.0f);

                final int right = root.getMinWidth() + root.padRight - padding.left - padding.right;
                AndroidUtilities.rectTmp.set(right - dp(12), 0, right, bgHeight);
                root.clip.draw(canvas, AndroidUtilities.rectTmp, GradientClip.RIGHT, 1.0f);
            }
            canvas.restore();
        }

        @Override
        public int getHeight() {
            return padding.top + contentH + dp(VPAD) * 2 + padding.bottom;
        }

        @Override
        public int getMinWidth() {
            return padding.left + Math.min(viewportWidth, contentWidth) + padding.right;
        }

        @Override
        public int getLastLineWidth() {
            return getMinWidth();
        }

        private int minFlingVelocity, maxFlingVelocity;
        private VelocityTracker velocityTracker;
        private OverScroller scroller;
        private final Runnable flingTick = new Runnable() {
            @Override public void run() {
                if (scroller == null || view == null) return;
                if (scroller.computeScrollOffset()) {
                    int next = scroller.getCurrX();
                    if (next < 0) next = 0;
                    if (next > maxScrollX) next = maxScrollX;
                    if (next != scrollX) {
                        scrollX = next;
                        view.invalidate();
                    }
                    if (!scroller.isFinished()) {
                        view.postOnAnimation(this);
                    }
                }
            }
        };

        private void ensureTouchConfig() {
            if (touchSlop == 0 && view != null) {
                final ViewConfiguration vc = ViewConfiguration.get(view.getContext());
                touchSlop = vc.getScaledTouchSlop();
                minFlingVelocity = vc.getScaledMinimumFlingVelocity();
                maxFlingVelocity = vc.getScaledMaximumFlingVelocity();
            }
            if (scroller == null && view != null) {
                scroller = new android.widget.OverScroller(view.getContext());
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (maxScrollX <= 0) return false;
            final int act = event.getActionMasked();

            if (act == MotionEvent.ACTION_DOWN) {
                ensureTouchConfig();
                if (scroller != null && !scroller.isFinished()) {
                    scroller.forceFinished(true);
                }
                downX = event.getX();
                downScrollX = scrollX;
                dragging = false;
                if (velocityTracker == null) velocityTracker = VelocityTracker.obtain();
                else velocityTracker.clear();
                velocityTracker.addMovement(event);
                return true;
            }
            if (act == MotionEvent.ACTION_MOVE) {
                if (velocityTracker != null) velocityTracker.addMovement(event);
                final float dx = event.getX() - downX;
                if (!dragging && Math.abs(dx) > touchSlop) {
                    dragging = true;
                    requestDisallowParentIntercept(true);
                }
                if (dragging) {
                    int next = (int) (downScrollX - dx);
                    if (next < 0) next = 0;
                    if (next > maxScrollX) next = maxScrollX;
                    if (next != scrollX) {
                        scrollX = next;
                        if (view != null) view.invalidate();
                    }
                    return true;
                }
                return false;
            }
            if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
                final boolean wasDragging = dragging;
                dragging = false;
                if (wasDragging) {
                    requestDisallowParentIntercept(false);
                    if (act == MotionEvent.ACTION_UP && velocityTracker != null && scroller != null && view != null) {
                        velocityTracker.addMovement(event);
                        velocityTracker.computeCurrentVelocity(1000, maxFlingVelocity);
                        final float xv = -velocityTracker.getXVelocity();
                        if (Math.abs(xv) > minFlingVelocity) {
                            scroller.fling(scrollX, 0, (int) xv, 0, 0, maxScrollX, 0, 0);
                            view.postOnAnimation(flingTick);
                        }
                    }
                }
                if (velocityTracker != null) {
                    velocityTracker.recycle();
                    velocityTracker = null;
                }
                return wasDragging || act == MotionEvent.ACTION_UP;
            }
            return false;
        }

        @Override
        public boolean isHorizontallyDragging() {
            return dragging || (scroller != null && !scroller.isFinished());
        }
    }

    public static class RichThinkingBlock extends RichBlock {

        public final Text text;
        public final Text[] texts;

        @Override
        public void appendAccessibilityText(SpannableStringBuilder sb) {
            appendText(sb, text, texts);
        }

        public int gradientColor;
        public LinearGradient gradient;
        public final Matrix matrix = new Matrix();
        public final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public RichThinkingBlock(
            RichMessageLayout root,
            Rect padding, int maxWidth,
            CharSequence text
        ) {
            super(root, padding, maxWidth);

            this.text = new Text(root, text, this.maxWidth);
            this.texts = new Text[1];
            this.texts[0] = this.text;
        }

        private int rtlOffset() {
            if (!root.isRtl()) return 0;
            return root.getMinWidth() + root.padRight - dp(14) - padding.right - padding.left - text.getMinWidth();
        }

        private void updateGradient() {
            final int color = root.getThemedColor(root.isOut() ? Theme.key_chat_messageTextOut : Theme.key_chat_messageTextIn);
            if (gradient == null || gradientColor != color) {
                gradientColor = color;
                gradient = new LinearGradient(0, 0, maxWidth, 0, new int[] { Theme.multAlpha(color, 0.7f), Theme.multAlpha(color, 0.25f), Theme.multAlpha(color, 0.7f) }, new float[] {0, 0.5f, 1.0f}, Shader.TileMode.REPEAT);
                paint.setShader(gradient);
                paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.saveLayerAlpha(0, 0, root.getMinWidth(), getHeight(), 0xFF, Canvas.ALL_SAVE_FLAG);

            final int off = rtlOffset();
            if (off != 0) {
                text.setX(padding.left + off - text.left);
                canvas.save();
                canvas.translate(off, 0);
            }
            text.draw(canvas);
            if (root.isOverlayActive()) {
                canvas.save();
                canvas.translate(-text.left, 0);
                AnimatedEmojiSpan.drawAnimatedEmojis(canvas, text.layout, text.animatedEmojiStack, 0, text.spoilers, 0, 0, 0, 1.0f);
                canvas.restore();
            }
            if (off != 0) {
                canvas.restore();
            }

            updateGradient();
            matrix.reset();
            matrix.postTranslate((System.currentTimeMillis() % 2000) / 2000f * maxWidth, 0);
            gradient.setLocalMatrix(matrix);
            canvas.drawRect(0, 0, root.getMinWidth(), getHeight(), paint);

            canvas.restore();

            if (view != null) {
                view.invalidate();
            }
        }

        @Override
        protected void onDrawFaded(Canvas canvas, int lineIndex, float xPosition) {
            canvas.saveLayerAlpha(0, 0, root.getMinWidth(), getHeight(), 0xFF, Canvas.ALL_SAVE_FLAG);

            final int off = rtlOffset();
            if (off != 0) {
                text.setX(padding.left + off - text.left);
            }
            canvas.save();
            canvas.translate(off - text.left, 0);
            final int wasColor = root.textPaint.getColor();
            root.textPaint.setColor(0xFFFFFFFF);
            root.textPaint.linkColor = root.getThemedColor(root.isOut() ? Theme.key_chat_messageLinkOut : Theme.key_chat_messageLinkIn);
            final View v = view;
            MultiLayoutTypingAnimator.drawLayoutWithLastLineFade(canvas, text.layout, lineIndex, xPosition, c -> {
                SpoilerEffect.renderWithRipple(v, false, 0xFFFFFFFF, 0, text.spoilersPatchedTextLayout, 0, text.layout, text.spoilers, c, false);
                AnimatedEmojiSpan.drawAnimatedEmojis(c, text.layout, text.animatedEmojiStack, 0, text.spoilers, 0, 0, 0, 1.0f);
            });
            canvas.restore();
            root.textPaint.setColor(wasColor);

            updateGradient();
            matrix.reset();
            matrix.postTranslate((System.currentTimeMillis() % 2000) / 2000f * maxWidth, 0);
            gradient.setLocalMatrix(matrix);
            canvas.drawRect(0, 0, root.getMinWidth(), getHeight(), paint);

            canvas.restore();

            if (view != null) {
                view.invalidate();
            }
        }

        @Override
        public Layout getLayout() {
            return text.layout;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            final int off = rtlOffset();
            if (off == 0) return text.onTouchEvent(event);
            event.offsetLocation(-off, 0);
            final boolean h = text.onTouchEvent(event);
            event.offsetLocation(off, 0);
            return h;
        }

        @Override
        public boolean findLink(CharacterStyle link, int blockY, FoundLink out) {
            if (text.fillFoundLink(link, out)) {
                out.x = padding.left + rtlOffset() - text.left;
                out.y = blockY + padding.top;
                return true;
            }
            return false;
        }

        @Override
        public int getHeight() {
            return padding.top + text.getHeight() + padding.bottom;
        }

        @Override
        public int getMinWidth() {
            return padding.left + text.getMinWidth() + padding.right;
        }

        @Override
        public int getLastLineWidth() {
            return padding.left + text.getLastLineWidth() + padding.right;
        }

        @Override
        protected TextSelectionHelper.TextLayoutBlock[] getText() {
            return this.texts;
        }

        @Override
        protected void placeTexts(int blockX, int blockY, int row) {
            super.placeTexts(blockX, blockY, row);
            final int off = rtlOffset();
            if (off != 0) {
                text.setX(blockX + off - text.left);
            }
        }

        @Override
        public boolean drawOverlay(Canvas canvas, ColorFilter colorFilter) {
            return false;
        }

        @Override
        protected void onAttachedToWindow() {
            text.attach(view);
        }
        @Override
        protected void onDetachedFromWindow() {
            text.detach(view);
        }

    }

    static final class SpoilerReveal {
        float progress;
        boolean revealed;
        private float cx, cy, maxR;
        private ValueAnimator animator;
        private final Path path = new Path();

        boolean fullyRevealed() {
            return revealed && progress >= 1f;
        }

        boolean isRevealing() {
            return revealed || animator != null;
        }

        void start(View view, float cx, float cy, float w, float h) {
            if (revealed || animator != null) return;
            this.cx = cx;
            this.cy = cy;
            this.maxR = (float) Math.sqrt(w * w + h * h);
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration((long) Utilities.clamp(maxR * 0.3f, 550, 250));
            animator.setInterpolator(CubicBezierInterpolator.EASE_BOTH);
            animator.addUpdateListener(a -> {
                progress = (float) a.getAnimatedValue();
                if (view != null) view.invalidate();
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    revealed = true;
                    animator = null;
                    if (view != null) view.invalidate();
                }
            });
            animator.start();
        }

        void clipOut(Canvas canvas) {
            if (progress > 0f) {
                path.rewind();
                path.addCircle(cx, cy, maxR * progress, Path.Direction.CW);
                canvas.clipPath(path, Region.Op.DIFFERENCE);
            }
        }
    }

    public static abstract class RichMediaBlock extends RichBlock
        implements DownloadController.FileDownloadProgressListener {

        public final boolean first;
        public final ImageReceiver imageReceiver = new ImageReceiver();
        public final ImageReceiver blurImageReceiver = new ImageReceiver();
        private Bitmap blurSource;
        protected RadialProgress2 radialProgress;
        protected int imgWidth, imgHeight;
        protected boolean autoDownload;
        private final int observerTag;
        private int buttonState = -1;
        private boolean buttonPressed;
        private boolean photoPressed;
        private int buttonX, buttonY;
        private final int buttonSize = dp(48);
        private static Paint mediaBgPaint;
        private static ColorMatrixColorFilter fancyBlurFilter;

        public RichMediaBlock(RichMessageLayout root, Rect padding, int maxWidth, boolean first) {
            super(root, padding, maxWidth);
            this.first = first;
            observerTag = DownloadController.getInstance(root.currentAccount).generateObserverTag();
            imageReceiver.setAllowLoadingOnAttachedOnly(true);
            blurImageReceiver.setAllowLoadingOnAttachedOnly(true);
            imageReceiver.setDelegate(new ImageReceiver.ImageReceiverDelegate() {
                @Override
                public void didSetImage(ImageReceiver imageReceiver, boolean set, boolean thumb, boolean memCache) {}
                @Override
                public void onAnimationReady(ImageReceiver imageReceiver) {
                    updateButtonState(true);
                }
            });
        }

        public abstract TL_iv.PageBlock getBlock();

        protected abstract void applyImage(boolean allowMedia);

        protected abstract String getFileName();
        protected abstract boolean fileExists();

        protected boolean isRealVideo() { return false; }
        protected boolean isAnimatedContent() { return false; }
        protected boolean allowAutoplay() { return true; }
        protected boolean mediaForced;

        protected void finishLayout() {
            imageReceiver.setImageCoords(0, 0, imgWidth, imgHeight);
            buttonX = (imgWidth - buttonSize) / 2;
            buttonY = (imgHeight - buttonSize) / 2;
            autoDownload = computeAutoDownload();
            applyImage(autoDownload || fileExists());
        }

        protected boolean computeAutoDownload() {
            return (DownloadController.getInstance(root.currentAccount).getCurrentDownloadMask() & DownloadController.AUTODOWNLOAD_TYPE_PHOTO) != 0;
        }

        private int availWidth() {
            return root.getMinWidth() - padding.left - padding.right;
        }

        protected int getImageLeft() {
            final int avail = availWidth();
            return avail > imgWidth ? (avail - imgWidth) / 2 : 0;
        }

        private void prepareBlurImage() {
            if (blurImageReceiver.getBitmap() != null && imageReceiver.getAnimation() != null) return;
            final Bitmap bitmap = imageReceiver.getBitmap();
            if (bitmap == null || bitmap.isRecycled()) return;
            if (bitmap == blurSource && blurImageReceiver.getBitmap() != null) return;
            blurSource = bitmap;
            blurImageReceiver.setImageBitmap(Utilities.stackBlurBitmapMax(bitmap, false));
            if (fancyBlurFilter == null) {
                final ColorMatrix colorMatrix = new ColorMatrix();
                AndroidUtilities.multiplyBrightnessColorMatrix(colorMatrix, .9f);
                AndroidUtilities.adjustSaturationColorMatrix(colorMatrix, +.6f);
                fancyBlurFilter = new ColorMatrixColorFilter(colorMatrix);
            }
            blurImageReceiver.setColorFilter(fancyBlurFilter);
        }

        private final Path clipPath = new Path();
        @Override
        protected void onDraw(Canvas canvas) {
            if (mediaBgPaint == null) {
                mediaBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                mediaBgPaint.setColor(0x0f000000);
            }
            final int width = root.getMinWidth() - padding.left - padding.right;
            final boolean inQuote = isInQuote();
            final int pad = dp(2);
            final int bleedL = inQuote ? 0 : root.padLeft - pad;
            final int bleedR = inQuote ? 0 : root.padRight - pad;
            final boolean fit = availWidth() > imgWidth;
            if (inQuote) {
                canvas.save();
                clipPath.rewind();
                clipPath.addRoundRect(0, 0, width, imgHeight, dp(8), dp(8), Path.Direction.CW);
                canvas.clipPath(clipPath);
            }
            if (!imageReceiver.hasBitmapImage() || imageReceiver.getCurrentAlpha() != 1.0f) {
                canvas.drawRect(-bleedL, 0, width + bleedR, imgHeight, mediaBgPaint);
            }
            if (fit) {
                prepareBlurImage();
                updateRoundRadius(blurImageReceiver, false);
                updateRoundRadius(imageReceiver, true);
                if (blurImageReceiver.getBitmap() != null) {
                    blurImageReceiver.setImageCoords(-bleedL, 0, bleedL + width + bleedR, imgHeight);
                    blurImageReceiver.setAlpha(imageReceiver.getCurrentAlpha());
                    blurImageReceiver.draw(canvas);
                }
                imageReceiver.setAspectFit(true);
                imageReceiver.setImageCoords(0, 0, availWidth(), imgHeight);
            } else {
                updateRoundRadius(imageReceiver, false);
                imageReceiver.setAspectFit(false);
                imageReceiver.setImageCoords(-bleedL, 0, bleedL + width + bleedR, imgHeight);
            }
            imageReceiver.draw(canvas);
            if (isSpoiler() && !spoilerReveal.fullyRevealed()) {
                drawMediaSpoiler(canvas);
            } else if (radialProgress != null && buttonState != -1) {
                final int left = getImageLeft();
                radialProgress.setProgressRect(left + buttonX, buttonY, left + buttonX + buttonSize, buttonY + buttonSize);
                radialProgress.draw(canvas);
            }
            if (inQuote) {
                canvas.restore();
            }
        }

        private void updateRoundRadius(ImageReceiver imageReceiver, boolean none) {
            if (none) {
                imageReceiver.setRoundRadius(0);
                return;
            }
            int rad;
            if (SharedConfig.bubbleRadius > 2) {
                rad = dp(SharedConfig.bubbleRadius - 2);
            } else {
                rad = dp(SharedConfig.bubbleRadius);
            }
            final int nearRad = Math.min(dp(3), rad);
            imageReceiver.setRoundRadius(first && !root.hasNameOffset() && (root.isOut() || !root.isPinnedTop()) ? rad : nearRad, first && (!root.isOut() || !root.isPinnedTop()) ? rad : nearRad, nearRad, nearRad);
        }

        protected boolean isSpoiler() { return false; }

        private final SpoilerReveal spoilerReveal = new SpoilerReveal();

        private void startSpoilerReveal() {
            final float w = imageReceiver.getImageWidth();
            final float h = imageReceiver.getImageHeight();
            spoilerReveal.start(view, imageReceiver.getImageX() + w / 2f, imageReceiver.getImageY() + h / 2f, w, h);
        }

        private void drawMediaSpoiler(Canvas canvas) {
            if (spoilerReveal.fullyRevealed()) return;
            prepareBlurImage();
            final float x = imageReceiver.getImageX();
            final float y = imageReceiver.getImageY();
            final float w = imageReceiver.getImageWidth();
            final float h = imageReceiver.getImageHeight();
            if (w <= 0 || h <= 0) return;
            canvas.save();
            canvas.clipRect(x, y, x + w, y + h);
            spoilerReveal.clipOut(canvas);
            if (blurImageReceiver.getBitmap() != null) {
                updateRoundRadius(blurImageReceiver, false);
                blurImageReceiver.setImageCoords(x, y, w, h);
                blurImageReceiver.setAlpha(imageReceiver.getCurrentAlpha());
                blurImageReceiver.draw(canvas);
            }
            final SpoilerEffect2 effect = root.getMediaSpoilerEffect();
            if (effect != null) {
                canvas.translate(x, y);
                effect.draw(canvas, view, Math.round(w), Math.round(h), imageReceiver.getCurrentAlpha());
            }
            canvas.restore();
            if (view != null) view.invalidate();
        }

        @Override
        public int getHeight() {
            return padding.top + imgHeight + padding.bottom;
        }

        @Override
        public int getMinWidth() {
            return padding.left + imgWidth + padding.right;
        }

        @Override
        public int getLastLineWidth() {
            return getMinWidth();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            final int act = event.getActionMasked();
            final float x = event.getX() - padding.left - getImageLeft();
            final float y = event.getY() - padding.top;
            final boolean inside = x >= 0 && x <= imgWidth && y >= 0 && y <= imgHeight;
            final boolean onButton = buttonState != -1 && x >= buttonX && x <= buttonX + buttonSize && y >= buttonY && y <= buttonY + buttonSize;

            if (act == MotionEvent.ACTION_DOWN) {
                if (inside && (onButton || buttonState == 0 || buttonState == 2)) {
                    buttonPressed = true;
                    if (view != null) view.invalidate();
                    return true;
                }
                if (inside) {
                    photoPressed = true;
                    return true;
                }
                return false;
            }
            if (act == MotionEvent.ACTION_UP) {
                if (buttonPressed) {
                    buttonPressed = false;
                    if (view != null) {
                        view.playSoundEffect(SoundEffectConstants.CLICK);
                        view.invalidate();
                    }
                    didPressButton(true);
                    return true;
                }
                if (photoPressed) {
                    photoPressed = false;
                    if (inside) {
                        if (view != null) view.playSoundEffect(SoundEffectConstants.CLICK);
                        if (isSpoiler() && !spoilerReveal.isRevealing()) {
                            startSpoilerReveal();
                        } else if (root.delegate != null) {
                            root.delegate.openArticlePhoto(root.cell, getBlock());
                        }
                        return true;
                    }
                }
                return false;
            }
            if (act == MotionEvent.ACTION_CANCEL) {
                photoPressed = false;
                buttonPressed = false;
                return false;
            }
            return photoPressed || buttonPressed;
        }

        @Override
        protected int getBlockAccessibilityElementCount() {
            return 1;
        }

        @Override
        protected CharSequence getBlockAccessibilityElementText(int element) {
            final CharSequence type = LocaleController.getString(isRealVideo() ? R.string.AttachVideo : R.string.AttachPhoto);
            if (isSpoiler() && !spoilerReveal.fullyRevealed()) {
                return TextUtils.concat(type, ", ", LocaleController.getString(R.string.Spoiler));
            }
            return type;
        }

        @Override
        protected void getBlockAccessibilityElementBounds(int element, Rect out) {
            final int left = padding.left + getImageLeft();
            final int top = (int) currY + padding.top;
            out.set(left, top, left + imgWidth, top + imgHeight);
        }

        @Override
        protected boolean onBlockAccessibilityElementClick(int element, View host) {
            if (isSpoiler() && !spoilerReveal.isRevealing()) {
                startSpoilerReveal();
                return true;
            }
            if (root.delegate != null) {
                root.delegate.openArticlePhoto(root.cell, getBlock());
                return true;
            }
            return false;
        }

        private void didPressButton(boolean animated) {
            if (buttonState == 0) {
                mediaForced = true;
                if (radialProgress != null) radialProgress.setProgress(0, animated);
                applyImage(true);
                buttonState = 1;
                if (radialProgress != null) {
                    radialProgress.setIcon(MediaActionDrawable.ICON_CANCEL, true, animated);
                }
                if (view != null) view.invalidate();
            } else if (buttonState == 1) {
                mediaForced = false;
                imageReceiver.cancelLoadImage();
                buttonState = 0;
                if (radialProgress != null) {
                    radialProgress.setIcon(MediaActionDrawable.ICON_DOWNLOAD, false, animated);
                }
                if (view != null) view.invalidate();
            } else if (buttonState == 2) {
                mediaForced = true;
                imageReceiver.setAllowStartAnimation(true);
                applyImage(true);
                imageReceiver.startAnimation();
                buttonState = -1;
                if (radialProgress != null) {
                    radialProgress.setIcon(MediaActionDrawable.ICON_NONE, false, animated);
                }
                if (view != null) view.invalidate();
            } else if (buttonState == 3) {
                if (root.delegate != null) root.delegate.openArticlePhoto(root.cell, getBlock());
            }
        }

        public void updateButtonState(boolean animated) {
            ensureProgress();
            final String fileName = getFileName();
            if (TextUtils.isEmpty(fileName)) {
                buttonState = -1;
                if (radialProgress != null) {
                    radialProgress.setIcon(MediaActionDrawable.ICON_NONE, false, false);
                }
                return;
            }
            final AnimatedFileDrawable animation = imageReceiver.getAnimation();
            final boolean animationActive = animation != null && (animation.hasBitmap() || imageReceiver.isAnimationRunning());
            if (fileExists() || isAnimatedContent() && animationActive) {
                DownloadController.getInstance(root.currentAccount).removeLoadingFileObserver(this);
                if (isRealVideo() && !animationActive) {
                    buttonState = 3;
                    if (radialProgress != null) {
                        radialProgress.setIcon(MediaActionDrawable.ICON_PLAY, false, animated);
                    }
                } else if (isAnimatedContent() && !animationActive && !allowAutoplay() && !mediaForced) {
                    buttonState = 2;
                    if (radialProgress != null) {
                        radialProgress.setIcon(MediaActionDrawable.ICON_GIF, false, animated);
                    }
                } else {
                    buttonState = -1;
                    if (radialProgress != null) {
                        radialProgress.setIcon(MediaActionDrawable.ICON_NONE, false, animated);
                    }
                }
            } else {
                DownloadController.getInstance(root.currentAccount).addLoadingFileObserver(fileName, null, this);
                float setProgress = 0;
                if (autoDownload || mediaForced || FileLoader.getInstance(root.currentAccount).isLoadingFile(fileName)) {
                    buttonState = 1;
                    Float progress = ImageLoader.getInstance().getFileProgress(fileName);
                    setProgress = progress != null ? progress : 0;
                    if (radialProgress != null) {
                        radialProgress.setIcon(MediaActionDrawable.ICON_CANCEL, true, animated);
                    }
                } else if (isRealVideo()) {
                    buttonState = 3;
                    if (radialProgress != null) {
                        radialProgress.setIcon(MediaActionDrawable.ICON_PLAY, true, animated);
                    }
                } else {
                    buttonState = 0;
                    if (radialProgress != null) {
                        radialProgress.setIcon(MediaActionDrawable.ICON_DOWNLOAD, true, animated);
                    }
                }
                if (radialProgress != null) radialProgress.setProgress(setProgress, false);
            }
            if (view != null) view.invalidate();
        }

        private void ensureProgress() {
            if (radialProgress == null && view != null) {
                radialProgress = new RadialProgress2(view);
                radialProgress.setProgressColor(0xffffffff);
                radialProgress.setColors(0x66000000, 0x7f000000, 0xffffffff, 0xffd9d9d9);
                radialProgress.setProgressRect(buttonX, buttonY, buttonX + buttonSize, buttonY + buttonSize);
            } else if (radialProgress != null && view != null) {
                radialProgress.setParent(view);
                radialProgress.setProgressRect(buttonX, buttonY, buttonX + buttonSize, buttonY + buttonSize);
            }
        }

        @Override
        protected void onAttachedToWindow() {
            imageReceiver.setParentView(view);
            imageReceiver.onAttachedToWindow();
            blurImageReceiver.setParentView(view);
            blurImageReceiver.onAttachedToWindow();
            updateButtonState(false);
        }
        @Override
        protected void onDetachedFromWindow() {
            imageReceiver.onDetachedFromWindow();
            blurImageReceiver.onDetachedFromWindow();
            blurSource = null;
            DownloadController.getInstance(root.currentAccount).removeLoadingFileObserver(this);
        }

        @Override public int getObserverTag() { return observerTag; }
        @Override public void onFailedDownload(String fileName, boolean canceled) {
            updateButtonState(false);
        }
        @Override public void onSuccessDownload(String fileName) {
            if (radialProgress != null) radialProgress.setProgress(1, true);
            if (isAnimatedContent() && (allowAutoplay() || mediaForced)) {
                applyImage(true);
            }
            updateButtonState(true);
        }
        @Override public void onProgressUpload(String fileName, long uploadedSize, long totalSize, boolean isEncrypted) {}
        @Override public void onProgressDownload(String fileName, long downloadSize, long totalSize) {
            if (radialProgress != null) {
                radialProgress.setProgress(Math.min(1f, totalSize <= 0 ? 0 : downloadSize / (float) totalSize), true);
            }
            if (buttonState != 1) updateButtonState(true);
        }
    }

    public static class RichPhotoBlock extends RichMediaBlock {

        public final TL_iv.pageBlockPhoto block;
        public final TLRPC.Photo photo;
        public final TLRPC.PhotoSize sizeFull;
        public final TLRPC.PhotoSize strippedSize;

        public RichPhotoBlock(
            RichMessageLayout root,
            Rect padding, int maxWidth,
            TL_iv.pageBlockPhoto block,
            boolean first
        ) {
            super(root, padding, maxWidth, first);
            this.block = block;
            this.photo = root.getPhoto(block.photo_id);

            if (photo != null) {
                sizeFull = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, AndroidUtilities.getPhotoSize());
                strippedSize = FileLoader.getStrippedPhotoSize(photo.sizes);
            } else {
                sizeFull = null;
                strippedSize = null;
            }

            int w = sizeFull != null ? sizeFull.w : 100;
            int h = sizeFull != null ? sizeFull.h : 100;
            int width = this.maxWidth;
            float scale = width / (float) Math.max(1, w);
            int height = (int) (scale * h);
            final int maxH = (int) (Math.max(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.55f);
            if (height > maxH) {
                height = maxH;
                scale = height / (float) Math.max(1, h);
                width = (int) (scale * w);
            }
            imgWidth = width;
            imgHeight = height;
            finishLayout();
        }

        @Override
        protected void applyImage(boolean allowMedia) {
            if (photo == null || sizeFull == null) return;
            final ImageLocation thumbLoc = strippedSize != null ? ImageLocation.getForPhoto(strippedSize, photo) : null;
            if (allowMedia) {
                imageReceiver.setImage(
                    null, null,
                    ImageLocation.getForPhoto(sizeFull, photo), null,
                    thumbLoc, "b1",
                    null, sizeFull.size, null, root.messageObject, 1
                );
            } else {
                imageReceiver.setImage(
                    null, null,
                    null, null,
                    thumbLoc, "b1",
                    null, sizeFull.size, null, root.messageObject, 1
                );
            }
        }

        @Override
        protected String getFileName() {
            return FileLoader.getAttachFileName(sizeFull);
        }

        @Override
        protected boolean fileExists() {
            if (sizeFull == null) return true;
            final File p1 = FileLoader.getInstance(root.currentAccount).getPathToAttach(sizeFull, true);
            final File p2 = FileLoader.getInstance(root.currentAccount).getPathToAttach(sizeFull, false);
            return p1.exists() || (p2 != null && p2.exists());
        }

        @Override
        public TL_iv.PageBlock getBlock() {
            return block;
        }

        @Override
        protected boolean isSpoiler() {
            return block != null && block.spoiler;
        }
    }

    public static class RichVideoBlock extends RichMediaBlock {

        public final TL_iv.pageBlockVideo block;
        public final TLRPC.Document document;
        public final TLRPC.PhotoSize previewThumb;
        public final TLRPC.PhotoSize strippedThumb;
        public final boolean isVideo;
        public final boolean realVideo;

        public RichVideoBlock(
            RichMessageLayout root,
            Rect padding, int maxWidth,
            TL_iv.pageBlockVideo block,
            boolean first
        ) {
            super(root, padding, maxWidth, first);
            this.block = block;
            this.document = root.getDocument(block.video_id);
            this.realVideo = MessageObject.isVideoDocument(document);
            this.isVideo = realVideo || MessageObject.isGifDocument(document);
            if (document != null) {
                previewThumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 320, false, null, true);
                strippedThumb = FileLoader.getStrippedPhotoSize(document.thumbs);
            } else {
                previewThumb = null;
                strippedThumb = null;
            }

            int w = 100, h = 100;
            if (document != null) {
                for (int i = 0; i < document.attributes.size(); ++i) {
                    final TLRPC.DocumentAttribute attr = document.attributes.get(i);
                    if (attr instanceof TLRPC.TL_documentAttributeVideo) {
                        w = attr.w; h = attr.h;
                        break;
                    }
                }
                if (w <= 0 || h <= 0) {
                    w = previewThumb != null ? previewThumb.w : 100;
                    h = previewThumb != null ? previewThumb.h : 100;
                }
            }
            int width = this.maxWidth;
            float scale = width / (float) Math.max(1, w);
            int height = (int) (scale * h);
            final int maxH = (int) (Math.max(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.55f);
            if (height > maxH) {
                height = maxH;
                scale = height / (float) Math.max(1, h);
                width = (int) (scale * w);
            }
            imgWidth = width;
            imgHeight = height;
            finishLayout();
        }

        @Override
        protected boolean isRealVideo() {
            return realVideo;
        }

        @Override
        protected boolean isAnimatedContent() {
            return isVideo;
        }

        @Override
        protected boolean allowAutoplay() {
            return realVideo ? SharedConfig.isAutoplayVideo() : SharedConfig.isAutoplayGifs();
        }

        @Override
        protected boolean computeAutoDownload() {
            if (document == null) return false;
            return isVideo
                ? allowAutoplay() && DownloadController.getInstance(root.currentAccount).canDownloadMedia(DownloadController.AUTODOWNLOAD_TYPE_VIDEO, document.size)
                : true;
        }

        @Override
        protected void applyImage(boolean allowMedia) {
            if (document == null) return;
            final ImageLocation thumbLoc = strippedThumb != null ? ImageLocation.getForDocument(strippedThumb, document) : null;
            final ImageLocation imageLoc = previewThumb != null ? ImageLocation.getForDocument(previewThumb, document) : null;
            if (allowMedia && isVideo && (allowAutoplay() || mediaForced)) {
                imageReceiver.setAllowStartAnimation(true);
                imageReceiver.setAutoRepeat(1);
                imageReceiver.setImage(
                    ImageLocation.getForDocument(document), ImageLoader.AUTOPLAY_FILTER,
                    imageLoc, null,
                    thumbLoc, "b1",
                    null, document.size, "mp4", root.messageObject, 1
                );
            } else {
                imageReceiver.setImage(
                    null, null,
                    imageLoc, null,
                    thumbLoc, "b1",
                    null, document.size, "mp4", root.messageObject, 1
                );
            }
        }

        @Override
        protected String getFileName() {
            return FileLoader.getAttachFileName(document);
        }

        @Override
        protected boolean fileExists() {
            if (document == null) return true;
            final File p1 = FileLoader.getInstance(root.currentAccount).getPathToAttach(document);
            final File p2 = FileLoader.getInstance(root.currentAccount).getPathToAttach(document, true);
            return (p1 != null && p1.exists()) || (p2 != null && p2.exists());
        }

        @Override
        public TL_iv.PageBlock getBlock() {
            return block;
        }

        @Override
        protected boolean isSpoiler() {
            return block != null && block.spoiler;
        }
    }

    public static class RichMapBlock extends RichBlock {

        public final TL_iv.pageBlockMap block;
        public final ImageReceiver imageReceiver = new ImageReceiver();
        private final int imgWidth, imgHeight;
        private int currentMapProvider;
        private boolean photoPressed;

        private Drawable redPinIcon;
        private static Paint mapBgPaint;

        public RichMapBlock(
            RichMessageLayout root,
            Rect padding, int maxWidth,
            TL_iv.pageBlockMap block
        ) {
            super(root, padding, maxWidth);
            this.block = block;
            imageReceiver.setAllowLoadingOnAttachedOnly(true);

            int w = block.w > 0 ? block.w : 100;
            int h = block.h > 0 ? block.h : 100;
            int width = this.maxWidth;
            float scale = width / (float) Math.max(1, w);
            int height = (int) (scale * h);
            final int maxH = (int) (Math.max(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.55f);
            if (height > maxH) {
                height = maxH;
                scale = height / (float) Math.max(1, h);
                width = (int) (scale * w);
            }
            imgWidth = width;
            imgHeight = height;
            imageReceiver.setImageCoords(0, 0, imgWidth, imgHeight);
            applyImage();
        }

        private void applyImage() {
            if (block.geo == null) return;
            final int currentAccount = root.currentAccount;
            currentMapProvider = MessagesController.getInstance(currentAccount).mapProvider;
            final int wDp = (int) (imgWidth / AndroidUtilities.density);
            final int hDp = (int) (imgHeight / AndroidUtilities.density);
            final int zoom = block.zoom > 0 ? block.zoom : 15;
            if (currentMapProvider == 2) {
                final WebFile webFile = WebFile.createWithGeoPoint(block.geo, wDp, hDp, zoom, Math.min(2, (int) Math.ceil(AndroidUtilities.density)));
                if (webFile != null) {
                    imageReceiver.setImage(ImageLocation.getForWebFile(webFile), null, null, null, root.messageObject, 0);
                }
            } else {
                final String url = AndroidUtilities.formapMapUrl(currentAccount, block.geo.lat, block.geo._long, wDp, hDp, true, zoom, -1);
                if (url != null) {
                    imageReceiver.setImage(url, null, null, null, 0);
                }
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (mapBgPaint == null) {
                mapBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            }
            mapBgPaint.setColor(root.getThemedColor(Theme.key_chat_inLocationBackground));
            final boolean inQuote = isInQuote();
            final int bleedL = inQuote ? 0 : root.padLeft;
            final int bleedR = inQuote ? 0 : root.padRight;
            canvas.drawRect(-bleedL, 0, imgWidth + bleedR, imgHeight, mapBgPaint);

            final Drawable placeholder = Theme.chat_locationDrawable[root.isOut() ? 1 : 0];
            if (placeholder != null) {
                final int pw = placeholder.getIntrinsicWidth();
                final int ph = placeholder.getIntrinsicHeight();
                final int left = (imgWidth - pw) / 2;
                final int top = (imgHeight - ph) / 2;
                placeholder.setBounds(left, top, left + pw, top + ph);
                placeholder.draw(canvas);
            }

            imageReceiver.setImageCoords(-bleedL, 0, imgWidth + bleedL + bleedR, imgHeight);
            imageReceiver.draw(canvas);

            if (currentMapProvider == 2 && imageReceiver.hasNotThumb()) {
                if (redPinIcon == null && view != null) {
                    redPinIcon = ContextCompat.getDrawable(view.getContext(), R.drawable.map_pin).mutate();
                }
                if (redPinIcon != null) {
                    final int w = (int) (redPinIcon.getIntrinsicWidth() * 0.8f);
                    final int h = (int) (redPinIcon.getIntrinsicHeight() * 0.8f);
                    final int x = (imgWidth - w) / 2;
                    final int y = imgHeight / 2 - h;
                    redPinIcon.setAlpha((int) (255 * imageReceiver.getCurrentAlpha()));
                    redPinIcon.setBounds(x, y, x + w, y + h);
                    redPinIcon.draw(canvas);
                }
            }
        }

        @Override
        public int getHeight() {
            return padding.top + imgHeight + padding.bottom;
        }

        @Override
        public int getMinWidth() {
            return padding.left + imgWidth + padding.right;
        }

        @Override
        public int getLastLineWidth() {
            return getMinWidth();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            final int act = event.getActionMasked();
            final float x = event.getX() - padding.left;
            final float y = event.getY() - padding.top;
            final boolean inside = x >= 0 && x <= imgWidth && y >= 0 && y <= imgHeight;

            if (act == MotionEvent.ACTION_DOWN) {
                if (inside) {
                    photoPressed = true;
                    return true;
                }
                return false;
            }
            if (act == MotionEvent.ACTION_UP) {
                if (photoPressed) {
                    photoPressed = false;
                    if (inside && block.geo != null && view != null) {
                        view.playSoundEffect(SoundEffectConstants.CLICK);
                        try {
                            final double lat = block.geo.lat;
                            final double lon = block.geo._long;
                            view.getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("geo:" + lat + "," + lon + "?q=" + lat + "," + lon)));
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                        return true;
                    }
                }
                return false;
            }
            if (act == MotionEvent.ACTION_CANCEL) {
                photoPressed = false;
            }
            return photoPressed;
        }

        @Override
        protected void onAttachedToWindow() {
            imageReceiver.setParentView(view);
            imageReceiver.onAttachedToWindow();
        }

        @Override
        protected void onDetachedFromWindow() {
            imageReceiver.onDetachedFromWindow();
        }
    }

    public static class RichAudioBlock extends RichBlock
        implements DownloadController.FileDownloadProgressListener, NotificationCenter.NotificationCenterDelegate {

        public final TL_iv.pageBlockAudio block;
        private final MessageObject currentMessageObject;
        private final TLRPC.Document currentDocument;

        private final RadialProgress2 radialProgress;
        private final SeekBar seekBar;
        private final TextPaint audioTimePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

        private StaticLayout titleLayout;
        private StaticLayout durationLayout;
        private String lastTimeString;

        private final int buttonX = dp(16);
        private final int buttonY = dp(4 + 5);
        private final int size = dp(44);
        private int seekBarX;
        private int seekBarY;
        private int seekBarWidth;
        private int layoutWidth = -1;

        private int buttonState;
        private boolean buttonPressed;
        private final int observerTag;

        public RichAudioBlock(RichMessageLayout root, Rect padding, int maxWidth, TL_iv.pageBlockAudio block) {
            super(root, padding, maxWidth);
            this.block = block;
            this.currentMessageObject = root.audioBlocks.get(block);
            this.currentDocument = currentMessageObject != null ? currentMessageObject.getDocument() : null;
            this.observerTag = DownloadController.getInstance(root.currentAccount).generateObserverTag();

            radialProgress = new RadialProgress2(null);
            radialProgress.setCircleRadius(dp(24));
            radialProgress.setProgressRect(buttonX, buttonY, buttonX + size, buttonY + size);

            seekBar = new SeekBar(null);
            seekBar.setDelegate(progress -> {
                if (currentMessageObject == null) return;
                currentMessageObject.audioProgress = progress;
                MediaController.getInstance().seekToProgress(currentMessageObject, progress);
            });

            layoutInner();
            updateButtonState(false);
        }

        private void layoutInner() {
            layoutWidth = this.maxWidth + root.padLeft + root.padRight;
            seekBarX = buttonX + dp(50) + size;
            seekBarWidth = Math.max(0, layoutWidth - seekBarX - dp(18));

            String author = currentMessageObject != null ? currentMessageObject.getMusicAuthor(false) : null;
            String title = currentMessageObject != null ? currentMessageObject.getMusicTitle(false) : null;
            if (!TextUtils.isEmpty(title) || !TextUtils.isEmpty(author)) {
                SpannableStringBuilder stringBuilder;
                if (!TextUtils.isEmpty(title) && !TextUtils.isEmpty(author)) {
                    stringBuilder = new SpannableStringBuilder(String.format("%s - %s", author, title));
                } else if (!TextUtils.isEmpty(title)) {
                    stringBuilder = new SpannableStringBuilder(title);
                } else {
                    stringBuilder = new SpannableStringBuilder(author);
                }
                if (!TextUtils.isEmpty(author)) {
                    final TypefaceSpan span = new TypefaceSpan(AndroidUtilities.bold());
                    stringBuilder.setSpan(span, 0, author.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                }
                audioTimePaint.setTextSize(dp(16));
                final int titleWidth = seekBarWidth + dp(50);
                final CharSequence stringFinal = TextUtils.ellipsize(stringBuilder, Theme.chat_audioTitlePaint, titleWidth, TextUtils.TruncateAt.END);
                titleLayout = new StaticLayout(stringFinal, audioTimePaint, titleWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                seekBarY = buttonY + (size - dp(30)) / 2 + dp(11);
            } else {
                titleLayout = null;
                seekBarY = buttonY + (size - dp(30)) / 2;
            }
            seekBar.setSize(seekBarWidth, dp(30));
        }

        @Override
        public int getHeight() {
            return padding.top + dp(4 + 54 + 4) + padding.bottom;
        }

        @Override
        public int getLastLineWidth() {
            return getMinWidth();
        }

        @Override
        public boolean isHorizontallyDragging() {
            return seekBar.isDragging();
        }

        private int getIconForCurrentState() {
            if (buttonState == 1) return MediaActionDrawable.ICON_PAUSE;
            if (buttonState == 2) return MediaActionDrawable.ICON_DOWNLOAD;
            if (buttonState == 3) return MediaActionDrawable.ICON_CANCEL;
            return MediaActionDrawable.ICON_PLAY;
        }

        private boolean canStream() {
            return SharedConfig.streamMedia
                && currentMessageObject != null
                && currentMessageObject.isMusic()
                && !currentMessageObject.shouldEncryptPhotoOrVideo()
                && !DialogObject.isEncryptedDialog(currentMessageObject.getDialogId());
        }

        public void updatePlayingMessageProgress() {
            if (currentDocument == null || currentMessageObject == null) return;
            if (!seekBar.isDragging()) {
                seekBar.setProgress(currentMessageObject.audioProgress);
            }
            int duration = 0;
            if (MediaController.getInstance().isPlayingMessage(currentMessageObject)) {
                duration = currentMessageObject.audioProgressSec;
            } else {
                for (int a = 0; a < currentDocument.attributes.size(); a++) {
                    final TLRPC.DocumentAttribute attribute = currentDocument.attributes.get(a);
                    if (attribute instanceof TLRPC.TL_documentAttributeAudio) {
                        duration = (int) attribute.duration;
                        break;
                    }
                }
            }
            final String timeString = AndroidUtilities.formatShortDuration(duration);
            if (lastTimeString == null || !lastTimeString.equals(timeString)) {
                lastTimeString = timeString;
                audioTimePaint.setTextSize(dp(16));
                final int timeWidth = (int) Math.ceil(audioTimePaint.measureText(timeString));
                durationLayout = new StaticLayout(timeString, audioTimePaint, timeWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            }
            audioTimePaint.setColor(root.getThemedColor(root.isOut() ? Theme.key_chat_messageTextOut : Theme.key_chat_messageTextIn));
            if (view != null) view.invalidate();
        }

        public void updateButtonState(boolean animated) {
            final int currentAccount = root.currentAccount;
            final String fileName = FileLoader.getAttachFileName(currentDocument);
            final File path = currentDocument == null ? null : FileLoader.getInstance(currentAccount).getPathToAttach(currentDocument, true);
            final boolean fileExists = path != null && path.exists();
            if (TextUtils.isEmpty(fileName)) {
                radialProgress.setIcon(MediaActionDrawable.ICON_NONE, false, false);
                return;
            }
            if (fileExists) {
                DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
                final boolean playing = MediaController.getInstance().isPlayingMessage(currentMessageObject);
                if (!playing || MediaController.getInstance().isMessagePaused()) {
                    buttonState = 0;
                } else {
                    buttonState = 1;
                }
                radialProgress.setIcon(getIconForCurrentState(), false, animated);
            } else {
                DownloadController.getInstance(currentAccount).addLoadingFileObserver(fileName, null, this);
                if (canStream()) {
                    final boolean playing = MediaController.getInstance().isPlayingMessage(currentMessageObject);
                    buttonState = playing && !MediaController.getInstance().isMessagePaused() ? 1 : 0;
                    radialProgress.setIcon(getIconForCurrentState(), false, animated);
                } else if (!FileLoader.getInstance(currentAccount).isLoadingFile(fileName)) {
                    buttonState = 2;
                    radialProgress.setProgress(0, animated);
                    radialProgress.setIcon(getIconForCurrentState(), false, animated);
                } else {
                    buttonState = 3;
                    final Float progress = ImageLoader.getInstance().getFileProgress(fileName);
                    radialProgress.setProgress(progress != null ? progress : 0, animated);
                    radialProgress.setIcon(getIconForCurrentState(), true, animated);
                }
            }
            updatePlayingMessageProgress();
        }

        private void didPressedButton(boolean animated) {
            final int currentAccount = root.currentAccount;
            if (buttonState == 0) {
                if (MediaController.getInstance().setPlaylist(root.audioMessages, currentMessageObject, 0, false, null)) {
                    buttonState = 1;
                    radialProgress.setIcon(getIconForCurrentState(), false, animated);
                    if (view != null) view.invalidate();
                }
            } else if (buttonState == 1) {
                if (MediaController.getInstance().pauseMessage(currentMessageObject)) {
                    buttonState = 0;
                    radialProgress.setIcon(getIconForCurrentState(), false, animated);
                    if (view != null) view.invalidate();
                }
            } else if (buttonState == 2) {
                radialProgress.setProgress(0, false);
                FileLoader.getInstance(currentAccount).loadFile(currentDocument, root.messageObject, 1, 1);
                buttonState = 3;
                radialProgress.setIcon(getIconForCurrentState(), true, animated);
                if (view != null) view.invalidate();
            } else if (buttonState == 3) {
                FileLoader.getInstance(currentAccount).cancelLoadFile(currentDocument);
                buttonState = 2;
                radialProgress.setIcon(getIconForCurrentState(), false, animated);
                if (view != null) view.invalidate();
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (currentMessageObject == null || currentDocument == null) return;
            if (layoutWidth != this.maxWidth + root.padLeft + root.padRight) {
                layoutInner();
            }

            canvas.save();
            canvas.translate(-root.padLeft, 0);

            radialProgress.setColorKeys(
                root.isOut() ? Theme.key_chat_outLoader : Theme.key_chat_inLoader,
                root.isOut() ? Theme.key_chat_outLoaderSelected : Theme.key_chat_inLoaderSelected,
                root.isOut() ? Theme.key_chat_outMediaIcon : Theme.key_chat_inMediaIcon,
                root.isOut() ? Theme.key_chat_outMediaIconSelected : Theme.key_chat_inMediaIconSelected
            );
            radialProgress.setProgressColor(root.getThemedColor(root.isOut() ? Theme.key_chat_outFileProgress : Theme.key_chat_inFileProgress));
            radialProgress.draw(canvas);

            seekBar.setColors(
                root.getThemedColor(root.isOut() ? Theme.key_chat_outAudioSeekbar : Theme.key_chat_inAudioSeekbar),
                root.getThemedColor(root.isOut() ? Theme.key_chat_outAudioCacheSeekbar : Theme.key_chat_inAudioCacheSeekbar),
                root.getThemedColor(root.isOut() ? Theme.key_chat_outAudioSeekbarFill : Theme.key_chat_inAudioSeekbarFill),
                root.getThemedColor(root.isOut() ? Theme.key_chat_outAudioSeekbarFill : Theme.key_chat_inAudioSeekbarFill),
                root.getThemedColor(root.isOut() ? Theme.key_chat_outAudioSeekbarSelected : Theme.key_chat_inAudioSeekbarSelected)
            );

            canvas.save();
            canvas.translate(seekBarX, seekBarY);
            seekBar.draw(canvas);
            canvas.restore();

            if (durationLayout != null) {
                canvas.save();
                canvas.translate(buttonX + dp(54), seekBarY + dp(6));
                durationLayout.draw(canvas);
                canvas.restore();
            }
            if (titleLayout != null) {
                canvas.save();
                canvas.translate(buttonX + dp(54), seekBarY - dp(16));
                titleLayout.draw(canvas);
                canvas.restore();
            }

            canvas.restore();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            final int act = event.getActionMasked();
            final float x = event.getX() + root.padLeft;
            final float y = event.getY();

            final boolean seekHandled = seekBar.onTouch(act, x - seekBarX, y - seekBarY);
            if (seekHandled) {
                if (act == MotionEvent.ACTION_DOWN) requestDisallowParentIntercept(true);
                if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) requestDisallowParentIntercept(false);
                if (view != null) view.invalidate();
                return true;
            }

            if (act == MotionEvent.ACTION_DOWN) {
                if (buttonState != -1 && x >= buttonX && x <= buttonX + dp(48) && y >= buttonY && y <= buttonY + dp(48)) {
                    buttonPressed = true;
                    if (view != null) view.invalidate();
                    return true;
                }
            } else if (act == MotionEvent.ACTION_UP) {
                if (buttonPressed) {
                    buttonPressed = false;
                    if (view != null) view.playSoundEffect(SoundEffectConstants.CLICK);
                    didPressedButton(true);
                    if (view != null) view.invalidate();
                    return true;
                }
            } else if (act == MotionEvent.ACTION_CANCEL) {
                buttonPressed = false;
            }
            return buttonPressed;
        }

        @Override
        protected void onAttachedToWindow() {
            if (view != null) {
                radialProgress.setParent(view);
                seekBar.setParent(view);
            }
            updateButtonState(false);
            NotificationCenter.getInstance(root.currentAccount).addObserver(this, NotificationCenter.messagePlayingDidStart);
            NotificationCenter.getInstance(root.currentAccount).addObserver(this, NotificationCenter.messagePlayingDidReset);
            NotificationCenter.getInstance(root.currentAccount).addObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
            NotificationCenter.getInstance(root.currentAccount).addObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
        }

        @Override
        protected void onDetachedFromWindow() {
            DownloadController.getInstance(root.currentAccount).removeLoadingFileObserver(this);
            NotificationCenter.getInstance(root.currentAccount).removeObserver(this, NotificationCenter.messagePlayingDidStart);
            NotificationCenter.getInstance(root.currentAccount).removeObserver(this, NotificationCenter.messagePlayingDidReset);
            NotificationCenter.getInstance(root.currentAccount).removeObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
            NotificationCenter.getInstance(root.currentAccount).removeObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
        }

        @Override
        public void didReceivedNotification(int id, int account, Object... args) {
            if (currentMessageObject == null) return;
            if (id == NotificationCenter.messagePlayingDidStart) {
                updateButtonState(true);
            } else if (id == NotificationCenter.messagePlayingDidReset || id == NotificationCenter.messagePlayingPlayStateChanged) {
                updateButtonState(true);
            } else if (id == NotificationCenter.messagePlayingProgressDidChanged) {
                final Integer mid = (Integer) args[0];
                if (currentMessageObject.getId() == mid) {
                    final MessageObject player = MediaController.getInstance().getPlayingMessageObject();
                    if (player != null) {
                        currentMessageObject.audioProgress = player.audioProgress;
                        currentMessageObject.audioProgressSec = player.audioProgressSec;
                        currentMessageObject.audioPlayerDuration = player.audioPlayerDuration;
                        updatePlayingMessageProgress();
                    }
                }
            }
        }

        @Override public int getObserverTag() { return observerTag; }
        @Override public void onFailedDownload(String fileName, boolean canceled) { updateButtonState(true); }
        @Override public void onSuccessDownload(String fileName) {
            radialProgress.setProgress(1, true);
            updateButtonState(true);
        }
        @Override public void onProgressUpload(String fileName, long uploadedSize, long totalSize, boolean isEncrypted) {}
        @Override public void onProgressDownload(String fileName, long downloadSize, long totalSize) {
            radialProgress.setProgress(Math.min(1f, totalSize <= 0 ? 0 : downloadSize / (float) totalSize), true);
            if (buttonState != 3) updateButtonState(true);
        }
    }

    public static class RichDocumentBlock extends RichBlock implements DownloadController.FileDownloadProgressListener {
        public final TL_iv.pageBlockDocument block;
        private final TLRPC.Document document;
        private final RadialProgress2 radialProgress;
        private final ImageReceiver previewImage = new ImageReceiver();
        private final Paint previewBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint sizePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private StaticLayout titleLayout;
        private StaticLayout sizeLayout;
        private final int buttonX = dp(16);
        private final int previewX;
        private final int buttonY = dp(9);
        private final int buttonSize = dp(42);
        private final int buttonTextSpacing = dp(14);
        private final RectF optionsHit = new RectF();
        private final boolean hasPreview;
        private int buttonState;
        private boolean pressed;
        private boolean optionsPressed;
        private int layoutWidth = -1;
        private final int observerTag;

        private static final int MIN_WIDTH_DP = 220;

        public RichDocumentBlock(RichMessageLayout root, Rect padding, int maxWidth, TL_iv.pageBlockDocument block) {
            super(root, padding, maxWidth);
            this.block = block;
            this.document = root.getDocument(block.document_id);
            this.previewX = root.padLeft + dp(10);
            this.hasPreview = MessageObject.isDocumentHasThumb(document);
            observerTag = DownloadController.getInstance(root.currentAccount).generateObserverTag();
            radialProgress = new RadialProgress2(null);
            radialProgress.setCircleRadius(buttonSize / 2);
            final int progressX = hasPreview ? previewX + (dp(86) - buttonSize) / 2 : buttonX;
            final int progressY = hasPreview ? dp(10) + (dp(86) - buttonSize) / 2 : buttonY;
            radialProgress.setProgressRect(progressX, progressY, progressX + buttonSize, progressY + buttonSize);
            previewImage.setRoundRadius(dp(6));
            previewImage.setAllowLoadingOnAttachedOnly(true);
            if (hasPreview) {
                final TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 320, false, null, true);
                previewImage.setImageCoords(previewX, dp(10), dp(86), dp(86));
                previewImage.setImage(
                    thumb == null ? null : ImageLocation.getForDocument(thumb, document), "86_86",
                    ImageLoader.createStripedBitmap(document.thumbs), null,
                    root.messageObject, 1
                );
            }
            rebuildLayouts();
            updateButtonState(false);
        }

        private void rebuildLayouts() {
            layoutWidth = getLayoutWidth();
            final int textX = hasPreview ? previewX + dp(86 + 11) : buttonX + buttonSize + buttonTextSpacing;
            final int width = Math.max(dp(40), layoutWidth - textX - dp(48));
            titlePaint.setTextSize(dp(root.fontSize - 1));
            titlePaint.setTypeface(AndroidUtilities.bold());
            sizePaint.setTextSize(dp(root.fontSize - 3));
            final String name = document == null ? "" : FileLoader.getDocumentFileName(document);
            final CharSequence title = TextUtils.ellipsize(name, titlePaint, width, TextUtils.TruncateAt.END);
            titleLayout = new StaticLayout(title, titlePaint, width, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
            sizeLayout = new StaticLayout(document == null ? "" : AndroidUtilities.formatFileSize(document.size), sizePaint, width, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
        }

        @Override public int getHeight() { return padding.top + dp(hasPreview ? 106 : 60) + padding.bottom; }
        @Override public int getMinWidth() {
            final String name = document == null ? "" : FileLoader.getDocumentFileName(document);
            final String size = document == null ? "" : AndroidUtilities.formatFileSize(document.size);
            final int textWidth = (int) Math.ceil(Math.max(titlePaint.measureText(name), sizePaint.measureText(size)));
            final int textX = hasPreview ? dp(10 + 86 + 11) : buttonX + buttonSize + buttonTextSpacing;
            final int contentWidth = Math.min(maxWidth, Math.max(dp(MIN_WIDTH_DP), textX + textWidth + dp(48)));
            return padding.left + contentWidth + padding.right;
        }
        @Override public int getLastLineWidth() {
            final int contentRight;
            if (hasPreview) {
                contentRight = previewX - root.padLeft + dp(86);
            } else {
                final int sizeWidth = sizeLayout != null && sizeLayout.getLineCount() > 0
                    ? (int) Math.ceil(sizeLayout.getLineWidth(sizeLayout.getLineCount() - 1))
                    : 0;
                contentRight = buttonX - root.padLeft + buttonSize + buttonTextSpacing + sizeWidth;
            }
            return padding.left + contentRight + padding.right;
        }

        private int getLayoutWidth() {
            final int rootWidth = root.getMinWidth();
            final int contentWidth = rootWidth > 0
                ? Math.min(maxWidth, Math.max(0, rootWidth - padding.left - padding.right))
                : maxWidth;
            return contentWidth + root.padLeft + root.padRight;
        }

        private File path() {
            if (document == null) return null;
            final File stored = FileLoader.getInstance(root.currentAccount).getPathToAttach(document, false);
            if (stored != null && stored.exists()) return stored;
            return FileLoader.getInstance(root.currentAccount).getPathToAttach(document, true);
        }

        public void updateButtonState(boolean animated) {
            if (hasPreview) {
                radialProgress.setColorKeys(Theme.key_chat_mediaLoaderPhoto, Theme.key_chat_mediaLoaderPhotoSelected, Theme.key_chat_mediaLoaderPhotoIcon, Theme.key_chat_mediaLoaderPhotoIconSelected);
                radialProgress.setProgressColor(root.getThemedColor(Theme.key_chat_mediaProgress));
            } else {
                radialProgress.setColorKeys(
                        root.isOut() ? Theme.key_chat_outLoader : Theme.key_chat_inLoader,
                        root.isOut() ? Theme.key_chat_outLoaderSelected : Theme.key_chat_inLoaderSelected,
                        root.isOut() ? Theme.key_chat_outMediaIcon : Theme.key_chat_inMediaIcon,
                        root.isOut() ? Theme.key_chat_outMediaIconSelected : Theme.key_chat_inMediaIconSelected);
                radialProgress.setProgressColor(root.getThemedColor(root.isOut() ? Theme.key_chat_outFileProgress : Theme.key_chat_inFileProgress));
            }
            final String fileName = FileLoader.getAttachFileName(document);
            final File file = path();
            if (file != null && file.exists()) {
                DownloadController.getInstance(root.currentAccount).removeLoadingFileObserver(this);
                buttonState = 0;
                radialProgress.setIcon(hasPreview ? MediaActionDrawable.ICON_NONE : MediaActionDrawable.ICON_FILE, false, animated);
            } else if (!TextUtils.isEmpty(fileName)) {
                DownloadController.getInstance(root.currentAccount).addLoadingFileObserver(fileName, null, this);
                if (FileLoader.getInstance(root.currentAccount).isLoadingFile(fileName)) {
                    buttonState = 2;
                    final Float progress = ImageLoader.getInstance().getFileProgress(fileName);
                    radialProgress.setProgress(progress == null ? 0 : progress, animated);
                    radialProgress.setIcon(MediaActionDrawable.ICON_CANCEL, true, animated);
                } else {
                    buttonState = 1;
                    radialProgress.setProgress(0, animated);
                    radialProgress.setIcon(MediaActionDrawable.ICON_DOWNLOAD, false, animated);
                }
            } else {
                radialProgress.setIcon(MediaActionDrawable.ICON_NONE, false, animated);
            }
            if (view != null) view.invalidate();
        }

        private void press() {
            if (buttonState == 0) {
                final Activity activity = findActivity(view == null ? null : view.getContext());
                final File file = path();
                if (activity != null && file != null) AndroidUtilities.openForView(file, FileLoader.getDocumentFileName(document), document.mime_type, activity, root.resourcesProvider, false);
            } else if (buttonState == 1 && document != null) {
                FileLoader.getInstance(root.currentAccount).loadFile(document, root.messageObject, 1, 1);
                buttonState = 2;
                radialProgress.setIcon(MediaActionDrawable.ICON_CANCEL, true, true);
            } else if (buttonState == 2 && document != null) {
                FileLoader.getInstance(root.currentAccount).cancelLoadFile(document);
                buttonState = 1;
                radialProgress.setIcon(MediaActionDrawable.ICON_DOWNLOAD, false, true);
            }
            if (view != null) view.invalidate();
        }

        private static Activity findActivity(Context context) {
            while (context instanceof ContextWrapper) {
                if (context instanceof Activity) return (Activity) context;
                context = ((ContextWrapper) context).getBaseContext();
            }
            return context instanceof Activity ? (Activity) context : null;
        }

        @Override protected void onDraw(Canvas canvas) {
            if (document == null) return;
            if (layoutWidth != getLayoutWidth()) rebuildLayouts();
            canvas.save();
            canvas.translate(-root.padLeft, 0);
            if (hasPreview && !previewImage.draw(canvas)) {
                previewBackgroundPaint.setColor(root.getThemedColor(root.isOut() ? Theme.key_chat_outFileBackground : Theme.key_chat_inFileBackground));
                canvas.drawRoundRect(previewX, dp(10), previewX + dp(86), dp(96), dp(6), dp(6), previewBackgroundPaint);
            }
            radialProgress.draw(canvas);
            titlePaint.setColor(root.getThemedColor(root.isOut() ? Theme.key_chat_outFileNameText : Theme.key_chat_inFileNameText));
            sizePaint.setColor(root.getThemedColor(root.isOut() ? Theme.key_chat_outTimeText : Theme.key_chat_inTimeText));
            final int textX = hasPreview ? previewX + dp(86 + 11) : buttonX + buttonSize + buttonTextSpacing;
            final int titleY = dp(11);
            final int sizeY = titleY + titleLayout.getHeight() + dp(2);
            canvas.save(); canvas.translate(textX, titleY); titleLayout.draw(canvas); canvas.restore();
            canvas.save(); canvas.translate(textX, sizeY); sizeLayout.draw(canvas); canvas.restore();
            if (canShowOptions()) {
                final Drawable menuDrawable = root.isOut()
                    ? root.getThemedDrawable(Theme.key_drawable_msgOutMenu)
                    : Theme.chat_msgInMenuDrawable;
                final int menuX = getMenuX();
                final int menuY = dp(7);
                menuDrawable.setBounds(menuX, menuY, menuX + menuDrawable.getIntrinsicWidth(), menuY + menuDrawable.getIntrinsicHeight());
                menuDrawable.draw(canvas);
                optionsHit.set(menuX - dp(8), 0, menuX + menuDrawable.getIntrinsicWidth() + dp(8), dp(54));
            } else {
                optionsHit.setEmpty();
            }
            canvas.restore();
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            final float x = event.getX() + root.padLeft;
            final float y = event.getY();
            final boolean insideOptions = optionsHit.contains(x, y);
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN && insideOptions) {
                optionsPressed = true;
                return true;
            }
            if (optionsPressed) {
                if (event.getActionMasked() == MotionEvent.ACTION_MOVE && !insideOptions) {
                    optionsPressed = false;
                    return true;
                }
                if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    final boolean openOptions = event.getActionMasked() == MotionEvent.ACTION_UP && insideOptions;
                    optionsPressed = false;
                    if (openOptions && document != null && canShowOptions()) {
                        if (view != null) view.playSoundEffect(SoundEffectConstants.CLICK);
                        final float anchorX = root.cell.getTextX() + padding.left - root.padLeft + getMenuX();
                        final float anchorY = root.cell.getTextY() + currY + padding.top + dp(7);
                        root.delegate.didPressRichDocumentOptions(root.cell, document, anchorX, anchorY);
                    }
                    return true;
                }
            }
            final boolean inside = x >= (hasPreview ? previewX : buttonX) && x <= layoutWidth - dp(12) && y >= dp(10) && y <= dp(hasPreview ? 96 : 53);
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN && inside) { pressed = true; return true; }
            if (event.getActionMasked() == MotionEvent.ACTION_UP && pressed) {
                pressed = false;
                if (inside) { if (view != null) view.playSoundEffect(SoundEffectConstants.CLICK); press(); }
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) pressed = false;
            return pressed;
        }

        private boolean canShowOptions() {
            return document != null && root.cell != null && root.delegate != null && root.delegate.canSaveRichDocument(root.cell);
        }

        private int getMenuX() {
            return layoutWidth + padding.right - dp(32);
        }

        @Override protected void onAttachedToWindow() { if (view != null) { radialProgress.setParent(view); previewImage.setParentView(view); } previewImage.onAttachedToWindow(); updateButtonState(false); }
        @Override protected void onDetachedFromWindow() { previewImage.onDetachedFromWindow(); DownloadController.getInstance(root.currentAccount).removeLoadingFileObserver(this); }
        @Override public int getObserverTag() { return observerTag; }
        @Override public void onFailedDownload(String fileName, boolean canceled) { updateButtonState(true); }
        @Override public void onSuccessDownload(String fileName) { radialProgress.setProgress(1, true); updateButtonState(true); }
        @Override public void onProgressUpload(String fileName, long uploadedSize, long totalSize, boolean isEncrypted) {}
        @Override public void onProgressDownload(String fileName, long downloadSize, long totalSize) {
            radialProgress.setProgress(totalSize <= 0 ? 0 : Math.min(1f, downloadSize / (float) totalSize), true);
            if (buttonState != 2) updateButtonState(true);
        }
    }

    public static class MediaCell implements DownloadController.FileDownloadProgressListener {

        public final RichMessageLayout root;
        public final TL_iv.PageBlock pageBlock;
        public final ImageReceiver imageReceiver = new ImageReceiver();
        public final ImageReceiver blurImageReceiver = new ImageReceiver();
        private Bitmap blurSource;
        private static ColorMatrixColorFilter fancyBlurFilter;
        public RadialProgress2 radialProgress;

        public final TLRPC.Photo photo;
        public final TLRPC.PhotoSize sizeFull;
        public final TLRPC.PhotoSize strippedSize;

        public final TLRPC.Document document;
        public final TLRPC.PhotoSize previewThumb;
        public final TLRPC.PhotoSize strippedThumb;
        public final boolean isVideo;
        public final boolean realVideo;

        public final float aspectRatio;

        public int x, y, w, h;
        public boolean autoDownload;
        private int buttonState = -1;
        private int buttonX, buttonY;
        private final int buttonSize = dp(48);
        private final int observerTag;
        private boolean buttonPressed;
        private boolean photoPressed;
        private boolean mediaForced;
        private View parentView;

        public static MediaCell forPageBlock(RichMessageLayout root, TL_iv.PageBlock pageBlock) {
            if (pageBlock instanceof TL_iv.pageBlockPhoto) {
                return new MediaCell(root, (TL_iv.pageBlockPhoto) pageBlock);
            } else if (pageBlock instanceof TL_iv.pageBlockVideo) {
                return new MediaCell(root, (TL_iv.pageBlockVideo) pageBlock);
            }
            return null;
        }

        private MediaCell(RichMessageLayout root, TL_iv.pageBlockPhoto pb) {
            this.root = root;
            this.pageBlock = pb;
            this.photo = root.getPhoto(pb.photo_id);
            if (photo != null) {
                sizeFull = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, AndroidUtilities.getPhotoSize());
                strippedSize = FileLoader.getStrippedPhotoSize(photo.sizes);
            } else {
                sizeFull = null;
                strippedSize = null;
            }
            document = null;
            previewThumb = null;
            strippedThumb = null;
            isVideo = false;
            realVideo = false;
            aspectRatio = sizeFull != null && sizeFull.h > 0 ? sizeFull.w / (float) sizeFull.h : 1f;
            observerTag = DownloadController.getInstance(root.currentAccount).generateObserverTag();
            imageReceiver.setAllowLoadingOnAttachedOnly(true);
            blurImageReceiver.setAllowLoadingOnAttachedOnly(true);
        }

        private MediaCell(RichMessageLayout root, TL_iv.pageBlockVideo pb) {
            this.root = root;
            this.pageBlock = pb;
            this.photo = null;
            this.sizeFull = null;
            this.strippedSize = null;
            this.document = root.getDocument(pb.video_id);
            this.realVideo = MessageObject.isVideoDocument(document);
            this.isVideo = realVideo || MessageObject.isGifDocument(document);
            if (document != null) {
                previewThumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 320, false, null, true);
                strippedThumb = FileLoader.getStrippedPhotoSize(document.thumbs);
            } else {
                previewThumb = null;
                strippedThumb = null;
            }
            float ar = 1f;
            if (document != null) {
                for (int i = 0; i < document.attributes.size(); ++i) {
                    final TLRPC.DocumentAttribute attr = document.attributes.get(i);
                    if (attr instanceof TLRPC.TL_documentAttributeVideo && attr.h > 0) {
                        ar = attr.w / (float) attr.h;
                        break;
                    }
                }
            }
            this.aspectRatio = ar;
            observerTag = DownloadController.getInstance(root.currentAccount).generateObserverTag();
            imageReceiver.setAllowLoadingOnAttachedOnly(true);
            blurImageReceiver.setAllowLoadingOnAttachedOnly(true);
            imageReceiver.setDelegate(new ImageReceiver.ImageReceiverDelegate() {
                @Override
                public void didSetImage(ImageReceiver imageReceiver, boolean set, boolean thumb, boolean memCache) {}
                @Override
                public void onAnimationReady(ImageReceiver imageReceiver) {
                    updateButtonState(parentView, true);
                }
            });
        }

        public void setRect(int x, int y, int w, int h) {
            this.x = x; this.y = y; this.w = w; this.h = h;
            imageReceiver.setImageCoords(x, y, w, h);
            buttonX = x + (w - buttonSize) / 2;
            buttonY = y + (h - buttonSize) / 2;
            if (radialProgress != null) {
                radialProgress.setProgressRect(buttonX, buttonY, buttonX + buttonSize, buttonY + buttonSize);
            }
            autoDownload = computeAutoDownload();
            applyImage(autoDownload || fileExists());
        }

        private boolean allowAutoplay() {
            return realVideo ? SharedConfig.isAutoplayVideo() : SharedConfig.isAutoplayGifs();
        }

        private boolean computeAutoDownload() {
            if (document != null) {
                return isVideo
                    ? allowAutoplay() && DownloadController.getInstance(root.currentAccount).canDownloadMedia(DownloadController.AUTODOWNLOAD_TYPE_VIDEO, document.size)
                    : true;
            }
            return (DownloadController.getInstance(root.currentAccount).getCurrentDownloadMask() & DownloadController.AUTODOWNLOAD_TYPE_PHOTO) != 0;
        }

        public boolean fileExists() {
            if (sizeFull != null) {
                final File p1 = FileLoader.getInstance(root.currentAccount).getPathToAttach(sizeFull, true);
                final File p2 = FileLoader.getInstance(root.currentAccount).getPathToAttach(sizeFull, false);
                return p1.exists() || (p2 != null && p2.exists());
            }
            if (document != null) {
                final File p1 = FileLoader.getInstance(root.currentAccount).getPathToAttach(document);
                final File p2 = FileLoader.getInstance(root.currentAccount).getPathToAttach(document, true);
                return (p1 != null && p1.exists()) || (p2 != null && p2.exists());
            }
            return true;
        }

        public String getFileName() {
            if (sizeFull != null) return FileLoader.getAttachFileName(sizeFull);
            if (document != null) return FileLoader.getAttachFileName(document);
            return null;
        }

        private void applyImage(boolean allowMedia) {
            if (photo != null && sizeFull != null) {
                final ImageLocation thumbLoc = strippedSize != null ? ImageLocation.getForPhoto(strippedSize, photo) : null;
                if (allowMedia) {
                    imageReceiver.setImage(
                        null, null,
                        ImageLocation.getForPhoto(sizeFull, photo), null,
                        thumbLoc, "b1",
                        null, sizeFull.size, null, root.messageObject, 1
                    );
                } else {
                    imageReceiver.setImage(
                        null, null,
                        null, null,
                        thumbLoc, "b1",
                        null, sizeFull.size, null, root.messageObject, 1
                    );
                }
            } else if (document != null) {
                final ImageLocation thumbLoc = strippedThumb != null ? ImageLocation.getForDocument(strippedThumb, document) : null;
                final ImageLocation imageLoc = previewThumb != null ? ImageLocation.getForDocument(previewThumb, document) : null;
                if (allowMedia && isVideo && (allowAutoplay() || mediaForced)) {
                    imageReceiver.setAllowStartAnimation(true);
                    imageReceiver.setAutoRepeat(1);
                    imageReceiver.setImage(
                        ImageLocation.getForDocument(document), ImageLoader.AUTOPLAY_FILTER,
                        imageLoc, null,
                        thumbLoc, "b1",
                        null, document.size, "mp4", root.messageObject, 1
                    );
                } else {
                    imageReceiver.setImage(
                        null, null,
                        imageLoc, null,
                        thumbLoc, "b1",
                        null, document.size, "mp4", root.messageObject, 1
                    );
                }
            }
        }

        public void ensureProgress(View view) {
            if (radialProgress == null && view != null) {
                radialProgress = new RadialProgress2(view);
                radialProgress.setProgressColor(0xffffffff);
                radialProgress.setColors(0x66000000, 0x7f000000, 0xffffffff, 0xffd9d9d9);
                radialProgress.setProgressRect(buttonX, buttonY, buttonX + buttonSize, buttonY + buttonSize);
            } else if (radialProgress != null && view != null) {
                radialProgress.setParent(view);
                radialProgress.setProgressRect(buttonX, buttonY, buttonX + buttonSize, buttonY + buttonSize);
            }
        }

        public void attach(View view) {
            parentView = view;
            imageReceiver.setParentView(view);
            imageReceiver.onAttachedToWindow();
            blurImageReceiver.setParentView(view);
            blurImageReceiver.onAttachedToWindow();
            ensureProgress(view);
            updateButtonState(view, false);
        }

        public void detach() {
            imageReceiver.onDetachedFromWindow();
            blurImageReceiver.onDetachedFromWindow();
            blurSource = null;
            DownloadController.getInstance(root.currentAccount).removeLoadingFileObserver(this);
        }

        private boolean isSpoiler() {
            if (pageBlock instanceof TL_iv.pageBlockPhoto) return ((TL_iv.pageBlockPhoto) pageBlock).spoiler;
            if (pageBlock instanceof TL_iv.pageBlockVideo) return ((TL_iv.pageBlockVideo) pageBlock).spoiler;
            return false;
        }

        private void prepareBlurImage() {
            if (blurImageReceiver.getBitmap() != null && imageReceiver.getAnimation() != null) return;
            final Bitmap bitmap = imageReceiver.getBitmap();
            if (bitmap == null || bitmap.isRecycled()) return;
            if (bitmap == blurSource && blurImageReceiver.getBitmap() != null) return;
            blurSource = bitmap;
            blurImageReceiver.setImageBitmap(Utilities.stackBlurBitmapMax(bitmap, false));
            if (fancyBlurFilter == null) {
                final ColorMatrix colorMatrix = new ColorMatrix();
                AndroidUtilities.multiplyBrightnessColorMatrix(colorMatrix, .9f);
                AndroidUtilities.adjustSaturationColorMatrix(colorMatrix, +.6f);
                fancyBlurFilter = new ColorMatrixColorFilter(colorMatrix);
            }
            blurImageReceiver.setColorFilter(fancyBlurFilter);
        }

        public void updateButtonState(View view, boolean animated) {
            if (view == null) view = parentView;
            ensureProgress(view);
            final String fileName = getFileName();
            if (TextUtils.isEmpty(fileName)) {
                buttonState = -1;
                if (radialProgress != null) radialProgress.setIcon(MediaActionDrawable.ICON_NONE, false, false);
                return;
            }
            final AnimatedFileDrawable animation = imageReceiver.getAnimation();
            final boolean animationActive = animation != null && (animation.hasBitmap() || imageReceiver.isAnimationRunning());
            if (fileExists() || isVideo && animationActive) {
                DownloadController.getInstance(root.currentAccount).removeLoadingFileObserver(this);
                if (realVideo && !animationActive) {
                    buttonState = 3;
                    if (radialProgress != null) radialProgress.setIcon(MediaActionDrawable.ICON_PLAY, false, animated);
                } else if (isVideo && !animationActive && !allowAutoplay() && !mediaForced) {
                    buttonState = 2;
                    if (radialProgress != null) radialProgress.setIcon(MediaActionDrawable.ICON_GIF, false, animated);
                } else {
                    buttonState = -1;
                    if (radialProgress != null) radialProgress.setIcon(MediaActionDrawable.ICON_NONE, false, animated);
                }
            } else {
                DownloadController.getInstance(root.currentAccount).addLoadingFileObserver(fileName, null, this);
                float setProgress = 0;
                if (autoDownload || mediaForced || FileLoader.getInstance(root.currentAccount).isLoadingFile(fileName)) {
                    buttonState = 1;
                    Float progress = ImageLoader.getInstance().getFileProgress(fileName);
                    setProgress = progress != null ? progress : 0;
                    if (radialProgress != null) radialProgress.setIcon(MediaActionDrawable.ICON_CANCEL, true, animated);
                } else if (realVideo) {
                    buttonState = 3;
                    if (radialProgress != null) radialProgress.setIcon(MediaActionDrawable.ICON_PLAY, true, animated);
                } else {
                    buttonState = 0;
                    if (radialProgress != null) radialProgress.setIcon(MediaActionDrawable.ICON_DOWNLOAD, true, animated);
                }
                if (radialProgress != null) radialProgress.setProgress(setProgress, false);
            }
            if (view != null) view.invalidate();
        }

        private void didPressButton(View view, boolean animated) {
            if (buttonState == 0) {
                mediaForced = true;
                if (radialProgress != null) radialProgress.setProgress(0, animated);
                applyImage(true);
                buttonState = 1;
                if (radialProgress != null) radialProgress.setIcon(MediaActionDrawable.ICON_CANCEL, true, animated);
                if (view != null) view.invalidate();
            } else if (buttonState == 1) {
                mediaForced = false;
                imageReceiver.cancelLoadImage();
                buttonState = 0;
                if (radialProgress != null) radialProgress.setIcon(MediaActionDrawable.ICON_DOWNLOAD, false, animated);
                if (view != null) view.invalidate();
            } else if (buttonState == 2) {
                mediaForced = true;
                imageReceiver.setAllowStartAnimation(true);
                applyImage(true);
                imageReceiver.startAnimation();
                buttonState = -1;
                if (radialProgress != null) radialProgress.setIcon(MediaActionDrawable.ICON_NONE, false, animated);
                if (view != null) view.invalidate();
            } else if (buttonState == 3) {
                if (root.delegate != null) root.delegate.openArticlePhoto(root.cell, pageBlock);
            }
        }

        public boolean isInside(float lx, float ly) {
            return lx >= x && lx <= x + w && ly >= y && ly <= y + h;
        }

        private boolean isOnButton(float lx, float ly) {
            return buttonState != -1 && lx >= buttonX && lx <= buttonX + buttonSize && ly >= buttonY && ly <= buttonY + buttonSize;
        }

        public boolean onTouchEvent(MotionEvent event, View view) {
            final int act = event.getActionMasked();
            final float lx = event.getX();
            final float ly = event.getY();
            final boolean inside = isInside(lx, ly);
            final boolean onButton = isOnButton(lx, ly);
            if (act == MotionEvent.ACTION_DOWN) {
                if (inside && (onButton || buttonState == 0 || buttonState == 2)) {
                    buttonPressed = true;
                    if (view != null) view.invalidate();
                    return true;
                }
                if (inside) { photoPressed = true; return true; }
                return false;
            }
            if (act == MotionEvent.ACTION_UP) {
                if (buttonPressed) {
                    buttonPressed = false;
                    if (view != null) { view.playSoundEffect(SoundEffectConstants.CLICK); view.invalidate(); }
                    didPressButton(view, true);
                    return true;
                }
                if (photoPressed) {
                    photoPressed = false;
                    if (inside) {
                        if (view != null) view.playSoundEffect(SoundEffectConstants.CLICK);
                        if (isSpoiler() && !spoilerReveal.isRevealing()) {
                            final float w = imageReceiver.getImageWidth();
                            final float h = imageReceiver.getImageHeight();
                            spoilerReveal.start(view, imageReceiver.getImageX() + w / 2f, imageReceiver.getImageY() + h / 2f, w, h);
                        } else if (root.delegate != null) {
                            root.delegate.openArticlePhoto(root.cell, pageBlock);
                        }
                        return true;
                    }
                }
                return false;
            }
            if (act == MotionEvent.ACTION_CANCEL) {
                photoPressed = false; buttonPressed = false; return false;
            }
            return photoPressed || buttonPressed;
        }

        public CharSequence getAccessibilityText() {
            final CharSequence type = LocaleController.getString(isVideo ? R.string.AttachVideo : R.string.AttachPhoto);
            if (isSpoiler() && !spoilerReveal.fullyRevealed()) {
                return TextUtils.concat(type, ", ", LocaleController.getString(R.string.Spoiler));
            }
            return type;
        }

        public boolean onAccessibilityClick(View view) {
            if (isSpoiler() && !spoilerReveal.isRevealing()) {
                final float w = imageReceiver.getImageWidth();
                final float h = imageReceiver.getImageHeight();
                spoilerReveal.start(view, imageReceiver.getImageX() + w / 2f, imageReceiver.getImageY() + h / 2f, w, h);
                return true;
            }
            if (root.delegate != null) {
                root.delegate.openArticlePhoto(root.cell, pageBlock);
                return true;
            }
            return false;
        }

        private final SpoilerReveal spoilerReveal = new SpoilerReveal();

        public void draw(Canvas canvas) {
            imageReceiver.draw(canvas);
            if (isSpoiler() && !spoilerReveal.fullyRevealed()) {
                drawSpoiler(canvas);
                return;
            }
            if (radialProgress != null && buttonState != -1) radialProgress.draw(canvas);
        }

        private void drawSpoiler(Canvas canvas) {
            prepareBlurImage();
            final float x = imageReceiver.getImageX();
            final float y = imageReceiver.getImageY();
            final float w = imageReceiver.getImageWidth();
            final float h = imageReceiver.getImageHeight();
            if (w <= 0 || h <= 0) return;
            canvas.save();
            canvas.clipRect(x, y, x + w, y + h);
            spoilerReveal.clipOut(canvas);
            if (blurImageReceiver.getBitmap() != null) {
                blurImageReceiver.setImageCoords(x, y, w, h);
                blurImageReceiver.setAlpha(imageReceiver.getCurrentAlpha());
                blurImageReceiver.draw(canvas);
            }
            final SpoilerEffect2 effect = root.getMediaSpoilerEffect();
            if (effect != null) {
                canvas.translate(x, y);
                effect.draw(canvas, parentView, Math.round(w), Math.round(h), imageReceiver.getCurrentAlpha());
            }
            canvas.restore();
            if (parentView != null) parentView.invalidate();
        }

        @Override public int getObserverTag() { return observerTag; }
        @Override public void onFailedDownload(String fileName, boolean canceled) {
            updateButtonState(parentView, false);
        }
        @Override public void onSuccessDownload(String fileName) {
            if (radialProgress != null) radialProgress.setProgress(1, true);
            if (isVideo && (allowAutoplay() || mediaForced)) {
                applyImage(true);
            }
            updateButtonState(parentView, true);
        }
        @Override public void onProgressUpload(String fileName, long uploadedSize, long totalSize, boolean isEncrypted) {}
        @Override public void onProgressDownload(String fileName, long downloadSize, long totalSize) {
            if (radialProgress != null) {
                radialProgress.setProgress(Math.min(1f, totalSize <= 0 ? 0 : downloadSize / (float) totalSize), true);
            }
            if (buttonState != 1) updateButtonState(parentView, true);
        }
    }

    public static class RichCollageBlock extends RichBlock {

        public final TL_iv.pageBlockCollage block;
        public final boolean first;
        public final ArrayList<MediaCell> cells = new ArrayList<>();
        private int[] cellFlags;
        private int contentHeight;
        private MediaCell pressedCell;
        private static Paint mediaBgPaint;

        public RichCollageBlock(RichMessageLayout root, Rect padding, int maxWidth, TL_iv.pageBlockCollage block, boolean first) {
            super(root, padding, maxWidth);
            this.block = block;
            this.first = first;
            for (int i = 0; i < block.items.size(); ++i) {
                final MediaCell cell = MediaCell.forPageBlock(root, block.items.get(i));
                if (cell != null) cells.add(cell);
            }
            layoutCells();
        }

        private void layoutCells() {
            cellFlags = new int[cells.size()];
            if (cells.isEmpty()) { contentHeight = 0; return; }
            if (cells.size() == 1) {
                final MediaCell c = cells.get(0);
                final float ar = c.aspectRatio <= 0 ? 1f : c.aspectRatio;
                int w = this.maxWidth;
                int h = (int) (w / ar);
                final int maxH = (int) (Math.max(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.55f);
                if (h > maxH) { h = maxH; w = (int) (h * ar); }
                c.setRect(0, 0, w, h);
                cellFlags[0] = MessageObject.POSITION_FLAG_TOP | MessageObject.POSITION_FLAG_BOTTOM | MessageObject.POSITION_FLAG_LEFT | MessageObject.POSITION_FLAG_RIGHT;
                contentHeight = h;
                return;
            }
            final float[] ratios = new float[cells.size()];
            for (int i = 0; i < cells.size(); ++i) ratios[i] = cells.get(i).aspectRatio;
            final MessageObject.GroupedMessagePosition[] positions = computeGrouped(ratios);

            final int maxSizeWidth = 1000;
            int maxRow = 0;
            for (MessageObject.GroupedMessagePosition p : positions) maxRow = Math.max(maxRow, p.maxY);

            final float[] rowH = new float[maxRow + 1];
            for (MessageObject.GroupedMessagePosition p : positions) {
                if (p.minY == p.maxY) rowH[p.minY] = Math.max(rowH[p.minY], p.ph);
            }
            for (MessageObject.GroupedMessagePosition p : positions) {
                if (p.minY != p.maxY) {
                    final int span = p.maxY - p.minY + 1;
                    if (p.siblingHeights != null && p.siblingHeights.length == span) {
                        for (int r = 0; r < span; ++r) {
                            rowH[p.minY + r] = Math.max(rowH[p.minY + r], p.siblingHeights[r]);
                        }
                    } else {
                        final float per = p.ph / span;
                        for (int r = p.minY; r <= p.maxY; ++r) rowH[r] = Math.max(rowH[r], per);
                    }
                }
            }

            final float pixelMaxHeight = Math.max(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.5f;
            final int[] rowYPx = new int[maxRow + 2];
            float acc = 0f;
            for (int r = 0; r <= maxRow; ++r) {
                rowYPx[r] = Math.round(acc * pixelMaxHeight);
                acc += rowH[r];
            }
            rowYPx[maxRow + 1] = Math.round(acc * pixelMaxHeight);

            final int gap = dp(2);
            for (int i = 0; i < positions.length; ++i) {
                final MessageObject.GroupedMessagePosition p = positions[i];
                int yPx = rowYPx[p.minY];
                int hPx = rowYPx[p.maxY + 1] - yPx;

                int xPx;
                if (p.leftSpanOffset > 0) {
                    xPx = Math.round(p.leftSpanOffset * this.maxWidth / (float) maxSizeWidth);
                } else {
                    int leftUnits = 0;
                    for (int j = 0; j < positions.length; ++j) {
                        if (j == i) continue;
                        final MessageObject.GroupedMessagePosition q = positions[j];
                        if (q.minY <= p.minY && q.maxY >= p.minY && q.minX < p.minX) {
                            leftUnits += q.pw;
                        }
                    }
                    xPx = Math.round(leftUnits * this.maxWidth / (float) maxSizeWidth);
                }

                int wPx;
                if ((p.flags & MessageObject.POSITION_FLAG_RIGHT) != 0) {
                    wPx = this.maxWidth - xPx;
                } else {
                    wPx = Math.round(p.pw * this.maxWidth / (float) maxSizeWidth);
                    wPx -= gap;
                }
                if ((p.flags & MessageObject.POSITION_FLAG_BOTTOM) == 0) {
                    hPx -= gap;
                }

                cells.get(i).setRect(xPx, yPx, Math.max(0, wPx), Math.max(0, hPx));
                cellFlags[i] = p.flags;
            }
            contentHeight = rowYPx[maxRow + 1];
        }

        private void updateRoundRadius(ImageReceiver imageReceiver, int flags, boolean inQuote) {
            final boolean top = (flags & MessageObject.POSITION_FLAG_TOP) != 0;
            final boolean bottom = (flags & MessageObject.POSITION_FLAG_BOTTOM) != 0;
            final boolean left = (flags & MessageObject.POSITION_FLAG_LEFT) != 0;
            final boolean right = (flags & MessageObject.POSITION_FLAG_RIGHT) != 0;
            if (inQuote) {
                final int rad = dp(8);
                imageReceiver.setRoundRadius(top && left ? rad : 0, top && right ? rad : 0, bottom && right ? rad : 0, bottom && left ? rad : 0);
                return;
            }
            int rad;
            if (SharedConfig.bubbleRadius > 2) {
                rad = dp(SharedConfig.bubbleRadius - 2);
            } else {
                rad = dp(SharedConfig.bubbleRadius);
            }
            final int nearRad = Math.min(dp(3), rad);
            final int tl = top && left ? (first && !root.hasNameOffset() && (root.isOut() || !root.isPinnedTop()) ? rad : nearRad) : 0;
            final int tr = top && right ? (first && !root.hasNameOffset() && (!root.isOut() || !root.isPinnedTop()) ? rad : nearRad) : 0;
            final int br = bottom && right ? nearRad : 0;
            final int bl = bottom && left ? nearRad : 0;
            imageReceiver.setRoundRadius(tl, tr, br, bl);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (mediaBgPaint == null) {
                mediaBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                mediaBgPaint.setColor(0x0f000000);
            }
            final boolean inQuote = isInQuote();
            final int pad = dp(2);
            final int padL = inQuote ? 0 : root.padLeft - pad;
            final int padR = inQuote ? 0 : root.padRight - pad;
            final float k = (this.maxWidth > 0 && (padL > 0 || padR > 0))
                ? (this.maxWidth + padL + padR) / (float) this.maxWidth
                : 1f;
            for (int i = 0; i < cells.size(); ++i) {
                final MediaCell c = cells.get(i);
                final int vx = Math.round(c.x * k) - padL;
                final int vw = Math.round(c.w * k);
                updateRoundRadius(c.imageReceiver, cellFlags != null && i < cellFlags.length ? cellFlags[i] : 0, inQuote);
                c.imageReceiver.setImageCoords(vx, c.y, vw, c.h);
                if (!c.imageReceiver.hasBitmapImage() || c.imageReceiver.getCurrentAlpha() != 1.0f) {
                    canvas.drawRect(vx, c.y, vx + vw, c.y + c.h, mediaBgPaint);
                }
                c.draw(canvas);
            }
        }

        @Override public int getHeight() { return contentHeight; }
        @Override public int getMinWidth() { return padding.left + this.maxWidth + padding.right; }
        @Override public int getLastLineWidth() { return getMinWidth(); }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            final int act = event.getActionMasked();
            event.offsetLocation(-padding.left, -padding.top);
            try {
                if (act == MotionEvent.ACTION_DOWN) {
                    pressedCell = null;
                    for (int i = 0; i < cells.size(); ++i) {
                        final MediaCell c = cells.get(i);
                        if (c.isInside(event.getX(), event.getY())) {
                            if (c.onTouchEvent(event, view)) { pressedCell = c; return true; }
                        }
                    }
                    return false;
                }
                if (pressedCell == null) return false;
                final boolean handled = pressedCell.onTouchEvent(event, view);
                if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) pressedCell = null;
                return handled;
            } finally {
                event.offsetLocation(padding.left, padding.top);
            }
        }

        @Override
        protected int getBlockAccessibilityElementCount() {
            return cells.size();
        }

        @Override
        protected CharSequence getBlockAccessibilityElementText(int element) {
            if (element < 0 || element >= cells.size()) return null;
            return cells.get(element).getAccessibilityText();
        }

        @Override
        protected void getBlockAccessibilityElementBounds(int element, Rect out) {
            if (element < 0 || element >= cells.size()) return;
            final MediaCell cell = cells.get(element);
            final int left = padding.left + cell.x;
            final int top = (int) currY + padding.top + cell.y;
            out.set(left, top, left + cell.w, top + cell.h);
        }

        @Override
        protected boolean onBlockAccessibilityElementClick(int element, View host) {
            if (element < 0 || element >= cells.size()) return false;
            return cells.get(element).onAccessibilityClick(host);
        }

        @Override protected void onAttachedToWindow() { for (MediaCell c : cells) c.attach(view); }
        @Override protected void onDetachedFromWindow() { for (MediaCell c : cells) c.detach(); }
    }

    public static class RichSlideshowBlock extends RichBlock {

        public final TL_iv.pageBlockSlideshow block;
        public final boolean first;
        public final ArrayList<MediaCell> cells = new ArrayList<>();

        private int slideWidth, slideHeight;
        private int dotsHeight;
        private int currentPage;
        private float pageOffset;
        private boolean dragging, verticalDragging;
        private float downX, downY;
        private int touchSlop, minFlingVelocity, maxFlingVelocity;
        private VelocityTracker velocityTracker;
        private ValueAnimator settleAnimator;

        private static Paint slideDotPaint;
        private static Paint mediaBgPaint;

        public RichSlideshowBlock(RichMessageLayout root, Rect padding, int maxWidth, TL_iv.pageBlockSlideshow block, boolean first) {
            super(root, padding, maxWidth);
            this.block = block;
            this.first = first;
            for (int i = 0; i < block.items.size(); ++i) {
                final MediaCell cell = MediaCell.forPageBlock(root, block.items.get(i));
                if (cell != null) cells.add(cell);
            }
            layoutCells();
        }

        private void layoutCells() {
            if (cells.isEmpty()) { slideWidth = slideHeight = 0; return; }
            slideWidth = this.maxWidth;
            float avg = 0;
            for (MediaCell c : cells) avg += c.aspectRatio <= 0 ? 1f : c.aspectRatio;
            avg /= cells.size();
            int h = (int) (slideWidth / Math.max(0.5f, avg));
            final int maxH = (int) (Math.max(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.55f);
            if (h > maxH) h = maxH;
            slideHeight = h;
            dotsHeight = 0;
            for (MediaCell c : cells) c.setRect(0, 0, slideWidth, slideHeight);
        }

        private final Path clipPath = new Path();

        @Override
        protected void onDraw(Canvas canvas) {
            if (cells.isEmpty()) return;
            if (mediaBgPaint == null) {
                mediaBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                mediaBgPaint.setColor(0x0f000000);
            }
            final boolean inQuote = isInQuote();
            final int pad = dp(2);
            final int padL = inQuote ? 0 : root.padLeft - pad;
            final int padR = inQuote ? 0 : root.padRight - pad;
            final int slideWidth = this.slideWidth + padL + padR;
            int clipTl = 0, clipTr = 0, clipBr = 0, clipBl = 0;
            canvas.save();
            if (inQuote) {
                clipTl = clipTr = clipBr = clipBl = dp(8);
                clipPath.rewind();
                clipPath.addRoundRect(0, 0, this.slideWidth, slideHeight, clipTl, clipTl, Path.Direction.CW);
                canvas.clipPath(clipPath);
            } else if (first) {
                int rad;
                if (SharedConfig.bubbleRadius > 2) {
                    rad = dp(SharedConfig.bubbleRadius - 2);
                } else {
                    rad = dp(SharedConfig.bubbleRadius);
                }
                final int nearRad = Math.min(dp(3), rad);
                clipTl = (root.isOut() || !root.isPinnedTop()) && !root.hasNameOffset() ? rad : nearRad;
                clipTr = (!root.isOut() || !root.isPinnedTop()) && !root.hasNameOffset() ? rad : nearRad;
                clipBr = clipBl = nearRad;
                final float[] radii = { clipTl, clipTl, clipTr, clipTr, clipBr, clipBr, clipBl, clipBl };
                clipPath.rewind();
                clipPath.addRoundRect(-padL, 0, this.slideWidth + padR, slideHeight, radii, Path.Direction.CW);
                canvas.clipPath(clipPath);
            } else {
                canvas.clipRect(-padL, 0, root.getMinWidth() + padR, slideHeight);
            }
            if (currentPage == 0 && pageOffset < 0 || currentPage == cells.size() - 1 && pageOffset > 0) {
                canvas.drawColor(Theme.multAlpha(root.getThemedColor(root.isOut() ? Theme.key_chat_outReplyNameText : Theme.key_chat_inReplyNameText), 0.2f));
            }
            final float dx = -pageOffset * slideWidth;
            for (int i = currentPage - 1; i <= currentPage + 1; ++i) {
                if (i < 0 || i >= cells.size()) continue;
                final MediaCell c = cells.get(i);
                canvas.save();
                canvas.translate((i - currentPage) * slideWidth + dx, 0);
                c.imageReceiver.setRoundRadius(
                    i == 0 ? clipTl : 0,
                    i == cells.size() - 1 ? clipTr : 0,
                    i == cells.size() - 1 ? clipBr : 0,
                    i == 0 ? clipBl : 0
                );
                c.blurImageReceiver.setRoundRadius(
                    i == 0 ? clipTl : 0,
                    i == cells.size() - 1 ? clipTr : 0,
                    i == cells.size() - 1 ? clipBr : 0,
                    i == 0 ? clipBl : 0
                );
                c.imageReceiver.setImageCoords(-padL, 0, slideWidth, slideHeight);
                if (!c.imageReceiver.hasBitmapImage() || c.imageReceiver.getCurrentAlpha() != 1.0f) {
                    canvas.drawRect(-padL, 0, slideWidth + padR, slideHeight, mediaBgPaint);
                }
                c.draw(canvas);
                canvas.restore();
            }
            canvas.restore();

            final int n = cells.size();
            if (n > 1) {
                if (slideDotPaint == null) {
                    slideDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    slideDotPaint.setColor(0xFFFFFFFF);
                    slideDotPaint.setShadowLayer(dpf2(3), 0, dpf2(1), 0x80000000);
                }
                final float dotsY = slideHeight - dp(7 + 16) + dp(5);
                final int totalWidth = n * dp(7) + (n - 1) * dp(6) + dp(4);
                final float selectedPage = currentPage + pageOffset;
                float xOffset;
                if (totalWidth < slideWidth) {
                    xOffset = (slideWidth - totalWidth) / 2f;
                } else {
                    xOffset = dp(4);
                    final int size = dp(13);
                    final int halfCount = (slideWidth - dp(8)) / 2 / size;
                    final float maxShift = Math.max(0, n - halfCount * 2 - 1);
                    xOffset -= Utilities.clamp(selectedPage - halfCount, maxShift, 0) * size;
                }
                canvas.save();
                canvas.clipRect(0, slideHeight - dp(7 + 16), slideWidth, slideHeight);
                for (int a = 0; a < n; ++a) {
                    final float selection = Math.max(0, 1f - Math.abs(a - selectedPage));
                    final float radius = dp(2) + dp(1) * selection;
                    slideDotPaint.setAlpha((int) (0xA0 + (0xFF - 0xA0) * selection));
                    final float cx = xOffset + dp(4) + dp(13) * a;
                    canvas.drawCircle(cx, dotsY, radius, slideDotPaint);
                }
                canvas.restore();
            }
        }

        @Override public int getHeight() { return slideHeight + dotsHeight; }
        @Override public int getMinWidth() { return padding.left + this.maxWidth + padding.right; }
        @Override public int getLastLineWidth() { return getMinWidth(); }

        @Override
        protected int getBlockAccessibilityElementCount() {
            return cells.isEmpty() ? 0 : 1;
        }

        @Override
        protected CharSequence getBlockAccessibilityElementText(int element) {
            if (cells.isEmpty()) return null;
            final int page = Math.max(0, Math.min(currentPage, cells.size() - 1));
            return TextUtils.concat(cells.get(page).getAccessibilityText(), ", ", LocaleController.formatString(R.string.Of, page + 1, cells.size()));
        }

        @Override
        protected void getBlockAccessibilityElementBounds(int element, Rect out) {
            final int left = padding.left;
            final int top = (int) currY + padding.top;
            out.set(left, top, left + slideWidth, top + slideHeight);
        }

        @Override
        protected boolean onBlockAccessibilityElementClick(int element, View host) {
            if (cells.isEmpty()) return false;
            final int page = Math.max(0, Math.min(currentPage, cells.size() - 1));
            return cells.get(page).onAccessibilityClick(host);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            final int act = event.getActionMasked();
            event.offsetLocation(-padding.left, -padding.top);
            try {
                if (act == MotionEvent.ACTION_DOWN) {
                    if (touchSlop == 0 && view != null) {
                        final ViewConfiguration configuration = ViewConfiguration.get(view.getContext());
                        touchSlop = configuration.getScaledTouchSlop();
                        minFlingVelocity = configuration.getScaledMinimumFlingVelocity();
                        maxFlingVelocity = configuration.getScaledMaximumFlingVelocity();
                    }
                    downX = event.getX(); downY = event.getY();
                    dragging = false;
                    verticalDragging = false;
                    if (velocityTracker == null) velocityTracker = VelocityTracker.obtain();
                    else velocityTracker.clear();
                    velocityTracker.addMovement(event);
                    requestDisallowParentIntercept(true);
                    if (settleAnimator != null) { settleAnimator.cancel(); settleAnimator = null; }
                    if (currentPage >= 0 && currentPage < cells.size()) {
                        cells.get(currentPage).onTouchEvent(event, view);
                    }
                    return true;
                }
                if (act == MotionEvent.ACTION_MOVE) {
                    if (verticalDragging) return true;
                    if (velocityTracker != null) velocityTracker.addMovement(event);
                    final float ddx = event.getX() - downX;
                    final float ddy = event.getY() - downY;
                    if (!dragging && Math.abs(ddy) > touchSlop && Math.abs(ddy) > Math.abs(ddx)) {
                        verticalDragging = true;
                        if (currentPage >= 0 && currentPage < cells.size()) {
                            final MotionEvent cancel = MotionEvent.obtain(event);
                            cancel.setAction(MotionEvent.ACTION_CANCEL);
                            cells.get(currentPage).onTouchEvent(cancel, view);
                            cancel.recycle();
                        }
                        requestDisallowParentIntercept(false);
                        return true;
                    }
                    if (!dragging && Math.abs(ddx) > touchSlop && Math.abs(ddx) > Math.abs(ddy)) {
                        dragging = true;
                        if (currentPage >= 0 && currentPage < cells.size()) {
                            final MotionEvent cancel = MotionEvent.obtain(event);
                            cancel.setAction(MotionEvent.ACTION_CANCEL);
                            cells.get(currentPage).onTouchEvent(cancel, view);
                            cancel.recycle();
                        }
                    }
                    if (dragging) {
                        float off = -ddx / (float) slideWidth;
                        if (currentPage == 0 && off < 0) off *= 0.3f;
                        if (currentPage == cells.size() - 1 && off > 0) off *= 0.3f;
                        pageOffset = off;
                        if (view != null) view.invalidate();
                        return true;
                    }
                    if (currentPage >= 0 && currentPage < cells.size()) return cells.get(currentPage).onTouchEvent(event, view);
                    return false;
                }
                if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
                    final boolean wasVerticalDragging = verticalDragging;
                    verticalDragging = false;
                    float velocityX = 0;
                    if (!wasVerticalDragging && act == MotionEvent.ACTION_UP && velocityTracker != null) {
                        velocityTracker.addMovement(event);
                        velocityTracker.computeCurrentVelocity(1000, maxFlingVelocity);
                        final float x = velocityTracker.getXVelocity();
                        final float y = velocityTracker.getYVelocity();
                        if (Math.abs(x) >= minFlingVelocity && Math.abs(x) > Math.abs(y)) {
                            velocityX = x;
                        }
                    }
                    if (velocityTracker != null) {
                        velocityTracker.recycle();
                        velocityTracker = null;
                    }
                    requestDisallowParentIntercept(false);
                    if (wasVerticalDragging) return true;
                    if (dragging) {
                        dragging = false;
                        settle(velocityX);
                        return true;
                    }
                    if (currentPage >= 0 && currentPage < cells.size()) return cells.get(currentPage).onTouchEvent(event, view);
                    return false;
                }
                return false;
            } finally {
                event.offsetLocation(padding.left, padding.top);
            }
        }

        private void settle(float velocityX) {
            int targetDelta = 0;
            if (velocityX < 0 && currentPage < cells.size() - 1) targetDelta = 1;
            else if (velocityX > 0 && currentPage > 0) targetDelta = -1;
            else if (pageOffset > 0.5f && currentPage < cells.size() - 1) targetDelta = 1;
            else if (pageOffset < -0.5f && currentPage > 0) targetDelta = -1;
            final int target = currentPage + targetDelta;
            final float from = pageOffset;
            final float to = (target - currentPage);
            settleAnimator = ValueAnimator.ofFloat(from, to);
            settleAnimator.setDuration(420);
            settleAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            settleAnimator.addUpdateListener(a -> {
                pageOffset = (float) a.getAnimatedValue();
                if (view != null) view.invalidate();
            });
            settleAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(android.animation.Animator animation) {
                    currentPage = target;
                    pageOffset = 0;
                    if (view != null) view.invalidate();
                }
            });
            settleAnimator.start();
        }

        public int getCurrentPage() { return currentPage; }

        public void setCurrentPage(int page) {
            final int newPage = Math.max(0, Math.min(page, cells.size() - 1));
            if (settleAnimator != null) {
                settleAnimator.cancel();
                settleAnimator = null;
            }
            if (currentPage == newPage && pageOffset == 0) return;
            currentPage = newPage;
            pageOffset = 0;
            dragging = false;
            verticalDragging = false;
            if (view != null) view.invalidate();
        }

        @Override
        public boolean isHorizontallyDragging() {
            return dragging || (settleAnimator != null && settleAnimator.isRunning());
        }

        @Override protected void onAttachedToWindow() { for (MediaCell c : cells) c.attach(view); }
        @Override protected void onDetachedFromWindow() {
            requestDisallowParentIntercept(false);
            dragging = false;
            verticalDragging = false;
            if (velocityTracker != null) {
                velocityTracker.recycle();
                velocityTracker = null;
            }
            for (MediaCell c : cells) c.detach();
        }
    }

    public static MessageObject.GroupedMessagePosition[] computeGrouped(float[] ratios) {
        final int count = ratios.length;
        final MessageObject.GroupedMessagePosition[] arr = new MessageObject.GroupedMessagePosition[count];
        if (count == 0) return arr;

        int maxSizeWidth = 1000;
        final float maxSizeHeight = 814.0f;

        final StringBuilder proportions = new StringBuilder();
        float averageAspectRatio = 0f;
        boolean forceCalc = false;
        for (int i = 0; i < count; ++i) {
            float ar = ratios[i] <= 0 ? 1f : ratios[i];
            arr[i] = new MessageObject.GroupedMessagePosition();
            arr[i].aspectRatio = ar;
            if (ar > 1.2f) proportions.append("w");
            else if (ar < 0.8f) proportions.append("n");
            else proportions.append("q");
            averageAspectRatio += ar;
            if (ar > 2.0f) forceCalc = true;
        }
        averageAspectRatio /= count;

        final int minHeight = dp(120);
        final int minWidth = (int) (dp(120) / (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) / (float) maxSizeWidth));
        final int paddingsWidth = (int) (dp(40) / (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) / (float) maxSizeWidth));
        final float maxAspectRatio = maxSizeWidth / maxSizeHeight;
        final float minH = dp(100) / maxSizeHeight;

        if (count == 1) {
            arr[0].set(0, 0, 0, 0, maxSizeWidth,
                Math.round(Math.min(maxSizeWidth / arr[0].aspectRatio, maxSizeHeight / 2.0f)) / maxSizeHeight,
                MessageObject.POSITION_FLAG_LEFT | MessageObject.POSITION_FLAG_RIGHT | MessageObject.POSITION_FLAG_TOP | MessageObject.POSITION_FLAG_BOTTOM);
            return arr;
        }

        if (!forceCalc && (count == 2 || count == 3 || count == 4)) {
            if (count == 2) {
                final MessageObject.GroupedMessagePosition p1 = arr[0], p2 = arr[1];
                final String s = proportions.toString();
                if (s.equals("ww") && averageAspectRatio > 1.4f * maxAspectRatio && p1.aspectRatio - p2.aspectRatio < 0.2f) {
                    float height = Math.round(Math.min(maxSizeWidth / p1.aspectRatio, Math.min(maxSizeWidth / p2.aspectRatio, maxSizeHeight / 2.0f))) / maxSizeHeight;
                    p1.set(0, 0, 0, 0, maxSizeWidth, height, MessageObject.POSITION_FLAG_LEFT | MessageObject.POSITION_FLAG_RIGHT | MessageObject.POSITION_FLAG_TOP);
                    p2.set(0, 0, 1, 1, maxSizeWidth, height, MessageObject.POSITION_FLAG_LEFT | MessageObject.POSITION_FLAG_RIGHT | MessageObject.POSITION_FLAG_BOTTOM);
                } else if (s.equals("ww") || s.equals("qq")) {
                    int width = maxSizeWidth / 2;
                    float height = Math.round(Math.min(width / p1.aspectRatio, Math.min(width / p2.aspectRatio, maxSizeHeight))) / maxSizeHeight;
                    p1.set(0, 0, 0, 0, width, height, MessageObject.POSITION_FLAG_LEFT | MessageObject.POSITION_FLAG_BOTTOM | MessageObject.POSITION_FLAG_TOP);
                    p2.set(1, 1, 0, 0, width, height, MessageObject.POSITION_FLAG_RIGHT | MessageObject.POSITION_FLAG_BOTTOM | MessageObject.POSITION_FLAG_TOP);
                } else {
                    int secondWidth = (int) Math.max(0.4f * maxSizeWidth, Math.round((maxSizeWidth / p1.aspectRatio / (1.0f / p1.aspectRatio + 1.0f / p2.aspectRatio))));
                    int firstWidth = maxSizeWidth - secondWidth;
                    if (firstWidth < minWidth) { int diff = minWidth - firstWidth; firstWidth = minWidth; secondWidth -= diff; }
                    float height = Math.min(maxSizeHeight, Math.round(Math.min(firstWidth / p1.aspectRatio, secondWidth / p2.aspectRatio))) / maxSizeHeight;
                    p1.set(0, 0, 0, 0, firstWidth, height, MessageObject.POSITION_FLAG_LEFT | MessageObject.POSITION_FLAG_BOTTOM | MessageObject.POSITION_FLAG_TOP);
                    p2.set(1, 1, 0, 0, secondWidth, height, MessageObject.POSITION_FLAG_RIGHT | MessageObject.POSITION_FLAG_BOTTOM | MessageObject.POSITION_FLAG_TOP);
                }
            } else if (count == 3) {
                final MessageObject.GroupedMessagePosition p1 = arr[0], p2 = arr[1], p3 = arr[2];
                if (proportions.charAt(0) == 'n') {
                    float thirdHeight = Math.min(maxSizeHeight * 0.5f, Math.round(p2.aspectRatio * maxSizeWidth / (p3.aspectRatio + p2.aspectRatio)));
                    float secondHeight = maxSizeHeight - thirdHeight;
                    int rightWidth = (int) Math.max(minWidth, Math.min(maxSizeWidth * 0.5f, Math.round(Math.min(thirdHeight * p3.aspectRatio, secondHeight * p2.aspectRatio))));
                    int leftWidth = Math.round(Math.min(maxSizeHeight * p1.aspectRatio + paddingsWidth, maxSizeWidth - rightWidth));
                    p1.set(0, 0, 0, 1, leftWidth, 1.0f, MessageObject.POSITION_FLAG_LEFT | MessageObject.POSITION_FLAG_BOTTOM | MessageObject.POSITION_FLAG_TOP);
                    p2.set(1, 1, 0, 0, rightWidth, secondHeight / maxSizeHeight, MessageObject.POSITION_FLAG_RIGHT | MessageObject.POSITION_FLAG_TOP);
                    p3.set(1, 1, 1, 1, rightWidth, thirdHeight / maxSizeHeight, MessageObject.POSITION_FLAG_RIGHT | MessageObject.POSITION_FLAG_BOTTOM);
                } else {
                    float firstHeight = Math.round(Math.min(maxSizeWidth / p1.aspectRatio, maxSizeHeight * 0.66f)) / maxSizeHeight;
                    p1.set(0, 1, 0, 0, maxSizeWidth, firstHeight, MessageObject.POSITION_FLAG_LEFT | MessageObject.POSITION_FLAG_RIGHT | MessageObject.POSITION_FLAG_TOP);
                    int width = maxSizeWidth / 2;
                    float secondHeight = Math.min(maxSizeHeight - firstHeight, Math.round(Math.min(width / p2.aspectRatio, width / p3.aspectRatio))) / maxSizeHeight;
                    if (secondHeight < minH) secondHeight = minH;
                    p2.set(0, 0, 1, 1, width, secondHeight, MessageObject.POSITION_FLAG_LEFT | MessageObject.POSITION_FLAG_BOTTOM);
                    p3.set(1, 1, 1, 1, width, secondHeight, MessageObject.POSITION_FLAG_RIGHT | MessageObject.POSITION_FLAG_BOTTOM);
                }
            } else {
                final MessageObject.GroupedMessagePosition p1 = arr[0], p2 = arr[1], p3 = arr[2], p4 = arr[3];
                if (proportions.charAt(0) == 'w') {
                    float h0 = Math.round(Math.min(maxSizeWidth / p1.aspectRatio, maxSizeHeight * 0.66f)) / maxSizeHeight;
                    p1.set(0, 2, 0, 0, maxSizeWidth, h0, MessageObject.POSITION_FLAG_LEFT | MessageObject.POSITION_FLAG_RIGHT | MessageObject.POSITION_FLAG_TOP);
                    float h = Math.round(maxSizeWidth / (p2.aspectRatio + p3.aspectRatio + p4.aspectRatio));
                    int w0 = (int) Math.max(minWidth, Math.min(maxSizeWidth * 0.4f, h * p2.aspectRatio));
                    int w2 = (int) Math.max(Math.max(minWidth, maxSizeWidth * 0.33f), h * p4.aspectRatio);
                    int w1 = maxSizeWidth - w0 - w2;
                    if (w1 < dp(58)) { int diff = dp(58) - w1; w1 = dp(58); w0 -= diff / 2; w2 -= (diff - diff / 2); }
                    h = Math.min(maxSizeHeight - h0, h);
                    h /= maxSizeHeight;
                    if (h < minH) h = minH;
                    p2.set(0, 0, 1, 1, w0, h, MessageObject.POSITION_FLAG_LEFT | MessageObject.POSITION_FLAG_BOTTOM);
                    p3.set(1, 1, 1, 1, w1, h, MessageObject.POSITION_FLAG_BOTTOM);
                    p4.set(2, 2, 1, 1, w2, h, MessageObject.POSITION_FLAG_RIGHT | MessageObject.POSITION_FLAG_BOTTOM);
                } else {
                    int w = Math.max(minWidth, Math.round(maxSizeHeight / (1.0f / p2.aspectRatio + 1.0f / p3.aspectRatio + 1.0f / p4.aspectRatio)));
                    float h0 = Math.min(0.33f, Math.max(minHeight, w / p2.aspectRatio) / maxSizeHeight);
                    float h1 = Math.min(0.33f, Math.max(minHeight, w / p3.aspectRatio) / maxSizeHeight);
                    float h2 = 1.0f - h0 - h1;
                    int w0 = Math.round(Math.min(maxSizeHeight * p1.aspectRatio + paddingsWidth, maxSizeWidth - w));
                    p1.set(0, 0, 0, 2, w0, h0 + h1 + h2, MessageObject.POSITION_FLAG_LEFT | MessageObject.POSITION_FLAG_TOP | MessageObject.POSITION_FLAG_BOTTOM);
                    p2.set(1, 1, 0, 0, w, h0, MessageObject.POSITION_FLAG_RIGHT | MessageObject.POSITION_FLAG_TOP);
                    p3.set(1, 1, 1, 1, w, h1, MessageObject.POSITION_FLAG_RIGHT);
                    p4.set(1, 1, 2, 2, w, h2, MessageObject.POSITION_FLAG_RIGHT | MessageObject.POSITION_FLAG_BOTTOM);
                }
            }
            return arr;
        }

        float[] croppedRatios = new float[count];
        for (int a = 0; a < count; ++a) {
            float ar = arr[a].aspectRatio;
            if (averageAspectRatio > 1.1f) croppedRatios[a] = Math.max(1.0f, ar);
            else croppedRatios[a] = Math.min(1.0f, ar);
            croppedRatios[a] = Math.max(0.66667f, Math.min(1.7f, croppedRatios[a]));
        }

        final ArrayList<int[]> attemptCounts = new ArrayList<>();
        final ArrayList<float[]> attemptHeights = new ArrayList<>();
        for (int firstLine = 1; firstLine < count; ++firstLine) {
            int secondLine = count - firstLine;
            if (firstLine > 3 || secondLine > 3) continue;
            attemptCounts.add(new int[]{firstLine, secondLine});
            attemptHeights.add(new float[]{
                multiHeight(croppedRatios, 0, firstLine, maxSizeWidth),
                multiHeight(croppedRatios, firstLine, count, maxSizeWidth)
            });
        }
        for (int firstLine = 1; firstLine < count - 1; ++firstLine) {
            for (int secondLine = 1; secondLine < count - firstLine; ++secondLine) {
                int thirdLine = count - firstLine - secondLine;
                if (firstLine > 3 || secondLine > (averageAspectRatio < 0.85f ? 4 : 3) || thirdLine > 3) continue;
                attemptCounts.add(new int[]{firstLine, secondLine, thirdLine});
                attemptHeights.add(new float[]{
                    multiHeight(croppedRatios, 0, firstLine, maxSizeWidth),
                    multiHeight(croppedRatios, firstLine, firstLine + secondLine, maxSizeWidth),
                    multiHeight(croppedRatios, firstLine + secondLine, count, maxSizeWidth)
                });
            }
        }
        for (int firstLine = 1; firstLine < count - 2; ++firstLine) {
            for (int secondLine = 1; secondLine < count - firstLine; ++secondLine) {
                for (int thirdLine = 1; thirdLine < count - firstLine - secondLine; ++thirdLine) {
                    int fourthLine = count - firstLine - secondLine - thirdLine;
                    if (firstLine > 3 || secondLine > 3 || thirdLine > 3 || fourthLine > 3) continue;
                    attemptCounts.add(new int[]{firstLine, secondLine, thirdLine, fourthLine});
                    attemptHeights.add(new float[]{
                        multiHeight(croppedRatios, 0, firstLine, maxSizeWidth),
                        multiHeight(croppedRatios, firstLine, firstLine + secondLine, maxSizeWidth),
                        multiHeight(croppedRatios, firstLine + secondLine, firstLine + secondLine + thirdLine, maxSizeWidth),
                        multiHeight(croppedRatios, firstLine + secondLine + thirdLine, count, maxSizeWidth)
                    });
                }
            }
        }

        int optimalIdx = -1;
        float optimalDiff = 0f;
        final float targetHeight = maxSizeWidth / 3f * 4f;
        for (int a = 0; a < attemptCounts.size(); ++a) {
            float height = 0;
            float minLineH = Float.MAX_VALUE;
            final float[] hs = attemptHeights.get(a);
            final int[] cs = attemptCounts.get(a);
            for (float v : hs) { height += v; if (v < minLineH) minLineH = v; }
            float diff = Math.abs(height - targetHeight);
            if (cs.length > 1 && (cs[0] > cs[1] || (cs.length > 2 && cs[1] > cs[2]) || (cs.length > 3 && cs[2] > cs[3]))) diff *= 1.2f;
            if (minLineH < minWidth) diff *= 1.5f;
            if (optimalIdx == -1 || diff < optimalDiff) { optimalIdx = a; optimalDiff = diff; }
        }
        if (optimalIdx == -1) {
            for (int a = 0; a < count; ++a) {
                arr[a].set(0, 0, a, a, maxSizeWidth, 0.4f, MessageObject.POSITION_FLAG_LEFT | MessageObject.POSITION_FLAG_RIGHT);
            }
            return arr;
        }

        final int[] cs = attemptCounts.get(optimalIdx);
        final float[] hs = attemptHeights.get(optimalIdx);
        int index = 0;
        for (int i = 0; i < cs.length; ++i) {
            final int c = cs[i];
            final float lineHeight = hs[i];
            int spanLeft = maxSizeWidth;
            MessageObject.GroupedMessagePosition fixPos = null;
            for (int k = 0; k < c; ++k) {
                final float ratio = croppedRatios[index];
                final int width = (int) (ratio * lineHeight);
                spanLeft -= width;
                final MessageObject.GroupedMessagePosition pos = arr[index];
                int flags = 0;
                if (i == 0) flags |= MessageObject.POSITION_FLAG_TOP;
                if (i == cs.length - 1) flags |= MessageObject.POSITION_FLAG_BOTTOM;
                if (k == 0) flags |= MessageObject.POSITION_FLAG_LEFT;
                if (k == c - 1) { flags |= MessageObject.POSITION_FLAG_RIGHT; fixPos = pos; }
                pos.set(k, k, i, i, width, Math.max(minH, lineHeight / maxSizeHeight), flags);
                index++;
            }
            if (fixPos != null) { fixPos.pw += spanLeft; fixPos.spanSize += spanLeft; }
        }
        return arr;
    }

    private static float multiHeight(float[] array, int start, int end, int maxSizeWidth) {
        float sum = 0;
        for (int a = start; a < end; ++a) sum += array[a];
        return maxSizeWidth / Math.max(0.0001f, sum);
    }

    public static abstract class RichBlock implements MultiLayoutTypingAnimator.Block {

        public final RichMessageLayout root;
        public final Rect padding;
        public final int maxWidth;

        private StaticLayout numLayout;
        private float numLayoutY;
        private int numLayoutLeft, numLayoutRight;
        private int listMarkerWidth;
        private CheckBoxBase checkbox;
        private float checkboxY;

        public int listLevel;
        public boolean listOrdered;
        public boolean listCheckbox;
        public boolean listChecked;
        public int accessibilityParentLabelResId;
        public int accessibilityLabelResId;

        public CharSequence getAccessibilityLabel() {
            final CharSequence parentLabel = accessibilityParentLabelResId == 0 ? null : LocaleController.getString(accessibilityParentLabelResId);
            final CharSequence label = accessibilityLabelResId == 0 ? null : LocaleController.getString(accessibilityLabelResId);
            if (TextUtils.isEmpty(parentLabel)) return label;
            if (TextUtils.isEmpty(label)) return parentLabel;
            return TextUtils.concat(parentLabel, ", ", label);
        }

        public CharSequence getAccessibilityListMarker() {
            return listOrdered && numLayout != null ? numLayout.getText() : null;
        }

        private TLObject checkboxItem;
        private final RectF checkboxHit = new RectF();
        private boolean checkboxPressed;
        private ButtonBounce checkboxBounce;

        public MultiLayoutTypingAnimator typingAnimator;

        public RichDetailsBlock parentDetails;

        public float currY;
        public int currH;
        public float prevY;
        public int prevH;
        public boolean currVisible = true;
        public boolean prevVisible = true;

        public RichBlock(RichMessageLayout root, Rect padding, int maxWidth) {
            this.root = root;
            this.padding = new Rect(padding);
            this.maxWidth = maxWidth - padding.left - padding.right;
        }

        protected int getContentPaddingTop() {
            return 0;
        }

        public boolean isVisible() {
            if (parentDetails == null) return true;
            if (!parentDetails.isOpen()) return false;
            return parentDetails.isVisible();
        }

        public boolean isInQuote() {
            if (root.quotes.isEmpty()) return false;
            final int index = root.blocks.indexOf(this);
            if (index < 0) return false;
            for (int i = 0; i < root.quotes.size(); ++i) {
                final QuoteBackground q = root.quotes.get(i);
                if (index >= q.startBlockIndex && index <= q.endBlockIndex) return true;
            }
            return false;
        }

        public void appendAccessibilityText(SpannableStringBuilder sb) {}

        protected static void appendText(SpannableStringBuilder sb, Text single, Text[] arr) {
            if (single != null && single.layout != null && !TextUtils.isEmpty(single.layout.getText())) {
                sb.append(withReplacements(single.layout.getText()));
            } else if (arr != null) {
                for (Text t : arr) {
                    if (t != null && t.layout != null && !TextUtils.isEmpty(t.layout.getText())) {
                        if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
                            sb.append('\n');
                        }
                        sb.append(withReplacements(t.layout.getText()));
                    }
                }
            }
        }

        protected static CharSequence withReplacements(CharSequence cs) {
            if (!(cs instanceof Spanned)) {
                return cs;
            }
            final Spanned spanned = (Spanned) cs;
            final TextSelectionHelper.ReplaceCopyTextSpannable[] spans = spanned.getSpans(0, spanned.length(), TextSelectionHelper.ReplaceCopyTextSpannable.class);
            if (spans == null || spans.length == 0) {
                return cs;
            }
            final SpannableStringBuilder ssb = new SpannableStringBuilder(cs);
            Arrays.sort(spans, (a, b) -> spanned.getSpanStart(b) - spanned.getSpanStart(a));
            for (TextSelectionHelper.ReplaceCopyTextSpannable span : spans) {
                final int start = spanned.getSpanStart(span);
                final int end = spanned.getSpanEnd(span);
                if (start < 0 || end < 0 || start > end || end > ssb.length()) {
                    continue;
                }
                ssb.replace(start, end, span.replacement == null ? "" : span.replacement);
            }
            return ssb;
        }

        public final int getAccessibilityElementCount() {
            return getCheckboxAccessibilityElementCount() + getBlockAccessibilityElementCount();
        }

        public final CharSequence getAccessibilityElementText(int element) {
            if (checkbox != null && element == 0) {
                final SpannableStringBuilder text = new SpannableStringBuilder();
                final CharSequence marker = getAccessibilityListMarker();
                final CharSequence label = getAccessibilityLabel();
                if (!TextUtils.isEmpty(marker)) {
                    text.append(marker).append(' ');
                }
                if (!TextUtils.isEmpty(label)) {
                    text.append(label).append(", ");
                }
                appendAccessibilityText(text);
                return text.length() > 0 ? text : LocaleController.getString(R.string.AccDescrCheckbox);
            }
            return getBlockAccessibilityElementText(element - getCheckboxAccessibilityElementCount());
        }

        public final boolean isAccessibilityElementCheckbox(int element) {
            return checkbox != null && element == 0;
        }

        public final boolean isAccessibilityElementChecked(int element) {
            return isAccessibilityElementCheckbox(element) && getCheckboxChecked();
        }

        public final boolean isAccessibilityElementClickable(int element) {
            return !isAccessibilityElementCheckbox(element) || canToggleCheckbox();
        }

        public final boolean isAccessibilityElementText(int element) {
            return !isAccessibilityElementCheckbox(element) && isBlockAccessibilityElementText(element - getCheckboxAccessibilityElementCount());
        }

        public final CharSequence getAccessibilityElementStateDescription(int element) {
            if (isAccessibilityElementCheckbox(element)) {
                return LocaleController.getString(getCheckboxChecked() ? R.string.AccDescrChecked : R.string.AccDescrNotChecked);
            }
            return getBlockAccessibilityElementStateDescription(element - getCheckboxAccessibilityElementCount());
        }

        protected int getBlockAccessibilityElementCount() {
            return 0;
        }

        protected CharSequence getBlockAccessibilityElementText(int element) {
            return null;
        }

        protected CharSequence getBlockAccessibilityElementStateDescription(int element) {
            return null;
        }

        protected boolean isBlockAccessibilityElementText(int element) {
            return false;
        }

        public final void getAccessibilityElementBounds(int element, Rect out) {
            if (checkbox != null && element == 0) {
                out.set(padding.left, (int) currY, padding.left + maxWidth, (int) (currY + getHeight()));
            } else {
                getBlockAccessibilityElementBounds(element - getCheckboxAccessibilityElementCount(), out);
            }
            final int bubbleLeft = -root.padLeft;
            final int bubbleRight = root.getMinWidth() + root.padRight;
            out.left = Math.min(bubbleRight, Math.max(out.left, bubbleLeft));
            out.right = Math.max(out.left, Math.min(out.right, bubbleRight));
        }

        protected void getBlockAccessibilityElementBounds(int element, Rect out) {
            out.set(padding.left, (int) currY, padding.left + maxWidth, (int) (currY + getHeight()));
        }

        public final boolean onAccessibilityElementClick(int element, View host) {
            if (checkbox != null && element == 0) {
                if (!canToggleCheckbox()) return false;
                toggleCheckbox();
                return true;
            }
            return onBlockAccessibilityElementClick(element - getCheckboxAccessibilityElementCount(), host);
        }

        protected boolean onBlockAccessibilityElementClick(int element, View host) {
            return false;
        }

        private int getCheckboxAccessibilityElementCount() {
            return checkbox == null ? 0 : 1;
        }

        public void snapshot() {
            prevY = currY;
            prevH = currH;
            prevVisible = currVisible;
        }

        public void setNum(String num) {
            root.numTextPaint.setTextSize(dp(SharedConfig.fontSize));
            CharSequence number = num;
            final Layout textLayout = getLayout();
            final CharSequence itemText = textLayout == null ? null : textLayout.getText();
            if (!TextUtils.isEmpty(number) && itemText instanceof Spanned && itemText.length() > 0) {
                final StyleSpan[] spans = ((Spanned) itemText).getSpans(0, 1, StyleSpan.class);
                for (StyleSpan span : spans) {
                    if (hasFlag(span.flags, TEXT_FLAG_BOLD)) {
                        final SpannableString boldNumber = new SpannableString(number);
                        boldNumber.setSpan(new android.text.style.StyleSpan(Typeface.BOLD), 0, boldNumber.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        number = boldNumber;
                        break;
                    }
                }
            }
            final int markerWidth = listMarkerWidth > 0
                ? listMarkerWidth
                : Math.max(root.isRtl() ? padding.right : padding.left, dp(4 + root.fontSize));
            numLayout = new StaticLayout(number, root.numTextPaint, markerWidth,
                root.isRtl() ? Layout.Alignment.ALIGN_NORMAL : Layout.Alignment.ALIGN_OPPOSITE, 1.0f, 0, false);
            numLayoutLeft = dp(4 + root.fontSize);
            numLayoutRight = 0;
            for (int line = 0; line < numLayout.getLineCount(); ++line) {
                numLayoutLeft = Math.min(numLayoutLeft, (int) numLayout.getLineLeft(line));
                numLayoutRight = Math.max(numLayoutRight, (int) numLayout.getLineRight(line));
            }
            updateListMarkerY();
        }

        public void setListMarkerWidth(int width) {
            listMarkerWidth = width;
        }

        protected final void updateListMarkerY() {
            if (numLayout != null) {
                if (getLayout() != null && getLayout().getLineCount() > 0 && numLayout.getLineCount() > 0) {
                    numLayoutY = getContentPaddingTop() + getLayout().getLineBaseline(0) - numLayout.getLineBaseline(0);
                } else {
                    numLayoutY = (Math.min(dp(14 + root.fontSize), getHeight() - padding.top - padding.bottom) - numLayout.getHeight()) / 2f;
                }
            }
            if (checkbox != null) {
                if (getLayout() != null && getLayout().getLineCount() > 0) {
                    checkboxY = getContentPaddingTop() + getLayout().getLineBaseline(0) - dp(20) * 0.7f;
                } else {
                    checkboxY = (Math.min(dp(14 + root.fontSize), getHeight() - padding.top - padding.bottom) - dp(20)) / 2f;
                }
            }
        }

        public void setCheckbox(boolean checked) {
            setCheckbox(checked, null);
        }
        public void setCheckbox(boolean checked, TLObject sourceItem) {
            this.checkboxItem = sourceItem;
            if (checkbox == null) {
                checkbox = new CheckBoxBase(null, 20, root.resourcesProvider);
                checkbox.setColor(Theme.key_telegram_color, Theme.key_dialogCheckboxSquareDisabled, Theme.key_checkboxCheck);
                checkbox.setBackgroundType(10);
                checkbox.setDrawUnchecked(true);
                checkbox.setCustomRadius(dp(5));
            }
            checkbox.setChecked(checked, false);
            updateListMarkerY();
        }

        public void draw(Canvas canvas) {
            draw(canvas, Integer.MIN_VALUE, 0);
        }
        public void draw(Canvas canvas, int lineIndex, float xPosition) {
            canvas.save();
            canvas.translate(padding.left, padding.top);

            final boolean rtl = root.isRtl();
            final float rtlTextRight = root.getMinWidth() + root.padRight - dp(14) - padding.right - padding.left;

            if (numLayout != null) {
                root.numTextPaint.setTextSize(dp(SharedConfig.fontSize));
                root.numTextPaint.setColor(root.getThemedColor(root.isOut() ? Theme.key_chat_messageTextOut : Theme.key_chat_messageTextIn));
                canvas.save();
                if (!listOrdered && !listCheckbox) {
                    final float diameter = dpf2(4.3f);
                    final float centerX = rtl
                        ? rtlTextRight + dp(18) - dpf2(5.66f) - diameter / 2f
                        : dpf2(5.66f) + diameter / 2f - dp(18);
                    final float centerY = numLayoutY + numLayout.getLineBaseline(0) - dp(SharedConfig.fontSize) * .35f;
                    canvas.drawCircle(centerX, centerY, diameter / 2f, root.numTextPaint);
                } else if (rtl) {
                    canvas.translate(rtlTextRight + dp(ORDERED_LIST_MARKER_START_DP)
                        - numLayoutLeft + (checkbox != null ? dp(26) : 0), numLayoutY);
                    numLayout.draw(canvas);
                } else {
                    canvas.translate(dp(ORDERED_LIST_MARKER_START_DP) - listMarkerWidth
                        - numLayoutLeft, numLayoutY);
                    numLayout.draw(canvas);
                }
                canvas.restore();
            }
            if (checkbox != null) {
                final int checkboxX = rtl ? (int) (rtlTextRight + dp(6)) : -dp(26);
                checkboxHit.set(checkboxX - dp(6), checkboxY - dp(6), checkboxX + dp(20) + dp(6), checkboxY + dp(20) + dp(6));
                if (root.view != null && checkbox.getParentView() == null) {
                    checkbox.setParentView(root.view);
                }
                final float scale = checkboxBounce != null ? checkboxBounce.getScale(0.1f) : 1f;
                canvas.save();
                canvas.scale(scale, scale, checkboxX + dp(10), checkboxY + dp(10));
                checkbox.setBounds(checkboxX, (int) checkboxY, dp(20), dp(20));
                checkbox.draw(canvas);
                canvas.restore();
            }

            if (lineIndex == Integer.MIN_VALUE) {
                onDraw(canvas);
            } else {
                onDrawFaded(canvas, lineIndex, xPosition);
            }
            canvas.restore();
        }
        public boolean touchEvent(MotionEvent event) {
            event.offsetLocation(-padding.left, -padding.top);
            try {
                if (checkbox != null) {
                    final int act = event.getActionMasked();
                    final boolean inside = checkboxHit.contains(event.getX(), event.getY());
                    if (act == MotionEvent.ACTION_DOWN) {
                        if (inside && canToggleCheckbox()) {
                            checkboxPressed = true;
                            if (checkboxBounce == null && root.view != null) checkboxBounce = new ButtonBounce(root.view);
                            if (checkboxBounce != null) checkboxBounce.setPressed(true);
                            invalidateCell();
                            return true;
                        }
                    } else if (checkboxPressed) {
                        if (act == MotionEvent.ACTION_MOVE) {
                            if (!inside) {
                                checkboxPressed = false;
                                if (checkboxBounce != null) checkboxBounce.setPressed(false);
                            }
                            return true;
                        } else if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
                            final boolean doToggle = act == MotionEvent.ACTION_UP && inside;
                            checkboxPressed = false;
                            if (checkboxBounce != null) checkboxBounce.setPressed(false);
                            if (doToggle) toggleCheckbox();
                            invalidateCell();
                            return true;
                        }
                    }
                }
                return onTouchEvent(event);
            } finally {
                event.offsetLocation(padding.left, padding.top);
            }
        }
        protected boolean onTouchEvent(MotionEvent event) { return false; }

        private void invalidateCell() {
            if (root.view != null) root.view.invalidate();
        }

        private boolean getCheckboxChecked() {
            if (checkboxItem instanceof TL_iv.PageListItem) return ((TL_iv.PageListItem) checkboxItem).checked;
            if (checkboxItem instanceof TL_iv.PageListOrderedItem) return ((TL_iv.PageListOrderedItem) checkboxItem).checked;
            return checkbox != null && checkbox.isChecked();
        }

        private void setCheckboxChecked(boolean value) {
            if (checkboxItem instanceof TL_iv.PageListItem) ((TL_iv.PageListItem) checkboxItem).checked = value;
            else if (checkboxItem instanceof TL_iv.PageListOrderedItem) ((TL_iv.PageListOrderedItem) checkboxItem).checked = value;
        }

        private boolean canToggleCheckbox() {
            return checkbox != null && checkboxItem != null
                && root.getCell() != null && root.getDelegate() != null
                && root.getDelegate().canToggleRichMessageCheckbox(root.getCell());
        }

        private void toggleCheckbox() {
            if (!canToggleCheckbox()) return;
            if (!MessagesController.getInstance(root.currentAccount).richEditorAllowed()) {
                new PremiumFeatureBottomSheet(root.cell.getContext(), PremiumPreviewFragment.PREMIUM_FEATURE_RICH_EDITOR, true, root.resourcesProvider).show();
                return;
            }
            final boolean newChecked = !getCheckboxChecked();
            setCheckboxChecked(newChecked);
            if (root.view != null) checkbox.setParentView(root.view);
            checkbox.setChecked(newChecked, true);
            invalidateCell();
            if (root.view != null) {
                root.view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
            }
            final Runnable revertOnError = () -> {
                setCheckboxChecked(!newChecked);
                if (root.view != null) checkbox.setParentView(root.view);
                checkbox.setChecked(!newChecked, true);
                invalidateCell();
            };
            root.getDelegate().didToggleRichMessageCheckbox(root.getCell(), newChecked, revertOnError);
        }
        public boolean isHorizontallyDragging() { return false; }
        public boolean isPressingLink() {
            final TextSelectionHelper.TextLayoutBlock[] texts = getText();
            if (texts == null) return false;
            for (TextSelectionHelper.TextLayoutBlock tb : texts) {
                if (tb instanceof Text && ((Text) tb).isPressingLink()) return true;
            }
            return false;
        }

        public boolean findLink(CharacterStyle link, int blockY, FoundLink out) {
            final TextSelectionHelper.TextLayoutBlock[] texts = getText();
            if (texts == null) return false;
            for (TextSelectionHelper.TextLayoutBlock tb : texts) {
                if (!(tb instanceof Text)) continue;
                final Text t = (Text) tb;
                if (t.fillFoundLink(link, out)) {
                    out.x = padding.left - t.left;
                    out.y = blockY + padding.top;
                    return true;
                }
            }
            return false;
        }

        protected void requestDisallowParentIntercept(boolean disallow) {
            if (view == null) return;
            ViewParent p = view.getParent();
            while (p != null) {
                p.requestDisallowInterceptTouchEvent(disallow);
                p = p.getParent();
            }
        }

        public int getMinWidth() { return padding.left + maxWidth + padding.right; }
        public int getLastLineWidth() { return getMinWidth(); }
        public boolean forcesTimeToNewLine() { return getLastLineWidth() >= getMinWidth(); }
        public int getHeight() { return 0; }

        protected void onDraw(Canvas canvas) {}
        protected void onDrawFaded(Canvas canvas, int lineIndex, float xPosition) {
            onDraw(canvas);
        }

        public boolean drawOverlay(Canvas canvas) {
            return drawOverlay(canvas, null);
        }

        public boolean drawOverlay(Canvas canvas, ColorFilter colorFilter) {
            final TextSelectionHelper.TextLayoutBlock[] texts = getText();
            if (texts == null) return false;
            boolean drew = false;
            for (TextSelectionHelper.TextLayoutBlock tb : texts) {
                if (!(tb instanceof Text)) continue;
                final Text t = (Text) tb;
                if (t.animatedEmojiStack == null || t.animatedEmojiStack.holders.isEmpty()) continue;
                canvas.save();
                canvas.translate(t.x, t.y - currY);
                AnimatedEmojiSpan.drawAnimatedEmojis(canvas, t.layout, t.animatedEmojiStack, 0, t.spoilers, 0, 0, 0, 1.0f, colorFilter);
                canvas.restore();
                drew = true;
            }
            return drew;
        }

        protected void onAttachedToWindow() {}
        protected void onDetachedFromWindow() {}
        protected TextSelectionHelper.TextLayoutBlock[] getText() { return null; }

        protected int layoutX, layoutY, layoutRow;
        protected void placeTexts(int blockX, int blockY, int row) {
            this.layoutX = blockX;
            this.layoutY = blockY;
            this.layoutRow = row;
            final TextSelectionHelper.TextLayoutBlock[] texts = getText();
            if (texts == null) return;
            for (TextSelectionHelper.TextLayoutBlock tb : texts) {
                if (tb instanceof Text) {
                    final Text t = (Text) tb;
                    t.setX(blockX - t.left);
                    t.setY(blockY);
                    t.setRow(row);
                }
            }
        }

        public float getBackgroundScale() {
            return 1f;
        }

        @Override public Layout getLayout() { return null; }

        @Override public View getParentView() { return null; }

        public void collectAnimatorBlocks(List<MultiLayoutTypingAnimator.Block> out) {
            out.add(this);
        }

        public void drawWithTyping(Canvas canvas) {
            final MultiLayoutTypingAnimator anim = typingAnimator;
            if (anim != null && anim.isRunning() && anim.indexOf(this) >= 0) {
                if (!anim.needDraw(this)) return;
                if (anim.isFadeBlock(this)) {
                    draw(canvas, anim.getFadeLineIndex(this), anim.getFadeXPosition(this));
                    return;
                }
                final float alpha = anim.getBlockAlpha(this);
                if (alpha <= 0f) return;
                if (alpha < 1f) {
                    final int sc = canvas.saveLayerAlpha(0, 0, padding.left + maxWidth + padding.right, getHeight(), (int) (alpha * 255));
                    draw(canvas);
                    canvas.restoreToCount(sc);
                    return;
                }
            }
            draw(canvas);
        }

        protected View view;

        public void attach(View view) {
            if (this.view == view) return;
            if (this.view != null) {
                onDetachedFromWindow();
                if (checkbox != null) checkbox.onDetachedFromWindow();
                this.view = null;
            }
            this.view = view;
            if (checkbox != null) {
                checkbox.setParentView(view);
                checkbox.onAttachedToWindow();
            }
            onAttachedToWindow();
        }
        public void detach(View view) {
            if (this.view == null) return;
            if (this.view != view) return;
            onDetachedFromWindow();
            if (checkbox != null) checkbox.onDetachedFromWindow();
            this.view = null;
        }
        public boolean isAttachedToWindow() {
            return this.view != null;
        }
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    private Drawable getThemedDrawable(String key) {
        final Drawable drawable = resourcesProvider != null ? resourcesProvider.getDrawable(key) : null;
        return drawable != null ? drawable : Theme.getThemeDrawable(key);
    }

    public static class PreviewView extends View implements TextSelectionHelper.ArticleSelectableView {

        private final int currentAccount;
        private Theme.ResourcesProvider resourcesProvider;

        private MessageObject messageObject;
        private TL_iv.RichMessage richMessage;

        private RichMessageLayout layout;

        public PreviewView(Context context) {
            this(context, UserConfig.selectedAccount, null);
        }

        public PreviewView(Context context, int currentAccount, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.currentAccount = currentAccount;
            this.resourcesProvider = resourcesProvider;
            NotificationCenter.listenEmojiLoading(this);
        }

        public void setResourcesProvider(Theme.ResourcesProvider resourcesProvider) {
            this.resourcesProvider = resourcesProvider;
            if (layout != null) layout.setResourcesProvider(resourcesProvider);
        }

        private int insetLeft, insetTop, insetRight, insetBottom;

        @Override
        public void setPadding(int left, int top, int right, int bottom) {
            if (insetLeft == left && insetTop == top && insetRight == right && insetBottom == bottom) return;
            insetLeft = left;
            insetTop = top;
            insetRight = right;
            insetBottom = bottom;
            requestLayout();
            invalidate();
        }

        private boolean translationLoading;
        public void setTranslationLoading(boolean loading) {
            translationLoading = loading;
            if (layout != null) layout.forceTranslationLoading = loading;
            invalidate();
        }

        @Override
        public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> blocks) {
            if (layout != null) {
                final int px = insetLeft, py = insetTop;
                for (int i = 0; i < layout.textBlocks.size(); i++) {
                    blocks.add(new PaddedTextLayoutBlock(layout.textBlocks.get(i), px, py));
                }
            }
        }

        private TextSelectionHelper.ArticleTextSelectionHelper textSelectionHelper;
        public void setTextSelectionHelper(TextSelectionHelper.ArticleTextSelectionHelper textSelectionHelper) {
            this.textSelectionHelper = textSelectionHelper;
        }

        public void set(TL_iv.RichMessage rich_message) {
            if (richMessage == rich_message) return;
            richMessage = rich_message;
            if (messageObject == null) {
                final TLRPC.TL_message message = new TLRPC.TL_message();
                message.out = true;
                messageObject = new MessageObject(currentAccount, message, false, false);
            }
            messageObject.messageOwner.rich_message = rich_message;
            if (layout != null) layout.detach(this);
            layout = null;
            requestLayout();
            invalidate();
        }

        private void buildLayout(int width) {
            if (width <= 0 || richMessage == null) {
                layout = null;
                return;
            }
            if (layout != null && !layout.needsUpdate(richMessage, width)) {
                return;
            }
            if (layout != null) layout.detach(this);
            layout = new RichMessageLayout(messageObject, width, null);
            layout.forceTranslationLoading = translationLoading;
            layout.setResourcesProvider(resourcesProvider);
            layout.invalidateAnimatedEmojiInParent = true;
            layout.checkQuoteLine(null, null);
            if (isAttachedToWindow()) {
                layout.attach(this);
                layout.updateAnimatedEmojis(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES);
            }
        }

        private int minHeight = -1;
        private int maxHeight = -1;
        public void setMinHeight(int minHeight) {
            this.minHeight = minHeight;
        }
        public void setMaxHeight(int maxHeight) {
            this.maxHeight = maxHeight;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            buildLayout(width - insetLeft - insetRight);

            int height = (layout != null ? layout.getHeight() : 0) + insetTop + insetBottom;
            if (maxHeight > 0 && height > maxHeight)
                height = maxHeight;
            if (minHeight > 0 && height < minHeight)
                height = minHeight;
            switch (MeasureSpec.getMode(heightMeasureSpec)) {
                case MeasureSpec.EXACTLY:
                    height = MeasureSpec.getSize(heightMeasureSpec);
                    break;
                case MeasureSpec.AT_MOST:
                    height = Math.min(height, MeasureSpec.getSize(heightMeasureSpec));
                    break;
            }
            setMeasuredDimension(width, height);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (layout == null) return;
            final float clipTop = 0f;
            final float clipBottom = getHeight() - insetTop - insetBottom;
            final boolean clip = layout.getHeight() > getHeight() - insetTop - insetBottom;
            canvas.save();
            if (clip) {
                canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), 0xFF, Canvas.ALL_SAVE_FLAG);
            }
            canvas.translate(insetLeft, insetTop);
            layout.draw(canvas, insetLeft, Math.max(0, getWidth() - layout.getMinWidth() - insetLeft), null, clipTop, clipBottom);
            if (layout.hasOverlay()) {
                layout.drawOverlay(canvas, null);
            }
            if (clip) {
                canvas.translate(-insetLeft, -insetTop);
                canvas.save();
                AndroidUtilities.rectTmp.set(0, getHeight() - dp(24), getWidth(), getHeight());
                layout.clip.draw(canvas, AndroidUtilities.rectTmp, GradientClip.BOTTOM, 1.0f);
                canvas.restore();
                canvas.restore();
            }
            canvas.restore();
            if (textSelectionHelper != null && textSelectionHelper.isInSelectionMode()) {
                final int px = insetLeft, py = insetTop;
                for (int i = 0; i < layout.textBlocks.size(); i++) {
                    final TextSelectionHelper.TextLayoutBlock tb = layout.textBlocks.get(i);
                    canvas.save();
                    canvas.translate(tb.getX() + px, tb.getY() + py);
                    textSelectionHelper.draw(canvas, this, i);
                    canvas.restore();
                }
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (layout != null) {
                layout.attach(this);
                layout.updateAnimatedEmojis(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES);
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (layout != null) layout.detach(this);
        }

        private boolean allowActions = true;
        public void setAllowActions(boolean allow) {
            this.allowActions = allow;
        }

        private Runnable textSelectionLongPressRunnable;

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (!allowActions) {
                return super.onTouchEvent(event);
            }
            if (textSelectionHelper != null) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        textSelectionHelper.setMaybeView(
                            (int) event.getX(),
                            (int) event.getY(),
                            this
                        );
                        if (textSelectionLongPressRunnable == null) {
                            textSelectionLongPressRunnable = () -> {
                                if (layout != null && layout.isPressingLink()) return;
                                textSelectionHelper.trySelect(this);
                            };
                        }
                        removeCallbacks(textSelectionLongPressRunnable);
                        postDelayed(textSelectionLongPressRunnable, ViewConfiguration.getLongPressTimeout());
                        break;
                    case MotionEvent.ACTION_MOVE:
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (textSelectionLongPressRunnable != null) {
                            removeCallbacks(textSelectionLongPressRunnable);
                        }
                        break;
                }
            }
            if (layout != null) {
                event.offsetLocation(-insetLeft, -insetTop);
                final boolean handled = layout.onTouchEvent(event);
                event.offsetLocation(insetLeft, insetTop);
                if (handled) return true;
            }
            return super.onTouchEvent(event);
        }

        public static final class Factory extends UItem.UItemFactory<PreviewView> {
            static { setup(new Factory()); }

            @Override
            public PreviewView createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                final PreviewView cell = new PreviewView(context, currentAccount, resourcesProvider);
                cell.setPadding(dp(20), 0, dp(20), dp(16));
                return cell;
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                final PreviewView cell = (PreviewView) view;
                cell.set((TL_iv.RichMessage) item.object);
                cell.setTranslationLoading(item.checked);
            }

            @Override
            public boolean equals(UItem a, UItem b) {
                return a.id == b.id;
            }

            @Override
            public boolean contentsEquals(UItem a, UItem b) {
                return a.id == b.id && a.object == b.object && a.checked == b.checked;
            }

            public static UItem of(TL_iv.RichMessage richMessage) {
                final UItem item = UItem.ofFactory(Factory.class);
                item.object = richMessage;
                return item;
            }

            @Override
            public boolean isClickable() {
                return false;
            }
        }

        private static class PaddedTextLayoutBlock implements TextSelectionHelper.TextLayoutBlock {
            private final TextSelectionHelper.TextLayoutBlock inner;
            private final int px, py;

            PaddedTextLayoutBlock(TextSelectionHelper.TextLayoutBlock inner, int px, int py) {
                this.inner = inner;
                this.px = px;
                this.py = py;
            }

            @Override
            public Layout getLayout() { return inner.getLayout(); }
            @Override
            public int getX() { return inner.getX() + px; }
            @Override
            public int getY() { return inner.getY() + py; }
            @Override
            public int getRow() { return inner.getRow(); }
            @Override
            public CharSequence getPrefix() { return inner.getPrefix(); }
            @Override
            public CharSequence getText() { return inner.getText(); }
        }
    }
}
