# WakeSync — Jerarquía Documentación

## 1. Propósito
Este documento describe la jerarquía del diseño de WakeSync, enfocada en cómo se organiza la experiencia visual y de interacción en niveles de abstracción: sistema, pantallas, componentes y tokens.

> **Nota de alcance:** Aunque este documento se llama “Jerarquía”, en esta versión queda estrictamente orientado a diseño (no incluye fórmulas matemáticas).

## 2. Jerarquía del sistema de diseño

## 2.1 Nivel 1 — Fundaciones visuales
Base compartida para toda la interfaz:
- Paleta de color por estados funcionales.
- Escala tipográfica para lectura rápida.
- Espaciado y tamaños mínimos táctiles.
- Tema oscuro para contexto wearable OLED.

## 2.2 Nivel 2 — Tokens de diseño
Estructura recomendada de tokens:
1. **Globales (crudos):** valores base (HEX, dp, sp).
2. **Semánticos:** intención funcional (estado, alerta, éxito, advertencia).
3. **De componente:** aplicación concreta en cada patrón UI.

Esta jerarquía permite mantener coherencia y facilitar evolución sin romper comportamiento visual.

## 2.3 Nivel 3 — Patrones de componente
Componentes principales y su rol jerárquico:
- **Estructurales:** contenedor principal, listas escaladas, tarjetas de modo.
- **Informativos:** métricas primarias, indicadores de estado, banners de advertencia.
- **Acción:** botones de inicio/detención, selección de destino, descarte de alerta.
- **Críticos:** overlay de alerta activa con prioridad máxima de interacción.

## 2.4 Nivel 4 — Pantallas
Pantallas base del sistema:
- Inicio
- Siesta
- Transporte
- Configuración

Cada pantalla reutiliza fundaciones y tokens, adaptando la intensidad visual al contexto de uso.

## 3. Jerarquía visual por prioridad de información

## 3.1 Prioridad alta
- Estado actual del modo.
- Métrica principal del flujo (tiempo restante o distancia).
- Alerta activa y acción de descarte.

## 3.2 Prioridad media
- Indicadores secundarios (score, FC, velocidad, radio dinámico mostrado como dato de apoyo).
- Mensajes de confirmación o transición.

## 3.3 Prioridad baja
- Texto descriptivo auxiliar.
- Información de versión y soporte.

## 4. Jerarquía de interacción
- **Primaria:** iniciar modo, detener modo, descartar alerta.
- **Secundaria:** seleccionar destino, alternar simulación, limpiar historial.
- **Contextual:** conceder permisos, regresar de configuración, cancelar diálogo.

## 5. Jerarquía de estados de diseño
- **Operación normal:** inicio, monitoreo, ruta.
- **Operación enfocada:** calibración, reposo confirmado.
- **Operación crítica:** alerta activa.
- **Operación degradada:** sensor no disponible, permisos faltantes.

Cada categoría define intensidad visual, uso de color y prominencia de controles.

## 6. Jerarquía de consistencia entre documentos
- **Diseño Documentación:** define principios, tokens, apariencia y ergonomía.
- **Jerarquía Documentación:** define capas de organización visual e interacción.
- **Navegación Documentación:** define recorridos y transiciones entre pantallas.

## 7. Reglas de gobernanza del diseño
- No introducir colores fuera del sistema semántico sin justificación.
- Mantener objetivos táctiles mínimos en toda nueva acción.
- Preservar significado de color por estado.
- Evitar introducir componentes que compitan con la capa de alerta crítica.

## 8. Alcance de este documento
Documento orientado a estructura de diseño y organización de experiencia.  
No cubre cálculo interno, modelado matemático ni definición algorítmica.
