
package com.goldai.analyst

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private val Background = Color(0xFF0B0D10)
private val CardColor = Color(0xFF171C23)
private val Gold = Color(0xFFE5B94B)
private val White = Color(0xFFF5F5F5)
private val Muted = Color(0xFF9AA4B2)
private val Green = Color(0xFF35C98A)
private val Red = Color(0xFFFF6677)

private const val SERVER_URL = "http://127.0.0.1:5000"

data class MarketData(
    val connected: Boolean = false,
    val price: String = "—",
    val signal: String = "NO TRADE",
    val reason: String = "Ожидание данных",
    val entry: String = "—",
    val stopLoss: String = "—",
    val takeProfit: String = "—",
    val trend: String = "Нет данных",
    val volume: String = "Нет данных",
    val updated: String = "Ещё не обновлялось"
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GoldAIApp()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoldAIApp() {

    var data by remember { mutableStateOf(MarketData()) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) }

    val scope = rememberCoroutineScope()

    suspend fun updateData() {
        if (refreshing) return

        refreshing = true
        error = ""

        try {
            val result = withContext(Dispatchers.IO) {
                loadMarketData()
            }

            data = result.copy(
                updated = java.text.SimpleDateFormat(
                    "HH:mm:ss",
                    java.util.Locale.getDefault()
                ).format(java.util.Date())
            )

        } catch (e: Exception) {
            error = e.message ?: "Ошибка подключения"
            data = data.copy(
                connected = false,
                signal = "NO TRADE"
            )
        } finally {
            refreshing = false
        }
    }

    LaunchedEffect(Unit) {
        updateData()

        while (true) {
            delay(30_000)
            updateData()
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Gold,
            background = Background,
            surface = CardColor
        )
    ) {
        Scaffold(
            containerColor = Background,
            bottomBar = {
                NavigationBar(
                    containerColor = CardColor
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = {
                            Icon(Icons.Default.Analytics, null)
                        },
                        label = { Text("Анализ") }
                    )

                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = {
                            Icon(Icons.Default.ShowChart, null)
                        },
                        label = { Text("График") }
                    )

                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = {
                            Icon(Icons.Default.History, null)
                        },
                        label = { Text("История") }
                    )
                }
            }
        ) { padding ->

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Background)
            ) {

                Header(data.connected)

                when (selectedTab) {

                    0 -> PullToRefreshBox(
                        isRefreshing = refreshing,
                        onRefresh = {
                            scope.launch { updateData() }
                        },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {

                            Text(
                                "XAU/USD",
                                color = White,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                "GOLD / US DOLLAR",
                                color = Muted,
                                fontSize = 11.sp
                            )

                            Panel {
                                Text(
                                    "ТЕКУЩАЯ ЦЕНА",
                                    color = Muted,
                                    fontSize = 11.sp
                                )

                                Text(
                                    data.price,
                                    color = White,
                                    fontSize = 34.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Panel {
                                Text(
                                    "ТОРГОВЫЙ СИГНАЛ",
                                    color = Muted,
                                    fontSize = 11.sp
                                )

                                Spacer(Modifier.height(10.dp))

                                Text(
                                    data.signal,
                                    color = when (data.signal.uppercase()) {
                                        "BUY" -> Green
                                        "SELL" -> Red
                                        else -> Gold
                                    },
                                    fontSize = 30.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    data.reason,
                                    color = Muted,
                                    fontSize = 12.sp
                                )

                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 12.dp),
                                    color = Muted.copy(alpha = 0.2f)
                                )

                                Metric("Entry", data.entry)
                                Metric("Stop Loss", data.stopLoss)
                                Metric("Take Profit", data.takeProfit)
                            }

                            Panel {
                                Text(
                                    "АНАЛИТИКА РЫНКА",
                                    color = Gold,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(Modifier.height(10.dp))

                                Metric("Тренд H1", data.trend)
                                Metric("Объём", data.volume)
                            }

                            Panel {
                                Text(
                                    "СОЕДИНЕНИЕ С СЕРВЕРОМ",
                                    color = Gold,
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    if (data.connected) "Подключено"
                                    else "Нет подключения",
                                    color = if (data.connected) Green else Red
                                )

                                Text(
                                    "Последнее обновление: ${data.updated}",
                                    color = Muted,
                                    fontSize = 11.sp
                                )

                                if (error.isNotEmpty()) {
                                    Text(
                                        error,
                                        color = Red,
                                        fontSize = 12.sp
                                    )
                                }

                                Spacer(Modifier.height(8.dp))

                                Text(
                                    "Потяни экран вниз для обновления",
                                    color = Muted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    1 -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp)
                    ) {
                        Text(
                            "ГРАФИК XAU/USD",
                            color = Gold,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.height(12.dp))

                        Text(
                            "График TradingView подключим следующим этапом.",
                            color = Muted
                        )
                    }

                    2 -> Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text(
                            "ИСТОРИЯ СИГНАЛОВ",
                            color = Gold,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            "История будет заполняться после подключения аналитического API.",
                            color = Muted
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun Header(connected: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardColor)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "GOLD AI ANALYST",
                color = Gold,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                "INTELLIGENT TRADING SYSTEM",
                color = Muted,
                fontSize = 9.sp
            )
        }

        Text(
            if (connected) "● ONLINE" else "● OFFLINE",
            color = if (connected) Green else Red,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = CardColor
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
fun Metric(name: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(name, color = Muted, fontSize = 12.sp)
        Text(value, color = White, fontSize = 12.sp)
    }
}

fun loadMarketData(): MarketData {

    val endpoints = listOf(
        "/analysis",
        "/api/analysis"
    )

    var lastError = "API не найден"

    for (endpoint in endpoints) {

        var connection: HttpURLConnection? = null

        try {
            connection = URL(
                SERVER_URL + endpoint
            ).openConnection() as HttpURLConnection

            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 7000

            if (connection.responseCode !in 200..299) {
                lastError = "HTTP ${connection.responseCode}"
                continue
            }

            val body = connection.inputStream
                .bufferedReader()
                .use { it.readText() }

            val json = JSONObject(body)

            val result = json.optJSONObject("data")
                ?: json.optJSONObject("analysis")
                ?: json

            return MarketData(
                connected = true,
                price = result.optString("price", "—"),
                signal = result.optString(
                    "signal",
                    "NO TRADE"
                ).uppercase(),
                reason = result.optString(
                    "reason",
                    "Анализ получен"
                ),
                entry = result.optString("entry", "—"),
                stopLoss = result.optString("stop_loss", "—"),
                takeProfit = result.optString("take_profit", "—"),
                trend = result.optString("trend_h1", "Нет данных"),
                volume = result.optString("volume", "Нет данных")
            )

        } catch (e: Exception) {
            lastError = e.message ?: "Ошибка соединения"
        } finally {
            connection?.disconnect()
        }
    }

    throw IllegalStateException(lastError)
}
