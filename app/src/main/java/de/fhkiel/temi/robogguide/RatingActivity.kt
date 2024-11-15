package de.fhkiel.temi.robogguide

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class RatingActivity : AppCompatActivity() {
    private var tourService: TourService? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val tourBinder = binder as TourService.TourBinder
            tourService = tourBinder.getService()
            isBound = true
            Log.d("ExecutionActivity", "TourService verbunden")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            tourService = null
            isBound = false
            Log.d("ExecutionActivity", "TourService getrennt")
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val serviceIntent = Intent(this, TourService::class.java)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
        setContentView(R.layout.activity_rating)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.RatingView)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        findViewById<Button>(R.id.btnContinue).setOnClickListener {
            finish()
            tourService!!.reset()
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }
        findViewById<Button>(R.id.btnSkipRating).setOnClickListener {

        }
    }
}