package com.lanraragi.reader.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lanraragi.reader.data.metadata.MetadataApplyBlockReason
import com.lanraragi.reader.data.metadata.MetadataApplyPolicy
import com.lanraragi.reader.data.metadata.MetadataFieldName
import com.lanraragi.reader.data.metadata.MetadataMergePolicy
import com.lanraragi.reader.data.metadata.MetadataSnapshot
import com.lanraragi.reader.data.metadata.MetadataState
import com.lanraragi.reader.data.metadata.MetadataStateStatus
import com.lanraragi.reader.data.metadata.MetadataTagOperation
import com.lanraragi.reader.data.metadata.matching.MatchEvidence
import com.lanraragi.reader.data.metadata.matching.MatchResult
import com.lanraragi.reader.domain.metadata.CanonicalTag
import com.lanraragi.reader.domain.metadata.MetadataField
import com.lanraragi.reader.domain.metadata.MetadataPatch
import com.lanraragi.reader.domain.metadata.MetadataProvenance
import com.lanraragi.reader.domain.metadata.TagSource

internal data class MetadataEditSubmission(
    val title: String?,
    val tags: String?,
    val summary: String?,
)

internal data class ManualMetadataPatch(
    val baseline: MetadataSnapshot,
    val patch: MetadataPatch,
)

internal fun userMetadataApplyPolicy(approveRebasedSnapshot: Boolean = false) = MetadataApplyPolicy(
    mergePolicy = MetadataMergePolicy(
        overwriteExistingFields = true,
        preserveUserOverrides = false,
        protectUserTags = false,
    ),
    approveRebasedSnapshot = approveRebasedSnapshot,
)

internal fun providerMetadataApplyPolicy(approveRebasedSnapshot: Boolean = false) = MetadataApplyPolicy(
    mergePolicy = MetadataMergePolicy(
        overwriteExistingFields = true,
        preserveUserOverrides = true,
        protectUserTags = true,
    ),
    approveRebasedSnapshot = approveRebasedSnapshot,
)

