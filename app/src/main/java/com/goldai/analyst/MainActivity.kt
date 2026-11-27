package com.goldai.analyst

import android.annotation.SuppressLint
import android.graphics.Color as AColor
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val ENGINE = "http://127.0.0.1:5000"
private val BG = Color(0xFF080D16)
private val PANEL = Color(0xFF111A28)
private val MUTED = Color(0xFF91A0B5)
private val GREEN = Color(0xFF22C58B)
private val RED = Color(0xFFFF647C)
private val BLUE = Color(0xFF55A6FF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = AColor.rgb(8, 13, 22)
        window.navigationBarColor = AColor.rgb(8, 13, 22)
        setContent { GoldApp() }
    }
}

@Composable
private fun GoldApp() {
    var tab by remember { mutableStateOf("Обзор") }
    var timeframe by remember { mutableStateOf("15m") }
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf("") }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        while (true) {
            try {
                data = getJson("$ENGINE/analysis")
                error = ""
            } catch (_: Exception) {
                error = "Нет связи с Python Engine. Запусти main.py в Termux."
            }
            delay(2_000)
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = BLUE, background = BG, surface = PANEL)) {
        Column(Modifier.fillMaxSize().background(BG).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(18.dp))
            Text("GOLD AI ANALYST", color = BLUE, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("XAU / USD", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text("Biquote • Live Gold • автообновление 2 сек • свайп вниз вручную", color = MUTED, fontSize = 11.sp)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Обзор", "График", "История").forEach { name ->
                    Box(
                        Modifier.weight(1f)
                            .background(if (tab == name) BLUE.copy(alpha = .18f) else PANEL, RoundedCornerShape(12.dp))
                            .clickable { tab = name }.padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(name, color = if (tab == name) BLUE else MUTED, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            when (tab) {
                "Обзор" -> Overview(data, error, refreshing) {
                    scope.launch {
                        refreshing = true
                        try {
                            data = getJson("$ENGINE/analysis")
                            error = ""
                        } catch (_: Exception) {
                            error = "Не удалось обновить данные. Проверь Python Engine в Termux."
                        } finally {
                            refreshing = false
                        }
                    }
                }
                "График" -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("15m", "1h").forEach { tf ->
                            FilterChip(selected = timeframe == tf, onClick = { timeframe = tf }, label = { Text(tf.uppercase()) })
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    CandleChart(timeframe)
                }
                else -> History(data)
            }
        }
    }
}

@Composable
private fun Overview(data: JSONObject?, error: String, refreshing: Boolean, onRefresh: () -> Unit) {
    val signal = data?.optString("signal", "WAIT") ?: "WAIT"
    val price = data?.optDouble("price", 0.0) ?: 0.0
    val scores = data?.optJSONObject("scores")
    val buy = scores?.optInt("buy", 0) ?: 0
    val sell = scores?.optInt("sell", 0) ?: 0
    val indicators = data?.optJSONObject("indicators")
    val plan = data?.optJSONObject("trade_plan")
    val targets = plan?.optJSONObject("take_profit")
    val risk = data?.optJSONObject("risk_filter")
    val m5 = data?.optJSONObject("m5_entry")
    val color = when (signal.uppercase()) {
        "BUY" -> GREEN
        "SELL" -> RED
        else -> BLUE
    }

    fun fmt(value: Double?): String =
        if (value == null || !value.isFinite() || value <= 0.0) "—"
        else "$" + "%.2f".format(value)

    val scrollState = rememberScrollState()
    var pullDistance by remember { mutableStateOf(0f) }
    val refreshThreshold = with(androidx.compose.ui.platform.LocalDensity.current) { 72.dp.toPx() }

    val pullToRefreshConnection = remember(scrollState, refreshing, onRefresh, refreshThreshold) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // At the very top, capture a downward pull before the list consumes it.
                if (scrollState.value == 0 && available.y > 0f && !refreshing) {
                    pullDistance = (pullDistance + available.y).coerceAtMost(refreshThreshold * 1.5f)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pullDistance >= refreshThreshold && !refreshing) {
                    pullDistance = 0f
                    onRefresh()
                } else {
                    pullDistance = 0f
                }
                return Velocity.Zero
            }
        }
    }

    Column(
        Modifier.fillMaxSize()
            .nestedScroll(pullToRefreshConnection)
            .verticalScroll(scrollState)
            .padding(bottom = 12.dp)
    ) {
        if (refreshing || pullDistance > 0f) {
            Text(
                if (refreshing) "Обновление анализа…" else "Потяни вниз и отпусти для обновления",
                color = BLUE, fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            )
        }
        if (error.isNotEmpty()) PanelCard { Text(error, color = RED) }

        PanelCard {
            Text("XAU/USD • ТЕКУЩАЯ ЦЕНА", color = MUTED, fontSize = 11.sp)
            Text(fmt(price), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("Источник: Biquote", color = MUTED, fontSize = 11.sp)
        }

        Spacer(Modifier.height(10.dp))
        PanelCard {
            Text("ТОРГОВЫЙ СИГНАЛ", color = MUTED, fontSize = 11.sp)
            Text(signal, color = color, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("BUY  $buy", color = GREEN, fontWeight = FontWeight.Bold)
                Text("SELL  $sell", color = RED, fontWeight = FontWeight.Bold)
            }
            Text("Сигнал формируется только после прохождения фильтров риска и подтверждения M5.",
                color = MUTED, fontSize = 10.sp)
        }

        Spacer(Modifier.height(10.dp))
        PanelCard {
            Text("ТОРГОВЫЙ ПЛАН", color = MUTED, fontSize = 11.sp)
            if (plan != null && (signal == "BUY" || signal == "SELL")) {
                val direction = plan.optString("direction", signal)
                Text(direction, color = if (direction == "BUY") GREEN else RED,
                    fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                InfoRow("ENTRY", fmt(plan.optDouble("entry", Double.NaN)))
                InfoRow("STOP LOSS", fmt(plan.optDouble("stop_loss", Double.NaN)))
                InfoRow("TAKE PROFIT 1", fmt(targets?.optDouble("tp1", Double.NaN)))
                InfoRow("TAKE PROFIT 2", fmt(targets?.optDouble("tp2", Double.NaN)))
                InfoRow("TAKE PROFIT 3", fmt(targets?.optDouble("tp3", Double.NaN)))
                InfoRow("RISK / REWARD", "1:1 / 1:2 / 1:3")
                Text("Статус: ${plan.optString("status", "PROPOSED_NOT_EXECUTED")}",
                    color = MUTED, fontSize = 10.sp)
            } else {
                Text("WAIT — подтверждённой точки входа сейчас нет",
                    color = BLUE, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("Приложение не будет подставлять выдуманные цены. Entry, Stop Loss и Take Profit появятся после подтверждения условий.",
                    color = MUTED, fontSize = 12.sp)
                if (m5 != null) {
                    InfoRow("M5", m5.optString("status", "—"))
                    InfoRow("M5 событие", m5.optString("event", "—"))
                }
                val reasons = risk?.optJSONArray("reasons")
                if (reasons != null) {
                    for (i in 0 until reasons.length()) {
                        Text("• ${reasons.optString(i)}", color = MUTED, fontSize = 11.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        PanelCard {
            Text("ТРЕНД И СТРУКТУРА", color = MUTED, fontSize = 11.sp)
            InfoRow("H1", data?.optString("trend_h1", "—") ?: "—")
            InfoRow("M15", data?.optString("trend_m15", "—") ?: "—")
            InfoRow("Структура M15", data?.optString("structure_m15", "—") ?: "—")
            InfoRow("Ликвидность", data?.optString("liquidity", "—") ?: "—")
        }

        Spacer(Modifier.height(10.dp))
        PanelCard {
            Text("ИНДИКАТОРЫ", color = MUTED, fontSize = 11.sp)
            InfoRow("RSI 14", indicators?.optDouble("rsi14")?.let { "%.2f".format(it) } ?: "—")
            InfoRow("MACD histogram",
                indicators?.optJSONObject("macd")?.optDouble("histogram")?.let { "%.4f".format(it) } ?: "—")
            InfoRow("ATR 14", indicators?.optDouble("atr14")?.let { "%.4f".format(it) } ?: "—")
            InfoRow("Volume ratio", indicators?.optDouble("volume_ratio")?.let { "%.2f".format(it) } ?: "—")
        }

        val reasons = data?.optJSONArray("reasons")
        if (reasons != null && reasons.length() > 0) {
            Spacer(Modifier.height(10.dp))
            PanelCard {
                Text("ОБОСНОВАНИЕ", color = MUTED, fontSize = 11.sp)
                for (i in 0 until reasons.length()) {
                    Text("• ${reasons.optString(i)}", color = Color.White, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun History(data: JSONObject?) {
    Column(Modifier.fillMaxSize()) {
        PanelCard {
            Text("ИСТОРИЯ СИГНАЛОВ", color = MUTED, fontSize = 11.sp)
            Text("История сделок пока не сохраняется.", color = Color.White)
            Text("Текущий статус: ${data?.optString("signal", "нет данных") ?: "нет данных"}", color = BLUE)
        }
    }
}

@Composable
private fun PanelCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PANEL)
    ) { Column(Modifier.padding(16.dp), content = content) }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(top = 9.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MUTED, fontSize = 13.sp)
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun CandleChart(timeframe: String) {
    val context = LocalContext.current
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(520.dp).background(PANEL, RoundedCornerShape(14.dp)),
        factory = {
            WebView(context).apply {
                setBackgroundColor(AColor.rgb(17, 26, 40))
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webChromeClient = WebChromeClient()
                webViewClient = WebViewClient()
                loadDataWithBaseURL("$ENGINE/", chartPage(timeframe), "text/html", "UTF-8", null)
            }
        },
        update = { it.evaluateJavascript("if(window.changeInterval){window.changeInterval('$timeframe');}", null) }
    )
}

private fun chartPage(startInterval: String): String = """
<!doctype html><html><head>
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
<style>
html,body{margin:0;width:100%;height:100%;overflow:hidden;background:#111a28;color:#91a0b5;font-family:Arial}
#status{height:32px;box-sizing:border-box;padding:9px 10px;font-size:11px}
#chart{width:100%;height:calc(100% - 32px)}
</style>
<script src="https://unpkg.com/lightweight-charts@4.2.0/dist/lightweight-charts.standalone.production.js"></script>
</head><body><div id="status">Загрузка Binance…</div><div id="chart"></div>
<script>
(function(){
let interval='$startInterval',chart,candleSeries,ema20Series,ema50Series,volumeSeries;
const status=document.getElementById('status');
function ema(rows,n){let k=2/(n+1),p=null;return rows.map(x=>{p=p===null?x.close:x.close*k+p*(1-k);return {time:x.time,value:p};});}
function init(){
 if(typeof LightweightCharts==='undefined'){status.textContent='Нет библиотеки графика. Проверь интернет.';return;}
 const el=document.getElementById('chart');
 chart=LightweightCharts.createChart(el,{
  width:el.clientWidth,height:el.clientHeight,
  layout:{background:{color:'#111a28'},textColor:'#91a0b5'},
  grid:{vertLines:{color:'#1d2939'},horzLines:{color:'#1d2939'}},
  rightPriceScale:{borderColor:'#344256'},
  timeScale:{borderColor:'#344256',timeVisible:true,secondsVisible:false}
 });
 candleSeries=chart.addCandlestickSeries({upColor:'#22c58b',downColor:'#ff647c',borderUpColor:'#22c58b',borderDownColor:'#ff647c',wickUpColor:'#22c58b',wickDownColor:'#ff647c'});
 ema20Series=chart.addLineSeries({color:'#55a6ff',lineWidth:2,priceLineVisible:false});
 ema50Series=chart.addLineSeries({color:'#f5b942',lineWidth:2,priceLineVisible:false});
 volumeSeries=chart.addHistogramSeries({priceFormat:{type:'volume'},priceScaleId:'volume',scaleMargins:{top:.82,bottom:0}});
 chart.priceScale('volume').applyOptions({scaleMargins:{top:.82,bottom:0}});
 new ResizeObserver(()=>chart.applyOptions({width:el.clientWidth,height:el.clientHeight})).observe(el);
 loadCandles(true);setInterval(()=>loadCandles(false),30000);
}
async function loadCandles(first){
 try{
  status.textContent='Обновление '+interval.toUpperCase()+'…';
  const response=await fetch('/candles?interval='+encodeURIComponent(interval)+'&_='+Date.now());
  if(!response.ok)throw new Error('HTTP '+response.status);
  const json=await response.json();
  if(!json.candles||!json.candles.length)throw new Error('Нет свечей в ответе API');
  const rows=json.candles.map(x=>({time:Math.floor(new Date(x.time).getTime()/1000),open:+x.open,high:+x.high,low:+x.low,close:+x.close,volume:+x.volume}));
  const range=first?null:chart.timeScale().getVisibleLogicalRange();
  candleSeries.setData(rows);
  ema20Series.setData(ema(rows,20));ema50Series.setData(ema(rows,50));
  volumeSeries.setData(rows.map(x=>({time:x.time,value:x.volume,color:x.close>=x.open?'#22c58b66':'#ff647c66'})));
  if(range)chart.timeScale().setVisibleLogicalRange(range);else chart.timeScale().fitContent();
  status.textContent='BINANCE PAXGUSDT • '+interval.toUpperCase()+' • '+rows.length+' свечей';
 }catch(e){status.textContent='Ошибка графика: '+e.message;}
}
window.changeInterval=function(next){if(next!==interval){interval=next;loadCandles(true);}};
window.addEventListener('load',init);
})();
</script></body></html>
""".trimIndent()

private suspend fun getJson(address: String): JSONObject = withContext(Dispatchers.IO) {
    val connection = URL(address).openConnection() as HttpURLConnection
    connection.requestMethod = "GET"
    connection.connectTimeout = 8000
    connection.readTimeout = 8000
    try {
        JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
    } finally {
        connection.disconnect()
    }
}
