package mx.edu.ebc.rowa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Ink = Color(0xFF302B26)
private val Cocoa = Color(0xFF654F3B)
private val Sand = Color(0xFFECE1D2)
private val Paper = Color(0xFFFAF8F5)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val store = remember { RowaStore(applicationContext) }
            RowaTheme { RowaApp(store) }
        }
    }
}
@Composable
fun RowaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(
        primary = Cocoa, onPrimary = Color.White, primaryContainer = Sand, onPrimaryContainer = Ink,
        secondary = Cocoa, secondaryContainer = Sand, onSecondaryContainer = Ink, background = Paper, surface = Paper, onBackground = Ink, onSurface = Ink,
        surfaceVariant = Color(0xFFF0EBE4), onSurfaceVariant = Color(0xFF665E55), outline = Color(0xFFB2A79A)
    ), content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RowaApp(store: RowaStore) {
    var page by rememberSaveable { mutableStateOf("catalog") }
    var previous by rememberSaveable { mutableStateOf("catalog") }
    var selectedId by rememberSaveable { mutableStateOf("lino") }
    var info by remember { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun notify(message: String) { scope.launch { snack.showSnackbar(message) } }
    fun openProduct(product: Product) { previous = page; selectedId = product.id; page = "detail" }
    fun back() { page = when(page) { "detail" -> previous; "checkout", "order" -> "cart"; else -> "catalog" } }
    BackHandler(enabled = page != "catalog") { back() }
    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("rowaApp"),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("ROWA", fontFamily = FontFamily.Serif, fontSize = 29.sp, letterSpacing = 5.sp) },
                navigationIcon = { if(page in listOf("detail", "checkout", "order")) IconButton(onClick = { back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
                actions = { TextButton(onClick = { info = true }) { Text("DEMO", fontSize = 11.sp, letterSpacing = 1.sp) } },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Paper)
            )
        },
        snackbarHost = { SnackbarHost(snack) { data ->
            Snackbar(action = { TextButton(onClick = { data.dismiss() }) { Text("Cerrar", color = Sand) } }) { Text(data.visuals.message) }
        } },
        bottomBar = {
            if (page != "checkout") NavigationBar(containerColor = Paper, tonalElevation = 0.dp) {
                val tabs = listOf(Triple("catalog", "Catálogo", Icons.Default.Storefront), Triple("favorites", "Favoritos", Icons.Default.FavoriteBorder), Triple("cart", "Carrito", Icons.Default.ShoppingBag))
                tabs.forEach { (route, label, icon) ->
                    NavigationBarItem(selected = page == route || (page == "detail" && previous == route), onClick = { page = route },
                        modifier = Modifier.testTag("nav_$route"),
                        icon = { BadgedBox(badge = { if(route == "cart" && store.cart.isNotEmpty()) Badge { Text(store.cart.sumOf { it.quantity }.toString()) } }) { Icon(icon, contentDescription = null) } }, label = { Text(label) })
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when(page) {
                "catalog" -> CatalogScreen(store, false, ::openProduct)
                "favorites" -> CatalogScreen(store, true, ::openProduct)
                "detail" -> DetailScreen(Catalog.find(selectedId), store) { notify(it) }
                "cart" -> CartScreen(store, { page = "catalog" }, { page = "checkout" }, { page = "order" })
                "checkout" -> CheckoutScreen(store) { if(store.confirm()) page = "order" else notify("Revisa los datos y el contenido del carrito.") }
                "order" -> OrderScreen(store.lastOrder) { page = "catalog" }
            }
        }
    }
    if(info) AlertDialog(onDismissRequest = { info = false }, title = { Text("Una experiencia de demostración") },
        text = { Text("Proyecto académico de Alejandro Ochoa Buendía. Las prendas, ilustraciones y precios son ejemplos, no un catálogo oficial. Los pedidos se guardan solo en este dispositivo. No se realizan cobros ni envíos. Utiliza datos ficticios para probar.") },
        confirmButton = { TextButton(onClick = { info = false }) { Text("Entendido") } })
}

@Composable
private fun CatalogScreen(store: RowaStore, favoriteOnly: Boolean, onProduct: (Product) -> Unit) {
    var query by rememberSaveable(favoriteOnly) { mutableStateOf("") }
    var category by rememberSaveable(favoriteOnly) { mutableStateOf("Todas") }
    val products = Catalog.search(query, category, if(favoriteOnly) store.favorites else null)
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(if(favoriteOnly) "Tus favoritos" else "Lo esencial, a tu manera.", fontFamily = FontFamily.Serif, fontSize = 26.sp)
            Text(if(favoriteOnly) "Guarda hoy. Elige después." else "Colección de ejemplo · Precios en MXN", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp))
            OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true, label = { Text("Buscar prendas") },
                leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = { if(query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Borrar búsqueda") } },
                shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().testTag("search"))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Todas", "Camisas", "Playeras", "Pantalones").forEach { label ->
                    FilterChip(selected = category == label, onClick = { category = label }, label = { Text(label, fontSize = 11.sp) }, modifier = Modifier.testTag("filter_$label"))
                }
            }
        }
        if(products.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(if(favoriteOnly) Icons.Default.FavoriteBorder else Icons.Default.SearchOff, null, Modifier.size(40.dp), tint = Cocoa)
                Spacer(Modifier.height(16.dp))
                Text(if(favoriteOnly && store.favorites.isEmpty()) "Aún no tienes favoritos" else "Sin resultados", style = MaterialTheme.typography.titleMedium)
                Text(if(favoriteOnly && store.favorites.isEmpty()) "Toca el corazón de una prenda para guardarla." else "Prueba otro nombre o categoría.", textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                if(query.isNotEmpty() || category != "Todas") TextButton(onClick = { query = ""; category = "Todas" }) { Text("Limpiar búsqueda") }
            }
        } else LazyVerticalGrid(columns = GridCells.Fixed(2), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.weight(1f).testTag("productGrid")) {
            items(products, key = { it.id }) { product ->
                Column {
                    Box(Modifier.fillMaxWidth().height(165.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))) {
                        Box(Modifier.fillMaxSize().clickable { onProduct(product) }.testTag("product_${product.id}").semantics { contentDescription = "Ver ${product.name}" }) { GarmentArt(product, Modifier.fillMaxSize().padding(14.dp)) }
                        IconButton(onClick = { store.toggleFavorite(product.id) }, modifier = Modifier.align(Alignment.TopEnd).testTag("favorite_${product.id}")) {
                            Icon(if(product.id in store.favorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder, if(product.id in store.favorites) "Quitar ${product.name} de favoritos" else "Guardar ${product.name} en favoritos", tint = Cocoa)
                        }
                    }
                    Text(product.name, fontWeight = FontWeight.Medium, fontSize = 14.sp, modifier = Modifier.padding(top = 9.dp).clickable { onProduct(product) })
                    Text(product.color, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(money(product.cents), fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun DetailScreen(product: Product, store: RowaStore, onMessage: (String) -> Unit) {
    var selectedSize by rememberSaveable(product.id) { mutableStateOf<String?>(null) }
    var showError by rememberSaveable(product.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(product.category.uppercase(), fontSize = 11.sp, letterSpacing = 2.sp, modifier = Modifier.weight(1f))
            IconToggleButton(checked = product.id in store.favorites, onCheckedChange = { store.toggleFavorite(product.id) }) { Icon(if(product.id in store.favorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Guardar o quitar favorito", tint = Cocoa) }
        }
        GarmentArt(product, Modifier.fillMaxWidth().height(230.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp)).padding(20.dp))
        Text(product.name, fontFamily = FontFamily.Serif, fontSize = 28.sp)
        Text(money(product.cents), style = MaterialTheme.typography.titleLarge)
        Text("Color: ${product.color}", style = MaterialTheme.typography.bodyMedium)
        Text(product.description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text("Selecciona tu talla", fontWeight = FontWeight.Medium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            product.sizes.forEach { size -> FilterChip(selected = selectedSize == size, onClick = { selectedSize = size; showError = false }, label = { Text(size) }, modifier = Modifier.testTag("size_$size")) }
        }
        if(showError) Text("Selecciona una talla disponible.", color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("sizeError"))
        Button(onClick = {
            showError = selectedSize == null
            if(!showError) onMessage(store.add(product.id, selectedSize))
        }, modifier = Modifier.fillMaxWidth().height(52.dp).testTag("addToCart"), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.AddShoppingCart, null); Spacer(Modifier.width(10.dp)); Text("Agregar al carrito") }
        Text("Pedido simulado · Sin cobro real", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CartScreen(store: RowaStore, explore: () -> Unit, checkout: () -> Unit, order: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text("Tu carrito", fontFamily = FontFamily.Serif, fontSize = 28.sp)
        Text("El siguiente paso de tu elección.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp))
        if(store.cart.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.ShoppingBag, null, Modifier.size(48.dp), tint = Cocoa)
                Text("Tu carrito está vacío", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 10.dp))
                Button(onClick = explore) { Text("Explorar catálogo") }
            }
        } else {
            LazyColumn(Modifier.weight(1f).testTag("cartList"), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                items(store.cart, key = { it.productId + ":" + it.size }) { line ->
                    val product = Catalog.find(line.productId)
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                GarmentArt(product, Modifier.size(64.dp).background(Paper, RoundedCornerShape(10.dp)))
                                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text(product.name, fontWeight = FontWeight.Medium)
                                    Text("${product.color} · Talla ${line.size}", fontSize = 12.sp)
                                    Text(money(product.cents), fontSize = 13.sp)
                                }
                                IconButton(onClick = { store.remove(line) }, modifier = Modifier.testTag("remove_${line.productId}_${line.size}")) { Icon(Icons.Default.DeleteOutline, "Eliminar ${product.name}, talla ${line.size}", tint = Cocoa) }
                            }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { store.quantity(line, -1) }, enabled = line.quantity > 1, modifier = Modifier.testTag("minus_${line.productId}_${line.size}")) { Icon(Icons.Default.Remove, "Reducir cantidad de ${product.name}") }
                                Text(line.quantity.toString(), modifier = Modifier.testTag("qty_${line.productId}_${line.size}"))
                                IconButton(onClick = { store.quantity(line, 1) }, enabled = line.quantity < 10, modifier = Modifier.testTag("plus_${line.productId}_${line.size}")) { Icon(Icons.Default.Add, "Aumentar cantidad de ${product.name}") }
                                Spacer(Modifier.weight(1f))
                                Text(money(product.cents * line.quantity), fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
            Text("Máximo 10 piezas por prenda y talla.", fontSize = 11.sp)
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("Envío simulado"); Text("$0.00 MXN") }
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("Total", style = MaterialTheme.typography.titleLarge); Text(money(cartTotal(store.cart)), fontWeight = FontWeight.Bold, modifier = Modifier.testTag("cartTotal")) }
            Button(onClick = checkout, modifier = Modifier.fillMaxWidth().height(50.dp).testTag("checkout"), shape = RoundedCornerShape(14.dp)) { Text("Continuar con la entrega") }
        }
        if(store.lastOrder != null) TextButton(onClick = order, modifier = Modifier.fillMaxWidth().testTag("lastOrder")) { Text("Consultar último pedido") }
        Text("Demostración: no se procesan pagos ni envíos.", fontSize = 11.sp, modifier = Modifier.padding(vertical = 10.dp))
    }
}

@Composable
private fun CheckoutScreen(store: RowaStore, confirm: () -> Unit) {
    var attempted by rememberSaveable { mutableStateOf(false) }
    val delivery = store.delivery
    val errors = if(attempted) validateDelivery(delivery) else emptyMap()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Datos de entrega", fontFamily = FontFamily.Serif, fontSize = 28.sp)
        Text("Utiliza datos ficticios para esta demostración.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        DeliveryField("Nombre completo", delivery.name, { store.updateDelivery(delivery.copy(name = it)) }, errors["name"], "name")
        DeliveryField("Correo electrónico", delivery.email, { store.updateDelivery(delivery.copy(email = it)) }, errors["email"], "email", KeyboardType.Email)
        DeliveryField("Calle y número", delivery.street, { store.updateDelivery(delivery.copy(street = it)) }, errors["street"], "street")
        DeliveryField("Ciudad", delivery.city, { store.updateDelivery(delivery.copy(city = it)) }, errors["city"], "city")
        DeliveryField("Código postal", delivery.postalCode, { store.updateDelivery(delivery.copy(postalCode = it)) }, errors["postalCode"], "postalCode", KeyboardType.Number)
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        Text("Total: ${money(cartTotal(store.cart))}", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text("No se realizará ningún cargo. Al confirmar se guarda un pedido local y se vacía el carrito.", fontSize = 12.sp)
        Button(onClick = { attempted = true; if(validateDelivery(delivery).isEmpty()) confirm() }, enabled = store.cart.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(54.dp).testTag("confirmOrder"), shape = RoundedCornerShape(14.dp)) { Text("Confirmar pedido simulado") }
        Spacer(Modifier.height(16.dp))
    }
}
@Composable
private fun DeliveryField(label: String, value: String, changed: (String) -> Unit, error: String?, tag: String, keyboard: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(value = value, onValueChange = changed, label = { Text(label) }, singleLine = true, isError = error != null,
        supportingText = if(error != null) {{ Text(error) }} else null,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().testTag("field_$tag"))
}
@Composable
private fun OrderScreen(order: Order?, explore: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if(order == null) { Text("Aún no hay pedidos"); Button(onClick = explore) { Text("Explorar catálogo") }; return@Column }
        Icon(Icons.Default.CheckCircle, null, Modifier.size(54.dp), tint = Cocoa)
        Text("Tu pedido fue registrado", fontFamily = FontFamily.Serif, fontSize = 28.sp)
        Text(order.folio, style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("orderFolio"))
        Text(SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.forLanguageTag("es-MX")).format(Date(order.timestamp)), fontSize = 12.sp)
        Surface(color = Sand, shape = RoundedCornerShape(20.dp)) { Text("Recibido · Pedido simulado", modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), fontSize = 12.sp) }
        Text("Resumen", style = MaterialTheme.typography.titleMedium)
        order.items.forEach { item ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) { Text(item.name); Text("${item.color} · ${item.size} · ${item.quantity} pieza(s)", fontSize = 12.sp) }
                Text(money(item.unitCents * item.quantity), fontSize = 13.sp)
            }
        }
        HorizontalDivider()
        Text("Total: ${money(order.totalCents)}", fontWeight = FontWeight.Bold, modifier = Modifier.testTag("orderTotal"))
        Text("Entrega de demostración", style = MaterialTheme.typography.titleMedium)
        Text("${order.delivery.name}\n${order.delivery.street}\n${order.delivery.city}, CP ${order.delivery.postalCode}\n${order.delivery.email}")
        Text("Este registro no es una compra real. No se ha realizado ningún cobro y no se enviará mercancía.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = explore, modifier = Modifier.fillMaxWidth().height(52.dp).testTag("backToCatalog"), shape = RoundedCornerShape(14.dp)) { Text("Volver al catálogo") }
    }
}

/** Original vector-like Compose drawings; no downloaded imagery or internet dependency. */
@Composable
private fun GarmentArt(product: Product, modifier: Modifier = Modifier) {
    val fabric = when(product.color) { "Olivo" -> Color(0xFF777D63); "Azul humo" -> Color(0xFF8197A4); "Terracota" -> Color(0xFFAF7761); "Grafito" -> Color(0xFF60625F); "Piedra" -> Color(0xFFB7AF9F); else -> Color(0xFFBBA98F) }
    Canvas(modifier.semantics { contentDescription = "Ilustración de ${product.name} en color ${product.color}" }) {
        val scale = minOf(size.width / 200f, size.height / 240f)
        val left = (size.width - 200f * scale) / 2
        val top = (size.height - 240f * scale) / 2
        fun point(x: Float, y: Float) = Offset(left + x * scale, top + y * scale)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(fabric.copy(alpha = 0.55f).let { Color(0xFF665B4D).copy(alpha = .55f) }, point(x1,y1), point(x2,y2), 1.6f * scale)
        drawOval(Color.Black.copy(alpha = .07f), topLeft = point(34f, 211f), size = Size(132f * scale, 12f * scale))
        val path = Path()
        fun move(x:Float,y:Float) { val p = point(x,y); path.moveTo(p.x,p.y) }
        fun to(x:Float,y:Float) { val p = point(x,y); path.lineTo(p.x,p.y) }
        if(product.artwork == "pants") {
            move(50f,25f); to(150f,25f); to(157f,208f); to(111f,208f); to(100f,100f); to(89f,208f); to(43f,208f); path.close()
            drawPath(path, fabric); line(50f,42f,150f,42f); line(100f,42f,100f,100f); line(55f,45f,72f,67f); line(145f,45f,128f,67f)
            if(product.id=="cargo") { line(48f,110f,82f,110f); line(118f,110f,152f,110f); line(48f,140f,82f,140f); line(118f,140f,152f,140f) }
        } else {
            move(65f,35f); to(85f,25f); to(115f,25f); to(135f,35f); to(177f,70f); to(155f,104f); to(135f,89f); to(140f,207f); to(60f,207f); to(65f,89f); to(45f,104f); to(23f,70f); path.close()
            drawPath(path, fabric)
            if(product.artwork == "shirt") {
                val collar = Path().apply { val a=point(85f,25f); moveTo(a.x,a.y); val b=point(100f,50f); lineTo(b.x,b.y); val c=point(115f,25f); lineTo(c.x,c.y) }
                drawPath(collar, Color(0xFF665B4D).copy(alpha=.6f), style=Stroke(1.8f*scale)); line(100f,50f,100f,207f)
                for(y in listOf(68f,94f,120f,146f,172f)) drawCircle(Color(0xFFF9F4EB), 2f*scale, point(102f,y))
                line(112f,72f,130f,72f); line(112f,72f,112f,94f); line(112f,94f,130f,94f)
            } else drawArc(Color(0xFF514F43).copy(alpha=.45f), 0f, 180f, false, topLeft=point(84f,16f), size=Size(32f*scale,25f*scale), style=Stroke(3f*scale))
            line(65f,198f,135f,198f)
        }
    }
}
