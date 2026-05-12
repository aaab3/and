package com.runtime.android.db

import androidx.room.*

@Dao
interface SkillDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SkillEntity)

    @Query("SELECT * FROM installed_skills ORDER BY name ASC")
    suspend fun getAll(): List<SkillEntity>

    @Query("SELECT * FROM installed_skills WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): SkillEntity?

    @Query("DELETE FROM installed_skills WHERE name = :name")
    suspend fun delete(name: String)
}
