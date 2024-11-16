package de.fhkiel.temi.robogguide

import TourViewModel
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import com.robotemi.sdk.Robot
import java.net.URL
import kotlin.concurrent.thread

class ExecutionActivity : AppCompatActivity() {
    private lateinit var viewModel: TourViewModel
    private lateinit var tvDescription: TextView
    private lateinit var tvHeading: TextView
    private lateinit var ivAreaImage: ImageView
    private lateinit var progressBar: ProgressBar
    private var mRobot: Robot? = null
    private var isRunning = true
    private var tourService: TourService? = null




    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        viewModel = ViewModelProvider(this)[TourViewModel::class.java]
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_execution)
        tvHeading = findViewById(R.id.tvCurrentArea)
        tvDescription = findViewById(R.id.tvDescription)
        ivAreaImage = findViewById(R.id.ivAreaImage)
        progressBar = findViewById(R.id.progressBar)
        mRobot = Robot.getInstance()
        observeLiveData()



        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.execution_view)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val btnPauseContinue: Button = findViewById(R.id.btnPauseContinue)
        fun updateButtonState() {
            if (isRunning) {
                btnPauseContinue.text = "Pause"
                btnPauseContinue.setBackgroundColor(ContextCompat.getColor(this, R.color.pauseButton))  // Button auf Orange setzen
            } else {
                btnPauseContinue.text = "Weiter"
                btnPauseContinue.setBackgroundColor(ContextCompat.getColor(this, R.color.continueButton))  // Button auf Grün setzen
            }
        }

        findViewById<Button>(R.id.btnPauseContinue).setOnClickListener {
            updateButtonState()
            if(isRunning){
                isRunning = false
                tourService!!.pauseTour()
            }else{
                isRunning = true
                tourService!!.continueTour()
            }
        }

        findViewById<Button>(R.id.btnSkip).setOnClickListener {
            tourService!!.skip()
        }

        findViewById<Button>(R.id.btnExit).setOnClickListener {
            tourService!!.endTour()
        }
    }
    private fun observeLiveData() {
        // Beobachte das Textfeld
        viewModel.text.observe(this) { newText ->
            tvDescription.text = newText
        }

        // Beobachte das Heading
        viewModel.heading.observe(this) { newHeading ->
            tvHeading.text = newHeading
        }

        // Beobachte das Bild
        viewModel.imageUrl.observe(this) { imageUrl ->
            imageUrl?.let {
                loadImageFromUrl(it)
            }
        }

        // Beobachte den Fortschritt
        viewModel.progress.observe(this) { progress ->
            progressBar.progress = progress
        }
    }


    private fun loadImageFromUrl(url: String) {
        thread {
            try {
                Log.d("ExecutionActivity", "Lade Bild von URL: $url")
                val bitmap = BitmapFactory.decodeStream(URL(url).openStream())
                runOnUiThread {
                    Log.d("ExecutionActivity", "Bild erfolgreich geladen von URL: $url")
                    ivAreaImage.setImageBitmap(bitmap)
                }
            } catch (e: Exception) {
                Log.e("ExecutionActivity", "Fehler beim Laden des Bildes von URL: $url", e)
            }
        }
    }


    override fun onDestroy() {
        super.onDestroy()

    }

}
