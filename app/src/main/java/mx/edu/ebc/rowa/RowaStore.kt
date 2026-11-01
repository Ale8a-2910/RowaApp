package mx.edu.ebc.rowa

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/** Small offline store. Only this device's private preferences are used; no network access. */
class RowaStore(context: Context) {
    private val prefs = context.getSharedPreferences("rowa_v1", Context.MODE_PRIVATE)
    var favorites by mutableStateOf(setOf<String>()); private set
    var cart by mutableStateOf(listOf<CartLine>()); private set
    var lastOrder by mutableStateOf<Order?>(null); private set
    var delivery by mutableStateOf(Delivery()); private set
    private var nextOrder = 1
    init {
        runCatching {
            val data = JSONObject(prefs.getString("state", "{}") ?: "{}")
            favorites = data.optJSONArray("favorites").strings().filter { id -> Catalog.products.any { it.id == id } }.toSet()
            val rows = data.optJSONArray("cart") ?: JSONArray()
            cart = (0 until rows.length()).mapNotNull { index ->
                val row = rows.getJSONObject(index)
                val product = Catalog.products.find { it.id == row.optString("id") }
                val size = row.optString("size")
                if (product == null || size !in product.sizes) null
                else CartLine(product.id, size, row.optInt("quantity", 1).coerceIn(1, 10))
            }
            nextOrder = data.optInt("nextOrder", 1).coerceAtLeast(1)
            data.optJSONObject("order")?.let { saved ->
                val items = saved.getJSONArray("items")
                val snapshot = (0 until items.length()).map { i ->
                    val row = items.getJSONObject(i)
                    OrderItem(row.getString("name"), row.getString("color"), row.getString("size"), row.getInt("quantity"), row.getInt("unitCents"))
                }
                lastOrder = Order(saved.getString("folio"), saved.getLong("timestamp"), snapshot, saved.getJSONObject("delivery").asDelivery())
            }
        }.onFailure {
            favorites = emptySet(); cart = emptyList(); lastOrder = null; nextOrder = 1
        }
    }
    fun toggleFavorite(id: String) {
        favorites = if (id in favorites) favorites - id else favorites + id
        save()
    }
    fun add(id: String, size: String?): String {
        val product = Catalog.find(id)
        if (size == null || size !in product.sizes) return "Selecciona una talla disponible."
        val existing = cart.find { it.productId == id && it.size == size }
        if ((existing?.quantity ?: 0) >= 10) return "El máximo es de 10 piezas por prenda y talla."
        cart = if (existing == null) cart + CartLine(id, size, 1)
        else cart.map { if (it == existing) it.copy(quantity = it.quantity + 1) else it }
        save()
        return "Prenda agregada al carrito."
    }
    fun quantity(line: CartLine, delta: Int) {
        cart = cart.map { if (it.productId == line.productId && it.size == line.size) it.copy(quantity = (it.quantity + delta).coerceIn(1, 10)) else it }
        save()
    }
    fun remove(line: CartLine) { cart = cart.filterNot { it.productId == line.productId && it.size == line.size }; save() }
    fun updateDelivery(value: Delivery) { delivery = value }
    fun confirm(): Boolean {
        if (cart.isEmpty() || validateDelivery(delivery).isNotEmpty()) return false
        val clean = delivery.copy(name = delivery.name.trim(), email = delivery.email.trim(), street = delivery.street.trim(), city = delivery.city.trim(), postalCode = delivery.postalCode.trim())
        lastOrder = Order("DEMO-" + nextOrder.toString().padStart(4, '0'), System.currentTimeMillis(), cart.map {
            val product = Catalog.find(it.productId)
            OrderItem(product.name, product.color, it.size, it.quantity, product.cents)
        }, clean)
        nextOrder++
        cart = emptyList()
        delivery = Delivery()
        save() // Cart and order are serialized together in one preference update.
        return true
    }
    private fun save() {
        val data = JSONObject().put("favorites", JSONArray(favorites.toList())).put("nextOrder", nextOrder)
            .put("cart", JSONArray().apply { cart.forEach { put(JSONObject().put("id", it.productId).put("size", it.size).put("quantity", it.quantity)) } })
        lastOrder?.let { order -> data.put("order", JSONObject().put("folio", order.folio).put("timestamp", order.timestamp)
            .put("delivery", JSONObject().put("name", order.delivery.name).put("email", order.delivery.email).put("street", order.delivery.street).put("city", order.delivery.city).put("postalCode", order.delivery.postalCode))
            .put("items", JSONArray().apply { order.items.forEach { item -> put(JSONObject().put("name", item.name).put("color", item.color).put("size", item.size).put("quantity", item.quantity).put("unitCents", item.unitCents)) } })) }
        prefs.edit().putString("state", data.toString()).apply()
    }
    private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).map { getString(it) }
    private fun JSONObject.asDelivery() = Delivery(getString("name"), getString("email"), getString("street"), getString("city"), getString("postalCode"))
}
