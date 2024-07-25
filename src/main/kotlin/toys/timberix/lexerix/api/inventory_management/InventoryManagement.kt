@file:Suppress("ClassName", "Unused", "MemberVisibilityCanBePrivate")

package toys.timberix.lexerix.api.inventory_management

import kotlinx.datetime.Clock
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import org.jetbrains.exposed.sql.statements.InsertStatement
import toys.timberix.lexerix.api.asCurrency
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

object InventoryManagement {
    object Customers : IntIdTable("FK_Kunde", "SheetNr") {
        /**
         * Unique customer number. Must be unique.
         */
        val kundenNr = varchar("KundenNr", 20)

        /**
         * Short name/identifier for the customer. Should be unique.
         */
        val matchcode = varchar("Matchcode", 255)

        val anschriftFirma = varchar("Anschrift_Firma", 255).default("")
        val anschriftAnrede = varchar("Anschrift_Anrede", 255).default("")
        val anschriftName = varchar("Anschrift_Name", 255).default("")
        val anschriftVorname = varchar("Anschrift_Vorname", 255).default("")
        val anschriftZusatz = varchar("Anschrift_Zusatz", 255).default("")
        val anschriftStrasse = varchar("Anschrift_Strasse", 255).default("")
        val anschriftHausNr = varchar("Anschrift_HausNr", 255).default("")
        val anschriftOrt = varchar("Anschrift_Ort", 255).default("")
        val anschriftPlz = varchar("Anschrift_Plz", 255).default("")
        val anschriftLand = varchar("Anschrift_Land", 255).default("")
        val anschriftEmail = varchar("Anschrift_Email", 255).default("")
        val anschriftTel1 = varchar("Anschrift_Tel1", 20).default("")
        val anschriftTel2 = varchar("Anschrift_Tel2", 20).default("")
        val anschriftWeb = varchar("Anschrift_Web", 255).default("")
        val bemerkung = varchar("Bemerkung", 255).default("Customer created by LEXERIX")

        /** Inserts a customer with `id` and `KundenNr` that are not yet used. */
        fun insertUnique(block: Customers.(InsertStatement<EntityID<Int>>) -> Unit): EntityID<Int> {
            // Fetch the highest id
            val customers = selectAll()
            val highestId = customers.maxOf { maxOf(it[id].value, it[kundenNr].toIntOrNull() ?: 0) }

            // Insert new customer
            return insertAndGetId {
                it[id] = highestId + 1
                it[kundenNr] = (highestId + 1).toString()
                block(it)
            }
        }
    }

    object Companies : IntIdTable("FK_Firma", "lID_Firma") {
        val bBrutto = bool("bBrutto")
    }

    object Products : DatedTable("FK_Artikel", "SheetNr") {
        val artikelNr = integer("ArtikelNr")
        val matchcode = varchar("Matchcode", 35)
        val bezeichnung = varchar("Bezeichnung", 255)
        val beschreibung = varchar("Beschreibung", 255).default("Product created by LEXERIX")
        val gewicht = float("Gewicht")
        val unit = varchar("Einheit", 20).default("Stück")

        /** no price? --- use [withPrices] */
        val preis = float("Vk_preis")
        val bestand = float("Menge_bestand")
        val minBestand = float("Menge_minbestand")

        /** Should this product be visible in the webshop? */
        val webShop = bool("bStatus_WebShop")

        fun withPrices(priceGroup: Int = 1, quantity: Int = 1) = (this innerJoin PriceMatrix).selectAll().where {
            (PriceMatrix.preisGrpNr eq priceGroup) and (PriceMatrix.mengeNr eq quantity)
        }
    }

    object PriceMatrix : Table("FK_Preismatrix") {
        val artikelNr = reference("ArtikelNr", Products.artikelNr)
        val mengeNr = integer("MengeNr")
        val preisGrpNr = integer("PreisGrpNr")

        /** Net (!) selling price in € */
        val vkPreisNetto = float("Vk_preis_eur")
    }

    object Orders : DatedTable("FK_Auftrag", "SheetNr") {
        val auftragsNr = varchar("AuftragsNr", 20)
        val auftragsKennung = integer("AuftragsKennung").default(1) // ?

        // customer
        val kundenNr = reference("KundenNr", Customers.kundenNr)
        val kundenMatchcode = varchar("KundenMatchcode", 35)

        // Konditionen
        /** 1, 2 or 3 */
        val preisGrp = integer("Konditionen_PreisgrpNr").default(1)
        val rabatt = float("Konditionen_Rabatt").default(0f)
        val rabattProz = float("Konditionen_Rabatt_Proz").default(0f)

        /** 978 = € */
        val currency = integer("Konditionen_Waehrung")

        /** 4 = online */
        val zahlungsArt = integer("Konditionen_Zahlungsart").default(4)

        /** "Lieferung per Postversand" oder "Lieferung frei Haus" */
        val lieferArt = varchar("Konditionen_Lieferart", 200).default("Lieferung per Postversand")

