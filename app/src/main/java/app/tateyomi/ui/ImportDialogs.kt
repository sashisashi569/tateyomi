package app.tateyomi.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ImportBookConflictDialog(
    conflict: ImportBookConflict,
    onResolve: (Boolean) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onResolve(false) },
        title = { Text("同じ本があります", fontFamily = FontFamily.SansSerif) },
        text = {
            Text(
                "『${conflict.existing.title}』は既に本棚にあります。置き換えますか？",
                fontSize = 14.sp,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onResolve(true) },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text("置き換え")
            }
        },
        dismissButton = {
            TextButton(onClick = { onResolve(false) }) {
                Text("キャンセル")
            }
        },
    )
}

@Composable
fun ImportArchiveFolderDialog(
    choice: ImportArchiveFolderChoice,
    onResolve: (String?) -> Unit,
) {
    var folderName by remember(choice) { mutableStateOf(choice.archiveName) }
    AlertDialog(
        onDismissRequest = { onResolve(null) },
        title = { Text("一括追加", fontFamily = FontFamily.SansSerif) },
        text = {
            Column {
                Text(
                    "フォルダを作成し、その中に追加しますか？",
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("フォルダ名") },
                    singleLine = true,
                    isError = folderName.isBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onResolve(folderName.trim()) },
                enabled = folderName.isNotBlank(),
            ) {
                Text("作成")
            }
        },
        dismissButton = {
            TextButton(onClick = { onResolve(null) }) {
                Text("作成しない")
            }
        },
    )
}

@Composable
fun ImportFolderConflictDialog(
    conflict: ImportFolderConflict,
    onResolve: (Boolean) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onResolve(false) },
        title = { Text("フォルダを統合", fontFamily = FontFamily.SansSerif) },
        text = {
            Text(
                "「${conflict.existing.name}」は既にあります。フォルダ内の本を統合しますか？",
                fontSize = 14.sp,
            )
        },
        confirmButton = {
            TextButton(onClick = { onResolve(true) }) {
                Text("統合")
            }
        },
        dismissButton = {
            TextButton(onClick = { onResolve(false) }) {
                Text("キャンセル")
            }
        },
    )
}
