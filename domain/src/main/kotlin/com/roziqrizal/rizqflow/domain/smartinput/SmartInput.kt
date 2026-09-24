package com.roziqrizal.rizqflow.domain.smartinput

import com.roziqrizal.rizqflow.domain.ledger.Account
import com.roziqrizal.rizqflow.domain.ledger.CatatContext
import com.roziqrizal.rizqflow.domain.ledger.CatatDraft
import com.roziqrizal.rizqflow.domain.ledger.CatatMode
import com.roziqrizal.rizqflow.domain.ledger.Category
import com.roziqrizal.rizqflow.domain.ledger.INCOME_SOURCES
import com.roziqrizal.rizqflow.domain.ledger.LedgerService
import com.roziqrizal.rizqflow.domain.ledger.Room
import com.roziqrizal.rizqflow.domain.ledger.TransactionRepository
import com.roziqrizal.rizqflow.domain.model.AccountId
import com.roziqrizal.rizqflow.domain.model.AccountKind
import com.roziqrizal.rizqflow.domain.model.CategoryId
import com.roziqrizal.rizqflow.domain.model.RoomId
import com.roziqrizal.rizqflow.domain.model.TransactionKind
import com.roziqrizal.rizqflow.domain.money.Money
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate

/*
 * Input cerdas (Tahap 11, Gratis): kalimat bebas seperti "gojek 23rb dari gopay", "gaji masuk 8jt",
 * atau "tf bca ke gopay 100rb kemarin" diuraikan jadi isian Catat. Berbasis aturan dan sepenuhnya di
 * perangkat, tanpa jaringan dan tanpa model. Hasilnya TIDAK PERNAH tersimpan sendiri: ia hanya
 * mengisi layar Catat, tempat pengguna memeriksa dan menekan Simpan.
 */

/** Bagian isian yang benar-benar dikenali dari kalimat (bukan diisi bawaan); dipakai layar untuk menandai yang perlu diperiksa. */
enum class SmartField { KIND, AMOUNT, ACCOUNT, TO_ACCOUNT, CATEGORY, ROOM, DATE, SOURCE }

/** Satu pengeluaran lampau (catatan beserta ruang, kategori, dan akunnya) untuk menebak kategori dari kata yang pernah dipakai. */
data class HistoryHint(val note: String, val roomId: RoomId, val categoryId: CategoryId)

/** Bahan penguraian: pilihan yang ada di Catat, riwayat pengeluaran (terbaru dulu), dan hari ini. */
data class SmartInputContext(
    val accounts: List<Account>,
    val rooms: List<Room>,
    val categories: Map<RoomId, List<Category>>,
    val history: List<HistoryHint>,
    val today: LocalDate,
)

/**
 * Hasil uraian. [kind] hanya INCOME, EXPENSE, atau TRANSFER. Kolom yang tidak dikenali null (atau
 * kosong untuk [note]) dan diisi bawaan Catat; [understood] mencatat mana yang benar-benar dikenali.
 */
data class ParsedEntry(
    val kind: TransactionKind,
    val amount: Money,
    val note: String,
    val date: LocalDate,
    val accountId: AccountId?,
    val toAccountId: AccountId?,
    val roomId: RoomId?,
    val categoryId: CategoryId?,
    val source: String?,
    val understood: Set<SmartField>,
)

object SmartInputParser {

