package com.crypho.plugins;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity
public class DataChunk {
    @PrimaryKey
    @NonNull
    public String key;

    @ColumnInfo
    public int value;

    public DataChunk(@NonNull String key, int value) {
        this.key = key;
        this.value = value;
    }
}
