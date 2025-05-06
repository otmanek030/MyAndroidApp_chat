package com.plcoding.audiorecorder.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class RecordingDatabase extends SQLiteOpenHelper {
    private static final String TAG = "RecordingDatabase";
    private static final String DATABASE_NAME = "recordings.db";
    private static final int DATABASE_VERSION = 4; // Increment version to add device_id

    // Table name
    public static final String TABLE_RECORDINGS = "recordings";

    // Column names
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_TITLE = "title";
    public static final String COLUMN_FILE_PATH = "file_path";
    public static final String COLUMN_DURATION = "duration";
    public static final String COLUMN_DATE = "created_at";
    public static final String COLUMN_TYPE = "type";
    public static final String COLUMN_TEXT_CONTENT = "text_content";
    public static final String COLUMN_DEVICE_ID = "device_id";

    public RecordingDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTableQuery = "CREATE TABLE " + TABLE_RECORDINGS + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_TITLE + " TEXT NOT NULL, " +
                COLUMN_FILE_PATH + " TEXT, " +
                COLUMN_DURATION + " INTEGER DEFAULT 0, " +
                COLUMN_DATE + " INTEGER NOT NULL, " +
                COLUMN_TYPE + " TEXT NOT NULL DEFAULT 'voice', " +
                COLUMN_TEXT_CONTENT + " TEXT, " +
                COLUMN_DEVICE_ID + " TEXT)";

        Log.d(TAG, "Creating table with query: " + createTableQuery);
        db.execSQL(createTableQuery);
        Log.d(TAG, "Table created successfully");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.d(TAG, "Upgrading database from version " + oldVersion + " to " + newVersion);

        if (oldVersion < 2) {
            try {
                if (!columnExists(db, TABLE_RECORDINGS, COLUMN_TYPE)) {
                    db.execSQL("ALTER TABLE " + TABLE_RECORDINGS + " ADD COLUMN " + COLUMN_TYPE + " TEXT NOT NULL DEFAULT 'voice'");
                    Log.d(TAG, "Added TYPE column");
                }

                if (!columnExists(db, TABLE_RECORDINGS, COLUMN_TEXT_CONTENT)) {
                    db.execSQL("ALTER TABLE " + TABLE_RECORDINGS + " ADD COLUMN " + COLUMN_TEXT_CONTENT + " TEXT");
                    Log.d(TAG, "Added TEXT_CONTENT column");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error upgrading to version 2", e);
            }
        }

        if (oldVersion < 3) {
            // Migration to make file_path nullable
            try {
                String tempTableQuery = "CREATE TABLE recordings_temp (" +
                        COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        COLUMN_TITLE + " TEXT NOT NULL, " +
                        COLUMN_FILE_PATH + " TEXT, " +
                        COLUMN_DURATION + " INTEGER DEFAULT 0, " +
                        COLUMN_DATE + " INTEGER NOT NULL, " +
                        COLUMN_TYPE + " TEXT NOT NULL DEFAULT 'voice', " +
                        COLUMN_TEXT_CONTENT + " TEXT)";
                db.execSQL(tempTableQuery);

                db.execSQL("INSERT INTO recordings_temp SELECT * FROM " + TABLE_RECORDINGS);
                db.execSQL("DROP TABLE " + TABLE_RECORDINGS);
                db.execSQL("ALTER TABLE recordings_temp RENAME TO " + TABLE_RECORDINGS);

                Log.d(TAG, "Successfully migrated to version 3");
            } catch (Exception e) {
                Log.e(TAG, "Error upgrading to version 3", e);
                db.execSQL("DROP TABLE IF EXISTS " + TABLE_RECORDINGS);
                onCreate(db);
            }
        }

        if (oldVersion < 4) {
            // Add device_id column
            try {
                if (!columnExists(db, TABLE_RECORDINGS, COLUMN_DEVICE_ID)) {
                    db.execSQL("ALTER TABLE " + TABLE_RECORDINGS + " ADD COLUMN " + COLUMN_DEVICE_ID + " TEXT");
                    Log.d(TAG, "Added DEVICE_ID column");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error upgrading to version 4", e);
                db.execSQL("DROP TABLE IF EXISTS " + TABLE_RECORDINGS);
                onCreate(db);
            }
        }
    }

    private boolean columnExists(SQLiteDatabase db, String tableName, String columnName) {
        boolean exists = false;
        android.database.Cursor cursor = null;
        try {
            cursor = db.rawQuery("PRAGMA table_info(" + tableName + ")", null);
            if (cursor != null) {
                int nameIndex = cursor.getColumnIndex("name");
                while (cursor.moveToNext()) {
                    String name = cursor.getString(nameIndex);
                    if (columnName.equals(name)) {
                        exists = true;
                        break;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking if column exists", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return exists;
    }
}