    /** Uraian [text]; null bila kosong atau tidak ada nominal yang bisa dibaca. */
    fun parse(text: String, context: SmartInputContext): ParsedEntry? {
        val tokens = text.trim().split(WHITESPACE).filter { it.isNotEmpty() }.map { Token(it) }
        if (tokens.isEmpty()) return null
        val understood = mutableSetOf<SmartField>()

        val date = extractDate(tokens, context.today)?.also { understood += SmartField.DATE } ?: context.today
        val amount = extractAmount(tokens) ?: return null
        understood += SmartField.AMOUNT

        val mentions = extractAccounts(tokens, context.accounts)
        val kind = decideKind(tokens, mentions.size)
        if (kind.explicit) understood += SmartField.KIND
        consumeKindWords(tokens, kind.kind)

        var accountId: AccountId? = null
        var toAccountId: AccountId? = null
        when (kind.kind) {
            TransactionKind.TRANSFER -> {
                val from = mentions.firstOrNull { it.direction == Direction.FROM }
                val to = mentions.firstOrNull { it.direction == Direction.TO }
                val loose = mentions.filter { it.direction == Direction.NONE }.toMutableList()
                var fromAccount = from?.account
                var toAccount = to?.account
                // "topup gopay 100rb": satu akun tanpa arah pada kata masuk-ke berarti tujuannya.
                if (kind.destinationFirst && fromAccount == null && toAccount == null && loose.size == 1) {
                    toAccount = loose.removeAt(0).account
                }
                if (fromAccount == null && loose.isNotEmpty()) fromAccount = loose.removeAt(0).account
                if (toAccount == null && loose.isNotEmpty()) toAccount = loose.removeAt(0).account
                accountId = fromAccount?.id
                toAccountId = toAccount?.id?.takeIf { it != accountId }
                if (accountId != null) understood += SmartField.ACCOUNT
                if (toAccountId != null) understood += SmartField.TO_ACCOUNT
            }

            else -> {
                val main = mentions.firstOrNull { it.direction == Direction.FROM } ?: mentions.firstOrNull()
                accountId = main?.account?.id
                if (accountId != null) understood += SmartField.ACCOUNT
            }
        }
        // Akun yang tidak terpakai (mis. "ke" pada bukan transfer) tetap dianggap bagian kalimat, bukan catatan.
        mentions.forEach { it.consume(tokens) }

        val income = kind.kind == TransactionKind.INCOME
        val source = if (income) incomeSourceOf(tokens)?.also { understood += SmartField.SOURCE } else null

        var roomId: RoomId? = null
        var categoryId: CategoryId? = null
        if (kind.kind == TransactionKind.EXPENSE) {
            val match = matchCategory(tokens, context)
            if (match != null) {
                roomId = match.roomId
                categoryId = match.categoryId
                understood += if (match.categoryId != null) SmartField.CATEGORY else SmartField.ROOM
            }
        }

        return ParsedEntry(
            kind = kind.kind,
            amount = amount,
            note = noteOf(tokens),
            date = date,
            accountId = accountId,
            toAccountId = toAccountId,
            roomId = roomId,
            categoryId = categoryId,
            source = source,
            understood = understood,
        )
    }

    /**
     * Menerapkan [entry] ke isian Catat lewat [CatatDraft.withMode], sehingga hasilnya selalu sah
     * terhadap [context]. Kolom yang tidak dikenali memakai bawaan Catat (akun, ruang, dan kategori terakhir).
     */
    fun toDraft(entry: ParsedEntry, context: CatatContext, today: LocalDate): CatatDraft {
        val mode = when (entry.kind) {
            TransactionKind.INCOME -> CatatMode.INCOME
            TransactionKind.TRANSFER -> CatatMode.TRANSFER
            else -> CatatMode.EXPENSE
        }
        var draft = CatatDraft.start(context, today, mode).copy(
            digits = entry.amount.minor.toString(),
            date = entry.date,
            note = entry.note.take(LedgerService.NOTE_MAX),
        )
        entry.accountId?.let { draft = draft.withAccount(it, context) }
        entry.toAccountId?.let { draft = draft.withToAccount(it, context) }
        entry.roomId?.let { draft = draft.withRoom(it, context) }
        entry.categoryId?.let { draft = draft.withCategory(it) }
        entry.source?.let { draft = draft.withSource(it) }
        if (mode == CatatMode.INCOME && entry.source == null) draft = draft.withSource(INCOME_SOURCES.last())
        return draft.withMode(mode, context)
    }

    // ------------------------------------------------------------------------------ token

    private class Token(val raw: String) {
        /** Huruf kecil tanpa tanda baca di ujung; dasar semua pencocokan. */
        val low: String = raw.lowercase().trim { it in EDGE_PUNCTUATION }
        var used: Boolean = false
    }

    private fun List<Token>.free(): List<IndexedValue<Token>> = withIndex().filter { !it.value.used }

    // ------------------------------------------------------------------------------ tanggal

    private val weekdays = mapOf(
        "senin" to DayOfWeek.MONDAY, "selasa" to DayOfWeek.TUESDAY, "rabu" to DayOfWeek.WEDNESDAY, "kamis" to DayOfWeek.THURSDAY,
        "jumat" to DayOfWeek.FRIDAY, "jum'at" to DayOfWeek.FRIDAY, "sabtu" to DayOfWeek.SATURDAY, "minggu" to DayOfWeek.SUNDAY, "ahad" to DayOfWeek.SUNDAY,
    )

