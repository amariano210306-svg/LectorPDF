# Matriz técnica del lector PDF

## Conservado y validado

- Desplazamiento vertical continuo virtualizado y modo horizontal por página.
- Página dominante calculada por área visible, porcentaje con offset y restauración página + offset.
- Ajuste al ancho/página, comportamiento landscape, zoom, pan, doble toque y rotación.
- Miniaturas bajo demanda, slider, salto directo de página y búsqueda nativa en Android 15+.
- Recorte visual automático, brillo de Activity, orientación, pantalla activa, botones de volumen y modo enfoque.
- Sesiones, progreso debounced y guardado forzado en `onPause`/`onStop`.

## Añadido en esta fase

- Caché en bytes inmutables y cierre idempotente del motor sin reciclar bitmaps compartidos con Compose.
- Marcadores reales con página y offset, navegación y eliminación.
- Recorte manual por los cuatro lados, persistido por documento.
- Temas Día, Noche, Sepia y Consola mediante `ColorMatrix`.
- Gesto de brillo reservado al borde izquierdo con indicador de porcentaje.
- Vista de doble página seleccionable.
- Resultados de búsqueda con conteo y snippet cuando Android expone texto.
- Text To Speech en Android 15+ por fragmentos/páginas, con pausa, avance, velocidad y tono persistentes.
- Exportación Markdown mediante SAF de marcadores, notas y resaltados almacenados.
- Portadas PDF reales bajo demanda con caché de disco limitada.

## Pulido visual y herramientas avanzadas

- Identidad visible `Folio PDF`, icono original libro/documento + marcador, Adaptive Icon, icono monocromático y splash nativo.
- Gestos rápidos configurables dentro del contenido seguro: brillo en franja izquierda, ciclo Día → Sepia → Noche → Consola y marcador en esquina derecha.
- Feedback Compose temporal para brillo, tema, marcador, recorte y ajuste; indicador accesible de marcador actual.
- Selección textual real en Android 15+ mediante `PdfRenderer.Page.selectContent(SelectionBoundary, SelectionBoundary)`.
- Transformaciones puras PDF ↔ viewport que contemplan crop normalizado, rotación 0/90/180/270, fit, zoom y pan.
- Menú contextual propio con copiar, resaltar, cita, traducir, diccionario, compartir, nota, subrayado y búsquedas.
- Resaltados persistentes en amarillo, verde, azul, rosa y morado; subrayados, citas y notas como overlays vectoriales sin modificar el PDF.
- Panel `Notas y resaltados`, edición al tocar una anotación y navegación precisa a página + offset.
- Diccionario, traducción y búsqueda explícita mediante WebView HTTPS restringida; JavaScript y acceso a archivos/contenido desactivados.
- Recorte `Sin recorte / Automático / Manual` y ajuste `Página completa / Ancho / Contenido` con render acorde a la ampliación.
- Room 4 con migración no destructiva 3 → 4 para persistir el modo de recorte conservando progreso y márgenes previos.

## Limitaciones deliberadas de la plataforma

- `PdfRenderer` no expone el outline/árbol estructural global del documento. Los enlaces internos de página no son una tabla de contenidos y no se convierten en capítulos inventados.
- La extracción, búsqueda y selección nativas requieren Android 15 (API 35) en el motor usado. Un PDF escaneado sin capa de texto no puede buscarse, seleccionarse ni leerse con TTS sin OCR.
- En Android 14 o anterior no existe selección textual pública equivalente en `PdfRenderer`; se informa la limitación y no se simula selección sobre el bitmap.
- Un documento escaneado sin capa textual conserva lectura/render/crop, pero no ofrece selección, búsqueda o TTS; OCR permanece fuera de esta fase.
- La vista dividida con dos motores simultáneos no se habilita en esta fase: duplicaría el presupuesto de bitmaps y necesita una política específica de memoria y ciclo de vida antes de ser segura.
- EPUB/Readium Navigator permanece deliberadamente sin implementar durante esta fase.
