package de.fhkiel.temi.robogguide

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.robotemi.sdk.Robot
import com.robotemi.sdk.TtsRequest
import com.robotemi.sdk.listeners.OnGoToLocationStatusChangedListener
import org.json.JSONObject
import de.fhkiel.temi.robogguide.database.DatabaseHelper

class IndividualGuideActivity : AppCompatActivity() {
    private val selectedItems = mutableListOf<String>()
    private lateinit var tourHelper: TourHelper
    private lateinit var database: DatabaseHelper
    private var mRobot: Robot? = null
    private var route: List<String> = listOf()
    private var currentIndex = 0
    private val TAG = "IndividualGuideActivity-Tour"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_individual_guide)

        // Initialisiere die Datenbank und den TourHelper
        database = DatabaseHelper.getInstance(this, "roboguide.db")
        database.initializeDatabase()
        tourHelper = TourHelper(database)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.individual_guide_view)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Hole die Location-Daten und baue die UI
        val locationsMap = intent.getSerializableExtra(EXTRA_LOCATIONS) as? HashMap<String, String>
        val locations = locationsMap?.mapValues { JSONObject(it.value) } ?: emptyMap()

        val container = findViewById<LinearLayout>(R.id.containerLayout)
        locations.forEach { (key, jsonObject) ->
            val itemLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(16, 24, 16, 24)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setBackgroundColor(Color.WHITE)
                isClickable = true
                setOnClickListener {
                    if (selectedItems.contains(key)) {
                        selectedItems.remove(key)
                        setBackgroundColor(Color.WHITE)
                    } else {
                        selectedItems.add(key)
                        setBackgroundColor(Color.GREEN)
                    }
                }
            }

            val textView = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                text = jsonObject["name"].toString()
                textSize = 18f
            }
            itemLayout.addView(textView)
            container.addView(itemLayout)

            val divider = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2)
                setBackgroundColor(Color.GRAY)
            }
            container.addView(divider)
        }

        // Starte die individuelle Tour
        findViewById<Button>(R.id.btnStart).setOnClickListener {
            if (selectedItems.isNotEmpty()) {
                route = tourHelper.planIndividualRoute(selectedItems)
                if (route.isNotEmpty()) {
                    startTour()
                } else {
                    Log.e(TAG, "Keine gültige Route gefunden, Navigation nicht möglich.")
                }
            } else {
                Log.w(TAG, "Keine Locations ausgewählt!")
            }
        }

        findViewById<Button>(R.id.btnBack).setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }
    }

    // Startet die Tour mit den ausgewählten Locations
    private fun startTour() {
        currentIndex = 0
        mRobot = Robot.getInstance()
        Log.i(TAG, "Individuelle Tour gestartet.")

        val listener = object : OnGoToLocationStatusChangedListener {
            override fun onGoToLocationStatusChanged(location: String, status: String, descriptionId: Int, description: String) {
                Log.d(TAG, "Statusänderung bei $location - Status: $status, Beschreibung: $description")

                when (status) {
                    "complete" -> {
                        Log.i(TAG, "Position erreicht: $location")
                        speakText("Ich bin bei $location angekommen.")
                        currentIndex++
                        if (currentIndex < route.size) {
                            navigateToNextLocation()
                        } else {
                            Log.i(TAG, "Tour abgeschlossen.")
                            speakText("Die Tour ist abgeschlossen.")
                            mRobot?.removeOnGoToLocationStatusChangedListener(this)
                        }
                    }
                    "abort" -> retryNavigation(location, this)
                    else -> Log.d(TAG, "Nicht behandelter Status: $status bei $location")
                }
            }
        }

        mRobot?.addOnGoToLocationStatusChangedListener(listener)
        if (route.isNotEmpty()) {
            navigateToNextLocation()
        } else {
            Log.w(TAG, "Die Route ist leer, Tour kann nicht gestartet werden.")
        }
    }

    // Navigiert zur nächsten Location in der Route
    private fun navigateToNextLocation() {
        val nextLocation = route[currentIndex]
        Log.i(TAG, "Navigiere zu: $nextLocation")
        speakText("Ich navigiere jetzt zu $nextLocation.")
        mRobot?.goTo(nextLocation)
    }

    // Versucht, die Navigation bei einem Abbruch neu zu starten
    private fun retryNavigation(location: String, listener: OnGoToLocationStatusChangedListener) {
        var retryCount = 0
        val maxRetries = 3
        val retryDelayMillis = 5000L

        if (retryCount < maxRetries) {
            retryCount++
            Log.w(TAG, "Navigation zu $location abgebrochen. Versuch $retryCount von $maxRetries in ${retryDelayMillis / 1000} Sekunden.")
            Handler(Looper.getMainLooper()).postDelayed({
                mRobot?.goTo(location)
            }, retryDelayMillis)
        } else {
            Log.e(TAG, "Navigation zu $location fehlgeschlagen nach $maxRetries Versuchen. Fortsetzung der Tour.")
            retryCount = 0
            currentIndex++
            if (currentIndex < route.size) {
                navigateToNextLocation()
            } else {
                Log.i(TAG, "Tour abgeschlossen.")
                mRobot?.removeOnGoToLocationStatusChangedListener(listener)
            }
        }
    }

    // Gibt Text über den Temi-Roboter aus
    private fun speakText(text: String, isShowOnConversationLayer: Boolean = true) {
        mRobot?.let { robot ->
            val ttsRequest: TtsRequest = TtsRequest.create(speech = text, isShowOnConversationLayer = isShowOnConversationLayer)
            robot.speak(ttsRequest)
        }
    }

    companion object {
        private const val EXTRA_LOCATIONS = "extra_locations"

        // Methode zum Erstellen eines Intents mit den übergebenen Daten
        fun newIntent(context: Context, locations: Map<String, JSONObject>): Intent {
            val intent = Intent(context, IndividualGuideActivity::class.java)
            val locationsMap = HashMap<String, String>()
            locations.forEach { (key, jsonObject) ->
                locationsMap[key] = jsonObject.toString()
            }
            intent.putExtra(EXTRA_LOCATIONS, locationsMap)
            return intent
        }
    }
}