    /** Menemukan dan memakai satu penanda tanggal; tanggal di masa depan tidak pernah dihasilkan. */
    private fun extractDate(tokens: List<Token>, today: LocalDate): LocalDate? {
        fun low(i: Int) = tokens.getOrNull(i)?.takeIf { !it.used }?.low
        fun take(vararg indexes: Int) = indexes.forEach { tokens[it].used = true }
        for (i in tokens.indices) {
            val t = low(i) ?: continue
            val n1 = low(i + 1)
            val n2 = low(i + 2)
            val n3 = low(i + 3)
            when {
                t in YESTERDAY_BEFORE && n1 == "lusa" -> { take(i, i + 1); return today.minusDays(2) }
                t in YESTERDAY -> { take(i); return today.minusDays(1) }
                t == "tadi" && n1 == "malam" || t == "malam" && n1 == "tadi" -> { take(i, i + 1); return today.minusDays(1) }
                t == "tadi" && n1 in DAYPARTS -> { take(i, i + 1); return today }
                t == "tadi" || t == "barusan" || t == "td" -> { take(i); return today }
                t == "hari" && n1 == "ini" -> { take(i, i + 1); return today }
                t == "hari" && n1 != null && n1 in weekdays -> { take(i, i + 1); return mostRecent(today, weekdays.getValue(n1)) }
                t == "seminggu" && n1 == "lalu" -> { take(i, i + 1); return today.minusDays(7) }
                (t == "tgl" || t == "tanggal") && n1 != null && n1.all(Char::isDigit) && n1.length in 1..2 -> {
                    val day = n1.toInt()
                    val date = dayOfRecentMonth(today, day)
                    if (date != null) { take(i, i + 1); return date }
                }

                t.all(Char::isDigit) && t.length in 1..2 && (n1 == "hari" || n1 == "hr") && (n2 == "lalu" || n2 == "yang" && n3 == "lalu") -> {
                    val days = t.toInt()
                    if (days in 1..MAX_DAYS_BACK) {
                        if (n2 == "lalu") take(i, i + 1, i + 2) else take(i, i + 1, i + 2, i + 3)
                        return today.minusDays(days.toLong())
                    }
                }

                else -> numericDate(t, today)?.let { take(i); return it }
            }
        }
        return null
    }

    private fun mostRecent(today: LocalDate, day: DayOfWeek): LocalDate {
        val back = (today.dayOfWeek.value - day.value + 7) % 7
        return today.minusDays(back.toLong())
    }

    /** "tanggal 24": tanggal itu bulan ini, atau bulan lalu bila belum tiba; null bila tanggalnya tidak ada. */
    private fun dayOfRecentMonth(today: LocalDate, day: Int): LocalDate? {
        if (day !in 1..31) return null
        val thisMonth = runCatching { today.withDayOfMonth(day) }.getOrNull()
        if (thisMonth != null && thisMonth <= today) return thisMonth
        val previous = today.minusMonths(1)
        return runCatching { previous.withDayOfMonth(day) }.getOrNull()
    }

    /** "24/9", "24-9-2026", "24/09/26": hari lebih dulu; tanpa tahun berarti tahun ini (atau tahun lalu bila belum tiba). */
    private fun numericDate(token: String, today: LocalDate): LocalDate? {
        val match = NUMERIC_DATE.matchEntire(token) ?: return null
        val day = match.groupValues[1].toInt()
        val month = match.groupValues[2].toInt()
        val yearText = match.groupValues[3]
        val year = when {
            yearText.isEmpty() -> today.year
            yearText.length == 2 -> 2000 + yearText.toInt()
            else -> yearText.toInt()
        }
        val date = runCatching { LocalDate.of(year, month, day) }.getOrNull() ?: return null
        if (date <= today) return date
        return if (yearText.isEmpty()) runCatching { LocalDate.of(year - 1, month, day) }.getOrNull() else null
    }

    // ------------------------------------------------------------------------------ nominal

    private class AmountCandidate(val value: Long, val rank: Int, val indexes: List<Int>)

