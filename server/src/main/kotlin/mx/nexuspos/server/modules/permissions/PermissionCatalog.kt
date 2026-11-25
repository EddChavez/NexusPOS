package mx.nexuspos.server.modules.permissions

import mx.nexuspos.contracts.identity.PermissionDto

internal object PermissionCatalog {
    val definitions = buildList {
        fun group(group: String, vararg actions: Pair<String, String>) = actions.forEach { (key, label) ->
            add(PermissionDto(key, group, label))
        }
        group("Usuarios y roles",
            "users.view" to "Consultar usuarios", "users.create" to "Crear usuarios",
            "users.edit" to "Editar usuarios y cambiar PIN", "users.deactivate" to "Desactivar usuarios",
            "users.reactivate" to "Reactivar usuarios", "users.assign_role" to "Asignar roles",
            "roles.manage" to "Administrar roles y permisos")
        group("Configuración",
            "settings.profile" to "Editar negocio y sucursal", "settings.operating" to "Cambiar políticas de operación",
            "settings.commerce" to "Cambiar preferencias comerciales", "registers.manage" to "Administrar cajas", "devices.manage" to "Administrar dispositivos")
        group("Hardware",
            "hardware.printer" to "Configurar impresora y cajón",
            "hardware.scale" to "Configurar báscula",
            "hardware.test" to "Ejecutar pruebas de hardware")
        group("Clientes",
            "customers.view" to "Consultar clientes", "customers.create" to "Crear clientes",
            "customers.update" to "Editar ficha", "customers.deactivate" to "Desactivar clientes",
            "customers.reactivate" to "Reactivar clientes", "customers.credit.configure" to "Habilitar crédito y cambiar límites",
            "customers.credit.payment" to "Registrar abonos", "customers.statement.view" to "Consultar estados de cuenta",
            "customers.loyalty.view" to "Consultar monedero", "customers.loyalty.adjust" to "Ajustar monedero")
        group("Carrito",
            "cart.create" to "Crear nueva venta", "cart.add" to "Agregar artículo", "cart.remove" to "Quitar artículo",
            "cart.increase" to "Aumentar cantidad", "cart.decrease" to "Disminuir cantidad")
        group("Ventas",
            "sales.checkout" to "Cobrar venta", "sales.quick" to "Cobrar artículos libres",
            "sales.customer" to "Asignar cliente", "sales.discount" to "Aplicar descuento",
            "sales.price.manual" to "Modificar precio de venta manualmente",
            "sales.pay.cash" to "Cobrar en efectivo", "sales.pay.card" to "Cobrar con tarjeta",
            "sales.pay.transfer" to "Cobrar por transferencia", "sales.pay.credit" to "Cobrar a crédito",
            "sales.pay.loyalty" to "Cobrar con monedero", "sales.pay.mixed" to "Cobrar con pago mixto", "sales.held.view" to "Consultar ventas estacionadas",
            "sales.held.park" to "Estacionar ventas", "sales.held.retrieve" to "Recuperar ventas estacionadas",
            "sales.held.discard" to "Descartar ventas estacionadas")
        for (level in 2..5) {
            group("Ventas", "sales.price.$level" to "Asignar Precio $level")
            group("Productos", "products.price.$level" to "Editar Precio $level")
        }
        group("Transacciones",
            "transactions.own" to "Consultar ventas propias", "transactions.all" to "Consultar ventas de otros operadores",
            "transactions.closed" to "Consultar ventas de turnos cerrados", "transactions.cancel" to "Cancelar ventas",
            "transactions.refund" to "Devolver artículos", "transactions.reprint" to "Reimprimir comprobante",
            "transactions.share" to "Compartir comprobante")
        group("Productos",
            "products.view" to "Consultar catálogo", "products.create" to "Crear productos",
            "products.edit" to "Editar productos", "products.cost.view" to "Consultar costos",
            "products.cost.edit" to "Cambiar costos", "products.price.edit" to "Editar precio público",
            "products.tax.edit" to "Editar IVA", "products.tiers.edit" to "Modificar escalas por cantidad",
            "products.stock.edit" to "Modificar datos de inventario", "products.unit.edit" to "Cambiar unidad",
            "products.kit.edit" to "Gestionar kits", "products.deactivate" to "Desactivar productos",
            "products.reactivate" to "Reactivar productos", "products.import" to "Importar catálogo",
            "products.export" to "Exportar catálogo", "departments.edit" to "Crear o modificar departamentos",
            "departments.delete" to "Eliminar departamentos")
        group("Inventario",
            "inventory.stock" to "Ver existencias", "inventory.receive" to "Registrar entradas",
            "inventory.adjust" to "Ajustar inventario", "inventory.movements" to "Consultar movimientos",
            "inventory.report" to "Consultar reporte de inventario", "inventory.valuation" to "Ver valuación",
            "inventory.lots" to "Gestionar lotes", "inventory.write_off" to "Registrar merma de lotes")
        group("Turnos y caja",
            "cash.limit.approve" to "Autorizar efectivo sobre el límite", "cash.open" to "Abrir turno", "cash.handoff" to "Relevar operador", "cash.view" to "Consultar turno activo", "cash.close" to "Cerrar turno",
            "cash.in" to "Registrar entrada", "cash.out" to "Registrar salida", "cash.history" to "Consultar historial de cortes",
            "cash.detail" to "Ver corte detallado", "cash.audit" to "Consultar movimientos de caja",
            "cash.reverse" to "Revertir movimientos de efectivo", "cash.drawer.open" to "Abrir cajón manualmente")
        group("Compras",
            "purchases.view" to "Consultar compras", "purchases.receive" to "Recibir compras",
            "purchases.return" to "Devolver a proveedor", "suppliers.edit" to "Crear o modificar proveedores")
        group("Reportes", "reports.sales" to "Consultar reportes de ventas", "reports.margin" to "Consultar costos, margen y utilidad",
            "reports.customer" to "Ver ventas por cliente", "reports.export" to "Exportar reportes")
    }.map { definition ->
        val parent = when {
            definition.key.startsWith("sales.pay.") -> "sales.checkout"
            definition.key in setOf("transactions.all", "transactions.closed") -> "transactions.own"
            definition.key in setOf("reports.margin", "reports.customer", "reports.export") -> "reports.sales"
            definition.key == "inventory.valuation" -> "inventory.report"
            definition.key == "cash.detail" -> "cash.history"
            definition.key == "products.cost.edit" -> "products.cost.view"
            else -> null
        }
        definition.copy(parent = parent)
    }
    val keys = definitions.map(PermissionDto::key).toSet()

