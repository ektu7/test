package com.halogallery.offline;

import android.app.Activity;
import android.app.PictureInPictureParams;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Rational;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.media.AudioManager;
import android.view.WindowManager;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

/** Samsung-inspired controls, native offline video playback and background audio. */
public final class VideoPlayerActivity extends Activity {
    private PlaybackService playback;
    private boolean bound;
    private boolean surfaceReady;
    private SurfaceView surface;
    private SeekBar scrubber;
    private TextView play, positionLabel, lengthLabel, speed, favorite, repeat, status, titleView, gestureHint;
    private LinearLayout header, controls;
    private Uri uri;
    private String currentTitle;
    private boolean tracking;
    private final ExecutorService libraryWorker = Executors.newSingleThreadExecutor();
    private final List<MediaItem> playlist = new ArrayList<>();
    private int playlistIndex = -1;
    private int gestureMode = 0; // 0 none, 1 horizontal seek, 2 brightness, 3 volume
    private int dragStartPosition, dragTargetPosition, initialVolume, maxVolume;
    private float initialBrightness;
    private AudioManager audio;
    private boolean scrubbingGesture;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (playback != null) {
                int pos = playback.position();
                int duration = playback.duration();
                if (!tracking) {
                    scrubber.setMax(Math.max(1, duration));
                    scrubber.setProgress(Math.min(duration, pos));
                    positionLabel.setText(MediaRepository.duration(pos));
                }
                lengthLabel.setText(MediaRepository.duration(duration));
                play.setText(playback.isPlaying() ? "Ⅱ" : "▶");
                play.setContentDescription(playback.isPlaying() ? "Pause" : "Play");
                speed.setText(String.format(java.util.Locale.ROOT,"%s×", trimRate(playback.speed())));
                repeat.setText(playback.isLooping() ? "↻ On" : "↻ Off");
                status.setText(playback.isPreparing() ? "Loading video…" : playback.isReady()
                        ? "Audio keeps playing with screen off" : "Tap play to start");
            }
            handler.postDelayed(this, 500);
        }
    };

    private static String trimRate(float f) {
        String s = String.format(java.util.Locale.ROOT, "%.2f", f);
        while (s.endsWith("0")) s = s.substring(0,s.length()-1);
        if (s.endsWith(".")) s = s.substring(0,s.length()-1);
        return s;
    }

    private final ServiceConnection conn = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            playback = ((PlaybackService.LocalBinder) binder).getService();
            if (surfaceReady) playback.attach(surface.getHolder());
            handler.removeCallbacks(ticker);
            handler.post(ticker);
        }
        @Override public void onServiceDisconnected(ComponentName name) { playback = null; }
    };

    @Override public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        uri = getIntent().getData();
        if (uri == null) { finish(); return; }
        currentTitle = getIntent().getStringExtra("title");
        if (currentTitle == null || currentTitle.isEmpty()) currentTitle = uri.getLastPathSegment() == null ? "Video" : uri.getLastPathSegment();
        audio = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        buildUi();
        loadPlaylist();
        startAndBind();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        Uri newUri = intent.getData();
        if (newUri != null && !newUri.equals(uri)) {
            uri = newUri;
            currentTitle = intent.getStringExtra("title");
            if (currentTitle == null || currentTitle.isEmpty()) currentTitle = uri.getLastPathSegment();
            if (titleView != null) titleView.setText(currentTitle);
            syncFavorite();
            loadPlaylist();
            startPlayback();
        }
    }

    private void startAndBind() {
        startPlayback();
        bound = bindService(new Intent(this, PlaybackService.class), conn, Context.BIND_AUTO_CREATE);
    }
    private void startPlayback() {
        Intent start = new Intent(this, PlaybackService.class);
        start.setAction(PlaybackService.ACTION_START).setData(uri)
                .putExtra(PlaybackService.EXTRA_TITLE, currentTitle);
        try { startForegroundService(start); }
        catch (RuntimeException ex) {
            Toast.makeText(this, "Video playback could not start", Toast.LENGTH_LONG).show();
        }
    }

    private TextView ctrl(String text, int textSize) {
        TextView t = Ui.text(this, text, textSize, Color.WHITE, true);
        t.setGravity(Gravity.CENTER);
        t.setClickable(true);
        t.setFocusable(true);
        return t;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF050505);
        setContentView(root);
        Ui.respectSystemBars(root);

        header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Ui.pad(header, this, 9, 4, 9, 4);
        root.addView(header, new LinearLayout.LayoutParams(-1, Ui.dp(this, 60)));
        TextView back = ctrl("‹", 36);
        back.setContentDescription("Back to videos");
        header.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 50), -1));
        back.setOnClickListener(v -> finish());
        titleView = Ui.text(this, currentTitle, 16, Color.WHITE, true);
        titleView.setSingleLine(true);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titleView.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(titleView, new LinearLayout.LayoutParams(0, -1, 1));
        favorite = ctrl("♡", 27);
        favorite.setContentDescription("Favorite");
        header.addView(favorite, new LinearLayout.LayoutParams(Ui.dp(this, 44), -1));
        syncFavorite();
        favorite.setOnClickListener(v -> { Favorites.toggle(this, uri.toString()); syncFavorite(); });
        TextView share = ctrl("↗", 25);
        share.setContentDescription("Share video");
        header.addView(share, new LinearLayout.LayoutParams(Ui.dp(this, 44), -1));
        share.setOnClickListener(v -> shareVideo());

        FrameLayout stage = new FrameLayout(this);
        stage.setBackgroundColor(Color.BLACK);
        root.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1));
        surface = new SurfaceView(this);
        stage.addView(surface, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        surface.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override public void surfaceCreated(SurfaceHolder h) { surfaceReady = true; if (playback != null) playback.attach(h); }
            @Override public void surfaceChanged(SurfaceHolder h, int format, int width, int height) { }
            @Override public void surfaceDestroyed(SurfaceHolder h) { surfaceReady = false; if (playback != null) playback.detach(h); }
        });
        gestureHint = Ui.text(this, "", 18, Color.WHITE, true);
        gestureHint.setGravity(Gravity.CENTER);
        gestureHint.setBackground(Ui.bg(0xAA141822, 22, this));
        gestureHint.setVisibility(View.GONE);
        FrameLayout.LayoutParams hintParams = new FrameLayout.LayoutParams(Ui.dp(this, 176), Ui.dp(this, 56), Gravity.CENTER);
        stage.addView(gestureHint, hintParams);
        GestureDetector gesture = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) {
                gestureMode = 0;
                scrubbingGesture = false;
                dragStartPosition = playback == null ? 0 : playback.position();
                dragTargetPosition = dragStartPosition;
                WindowManager.LayoutParams w = getWindow().getAttributes();
                initialBrightness = w.screenBrightness < 0 ? 0.55f : w.screenBrightness;
                if (audio != null) {
                    initialVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
                    maxVolume = Math.max(1, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
                }
                return true;
            }
            @Override public boolean onSingleTapConfirmed(MotionEvent e) {
                if (playback != null && !scrubbingGesture) playback.toggle(); return true;
            }
            @Override public boolean onDoubleTap(MotionEvent e) {
                if (playback != null) {
                    int jump = e.getX() < surface.getWidth() / 2f ? -10000 : 10000;
                    playback.skip(jump);
                    showGestureHint(jump < 0 ? "−10 seconds" : "+10 seconds");
                    handler.postDelayed(() -> { if (gestureHint != null && gestureMode == 0) gestureHint.setVisibility(View.GONE); }, 700);
                }
                return true;
            }
            @Override public boolean onScroll(MotionEvent start, MotionEvent current, float dx, float dy) {
                if (start == null || current == null) return false;
                float horizontal = current.getX() - start.getX();
                float vertical = current.getY() - start.getY();
                if (gestureMode == 0 && Math.max(Math.abs(horizontal), Math.abs(vertical)) >= Ui.dp(VideoPlayerActivity.this, 22)) {
                    gestureMode = Math.abs(horizontal) > Math.abs(vertical) * 1.15f ? 1
                            : (start.getX() < surface.getWidth() / 2f ? 2 : 3);
                    scrubbingGesture = true;
                }
                if (gestureMode == 1 && playback != null) {
                    // One full-width swipe seeks 90 seconds; show the exact new timestamp.
                    long change = Math.round(horizontal / Math.max(1f, surface.getWidth()) * 90000.0);
                    int dur = playback.duration();
                    dragTargetPosition = (int)Math.max(0, Math.min(dur, (long)dragStartPosition + change));
                    showGestureHint((change < 0 ? "−" : "+") + MediaRepository.duration(Math.abs(change))
                            + "  ·  " + MediaRepository.duration(dragTargetPosition));
                } else if (gestureMode == 2) {
                    WindowManager.LayoutParams lp = getWindow().getAttributes();
                    float b = Math.max(0.03f, Math.min(1f, initialBrightness - vertical / Math.max(1f, surface.getHeight())));
                    lp.screenBrightness = b; getWindow().setAttributes(lp);
                    showGestureHint("Brightness " + Math.round(b * 100) + "%");
                } else if (gestureMode == 3 && audio != null) {
                    int volume = Math.max(0, Math.min(maxVolume, Math.round(initialVolume - vertical / Math.max(1f,surface.getHeight()) * maxVolume)));
                    try { audio.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0); } catch (SecurityException ignored) { }
                    showGestureHint("Volume " + Math.round(100f * volume / maxVolume) + "%");
                }
                return true;
            }
        });
        surface.setOnTouchListener((v,event) -> {
            boolean consumed = gesture.onTouchEvent(event);
            if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                if (gestureMode == 1 && playback != null && event.getActionMasked() == MotionEvent.ACTION_UP) {
                    playback.seekTo(dragTargetPosition);
                }
                if (gestureMode != 0) { gestureHint.setVisibility(View.GONE); gestureMode=0; }
                return true;
            }
            return consumed;
        });

        controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setBackgroundColor(0xFF13151A);
        Ui.pad(controls, this, 16, 9, 16, 12);
        root.addView(controls, new LinearLayout.LayoutParams(-1, Ui.dp(this, 188)));
        LinearLayout timeline = new LinearLayout(this);
        timeline.setGravity(Gravity.CENTER_VERTICAL);
        controls.addView(timeline, new LinearLayout.LayoutParams(-1, Ui.dp(this, 44)));
        positionLabel = Ui.text(this, "0:00", 12, Color.WHITE, false);
        positionLabel.setGravity(Gravity.CENTER_VERTICAL);
        timeline.addView(positionLabel, new LinearLayout.LayoutParams(Ui.dp(this, 49), -1));
        scrubber = new SeekBar(this);
        scrubber.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFF91A6FF));
        scrubber.setThumbTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        timeline.addView(scrubber, new LinearLayout.LayoutParams(0, -1, 1));
        lengthLabel = Ui.text(this, "0:00", 12, 0xFFB8B9C6, false);
        lengthLabel.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        timeline.addView(lengthLabel, new LinearLayout.LayoutParams(Ui.dp(this, 49), -1));
        scrubber.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onStartTrackingTouch(SeekBar s) { tracking = true; }
            @Override public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                if (fromUser) positionLabel.setText(MediaRepository.duration(progress));
            }
            @Override public void onStopTrackingTouch(SeekBar s) { if (playback != null) playback.seekTo(s.getProgress()); tracking = false; }
        });

        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.CENTER);
        controls.addView(buttons, new LinearLayout.LayoutParams(-1, Ui.dp(this, 64)));
        TextView previous = ctrl("⏮", 25);
        previous.setContentDescription("Previous video");
        buttons.addView(previous, new LinearLayout.LayoutParams(0, -1, 1));
        previous.setOnClickListener(v -> moveVideo(-1));
        TextView rewind = ctrl("−10", 17);
        rewind.setContentDescription("Rewind 10 seconds");
        buttons.addView(rewind, new LinearLayout.LayoutParams(0, -1, 1));
        rewind.setOnClickListener(v -> { if (playback != null) playback.skip(-10000); });
        play = ctrl("▶", 31);
        play.setContentDescription("Play or pause");
        buttons.addView(play, new LinearLayout.LayoutParams(0, -1, 1));
        play.setOnClickListener(v -> { if (playback != null) playback.toggle(); });
        TextView forward = ctrl("+10", 17);
        forward.setContentDescription("Forward 10 seconds");
        buttons.addView(forward, new LinearLayout.LayoutParams(0, -1, 1));
        forward.setOnClickListener(v -> { if (playback != null) playback.skip(10000); });
        TextView nextVideo = ctrl("⏭", 25);
        nextVideo.setContentDescription("Next video");
        buttons.addView(nextVideo, new LinearLayout.LayoutParams(0, -1, 1));
        nextVideo.setOnClickListener(v -> moveVideo(1));
        TextView pip = ctrl("▣", 27);
        pip.setContentDescription("Floating mini-player");
        buttons.addView(pip, new LinearLayout.LayoutParams(0, -1, 1));
        pip.setOnClickListener(v -> enterMiniPlayer());

        LinearLayout extras = new LinearLayout(this);
        extras.setGravity(Gravity.CENTER_VERTICAL);
        controls.addView(extras, new LinearLayout.LayoutParams(-1, Ui.dp(this, 41)));
        speed = ctrl("1×", 16);
        extras.addView(speed, new LinearLayout.LayoutParams(0, -1, 1));
        speed.setContentDescription("Change speed");
        speed.setOnClickListener(v -> showSpeedMenu());
        repeat = ctrl("↻ Off", 13);
        repeat.setContentDescription("Repeat video");
        extras.addView(repeat, new LinearLayout.LayoutParams(0, -1, 1));
        repeat.setOnClickListener(v -> { if (playback != null) playback.toggleLooping(); });
        TextView rotate = ctrl("⤢ Rotate", 13);
        rotate.setContentDescription("Rotate player");
        extras.addView(rotate, new LinearLayout.LayoutParams(0, -1, 1));
        rotate.setOnClickListener(v -> {
            setRequestedOrientation(getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE
                    ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        });
        TextView bg = ctrl("♫ Background", 13);
        bg.setContentDescription("Continue audio in background");
        extras.addView(bg, new LinearLayout.LayoutParams(0, -1, 1));
        bg.setOnClickListener(v -> {
            Toast.makeText(this, "Audio continues when you leave or lock the screen", Toast.LENGTH_SHORT).show();
            moveTaskToBack(true);
        });
        status = Ui.text(this, "Ready", 11, 0xFFAAB1BE, false);
        status.setGravity(Gravity.CENTER);
        controls.addView(status, new LinearLayout.LayoutParams(-1, Ui.dp(this, 27)));
    }

    private void showGestureHint(String text) {
        if (gestureHint == null) return;
        gestureHint.setText(text);
        gestureHint.setVisibility(View.VISIBLE);
    }

    private void loadPlaylist() {
        // Visible media list is retained for within-app navigation. Also load all videos
        // when opened from a separate Gallery process with an ACTION_VIEW intent.
        final Uri opened = uri;
        libraryWorker.submit(() -> {
            List<MediaItem> candidates = new ArrayList<>();
            if (!SelectionSession.videos.isEmpty()) candidates.addAll(SelectionSession.videos);
            if (candidates.isEmpty() || !containsVideo(candidates, opened))
                candidates = MediaRepository.load(getApplicationContext(), true);
            List<MediaItem> result = candidates;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || !opened.equals(uri)) return;
                playlist.clear(); playlist.addAll(result);
                playlistIndex = indexForUri(opened);
                if (playlistIndex < 0) {
                    // ACTION_VIEW may refer to a document supplied by another app.
                    playlist.clear(); playlist.add(new MediaItem(opened, currentTitle,
                            "", "", true, System.currentTimeMillis(), 0));
                    playlistIndex = 0;
                }
            });
        });
    }
    private static boolean containsVideo(List<MediaItem> items, Uri u) {
        for (MediaItem item : items) if (u.equals(item.uri)) return true;
        return false;
    }
    private int indexForUri(Uri u) {
        for (int i=0; i<playlist.size(); i++) if (u.equals(playlist.get(i).uri)) return i;
        return -1;
    }
    private void moveVideo(int offset) {
        if (playlistIndex < 0 || playlist.isEmpty()) {
            Toast.makeText(this, "Loading video playlist…", Toast.LENGTH_SHORT).show();
            return;
        }
        int index = playlistIndex + offset;
        if (index < 0 || index >= playlist.size()) {
            Toast.makeText(this, offset < 0 ? "First video" : "Last video", Toast.LENGTH_SHORT).show();
            return;
        }
        playlistIndex = index;
        MediaItem next = playlist.get(index);
        uri = next.uri;
        currentTitle = next.title == null ? "Video" : next.title;
        titleView.setText(currentTitle);
        syncFavorite();
        startPlayback();
    }

    private void showSpeedMenu() {
        PopupMenu popup = new PopupMenu(this, speed);
        String[] options = {"0.5×", "0.75×", "1×", "1.25×", "1.5×", "2×"};
        float[] factors = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f};
        for (String o : options) popup.getMenu().add(o);
        popup.setOnMenuItemClickListener(item -> {
            for (int i = 0; i < options.length; i++) {
                if (!options[i].contentEquals(item.getTitle())) continue;
                if (playback == null || !playback.changeSpeed(factors[i]))
                    Toast.makeText(this,"Speed isn't available yet",Toast.LENGTH_SHORT).show();
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void enterMiniPlayer() {
        if (android.os.Build.VERSION.SDK_INT >= 26
                && getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            try {
                PictureInPictureParams p = new PictureInPictureParams.Builder().setAspectRatio(new Rational(16,9)).build();
                enterPictureInPictureMode(p);
            } catch (RuntimeException ex) {
                Toast.makeText(this,"Mini-player unavailable",Toast.LENGTH_SHORT).show();
            }
        } else Toast.makeText(this,"Mini-player is unavailable",Toast.LENGTH_SHORT).show();
    }
    @Override public void onPictureInPictureModeChanged(boolean pip, Configuration config) {
        super.onPictureInPictureModeChanged(pip, config);
        if (header != null) header.setVisibility(pip ? View.GONE : View.VISIBLE);
        if (controls != null) controls.setVisibility(pip ? View.GONE : View.VISIBLE);
    }

    private void syncFavorite() {
        if (favorite == null || uri == null) return;
        boolean f = Favorites.contains(this, uri.toString());
        favorite.setText(f ? "♥" : "♡");
        favorite.setTextColor(f ? 0xFFFF7D95 : Color.WHITE);
    }
    private void shareVideo() {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("video/*");
        i.putExtra(Intent.EXTRA_STREAM, uri);
        i.setClipData(ClipData.newUri(getContentResolver(), "Video", uri));
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(i, "Share video"));
    }

    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }
    @Override protected void onPause() {
        handler.removeCallbacks(ticker);
        // IMPORTANT: do not pause playback here: the service keeps audio playing.
        super.onPause();
    }
    @Override protected void onDestroy() {
        libraryWorker.shutdownNow();
        handler.removeCallbacks(ticker);
        if (playback != null && surfaceReady) playback.detach(surface.getHolder());
        if (bound) { unbindService(conn); bound = false; }
        playback = null;
        // Do not stop the service; playback continues after back/home/lock.
        super.onDestroy();
    }
}
