package com.halogallery.offline;

import java.util.ArrayList;
import java.util.List;

// Short-lived in-memory navigation state. No photos or file paths are persisted.
final class SelectionSession {
    static List<MediaItem> photos = new ArrayList<>();
    static void set(List<MediaItem> visible) {
        photos = new ArrayList<>();
        for (MediaItem item : visible) if (!item.isVideo) photos.add(item);
    }
}
