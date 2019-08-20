package com.crypho.plugins;

import java.util.List;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface DataDAO {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void store(Data data);

    @Query("SELECT value FROM data WHERE `key` = :key")
    String fetch(String key);

    @Query("DELETE FROM Data WHERE `key` = :key")
    void remove(String key);

    @Query("SELECT `key` FROM data")
    List<String> keys();

    @Query("DELETE FROM Data")
    void clear();

    @Query("DELETE FROM Data WHERE `key` LIKE :key")
    void clearKey(String key);
}
