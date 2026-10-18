package app.lekto.prototype.icons.symbols

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val SymbolsDemo.VolumeUp: ImageVector
    get() {
        if (_VolumeUp != null) {
            return _VolumeUp!!
        }
        _VolumeUp = ImageVector.Builder(
            name = "VolumeUp",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(560f, 829f)
                verticalLineToRelative(-82f)
                quadToRelative(90f, -26f, 145f, -100f)
                reflectiveQuadToRelative(55f, -168f)
                quadToRelative(0f, -94f, -55f, -168f)
                reflectiveQuadTo(560f, 211f)
                verticalLineToRelative(-82f)
                quadToRelative(124f, 28f, 202f, 125.5f)
                reflectiveQuadTo(840f, 479f)
                quadToRelative(0f, 127f, -78f, 224.5f)
                reflectiveQuadTo(560f, 829f)
                close()
                moveTo(120f, 600f)
                verticalLineToRelative(-240f)
                horizontalLineToRelative(160f)
                lineToRelative(200f, -200f)
                verticalLineToRelative(640f)
                lineTo(280f, 600f)
                lineTo(120f, 600f)
                close()
                moveTo(560f, 640f)
                verticalLineToRelative(-322f)
                quadToRelative(47f, 22f, 73.5f, 66f)
                reflectiveQuadToRelative(26.5f, 96f)
                quadToRelative(0f, 51f, -26.5f, 94.5f)
                reflectiveQuadTo(560f, 640f)
                close()
                moveTo(400f, 354f)
                lineToRelative(-86f, 86f)
                lineTo(200f, 440f)
                verticalLineToRelative(80f)
                horizontalLineToRelative(114f)
                lineToRelative(86f, 86f)
                verticalLineToRelative(-252f)
                close()
                moveTo(300f, 480f)
                close()
            }
        }.build()

        return _VolumeUp!!
    }

@Suppress("ObjectPropertyName")
private var _VolumeUp: ImageVector? = null
