/*
 * Copyright (C) 2026 Manus
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package rkr.simplekeyboard.inputmethod.latin;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AtomicFile;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import rkr.simplekeyboard.inputmethod.compat.PreferenceManagerCompat;
import rkr.simplekeyboard.inputmethod.latin.settings.Settings;

/** Local, text-only clipboard history. The caller must explicitly request each save. */
public final class ClipboardHistoryStore {
    private static final String STORAGE_FILENAME = "hotkey-clipboard-history.json";
    private static final int MAX_ENTRIES = 50;
    public static final int MAX_TEXT_LENGTH = 20000;
    private static final long MILLIS_PER_DAY = 24L * 60L * 60L * 1000L;

    public static final class Entry {
        public final String id;
        public final String text;
        public final long timestamp;
        public final boolean pinned;

        private Entry(final String id, final String text, final long timestamp,
                final boolean pinned) {
            this.id = id;
            this.text = text;
            this.timestamp = timestamp;
            this.pinned = pinned;
        }

        private Entry withPinned(final boolean value) {
            return new Entry(id, text, timestamp, value);
        }
    }

    private final SharedPreferences mPreferences;
    private final AtomicFile mHistoryFile;

    public ClipboardHistoryStore(final Context context) {
        mPreferences = PreferenceManagerCompat.getDeviceSharedPreferences(context);
        mHistoryFile = new AtomicFile(new File(context.getNoBackupFilesDir(), STORAGE_FILENAME));
    }

    public boolean isEnabled() {
        return mPreferences.getBoolean(Settings.PREF_CLIPBOARD_HISTORY_ENABLED, false);
    }

    /** Saves one string only when history is enabled and the caller has explicit user intent. */
    public boolean saveText(final String text) {
        if (!isEnabled() || text == null || text.length() == 0
                || text.length() > MAX_TEXT_LENGTH) {
            return false;
        }
        final ArrayList<Entry> entries = readEntries();
        pruneExpired(entries, System.currentTimeMillis());
        String id = UUID.randomUUID().toString();
        boolean pinned = false;
        final Iterator<Entry> iterator = entries.iterator();
        while (iterator.hasNext()) {
            final Entry entry = iterator.next();
            if (entry.text.equals(text)) {
                id = entry.id;
                pinned = entry.pinned;
                iterator.remove();
                break;
            }
        }
        entries.add(0, new Entry(id, text, System.currentTimeMillis(), pinned));
        trimToLimit(entries);
        return writeEntries(entries);
    }

    public List<Entry> getEntries() {
        final ArrayList<Entry> entries = readEntries();
        if (pruneExpired(entries, System.currentTimeMillis())) {
            writeEntries(entries);
        }
        Collections.sort(entries, new Comparator<Entry>() {
            @Override
            public int compare(final Entry left, final Entry right) {
                if (left.pinned != right.pinned) {
                    return left.pinned ? -1 : 1;
                }
                return Long.compare(right.timestamp, left.timestamp);
            }
        });
        return entries;
    }

    public void setPinned(final String id, final boolean pinned) {
        final ArrayList<Entry> entries = readEntries();
        for (int i = 0; i < entries.size(); i++) {
            final Entry entry = entries.get(i);
            if (entry.id.equals(id)) {
                entries.set(i, entry.withPinned(pinned));
                break;
            }
        }
        writeEntries(entries);
    }

    public void delete(final String id) {
        final ArrayList<Entry> entries = readEntries();
        final Iterator<Entry> iterator = entries.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().id.equals(id)) {
                iterator.remove();
                break;
            }
        }
        writeEntries(entries);
    }

    public void clear() {
        mHistoryFile.delete();
    }

    private ArrayList<Entry> readEntries() {
        final ArrayList<Entry> entries = new ArrayList<>();
        FileInputStream stream = null;
        try {
            stream = mHistoryFile.openRead();
            final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            final byte[] buffer = new byte[4096];
            int count;
            while ((count = stream.read(buffer)) != -1) {
                bytes.write(buffer, 0, count);
            }
            final JSONArray array = new JSONArray(new String(bytes.toByteArray(),
                    StandardCharsets.UTF_8));
            for (int i = 0; i < array.length(); i++) {
                final JSONObject item = array.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                final String id = item.optString("id", "");
                final String text = item.optString("text", "");
                final long timestamp = item.optLong("timestamp", 0L);
                if (id.length() > 0 && text.length() > 0 && timestamp > 0L
                        && text.length() <= MAX_TEXT_LENGTH) {
                    entries.add(new Entry(id, text, timestamp, item.optBoolean("pinned", false)));
                }
            }
        } catch (final IOException | JSONException ignored) {
            // Corrupt or absent local history is treated as empty; never log clipboard content.
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (final IOException ignored) {
                    // Ignore close failures; clipboard text is never written to logs.
                }
            }
        }
        return entries;
    }

    private boolean pruneExpired(final ArrayList<Entry> entries, final long now) {
        final String retention = mPreferences.getString(Settings.PREF_CLIPBOARD_RETENTION, "7");
        if ("never".equals(retention)) {
            return false;
        }
        final long days;
        try {
            days = Long.parseLong(retention);
        } catch (final NumberFormatException ignored) {
            return false;
        }
        final long retentionMillis = days * MILLIS_PER_DAY;
        boolean changed = false;
        final Iterator<Entry> iterator = entries.iterator();
        while (iterator.hasNext()) {
            final Entry entry = iterator.next();
            if (!entry.pinned && now - entry.timestamp > retentionMillis) {
                iterator.remove();
                changed = true;
            }
        }
        return changed;
    }

    private static void trimToLimit(final ArrayList<Entry> entries) {
        while (entries.size() > MAX_ENTRIES) {
            int removeIndex = entries.size() - 1;
            while (removeIndex >= 0 && entries.get(removeIndex).pinned) {
                removeIndex--;
            }
            if (removeIndex < 0) {
                removeIndex = entries.size() - 1;
            }
            entries.remove(removeIndex);
        }
    }

    private boolean writeEntries(final List<Entry> entries) {
        final JSONArray array = new JSONArray();
        for (final Entry entry : entries) {
            final JSONObject item = new JSONObject();
            try {
                item.put("id", entry.id);
                item.put("text", entry.text);
                item.put("timestamp", entry.timestamp);
                item.put("pinned", entry.pinned);
            } catch (final JSONException ignored) {
                continue;
            }
            array.put(item);
        }
        FileOutputStream stream = null;
        try {
            stream = mHistoryFile.startWrite();
            stream.write(array.toString().getBytes(StandardCharsets.UTF_8));
            mHistoryFile.finishWrite(stream);
            return true;
        } catch (final IOException ignored) {
            if (stream != null) {
                mHistoryFile.failWrite(stream);
            }
            // Keep clipboard text out of log output. The previous atomic file remains intact.
            return false;
        }
    }
}
