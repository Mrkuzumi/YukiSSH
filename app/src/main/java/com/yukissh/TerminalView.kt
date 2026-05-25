package com.yukissh

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class TerminalView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    companion object {
        private val COLORS = intArrayOf(
            0xFF0C0C0C.toInt(),  // 0  Black
            0xFFC50F1F.toInt(),  // 1  Red
            0xFF13A10E.toInt(),  // 2  Green
            0xFFC19C00.toInt(),  // 3  Yellow
            0xFF0037DA.toInt(),  // 4  Blue
            0xFF881798.toInt(),  // 5  Magenta
            0xFF3A96DD.toInt(),  // 6  Cyan
            0xFFCCCCCC.toInt(),  // 7  White
            0xFF767676.toInt(),  // 8  Bright Black
            0xFFE74856.toInt(),  // 9  Bright Red
            0xFF16C60C.toInt(),  // 10 Bright Green
            0xFFF9F1A5.toInt(),  // 11 Bright Yellow
            0xFF3B78FF.toInt(),  // 12 Bright Blue
            0xFFB4009E.toInt(),  // 13 Bright Magenta
            0xFF61D6D6.toInt(),  // 14 Bright Cyan
            0xFFF2F2F2.toInt(),  // 15 Bright White
        )

        private const val DEFAULT_FG = 7
        private const val DEFAULT_BG = 0
    }

    data class Cell(var ch: Char = ' ', var fg: Int = DEFAULT_FG, var bg: Int = DEFAULT_BG, var bold: Boolean = false) {
        fun reset() { ch = ' '; fg = DEFAULT_FG; bg = DEFAULT_BG; bold = false }
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLORS[DEFAULT_FG]
        textSize = dp(14f)
        typeface = Typeface.MONOSPACE
    }
    private val bgPaint = Paint()

    private var charWidth = 0f
    private var charHeight = 0f
    var rows = 0
        private set
    var cols = 0
        private set

    private val buffer = mutableListOf<MutableList<Cell>>()
    private var cursorRow = 0
    private var cursorCol = 0

    private var currentFg = DEFAULT_FG
    private var currentBg = DEFAULT_BG
    private var currentBold = false

    private var escState = 0
    private val params = StringBuilder()
    private val csiParams = mutableListOf<Int>()

    // UTF-8 decoder state
    private val utf8Buf = ByteArray(4)
    private var utf8Len = 0
    private var utf8Expected = 0

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        charWidth = textPaint.measureText("X")
        charHeight = textPaint.fontSpacing
        val newCols = (w / charWidth).toInt().coerceAtLeast(1)
        val newRows = (h / charHeight).toInt().coerceAtLeast(1)
        if (newCols != cols || newRows != rows) {
            cols = newCols
            rows = newRows
            resizeBuffer()
        }
    }

    private fun resizeBuffer() {
        while (buffer.size < rows) {
            buffer.add(MutableList(cols) { Cell() })
        }
        while (buffer.size > rows) {
            buffer.removeAt(buffer.size - 1)
        }
        for (line in buffer) {
            while (line.size < cols) line.add(Cell())
            while (line.size > cols) line.removeAt(line.size - 1)
        }
        cursorRow = cursorRow.coerceIn(0, rows - 1)
        cursorCol = cursorCol.coerceIn(0, cols - 1)
    }

    fun write(data: ByteArray, len: Int) {
        for (i in 0 until len) {
            val b = data[i].toInt() and 0xFF
            if (utf8Expected == 0) {
                when {
                    b and 0x80 == 0 -> processByte(b)  // ASCII
                    b and 0xE0 == 0xC0 -> { utf8Expected = 2; utf8Buf[0] = b.toByte(); utf8Len = 1 }
                    b and 0xF0 == 0xE0 -> { utf8Expected = 3; utf8Buf[0] = b.toByte(); utf8Len = 1 }
                    b and 0xF8 == 0xF0 -> { utf8Expected = 4; utf8Buf[0] = b.toByte(); utf8Len = 1 }
                    else -> processByte(b)  // invalid, raw
                }
            } else {
                utf8Buf[utf8Len] = b.toByte()
                utf8Len++
                if (utf8Len == utf8Expected) {
                    val ch = String(utf8Buf, 0, utf8Len, Charsets.UTF_8)
                    utf8Expected = 0; utf8Len = 0
                    for (c in ch) putUtf8Char(c)
                }
            }
        }
        postInvalidateOnAnimation()
    }

    private fun putUtf8Char(ch: Char) {
        val width = if (ch.code > 0x7F) 2 else 1
        if (cursorCol + width > cols) { cursorCol = 0; lineFeed() }
        val line = buffer[cursorRow]
        line[cursorCol] = Cell(ch, currentFg, currentBg, currentBold)
        if (width == 2 && cursorCol + 1 < cols) {
            line[cursorCol + 1] = Cell(0.toChar(), currentFg, currentBg, currentBold)
        }
        cursorCol += width
        if (cursorCol >= cols) { cursorCol = 0; lineFeed() }
    }

    private fun processByte(b: Int) {
        when (escState) {
            0 -> {
                when (b) {
                    0x1B -> escState = 1
                    '\n'.code -> lineFeed()
                    '\r'.code -> cursorCol = 0
                    '\b'.code -> { if (cursorCol > 0) cursorCol-- }
                    '\t'.code -> cursorCol = ((cursorCol / 8) + 1) * 8
                    0x07 -> { /* bell */ }
                    else -> putChar(b.toChar())
                }
            }
            1 -> {
                when (b) {
                    '['.code -> { escState = 2; params.clear(); csiParams.clear() }
                    ']'.code -> escState = 3
                    else -> escState = 0
                }
            }
            2 -> {
                when {
                    b in '0'.code..'9'.code || b == ';'.code || b == '?'.code -> {
                        if (b == ';'.code) {
                            csiParams.add(params.toString().toIntOrNull() ?: 0)
                            params.clear()
                        } else if (b != '?'.code) {
                            params.append(b.toChar())
                        }
                    }
                    else -> {
                        if (params.isNotEmpty()) {
                            csiParams.add(params.toString().toIntOrNull() ?: 0)
                        }
                        handleCsi(b.toChar())
                        escState = 0
                    }
                }
            }
            3 -> {
                if (b == 0x07 || b == 0x9C) escState = 0
            }
        }
    }

    private fun handleCsi(cmd: Char) {
        val p = csiParams
        when (cmd) {
            'm' -> handleSgr(p)
            'A' -> cursorRow = (cursorRow - (p.getOrElse(0) { 1 })).coerceAtLeast(0)
            'B' -> cursorRow = (cursorRow + (p.getOrElse(0) { 1 })).coerceAtMost(rows - 1)
            'C' -> cursorCol = (cursorCol + (p.getOrElse(0) { 1 })).coerceAtMost(cols - 1)
            'D' -> cursorCol = (cursorCol - (p.getOrElse(0) { 1 })).coerceAtLeast(0)
            'H', 'f' -> {
                val row = (p.getOrElse(0) { 1 } - 1).coerceIn(0, rows - 1)
                val col = (p.getOrElse(1) { 1 } - 1).coerceIn(0, cols - 1)
                cursorRow = row
                cursorCol = col
            }
            'J' -> {
                when (p.getOrElse(0) { 0 }) {
                    0 -> eraseDisplay(cursorRow, cursorCol, rows - 1, cols - 1)
                    1 -> eraseDisplay(0, 0, cursorRow, cursorCol)
                    2, 3 -> {
                        for (r in buffer.indices) for (c in buffer[r].indices) buffer[r][c].reset()
                        cursorRow = 0; cursorCol = 0
                    }
                }
            }
            'K' -> {
                when (p.getOrElse(0) { 0 }) {
                    0 -> eraseLine(cursorCol, cols - 1)
                    1 -> eraseLine(0, cursorCol)
                    2 -> eraseLine(0, cols - 1)
                }
            }
        }
    }

    private fun handleSgr(params: List<Int>) {
        if (params.isEmpty() || (params.size == 1 && params[0] == 0)) {
            currentFg = DEFAULT_FG; currentBg = DEFAULT_BG; currentBold = false
            return
        }
        var i = 0
        while (i < params.size) {
            val p = params[i]
            when {
                p == 0 -> { currentFg = DEFAULT_FG; currentBg = DEFAULT_BG; currentBold = false }
                p == 1 -> currentBold = true
                p == 22 -> currentBold = false
                p in 30..37 -> currentFg = p - 30
                p in 40..47 -> currentBg = p - 40
                p == 38 && i + 2 < params.size && params[i + 1] == 5 -> {
                    currentFg = params[i + 2].coerceIn(0, 15); i += 2
                }
                p == 48 && i + 2 < params.size && params[i + 1] == 5 -> {
                    currentBg = params[i + 2].coerceIn(0, 15); i += 2
                }
                p in 90..97 -> currentFg = p - 90 + 8
                p in 100..107 -> currentBg = p - 100 + 8
            }
            i++
        }
    }

    private fun putChar(ch: Char) {
        if (cursorCol >= cols) { cursorCol = 0; lineFeed() }
        val line = buffer[cursorRow]
        line[cursorCol] = Cell(ch, if (currentBold && currentFg < 8) currentFg + 8 else currentFg, currentBg, currentBold)
        cursorCol++
        if (cursorCol >= cols) { cursorCol = 0; lineFeed() }
    }

    private fun lineFeed() {
        if (cursorRow < rows - 1) {
            cursorRow++
        } else {
            buffer.removeAt(0)
            buffer.add(MutableList(cols) { Cell() })
        }
    }

    private fun eraseDisplay(r1: Int, c1: Int, r2: Int, c2: Int) {
        for (r in r1..r2) {
            val startC = if (r == r1) c1 else 0
            val endC = if (r == r2) c2 else cols - 1
            for (c in startC..endC) buffer[r][c].reset()
        }
    }

    private fun eraseLine(c1: Int, c2: Int) {
        for (c in c1..c2) buffer[cursorRow][c].reset()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (r in 0 until rows) {
            val y = (r + 1) * charHeight
            val line = buffer[r]
            var c = 0
            while (c < cols) {
                val cell = line[c]
                if (cell.ch == ' ' || cell.ch.code == 0) { c++; continue }
                var end = c + 1
                while (end < cols) {
                    val next = line[end]
                    if (next.ch == ' ' || next.ch.code == 0) break
                    if (next.fg != cell.fg || next.bg != cell.bg || next.bold != cell.bold) break
                    end++
                }

                val x = c * charWidth
                val width = (end - c) * charWidth

                if (cell.bg != DEFAULT_BG) {
                    bgPaint.color = COLORS[cell.bg]
                    canvas.drawRect(x, y - charHeight + dp(1f), x + width, y + dp(1f), bgPaint)
                }

                textPaint.color = COLORS[cell.fg]
                textPaint.isFakeBoldText = cell.bold
                val text = String(CharArray(end - c) { i -> line[c + i].ch })
                canvas.drawText(text, x, y - textPaint.descent(), textPaint)

                c = end
            }
        }
    }

    fun reset() {
        buffer.clear()
        for (i in 0 until rows) buffer.add(MutableList(cols) { Cell() })
        cursorRow = 0; cursorCol = 0
        currentFg = DEFAULT_FG; currentBg = DEFAULT_BG; currentBold = false
        escState = 0; params.clear(); csiParams.clear()
        utf8Expected = 0; utf8Len = 0
        invalidate()
    }

    fun getText(): String {
        val sb = StringBuilder()
        for (row in buffer) {
            val line = row.map { it.ch }.joinToString("").trimEnd()
            sb.appendLine(line)
        }
        return sb.toString()
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
