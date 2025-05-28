// RecordingDatabase.java - Fixed version with proper chat support

package com.plcoding.audiorecorder.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class RecordingDatabase extends SQLiteOpenHelper {
    private static final String TAG = "RecordingDatabase";
    private static final String DATABASE_NAME = "recordings.db";
    private static final int DATABASE_VERSION = 7; // Increased version

    // Table names
    public static final String TABLE_RECORDINGS = "recordings";
    public static final String TABLE_CHAT_MESSAGES = "chat_messages";

    // Recordings table columns
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_TITLE = "title";
    public static final String COLUMN_FILE_PATH = "file_path";
    public static final String COLUMN_DURATION = "duration";
    public static final String COLUMN_DATE = "created_at";
    public static final String COLUMN_TYPE = "type";
    public static final String COLUMN_TEXT_CONTENT = "text_content";
    public static final String COLUMN_DEVICE_ID = "device_id";

    // Chat messages table columns
    public static final String COLUMN_MSG_ID = "id";
    public static final String COLUMN_MSG_RECORDING_ID = "recording_id";
    public static final String COLUMN_MSG_CONTENT = "message";
    public static final String COLUMN_MSG_IS_FROM_DEVICE = "is_from_device";
    public static final String COLUMN_MSG_TIMESTAMP = "timestamp";
    public static final String COLUMN_MSG_IS_SYNCED = "is_synced";
    public static final String COLUMN_MSG_SERVER_ID = "server_message_id";
    public static final String COLUMN_MSG_SENDER_TYPE = "sender_type";

    public RecordingDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Create recordings table
        String createRecordingsTable = "CREATE TABLE " + TABLE_RECORDINGS + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_TITLE + " TEXT NOT NULL, " +
                COLUMN_FILE_PATH + " TEXT, " +
                COLUMN_DURATION + " INTEGER DEFAULT 0, " +
                COLUMN_DATE + " INTEGER NOT NULL, " +
                COLUMN_TYPE + " TEXT NOT NULL DEFAULT 'voice', " +
                COLUMN_TEXT_CONTENT + " TEXT, " +
                COLUMN_DEVICE_ID + " TEXT)";

        Log.d(TAG, "Creating recordings table: " + createRecordingsTable);
        db.execSQL(createRecordingsTable);

        // Create chat messages table - FIXED VERSION
        String createChatTable = "CREATE TABLE " + TABLE_CHAT_MESSAGES + " (" +
                COLUMN_MSG_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_MSG_RECORDING_ID + " INTEGER NOT NULL, " +
                COLUMN_MSG_CONTENT + " TEXT NOT NULL, " +
                COLUMN_MSG_IS_FROM_DEVICE + " INTEGER NOT NULL, " +
                COLUMN_MSG_TIMESTAMP + " INTEGER NOT NULL, " +
                COLUMN_MSG_IS_SYNCED + " INTEGER NOT NULL DEFAULT 0, " +
                COLUMN_MSG_SERVER_ID + " TEXT, " +
                COLUMN_MSG_SENDER_TYPE + " TEXT DEFAULT 'device', " +
                "FOREIGN KEY(" + COLUMN_MSG_RECORDING_ID + ") REFERENCES " + TABLE_RECORDINGS + "(" + COLUMN_ID + "))";

        Log.d(TAG, "Creating chat messages table: " + createChatTable);
        db.execSQL(createChatTable);

        // Create indexes for better performance
        db.execSQL("CREATE INDEX idx_chat_recording ON " + TABLE_CHAT_MESSAGES + "(" + COLUMN_MSG_RECORDING_ID + ")");
        db.execSQL("CREATE INDEX idx_chat_timestamp ON " + TABLE_CHAT_MESSAGES + "(" + COLUMN_MSG_TIMESTAMP + ")");
        db.execSQL("CREATE INDEX idx_chat_synced ON " + TABLE_CHAT_MESSAGES + "(" + COLUMN_MSG_IS_SYNCED + ")");

        Log.d(TAG, "Database tables created successfully");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.d(TAG, "Upgrading database from version " + oldVersion + " to " + newVersion);

        try {
            // Version 2: Add type and text_content columns
            if (oldVersion < 2) {
                if (!columnExists(db, TABLE_RECORDINGS, COLUMN_TYPE)) {
                    db.execSQL("ALTER TABLE " + TABLE_RECORDINGS + " ADD COLUMN " + COLUMN_TYPE + " TEXT NOT NULL DEFAULT 'voice'");
                    Log.d(TAG, "Added TYPE column");
                }

                if (!columnExists(db, TABLE_RECORDINGS, COLUMN_TEXT_CONTENT)) {
                    db.execSQL("ALTER TABLE " + TABLE_RECORDINGS + " ADD COLUMN " + COLUMN_TEXT_CONTENT + " TEXT");
                    Log.d(TAG, "Added TEXT_CONTENT column");
                }
            }

            // Version 3: Make file_path nullable
            if (oldVersion < 3) {
                String tempTable = "recordings_temp";
                String createTempTable = "CREATE TABLE " + tempTable + " (" +
                        COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        COLUMN_TITLE + " TEXT NOT NULL, " +
                        COLUMN_FILE_PATH + " TEXT, " +
                        COLUMN_DURATION + " INTEGER DEFAULT 0, " +
                        COLUMN_DATE + " INTEGER NOT NULL, " +
                        COLUMN_TYPE + " TEXT NOT NULL DEFAULT 'voice', " +
                        COLUMN_TEXT_CONTENT + " TEXT)";

                db.execSQL(createTempTable);
                db.execSQL("INSERT INTO " + tempTable + " SELECT * FROM " + TABLE_RECORDINGS);
                db.execSQL("DROP TABLE " + TABLE_RECORDINGS);
                db.execSQL("ALTER TABLE " + tempTable + " RENAME TO " + TABLE_RECORDINGS);
                Log.d(TAG, "Successfully migrated to version 3");
            }

            // Version 4: Add device_id column
            if (oldVersion < 4) {
                if (!columnExists(db, TABLE_RECORDINGS, COLUMN_DEVICE_ID)) {
                    db.execSQL("ALTER TABLE " + TABLE_RECORDINGS + " ADD COLUMN " + COLUMN_DEVICE_ID + " TEXT");
                    Log.d(TAG, "Added DEVICE_ID column");
                }
            }

            // Version 5-6: Basic chat table (legacy)
            if (oldVersion < 6) {
                // Drop old chat table if it exists
                db.execSQL("DROP TABLE IF EXISTS chat_messages");

                // Create basic chat table
                String createBasicChatTable = "CREATE TABLE IF NOT EXISTS chat_messages (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "recording_id INTEGER NOT NULL, " +
                        "message TEXT NOT NULL, " +
                        "is_from_device INTEGER NOT NULL, " +
                        "timestamp INTEGER NOT NULL, " +
                        "is_synced INTEGER NOT NULL DEFAULT 0, " +
                        "message_id TEXT)";

                db.execSQL(createBasicChatTable);
                Log.d(TAG, "Added basic chat_messages table");
            }

            // Version 7: Enhanced chat table with proper structure
            if (oldVersion < 7) {
                // Drop and recreate chat table with new structure
                db.execSQL("DROP TABLE IF EXISTS " + TABLE_CHAT_MESSAGES);

                String createEnhancedChatTable = "CREATE TABLE " + TABLE_CHAT_MESSAGES + " (" +
                        COLUMN_MSG_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        COLUMN_MSG_RECORDING_ID + " INTEGER NOT NULL, " +
                        COLUMN_MSG_CONTENT + " TEXT NOT NULL, " +
                        COLUMN_MSG_IS_FROM_DEVICE + " INTEGER NOT NULL, " +
                        COLUMN_MSG_TIMESTAMP + " INTEGER NOT NULL, " +
                        COLUMN_MSG_IS_SYNCED + " INTEGER NOT NULL DEFAULT 0, " +
                        COLUMN_MSG_SERVER_ID + " TEXT, " +
                        COLUMN_MSG_SENDER_TYPE + " TEXT DEFAULT 'device', " +
                        "FOREIGN KEY(" + COLUMN_MSG_RECORDING_ID + ") REFERENCES " + TABLE_RECORDINGS + "(" + COLUMN_ID + "))";

                db.execSQL(createEnhancedChatTable);

                // Create indexes
                db.execSQL("CREATE INDEX idx_chat_recording ON " + TABLE_CHAT_MESSAGES + "(" + COLUMN_MSG_RECORDING_ID + ")");
                db.execSQL("CREATE INDEX idx_chat_timestamp ON " + TABLE_CHAT_MESSAGES + "(" + COLUMN_MSG_TIMESTAMP + ")");
                db.execSQL("CREATE INDEX idx_chat_synced ON " + TABLE_CHAT_MESSAGES + "(" + COLUMN_MSG_IS_SYNCED + ")");

                Log.d(TAG, "Enhanced chat_messages table created");
            }

            Log.d(TAG, "Database upgrade completed successfully");

        } catch (Exception e) {
            Log.e(TAG, "Error during database upgrade", e);
            // If upgrade fails, recreate all tables
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_RECORDINGS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_CHAT_MESSAGES);
            onCreate(db);
        }
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.w(TAG, "Downgrading database from version " + oldVersion + " to " + newVersion);
        // For simplicity, just recreate the database on downgrade
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_RECORDINGS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_CHAT_MESSAGES);
        onCreate(db);
    }

    /**
     * Check if a column exists in a table
     */
    private boolean columnExists(SQLiteDatabase db, String tableName, String columnName) {
        boolean exists = false;
        android.database.Cursor cursor = null;
        try {
            cursor = db.rawQuery("PRAGMA table_info(" + tableName + ")", null);
            if (cursor != null) {
                int nameIndex = cursor.getColumnIndex("name");
                if (nameIndex >= 0) {
                    while (cursor.moveToNext()) {
                        String name = cursor.getString(nameIndex);
                        if (columnName.equals(name)) {
                            exists = true;
                            break;
                        }
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

    /**
     * Get database version info for debugging
     */
    public void logDatabaseInfo() {
        SQLiteDatabase db = getReadableDatabase();
        Log.d(TAG, "Database version: " + db.getVersion());

        // Log table structures
        android.database.Cursor cursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null);
        if (cursor != null) {
            Log.d(TAG, "Tables in database:");
            while (cursor.moveToNext()) {
                String tableName = cursor.getString(0);
                Log.d(TAG, "  - " + tableName);
            }
            cursor.close();
        }
    }
}