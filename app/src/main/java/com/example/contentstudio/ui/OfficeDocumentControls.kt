package com.example.contentstudio.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.contentstudio.R
import com.example.contentstudio.documents.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OfficeInputControls(state: TextDocumentState, model: TextDocumentViewModel, active: Boolean) {
    val office = state.content?.office ?: return
    office.workbook?.let { book ->
        Text(stringResource(R.string.office_sheet), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            book.sheets.forEachIndexed { index, sheet ->
                FilterChip(office.sheet == index, { model.selectOffice(sheet = index) }, enabled = active,
                    label = { Text(sheet.name) }, modifier = Modifier.testTag("office-sheet-$index"))
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OfficeCellMode.entries.forEach { mode ->
                FilterChip(office.mode == mode, { model.selectOffice(mode = mode) }, enabled = active,
                    label = { Text(stringResource(if (mode == OfficeCellMode.FORMULAS) R.string.office_formulas else R.string.office_values)) },
                    modifier = Modifier.testTag("office-mode-${mode.name}"))
            }
        }
        Text(stringResource(R.string.office_dates, if (book.date1904) 1904 else 1900), style = MaterialTheme.typography.bodySmall)
    }
    Text(stringResource(R.string.office_limits), style = MaterialTheme.typography.bodySmall)
}

@Composable
internal fun OfficeConversionNotice(state: TextDocumentState, model: TextDocumentViewModel, active: Boolean) {
    val office = state.content?.office ?: return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.office_notice), modifier = Modifier.testTag("office-loss-notice"))
        office.workbook?.sheets?.getOrNull(office.sheet)?.let { sheet ->
            Text(stringResource(R.string.office_sheet) + ": " + sheet.name, modifier = Modifier.testTag("office-selected-sheet"))
            if (office.missingResults.isNotEmpty()) {
                Text(stringResource(R.string.office_cache_missing, office.missingResults.size),
                    color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("office-missing-cache"))
                Text(office.missingResults.take(5).joinToString(", "), style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics(mergeDescendants = true) {}
            .toggleable(state.officeConsent, enabled = active, role = Role.Checkbox, onValueChange = model::setOfficeConsent)
            .testTag("office-consent")) {
            Checkbox(state.officeConsent, onCheckedChange = null, enabled = active)
            Text(stringResource(R.string.office_consent), Modifier.weight(1f).padding(top = 12.dp))
        }
    }
}
