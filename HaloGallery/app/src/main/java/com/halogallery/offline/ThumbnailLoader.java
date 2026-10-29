package com.halogallery.offline;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.LruCache;
import android.util.Size;
import android.widget.ImageView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.os.Handler;
import android.os.Looper;

final class ThumbnailLoader {
    private final Context context;
    private final ExecutorService io = Executors.newFixedThreadPool(3);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> cache;

    ThumbnailLoader(Context context) {
        this.context = context.getApplicationContext();
        int bytes = (int) Math.min(24 * 1024 * 1024L, Runtime.getRuntime().maxMemory() / 8);
        cache = new LruCache<String, Bitmap>(bytes) {
            @Override protected int sizeOf(String key, Bitmap image) { return image.getByteCount(); }
        };
    }

    void bind(ImageView target, MediaItem item) {
        String key = item.key();
        target.setTag(key);
        Bitmap cached = cache.get(key);
        target.setImageBitmap(cached);
        if (cached != null) return;
        io.execute(() -> {
            Bitmap bmp = null;
            try {
                bmp = context.getContentResolver().loadThumbnail(item.uri,
                        new Size(320, 320), null);
            } catch (Exception ignored) { }
            if (bmp == null) return;
            Bitmap result = bmp;
            main.post(() -> {
                cache.put(key, result);
                if (key.equals(target.getTag())) target.setImageBitmap(result);
            });
        });
    }

    void shutdown() { io.shutdownNow(); cache.evictAll(); }
}