    /** Memilih satu nominal: yang bersatuan (rb, k, jt) atau berawalan Rp lebih dulu, lalu bilangan bulat besar, lalu yang kecil. */
    private fun extractAmount(tokens: List<Token>): Money? {
        val candidates = mutableListOf<AmountCandidate>()
        for ((i, token) in tokens.free()) {
            val low = token.low
            var index = i
            var text = low
            var indexes = mutableListOf(i)
            var rupiahPrefix = false
            if (low == "rp" || low == "rp.") {
                val next = tokens.getOrNull(i + 1)?.takeIf { !it.used } ?: continue
                text = next.low
                indexes = mutableListOf(i, i + 1)
                index = i + 1
                rupiahPrefix = true
            } else if (low.startsWith("rp") && low.length > 2 && low[2].let { it.isDigit() || it == '.' }) {
                text = low.removePrefix("rp").removePrefix(".")
                rupiahPrefix = true
            }
            val match = AMOUNT_TOKEN.matchEntire(text) ?: continue
            val number = match.groupValues[1]
            var unit = match.groupValues[2]
            if (unit.isEmpty()) {
                val next = tokens.getOrNull(index + 1)?.takeIf { !it.used }?.low
                if (next != null && next in UNITS) {
                    unit = next
                    indexes += index + 1
                }
            }
            val value = valueOf(number, unit) ?: continue
            val grouped = unit.isEmpty() && THOUSANDS.matches(number)
            val rank = when {
                unit.isNotEmpty() || rupiahPrefix || grouped -> 1
                value >= SMALL_LIMIT -> 2
                else -> 3
            }
            candidates += AmountCandidate(value, rank, indexes)
        }
        val best = candidates.minWithOrNull(compareBy<AmountCandidate> { it.rank }.thenByDescending { it.value }) ?: return null
        if (best.value <= 0) return null
        best.indexes.forEach { tokens[it].used = true }
        return Money.rupiah(best.value)
    }

    /** "25000", "25.000", "1.250.000" tanpa satuan; "23rb", "1,5jt", "2.5k" dengan satuan (pecahan boleh). Null bila tidak masuk akal. */
    private fun valueOf(number: String, unit: String): Long? {
        if (number.length > MAX_DIGITS) return null
        if (unit.isEmpty()) {
            if (THOUSANDS.matches(number)) return number.replace(Regex("[.,]"), "").toLongOrNull()
            return number.takeIf { it.all(Char::isDigit) }?.toLongOrNull()
        }
        val factor = when (unit) {
            "juta", "jt" -> 1_000_000L
            else -> 1_000L
        }
        val normalized = number.replace(',', '.')
        if (normalized.count { it == '.' } > 1) return null
        val decimal = normalized.toBigDecimalOrNull() ?: return null
        return decimal.multiply(BigDecimal(factor)).setScale(0, RoundingMode.HALF_UP).toLong()
    }

    // ------------------------------------------------------------------------------ jenis

    private class KindDecision(val kind: TransactionKind, val explicit: Boolean, val destinationFirst: Boolean = false)

    /**
     * Urutan keputusan: transfer (kata transfer dengan cukup akun) lebih dulu; lalu kata kerja pengeluaran
     * (beli, bayar) mengalahkan kata benda pemasukan (gaji, bonus) kecuali ada kata kerja penerimaan
     * (terima, dapat, cair); lalu pemasukan; selebihnya pengeluaran.
     */
    private fun decideKind(tokens: List<Token>, accountMentions: Int): KindDecision {
        val words = tokens.free().map { it.value.low }
        val hasTopUp = words.any { it == "topup" || it == "top-up" } || words.zipWithNext().any { (a, b) -> a == "top" && b == "up" }
        val transferWord = hasTopUp || words.any { it in TRANSFER_WORDS }
        if (transferWord) {
            val needsTwo = words.any { it in TRANSFER_TWO_ACCOUNTS }
            if (accountMentions >= 2 || (!needsTwo && accountMentions >= 1)) {
                return KindDecision(TransactionKind.TRANSFER, explicit = true, destinationFirst = hasTopUp)
            }
        }
        val expenseVerb = words.any { it in EXPENSE_VERBS }
        val incomeVerb = words.withIndex().any { (i, w) -> w in INCOME_VERBS || (w == "masuk" && words.getOrNull(i - 1) !in NOT_INCOME_BEFORE_MASUK) }
        val receiveVerb = words.any { it in RECEIVE_VERBS }
        if (expenseVerb && !receiveVerb) return KindDecision(TransactionKind.EXPENSE, explicit = true)
        // Kata usaha ("omzet", "jualan", ...) tanpa kata kerja lain juga menandakan pemasukan, seperti "gaji" atau "bonus".
        if (incomeVerb || words.any { it in INCOME_NOUNS || it in BUSINESS_WORDS }) return KindDecision(TransactionKind.INCOME, explicit = true)
        return KindDecision(TransactionKind.EXPENSE, explicit = expenseVerb)
    }

