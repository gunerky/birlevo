package com.example.contentstudio.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.contentstudio.R
import com.example.contentstudio.batch.*
import com.example.contentstudio.documents.*

internal fun batchDocumentIssueText(issue: BatchDocumentIssue) = when(issue) {
    BatchDocumentIssue.TARGET -> R.string.batch_document_target
    BatchDocumentIssue.SHEET -> R.string.batch_document_sheet
    BatchDocumentIssue.CONSENT -> R.string.office_consent
    BatchDocumentIssue.ACCESS -> R.string.error_file_access
    BatchDocumentIssue.ENCODING -> R.string.document_bad_encoding
    BatchDocumentIssue.INVALID -> R.string.document_invalid
    BatchDocumentIssue.LIMIT -> R.string.document_table_limits
    BatchDocumentIssue.OUTPUT_LIMIT -> R.string.batch_documents_output_limit
    BatchDocumentIssue.PDF_LIMIT -> R.string.document_pdf_limit
    BatchDocumentIssue.EXPORT -> R.string.pdf_export_error
    BatchDocumentIssue.MEMORY -> R.string.error_memory
    BatchDocumentIssue.OFFICE -> R.string.office_error
    BatchDocumentIssue.OFFICE_LIMIT -> R.string.office_limits
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BatchDocumentOptions(options: BatchOptions, enabled: Boolean, change: (BatchOptions) -> Unit) {
    Text(stringResource(R.string.output_format))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DocumentTarget.entries.forEach { target -> FilterChip(options.documentTarget == target,
            { change(options.copy(documentTarget = target)) }, enabled = enabled, label = { Text(target.name) },
            modifier = Modifier.heightIn(min = 48.dp).testTag("batch-document-target-${target.name}")) }
    }
    Text(stringResource(R.string.document_input_format), style = MaterialTheme.typography.titleSmall)
    Text(stringResource(R.string.document_encoding))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DocumentEncoding.entries.forEach { encoding -> FilterChip(options.documentEncoding == encoding,
            { change(options.copy(documentEncoding = encoding)) }, enabled = enabled,
            label = { Text(encoding.charsetName ?: stringResource(R.string.document_auto)) },
            modifier = Modifier.heightIn(min = 48.dp).testTag("batch-document-encoding-${encoding.name}")) }
    }
    Text(stringResource(R.string.document_separator))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(null to R.string.document_auto, ',' to R.string.document_comma, ';' to R.string.document_semicolon, '\t' to R.string.document_tab).forEach { (delimiter, label) ->
            FilterChip(options.documentDelimiter == delimiter, { change(options.copy(documentDelimiter = delimiter)) }, enabled = enabled,
                label = { Text(stringResource(label)) }, modifier = Modifier.heightIn(min = 48.dp).testTag("batch-document-separator-${delimiter?.code ?: -1}"))
        }
    }
    Text(stringResource(R.string.document_losses))
    WorkspaceHelp { Text(stringResource(R.string.document_table_limits)); Text(stringResource(R.string.office_limits)) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BatchDocumentRow(item: BatchItem, options: BatchOptions, index: Int, state: BatchState, model: BatchViewModel) {
    val meta = item.document ?: return
    val supported = options.documentTarget in DocumentPolicy.targets(meta.kind)
    if (!supported) Text(stringResource(R.string.batch_document_target), color = MaterialTheme.colorScheme.error)
    if (meta.office == null) return
    var expanded by rememberSaveable(item.id) { mutableStateOf(false) }
    val key = meta.key(item, options)
    val approved = key in state.documentApproved
    if (meta.sheet < 0) Text(stringResource(R.string.batch_document_sheet), color = MaterialTheme.colorScheme.error)
    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.heightIn(min = 48.dp).testTag("batch-document-review-$index")) {
        Text(stringResource(R.string.batch_document_review))
    }
    if (approved && !expanded) Text(stringResource(R.string.office_consent), modifier = Modifier.testTag("batch-document-approved-$index"))
    if (expanded) {
        if (meta.office == OfficeFormat.XLSX) {
            Text(stringResource(R.string.office_sheet))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                meta.sheets.forEachIndexed { sheet, name -> FilterChip(meta.sheet == sheet,
                    { model.document(item.id, sheet, meta.mode) }, enabled = !state.busy,
                    label = { Text(name) }, modifier = Modifier.heightIn(min = 48.dp).testTag("batch-document-sheet-$index-$sheet")) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OfficeCellMode.entries.forEach { mode -> FilterChip(meta.mode == mode,
                    { model.document(item.id, meta.sheet, mode) }, enabled = !state.busy && meta.sheet >= 0,
                    label = { Text(stringResource(if (mode == OfficeCellMode.FORMULAS) R.string.office_formulas else R.string.office_values)) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("batch-document-mode-$index-${mode.name}")) }
            }
            Text(stringResource(R.string.office_dates, if (meta.date1904) 1904 else 1900))
            meta.missing.getOrNull(meta.sheet)?.takeIf { it > 0 }?.let { count ->
                Text(stringResource(R.string.office_cache_missing, count), color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("batch-document-missing-$index"))
            }
        }
        Text(stringResource(R.string.office_notice), modifier = Modifier.testTag("batch-document-loss-$index"))
        val enabled = !state.busy && meta.sheet >= 0 && supported
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(approved, enabled = enabled, role = Role.Checkbox,
            onValueChange = { model.approveDocument(key, it) }).testTag("batch-document-consent-$index")) {
            Checkbox(approved, null, enabled = enabled)
            Text(stringResource(R.string.office_consent), Modifier.weight(1f))
        }
    }
}
