package com.halogallery.offline;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

final class MediaRepository {
    private MediaRepository() {}

    static List<MediaItem> load(Context context, boolean videosOnly) {
        List<MediaItem> all = new ArrayList<>();
        if (videosOnly) query(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, all);
        else {
            query(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false, all);
            query(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, all);
        }
        Collections.sort(all, (a, b) -> Long.compare(b.when, a.when));
        return all;
    }

    private static void query(Context context, Uri collection, boolean video, List<MediaItem> out) {
        String[] projection = {
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            "bucket_id", "bucket_display_name", "datetaken",
            MediaStore.MediaColumns.DATE_ADDED,
            video ? "duration" : MediaStore.MediaColumns.SIZE
        };
        try (Cursor c = context.getContentResolver().query(collection, projection, null, null, null)) {
            if (c == null) return;
            int idIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID);
            int nameIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME);
            int bucketIdIdx = c.getColumnIndexOrThrow("bucket_id");
            int bucketIdx = c.getColumnIndexOrThrow("bucket_display_name");
            int takenIdx = c.getColumnIndexOrThrow("datetaken");
            int addedIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED);
            int durationIdx = c.getColumnIndex(video ? "duration" : MediaStore.MediaColumns.SIZE);
            while (c.moveToNext()) {
                long id = c.getLong(idIdx);
                long captured = c.isNull(takenIdx) ? 0L : c.getLong(takenIdx);
                long added = c.isNull(addedIdx) ? 0L : c.getLong(addedIdx) * 1000L;
                long duration = video && durationIdx >= 0 && !c.isNull(durationIdx)
                        ? c.getLong(durationIdx) : 0L;
                out.add(new MediaItem(ContentUris.withAppendedId(collection, id),
                    c.getString(nameIdx), c.getString(bucketIdIdx), c.getString(bucketIdx),
                    video, captured > 0 ? captured : added, duration));
            }
        } catch (SecurityException ignored) {
            // The user may have granted only some media, or changed permission mid-query.
        } catch (RuntimeException ignored) {
            // A storage provider can be unavailable or omit an optional column.
        }
    }

    static String duration(long milliseconds) {
        long s = Math.max(0, milliseconds / 1000);
        if (s >= 3600) return String.format(java.util.Locale.ROOT, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
        return String.format(java.util.Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }
}
