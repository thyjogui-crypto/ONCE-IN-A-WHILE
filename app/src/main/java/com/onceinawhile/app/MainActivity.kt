package com.onceinawhile.app

import android.Manifest
import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private var exportPayload = "[]"
    private val exportFile = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) contentResolver.openOutputStream(uri)?.use { it.write(exportPayload.toByteArray()) }
    }
    private val importFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val json = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            if (json != null && TaskStore.importJson(this, json)) recreate()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            val prefs = getSharedPreferences("once_in_a_while", MODE_PRIVATE)
            var theme by remember { mutableStateOf(prefs.getString("theme", "Sistema") ?: "Sistema") }
            val dark = when (theme) { "Escuro" -> true; "Claro" -> false; else -> isSystemInDarkTheme() }
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                RoutineApp(
                    onExport = { exportPayload = TaskStore.exportJson(this); exportFile.launch("once-in-a-while-backup.json") },
                    onImport = { importFile.launch(arrayOf("application/json", "text/*")) },
                    theme = theme,
                    onTheme = { theme = it; prefs.edit().putString("theme", it).apply() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineApp(onExport: () -> Unit, onImport: () -> Unit, theme: String, onTheme: (String) -> Unit) {
    val context = LocalContext.current
    var tasks by remember { mutableStateOf(TaskStore.all(context).sortedBy { it.dueAt }) }
    var editing by remember { mutableStateOf<Task?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("Todas") }
    var categoryFilter by remember { mutableStateOf("Todas") }
    var page by remember { mutableStateOf("Hoje") }
    val categories = listOf("Pessoal", "Casa", "Saúde", "Trabalho", "Família", "Finanças", "Compras", "Projetos")
    val recurrences = listOf("Única", "Diária", "Semanal", "Mensal", "Anual")
    fun refresh() { tasks = TaskStore.all(context).sortedBy { it.dueAt } }
    val now = System.currentTimeMillis()
    val activeTasks = tasks.filter { it.active && !it.done }
    val completedTasks = tasks.filter { it.done || !it.active }
    val todayCal = Calendar.getInstance()
    val todayTasks = activeTasks.filter {
        val c = Calendar.getInstance().apply { timeInMillis = it.dueAt }
        c.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
            c.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)
    }
    val overdueTasks = activeTasks.filter { it.dueAt < now }
    val progress = if (tasks.isEmpty()) 0f else completedTasks.size.toFloat() / tasks.size.toFloat()

    val bg = Color(0xFF0D1020)
    val panel = Color(0xFF191D32)
    val panel2 = Color(0xFF222740)
    val violet = Color(0xFFAA96FF)
    val mint = Color(0xFF70E1B5)
    val amber = Color(0xFFFFC078)
    val onDark = Color(0xFFF5F5FF)
    val muted = Color(0xFFA8AEC8)

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = violet, secondary = mint, tertiary = amber,
            background = bg, surface = panel, surfaceVariant = panel2,
            onBackground = onDark, onSurface = onDark, onSurfaceVariant = muted
        )
    ) {
        Scaffold(
            containerColor = bg,
            topBar = {
                TopAppBar(
                    title = {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("ONCE IN A WHILE", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                            Text(
                                when (page) {
                                    "Hoje" -> SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date()).replaceFirstChar { it.uppercase() }
                                    "Tarefas" -> "Tudo o que tens em mãos"
                                    "Histórico" -> "O progresso que construíste"
                                    else -> "Personaliza a tua experiência"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = muted
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { page = "Definições" }) {
                            Icon(Icons.Default.Settings, contentDescription = "Definições", tint = violet)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = bg)
                )
            },
            bottomBar = {
                NavigationBar(containerColor = panel, contentColor = violet) {
                    listOf(
                        Triple("Hoje", Icons.Default.Home, "Hoje"),
                        Triple("Tarefas", Icons.Default.List, "Tarefas"),
                        Triple("Histórico", Icons.Default.BarChart, "Histórico"),
                        Triple("Definições", Icons.Default.Settings, "Definições")
                    ).forEach { (label, icon, destination) ->
                        NavigationBarItem(
                            selected = page == destination,
                            onClick = {
                                page = destination
                            },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label, maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = violet,
                                selectedTextColor = violet,
                                indicatorColor = Color(0xFF302A50),
                                unselectedIconColor = muted,
                                unselectedTextColor = muted
                            )
                        )
                    }
                }
            },
            floatingActionButton = {
                if (page == "Hoje" || page == "Tarefas") {
                    FloatingActionButton(
                        onClick = { editing = null; showEditor = true },
                        containerColor = violet,
                        contentColor = Color(0xFF171326),
                        shape = RoundedCornerShape(20.dp)
                    ) { Icon(Icons.Default.Add, "Adicionar tarefa") }
                }
            }
        ) { padding ->
            when (page) {
                "Hoje" -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 100.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Row(
                                Modifier.fillMaxWidth()
                                    .background(
                                        androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF292448), Color(0xFF1A2039))),
                                        RoundedCornerShape(28.dp)
                                    ).padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Default.AutoAwesome, null, tint = violet, modifier = Modifier.size(17.dp))
                                        Text("O TEU RITMO", color = violet, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                                    }
                                    Text("Um passo de cada vez.", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = onDark)
                                    Text(
                                        when {
                                            activeTasks.isEmpty() -> "Tudo tratado. Aproveita o momento."
                                            overdueTasks.isNotEmpty() -> "Vamos recuperar o ritmo, sem pressão."
                                            else -> "Pequenas ações, progresso real."
                                        },
                                        style = MaterialTheme.typography.bodyMedium, color = muted
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                ProgressRing(progress = progress, value = "${(progress * 100).toInt()}%", violet = violet, track = Color(0xFF41405F), textColor = onDark)
                            }
                        }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                PremiumMetric("Para hoje", todayTasks.size.toString(), Icons.Default.Schedule, violet, Modifier.weight(1f))
                                PremiumMetric("Pendentes", activeTasks.size.toString(), Icons.Default.List, amber, Modifier.weight(1f))
                                PremiumMetric("Concluídas", completedTasks.size.toString(), Icons.Default.CheckCircle, mint, Modifier.weight(1f))
                            }
                        }
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.weight(1f)) {
                                    Text("A seguir", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = onDark)
                                    Text("As tarefas que pedem atenção", style = MaterialTheme.typography.bodySmall, color = muted)
                                }
                                TextButton(onClick = { page = "Tarefas"; filter = "Todas" }) { Text("Ver todas", color = violet) }
                            }
                        }
                        if (activeTasks.isEmpty()) {
                            item {
                                EmptyPremiumCard(
                                    title = "Espaço para respirar",
                                    subtitle = "Não tens tarefas pendentes. Adiciona uma quando precisares.",
                                    violet = violet, panel = panel, muted = muted
                                )
                            }
                        } else {
                            items(activeTasks.sortedWith(compareBy<Task> { if (it.dueAt < now) 0 else 1 }.thenBy { it.dueAt }).take(5), key = { it.id }) { task ->
                                PremiumTaskCard(
                                    task = task, panel = panel, muted = muted, violet = violet, mint = mint,
                                    onEdit = { editing = task; showEditor = true },
                                    onDone = {
                                        TaskStore.markDone(context, task.id)
                                        ReminderScheduler.cancel(context, task.id)
                                        refresh()
                                    },
                                    onDelete = {
                                        ReminderScheduler.cancel(context, task.id)
                                        TaskStore.saveAll(context, TaskStore.all(context).filterNot { it.id == task.id })
                                        refresh()
                                    }
                                )
                            }
                        }
                        item {
                            Text("Visão geral", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = onDark)
                            Text("Distribuição por categoria", style = MaterialTheme.typography.bodySmall, color = muted)
                            Spacer(Modifier.height(10.dp))
                            CategoryChart(
                                categories = categories,
                                counts = categories.map { c -> tasks.count { it.category == c } },
                                violet = violet, mint = mint, panel = panel, muted = muted, textColor = onDark
                            )
                        }
                    }
                }
                "Tarefas" -> {
                    Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Todas", "Atrasadas", "Hoje", "Futuras", "Concluídas").forEach { f ->
                                FilterChip(
                                    selected = filter == f, onClick = { filter = f },
                                    label = { Text(f, maxLines = 1, softWrap = false) }
                                )
                            }
                        }
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (listOf("Todas") + categories).forEach { c ->
                                FilterChip(
                                    selected = categoryFilter == c, onClick = { categoryFilter = c },
                                    label = { Text(c, maxLines = 1, softWrap = false) }
                                )
                            }
                        }
                        val visible = tasks.filter { task ->
                            val taskCal = Calendar.getInstance().apply { timeInMillis = task.dueAt }
                            val sameDay = taskCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
                                taskCal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)
                            val status = when (filter) {
                                "Atrasadas" -> task.active && !task.done && task.dueAt < now
                                "Hoje" -> task.active && !task.done && sameDay
                                "Futuras" -> task.active && !task.done && task.dueAt > now && !sameDay
                                "Concluídas" -> task.done || !task.active
                                else -> true
                            }
                            status && (categoryFilter == "Todas" || task.category == categoryFilter)
                        }
                        if (visible.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                EmptyPremiumCard("Nada por aqui", "Experimenta outro filtro ou cria uma nova tarefa.", violet, panel, muted)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(top = 12.dp, bottom = 100.dp),
                                verticalArrangement = Arrangement.spacedBy(9.dp)
                            ) {
                                items(visible, key = { it.id }) { task ->
                                    PremiumTaskCard(
                                        task = task, panel = panel, muted = muted, violet = violet, mint = mint,
                                        onEdit = { editing = task; showEditor = true },
                                        onDone = {
                                            TaskStore.markDone(context, task.id)
                                            ReminderScheduler.cancel(context, task.id)
                                            refresh()
                                        },
                                        onDelete = {
                                            ReminderScheduler.cancel(context, task.id)
                                            TaskStore.saveAll(context, TaskStore.all(context).filterNot { it.id == task.id })
                                            refresh()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                "Histórico" -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentPadding = PaddingValues(18.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Text("O progresso conta", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = onDark)
                            Text("Um retrato das tuas tarefas guardadas", style = MaterialTheme.typography.bodyMedium, color = muted)
                        }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                PremiumMetric("Total", tasks.size.toString(), Icons.Default.List, violet, Modifier.weight(1f))
                                PremiumMetric("Feitas", completedTasks.size.toString(), Icons.Default.CheckCircle, mint, Modifier.weight(1f))
                                PremiumMetric("Atrasadas", overdueTasks.size.toString(), Icons.Default.Schedule, amber, Modifier.weight(1f))
                            }
                        }
                        item {
                            Text("Tarefas por categoria", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = onDark)
                            CategoryChart(categories, categories.map { c -> tasks.count { it.category == c } }, violet, mint, panel, muted, onDark)
                        }
                        item {
                            Text("Atividade recente", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = onDark)
                        }
                        if (tasks.isEmpty()) item { EmptyPremiumCard("O teu histórico começa aqui", "As tarefas aparecerão nesta área.", violet, panel, muted) }
                        else items(tasks.sortedByDescending { it.dueAt }.take(20), key = { it.id }) { task ->
                            Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(18.dp)) {
                                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(if (task.done || !task.active) Icons.Default.CheckCircle else Icons.Default.Schedule, null, tint = if (task.done || !task.active) mint else violet)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(task.title, color = onDark, fontWeight = FontWeight.SemiBold)
                                        Text("${task.category} · ${SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(task.dueAt))}", color = muted, style = MaterialTheme.typography.bodySmall)
                                    }
                                    Text(if (task.done || !task.active) "Feita" else "Pendente", color = if (task.done || !task.active) mint else amber, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
                else -> {
                    Column(Modifier.fillMaxSize().padding(padding).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("A tua experiência", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = onDark)
                        Text("Escolhe o aspeto da aplicação e gere as tuas cópias de segurança.", color = muted)
                        Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(22.dp)) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Tema", color = onDark, fontWeight = FontWeight.Bold)
                                listOf("Sistema", "Claro", "Escuro").forEach { t ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(selected = theme == t, onClick = { onTheme(t) })
                                        Text(t, color = onDark)
                                    }
                                }
                            }
                        }
                        Button(onClick = onExport, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = violet, contentColor = Color(0xFF171326))) {
                            Text("Exportar cópia de segurança")
                        }
                        OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text("Importar cópia de segurança") }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            OutlinedButton(
                                onClick = {
                                    val alarmIntent = Intent(
                                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                                    ).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(alarmIntent)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Permitir alarmes e lembretes")
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                val notificationIntent = Intent(
                                    Settings.ACTION_APP_NOTIFICATION_SETTINGS
                                ).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                                context.startActivity(notificationIntent)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Gerir permissões de notificações")
                        }
                        Text("Os alarmes e notificações dependem das permissões e das restrições de bateria do Android.", style = MaterialTheme.typography.bodySmall, color = muted)
                    }
                }
            }
        }

        if (showEditor) TaskEditor(
            initial = editing, categories = categories, recurrences = recurrences,
            onDismiss = { showEditor = false },
            onSave = { task ->
                editing?.let { ReminderScheduler.cancel(context, it.id) }
                TaskStore.upsert(context, task)
                if (task.active && !task.done) ReminderScheduler.schedule(context, task, maxOf(task.dueAt, System.currentTimeMillis() + 1000L))
                showEditor = false
                editing = null
                refresh()
            }
        )
    }
}

