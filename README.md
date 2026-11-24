# Gastos VORDER

App Android para administrar gastos que **lee las notificaciones de billeteras virtuales y bancos** y registra solos pagos, transferencias e ingresos.

## Qué hace
- **Registro**: lista del mes con pagos, transferencias, gastos e ingresos; resumen (gastos / ingresos / balance), filtros por tipo, alta manual, edición y borrado.
- **Consumos**: una tarjeta por billetera con su total, porcentaje del consumo, desglose por tipo y por categoría, y el detalle de cada movimiento.
- **Ajustes**: estado del permiso, elección de apps a leer, exportar CSV, botón de prueba.
- Todo se guarda **solo en el teléfono** (SQLite). No hay servidores ni internet.

## Primera vez
1. Instalá el APK (release más reciente de este repo).
2. Abrí la app → **Ajustes** → *Abrir acceso a notificaciones* → activá «Gastos VORDER».
3. Si Android bloquea la opción (apps instaladas por APK): Info de la app → ⋮ → **Permitir ajustes restringidos**, y reintentá.
4. En Ajustes revisá que estén activadas tus billeteras y bancos.

## Cómo reconoce los movimientos
Busca frases típicas ("Pagaste $…", "Recibiste…", "Transferiste…", "Compra aprobada…") y un monto con `$`. Ignora promos y saldos. Si una notificación no se reconoce bien, se puede cargar/editar a mano. Las reglas están en `Parser.kt`.

## Compilación
GitHub Actions compila el APK en cada push a `main` y lo publica en *Releases*.
