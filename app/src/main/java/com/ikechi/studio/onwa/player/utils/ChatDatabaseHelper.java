package com.ikechi.studio.onwa.player.utils;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.ikechi.studio.onwa.player.models.ChatMessage;

import java.util.ArrayList;
import java.util.List;

public class ChatDatabaseHelper extends SQLiteOpenHelper {

    private static final String TAG = "ChatDatabaseHelper";
    private static final String DATABASE_NAME = "chat_history.db";
    private static final int DATABASE_VERSION = 2; // Upgraded for new schema

    private static final String TABLE_CHAT = "chat";
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_USERNAME = "username";        // Owner of the message
    private static final String COLUMN_REAL_NAME = "real_name";
    private static final String COLUMN_MESSAGE = "message";
    private static final String COLUMN_TIMESTAMP = "timestamp";
    private static final String COLUMN_IS_LOCAL = "is_local";
    private static final String COLUMN_IS_PRIVATE = "is_private";
    private static final String COLUMN_TARGET = "target_username";

    private static final String CREATE_TABLE = "CREATE TABLE " + TABLE_CHAT + "("
	+ COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
	+ COLUMN_USERNAME + " TEXT,"
	+ COLUMN_REAL_NAME + " TEXT,"
	+ COLUMN_MESSAGE + " TEXT,"
	+ COLUMN_TIMESTAMP + " INTEGER,"
	+ COLUMN_IS_LOCAL + " INTEGER,"
	+ COLUMN_IS_PRIVATE + " INTEGER DEFAULT 0,"
	+ COLUMN_TARGET + " TEXT)";

    private static ChatDatabaseHelper sInstance;
    private static final Object sLock = new Object();

    private final Handler mDbHandler;

    public static ChatDatabaseHelper getInstance(Context context) {
        if (sInstance == null) {
            synchronized (sLock) {
                if (sInstance == null) {
                    sInstance = new ChatDatabaseHelper(context.getApplicationContext());
                }
            }
        }
        return sInstance;
    }

    private ChatDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);

        HandlerThread thread = new HandlerThread("ChatDBThread");
        thread.start();
        mDbHandler = new Handler(thread.getLooper());
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            // Add new columns for private messages
            db.execSQL("ALTER TABLE " + TABLE_CHAT + " ADD COLUMN " + COLUMN_IS_PRIVATE + " INTEGER DEFAULT 0");
            db.execSQL("ALTER TABLE " + TABLE_CHAT + " ADD COLUMN " + COLUMN_TARGET + " TEXT");
        }
    }

    /**
     * Insert a chat message. The message is stored under the owner's username.
     */
    public void insertMessage(final ChatMessage msg) {
        mDbHandler.post(new Runnable() {
				@Override
				public void run() {
					SQLiteDatabase db = getWritableDatabase();
					ContentValues values = new ContentValues();
					values.put(COLUMN_USERNAME, msg.getUsername());
					values.put(COLUMN_REAL_NAME, msg.getRealName());
					values.put(COLUMN_MESSAGE, msg.getMessage());
					values.put(COLUMN_TIMESTAMP, msg.getTimestamp());
					values.put(COLUMN_IS_LOCAL, msg.isLocal() ? 1 : 0);
					values.put(COLUMN_IS_PRIVATE, msg.isPrivate() ? 1 : 0);
					values.put(COLUMN_TARGET, msg.getTargetUsername());

					try {
						db.insert(TABLE_CHAT, null, values);
					} catch (Exception e) {
						Log.e(TAG, "Error inserting chat message", e);
					}
				}
			});
    }

    /**
     * Retrieve all chat messages for a given username (owner).
     */
    public List<ChatMessage> getMessagesForUser(String username) {
        final List<ChatMessage> list = new ArrayList<ChatMessage>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        try {
            String[] columns = {
                COLUMN_ID, COLUMN_USERNAME, COLUMN_REAL_NAME,
                COLUMN_MESSAGE, COLUMN_TIMESTAMP, COLUMN_IS_LOCAL,
                COLUMN_IS_PRIVATE, COLUMN_TARGET
            };
            String selection = COLUMN_USERNAME + " = ?";
            String[] selectionArgs = {username};
            String orderBy = COLUMN_TIMESTAMP + " ASC";

            cursor = db.query(TABLE_CHAT, columns, selection, selectionArgs, null, null, orderBy);
            while (cursor.moveToNext()) {
                ChatMessage msg = new ChatMessage();
                msg.setId(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)));
                msg.setUsername(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USERNAME)));
                msg.setRealName(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_REAL_NAME)));
                msg.setMessage(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MESSAGE)));
                msg.setTimestamp(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP)));
                msg.setLocal(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_IS_LOCAL)) == 1);
                msg.setPrivate(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_IS_PRIVATE)) == 1);
                msg.setTargetUsername(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TARGET)));
                list.add(msg);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading messages for user " + username, e);
        } finally {
            if (cursor != null) cursor.close();
        }
        return list;
    }

    /**
     * Delete all chat messages for a given username.
     */
    public void deleteMessagesForUser(final String username) {
        mDbHandler.post(new Runnable() {
				@Override
				public void run() {
					SQLiteDatabase db = getWritableDatabase();
					try {
						db.delete(TABLE_CHAT, COLUMN_USERNAME + " = ?", new String[]{username});
					} catch (Exception e) {
						Log.e(TAG, "Error deleting messages for user " + username, e);
					}
				}
			});
    }
}
