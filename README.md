# WakeSync

**Asistente de descanso y movilidad para smartwatches Wear OS.**

WakeSync es una aplicación nativa e independiente (*standalone*) para relojes Wear OS que te ayuda a
**descansar sin quedarte dormido de más** y a **no pasarte de tu parada** en el transporte público.
Funciona por completo en el reloj, sin necesidad de un teléfono.

> Proyecto académico desarrollado en la Universidad Cooperativa de Colombia (UCC) — Diseño, quinto semestre.

---

## ¿Qué hace?

WakeSync tiene **dos modos**, y solo uno puede estar activo a la vez.

### 🛏️ Modo Siesta (MicroNap)

Un temporizador fijo de siesta suena demasiado pronto si tardas en relajarte, o demasiado tarde si ya
caíste en sueño profundo. WakeSync se adapta a ti:

1. **Calibra** tu frecuencia cardíaca en reposo durante 20 segundos.
2. **Monitorea** tu pulso y tu quietud para estimar cuándo entras en reposo profundo.
3. Al confirmarlo, inicia una **cuenta regresiva de 15 minutos** y te despierta con una vibración suave.

Opcionalmente puedes fijar un destino: si viajas mientras descansas, la llegada a tu parada tiene
prioridad sobre el temporizador.

### 🚌 Modo Transporte (TransitNudge)

1. **Eliges tu destino** desde el reloj: buscándolo por voz o teclado, o marcándolo en un mapa.
2. WakeSync sigue tu posición por GPS y calcula un **radio de alerta dinámico** según tu velocidad.
3. Cuando te acercas, el reloj **vibra para avisarte** que es hora de bajar.

Una vez fijado el destino, el seguimiento y la alerta funcionan **sin conexión a internet**.

### ✨ Además

- **Resumen con IA:** al terminar cada sesión, un breve comentario generado por inteligencia
  artificial (máximo 140 caracteres) sobre cómo te fue.
- **Historial:** lista de tus sesiones anteriores, con su detalle y su resumen guardado.
- **Modo Simulación:** reproduce sensores y rutas simuladas para probar o demostrar la app en el
  emulador, sin reloj físico.
- **Modo ambiente:** pantalla atenuada de bajo consumo durante las sesiones.
- **Diseño "reloj clásico":** interfaz calmada en azul marino, oro rosa y crema, con contraste
  accesible (WCAG AA).

> **Aviso:** WakeSync es una herramienta de bienestar y productividad, **no un dispositivo médico**.
> No diagnostica trastornos del sueño; los estados de reposo son estimaciones heurísticas.

---

## ¿Cómo funciona?

```
            ┌──────────────── Reloj Wear OS ────────────────┐
            │  Modo Siesta  ·  Modo Transporte  ·  Historial │
            │  Sensores (pulso, acelerómetro, GPS)           │
            │  Estimador de reposo y geocerca (locales)      │
            │  Vibraciones de alerta                         │
            └──────────────────────┬────────────────────────┘
                                   │ HTTPS (WiFi/LTE del reloj)
                    ┌──────────────▼───────────────┐
                    │  wakesync-gateway            │
                    │  Cloudflare Worker sin estado │
                    └──────┬───────────────┬───────┘
                           │               │
                       Mapbox          DeepSeek
                 (búsqueda y mapas)   (resúmenes IA)
```

- **Estimador de reposo:** combina la caída de la frecuencia cardíaca respecto a tu basal con la
  quietud del movimiento:
  `score = 0.5 · max(0, (HR_base − HR_actual) / HR_base) + 0.5 · (1 − min(1, SVM_media / 2.5))`.
  Con `score ≥ 0.60` en dos evaluaciones seguidas se considera reposo profundo.
- **Radio de alerta del transporte:** `R_alert = max(250 m, v · T_reacción + v² / (2 · |a_frenado|))`,
  con un tiempo de reacción de 60 s (90 s si estás en reposo profundo).
