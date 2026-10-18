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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { MaterialTheme { RoutineApp() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineApp() {
    val context = LocalContext.current
    var tasks by remember { mutableStateOf(TaskStore.all(context).sortedBy { it.dueAt }) }
    var showAdd by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Pessoal") }
    var recurrence by remember { mutableStateOf("Única") }
    var dueAt by remember { mutableLongStateOf(System.currentTimeMillis() + 60 * 60 * 1000L) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var recurrenceExpanded by remember { mutableStateOf(false) }
    val categories = listOf("Pessoal", "Casa", "Saúde", "Trabalho", "Família", "Finanças", "Compras", "Projetos")
    val recurrences = listOf("Única", "Diária", "Semanal", "Mensal", "Anual")

    fun refresh() { tasks = TaskStore.all(context).sortedBy { it.dueAt } }
    fun pickDateTime() {
        val cal = Calendar.getInstance().apply { timeInMillis = dueAt }
        DatePickerDialog(context, { _, y, m, d ->
            val updated = Calendar.getInstance().apply {
                timeInMillis = dueAt
                set(Calendar.YEAR, y); set(Calendar.MONTH, m); set(Calendar.DAY_OF_MONTH, d)
            }
            TimePickerDialog(context, { _, h, min ->
                updated.set(Calendar.HOUR_OF_DAY, h); updated.set(Calendar.MINUTE, min); updated.set(Calendar.SECOND, 0)
                dueAt = updated.timeInMillis
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ONCE IN A WHILE", fontWeight = FontWeight.Bold)
                        Text("Your time. Your tasks. Your rhythm.", style = MaterialTheme.typography.labelSmall)
                    }
                },
                actions = { IconButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, "Adicionar tarefa") } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, "Adicionar") }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(8.dp))
            Text("As tuas tarefas", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("${tasks.count { !it.done && it.active }} pendentes · ${tasks.count { it.done || !it.active }} concluídas", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            if (tasks.isEmpty()) {
                Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Notifications, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Text("Começa com uma tarefa", style = MaterialTheme.typography.titleMedium)
                        Text("Cria um lembrete e deixa o ONCE IN A WHILE acompanhar-te.", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { showAdd = true }) { Text("Adicionar tarefa") }
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 100.dp)) {
                    items(tasks, key = { it.id }) { task ->
                        Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(task.title, fontWeight = FontWeight.SemiBold)
                                    Text("${task.category} · ${task.recurrence}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                    Text(SimpleDateFormat("EEE, dd MMM · HH:mm", Locale.getDefault()).format(Date(task.dueAt)), style = MaterialTheme.typography.bodySmall)
                                    if (task.done || !task.active) Text("Concluída", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelMedium)
                                }
                                if (!task.done && task.active) {
                                    IconButton(onClick = {
                                        TaskStore.markDone(context, task.id)
                                        refresh()
                                    }) { Icon(Icons.Default.Check, "Concluir") }
                                }
                                IconButton(onClick = {
                                    ReminderScheduler.cancel(context, task.id)
                                    TaskStore.saveAll(context, TaskStore.all(context).filterNot { it.id == task.id })
                                    refresh()
                                }) { Icon(Icons.Default.Delete, "Eliminar") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Nova tarefa") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("O que precisas de fazer?") }, singleLine = true)
                    ExposedDropdownMenuBox(expanded = categoryExpanded, onExpandedChange = { categoryExpanded = !categoryExpanded }) {
                        OutlinedTextField(value = category, onValueChange = {}, readOnly = true, label = { Text("Categoria") }, modifier = Modifier.menuAnchor().fillMaxWidth())
                        ExposedDropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                            categories.forEach { c -> DropdownMenuItem(text = { Text(c) }, onClick = { category = c; categoryExpanded = false }) }
                        }
                    }
                    ExposedDropdownMenuBox(expanded = recurrenceExpanded, onExpandedChange = { recurrenceExpanded = !recurrenceExpanded }) {
                        OutlinedTextField(value = recurrence, onValueChange = {}, readOnly = true, label = { Text("Repetição") }, modifier = Modifier.menuAnchor().fillMaxWidth())
                        ExposedDropdownMenu(expanded = recurrenceExpanded, onDismissRequest = { recurrenceExpanded = false }) {
                            recurrences.forEach { r -> DropdownMenuItem(text = { Text(r) }, onClick = { recurrence = r; recurrenceExpanded = false }) }
                        }
                    }
                    OutlinedButton(onClick = { pickDateTime() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Data e hora: " + SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(dueAt)))
                    }
                    Text("Lembretes repetidos a cada 5 minutos enquanto pendente (máximo 12).", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (title.isNotBlank()) {
                        val task = Task(
                            id = System.currentTimeMillis(),
                            title = title.trim(),
                            category = category,
                            dueAt = dueAt,
                            recurrence = recurrence
                        )
                        TaskStore.upsert(context, task)
                        ReminderScheduler.schedule(context, task)
                        title = ""; category = "Pessoal"; recurrence = "Única"
                        dueAt = System.currentTimeMillis() + 60 * 60 * 1000L
                        showAdd = false
                        refresh()
                    }
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancelar") } }
        )
    }
}