        // Anschrift
        val anschriftAnrede = varchar("Anschrift_Anrede", 255)
        val anschriftFirma = varchar("Anschrift_Firma", 255)
        val anschriftName = varchar("Anschrift_Name", 255)
        val anschriftVorname = varchar("Anschrift_Vorname", 255)
        val anschriftZusatz = varchar("Anschrift_Zusatz", 255)
        val anschriftStrasse = varchar("Anschrift_Strasse", 255)
        val anschriftHausNr = varchar("Anschrift_HausNr", 255)
        val anschriftOrt = varchar("Anschrift_Ort", 255)
        val anschriftPlz = varchar("Anschrift_Plz", 255)
        val anschriftLand = varchar("Anschrift_Land", 255)
        val anschriftTel1 = varchar("Anschrift_Tel1", 20)
        val anschriftTel2 = varchar("Anschrift_Tel2", 20)
        val anschriftEmail = varchar("Anschrift_Email", 255)

        // timestamps
        val datumErfassung = timestamp("Datum_erfassung").clientDefault { Clock.System.now() }
        val datumZahlung = timestamp("Datum_zahlung")

        // prices
        val nettoHaupt = float("Summen_netto_haupt")
        val bruttoHaupt = float("Summen_brutto_haupt")
        val nettoNeben = float("Summen_netto_neben").default(0f)
        val bruttoNeben = float("Summen_brutto_neben").default(0f)

        /** only USt */
        val totalTax = float("Summen_ust_gesamt")

        /** total gross price with tax */
        val totalGrossPrice = float("Summen_gesamt")

        // todo Abschlag blablabla?
        val abschlagForderung = float("Summen_abschlag_forderung") // the same as "Summen_gesamt"?

        // statuses
        val lagergebucht = integer("bStatus_lagergebucht").default(0)
        val gebucht = integer("bStatus_gebucht").default(0)
        val uebernommen = integer("bStatus_uebernommen").default(0)

        // what is "Status_gedruckt" and "Status_drucken"?!?
        val gedruckt = integer("bStatus_gedruckt").default(0)
        val exportiert = integer("bStatus_exportiert").default(0)

        /** was the order delivered? */
        val delivered = integer("bStatus_geliefert").default(0)

        /** was the order paid? */
        val paid = integer("bStatus_bezahlt").default(0)
        val weitergefuehrt = integer("bStatus_weitergefuehrt").default(0)

        /** expected delivery *date* (time does not matter) */
        val deliveryDate = timestamp("tsLieferTermin").clientDefault { Clock.System.now() }

        // lHerkunft ?

        val gewinnNetto = float("dftSumme_GewinnNetto_NAR").default(0f) // ?

        val weight = float("dftSummen_Gewicht").default(0f)

        fun selectWithProducts() = ((this innerJoin OrderContents) innerJoin Products)
            .selectAll()
            .groupBy {
                it[id]
            }

        /** Inserts an order with `id` and `AuftragsNr` that are not yet used. */
        fun insertUnique(block: Orders.(InsertStatement<EntityID<Int>>) -> Unit): EntityID<Int> {
            // Fetch the highest id
            val orders = selectAll()
            val highestId = orders.maxOf { maxOf(it[id].value, it[auftragsNr].toIntOrNull() ?: 0) }

            // Insert new customer
            return insertAndGetId {
                it[id] = highestId + 1
                it[auftragsNr] = (highestId + 1).toString()
                block(it)
            }
        }
    }

    context(Orders)
    @Suppress("USELESS_ELVIS", "RemoveRedundantQualifierName")
    fun InsertStatement<EntityID<Int>>.applyCustomerData(customer: ResultRow) {
        val mapping = mapOf(
            Orders.anschriftAnrede to Customers.anschriftAnrede,
            Orders.anschriftFirma to Customers.anschriftFirma,
            Orders.anschriftName to Customers.anschriftName,
            Orders.anschriftVorname to Customers.anschriftVorname,
            Orders.anschriftZusatz to Customers.anschriftZusatz,
            Orders.anschriftStrasse to Customers.anschriftStrasse,
            Orders.anschriftHausNr to Customers.anschriftHausNr,
            Orders.anschriftOrt to Customers.anschriftOrt,
            Orders.anschriftPlz to Customers.anschriftPlz,
            Orders.anschriftLand to Customers.anschriftLand,
            Orders.anschriftTel1 to Customers.anschriftTel1,
            Orders.anschriftTel2 to Customers.anschriftTel2,
            Orders.anschriftEmail to Customers.anschriftEmail
        )

        mapping.forEach { (orderColumn, customerColumn) ->
            this[orderColumn] = customer[customerColumn] ?: ""
        }
    }

    object OrderContents : Table("FK_AuftragPos") {
        val lNr = integer("lNr")
        val auftragsNr = reference("AuftragsNr", Orders.auftragsNr)
        val auftragsKennung = integer("AuftragsKennung").default(1) // ?

        /** index of content in order, e.g. order 1 could have contents 1,2,3 and order 2 could have contents 1,2 */
        val lfdNumber = integer("LfdNr")
        val szPosNr = integer("szPosNr") // = lfdNumber (?)
        val posNumber = integer("PosNr") // 1 bigger than lfdNumber (?)