    private val cashier = setOf(
        "products.view", "inventory.stock", "customers.view", "customers.credit.payment", "customers.statement.view",
        "customers.loyalty.view", "sales.checkout", "sales.quick", "sales.customer", "sales.discount", "sales.pay.cash",
        "sales.pay.card", "sales.pay.transfer", "sales.pay.loyalty", "sales.pay.credit", "sales.pay.mixed", "sales.held.view",
        "sales.held.park", "sales.held.retrieve", "sales.held.discard", "transactions.own", "cash.open", "cash.view", "cash.close",
        "cash.in", "cash.out", "cash.handoff", "cash.drawer.open",
        "cart.create", "cart.add", "cart.remove", "cart.increase", "cart.decrease",
        "transactions.reprint", "transactions.share"
    )
    private val supervisor = cashier + (keys - setOf(
        "users.view", "users.create", "users.edit", "users.deactivate", "users.reactivate", "users.assign_role", "roles.manage",
        "settings.profile", "settings.operating", "settings.commerce", "registers.manage", "devices.manage", "products.cost.edit",
        "customers.deactivate", "customers.reactivate"
    ))
    val builtIns = linkedMapOf("ADMIN" to "Administrador", "SUPERVISOR" to "Supervisor", "CASHIER" to "Cajero")
    fun builtIn(role: String): Set<String>? = when (role) {
        "ADMIN" -> keys
        "SUPERVISOR" -> supervisor
        "CASHIER" -> cashier
        else -> null
    }
}
