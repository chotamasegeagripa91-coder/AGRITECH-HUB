package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.models.CustomerEntity
import com.example.data.models.MaterialEntity
import com.example.data.models.QuoteEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [MaterialEntity::class, CustomerEntity::class, QuoteEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun materialDao(): MaterialDao
    abstract fun customerDao(): CustomerDao
    abstract fun quoteDao(): QuoteDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "agritech_hub_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance

                scope.launch(Dispatchers.IO) {
                    seedDefaultDataIfEmpty(
                        instance.materialDao(),
                        instance.customerDao(),
                        instance.quoteDao()
                    )
                }

                instance
            }
        }

        suspend fun seedDefaultDataIfEmpty(
            materialDao: MaterialDao,
            customerDao: CustomerDao,
            quoteDao: QuoteDao
        ) {
            materialDao.normalizeLegacyCategories()
            if (materialDao.getCountByCategory("Plumbing") == 0) {
                materialDao.insertAll(defaultPlumbingMaterials)
            }
            if (materialDao.getCountByCategory("Construction") == 0) {
                materialDao.insertAll(defaultConstructionMaterials)
            }
            if (materialDao.getDemoCount() == 0 || materialDao.getCount() == 0) {
                populateMaterials(materialDao)
            }
            if (customerDao.getDemoCount() == 0) {
                populateCustomers(customerDao)
            }
            if (quoteDao.getDemoCount() == 0) {
                populateQuotes(quoteDao)
            }
        }

        suspend fun populateDefaultData(
            materialDao: MaterialDao,
            customerDao: CustomerDao,
            quoteDao: QuoteDao
        ) {
            populateMaterials(materialDao)
            populateCustomers(customerDao)
            populateQuotes(quoteDao)
        }

        private suspend fun populateMaterials(materialDao: MaterialDao) {
            val allDefault = defaultElectricalMaterials + defaultPlumbingMaterials + defaultConstructionMaterials
            materialDao.insertAll(allDefault)
        }

        val defaultElectricalMaterials = listOf(
            MaterialEntity(name = "Cable 1.5mm Twin & Earth (Flat)", unit = "Roll", price = 180000.0, category = "Electrical"),
            MaterialEntity(name = "Cable 2.5mm Twin & Earth (Flat)", unit = "Roll", price = 280000.0, category = "Electrical"),
            MaterialEntity(name = "Cable 4.0mm Single Core (Red)", unit = "Roll", price = 125000.0, category = "Electrical"),
            MaterialEntity(name = "Cable 4.0mm Single Core (Black)", unit = "Roll", price = 125000.0, category = "Electrical"),
            MaterialEntity(name = "Cable 6.0mm Single Core (Red/Black)", unit = "Roll", price = 175000.0, category = "Electrical"),
            MaterialEntity(name = "Cable 10.0mm Single Core", unit = "Roll", price = 290000.0, category = "Electrical"),
            MaterialEntity(name = "Cable 16.0mm Armoured Cable 4-Core", unit = "Meter", price = 38000.0, category = "Electrical"),
            MaterialEntity(name = "Cable 25.0mm Armoured Cable 4-Core", unit = "Meter", price = 55000.0, category = "Electrical"),
            MaterialEntity(name = "Flexible Cable 1.5mm 3-Core", unit = "Roll", price = 145000.0, category = "Electrical"),
            MaterialEntity(name = "Flexible Cable 2.5mm 3-Core", unit = "Roll", price = 210000.0, category = "Electrical"),
            MaterialEntity(name = "Drop Wire 4.0mm (Tanesco Hook)", unit = "Meter", price = 3500.0, category = "Electrical"),
            MaterialEntity(name = "Main Switch 63A Double Pole", unit = "Pcs", price = 35000.0, category = "Electrical"),
            MaterialEntity(name = "Main Switch 100A Triple Pole (3-Phase)", unit = "Pcs", price = 95000.0, category = "Electrical"),
            MaterialEntity(name = "Consumer Unit DB 4-Way Surface", unit = "Pcs", price = 32000.0, category = "Electrical"),
            MaterialEntity(name = "Consumer Unit DB 8-Way Surface", unit = "Pcs", price = 45000.0, category = "Electrical"),
            MaterialEntity(name = "Consumer Unit DB 12-Way Flush", unit = "Pcs", price = 65000.0, category = "Electrical"),
            MaterialEntity(name = "Consumer Unit DB 18-Way Flush", unit = "Pcs", price = 95000.0, category = "Electrical"),
            MaterialEntity(name = "3-Phase DB 12-Way Heavy Duty", unit = "Pcs", price = 220000.0, category = "Electrical"),
            MaterialEntity(name = "Circuit Breaker MCB 10A (Lighting)", unit = "Pcs", price = 8500.0, category = "Electrical"),
            MaterialEntity(name = "Circuit Breaker MCB 20A / 32A (Sockets)", unit = "Pcs", price = 8500.0, category = "Electrical"),
            MaterialEntity(name = "Circuit Breaker MCB 45A / 63A Single Pole", unit = "Pcs", price = 12000.0, category = "Electrical"),
            MaterialEntity(name = "3-Phase MCB Breaker 63A TP", unit = "Pcs", price = 45000.0, category = "Electrical"),
            MaterialEntity(name = "Residual Current Device RCD 63A 30mA 2P", unit = "Pcs", price = 55000.0, category = "Electrical"),
            MaterialEntity(name = "Residual Current Device RCD 63A 30mA 4P", unit = "Pcs", price = 85000.0, category = "Electrical"),
            MaterialEntity(name = "Changeover Switch 63A Manual 2P", unit = "Pcs", price = 65000.0, category = "Electrical"),
            MaterialEntity(name = "Automatic Changeover Switch (ATS) 63A", unit = "Pcs", price = 185000.0, category = "Electrical"),
            MaterialEntity(name = "Socket 13A Single Switch Socket", unit = "Pcs", price = 6500.0, category = "Electrical"),
            MaterialEntity(name = "Socket 13A Twin Double Switch Socket", unit = "Pcs", price = 12000.0, category = "Electrical"),
            MaterialEntity(name = "Socket 15A Heavy Duty Single Socket", unit = "Pcs", price = 9500.0, category = "Electrical"),
            MaterialEntity(name = "1 Gang 1 Way Light Switch", unit = "Pcs", price = 4500.0, category = "Electrical"),
            MaterialEntity(name = "2 Gang 1 Way Light Switch", unit = "Pcs", price = 6500.0, category = "Electrical"),
            MaterialEntity(name = "3 Gang 1 Way Light Switch", unit = "Pcs", price = 8500.0, category = "Electrical"),
            MaterialEntity(name = "4 Gang 1 Way Light Switch", unit = "Pcs", price = 11000.0, category = "Electrical"),
            MaterialEntity(name = "1 Gang 2 Way Light Switch (Staircase)", unit = "Pcs", price = 5500.0, category = "Electrical"),
            MaterialEntity(name = "Cooker Control Unit 45A with Neon", unit = "Pcs", price = 28000.0, category = "Electrical"),
            MaterialEntity(name = "Water Heater Switch 20A DP", unit = "Pcs", price = 14000.0, category = "Electrical"),
            MaterialEntity(name = "AC Switch 30A with Indicator", unit = "Pcs", price = 18000.0, category = "Electrical"),
            MaterialEntity(name = "LED Ceiling Panel 18W Round Warm/White", unit = "Pcs", price = 15000.0, category = "Electrical"),
            MaterialEntity(name = "LED Ceiling Panel 24W Square White", unit = "Pcs", price = 22000.0, category = "Electrical"),
            MaterialEntity(name = "LED Downlight 7W / 12W Spot", unit = "Pcs", price = 10000.0, category = "Electrical"),
            MaterialEntity(name = "LED Tube Fitting 4ft Single 18W", unit = "Pcs", price = 14000.0, category = "Electrical"),
            MaterialEntity(name = "LED Tube Fitting 4ft Double 36W", unit = "Pcs", price = 24000.0, category = "Electrical"),
            MaterialEntity(name = "LED Floodlight 50W IP65 Outdoor", unit = "Pcs", price = 48000.0, category = "Electrical"),
            MaterialEntity(name = "LED Floodlight 100W IP65 Outdoor", unit = "Pcs", price = 85000.0, category = "Electrical"),
            MaterialEntity(name = "Bulkhead Fitting IP65 (Gate Light)", unit = "Pcs", price = 18000.0, category = "Electrical"),
            MaterialEntity(name = "Conduit Pipe 20mm PVC Heavy Duty", unit = "Pcs", price = 4500.0, category = "Electrical"),
            MaterialEntity(name = "Conduit Pipe 25mm PVC Heavy Duty", unit = "Pcs", price = 6500.0, category = "Electrical"),
            MaterialEntity(name = "Flexible Conduit Pipe 20mm (50m Roll)", unit = "Roll", price = 35000.0, category = "Electrical"),
            MaterialEntity(name = "Trunking PVC 20x10mm", unit = "Pcs", price = 3500.0, category = "Electrical"),
            MaterialEntity(name = "Trunking PVC 40x25mm", unit = "Pcs", price = 7500.0, category = "Electrical"),
            MaterialEntity(name = "PVC Pattress Box Single 3x3", unit = "Pcs", price = 1200.0, category = "Electrical"),
            MaterialEntity(name = "PVC Pattress Box Twin 3x6", unit = "Pcs", price = 2000.0, category = "Electrical"),
            MaterialEntity(name = "Metal Flush Box Single 3x3", unit = "Pcs", price = 2500.0, category = "Electrical"),
            MaterialEntity(name = "PVC Bends / Couplers 20mm", unit = "Pkt", price = 12000.0, category = "Electrical"),
            MaterialEntity(name = "Insulation Tape 3M Premium", unit = "Roll", price = 2500.0, category = "Electrical"),
            MaterialEntity(name = "Earth Rod 5ft Copper Clad with Clamp", unit = "Set", price = 35000.0, category = "Electrical"),
            MaterialEntity(name = "Solar Inverter 3.5kVA 24V Pure Sine", unit = "Pcs", price = 1250000.0, category = "Electrical"),
            MaterialEntity(name = "Solar Panel 400W Mono-Crystalline", unit = "Pcs", price = 320000.0, category = "Electrical"),
            MaterialEntity(name = "Lithium LiFePO4 Battery 24V 100Ah", unit = "Pcs", price = 1850000.0, category = "Electrical")
        )

        val defaultPlumbingMaterials = listOf(
            MaterialEntity(name = "PPR Pipe 20mm (PN20) 4m Hot/Cold", unit = "Pcs", price = 12000.0, category = "Plumbing"),
            MaterialEntity(name = "PPR Pipe 25mm (PN20) 4m Hot/Cold", unit = "Pcs", price = 18000.0, category = "Plumbing"),
            MaterialEntity(name = "PPR Pipe 32mm (PN20) 4m", unit = "Pcs", price = 28000.0, category = "Plumbing"),
            MaterialEntity(name = "PVC Waste Pipe 1.5\" (3m Class B)", unit = "Pcs", price = 8500.0, category = "Plumbing"),
            MaterialEntity(name = "PVC Waste Pipe 2\" (3m Class B)", unit = "Pcs", price = 12000.0, category = "Plumbing"),
            MaterialEntity(name = "PVC Soil & Waste Pipe 4\" (Class B)", unit = "Pcs", price = 26000.0, category = "Plumbing"),
            MaterialEntity(name = "Brass Gate Valve 3/4\" (Pegler Heavy)", unit = "Pcs", price = 22000.0, category = "Plumbing"),
            MaterialEntity(name = "Brass Gate Valve 1\" (Pegler Heavy)", unit = "Pcs", price = 32000.0, category = "Plumbing"),
            MaterialEntity(name = "Water Meter 1/2\" Brass Single Jet", unit = "Pcs", price = 45000.0, category = "Plumbing"),
            MaterialEntity(name = "PPR Equal Elbow 20mm (90 Degree)", unit = "Pcs", price = 800.0, category = "Plumbing"),
            MaterialEntity(name = "PPR Equal Tee 20mm", unit = "Pcs", price = 1200.0, category = "Plumbing"),
            MaterialEntity(name = "PPR Female Socket 20mm x 1/2\"", unit = "Pcs", price = 2500.0, category = "Plumbing"),
            MaterialEntity(name = "Water Tap Bibcock 1/2\" Brass", unit = "Pcs", price = 8500.0, category = "Plumbing"),
            MaterialEntity(name = "Kitchen Sink Mixer Tap Chrome", unit = "Pcs", price = 48000.0, category = "Plumbing"),
            MaterialEntity(name = "Flexible Hose Pipe 1/2\" x 1/2\" (45cm)", unit = "Pcs", price = 5500.0, category = "Plumbing"),
            MaterialEntity(name = "Water Tank Simtank 1000L Cylindrical", unit = "Pcs", price = 260000.0, category = "Plumbing"),
            MaterialEntity(name = "Water Tank Simtank 2000L Heavy Duty", unit = "Pcs", price = 480000.0, category = "Plumbing"),
            MaterialEntity(name = "PVC Solvent Cement Glue Tangit 500ml", unit = "Tin", price = 18000.0, category = "Plumbing"),
            MaterialEntity(name = "Teflon Thread Seal Tape 12mm x 10m", unit = "Roll", price = 1500.0, category = "Plumbing"),
            MaterialEntity(name = "Shower Head with Arm Stainless Steel", unit = "Set", price = 25000.0, category = "Plumbing"),
            MaterialEntity(name = "Toilet Cistern Float Valve Ballcock 1/2\"", unit = "Set", price = 14000.0, category = "Plumbing")
        )

        val defaultConstructionMaterials = listOf(
            MaterialEntity(name = "Cement Simba / Twiga 42.5N (50kg Bag)", unit = "Bag", price = 19500.0, category = "Construction"),
            MaterialEntity(name = "White Cement (50kg Bag)", unit = "Bag", price = 38000.0, category = "Construction"),
            MaterialEntity(name = "Reinforcement Bar (Nondo) 10mm TMT (12m)", unit = "Pcs", price = 18500.0, category = "Construction"),
            MaterialEntity(name = "Reinforcement Bar (Nondo) 12mm TMT (12m)", unit = "Pcs", price = 26500.0, category = "Construction"),
            MaterialEntity(name = "Reinforcement Bar (Nondo) 16mm TMT (12m)", unit = "Pcs", price = 48000.0, category = "Construction"),
            MaterialEntity(name = "Plaster Sand (Mchanga wa Plasta)", unit = "Trip", price = 180000.0, category = "Construction"),
            MaterialEntity(name = "River Sand (Mchanga wa Mto kwa Zege)", unit = "Trip", price = 220000.0, category = "Construction"),
            MaterialEntity(name = "Gravel / Aggregate (Kokoto 3/4\")", unit = "Trip", price = 260000.0, category = "Construction"),
            MaterialEntity(name = "Concrete Blocks 5\" Solid (Matofali ya Zege)", unit = "Pcs", price = 1200.0, category = "Construction"),
            MaterialEntity(name = "Concrete Blocks 6\" Hollow (Matofali ya Matundu)", unit = "Pcs", price = 1400.0, category = "Construction"),
            MaterialEntity(name = "Timber Mbao 2x4 Treated Pine (12ft)", unit = "Pcs", price = 9500.0, category = "Construction"),
            MaterialEntity(name = "Timber Mbao 2x6 Treated Pine (12ft)", unit = "Pcs", price = 14000.0, category = "Construction"),
            MaterialEntity(name = "Plywood Sheet 8x4 (12mm Marine)", unit = "Pcs", price = 42000.0, category = "Construction"),
            MaterialEntity(name = "Corrugated Iron Sheet (Bati Gauge 28 3m)", unit = "Pcs", price = 28000.0, category = "Construction"),
            MaterialEntity(name = "Resincot Color Roofing Sheet 3m", unit = "Pcs", price = 36000.0, category = "Construction"),
            MaterialEntity(name = "Binding Wire (Waya wa Kufungia Nondo 25kg)", unit = "Roll", price = 65000.0, category = "Construction"),
            MaterialEntity(name = "Wire Nails 3\" / 4\" (Misumari ya Mbao 1kg)", unit = "Kg", price = 3500.0, category = "Construction"),
            MaterialEntity(name = "Roofing Nails with Rubber Washer (1kg)", unit = "Kg", price = 4500.0, category = "Construction"),
            MaterialEntity(name = "DPM Polythene Waterproof Membrane (30m)", unit = "Roll", price = 55000.0, category = "Construction"),
            MaterialEntity(name = "BRC Mesh Reinforcement A142 (Roll)", unit = "Roll", price = 140000.0, category = "Construction")
        )

        private suspend fun populateCustomers(customerDao: CustomerDao) {
            val defaultCustomers = listOf(
                CustomerEntity(name = "[DEMO / SAMPLE] Mhandisi Juma Rashid", phone = "+255 712 345 678", location = "Mikocheni, Dar es Salaam", notes = "[DEMO / SAMPLE] Mradi wa jengo la ghorofa 2"),
                CustomerEntity(name = "[DEMO / SAMPLE] Bi. Amina Said", phone = "+255 754 987 654", location = "Kijitonyama, Dar es Salaam", notes = "[DEMO / SAMPLE] Ukarabati wa nyumba ya makazi"),
                CustomerEntity(name = "[DEMO / SAMPLE] Kampuni ya Mlimani Estates", phone = "+255 689 112 233", location = "Mbezi Beach, Dar es Salaam", notes = "[DEMO / SAMPLE] Ufungaji wa mifumo ya solar na umeme"),
                CustomerEntity(name = "[DEMO / SAMPLE] Mwalimu Hassan Ally", phone = "+255 767 889 900", location = "Sinza Kumekucha, Dar es Salaam", notes = "[DEMO / SAMPLE] Ufungaji wa umeme kwenye duka la biashara")
            )
            customerDao.insertAll(defaultCustomers)
        }

        private suspend fun populateQuotes(quoteDao: QuoteDao) {
            val sampleQuotes = listOf(
                // 3 Sample Quotations
                QuoteEntity(
                    number = "DEMO-QUO-001",
                    date = "2026-09-01",
                    validUntil = "2026-09-15",
                    customerName = "[DEMO / SAMPLE] Mhandisi Juma Rashid",
                    customerPhone = "+255 712 345 678",
                    customerLocation = "Mikocheni, Dar es Salaam",
                    description = "[DEMO / SAMPLE] Ufungaji wa mifumo ya umeme nyumba ya ghorofa moja",
                    itemsJson = """[{"id":"1","name":"Cable 1.5mm Twin & Earth (Flat)","unit":"Roll","price":180000.0,"quantity":2.0,"total":360000.0},{"id":"2","name":"Cable 2.5mm Twin & Earth (Flat)","unit":"Roll","price":280000.0,"quantity":3.0,"total":840000.0},{"id":"3","name":"Consumer Unit DB 12-Way Flush","unit":"Pcs","price":65000.0,"quantity":1.0,"total":65000.0},{"id":"4","name":"Circuit Breaker MCB 10A (Lighting)","unit":"Pcs","price":8500.0,"quantity":6.0,"total":51000.0},{"id":"5","name":"Circuit Breaker MCB 20A / 32A (Sockets)","unit":"Pcs","price":8500.0,"quantity":6.0,"total":51000.0},{"id":"6","name":"Socket 13A Twin Double Switch Socket","unit":"Pcs","price":12000.0,"quantity":12.0,"total":144000.0},{"id":"7","name":"2 Gang 1 Way Switch","unit":"Pcs","price":6500.0,"quantity":8.0,"total":52000.0},{"id":"8","name":"LED Ceiling Panel 18W Round Warm/White","unit":"Pcs","price":15000.0,"quantity":16.0,"total":240000.0}]""",
                    materialsTotal = 1803000.0,
                    labour = 450000.0,
                    grandTotal = 2253000.0,
                    status = "quotation",
                    paid = false,
                    createdAt = System.currentTimeMillis() - 864000000L
                ),
                QuoteEntity(
                    number = "DEMO-QUO-002",
                    date = "2026-09-05",
                    validUntil = "2026-09-19",
                    customerName = "[DEMO / SAMPLE] Bi. Amina Said",
                    customerPhone = "+255 754 987 654",
                    customerLocation = "Kijitonyama, Dar es Salaam",
                    description = "[DEMO / SAMPLE] Ukarabati wa jikoni na sebule (Rewiring & New Sockets)",
                    itemsJson = """[{"id":"1","name":"Cable 2.5mm Twin & Earth (Flat)","unit":"Roll","price":280000.0,"quantity":1.0,"total":280000.0},{"id":"2","name":"Cooker Control Unit 45A with Neon","unit":"Pcs","price":28000.0,"quantity":1.0,"total":28000.0},{"id":"3","name":"Socket 13A Twin Double Switch Socket","unit":"Pcs","price":12000.0,"quantity":6.0,"total":72000.0},{"id":"4","name":"Water Heater Switch 20A DP","unit":"Pcs","price":14000.0,"quantity":1.0,"total":14000.0},{"id":"5","name":"LED Downlight 7W / 12W Spot","unit":"Pcs","price":10000.0,"quantity":8.0,"total":80000.0}]""",
                    materialsTotal = 474000.0,
                    labour = 150000.0,
                    grandTotal = 624000.0,
                    status = "quotation",
                    paid = false,
                    createdAt = System.currentTimeMillis() - 432000000L
                ),
                QuoteEntity(
                    number = "DEMO-QUO-003",
                    date = "2026-09-08",
                    validUntil = "2026-09-22",
                    customerName = "[DEMO / SAMPLE] Kampuni ya Mlimani Estates",
                    customerPhone = "+255 689 112 233",
                    customerLocation = "Mbezi Beach, Dar es Salaam",
                    description = "[DEMO / SAMPLE] Mfumo wa Solar Backup 3.5kVA na Taa za Nje",
                    itemsJson = """[{"id":"1","name":"Solar Inverter 3.5kVA 24V Pure Sine","unit":"Pcs","price":1250000.0,"quantity":1.0,"total":1250000.0},{"id":"2","name":"Solar Panel 400W Mono-Crystalline","unit":"Pcs","price":320000.0,"quantity":4.0,"total":1280000.0},{"id":"3","name":"Lithium LiFePO4 Battery 24V 100Ah","unit":"Pcs","price":1850000.0,"quantity":1.0,"total":1850000.0},{"id":"4","name":"LED Floodlight 100W IP65 Outdoor","unit":"Pcs","price":85000.0,"quantity":2.0,"total":170000.0}]""",
                    materialsTotal = 4550000.0,
                    labour = 650000.0,
                    grandTotal = 5200000.0,
                    status = "quotation",
                    paid = false,
                    createdAt = System.currentTimeMillis() - 172800000L
                ),

                // 3 Sample Invoices (validUntil is empty, no validity on invoices)
                QuoteEntity(
                    number = "DEMO-INV-001",
                    date = "2026-08-20",
                    validUntil = "",
                    customerName = "[DEMO / SAMPLE] Mhandisi Juma Rashid",
                    customerPhone = "+255 712 345 678",
                    customerLocation = "Mikocheni, Dar es Salaam",
                    description = "[DEMO / SAMPLE] Ankara ya malipo ya Awamu ya 1 - Mfumo wa Wiring Ghorofa ya Chini",
                    itemsJson = """[{"id":"1","name":"Cable 1.5mm Twin & Earth (Flat)","unit":"Roll","price":180000.0,"quantity":1.0,"total":180000.0},{"id":"2","name":"Cable 2.5mm Twin & Earth (Flat)","unit":"Roll","price":280000.0,"quantity":2.0,"total":560000.0},{"id":"3","name":"Consumer Unit DB 8-Way Surface","unit":"Pcs","price":45000.0,"quantity":1.0,"total":45000.0},{"id":"4","name":"Main Switch 63A Double Pole","unit":"Pcs","price":35000.0,"quantity":1.0,"total":35000.0}]""",
                    materialsTotal = 820000.0,
                    labour = 250000.0,
                    grandTotal = 1070000.0,
                    status = "invoice",
                    paid = true,
                    createdAt = System.currentTimeMillis() - 1800000000L
                ),
                QuoteEntity(
                    number = "DEMO-INV-002",
                    date = "2026-08-28",
                    validUntil = "",
                    customerName = "[DEMO / SAMPLE] Mwalimu Hassan Ally",
                    customerPhone = "+255 767 889 900",
                    customerLocation = "Sinza Kumekucha, Dar es Salaam",
                    description = "[DEMO / SAMPLE] Ankara ya Malipo ya Ufungaji Taa na Swichi za Duka",
                    itemsJson = """[{"id":"1","name":"LED Ceiling Panel 18W Round Warm/White","unit":"Pcs","price":15000.0,"quantity":10.0,"total":150000.0},{"id":"2","name":"2 Gang 1 Way Switch","unit":"Pcs","price":6500.0,"quantity":4.0,"total":26000.0},{"id":"3","name":"Socket 13A Single Switch Socket","unit":"Pcs","price":6500.0,"quantity":4.0,"total":26000.0},{"id":"4","name":"Conduit Pipe 20mm PVC Heavy Duty","unit":"Pcs","price":4500.0,"quantity":10.0,"total":45000.0}]""",
                    materialsTotal = 247000.0,
                    labour = 80000.0,
                    grandTotal = 327000.0,
                    status = "invoice",
                    paid = true,
                    createdAt = System.currentTimeMillis() - 1100000000L
                ),
                QuoteEntity(
                    number = "DEMO-INV-003",
                    date = "2026-09-02",
                    validUntil = "",
                    customerName = "[DEMO / SAMPLE] Kampuni ya Mlimani Estates",
                    customerPhone = "+255 689 112 233",
                    customerLocation = "Mbezi Beach, Dar es Salaam",
                    description = "[DEMO / SAMPLE] Ankara ya Ufungaji Taa za Nje (Floodlights) Mlimani Park",
                    itemsJson = """[{"id":"1","name":"LED Floodlight 50W IP65 Outdoor","unit":"Pcs","price":48000.0,"quantity":6.0,"total":288000.0},{"id":"2","name":"Cable 2.5mm Twin & Earth (Flat)","unit":"Roll","price":280000.0,"quantity":1.0,"total":280000.0},{"id":"3","name":"Earth Rod 5ft Copper Clad with Clamp","unit":"Set","price":35000.0,"quantity":1.0,"total":35000.0}]""",
                    materialsTotal = 603000.0,
                    labour = 150000.0,
                    grandTotal = 753000.0,
                    status = "invoice",
                    paid = false,
                    createdAt = System.currentTimeMillis() - 691200000L
                )
            )
            quoteDao.insertAll(sampleQuotes)
        }
    }
}
