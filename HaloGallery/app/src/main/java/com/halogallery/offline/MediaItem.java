package com.halogallery.offline;

import android.net.Uri;

final class MediaItem {
    final Uri uri;
    final String title;
    final String bucketId;
    final String bucketName;
    final boolean isVideo;
    final long when;
    final long durationMs;

    MediaItem(Uri uri, String title, String bucketId, String bucketName,
              boolean isVideo, long when, long durationMs) {
        this.uri = uri;
        this.title = title == null ? "Untitled" : title;
        this.bucketId = bucketId == null ? "unknown" : bucketId;
        this.bucketName = bucketName == null || bucketName.isEmpty() ? "Other" : bucketName;
        this.isVideo = isVideo;
        this.when = when;
        this.durationMs = durationMs;
    }

    String key() { return uri.toString(); }
}
