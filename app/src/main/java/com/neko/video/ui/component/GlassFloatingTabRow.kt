package com.neko.video.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastRoundToInt
import com.neko.video.ui.component.liquid.lens
import com.neko.video.ui.component.liquid.vibrancy
import com.neko.video.ui.theme.LocalEnableFloatingBottomBarBlur
import com.neko.video.ui.theme.isInDarkTheme
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Floating liquid-glass segment control, styled after [FloatingBottomBar],
 * with a sliding selected indicator (KernelSU TabRow-style).
 */
@Composable
fun GlassFloatingTabRow(
    tabs: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    if (tabs.isEmpty()) return

    val preferGlass = LocalEnableFloatingBottomBarBlur.current && isRenderEffectSupported()
    val isInDark = isInDarkTheme()
    val shape = remember { RoundedCornerShape(22.dp) }
    val indicatorShape = remember { RoundedCornerShape(18.dp) }
    val surfaceContainer = MiuixTheme.colorScheme.surfaceContainer
    val glassTint = if (preferGlass) surfaceContainer.copy(0.35f) else surfaceContainer.copy(0.88f)
    val selectedTint = MiuixTheme.colorScheme.primary.copy(alpha = if (preferGlass) 0.22f else 0.16f)
    val selectedContent = MiuixTheme.colorScheme.primary
    val normalContent = MiuixTheme.colorScheme.onSurface
    val density = LocalDensity.current
    val indicatorOffset = remember { Animatable(0f) }
    var indicatorReady by remember { mutableStateOf(false) }
    val safeSelectedIndex = selectedTabIndex.coerceIn(0, tabs.lastIndex)

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .dropShadow(
                    shape = shape,
                    shadow = Shadow(
                        radius = 12.dp,
                        color = Color.Black,
                        alpha = if (isInDark) 0.22f else 0.10f,
                    ),
                )
                .then(
                    if (preferGlass) {
                        Modifier.drawBackdrop(
                            backdrop = backdrop,
                            shape = { shape },
                            effects = {
                                vibrancy()
                                blur(4.dp.toPx(), 4.dp.toPx())
                                lens(
                                    refractionHeight = 18.dp.toPx(),
                                    refractionAmount = 18.dp.toPx(),
                                )
                            },
                            onDrawSurface = { drawRect(glassTint) },
                        )
                    } else {
                        Modifier.background(glassTint, shape)
                    },
                )
                .clip(shape)
                .padding(4.dp)
                .height(48.dp)
                .fillMaxWidth(),
        ) {
            val tabWidth = maxWidth / tabs.size.coerceAtLeast(1)
            val tabWidthPx = with(density) { tabWidth.toPx() }

            LaunchedEffect(safeSelectedIndex, tabWidthPx) {
                val target = safeSelectedIndex * tabWidthPx
                if (!indicatorReady) {
                    indicatorOffset.snapTo(target)
                    indicatorReady = true
                } else {
                    indicatorOffset.animateTo(
                        target,
                        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .offset { IntOffset(indicatorOffset.value.fastRoundToInt(), 0) }
                        .width(tabWidth)
                        .fillMaxHeight()
                        .clip(indicatorShape)
                        .background(selectedTint),
                )
                Row(modifier = Modifier.fillMaxSize()) {
                    tabs.forEachIndexed { index, label ->
                        val selected = index == safeSelectedIndex
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { onTabSelected(index) },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = label,
                                color = if (selected) selectedContent else normalContent,
                                fontSize = 13.sp,
                                lineHeight = 16.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}
