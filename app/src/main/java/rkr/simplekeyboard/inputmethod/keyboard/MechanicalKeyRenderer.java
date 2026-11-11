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

import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.DisplayMetrics;

import rkr.simplekeyboard.inputmethod.latin.settings.Settings;

/**
 * Paints raised mechanical-style keycaps without allocating a bitmap per key.
 * All drawing state is retained and reused on the keyboard's UI thread.
 */
public final class MechanicalKeyRenderer {
    public static final String PROFILE_CLASSIC = "classic";
    public static final String PROFILE_RAISED = "raised";
    private static final String PROFILE_ROUNDED = "rounded";
    private static final String PROFILE_SCULPTED = "sculpted";

    private static final Palette OBSIDIAN = new Palette(
            0xFF111721, 0xFF263244, 0xFF151D2A, 0xFF46566B, 0xFF8BE9F2,
            0xFFEEF8FF, 0xFF9BAABD, 0xFF354459);
    private static final Palette CYBER_PURPLE = new Palette(
            0xFF15101D, 0xFF30223F, 0xFF1C1428, 0xFF59426D, 0xFFE0A7FF,
            0xFFFFF2FF, 0xFFC5AFD0, 0xFF432E56);
    private static final Palette EMERALD = new Palette(
            0xFF0B1712, 0xFF1A3328, 0xFF102219, 0xFF39634E, 0xFF59E5A6,
            0xFFE8FFF4, 0xFFA4C6B4, 0xFF244638);
    private static final Palette ARCTIC = new Palette(
            0xFF0A1722, 0xFF1C3448, 0xFF102333, 0xFF3B617C, 0xFF8EDFFF,
            0xFFEDF9FF, 0xFFA5BDCD, 0xFF29485F);
    private static final Palette CRIMSON = new Palette(
            0xFF1A0E13, 0xFF3B202A, 0xFF24131A, 0xFF70404F, 0xFFFF718A,
            0xFFFFF0F2, 0xFFD1A7B0, 0xFF512A37);
    private static final Palette TITANIUM = new Palette(
            0xFF1D252D, 0xFFCBD3DB, 0xFF8794A1, 0xFF627180, 0xFF1684B6,
            0xFF17232D, 0xFF485968, 0xFFAEBAC6);

    private final float mDensity;
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();
    private Palette mPalette = OBSIDIAN;
    private String mProfile = PROFILE_RAISED;
    private float mDepth;
    private float mRadius;
    private float mInset;
    private float mSideInset;