@Composable
private fun ProgressRing(progress: Float, value: String, violet: Color, track: Color, textColor: Color) {
    val animated by animateFloatAsState(targetValue = progress.coerceIn(0f, 1f), animationSpec = tween(900), label = "progress")
    Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 8.dp.toPx()
            drawArc(track, -90f, 360f, false, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(violet, -90f, 360f * animated, false, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = textColor, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
            Text("feito", color = violet, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun PremiumMetric(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = Color(0xFF191D32)), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(21.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = Color(0xFFF5F5FF))
            Text(title, style = MaterialTheme.typography.labelSmall, color = Color(0xFFA8AEC8), maxLines = 1)
        }
    }
}

@Composable
private fun PremiumTaskCard(
    task: Task, panel: Color, muted: Color, violet: Color, mint: Color,
    onEdit: () -> Unit, onDone: () -> Unit, onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onEdit() },
        colors = CardDefaults.cardColors(containerColor = panel),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(43.dp).background(
                    when (task.category) {
                        "Saúde" -> Color(0xFF1D423D)
                        "Casa" -> Color(0xFF3A304D)
                        "Trabalho" -> Color(0xFF273958)
                        "Finanças" -> Color(0xFF493927)
                        "Família" -> Color(0xFF493047)
                        else -> Color(0xFF302B50)
                    }, RoundedCornerShape(14.dp)
                ), contentAlignment = Alignment.Center
            ) {
                Icon(
                    when (task.category) {
                        "Saúde" -> Icons.Default.CheckCircle
                        "Trabalho" -> Icons.Default.TrendingUp
                        else -> Icons.Default.AutoAwesome
                    }, null, tint = violet, modifier = Modifier.size(21.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(task.title, color = Color(0xFFF5F5FF), fontWeight = FontWeight.SemiBold)
                Text("${task.category} · ${task.recurrence}", color = violet, style = MaterialTheme.typography.labelSmall)
                val schedule = when (task.scheduleMode) {
                    "daily" -> "Pelo menos uma vez por dia"
                    "periods" -> task.periods.joinToString(", ")
                    else -> SimpleDateFormat("EEE, dd MMM · HH:mm", Locale.getDefault()).format(Date(task.dueAt))
                }
                Text(schedule, color = muted, style = MaterialTheme.typography.bodySmall)
                if (task.done || !task.active) Text("Concluída", color = mint, style = MaterialTheme.typography.labelMedium)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (task.active && !task.done) {
                    IconButton(onClick = onDone) { Icon(Icons.Default.Check, "Concluir", tint = mint) }
                } else {
                    Icon(Icons.Default.CheckCircle, null, tint = mint, modifier = Modifier.padding(12.dp).size(20.dp))
                }
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.Edit, "Editar", tint = muted, modifier = Modifier.size(17.dp)) }
                    IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.Delete, "Eliminar", tint = muted, modifier = Modifier.size(17.dp)) }
                }
            }
        }
    }
}

