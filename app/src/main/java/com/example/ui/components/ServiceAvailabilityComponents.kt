package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextOnYellow
import com.example.ui.theme.YellowPrimary
import com.example.util.AvailabilityResult

/**
 * Displays the real service unavailability reason with a clickable "Why?" link
 * right under the message that opens [AvailabilityWhyDialog] with each check & value.
 */
@Composable
fun AvailabilityWarningCard(
    result: AvailabilityResult,
    modifier: Modifier = Modifier,
    overrideMessage: String? = null
) {
    var showWhyDialog by remember { mutableStateOf(false) }
    val message = overrideMessage?.takeIf { it.isNotBlank() }
        ?: result.reason.ifBlank { "Service availability check failed" }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = ErrorRed.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.38f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("availability_warning_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Unavailable",
                tint = ErrorRed,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(20.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = message,
                    color = ErrorRed,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.testTag("availability_warning_reason")
                )
                Text(
                    text = "Why?",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { showWhyDialog = true }
                        .padding(vertical = 4.dp, horizontal = 2.dp)
                        .testTag("availability_why_link")
                )
            }
        }
    }

    if (showWhyDialog) {
        AvailabilityWhyDialog(
            result = result,
            onDismiss = { showWhyDialog = false }
        )
    }
}

/**
 * Compact message + "Why?" trigger for tight areas such as checkout bottom bars.
 */
@Composable
fun AvailabilityInlineWhyTrigger(
    result: AvailabilityResult,
    modifier: Modifier = Modifier
) {
    var showWhyDialog by remember { mutableStateOf(false) }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = "Availability info",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = "Why?",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable { showWhyDialog = true }
                .padding(horizontal = 4.dp, vertical = 2.dp)
                .testTag("availability_why_link")
        )
    }
    if (showWhyDialog) {
        AvailabilityWhyDialog(
            result = result,
            onDismiss = { showWhyDialog = false }
        )
    }
}

@Composable
fun AvailabilityWhyDialog(
    result: AvailabilityResult,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("availability_why_dialog"),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = YellowPrimary,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "Service Availability Checks",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (result.reason.isNotBlank()) {
                    Text(
                        text = result.reason,
                        color = ErrorRed,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    HorizontalDivider()
                }
                result.checks.forEach { check ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(
                                    if (check.passed) SuccessGreen.copy(alpha = 0.14f)
                                    else ErrorRed.copy(alpha = 0.14f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (check.passed) Icons.Default.CheckCircle else Icons.Default.Close,
                                contentDescription = if (check.passed) "Passed" else "Failed",
                                tint = if (check.passed) SuccessGreen else ErrorRed,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = check.label,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = check.value,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = YellowPrimary,
                    contentColor = TextOnYellow
                ),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("availability_why_close_button")
            ) {
                Text("OK", fontWeight = FontWeight.Bold)
            }
        }
    )
}
