package tv.own.owntv.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.R
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.res.pluralStringResource
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.core.settings.PanelSection
import tv.own.owntv.core.settings.PanelShares
import tv.own.owntv.core.settings.PanelWidthLimits
import tv.own.owntv.core.settings.CINEMATIC_DETAILS_DEFAULT
import tv.own.owntv.core.settings.CINEMATIC_DETAILS_MAX
import tv.own.owntv.features.settings.data.BrowseColumnGap
import tv.own.owntv.features.settings.data.BrowseColumnDividerSpace
import tv.own.owntv.features.settings.data.BrowseContainerPadding
import tv.own.owntv.features.settings.data.defaultPanelShares
import tv.own.owntv.ui.components.ContentPanelFill
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.PreviewPanelFill
import tv.own.owntv.ui.components.restoreAfterDialogClose

/**
 * Panel Width Adjustment — lets the user re-balance the three browse panels (category rail · item
 * list/grid · preview/poster) independently for Live TV, Movies and Series.
 *
 * Each panel holds its share of the screen in percent, and the three must add up to exactly 100%.
 * The dialog shows a running total and refuses to save while it doesn't read 100, so the numbers
 * always mean what they look like they mean.
 *
 * This screen measures itself before its own padding, so `maxWidth` here is exactly the width the
 * browse row gets — that's what the stock (seed) percentages are derived from.
 */
@Composable
fun PanelWidthSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val vm: SettingsViewModel = koinViewModel()

    var open by remember { mutableStateOf<PanelSection?>(null) }
    val rowFocus = remember { PanelSection.entries.associateWith { FocusRequester() } }
    val scrollState = rememberScrollState()
    var dialogReturn by remember { mutableStateOf<FocusRequester?>(null) }

    LaunchedEffect(Unit) { runCatching { rowFocus.getValue(PanelSection.LIVE).requestFocus() } }
    // Snapshot the offset while the dialog is up and hold it across the restore, so the row is already
    // in view when focus lands — see [restoreAfterDialogClose].
    var savedScroll by remember { mutableIntStateOf(0) }
    LaunchedEffect(open) {
        if (open != null) { savedScroll = scrollState.value; return@LaunchedEffect }
        if (dialogReturn != null) restoreAfterDialogClose(dialogReturn, scrollState, savedScroll)
        dialogReturn = null
    }
    // One row per screen, named with the layout it uses now; the panel draws that layout at its widths.
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val rowWidth = maxWidth - BrowseContainerPadding * 2
        val vodLayout by vm.vodLayout.collectAsStateWithLifecycle()
        val cinematic = vodLayout != tv.own.owntv.core.settings.SettingsRepository.VodLayout.SEPARATE
        val liveLayout by vm.liveLayout.collectAsStateWithLifecycle()
        val liveStage = liveLayout == tv.own.owntv.core.settings.SettingsRepository.LiveLayout.STAGE
        val liveStageSaved by vm.liveStageWidths.collectAsStateWithLifecycle()
        val detailsHeights = PanelSection.entries.associateWith {
            vm.cinematicDetailsHeight(it).collectAsStateWithLifecycle().value
        }
        val sheetWidths = PanelSection.entries.associateWith {
            vm.cinematicSheetWidth(it).collectAsStateWithLifecycle().value
        }
        val rules = stringResource(R.string.settings_panel_width_help, *NO_ARGS)
        val sep = stringResource(R.string.content_epg_bits_separator)
        StageFullPage(
            parents = listOf(stringResource(R.string.settings_group_layout)),
            title = stringResource(R.string.settings_panel_width),
            count = pluralStringResource(R.plurals.settings_setting_count, PanelSection.entries.size, PanelSection.entries.size),
            onBack = onBack,
            modifier = Modifier,
            scroll = scrollState,
        ) {
            PanelSection.entries.forEach { section ->
                val enabled by vm.panelWidthEnabled.getValue(section).collectAsStateWithLifecycle()
                val shares by vm.panelShares.getValue(section).collectAsStateWithLifecycle()
                val current = shares ?: defaultPanelShares(section, rowWidth)
                val stageWidths = (liveStageSaved ?: tv.own.owntv.core.settings.LiveStageWidths.DEFAULT)
                    .takeIf { section == PanelSection.LIVE && liveStage }
                val vodCinematic = cinematic && section != PanelSection.LIVE
                val layoutName = stringResource(
                    when {
                        section == PanelSection.LIVE && liveStage -> R.string.settings_live_layout_stage
                        vodCinematic -> R.string.settings_vod_layout_cinematic
                        else -> R.string.settings_vod_layout_separate
                    },
                )
                val title = sectionTitle(section) + sep + layoutName
                val value = SettingValue.Opens(stringResource(if (enabled) R.string.settings_live_latency_custom else R.string.settings_subtitle_default))
                StageSettingRow(
                    icon = when (section) {
                        PanelSection.LIVE -> OwnTVIcon.LIVE_TV
                        PanelSection.MOVIES -> OwnTVIcon.MOVIES
                        PanelSection.SERIES -> OwnTVIcon.SERIES
                    },
                    title = title,
                    desc = if (stageWidths != null) {
                        stringResource(R.string.settings_panel_width_summary_stage, stageWidths.sheet, stageWidths.list, stageWidths.preview)
                    } else if (vodCinematic) {
                        // Stage Cinematic: the categories are a sheet over the full-width titles.
                        stringResource(R.string.settings_panel_width_summary_cinematic, sheetWidths.getValue(section), detailsHeights.getValue(section))
                    } else stringResource(
                        R.string.settings_panel_width_summary,
                        current.category,
                        current.list,
                        previewLabel(section),
                        current.preview,
                    ),
                    value = value,
                    onClick = { dialogReturn = rowFocus.getValue(section); open = section },
                    help = SettingHelp(
                        title, rules,
                        hints = settingHints(value, pinnable = false),
                        extra = {
                            androidx.compose.foundation.layout.Box(Modifier.padding(top = 18.mpx)) {
                                if (stageWidths != null) StageWidthDiagram(stageWidths)
                                else PanelWidthDiagram(current, vodCinematic, detailsHeights.getValue(section))
                            }
                        },
                    ),
                    modifier = Modifier.focusRequester(rowFocus.getValue(section)),
                )
            }
        }

        open?.let { section ->
            PanelWidthDialog(section = section, rowWidth = rowWidth, vm = vm, onDismiss = { open = null })
        }
    }
}