        /** additional text per content, e.g. "I want this (the apples) to be green" */
        val note = varchar("PosText", 5000).default("")
        val productNr = reference("ArtikelNr", Products.artikelNr)

        // "ErloesKonto" doesn't seem to matter
        val warengrpNr = integer("WarengrpNr").default(1)

        // product data (why is this duplicated?)
        val productName = varchar("Artikel_Bezeichnung", 100)
        val productMatchcode = varchar("Artikel_Matchcode", 35)
        val productUnit = varchar("Artikel_Einheit", 20).default("Stück")
        val productWeight = float("Artikel_kg_Einheit")

        val count = integer("Artikel_Menge")
        val priceFactor = integer("Artikel_Preisfaktor") // = count
        val netPricePerProduct = float("Summen_preis") // duplicate of netProductCost?
        val totalNetPrice = float("Summen_netto")
        val totalGrossPrice = float("Summen_brutto")
        val taxProz = float("Summen_ust_proz").default(19f)

        /** calculated tax for this content */
        val totalTax = float("Summen_ust_gesamt")

        val totalTaxAfterAufrab = float("Summen_ust_nach_Aufrab") // = totalTax
        val totalNetPriceAfterAufrab = float("Summen_netto_nach_Aufrab") // = totalPriceNetto
        val totalGrossPriceAfterAufrab = float("Summen_brutto_nach_Aufrab") // = totalPriceBrutto
        val mitAuftragsRabatt = integer("bMitAuftragsrabatt").default(1)

        val productId = integer("lArtikelID") // = SheetNr of product

        val rabattWarnung = integer("fRabattWarnung").default(1)
        val productShortDesc = varchar("szArtikel_Kurzbezeichnung", 100) // often eq. productName

        // cost of product for company without profit -- not used?
        val productCost = float("dftArtikel_Selbstkosten")
        val netProductCost = float("dftArtikelpreisNetto")
        val grossProductCost = float("dftArtikelpreisBrutto")

        val gewinnNetto = float("dftSumme_GewinnNetto_NAR") // doesn't seem to be used

        val weight = float("dftSummen_Gewicht")

        // tsLieferRueckstand ?

        /** Inserts an order content with `id` that is not yet used. */
        fun insertUnique(block: OrderContents.(InsertStatement<Number>) -> Unit): InsertStatement<Number> {
            // Fetch the highest id
            val orders = selectAll()
            val highestId = orders.maxOf { it[lNr] }

            // Insert new customer
            return insert {
                // for some reason, the id is not auto-incremented
                it[lNr] = highestId + 1
                block(it)
            }
        }

        /**
         * @param product the product inner joined with [PriceMatrix]!! ([Products.withPrices])
         * @param index index of the content in the order, starting at 0
         */
        fun insertFor(
            orderNr: String,
            index: Int,
            product: ResultRow,
            count: Int,
            note: String = ""
        ) = insertUnique {
            it[auftragsNr] = orderNr

            it[lfdNumber] = index + 1
            it[szPosNr] = index + 1
            it[posNumber] = index + 2

            it[this.note] = note
            it[productNr] = product[Products.artikelNr]
            it[productName] = product[Products.bezeichnung]
            it[productMatchcode] = product[Products.matchcode]
            it[productUnit] = product[Products.unit]
            it[productWeight] = BigDecimal(product[Products.gewicht].toString(), MathContext(3, RoundingMode.HALF_UP)).toFloat()

            it[this.count] = count
            it[priceFactor] = count
            val netPrice = product[PriceMatrix.vkPreisNetto].asCurrency()
            val taxPortion = BigDecimal("0.19") // todo check tax
            val tax = netPrice * taxPortion
            val grossPrice = netPrice + tax
            val totalNetPrice = netPrice * BigDecimal(count)
            val totalTax = totalNetPrice * taxPortion
            val totalGrossPrice = totalNetPrice + totalTax
            it[netPricePerProduct] = netPrice.toFloat()
            it[this.totalNetPrice] = totalNetPrice.toFloat()
            it[this.totalGrossPrice] = totalGrossPrice.toFloat()
            it[taxProz] = taxPortion.toFloat()
            it[this.totalTax] = totalTax.toFloat()

            it[totalTaxAfterAufrab] = totalTax.toFloat()
            it[totalNetPriceAfterAufrab] = totalNetPrice.toFloat()
            it[totalGrossPriceAfterAufrab] = totalGrossPrice.toFloat()

            it[productId] = product[Products.id].value
            it[productShortDesc] = product[Products.bezeichnung]

            it[netProductCost] = netPrice.toFloat()
            it[grossProductCost] = grossPrice.toFloat()

            it[weight] = product[Products.gewicht] * count
        }

        fun insertFor(orderNr: String, vararg products: OrderContentData) {
            products.forEachIndexed { index, data ->
                insertFor(orderNr, index, data.product, data.count, data.note)
            }
        }
    }

    data class OrderContentData(
        val product: ResultRow,
        val count: Int,
        val note: String = ""
    )
}