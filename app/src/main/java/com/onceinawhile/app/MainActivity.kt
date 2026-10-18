package com.onceinawhile.app

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
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
    var showSettings by remember { mutableStateOf(false) }
    val categories = listOf("Pessoal", "Casa", "Saúde", "Trabalho", "Família", "Finanças", "Compras", "Projetos")
    val recurrences = listOf("Única", "Diária", "Semanal", "Mensal", "Anual")
    fun refresh() { tasks = TaskStore.all(context).sortedBy { it.dueAt } }
    val now = System.currentTimeMillis()
    Scaffold(topBar = {
        TopAppBar(
            title = { Column { Text("ONCE IN A WHILE", fontWeight = FontWeight.Bold); Text("As tuas tarefas, ao teu ritmo", style = MaterialTheme.typography.labelSmall) } },
            actions = { TextButton(onClick = { showSettings = true }) { Text("Definições") } }
        )
    }, floatingActionButton = {
        FloatingActionButton(onClick = { editing = null; showEditor = true }) { Icon(Icons.Default.Add, "Adicionar tarefa") }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(8.dp))
            Text("As tuas tarefas", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            val pending = tasks.count { it.active && !it.done }
            val done = tasks.count { it.done || !it.active }
            Text("$pending pendentes · $done concluídas", color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator(progress = { if (tasks.isEmpty()) 0f else done.toFloat() / tasks.size }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf("Todas", "Atrasadas", "Hoje", "Futuras", "Concluídas").forEach { f ->
                    FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                (listOf("Todas") + categories).forEach { c ->
                    FilterChip(selected = categoryFilter == c, onClick = { categoryFilter = c }, label = { Text(c) })
                }
            }
            Spacer(Modifier.height(6.dp))
            val todayCal = Calendar.getInstance()
            val visible = tasks.filter { task ->
                val taskCal = Calendar.getInstance().apply { timeInMillis = task.dueAt }
                val sameDay = taskCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) && taskCal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)
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
                Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Notifications, null, modifier = Modifier.size(38.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(if (filter == "Todas") "Começa com uma tarefa" else "Sem tarefas nesta secção", style = MaterialTheme.typography.titleMedium)
                        Text("Os teus dados ficam guardados localmente.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 100.dp)) {
                items(visible, key = { it.id }) { task ->
                    Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).clickable { editing = task; showEditor = true }.padding(vertical = 4.dp)) {
                                Text(task.title, fontWeight = FontWeight.SemiBold)
                                Text("${task.category} · ${task.recurrence}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                val schedule = when (task.scheduleMode) {
                                    "daily" -> "Pelo menos uma vez por dia"
                                    "periods" -> task.periods.joinToString(", ")
                                    else -> SimpleDateFormat("EEE, dd MMM · HH:mm", Locale.getDefault()).format(Date(task.dueAt))
                                }
                                Text(schedule, style = MaterialTheme.typography.bodySmall)
                                if (task.done || !task.active) Text("Concluída", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelMedium)
                            }
                            IconButton(onClick = { editing = task; showEditor = true }) { Icon(Icons.Default.Edit, "Editar") }
                            if (task.active && !task.done) IconButton(onClick = { TaskStore.markDone(context, task.id); ReminderScheduler.cancel(context, task.id); refresh() }) { Icon(Icons.Default.Check, "Concluir") }
                            IconButton(onClick = { ReminderScheduler.cancel(context, task.id); TaskStore.saveAll(context, TaskStore.all(context).filterNot { it.id == task.id }); refresh() }) { Icon(Icons.Default.Delete, "Eliminar") }
                        }
                    }
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
            showEditor = false; editing = null; refresh()
        }
    )
    if (showSettings) AlertDialog(
        onDismissRequest = { showSettings = false },
        title = { Text("Definições") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Tema")
            listOf("Sistema", "Claro", "Escuro").forEach { t ->
                Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = theme == t, onClick = { onTheme(t) }); Text(t) }
            }
            OutlinedButton(onClick = onExport, modifier = Modifier.fillMaxWidth()) { Text("Exportar cópia de segurança") }
            OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text("Importar cópia de segurança") }
            Text("Manhã 08:00–12:00 · Tarde 12:00–18:00 · Noite 18:00–22:00", style = MaterialTheme.typography.bodySmall)
            Text("Os resumos diários e os alarmes dependem das permissões e das restrições do Android.", style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { TextButton(onClick = { showSettings = false }) { Text("Fechar") } }
    )
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
