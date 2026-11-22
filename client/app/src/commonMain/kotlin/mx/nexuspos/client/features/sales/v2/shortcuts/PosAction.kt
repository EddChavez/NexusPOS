package mx.nexuspos.client.features.sales.v2.shortcuts

enum class PosAction(
    val labelWin: String,
    val labelMac: String,
    val description: String
) {
    SWITCH_TICKET("F5", "⌘5", "Cambiar de venta en progreso"),
    CHECKOUT("F12", "⌘↵", "Cobrar venta actual"),
    FAST_SALE("F1 / Ctrl+R", "⌘R", "Venta rápida"),
    PRICE_CHECKER("F9", "⌘F9", "Verificador de precios"),
    REPRINT_TICKET("Ctrl+P / Alt+P", "⌘P", "Reimprimir último ticket"),
    FOCUS_SEARCH("F2 / Ctrl+F", "⌘F", "Enfocar buscador"),
    NAVIGATE_UP("↑", "↑", "Navegar hacia arriba"),
    NAVIGATE_DOWN("↓", "↓", "Navegar hacia abajo"),
    CONFIRM_SELECTION("Enter", "Enter", "Confirmar selección"),
    ESCAPE("Esc", "Esc", "Cerrar / Cancelar"),
    QTY_INCREMENT("+", "+", "Aumentar cantidad"),
    QTY_DECREMENT("-", "-", "Reducir cantidad"),
    DELETE_ITEM("Supr", "⌫", "Eliminar artículo"),
    ASSIGN_CUSTOMER("F11 / Ctrl+L", "⌘L", "Asignar cliente"),
    GLOBAL_DISCOUNT("F10 / Ctrl+D", "⌘D", "Descuento global"),
    CASH_IN("F7", "⌘7", "Entrada de efectivo"),
    CASH_OUT("F8", "⌘8", "Salida de efectivo"),
    OPEN_DRAWER("Ctrl+K / Alt+C", "⌘K", "Abrir cajón de dinero");

    fun getLabel(os: HostOs): String = if (os == HostOs.MACOS) labelMac else labelWin
}
