package io.github.randomtools3377.fitness.data

import android.content.ContentResolver
import android.net.Uri
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.withTransaction
import io.github.randomtools3377.fitness.core.Nutrients
import io.github.randomtools3377.fitness.core.Product
import io.github.randomtools3377.fitness.core.Products
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.util.zip.GZIPInputStream

/** A packaged product from a country file. The same barcode can come from several countries. */
@Entity(tableName = "products", primaryKeys = ["barcode", "country"], indices = [Index("country")])
data class ProductRow(
    val barcode: String,
    val country: String,
    val name: String,
    val brand: String,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val servingGrams: Double?,
    val servingLabel: String,
)

/** A country file the user added. */
@Entity(tableName = "countries")
data class CountryRow(
    @androidx.room.PrimaryKey val name: String,
    val products: Int,
    val addedAt: Long,
)

@Dao
interface ProductDao {
    @Query("SELECT * FROM products WHERE barcode = :barcode LIMIT 1")
    suspend fun product(barcode: String): ProductRow?

    @Query("SELECT * FROM products WHERE barcode IN (:barcodes)")
    suspend fun products(barcodes: List<String>): List<ProductRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(rows: List<ProductRow>)

    @Query("DELETE FROM products WHERE country = :country")
    suspend fun deleteCountryProducts(country: String)

    @Query("SELECT * FROM countries ORDER BY name")
    fun countries(): Flow<List<CountryRow>>

    @Upsert
    suspend fun putCountry(row: CountryRow)

    @Query("DELETE FROM countries WHERE name = :name")
    suspend fun deleteCountry(name: String)
}

fun ProductRow.toCore() = Product(barcode, name, brand, Nutrients(kcal, protein, carbs, fat), servingGrams, servingLabel)

/** Reading a country file into the database, a few thousand products at a time. */
object CountryFiles {
    class NotACountryFile : Exception("This isn't a country file. Download one from the app's Releases page, under Country files.")

    /**
     * Adds the products in the file at [uri], replacing any earlier copy of the same country.
     * [progress] is called now and then with the number of products read so far. Gives back the
     * country's name and how many products it holds.
     */
    suspend fun import(db: AppDatabase, resolver: ContentResolver, uri: Uri, now: Long, progress: (Int) -> Unit): Pair<String, Int> =
        withContext(Dispatchers.IO) {
            val dao = db.productDao()
            val raw = BufferedInputStream(resolver.openInputStream(uri) ?: throw NotACountryFile())
            raw.mark(4)
            val gz = raw.read() == 0x1f && raw.read() == 0x8b
            raw.reset()
            val stream = if (gz) GZIPInputStream(raw, 1 shl 16) else raw
            stream.bufferedReader(Charsets.UTF_8).use { reader ->
                val country = Products.countryOf(reader.readLine() ?: "") ?: throw NotACountryFile()
                db.withTransaction {
                    dao.deleteCountryProducts(country)
                    dao.deleteCountry(country)
                }
                var count = 0
                val batch = ArrayList<ProductRow>(BATCH)
                suspend fun flush() {
                    if (batch.isEmpty()) return
                    val rows = batch.toList()
                    db.withTransaction { dao.insertProducts(rows) }
                    batch.clear()
                    progress(count)
                }
                while (true) {
                    val line = reader.readLine() ?: break
                    val p = Products.parseLine(line) ?: continue
                    batch += ProductRow(
                        p.barcode, country, p.name, p.brand, p.per100.kcal, p.per100.protein, p.per100.carbs, p.per100.fat,
                        p.servingGrams, p.servingLabel,
                    )
                    count++
                    if (batch.size >= BATCH) flush()
                }
                flush()
                dao.putCountry(CountryRow(country, count, now))
                country to count
            }
        }

    private const val BATCH = 5000
}
