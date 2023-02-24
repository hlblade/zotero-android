package org.zotero.android.uicomponents.bottomsheet

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import org.zotero.android.database.objects.Attachment
import org.zotero.android.screens.itemdetails.data.ItemDetailCreator
import org.zotero.android.sync.Note
import org.zotero.android.sync.Tag
import org.zotero.android.uicomponents.Drawables
import org.zotero.android.uicomponents.Strings
import org.zotero.android.uicomponents.theme.CustomPalette

sealed class LongPressOptionItem(
    @StringRes val titleId: Int,
    @DrawableRes val resIcon: Int,
    val textAndIconColor: Color? = null,
) {
    data class TrashNote(val note: Note): LongPressOptionItem(
        titleId = Strings.moveToTrash,
        textAndIconColor = CustomPalette.ErrorRed,
        resIcon = Drawables.ic_delete_20dp
    )

    data class DeleteTag(val tag: Tag): LongPressOptionItem(
        titleId = Strings.delete,
        textAndIconColor = CustomPalette.ErrorRed,
        resIcon = Drawables.ic_delete_20dp
    )
    data class DeleteCreator(val creator: ItemDetailCreator): LongPressOptionItem(
        titleId = Strings.delete,
        textAndIconColor = CustomPalette.ErrorRed,
        resIcon = Drawables.ic_delete_20dp
    )

    data class DeleteAttachmentFile(val attachment: Attachment): LongPressOptionItem(
        titleId = Strings.removeDownload,
        resIcon = Drawables.ic_delete_20dp
    )

    data class MoveToTrashAttachment(val attachment: Attachment): LongPressOptionItem(
        titleId = Strings.moveToTrash,
        textAndIconColor = CustomPalette.ErrorRed,
        resIcon = Drawables.ic_delete_20dp
    )

    data class MoveToStandaloneAttachment(val attachment: Attachment): LongPressOptionItem(
        titleId = Strings.moveToStandaloneAttachment,
        resIcon = Drawables.baseline_arrow_upward_24
    )
}