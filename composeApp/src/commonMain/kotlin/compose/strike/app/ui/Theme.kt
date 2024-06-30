package compose.strike.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType.Companion.NonZero
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap.Companion.Round
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.ImageVector.Builder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object Theme {
    object Icons {
        // MIT https://github.com/DevSrSouza/compose-icons
        val Clipboard: ImageVector
            get() {
                if (clipboard != null) {
                    return clipboard!!
                }
                clipboard =
                    Builder(
                        name = "Clipboard",
                        defaultWidth = 24.0.dp,
                        defaultHeight = 24.0.dp,
                        viewportWidth = 24.0f,
                        viewportHeight = 24.0f,
                    ).apply {
                        path(
                            fill = SolidColor(Color(0x00000000)),
                            stroke = SolidColor(Color(0xFF000000)),
                            strokeLineWidth = 2.0f,
                            strokeLineCap = Round,
                            strokeLineJoin =
                                StrokeJoin.Companion.Round,
                            strokeLineMiter = 4.0f,
                            pathFillType = NonZero,
                        ) {
                            moveTo(16.0f, 4.0f)
                            horizontalLineToRelative(2.0f)
                            arcToRelative(2.0f, 2.0f, 0.0f, false, true, 2.0f, 2.0f)
                            verticalLineToRelative(14.0f)
                            arcToRelative(2.0f, 2.0f, 0.0f, false, true, -2.0f, 2.0f)
                            horizontalLineTo(6.0f)
                            arcToRelative(2.0f, 2.0f, 0.0f, false, true, -2.0f, -2.0f)
                            verticalLineTo(6.0f)
                            arcToRelative(2.0f, 2.0f, 0.0f, false, true, 2.0f, -2.0f)
                            horizontalLineToRelative(2.0f)
                        }
                        path(
                            fill = SolidColor(Color(0x00000000)),
                            stroke = SolidColor(Color(0xFF000000)),
                            strokeLineWidth = 2.0f,
                            strokeLineCap = Round,
                            strokeLineJoin =
                                StrokeJoin.Companion.Round,
                            strokeLineMiter = 4.0f,
                            pathFillType = NonZero,
                        ) {
                            moveTo(9.0f, 2.0f)
                            lineTo(15.0f, 2.0f)
                            arcTo(1.0f, 1.0f, 0.0f, false, true, 16.0f, 3.0f)
                            lineTo(16.0f, 5.0f)
                            arcTo(1.0f, 1.0f, 0.0f, false, true, 15.0f, 6.0f)
                            lineTo(9.0f, 6.0f)
                            arcTo(1.0f, 1.0f, 0.0f, false, true, 8.0f, 5.0f)
                            lineTo(8.0f, 3.0f)
                            arcTo(1.0f, 1.0f, 0.0f, false, true, 9.0f, 2.0f)
                            close()
                        }
                    }.build()
                return clipboard!!
            }

        private var clipboard: ImageVector? = null

        // MIT https://github.com/DevSrSouza/compose-icons
        val User: ImageVector
            get() {
                if (user != null) {
                    return user!!
                }
                user =
                    Builder(
                        name = "User",
                        defaultWidth = 24.0.dp,
                        defaultHeight = 24.0.dp,
                        viewportWidth = 24.0f,
                        viewportHeight = 24.0f,
                    ).apply {
                        path(
                            fill = SolidColor(Color(0x00000000)),
                            stroke = SolidColor(Color(0xFF000000)),
                            strokeLineWidth = 2.0f,
                            strokeLineCap = Round,
                            strokeLineJoin =
                                StrokeJoin.Companion.Round,
                            strokeLineMiter = 4.0f,
                            pathFillType = NonZero,
                        ) {
                            moveTo(20.0f, 21.0f)
                            verticalLineToRelative(-2.0f)
                            arcToRelative(4.0f, 4.0f, 0.0f, false, false, -4.0f, -4.0f)
                            horizontalLineTo(8.0f)
                            arcToRelative(4.0f, 4.0f, 0.0f, false, false, -4.0f, 4.0f)
                            verticalLineToRelative(2.0f)
                        }
                        path(
                            fill = SolidColor(Color(0x00000000)),
                            stroke = SolidColor(Color(0xFF000000)),
                            strokeLineWidth = 2.0f,
                            strokeLineCap = Round,
                            strokeLineJoin =
                                StrokeJoin.Companion.Round,
                            strokeLineMiter = 4.0f,
                            pathFillType = NonZero,
                        ) {
                            moveTo(12.0f, 7.0f)
                            moveToRelative(-4.0f, 0.0f)
                            arcToRelative(4.0f, 4.0f, 0.0f, true, true, 8.0f, 0.0f)
                            arcToRelative(4.0f, 4.0f, 0.0f, true, true, -8.0f, 0.0f)
                        }
                    }.build()
                return user!!
            }

        private var user: ImageVector? = null
    }
}