@Composable
private fun sectionTitle(section: PanelSection): String = when (section) {
    PanelSection.LIVE -> stringResource(R.string.settings_live_tv)
    PanelSection.MOVIES -> stringResource(R.string.settings_movies)
    PanelSection.SERIES -> stringResource(R.string.settings_series)
}

@Composable
private fun previewLabel(section: PanelSection): String =
    stringResource(if (section == PanelSection.LIVE) R.string.settings_panel_width_preview else R.string.settings_panel_width_poster)

/**
 * The second and third slider labels, which change with the layout.
 *
 * In Cinematic there is no preview panel to size, so the third slider sets the height of the detail
 * block instead and the second one is sizing the whole content column, not a list next to a preview.
 * Live TV is never Cinematic, so its labels never move.
 */
@Composable
private fun listLabel(section: PanelSection, cinematic: Boolean): String =
    if (cinematic && section != PanelSection.LIVE) stringResource(R.string.settings_panel_width_content_area)
    else stringResource(R.string.settings_panel_width_list)

@Composable
private fun thirdSliderLabel(section: PanelSection, cinematic: Boolean): String =
    if (cinematic && section != PanelSection.LIVE) stringResource(R.string.settings_panel_width_details_height)
    else stringResource(R.string.settings_panel_width_preview_panel, previewLabel(section))

/**
 * The per-section popup: master toggle, then one −/+ stepper per panel, a running total, and
 * Reset / Okay.
 *
 * Edits are held as a draft and only written on Okay — and Okay refuses while the total isn't 100%,
 * showing the reason in red. That way nothing half-adjusted can ever reach the browse screens, and
 * backing out discards cleanly.
 */
