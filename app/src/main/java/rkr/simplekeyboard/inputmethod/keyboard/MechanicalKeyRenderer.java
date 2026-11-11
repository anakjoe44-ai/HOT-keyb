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
import android.os.SystemClock;
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
    private static final String EFFECT_OFF = "off";
    private static final String EFFECT_STATIC = "static";
    private static final String EFFECT_RAINBOW = "rainbow";
    private static final String EFFECT_BREATHING = "breathing";
    private static final String EFFECT_GRADIENT = "gradient";
    private static final String EFFECT_WAVE = "wave";
    private static final String EFFECT_REACTIVE = "reactive";

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
    private String mRgbEffect = EFFECT_STATIC;
    private int mRgbBrightness = 75;
    private int mRgbSpeed = 100;
    private final float[] mHsv = new float[3];

    public MechanicalKeyRenderer(final DisplayMetrics metrics) {
        mDensity = metrics.density;
        mPaint.setStrokeJoin(Paint.Join.ROUND);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void update(final SharedPreferences preferences) {
        final String theme = preferences.getString(Settings.PREF_MECHANICAL_THEME, "obsidian_neon");
        mProfile = preferences.getString(Settings.PREF_KEYCAP_PROFILE, PROFILE_RAISED);
        mPalette = paletteFor(theme);
        mRgbEffect = preferences.getString(Settings.PREF_RGB_EFFECT, EFFECT_STATIC);
        mRgbBrightness = Math.max(0, Math.min(100,
                preferences.getInt(Settings.PREF_RGB_BRIGHTNESS, 75)));
        mRgbSpeed = Math.max(25, Math.min(200,
                preferences.getInt(Settings.PREF_RGB_SPEED, 100)));
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

    public boolean isAnimationActive() {
        return isEnabled() && (EFFECT_RAINBOW.equals(mRgbEffect)
                || EFFECT_BREATHING.equals(mRgbEffect) || EFFECT_WAVE.equals(mRgbEffect));
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

    public void drawKey(final Canvas canvas, final Key key, final int[] drawableState,
            final int keyboardWidth) {
        final float width = key.getWidth();
        final float height = key.getHeight();
        if (width <= dp(2.0f) || height <= dp(2.0f)) {
            return;
        }

        final boolean pressed = hasState(drawableState, android.R.attr.state_pressed);
        final boolean selected = hasState(drawableState, android.R.attr.state_active)
                || hasState(drawableState, android.R.attr.state_checked);
        final int rgbColor = getLightingColor(key, keyboardWidth, pressed, selected);
        final float depth = Math.min(mDepth, height * 0.18f);
        final float pressedDepth = pressed ? Math.min(depth, dp(0.9f)) : depth;
        final float pressOffset = depth - pressedDepth;
        final float radius = Math.min(mRadius, Math.min(width, height) * 0.22f);
        final int sideColor = selected ? blend(mPalette.side, rgbColor, 0.24f) : mPalette.side;
        final int faceColor = selected ? blend(mPalette.face, rgbColor, 0.13f)
                : (pressed ? mPalette.pressedFace : mPalette.face);
        final float edgeLighting = EFFECT_OFF.equals(mRgbEffect) ? 0.0f
                : (EFFECT_REACTIVE.equals(mRgbEffect) && !pressed && !selected ? 0.08f : 0.52f);
        final int edgeColor = blend(mPalette.border, rgbColor, edgeLighting);

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
        final int topHighlight = EFFECT_OFF.equals(mRgbEffect) ? mPalette.highlight
                : blend(mPalette.highlight, rgbColor, 0.2f);
        mPaint.setColor(withAlpha(topHighlight, pressed ? 72 : 116));
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

    private int getLightingColor(final Key key, final int keyboardWidth, final boolean pressed,
            final boolean selected) {
        final float brightness = mRgbBrightness / 100.0f;
        final float position = keyboardWidth <= 0 ? 0.0f
                : Math.max(0.0f, Math.min(1.0f, (float)key.getX() / keyboardWidth));
        if (EFFECT_OFF.equals(mRgbEffect)) {
            return mPalette.accent;
        }
        if (EFFECT_RAINBOW.equals(mRgbEffect)) {
            final float hue = (position * 360.0f
                    + SystemClock.uptimeMillis() * (mRgbSpeed / 100.0f) * 0.035f) % 360.0f;
            return applyBrightness(hsvColor(hue, 0.82f, 1.0f), brightness);
        }
        if (EFFECT_BREATHING.equals(mRgbEffect)) {
            final double phase = SystemClock.uptimeMillis() * (mRgbSpeed / 100.0f) * 0.0015;
            final float pulse = 0.25f + 0.75f * (0.5f + 0.5f * (float)Math.sin(phase));
            return applyBrightness(mPalette.accent, brightness * pulse);
        }
        if (EFFECT_GRADIENT.equals(mRgbEffect)) {
            return applyBrightness(hsvColor(190.0f + 130.0f * position, 0.78f, 1.0f), brightness);
        }
        if (EFFECT_WAVE.equals(mRgbEffect)) {
            final double phase = position * 6.283185307179586
                    - SystemClock.uptimeMillis() * (mRgbSpeed / 100.0f) * 0.004;
            final float pulse = 0.24f + 0.76f * (0.5f + 0.5f * (float)Math.sin(phase));
            final int waveColor = hsvColor(190.0f + 130.0f * position, 0.78f, 1.0f);
            return applyBrightness(waveColor, brightness * pulse);
        }
        if (EFFECT_REACTIVE.equals(mRgbEffect)) {
            return applyBrightness(mPalette.accent, brightness
                    * ((pressed || selected) ? 1.0f : 0.18f));
        }
        return applyBrightness(mPalette.accent, brightness);
    }

    private int hsvColor(final float hue, final float saturation, final float value) {
        mHsv[0] = hue;
        mHsv[1] = saturation;
        mHsv[2] = value;
        return Color.HSVToColor(mHsv);
    }

    private static int applyBrightness(final int color, final float brightness) {
        final float level = Math.max(0.0f, Math.min(1.0f, brightness));
        return Color.rgb(Math.round(Color.red(color) * level),
                Math.round(Color.green(color) * level), Math.round(Color.blue(color) * level));
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