    /** Kata penunjuk jenis (beli, terima, transfer, ...) bukan bagian catatan; kata benda seperti "gaji" tetap. */
    private fun consumeKindWords(tokens: List<Token>, kind: TransactionKind) {
        val words = when (kind) {
            TransactionKind.TRANSFER -> TRANSFER_WORDS + setOf("topup", "top-up")
            TransactionKind.INCOME -> INCOME_VERBS + RECEIVE_VERBS
            else -> EXPENSE_VERBS
        }
        for ((i, token) in tokens.free()) {
            if (token.low in words) token.used = true
            if (kind == TransactionKind.TRANSFER && token.low == "top" && tokens.getOrNull(i + 1)?.takeIf { !it.used }?.low == "up") {
                token.used = true
                tokens[i + 1].used = true
            }
        }
        if (kind == TransactionKind.INCOME) {
            for ((i, token) in tokens.free()) {
                if (token.low == "masuk" && tokens.getOrNull(i - 1)?.low !in NOT_INCOME_BEFORE_MASUK) token.used = true
            }
        }
    }

    private fun incomeSourceOf(tokens: List<Token>): String? {
        val words = tokens.map { it.low }
        return when {
            words.any { it == "gaji" || it == "gajian" } -> INCOME_SOURCES[0]
            words.any { it in BUSINESS_WORDS } -> INCOME_SOURCES[1]
            else -> null
        }
    }

    // ------------------------------------------------------------------------------ akun

    private enum class Direction { FROM, TO, NONE }

    /** Satu penyebutan akun: token yang dipakai (termasuk kata depan "dari"/"ke"), dan arahnya. */
    private class AccountMention(val account: Account, val indexes: List<Int>, val direction: Direction) {
        fun consume(tokens: List<Token>) = indexes.forEach { tokens[it].used = true }
    }

    private fun normalize(text: String): List<String> =
        text.lowercase().split(Regex("[^\\p{L}\\p{Nd}]+")).filter { it.isNotEmpty() }

    /**
     * Mencari akun yang disebut: nama lengkap (boleh beberapa kata) lebih dulu, lalu satu kata nama yang
     * hanya dimiliki satu akun. "tunai", "cash", dan "kas" berarti akun bertipe tunai bila tidak ada akun
     * bernama itu. Kata depan langsung sebelumnya menentukan arah.
     */
    private fun extractAccounts(tokens: List<Token>, accounts: List<Account>): List<AccountMention> {
        val found = mutableListOf<AccountMention>()
        val taken = mutableSetOf<Int>()
        val words = tokens.map { normalize(it.low).joinToString("") }

        fun mention(account: Account, from: Int, length: Int) {
            val indexes = (from until from + length).toMutableList()
            val previous = from - 1
            var direction = Direction.NONE
            if (previous >= 0 && previous !in taken && !tokens[previous].used) {
                when (tokens[previous].low) {
                    in FROM_PREPOSITIONS -> { direction = Direction.FROM; indexes.add(0, previous) }
                    "ke" -> { direction = Direction.TO; indexes.add(0, previous) }
                }
            }
            taken += indexes
            found += AccountMention(account, indexes, direction)
        }

        // Nama lengkap, yang terpanjang lebih dulu supaya "BCA Utama" tidak kalah dari "BCA".
        for (account in accounts.sortedByDescending { normalize(it.name).size }) {
            val name = normalize(account.name)
            if (name.isEmpty()) continue
            var i = 0
            while (i + name.size <= tokens.size) {
                val window = (i until i + name.size)
                if (window.none { it in taken || tokens[it].used } && window.zip(name).all { (index, part) -> words[index] == part }) {
                    mention(account, i, name.size)
                    break
                }
                i++
            }
        }
        // Satu kata nama yang unik di antara semua akun ("bca" untuk "BCA Utama").
        val ownersOfWord = accounts.flatMap { account -> normalize(account.name).distinct().map { it to account } }.groupBy({ it.first }, { it.second })
        for ((i, word) in words.withIndex()) {
            if (i in taken || tokens[i].used || word.length < MIN_ACCOUNT_WORD) continue
            val owner = ownersOfWord[word]?.singleOrNull() ?: continue
            if (found.any { it.account.id == owner.id }) continue
            mention(owner, i, 1)
        }
        // Kata tunai untuk akun bertipe tunai.
        for ((i, word) in words.withIndex()) {
            if (i in taken || tokens[i].used || word !in CASH_WORDS) continue
            val cash = accounts.firstOrNull { it.kind == AccountKind.CASH } ?: continue
            if (found.any { it.account.id == cash.id }) continue
            mention(cash, i, 1)
        }
        return found.sortedBy { it.indexes.min() }
    }

