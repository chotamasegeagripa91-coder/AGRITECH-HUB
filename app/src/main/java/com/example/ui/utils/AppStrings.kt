package com.example.ui.utils

object AppStrings {
    fun t(key: String, lang: String): String {
        val isSw = lang == "sw"
        return when (key) {
            "app_title" -> if (isSw) "AGRITECH HUB" else "AGRITECH HUB"
            "app_subtitle" -> if (isSw) "Ubora na Usalama wa Umeme Ndio Fahari Yetu" else "Professional Electrical & Power Management"
            "nav_dashboard" -> if (isSw) "Dashibodi" else "Dashboard"
            "nav_materials" -> if (isSw) "Vifaa" else "Materials"
            "nav_customers" -> if (isSw) "Wateja" else "Customers"
            "nav_new_quote" -> if (isSw) "Makadirio Mapya" else "New Quote"
            "nav_quotes" -> if (isSw) "Ankara & Makadirio" else "Quotes & Invoices"
            "nav_settings" -> if (isSw) "Mipangilio" else "Settings"

            "trial_active" -> if (isSw) "Jaribio la Bure (Bado Siku %d)" else "Free Trial (%d Days Left)"
            "trial_expired" -> if (isSw) "Muda wa Jaribio Umekwisha!" else "Trial Period Expired!"
            "license_active" -> if (isSw) "Leseni Imethibitishwa: %s" else "Licensed: %s"
            "license_expired" -> if (isSw) "Leseni Imekwisha Muda!" else "License Expired!"
            "license_tampered" -> if (isSw) "Tarehe ya Simu Imebadilishwa!" else "Clock Tampered Detected!"
            "btn_activate_now" -> if (isSw) "Washa Leseni Kamili" else "Activate License"

            "stat_total_quotes" -> if (isSw) "Makadirio" else "Total Quotes"
            "stat_total_invoices" -> if (isSw) "Ankara Rasmi" else "Official Invoices"
            "stat_materials_count" -> if (isSw) "Aina za Vifaa" else "Material Items"
            "stat_customers_count" -> if (isSw) "Wateja Waliosajiliwa" else "Saved Customers"
            "stat_total_value" -> if (isSw) "Jumla ya Thamani" else "Total Projected Value"
            "stat_labour_charges" -> if (isSw) "Gharama za Ufundi" else "Labour Charges"
            "stat_total_project_value" -> if (isSw) "Jumla ya Miradi" else "Total Project Value"

            "quick_new_quote" -> if (isSw) "Tengeneza Makadirio Mapya" else "Create New Quote"
            "quick_add_customer" -> if (isSw) "Ongeza Mteja Mpya" else "Add New Customer"
            "quick_add_material" -> if (isSw) "Ongeza Kifaa Kipya" else "Add Material Item"
            "quick_view_invoices" -> if (isSw) "Tazama Ankara & Malipo" else "View Invoices & Payments"

            "recent_activity" -> if (isSw) "Miamala na Makadirio ya Karibuni" else "Recent Quotations & Invoices"
            "no_recent_activity" -> if (isSw) "Bado hakuna makadirio yaliyoundwa. Bonyeza kitufe hapa chini kuanza." else "No quotes created yet. Tap below to create your first quotation."

            "tab_all" -> if (isSw) "Zote" else "All"
            "tab_quotations" -> if (isSw) "Makadirio" else "Quotations"
            "tab_invoices" -> if (isSw) "Ankara" else "Invoices"
            "tab_paid" -> if (isSw) "Zilizolipwa" else "Paid"
            "tab_unpaid" -> if (isSw) "Hazijalipwa" else "Unpaid"

            "status_quotation" -> if (isSw) "MAKADIRIO" else "QUOTATION"
            "status_invoice" -> if (isSw) "ANKARA RASMI" else "INVOICE"
            "status_paid" -> if (isSw) "IMELIPWA" else "PAID"
            "status_unpaid" -> if (isSw) "HAIJALIPWA" else "UNPAID"

            "btn_save" -> if (isSw) "Hifadhi" else "Save"
            "btn_cancel" -> if (isSw) "Ghairi" else "Cancel"
            "btn_delete" -> if (isSw) "Futa" else "Delete"
            "btn_edit" -> if (isSw) "Hariri" else "Edit"
            "btn_convert_to_invoice" -> if (isSw) "Badilisha Kuwa Ankara" else "Convert to Invoice"
            "btn_mark_paid" -> if (isSw) "Weka Alama Imelipwa" else "Mark as Paid"
            "btn_mark_unpaid" -> if (isSw) "Weka Alama Haijalipwa" else "Mark as Unpaid"
            "btn_share" -> if (isSw) "Tuma / Share (WhatsApp)" else "Share (WhatsApp/SMS)"
            "btn_preview_print" -> if (isSw) "Muonekano wa Kuchapa (Print / PDF)" else "Print / PDF Preview"
            "btn_close" -> if (isSw) "Funga" else "Close"

            "lbl_customer" -> if (isSw) "Mteja" else "Customer"
            "lbl_phone" -> if (isSw) "Namba ya Simu" else "Phone Number"
            "lbl_location" -> if (isSw) "Mahali / Eneo" else "Location"
            "lbl_description" -> if (isSw) "Maelezo ya Kazi" else "Job Description"
            "lbl_materials" -> if (isSw) "Orodha ya Vifaa" else "Materials List"
            "lbl_labour" -> if (isSw) "Gharama ya Ufundi (Labour)" else "Labour Cost"
            "lbl_materials_total" -> if (isSw) "Jumla ya Vifaa" else "Materials Total"
            "lbl_grand_total" -> if (isSw) "Jumla Kuu" else "Grand Total"
            "lbl_search" -> if (isSw) "Tafuta..." else "Search..."
            "lbl_category" -> if (isSw) "Kundi (Category)" else "Category"
            "lbl_unit" -> if (isSw) "Kipimo (Unit)" else "Unit"
            "lbl_price" -> if (isSw) "Bei ya Kizio (Unit Price)" else "Unit Price"
            "lbl_quantity" -> if (isSw) "Idadi (Quantity)" else "Quantity"

            "security_lock_title" -> if (isSw) "AGRITECH HUB - Ulinzi wa Mfumo" else "AGRITECH HUB Security Lock"
            "security_lock_desc" -> if (isSw) "Weka nenosiri lako kufungua mfumo" else "Enter your password to unlock the system"
            "btn_unlock" -> if (isSw) "Fungua Mfumo" else "Unlock System"

            "btn_download_pdf" -> if (isSw) "Pakua PDF" else "Download PDF"
            "btn_share_app" -> if (isSw) "Shiriki App" else "Share App"
            "btn_share_whatsapp" -> if (isSw) "Tuma WhatsApp" else "Send WhatsApp"
            "lbl_payment_accounts" -> if (isSw) "Akaunti za Malipo" else "Payment Accounts"
            "lbl_bank_details" -> if (isSw) "Jina la Benki & Namba ya Akaunti" else "Bank Name & Account Number"
            "lbl_account_name" -> if (isSw) "Jina la Akaunti" else "Account Name"
            "lbl_lipa_number" -> if (isSw) "Lipa Namba / Till" else "Till / Lipa Number"
            "lbl_mobile_money" -> if (isSw) "Mitandao ya Simu (M-Pesa / Tigo / Airtel)" else "Mobile Money (M-Pesa / Tigo / Airtel)"
            "powered_by" -> "Powered by Agritech"

            // Draft Quotations
            "draft_quotations" -> if (isSw) "Rasimu za Makadirio" else "Draft Quotations"
            "draft_tag" -> if (isSw) "RASIMU" else "DRAFT"
            "draft_quote_desc" -> if (isSw) "Makadirio ambayo hayajakamilika. Gusa kuendelea nayo." else "Unfinished quotations. Tap to continue editing."
            "resume_draft" -> if (isSw) "Endelea na Rasimu" else "Resume Draft"
            "delete_draft" -> if (isSw) "Futa Rasimu" else "Delete Draft"
            "delete_draft_confirm" -> if (isSw) "Je, una uhakika unataka kufuta rasimu hii ya makadirio?" else "Are you sure you want to delete this draft quotation?"
            "draft_auto_saved" -> if (isSw) "Rasimu Imehifadhiwa" else "Draft Saved"
            "no_drafts" -> if (isSw) "Hakuna rasimu zilizohifadhiwa" else "No saved drafts"
            "open_draft" -> if (isSw) "Fungua Rasimu" else "Open Draft"

            else -> key
        }
    }
}
