package com.crypho.plugins;


import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = {Data.class, DataChunk.class}, version = 1)
public abstract class DataDatabase extends RoomDatabase {
    public abstract DataDAO dataDao();
    public abstract DataChunkDAO dataChunkDao();

}
