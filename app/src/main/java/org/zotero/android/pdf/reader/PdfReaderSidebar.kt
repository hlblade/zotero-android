package org.zotero.android.pdf.reader

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.zotero.android.R
import org.zotero.android.androidx.content.pxToDp
import org.zotero.android.architecture.ui.CustomLayoutSize
import org.zotero.android.database.objects.AnnotationType
import org.zotero.android.pdf.data.Annotation
import org.zotero.android.uicomponents.Drawables
import org.zotero.android.uicomponents.Strings
import org.zotero.android.uicomponents.foundation.safeClickable
import org.zotero.android.uicomponents.textinput.CustomTextField
import org.zotero.android.uicomponents.theme.CustomPalette
import org.zotero.android.uicomponents.theme.CustomTheme

@Composable
internal fun PdfReaderSidebar(
    viewState: PdfReaderViewState,
    layoutType: CustomLayoutSize.LayoutType,
    viewModel: PdfReaderViewModel,
    focusRequester: FocusRequester,
    lazyListState: LazyListState
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CustomTheme.colors.pdfAnnotationsFormBackground)
            .padding(horizontal = 16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = layoutType.calculateAllItemsBottomPanelHeight())


        ) {
            Spacer(modifier = Modifier.height(16.dp))
            PdfSidebarSearchBar(viewState = viewState, viewModel = viewModel)
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                state = lazyListState,
            ) {
                itemsIndexed(
                    items = viewModel.viewState.sortedKeys
                ) { _, key ->
                    val annotation = viewModel.annotation(key) ?: return@itemsIndexed
                    var rowModifier: Modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape = RoundedCornerShape(10.dp))
                        .background(CustomTheme.colors.pdfAnnotationsItemBackground)

                    val selected = annotation.key == viewModel.viewState.selectedAnnotationKey?.key
                    if (selected) {
                        rowModifier = rowModifier.border(
                            width = 3.dp,
                            color = CustomPalette.pdfAnnotationSidebarSelectedItem,
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = rowModifier
                            .safeClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { viewModel.selectAnnotation(key) },
                            )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                        ) {
                            val annotationColor =
                                Color(android.graphics.Color.parseColor(annotation.displayColor))
                            val loadPreview = {
                                val preview =
                                    viewModel.annotationPreviewMemoryCache.getBitmap(annotation.key)
                                if (preview == null) {
                                    viewModel.loadPreviews(listOf(annotation.key))
                                }
                                preview
                            }

                            HeaderRow(
                                annotation = annotation,
                                annotationColor = annotationColor,
                                layoutType = layoutType,
                                viewState = viewState,
                                viewModel = viewModel,
                            )
                            if (!annotation.tags.isEmpty() || !annotation.comment.isEmpty()) {
                                SidebarDivider(modifier = Modifier.fillMaxWidth())
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            when (annotation.type) {
                                AnnotationType.note -> NoteRow(
                                    annotation = annotation,
                                    layoutType = layoutType,
                                    viewModel = viewModel,
                                    viewState = viewState,
                                    focusRequester = focusRequester,
                                )

                                AnnotationType.highlight -> HighlightRow(
                                    annotation = annotation,
                                    layoutType = layoutType,
                                    annotationColor = annotationColor,
                                    viewModel = viewModel,
                                    viewState = viewState,
                                    focusRequester = focusRequester,
                                )

                                AnnotationType.ink -> InkRow(
                                    viewModel = viewModel,
                                    annotation = annotation,
                                    loadPreview = loadPreview,
                                    layoutType = layoutType,
                                )

                                AnnotationType.image -> ImageRow(
                                    viewModel = viewModel,
                                    viewState = viewState,
                                    annotation = annotation,
                                    loadPreview = loadPreview,
                                    layoutType = layoutType,
                                    focusRequester = focusRequester,
                                )
                            }
                        }
                    }
                }
            }
        }
        PdfReaderBottomPanel(
            layoutType = layoutType,
            viewModel = viewModel,
            viewState = viewState
        )
    }
}

@Composable
fun ImageRow(
    viewModel: PdfReaderViewModel,
    viewState: PdfReaderViewState,
    annotation: Annotation,
    loadPreview: () -> Bitmap?,
    layoutType: CustomLayoutSize.LayoutType,
    focusRequester: FocusRequester,
) {
    val cachedBitmap = loadPreview()
    if (cachedBitmap != null) {
        Image(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .fillMaxWidth()
                .heightIn(max = viewModel.annotationMaxSideSize.pxToDp()),
            bitmap = cachedBitmap.asImageBitmap(),
            contentDescription = null,
        )
    }
    TagsAndCommentsSection(
        annotation = annotation,
        viewModel = viewModel,
        viewState = viewState,
        layoutType = layoutType,
        focusRequester = focusRequester,
    )
}