- **Privacidad:** las claves de Mapbox y DeepSeek viven solo en el backend, nunca en la app. El reloj
  solo envía al backend lo mínimo necesario, y tu historial se guarda únicamente en el reloj.

---

## Tecnologías

| Parte | Tecnología |
|---|---|
| App | Kotlin · Jetpack Compose for Wear OS + Material 3 · Horologist |
| Sensores | Health Services API · Fused Location |
| Concurrencia y datos | Corrutinas + `StateFlow` · DataStore |
| Red | OkHttp + kotlinx.serialization |
| Backend | TypeScript en Cloudflare Workers (plan gratuito) |
| Servicios externos | Mapbox (Search Box, Geocoding v6, Static Images) · DeepSeek |
| Plataforma mínima | Wear OS 3.0+ (API 30), target SDK 34 |

---

## Estructura del repositorio

```
WakeSync/
├── app/                                ← aplicación Wear OS
│   └── src/main/java/com/wakesync/
│       ├── core/        infraestructura, estado de sesión y contratos
│       ├── sensors/     sensores reales y simulados
│       ├── ai/          estimador de reposo
│       ├── sleep/       Modo Siesta
│       ├── transit/     Modo Transporte
│       ├── alerts/      vibraciones
│       ├── places/      selector de destino (Mapbox vía backend)
│       ├── insights/    resúmenes con IA (DeepSeek vía backend)
│       ├── network/     cliente HTTP único hacia el backend
│       └── ui/          interfaz en Compose
├── backend/                            ← Worker wakesync-gateway (TypeScript)
└── WAKESYNC_MASTER_DOCUMENTATION.md    ← especificación completa (SRS)
```

---

## Cómo ejecutarlo

### Requisitos

- Android Studio con el SDK de Android y un emulador **Wear OS Large Round** (API 34).
- JDK 21.
- El backend `wakesync-gateway` desplegado (ver más abajo).

### 1. Configurar la conexión al backend

Crea `local.properties` en la raíz del proyecto (no se sube al repositorio) con:

```properties
wakesync.backendBaseUrl=https://<tu-worker>.workers.dev
wakesync.backendAppToken=<token-de-la-app>
```

### 2. Compilar, probar e instalar

```bash
./gradlew clean testDebugUnitTest
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

También puedes abrir el proyecto en Android Studio, elegir el emulador Wear OS y pulsar **Run ▶**.

### 3. Probarlo sin reloj físico

Activa el **Modo Simulación** en *Ajustes* dentro de la app: el pulso, el movimiento y la ruta se
simulan, y el botón de avance rápido acelera la siesta o el viaje para ver la alerta en segundos.

### Backend

```bash
cd backend
npm install
npx wrangler login
npx wrangler secret put MAPBOX_ACCESS_TOKEN
npx wrangler secret put DEEPSEEK_API_KEY
npx wrangler secret put APP_TOKEN
npx wrangler deploy
```

Endpoints principales: `GET /v1/health`, `POST /v1/places/autocomplete`, detalle y geocodificación
de lugares, mapa estático e `POST /v1/insights`. El contrato completo está en el **Apéndice F** de
la documentación.

---

## Instalar en un reloj real

1. En el reloj: activa las **Opciones de desarrollador**, la **Depuración ADB** y la
   **Depuración inalámbrica**.
2. Vincula y conecta el PC: `adb pair <ip>:<puerto>` y luego `adb connect <ip>:<puerto>`.
3. Instala el APK: `adb install -r WakeSync-1.0.0.apk`.

Requiere un reloj con **Wear OS 3 o superior** (por ejemplo, Galaxy Watch 4 o posterior, o Pixel Watch).

---

## Documentación

Toda la especificación del sistema —requisitos, arquitectura, fórmulas, criterios de aceptación y
contrato del backend— está en [`WAKESYNC_MASTER_DOCUMENTATION.md`](WAKESYNC_MASTER_DOCUMENTATION.md).