@Composable
private fun PanelWidthDialog(
    section: PanelSection,
    rowWidth: Dp,
    vm: SettingsViewModel,
    onDismiss: () -> Unit,
) {
    val savedEnabled by vm.panelWidthEnabled.getValue(section).collectAsStateWithLifecycle()
    val savedShares by vm.panelShares.getValue(section).collectAsStateWithLifecycle()
    val livePreviewEnabled by vm.livePreviewEnabled.collectAsStateWithLifecycle()
    val vodLayout by vm.vodLayout.collectAsStateWithLifecycle()
    val cinematic = vodLayout != tv.own.owntv.core.settings.SettingsRepository.VodLayout.SEPARATE
    val stock = remember(section, rowWidth) { defaultPanelShares(section, rowWidth) }
    // Cinematic's detail block is a HEIGHT, so it is its own stored value and takes no part in the
    // 100% row budget below. Live TV is never Cinematic and never shows this row.
    val showDetailsHeight = cinematic && section != PanelSection.LIVE
    val savedDetailsHeight by vm.cinematicDetailsHeight(section).collectAsStateWithLifecycle()

    var enabled by remember { mutableStateOf(savedEnabled) }
    var draft by remember { mutableStateOf(savedShares ?: stock) }
    // Live TV in the Stage layout sizes a different thing: the sheet over the list, on its own scale,
    // and a row of list + preview that totals 100. Seeded once, like `draft`.
    val liveLayout by vm.liveLayout.collectAsStateWithLifecycle()
    val stage = section == PanelSection.LIVE && liveLayout == tv.own.owntv.core.settings.SettingsRepository.LiveLayout.STAGE
    val savedStage by vm.liveStageWidths.collectAsStateWithLifecycle()
    var stageDraft by remember { mutableStateOf(savedStage ?: tv.own.owntv.core.settings.LiveStageWidths.DEFAULT) }
    // Seeded ONCE, exactly like `draft` above — never resynced from the flow while the dialog is up.
    // The saved value is a `stateIn(WhileSubscribed)` StateFlow, so it starts at the default and the
    // stored number lands a frame later; a resync would let that late emission overwrite whatever the
    // user had already stepped to, and the edit would save as the default instead.
    var detailsHeight by remember(section) { mutableStateOf(savedDetailsHeight) }
    // Cinematic's sheet: its own value, seeded once like the height; the Separate shares stay as they are.
    val savedSheet by vm.cinematicSheetWidth(section).collectAsStateWithLifecycle()
    var sheetWidth by remember(section) { mutableStateOf(savedSheet) }
    // The red note only appears once the user has actually tried to save an unbalanced total.
    var showError by remember { mutableStateOf(false) }
    var showPreviewDisableConfirmation by remember { mutableStateOf(false) }
    val valid = if (stage) stageDraft.isValid else draft.isValid
    val shownTotal = if (stage) stageDraft.list + stageDraft.preview else draft.total

    val toggleFocus = remember { FocusRequester() }
    LaunchedEffect(showPreviewDisableConfirmation) {
        kotlinx.coroutines.delay(80)
        if (!showPreviewDisableConfirmation) runCatching { toggleFocus.requestFocus() }
    }
    LaunchedEffect(valid) { if (valid) showError = false }
    val save = {
        // An unbalanced total is only a problem for a section that's actually on.
        if (enabled && !valid) {
            showError = true
        } else if (
            section == PanelSection.LIVE && enabled && livePreviewEnabled &&
            (if (stage) stageDraft.preview == 0 else draft.preview == 0)
        ) {
            showPreviewDisableConfirmation = true
        } else if (stage) {
            vm.setLiveStageWidths(enabled, stageDraft)
            onDismiss()
        } else {
            // Cinematic edits only its own two values; the Separate widths stay as saved.
            vm.setPanelWidths(section, enabled, if (showDetailsHeight) savedShares ?: stock else draft)
            if (showDetailsHeight) {
                vm.setCinematicDetailsHeight(section, detailsHeight)
                vm.setCinematicSheetWidth(section, sheetWidth)
            }
            onDismiss()
        }
    }
    val hint = tv.own.owntv.ui.theme.stageText(15, 500)
    val muted = tv.own.owntv.ui.theme.StageColors.Muted
    tv.own.owntv.ui.stage.StagePopup(
        onDismiss = onDismiss,
        title = stringResource(R.string.settings_panel_width_dialog_title, sectionTitle(section)),
        width = 820.mpx,
        buttons = {
            tv.own.owntv.ui.stage.StageButton(stringResource(R.string.common_reset), onClick = {
                draft = stock
                stageDraft = tv.own.owntv.core.settings.LiveStageWidths.DEFAULT
                detailsHeight = CINEMATIC_DETAILS_DEFAULT
                sheetWidth = tv.own.owntv.core.settings.LiveStageWidths.DEFAULT.sheet
                showError = false
            }, height = 56.mpx, textSize = 19)
            tv.own.owntv.ui.stage.StageButton(stringResource(R.string.common_ok), onClick = save, height = 56.mpx, textSize = 19, tinted = true)
        },
    ) {
        tv.own.owntv.ui.stage.StagePopupOption(
            title = stringResource(R.string.settings_panel_width_customize), onClick = { enabled = !enabled },
            modifier = Modifier.focusRequester(toggleFocus),
            trailing = { tv.own.owntv.ui.stage.StageSwitch(enabled) },
        )
        if (enabled) {
            Spacer(Modifier.height(10.mpx))
            if (stage) {
                // List and preview are one row that must total 100, so they move together.
                StepRow(listLabel(section, false), stageDraft.list, maximum = PanelWidthLimits.TOTAL) {
                    stageDraft = stageDraft.copy(list = it, preview = PanelWidthLimits.TOTAL - it)
                }
                StepRow(thirdSliderLabel(section, false), stageDraft.preview, minimum = 0, maximum = PanelWidthLimits.TOTAL - PanelWidthLimits.MIN) {
                    stageDraft = stageDraft.copy(preview = it, list = PanelWidthLimits.TOTAL - it)
                }
            } else if (showDetailsHeight) {
                // Stage Cinematic: the titles fill the row and the categories open as a sheet over them, so
                // the one width is the sheet's, on its own scale (the Separate widths are not touched).
                StepRow(
                    stringResource(R.string.settings_panel_width_live_sheet), sheetWidth,
                    minimum = tv.own.owntv.core.settings.LiveStageWidths.LIVE_SHEET_MIN,
                    maximum = tv.own.owntv.core.settings.LiveStageWidths.LIVE_SHEET_MAX,
                ) { sheetWidth = it }
                Text(stringResource(R.string.settings_panel_width_cinematic_sheet_hint), style = hint, color = muted, modifier = Modifier.padding(horizontal = 22.mpx, vertical = 6.mpx))
            } else {
                StepRow(stringResource(R.string.settings_panel_width_category), draft.category) { draft = draft.copy(category = it) }
                StepRow(listLabel(section, cinematic), draft.list, maximum = PanelWidthLimits.listMax(draft.preview)) { draft = draft.copy(list = it) }
                StepRow(thirdSliderLabel(section, cinematic), draft.preview, minimum = 0) {
                    // Bringing the third panel back lowers the list's ceiling to MAX again.
                    draft = draft.copy(preview = it, list = draft.list.coerceAtMost(PanelWidthLimits.listMax(it)))
                }
            }

            Box(Modifier.padding(horizontal = 8.mpx, vertical = 14.mpx)) {
                if (stage) StageWidthDiagram(stageDraft)
                else PanelWidthDiagram(
                    if (showDetailsHeight) PanelShares(sheetWidth, PanelWidthLimits.TOTAL - sheetWidth, 0) else draft,
                    cinematic = showDetailsHeight,
                    detailsHeight = detailsHeight,
                )
            }

            // Nothing in Cinematic adds up to 100%: the sheet and the height are each on their own scale.
            if (!showDetailsHeight) Row(Modifier.fillMaxWidth().padding(horizontal = 22.mpx), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_panel_width_total), style = tv.own.owntv.ui.theme.stageText(19, 700), color = tv.own.owntv.ui.theme.StageColors.Text, modifier = Modifier.weight(1f))
                Text(
                    stringResource(R.string.common_percent, shownTotal), style = tv.own.owntv.ui.theme.stageText(20, 800),
                    color = if (valid) tv.own.owntv.ui.theme.stageAccent.accent else tv.own.owntv.ui.theme.StageColors.Danger,
                )
            }
            if (stage) {
                // Below the total: the sheet slides over the row and takes no part in its 100%.
                tv.own.owntv.ui.stage.StagePopupDivider()
                StepRow(
                    stringResource(R.string.settings_panel_width_live_sheet), stageDraft.sheet,
                    minimum = tv.own.owntv.core.settings.LiveStageWidths.LIVE_SHEET_MIN,
                    maximum = tv.own.owntv.core.settings.LiveStageWidths.LIVE_SHEET_MAX,
                ) { stageDraft = stageDraft.copy(sheet = it) }
                Text(stringResource(R.string.settings_panel_width_live_sheet_hint, *NO_ARGS), style = hint, color = muted, modifier = Modifier.padding(horizontal = 22.mpx, vertical = 6.mpx))
            }
            if (showDetailsHeight) {
                // Below the total on purpose: it takes no part in the row's 100%.
                tv.own.owntv.ui.stage.StagePopupDivider()
                StepRow(stringResource(R.string.settings_panel_width_details_height), detailsHeight, minimum = 0, maximum = CINEMATIC_DETAILS_MAX) { detailsHeight = it }
                Text(stringResource(R.string.settings_panel_width_details_hint), style = hint, color = muted, modifier = Modifier.padding(horizontal = 22.mpx, vertical = 6.mpx))
            }
            if (showError) {
                Text(
                    stringResource(R.string.settings_panel_width_invalid_total, shownTotal),
                    style = hint, color = tv.own.owntv.ui.theme.StageColors.Danger,
                    modifier = Modifier.padding(top = 12.mpx).fillMaxWidth()
                        .background(tv.own.owntv.ui.theme.StageColors.Danger.copy(alpha = 0.14f), RoundedCornerShape(14.mpx))
                        .padding(horizontal = 18.mpx, vertical = 10.mpx),
                )
            }
        }
    }
    if (showPreviewDisableConfirmation) {
        tv.own.owntv.ui.stage.StageConfirm(
            title = stringResource(R.string.settings_panel_width_disable_preview_title),
            body = stringResource(R.string.settings_panel_width_disable_preview_description),
            confirm = stringResource(R.string.common_ok),
            onConfirm = {
                // This branch is Live TV only, which is never Cinematic.
                if (stage) vm.setLiveStageWidths(enabled, stageDraft) else vm.setPanelWidths(section, enabled, draft)
                onDismiss()
            },
            onCancel = { showPreviewDisableConfirmation = false },
            focusCancel = true,
        )
    }
}