    public MechanicalKeyRenderer(final DisplayMetrics metrics) {
        mDensity = metrics.density;
        mPaint.setStrokeJoin(Paint.Join.ROUND);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void update(final SharedPreferences preferences) {
        final String theme = preferences.getString(Settings.PREF_MECHANICAL_THEME, "obsidian_neon");
        mProfile = preferences.getString(Settings.PREF_KEYCAP_PROFILE, PROFILE_RAISED);
        mPalette = paletteFor(theme);
        if (PROFILE_CLASSIC.equals(mProfile)) {
            mDepth = 0.0f;
            mRadius = dp(6.0f);
            mInset = dp(1.0f);
            mSideInset = 0.0f;
        } else if (PROFILE_ROUNDED.equals(mProfile)) {
            mDepth = dp(2.2f);
            mRadius = dp(7.0f);
            mInset = dp(1.0f);
            mSideInset = dp(0.5f);
        } else if (PROFILE_SCULPTED.equals(mProfile)) {
            mDepth = dp(4.2f);
            mRadius = dp(7.0f);
            mInset = dp(1.0f);
            mSideInset = dp(1.0f);
        } else {
            mProfile = PROFILE_RAISED;
            mDepth = dp(3.2f);
            mRadius = dp(5.5f);
            mInset = dp(1.0f);
            mSideInset = dp(0.6f);
        }
    }

    public boolean isEnabled() {
        return !PROFILE_CLASSIC.equals(mProfile);
    }

    public int getBoardColor() {
        return mPalette.board;
    }

    public int getTextColor() {
        return mPalette.text;
    }

    public int getMutedTextColor() {
        return mPalette.mutedText;
    }

    public int getAccentColor() {
        return mPalette.accent;
    }

    public int getBorderColor() {
        return mPalette.border;
    }

    public void applyTextColors(final rkr.simplekeyboard.inputmethod.keyboard.internal.KeyDrawParams params) {
        params.mTextColor = mPalette.text;
        params.mTextInactivatedColor = mPalette.mutedText;
        params.mTextShadowColor = Color.TRANSPARENT;
        params.mFunctionalTextColor = mPalette.accent;
        params.mHintLetterColor = mPalette.mutedText;
        params.mHintLabelColor = mPalette.mutedText;
        params.mShiftedLetterHintInactivatedColor = mPalette.mutedText;
        params.mShiftedLetterHintActivatedColor = mPalette.accent;
        params.mPreviewTextColor = mPalette.text;
    }

    public void drawKey(final Canvas canvas, final Key key, final int[] drawableState) {
        final float width = key.getWidth();
        final float height = key.getHeight();
        if (width <= dp(2.0f) || height <= dp(2.0f)) {
            return;
        }

        final boolean pressed = hasState(drawableState, android.R.attr.state_pressed);
        final boolean selected = hasState(drawableState, android.R.attr.state_active)
                || hasState(drawableState, android.R.attr.state_checked);
        final float depth = Math.min(mDepth, height * 0.18f);
        final float pressedDepth = pressed ? Math.min(depth, dp(0.9f)) : depth;
        final float pressOffset = depth - pressedDepth;
        final float radius = Math.min(mRadius, Math.min(width, height) * 0.22f);
        final int sideColor = selected ? blend(mPalette.side, mPalette.accent, 0.24f) : mPalette.side;
        final int faceColor = selected ? blend(mPalette.face, mPalette.accent, 0.13f)
                : (pressed ? mPalette.pressedFace : mPalette.face);
        final int edgeColor = selected ? blend(mPalette.border, mPalette.accent, 0.42f) : mPalette.border;

        mPaint.setShader(null);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(sideColor);
        mRect.set(mInset, mInset + depth, width - mInset, height - mInset);
        canvas.drawRoundRect(mRect, radius, radius, mPaint);

        final float left = mInset + mSideInset;
        final float right = width - mInset - mSideInset;
        final float top = mInset + pressOffset;
        final float bottom = Math.max(top + dp(1.0f), height - mInset - depth + pressOffset);
        mRect.set(left, top, right, bottom);
        mPaint.setColor(faceColor);
        canvas.drawRoundRect(mRect, radius, radius, mPaint);

        // Thin directional top sheen and a restrained lower inner edge sell the sculpted cap.
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(dp(0.7f));
        mPaint.setColor(withAlpha(mPalette.highlight, pressed ? 72 : 116));
        final float lineInset = Math.min(radius, (right - left) * 0.32f);
        canvas.drawLine(left + lineInset, top + dp(1.3f), right - lineInset,
                top + dp(1.3f), mPaint);
        mPaint.setColor(withAlpha(edgeColor, 105));
        canvas.drawLine(left + radius * 0.65f, bottom - dp(1.0f),
                right - radius * 0.65f, bottom - dp(1.0f), mPaint);

        mPaint.setStrokeWidth(dp(0.75f));
        mPaint.setColor(withAlpha(edgeColor, 220));
        mRect.inset(dp(0.4f), dp(0.4f));
        canvas.drawRoundRect(mRect, radius, radius, mPaint);
        mPaint.setStyle(Paint.Style.FILL);
    }

    private float dp(final float value) {
        return value * mDensity;
    }

    private static boolean hasState(final int[] states, final int state) {
        if (states == null) {
            return false;
        }
        for (final int value : states) {
            if (value == state) {
                return true;
            }
        }
        return false;
    }

    private static int withAlpha(final int color, final int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private static int blend(final int from, final int to, final float amount) {
        final float inverse = 1.0f - amount;
        return Color.rgb(
                Math.round(Color.red(from) * inverse + Color.red(to) * amount),
                Math.round(Color.green(from) * inverse + Color.green(to) * amount),
                Math.round(Color.blue(from) * inverse + Color.blue(to) * amount));
    }

    private static Palette paletteFor(final String id) {
        if ("cyber_purple".equals(id)) return CYBER_PURPLE;
        if ("emerald_matrix".equals(id)) return EMERALD;
        if ("arctic_blue".equals(id)) return ARCTIC;
        if ("crimson_pulse".equals(id)) return CRIMSON;
        if ("titanium_silver".equals(id)) return TITANIUM;
        return OBSIDIAN;
    }

    private static final class Palette {
        final int board;
        final int face;
        final int side;
        final int border;
        final int accent;
        final int text;
        final int mutedText;
        final int pressedFace;
        final int highlight;

        Palette(final int board, final int face, final int side, final int border,
                final int accent, final int text, final int mutedText, final int pressedFace) {
            this.board = board;
            this.face = face;
            this.side = side;
            this.border = border;
            this.accent = accent;
            this.text = text;
            this.mutedText = mutedText;
            this.pressedFace = pressedFace;
            this.highlight = blend(face, Color.WHITE, 0.34f);
        }
    }
}
