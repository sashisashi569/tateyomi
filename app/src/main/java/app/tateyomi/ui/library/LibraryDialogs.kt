package app.tateyomi.ui.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tateyomi.data.BookSummary
import app.tateyomi.data.LibraryFolder
import app.tateyomi.data.parseTagInput
import app.tateyomi.data.sanitizeTags
import app.tateyomi.ui.components.rememberBitmap
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookActionSheet(
    book: BookSummary,
    folderName: String?,
    onDismiss: () -> Unit,
    onSelect: () -> Unit,
    onMove: () -> Unit,
    onExport: () -> Unit,
    onEdit: () -> Unit,
    onResetProgress: () -> Unit,
    onDelete: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 20.dp)) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                Text(
                    text = book.title,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (book.author.isNotBlank()) {
                    Text(
                        text = book.author,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (folderName != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = folderName,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            BookActionRow(
                icon = { Icon(Icons.Outlined.CheckCircle, contentDescription = null) },
                label = "選択",
                onClick = onSelect,
            )
            BookActionRow(
                icon = { Icon(Icons.Outlined.Folder, contentDescription = null) },
                label = "移動",
                onClick = onMove,
            )
            BookActionRow(
                icon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                label = "書き出す",
                onClick = onExport,
            )
            BookActionRow(
                icon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                label = "編集",
                onClick = onEdit,
            )
            BookActionRow(
                icon = { Icon(Icons.Outlined.RestartAlt, contentDescription = null) },
                label = "読書進捗をリセット",
                onClick = onResetProgress,
            )
            BookActionRow(
                icon = {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                label = "削除",
                danger = true,
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun BookActionRow(
    icon: @Composable () -> Unit,
    label: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                text = label,
                color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        },
        leadingContent = icon,
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
fun MoveBookDialog(
    book: BookSummary,
    folders: List<LibraryFolder>,
    onDismiss: () -> Unit,
    onConfirm: (folderId: String?) -> Unit,
    onCreateAndMove: (name: String) -> Unit,
) {
    MoveSelectionDialog(
        title = "本を移動",
        count = 1,
        currentFolderId = book.folderId,
        folders = folders,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
        onCreateAndMove = onCreateAndMove,
    )
}

@Composable
fun MoveBooksDialog(
    count: Int,
    currentFolderId: String?,
    folders: List<LibraryFolder>,
    onDismiss: () -> Unit,
    onConfirm: (folderId: String?) -> Unit,
    onCreateAndMove: (name: String) -> Unit,
) {
    MoveSelectionDialog(
        title = "${count}冊を移動",
        count = count,
        currentFolderId = currentFolderId,
        folders = folders,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
        onCreateAndMove = onCreateAndMove,
    )
}

@Composable
private fun MoveSelectionDialog(
    title: String,
    count: Int,
    currentFolderId: String?,
    folders: List<LibraryFolder>,
    onDismiss: () -> Unit,
    onConfirm: (folderId: String?) -> Unit,
    onCreateAndMove: (name: String) -> Unit,
) {
    var selected by remember(count, currentFolderId) { mutableStateOf(currentFolderId) }
    var newFolderName by remember(count, currentFolderId) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontFamily = FontFamily.SansSerif) },
        text = {
            Column(Modifier.heightIn(max = 440.dp)) {
                LazyColumn(Modifier.heightIn(max = 260.dp)) {
                    item {
                        MoveTargetRow(
                            label = "フォルダなし",
                            selected = newFolderName.isBlank() && selected == null,
                            onClick = {
                                selected = null
                                newFolderName = ""
                            },
                        )
                    }
                    items(folders, key = { it.id }) { folder ->
                        MoveTargetRow(
                            label = folder.name,
                            selected = newFolderName.isBlank() && selected == folder.id,
                            onClick = {
                                selected = folder.id
                                newFolderName = ""
                            },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it.take(40) },
                    label = { Text("新しいフォルダ名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (newFolderName.isNotBlank()) {
                        onCreateAndMove(newFolderName.trim())
                    } else {
                        onConfirm(selected)
                    }
                },
            ) {
                Text("移動")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        },
    )
}

@Composable
private fun MoveTargetRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            text = label,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditBookDialog(
    book: BookSummary,
    coverFile: File?,
    onDismiss: () -> Unit,
    onSave: (title: String, author: String, tags: List<String>) -> Unit,
    onPickCover: () -> Unit,
    onResetCover: () -> Unit,
) {
    var title by remember(book.id) { mutableStateOf(book.title) }
    var author by remember(book.id) { mutableStateOf(book.author) }
    var tags by remember(book.id) { mutableStateOf(book.tags) }
    var tagInput by remember(book.id) { mutableStateOf("") }
    val cover = rememberBitmap(coverFile, 700)

    fun addTag() {
        val added = parseTagInput(tagInput)
        if (added.isNotEmpty()) tags = sanitizeTags(tags + added)
        tagInput = ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("本を編集", fontFamily = FontFamily.SansSerif) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .width(64.dp)
                            .height(96.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        if (cover != null) {
                            Image(
                                bitmap = cover,
                                contentDescription = "現在の表紙",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.matchParentSize(),
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        OutlinedButton(onClick = onPickCover) {
                            Text("表紙を選ぶ")
                        }
                        TextButton(onClick = onResetCover) {
                            Text("表紙をリセット")
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(200) },
                    label = { Text("書名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it.take(120) },
                    label = { Text("著者") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = tagInput,
                    onValueChange = { tagInput = it.take(120) },
                    label = { Text("タグ") },
                    placeholder = { Text("例：短編、時代小説") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addTag() }),
                    trailingIcon = {
                        TextButton(onClick = { addTag() }) {
                            Text("追加")
                        }
                    },
                    supportingText = {
                        Text("カンマ区切りで追加できます。", fontSize = 11.sp)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (tags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        tags.forEach { tag ->
                            InputChip(
                                selected = false,
                                onClick = { tags = tags - tag },
                                label = { Text(tag, fontSize = 12.sp) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Outlined.Close,
                                        contentDescription = "「$tag」を削除",
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title.trim(), author.trim(), tags) },
                enabled = title.isNotBlank(),
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditTagsDialog(
    count: Int,
    commonTags: List<String>,
    onDismiss: () -> Unit,
    onApply: (add: Set<String>, remove: Set<String>) -> Unit,
) {
    val initial = remember(commonTags) { commonTags.toSet() }
    var tags by remember(commonTags) { mutableStateOf(initial) }
    var tagInput by remember { mutableStateOf("") }

    fun addTag() {
        val added = parseTagInput(tagInput)
        if (added.isNotEmpty()) tags = sanitizeTags(tags.toList() + added).toSet()
        tagInput = ""
    }

    val added = tags - initial
    val removed = initial - tags

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("タグを編集", fontFamily = FontFamily.SansSerif) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                Text(
                    text = "選択した${count}冊のタグを追加・削除します。",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = tagInput,
                    onValueChange = { tagInput = it.take(120) },
                    label = { Text("タグを追加") },
                    placeholder = { Text("例：短編、時代小説") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addTag() }),
                    trailingIcon = {
                        TextButton(onClick = { addTag() }) {
                            Text("追加")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (tags.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        tags.forEach { tag ->
                            InputChip(
                                selected = false,
                                onClick = { tags = tags - tag },
                                label = { Text(tag, fontSize = 12.sp) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Outlined.Close,
                                        contentDescription = "「$tag」を削除",
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onApply(added, removed) },
                enabled = added.isNotEmpty() || removed.isNotEmpty(),
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        },
    )
}

private enum class FolderDialogMode { LIST, CREATE, RENAME, DELETE }

@Composable
fun FolderManagerDialog(
    folders: List<LibraryFolder>,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var mode by remember { mutableStateOf(FolderDialogMode.LIST) }
    var target by remember { mutableStateOf<LibraryFolder?>(null) }
    var name by remember { mutableStateOf("") }

    fun reset() {
        mode = FolderDialogMode.LIST
        target = null
        name = ""
    }

    when (mode) {
        FolderDialogMode.CREATE, FolderDialogMode.RENAME -> {
            val renaming = mode == FolderDialogMode.RENAME
            AlertDialog(
                onDismissRequest = { reset() },
                title = {
                    Text(
                        if (renaming) "フォルダ名を変更" else "フォルダを作成",
                        fontFamily = FontFamily.SansSerif,
                    )
                },
                text = {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(40) },
                        label = { Text("フォルダ名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (renaming) {
                                target?.let { onRename(it.id, name.trim()) }
                            } else {
                                onCreate(name.trim())
                            }
                            reset()
                        },
                        enabled = name.isNotBlank(),
                    ) {
                        Text(if (renaming) "保存" else "作成")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { reset() }) {
                        Text("キャンセル")
                    }
                },
            )
        }
        FolderDialogMode.DELETE -> {
            val folder = target
            AlertDialog(
                onDismissRequest = { reset() },
                title = { Text("フォルダを削除", fontFamily = FontFamily.SansSerif) },
                text = {
                    Text(
                        "「${folder?.name.orEmpty()}」を削除します。フォルダ内の本は本棚に残ります。",
                        fontSize = 14.sp,
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            folder?.let { onDelete(it.id) }
                            reset()
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text("削除")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { reset() }) {
                        Text("キャンセル")
                    }
                },
            )
        }
        FolderDialogMode.LIST -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("フォルダ", fontFamily = FontFamily.SansSerif) },
                text = {
                    Column(Modifier.heightIn(max = 420.dp)) {
                        if (folders.isEmpty()) {
                            Text(
                                text = "フォルダはまだありません。",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            LazyColumn(Modifier.heightIn(max = 320.dp)) {
                                items(folders, key = { it.id }) { folder ->
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = folder.name,
                                            fontSize = 14.sp,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        IconButton(
                                            onClick = {
                                                target = folder
                                                name = folder.name
                                                mode = FolderDialogMode.RENAME
                                            },
                                        ) {
                                            Icon(Icons.Outlined.Edit, contentDescription = "フォルダ名を変更")
                                        }
                                        IconButton(
                                            onClick = {
                                                target = folder
                                                mode = FolderDialogMode.DELETE
                                            },
                                        ) {
                                            Icon(
                                                Icons.Outlined.DeleteOutline,
                                                contentDescription = "フォルダを削除",
                                                tint = MaterialTheme.colorScheme.error,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            onClick = {
                                name = ""
                                mode = FolderDialogMode.CREATE
                            },
                        ) {
                            Text("フォルダを作成")
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = onDismiss) {
                        Text("閉じる")
                    }
                },
            )
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    danger: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontFamily = FontFamily.SansSerif) },
        text = { Text(body, fontSize = 14.sp) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = if (danger) {
                    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.textButtonColors()
                },
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        },
    )
}
