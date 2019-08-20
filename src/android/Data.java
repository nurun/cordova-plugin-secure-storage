package com.crypho.plugins;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity
public class Data {
    @PrimaryKey
    @NonNull
    public String key;

    @ColumnInfo
    public String value;

    public Data(@NonNull String key, String value) {
        this.key = key;
        this.value = value;
    }
}
