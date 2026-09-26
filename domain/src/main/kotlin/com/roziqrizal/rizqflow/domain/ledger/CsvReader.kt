package com.roziqrizal.rizqflow.domain.ledger

/** Pengurai CSV RFC 4180 minimal: kolom berkoma, bidang bertanda kutip boleh berisi koma dan baris baru. */
internal object CsvReader {
    fun read(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var field = StringBuilder()
        var row = mutableListOf<String>()
        var inQuotes = false
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < n && text[i + 1] == '"') {
                        field.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    field.append(c)
                }
            } else {
                when (c) {
                    '"' -> inQuotes = true
                    ',' -> {
                        row.add(field.toString())
                        field = StringBuilder()
                    }

                    '\r' -> Unit
                    '\n' -> {
                        row.add(field.toString())
                        field = StringBuilder()
                        rows.add(row)
                        row = mutableListOf()
                    }

                    else -> field.append(c)
                }
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            rows.add(row)
        }
        return rows
    }
}
