package de.h4b1ts.app.productivity

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.UUID

enum class AttachmentOwner { HABIT, NOTE, TASK, DAY }

/**
 * One picture, attached to something.
 *
 * [ownerId] is the habit, note or task id — or an ISO date when the owner is a
 * day, which is what lets the calendar show pictures without a separate model.
 */
data class Attachment(
    val id: String = UUID.randomUUID().toString(),
    val owner: AttachmentOwner,
    val ownerId: String,
    /** File name inside the app's private gallery directory. */
    val fileName: String,
    val addedAt: Long = System.currentTimeMillis(),
)

/**
 * The gallery.
 *
 * Picked images are copied into app-private storage rather than kept as content
 * URIs: a URI permission granted by the photo picker does not survive a reboot,
 * so a gallery built on them quietly turns into a wall of broken thumbnails. The
 * copy also keeps the promise that nothing leaves the device.
 */
object AttachmentStore : JsonStore<Attachment>("attachments.json", "attachments") {

    private const val DIR_NAME = "gallery"

    val attachments: SnapshotStateList<Attachment> = mutableStateListOf()

    private var dir: File? = null

    override fun items(): MutableList<Attachment> = attachments

    fun initGallery(context: Context) {
        init(context)
        dir = File(context.applicationContext.filesDir, DIR_NAME).apply { mkdirs() }
    }

    fun fileOf(attachment: Attachment): File? =
        dir?.let { File(it, attachment.fileName) }?.takeIf { it.exists() }

    fun forOwner(owner: AttachmentOwner, ownerId: String): List<Attachment> =
        attachments.filter { it.owner == owner && it.ownerId == ownerId }
            .sortedByDescending { it.addedAt }

    fun forDay(date: LocalDate): List<Attachment> = forOwner(AttachmentOwner.DAY, date.toString())

    fun all(): List<Attachment> = attachments.sortedByDescending { it.addedAt }

    /** Copies the picked image in and returns the new attachment, or null on failure. */
    fun attach(
        context: Context,
        source: Uri,
        owner: AttachmentOwner,
        ownerId: String,
    ): Attachment? {
        val target = dir ?: return null
        val name = "${UUID.randomUUID()}.jpg"
        val copied = runCatching {
            context.contentResolver.openInputStream(source)?.use { input ->
                File(target, name).outputStream().use { output -> input.copyTo(output) }
            } ?: return null
        }.isSuccess
        if (!copied) return null

        val attachment = Attachment(owner = owner, ownerId = ownerId, fileName = name)
        attachments.add(attachment)
        save()
        return attachment
    }

    /**
     * Decodes an attachment for display, downsampled so the longest edge lands
     * near [maxPx].
     *
     * Every caller goes through here because of the second half: a photo from a
     * phone camera is almost always stored in the sensor's own orientation with a
     * separate EXIF tag saying how to turn it. [BitmapFactory] ignores that tag,
     * which is why pictures taken in portrait came back on their side. The copy
     * in [attach] is byte-for-byte, so the tag survives and can be read here.
     */
    fun decode(attachment: Attachment, maxPx: Int): Bitmap? {
        val file = fileOf(attachment) ?: return null
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)

            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) {
                sample *= 2
            }

            val decoded = BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sample },
            ) ?: return@runCatching null

            decoded.orientedBy(ExifInterface(file.absolutePath))
        }.getOrNull()
    }

    /**
     * Applies an EXIF orientation. Covers the mirrored cases too: front-facing
     * cameras write those, and a selfie flipped left-to-right is just as wrong as
     * one lying on its side.
     */
    private fun Bitmap.orientedBy(exif: ExifInterface): Bitmap {
        val matrix = Matrix()
        when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return this
        }
        return runCatching {
            Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
        }.getOrDefault(this)
    }

    fun remove(attachmentId: String) {
        val attachment = attachments.firstOrNull { it.id == attachmentId } ?: return
        fileOf(attachment)?.delete()
        attachments.remove(attachment)
        save()
    }

    fun removeAllFor(owner: AttachmentOwner, ownerId: String) {
        forOwner(owner, ownerId).forEach { remove(it.id) }
    }

    override fun toJson(item: Attachment): JSONObject = JSONObject().apply {
        put("id", item.id)
        put("owner", item.owner.name)
        put("ownerId", item.ownerId)
        put("fileName", item.fileName)
        put("addedAt", item.addedAt)
    }

    override fun fromJson(json: JSONObject): Attachment? {
        val owner = runCatching { AttachmentOwner.valueOf(json.optString("owner")) }.getOrNull()
            ?: return null
        val fileName = json.optString("fileName").takeIf { it.isNotBlank() } ?: return null
        return Attachment(
            id = json.optString("id", UUID.randomUUID().toString()),
            owner = owner,
            ownerId = json.optString("ownerId"),
            fileName = fileName,
            addedAt = json.optLong("addedAt", System.currentTimeMillis()),
        )
    }
}
