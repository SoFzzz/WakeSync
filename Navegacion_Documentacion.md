# WakeSync — Navegacion Documentacion

## 1. Propósito
Este documento define la navegación de WakeSync desde una perspectiva de diseño de interacción en Wear OS, priorizando rutas claras, mínima fricción y control inmediato en estados críticos.

## 2. Mapa de navegación principal
- **Inicio** (pantalla raíz)
  - Acción: iniciar Siesta
  - Acción: iniciar Transporte
  - Acción: abrir Configuración
- **Siesta**
  - Acción: detener sesión
  - Acción opcional: simular siesta
- **Transporte**
  - Acción: detener sesión
  - Acción opcional: simular ruta
- **Configuración**
  - Acción: limpiar historial (con confirmación)
  - Acción: solicitar permisos
  - Acción: alternar simulación
  - Acción: volver

## 3. Modelo de navegación orientado a estado
La navegación no depende solo de clics; depende del estado activo del sistema:
- Si `sessionType = NAP` → vista principal de Siesta.
- Si `sessionType = TRANSIT` → vista principal de Transporte.
- Si no hay sesión activa y configuración abierta → vista de Configuración.
- Si no hay sesión activa y configuración cerrada → vista de Inicio.

## 4. Capas transversales de navegación

## 4.1 Diálogo de conflicto de sesión
Aparece por encima de la pantalla base cuando el usuario intenta iniciar un modo mientras otro está activo:
- Opción 1: mantener sesión actual.
- Opción 2: finalizar actual e iniciar nueva.

## 4.2 Overlay de alerta activa
Tiene prioridad máxima sobre cualquier pantalla:
- Muestra estado de alerta.
- Presenta acción inmediata de descarte.
- Bloquea cognitivamente la atención hacia el evento crítico.

## 5. Flujos de usuario críticos

## 5.1 Flujo rápido de Siesta
Inicio → Siesta (1 toque) → Calibración → Monitoreo → Reposo confirmado/alerta → Detener o descartar.

## 5.2 Flujo rápido de Transporte
Inicio → Transporte (1 toque) → Selección/confirmación de destino → Seguimiento en ruta → Alerta de llegada → Descarte o detener.

## 5.3 Flujo de recuperación
Inicio/Configuración → faltan permisos → re-solicitud → retorno a operación normal.

## 6. Patrones de navegación en pantalla circular
- Listas con desplazamiento por corona rotatoria.
- Acciones primarias visibles sin profundizar niveles.
- Diálogos cortos para confirmar acciones destructivas.
- Reducción de pasos para tareas frecuentes (inicio/detener).

## 7. Reglas de consistencia de navegación
- Toda sesión debe poder detenerse de forma directa.
- Toda alerta activa debe poder descartarse con un solo toque.
- Configuración no debe ocultar rutas de retorno.
- Navegación y estado visual deben permanecer sincronizados.

## 8. Edge cases de interacción
- Intento de doble inicio del mismo modo: no debe duplicar sesión.
- Intento de iniciar modo distinto con sesión activa: debe abrir conflicto.
- Estado degradado por sensor: mantiene navegación operativa.
- Estado de alerta: mantiene prioridad de control sin perder contexto funcional.

## 9. Alcance de este documento
Documento enfocado en arquitectura de navegación UX/UI y flujos de interacción.  
No incluye fórmulas matemáticas ni detalles algorítmicos internos.
