package de.fhkiel.temi.robogguide

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.robotemi.sdk.Robot
import java.net.URL
import kotlin.concurrent.thread

class ExecutionActivity : AppCompatActivity() {
    private lateinit var tvDescription: TextView
    private lateinit var ivAreaImage: ImageView
    private lateinit var progressBar: ProgressBar

    private val textReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val spokenText = intent?.getStringExtra("EXTRA_SPOKEN_TEXT")
            tvDescription.text = spokenText
        }
    }

    private val mediaReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val mediaUrl = intent?.getStringExtra("EXTRA_MEDIA_URL")
            mediaUrl?.let { url ->
                loadImageFromUrl(url)
            }
        }
    }
    private val progressReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val progress = intent?.getIntExtra("EXTRA_PROGRESS", 0) ?: 0
            progressBar.progress = progress
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_execution)
        tvDescription = findViewById(R.id.tvDescription)
        ivAreaImage = findViewById(R.id.ivAreaImage)
        progressBar = findViewById(R.id.progressBar)



        // Registriere den BroadcastReceiver
        registerReceiver(textReceiver, IntentFilter("ACTION_UPDATE_SPOKEN_TEXT"),
            RECEIVER_NOT_EXPORTED)
        registerReceiver(mediaReceiver, IntentFilter("ACTION_UPDATE_MEDIA"),
            RECEIVER_NOT_EXPORTED)
        registerReceiver(progressReceiver, IntentFilter("ACTION_UPDATE_PROGRESS"),
            RECEIVER_NOT_EXPORTED
        )


        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.execution_view)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<Button>(R.id.btnPause).setOnClickListener {
            //to Do
        }

        findViewById<Button>(R.id.btnSkip).setOnClickListener {
            //to Do
        }

        findViewById<Button>(R.id.btnExit).setOnClickListener {
            val intent = Intent(this, RatingActivity::class.java)
            startActivity(intent)
            Robot.getInstance().goTo("home base")
        }
    }
    private fun loadImageFromUrl(url: String) {
        thread {
            try {
                val bitmap = BitmapFactory.decodeStream(URL(url).openStream())
                runOnUiThread {
                    ivAreaImage.setImageBitmap(bitmap)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        // Unregister the receiver to avoid memory leaks
        unregisterReceiver(textReceiver)
        unregisterReceiver(mediaReceiver)
        unregisterReceiver(progressReceiver)

    }
}
