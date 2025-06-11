package com.example.aquameasure_app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
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
    private val handler = Handler(Looper.getMainLooper())
    private val intervalo: Long = 300000
    private var ultimoPorcentajeAltoNotificado: Double = -1.0
    private var ultimoPorcentajeBajoNotificado: Double = -1.0
    private var notificadoLleno = false
    private var notificadoVacio = false


    private lateinit var queue: RequestQueue  // Mantener una sola instancia

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
        val servicioIntent = Intent(this, NivelAguaService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(servicioIntent)
        } else {
            startService(servicioIntent)
        }
        crearCanalNotificacion()
        actualizarDatosPeriodicamente()
        btnGraficas.setOnClickListener {
            val intent = Intent(this, GraficasActivity::class.java)
            startActivity(intent)
        }
        val linkWeb: TextView = findViewById(R.id.linkWeb)
        linkWeb.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aquameasure.onrender.com"))
            startActivity(intent)
        }

        val linkDocs: TextView = findViewById(R.id.linkDocs)
        linkDocs.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/CesarUrrutia4383/aquameasure_dashboard"))
            startActivity(intent)
        }
        val linkDocs2: TextView = findViewById(R.id.linkDocs2)
        linkDocs2.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/CesarUrrutia4383/aquameasure_app_full_v1"))
            startActivity(intent)
        }
    }

    private fun actualizarDatosPeriodicamente() {
        handler.post(object : Runnable {
            override fun run() {
                obtenerDatos()
                handler.postDelayed(this, intervalo)
            }
        })
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

                    // NOTIFICACIÓN ALTA

                }
                progressBar.visibility = View.GONE
            },
            { error ->
                error.printStackTrace()
                progressBar.visibility = View.GONE
            })

        // AUMENTAR TIEMPO DE ESPERA
        request.retryPolicy = DefaultRetryPolicy(
            15000, // tiempo de espera: 15 segundos
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
