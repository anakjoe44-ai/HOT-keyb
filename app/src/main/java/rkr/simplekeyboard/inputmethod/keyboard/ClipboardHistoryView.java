/*
 * Copyright (C) 2026 Manus
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package rkr.simplekeyboard.inputmethod.keyboard;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

import rkr.simplekeyboard.inputmethod.R;
import rkr.simplekeyboard.inputmethod.compat.PreferenceManagerCompat;
import rkr.simplekeyboard.inputmethod.latin.ClipboardHistoryStore;
import rkr.simplekeyboard.inputmethod.latin.settings.Settings;

/** A compact clipboard manager rendered inside the keyboard window. */
public final class ClipboardHistoryView extends LinearLayout {
    public interface Listener {
        void onSaveCurrentClip();
        void onPasteEntry(ClipboardHistoryStore.Entry entry);
        void onCopyEntry(ClipboardHistoryStore.Entry entry);
        void onPinEntry(ClipboardHistoryStore.Entry entry, boolean pinned);
        void onDeleteEntry(ClipboardHistoryStore.Entry entry);
        void onClearHistory();
        void onCloseClipboardHistory();
        void onOpenClipboardSettings();
    }

    private static final int CLEAR_CONFIRM_DURATION_MS = 4000;

    private final MechanicalKeyRenderer mColors;
    private final LinearLayout mEntriesContainer;
    private final TextView mStatus;
    private final TextView mEmptyMessage;
    private final TextView mAddButton;
    private final TextView mClearButton;
    private Listener mListener;
    private boolean mClearConfirmationArmed;
    private final Runnable mResetClearConfirmation = new Runnable() {
        @Override
        public void run() {
            mClearConfirmationArmed = false;
            mClearButton.setText(R.string.clipboard_clear_all);
        }
    };

