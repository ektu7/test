package com.halogallery.offline;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;
import java.util.ArrayList;

/** Open a chosen Gallery video in the separately installed Halo Video app. */
final class VideoBridge {
    static final String VIDEO_PACKAGE = "com.halogallery.video31";
    private VideoBridge() { }
    static void open(Activity activity, Uri uri, String title) {
        Intent open = new Intent(Intent.ACTION_VIEW);
        open.setDataAndType(uri, "video/*");
        open.setPackage(VIDEO_PACKAGE);
        open.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        // Transfer the Gallery's visible video sequence and URI grants, so Next/Previous
        // work immediately even when the user hasn't opened Halo Video before.
        ArrayList<String> playlistUris = new ArrayList<>();
        ArrayList<String> playlistTitles = new ArrayList<>();
        ClipData grants = ClipData.newUri(activity.getContentResolver(), "Video", uri);
        for (MediaItem video : SelectionSession.videos) {
            if (playlistUris.size() >= 80) break;
            if (!video.isVideo) continue;
            playlistUris.add(video.uri.toString());
            playlistTitles.add(video.title == null ? "Video" : video.title);
            if (!video.uri.equals(uri)) grants.addItem(new ClipData.Item(video.uri));
        }
        open.setClipData(grants);
        open.putStringArrayListExtra("halo_playlist_uris", playlistUris);
        open.putStringArrayListExtra("halo_playlist_titles", playlistTitles);
        open.putExtra("title", title);
        try {
            activity.startActivity(open);
        } catch (ActivityNotFoundException exception) {
            new android.app.AlertDialog.Builder(activity)
                .setTitle("Halo Video not installed")
                .setMessage("Install Halo Video 3.0 for background playback, timeline seeking and next/previous videos. You can also use another video player.")
                .setPositiveButton("Open with another player", (d, which) -> {
                    Intent fallback = new Intent(Intent.ACTION_VIEW);
                    fallback.setDataAndType(uri, "video/*");
                    fallback.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    fallback.setClipData(ClipData.newUri(activity.getContentResolver(), "Video", uri));
                    try { activity.startActivity(Intent.createChooser(fallback, "Open video with")); }
                    catch (ActivityNotFoundException ex) { Toast.makeText(activity, "No compatible video player found", Toast.LENGTH_LONG).show(); }
                }).setNegativeButton("Cancel", null).show();
        } catch (SecurityException exception) {
            Toast.makeText(activity, "Video permission was denied", Toast.LENGTH_LONG).show();
        }
    }
}
