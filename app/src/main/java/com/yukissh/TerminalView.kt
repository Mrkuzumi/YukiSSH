package com.yukissh

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver

class TerminalView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    companion object {
        private val COLORS = intArrayOf(
            0xFF0C0C0C.toInt(), 0xFFC50F1F.toInt(), 0xFF13A10E.toInt(), 0xFFC19C00.toInt(),
            0xFF0037DA.toInt(), 0xFF881798.toInt(), 0xFF3A96DD.toInt(), 0xFFCCCCCC.toInt(),
            0xFF767676.toInt(), 0xFFE74856.toInt(), 0xFF16C60C.toInt(), 0xFFF9F1A5.toInt(),
            0xFF3B78FF.toInt(), 0xFFB4009E.toInt(), 0xFF61D6D6.toInt(), 0xFFF2F2F2.toInt(),
        )
        private const val DEFAULT_FG = 7
        private const val DEFAULT_BG = 0
        private const val MIN_FONT_DP = 6f
        private const val MAX_FONT_DP = 20f
        private const val MAX_SCROLLBACK = 5000
    }

    data class Cell(var ch: Char = ' ', var fg: Int = DEFAULT_FG, var bg: Int = DEFAULT_BG, var bold: Boolean = false) {
        fun reset() { ch = ' '; fg = DEFAULT_FG; bg = DEFAULT_BG; bold = false }
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLORS[DEFAULT_FG]
        typeface = Typeface.MONOSPACE
    }
    private val bgPaint = Paint()

    var fontSizeDp = 9f; private set

    private var charWidth = 0f
    private var charHeight = 0f
    var rows = 0; private set
    var cols = 0; private set

    private val buffer = mutableListOf<MutableList<Cell>>()
    private var cursorRow = 0
    private var cursorCol = 0

    private var currentFg = DEFAULT_FG
    private var currentBg = DEFAULT_BG
    private var currentBold = false

    private var escState = 0
    private val params = StringBuilder()
    private val csiParams = mutableListOf<Int>()

    private val utf8Buf = ByteArray(4)
    private var utf8Len = 0
    private var utf8Expected = 0

    private var maxCols = 0
    private var autoScroll = true
    private var viewScrollY = 0  // Custom scroll Y, not system scrollTo

    // Touch tracking
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var lastSX = 0
    private var lastSY = 0
    private var moved = false

    init {
        applyFontSize(fontSizeDp)
    }

    fun changeFontSize(deltaDp: Float) {
        val newSize = (fontSizeDp + deltaDp).coerceIn(MIN_FONT_DP, MAX_FONT_DP)
        if (newSize != fontSizeDp) {
            applyFontSize(newSize)
            charWidth = textPaint.measureText("X")
            charHeight = textPaint.fontSpacing
            if (width > 0) cols = (width / charWidth).toInt().coerceAtLeast(1)
            if (height > 0) rows = (height / charHeight).toInt().coerceAtLeast(1)
            invalidate()
        }
    }

    private fun applyFontSize(dp: Float) {
        fontSizeDp = dp
        textPaint.textSize = dpToPx(dp)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        charWidth = textPaint.measureText("X")
        charHeight = textPaint.fontSpacing
        if (w > 0) cols = (w / charWidth).toInt().coerceAtLeast(1)
        if (h > 0) rows = (h / charHeight).toInt().coerceAtLeast(1)
        if (oldw == 0 || oldh == 0) {
            resizeBuffer()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
    }

    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        if (autoScroll && buffer.size > rows && rows > 0) {
            val target = ((buffer.size - rows) * charHeight).toInt().coerceAtLeast(0)
            if (viewScrollY != target) {
                viewScrollY = target
                invalidate()
            }
        }
    }

    private fun resizeBuffer() {
        buffer.clear()
        for (i in 0 until rows) buffer.add(mutableListOf())
        cursorRow = 0; cursorCol = 0
        maxCols = 0; autoScroll = true
        viewScrollY = 0; scrollTo(0, 0)
        invalidate()
    }

    fun write(data: ByteArray, len: Int) {
        for (i in 0 until len) {
            val b = data[i].toInt() and 0xFF
            if (utf8Expected == 0) {
                when {
                    b and 0x80 == 0 -> processByte(b)
                    b and 0xE0 == 0xC0 -> { utf8Expected = 2; utf8Buf[0] = b.toByte(); utf8Len = 1 }
                    b and 0xF0 == 0xE0 -> { utf8Expected = 3; utf8Buf[0] = b.toByte(); utf8Len = 1 }
                    b and 0xF8 == 0xF0 -> { utf8Expected = 4; utf8Buf[0] = b.toByte(); utf8Len = 1 }
                    else -> processByte(b)
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
        if (autoScroll) scrollToBottom()
        postInvalidateOnAnimation()
    }

    private fun scrollToBottom() {
        val maxSY = ((buffer.size - rows) * charHeight).toInt().coerceAtLeast(0)
        viewScrollY = maxSY
        invalidate()
    }

    private fun putUtf8Char(ch: Char) {
        val width = if (ch.code > 0x7F) 2 else 1
        ensureLineCapacity(cursorRow, cursorCol + width)
        val line = buffer[cursorRow]
        while (line.size <= cursorCol) line.add(Cell())
        line[cursorCol] = Cell(ch, currentFg, currentBg, currentBold)
        if (width == 2) {
            while (line.size <= cursorCol + 1) line.add(Cell())
            line[cursorCol + 1] = Cell(0.toChar(), currentFg, currentBg, currentBold)
        }
        cursorCol += width
        maxCols = maxOf(maxCols, cursorCol)
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
                    0x07 -> {}
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
                            csiParams.add(params.toString().toIntOrNull() ?: 0); params.clear()
                        } else if (b != '?'.code) params.append(b.toChar())
                    }
                    else -> {
                        if (params.isNotEmpty()) csiParams.add(params.toString().toIntOrNull() ?: 0)
                        handleCsi(b.toChar()); escState = 0
                    }
                }
            }
            3 -> { if (b == 0x07 || b == 0x9C) escState = 0 }
        }
    }

    private fun handleCsi(cmd: Char) {
        val p = csiParams
        when (cmd) {
            'm' -> handleSgr(p)
            'A' -> cursorRow = (cursorRow - (p.getOrElse(0) { 1 })).coerceAtLeast(0)
            'B' -> cursorRow = (cursorRow + (p.getOrElse(0) { 1 })).coerceAtMost(buffer.size - 1)
            'C' -> cursorCol += (p.getOrElse(0) { 1 })
            'D' -> cursorCol = (cursorCol - (p.getOrElse(0) { 1 })).coerceAtLeast(0)
            'H', 'f' -> {
                cursorRow = (p.getOrElse(0) { 1 } - 1).coerceIn(0, buffer.size - 1)
                cursorCol = (p.getOrElse(1) { 1 } - 1).coerceAtLeast(0)
            }
            'J' -> {
                when (p.getOrElse(0) { 0 }) {
                    0 -> eraseDisplay(cursorRow, cursorCol, buffer.size - 1, Int.MAX_VALUE)
                    1 -> eraseDisplay(0, 0, cursorRow, cursorCol)
                    2, 3 -> { for (r in buffer.indices) buffer[r].clear(); cursorRow = 0; cursorCol = 0; maxCols = 0 }
                }
            }
            'K' -> {
                when (p.getOrElse(0) { 0 }) {
                    0 -> eraseLine(cursorCol, Int.MAX_VALUE)
                    1 -> eraseLine(0, cursorCol)
                    2 -> eraseLine(0, Int.MAX_VALUE)
                }
            }
        }
    }

    private fun handleSgr(params: List<Int>) {
        if (params.isEmpty() || (params.size == 1 && params[0] == 0)) {
            currentFg = DEFAULT_FG; currentBg = DEFAULT_BG; currentBold = false; return
        }
        var i = 0
        while (i < params.size) {
            when (val p = params[i]) {
                0 -> { currentFg = DEFAULT_FG; currentBg = DEFAULT_BG; currentBold = false }
                1 -> currentBold = true
                22 -> currentBold = false
                in 30..37 -> currentFg = p - 30
                in 40..47 -> currentBg = p - 40
                38 -> { if (i + 2 < params.size && params[i + 1] == 5) { currentFg = params[i + 2].coerceIn(0, 15); i += 2 } }
                48 -> { if (i + 2 < params.size && params[i + 1] == 5) { currentBg = params[i + 2].coerceIn(0, 15); i += 2 } }
                in 90..97 -> currentFg = p - 90 + 8
                in 100..107 -> currentBg = p - 100 + 8
            }
            i++
        }
    }

    private fun putChar(ch: Char) {
        ensureLineCapacity(cursorRow, cursorCol + 1)
        val line = buffer[cursorRow]
        while (line.size <= cursorCol) line.add(Cell())
        line[cursorCol] = Cell(ch, if (currentBold && currentFg < 8) currentFg + 8 else currentFg, currentBg, currentBold)
        cursorCol++
        maxCols = maxOf(maxCols, cursorCol)
    }

    private fun ensureLineCapacity(row: Int, needCols: Int) {
        while (buffer.size <= row) buffer.add(mutableListOf())
    }

    private fun lineFeed() {
        cursorRow++
        cursorCol = 0
        ensureLineCapacity(cursorRow, 0)
        // Trim old lines if buffer exceeds limit
        while (buffer.size > MAX_SCROLLBACK) {
            buffer.removeAt(0)
            cursorRow--
            if (cursorRow < 0) cursorRow = 0
        }
    }

    private fun eraseDisplay(r1: Int, c1: Int, r2: Int, c2: Int) {
        for (r in r1..r2.coerceAtMost(buffer.size - 1)) {
            val line = buffer[r]
            val startC = if (r == r1) c1 else 0
            val endC = if (r == r2) c2.coerceAtMost(line.size - 1) else line.size - 1
            for (c in startC..endC) {
                while (line.size <= c) line.add(Cell())
                line[c].reset()
            }
        }
    }

    private fun eraseLine(c1: Int, c2: Int) {
        val line = buffer[cursorRow]
        val endC = c2.coerceAtMost(line.size - 1)
        for (c in c1..endC) {
            while (line.size <= c) line.add(Cell())
            line[c].reset()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Force-scroll to bottom before every frame if autoScroll is on
        if (autoScroll && buffer.size > rows && rows > 0) {
            viewScrollY = ((buffer.size - rows) * charHeight).toInt().coerceAtLeast(0)
        }
        if (charWidth == 0f || charHeight == 0f) return
        val topRow = (viewScrollY / charHeight).toInt()
        val startCol = (scrollX / charWidth).toInt()
        val visibleCols = (width / charWidth).toInt() + 1
        val visibleRows = rows + 1
        for (sr in 0 until visibleRows) {
            val br = topRow + sr
            if (br >= buffer.size) break
            val y = (sr + 1) * charHeight
            val line = buffer[br]
            val lineLen = line.size
            if (lineLen == 0) continue
            val endCol = (startCol + visibleCols).coerceAtMost(lineLen)
            var c = startCol
            while (c < endCol) {
                val cell = line[c]
                if (cell.ch == ' ' || cell.ch.code == 0) { c++; continue }
                var end = c + 1
                while (end < endCol) {
                    val next = line[end]
                    if (next.ch == ' ' || next.ch.code == 0) break
                    if (next.fg != cell.fg || next.bg != cell.bg || next.bold != cell.bold) break
                    end++
                }
                val x = c * charWidth
                val segWidth = (end - c) * charWidth
                if (cell.bg != DEFAULT_BG) {
                    bgPaint.color = COLORS[cell.bg]
                    canvas.drawRect(x, y - charHeight + dpToPx(1f), x + segWidth, y + dpToPx(1f), bgPaint)
                }
                textPaint.color = COLORS[cell.fg]
                textPaint.isFakeBoldText = cell.bold
                val chars = CharArray(end - c) { i -> line[c + i].ch }
                canvas.drawText(String(chars), x, y - textPaint.descent(), textPaint)
                c = end
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = event.x; touchDownY = event.y
                lastSX = scrollX; lastSY = viewScrollY; moved = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (touchDownX - event.x).toInt()
                val dy = (touchDownY - event.y).toInt()
                if (Math.abs(dx) > 5 || Math.abs(dy) > 5) moved = true
                val maxSX = ((maxCols * charWidth) - width).toInt().coerceAtLeast(0)
                val newX = (lastSX + dx).coerceIn(0, maxSX)
                val maxSY = ((buffer.size - rows) * charHeight).toInt().coerceAtLeast(0)
                val newY = (lastSY + dy).coerceIn(0, maxSY)
                scrollTo(newX, 0)
                viewScrollY = newY
                if (moved) autoScroll = newY >= maxSY
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                if (!moved) performClick()
            }
        }
        return true
    }

    override fun computeHorizontalScrollRange(): Int = (maxCols * charWidth).toInt()
    override fun computeHorizontalScrollOffset(): Int = scrollX
    override fun computeHorizontalScrollExtent(): Int = width
    override fun computeVerticalScrollRange(): Int = (buffer.size * charHeight).toInt()
    override fun computeVerticalScrollOffset(): Int = viewScrollY
    override fun computeVerticalScrollExtent(): Int = height

    fun reset() {
        buffer.clear()
        for (i in 0 until rows) buffer.add(mutableListOf())
        cursorRow = 0; cursorCol = 0; maxCols = 0
        currentFg = DEFAULT_FG; currentBg = DEFAULT_BG; currentBold = false
        escState = 0; params.clear(); csiParams.clear()
        utf8Expected = 0; utf8Len = 0
        autoScroll = true; viewScrollY = 0; scrollTo(0, 0)
        invalidate()
    }

    fun getText(): String {
        val sb = StringBuilder()
        for (row in buffer) {
            sb.appendLine(row.map { it.ch }.joinToString("").trimEnd())
        }
        return sb.toString()
    }

    private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density
}
