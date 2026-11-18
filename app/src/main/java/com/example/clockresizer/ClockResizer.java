package com.example.clockresizer;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

/** Long-press the clock -> edit mode: drag inside = move, drag an edge = resize that axis, double-tap = reset. Tap outside = done. */
public class ClockResizer {
    private final View clock;
    private final String key;
    private final SharedPreferences prefs;
    private float sx = 1f, sy = 1f, tx = 0f, ty = 0f;
    private Overlay overlay;

    ClockResizer(View clock, String key) {
        this.clock = clock;
        this.key = key;
        Context c = clock.getContext().createDeviceProtectedStorageContext(); // keyguard shows before first unlock
        prefs = c.getSharedPreferences("clock_resizer", Context.MODE_PRIVATE);
        sx = prefs.getFloat(key + "_sx", 1f); sy = prefs.getFloat(key + "_sy", 1f);
        tx = prefs.getFloat(key + "_tx", 0f); ty = prefs.getFloat(key + "_ty", 0f);
    }

    void install() {
        clock.setOnLongClickListener(v -> { enterEdit(); return true; });
        clock.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> apply());
        clock.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) { apply(); }
            @Override public void onViewDetachedFromWindow(View v) { exitEdit(); }
        });
        apply();
    }

    private void apply() {
        clock.setScaleX(sx); clock.setScaleY(sy);
        clock.setTranslationX(tx); clock.setTranslationY(ty);
        if (overlay != null) overlay.invalidate();
    }

    private void save() {
        prefs.edit().putFloat(key + "_sx", sx).putFloat(key + "_sy", sy)
                .putFloat(key + "_tx", tx).putFloat(key + "_ty", ty).apply();
    }

    private void enterEdit() {
        View root = clock.getRootView();
        if (overlay != null || !(root instanceof ViewGroup)) return;
        overlay = new Overlay(clock.getContext());
        ((ViewGroup) root).addView(overlay,
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void exitEdit() {
        if (overlay == null) return;
        ViewGroup p = (ViewGroup) overlay.getParent();
        if (p != null) p.removeView(overlay);
        overlay = null;
        save();
    }

    private class Overlay extends View {
        private static final int NONE = 0, MOVE = 1, RESIZE = 2;
        private final float slop = 40 * getResources().getDisplayMetrics().density;
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final GestureDetector gd;
        private int mode = NONE;
        private boolean nL, nR, nT, nB;
        private float dx0, dy0, sx0, sy0, tx0, ty0;

        Overlay(Context c) {
            super(c);
            line.setStyle(Paint.Style.STROKE); line.setColor(0xFFFFFFFF); line.setStrokeWidth(3f * getResources().getDisplayMetrics().density);
            fill.setColor(0xFFFFFFFF);
            gd = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
                @Override public boolean onDoubleTap(MotionEvent e) {
                    if (bounds().contains(e.getX(), e.getY())) { sx = sy = 1f; tx = ty = 0f; apply(); save(); }
                    return true;
                }
            });
        }

        private RectF bounds() {
            int[] c = new int[2], o = new int[2];
            clock.getLocationInWindow(c);
            getLocationInWindow(o);
            float l = c[0] - o[0], t = c[1] - o[1];
            return new RectF(l, t, l + clock.getWidth() * clock.getScaleX(), t + clock.getHeight() * clock.getScaleY());
        }

        @Override protected void onDraw(Canvas c) {
            RectF r = bounds();
            c.drawRoundRect(r, 24f, 24f, line);
            float hr = 7f * getResources().getDisplayMetrics().density;
            c.drawCircle(r.left, r.centerY(), hr, fill); c.drawCircle(r.right, r.centerY(), hr, fill);
            c.drawCircle(r.centerX(), r.top, hr, fill); c.drawCircle(r.centerX(), r.bottom, hr, fill);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            gd.onTouchEvent(e);
            getParent().requestDisallowInterceptTouchEvent(true);
            float x = e.getX(), y = e.getY();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    RectF r = bounds();
                    RectF big = new RectF(r); big.inset(-slop, -slop);
                    if (!big.contains(x, y)) { exitEdit(); return true; }
                    nL = Math.abs(x - r.left) < slop; nR = Math.abs(x - r.right) < slop;
                    nT = Math.abs(y - r.top) < slop;  nB = Math.abs(y - r.bottom) < slop;
                    mode = (nL || nR || nT || nB) ? RESIZE : MOVE;
                    dx0 = x; dy0 = y; sx0 = sx; sy0 = sy; tx0 = tx; ty0 = ty;
                    return true;
                }
                case MotionEvent.ACTION_MOVE: {
                    float dx = x - dx0, dy = y - dy0;
                    if (mode == MOVE) { tx = tx0 + dx; ty = ty0 + dy; }
                    else if (mode == RESIZE) {
                        float w = clock.getWidth() * sx0, h = clock.getHeight() * sy0;
                        if (nR) sx = clamp(sx0 * (1f + 2f * dx / w));
                        if (nL) sx = clamp(sx0 * (1f - 2f * dx / w));
                        if (nB) sy = clamp(sy0 * (1f + 2f * dy / h));
                        if (nT) sy = clamp(sy0 * (1f - 2f * dy / h));
                    }
                    apply();
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    mode = NONE; save(); return true;
            }
            return true;
        }

        private float clamp(float v) { return Math.max(0.3f, Math.min(4f, v)); }
    }
}