/** Live TV, Stage layout: the list and the preview across the row, the categories sheet drawn over its left edge. */
@Composable
private fun StageWidthDiagram(w: tv.own.owntv.core.settings.LiveStageWidths) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(tv.own.owntv.ui.theme.StageColors.Ink.copy(alpha = 0.04f))
            .padding(4.dp),
    ) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.weight(w.list.toFloat()).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(tv.own.owntv.ui.theme.StageColors.Ink.copy(alpha = 0.08f)))
            if (w.preview > 0) {
                Box(Modifier.weight(w.preview.toFloat()).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(tv.own.owntv.ui.theme.StageColors.Ink.copy(alpha = 0.12f)))
            }
        }
        Box(
            Modifier
                .fillMaxWidth(w.sheet / 100f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(6.dp))
                .background(tv.own.owntv.ui.theme.stageAccent.accent.copy(alpha = 0.55f)),
        )
    }
}

/** The browse layout users are sizing: one container, two plain columns, and a raised preview. */
@Composable
private fun PanelWidthDiagram(shares: PanelShares, cinematic: Boolean = false, detailsHeight: Int = 0) {
    if (cinematic) {
        // The titles across the whole row with the details band on top, and the categories sheet
        // drawn over its left edge — the Stage Cinematic layout, as Live TV's Stage diagram.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ContentPanelFill)
                .padding(4.dp),
        ) {
            Column(Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))) {
                val detailsWeight = detailsHeight.toFloat().coerceIn(0f, CINEMATIC_DETAILS_MAX.toFloat())
                Box(Modifier.weight(detailsWeight.coerceAtLeast(0.01f)).fillMaxWidth().background(tv.own.owntv.ui.theme.stageAccent.accent.copy(alpha = 0.32f)))
                Box(Modifier.weight((100f - detailsWeight).coerceAtLeast(1f)).fillMaxWidth().background(tv.own.owntv.ui.theme.StageColors.Text.copy(alpha = 0.10f)))
            }
            Box(
                Modifier
                    .fillMaxWidth(shares.category / 100f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .background(tv.own.owntv.ui.theme.stageAccent.accent.copy(alpha = 0.55f)),
            )
        }
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
                .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
                .background(ContentPanelFill)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .weight(shares.category.toFloat())
                .fillMaxHeight()
                .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                .background(tv.own.owntv.ui.theme.StageColors.Text.copy(alpha = 0.035f)),
        )
        Spacer(Modifier.width(BrowseColumnGap))
        Box(
            Modifier
                .width(BrowseColumnDividerSpace)
                .fillMaxHeight()
                .padding(vertical = 2.dp)
                .background(tv.own.owntv.ui.theme.StageColors.Ink.copy(alpha = 0.10f)),
        )
        Spacer(Modifier.width(BrowseColumnGap))
        Box(
            Modifier
                .weight(shares.list.toFloat())
                .fillMaxHeight(),
        )
        if (shares.preview != 0) {
            Spacer(Modifier.width(BrowseColumnGap))
            Box(
                Modifier
                    .weight(shares.preview.toFloat())
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(PreviewPanelFill),
            )
        }
    }
}

