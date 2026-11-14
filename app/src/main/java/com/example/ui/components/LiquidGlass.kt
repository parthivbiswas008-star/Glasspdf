package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.JetBrainsTabularFamily
import com.example.ui.theme.LocalLiquidGlass

/**
 * Optical Liquid Glass Surface with 145-degree refractive tint, 1dp specular hairline border,
 * and top-edge inner luster highlight.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    elevation: Dp = 14.dp,
    tintOverrideTop: Color? = null,
    tintOverrideBottom: Color? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val glass = LocalLiquidGlass.current
    val topColor = tintOverrideTop ?: glass.glassSurfaceTop
    val bottomColor = tintOverrideBottom ?: glass.glassSurfaceBottom

    Box(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = if (glass.isDark) 0.45f else 0.18f),
                spotColor = Color.Black.copy(alpha = if (glass.isDark) 0.55f else 0.22f)
            )
            .clip(shape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(topColor, bottomColor),
                    start = Offset(0f, 0f),
                    end = Offset(900f, 900f)
                )
            )
            .drawWithContent {
                drawContent()
                // Top-edge inner specular luster line
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            glass.innerSpecular,
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = 6.dp.toPx()
                    )
                )
            }
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        glass.rimBorder,
                        glass.rimBorder.copy(alpha = 0.22f),
                        glass.rimBorder.copy(alpha = 0.50f)
                    )
                ),
                shape = shape
            ),
        content = content
    )
}

/**
 * Interactive Floating Liquid Glass Pill with elastic spring compression,
 * radial touch shimmer, and subtle haptic feedback.
 */
@Composable
fun GlassPill(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    isSelected: Boolean = false,
    shape: Shape = RoundedCornerShape(50),
    elevation: Dp = 10.dp,
    contentPaddingHorizontal: Dp = 14.dp,
    contentPaddingVertical: Dp = 9.dp,
    content: @Composable RowScope.() -> Unit
) {
    val glass = LocalLiquidGlass.current
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = 0.65f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "glassPillScale"
    )

    val topTint = when {
        isSelected -> glass.accent.copy(alpha = if (glass.isDark) 0.38f else 0.20f)
        else -> glass.glassPillTop
    }
    val bottomTint = when {
        isSelected -> glass.accent.copy(alpha = if (glass.isDark) 0.24f else 0.12f)
        else -> glass.glassPillBottom
    }

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
        )
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .scale(scale)
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.18f),
                spotColor = Color.Black.copy(alpha = 0.22f)
            )
            .clip(shape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(topTint, bottomTint)
                )
            )
            .border(
                width = 1.dp,
                color = if (isSelected) glass.accent.copy(alpha = 0.65f) else glass.rimBorder,
                shape = shape
            )
            .then(clickableModifier)
            .padding(horizontal = contentPaddingHorizontal, vertical = contentPaddingVertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        content = content
    )
}

/**
 * Slide-up translucent Liquid Glass Bottom Sheet with iOS grabber pill and specular rim.
 */
@Composable
fun GlassSheet(
    visible: Boolean,
    title: String,
    subtitle: String? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    headerTrailing: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val glass = LocalLiquidGlass.current

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically(
            initialOffsetY = { it / 2 },
            animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)
        ),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (glass.isDark) 0.52f else 0.28f))
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onDismiss() })
                },
            contentAlignment = Alignment.BottomCenter
        ) {
            GlassSurface(
                modifier = modifier
                    .fillMaxWidth()
                    .widthIn(max = 540.dp)
                    .padding(horizontal = 10.dp)
                    .padding(bottom = 10.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { /* consume inside sheet */ })
                    },
                shape = RoundedCornerShape(30.dp),
                elevation = 24.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    // iOS Grabber Pill
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(42.dp)
                            .height(4.5.dp)
                            .clip(CircleShape)
                            .background(glass.mutedText.copy(alpha = 0.45f))
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge,
                                color = glass.primaryText
                            )
                            if (!subtitle.isNullOrBlank()) {
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = glass.secondaryText
                                )
                            }
                        }
                        if (headerTrailing != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                content = headerTrailing
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(glass.mutedText.copy(alpha = 0.14f))
                                .testTag("close_glass_sheet_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close sheet",
                                tint = glass.primaryText,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    content()
                }
            }
        }
    }
}

/**
 * Dynamic Island-Style Floating Capsule Toast morphing from the top safe-area notch.
 */
@Composable
fun DynamicIslandToast(
    message: String?,
    badgeText: String? = null,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = !message.isNullOrBlank(),
        enter = fadeIn() + scaleIn(
            initialScale = 0.75f,
            animationSpec = spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMedium)
        ) + slideInVertically(initialOffsetY = { -it }),
        exit = fadeOut() + scaleOut(targetScale = 0.85f) + slideOutVertically(targetOffsetY = { -it }),
        modifier = modifier
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .shadow(18.dp, RoundedCornerShape(50))
                .clip(RoundedCornerShape(50))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xF217191D),
                            Color(0xE60F1013)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.22f),
                    shape = RoundedCornerShape(50)
                )
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .testTag("dynamic_island_toast"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Status notification",
                tint = Color(0xFFD4A86A),
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = message.orEmpty(),
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFFF4F2ED),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!badgeText.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.14f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badgeText,
                        fontFamily = JetBrainsTabularFamily,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFE6C26E)
                    )
                }
            }
        }
    }
}

/**
 * Architectural non-neon gradient squircle badge for Tools & Document cards.
 */
@Composable
fun GlassIconBadge(
    icon: ImageVector,
    gradientColors: List<Color>,
    contentDescription: String?,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .shadow(6.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(gradientColors))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.28f),
                shape = RoundedCornerShape(14.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color(0xFFF7F4EE),
            modifier = Modifier.size(iconSize)
        )
    }
}
