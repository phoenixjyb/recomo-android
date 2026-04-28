package com.recomo.user.ui.screens.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.recomo.common.chat.CandidateSet
import com.recomo.common.chat.PromptHint
import com.recomo.common.chat.PromptLanguage
import com.recomo.common.chat.PromptTemplate
import com.recomo.common.chat.PromptTemplates
import com.recomo.common.chat.SimResult
import com.recomo.common.chat.SubjectStand
import com.recomo.common.chat.TrajectoryCandidate
import com.recomo.user.R
import com.recomo.user.ui.theme.StudioChrome
import java.util.Locale

// ════════════════════════════════════════════════════════════════════
// Prompt hint card — shown when the dialogue agent wants to coach the
// user on how to prompt (welcome or clarification).
// ════════════════════════════════════════════════════════════════════

@Composable
fun PromptHintCard(
    hint: PromptHint,
    onExampleTap: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 560.dp),
        color = StudioChrome.panelMuted,
        shape = RoundedCornerShape(StudioChrome.radiusLg),
        border = BorderStroke(1.dp, StudioChrome.panelBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(StudioChrome.accentBlue)
                )
                Text(
                    text = hint.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = StudioChrome.textStrong,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = hint.guidance,
                style = MaterialTheme.typography.bodyMedium,
                color = StudioChrome.textSecondary
            )
            if (hint.requiredFields.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    hint.requiredFields.forEach { field ->
                        Surface(
                            color = Color(0x1F00A3FF),
                            shape = RoundedCornerShape(999.dp),
                            border = BorderStroke(1.dp, Color(0x3300A3FF))
                        ) {
                            Text(
                                text = "• $field",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = StudioChrome.accentBlue
                            )
                        }
                    }
                }
            }
            if (hint.examplePrompts.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.chat_prompt_examples),
                    style = MaterialTheme.typography.labelMedium,
                    color = StudioChrome.textTertiary
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    hint.examplePrompts.forEach { example ->
                        OutlinedButton(
                            onClick = { onExampleTap(example) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(StudioChrome.radiusMd),
                            border = BorderStroke(1.dp, StudioChrome.panelBorder),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = StudioChrome.textStrong
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = example,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// Candidate carousel — horizontal LazyRow of TrajectoryCandidate cards.
// ════════════════════════════════════════════════════════════════════

@Composable
fun CandidateCarousel(
    candidateSet: CandidateSet,
    selectedCandidateId: String?,
    onPreview: (TrajectoryCandidate) -> Unit,
    onSelect: (TrajectoryCandidate) -> Unit,
    modifier: Modifier = Modifier
) {
    // Non-lazy Row + horizontalScroll. With ≤ 5 candidates the laziness buys
    // nothing, and it dodges the intrinsic-height measurement bug LazyRow
    // has when rendered inside a LazyColumn item without an explicit height.
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(
                R.string.chat_candidates_label,
                candidateSet.candidates.size
            ),
            style = MaterialTheme.typography.labelMedium,
            color = StudioChrome.textTertiary
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            candidateSet.candidates.forEach { candidate ->
                CandidateCard(
                    candidate = candidate,
                    isSelected = candidate.id == selectedCandidateId,
                    onPreview = { onPreview(candidate) },
                    onSelect = { onSelect(candidate) }
                )
            }
        }
    }
}

@Composable
private fun CandidateCard(
    candidate: TrajectoryCandidate,
    isSelected: Boolean,
    onPreview: () -> Unit,
    onSelect: () -> Unit
) {
    val borderColor = if (isSelected) StudioChrome.accentBlue else StudioChrome.panelBorder
    val borderWidth = if (isSelected) 1.6.dp else 1.dp
    Surface(
        modifier = Modifier
            .width(260.dp)
            .heightIn(min = 230.dp),
        color = StudioChrome.panelMuted,
        shape = RoundedCornerShape(StudioChrome.radiusLg),
        border = BorderStroke(borderWidth, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "#${candidate.rank}",
                    style = MaterialTheme.typography.labelMedium,
                    color = StudioChrome.accentBlue,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = candidateShortName(candidate.name),
                    style = MaterialTheme.typography.titleSmall,
                    color = StudioChrome.textStrong,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                SimStatusBadge(candidate.simResult)
            }

            // Duration + subject distance
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MetricPill(stringResource(R.string.chat_metric_duration, candidate.durationSec))
                candidate.subjectStand?.let { ss ->
                    if (ss.confident) {
                        MetricPill("%.1fm".format(ss.distanceM))
                    }
                }
            }

            // Inline 3D trajectory preview
            candidate.tumText?.let { tum ->
                if (tum.isNotBlank()) {
                    TrajectoryMiniPreview(tumText = tum)
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Actions
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = onPreview,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(StudioChrome.radiusMd),
                    border = BorderStroke(1.dp, StudioChrome.panelBorderActive),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StudioChrome.accentBlue),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Visibility, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.chat_preview_title), style = MaterialTheme.typography.labelMedium)
                }
                Button(
                    onClick = onSelect,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(StudioChrome.radiusMd),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) StudioChrome.success else StudioChrome.accentBlue
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(
                            if (isSelected) R.string.chat_candidate_selected
                            else R.string.chat_candidate_select
                        ),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricPill(text: String) {
    Surface(
        color = Color(0x14FFFFFF),
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = StudioChrome.textSecondary
        )
    }
}

@Composable
private fun SimStatusBadge(sim: SimResult?) {
    if (sim == null) return
    val (icon, tint) = when {
        !sim.feasible -> Icons.Default.Error to StudioChrome.danger
        sim.warnings.isNotEmpty() -> Icons.Default.Warning to StudioChrome.warning
        else -> Icons.Default.CheckCircle to StudioChrome.success
    }
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(16.dp)
    )
}

