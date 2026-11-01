package mx.edu.ebc.rowa

import org.junit.Assert.*
import org.junit.Test

class DomainTest {
    @Test fun totalsIncludeQuantityAndDifferentSizes() {
        val lines = listOf(CartLine("lino", "M", 2), CartLine("basica", "G", 1), CartLine("lino", "G", 1))
        assertEquals(279600, cartTotal(lines))
        assertEquals(0, cartTotal(emptyList()))
    }
    @Test fun requiredFieldsAndInvalidValuesAreRejected() {
        assertEquals(setOf("name", "email", "street", "city", "postalCode"), validateDelivery(Delivery()).keys)
        val valid = Delivery("Ana", "ana@example.com", "Calle 10", "Puebla", "01234")
        assertTrue(validateDelivery(valid).isEmpty())
        for (email in listOf("correo", "@example.com", "a@", "a b@example.com", "a@example..com")) {
            assertTrue(validateDelivery(valid.copy(email = email)).containsKey("email"))
        }
        for (cp in listOf("1234", "123456", "12A45")) assertTrue(validateDelivery(valid.copy(postalCode = cp)).containsKey("postalCode"))
        assertTrue(validateDelivery(valid.copy(name="   ")).containsKey("name"))
    }
    @Test fun searchCombinesCategoryAndFavorites() {
        assertEquals(listOf("lino"), Catalog.search("LINO", "Camisas").map { it.id })
        assertTrue(Catalog.search("lino", "Pantalones").isEmpty())
        assertTrue(Catalog.search("zzzz", "Todas").isEmpty())
        assertEquals(listOf("basica"), Catalog.search("", "Todas", setOf("basica")).map { it.id })
    }
    @Test fun orderUsesHistoricalSnapshotAndIntegerCents() {
        val order = Order("DEMO-0001", 0L, listOf(OrderItem("Prenda", "Arena", "M", 3, 79900)), Delivery())
        assertEquals(239700, order.totalCents)
    }
}
