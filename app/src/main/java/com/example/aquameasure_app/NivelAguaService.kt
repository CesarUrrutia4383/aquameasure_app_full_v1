package com.example.aquameasure_app

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject
import java.util.*

class NivelAguaService : Service() {

    private val url = "https://aquameasure-esp32.onrender.com/ver"
    private val handler = Handler()
    private val intervalo: Long = 300000
    private var notificadoLleno = false
    private var notificadoVacio = false// 5 minutos

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        crearCanalServicio()
        startForeground(1, crearNotificacionInicial())
        handler.post(ejecutarTarea)
        return START_STICKY
    }

    private val ejecutarTarea = object : Runnable {
        override fun run() {
            obtenerDatos()
            handler.postDelayed(this, intervalo)
        }
    }

    private fun obtenerDatos() {
        val queue = Volley.newRequestQueue(this)
        val request = JsonArrayRequest(url, { response ->
            if (response.length() > 0) {
                val lastObject = response.getJSONObject(response.length() - 1)
                val porcentaje = lastObject.getDouble("porcentajeLlenado")
                val porcentajeStr = String.format("%.2f", porcentaje) + "%"

                if (porcentaje > 95.0) {
                    if (!notificadoLleno) {
                        enviarNotificacion("🚨 Tinaco casi lleno", "Nivel actual: $porcentajeStr", 1001)
                        notificadoLleno = true
                        notificadoVacio = false // Resetea el otro estado
                    }
                } else if (porcentaje < 20.0) {
                    if (!notificadoVacio) {
                        enviarNotificacion("⚠️ Tinaco casi vacío", "Nivel actual: $porcentajeStr", 1002)
                        notificadoVacio = true
                        notificadoLleno = false // Resetea el otro estado
                    }
                } else {
                    // Nivel en rango normal, resetea ambas alertas para futuras notificaciones
                    notificadoLleno = false
                    notificadoVacio = false
                }
            }
        }, {
            Log.e("SERVICIO", "Error de red: ${it.message}")
        })

        queue.add(request)
    }

    private fun enviarNotificacion(titulo: String, mensaje: String, id: Int) {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, "alerta_nivel")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(titulo)
            .setContentText(mensaje)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(id, notification)
    }

    private fun crearNotificacionInicial(): Notification {
        return NotificationCompat.Builder(this, "alerta_nivel")
            .setContentTitle("⏳ Monitoreando nivel de agua")
            .setContentText("El servicio está activo en segundo plano.")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun crearCanalServicio() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(
                "alerta_nivel",
                "Alerta de nivel de agua",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifica cuando el tinaco está en estado crítico."
                enableLights(true)
                enableVibration(true)
                importance = NotificationManager.IMPORTANCE_HIGH
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(canal)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
