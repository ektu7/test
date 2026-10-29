package com.halogallery.offline;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

/** Pinch to zoom, drag while zoomed, and double-tap to toggle zoom. */
final class PhotoZoomView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final ScaleGestureDetector scaling;
    private final GestureDetector gestures;
    private Bitmap bitmap;
    private float zoom = 1f, dx = 0f, dy = 0f;
    private float lastX, lastY;

    PhotoZoomView(Context context) {
        super(context);
        setBackgroundColor(0xFF090909);
        scaling = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector detector) {
                zoom = Math.max(1f, Math.min(6f, zoom * detector.getScaleFactor()));
                if (zoom <= 1.01f) { zoom = 1f; dx = dy = 0f; }
                constrain();
                invalidate();
                return true;
            }
        });
        gestures = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onDoubleTap(MotionEvent e) {
                if (zoom > 1f) { zoom = 1f; dx = dy = 0; }
                else zoom = 2.5f;
                constrain();
                invalidate();
                return true;
            }
        });
    }

    void setBitmap(Bitmap bitmap) {
        this.bitmap = bitmap;
        zoom = 1f;
        dx = dy = 0f;
        invalidate();
    }

    private void constrain() {
        if (bitmap == null || getWidth() < 1 || getHeight() < 1) return;
        float base = Math.min((float)getWidth() / bitmap.getWidth(),
                              (float)getHeight() / bitmap.getHeight());
        float overX = Math.max(0f, (bitmap.getWidth() * base * zoom - getWidth()) / 2f);
        float overY = Math.max(0f, (bitmap.getHeight() * base * zoom - getHeight()) / 2f);
        dx = Math.min(overX, Math.max(-overX, dx));
        dy = Math.min(overY, Math.max(-overY, dy));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bitmap == null || bitmap.isRecycled()) return;
        float base = Math.min((float)getWidth() / bitmap.getWidth(),
                              (float)getHeight() / bitmap.getHeight());
        float w = bitmap.getWidth() * base * zoom;
        float h = bitmap.getHeight() * base * zoom;
        float left = (getWidth() - w) / 2f + dx;
        float top = (getHeight() - h) / 2f + dy;
        canvas.drawBitmap(bitmap, null, new RectF(left, top, left + w, top + h), paint);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        scaling.onTouchEvent(e);
        gestures.onTouchEvent(e);
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_UP) {
            lastX = e.getX(); lastY = e.getY();
        } else if (action == MotionEvent.ACTION_MOVE && e.getPointerCount() == 1 && !scaling.isInProgress()) {
            float x = e.getX(), y = e.getY();
            if (zoom > 1f) {
                dx += x - lastX;
                dy += y - lastY;
                constrain();
                invalidate();
            }
            lastX = x; lastY = y;
        }
        return true;
    }
}
