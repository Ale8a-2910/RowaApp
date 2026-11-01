# Verificación de Rowa

## Entorno y resultado

- APK de depuración compilado con Gradle 8.13, AGP 8.9.2, Kotlin 2.1.20 y JDK 17.
- Ejecución en emulador Android 11, API 30, x86_64, con resolución 720 × 1280 y densidad 280.
- **Cuatro pruebas unitarias y tres escenarios de interfaz aprobados.**
- La actividad de entrada `mx.edu.ebc.rowa/.MainActivity` se comprobó en estado `ResumedActivity` después de iniciarla fuera de la actividad de pruebas.
- Sin excepciones de la app en el registro AndroidRuntime revisado.

## Cobertura

| Requisito | Comprobación |
| --- | --- |
| RF01 Catálogo | Carga y consulta del catálogo de ejemplo en Compose. |
| RF02 Búsqueda y filtros | Filtro Pantalones, ausencia de coincidencias, limpieza y búsqueda por nombre. |
| RF03 Detalle | Apertura de la prenda con descripción, precio y tallas. |
| RF04 Favoritos | Guardar, consultar, quitar y cargar de nuevo el estado almacenado. |
| RF05 Agregar | Talla obligatoria, rechazo de talla nula y agregado con talla M. |
| RF06 Carrito | Suma exacta de 2 camisas de $799.00 = $1,598.00; mínimo 1, máximo 10; tallas separadas y eliminación. |
| RF07 Entrega | Campos vacíos, correo inválido, CP incorrecto y CP válido con cero inicial. |
| RF08 Confirmación | Folio DEMO-0001, total correcto, carrito vacío y rechazo de doble confirmación. |
| RF09 Último pedido | Recuperación del pedido almacenado, consulta desde el carrito y siguiente folio DEMO-0002. |

Las pruebas unitarias están en `DomainTest.kt`. Las pruebas Android están en `RowaFlowTest.kt`. Para repetirlas: `./gradlew testDebugUnitTest connectedDebugAndroidTest`, con un dispositivo conectado para la segunda tarea.

## Evidencias y alcance

Las capturas del manual provienen de la vista Compose de la app ejecutada en el emulador, mediante `captureToImage()`. Se captura la aplicación, excluyendo la barra de estado y las ventanas de otros procesos. No se utilizaron mockups como evidencia de ejecución.

El entorno de emulación no dispone de aceleración por hardware y mostró avisos de lentitud de System UI y del launcher durante la preparación. Las pruebas del código y de la vista de la app se completaron. Esto no equivale a una prueba de rendimiento ni a validar todos los modelos de teléfono o versiones de Android.

Los ejemplos de entrega usan nombres y direcciones ficticios. El pedido es local y simulado, sin cargos ni envío de mercancía.
