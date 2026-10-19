package app.lekto.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.lekto.text.LocalAppTexts
import app.lekto.text.TextKey

/** The test tag on a confirmation dialog's destructive confirm button. */
internal const val CONFIRM_DIALOG_CONFIRM_TAG: String = "confirm-dialog-confirm"

/** The test tag on a confirmation dialog's cancel button. */
internal const val CONFIRM_DIALOG_CANCEL_TAG: String = "confirm-dialog-cancel"

/**
 * The confirmation dialog `docs/design.md` prescribes for irreversible actions —
 * book deletion, vault migration (§2.4, §8): a title, a message in plain
 * language, and a cancel / destructive-confirm pair.
 *
 * It carries no state of its own, so the caller decides what each choice does and
 * the dialog cannot act before the user confirms. The confirm button takes the
 * error colour, the "destructive" tone the spec fixes for an action that cannot
 * be undone.
 */
@Suppress("LongParameterList") // A dialog's labels and its two choices; a bundle would only hide them.
@Composable
fun ConfirmationDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag(CONFIRM_DIALOG_CONFIRM_TAG),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag(CONFIRM_DIALOG_CANCEL_TAG)) {
                Text(LocalAppTexts.current[TextKey.CANCEL])
            }
        },
        modifier = modifier,
    )
}
