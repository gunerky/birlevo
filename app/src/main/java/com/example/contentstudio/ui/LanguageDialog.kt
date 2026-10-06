package com.example.contentstudio.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.example.contentstudio.data.*
import com.example.contentstudio.ui.appLocales

import com.example.contentstudio.R
@Composable
internal fun LanguageDialog(onDismiss: () -> Unit) {
    val selected = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    fun choose(tag: String) {
        onDismiss()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 440.dp)) {
                item {
                    LanguageRow(stringResource(R.string.device_language), selected.isEmpty()) { choose("") }
                }
                items(appLocales, key = { it.tag }) { locale ->
                    LanguageRow(locale.nativeName, selected == locale.tag) { choose(locale.tag) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun LanguageRow(name: String, selected: Boolean, onSelect: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onSelect).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(name, modifier = Modifier.weight(1f))
    }
}
