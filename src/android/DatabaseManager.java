package com.crypho.plugins;

import android.content.ContentValues;
import android.content.Context;

import net.sqlcipher.Cursor;
import net.sqlcipher.database.SQLiteDatabase;

import org.json.JSONArray;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {

    private SQLiteDatabase database;
    private static final String TABLE_DATA = "datas";
    private static final String COLUMN_KEY = "key";
    private static final String COLUMN_VALUE = "value";

    private String[] allColumns = {COLUMN_KEY, COLUMN_VALUE};

    public static final String DATABASE_CREATE_COMMAND = "create table " + TABLE_DATA + "( "
            + COLUMN_KEY + " text primary key, "
            + COLUMN_VALUE + " text not null);";

    public DatabaseManager(Context context, String databaseName, String password) {
        SQLiteDatabase.loadLibs(context);
        File databaseFile = context.getDatabasePath(databaseName + ".db");
        if (!databaseFile.exists()) {
            databaseFile.mkdirs();
            databaseFile.delete();
            database = SQLiteDatabase.openOrCreateDatabase(databaseFile, password, null);
            database.execSQL(DATABASE_CREATE_COMMAND);
        } else {
            database = SQLiteDatabase.openOrCreateDatabase(databaseFile, password, null);
        }
    }

    public void set(String key, String value) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_KEY, key);
        values.put(COLUMN_VALUE, value);
        database.insertWithOnConflict(TABLE_DATA, COLUMN_KEY, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public String get(String key) {
        String whereClause = String.format("%s=?", COLUMN_KEY);
        String[] whereArgs = new String[]{key};
        Cursor cursor = database.query(TABLE_DATA, null, whereClause, whereArgs, null, null, null);

        String value = "";
        if (cursor.moveToFirst()) {
            value = cursor.getString(cursor.getColumnIndex(COLUMN_VALUE));
        }
        cursor.close();
        cursor = null;
        return value;
    }

    public void remove(String key) {
        String whereClause = String.format("%s=?", COLUMN_KEY);
        String[] whereArgs = new String[]{key};
        database.delete(TABLE_DATA, whereClause, whereArgs);
    }

    public JSONArray keys() {
        Cursor cursor = database.query(TABLE_DATA, new String[]{COLUMN_KEY}, null, null, null, null, null);
        cursor.moveToFirst();
        List<String> keys = new ArrayList<>();
        while (!cursor.isAfterLast()) {
            keys.add(cursor.getString(0));
            cursor.moveToNext();
        }
        cursor.close();
        cursor = null;
        return new JSONArray(keys);
    }

    public void clear() {
        database.delete(TABLE_DATA, null, null);
    }
}
