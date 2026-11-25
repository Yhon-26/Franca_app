package com.grafito.finanzas.data

import com.grafito.finanzas.model.Money
import org.json.JSONArray
import org.json.JSONObject

data class BackupSnapshot(
    val accounts: List<AccountEntity>,
    val categories: List<CategoryEntity>,
    val transactions: List<TransactionEntity>,
    val favorites: List<FavoriteEntity>,
    val defaultAccountId: Long?,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun validate() {
        fun ids(values: List<Long>): Set<Long> {
            require(values.size <= 100_000 && values.all { it in 1 until Long.MAX_VALUE } && values.toSet().size == values.size)
            return values.toSet()
        }
        require(accounts.isNotEmpty() && accounts.size <= 1_000 && categories.size <= MAX_BACKUP_CATEGORIES && createdAt >= 0)
        val accountIds = ids(accounts.map { it.id })
        val categoryIds = ids(categories.map { it.id })
        ids(transactions.map { it.id }); ids(favorites.map { it.id })
        require(defaultAccountId == null || defaultAccountId in accountIds)
        accounts.forEach { require(it.name.isNotBlank() && it.name.length <= 40 && it.openingBalanceCents in 0..Money.MAX_CENTS && it.createdAt >= 0) }
        categories.forEach { require(it.name.isNotBlank() && it.name.length <= 80 && it.emoji.length <= 32 && it.type in listOf(EntryType.EXPENSE, EntryType.INCOME)) }
        val categoriesById = categories.associateBy { it.id }
        val fingerprints = transactions.flatMap { listOfNotNull(it.receiptFingerprint, it.bcpOperationFingerprint) }
        require(fingerprints.toSet().size == fingerprints.size && fingerprints.all(receiptFingerprintFormat::matches))
        transactions.forEach {
            require(it.bcpOperationFingerprint == null || it.receiptFingerprint != null)
            require(it.amountCents in 1..Money.MAX_CENTS && it.accountId in accountIds && it.occurredAt >= 0 && it.note.length <= 160)
            require(it.type in listOf(EntryType.EXPENSE, EntryType.INCOME, EntryType.TRANSFER))
            if (it.type == EntryType.TRANSFER) require(it.toAccountId in accountIds && it.toAccountId != it.accountId && it.categoryId == null)
            else require(it.toAccountId == null && (it.categoryId == null || categoriesById[it.categoryId]?.type == it.type))
        }
        favorites.forEach {
            require(it.title.isNotBlank() && it.title.length <= 35 && it.amountCents in 1..Money.MAX_CENTS)
            require(it.accountId == null || it.accountId in accountIds)
            require(it.categoryId == null || (it.categoryId in categoryIds && categoriesById[it.categoryId]?.type == EntryType.EXPENSE))
        }
    }

    fun encode(): ByteArray {
        validate()
        fun array(items: List<JSONObject>) = JSONArray().also { a -> items.forEach { a.put(it) } }
        fun obj(vararg pairs: Pair<String, Any?>) = JSONObject().also { o -> pairs.forEach { o.put(it.first, it.second ?: JSONObject.NULL) } }
        return obj("format" to 1, "createdAt" to createdAt, "defaultAccountId" to defaultAccountId,
            "accounts" to array(accounts.map { obj("id" to it.id, "name" to it.name, "opening" to it.openingBalanceCents, "createdAt" to it.createdAt) }),
            "categories" to array(categories.map { obj("id" to it.id, "name" to it.name, "emoji" to it.emoji, "type" to it.type) }),
            "transactions" to array(transactions.map { obj("id" to it.id, "type" to it.type, "amount" to it.amountCents, "account" to it.accountId,
                "destination" to it.toAccountId, "category" to it.categoryId, "note" to it.note, "occurredAt" to it.occurredAt,
                "receiptFingerprint" to it.receiptFingerprint, "bcpOperationFingerprint" to it.bcpOperationFingerprint) }),
            "favorites" to array(favorites.map { obj("id" to it.id, "title" to it.title, "amount" to it.amountCents, "category" to it.categoryId, "account" to it.accountId) })
        ).toString().toByteArray(Charsets.UTF_8)
    }

    companion object {
        fun decode(bytes: ByteArray): BackupSnapshot {
            require(bytes.size <= BackupCipher.MAX_BYTES)
            val root = JSONObject(String(bytes, Charsets.UTF_8))
            fun JSONObject.number(key: String): Long {
                val value = get(key)
                require(value is Int || value is Long) { "Importe o identificador inválido." }
                return (value as Number).toLong()
            }
            fun JSONObject.optionalNumber(key: String): Long? = if (get(key) == JSONObject.NULL) null else number(key)
            fun JSONObject.string(key: String): String = (get(key) as? String) ?: error("Texto inválido.")
            fun <T> list(key: String, decode: (JSONObject) -> T): List<T> {
                val array = root.getJSONArray(key)
                require(array.length() <= 100_000)
                return (0 until array.length()).map { decode(array.getJSONObject(it)) }
            }
            require(root.number("format") == 1L) { "Versión de copia no compatible." }
            return BackupSnapshot(
                list("accounts") { AccountEntity(it.number("id"), it.string("name"), it.number("opening"), it.number("createdAt")) },
                list("categories") { CategoryEntity(it.number("id"), it.string("name"), it.string("emoji"), it.string("type")) },
                list("transactions") { TransactionEntity(it.number("id"), it.string("type"), it.number("amount"), it.number("account"),
                    it.optionalNumber("destination"), it.optionalNumber("category"), it.string("note"), it.number("occurredAt"),
                    if (it.isNull("receiptFingerprint")) null else it.string("receiptFingerprint"),
                    if (it.isNull("bcpOperationFingerprint")) null else it.string("bcpOperationFingerprint")) },
                list("favorites") { FavoriteEntity(it.number("id"), it.string("title"), it.number("amount"), it.optionalNumber("category"), it.optionalNumber("account")) },
                root.optionalNumber("defaultAccountId"), root.number("createdAt")
            ).also { it.validate() }
        }
    }
}
