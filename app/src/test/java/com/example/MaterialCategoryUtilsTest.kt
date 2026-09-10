package com.example

import com.example.data.models.MaterialEntity
import com.example.ui.utils.MaterialCategoryUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MaterialCategoryUtilsTest {

    @Test
    fun testCategoryMapping() {
        val cable = MaterialEntity(name = "Cable 1.5mm Twin & Earth", unit = "Roll", price = 180000.0, category = "Waya & Kebo (Cables)")
        val switch = MaterialEntity(name = "Socket 13A Single Switch Socket", unit = "Pcs", price = 6500.0, category = "Swichi & Soketi (Switches)")
        val pipe = MaterialEntity(name = "PPR Pipe 20mm (PN20)", unit = "Pcs", price = 12000.0, category = "Plumbing")
        val cement = MaterialEntity(name = "Cement Simba 42.5N (50kg Bag)", unit = "Bag", price = 19500.0, category = "Construction")

        assertEquals("Electrical", MaterialCategoryUtils.getCanonicalCategory(cable.category, cable.name))
        assertEquals("Electrical", MaterialCategoryUtils.getCanonicalCategory(switch.category, switch.name))
        assertEquals("Plumbing", MaterialCategoryUtils.getCanonicalCategory(pipe.category, pipe.name))
        assertEquals("Construction", MaterialCategoryUtils.getCanonicalCategory(cement.category, cement.name))
    }

    @Test
    fun testMatchesCategory() {
        val electrical = MaterialEntity(name = "Circuit Breaker MCB 10A", unit = "Pcs", price = 8500.0, category = "Electrical")
        val plumbing = MaterialEntity(name = "Water Meter 1/2\" Brass Single Jet", unit = "Pcs", price = 45000.0, category = "Plumbing")
        val construction = MaterialEntity(name = "Concrete Blocks 5\" Solid", unit = "Pcs", price = 1200.0, category = "Construction")

        // All category matches everything
        assertTrue(MaterialCategoryUtils.matchesCategory(electrical, "All"))
        assertTrue(MaterialCategoryUtils.matchesCategory(plumbing, "All"))
        assertTrue(MaterialCategoryUtils.matchesCategory(construction, "All"))

        // Specific category checks
        assertTrue(MaterialCategoryUtils.matchesCategory(electrical, "Electrical"))
        assertFalse(MaterialCategoryUtils.matchesCategory(electrical, "Plumbing"))
        assertFalse(MaterialCategoryUtils.matchesCategory(electrical, "Construction"))

        assertTrue(MaterialCategoryUtils.matchesCategory(plumbing, "Plumbing"))
        assertFalse(MaterialCategoryUtils.matchesCategory(plumbing, "Electrical"))

        assertTrue(MaterialCategoryUtils.matchesCategory(construction, "Construction"))
        assertFalse(MaterialCategoryUtils.matchesCategory(construction, "Electrical"))
    }
}
