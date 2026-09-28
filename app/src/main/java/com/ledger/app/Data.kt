package com.ledger.app

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Entity(tableName = "categories", indices = [Index(value = ["name"], unique = true)])
data class CategoryEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String)

@Entity(tableName = "category_rules", indices = [Index(value = ["keyword"], unique = true)])
data class CategoryRuleEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val keyword: String, val categoryName: String)

@Entity(tableName = "transactions", foreignKeys = [ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"])], indices = [Index("dateEpochDay"), Index("categoryId")])
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateEpochDay: Long,
    val name: String,
    val amountCents: Long,
    val categoryId: Long,
    val type: String,
    val note: String = "",
    val excludeFromDailyStats: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val date: LocalDate get() = LocalDate.ofEpochDay(dateEpochDay)
}

@Entity(tableName = "budgets")
data class BudgetEntity(@PrimaryKey val period: String, val amountCents: Long)

@Dao
interface LedgerDao {
    @Query("SELECT * FROM transactions ORDER BY dateEpochDay DESC, id DESC") fun observeTransactions(): Flow<List<TransactionEntity>>
    @Query("SELECT * FROM categories ORDER BY id") fun observeCategories(): Flow<List<CategoryEntity>>
    @Query("SELECT * FROM category_rules ORDER BY length(keyword) DESC") fun observeRules(): Flow<List<CategoryRuleEntity>>
    @Query("SELECT * FROM budgets") fun observeBudgets(): Flow<List<BudgetEntity>>
    @Query("SELECT * FROM transactions ORDER BY id") suspend fun allTransactions(): List<TransactionEntity>
    @Query("SELECT * FROM categories ORDER BY id") suspend fun allCategories(): List<CategoryEntity>
    @Query("SELECT * FROM category_rules ORDER BY id") suspend fun allRules(): List<CategoryRuleEntity>
    @Query("SELECT * FROM budgets") suspend fun allBudgets(): List<BudgetEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertCategory(category: CategoryEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertRule(rule: CategoryRuleEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertBudget(budget: BudgetEntity)
    @Insert suspend fun insertTransactions(rows: List<TransactionEntity>)
    @Insert suspend fun insertTransaction(row: TransactionEntity): Long
    @Query("UPDATE transactions SET dateEpochDay = :date, name = :name, amountCents = :amount, categoryId = :category, type = :type, note = :note, excludeFromDailyStats = :excluded, updatedAt = :updated WHERE id = :id")
    suspend fun updateTransaction(id: Long, date: Long, name: String, amount: Long, category: Long, type: String, note: String, excluded: Boolean, updated: Long)
    @Query("DELETE FROM transactions WHERE id = :id") suspend fun deleteTransaction(id: Long)
    @Query("DELETE FROM transactions") suspend fun deleteTransactions()
    @Query("DELETE FROM category_rules") suspend fun deleteRules()
    @Query("DELETE FROM budgets") suspend fun deleteBudgets()
    @Query("DELETE FROM categories") suspend fun deleteCategories()
}

@Database(entities = [TransactionEntity::class, CategoryEntity::class, CategoryRuleEntity::class, BudgetEntity::class], version = 1, exportSchema = true)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun dao(): LedgerDao
    companion object {
        @Volatile private var instance: LedgerDatabase? = null
        fun get(context: Context): LedgerDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, LedgerDatabase::class.java, "ledger.db").build().also { instance = it }
        }
    }
}

class LedgerRepository(private val db: LedgerDatabase) {
    private val dao = db.dao()
    val transactions = dao.observeTransactions()
    val categories = dao.observeCategories()
    val rules = dao.observeRules()
    val budgets = dao.observeBudgets()

    suspend fun seedCategories() { CategoryRuleEngine.categories.forEach { dao.insertCategory(CategoryEntity(name = it)) } }
    suspend fun categoryIds() = dao.allCategories().associate { it.name to it.id }

    suspend fun saveParsed(rows: List<ParsedTransaction>, categoryIds: Map<String, Long>) {
        require(rows.isNotEmpty())
        db.withTransaction {
            dao.insertTransactions(rows.map { row ->
                TransactionEntity(dateEpochDay = row.date.toEpochDay(), name = row.name, amountCents = row.amountCents,
                    categoryId = categoryIds.getValue(row.suggestedCategory), type = row.type.name)
            })
            rows.forEach { row ->
                if (row.suggestedCategory != CategoryRuleEngine.infer(row.name)) {
                    dao.insertRule(CategoryRuleEntity(keyword = row.name.trim(), categoryName = row.suggestedCategory))
                }
            }
        }
    }

    suspend fun save(row: TransactionEntity) { if (row.id == 0L) dao.insertTransaction(row) else dao.updateTransaction(row.id, row.dateEpochDay, row.name, row.amountCents, row.categoryId, row.type, row.note, row.excludeFromDailyStats, System.currentTimeMillis()) }
    suspend fun delete(id: Long) = dao.deleteTransaction(id)
    suspend fun remember(name: String, category: String) = dao.insertRule(CategoryRuleEntity(keyword = name.trim(), categoryName = category))
    suspend fun setBudget(period: String, amount: Long) = dao.insertBudget(BudgetEntity(period, amount))
    suspend fun clear() = db.withTransaction { dao.deleteTransactions(); dao.deleteRules(); dao.deleteBudgets() }
    suspend fun snapshot() = BackupData(dao.allTransactions(), dao.allCategories(), dao.allRules(), dao.allBudgets())

    suspend fun restore(data: BackupData) = db.withTransaction {
        dao.deleteTransactions(); dao.deleteRules(); dao.deleteBudgets(); dao.deleteCategories()
        data.categories.forEach { dao.insertCategory(it) }
        dao.insertTransactions(data.transactions)
        data.rules.forEach { dao.insertRule(it) }
        data.budgets.forEach { dao.insertBudget(it) }
    }
}

data class BackupData(val transactions: List<TransactionEntity>, val categories: List<CategoryEntity>, val rules: List<CategoryRuleEntity>, val budgets: List<BudgetEntity>)
