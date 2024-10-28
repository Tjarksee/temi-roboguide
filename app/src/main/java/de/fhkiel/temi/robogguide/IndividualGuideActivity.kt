package de.fhkiel.temi.robogguide

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONObject
import de.fhkiel.temi.robogguide.database.DatabaseHelper

class IndividualGuideActivity : AppCompatActivity() {
    private val selectedItems = mutableListOf<String>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_individual_guide)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.individual_guide_view)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        val locationsMap = intent.getSerializableExtra(EXTRA_LOCATIONS) as? HashMap<String, String>
        val locations = locationsMap?.mapValues { JSONObject(it.value) } ?: emptyMap()


        val container = findViewById<LinearLayout>(R.id.containerLayout)
        locations.forEach { (key, jsonObject) ->
            // Erstelle ein neues horizontales LinearLayout
            val itemLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(16, 24, 16, 24)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setBackgroundColor(Color.WHITE)

                // Mach das ganze Layout klickbar
                isClickable = true
                setOnClickListener {
                    if (selectedItems.contains(key)) {
                        // Deselektieren und von der Liste entfernen
                        selectedItems.remove(key)
                        setBackgroundColor(Color.WHITE)
                    } else {
                        // Selektieren und zur Liste hinzufügen
                        selectedItems.add(key)
                        setBackgroundColor(Color.GREEN)
                    }
                }
            }


            val textView = TextView(this).apply {
                layoutParams =
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                text =jsonObject["name"].toString()
                textSize = 18f
            }


            itemLayout.addView(textView)
            container.addView(itemLayout)

            val divider = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    2  // Höhe der Trennlinie
                )
                setBackgroundColor(Color.GRAY)  // Farbe der Trennlinie
            }

            // Füge die Trennlinie zum container hinzu
            container.addView(divider)


            findViewById<Button>(R.id.btnStart).setOnClickListener {
                val intent = Intent(this, ExecutionActivity::class.java)
                startActivity(intent)
            }

            findViewById<Button>(R.id.btnBack).setOnClickListener {
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
            }
        }
    }
    companion object {
        private const val EXTRA_LOCATIONS = "extra_locations"

        // Methode zum Erstellen eines Intents mit den übergebenen Daten
        fun newIntent(context: Context, locations: Map<String, JSONObject>): Intent {
            val intent = Intent(context, IndividualGuideActivity::class.java)

            // Map<String, JSONObject> in HashMap<String, String> konvertieren
            val locationsMap = HashMap<String, String>()
            locations.forEach { (key, jsonObject) ->
                locationsMap[key] = jsonObject.toString() // JSONObject in String konvertieren
            }

            intent.putExtra(EXTRA_LOCATIONS, locationsMap)
            return intent
        }
    }
}