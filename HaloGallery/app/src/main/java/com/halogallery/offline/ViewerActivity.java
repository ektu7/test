package com.halogallery.offline;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ViewerActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<MediaItem> session = new ArrayList<>();
    private int position = 0, requested = 0;
    private PhotoZoomView image;
    private TextView title, favorite, prev, next;
    private Uri currentUri;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        getWindow().getDecorView().setSystemUiVisibility(0);
        currentUri = getIntent().getData();
        if (currentUri == null) { finish(); return; }
        session.addAll(SelectionSession.photos);
        position = findPosition(currentUri);
        if (position < 0) {
            session.clear();
            session.add(new MediaItem(currentUri, getIntent().getStringExtra("title"),
                "", "", false, System.currentTimeMillis(), 0));
            position = 0;
        }
        buildUi();
        showPhoto();
    }

    private int findPosition(Uri uri) {
        for (int i = 0; i < session.size(); i++) if (uri.equals(session.get(i).uri)) return i;
        return -1;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF090909);
        setContentView(root);
        Ui.respectSystemBars(root);
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        Ui.pad(bar, this, 14, 8, 14, 8);
        root.addView(bar, new LinearLayout.LayoutParams(-1, Ui.dp(this, 66)));
        TextView back = darkButton("‹", 35);
        back.setContentDescription("Back to gallery");
        bar.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 48), -1));
        back.setOnClickListener(v -> finish());
        title = Ui.text(this, "", 15, Color.WHITE, true);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        bar.addView(title, new LinearLayout.LayoutParams(0, -1, 1));
        TextView share = darkButton("↗", 27);
        share.setContentDescription("Share photo");
        bar.addView(share, new LinearLayout.LayoutParams(Ui.dp(this, 48), -1));
        share.setOnClickListener(v -> shareCurrent());

        image = new PhotoZoomView(this);
        root.addView(image, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER);
        Ui.pad(bottom, this, 14, 6, 14, 16);
        root.addView(bottom, new LinearLayout.LayoutParams(-1, Ui.dp(this, 85)));
        prev = darkButton("‹", 32);
        prev.setContentDescription("Previous photo");
        bottom.addView(prev, new LinearLayout.LayoutParams(0, -1, 1));
        prev.setOnClickListener(v -> move(-1));
        favorite = darkButton("♡", 30);
        favorite.setContentDescription("Toggle favorite");
        bottom.addView(favorite, new LinearLayout.LayoutParams(0, -1, 1));
        favorite.setOnClickListener(v -> {
            Favorites.toggle(this, session.get(position).key());
            syncFavorite();
        });
        next = darkButton("›", 32);
        next.setContentDescription("Next photo");
        bottom.addView(next, new LinearLayout.LayoutParams(0, -1, 1));
        next.setOnClickListener(v -> move(1));
    }

    private TextView darkButton(String label, int size) {
        TextView button = Ui.text(this, label, size, Color.WHITE, false);
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        return button;
    }

    private void syncFavorite() {
        boolean selected = Favorites.contains(this, session.get(position).key());
        favorite.setText(selected ? "♥" : "♡");
        favorite.setTextColor(selected ? 0xFFFF718C : Color.WHITE);
    }

    private void move(int step) {
        int newPosition = position + step;
        if (newPosition < 0 || newPosition >= session.size()) return;
        position = newPosition;
        showPhoto();
    }

    private void showPhoto() {
        MediaItem item = session.get(position);
        currentUri = item.uri;
        title.setText(item.title);
        syncFavorite();
        prev.setAlpha(position == 0 ? .25f : 1f);
        next.setAlpha(position == session.size()-1 ? .25f : 1f);
        image.setBitmap(null);
        int id = ++requested;
        worker.submit(() -> {
            Bitmap bitmap = null;
            try {
                ImageDecoder.Source src = ImageDecoder.createSource(getContentResolver(), item.uri);
                bitmap = ImageDecoder.decodeBitmap(src, (decoder, info, source) -> {
                    int largest = Math.max(info.getSize().getWidth(), info.getSize().getHeight());
                    if (largest > 4096) decoder.setTargetSampleSize((int)Math.ceil((double)largest / 4096d));
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                });
            } catch (Exception | OutOfMemoryError ignored) { }
            Bitmap result = bitmap;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || id != requested) return;
                if (result == null) Toast.makeText(this, "Could not open this photo", Toast.LENGTH_SHORT).show();
                image.setBitmap(result);
            });
        });
    }

    private void shareCurrent() {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("image/*");
        share.putExtra(Intent.EXTRA_STREAM, currentUri);
        share.setClipData(ClipData.newUri(getContentResolver(), "Photo", currentUri));
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(share, "Share photo"));
    }

    @Override protected void onDestroy() {
        requested++;
        worker.shutdownNow();
        super.onDestroy();
    }
}
