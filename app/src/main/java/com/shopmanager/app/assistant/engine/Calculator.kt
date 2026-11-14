package com.shopmanager.app.assistant.engine

/** حاسبة بسيطة للجمل مثل "3 كيس × 18500 كم يصير؟". */
internal object Calculator {
    private val MUL = setOf("×", "*", "x", "ضرب", "في")
    private val DIV = setOf("÷", "/", "قسمه", "قسم", "على", ArabicText.PREP_ON)
    private val ADD = setOf("+", "زائد", "جمع", "اجمع", "زيد")
    private val SUB = setOf("-", "−", "ناقص", "طرح", "انقص")

    data class Result(val expression: String, val value: Double)

    private fun fmt(v: Double): String =
        if (v == Math.rint(v) && Math.abs(v) < 1e15) {
            val s = v.toLong().toString()
            s.reversed().chunked(3).joinToString(",").reversed().replace("-,", "-")
        } else String.format(java.util.Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')

    /** [numbers] مواقع الأرقام في [norm]؛ [implicitMultiply] يسمح بالضرب الضمني عند غياب العلامة. */
    fun evaluate(norm: List<String>, numbers: List<NumberMatch>, implicitMultiply: Boolean): Result? {
        if (numbers.size < 2) return null
        val vals = ArrayList<Double>()
        val ops = ArrayList<Char>()
        for ((idx, n) in numbers.withIndex()) {
            vals.add(n.value)
            if (idx == numbers.size - 1) break
            val next = numbers[idx + 1]
            var op: Char? = null
            for (k in n.end until next.start) {
                val t = norm[k]
                op = when {
                    t in MUL -> '×'
                    t in DIV -> '÷'
                    t in ADD -> '+'
                    t in SUB -> '-'
                    else -> op
                }
            }
            if (op == null) {
                if (!implicitMultiply) return null
                op = '×'
            }
            ops.add(op)
        }
        // أولوية الضرب والقسمة.
        val v2 = ArrayList<Double>(); val o2 = ArrayList<Char>()
        v2.add(vals[0])
        for (i in ops.indices) {
            val o = ops[i]
            if (o == '×' || o == '÷') {
                val last = v2.removeAt(v2.size - 1)
                if (o == '÷' && vals[i + 1] == 0.0) return null
                v2.add(if (o == '×') last * vals[i + 1] else last / vals[i + 1])
            } else { o2.add(o); v2.add(vals[i + 1]) }
        }
        var res = v2[0]
        for (i in o2.indices) res = if (o2[i] == '+') res + v2[i + 1] else res - v2[i + 1]
        val expr = StringBuilder(fmt(vals[0]))
        for (i in ops.indices) expr.append(" ").append(ops[i]).append(" ").append(fmt(vals[i + 1]))
        return Result(expr.toString(), res)
    }

    fun format(v: Double): String = fmt(v)
}
