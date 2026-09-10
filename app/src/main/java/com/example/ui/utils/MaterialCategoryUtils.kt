package com.example.ui.utils

import com.example.data.models.MaterialEntity

object MaterialCategoryUtils {
    const val ALL = "All"
    const val ELECTRICAL = "Electrical"
    const val PLUMBING = "Plumbing"
    const val CONSTRUCTION = "Construction"

    val CATEGORIES = listOf(ALL, ELECTRICAL, PLUMBING, CONSTRUCTION)
    val ITEM_CATEGORIES = listOf(ELECTRICAL, PLUMBING, CONSTRUCTION)

    /**
     * Determines the canonical category (Electrical, Plumbing, Construction)
     * based on the stored category and material name.
     */
    fun getCanonicalCategory(category: String?, name: String? = ""): String {
        val cat = (category ?: "").trim()
        val catLower = cat.lowercase()
        val n = (name ?: "").trim().lowercase()

        // 1. Direct equality matches
        if (cat.equals(PLUMBING, ignoreCase = true)) return PLUMBING
        if (cat.equals(CONSTRUCTION, ignoreCase = true)) return CONSTRUCTION
        if (cat.equals(ELECTRICAL, ignoreCase = true)) return ELECTRICAL

        // 2. Swahili or partial category matches
        if (catLower.contains("plumbing") || catLower.contains("mabomba ya maji") || catLower.contains("bomba la maji")) {
            return PLUMBING
        }
        if (catLower.contains("construction") || catLower.contains("ujenzi") || catLower.contains("jengo")) {
            return CONSTRUCTION
        }
        if (catLower.contains("electrical") || catLower.contains("umeme") || catLower.contains("solar") ||
            catLower.contains("waya") || catLower.contains("kebo") || catLower.contains("swichi") ||
            catLower.contains("soketi") || catLower.contains("breaker") || catLower.contains("mcb") ||
            catLower.contains("distribution") || catLower.contains("db") || catLower.contains("taa") ||
            catLower.contains("led") || catLower.contains("lighting")
        ) {
            return ELECTRICAL
        }

        // 3. Name-based inspection for Plumbing
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

        // 4. Name-based inspection for Construction
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

        // 5. Default fallback
        return ELECTRICAL
    }

    /**
     * Checks if a material matches the selected filter category.
     */
    fun matchesCategory(material: MaterialEntity, selectedCategory: String): Boolean {
        if (selectedCategory == ALL || selectedCategory.equals("All", ignoreCase = true) || selectedCategory.equals("Zote", ignoreCase = true)) {
            return true
        }
        val canonical = getCanonicalCategory(material.category, material.name)
        return canonical.equals(selectedCategory, ignoreCase = true)
    }
}