// ════════════════════════════════════════════════════════════════════
// Prompt template chip row — shown when the conversation is empty so
// the user has a starting surface. Taps prefill the input with a
// suggested prompt the user can edit before sending.
// ════════════════════════════════════════════════════════════════════

@Composable
fun PromptTemplateChipRow(
    onTemplateTap: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val lang = currentPromptLanguage()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.chat_templates_label),
            style = MaterialTheme.typography.labelMedium,
            color = StudioChrome.textTertiary,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(
                items = PromptTemplates.all,
                key = { it.id }
            ) { template ->
                PromptTemplateChip(
                    template = template,
                    lang = lang,
                    onTap = { onTemplateTap(PromptTemplates.prompt(template, lang)) }
                )
            }
        }
    }
}

@Composable
private fun PromptTemplateChip(
    template: PromptTemplate,
    lang: PromptLanguage,
    onTap: () -> Unit
) {
    Surface(
        onClick = onTap,
        color = StudioChrome.panelMuted,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, StudioChrome.panelBorder),
        modifier = Modifier.widthIn(min = 100.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = template.category.uppercase(Locale.US),
                style = MaterialTheme.typography.labelSmall,
                color = StudioChrome.textTertiary,
                fontSize = 9.sp
            )
            Text(
                text = PromptTemplates.label(template, lang),
                style = MaterialTheme.typography.labelMedium,
                color = StudioChrome.textStrong,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Pick EN or ZH for the template catalogue based on the currently-active
 * Compose configuration (driven by AppCompatDelegate's application locales).
 */
@Composable
private fun currentPromptLanguage(): PromptLanguage {
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val tag = config.locales.get(0)?.language ?: Locale.getDefault().language
    return if (tag.startsWith("zh", ignoreCase = true)) PromptLanguage.ZH else PromptLanguage.EN
}

// ════════════════════════════════════════════════════════════════════
// Selected candidate banner — sticky above input when a candidate has
// been selected, offers the Execute CTA.
// ════════════════════════════════════════════════════════════════════

@Composable
fun SelectedCandidateBanner(
    candidate: TrajectoryCandidate,
    onExecute: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Execute requires both a cloud-authored execution ref AND a passing sim.
    // The cloud is expected to only surface candidates that pass its PnC sim,
    // so in practice `executionRef != null` is the real gate. The extra
    // `simResult?.feasible != false` guard is belt-and-suspenders.
    val canExecute = candidate.executionRef != null && candidate.simResult?.feasible != false
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = StudioChrome.panelMuted,
        shape = RoundedCornerShape(StudioChrome.radiusMd),
        border = BorderStroke(1.dp, StudioChrome.panelBorderActive)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = StudioChrome.success,
                modifier = Modifier.size(18.dp)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.chat_selected_banner_title),
                    style = MaterialTheme.typography.labelSmall,
                    color = StudioChrome.textTertiary
                )
                Text(
                    text = candidate.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = StudioChrome.textStrong,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!canExecute) {
                    Text(
                        text = stringResource(R.string.chat_candidate_preview_only),
                        style = MaterialTheme.typography.labelSmall,
                        color = StudioChrome.textTertiary
                    )
                }
            }
            OutlinedButton(
                onClick = onClear,
                shape = RoundedCornerShape(StudioChrome.radiusMd),
                border = BorderStroke(1.dp, StudioChrome.panelBorder),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = StudioChrome.textSecondary),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = stringResource(R.string.chat_selected_clear),
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Button(
                onClick = onExecute,
                enabled = canExecute,
                shape = RoundedCornerShape(StudioChrome.radiusMd),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StudioChrome.success,
                    disabledContainerColor = StudioChrome.panelSoft
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.chat_execute_on_robot),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * Shorten a long candidate name like "AIgen-20260416114722-0034" to
 * "AIgen-0416-1147-34" for card display. If the name doesn't match
 * the expected timestamp pattern, hash the name into a short suffix.
 */
private fun candidateShortName(fullName: String): String {
    // Pattern: AIgen-YYYYMMDDHHmmss-NNNN or AIgen-YYYYMMDDHHmmss-NNNN
    val m = Regex("AIgen-(?:\\d{4})(\\d{2})(\\d{2})(\\d{2})(\\d{2})\\d{2}-(\\d+)")
        .find(fullName)
    if (m != null) {
        val (mm, dd, hh, mi, seq) = m.destructured
        return "AIgen-$mm$dd-$hh$mi-$seq"
    }
    // Fallback: keep first 12 chars + short hash
    if (fullName.length > 16) {
        val hash = (fullName.hashCode() and 0xFFFF).toString(16)
        return fullName.take(12) + "-" + hash
    }
    return fullName
}

// ── Quick-refine chips (shown below candidate carousel) ──────────

private data class RefineOption(val label: String, val prompt: String)

private val refineOptions = listOf(
    RefineOption("再来 5 条", "再给我 5 条不同的运镜候选"),
    RefineOption("更短的", "给我更短的版本，10 秒以内"),
    RefineOption("更慢", "速度慢一些，更平稳"),
    RefineOption("换个角度", "换一个完全不同的拍摄角度"),
    RefineOption("More dramatic", "Make it more dramatic and cinematic"),
)

@Composable
fun QuickRefineChips(
    onRefine: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        refineOptions.forEach { option ->
            Surface(
                onClick = { onRefine(option.prompt) },
                color = Color(0xFF252530),
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, Color(0xFF3A3A45))
            ) {
                Text(
                    text = option.label,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    color = Color(0xFFAAB0CC),
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
        }
    }
}
