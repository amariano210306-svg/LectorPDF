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

## Limitaciones deliberadas de la plataforma

- `PdfRenderer` no expone el outline/árbol estructural global del documento. Los enlaces internos de página no son una tabla de contenidos y no se convierten en capítulos inventados.
- La extracción, búsqueda y selección nativas requieren Android 15 (API 35) en el motor usado. Un PDF escaneado sin capa de texto no puede buscarse, seleccionarse ni leerse con TTS sin OCR.
- La selección y los resaltados visuales no se habilitan hasta disponer de un mapeo estable entre coordenadas PDF, recorte, rotación y zoom. No se simula selección sobre el bitmap.
- La vista dividida con dos motores simultáneos no se habilita en esta fase: duplicaría el presupuesto de bitmaps y necesita una política específica de memoria y ciclo de vida antes de ser segura.
