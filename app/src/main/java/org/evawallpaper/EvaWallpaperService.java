package org.evawallpaper;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

/** Prototype Canvas : aucune ressource réseau, animation suspendue hors écran. */
public final class EvaWallpaperService extends WallpaperService {
    @Override public Engine onCreateEngine() { return new EvaEngine(); }

    private final class EvaEngine extends Engine {
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Runnable frame = this::drawFrame;
        private boolean visible;
        private boolean surfaceReady;

        @Override public void onVisibilityChanged(boolean isVisible) {
            visible = isVisible;
            scheduleFrame();
        }
        @Override public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            surfaceReady = true;
            scheduleFrame();
        }
        @Override public void onSurfaceDestroyed(SurfaceHolder holder) {
            surfaceReady = false;
            handler.removeCallbacks(frame);
            super.onSurfaceDestroyed(holder);
        }
        @Override public void onDestroy() {
            visible = false;
            handler.removeCallbacks(frame);
            super.onDestroy();
        }
        private void scheduleFrame() {
            handler.removeCallbacks(frame);
            if (visible && surfaceReady) handler.post(frame);
        }
        private void drawFrame() {
            if (!visible || !surfaceReady) return;
            SurfaceHolder holder = getSurfaceHolder();
            Canvas canvas = null;
            try {
                canvas = holder.lockCanvas();
                if (canvas != null) {
                    canvas.drawColor(Color.rgb(15, 12, 24));
                    float wave = (float) Math.sin(SystemClock.uptimeMillis() / 1600.0);
                    float radius = Math.min(canvas.getWidth(), canvas.getHeight()) * (0.22f + 0.03f * wave);
                    paint.setColor(Color.rgb(116, 67, 166));
                    canvas.drawCircle(canvas.getWidth() / 2f, canvas.getHeight() / 2f, radius, paint);
                    paint.setColor(Color.rgb(164, 231, 72));
                    float y = canvas.getHeight() * (0.5f + 0.25f * wave);
                    canvas.drawRect(0, y, canvas.getWidth(), y + 5, paint);
                }
            } finally {
                if (canvas != null) holder.unlockCanvasAndPost(canvas);
            }
            if (visible && surfaceReady) handler.postDelayed(frame, 50);
        }
    }
}
