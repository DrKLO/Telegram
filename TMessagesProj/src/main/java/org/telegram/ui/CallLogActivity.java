package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.messenger.MessagesController.findUpdatesAndRemove;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Layout;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ImageSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_phone;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.LoadingCell;
import org.telegram.ui.Cells.LocationCell;
import org.telegram.ui.Cells.ProfileSearchCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.AvatarsImageView;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.DialogsActivityTopPanelLayout;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.FragmentFloatingButton;
import org.telegram.ui.Components.FragmentContextView;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.NumberTextView;
import org.telegram.ui.Components.ProgressButton;
import org.telegram.ui.Components.QRCodeBottomSheet;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.RecyclerAnimationScrollHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.ShareAlert;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.TextHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.DownscaleScrollableNoiseSuppressor;
import org.telegram.ui.Components.blur3.ViewGroupPartRenderer;
import org.telegram.ui.Components.blur3.capture.IBlur3Capture;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceColor;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceRenderNode;
import org.telegram.ui.Components.chat.ViewPositionWatcher;
import org.telegram.ui.Components.voip.VoIPHelper;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;
import org.telegram.ui.Stories.recorder.HintView2;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import androidx.annotation.NonNull;
import androidx.collection.LongSparseArray;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class CallLogActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, MainTabsActivity.TabFragmentDelegate {
	private final int ADDITIONAL_LIST_HEIGHT_DP = Build.VERSION.SDK_INT >= 31 ? 48 : 0;

	private EmptyTextProgressView emptyView;
	private LinearLayoutManager layoutManager;
	private UniversalRecyclerView listView;
	private RecyclerAnimationScrollHelper scrollHelper;
	private FragmentFloatingButton floatingButton;
	private FlickerLoadingView flickerLoadingView;
	private SizeNotifierFrameLayout contentView;
	private HeaderShadowView headerShadowView;
	private HintView2 hideCallTabsHintView;
	private boolean needFinishFragment = true;
	private boolean hasMainTabs;

	private @Nullable ImageView actionModeCloseView;
	private NumberTextView selectedDialogsCountTextView;
	private final ArrayList<View> actionModeViews = new ArrayList<>();

	private ActionBarMenuItem otherItem;

	private final ArrayList<CallLogRow> calls = new ArrayList<>();
	private boolean loading;
	private boolean firstLoaded;
	private boolean endReached;

	private ArrayList<Long> activeGroupCalls;

	private final ArrayList<Integer> selectedIds = new ArrayList<>();

	private DialogsActivityTopPanelLayout topPanelLayout;
	private FrameLayout fragmentContextViewWrapper;
	private FragmentContextView fragmentContextView;

	private Drawable greenDrawable;
	private Drawable greenDrawable2;
	private Drawable redDrawable;
	private Drawable redDrawable2;
	private ImageSpan iconOut, iconIn, iconMissedIn, getIconMissedOut;
	private TLRPC.User lastCallUser;
	private TLRPC.Chat lastCallChat;

	private Long waitingForCallChatId;

	private boolean openTransitionStarted;

	private static final int TYPE_OUT = 0;
	private static final int TYPE_IN = 1;
	private static final int TYPE_MISSED_IN = 2;
	private static final int TYPE_MISSED_OUT = 3;

	private static final int delete = 2;

	public CallLogActivity() {
		this(null);
	}

	public CallLogActivity(Bundle args) {
		super(args);

		iBlur3SourceColor = new BlurredBackgroundSourceColor();
		iBlur3SourceColor.setColor(getThemedColor(Theme.key_windowBackgroundGray));
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			scrollableViewNoiseSuppressor = new DownscaleScrollableNoiseSuppressor();
			iBlur3SourceGlassFrosted = new BlurredBackgroundSourceRenderNode(null);
			iBlur3SourceGlass = new BlurredBackgroundSourceRenderNode(null);
			iBlur3FactoryLiquidGlass = new BlurredBackgroundDrawableViewFactory(iBlur3SourceGlass);
			iBlur3FactoryLiquidGlass.setLiquidGlassEffectAllowed(LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS));
		} else {
			scrollableViewNoiseSuppressor = null;
			iBlur3SourceGlassFrosted = null;
			iBlur3SourceGlass = null;
			iBlur3FactoryLiquidGlass = new BlurredBackgroundDrawableViewFactory(iBlur3SourceColor);
		}
	}

	private class EmptyTextProgressView extends FrameLayout {

		private final TextView emptyTextView1;
		private final TextView emptyTextView2;
		private final View progressView;
		private final RLottieImageView imageView;

		public EmptyTextProgressView(Context context) {
			this(context, null);
		}

		public EmptyTextProgressView(Context context, View progressView) {
			super(context);

			addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
			this.progressView = progressView;

			imageView = new RLottieImageView(context);
			imageView.setAnimation(R.raw.utyan_call, 110, 110);
			imageView.setAutoRepeat(false);
			addView(imageView, LayoutHelper.createFrame(110, 110, Gravity.CENTER, 52, 17, 52, 60));
			imageView.setOnClickListener(v -> {
				if (!imageView.isPlaying()) {
					imageView.setProgress(0.0f);
					imageView.playAnimation();
				}
			});

			emptyTextView1 = new TextView(context);
			emptyTextView1.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
			emptyTextView1.setText(getString(R.string.MakeYourFirstCall));
			emptyTextView1.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
			emptyTextView1.setTypeface(AndroidUtilities.bold());
			emptyTextView1.setGravity(Gravity.CENTER);
			addView(emptyTextView1, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 17, 40, 17, 0));

			emptyTextView2 = new TextView(context);
			String help = formatString(R.string.MakeYourFirstCallHint, getMessagesController().conferenceCallSizeLimit);
			if (AndroidUtilities.isTablet() && !AndroidUtilities.isSmallTablet()) {
				help = help.replace('\n', ' ');
			}
			emptyTextView2.setText(help);
			emptyTextView2.setTextColor(Theme.getColor(Theme.key_emptyListPlaceholder));
			emptyTextView2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
			emptyTextView2.setGravity(Gravity.CENTER);
			emptyTextView2.setLineSpacing(AndroidUtilities.dp(2), 1);
			addView(emptyTextView2, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 17, 80, 17, 0));

			progressView.setAlpha(0f);
			imageView.setAlpha(0f);
			emptyTextView1.setAlpha(0f);
			emptyTextView2.setAlpha(0f);

			setOnTouchListener((v, event) -> true);
		}

		public void showProgress() {
			imageView.animate().alpha(0f).setDuration(150).start();
			emptyTextView1.animate().alpha(0f).setDuration(150).start();
			emptyTextView2.animate().alpha(0f).setDuration(150).start();
			progressView.animate().alpha(1f).setDuration(150).start();
		}

		public void showTextView() {
			imageView.animate().alpha(1f).setDuration(150).start();
			emptyTextView1.animate().alpha(1f).setDuration(150).start();
			emptyTextView2.animate().alpha(1f).setDuration(150).start();
			progressView.animate().alpha(0f).setDuration(150).start();
			imageView.playAnimation();
		}

		@Override
		public boolean hasOverlappingRendering() {
			return false;
		}
	}

	@SuppressLint("NotifyDataSetChanged")
    @Override
	@SuppressWarnings("unchecked")
	public void didReceivedNotification(int id, int account, Object... args) {
		if (id == NotificationCenter.didReceiveNewMessages) {
			if (!firstLoaded) {
				return;
			}
			boolean scheduled = (Boolean) args[2];
			if (scheduled) {
				return;
			}
			ArrayList<MessageObject> arr = (ArrayList<MessageObject>) args[1];
			for (MessageObject msg : arr) {
				if (msg.messageOwner.action instanceof TLRPC.TL_messageActionPhoneCall) {
					long fromId = msg.getFromChatId();
					long userID = fromId == getUserConfig().getClientUserId() ? msg.messageOwner.peer_id.user_id : fromId;
					int callType = fromId == getUserConfig().getClientUserId() ? TYPE_OUT : TYPE_IN;
					TLRPC.PhoneCallDiscardReason reason = msg.messageOwner.action.reason;
					if (callType == TYPE_IN && (reason instanceof TLRPC.TL_phoneCallDiscardReasonMissed || reason instanceof TLRPC.TL_phoneCallDiscardReasonBusy)) {
						callType = TYPE_MISSED_IN;
					}
					if (callType == TYPE_OUT && (reason instanceof TLRPC.TL_phoneCallDiscardReasonMissed || reason instanceof TLRPC.TL_phoneCallDiscardReasonBusy)) {
						callType = TYPE_MISSED_OUT;
					}
					if (!calls.isEmpty()) {
						final CallLogRow topRow = calls.get(0);
						if (eq(userID, topRow.users) && topRow.type == callType) {
							topRow.calls.add(0, msg.messageOwner);
							continue;
						}
					}
					final CallLogRow row = new CallLogRow();
					row.calls.clear();
					row.calls.add(msg.messageOwner);
					row.users.clear();
					final TLRPC.User user = getMessagesController().getUser(userID);
					if (user != null) {
						row.users.add(user);
					}
					row.type = callType;
					row.video = msg.isVideoCall();
					calls.add(0, row);
					listView.adapter.update(true);
				} else if (msg.messageOwner.action instanceof TLRPC.TL_messageActionConferenceCall) {
					final TLRPC.TL_messageActionConferenceCall action = (TLRPC.TL_messageActionConferenceCall) msg.messageOwner.action;
					long fromId = msg.getFromChatId();
					final Set<Long> userIds = action.other_participants.stream().map(DialogObject::getPeerDialogId).collect(Collectors.toSet());
					userIds.add(fromId == getUserConfig().getClientUserId() ? msg.messageOwner.peer_id.user_id : fromId);
					int callType = fromId == getUserConfig().getClientUserId() ? TYPE_OUT : TYPE_IN;
					if (callType == TYPE_IN && action.missed) {
						callType = TYPE_MISSED_IN;
					}
					if (callType == TYPE_OUT && action.missed) {
						callType = TYPE_MISSED_OUT;
					}
					if (!calls.isEmpty()) {
						int sameRowIndex = -1;
						CallLogRow sameRow = null;
						for (int i = 0; i < calls.size(); ++i) {
							final CallLogRow row = calls.get(i);
							if (row.call_id == action.call_id) {
								sameRowIndex = i;
								sameRow = row;
								break;
							}
						}
						if (sameRow != null) {
							sameRow.calls.add(0, msg.messageOwner);
							for (long userId : userIds) {
								boolean contains = false;
								for (TLRPC.User user : sameRow.users) {
									if (userId == user.id) {
										contains = true;
										break;
									}
								}
								if (!contains) {
									final TLRPC.User user = getMessagesController().getUser(userId);
									if (user != null) {
										sameRow.users.add(user);
									}
								}
							}
							listView.adapter.update(true);
							continue;
						}
					}
					final CallLogRow row = new CallLogRow();
					row.call_id = action.call_id;
					row.calls.clear();
					row.calls.add(msg.messageOwner);
					row.users.clear();
					for (long userId : userIds) {
						final TLRPC.User user = getMessagesController().getUser(userId);
						if (user != null) {
							row.users.add(user);
						}
					}
					row.type = callType;
					row.video = msg.isVideoCall();
					calls.add(0, row);
					listView.adapter.update(true);
				}
			}
			if (otherItem != null) {
				otherItem.setVisibility(calls.isEmpty() ? View.GONE : View.VISIBLE);
			}
		} else if (id == NotificationCenter.messagesDeleted) {
			if (!firstLoaded) {
				return;
			}
			boolean scheduled = (Boolean) args[2];
			if (scheduled) {
				return;
			}
			boolean didChange = false;
			ArrayList<Integer> ids = (ArrayList<Integer>) args[0];
			Iterator<CallLogRow> itrtr = calls.iterator();
			while (itrtr.hasNext()) {
				CallLogRow row = itrtr.next();
				Iterator<TLRPC.Message> msgs = row.calls.iterator();
				while (msgs.hasNext()) {
					TLRPC.Message msg = msgs.next();
					if (ids.contains(msg.id)) {
						didChange = true;
						msgs.remove();
					}
				}
				if (row.calls.isEmpty()) {
					itrtr.remove();
				}
			}
			if (didChange && listView != null) {
				listView.adapter.update(true);
			}
		} else if (id == NotificationCenter.activeGroupCallsUpdated) {
			activeGroupCalls = getMessagesController().getActiveGroupCalls();
			if (listView != null) {
				listView.adapter.update(true);
			}
		} else if (id == NotificationCenter.chatInfoDidLoad) {
			if (waitingForCallChatId == null) {
				return;
			}
			TLRPC.ChatFull chatFull = (TLRPC.ChatFull) args[0];
			if (chatFull.id == waitingForCallChatId) {
				ChatObject.Call groupCall = getMessagesController().getGroupCall(waitingForCallChatId, true);
				if (groupCall != null) {
					VoIPHelper.startCall(lastCallChat, null, null, false, getParentActivity(), CallLogActivity.this, getAccountInstance());
					waitingForCallChatId = null;
				}
			}
		} else if (id == NotificationCenter.groupCallUpdated) {
			if (waitingForCallChatId == null) {
				return;
			}
			Long chatId = (Long) args[0];
			if (waitingForCallChatId.equals(chatId)) {
				VoIPHelper.startCall(lastCallChat, null, null, false, getParentActivity(), CallLogActivity.this, getAccountInstance());
				waitingForCallChatId = null;
			}
		}
	}

	private static boolean eq(long userId, ArrayList<TLRPC.User> users) {
		return users.size() == 1 && users.get(0).id == userId;
	}
	private static boolean eq(Set<Long> userIds, ArrayList<TLRPC.User> users) {
		if (userIds.size() != users.size())
			return false;
		for (TLRPC.User user : users) {
			if (!userIds.contains(user.id)) {
				return false;
			}
		}
		return true;
	}

	private static class CallCell extends FrameLayout {

		private final int currentAccount;
		private final AvatarsImageView avatarsImageView;
		private final ImageView imageView;
		private final ProfileSearchCell profileSearchCell;
		private final CheckBox2 checkBox;

		public CallCell(Context context, int currentAccount) {
			super(context);
			this.currentAccount = currentAccount;

			profileSearchCell = new ProfileSearchCell(context);
			profileSearchCell.setCallCellStyle();
			profileSearchCell.setPadding(LocaleController.isRTL ? AndroidUtilities.dp(32) : 0, 0, LocaleController.isRTL ? 0 : AndroidUtilities.dp(32), 0);
			profileSearchCell.setSublabelOffset(AndroidUtilities.dp(LocaleController.isRTL ? 2 : -2), -AndroidUtilities.dp(7));
			addView(profileSearchCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

			avatarsImageView = new AvatarsImageView(context, false);
			avatarsImageView.setAvatarsTextSize(dp(18));
			avatarsImageView.setStepFactor(0.4f);
			avatarsImageView.setSize(dp(29));
			avatarsImageView.setCentered(true);
			avatarsImageView.setVisibility(View.GONE);
			addView(avatarsImageView, LayoutHelper.createFrame(72, LayoutHelper.MATCH_PARENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, -2, 0, 0, 0));

			imageView = new ImageView(context);
			imageView.setColorFilter(Theme.getColor(Theme.key_telegram_color_text), PorterDuff.Mode.SRC_IN);
			imageView.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 1));
			imageView.setScaleType(ImageView.ScaleType.CENTER);
			imageView.setContentDescription(getString(R.string.Call));
			addView(imageView, LayoutHelper.createFrame(48, 48, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL, 8, 0, 8, 0));

			checkBox = new CheckBox2(context, 21);
			checkBox.getCheckBoxBase().setBackgroundColor(Theme.getColor(Theme.key_telegram_color));
			checkBox.setColor(-1, Theme.key_windowBackgroundWhite, Theme.key_checkboxCheck);
			checkBox.setDrawUnchecked(false);
			checkBox.setDrawBackgroundAsArc(3);
			addView(checkBox, LayoutHelper.createFrame(24, 24, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 42, 32, 42, 0));
		}

		public void setChecked(boolean checked, boolean animated) {
			if (checkBox == null) {
				return;
			}
			checkBox.setChecked(checked, animated);
		}

		public void set(CallLogRow row, View.OnClickListener imageOnClick) {
			imageView.setImageResource(row.video ? R.drawable.menu_videocall : R.drawable.menu_call_create_2_24);
			TLRPC.Message last = row.calls.get(0);
			SpannableString subtitle;
			String ldir = LocaleController.isRTL ? "\u202b" : "";
			if (row.calls.size() == 1) {
				subtitle = new SpannableString(ldir + "  " + LocaleController.formatDateCallLog(last.date));
			} else {
				subtitle = new SpannableString(String.format(ldir + "  (%d) %s", row.calls.size(), LocaleController.formatDateCallLog(last.date)));
			}
			switch (row.type) {
				case TYPE_OUT:
					subtitle.setSpan(iconOut(getContext()), ldir.length(), ldir.length() + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
					break;
				case TYPE_IN:
					subtitle.setSpan(iconIn(getContext()), ldir.length(), ldir.length() + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
					break;
				case TYPE_MISSED_IN:
					subtitle.setSpan(iconMissedIn(getContext()), ldir.length(), ldir.length() + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
					break;
				case TYPE_MISSED_OUT:
					subtitle.setSpan(iconMissedOut(getContext()), ldir.length(), ldir.length() + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
					break;
			}
			StringBuilder sb = null;
			if (row.call_id != 0) {
				sb = new StringBuilder();
				for (int i = 0; i < Math.min(3, row.users.size()); i++) {
					if (i > 0) {
						sb.append(", ");
					}
					sb.append(DialogObject.getShortName(row.users.get(i)));
				}
				if (row.users.size() > 3) {
					sb.append(" ");
					sb.append(LocaleController.formatPluralString("AndOther", row.users.size() - 3));
				}

				final ArrayList<TLRPC.User> users = new ArrayList<>(row.users);
				users.add(UserConfig.getInstance(currentAccount).getCurrentUser());

				profileSearchCell.setAllowEmojiStatus(false);
				profileSearchCell.setData(!row.users.isEmpty() ? row.users.get(0) : null, null, sb.toString(), subtitle, false, false);

				avatarsImageView.setVisibility(View.VISIBLE);
				profileSearchCell.avatarImage.clearImage();
				profileSearchCell.dontDrawAvatar = true;
				final int count = Math.min(3, users.size());
				for (int i = 0; i < count; ++i) {
					avatarsImageView.setObject(i, currentAccount, users.get(i));
				}
				avatarsImageView.commitTransition(false);
			} else {
				profileSearchCell.setAllowEmojiStatus(true);
				profileSearchCell.setData(!row.users.isEmpty() ? row.users.get(0) : null, null, null, subtitle, false, false);

				avatarsImageView.setVisibility(View.GONE);
				profileSearchCell.dontDrawAvatar = false;
			}
			imageView.setTag(row);
			imageView.setOnClickListener(imageOnClick);
		}

		private static ImageSpan iconOut(Context context) {
			final Drawable greenDrawable = context.getResources().getDrawable(R.drawable.mini_call_out_16).mutate();
			greenDrawable.setBounds(0, 0, greenDrawable.getIntrinsicWidth(), greenDrawable.getIntrinsicHeight());
			greenDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3), PorterDuff.Mode.MULTIPLY));
			return new ImageSpan(greenDrawable, ImageSpan.ALIGN_BOTTOM);
		}
		private static ImageSpan iconIn(Context context) {
			final Drawable greenDrawable2 = context.getResources().getDrawable(R.drawable.mini_call_in_16).mutate();
			greenDrawable2.setBounds(0, 0, greenDrawable2.getIntrinsicWidth(), greenDrawable2.getIntrinsicHeight());
			greenDrawable2.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3), PorterDuff.Mode.MULTIPLY));
			return new ImageSpan(greenDrawable2, ImageSpan.ALIGN_BOTTOM);
		}
		private static ImageSpan iconMissedIn(Context context) {
			final Drawable redDrawable = context.getResources().getDrawable(R.drawable.mini_call_in_16).mutate();
			redDrawable.setBounds(0, 0, redDrawable.getIntrinsicWidth(), redDrawable.getIntrinsicHeight());
			redDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_fill_RedNormal), PorterDuff.Mode.MULTIPLY));
			return new ImageSpan(redDrawable, ImageSpan.ALIGN_BOTTOM);
		}
		private static ImageSpan iconMissedOut(Context context) {
			final Drawable redDrawable2 = context.getResources().getDrawable(R.drawable.mini_call_out_16).mutate();
			redDrawable2.setBounds(0, 0, redDrawable2.getIntrinsicWidth(), redDrawable2.getIntrinsicHeight());
			redDrawable2.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_fill_RedNormal), PorterDuff.Mode.MULTIPLY));
			return new ImageSpan(redDrawable2, ImageSpan.ALIGN_BOTTOM);
		}

		public static final class Factory extends UItem.UItemFactory<CallCell> {
			static { setup(new Factory()); }

			@Override
			public CallCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
				return new CallCell(context, currentAccount);
			}

			@Override
			public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
				final CallLogRow row = (CallLogRow) item.object;
				final CallCell cell = (CallCell) view;
				cell.set(row, item.clickCallback);
				cell.setChecked(item.checked, false);
			}

			public static UItem of(CallLogRow row, View.OnClickListener onImageClick) {
				final UItem item = UItem.ofFactory(Factory.class);
				item.object = row;
				item.clickCallback = onImageClick;
				return item;
			}
		}
	}

	private static class GroupCallCell extends FrameLayout {

		private final ProfileSearchCell profileSearchCell;
		private final ProgressButton button;
		private TLRPC.Chat currentChat;

		public GroupCallCell(Context context) {
			super(context);

			String text = getString(R.string.VoipChatJoin);
			button = new ProgressButton(context);
			int width = (int) Math.ceil(button.getPaint().measureText(text));

			profileSearchCell = new ProfileSearchCell(context);
			profileSearchCell.setCallCellStyle();
			profileSearchCell.setPadding(LocaleController.isRTL ? (AndroidUtilities.dp(28 + 16) + width) : 0, 0, LocaleController.isRTL ? 0 : (AndroidUtilities.dp(28 + 16) + width), 0);
			profileSearchCell.setSublabelOffset(0, -AndroidUtilities.dp(4));
			addView(profileSearchCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

			button.setText(text);
			button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
			button.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
			button.setProgressColor(Theme.getColor(Theme.key_featuredStickers_buttonProgress));
			button.setBackgroundRoundRect(Theme.getColor(Theme.key_telegram_color), Theme.getColor(Theme.key_featuredStickers_addButtonPressed), 16);
			button.setPadding(AndroidUtilities.dp(14), 0, AndroidUtilities.dp(14), 0);
			addView(button, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, 28, Gravity.TOP | Gravity.END, 0, 16, 14, 0));
		}

		public void setChat(TLRPC.Chat chat, View.OnClickListener onJoinClick) {
			currentChat = chat;
			button.setTag(chat.id);
			String text;
			if (ChatObject.isChannel(chat) && !chat.megagroup) {
				if (!ChatObject.isPublic(chat)) {
					text = getString(R.string.ChannelPrivate).toLowerCase();
				} else {
					text = getString(R.string.ChannelPublic).toLowerCase();
				}
			} else {
				if (chat.has_geo) {
					text = getString(R.string.MegaLocation);
				} else if (!ChatObject.isPublic(chat)) {
					text = getString(R.string.MegaPrivate).toLowerCase();
				} else {
					text = getString(R.string.MegaPublic).toLowerCase();
				}
			}
			profileSearchCell.setData(chat, null, null, text, false, false);
			button.setOnClickListener(onJoinClick);
		}

		public static final class Factory extends UItem.UItemFactory<GroupCallCell> {
			static { setup(new Factory()); }

			@Override
			public GroupCallCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
				return new GroupCallCell(context);
			}

			@Override
			public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
				((GroupCallCell) view).setChat((TLRPC.Chat) item.object, item.clickCallback);
			}

			public static UItem of(TLRPC.Chat chat, View.OnClickListener onJoinClick) {
				final UItem item = UItem.ofFactory(Factory.class);
				item.object = chat;
				item.clickCallback = onJoinClick;
				return item;
			}
		}
	}

	@Override
	public boolean onFragmentCreate() {
		super.onFragmentCreate();
		getCalls(0, 50);
		activeGroupCalls = getMessagesController().getActiveGroupCalls();

		getNotificationCenter().addObserver(this, NotificationCenter.didReceiveNewMessages);
		getNotificationCenter().addObserver(this, NotificationCenter.messagesDeleted);
		getNotificationCenter().addObserver(this, NotificationCenter.activeGroupCallsUpdated);
		getNotificationCenter().addObserver(this, NotificationCenter.chatInfoDidLoad);
		getNotificationCenter().addObserver(this, NotificationCenter.groupCallUpdated);

		if (arguments != null) {
			needFinishFragment = arguments.getBoolean("needFinishFragment", true);
			hasMainTabs = arguments.getBoolean("hasMainTabs", false);
		}

		additionNavigationBarHeight = hasMainTabs ? dp(DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS) : 0;
		additionFloatingButtonOffset = hasMainTabs ? dp(DialogsActivity.MAIN_TABS_HEIGHT + DialogsActivity.MAIN_TABS_MARGIN) : 0;

		return true;
	}

	@Override
	public void onFragmentDestroy() {
		super.onFragmentDestroy();
		getNotificationCenter().removeObserver(this, NotificationCenter.didReceiveNewMessages);
		getNotificationCenter().removeObserver(this, NotificationCenter.messagesDeleted);
		getNotificationCenter().removeObserver(this, NotificationCenter.activeGroupCallsUpdated);
		getNotificationCenter().removeObserver(this, NotificationCenter.chatInfoDidLoad);
		getNotificationCenter().removeObserver(this, NotificationCenter.groupCallUpdated);
	}

	@SuppressLint("UseCompatLoadingForDrawables")
    @Override
	public View createView(Context context) {
		if (!hasMainTabs) {
			actionBar.setBackButtonDrawable(new BackDrawable(false));
		}
		actionBar.setAllowOverlayTitle(true);
		actionBar.setTitle(getString(R.string.Calls));
		actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
			@Override
			public void onItemClick(int id) {
				if (id == -1) {
					if (actionBar.isActionModeShowed()) {
						hideActionMode(true);
					} else {
						finishFragment();
					}
				} else if (id == delete) {
					showDeleteAlert(false);
				}
			}
		});

		ActionBarMenu menu = actionBar.createMenu();
		otherItem = menu.addItem(10, R.drawable.ic_ab_other);
		otherItem.setContentDescription(getString(R.string.AccDescrMoreOptions));
		otherItem.setOnClickListener(v -> showItemOptions());

		listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, this::onLongClick);
		listView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));
		listView.setSections();
		listView.adapter.setApplyBackground(false);
		contentView = new SizeNotifierFrameLayout(context) {
			@Override
			protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
				measureChildWithMargins(actionBar, widthMeasureSpec, 0, heightMeasureSpec, 0);
				((MarginLayoutParams) topPanelLayout.getLayoutParams()).topMargin = actionBar.getMeasuredHeight() - dp(14);
				((MarginLayoutParams) emptyView.getLayoutParams()).topMargin = actionBar.getMeasuredHeight();
				((MarginLayoutParams) headerShadowView.getLayoutParams()).topMargin = actionBar.getMeasuredHeight();
				checkUi_listViewPadding();
				super.onMeasure(widthMeasureSpec, heightMeasureSpec);
			}

			@Override
			protected void onLayout(boolean changed, int l, int t, int r, int b) {
				super.onLayout(changed, l, t, r, b);
				checkUi_floatingButton();
				checkUi_listClip();
			}

			@Override
			protected void dispatchDraw(Canvas canvas) {
				if (Build.VERSION.SDK_INT >= 31 && scrollableViewNoiseSuppressor != null) {
					blur3_InvalidateBlur();

					final int width = getMeasuredWidth();
					final int height = getMeasuredHeight();
					if (iBlur3SourceGlassFrosted != null && !iBlur3SourceGlassFrosted.inRecording()) {
						//if (iBlur3SourceGlassFrosted.needUpdateDisplayList(width, height) || iBlur3Invalidated) {
						final Canvas c = iBlur3SourceGlassFrosted.beginRecording(width, height);
						c.drawColor(getThemedColor(Theme.key_windowBackgroundGray));
						if (SharedConfig.chatBlurEnabled()) {
							scrollableViewNoiseSuppressor.draw(c, DownscaleScrollableNoiseSuppressor.DRAW_FROSTED_GLASS);
						}
						iBlur3SourceGlassFrosted.endRecording();
						//}
					}
					if (iBlur3SourceGlass != null && !iBlur3SourceGlass.inRecording()) {
						//if (iBlur3SourceGlass.needUpdateDisplayList(width, height) || iBlur3Invalidated) {
						final Canvas c = iBlur3SourceGlass.beginRecording(width, height);
						c.drawColor(getThemedColor(Theme.key_windowBackgroundGray));
						if (SharedConfig.chatBlurEnabled()) {
							scrollableViewNoiseSuppressor.draw(c, DownscaleScrollableNoiseSuppressor.DRAW_GLASS);
						}
						iBlur3SourceGlass.endRecording();
						//}
					}
					iBlur3Invalidated = false;
				}

				super.dispatchDraw(canvas);
			}

			@Override
			public void drawBlurRect(Canvas canvas, float y, Rect rectTmp, Paint blurScrimPaint, boolean top) {
				if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !SharedConfig.chatBlurEnabled() || iBlur3SourceGlassFrosted == null) {
					canvas.drawRect(rectTmp, blurScrimPaint);
					return;
				}

				canvas.save();
				canvas.translate(0, -y);
				iBlur3SourceGlassFrosted.draw(canvas, rectTmp.left, rectTmp.top + y, rectTmp.right, rectTmp.bottom + y);
				canvas.restore();

				final int oldScrimAlpha = blurScrimPaint.getAlpha();
				blurScrimPaint.setAlpha(ChatActivity.ACTION_BAR_BLUR_ALPHA);
				canvas.drawRect(rectTmp, blurScrimPaint);
				blurScrimPaint.setAlpha(oldScrimAlpha);
			}
		};

		iBlur3FactoryLiquidGlass.setSourceRootView(new ViewPositionWatcher(contentView), contentView);


		iBlur3Capture = new ViewGroupPartRenderer(listView, contentView, listView::drawChild);
		listView.addEdgeEffectListener(() -> listView.postOnAnimation(() -> {
			checkUi_listClip();
			blur3_InvalidateBlur();
		}));

		fragmentView = contentView;
		fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

		flickerLoadingView = new FlickerLoadingView(context);
		flickerLoadingView.setViewType(FlickerLoadingView.CALL_LOG_TYPE);
		flickerLoadingView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
		flickerLoadingView.showDate(false);
		emptyView = new EmptyTextProgressView(context, flickerLoadingView);
		contentView.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

		listView.setClipToPadding(false);
		listView.setEmptyView(emptyView);
		listView.setLayoutManager(layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
		listView.setVerticalScrollbarPosition(LocaleController.isRTL ? RecyclerListView.SCROLLBAR_POSITION_LEFT : RecyclerListView.SCROLLBAR_POSITION_RIGHT);
		scrollHelper = new RecyclerAnimationScrollHelper(listView, layoutManager);
		scrollHelper.setScrollListener(this::blur3_InvalidateBlur);
		contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT, 0, -ADDITIONAL_LIST_HEIGHT_DP, 0, -ADDITIONAL_LIST_HEIGHT_DP));

		listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
			private boolean scrollUpdated;

			@Override
			public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
				int firstVisibleItem = layoutManager.findFirstVisibleItemPosition();
				int visibleItemCount = firstVisibleItem == RecyclerView.NO_POSITION ? 0 : Math.abs(layoutManager.findLastVisibleItemPosition() - firstVisibleItem) + 1;
				if (visibleItemCount > 0) {
					int totalItemCount = listView.adapter.getItemCount();
					if (!endReached && !loading && !calls.isEmpty() && firstVisibleItem + visibleItemCount >= totalItemCount - 5) {
						final CallLogRow row = calls.get(calls.size() - 1);
						AndroidUtilities.runOnUIThread(() -> getCalls(row.calls.get(row.calls.size() - 1).id, 100));
					}
				}

				final View topChild = recyclerView.getChildAt(0);
				int firstViewTop = topChild != null ? topChild.getTop() : 0;

				if (dy != 0 && scrollUpdated) {
					floatingButton.setButtonVisible(dy < 0, true);
				}
				scrollUpdated = true;

				headerShadowView.setShadowVisible(!(firstVisibleItem == 0 && firstViewTop >= listView.getPaddingTop()), true);

				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && scrollableViewNoiseSuppressor != null) {
					scrollableViewNoiseSuppressor.onScrolled(dx, dy);
					blur3_InvalidateBlur();
				}
			}
		});

		if (loading) {
			emptyView.showProgress();
		} else {
			emptyView.showTextView();
		}

		floatingButton = new FragmentFloatingButton(context, resourceProvider);
		floatingButton.imageView.setImageResource(R.drawable.filled_calls_plus);
		floatingButton.setContentDescription(getString(R.string.Call));
		floatingButton.setOnClickListener(v -> openCreateCall());
		contentView.addView(floatingButton, FragmentFloatingButton.createDefaultLayoutParams());

		topPanelLayout = new DialogsActivityTopPanelLayout(context);
		topPanelLayout.setPadding(dp(11), dp(21), dp(11), dp(21));
		topPanelLayout.setOnAnimatedHeightChangedListener(() -> {
			blur3_InvalidateBlur();
			checkUi_listViewPadding();
		});

		BlurredBackgroundDrawable topPanelLayoutBackground = iBlur3FactoryLiquidGlass.create(topPanelLayout, BlurredBackgroundProviderImpl.topPanel(resourceProvider));
		topPanelLayoutBackground.setRadius(dp(24));
		topPanelLayoutBackground.setPadding(dp(7));
		topPanelLayout.setBlurredBackground(topPanelLayoutBackground);

		fragmentContextViewWrapper = new FrameLayout(context);
		topPanelLayout.addView(fragmentContextViewWrapper);
		topPanelLayout.setViewVisible(fragmentContextViewWrapper, true, false);
		fragmentContextView = new FragmentContextView(context, this, contentView, false, resourceProvider) {
			@Override
			public void setVisibility(int visibility) {
				topPanelLayout.setViewVisible(fragmentContextViewWrapper, visibility == VISIBLE);
			}
		};
		fragmentContextView.isInsideBubble = true;
		fragmentContextViewWrapper.addView(fragmentContextView);
		contentView.addView(topPanelLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 0, -14, 0, 0));
		contentView.addView(actionBar);

		headerShadowView = new HeaderShadowView(context, parentLayout);
		headerShadowView.setShadowVisible(false, false);
		contentView.addView(headerShadowView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 5, Gravity.TOP));

		actionBar.setDrawBlurBackground(contentView);
		actionBar.setAdaptiveBackground(listView);

		Bulletin.addDelegate(this, new Bulletin.Delegate() {
			@Override
			public void onBottomOffsetChange(float offset) {
				additionalFloatingTranslation = Math.max(0, offset - navigationBarHeight - additionFloatingButtonOffset);
				checkUi_floatingButton();
			}

			@Override
			public int getBottomOffset(int tag) {
				return navigationBarHeight + additionFloatingButtonOffset;
			}
		});
		if (hasMainTabs) {
			ViewCompat.setOnApplyWindowInsetsListener(fragmentView, this::onInsetsInternal);
		}
		return fragmentView;
	}

	private final int ID_CREATE_CALL = 1;
	private final int ID_SHOW_IN_MAIN_TABS = 2;

	private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
		final boolean hasActiveCalls = !activeGroupCalls.isEmpty();
		final boolean hasCalls = !calls.isEmpty();

		if (hasActiveCalls || hasCalls) {
			items.add(UItem.asButton(ID_CREATE_CALL, R.drawable.menu_call_create, getString(R.string.GroupCallCreate2)).accent());
			if (!getUserConfig().showCallsTab) {
				items.add(UItem.asButton(ID_SHOW_IN_MAIN_TABS, R.drawable.menu_add_tab_24, getString(R.string.GroupCallShowInMainTabs)).accent());
			}
			items.add(UItem.asShadow(null));
		}

		if (hasActiveCalls) {
			for (Long chatId : activeGroupCalls) {
				if (chatId == null) continue;
				final TLRPC.Chat chat = getMessagesController().getChat(chatId);
				if (chat == null) continue;
				items.add(GroupCallCell.Factory.of(chat, this::onGroupCallClick));
			}
			items.add(UItem.asShadow(null));
		}
		if (hasCalls) {
			for (CallLogRow row : calls) {
				items.add(CallCell.Factory.of(row, onCallClick(row)).setChecked(isSelected(row.calls)));
			}
			if (!endReached) {
				items.add(UItem.asFlicker(-1, FlickerLoadingView.CALL_LOG_TYPE));
				items.add(UItem.asFlicker(-2, FlickerLoadingView.CALL_LOG_TYPE));
				items.add(UItem.asFlicker(-3, FlickerLoadingView.CALL_LOG_TYPE));
			}
		}
	}

	private View.OnClickListener onCallClick(CallLogRow row) {
		return view -> {
			if (row.users.size() == 1) {
				final TLRPC.User user = row.users.get(0);
				final TLRPC.UserFull userFull = getMessagesController().getUserFull(user.id);
				VoIPHelper.startCall(lastCallUser = user, row.video, row.video || userFull != null && userFull.video_calls_available, getParentActivity(), null, getAccountInstance());
			} else {
				final boolean video = row.video;
				final HashSet<Long> participants = new HashSet<>();
				for (TLRPC.User user : row.users) {
					participants.add(user.id);
				}

				final TLRPC.TL_inputGroupCallInviteMessage inputGroupCall = new TLRPC.TL_inputGroupCallInviteMessage();
				inputGroupCall.msg_id = row.calls.get(0).id;

				final AlertDialog progressDialog = new AlertDialog(getContext(), AlertDialog.ALERT_TYPE_SPINNER);

				final TL_phone.getGroupCall req = new TL_phone.getGroupCall();
				req.call = inputGroupCall;
				req.limit = getMessagesController().conferenceCallSizeLimit;
				final int reqId = getConnectionsManager().sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
					progressDialog.dismiss();
					if (res instanceof TL_phone.groupCall) {
						final TL_phone.groupCall r = (TL_phone.groupCall) res;
						getMessagesController().putUsers(r.users, false);
						getMessagesController().putChats(r.chats, false);
						if (r.participants.isEmpty()) {
							showDialog(new CreateGroupCallSheet(getContext(), participants));
						} else {
							VoIPHelper.joinConference(getParentActivity(), currentAccount, inputGroupCall, video, r.call);
						}
					} else if (err != null && "GROUPCALL_INVALID".equalsIgnoreCase(err.text)) {
						showDialog(new CreateGroupCallSheet(getContext(), participants));
					} else if (err != null) {
						BulletinFactory.of(CallLogActivity.this).showForError(err);
					}
				}));
				progressDialog.setOnCancelListener(di -> getConnectionsManager().cancelRequest(reqId, true));
				progressDialog.showDelayed(600);
			}
		};
	}

	private void onGroupCallClick(View view) {
		Long tag = (Long) view.getTag();
		ChatObject.Call call = getMessagesController().getGroupCall(tag, false);
		lastCallChat = getMessagesController().getChat(tag);
		if (call != null) {
			VoIPHelper.startCall(lastCallChat, null, null, false, getParentActivity(), CallLogActivity.this, getAccountInstance());
		} else {
			waitingForCallChatId = tag;
			getMessagesController().loadFullChat(tag, 0, true);
		};
	}

	private void onClick(UItem item, View view, int position, float x, float y) {
		if (item.id == ID_SHOW_IN_MAIN_TABS) {
			setCallsTabVisible(true);
			BulletinFactory.of(CallLogActivity.this)
				.createSimpleBulletin(R.raw.contact_check, AndroidUtilities.replaceTags(getString(R.string.GroupCallTabWasShownTitle)), getString(R.string.UndoNoCaps), Bulletin.DURATION_PROLONG, true, () -> setCallsTabVisible(false))
				.setDuration(Bulletin.DURATION_PROLONG)
				.show();
		} else if (item.id == ID_CREATE_CALL) {
			openCreateCall();
		} else if (item.object instanceof CallLogRow) {
			CallLogRow row = (CallLogRow) item.object;
			if (actionBar.isActionModeShowed()) {
				addOrRemoveSelectedDialog(row.calls, (CallCell) view);
			} else if (row.call_id != 0 && !row.calls.isEmpty()) {
				final boolean video = row.video;
				final HashSet<Long> participants = new HashSet<>();
				for (TLRPC.User user : row.users) {
					participants.add(user.id);
				}

				final TLRPC.TL_inputGroupCallInviteMessage inputGroupCall = new TLRPC.TL_inputGroupCallInviteMessage();
				inputGroupCall.msg_id = row.calls.get(0).id;

				final AlertDialog progressDialog = new AlertDialog(getContext(), AlertDialog.ALERT_TYPE_SPINNER);

				final TL_phone.getGroupCall req = new TL_phone.getGroupCall();
				req.call = inputGroupCall;
				req.limit = getMessagesController().conferenceCallSizeLimit;
				final int reqId = getConnectionsManager().sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
					progressDialog.dismiss();
					if (res instanceof TL_phone.groupCall) {
						final TL_phone.groupCall r = (TL_phone.groupCall) res;
						getMessagesController().putUsers(r.users, false);
						getMessagesController().putChats(r.chats, false);
						if (r.participants.isEmpty()) {
							showDialog(new CreateGroupCallSheet(getContext(), participants));
						} else {
							VoIPHelper.joinConference(getParentActivity(), currentAccount, inputGroupCall, video, r.call);
						}
					} else if (err != null && "GROUPCALL_INVALID".equalsIgnoreCase(err.text)) {
						showDialog(new CreateGroupCallSheet(getContext(), participants));
					} else if (err != null) {
						BulletinFactory.of(CallLogActivity.this).showForError(err);
					}
				}));
				progressDialog.setOnCancelListener(di -> getConnectionsManager().cancelRequest(reqId, true));
				progressDialog.showDelayed(600);
			} else {
				Bundle args = new Bundle();
				args.putLong("user_id", MessageObject.getDialogId(row.calls.get(0)));
				args.putInt("message_id", row.calls.get(0).id);
				getNotificationCenter().postNotificationName(NotificationCenter.closeChats);
				presentFragment(new ChatActivity(args), needFinishFragment);
			}
		} else if (view instanceof GroupCallCell) {
			GroupCallCell cell = (GroupCallCell) view;
			Bundle args = new Bundle();
			args.putLong("chat_id", cell.currentChat.id);
			getNotificationCenter().postNotificationName(NotificationCenter.closeChats);
			presentFragment(new ChatActivity(args), needFinishFragment);
		}
	}

	private boolean onLongClick(UItem item, View view, int position, float x, float y) {
		if (item.object instanceof CallLogRow) {
			addOrRemoveSelectedDialog(((CallLogRow) item.object).calls, (CallCell) view);
			return true;
		}
		return false;
	}

	private boolean hideCallTabsHintWasShown = false;

	@Override
	public void onBecomeFullyVisible() {
		super.onBecomeFullyVisible();

		if (!hideCallTabsHintWasShown && getUserConfig().showCallsTab && MessagesController.getGlobalMainSettings().getInt("hidecallshint", 0) < 2) {
			hideCallTabsHintView = new HintView2(getContext(), HintView2.DIRECTION_TOP);
			hideCallTabsHintView.setDuration(3000);
			hideCallTabsHintView.setJoint(1, -(12 + 13));
			hideCallTabsHintView.setPadding(0, dp(4), 0, 0);
			hideCallTabsHintView.setText(AndroidUtilities.replaceTags(getString(R.string.TapToHideCallsTab)));
			// hideCallTabsHintView.setRounding(dp(12));
			contentView.addView(hideCallTabsHintView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 80, Gravity.TOP));
			hideCallTabsHintView.setTranslationY(AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight() - dp(16));
			hideCallTabsHintView.show();
			hideCallTabsHintWasShown = true;

			MessagesController.getGlobalMainSettings().edit().putInt("hidecallshint", MessagesController.getGlobalMainSettings().getInt("hidecallshint", 0) + 1).apply();
		}
	}

	@Override
	public ActionBar createActionBar(Context context) {
		ActionBar actionBar = super.createActionBar(context);
		actionBar.setUseContainerForTitles();
		actionBar.createAdditionalSubTitleOverlayContainer();
		actionBar.getAdditionalSubTitleOverlayContainer().setTranslationX(dp(4));
		actionBar.getAdditionalSubTitleOverlayContainer().setTranslationY(-dp(2));
		actionBar.getTitlesContainer().setTranslationX(dp(4));
		actionBar.setAddToContainer(false);
		return actionBar;
	}

	@SuppressLint("NotifyDataSetChanged")
    private void showDeleteAlert(boolean all) {
		AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());

		if (all) {
			builder.setTitle(getString(R.string.DeleteAllCalls));
			builder.setMessage(getString(R.string.DeleteAllCallsText));
		} else {
			builder.setTitle(getString(R.string.DeleteCalls));
			builder.setMessage(getString(R.string.DeleteSelectedCallsText));
		}
		final boolean[] checks = new boolean[]{false};
		FrameLayout frameLayout = new FrameLayout(getParentActivity());
		CheckBoxCell cell = new CheckBoxCell(getParentActivity(), 1);
		cell.setBackground(Theme.getSelectorDrawable(false));
		cell.setText(getString(R.string.DeleteCallsForEveryone), "", false, false);
		cell.setPadding(LocaleController.isRTL ? AndroidUtilities.dp(8) : 0, 0, LocaleController.isRTL ? 0 : AndroidUtilities.dp(8), 0);
		frameLayout.addView(cell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.TOP | Gravity.LEFT, 8, 0, 8, 0));
		cell.setOnClickListener(v -> {
			CheckBoxCell cell1 = (CheckBoxCell) v;
			checks[0] = !checks[0];
			cell1.setChecked(checks[0], true);
		});
		builder.setView(frameLayout);
		builder.setPositiveButton(getString(R.string.Delete), (dialogInterface, i) -> {
			if (all) {
				deleteAllMessages(checks[0]);
				calls.clear();
				loading = false;
				endReached = true;
				otherItem.setVisibility(View.GONE);
				listView.adapter.update(true);
			} else {
				getMessagesController().deleteMessages(new ArrayList<>(selectedIds), null, null, 0, 0, checks[0], 0);
			}
			hideActionMode(false);
		});
		builder.setNegativeButton(getString(R.string.Cancel), null);
		AlertDialog alertDialog = builder.create();
		showDialog(alertDialog);
		TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
		if (button != null) {
			button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
		}
	}

	private void deleteAllMessages(boolean revoke) {
		TLRPC.TL_messages_deletePhoneCallHistory req = new TLRPC.TL_messages_deletePhoneCallHistory();
		req.revoke = revoke;
		getConnectionsManager().sendRequest(req, (response, error) -> {
			if (response != null) {
				TLRPC.TL_messages_affectedFoundMessages res = (TLRPC.TL_messages_affectedFoundMessages) response;
				TLRPC.TL_updateDeleteMessages updateDeleteMessages = new TLRPC.TL_updateDeleteMessages();
				updateDeleteMessages.messages = res.messages;
				updateDeleteMessages.pts = res.pts;
				updateDeleteMessages.pts_count = res.pts_count;
				final TLRPC.TL_updates updates = new TLRPC.TL_updates();
				updates.updates.add(updateDeleteMessages);
				getMessagesController().processUpdates(updates, false);
				if (res.offset != 0) {
					deleteAllMessages(revoke);
				}
			}
		});
	}

	private void hideActionMode(boolean animated) {
		actionBar.hideActionMode();
		selectedIds.clear();
		for (int a = 0, N = listView.getChildCount(); a < N; a++) {
			View child = listView.getChildAt(a);
			if (child instanceof CallCell) {
				((CallCell) child).setChecked(false, animated);
			}
		}
		listView.adapter.update(true);
	}

	private boolean isSelected(ArrayList<TLRPC.Message> messages) {
		for (int a = 0, N = messages.size(); a < N; a++) {
			if (selectedIds.contains(messages.get(a).id)) {
				return true;
			}
		}
		return false;
	}

	private void createActionMode() {
		if (actionBar.actionModeIsExist(null)) {
			return;
		}
		final ActionBarMenu actionMode = actionBar.createActionMode();

		if (hasMainTabs) {
			actionModeCloseView = new ImageView(getContext());
			actionModeCloseView.setScaleType(ImageView.ScaleType.CENTER);
			actionModeCloseView.setImageDrawable(new BackDrawable(true));
			actionModeCloseView.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_actionBarActionModeDefaultIcon), PorterDuff.Mode.MULTIPLY));
			actionModeCloseView.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_actionBarActionModeDefaultSelector)));
			actionModeCloseView.setOnClickListener(v -> hideActionMode(true));
			actionMode.addView(actionModeCloseView, LayoutHelper.createLinear(54, 54, Gravity.CENTER_VERTICAL));
			actionModeViews.add(actionModeCloseView);
		}

		selectedDialogsCountTextView = new NumberTextView(actionMode.getContext());
		selectedDialogsCountTextView.setTextSize(18);
		selectedDialogsCountTextView.setTypeface(AndroidUtilities.bold());
		selectedDialogsCountTextView.setTextColor(Theme.getColor(Theme.key_actionBarActionModeDefaultIcon));
		actionMode.addView(selectedDialogsCountTextView, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, hasMainTabs ? 18 : 72, 0, 0, 0));
		selectedDialogsCountTextView.setOnTouchListener((v, event) -> true);

		actionModeViews.add(actionMode.addItemWithWidth(delete, R.drawable.msg_delete, AndroidUtilities.dp(54), getString(R.string.Delete)));
	}

	private boolean addOrRemoveSelectedDialog(ArrayList<TLRPC.Message> messages, CallCell cell) {
		if (messages.isEmpty()) {
			return false;
		}
		if (isSelected(messages)) {
			for (int a = 0, N = messages.size(); a < N; a++) {
				selectedIds.remove((Integer) messages.get(a).id);
			}
			cell.setChecked(false, true);
			showOrUpdateActionMode();
			return false;
		} else {
			for (int a = 0, N = messages.size(); a < N; a++) {
				Integer id = messages.get(a).id;
				if (!selectedIds.contains(id)) {
					selectedIds.add(id);
				}
			}
			cell.setChecked(true, true);
			showOrUpdateActionMode();
			return true;
		}
	}

	private void showOrUpdateActionMode() {
		boolean updateAnimated = false;
		if (actionBar.isActionModeShowed()) {
			if (selectedIds.isEmpty()) {
				hideActionMode(true);
				return;
			}
			updateAnimated = true;
		} else {
			createActionMode();
			actionBar.showActionMode();

			AnimatorSet animatorSet = new AnimatorSet();
			ArrayList<Animator> animators = new ArrayList<>();
			for (int a = 0; a < actionModeViews.size(); a++) {
				View view = actionModeViews.get(a);
				view.setPivotY(ActionBar.getCurrentActionBarHeight() / 2f);
				AndroidUtilities.clearDrawableAnimation(view);
				animators.add(ObjectAnimator.ofFloat(view, View.SCALE_Y, 0.1f, 1.0f));
			}
			animatorSet.playTogether(animators);
			animatorSet.setDuration(200);
			animatorSet.start();
		}
		selectedDialogsCountTextView.setNumber(selectedIds.size(), updateAnimated);
	}

	private void getCalls(int max_id, final int count) {
		if (loading) {
			return;
		}
		loading = true;
		if (emptyView != null && !firstLoaded) {
			emptyView.showProgress();
		}
		if (listView != null) {
			listView.adapter.update(true);
		}
		TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
		req.limit = count;
		req.peer = new TLRPC.TL_inputPeerEmpty();
		req.filter = new TLRPC.TL_inputMessagesFilterPhoneCalls();
		req.q = "";
		req.offset_id = max_id;
		int reqId = getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
			if (error == null) {
				final TLRPC.messages_Messages msgs = (TLRPC.messages_Messages) response;
				MessagesController.getInstance(currentAccount).putUsers(msgs.users, false);
				MessagesController.getInstance(currentAccount).putChats(msgs.chats, false);
				endReached = msgs.messages.isEmpty();
				CallLogRow currentRow = !calls.isEmpty() ? calls.get(calls.size() - 1) : null;
				for (int a = 0; a < msgs.messages.size(); a++) {
					TLRPC.Message msg = msgs.messages.get(a);
					if (msg.action == null || msg.action instanceof TLRPC.TL_messageActionHistoryClear) {
						continue;
					}
					long fromId = MessageObject.getFromChatId(msg);
					long userID = fromId == getUserConfig().getClientUserId() ? msg.peer_id.user_id : fromId;
					final Set<Long> userIds = new HashSet<>();
					int callType = MessageObject.getFromChatId(msg) == getUserConfig().getClientUserId() ? TYPE_OUT : TYPE_IN;
					if (msg.action instanceof TLRPC.TL_messageActionConferenceCall) {
						final TLRPC.TL_messageActionConferenceCall action = (TLRPC.TL_messageActionConferenceCall) msg.action;
						userIds.add(userID);
						userIds.addAll(action.other_participants.stream().map(DialogObject::getPeerDialogId).collect(Collectors.toSet()));
						if (callType == TYPE_IN && action.missed) {
							callType = TYPE_MISSED_IN;
						}
						if (callType == TYPE_OUT && action.missed) {
							callType = TYPE_MISSED_OUT;
						}

						CallLogRow sameRow = null;
						if (currentRow != null && currentRow.call_id == action.call_id) {
							sameRow = currentRow;
						} else for (int i = 0; i < calls.size(); ++i) {
							final CallLogRow row = calls.get(i);
							if (row.call_id == action.call_id) {
								sameRow = row;
								break;
							}
						}
						if (sameRow != null) {
							sameRow.calls.add(0, msg);
							for (long userId : userIds) {
								boolean contains = false;
								for (TLRPC.User user : sameRow.users) {
									if (userId == user.id) {
										contains = true;
										break;
									}
								}
								if (!contains) {
									final TLRPC.User user = getMessagesController().getUser(userId);
									if (user != null) {
										sameRow.users.add(user);
									}
								}
							}
						} else {
							if (currentRow != null && !calls.contains(currentRow)) {
								calls.add(currentRow);
							}
							final CallLogRow row = new CallLogRow();
							row.call_id = action.call_id;
							row.calls.clear();
							row.calls.add(msg);
							for (long userId : userIds) {
								if (row.users.stream().noneMatch(u -> u.id == userID)) {
									TLRPC.User user = getMessagesController().getUser(userId);
									if (user != null) {
										row.users.add(user);
									}
								}
							}
							row.type = callType;
							row.video = msg.action != null && msg.action.video;
							currentRow = row;
						}

					} else {
						userIds.add(userID);
						TLRPC.PhoneCallDiscardReason reason = msg.action.reason;
						if (callType == TYPE_IN && (reason instanceof TLRPC.TL_phoneCallDiscardReasonMissed || reason instanceof TLRPC.TL_phoneCallDiscardReasonBusy)) {
							callType = TYPE_MISSED_IN;
						}
						if (callType == TYPE_OUT && (reason instanceof TLRPC.TL_phoneCallDiscardReasonMissed || reason instanceof TLRPC.TL_phoneCallDiscardReasonBusy)) {
							callType = TYPE_MISSED_OUT;
						}

						if (currentRow == null || !eq(userIds, currentRow.users) || currentRow.type != callType) {
							if (currentRow != null && !calls.contains(currentRow)) {
								calls.add(currentRow);
							}
							final CallLogRow row = new CallLogRow();
							row.calls.clear();
							for (long userId : userIds) {
								if (row.users.stream().noneMatch(u -> u.id == userID)) {
									final TLRPC.User user = getMessagesController().getUser(userId);
									if (user != null) {
										row.users.add(user);
									}
								}
							}
							row.type = callType;
							row.video = msg.action != null && msg.action.video;
							currentRow = row;
						}
						currentRow.calls.add(msg);
					}
				}
				if (currentRow != null && !currentRow.calls.isEmpty() && !calls.contains(currentRow)) {
					calls.add(currentRow);
				}
			} else {
				endReached = true;
			}
			loading = false;
			if (!firstLoaded) {
				resumeDelayedFragmentAnimation();
			}
			firstLoaded = true;
			otherItem.setVisibility(calls.isEmpty() ? View.GONE : View.VISIBLE);
			if (emptyView != null) {
				emptyView.showTextView();
			}
			if (listView != null) {
				listView.adapter.update(true);
			}
		}), ConnectionsManager.RequestFlagFailOnServerErrors);
		getConnectionsManager().bindRequestToGuid(reqId, classGuid);
	}

	@Override
	public void onResume() {
		super.onResume();
		if (listView != null) {
			listView.adapter.update(true);
		}
	}

	@Override
	public void onRequestPermissionsResultFragment(int requestCode, String[] permissions, int[] grantResults) {
		if (requestCode == 101 || requestCode == 102 || requestCode == 103) {
			boolean allGranted = true;
			for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
			if (grantResults.length > 0 && allGranted) {
				if (requestCode == 103) {
					VoIPHelper.startCall(lastCallChat, null, null, false, getParentActivity(), CallLogActivity.this, getAccountInstance());
				} else {
					TLRPC.UserFull userFull = lastCallUser != null ? getMessagesController().getUserFull(lastCallUser.id) : null;
					VoIPHelper.startCall(lastCallUser, requestCode == 102, requestCode == 102 || userFull != null && userFull.video_calls_available, getParentActivity(), null, getAccountInstance());
				}
			} else {
				VoIPHelper.permissionDenied(getParentActivity(), null, requestCode);
			}
		}
	}

	private static class CallLogRow {
		public long call_id;
		public final ArrayList<TLRPC.User> users = new ArrayList<>();
		public final ArrayList<TLRPC.Message> calls = new ArrayList<>();
		public int type;
		public boolean video;
	}

	@Override
	public void onTransitionAnimationStart(boolean isOpen, boolean backward) {
		super.onTransitionAnimationStart(isOpen, backward);
		if (isOpen) {
			openTransitionStarted = true;
		}
	}

	@Override
	public boolean needDelayOpenAnimation() {
		return true;
	}

	@Override
	public boolean isSupportEdgeToEdge() {
		return true;
	}

	private int additionNavigationBarHeight;
	private int additionFloatingButtonOffset;
	private float additionalFloatingTranslation;
	private int navigationBarHeight;

	@Override
	public void onInsets(int left, int top, int right, int bottom) {
		navigationBarHeight = bottom;

		checkUi_listViewPadding();
		checkUi_floatingButton();
	}

	private void checkUi_floatingButton() {
        floatingButton.setTranslationY(-navigationBarHeight - additionFloatingButtonOffset - additionalFloatingTranslation);
	}

	private void checkUi_listViewPadding() {
		listView.setPadding(
			0,
			dp(ADDITIONAL_LIST_HEIGHT_DP) + actionBar.getMeasuredHeight() + (int) topPanelLayout.getAnimatedHeightWithPadding(dp(14)),
			0,
			dp(ADDITIONAL_LIST_HEIGHT_DP) + navigationBarHeight + additionNavigationBarHeight
		);

		emptyView.setPadding(0, 0, 0, navigationBarHeight + additionNavigationBarHeight);
	}


	private final Rect tmpClipRect = new Rect();
	private void checkUi_listClip() {
		if (listView.hasActiveEdgeEffects()) {
			listView.setClipBounds(null);
			return;
		}
		tmpClipRect.set(0, dp(ADDITIONAL_LIST_HEIGHT_DP) + actionBar.getMeasuredHeight(), listView.getMeasuredWidth(), listView.getMeasuredHeight() - dp(ADDITIONAL_LIST_HEIGHT_DP));
		listView.setClipBounds(tmpClipRect);
	}

	@Override
	public boolean canParentTabsSlide(MotionEvent ev, boolean forward) {
		return true;
	}

	@Override
	public boolean onBackPressed(boolean invoked) {
		if (actionBar.isActionModeShowed()) {
			if (invoked) hideActionMode(true);
			return false;
		}
		return super.onBackPressed(invoked);
	}

	@Override
	public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        ThemeDescription.ThemeDescriptionDelegate cellDelegate = () -> {
			if (listView != null) {
				int count = listView.getChildCount();
				for (int a = 0; a < count; a++) {
					View child = listView.getChildAt(a);
					if (child instanceof CallCell) {
						CallCell cell = (CallCell) child;
						cell.profileSearchCell.update(0);
					}
				}
			}

			if (actionModeCloseView != null) {
				actionModeCloseView.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_actionBarActionModeDefaultIcon), PorterDuff.Mode.MULTIPLY));
				actionModeCloseView.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_actionBarActionModeDefaultSelector)));
			}

			if (actionBar != null) {
				actionBar.updateColors();
			}
		};

		themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));