/** One panel's row: label, then − value + in [PanelWidthLimits.STEP] increments. */
@Composable
internal fun StepRow(
    label: String,
    value: Int,
    minimum: Int = PanelWidthLimits.MIN,
    maximum: Int = PanelWidthLimits.MAX,
    step: Int = PanelWidthLimits.STEP,
    onSet: (Int) -> Unit,
) {
    // One focus stop per value; ◀ ▶ step it, clamped at the ends (nothing to disable, so focus never drops).
    tv.own.owntv.ui.stage.StageSurface(
        onClick = {},
        radius = 16.mpx,
        focusStyle = tv.own.owntv.ui.stage.StageFocus.FX,
        modifier = Modifier.fillMaxWidth().height(64.mpx).onPreviewKeyEvent { e ->
            val d = when (e.key) { Key.DirectionLeft -> -1; Key.DirectionRight -> 1; else -> 0 }
            if (d != 0 && e.type == KeyEventType.KeyDown) onSet((value + d * step).coerceIn(minimum, maximum))
            d != 0
        },
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.mpx), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = tv.own.owntv.ui.theme.stageText(19, 600), color = tv.own.owntv.ui.theme.StageColors.Text, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            OwnTVIcon(OwnTVIcon.CHEVRON, if (value > minimum) tv.own.owntv.ui.theme.StageColors.Muted else tv.own.owntv.ui.theme.StageColors.Dim, Modifier.size(20.mpx).graphicsLayer { rotationZ = 180f })
            Text(
                stringResource(R.string.common_percent, value), style = tv.own.owntv.ui.theme.stageText(20, 800),
                color = tv.own.owntv.ui.theme.stageAccent.accent, textAlign = TextAlign.Center, modifier = Modifier.width(84.mpx),
            )
            OwnTVIcon(OwnTVIcon.CHEVRON, if (value < maximum) tv.own.owntv.ui.theme.StageColors.Muted else tv.own.owntv.ui.theme.StageColors.Dim, Modifier.size(20.mpx))
        }
    }
}

