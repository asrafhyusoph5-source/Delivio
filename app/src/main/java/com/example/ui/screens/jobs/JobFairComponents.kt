package com.example.ui.screens.jobs

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WorkOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.JobPost
import com.example.ui.theme.BadgeOrange
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextOnYellow
import com.example.ui.theme.YellowPrimary
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object JobFormatters {
    fun formatWorkType(workType: String): String = when (workType.trim().lowercase(Locale.ROOT)) {
        "full_time", "fulltime", "full-time" -> "Full-time"
        "part_time", "parttime", "part-time" -> "Part-time"
        "contract" -> "Contract"
        "freelance" -> "Freelance"
        "internship" -> "Internship"
        else -> workType.replace("_", " ").replaceFirstChar { it.uppercase(Locale.ROOT) }
    }

    fun formatSalary(
        min: Double?,
        max: Double?,
        period: String,
        currency: String,
        hideSalary: Boolean
    ): String {
        if (hideSalary || (min == null && max == null)) return "Salary not shown"
        val cur = currency.ifBlank { "₱" }
        val fmt = NumberFormat.getNumberInstance(Locale.US).apply {
            maximumFractionDigits = 0
        }
        val periodLabel = when (period.trim().lowercase(Locale.ROOT)) {
            "hourly", "hour", "hr" -> "/hr"
            "daily", "day" -> "/day"
            "weekly", "week" -> "/wk"
            "monthly", "month", "mo" -> "/mo"
            "yearly", "annual", "year" -> "/yr"
            "project" -> "/project"
            else -> if (period.isNotBlank()) "/$period" else ""
        }
        return when {
            min != null && max != null && max > min ->
                "$cur${fmt.format(min.toLong())} - $cur${fmt.format(max.toLong())}$periodLabel"
            min != null ->
                "$cur${fmt.format(min.toLong())}$periodLabel"
            max != null ->
                "Up to $cur${fmt.format(max.toLong())}$periodLabel"
            else -> "Salary not shown"
        }
    }

    fun formatRelativePostedTime(createdAtMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
        if (createdAtMillis <= 0L) return "Recently posted"
        val diffMs = (nowMillis - createdAtMillis).coerceAtLeast(0L)
        val minutes = diffMs / 60_000L
        val hours = diffMs / 3_600_000L
        val days = diffMs / 86_400_000L
        return when {
            minutes < 2L -> "Just now"
            minutes < 60L -> "${minutes}m ago"
            hours < 24L -> if (hours == 1L) "1 hour ago" else "$hours hours ago"
            days == 1L -> "1 day ago"
            days < 30L -> "$days days ago"
            else -> SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(createdAtMillis))
        }
    }

    fun formatDate(millis: Long?): String {
        if (millis == null || millis <= 0L) return ""
        return SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(millis))
    }
}

/**
 * Job post card for list screens. Intentionally NEVER displays employer phone/email contact details.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JobCard(
    job: JobPost,
    isSaved: Boolean,
    hasApplied: Boolean,
    onClick: () -> Unit,
    onToggleSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val salaryText = JobFormatters.formatSalary(
        min = job.salaryMin,
        max = job.salaryMax,
        period = job.salaryPeriod,
        currency = job.salaryCurrency,
        hideSalary = job.hideSalary
    )
    val locationLabel = job.cityName.ifBlank { job.cityId.replace("-", " ").replaceFirstChar { it.uppercase(Locale.ROOT) } }
        .ifBlank { "Philippines" }
    val postedLabel = JobFormatters.formatRelativePostedTime(job.createdAt)

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (job.featured) 4.dp else 2.dp),
        border = if (job.featured) BorderStroke(1.5.dp, YellowPrimary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("job_card_${job.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top badges row: Featured ribbon, New badge, Applied chip, Heart Save toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (job.featured) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = YellowPrimary,
                            modifier = Modifier.testTag("job_featured_ribbon_${job.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "Featured",
                                    tint = TextOnYellow,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "Featured",
                                    color = TextOnYellow,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }

                    if (job.isNewBadgeEligible) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SuccessGreen.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = "New",
                                color = SuccessGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (hasApplied) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "Applied",
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onToggleSave,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(48.dp)
                        .testTag("job_save_button_${job.id}")
                ) {
                    Icon(
                        imageVector = if (isSaved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isSaved) "Remove saved job" else "Save job",
                        tint = if (isSaved) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Job Title & Company
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = job.title.ifBlank { "Untitled Position" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Business,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = job.companyName.ifBlank { "Verified Employer" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Chips: city + Remote/Local + workType + category
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                JobMetaChip(
                    icon = Icons.Default.LocationOn,
                    text = locationLabel,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                )

                JobMetaChip(
                    icon = if (job.remote) Icons.Default.Public else null,
                    text = if (job.remote) "Remote" else "Local",
                    containerColor = if (job.remote) Color(0xFFE3F2FD) else BadgeOrange.copy(alpha = 0.14f),
                    contentColor = if (job.remote) Color(0xFF1565C0) else BadgeOrange
                )

                JobMetaChip(
                    text = JobFormatters.formatWorkType(job.workType),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )

                if (job.category.isNotBlank()) {
                    JobMetaChip(
                        text = job.category,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Footer: Salary range + Relative posted time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Payments,
                        contentDescription = null,
                        tint = if (job.hideSalary || (job.salaryMin == null && job.salaryMax == null)) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            SuccessGreen
                        },
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = salaryText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (job.hideSalary || (job.salaryMin == null && job.salaryMax == null)) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = postedLabel,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun JobMetaChip(
    text: String,
    containerColor: Color,
    contentColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(12.dp)
                )
            }
            Text(
                text = text,
                color = contentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
fun JobCardSkeletonList(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(4) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.65f)
                            .height(18.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.45f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .width(84.dp)
                                .height(22.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        )
                        Box(
                            modifier = Modifier
                                .width(72.dp)
                                .height(22.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.55f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                    )
                }
            }
        }
    }
}

@Composable
fun JobEmptyState(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.WorkOutline,
    modifier: Modifier = Modifier,
    actionButton: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(YellowPrimary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = YellowPrimary,
                modifier = Modifier.size(40.dp)
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionButton != null) {
            Spacer(modifier = Modifier.height(4.dp))
            actionButton()
        }
    }
}
