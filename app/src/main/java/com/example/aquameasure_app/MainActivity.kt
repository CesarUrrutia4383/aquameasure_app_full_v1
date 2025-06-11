package com.example.aquameasure_app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.*
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import com.android.volley.DefaultRetryPolicy
import com.android.volley.RequestQueue
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var textFecha: TextView
    private lateinit var textTemp: TextView
    private lateinit var textNivel: TextView
    private lateinit var textDist: TextView
    private lateinit var textCantidad: TextView
    private lateinit var textPorcentaje: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnGraficas: Button

    private val url = "https://aquameasure-esp32.onrender.com/ver"
    private var handler: Handler? = null
    private val intervalo: Long = 300000 // 5 minutos

    private lateinit var queue: RequestQueue

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textFecha = findViewById(R.id.textFecha)
        textTemp = findViewById(R.id.textTemp)
        textNivel = findViewById(R.id.textNivel)
        textDist = findViewById(R.id.textDist)
        textCantidad = findViewById(R.id.textCantidad)
        textPorcentaje = findViewById(R.id.textPorcentaje)
        progressBar = findViewById(R.id.progressBar)
        btnGraficas = findViewById(R.id.btnGraficas)

        queue = Volley.newRequestQueue(this)
        crearCanalNotificacion()
        iniciarActualizacionPeriodica()

        val servicioIntent = Intent(this, NivelAguaService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(servicioIntent)
        } else {
            startService(servicioIntent)
        }

        btnGraficas.setOnClickListener {
            startActivity(Intent(this, GraficasActivity::class.java))
        }

        findViewById<TextView>(R.id.linkWeb).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aquameasure.onrender.com")))
        }
        findViewById<TextView>(R.id.linkDocs).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/CesarUrrutia4383/aquameasure_dashboard")))
        }
        findViewById<TextView>(R.id.linkDocs2).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/CesarUrrutia4383/aquameasure_app_full_v1")))
        }
    }

    private fun iniciarActualizacionPeriodica() {
        handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                obtenerDatos()
                handler?.postDelayed(this, intervalo)
            }
        }
        handler?.post(runnable)
    }

    private fun obtenerDatos() {
        progressBar.visibility = View.VISIBLE

        val request = JsonArrayRequest(url,
            { response ->
                if (response.length() > 0) {
                    val lastObject = response.getJSONObject(response.length() - 1)

                    val fechaRaw = lastObject.get("fecha")
                    val temp = lastObject.getDouble("temp")
                    val nivel = lastObject.getDouble("nivelAgua")
                    val dist = lastObject.getDouble("distancia")
                    val cantidad = lastObject.getDouble("cantidadAgua")
                    val porcentaje = lastObject.getDouble("porcentajeLlenado")

                    val fechaFormateada = parsearFecha(fechaRaw)
                    val porcentajeStr = String.format("%.2f", porcentaje) + "%"

                    textFecha.text = "Fecha: $fechaFormateada"
                    textTemp.text = "Temperatura: ${"%.2f".format(temp)} °C"
                    textNivel.text = "Nivel de Agua: ${"%.2f".format(nivel)} cm"
                    textDist.text = "Distancia: ${"%.2f".format(dist)} cm"
                    textCantidad.text = "Cantidad de Agua: ${"%.2f".format(cantidad)} L"
                    textPorcentaje.text = "Porcentaje de Llenado: $porcentajeStr"
                }
                progressBar.visibility = View.GONE
            },
            { error ->
                error.printStackTrace()
                progressBar.visibility = View.GONE
            })

        request.retryPolicy = DefaultRetryPolicy(
            15000,
            DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
            DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        )

        queue.add(request)
    }

    private fun parsearFecha(fechaObj: Any): String {
        return try {
            when (fechaObj) {
                is JSONObject -> {
                    val seconds = fechaObj.getLong("seconds")
                    val date = Date(seconds * 1000)
                    val format = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                    format.format(date)
                }
                is String -> {
                    val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
                    isoFormat.timeZone = TimeZone.getTimeZone("UTC")
                    val fecha = isoFormat.parse(fechaObj)
                    val localFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                    localFormat.format(fecha ?: Date())
                }
                else -> "Fecha inválida"
            }
        } catch (e: Exception) {
            Log.e("FECHA", "Error al parsear: ${e.message}")
            "Fecha inválida"
        }
    }

    private fun crearCanalNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(
                "alerta_nivel",
                "Alerta de nivel de agua",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifica cuando el tinaco está casi lleno"
            }
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(canal)
        }
    }
}
