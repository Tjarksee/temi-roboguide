package de.fhkiel.temi.robogguide

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import de.fhkiel.temi.robogguide.database.DatabaseHelper
import org.json.JSONObject

class PlaceSelectionActivity : AppCompatActivity() {
    private lateinit var database: DatabaseHelper
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var places: Map<String, JSONObject>
    private var selectedPlace: String? = null
    private val TAG = "PlaceSelectionActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_place_selection)

        // Initialize SharedPreferences and Database
        sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        database = DatabaseHelper.getInstance(this, "roboguide.db")
        database.initializeDatabase()

        // UI References
        val tvSelectPlace = findViewById<TextView>(R.id.tvSelectPlace)
        val lvPlaces = findViewById<ListView>(R.id.lvPlaces)
        val btnConfirmPlace = findViewById<Button>(R.id.btnConfirmPlace)

        // Load places from the database
        places = database.getAllPlaces()

        if (places.isEmpty()) {
            tvSelectPlace.text = "Keine Orte verfügbar. Bitte prüfen Sie die Datenbank."
            btnConfirmPlace.isEnabled = false
            return
        }

        // Populate ListView with place names
        val placeNames = places.values.map { it.optString("name") }
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_single_choice, placeNames)
        lvPlaces.adapter = adapter
        lvPlaces.choiceMode = ListView.CHOICE_MODE_SINGLE

        // Handle ListView item selection
        lvPlaces.setOnItemClickListener { _, _, position, _ ->
            selectedPlace = placeNames[position]
            btnConfirmPlace.isEnabled = selectedPlace != null
        }

        // Handle Confirm button click
        btnConfirmPlace.setOnClickListener {
            if (selectedPlace != null) {
                val placeId = database.getPlaceIdByName(selectedPlace!!)
                if (placeId != null) {
                    // Save selected place to SharedPreferences
                    sharedPreferences.edit()
                        .putInt("PLACE_ID", placeId)
                        .apply()

                    // Start MainActivity
                    val intent = Intent(this, MainActivity::class.java)
                    startActivity(intent)
                    finish()
                } else {
                    Log.e(TAG, "Ungültige Place-ID für: $selectedPlace")
                }
            }
        }
    }
}