internal fun buildManualMetadataPatch(
    baseline: MetadataSnapshot,
    submission: MetadataEditSubmission,
    timestamp: Long = System.currentTimeMillis(),
): ManualMetadataPatch {
    val provenance = MetadataProvenance(providerId = "user", fetchedAt = timestamp)
    val overrides = baseline.userOverrides.toMutableSet()
    val title = submission.title?.trim()?.takeIf { it != baseline.title.orEmpty() }?.let {
        overrides += MetadataFieldName.TITLE
        MetadataField(it, provenance)
    }
    val summary = submission.summary?.trim()?.takeIf { it != baseline.summary.orEmpty() }?.let {
        overrides += MetadataFieldName.SUMMARY
        MetadataField(it, provenance)
    }

    val existingTags = baseline.canonicalTags
    val requestedTags = submission.tags?.split(',')
        ?.map(String::trim)
        ?.filter(String::isNotEmpty)
        ?.map { CanonicalTag.parse(it, source = TagSource.USER) }
        ?.associateBy(CanonicalTag::full)
    val addTags = requestedTags
        ?.filterKeys { it !in existingTags }
        ?.values
        ?.toSet()
        .orEmpty()
    val removeTags = requestedTags
        ?.let { requested -> existingTags.filterKeys { it !in requested }.values.toSet() }
        .orEmpty()
    if (addTags.isNotEmpty() || removeTags.isNotEmpty()) overrides += MetadataFieldName.TAGS
    val changedTagKeys = (addTags + removeTags).mapTo(linkedSetOf(), CanonicalTag::full)

    return ManualMetadataPatch(
        baseline = baseline.copy(userOverrides = overrides),
        patch = MetadataPatch(
            title = title,
            summary = summary,
            addTags = addTags,
            removeTags = removeTags,
            tagProvenance = changedTagKeys.associateWith { provenance },
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MetadataWorkbenchSheet(
    initial: MetadataSnapshot,
    state: MetadataState,
    candidates: List<MatchResult> = emptyList(),
    onPreviewCandidate: (MatchResult) -> Unit = {},
    onFetchCandidate: (MatchResult) -> Unit = {},
    nativeProviderLoading: Boolean = false,
    onPreview: (MetadataEditSubmission) -> Unit,
    onApply: (approveRebasedSnapshot: Boolean) -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember(initial) { mutableStateOf(initial.title.orEmpty()) }
    var tags by remember(initial) {
        mutableStateOf(initial.tags.sortedBy(CanonicalTag::full).joinToString(",") { it.raw })
    }
    var summary by remember(initial) { mutableStateOf(initial.summary.orEmpty()) }
    var includeTitle by remember { mutableStateOf(false) }
    var includeTags by remember { mutableStateOf(false) }
    var includeSummary by remember { mutableStateOf(false) }
    val busy = state.status == MetadataStateStatus.LOADING || state.status == MetadataStateStatus.SAVING
    val plan = state.plan
    val nonRebaseBlocks = plan?.blockReasons.orEmpty() -
        MetadataApplyBlockReason.BASE_CHANGED_REVIEW_REQUIRED
    // Local SAF metadata is persisted through Room and deliberately carries
    // TARGET_IS_NOT_REMOTE as an audit marker; it must not disable its local
    // save action. All other planner blocks remain actionable for the UI.
    val actionableBlocks = if (state.isRemote) {
        nonRebaseBlocks
    } else {
        nonRebaseBlocks - MetadataApplyBlockReason.TARGET_IS_NOT_REMOTE
    }
    val canApply = plan?.diff?.hasEffectiveChanges == true && actionableBlocks.isEmpty() && !busy

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("元数据工作台", style = MaterialTheme.typography.titleLarge)
            if (candidates.isNotEmpty()) {
                Text("匹配候选", style = MaterialTheme.typography.titleMedium)
                candidates.forEach { result ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        val candidate = result.candidate
                        Text(
                            candidate.title ?: candidate.sourceUrl ?: candidate.sourceId ?: candidate.providerId,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            "${candidate.providerId} · ${(result.score * 100).toInt()}% · ${result.evidence.joinToString("、", transform = MatchEvidence::label)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (result.lowConfidence) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(
                            onClick = { onPreviewCandidate(result) },
                            enabled = candidate.patch != null && !busy,
                        ) { Text(if (candidate.patch == null) "仅供检索参考" else "纳入应用预览") }
                        if (
                            candidate.providerId in setOf("ehentai", "nhentai") &&
                            candidate.sourceId != null &&
                            candidate.sourceUrl != null
                        ) {
                            TextButton(
                                onClick = { onFetchCandidate(result) },
                                enabled = !busy && !nativeProviderLoading,
                            ) { Text(if (nativeProviderLoading) "抓取中" else "抓取完整元数据") }
                        }
                    }
                }
                HorizontalDivider()
            }
            SelectableMetadataField("标题", includeTitle, { includeTitle = it }) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    enabled = includeTitle && !busy,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            SelectableMetadataField("标签", includeTags, { includeTags = it }) {
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    enabled = includeTags && !busy,
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            SelectableMetadataField("简介", includeSummary, { includeSummary = it }) {
                OutlinedTextField(
                    value = summary,
                    onValueChange = { summary = it },
                    enabled = includeSummary && !busy,
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDiscard, enabled = state.hasPendingPatch && !busy) {
                    Text("放弃待应用修改")
                }
                TextButton(
                    onClick = {
                        onPreview(
                            MetadataEditSubmission(
                                title = title.takeIf { includeTitle },
                                tags = tags.takeIf { includeTags },
                                summary = summary.takeIf { includeSummary },
                            ),
                        )
                    },
                    enabled = (includeTitle || includeTags || includeSummary) && !busy,
                ) {
                    Text("生成预览")
                }
            }

            plan?.let { currentPlan ->
                HorizontalDivider()
                Text("应用预览", style = MaterialTheme.typography.titleMedium)
                if (currentPlan.baseChanged) {
                    Text(
                        "服务器元数据已变化；保存时会基于最新内容重新合并。",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                currentPlan.diff.fields.forEach { change ->
                    Text("${fieldLabel(change.field)}: ${change.before.orEmpty()} -> ${change.after.orEmpty()}")
                }
                currentPlan.diff.tags.forEach { change ->
                    val marker = if (change.operation == MetadataTagOperation.ADD) "+" else "-"
                    Text("$marker ${change.tag.raw}")
                }
                currentPlan.diff.conflicts.forEach { conflict ->
                    Text(
                        conflict.explanation ?: "${conflict.key} 未应用",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (!currentPlan.diff.hasEffectiveChanges && currentPlan.diff.conflicts.isEmpty()) {
                    Text("没有需要应用的变化")
                }
                state.error?.let { Text(it.message, color = MaterialTheme.colorScheme.error) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("关闭") }
                    TextButton(
                        onClick = { onApply(currentPlan.baseChanged) },
                        enabled = canApply,
                    ) {
                        Text(if (currentPlan.baseChanged) "确认重合并并保存" else "确认保存")
                    }
                }
            }
            if (plan == null) {
                state.error?.let { Text(it.message, color = MaterialTheme.colorScheme.error) }
            }
            if (state.status == MetadataStateStatus.SAVED && !state.hasPendingPatch) {
                Text("元数据已保存", color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SelectableMetadataField(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Checkbox(checked = checked, onCheckedChange = onCheckedChange)
            Text(label, modifier = Modifier.padding(top = 12.dp))
        }
        content()
    }
}

private fun fieldLabel(field: MetadataFieldName): String = when (field) {
    MetadataFieldName.TITLE -> "标题"
    MetadataFieldName.SUMMARY -> "简介"
    MetadataFieldName.SOURCE_URL -> "来源"
    MetadataFieldName.TAGS -> "标签"
}

private fun MatchEvidence.label(): String = when (this) {
    MatchEvidence.URL -> "来源地址"
    MatchEvidence.SOURCE_TAG -> "来源标识"
    MatchEvidence.FILENAME_ID -> "文件名 ID"
    MatchEvidence.TITLE_EXACT -> "标题一致"
    MatchEvidence.TAG_INTERSECTION -> "标签交集"
    MatchEvidence.COVER_HASH -> "封面哈希"
    MatchEvidence.FUZZY_TITLE -> "标题相似"
}
