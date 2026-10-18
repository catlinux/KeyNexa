package io.github.catlinux.keynexa.desktop.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.catlinux.keynexa.vault.Entry
import io.github.catlinux.keynexa.vault.FieldValue

/** The names the KDBX format gives to the fields the form shows in its own way. */
internal val STANDARD_FIELD_NAMES = setOf(Entry.TITLE, Entry.USER_NAME, Entry.PASSWORD, Entry.URL, Entry.NOTES)

/** Why the name of a custom field cannot be used. */
internal enum class FieldNameProblem { Repeated, Standard }

/** One custom field being edited. */
internal class CustomField(
    name: String,
    value: String,
    isProtected: Boolean,
) {
    var name by mutableStateOf(name)
    var value by mutableStateOf(value)
    var isProtected by mutableStateOf(isProtected)
}

/**
 * What the entry form holds right now. Secrets live here only while the form is open: nothing goes to a log, and the
 * panel clears every field when it leaves.
 */
internal class EntryForm(
    initial: Map<String, FieldValue>?,
) {
    private val initialFields: Map<String, FieldValue> = initial.orEmpty()

    var title by mutableStateOf(initialFields[Entry.TITLE]?.value.orEmpty())
    var userName by mutableStateOf(initialFields[Entry.USER_NAME]?.value.orEmpty())
    var password by mutableStateOf(initialFields[Entry.PASSWORD]?.value.orEmpty())
    var url by mutableStateOf(initialFields[Entry.URL]?.value.orEmpty())
    var notes by mutableStateOf(initialFields[Entry.NOTES]?.value.orEmpty())
    var revealed by mutableStateOf(false)
    val custom = mutableStateListOf<CustomField>()

    init {
        initialFields.forEach { (name, field) ->
            if (name !in STANDARD_FIELD_NAMES) custom += CustomField(name, field.value, field.protected)
        }
    }

    /** True while the form holds exactly what the entry already had. */
    val unchanged: Boolean get() = toFields() == initialFields

    /** True when the entry cannot be saved: no title, or a custom field name that cannot be used. */
    val blocked: Boolean get() = title.isBlank() || custom.indices.any { nameProblem(it) != null }

    /** Why the custom field at [index] cannot be saved, or null when nothing is wrong with it. */
    fun nameProblem(index: Int): FieldNameProblem? =
        fieldNameProblem(custom[index].name, custom.filterIndexed { other, _ -> other != index }.map { it.name })

    fun addCustom() {
        custom += CustomField(name = "", value = "", isProtected = false)
    }

    fun removeCustom(index: Int) {
        if (index in custom.indices) custom.removeAt(index)
    }

    /** Forgets everything the form holds, so no secret outlives it. */
    fun clear() {
        title = ""
        userName = ""
        password = ""
        url = ""
        notes = ""
        revealed = false
        custom.clear()
    }

    /** The password of this form, which the panel wipes when it leaves. */
    fun passwordText(): String = password

    /**
     * The fields to save. An empty standard field only survives when the entry already had it, protected fields keep
     * their protection, and the password is protected unless the entry said otherwise (a new one is protected).
     */
    fun toFields(): Map<String, FieldValue> {
        val fields = LinkedHashMap<String, FieldValue>()
        putStandard(fields, Entry.TITLE, title.trim(), protected = null)
        putStandard(fields, Entry.USER_NAME, userName, protected = null)
        putStandard(fields, Entry.PASSWORD, password, protected = passwordProtected)
        putStandard(fields, Entry.URL, url, protected = null)
        putStandard(fields, Entry.NOTES, notes, protected = null)
        custom.forEach { field ->
            val name = field.name.trim()
            if (name.isNotEmpty()) fields[name] = FieldValue(field.value, protected = field.isProtected)
        }
        return fields
    }

    private fun putStandard(
        fields: MutableMap<String, FieldValue>,
        name: String,
        value: String,
        protected: Boolean?,
    ) {
        if (value.isNotEmpty() || name in initialFields) {
            fields[name] = FieldValue(value, protected = protected ?: fieldProtection(name))
        }
    }

    private fun fieldProtection(name: String): Boolean = initialFields[name]?.protected ?: false

    private val passwordProtected: Boolean = initialFields[Entry.PASSWORD]?.protected ?: true
}

/**
 * Why the name [name] cannot be used for a custom field, or null when it can: an empty one is not saved at all, a
 * standard name belongs to the field the form shows on its own, and a repeated one would hide the other field.
 */
internal fun fieldNameProblem(
    name: String,
    others: List<String>,
): FieldNameProblem? {
    val trimmed = name.trim()
    return when {
        trimmed.isEmpty() -> null
        trimmed in STANDARD_FIELD_NAMES -> FieldNameProblem.Standard
        others.any { it.trim() == trimmed } -> FieldNameProblem.Repeated
        else -> null
    }
}
