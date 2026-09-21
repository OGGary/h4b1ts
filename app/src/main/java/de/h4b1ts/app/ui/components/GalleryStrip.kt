package de.h4b1ts.app.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.h4b1ts.app.productivity.Attachment
import de.h4b1ts.app.productivity.AttachmentOwner
import de.h4b1ts.app.productivity.AttachmentStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.graphics.Bitmap

/**
 * The gallery, wherever it is needed.
 *
 * The same strip hangs off a habit, a note, a task or a day — that is the whole
 * point of keeping attachments in one store keyed by owner instead of building
 * three separate picture features.
 *
 * Uses the system photo picker, which needs no storage permission at all: the
 * user chooses one image and the app only ever sees that one.
 */
@Composable
fun GalleryStrip(
    owner: AttachmentOwner,
    ownerId: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Attachments live in a snapshot list, but the filtered view needs a nudge
    // after a picked image finishes copying.
    var version by remember { mutableIntStateOf(0) }
    var viewing by remember { mutableStateOf<Attachment?>(null) }
    val items = remember(ownerId, version, AttachmentStore.attachments.size) {
        AttachmentStore.forOwner(owner, ownerId)
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            AttachmentStore.attach(context, uri, owner, ownerId)
            version++
        }
    }

    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(12.dp),
                    )
                    .clickable {
                        picker.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 24.sp,
                )
            }
        }

        items(items, key = { it.id }) { attachment ->
            Thumbnail(attachment = attachment, onClick = { viewing = attachment })
        }
    }

    // A tap used to delete the picture outright, with no warning and no way to
    // look at it first. It opens it instead; deleting is a deliberate second act.
    viewing?.let { attachment ->
        ImageViewerDialog(
            attachment = attachment,
            onDismiss = { viewing = null },
            onDelete = {
                AttachmentStore.remove(attachment.id)
                version++
                viewing = null
            },
        )
    }
}

/**
 * One picture, big enough to actually look at, with deleting available while you
 * look. Shared by the attachment strips and the Photos grid so there is one
 * answer to "show me this picture" rather than two.
 */
@Composable
fun ImageViewerDialog(
    attachment: Attachment,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    caption: String? = null,
) {
    // Larger than a thumbnail but still downsampled: a full-resolution photo in a
    // dialog is a fast way to an OutOfMemoryError on a big picture.
    val bitmap = rememberDecoded(attachment, maxPx = 1440)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onBackground,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text(caption ?: "Picture") },
        text = {
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = caption,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
            } ?: Text("This picture could not be opened.")
        },
        confirmButton = {
            TextButton(onClick = onDelete) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}

/**
 * Decodes an attachment at thumbnail size. Shared so the gallery overview does
 * not repeat the downsampling logic.
 */
@Composable
fun rememberThumbnail(attachment: Attachment): Bitmap? = rememberDecoded(attachment, maxPx = 200)

/**
 * Decoding off the composition thread.
 *
 * Reading a phone photo, downsampling it and applying its EXIF rotation is tens
 * to hundreds of milliseconds of work, and done inside `remember` every one of
 * those milliseconds is a frame nobody draws. The picture arrives as null and is
 * swapped in when it is ready, which is what the placeholder behind it is for.
 */
@Composable
private fun rememberDecoded(attachment: Attachment, maxPx: Int): Bitmap? =
    produceState<Bitmap?>(initialValue = null, attachment.id, maxPx) {
        value = withContext(Dispatchers.IO) { AttachmentStore.decode(attachment, maxPx) }
    }.value

@Composable
private fun Thumbnail(attachment: Attachment, onClick: () -> Unit) {
    val bitmap = rememberThumbnail(attachment)

    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(64.dp),
            )
        }
    }
}
