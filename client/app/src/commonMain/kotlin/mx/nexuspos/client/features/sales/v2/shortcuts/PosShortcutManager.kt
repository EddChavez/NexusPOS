package mx.nexuspos.client.features.sales.v2.shortcuts

import androidx.compose.ui.input.key.*

/**
 * Gestor Global de Atajos de Teclado Multiplataforma (Windows / macOS) para NexusPOS.
 * Aplica la Matriz de Prioridad de Eventos en 4 capas estrictas para evitar colisiones.
 */
class PosShortcutManager(
    val os: HostOs = HostOsDetector.current,
    val hasActiveModal: () -> Boolean,
    val hasDropdownOpen: () -> Boolean,
    val isInputFocused: () -> Boolean,
    val onAction: (PosAction) -> Unit
) {
    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        val isMac = os == HostOs.MACOS
        val cmdOrCtrl = if (isMac) event.isMetaPressed else event.isCtrlPressed

        // =========================================================================
        // 1. CAPA 1: MODAL ACTIVO EN PANTALLA (PRIORIDAD MÁXIMA)
        // =========================================================================
        if (hasActiveModal()) {
            if (event.key == Key.Escape) {
                onAction(PosAction.ESCAPE)
                return true
            }
            // Bloquea atajos del fondo para aislar el modal
            return false
        }

        // =========================================================================
        // 2. CAPA 2: DESPLEGABLE OMNIBOX ABIERTO
        // =========================================================================
        if (hasDropdownOpen()) {
            when (event.key) {
                Key.DirectionUp -> {
                    onAction(PosAction.NAVIGATE_UP)
                    return true
                }
                Key.DirectionDown -> {
                    onAction(PosAction.NAVIGATE_DOWN)
                    return true
                }
                Key.Enter -> {
                    onAction(PosAction.CONFIRM_SELECTION)
                    return true
                }
                Key.Escape -> {
                    onAction(PosAction.ESCAPE)
                    return true
                }
            }
        }

        // =========================================================================
        // 3. CAPA 3 Y 4: AISLAMIENTO DE INPUTS (Carácteres únicos: +, -, Delete)
        // =========================================================================
        if (!isInputFocused()) {
            when (event.key) {
                Key.Plus, Key.NumPadAdd -> {
                    onAction(PosAction.QTY_INCREMENT)
                    return true
                }
                Key.Minus, Key.NumPadSubtract -> {
                    onAction(PosAction.QTY_DECREMENT)
                    return true
                }
                Key.Delete -> {
                    onAction(PosAction.DELETE_ITEM)
                    return true
                }
                Key.Backspace -> {
                    if (isMac && !cmdOrCtrl) {
                        onAction(PosAction.DELETE_ITEM)
                        return true
                    }
                }
            }
        }

        // =========================================================================
        // 4. ACCIONES OPERATIVAS GLOBALES CON MODIFICADORES Y TECLAS DE FUNCIÓN
        // =========================================================================
        return when {
            // A. Cambiar ticket / pestañas: Win F5, Mac Cmd+5
            (!isMac && event.key == Key.F5) || (isMac && cmdOrCtrl && event.key == Key.Five) -> {
                onAction(PosAction.SWITCH_TICKET)
                true
            }

            // B. Cobrar venta actual: Win F12, Mac Cmd+Enter o F12
            event.key == Key.F12 || (isMac && cmdOrCtrl && event.key == Key.Enter) -> {
                onAction(PosAction.CHECKOUT)
                true
            }

            // C. Venta rápida: Win F1 o Ctrl+R, Mac Cmd+R
            (!isMac && event.key == Key.F1) || (cmdOrCtrl && event.key == Key.R) -> {
                onAction(PosAction.FAST_SALE)
                true
            }

            // D. Verificador de precios: Win F9, Mac Cmd+F9
            (!isMac && event.key == Key.F9) || (isMac && cmdOrCtrl && event.key == Key.F9) -> {
                onAction(PosAction.PRICE_CHECKER)
                true
            }

            // E. Reimprimir último ticket: Win Ctrl+P o Alt+P, Mac Cmd+P
            (cmdOrCtrl && event.key == Key.P) || (!isMac && event.isAltPressed && event.key == Key.P) -> {
                onAction(PosAction.REPRINT_TICKET)
                true
            }

            // F. Enfocar buscador (Omnibox): Win F2 o Ctrl+F, Mac Cmd+F
            event.key == Key.F2 || (cmdOrCtrl && event.key == Key.F) -> {
                onAction(PosAction.FOCUS_SEARCH)
                true
            }

            // G. Asignar cliente: Win F11 o Ctrl+L, Mac Cmd+L
            (!isMac && event.key == Key.F11) || (cmdOrCtrl && event.key == Key.L) -> {
                onAction(PosAction.ASSIGN_CUSTOMER)
                true
            }

            // H. Descuento global: Win F10 o Ctrl+D, Mac Cmd+D
            (!isMac && event.key == Key.F10) || (cmdOrCtrl && event.key == Key.D) -> {
                onAction(PosAction.GLOBAL_DISCOUNT)
                true
            }

            // I. Entrada de efectivo: Win F7, Mac Cmd+7
            (!isMac && event.key == Key.F7) || (isMac && cmdOrCtrl && event.key == Key.Seven) -> {
                onAction(PosAction.CASH_IN)
                true
            }

            // J. Salida de efectivo: Win F8, Mac Cmd+8
            (!isMac && event.key == Key.F8) || (isMac && cmdOrCtrl && event.key == Key.Eight) -> {
                onAction(PosAction.CASH_OUT)
                true
            }

            // K. Abrir cajón de dinero: Win Ctrl+K o Alt+C, Mac Cmd+K
            (cmdOrCtrl && event.key == Key.K) || (!isMac && event.isAltPressed && event.key == Key.C) -> {
                onAction(PosAction.OPEN_DRAWER)
                true
            }

            // L. Escape general para cancelar
            event.key == Key.Escape -> {
                onAction(PosAction.ESCAPE)
                true
            }

            else -> false
        }
    }
}
