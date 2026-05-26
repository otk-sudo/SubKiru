package com.subkiru.subkiru.core.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.subkiru.subkiru.core.data.db.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query(
        """
        SELECT * FROM categories
        ORDER BY sort_order ASC, name ASC
        """
    )
    fun observeAllCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun getCategoryById(id: Long): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun addCategory(category: CategoryEntity): Long

    /** 初期データ投入など複数件を一括追加する（冪等性確保のため IGNORE を使用） */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addCategories(categories: List<CategoryEntity>)

    @Update
    suspend fun updateCategory(category: CategoryEntity)

    @Delete
    suspend fun deleteCategory(category: CategoryEntity)
}