    public ClipboardHistoryView(final Context context, final AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ClipboardHistoryView(final Context context, final AttributeSet attrs,
            final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(VERTICAL);
        setPadding(dp(12), dp(10), dp(12), dp(8));
        mColors = new MechanicalKeyRenderer(context.getResources().getDisplayMetrics());
        mColors.update(PreferenceManagerCompat.getDeviceSharedPreferences(context));
        setBackgroundColor(mColors.getBoardColor());
        setElevation(dp(8));

        final LinearLayout header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        final TextView title = new TextView(context);
        title.setText(R.string.clipboard_panel_title);
        title.setTextColor(mColors.getTextColor());
        title.setTextSize(16);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        final LayoutParams titleParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f);
        header.addView(title, titleParams);
        final TextView close = makeButton(R.string.clipboard_done);
        close.setContentDescription(getResources().getString(R.string.clipboard_done));
        close.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View view) {
                final Listener listener = mListener;
                if (listener != null) {
                    listener.onCloseClipboardHistory();
                }
            }
        });
        header.addView(close, new LayoutParams(LayoutParams.WRAP_CONTENT, dp(38)));
        addView(header, new LayoutParams(LayoutParams.MATCH_PARENT, dp(42)));

        mStatus = new TextView(context);
        mStatus.setTextColor(mColors.getMutedTextColor());
        mStatus.setTextSize(12);
        mStatus.setPadding(0, dp(2), 0, dp(7));
        addView(mStatus, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        final LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        mAddButton = makeButton(R.string.clipboard_add_current);
        mAddButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View view) {
                final Listener listener = mListener;
                if (listener != null) {
                    listener.onSaveCurrentClip();
                }
            }
        });
        actions.addView(mAddButton, new LayoutParams(0, dp(40), 1.0f));
        mClearButton = makeButton(R.string.clipboard_clear_all);
        mClearButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View view) {
                if (!mClearConfirmationArmed) {
                    mClearConfirmationArmed = true;
                    mClearButton.setText(R.string.clipboard_clear_confirm);
                    removeCallbacks(mResetClearConfirmation);
                    postDelayed(mResetClearConfirmation, CLEAR_CONFIRM_DURATION_MS);
                    return;
                }
                removeCallbacks(mResetClearConfirmation);
                mClearConfirmationArmed = false;
                mClearButton.setText(R.string.clipboard_clear_all);
                final Listener listener = mListener;
                if (listener != null) {
                    listener.onClearHistory();
                }
            }
        });
        final LayoutParams clearParams = new LayoutParams(0, dp(40), 1.0f);
        clearParams.leftMargin = dp(6);
        actions.addView(mClearButton, clearParams);
        final TextView settingsButton = makeButton(R.string.clipboard_open_settings);
        settingsButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View view) {
                final Listener listener = mListener;
                if (listener != null) {
                    listener.onOpenClipboardSettings();
                }
            }
        });
        final LayoutParams settingsParams = new LayoutParams(0, dp(40), 1.0f);
        settingsParams.leftMargin = dp(6);
        actions.addView(settingsButton, settingsParams);
        addView(actions, new LayoutParams(LayoutParams.MATCH_PARENT, dp(44)));

        final ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        mEntriesContainer = new LinearLayout(context);
        mEntriesContainer.setOrientation(VERTICAL);
        scroll.addView(mEntriesContainer, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        addView(scroll, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1.0f));

        mEmptyMessage = new TextView(context);
        mEmptyMessage.setText(R.string.clipboard_empty);
        mEmptyMessage.setTextColor(mColors.getMutedTextColor());
        mEmptyMessage.setGravity(Gravity.CENTER);
        mEntriesContainer.addView(mEmptyMessage, new LayoutParams(
                LayoutParams.MATCH_PARENT, dp(72)));
    }

    public void setListener(final Listener listener) {
        mListener = listener;
    }

    public void showEntries(final List<ClipboardHistoryStore.Entry> entries,
            final boolean historyEnabled) {
        mStatus.setText(historyEnabled ? R.string.clipboard_panel_privacy
                : R.string.clipboard_panel_disabled);
        mAddButton.setEnabled(historyEnabled);
        mAddButton.setAlpha(historyEnabled ? 1.0f : 0.45f);
        mEntriesContainer.removeAllViews();
        mEmptyMessage.setVisibility(entries.isEmpty() ? VISIBLE : GONE);
        if (entries.isEmpty()) {
            mEntriesContainer.addView(mEmptyMessage, new LayoutParams(
                    LayoutParams.MATCH_PARENT, dp(72)));
        }
        for (final ClipboardHistoryStore.Entry entry : entries) {
            addEntryRow(entry);
        }
        mClearConfirmationArmed = false;
        removeCallbacks(mResetClearConfirmation);
        mClearButton.setText(R.string.clipboard_clear_all);
        mClearButton.setEnabled(!entries.isEmpty());
        mClearButton.setAlpha(entries.isEmpty() ? 0.45f : 1.0f);
    }

    private void addEntryRow(final ClipboardHistoryStore.Entry entry) {
        final LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(VERTICAL);
        row.setPadding(dp(8), dp(7), dp(8), dp(5));
        row.setBackground(createRowBackground());
        final LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = dp(5);

        final TextView text = new TextView(getContext());
        text.setText(entry.text);
        text.setTextColor(mColors.getTextColor());
        text.setTextSize(13);
        text.setMaxLines(2);
        text.setEllipsize(android.text.TextUtils.TruncateAt.END);
        text.setFocusable(true);
        text.setClickable(true);
        text.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View view) {
                final Listener listener = mListener;
                if (listener != null) {
                    listener.onPasteEntry(entry);
                }
            }
        });
        row.addView(text, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        final LinearLayout actions = new LinearLayout(getContext());
        actions.setOrientation(HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        addEntryAction(actions, R.string.clipboard_copy, new Runnable() {
            @Override
            public void run() {
                final Listener listener = mListener;
                if (listener != null) {
                    listener.onCopyEntry(entry);
                }
            }
        });
        addEntryAction(actions, entry.pinned ? R.string.clipboard_unpin : R.string.clipboard_pin,
                new Runnable() {
                    @Override
                    public void run() {
                        final Listener listener = mListener;
                        if (listener != null) {
                            listener.onPinEntry(entry, !entry.pinned);
                        }
                    }
                });
        addEntryAction(actions, R.string.clipboard_delete, new Runnable() {
            @Override
            public void run() {
                final Listener listener = mListener;
                if (listener != null) {
                    listener.onDeleteEntry(entry);
                }
            }
        });
        row.addView(actions, new LayoutParams(LayoutParams.MATCH_PARENT, dp(36)));
        mEntriesContainer.addView(row, rowParams);
    }

    private void addEntryAction(final LinearLayout actions, final int labelRes,
            final Runnable callback) {
        final TextView button = makeButton(labelRes);
        button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View view) {
                callback.run();
            }
        });
        final LayoutParams params = new LayoutParams(0, dp(32), 1.0f);
        params.leftMargin = dp(3);
        params.rightMargin = dp(3);
        actions.addView(button, params);
    }

    private TextView makeButton(final int labelRes) {
        final TextView button = new TextView(getContext());
        button.setText(labelRes);
        button.setTextColor(mColors.getAccentColor());
        button.setTextSize(12);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(5), dp(3), dp(5), dp(3));
        button.setBackground(createRowBackground());
        button.setFocusable(true);
        button.setClickable(true);
        return button;
    }

    private GradientDrawable createRowBackground() {
        final GradientDrawable background = new GradientDrawable();
        background.setColor(mColors.getBoardColor());
        background.setCornerRadius(dp(9));
        background.setStroke(dp(1), mColors.getBorderColor());
        return background;
    }

    private int dp(final int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(mResetClearConfirmation);
        mClearConfirmationArmed = false;
        super.onDetachedFromWindow();
    }
}
