package org.classflow.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    currentSemesterName: String,
    onSyncNow: () -> Unit,
    onSaveQiniuCredentials: (ak: String, sk: String, bucket: String, endpoint: String) -> Unit
) {
    var showQiniuDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的与设置", fontWeight = FontWeight.Bold) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsCategoryCard(
                icon = Icons.Default.School,
                title = "当前学期",
                subtitle = currentSemesterName,
                onClick = { /* Semester switcher */ }
            )

            SettingsCategoryCard(
                icon = Icons.Default.Cloud,
                title = "七牛云 Kodo 同步 (BYOC)",
                subtitle = "配置专属对象存储凭证与增量同步",
                onClick = { showQiniuDialog = true }
            )

            SettingsCategoryCard(
                icon = Icons.Default.DateRange,
                title = "系统日历集成",
                subtitle = "同步课程表到系统日历应用",
                onClick = onSyncNow
            )

            SettingsCategoryCard(
                icon = Icons.Default.Info,
                title = "关于 ClassFlow",
                subtitle = "v1.0.0 (Native Cross-Platform Timetable)",
                onClick = { /* About modal */ }
            )
        }
    }

    if (showQiniuDialog) {
        QiniuConfigDialog(
            onDismiss = { showQiniuDialog = false },
            onSave = { ak, sk, bucket, endpoint ->
                onSaveQiniuCredentials(ak, sk, bucket, endpoint)
                showQiniuDialog = false
            }
        )
    }
}

@Composable
fun SettingsCategoryCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun QiniuConfigDialog(
    onDismiss: () -> Unit,
    onSave: (ak: String, sk: String, bucket: String, endpoint: String) -> Unit
) {
    var ak by remember { mutableStateOf("") }
    var sk by remember { mutableStateOf("") }
    var bucket by remember { mutableStateOf("") }
    var endpoint by remember { mutableStateOf("s3.cn-east-1.qiniucs.com") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("配置七牛云 Kodo 存储") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = endpoint,
                    onValueChange = { endpoint = it },
                    label = { Text("S3 兼容 Endpoint") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = bucket,
                    onValueChange = { bucket = it },
                    label = { Text("存储空间名称 (Bucket)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = ak,
                    onValueChange = { ak = it },
                    label = { Text("Access Key (AK)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = sk,
                    onValueChange = { sk = it },
                    label = { Text("Secret Key (SK)") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(ak, sk, bucket, endpoint) },
                enabled = ak.isNotBlank() && sk.isNotBlank() && bucket.isNotBlank()
            ) {
                Text("保存凭证")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