@Composable
fun InkRow(
    viewModel: PdfReaderViewModel,
    annotation: Annotation,
    loadPreview: () -> Bitmap?,
    layoutType: CustomLayoutSize.LayoutType,
) {
    val cachedBitmap = loadPreview()
    if (cachedBitmap != null) {
        Image(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .fillMaxWidth()
                .heightIn(max = viewModel.annotationMaxSideSize.pxToDp()),
            bitmap = cachedBitmap.asImageBitmap(),
            contentDescription = null,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 0.dp, bottom = 8.dp)
    ) {
        if (!annotation.tags.isEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            SidebarDivider(
                modifier = Modifier
                    .height(2.dp)
                    .fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
        TagsSection(viewModel = viewModel, annotation = annotation, layoutType = layoutType)
    }

}


@Composable
fun NoteRow(
    annotation: Annotation,
    layoutType: CustomLayoutSize.LayoutType,
    viewModel: PdfReaderViewModel,
    viewState: PdfReaderViewState,
    focusRequester: FocusRequester,
) {
    TagsAndCommentsSection(
        annotation = annotation,
        viewModel = viewModel,
        viewState = viewState,
        layoutType = layoutType,
        focusRequester = focusRequester,
    )
}

@Composable
fun HighlightRow(
    annotation: Annotation,
    viewModel: PdfReaderViewModel,
    viewState: PdfReaderViewState,
    layoutType: CustomLayoutSize.LayoutType,
    annotationColor: Color,
    focusRequester: FocusRequester,
) {
    Box(
        modifier = Modifier
            .padding(bottom = 8.dp)
            .fillMaxWidth()
            .height(IntrinsicSize.Max)
    ) {
        Box(
            modifier = Modifier
                .padding(start = 8.dp)
                .width(3.dp)
                .fillMaxHeight()
                .background(annotationColor)
        )
        Text(
            modifier = Modifier.padding(start = 20.dp),
            text = annotation.text ?: "",
            color = CustomTheme.colors.primaryContent,
            style = CustomTheme.typography.default,
            fontSize = layoutType.calculatePdfSidebarTextSize(),
        )
    }

    TagsAndCommentsSection(
        annotation = annotation,
        viewModel = viewModel,
        viewState = viewState,
        layoutType = layoutType,
        focusRequester = focusRequester,
    )
}

@Composable
private fun TagsAndCommentsSection(
    annotation: Annotation,
    viewModel: PdfReaderViewModel,
    viewState: PdfReaderViewState,
    layoutType: CustomLayoutSize.LayoutType,
    focusRequester: FocusRequester,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 0.dp, bottom = 8.dp)
    ) {
        CustomTextField(
            modifier = Modifier
                .padding(start = 8.dp)
                .onFocusChanged {
                    if (it.hasFocus) {
                        viewModel.onCommentFocusFieldChange(annotation.key)
                    }
                },
            value = if (annotation.key == viewState.commentFocusKey) {
                viewState.commentFocusText
            } else {
                annotation.comment
            },
            textStyle = CustomTheme.typography.default.copy(fontSize = layoutType.calculatePdfSidebarTextSize()),
            hint = stringResource(id = Strings.pdf_annotations_sidebar_add_comment),
            focusRequester = focusRequester,
            ignoreTabsAndCaretReturns = false,
            onValueChange = { viewModel.onCommentTextChange(annotationKey = annotation.key, it) })
            Spacer(modifier = Modifier.height(4.dp))
            SidebarDivider(
                modifier = Modifier
                    .height(2.dp)
                    .fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
        TagsSection(viewModel = viewModel, annotation = annotation, layoutType = layoutType)
    }
}

@Composable
private fun TagsSection(
    viewModel: PdfReaderViewModel,
    annotation: Annotation,
    layoutType: CustomLayoutSize.LayoutType
) {
    if (!annotation.tags.isEmpty()) {
        Text(
            modifier = Modifier
                .padding(start = 8.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { viewModel.onTagsClicked(annotation) }
                ),
            text = annotation.tags.joinToString(separator = ", ") { it.name },
            color = CustomTheme.colors.primaryContent,
            style = CustomTheme.typography.default,
            fontSize = layoutType.calculatePdfSidebarTextSize(),
        )
    } else {
        Text(
            modifier = Modifier
                .padding(start = 8.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { viewModel.onTagsClicked(annotation) }
                ),
            text = stringResource(id = Strings.pdf_annotations_sidebar_add_tags),
            color = CustomTheme.colors.zoteroDefaultBlue,
            style = CustomTheme.typography.default,
            fontSize = layoutType.calculatePdfSidebarTextSize(),
        )
    }
}

@Composable
private fun HeaderRow(
    viewModel: PdfReaderViewModel,
    viewState: PdfReaderViewState,
    annotation: Annotation,
    annotationColor: Color,
    layoutType: CustomLayoutSize.LayoutType
) {
    val title = stringResource(R.string.page) + " " + annotation.pageLabel
    val icon = when (annotation.type) {
        AnnotationType.note -> Drawables.note_large
        AnnotationType.highlight -> Drawables.highlighter_large
        AnnotationType.image -> Drawables.area_large
        AnnotationType.ink -> Drawables.ink_large
    }
    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(
            modifier = Modifier.size(layoutType.calculatePdfSidebarHeaderIconSize()),
            painter = painterResource(id = icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(annotationColor),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            color = CustomTheme.colors.primaryContent,
            style = CustomTheme.typography.defaultBold,
            fontSize = layoutType.calculatePdfSidebarTextSize(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (viewState.selectedAnnotationKey?.key == annotation.key) {
            Spacer(modifier = Modifier.weight(1f))
            Image(
                modifier = Modifier
                    .size(layoutType.calculatePdfSidebarHeaderIconSize())
                    .safeClickable(
                        onClick = { viewModel.onMoreOptionsForItemClicked() },
                        interactionSource = remember { MutableInteractionSource() },
                        indication = rememberRipple(bounded = false)
                    ),
                painter = painterResource(id = Drawables.more_horiz_24px),
                contentDescription = null,
                colorFilter = ColorFilter.tint(CustomTheme.colors.zoteroDefaultBlue),
            )
        }
    }
}

@Composable
fun SidebarDivider(modifier: Modifier = Modifier) {
    Divider(
        modifier = modifier,
        color = CustomTheme.colors.pdfAnnotationsDividerBackground,
        thickness = 1.dp
    )

}