    // ------------------------------------------------------------------------------ kategori

    private class CategoryMatch(val roomId: RoomId, val categoryId: CategoryId?)

    /**
     * Menebak ruang dan kategori dari kata sisa: nama kategori (bukan kategori sistem) yang kata-katanya
     * paling banyak cocok; lalu nama ruang; lalu kata yang pernah dipakai di riwayat pengeluaran. Nama
     * yang seri antara dua kategori dianggap tidak dikenali daripada menebak.
     */
    private fun matchCategory(tokens: List<Token>, context: SmartInputContext): CategoryMatch? {
        // Indeks token ikut disimpan supaya kata nama kategori atau ruang yang cocok bisa ditandai
        // terpakai (tidak berakhir dobel di catatan, seperti akun dan kata jenis).
        val contentTokens = tokens.free().mapNotNull { (i, token) ->
            val word = normalize(token.low).joinToString("")
            (i to word).takeIf { word.length >= MIN_CONTENT_WORD && word !in STOPWORDS }
        }
        val content = contentTokens.map { it.second }.toSet()
        if (content.isEmpty()) return null

        fun consume(words: Set<String>) = contentTokens.filter { it.second in words }.forEach { tokens[it.first].used = true }

        class Scored(val room: Room, val category: Category, val words: Set<String>, val score: Int)
        val scored = context.rooms.flatMap { room ->
            context.categories[room.id].orEmpty().filter { !it.archived && !it.isSystem }.map { category ->
                val nameWords = normalize(category.name).filter { it.length >= MIN_CONTENT_WORD && it !in STOPWORDS }.toSet()
                Scored(room, category, nameWords, nameWords.count { it in content })
            }
        }.filter { it.score > 0 }
        val top = scored.maxOfOrNull { it.score }
        if (top != null) {
            val best = scored.filter { it.score == top }
            if (best.size == 1) {
                consume(best.single().words)
                return CategoryMatch(best.single().room.id, best.single().category.id)
            }
        }

        val roomHits = context.rooms.filter { room -> normalize(room.name).any { it in content } }
        if (roomHits.size == 1) {
            consume(normalize(roomHits.single().name).toSet())
            return CategoryMatch(roomHits.single().id, null)
        }

        var bestHint: HistoryHint? = null
        var bestShared = 0
        for (hint in context.history) {
            val shared = normalize(hint.note).count { it.length >= MIN_CONTENT_WORD && it !in STOPWORDS && it in content }
            if (shared > bestShared) {
                bestShared = shared
                bestHint = hint
            }
        }
        return bestHint?.let { CategoryMatch(it.roomId, it.categoryId) }
    }

    // ------------------------------------------------------------------------------ catatan

    /** Kata sisa menjadi catatan, dengan huruf aslinya; kata sambung di ujung dibuang. */
    private fun noteOf(tokens: List<Token>): String {
        val rest = tokens.filter { !it.used }.map { it.raw.trim { c -> c in EDGE_PUNCTUATION } }.filter { it.isNotEmpty() }.toMutableList()
        while (rest.isNotEmpty() && rest.first().lowercase() in LEADING_CONNECTORS) rest.removeAt(0)
        while (rest.isNotEmpty() && rest.last().lowercase() in TRAILING_CONNECTORS) rest.removeAt(rest.size - 1)
        return rest.joinToString(" ")
    }

