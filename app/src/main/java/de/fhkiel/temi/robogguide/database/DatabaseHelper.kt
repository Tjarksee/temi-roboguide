package de.fhkiel.temi.robogguide.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.sql.Types.NULL

class DatabaseHelper(context: Context, private val databaseName: String) : SQLiteOpenHelper(context, databaseName, null, 1) {

    private val databasePath = File(context.getDatabasePath(databaseName).path).absolutePath
    private val databaseFullPath = "$databasePath$databaseName"
    private var database: SQLiteDatabase? = null
    private val appContext: Context = context
    private var dbFile: File? = null

    override fun onCreate(db: SQLiteDatabase) {}

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    @Suppress("unused")
    fun getDatabase(): SQLiteDatabase?{
        return database
    }

    @Suppress("unused")
    fun getDBFile(): File?{
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
    fun initializeDatabase(withOpen: Boolean = true){
        this.readableDatabase // Create an empty database in the default system path
        dbFile = copyDatabase() // Overwrite the existing database with the one from assets

        if (withOpen) {
            openDatabase() // Open the copied database
        }
    }

    // Method to open the copied database and store its reference
    private fun openDatabase(): SQLiteDatabase? {
        database = SQLiteDatabase.openDatabase(databaseFullPath, null, SQLiteDatabase.OPEN_READWRITE)
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

    fun getLocationDataAsJson(mapName: String): Map<String, JSONObject>{
        val placesId = getSingleValueFromTable("places","SELECT `id` FROM `places` WHERE name=$mapName").toInt()
        val jsonMap = getTableDataAsJson("locations","SELECT * FROM `locations` WHERE places_id=$placesId")
        return jsonMap
    }

    fun getDbWithDynamicIds(dbIds: List<String>, columnNames: List<String>, tableName: String): Map<String, JSONObject> {
        val limitedIds = dbIds.take(3)
        val limitedColumns = columnNames.take(3)
        if (limitedIds.size != limitedColumns.size) {
            throw IllegalArgumentException("Die Anzahl der IDs und der Spaltennamen muss übereinstimmen!")
        }
        val whereClause = limitedIds.zip(limitedColumns).joinToString(separator = " AND ") { (id, column) -> "$column=$id" }
        val query = "SELECT * FROM `$tableName` WHERE $whereClause"
        val values = getTableDataAsJson(tableName, query)
        return values
    }




    fun getSingleValueFromTable(tableName:String, query:String ) : String{
        var value = ""
        val(columns, primaryKey) = getTableStructure("items")
        database?.let { db ->
            val cursor = db.rawQuery("SELECT ${columns[0]} " + query, arrayOf(null))
            if (cursor.moveToFirst()) {
                value = cursor.getString(cursor.getColumnIndexOrThrow(columns[0]))
            }
            cursor.close()
        }
        if (value == ""){
            throw IllegalArgumentException("$query was not successful for $tableName .")
        }
        return value
    }
    // Method to read data dynamically and convert it into a JSON Map using the primary key as the key
    @Suppress("unused")
    fun getTableDataAsJson(tableName: String, query:String): Map<String, JSONObject> {
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
}
