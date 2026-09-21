# WakeSync — Diseño Documentación

## 1. Propósito
Este documento define el sistema de diseño visual y de interacción de WakeSync para Wear OS, priorizando legibilidad inmediata, ergonomía circular y consistencia entre estados críticos de uso (Siesta, Transporte y Alerta).

## 2. Principios de diseño
- **Glanceability primero:** la información crítica debe entenderse en menos de 3 segundos.
- **Interacción mínima:** acciones principales en 1–2 toques.
- **Silencio contextual:** evitar estímulos innecesarios durante reposo.
- **Contraste alto para pantalla OLED circular:** negro profundo y acentos por estado.
- **Consistencia semántica:** cada color y componente representa un estado funcional estable.

## 3. Tokens del sistema de diseño (según niveles de abstracción)
Basado en la regla de niveles de abstracción en tokens:

### 3.1 Tokens globales (valores crudos)
Fuente base de valores visuales:
- Colores HEX (ej. `#00E5FF`, `#FFD600`, `#7C4DFF`, `#00E676`, `#FF1744`, `#FF9100`)
- Escala tipográfica (9sp, 10sp, 11sp, 12sp, 13sp, 14sp, 15sp, 16sp, 18sp, 24sp)
- Espaciado base (2dp, 4dp, 6dp, 8dp, 10dp, 12dp, 16dp, 24dp)
- Tamaños mínimos táctiles (48dp x 48dp)

### 3.2 Tokens semánticos (propósito UI)
Asignación por significado:
- `color.estado.inicio` → blanco/carbón
- `color.estado.calibrando` → cian
- `color.estado.monitoreando` → ámbar
- `color.estado.reposo_confirmado` → índigo
- `color.estado.transporte` → verde
- `color.estado.alerta` → coral
- `color.estado.sensor_advertencia` → naranja

### 3.3 Tokens de componente (específicos)
Aplicación concreta en UI:
- `card.home.nap.container` usa superficie carbón
- `card.home.transit.container` usa superficie carbón
- `progress.nap.calibrando` usa cian
- `progress.nap.monitoreo` usa ámbar
- `progress.nap.reposo` usa índigo
- `progress.transit.ruta` usa verde
- `overlay.alerta.urgente` usa coral con pulso

## 4. Sistema de color (implementación actual)
Correspondencia principal con `WakeSyncColors`:
- **Inicio:** `White`, `Carbon`, `CarbonSurface`, `TextMuted`, `PureBlack`
- **Calibración:** `CyanBasal`, `CyanMuted`
- **Monitoreo Siesta:** `AmberMonitoring`, `AmberMuted`
- **Reposo Confirmado:** `IndigoDeepRest`, `IndigoMuted`
- **Transporte en Ruta:** `GreenTransit`, `GreenMuted`
- **Alerta Activa:** `CoralAlert`, `CoralMuted`
- **Sensor no disponible:** `OrangeWarning`, `OrangeMuted`

## 5. Tipografía y legibilidad
- Títulos de estado: peso alto, 12–16sp.
- Métricas principales (distancia/temporizador): 24sp.
- Soporte contextual y etiquetas secundarias: 9–11sp.
- Prioridad visual en columna central con jerarquía descendente: título → métrica principal → metadatos → acciones.

## 6. Componentes UI normativos
- **Cards de modo** en inicio (Siesta y Transporte).
- **Anillos de progreso circulares** para estados temporales y de proximidad.
- **Banner de advertencia** no bloqueante para sensor no disponible.
- **Overlay de alerta activa** con botón masivo de descarte.
- **Botones circulares** de acción para detener, confirmar, cancelar y navegación secundaria.

## 7. Estados visuales clave
- **Inicio (Idle):** neutral, sin vibración, foco en elección de modo.
- **Calibrando Basal:** cian + progreso rotatorio.
- **Siesta Monitoreando:** ámbar + score/estado.
- **Reposo Confirmado:** índigo + temporizador regresivo.
- **Transporte en Ruta:** verde + distancia y velocidad.
- **Alerta Activa:** coral + máxima visibilidad + descarte inmediato.
- **Sensor No Disponible:** naranja + advertencia no bloqueante.

## 8. Ergonomía Wear OS
- Objetivos táctiles mínimos de 48dp x 48dp.
- Uso de rotary input para listas y selección sin ocultar contenido.
- Botón de descarte en alerta con tamaño superior al mínimo para toque ciego.
- Prioridad de interacción con una mano y en movimiento.

## 9. Criterios de calidad visual
- Sin recortes de texto en pantalla circular.
- Contraste suficiente en estados críticos.
- Consistencia de color por estado entre pantallas.
- Feedback visual y háptico coherente con el nivel de urgencia.

## 10. Alcance de este documento
Este documento se enfoca exclusivamente en diseño UI/UX y sistema visual.  
No incluye fórmulas matemáticas ni modelado interno de cálculo.
