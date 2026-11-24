package com.example.toibot

import android.content.Context
import android.provider.ContactsContract

data class MatchedContact(
    val name: String,
    val phoneNumber: String
)

object ContactMatcher {

    private val cyrillicToLatinMap = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'ғ' to "g",
        'д' to "d", 'е' to "e", 'ё' to "yo", 'ж' to "j", 'з' to "z",
        'и' to "i", 'й' to "y", 'к' to "k", 'қ' to "q", 'л' to "l",
        'м' to "m", 'н' to "n", 'о' to "o", 'ў' to "o", 'п' to "p",
        'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u", 'ф' to "f",
        'х' to "x", 'ҳ' to "h", 'ц' to "ts", 'ч' to "ch", 'ш' to "sh",
        'щ' to "sh", 'ъ' to "", 'ы' to "i", 'ь' to "", 'э' to "e",
        'ю' to "yu", 'я' to "ya"
    )

    fun normalizeName(raw: String): String {
        val lower = raw.lowercase().trim()
        val sb = StringBuilder()
        for (ch in lower) {
            if (cyrillicToLatinMap.containsKey(ch)) {
                sb.append(cyrillicToLatinMap[ch])
            } else if (ch in 'a'..'z' || ch in '0'..'9') {
                sb.append(ch)
            }
        }
        return sb.toString()
            .replace("o'", "o")
            .replace("g'", "g")
            .replace("kh", "x")
            .replace("h", "x")
    }

    fun findContacts(context: Context, spokenQuery: String): List<MatchedContact> {
        val targetNorm = normalizeName(spokenQuery)
        if (targetNorm.length < 2) return emptyList()

        val results = mutableListOf<MatchedContact>()
        val seenNumbers = mutableSetOf<String>()

        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null,
            null,
            null
        )

        cursor?.use {
            val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                val displayName = it.getString(nameIdx) ?: continue
                val number = (it.getString(numIdx) ?: continue).replace("\s+".toRegex(), "")
                if (seenNumbers.contains(number)) continue

                val contactNorm = normalizeName(displayName)
                if (contactNorm == targetNorm ||
                    contactNorm.contains(targetNorm) ||
                    targetNorm.contains(contactNorm) ||
                    levenshteinDistance(contactNorm, targetNorm) <= 2
                ) {
                    seenNumbers.add(number)
                    results.add(MatchedContact(displayName, number))
                }
            }
        }
        return results
    }

    private fun levenshteinDistance(a: String, b: String): Int {
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val temp = dp[j]
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + cost)
                prev = temp
            }
        }
        return dp[b.length]
    }
}