@Composable
private fun CategoryChart(
    categories: List<String>, counts: List<Int>, violet: Color, mint: Color,
    panel: Color, muted: Color, textColor: Color
) {
    val maxCount = (counts.maxOrNull() ?: 0).coerceAtLeast(1)
    Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            categories.forEachIndexed { index, category ->
                val count = counts.getOrElse(index) { 0 }
                val fraction = count.toFloat() / maxCount.toFloat()
                val animated by animateFloatAsState(fraction, animationSpec = tween(700 + index * 45), label = "category-$index")
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(category, color = textColor, style = MaterialTheme.typography.bodySmall)
                        Text(count.toString(), color = muted, style = MaterialTheme.typography.labelSmall)
                    }
                    Box(Modifier.fillMaxWidth().height(7.dp).background(Color(0xFF30344D), RoundedCornerShape(20.dp))) {
                        Box(
                            Modifier.fillMaxWidth(animated.coerceIn(0f, 1f)).height(7.dp)
                                .background(if (index % 3 == 0) mint else violet, RoundedCornerShape(20.dp))
                        )
                    }
                }
            }
            Text("Dados calculados a partir das tarefas guardadas neste dispositivo.", color = muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun EmptyPremiumCard(title: String, subtitle: String, violet: Color, panel: Color, muted: Color) {
    Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(22.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.AutoAwesome, null, tint = violet, modifier = Modifier.size(34.dp))
            Text(title, color = Color(0xFFF5F5FF), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, color = muted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskEditor(initial: Task?, categories: List<String>, recurrences: List<String>, onDismiss: () -> Unit, onSave: (Task) -> Unit) {
    val context = LocalContext.current
    var title by remember(initial?.id) { mutableStateOf(initial?.title ?: "") }
    var category by remember(initial?.id) { mutableStateOf(initial?.category ?: "Pessoal") }
    var recurrence by remember(initial?.id) { mutableStateOf(initial?.recurrence ?: "Única") }
    var dueAt by remember(initial?.id) { mutableLongStateOf(initial?.dueAt ?: System.currentTimeMillis() + 3600000L) }
    var mode by remember(initial?.id) { mutableStateOf(initial?.scheduleMode ?: "specific") }
    var periods by remember(initial?.id) { mutableStateOf(initial?.periods ?: emptyList()) }
    var catExpanded by remember { mutableStateOf(false) }
    var recExpanded by remember { mutableStateOf(false) }
    fun pickDateTime() {
        val cal = Calendar.getInstance().apply { timeInMillis = dueAt }
        DatePickerDialog(context, { _, y, m, d ->
            val c = Calendar.getInstance().apply { timeInMillis = dueAt; set(Calendar.YEAR,y); set(Calendar.MONTH,m); set(Calendar.DAY_OF_MONTH,d) }
            TimePickerDialog(context, { _, h, min -> c.set(Calendar.HOUR_OF_DAY,h); c.set(Calendar.MINUTE,min); c.set(Calendar.SECOND,0); dueAt = c.timeInMillis },
                cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nova tarefa" else "Editar tarefa") },
        text = { Column(Modifier.heightIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Tarefa") }, singleLine = true)
            ExposedDropdownMenuBox(expanded = catExpanded, onExpandedChange = { catExpanded = !catExpanded }) {
                OutlinedTextField(value = category, onValueChange = {}, readOnly = true, label = { Text("Categoria") }, modifier = Modifier.menuAnchor().fillMaxWidth())
                ExposedDropdownMenu(expanded = catExpanded, onDismissRequest = { catExpanded = false }) { categories.forEach { c -> DropdownMenuItem(text = { Text(c) }, onClick = { category = c; catExpanded = false }) } }
            }
            ExposedDropdownMenuBox(expanded = recExpanded, onExpandedChange = { recExpanded = !recExpanded }) {
                OutlinedTextField(value = recurrence, onValueChange = {}, readOnly = true, label = { Text("Repetição") }, modifier = Modifier.menuAnchor().fillMaxWidth())
                ExposedDropdownMenu(expanded = recExpanded, onDismissRequest = { recExpanded = false }) { recurrences.forEach { r -> DropdownMenuItem(text = { Text(r) }, onClick = { recurrence = r; recExpanded = false }) } }
            }
            Text("Quando deve ser feita?")
            listOf("specific" to "Hora específica", "periods" to "Períodos do dia", "daily" to "Pelo menos uma vez por dia").forEach { (v,label) ->
                Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = mode == v, onClick = { mode = v }); Text(label) }
            }
            if (mode == "specific") OutlinedButton(onClick = { pickDateTime() }, modifier = Modifier.fillMaxWidth()) {
                Text(SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(dueAt)))
            }
            if (mode == "periods") {
                listOf("Manhã" to "08:00–12:00", "Tarde" to "12:00–18:00", "Noite" to "18:00–22:00").forEach { (period,hours) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = periods.contains(period), onCheckedChange = { checked -> periods = if (checked) periods + period else periods - period })
                        Text("$period ($hours)")
                    }
                }
                Text("Podes selecionar vários períodos.", style = MaterialTheme.typography.bodySmall)
            }
            Text("Lembretes repetidos a cada 5 minutos enquanto pendente.", style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { Button(enabled = title.isNotBlank() && (mode != "periods" || periods.isNotEmpty()), onClick = {
            val old = initial
            val adjustedDue = when (mode) {
                "periods" -> {
                    val first = periods.minByOrNull { when (it) { "Manhã" -> 8; "Tarde" -> 12; else -> 18 } } ?: "Manhã"
                    val hour = when (first) { "Manhã" -> 8; "Tarde" -> 12; else -> 18 }
                    Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY,hour); set(Calendar.MINUTE,0); set(Calendar.SECOND,0); if (timeInMillis < System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR,1) }.timeInMillis
                }
                "daily" -> Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY,20); set(Calendar.MINUTE,0); set(Calendar.SECOND,0); if (timeInMillis < System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR,1) }.timeInMillis
                else -> dueAt
            }
            onSave(Task(
                id = old?.id ?: System.currentTimeMillis(), title = title.trim(), category = category,
                dueAt = adjustedDue, recurrence = recurrence, done = false, remindersSent = 0, active = true,
                scheduleMode = mode, periods = periods, dailyDoneDate = "", snoozedUntil = 0L
            ))
        }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