    // ------------------------------------------------------------------------------ kamus

    private val WHITESPACE = Regex("\\s+")
    private val AMOUNT_TOKEN = Regex("^(\\d+(?:[.,]\\d+)*)(juta|jt|ribu|rb|k)?$")
    private val THOUSANDS = Regex("^\\d{1,3}(?:[.,]\\d{3})+$")
    private val NUMERIC_DATE = Regex("^(\\d{1,2})[/-](\\d{1,2})(?:[/-](\\d{2}|\\d{4}))?$")
    private const val EDGE_PUNCTUATION = ",;:!?()\"'"
    private const val MAX_DIGITS = 13
    private const val MAX_DAYS_BACK = 60
    private const val SMALL_LIMIT = 1_000L
    private const val MIN_ACCOUNT_WORD = 3
    private const val MIN_CONTENT_WORD = 3

    private val UNITS = setOf("juta", "jt", "ribu", "rb", "k")
    private val YESTERDAY = setOf("kemarin", "kemaren", "kmrn", "semalam")
    private val YESTERDAY_BEFORE = setOf("kemarin", "kemaren")
    private val DAYPARTS = setOf("pagi", "siang", "sore")
    private val FROM_PREPOSITIONS = setOf("dari", "pakai", "pake", "pk", "via", "lewat", "dengan", "dgn", "memakai", "menggunakan")
    private val CASH_WORDS = setOf("tunai", "cash", "kas")

    private val EXPENSE_VERBS = setOf("beli", "bayar", "byr", "bayarin", "jajan", "beliin")
    private val RECEIVE_VERBS = setOf("terima", "diterima", "dapat", "dapet", "cair", "dikasih")
    private val INCOME_VERBS = RECEIVE_VERBS
    private val INCOME_NOUNS = setOf("gaji", "gajian", "bonus", "thr", "upah", "honor", "komisi", "cashback", "refund", "dividen", "pemasukan", "income")
    private val BUSINESS_WORDS = setOf("usaha", "jualan", "dagang", "omzet", "laba", "untung", "penjualan")
    private val NOT_INCOME_BEFORE_MASUK = setOf("tiket", "karcis", "biaya", "tarif", "pintu", "tol", "parkir")
    private val TRANSFER_WORDS = setOf("transfer", "tf", "trf", "pindah", "pindahkan", "tarik", "setor")
    private val TRANSFER_TWO_ACCOUNTS = setOf("transfer", "tf", "trf")

    private val LEADING_CONNECTORS = setOf("untuk", "buat", "utk", "di", "ke", "dari", "pakai", "pake", "via", "lewat", "dengan", "dgn", "dan", "sama", "sebesar", "senilai", "rp", "sebanyak")
    private val TRAILING_CONNECTORS = LEADING_CONNECTORS
    private val STOPWORDS = setOf("dan", "dari", "untuk", "yang", "buat", "utk", "dengan", "dgn", "ini", "itu", "aku", "saya", "lagi", "sama", "beli", "bayar", "tadi")
}

/** Membangun [SmartInputContext] dari pilihan Catat ditambah riwayat pengeluaran 90 hari terakhir. */
class SmartInputService(private val transactions: TransactionRepository) {

    suspend fun contextFor(catat: CatatContext, today: LocalDate): SmartInputContext {
        val history = transactions.between(today.minusDays(HISTORY_DAYS), today)
            .filter { it.kind == TransactionKind.EXPENSE && !it.note.isNullOrBlank() && it.roomId != null && it.categoryId != null }
            .map { HistoryHint(it.note.orEmpty(), it.roomId!!, it.categoryId!!) }
        return SmartInputContext(catat.accounts, catat.rooms, catat.categories, history, today)
    }

    /** Uraian [text] langsung menjadi isian Catat; null bila belum ada nominal yang terbaca. */
    suspend fun draftFor(text: String, catat: CatatContext, today: LocalDate): Pair<ParsedEntry, CatatDraft>? {
        val entry = SmartInputParser.parse(text, contextFor(catat, today)) ?: return null
        return entry to SmartInputParser.toDraft(entry, catat, today)
    }

    private companion object {
        const val HISTORY_DAYS = 90L
    }
}
