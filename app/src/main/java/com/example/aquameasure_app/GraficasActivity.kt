package com.example.aquameasure_app

import android.app.ProgressDialog
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.Volley
import com.github.mikephil.charting.charts.*
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import org.json.JSONArray
import org.json.JSONObject
import java.time.OffsetDateTime

class GraficasActivity : AppCompatActivity() {

    private lateinit var chartTemp: LineChart
    private lateinit var chartNivel: BarChart
    private lateinit var chartPorcentaje: PieChart
    private lateinit var chartPorcentajeHistorial: CombinedChart

    private lateinit var tituloTemp: TextView
    private lateinit var tituloNivel: TextView
    private lateinit var tituloPorcentaje: TextView
    private lateinit var tituloFecha: TextView


    private lateinit var progressBar: ProgressBar

    private var ultimaFechaRegistrada: OffsetDateTime? = null
    private lateinit var progressDialog: ProgressDialog

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_graficas)

        chartTemp = findViewById(R.id.chartTemp)
        chartNivel = findViewById(R.id.chartNivel)
        chartPorcentaje = findViewById(R.id.chartPorcentaje)
        chartPorcentajeHistorial = findViewById(R.id.chartPorcentajeHistorial)

        tituloTemp = findViewById(R.id.tituloTemp)
        tituloNivel = findViewById(R.id.tituloNivel)
        tituloPorcentaje = findViewById(R.id.tituloPorcentaje)
        progressBar = findViewById(R.id.progressBarGraficas)
        tituloFecha = findViewById(R.id.tituloFecha)


        progressDialog = ProgressDialog(this).apply {
            setMessage("Graficando datos...")
            setCancelable(false)
            show()
        }

        cargarUltimoRegistro()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun cargarUltimoRegistro() {
        val url = "https://aquameasure-esp32.onrender.com/ver"
        val queue = Volley.newRequestQueue(this)

        val request = JsonArrayRequest(Request.Method.GET, url, null,
            { response: JSONArray ->

                if (response.length() == 0) {
                    mostrarMensajeSinDatos()
                    progressDialog.dismiss()
                    return@JsonArrayRequest
                }

                val listaTemp = mutableListOf<Entry>()
                val listaNivel = mutableListOf<BarEntry>()
                val listaPorcentaje = mutableListOf<Entry>()

                var fechaMasReciente: OffsetDateTime? = null

                for (i in 0 until response.length()) {
                    try {
                        val obj = response.getJSONObject(i)

                        if (!obj.has("fecha")) continue

                        val fechaStr = obj.getString("fecha")
                        val fechaParseada = OffsetDateTime.parse(fechaStr)

                        val minutos = (fechaParseada.hour * 60 + fechaParseada.minute).toFloat()
                        val temp = obj.optDouble("temp", -1.0).toFloat()
                        val nivel = obj.optDouble("nivelAgua", -1.0).toFloat()
                        val porcentaje = obj.optDouble("porcentajeLlenado", -1.0).toFloat()

                        listaTemp.add(Entry(minutos, temp))
                        listaNivel.add(BarEntry(minutos, nivel))
                        listaPorcentaje.add(Entry(minutos, porcentaje))

                        if (fechaMasReciente == null || fechaParseada.isAfter(fechaMasReciente)) {
                            fechaMasReciente = fechaParseada
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        continue
                    }
                }

                if (fechaMasReciente == null) {
                    mostrarMensajeSinDatos()
                    progressDialog.dismiss()
                    return@JsonArrayRequest
                }

                if (ultimaFechaRegistrada != null && !fechaMasReciente.isAfter(ultimaFechaRegistrada)) {
                    progressDialog.dismiss()
                    return@JsonArrayRequest
                }

                ultimaFechaRegistrada = fechaMasReciente
                tituloFecha.text = "Registros del día: ${fechaMasReciente.toLocalDate()}"
                mostrarGraficas(listaTemp, listaNivel, listaPorcentaje)

                progressDialog.dismiss()
                progressBar.visibility = View.GONE
            },
            { error ->
                error.printStackTrace()
                mostrarMensajeSinDatos()
                progressDialog.dismiss()
                progressBar.visibility = View.GONE
            })

        request.retryPolicy = DefaultRetryPolicy(
            10000,
            DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
            DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        )
        queue.add(request)
    }



    private fun mostrarMensajeSinDatos() {
        tituloTemp.text = "No hay datos recientes"
        tituloNivel.text = "No hay datos recientes"
        tituloPorcentaje.text = "No hay datos recientes"
        chartTemp.clear()
        chartNivel.clear()
        chartPorcentaje.clear()
        chartPorcentajeHistorial.clear()
    }

    private fun mostrarGraficas(temp: List<Entry>, nivel: List<BarEntry>, porcentaje: List<Entry>) {
        configurarGraficaLineaTemp(temp)
        configurarGraficaBarraNivel(nivel)
        configurarGraficaPastelPorcentaje(porcentaje)
        configurarCombinedChartPorcentaje(porcentaje)
    }

    private fun configurarEjeX(chart: com.github.mikephil.charting.charts.Chart<*>) {
        val xAxis = chart.xAxis
        xAxis.granularity = 30f
        xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val h = (value / 60).toInt()
                val m = (value % 60).toInt()
                return String.format("%02d:%02d", h, m)
            }
        }
    }

    private fun configurarGraficaLineaTemp(entries: List<Entry>) {
        val dataSet = LineDataSet(entries, "Temperatura").apply {
            color = Color.parseColor("#FF6F61")
            setCircleColor(Color.parseColor("#FF6F61"))
            circleRadius = 5f
            lineWidth = 3f
            valueTextSize = 12f
            setDrawValues(true)
        }

        chartTemp.data = LineData(dataSet)
        chartTemp.description = Description().apply { text = "Temperatura" }
        configurarEjeX(chartTemp)
        chartTemp.axisRight.isEnabled = false
        chartTemp.animateX(1000)
        chartTemp.invalidate()
        tituloTemp.text = "Historial de Temperatura"
    }

    private fun configurarGraficaBarraNivel(entries: List<BarEntry>) {
        val dataSet = BarDataSet(entries, "Nivel de Agua").apply {
            color = Color.parseColor("#4DB6AC")
            valueTextSize = 12f
            setDrawValues(true)
        }

        chartNivel.data = BarData(dataSet)
        chartNivel.description = Description().apply { text = "Nivel de Agua" }
        configurarEjeX(chartNivel)
        chartNivel.axisRight.isEnabled = false
        chartNivel.animateY(1000)
        chartNivel.invalidate()
        tituloNivel.text = "Historial de Nivel de Agua"
    }

    private fun configurarGraficaPastelPorcentaje(entries: List<Entry>) {
        val valor = entries.lastOrNull()?.y ?: 0f
        val pieEntries = listOf(
            PieEntry(valor, "Llenado"),
            PieEntry(100f - valor, "Vacío")
        )

        val dataSet = PieDataSet(pieEntries, "").apply {
            colors = listOf(Color.parseColor("#81C784"), Color.parseColor("#E0E0E0"))
            valueTextSize = 14f
            sliceSpace = 2f
            setDrawValues(true)
        }

        chartPorcentaje.data = PieData(dataSet)
        chartPorcentaje.description = Description().apply { text = "Historial de Porcentajes" }
        chartPorcentaje.animateY(1000)
        chartPorcentaje.invalidate()
        tituloPorcentaje.text = "Porcentaje actual"
    }

    private fun configurarCombinedChartPorcentaje(entries: List<Entry>) {
        val lineDataSet = LineDataSet(entries, "Línea").apply {
            color = Color.parseColor("#9575CD")
            circleRadius = 5f
            setCircleColor(Color.parseColor("#9575CD"))
            valueTextSize = 12f
            setDrawValues(true)
        }

        val barDataSet = BarDataSet(entries.map { BarEntry(it.x, it.y) }, "Barra").apply {
            color = Color.parseColor("#F48FB1")
            valueTextSize = 12f
        }

        val combinedData = CombinedData().apply {
            setData(LineData(lineDataSet))
            setData(BarData(barDataSet))
        }

        chartPorcentajeHistorial.data = combinedData
        chartPorcentajeHistorial.description = Description().apply { text = "Historial de Llenado" }
        configurarEjeX(chartPorcentajeHistorial)
        chartPorcentajeHistorial.axisRight.isEnabled = false
        chartPorcentajeHistorial.animateXY(1000, 1000)
        chartPorcentajeHistorial.invalidate()
    }
}
