package com.comfyremote.app.feature.update


import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.comfyremote.app.ui.ComfyTheme
import com.comfyremote.app.ui.L10n
import com.comfyremote.app.ui.Mono
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import com.comfyremote.app.*

@Composable
fun UpdateScreen(vm: MainViewModel, s: UiState) {
    BackHandler { vm.back() }
    val context = LocalContext.current
    val u = s.update
    Column(
        Modifier.fillMaxSize().imePadding().padding(28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(L10n.t("软件更新", "Software update"), fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(L10n.t("当前版本 v", "Current version v") + vm.currentVersionPublic(), fontFamily = Mono, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = s.ghToken,
            onValueChange = { vm.updateGhToken(it) },
            label = { Text(L10n.t("GitHub Token（可选）", "GitHub Token (optional)")) },
            supportingText = {
                Text(L10n.t("仓库公开时无需填写。若仍是私有：GitHub → Settings → Developer settings → Fine-grained tokens，仅授权 ComfyRemote 仓库、Contents:Read-only", "Not needed for a public repo. If still private: GitHub → Settings → Developer settings → Fine-grained tokens, scoped to ComfyRemote with Contents:Read-only"))
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { vm.checkUpdate() },
                enabled = u?.downloading != true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.weight(1f).height(50.dp)
            ) { Text(L10n.t("检查更新", "Check updates")) }
        }
        Spacer(Modifier.height(18.dp))
        when {
            u?.release != null -> {
                val rel = u.release
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(rel.name.ifEmpty { rel.tag }, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(rel.tag, fontFamily = Mono, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        if (rel.notes.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                rel.notes.take(1200),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        if (u.downloading) {
                            LinearProgressIndicator(progress = u.progress ?: 0f, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(4.dp))
                            Text(
                                (L10n.t("下载中 ", "Downloading ") + "${(u.progress ?: 0f) * 100}%").let { if (u.progress == null) L10n.t("下载中…", "Downloading…") else it },
                                fontFamily = Mono, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Button(
                                onClick = { vm.downloadUpdate() },
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) { Text(L10n.t("下载并安装", "Download & install")) }
                            val clipboard = LocalClipboardManager.current
                            TextButton(onClick = {
                                clipboard.setText(AnnotatedString(rel.browserUrl))
                                android.widget.Toast.makeText(context, L10n.t("链接已复制", "Link copied"), android.widget.Toast.LENGTH_SHORT).show()
                            }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                                Text(L10n.t("复制下载链接（浏览器打开更快）", "Copy download link (faster in a browser)"), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
            u?.status != null -> Text(
                u.status,
                color = if (u.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            L10n.t("首次安装时系统会请求「允许安装未知应用」权限，同意一次即可。更新包签名与本应用一致，正常覆盖安装不会丢数据。", "Android will ask for the install-unknown-apps permission once. Updates share this app signature, so existing data is kept."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

// ============================ WORKFLOWS ============================

