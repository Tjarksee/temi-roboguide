package de.fhkiel.temi.robogguide

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.robotemi.sdk.Robot
import com.robotemi.sdk.TtsRequest
import com.robotemi.sdk.listeners.OnGoToLocationStatusChangedListener
import org.json.JSONObject
import de.fhkiel.temi.robogguide.database.DatabaseHelper

class IndividualGuideActivity : AppCompatActivity() {
    private var tourService: TourService? = null
    private var isBound = false
    private val selectedItems = mutableListOf<String>()
    private lateinit var database: DatabaseHelper
    private val TAG = "IndividualGuideActivity-Tour"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_individual_guide)

        // Initialisiere die Datenbank und den TourHelper
        database = DatabaseHelper.getInstance(this, "roboguide.db")
        database.initializeDatabase()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.individual_guide_view)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        val selectedItemAmountTextView = findViewById<TextView>(R.id.selectedItemAmount)

        // Hole die Location-Daten und baue die UI
        val locationsMap = intent.getSerializableExtra(EXTRA_LOCATIONS) as? HashMap<String, String>
        val locations = locationsMap?.mapValues { JSONObject(it.value) } ?: emptyMap()

        val container = findViewById<LinearLayout>(R.id.containerLayout)
        val startButton = findViewById<Button>(R.id.btnStart)
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
                    updateSelectedItemCount(selectedItemAmountTextView,startButton)
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



        findViewById<Button>(R.id.btnStart).setOnClickListener {
            if (selectedItems.isNotEmpty()) {
                tourService!!.setIndividualRoute(selectedItems)
                if (tourService!!.isRouteEmpty()) {
                    tourService!!.startTour("individual")
                    val intent = Intent(this, ExecutionActivity::class.java)
                    startActivity(intent)
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
    private fun updateSelectedItemCount(textView: TextView, startButton: Button) {
        val count = selectedItems.size
        "Du hast aktuell $count Location${if (count == 1 || count == 0) "" else "s"} ausgewählt".also { textView.text = it }
        if(count>0){
            startButton.setBackgroundColor(ContextCompat.getColor(this, R.color.continueButton))
            startButton.isClickable = true
        }else{
            startButton.setBackgroundColor(ContextCompat.getColor(this, R.color.radio_button_unchecked))
            startButton.isClickable = false
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as TourService.TourBinder
            tourService = binder.getService()
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName) {
            isBound = false
            tourService = null
        }
    }

    override fun onStart() {
        super.onStart()
        Intent(this, TourService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }


    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(connection)
            isBound = false
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
