package com.halogallery.offline;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.view.SurfaceHolder;

import java.io.IOException;

/** Offline local media playback service. Owns the player independently of the screen. */
public final class PlaybackService extends Service {
    public static final String ACTION_START = "com.halogallery.offline.PLAY_VIDEO";
    public static final String ACTION_TOGGLE = "com.halogallery.offline.TOGGLE_VIDEO";
    public static final String ACTION_REWIND = "com.halogallery.offline.REWIND_VIDEO";
    public static final String ACTION_FORWARD = "com.halogallery.offline.FORWARD_VIDEO";
    public static final String ACTION_STOP = "com.halogallery.offline.STOP_VIDEO";
    public static final String EXTRA_TITLE = "title";
    private static final String CHANNEL = "halo_video_playback";
    private static final int NOTIFICATION_ID = 211;

    public final class LocalBinder extends Binder {
        public PlaybackService getService() { return PlaybackService.this; }
    }
    private final LocalBinder binder = new LocalBinder();
    private MediaPlayer player;
    private MediaSession mediaSession;
    private Uri currentUri;
    private String title = "Video";
    private boolean preparing;
    private boolean prepared;
    private boolean shouldPlay;
    private boolean looping;
    private float speed = 1f;
    private SurfaceHolder holder;
    private boolean foreground;

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Video playback", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Local video and background audio controls");
            ch.setSound(null, null);
            nm.createNotificationChannel(ch);
        }
        mediaSession = new MediaSession(this, "Halo Video");
        mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { play(); }
            @Override public void onPause() { pause(); }
            @Override public void onSeekTo(long pos) { seekTo((int) pos); }
            @Override public void onSkipToNext() { skip(10000); }
            @Override public void onSkipToPrevious() { skip(-10000); }
            @Override public void onStop() { stopSelfPlayback(); }
        });
        mediaSession.setActive(true);
        updateSession();
    }

    @Override public IBinder onBind(Intent intent) { return binder; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_STICKY;
        String action = intent.getAction();
        if (ACTION_STOP.equals(action)) { stopSelfPlayback(); return START_NOT_STICKY; }
        if (ACTION_TOGGLE.equals(action)) { toggle(); return START_STICKY; }
        if (ACTION_REWIND.equals(action)) { skip(-10000); return START_STICKY; }
        if (ACTION_FORWARD.equals(action)) { skip(10000); return START_STICKY; }
        if (ACTION_START.equals(action)) {
            Uri incoming = intent.getData();
            if (incoming == null) { stopSelf(); return START_NOT_STICKY; }
            if (currentUri == null || !currentUri.equals(incoming)) {
                title = intent.getStringExtra(EXTRA_TITLE);
                if (title == null || title.trim().isEmpty()) title = "Video";
                currentUri = incoming;
                startForegroundNow();
                beginPreparation();
            } else if (player == null) {
                startForegroundNow();
                beginPreparation();
            } else {
                // Reopening the player from notification must not restart the video.
                showNotification();
            }
        }
        return START_STICKY;
    }

    private void beginPreparation() {
        releasePlayer();
        preparing = true;
        prepared = false;
        shouldPlay = true;
        speed = 1f;
        try {
            MediaPlayer mp = new MediaPlayer();
            player = mp;
            mp.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build());
            mp.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
            mp.setDataSource(this, currentUri);
            if (holder != null) mp.setDisplay(holder);
            mp.setLooping(looping);
            mp.setOnPreparedListener(p -> {
                if (p != player) return;
                preparing = false;
                prepared = true;
                try {
                    if (shouldPlay) p.start();
                } catch (IllegalStateException ignored) { }
                updateSession();
                showNotification();
            });
            mp.setOnCompletionListener(p -> {
                if (p != player) return;
                shouldPlay = false;
                updateSession();
                showNotification();
            });
            mp.setOnErrorListener((p, what, extra) -> {
                preparing = false;
                prepared = false;
                shouldPlay = false;
                updateSession();
                showNotification();
                return true;
            });
            mp.prepareAsync();
        } catch (IOException | RuntimeException ex) {
            preparing = false;
            prepared = false;
            shouldPlay = false;
            releasePlayer();
            updateSession();
            showNotification();
        }
    }

    public boolean isCurrentUri(Uri other) { return currentUri != null && currentUri.equals(other); }
    public boolean isReady() { return prepared; }
    public boolean isPlaying() {
        try { return prepared && player != null && player.isPlaying(); }
        catch (IllegalStateException ex) { return false; }
    }
    public boolean isPreparing() { return preparing; }
    public int position() {
        try { return prepared && player != null ? player.getCurrentPosition() : 0; }
        catch (IllegalStateException ex) { return 0; }
    }
    public int duration() {
        try { return prepared && player != null ? Math.max(0, player.getDuration()) : 0; }
        catch (IllegalStateException ex) { return 0; }
    }
    public float speed() { return speed; }
    public boolean isLooping() { return looping; }
    public String title() { return title; }
    public void attach(SurfaceHolder newHolder) {
        holder = newHolder;
        if (player != null) {
            try { player.setDisplay(newHolder); } catch (IllegalStateException ignored) { }
        }
    }
    public void detach(SurfaceHolder oldHolder) {
        if (holder == oldHolder) {
            holder = null;
            if (player != null) {
                try { player.setDisplay(null); } catch (IllegalStateException ignored) { }
            }
        }
    }
    public void toggle() { if (isPlaying()) pause(); else play(); }
    public void play() {
        shouldPlay = true;
        if (prepared && player != null) {
            try {
                // Restart once when video has reached the end.
                if (duration() > 0 && position() >= duration() - 250) player.seekTo(0);
                player.start();
            } catch (IllegalStateException ignored) { }
        }
        if (!foreground) startForegroundNow();
        updateSession();
        showNotification();
    }
    public void pause() {
        shouldPlay = false;
        if (isPlaying() && player != null) player.pause();
        updateSession();
        showNotification();
    }
    public void seekTo(int pos) {
        if (prepared && player != null) {
            try { player.seekTo(Math.max(0, Math.min(pos, duration()))); }
            catch (IllegalStateException ignored) { }
        }
        updateSession();
    }
    public void skip(int milliseconds) { seekTo(position() + milliseconds); }
    public boolean changeSpeed(float factor) {
        if (player == null || !prepared) return false;
        try {
            boolean wasPlaying = isPlaying();
            player.setPlaybackParams(player.getPlaybackParams().setSpeed(factor));
            if (!wasPlaying) player.pause();
            speed = factor;
            updateSession();
            return true;
        } catch (IllegalStateException | IllegalArgumentException ex) { return false; }
    }
    public void toggleLooping() {
        looping = !looping;
        if (player != null) { try { player.setLooping(looping); } catch (IllegalStateException ignored) { } }
    }

    private PendingIntent command(String action, int request) {
        Intent i = new Intent(this, PlaybackService.class).setAction(action);
        return PendingIntent.getService(this, request, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    private Notification buildNotification() {
        Intent open = new Intent(this, VideoPlayerActivity.class).setData(currentUri)
                .putExtra(EXTRA_TITLE, title)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openIntent = PendingIntent.getActivity(this, 7, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(title)
                .setContentText(isPlaying() ? "Playing · Halo Video" : "Paused · Halo Video")
                .setContentIntent(openIntent)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                .setOngoing(isPlaying())
                .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_rew,
                        "Back 10 seconds", command(ACTION_REWIND, 2)).build())
                .addAction(new Notification.Action.Builder(isPlaying() ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                        isPlaying() ? "Pause" : "Play", command(ACTION_TOGGLE, 1)).build())
                .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_ff,
                        "Forward 10 seconds", command(ACTION_FORWARD, 3)).build())
                .addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel,
                        "Stop", command(ACTION_STOP, 4)).build())
                .setStyle(new Notification.MediaStyle().setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2))
                .build();
    }
    private void startForegroundNow() {
        try { startForeground(NOTIFICATION_ID, buildNotification()); foreground = true; }
        catch (RuntimeException ex) { foreground = false; }
    }
    private void showNotification() {
        if (mediaSession == null) return;
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (foreground) nm.notify(NOTIFICATION_ID, buildNotification());
        else if (isPlaying()) startForegroundNow();
        else nm.notify(NOTIFICATION_ID, buildNotification());
    }
    private void updateSession() {
        if (mediaSession == null) return;
        int state = isPlaying() ? PlaybackState.STATE_PLAYING
                : preparing ? PlaybackState.STATE_BUFFERING : PlaybackState.STATE_PAUSED;
        long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
                | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_SEEK_TO
                | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS
                | PlaybackState.ACTION_STOP;
        mediaSession.setPlaybackState(new PlaybackState.Builder()
                .setState(state, position(), isPlaying() ? speed : 0f)
                .setActions(actions).build());
        mediaSession.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "Halo Video · Offline")
                .putLong(MediaMetadata.METADATA_KEY_DURATION, duration()).build());
    }
    private void stopSelfPlayback() {
        shouldPlay = false;
        releasePlayer();
        currentUri = null;
        foreground = false;
        stopForeground(STOP_FOREGROUND_REMOVE);
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).cancel(NOTIFICATION_ID);
        stopSelf();
    }
    private void releasePlayer() {
        if (player != null) {
            try { player.reset(); player.release(); } catch (RuntimeException ignored) { }
            player = null;
        }
        prepared = false;
        preparing = false;
    }
    @Override public void onDestroy() {
        releasePlayer();
        if (mediaSession != null) { mediaSession.setActive(false); mediaSession.release(); mediaSession = null; }
        super.onDestroy();
    }
}