//		themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
		themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
		themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
		themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
		themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

		themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

		themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

		themeDescriptions.add(new ThemeDescription(emptyView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{EmptyTextProgressView.class}, new String[]{"emptyTextView1"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
		themeDescriptions.add(new ThemeDescription(emptyView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{EmptyTextProgressView.class}, new String[]{"emptyTextView2"}, null, null, null, Theme.key_emptyListPlaceholder));

		themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{LoadingCell.class}, new String[]{"progressBar"}, null, null, null, Theme.key_progressCircle));

		themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
		themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));

		if (floatingButton != null) {
			themeDescriptions.add(new ThemeDescription(floatingButton.imageView, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_chats_actionIcon));
			themeDescriptions.add(new ThemeDescription(floatingButton.imageView, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_chats_actionBackground));
			themeDescriptions.add(new ThemeDescription(floatingButton.imageView, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_chats_actionPressedBackground));
		}
		themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{CallCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_telegram_color_text));
		themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{CallCell.class}, null, new Drawable[]{Theme.dialogs_verifiedCheckDrawable}, null, Theme.key_chats_verifiedCheck));
		themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{CallCell.class}, null, new Drawable[]{Theme.dialogs_verifiedDrawable}, null, Theme.key_chats_verifiedBackground));
		themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{CallCell.class}, Theme.dialogs_offlinePaint, null, null, Theme.key_windowBackgroundWhiteGrayText3));
		themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{CallCell.class}, Theme.dialogs_onlinePaint, null, null, Theme.key_windowBackgroundWhiteBlueText3));
		themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{CallCell.class}, null, new Paint[]{Theme.dialogs_namePaint[0], Theme.dialogs_namePaint[1], Theme.dialogs_searchNamePaint}, null, null, Theme.key_chats_name));
		themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{CallCell.class}, null, new Paint[]{Theme.dialogs_nameEncryptedPaint[0], Theme.dialogs_nameEncryptedPaint[1], Theme.dialogs_searchNameEncryptedPaint}, null, null, Theme.key_chats_secretName));
		themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{CallCell.class}, null, Theme.avatarDrawables, null, Theme.key_avatar_text));
		themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundRed));
		themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundOrange));
		themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundViolet));
		themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundGreen));
		themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundCyan));
		themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundBlue));
		themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundPink));

		themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, null, new Drawable[]{greenDrawable, greenDrawable2, Theme.calllog_msgCallUpRedDrawable, Theme.calllog_msgCallDownRedDrawable}, null, Theme.key_windowBackgroundWhiteGrayText3));
		themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, null, new Drawable[]{redDrawable, redDrawable2, Theme.calllog_msgCallUpGreenDrawable, Theme.calllog_msgCallDownGreenDrawable}, null, Theme.key_fill_RedNormal));
		themeDescriptions.add(new ThemeDescription(flickerLoadingView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));

		themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));

		themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));

		return themeDescriptions;
	}

	public static void showCallLinkSheet(Context context, int currentAccount, TLRPC.InputGroupCall inputGroupCall, String link, Theme.ResourcesProvider resourcesProvider, boolean withJoinButton, boolean creator) {
		final BottomSheet.Builder b = new BottomSheet.Builder(context, false, resourcesProvider, Theme.getColor(Theme.key_dialogBackground, resourcesProvider));

		final String[] currentLink = new String[] { link };
		final LinearLayout linearLayout = new LinearLayout(context);
		linearLayout.setOrientation(LinearLayout.VERTICAL);
		linearLayout.setPadding(0, 0, 0, dp(8));

		final FrameLayout topView = new FrameLayout(context);
		topView.setClipChildren(false);
		topView.setClipToPadding(false);
		linearLayout.addView(topView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 92, Gravity.CENTER, 0, 0, 0, 0));

		final FrameLayout circle = new FrameLayout(context);
		ImageView imageView = new ImageView(context);
		imageView.setScaleType(ImageView.ScaleType.CENTER);
		imageView.setImageResource(R.drawable.story_link);
		imageView.setScaleX(2f);
		imageView.setScaleY(2f);
		circle.addView(imageView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));
		circle.setBackground(Theme.createCircleDrawable(dp(80), Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider)));
		topView.addView(circle, LayoutHelper.createFrame(80, 80, Gravity.CENTER_HORIZONTAL, 0, 12, 0, 0));

		final ImageView optionsView = new ImageView(context);
		optionsView.setScaleType(ImageView.ScaleType.CENTER);
		optionsView.setImageResource(R.drawable.ic_ab_other);
		optionsView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider), PorterDuff.Mode.SRC_IN));
		optionsView.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider)));
		if (creator) {
			topView.addView(optionsView, LayoutHelper.createFrame(56, 56, Gravity.RIGHT | Gravity.TOP, 0, 0, 0, 0));
		}

		LinkSpanDrawable.LinksTextView textView = TextHelper.makeLinkTextView(context, 20, Theme.key_windowBackgroundWhiteBlackText, true, resourcesProvider);
		textView.setText(getString(R.string.GroupCallCreatedLinkTitle));
		textView.setGravity(Gravity.CENTER);
		linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 32, 16, 32, 8));

		textView = TextHelper.makeLinkTextView(context, 14, Theme.key_windowBackgroundWhiteBlackText, false, resourcesProvider);
		textView.setText(getString(R.string.GroupCallCreatedLinkText));
		textView.setGravity(Gravity.CENTER);
		textView.setMaxWidth(HintView2.cutInFancyHalf(textView.getText(), textView.getPaint()));
		linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 32, 0, 32, 18));

		String formattedLink = link;
		if (formattedLink.startsWith("https://"))
			formattedLink = formattedLink.substring("https://".length());
		FrameLayout linkContainer = new FrameLayout(context);
		ScaleStateListAnimator.apply(linkContainer, .01f, 1.2f);
		linkContainer.setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider), Theme.blendOver(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider), Theme.getColor(Theme.key_listSelector, resourcesProvider)), 12, 12));
		linearLayout.addView(linkContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL, 16, 0, 16, 0));

		LinkSpanDrawable.LinksTextView linkText = TextHelper.makeLinkTextView(context, 13, Theme.key_windowBackgroundWhiteBlackText, false, resourcesProvider);
		linkText.setPadding(dp(16), dp(14), 0, dp(14));
		linkText.setText(formattedLink);
		linkContainer.addView(linkText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, 0, 0, 30, 0));

		ImageView linkOptionsView = new ImageView(context);
		linkOptionsView.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_ab_other));
		linkOptionsView.setContentDescription(LocaleController.getString(R.string.AccDescrMoreOptions));
		linkOptionsView.setScaleType(ImageView.ScaleType.CENTER);
		linkOptionsView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogTextGray3, resourcesProvider), PorterDuff.Mode.SRC_IN));
		linkContainer.addView(linkOptionsView, LayoutHelper.createFrame(40, 48, Gravity.RIGHT | Gravity.CENTER_VERTICAL));

		LinearLayout buttonsLayout = new LinearLayout(context);
		buttonsLayout.setOrientation(LinearLayout.HORIZONTAL);
		linearLayout.addView(buttonsLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16, 12, 16, 0));

		ButtonWithCounterView copyButton = new ButtonWithCounterView(context, resourcesProvider);
		SpannableStringBuilder sb = new SpannableStringBuilder("c ");
		sb.append(getString(R.string.GroupCallCreatedLinkCopy));
		sb.setSpan(new ColoredImageSpan(R.drawable.msg_copy_filled), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		copyButton.setText(sb, false);
		buttonsLayout.addView(copyButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 1, Gravity.LEFT | Gravity.TOP, 0, 0, 6, 0));

		ButtonWithCounterView shareButton = new ButtonWithCounterView(context, resourcesProvider);
		sb = new SpannableStringBuilder("c ");
		sb.append(getString(R.string.GroupCallCreatedLinkShare));
		sb.setSpan(new ColoredImageSpan(R.drawable.msg_share_filled), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		shareButton.setText(sb, false);
		buttonsLayout.addView(shareButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 1, Gravity.LEFT | Gravity.TOP, 6, 0, 0, 0));

		final BottomSheet[] _sheet = new BottomSheet[1];
		if (withJoinButton) {
			TextView or = new TextView(context) {
				private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
				@Override
				protected void dispatchDraw(@NonNull Canvas canvas) {
					paint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider), .8f));
					paint.setStyle(Paint.Style.STROKE);
					paint.setStrokeWidth(1);
					final float cy = getHeight() / 2f;
					int textWidth = 0;
					final Layout layout = getLayout();
					for (int i = 0; i < layout.getLineCount(); ++i) {
						textWidth = Math.max(textWidth, (int) layout.getLineWidth(i));
					}
					canvas.drawLine(0, cy, getWidth() / 2f - textWidth / 2f - dp(8), cy, paint);
					canvas.drawLine(getWidth() / 2f + textWidth / 2f + dp(8), cy, getWidth(), cy, paint);
					super.dispatchDraw(canvas);
				}
			};
			or.setGravity(Gravity.CENTER);
			or.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
			or.setText(" " + getString(R.string.GroupCallCreatedLinkJoinOr) + " ");
			or.setTextSize(14);
			linearLayout.addView(or, LayoutHelper.createLinear(190, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 28, 12, 28, 8));

			final Runnable join = () -> {
				final TLRPC.TL_inputGroupCallSlug inputGroupCallSlug = new TLRPC.TL_inputGroupCallSlug();
				final Uri uri = Uri.parse(link);
				inputGroupCallSlug.slug = uri.getPathSegments().get(uri.getPathSegments().size() - 1);
				VoIPHelper.joinConference(LaunchActivity.instance, currentAccount, inputGroupCallSlug, false, null);
				_sheet[0].dismiss();
			};
			textView = TextHelper.makeLinkTextView(context, 14, Theme.key_windowBackgroundWhiteBlackText, false, resourcesProvider);
			textView.setText(AndroidUtilities.replaceArrows(AndroidUtilities.replaceSingleTag(getString(R.string.GroupCallCreatedLinkJoinText), join), true));
			textView.setGravity(Gravity.CENTER);
			textView.setMaxWidth(HintView2.cutInFancyHalf(textView.getText(), textView.getPaint()));
			linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 32, 8, 32, 12));
			ScaleStateListAnimator.apply(textView, .05f, 1.2f);
			textView.setOnClickListener(v -> join.run());
		}

		b.setCustomView(linearLayout);
		BottomSheet sheet = _sheet[0] = b.show();

		linkContainer.setOnClickListener(v -> {
			AndroidUtilities.addToClipboard(currentLink[0]);

			BulletinFactory.of(sheet.topBulletinContainer, resourcesProvider)
				.createCopyBulletin(getString(R.string.LinkCopied))
				.show();
		});
		copyButton.setOnClickListener(v -> {
			AndroidUtilities.addToClipboard(currentLink[0]);

			BulletinFactory.of(sheet.topBulletinContainer, resourcesProvider)
				.createCopyBulletin(getString(R.string.LinkCopied))
				.show();
		});
		final Runnable revoke = () -> {
			final TL_phone.toggleGroupCallSettings req = new TL_phone.toggleGroupCallSettings();
			req.call = inputGroupCall;
			req.reset_invite_hash = true;
			ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> {
				if (res instanceof TLRPC.Updates) {
					MessagesController.getInstance(currentAccount).processUpdates((TLRPC.Updates) res, false);
				}

				final TL_phone.exportGroupCallInvite req2 = new TL_phone.exportGroupCallInvite();
				req2.call = inputGroupCall;
				ConnectionsManager.getInstance(currentAccount).sendRequest(req2, (res2, err2) -> AndroidUtilities.runOnUIThread(() -> {
					if (res2 instanceof TL_phone.exportedGroupCallInvite) {
						final TL_phone.exportedGroupCallInvite r = (TL_phone.exportedGroupCallInvite) res2;
						final String newLink = r.link;

						currentLink[0] = newLink;
						String formattedLink2 = newLink;
						if (formattedLink2.startsWith("https://"))
							formattedLink2 = formattedLink2.substring("https://".length());
						final String formatted = formattedLink2;

						ValueAnimator animator = ValueAnimator.ofFloat(0, 1).setDuration(220);
						AtomicBoolean changed = new AtomicBoolean();
						animator.addUpdateListener(animation -> {
							float val = (float) animation.getAnimatedValue();
							float scale = 0.5f * (2 - 1 / 5.f) + Math.abs(val - 0.5f) / 5.f;
							linkContainer.setScaleX(scale);
							linkContainer.setScaleY(scale);
							if (val >= 0.5f && !changed.get()) {
								changed.set(true);
								linkText.setText(formatted);
							}
						});
						animator.addListener(new AnimatorListenerAdapter() {
							@Override
							public void onAnimationEnd(Animator animation) {
								if (!changed.get()) {
									changed.set(true);
									linkText.setText(formatted);
								}
							}
						});
						animator.start();

						BulletinFactory.of(sheet.topBulletinContainer, resourcesProvider)
							.createSimpleBulletin(R.raw.linkbroken, getString(R.string.GroupCallCreatedLinkRevokedTitle), getString(R.string.GroupCallCreatedLinkRevokedText))
							.show();
					}
				}));
			});
		};
		linkOptionsView.setOnClickListener(v ->
			ItemOptions.makeOptions(sheet.container, resourcesProvider, linkContainer)
				.add(R.drawable.msg_copy, getString(R.string.Copy), () -> {
					AndroidUtilities.addToClipboard(currentLink[0]);

					BulletinFactory.of(sheet.topBulletinContainer, resourcesProvider)
						.createCopyBulletin(getString(R.string.LinkCopied))
						.show();
				})
				.add(R.drawable.msg_qrcode, getString(R.string.GetQRCode), () -> {
					QRCodeBottomSheet qrCodeBottomSheet = new QRCodeBottomSheet(
						context,
						LocaleController.getString(R.string.InviteByQRCode),
						currentLink[0],
						getString(R.string.QRCodeLinkGroupCall),
						false
					);
					qrCodeBottomSheet.setCenterAnimation(R.raw.qr_code_logo);
					qrCodeBottomSheet.show();
				})
				.addIf(creator, R.drawable.msg_delete, getString(R.string.RevokeLink), true, revoke)
				.show()
		);
		shareButton.setOnClickListener(v -> new ShareAlert(context, null, link, false, currentLink[0], false, resourcesProvider) {
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
                    str = formatString(R.string.InvLinkToChats, LocaleController.formatPluralString("Chats", dids == null ? 1 : dids.size()));
                }
                Bulletin b = BulletinFactory.of(sheet.topBulletinContainer, resourcesProvider).createSimpleBulletin(R.raw.forward, AndroidUtilities.replaceTags(str));
                b.hideAfterBottomSheet = false;
                b.show();
            }
        }.show());
		if (creator) {
			optionsView.setOnClickListener(v -> ItemOptions.makeOptions(sheet.getContainer(), resourcesProvider, optionsView)
                .add(R.drawable.menu_link_revoke, getString(R.string.GroupCallCreatedLinkRevoke), revoke)
                .setOnTopOfScrim()
                .translate(0, -dp(6))
                .setDimAlpha(0)
                .show());
		}
	}

	private void openCreateCall() {
		Bundle args = new Bundle();
		args.putBoolean("isCall", true);
		final GroupCreateActivity fragment = new GroupCreateActivity(args) {
			@Override
			protected void onCallUsersSelected(HashSet<Long> users, boolean video) {
				if (users.size() == 1) {
					final TLRPC.User user = getMessagesController().getUser(users.iterator().next());
					TLRPC.UserFull userFull = getMessagesController().getUserFull(user.id);
					if (userFull == null) {
						final TLRPC.TL_users_getFullUser req = new TLRPC.TL_users_getFullUser();
						req.id = getMessagesController().getInputUser(user.id);
						getConnectionsManager().sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
							TLRPC.UserFull newUserFull = null;
							if (res instanceof TLRPC.TL_users_userFull) {
								final TLRPC.TL_users_userFull r = (TLRPC.TL_users_userFull) res;
								MessagesController.getInstance(CallLogActivity.this.currentAccount).putUsers(r.users, false);
								MessagesController.getInstance(CallLogActivity.this.currentAccount).putChats(r.chats, false);
								newUserFull = r.full_user;
							}
							VoIPHelper.startCall(lastCallUser = user, video, newUserFull != null && newUserFull.video_calls_available, getParentActivity(), newUserFull, getAccountInstance());
						}));
						return;
					}
					VoIPHelper.startCall(lastCallUser = user, video, userFull != null && userFull.video_calls_available, getParentActivity(), userFull, getAccountInstance());
				} else {
					final TL_phone.createConferenceCall req = new TL_phone.createConferenceCall();
					req.random_id = Utilities.random.nextInt();
					ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
						if (res instanceof TLRPC.Updates) {
							final TLRPC.Updates updates = (TLRPC.Updates) res;
							MessagesController.getInstance(currentAccount).putUsers(updates.users, false);
							MessagesController.getInstance(currentAccount).putChats(updates.chats, false);

							TLRPC.GroupCall groupCall = null;
							for (TLRPC.TL_updateGroupCall u : findUpdatesAndRemove(updates, TLRPC.TL_updateGroupCall.class)) {
								groupCall = u.call;
							}

							if (LaunchActivity.instance == null) {
								return;
							}
							if (groupCall != null) {
								final TLRPC.TL_inputGroupCall inputGroupCall = new TLRPC.TL_inputGroupCall();
								inputGroupCall.id = groupCall.id;
								inputGroupCall.access_hash = groupCall.access_hash;
								VoIPHelper.joinConference(LaunchActivity.instance, currentAccount, inputGroupCall, video, groupCall, users);
							}
						} else if (res instanceof TL_phone.groupCall) {
							final TL_phone.groupCall r = (TL_phone.groupCall) res;
							MessagesController.getInstance(currentAccount).putUsers(r.users, false);
							MessagesController.getInstance(currentAccount).putChats(r.chats, false);
							if (LaunchActivity.instance == null) {
								return;
							}
							final TLRPC.TL_inputGroupCall inputGroupCall = new TLRPC.TL_inputGroupCall();
							inputGroupCall.id = r.call.id;
							inputGroupCall.access_hash = r.call.access_hash;
							VoIPHelper.joinConference(LaunchActivity.instance, currentAccount, inputGroupCall, video, r.call, users);
						} else if (err != null) {
							BulletinFactory.of(CallLogActivity.this)
								.showForError(err);
						}
					}));
				}
				finishFragment();
			}
		};
		presentFragment(fragment);
	}

	public static void createCallLink(Context context, int currentAccount, Theme.ResourcesProvider resourceProvider, Runnable done) {
		final AlertDialog progressDialog = new AlertDialog(context, AlertDialog.ALERT_TYPE_SPINNER);
		progressDialog.showDelayed(500);

		final TL_phone.createConferenceCall req = new TL_phone.createConferenceCall();
		req.random_id = Utilities.random.nextInt();
		ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
			if (res instanceof TLRPC.Updates) {
				TLRPC.Updates updates = (TLRPC.Updates) res;
				MessagesController.getInstance(currentAccount).putUsers(updates.users, false);
				MessagesController.getInstance(currentAccount).putChats(updates.chats, false);

				TLRPC.GroupCall groupCall = null;
				for (TLRPC.TL_updateGroupCall u : findUpdatesAndRemove(updates, TLRPC.TL_updateGroupCall.class)) {
					groupCall = u.call;
				}
				progressDialog.dismiss();
				if (groupCall != null) {
					final TLRPC.TL_inputGroupCall inputGroupCall = new TLRPC.TL_inputGroupCall();
					inputGroupCall.id = groupCall.id;
					inputGroupCall.access_hash = groupCall.access_hash;
					showCallLinkSheet(context, currentAccount, inputGroupCall, groupCall.invite_link, resourceProvider, true, true);

					AndroidUtilities.runOnUIThread(done);
				}
			} else if (res instanceof TL_phone.groupCall) {
				final TL_phone.groupCall r = (TL_phone.groupCall) res;
				MessagesController.getInstance(currentAccount).putUsers(r.users, false);
				MessagesController.getInstance(currentAccount).putChats(r.chats, false);

				final TL_phone.exportGroupCallInvite req2 = new TL_phone.exportGroupCallInvite();
				req2.call = new TLRPC.TL_inputGroupCall();
				req2.call.id = r.call.id;
				req2.call.access_hash = r.call.access_hash;
				ConnectionsManager.getInstance(currentAccount).sendRequest(req2, (res2, err2) -> AndroidUtilities.runOnUIThread(() -> {
					if (res2 instanceof TL_phone.exportedGroupCallInvite) {
						progressDialog.dismiss();

						final TL_phone.exportedGroupCallInvite r2 = (TL_phone.exportedGroupCallInvite) res2;
						showCallLinkSheet(context, currentAccount, req2.call, r2.link, resourceProvider, true, true);
					} else {
						progressDialog.dismiss();
					}

					AndroidUtilities.runOnUIThread(done);
				}));
			} else {
				progressDialog.dismiss();
				AndroidUtilities.runOnUIThread(done);
			}
		}));
	}

	private void showItemOptions() {
		ItemOptions io = ItemOptions.makeOptions(this, otherItem);
		// io.setColors(getThemedColor(Theme.key_actionBarDefaultTitle), getThemedColor(Theme.key_actionBarDefaultTitle));
		io.setDimAlpha(0x08);
		if (getUserConfig().showCallsTab) {
			io.add(R.drawable.msg_archive_hide, getString(R.string.HideCallTab), () -> {
				setCallsTabVisible(false);
				final BulletinFactory factory = hasMainTabs ? BulletinFactory.global() : BulletinFactory.of(CallLogActivity.this);
				factory.createSimpleBulletin(R.raw.contact_check, AndroidUtilities.replaceTags(getString(R.string.GroupCallTabWasHiddenTitle)), getString(R.string.UndoNoCaps), Bulletin.DURATION_PROLONG, true, () -> {
					setCallsTabVisible(true);
				}).setDuration(Bulletin.DURATION_PROLONG).show();
			});
		}
		io.add(R.drawable.msg_delete, getString(R.string.DeleteAllCalls), true, () -> showDeleteAlert(true));

		io.show();
		io.setTranslationY(-dp(64));
	}

	private void setCallsTabVisible(boolean visible) {
		if (visible == getUserConfig().showCallsTab) {
			return;
		}

		getUserConfig().setShowCallsTab(visible);
		listView.adapter.update(true);
		NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.callTabsVisibleToggled);
	}



	/* Blur */

	private final @Nullable DownscaleScrollableNoiseSuppressor scrollableViewNoiseSuppressor;
	private final @Nullable BlurredBackgroundSourceRenderNode iBlur3SourceGlassFrosted;
	private final @Nullable BlurredBackgroundSourceRenderNode iBlur3SourceGlass;
	private final @NonNull BlurredBackgroundSourceColor iBlur3SourceColor;
	private final @NonNull BlurredBackgroundDrawableViewFactory iBlur3FactoryLiquidGlass;

	private IBlur3Capture iBlur3Capture;
	private boolean iBlur3Invalidated;

	private final ArrayList<RectF> iBlur3Positions = new ArrayList<>();
	private final RectF iBlur3PositionActionBar = new RectF();
	private final RectF iBlur3PositionMainTabs = new RectF(); {
		iBlur3Positions.add(iBlur3PositionActionBar);
		iBlur3Positions.add(iBlur3PositionMainTabs);
	}

	private void blur3_InvalidateBlur() {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || scrollableViewNoiseSuppressor == null) {
			return;
		}

		final int additionalList = dp(48) + (int) topPanelLayout.getAnimatedHeightWithPadding(dp(7));
		final int mainTabBottom = fragmentView.getMeasuredHeight() - navigationBarHeight - dp(DialogsActivity.MAIN_TABS_MARGIN);
		final int mainTabTop = mainTabBottom - dp(DialogsActivity.MAIN_TABS_HEIGHT);

		iBlur3PositionActionBar.set(0, -additionalList, fragmentView.getMeasuredWidth(), actionBar.getMeasuredHeight() + additionalList);
		iBlur3PositionMainTabs.set(0, mainTabTop, fragmentView.getMeasuredWidth(), mainTabBottom);
		iBlur3PositionMainTabs.inset(0, LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS) ? 0 : -dp(48));

		scrollableViewNoiseSuppressor.setupRenderNodes(iBlur3Positions, hasMainTabs ? 2 : 1);
		scrollableViewNoiseSuppressor.invalidateResultRenderNodes(iBlur3Capture, fragmentView.getMeasuredWidth(), fragmentView.getMeasuredHeight());
	}

	@Override
	public BlurredBackgroundSourceRenderNode getGlassSource() {
		return iBlur3SourceGlass;
	}

	@Override
	public void onParentScrollToTop() {
		if (layoutManager.findFirstVisibleItemPosition() < 15) {
			listView.smoothScrollToPosition(0);
		} else {
			scrollHelper.setScrollDirection(RecyclerAnimationScrollHelper.SCROLL_DIRECTION_UP);
			scrollHelper.scrollToPosition(0, 0, false, true);
		}
	}
}
