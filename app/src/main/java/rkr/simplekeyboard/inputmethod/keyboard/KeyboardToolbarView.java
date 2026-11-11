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
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import rkr.simplekeyboard.inputmethod.R;
import rkr.simplekeyboard.inputmethod.compat.PreferenceManagerCompat;
import rkr.simplekeyboard.inputmethod.latin.settings.Settings;

/** Responsive, horizontally scrollable quick-action strip above the key rows. */
public final class KeyboardToolbarView extends HorizontalScrollView {
    public static final String ACTION_PASTE = "paste";
    public static final String ACTION_CLIPBOARD = "clipboard";
    public static final String ACTION_SYMBOLS = "symbols";
    public static final String ACTION_SETTINGS = "settings";

    public interface ActionListener {
        void onToolbarAction(String action);
    }

    private static final String[] ACTION_ORDER = {
            ACTION_PASTE, ACTION_CLIPBOARD, ACTION_SYMBOLS, ACTION_SETTINGS
    };
    private static final String[] ACTION_GLYPHS = {"↧", "▤", "123", "⚙"};
    private static final int TOOLBAR_HEIGHT_DP = 52;
    private static final int CHIP_WIDTH_DP = 72;
    private static final int CHIP_GAP_DP = 6;

    private final SharedPreferences mPreferences;
    private final SharedPreferences.OnSharedPreferenceChangeListener mPreferenceListener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(final SharedPreferences preferences,
                        final String key) {
                    if (Settings.PREF_TOOLBAR_TOOLS.equals(key)
                            || Settings.PREF_TOOLBAR_FIRST_TOOL.equals(key)
                            || Settings.PREF_MECHANICAL_THEME.equals(key)
                            || Settings.PREF_KEYCAP_PROFILE.equals(key)) {
                        rebuild();
                    }
                }
            };
    private final MechanicalKeyRenderer mColors;
    private ActionListener mActionListener;

    public KeyboardToolbarView(final Context context, final AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public KeyboardToolbarView(final Context context, final AttributeSet attrs,
            final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setHorizontalScrollBarEnabled(false);
        setFillViewport(true);
        mPreferences = PreferenceManagerCompat.getDeviceSharedPreferences(context);
        mColors = new MechanicalKeyRenderer(context.getResources().getDisplayMetrics());
        rebuild();
    }

    public void setActionListener(final ActionListener listener) {
        mActionListener = listener;
    }

    private void rebuild() {
        mColors.update(mPreferences);
        setBackgroundColor(mColors.getBoardColor());

        final Set<String> selected = mPreferences.getStringSet(Settings.PREF_TOOLBAR_TOOLS, null);
        final Set<String> enabled = selected == null
                ? new HashSet<>(Arrays.asList(ACTION_ORDER)) : new HashSet<>(selected);
        final ArrayList<String> ordered = new ArrayList<>();
        final String first = mPreferences.getString(Settings.PREF_TOOLBAR_FIRST_TOOL, ACTION_PASTE);
        if (enabled.contains(first) && isKnownAction(first)) {
            ordered.add(first);
        }
        for (final String action : ACTION_ORDER) {
            if (enabled.contains(action) && !ordered.contains(action)) {
                ordered.add(action);
            }
        }

        final String[] labels = getResources().getStringArray(R.array.toolbar_tool_names);
        setVisibility(ordered.isEmpty() ? View.GONE : View.VISIBLE);
        removeAllViews();
        final LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(4), dp(8), dp(4));
        addView(row, new HorizontalScrollView.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));

        for (int i = 0; i < ordered.size(); i++) {
            final String action = ordered.get(i);
            final int actionIndex = indexOfAction(action);
            final TextView chip = new TextView(getContext());
            chip.setText(ACTION_GLYPHS[actionIndex] + "\n" + labels[actionIndex]);
            chip.setTextSize(11.0f);
            chip.setTypeface(android.graphics.Typeface.DEFAULT,
                    android.graphics.Typeface.BOLD);
            chip.setGravity(Gravity.CENTER);
            chip.setIncludeFontPadding(false);
            chip.setMinWidth(dp(CHIP_WIDTH_DP));
            chip.setPadding(dp(7), dp(3), dp(7), dp(3));
            chip.setTextColor(mColors.getTextColor());
            chip.setContentDescription(labels[actionIndex]);
            chip.setBackground(createChipBackground());
            chip.setFocusable(true);
            chip.setClickable(true);
            chip.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(final View view) {
                    final ActionListener listener = mActionListener;
                    if (listener != null) {
                        listener.onToolbarAction(action);
                    }
                }
            });
            final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    dp(CHIP_WIDTH_DP), dp(TOOLBAR_HEIGHT_DP - 8));
            if (i > 0) {
                params.leftMargin = dp(CHIP_GAP_DP);
            }
            row.addView(chip, params);
        }
        invalidate();
    }

    private GradientDrawable createChipBackground() {
        final GradientDrawable background = new GradientDrawable();
        background.setColor(mColors.getBoardColor());
        background.setCornerRadius(dp(10));
        background.setStroke(dp(1), mColors.getBorderColor());
        return background;
    }

    private static boolean isKnownAction(final String action) {
        return indexOfAction(action) >= 0;
    }

    private static int indexOfAction(final String action) {
        for (int i = 0; i < ACTION_ORDER.length; i++) {
            if (ACTION_ORDER[i].equals(action)) {
                return i;
            }
        }
        return -1;
    }

    private int dp(final int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mPreferences.registerOnSharedPreferenceChangeListener(mPreferenceListener);
        rebuild();
    }

    @Override
    protected void onDetachedFromWindow() {
        mPreferences.unregisterOnSharedPreferenceChangeListener(mPreferenceListener);
        super.onDetachedFromWindow();
    }
}
