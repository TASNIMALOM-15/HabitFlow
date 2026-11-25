package com.habitflow.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitflow.app.AppVM
import com.habitflow.app.data.Category
import com.habitflow.app.data.Habit
import com.habitflow.app.data.t

@Composable
fun CategoriesScreen(vm: AppVM, pad: PaddingValues, onEdit: (Long) -> Unit) {
    val snap by vm.snap.collectAsState()
    val cats by vm.cats.collectAsState()
    var edit by remember { mutableStateOf<Category?>(null) }
    var del by remember { mutableStateOf<Category?>(null) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    val active = snap.habits.filter { it.status == 0 }
    val paused = snap.habits.filter { it.status == 1 }
    val archived = snap.habits.filter { it.status == 2 }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = pad.calculateTopPadding() + 8.dp, bottom = pad.calculateBottomPadding() + 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Categories", Modifier.weight(1f), fontSize = 26.sp, fontWeight = FontWeight.Bold)
                PillButton("+ Add", { edit = Category(0, "", 0xFF3B82F6.toInt()) }, filled = true, color = MaterialTheme.colorScheme.primary)
            }
        }
        cats.forEach { c ->
            item(key = "c${c.id}") {
                val list = active.filter { it.categoryId == c.id }
                GroupCard(c.name, Color(c.color), list, onEdit, menu = { close ->
                    DropdownMenuItem(text = { Text("Rename / recolor") }, onClick = { close(); edit = c })
                    DropdownMenuItem(text = { Text("Delete category") }, onClick = { close(); del = c })
                })
            }
        }
        item {
            val none = active.filter { h -> h.categoryId == 0L || cats.none { it.id == h.categoryId } }
            GroupCard("Uncategorized", muted, none, onEdit, menu = null)
        }
        if (paused.isNotEmpty()) {
            item { SectionTitle("⏸ Paused") }
            items2(paused) { h -> StatusRow(h, "Resume", { vm.setStatus(h, 0) }, onEdit) }
        }
        if (archived.isNotEmpty()) {
            item { SectionTitle("📦 Archived") }
            items2(archived) { h -> StatusRow(h, "Restore", { vm.setStatus(h, 0) }, onEdit) }
        }
    }

    edit?.let { c ->
        CategoryDialog(c, onDismiss = { edit = null }) { vm.saveCategory(it) }
    }
    del?.let { c ->
        AlertDialog(
            onDismissRequest = { del = null },
            title = { Text("Delete “${c.name}”?") },
            text = { Text("Habits in this category are kept and become uncategorized.") },
            confirmButton = { TextButton(onClick = { vm.deleteCategory(c); del = null }) { Text("Delete", color = Danger) } },
            dismissButton = { TextButton(onClick = { del = null }) { Text("Cancel") } }
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.items2(list: List<Habit>, content: @Composable (Habit) -> Unit) {
    list.forEach { h -> item(key = "s${h.id}") { content(h) } }
}

@Composable
private fun GroupCard(title: String, color: Color, list: List<Habit>, onEdit: (Long) -> Unit, menu: (@Composable (() -> Unit) -> Unit)?) {
    var open by remember { mutableStateOf(false) }
    HCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(color))
                Text(title, Modifier.weight(1f).padding(start = 10.dp), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Pill("${list.size}", color)
                if (menu != null) Box {
                    Text("⋮", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { open = true }.padding(start = 10.dp))
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) { menu { open = false } }
                }
            }
            if (list.isEmpty()) Text("No habits here yet", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            list.forEach { h ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onEdit(h.id) }
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(h.icon, fontSize = 20.sp)
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(h.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(h.t().label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusRow(h: Habit, action: String, onAction: () -> Unit, onEdit: (Long) -> Unit) {
    HCard {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).clickable { onEdit(h.id) }, verticalAlignment = Alignment.CenterVertically) {
                Text(h.icon, fontSize = 20.sp)
                Text(h.name, Modifier.padding(start = 10.dp), fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
            OutlinePill(action, onAction)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryDialog(c: Category, onDismiss: () -> Unit, onSave: (Category) -> Unit) {
    var name by remember { mutableStateOf(c.name) }
    var color by remember { mutableStateOf(c.color) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (c.id == 0L) "New category" else "Edit category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(24) }, singleLine = true, label = { Text("Name") },
                    shape = RoundedCornerShape(16.dp), keyboardOptions = KeyboardOptions.Default
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HabitColors.forEach { col ->
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(Color(col))
                                .then(if (color == col) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                .clickable { color = col }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) { onSave(c.copy(name = name.trim(), color = color)); onDismiss() } }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
