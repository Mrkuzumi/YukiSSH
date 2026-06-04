package com.yukissh

/**
 * 终端颜色主题定义，16 色调色板 + 默认前后景色
 */
data class TerminalTheme(
    val name: String,
    val defaultFg: Int,
    val defaultBg: Int,
    val palette16: IntArray
) {
    companion object {
        val BUILTIN = listOf(
            TerminalTheme(
                name = "GitHub Dark",
                defaultFg = 7, defaultBg = 0,
                palette16 = intArrayOf(
                    0xFF0D1117.toInt(), 0xFFC50F1F.toInt(), 0xFF13A10E.toInt(), 0xFFC19C00.toInt(),
                    0xFF0037DA.toInt(), 0xFF881798.toInt(), 0xFF3A96DD.toInt(), 0xFFCCCCCC.toInt(),
                    0xFF767676.toInt(), 0xFFE74856.toInt(), 0xFF16C60C.toInt(), 0xFFF9F1A5.toInt(),
                    0xFF3B78FF.toInt(), 0xFFB4009E.toInt(), 0xFF61D6D6.toInt(), 0xFFF2F2F2.toInt(),
                )
            ),
            TerminalTheme(
                name = "Dracula",
                defaultFg = 7, defaultBg = 0,
                palette16 = intArrayOf(
                    0xFF282A36.toInt(), 0xFFFF5555.toInt(), 0xFF50FA7B.toInt(), 0xFFF1FA8C.toInt(),
                    0xFF6272A4.toInt(), 0xFFBD93F9.toInt(), 0xFF8BE9FD.toInt(), 0xFFF8F8F2.toInt(),
                    0xFF44475A.toInt(), 0xFFFF6E67.toInt(), 0xFF5AF78E.toInt(), 0xFFF4F99D.toInt(),
                    0xFFCAA9FA.toInt(), 0xFFFF92D0.toInt(), 0xFF9AEDFE.toInt(), 0xFFFFFFFF.toInt(),
                )
            ),
            TerminalTheme(
                name = "Solarized Dark",
                defaultFg = 12, defaultBg = 8,
                palette16 = intArrayOf(
                    0xFF002B36.toInt(), 0xFFDC322F.toInt(), 0xFF859900.toInt(), 0xFFB58900.toInt(),
                    0xFF268BD2.toInt(), 0xFF6C71C4.toInt(), 0xFF2AA198.toInt(), 0xFF93A1A1.toInt(),
                    0xFF073642.toInt(), 0xFFCB4B16.toInt(), 0xFF586E75.toInt(), 0xFF657B83.toInt(),
                    0xFF839496.toInt(), 0xFFD33682.toInt(), 0xFFEEE8D5.toInt(), 0xFFFDF6E3.toInt(),
                )
            ),
            TerminalTheme(
                name = "Gruvbox Dark",
                defaultFg = 15, defaultBg = 0,
                palette16 = intArrayOf(
                    0xFF282828.toInt(), 0xFFCC241D.toInt(), 0xFF98971A.toInt(), 0xFFD79921.toInt(),
                    0xFF458588.toInt(), 0xFFB16286.toInt(), 0xFF689D6A.toInt(), 0xFFA89984.toInt(),
                    0xFF928374.toInt(), 0xFFFB4934.toInt(), 0xFFB8BB26.toInt(), 0xFFFABD2F.toInt(),
                    0xFF83A598.toInt(), 0xFFD3869B.toInt(), 0xFF8EC07C.toInt(), 0xFFEBDBB2.toInt(),
                )
            ),
            TerminalTheme(
                name = "CRT 经典绿",
                defaultFg = 2, defaultBg = 0,
                palette16 = intArrayOf(
                    0xFF000000.toInt(), 0xFF00AA00.toInt(), 0xFF00FF00.toInt(), 0xFF55FF55.toInt(),
                    0xFF00AA00.toInt(), 0xFF00FF00.toInt(), 0xFF00AA00.toInt(), 0xFF00FF00.toInt(),
                    0xFF005500.toInt(), 0xFF00CC00.toInt(), 0xFF00FF00.toInt(), 0xFF55FF55.toInt(),
                    0xFF00AA00.toInt(), 0xFF00FF00.toInt(), 0xFF00AA00.toInt(), 0xFF00FF00.toInt(),
                )
            ),
        )
    }
}
