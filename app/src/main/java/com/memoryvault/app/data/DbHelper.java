package com.memoryvault.app.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * SQLite storage for stories and their items (photos, videos and tags).
 * The data layer is plain Java; the UI is Kotlin.
 */
public final class DbHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "vault.db";
    private static final int DB_VERSION = 2;
    private static DbHelper instance;

    public static synchronized DbHelper get(Context ctx) {
        if (instance == null) instance = new DbHelper(ctx.getApplicationContext());
        return instance;
    }

    private DbHelper(Context ctx) {
        super(ctx, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE stories ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "name TEXT NOT NULL,"
                + "cover_path TEXT,"
                + "cover_thumb TEXT,"
                + "cover_video INTEGER NOT NULL DEFAULT 0,"
                + "loop_video INTEGER NOT NULL DEFAULT 1,"
                + "position INTEGER NOT NULL DEFAULT 0,"
                + "created INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE items ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "story_id INTEGER NOT NULL,"
                + "kind INTEGER NOT NULL DEFAULT 0,"
                + "path TEXT NOT NULL,"
                + "thumb TEXT,"
                + "is_video INTEGER NOT NULL DEFAULT 0,"
                + "name TEXT,"
                + "description TEXT,"
                + "notes TEXT,"
                + "position INTEGER NOT NULL DEFAULT 0,"
                + "created INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX idx_items_story ON items(story_id, kind, position)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            // v2: covers become items (kind 2) so they carry their own text
            db.execSQL("INSERT INTO items (story_id, kind, path, thumb, is_video, position, created) "
                    + "SELECT id, 2, cover_path, cover_thumb, cover_video, 0, created "
                    + "FROM stories WHERE cover_path IS NOT NULL");
        }
    }

    // ---------------------------------------------------------------- stories

    public List<Story> allStories() {
        List<Story> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("stories", null, null, null, null, null, "position ASC, id ASC");
        try {
            while (c.moveToNext()) out.add(readStory(c));
        } finally {
            c.close();
        }
        return out;
    }

    @Nullable
    public Story storyById(long id) {
        Cursor c = getReadableDatabase().query("stories", null, "id=?",
                new String[]{String.valueOf(id)}, null, null, null);
        try {
            return c.moveToFirst() ? readStory(c) : null;
        } finally {
            c.close();
        }
    }

    public long insertStory(String name, @Nullable String coverPath, @Nullable String coverThumb, boolean coverVideo) {
        SQLiteDatabase d = getWritableDatabase();
        long pos = 0;
        Cursor c = d.rawQuery("SELECT COALESCE(MAX(position), -1) + 1 FROM stories", null);
        try {
            if (c.moveToFirst()) pos = c.getLong(0);
        } finally {
            c.close();
        }
        ContentValues v = new ContentValues();
        v.put("name", name);
        v.put("cover_path", coverPath);
        v.put("cover_thumb", coverThumb);
        v.put("cover_video", coverVideo ? 1 : 0);
        v.put("loop_video", 1);
        v.put("position", pos);
        v.put("created", System.currentTimeMillis());
        return d.insert("stories", null, v);
    }

    public void updateStoryName(long id, String name) {
        ContentValues v = new ContentValues();
        v.put("name", name);
        getWritableDatabase().update("stories", v, "id=?", new String[]{String.valueOf(id)});
    }

    public void updateStoryCover(long id, @Nullable String path, @Nullable String thumb, boolean video) {
        ContentValues v = new ContentValues();
        v.put("cover_path", path);
        v.put("cover_thumb", thumb);
        v.put("cover_video", video ? 1 : 0);
        getWritableDatabase().update("stories", v, "id=?", new String[]{String.valueOf(id)});
    }

    public void updateStoryLoop(long id, boolean loop) {
        ContentValues v = new ContentValues();
        v.put("loop_video", loop ? 1 : 0);
        getWritableDatabase().update("stories", v, "id=?", new String[]{String.valueOf(id)});
    }

    public void saveStoryOrder(List<Long> ids) {
        SQLiteDatabase d = getWritableDatabase();
        d.beginTransaction();
        try {
            for (int i = 0; i < ids.size(); i++) {
                ContentValues v = new ContentValues();
                v.put("position", i);
                d.update("stories", v, "id=?", new String[]{String.valueOf(ids.get(i))});
            }
            d.setTransactionSuccessful();
        } finally {
            d.endTransaction();
        }
    }

    public void deleteStory(long id) {
        SQLiteDatabase d = getWritableDatabase();
        d.beginTransaction();
        try {
            d.delete("items", "story_id=?", new String[]{String.valueOf(id)});
            d.delete("stories", "id=?", new String[]{String.valueOf(id)});
            d.setTransactionSuccessful();
        } finally {
            d.endTransaction();
        }
    }

    // ------------------------------------------------------------------ items

    public long insertItem(long storyId, int kind, String path, @Nullable String thumb,
                           boolean video, @Nullable String name) {
        SQLiteDatabase d = getWritableDatabase();
        long pos = 0;
        Cursor c = d.rawQuery("SELECT COALESCE(MAX(position), -1) + 1 FROM items WHERE story_id=? AND kind=?",
                new String[]{String.valueOf(storyId), String.valueOf(kind)});
        try {
            if (c.moveToFirst()) pos = c.getLong(0);
        } finally {
            c.close();
        }
        ContentValues v = new ContentValues();
        v.put("story_id", storyId);
        v.put("kind", kind);
        v.put("path", path);
        v.put("thumb", thumb);
        v.put("is_video", video ? 1 : 0);
        v.put("name", name);
        v.put("position", pos);
        v.put("created", System.currentTimeMillis());
        return d.insert("items", null, v);
    }

    public List<Item> itemsOf(long storyId, int kind) {
        List<Item> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("items", null, "story_id=? AND kind=?",
                new String[]{String.valueOf(storyId), String.valueOf(kind)}, null, null, "position ASC, id ASC");
        try {
            while (c.moveToNext()) out.add(readItem(c));
        } finally {
            c.close();
        }
        return out;
    }

    @Nullable
    public Item itemById(long id) {
        Cursor c = getReadableDatabase().query("items", null, "id=?",
                new String[]{String.valueOf(id)}, null, null, null);
        try {
            return c.moveToFirst() ? readItem(c) : null;
        } finally {
            c.close();
        }
    }

    public void updateItemText(long id, @Nullable String name, @Nullable String description, @Nullable String notes) {
        ContentValues v = new ContentValues();
        v.put("name", name);
        v.put("description", description);
        v.put("notes", notes);
        getWritableDatabase().update("items", v, "id=?", new String[]{String.valueOf(id)});
    }

    public void deleteItem(long id) {
        getWritableDatabase().delete("items", "id=?", new String[]{String.valueOf(id)});
    }

    /** Moves an item to another kind (e.g. memory <-> cover), putting it last in that kind. */
    public void setItemKind(long id, int kind) {
        Item item = itemById(id);
        if (item == null) return;
        SQLiteDatabase d = getWritableDatabase();
        long pos = 0;
        Cursor c = d.rawQuery("SELECT COALESCE(MAX(position), -1) + 1 FROM items WHERE story_id=? AND kind=?",
                new String[]{String.valueOf(item.getStoryId()), String.valueOf(kind)});
        try {
            if (c.moveToFirst()) pos = c.getLong(0);
        } finally {
            c.close();
        }
        ContentValues v = new ContentValues();
        v.put("kind", kind);
        v.put("position", pos);
        d.update("items", v, "id=?", new String[]{String.valueOf(id)});
    }

    /** Switches the photo/video file of an item; name, description and notes stay untouched. */
    public void updateItemMedia(long id, String path, @Nullable String thumb, boolean video) {
        ContentValues v = new ContentValues();
        v.put("path", path);
        v.put("thumb", thumb);
        v.put("is_video", video ? 1 : 0);
        getWritableDatabase().update("items", v, "id=?", new String[]{String.valueOf(id)});
    }

    // ---------------------------------------------------------------- helpers

    private Story readStory(Cursor c) {
        return new Story(
                c.getLong(c.getColumnIndexOrThrow("id")),
                c.getString(c.getColumnIndexOrThrow("name")),
                c.getString(c.getColumnIndexOrThrow("cover_path")),
                c.getString(c.getColumnIndexOrThrow("cover_thumb")),
                c.getInt(c.getColumnIndexOrThrow("cover_video")) == 1,
                c.getInt(c.getColumnIndexOrThrow("loop_video")) == 1,
                c.getInt(c.getColumnIndexOrThrow("position")));
    }

    private Item readItem(Cursor c) {
        return new Item(
                c.getLong(c.getColumnIndexOrThrow("id")),
                c.getLong(c.getColumnIndexOrThrow("story_id")),
                c.getInt(c.getColumnIndexOrThrow("kind")),
                c.getString(c.getColumnIndexOrThrow("path")),
                c.getString(c.getColumnIndexOrThrow("thumb")),
                c.getInt(c.getColumnIndexOrThrow("is_video")) == 1,
                c.getString(c.getColumnIndexOrThrow("name")),
                c.getString(c.getColumnIndexOrThrow("description")),
                c.getString(c.getColumnIndexOrThrow("notes")),
                c.getInt(c.getColumnIndexOrThrow("position")));
    }
}
