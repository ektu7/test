package com.halogallery.offline;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.PopupMenu;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

public final class VideoPlayerActivity extends Activity {
    private VideoView video;
    private SeekBar scrubber;
    private TextView play, positionLabel, lengthLabel, speed, favorite;
    private MediaPlayer player;
    private Uri uri;
    private int restorePosition = 0;
    private boolean tracking = false;
    private float playbackSpeed = 1f;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (video != null && !tracking) {
                int pos = Math.max(0, video.getCurrentPosition());
                int max = Math.max(1, video.getDuration());
                scrubber.setMax(max);
                scrubber.setProgress(Math.min(max, pos));
                positionLabel.setText(MediaRepository.duration(pos));
                lengthLabel.setText(MediaRepository.duration(max));
                updatePlay();
            }
            handler.postDelayed(this, 500);
        }
    };

    @Override public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        uri = getIntent().getData();
        if (uri == null) { finish(); return; }
        if (savedState != null) restorePosition = savedState.getInt("position", 0);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        getWindow().getDecorView().setSystemUiVisibility(0);
        buildUi();
        video.setVideoURI(uri);
        video.setOnPreparedListener(mp -> {
            player = mp;
            scrubber.setMax(Math.max(1, mp.getDuration()));
            lengthLabel.setText(MediaRepository.duration(mp.getDuration()));
            if (restorePosition > 0) video.seekTo(restorePosition);
            video.start();
            updatePlay();
        });
        video.setOnCompletionListener(mp -> {
            play.setText("↻");
            scrubber.setProgress(scrubber.getMax());
        });
        video.setOnErrorListener((mp, what, extra) -> {
            Toast.makeText(this, "This video's format may not be supported", Toast.LENGTH_LONG).show();
            return true;
        });
    }

    private TextView ctrl(String glyph, int size) {
        TextView t = Ui.text(this, glyph, size, Color.WHITE, true);
        t.setGravity(Gravity.CENTER);
        t.setClickable(true);
        t.setFocusable(true);
        return t;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);
        Ui.respectSystemBars(root);
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        Ui.pad(bar, this, 10, 4, 12, 4);
        root.addView(bar, new LinearLayout.LayoutParams(-1, Ui.dp(this, 65)));
        TextView back = ctrl("‹", 35);
        bar.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 50), -1));
        back.setContentDescription("Back to gallery");
        back.setOnClickListener(v -> finish());
        TextView title = Ui.text(this, getIntent().getStringExtra("title"), 16, Color.WHITE, true);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        bar.addView(title, new LinearLayout.LayoutParams(0, -1, 1));
        favorite = ctrl("♡", 30);
        favorite.setContentDescription("Toggle favorite video");
        bar.addView(favorite, new LinearLayout.LayoutParams(Ui.dp(this, 45), -1));
        syncFavorite();
        favorite.setOnClickListener(v -> { Favorites.toggle(this, uri.toString()); syncFavorite(); });
        TextView share = ctrl("↗", 25);
        share.setContentDescription("Share video");
        bar.addView(share, new LinearLayout.LayoutParams(Ui.dp(this, 45), -1));
        share.setOnClickListener(v -> shareVideo());

        FrameLayout stage = new FrameLayout(this);
        stage.setBackgroundColor(Color.BLACK);
        root.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1));
        video = new VideoView(this);
        stage.addView(video, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        stage.setOnClickListener(v -> togglePlayback());
        video.setOnClickListener(v -> togglePlayback());

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        Ui.pad(controls, this, 15, 12, 15, 24);
        controls.setBackgroundColor(0xFF161616);
        root.addView(controls, new LinearLayout.LayoutParams(-1, Ui.dp(this, 145)));
        LinearLayout timeline = new LinearLayout(this);
        timeline.setGravity(Gravity.CENTER_VERTICAL);
        controls.addView(timeline, new LinearLayout.LayoutParams(-1, Ui.dp(this, 43)));
        positionLabel = Ui.text(this, "0:00", 12, Color.WHITE, false);
        timeline.addView(positionLabel, new LinearLayout.LayoutParams(Ui.dp(this, 47), -1));
        scrubber = new SeekBar(this);
        scrubber.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFF8F96FF));
        scrubber.setThumbTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        timeline.addView(scrubber, new LinearLayout.LayoutParams(0, -1, 1));
        lengthLabel = Ui.text(this, "0:00", 12, 0xFFA5A5A5, false);
        lengthLabel.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        timeline.addView(lengthLabel, new LinearLayout.LayoutParams(Ui.dp(this, 47), -1));
        scrubber.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onStartTrackingTouch(SeekBar bar) { tracking = true; }
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) positionLabel.setText(MediaRepository.duration(progress));
            }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                video.seekTo(bar.getProgress()); tracking = false;
            }
        });

        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.CENTER);
        controls.addView(buttons, new LinearLayout.LayoutParams(-1, Ui.dp(this, 60)));
        TextView rewind = ctrl("−10", 17);
        buttons.addView(rewind, new LinearLayout.LayoutParams(0, -1, 1));
        rewind.setContentDescription("Back 10 seconds");
        rewind.setOnClickListener(v -> video.seekTo(Math.max(0, video.getCurrentPosition() - 10000)));
        play = ctrl("Ⅱ", 31);
        buttons.addView(play, new LinearLayout.LayoutParams(0, -1, 1));
        play.setContentDescription("Play or pause");
        play.setOnClickListener(v -> togglePlayback());
        TextView forward = ctrl("+10", 17);
        buttons.addView(forward, new LinearLayout.LayoutParams(0, -1, 1));
        forward.setContentDescription("Forward 10 seconds");
        forward.setOnClickListener(v -> video.seekTo(Math.min(Math.max(0,video.getDuration()), video.getCurrentPosition()+10000)));
        speed = ctrl("1×", 16);
        buttons.addView(speed, new LinearLayout.LayoutParams(0, -1, 1));
        speed.setContentDescription("Playback speed");
        speed.setOnClickListener(v -> showSpeedMenu());
    }

    private void syncFavorite() {
        boolean isFavorite = Favorites.contains(this, uri.toString());
        favorite.setText(isFavorite ? "♥" : "♡");
        favorite.setTextColor(isFavorite ? 0xFFFF718C : Color.WHITE);
    }

    private void updatePlay() { if (play != null) play.setText(video.isPlaying() ? "Ⅱ" : "▶"); }

    private void togglePlayback() {
        if (video.isPlaying()) video.pause();
        else video.start();
        updatePlay();
    }

    private void showSpeedMenu() {
        PopupMenu popup = new PopupMenu(this, speed);
        String[] options = {"0.5×", "1×", "1.5×", "2×"};
        float[] rates = {0.5f, 1f, 1.5f, 2f};
        for (String option : options) popup.getMenu().add(option);
        popup.setOnMenuItemClickListener(item -> {
            for (int i=0; i<options.length; i++) {
                if (!options[i].contentEquals(item.getTitle())) continue;
                try {
                    if (player != null) {
                        boolean wasPlaying = video.isPlaying();
                        player.setPlaybackParams(player.getPlaybackParams().setSpeed(rates[i]));
                        if (!wasPlaying) video.pause();
                    }
                    playbackSpeed = rates[i];
                    speed.setText(options[i]);
                } catch (IllegalStateException | IllegalArgumentException e) {
                    Toast.makeText(this, "Speed unavailable for this video", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void shareVideo() {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("video/*");
        share.putExtra(Intent.EXTRA_STREAM, uri);
        share.setClipData(ClipData.newUri(getContentResolver(), "Video", uri));
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(share, "Share video"));
    }

    @Override protected void onPause() {
        handler.removeCallbacks(ticker);
        if (video != null) video.pause();
        super.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        if (video != null) handler.post(ticker);
    }

    @Override protected void onSaveInstanceState(Bundle b) {
        super.onSaveInstanceState(b);
        if (video != null) b.putInt("position", video.getCurrentPosition());
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(ticker);
        if (video != null) video.stopPlayback();
        player = null;
        super.onDestroy();
    }
}
