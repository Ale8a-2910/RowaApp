package mx.edu.ebc.rowa

import java.text.NumberFormat
import java.util.Locale

/** Prices are integer cents so quantity calculations do not accumulate rounding errors. */
data class Product(
    val id: String, val name: String, val category: String, val cents: Int,
    val color: String, val description: String, val sizes: List<String>, val artwork: String
)
data class CartLine(val productId: String, val size: String, val quantity: Int)
data class Delivery(val name: String = "", val email: String = "", val street: String = "", val city: String = "", val postalCode: String = "")
data class OrderItem(val name: String, val color: String, val size: String, val quantity: Int, val unitCents: Int)
data class Order(val folio: String, val timestamp: Long, val items: List<OrderItem>, val delivery: Delivery) {
    val totalCents: Int get() = items.sumOf { it.unitCents * it.quantity }
}
object Catalog {
    val products = listOf(
        Product("lino", "Camisa de lino", "Camisas", 79900, "Arena", "Corte regular, manga corta y textura ligera. Una prenda versátil para todos los días.", listOf("CH", "M", "G"), "shirt"),
        Product("basica", "Playera esencial", "Playeras", 39900, "Olivo", "Cuello redondo y corte cómodo. Un básico para combinar a tu manera.", listOf("CH", "M", "G", "EG"), "tee"),
        Product("oxford", "Camisa Oxford", "Camisas", 89900, "Azul humo", "Manga larga, cuello clásico y silueta recta. Estilo sencillo para cualquier ocasión.", listOf("M", "G", "EG"), "shirt"),
        Product("recto", "Pantalón recto", "Pantalones", 99900, "Piedra", "Cintura media y pierna recta. Diseño relajado y fácil de combinar.", listOf("28", "30", "32", "34"), "pants"),
        Product("premium", "Playera premium", "Playeras", 54900, "Terracota", "Corte amplio y cuello reforzado. Acabado suave y diseño sin estampados.", listOf("CH", "M", "G"), "tee"),
        Product("cargo", "Pantalón cargo", "Pantalones", 109900, "Grafito", "Bolsillos laterales y corte relajado. Una opción práctica para el día a día.", listOf("30", "32", "34", "36"), "pants")
    )
    fun find(id: String): Product = products.first { it.id == id }
    fun search(query: String, category: String, favorites: Set<String>? = null): List<Product> = products.filter {
        (category == "Todas" || it.category == category) &&
            (it.name.contains(query.trim(), ignoreCase = true) || it.description.contains(query.trim(), ignoreCase = true)) &&
            (favorites == null || it.id in favorites)
    }
}
fun money(cents: Int): String = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-MX")).format(cents / 100.0) + " MXN"
fun cartTotal(lines: List<CartLine>): Int = lines.sumOf { Catalog.find(it.productId).cents * it.quantity }
fun validateDelivery(d: Delivery): Map<String, String> = buildMap {
    if (d.name.isBlank()) put("name", "Escribe tu nombre.")
    if (!Regex("^[A-Za-z0-9.!#%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)+$").matches(d.email.trim())) put("email", "Escribe un correo válido, por ejemplo nombre@correo.com.")
    if (d.street.isBlank()) put("street", "Escribe calle y número.")
    if (d.city.isBlank()) put("city", "Escribe tu ciudad.")
    if (!Regex("^[0-9]{5}$").matches(d.postalCode.trim())) put("postalCode", "El código postal debe tener cinco dígitos.")
}
