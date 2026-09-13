package com.example.ui.utils

import com.example.data.models.MaterialEntity

object MaterialCategoryUtils {
    const val ALL = "All"
    const val ELECTRICAL = "Electrical"
    const val PLUMBING = "Plumbing"
    const val CONSTRUCTION = "Construction"

    val DEFAULT_CATEGORIES = listOf(ELECTRICAL, PLUMBING, CONSTRUCTION)
    val CATEGORIES = listOf(ALL, ELECTRICAL, PLUMBING, CONSTRUCTION)
    val ITEM_CATEGORIES = listOf(ELECTRICAL, PLUMBING, CONSTRUCTION)

    /**
     * Determines and formats the material category.
     * Supports standard categories (Electrical, Plumbing, Construction)
     * as well as custom user-defined categories (e.g. Solar, CCTV, Kilimo, Hardware, etc.).
     */
    fun getCanonicalCategory(category: String?, name: String? = ""): String {
        val cat = (category ?: "").trim()
        val catLower = cat.lowercase()
        val n = (name ?: "").trim().lowercase()

        // 1. Direct standard equality matches
        if (cat.equals(PLUMBING, ignoreCase = true)) return PLUMBING
        if (cat.equals(CONSTRUCTION, ignoreCase = true)) return CONSTRUCTION
        if (cat.equals(ELECTRICAL, ignoreCase = true)) return ELECTRICAL

        // 2. Swahili or standard alias matches
        if (catLower == "mabomba ya maji" || catLower == "mabomba" || catLower == "bomba" || catLower == "bomba la maji") {
            return PLUMBING
        }
        if (catLower == "ujenzi" || catLower == "jengo" || catLower == "majengo" || catLower == "building") {
            return CONSTRUCTION
        }
        if (catLower == "umeme" || catLower == "vifaa vya umeme" || catLower == "electric" || catLower == "electricity") {
            return ELECTRICAL
        }

        // 3. If explicit custom category is provided, preserve and format it
        if (cat.isNotBlank()) {
            return formatCategoryName(cat)
        }

        // 4. If category was empty, infer from material name keywords
        if (n.contains("ppr") || n.contains("gate valve") || n.contains("water pump") ||
            n.contains("water tank") || n.contains("simtank") || n.contains("sink") ||
            n.contains("choo") || n.contains("bomba la maji") || n.contains("bibcock") ||
            n.contains("water tap") || n.contains("faucet") || n.contains("stop cock") ||
            n.contains("waste pipe") || n.contains("soil pipe") || n.contains("solvent cement") ||
            n.contains("tangit") || n.contains("teflon") || n.contains("mixer tap") ||
            n.contains("water meter") || n.contains("ball valve") || n.contains("hose pipe") ||
            n.contains("drainage") || n.contains("shower") || n.contains("cistern") ||
            n.contains("ballcock")
        ) {
            return PLUMBING
        }

        if (n.contains("cement") || n.contains("simba") || n.contains("twiga") ||
            n.contains("mchanga") || n.contains("sand") || n.contains("kokoto") ||
            n.contains("gravel") || n.contains("aggregate") || n.contains("nondo") ||
            n.contains("rebar") || n.contains("tmt") || n.contains("mbao") ||
            n.contains("timber") || n.contains("bati") || n.contains("iron sheet") ||
            n.contains("resincot") || n.contains("tofali") || n.contains("matofali") ||
            n.contains("block") || n.contains("misumari") || n.contains("nail") ||
            n.contains("binding wire") || n.contains("dpm") || n.contains("polythene") ||
            n.contains("gypsum") || n.contains("plaster") || n.contains("brc mesh") ||
            n.contains("mesh") || n.contains("paint") || n.contains("rangi") ||
            n.contains("plywood")
        ) {
            return CONSTRUCTION
        }

        if (n.contains("waya") || n.contains("cable") || n.contains("wire") ||
            n.contains("swichi") || n.contains("switch") || n.contains("soketi") ||
            n.contains("socket") || n.contains("breaker") || n.contains("mcb") ||
            n.contains("taa") || n.contains("bulb") || n.contains("conduit") ||
            n.contains("distribution") || n.contains("db") || n.contains("solar") ||
            n.contains("inverter") || n.contains("battery") || n.contains("led")
        ) {
            return ELECTRICAL
        }

        // Default fallback for items with no category and no recognized keywords
        return ELECTRICAL
    }

    /**
     * Formats category name with title-case.
     */
    fun formatCategoryName(category: String): String {
        val trimmed = category.trim()
        if (trimmed.isBlank()) return ELECTRICAL
        return trimmed.split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.ROOT) else it.toString() }
            }
    }

    /**
     * Checks if a material matches the selected filter category.
     */
    fun matchesCategory(material: MaterialEntity, selectedCategory: String): Boolean {
        if (selectedCategory == ALL || selectedCategory.equals("All", ignoreCase = true) || selectedCategory.equals("Zote", ignoreCase = true)) {
            return true
        }
        val canonical = getCanonicalCategory(material.category, material.name)
        return canonical.equals(selectedCategory, ignoreCase = true) || material.category.trim().equals(selectedCategory.trim(), ignoreCase = true)
    }

    /**
     * Extracts all unique categories from the material list, preserving standard ones first.
     */
    fun extractDistinctCategories(materials: List<MaterialEntity>, allLabel: String = "All"): List<String> {
        val defaultList = listOf(ELECTRICAL, PLUMBING, CONSTRUCTION)
        val customList = materials.map { getCanonicalCategory(it.category, it.name) }
            .distinct()
            .filter { it !in defaultList && it.isNotBlank() }
            .sorted()
        return listOf(allLabel) + defaultList + customList
    }
}

object MaterialKeyUtils {
    fun normalize(str: String?): String =
        (str ?: "").trim().lowercase(java.util.Locale.ROOT).replace(Regex("\\s+"), " ")

    fun getNaturalKey(name: String, category: String, unit: String): String {
        val n = normalize(name)
        val c = normalize(MaterialCategoryUtils.getCanonicalCategory(category, name))
        val u = normalize(unit)
        return "$c|$n|$u"
    }

    fun getNaturalKey(material: MaterialEntity): String {
        return getNaturalKey(material.name, material.category, material.unit)
    }
}
