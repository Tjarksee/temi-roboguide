package de.fhkiel.temi.robogguide.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import de.fhkiel.temi.robogguide.Location
import de.fhkiel.temi.robogguide.TourHelper
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class DatabaseHelper(context: Context, private val databaseName: String) : SQLiteOpenHelper(context, databaseName, null, 1) {

    private val databasePath = File(context.getDatabasePath(databaseName).path).absolutePath
    private val databaseFullPath = "$databasePath$databaseName"
    private var database: SQLiteDatabase? = null
    private val appContext: Context = context
    private var dbFile: File? = null

    companion object {
        @Volatile
        private var instance: DatabaseHelper? = null

        fun getInstance(context: Context, databaseName: String): DatabaseHelper {
            return instance ?: synchronized(this) {
                instance ?: DatabaseHelper(context.applicationContext, databaseName).also { instance = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {}

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    @Suppress("unused")
    fun getDatabase(): SQLiteDatabase? {
        return database
    }

    @Suppress("unused")
    fun getDBFile(): File? {
        return dbFile
    }

    /**
     * Method to copy the database from assets to internal storage (always overwrites existing database)
     * @return  output File or null
     */
    @Throws(IOException::class)
    private fun copyDatabase(): File {
        val inputStream: InputStream = appContext.assets.open(databaseName)
        val outFileName = databaseFullPath
        val outFile = File(outFileName)
        val outputStream: OutputStream = FileOutputStream(outFile)

        val buffer = ByteArray(1024)
        var length: Int
        while (inputStream.read(buffer).also { length = it } > 0) {
            outputStream.write(buffer, 0, length)
        }

        outputStream.flush()
        outputStream.close()
        inputStream.close()

        return outFile
    }

    /**
     * Method to initialize the database (always copies and overwrites any existing database)
     * @param withOpen  Set to false, to only copy the database, otherwise it is also opened (default)
     */
    @Throws(IOException::class)
    fun initializeDatabase(withOpen: Boolean = true) {
        this.readableDatabase // Create an empty database in the default system path
        dbFile = copyDatabase() // Overwrite the existing database with the one from assets

        if (withOpen) {
            openDatabase() // Open the copied database
        }
    }

    @Suppress("unused")
    fun getTableDataAsJson(tableName: String): Map<String, JSONObject> {
        val (columns, primaryKey) = getTableStructure(tableName)

        if (primaryKey == null) {
            throw IllegalArgumentException("Table $tableName has no primary key.")
        }

        val jsonMap = mutableMapOf<String, JSONObject>()

        database?.let { db ->
            val cursor = db.rawQuery("SELECT * FROM `$tableName`", null)

            if (cursor.moveToFirst()) {
                do {
                    val jsonObject = JSONObject()
                    var primaryKeyValue: String? = null

                    for (column in columns) {
                        val value = cursor.getString(cursor.getColumnIndexOrThrow(column))
                        jsonObject.put(column, value)

                        // Check if this column is the primary key and save its value
                        if (column == primaryKey) {
                            primaryKeyValue = value
                        }
                    }

                    // Ensure the primary key value is not null before adding to the map
                    primaryKeyValue?.let {
                        jsonMap[it] = jsonObject
                    }
                } while (cursor.moveToNext())
            }
            cursor.close()
        }

        return jsonMap
    }


    // Method to open the copied database and store its reference
    private fun openDatabase(): SQLiteDatabase? {
        database =
            SQLiteDatabase.openDatabase(databaseFullPath, null, SQLiteDatabase.OPEN_READWRITE)
        return database
    }

    // Method to close the opened database
    fun closeDatabase() {
        database?.close()
    }

    // Method to retrieve the table structure dynamically, including the primary key
    private fun getTableStructure(tableName: String): Pair<List<String>, String?> {
        val columns = mutableListOf<String>()
        var primaryKey: String? = null

        database?.let { db ->
            val cursor = db.rawQuery("PRAGMA table_info(`$tableName`)", null)

            if (cursor.moveToFirst()) {
                do {
                    // Retrieve the column name from the result
                    val columnName = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                    columns.add(columnName)

                    // Check if this column is the primary key
                    val isPrimaryKey = cursor.getInt(cursor.getColumnIndexOrThrow("pk")) == 1
                    if (isPrimaryKey) {
                        primaryKey = columnName
                    }
                } while (cursor.moveToNext())
            }
            cursor.close()
        }

        return Pair(columns, primaryKey)
    }

    fun getLocationDataAsJson(): Map<String, JSONObject> {
        val jsonMap = getTableDataAsJsonWithQuery(
            "locations",
            "SELECT * FROM `locations` WHERE places_id='2'"
        )
        return jsonMap
    }
    fun getTransferDataAsJson(): Map<String, JSONObject> {
        val jsonMap = getTableDataAsJsonWithQuery(
            "transfers",
            "SELECT * FROM `transfers`"
        )
        Log.d("DatabaseHelper", "Transfer data: $jsonMap")
        return jsonMap
    }


    fun getDbWithDynamicIds(
        dbIds: List<String>,
        columnNames: List<String>,
        tableName: String
    ): Map<String, JSONObject> {
        val limitedIds = dbIds.take(3)
        val limitedColumns = columnNames.take(3)
        if (limitedIds.size != limitedColumns.size) {
            throw IllegalArgumentException("Die Anzahl der IDs und der Spaltennamen muss übereinstimmen!")
        }
        val whereClause = limitedIds.zip(limitedColumns)
            .joinToString(separator = " AND ") { (id, column) -> "$column=$id" }
        val query = "SELECT * FROM `$tableName` WHERE $whereClause"
        val values = getTableDataAsJsonWithQuery(tableName, query)
        return values
    }


    // Method to read data dynamically and convert it into a JSON Map using the primary key as the key
    @Suppress("unused")
    fun getTableDataAsJsonWithQuery(tableName: String, query: String): Map<String, JSONObject> {
        val (columns, primaryKey) = getTableStructure(tableName)

        if (primaryKey == null) {
            throw IllegalArgumentException("Table $tableName has no primary key.")
        }

        val jsonMap = mutableMapOf<String, JSONObject>()

        database?.let { db ->
            val cursor = db.rawQuery(query, null)

            if (cursor.moveToFirst()) {
                do {
                    val jsonObject = JSONObject()
                    var primaryKeyValue: String? = null

                    for (column in columns) {
                        val value = cursor.getString(cursor.getColumnIndexOrThrow(column))
                        if (value != null) {
                            jsonObject.put(column, value)
                        } else {
                            Log.w("DatabaseHelper", "Value for column $column is null or missing")
                            jsonObject.put(column, JSONObject.NULL) // Falls der Wert null ist, explizit NULL setzen
                        }

                        // Überprüfen, ob die Spalte der Primärschlüssel ist und den Wert speichern
                        if (column == primaryKey) {
                            primaryKeyValue = value
                        }
                    }

                    // Sicherstellen, dass der Primärschlüsselwert nicht null ist, bevor zum Map hinzugefügt wird
                    primaryKeyValue?.let {
                        jsonMap[it] = jsonObject
                    } ?: Log.e("DatabaseHelper", "Primary key for table $tableName is null")
                } while (cursor.moveToNext())
            } else {
                Log.w("DatabaseHelper", "Query returned no results: $query")
            }
            cursor.close()
        }

        Log.d("DatabaseHelper", "Loaded JSON data: $jsonMap")
        return jsonMap
    }


    fun getLocationName(locationId: String): String {
        val locationQuery = "SELECT * FROM `locations` WHERE `id` = '$locationId'"
        val locations = getTableDataAsJsonWithQuery("locations", locationQuery)
        return locations.values.firstOrNull()?.optString("name", "")?.lowercase() ?: ""
    }

    fun getImportantLocations(): Collection<JSONObject>{
        val query = "SELECT * FROM locations WHERE important = 1"
        return getTableDataAsJsonWithQuery("locations", query).values
    }

    fun getItems(locationsId: String): List<JSONObject?>{
        val query = "SELECT * FROM items WHERE locations_id = $locationsId"
        return getTableDataAsJsonWithQuery("items",query).values.toList()
    }

    fun getLocations(): Collection<JSONObject?> {
        val query = "SELECT * FROM locations"
        return getTableDataAsJsonWithQuery("locations", query).values
    }

    fun getTransferId(fromId : String) : String{
        val query = "SELECT * FROM transfers WHERE location_from = '$fromId'"
        val test = getTableDataAsJsonWithQuery("transfers", query)
        return test.values.firstOrNull()?.get("id").toString()
    }

    fun getTransferText(location: Location) : JSONObject? {
        val query = "SELECT * FROM texts WHERE transfers_id = '${location.transferId}'"
        return getTableDataAsJsonWithQuery("texts", query).values.firstOrNull()
    }
    fun getItemTexts(itemId : String) : JSONObject? {
        val query = "SELECT * FROM texts WHERE items_id = $itemId"
        return getTableDataAsJsonWithQuery("texts", query).values.firstOrNull()
    }
    fun getMedia(textId: String): String? {
        val query = "SELECT * FROM media WHERE texts_id = '$textId'"
        val mediaData = getTableDataAsJsonWithQuery("media", query)
        return mediaData.values.firstOrNull()?.optString("url", null)
    }




}
