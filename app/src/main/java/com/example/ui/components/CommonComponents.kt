package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ApiErrorInfo
import com.example.ui.theme.*

fun copyToClipboard(context: Context, label: String, text: String, onCopied: () -> Unit) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    onCopied()
}

@Composable
fun LtrField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 6,
    readOnly: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    trailingIcon: @Composable (() -> Unit)? = null,
    testTag: String? = null
) {
    val fieldModifier = if (testTag != null) modifier.testTag(testTag) else modifier
    Column(modifier = fieldModifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = if (placeholder.isNotEmpty()) {
                    {
                        Text(
                            text = placeholder,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }
                } else null,
                textStyle = TextStyle(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 13.sp,
                    color = TextPrimary
                ),
                singleLine = singleLine,
                minLines = minLines,
                maxLines = maxLines,
                readOnly = readOnly,
                visualTransformation = visualTransformation,
                keyboardOptions = keyboardOptions,
                trailingIcon = trailingIcon,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricCyan,
                    unfocusedBorderColor = SlateBorder,
                    focusedContainerColor = CodeSurface,
                    unfocusedContainerColor = CodeSurface,
                    cursorColor = CloudflareOrange
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun LtrCodeEditorBox(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "// Write code here...",
    fixedHeightDp: Int? = 280,
    testTag: String = "code_editor_input"
) {
    val lineCount = remember(value.text) { maxOf(1, value.text.count { it == '\n' } + 1) }
    val lineNumbersText = remember(lineCount) {
        (1..lineCount).joinToString("\n")
    }
    val vScroll = rememberScrollState()
    val hScroll = rememberScrollState()

    val baseModifier = if (fixedHeightDp != null) {
        modifier.fillMaxWidth().height(fixedHeightDp.dp)
    } else {
        modifier.fillMaxWidth()
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = baseModifier
                .clip(RoundedCornerShape(12.dp))
                .background(CodeSurface)
                .border(1.dp, SlateBorder, RoundedCornerShape(12.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(vScroll)
            ) {
                // Synced line gutter covering ALL lines
                Box(
                    modifier = Modifier
                        .width(44.dp)
                        .defaultMinSize(minHeight = (fixedHeightDp ?: 260).dp)
                        .background(CodeGutter)
                        .padding(vertical = 12.dp, horizontal = 6.dp),
                    contentAlignment = Alignment.TopEnd
                ) {
                    Text(
                        text = lineNumbersText,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp,
                        lineHeight = 19.sp,
                        color = TextMuted
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(hScroll)
                        .padding(12.dp)
                ) {
                    if (value.text.isEmpty()) {
                        Text(
                            text = placeholder,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 12.5.sp,
                            lineHeight = 19.sp,
                            color = TextMuted
                        )
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        textStyle = TextStyle(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 12.5.sp,
                            lineHeight = 19.sp,
                            color = TextPrimary
                        ),
                        cursorBrush = SolidColor(CloudflareOrange),
                        modifier = Modifier
                            .widthIn(min = 340.dp)
                            .defaultMinSize(minHeight = ((fixedHeightDp ?: 260) - 24).dp)
                            .testTag(testTag)
                    )
                }
            }
        }
    }
}

@Composable
fun LtrReadOnlyConsole(
    text: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    accentColor: Color = ElectricCyan,
    onCopy: (() -> Unit)? = null
) {
    val context = LocalContext.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = CodeSurface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, SlateBorder)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            if (title != null || onCopy != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (title != null) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelMedium,
                            color = accentColor
                        )
                    }
                    if (text.isNotBlank()) {
                        IconButton(
                            onClick = {
                                copyToClipboard(context, title ?: "output", text) {
                                    onCopy?.invoke()
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "کپی متن",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    Text(
                        text = text.ifBlank { "—" },
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
fun ErrorBannerCard(
    error: ApiErrorInfo,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showRaw by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = StatusErrorContainer,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, StatusError.copy(alpha = 0.55f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = "خطا",
                    tint = StatusError,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = error.persianSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                    if (error.code != null || error.httpStatus > 0) {
                        Text(
                            text = "کد خطا: ${error.code ?: error.httpStatus}",
                            style = MaterialTheme.typography.labelSmall,
                            color = StatusError
                        )
                    }
                }
                if (onDismiss != null) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "بستن خطا", tint = TextSecondary)
                    }
                }
            }

            if (error.rawDetail.isNotBlank()) {
                TextButton(
                    onClick = { showRaw = !showRaw },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = if (showRaw) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (showRaw) "مخفی کردن جزئیات فنی" else "نمایش جزئیات فنی",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
                AnimatedVisibility(visible = showRaw) {
                    LtrReadOnlyConsole(text = error.rawDetail, accentColor = StatusError)
                }
            }
        }
    }
}

@Composable
fun StatusPill(
    text: String,
    color: Color,
    containerColor: Color,
    icon: ImageVector? = null
) {
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(13.dp)
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun SectionCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    headerAction: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = SlateSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, SlateBorder.copy(alpha = 0.7f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = CloudflareOrangeContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = CloudflareOrange,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
                if (headerAction != null) {
                    headerAction()
                }
            }
            content()
        }
    }
}

@Composable
fun EmptyStateBox(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = SlateElevated.copy(alpha = 0.55f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, SlateBorder.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
fun ConfirmDeleteDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SlateElevated,
        title = { Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium, color = TextSecondary) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = StatusError, contentColor = TextPrimary),
                modifier = Modifier.testTag("confirm_delete_button")
            ) {
                Text("حذف")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف", color = TextSecondary)
            }
        }
    )
}
