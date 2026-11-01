package mx.edu.ebc.rowa

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class RowaFlowTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var store: RowaStore
    @Before fun setup() {
        // A slow software emulator can show a System UI dialog before the app starts.
        // Only the system's Wait button is acknowledged; application failures stay visible.
        fun waitForSystem(node: AccessibilityNodeInfo?): Boolean {
            if (node == null) return false
            if (node.text?.toString() == "Wait") return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            for (i in 0 until node.childCount) if (waitForSystem(node.getChild(i))) return true
            return false
        }
        waitForSystem(InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("rowa_v1", 0).edit().clear().commit()
        store = RowaStore(context)
        rule.setContent { RowaTheme { RowaApp(store) } }
    }
    private fun screenshot(name: String) {
        rule.mainClock.advanceTimeBy(500)
        rule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        Thread.sleep(250) // Let the emulator present the frame before PixelCopy.
        val file = File(instrumentation.targetContext.filesDir, "screens/$name.png")
        file.parentFile!!.mkdirs()
        rule.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
    @Test fun completeOrderWithValidationAndPersistence() {
        screenshot("01_catalogo")
        rule.onNodeWithTag("favorite_lino").performClick()
        rule.onNodeWithTag("nav_favorites").performClick()
        rule.onNodeWithTag("product_lino").assertExists().performClick()
        rule.onNodeWithTag("addToCart").performScrollTo().performClick()
        rule.onNodeWithTag("sizeError").assertExists()
        rule.onNodeWithTag("size_M").performScrollTo().performClick()
        screenshot("02_detalle")
        rule.onNodeWithTag("addToCart").performScrollTo().performClick()
        rule.onNodeWithText("Cerrar").performClick()
        rule.onNodeWithTag("nav_cart").performClick()
        rule.onNodeWithTag("minus_lino_M").assertIsNotEnabled()
        rule.onNodeWithTag("plus_lino_M").performClick()
        rule.onNodeWithTag("qty_lino_M").assertTextEquals("2")
        rule.onNodeWithTag("cartTotal").assertTextContains("1,598.00", substring = true)
        screenshot("03_carrito")
        rule.runOnIdle {
            val restored = RowaStore(InstrumentationRegistry.getInstrumentation().targetContext)
            assertEquals(setOf("lino"), restored.favorites)
            assertEquals(2, restored.cart.single().quantity)
        }
        rule.onNodeWithTag("checkout").performClick()
        rule.onNodeWithTag("confirmOrder").performScrollTo().performClick()
        screenshot("04_errores")
        rule.onNodeWithTag("field_name").performScrollTo().assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
        fun fill(tag:String,value:String) { rule.onNodeWithTag("field_$tag").performScrollTo().performTextInput(value) }
        fill("name", "Alejandro Demo")
        fill("email", "correo")
        fill("street", "Calle Ejemplo 10")
        fill("city", "Ciudad de Mexico")
        fill("postalCode", "12")
        rule.onNodeWithTag("confirmOrder").performScrollTo().performClick()
        rule.onNodeWithTag("field_postalCode").performScrollTo().assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
        rule.onNodeWithTag("field_email").performScrollTo().performTextReplacement("demo@example.com")
        rule.onNodeWithTag("field_postalCode").performScrollTo().performTextReplacement("01234")
        rule.onNodeWithText("Datos de entrega").performScrollTo()
        screenshot("04_entrega")
        rule.onNodeWithTag("confirmOrder").performScrollTo().performClick()
        rule.onNodeWithTag("orderFolio").assertTextEquals("DEMO-0001")
        rule.onNodeWithTag("orderTotal").assertTextContains("1,598.00", substring = true)
        rule.onNodeWithText("Tu pedido fue registrado").performScrollTo()
        screenshot("05_pedido")
        rule.runOnIdle {
            val restored = RowaStore(InstrumentationRegistry.getInstrumentation().targetContext)
            assertTrue(restored.cart.isEmpty())
            assertEquals(159800, restored.lastOrder!!.totalCents)
            assertEquals("01234", restored.lastOrder!!.delivery.postalCode)
        }
        rule.onNodeWithTag("nav_cart").performClick()
        rule.onNodeWithText("Tu carrito está vacío").assertExists()
        rule.onNodeWithTag("lastOrder").performClick()
        rule.onNodeWithTag("orderFolio").assertTextEquals("DEMO-0001")
    }
    @Test fun filteringNoResultsAndRemovingFavorite() {
        rule.onNodeWithTag("filter_Pantalones").performScrollTo().performClick()
        rule.onNodeWithTag("product_recto").assertExists()
        rule.onNodeWithTag("product_lino").assertDoesNotExist()
        rule.onNodeWithTag("search").performTextInput("zzzz")
        rule.onNodeWithText("Sin resultados").assertExists()
        rule.onNodeWithText("Limpiar búsqueda").performClick()
        rule.onNodeWithTag("favorite_lino").performClick()
        rule.onNodeWithTag("nav_favorites").performClick()
        rule.onNodeWithTag("favorite_lino").performClick()
        rule.onNodeWithText("Aún no tienes favoritos").assertExists()
    }
    @Test fun quantityLimitDeletionAndUniqueFolios() {
        rule.runOnIdle {
            assertEquals("Selecciona una talla disponible.", store.add("lino", null))
            repeat(12) { store.add("lino", "M") }
            assertEquals(10, store.cart.single().quantity)
            store.add("lino", "G")
            assertEquals(2, store.cart.size)
        }
        rule.onNodeWithTag("nav_cart").performClick()
        rule.onNodeWithTag("plus_lino_M").assertIsNotEnabled()
        rule.onNodeWithTag("cartList").performScrollToNode(hasTestTag("remove_lino_G"))
        rule.onNodeWithTag("remove_lino_G").performClick()
        rule.runOnIdle {
            assertEquals(1, store.cart.size)
            store.updateDelivery(Delivery("Demo", "demo@example.com", "Calle 1", "Puebla", "12345"))
            assertTrue(store.confirm())
            assertFalse(store.confirm()) // An empty cart cannot create a duplicate order.
            val restored = RowaStore(InstrumentationRegistry.getInstrumentation().targetContext)
            restored.add("basica", "CH")
            restored.updateDelivery(Delivery("Demo", "demo@example.com", "Calle 1", "Puebla", "12345"))
            assertTrue(restored.confirm())
            assertEquals("DEMO-0002", restored.lastOrder!!.folio)
        }
    }
}
