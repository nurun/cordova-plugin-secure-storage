package com.crypho.plugins;

import java.util.List;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface DataChunkDAO {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void store(DataChunk dataChunk);

    @Query("SELECT value FROM datachunk WHERE `key` = :key")
    int fetch(String key);

    @Query("DELETE FROM datachunk WHERE `key` = :key")
    void remove(String key);

    @Query("SELECT `key` FROM data")
    List<String> keys();

    @Query("DELETE FROM datachunk")
    void clear();
}
