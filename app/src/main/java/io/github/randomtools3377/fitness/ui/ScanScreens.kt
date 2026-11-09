package io.github.randomtools3377.fitness.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import io.github.randomtools3377.fitness.core.Units
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Where the country files can be downloaded, opened in the phone's browser. */
const val COUNTRY_FILES_PAGE = "https://github.com/random-tools-3377/fitness-app/releases/tag/countries"

/**
 * The barcode scanner (ZXing, which works offline). The camera permission is asked for the first
 * time it is used. [onResult] gets the barcode read, or null if the scan was cancelled.
 */
@Composable
fun rememberScanner(onResult: (String?) -> Unit): () -> Unit {
    val launcher: ManagedActivityResultLauncher<ScanOptions, ScanIntentResult> =
        rememberLauncherForActivityResult(ScanContract()) { r -> onResult(r.contents) }
    return {
        launcher.launch(
            ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.EAN_13, ScanOptions.EAN_8, ScanOptions.UPC_A, ScanOptions.UPC_E)
                .setPrompt("Point at the barcode")
                .setBeepEnabled(false)
                .setOrientationLocked(false),
        )
    }
}

/** A barcode found nowhere: offered to type in from the label, once. */
@Composable
fun BarcodeNotFoundDialog(barcode: String, onTypeIn: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Not found") },
        text = {
            Text(
                "Barcode $barcode isn't in your country files or your own foods. Type it in from the label once, " +
                    "and the app will know it next time.",
            )
        },
        confirmButton = { TextButton(onClick = onTypeIn, modifier = Modifier.testTag("type-in-barcode")) { Text("Type it in") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The country files added, for the Foods tab: add one, see how many products, remove one. */
@Composable
fun CountryFilesSection(vm: MainViewModel) {
    val countries by vm.countries.collectAsState()
    val importing by vm.countryImport.collectAsState()
    val context = LocalContext.current
    var removing by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.importCountry(uri) }

    Section("Country files, for barcodes") {
        Text(
            "To scan barcodes, add your country's file of packaged products. Download it on the phone from the app's " +
                "Releases page, under \"Country files\", then add it here. It stays on the phone; scanning needs no internet.",
            style = MaterialTheme.typography.bodySmall,
        )
        for (c in countries) {
            Row(Modifier.fillMaxWidth().testTag("country:${c.name}"), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(c.name)
                    Text("${Units.grouped(c.products)} products · added ${dayName(dayOfMillis(c.addedAt), vm.today())}", style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { removing = c.name }) { Icon(Icons.Filled.Delete, contentDescription = "Remove") }
            }
        }
        importing?.let { n ->
            Text("Adding… ${Units.grouped(n)} products so far", style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("country-progress"))
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { picker.launch(arrayOf("application/gzip", "application/x-gzip", "application/octet-stream", "text/tab-separated-values", "text/plain", "*/*")) },
                enabled = importing == null,
                modifier = Modifier.testTag("add-country"),
            ) { Text("Add a country file") }
            TextButton(onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(COUNTRY_FILES_PAGE))) }
                    .onFailure { vm.show("No browser found. The files are on the app's Releases page, under Country files.") }
            }) { Text("Open the download page") }
        }
        Text("Product data: Open Food Facts contributors, under the Open Database License (ODbL).", style = MaterialTheme.typography.bodySmall)
    }

    removing?.let { name ->
        ConfirmDialog(
            "Remove $name?", "Its products go from the phone. What you already logged stays in the diary.", "Remove",
            onConfirm = { vm.removeCountry(name); removing = null }, onDismiss = { removing = null },
        )
    }
}

fun dayOfMillis(ms: Long): LocalDate = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.currentSystemDefault()).date
