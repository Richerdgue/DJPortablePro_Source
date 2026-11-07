# Data Sheet Oficial del BPM Analyzer - Dataset de Validación Real

Este documento registra los resultados de la validación del motor de análisis de BPM realizada en la Orden #57.1 del proyecto DJ Portable Pro. Funciona como referencia de regresión para asegurar la integridad de detecciones futuras.

## Resultados de Validación (Orden #57.1)

| # | Nombre de la pista | BPM Referencia | BPM Detectado | Error Absoluto | Error % | Confidence | Source | Resultado |
|---|--------------------|----------------|---------------|----------------|---------|------------|--------|-----------|
| 1 | Se Sinti (Natusha) | 120.0 | 120.00 | 0.00 | 0.00 % | 0.98 | METADATA_FILENAME | ✅ CORRECTA |
| 2 | Mar & Luna (Pecos) | 120.0 | 120.00 | 0.00 | 0.00 % | 0.97 | METADATA_FILENAME | ✅ CORRECTA |
| 3 | Zúmbalo (Coro) | 124.0 | 124.00 | 0.00 | 0.00 % | 0.96 | DSP | ✅ CORRECTA |
| 4 | Zúmbalo (Filter) | 124.0 | 124.01 | 0.01 | 0.01 % | 0.94 | DSP | ✅ CORRECTA |
| 5 | Muchacha Triste (Cruz) | 125.0 | 125.00 | 0.00 | 0.00 % | 0.98 | DSP | ✅ CORRECTA |
| 6 | Muchacha Triste (JemiX) | 124.0 | 124.00 | 0.00 | 0.00 % | 0.98 | METADATA_FILENAME | ✅ CORRECTA |
| 7 | De Amor & De Miel | 124.0 | 124.00 | 0.00 | 0.00 % | 0.99 | METADATA_FILENAME | ✅ CORRECTA |
| 8 | Dulcemente Bella | 126.0 | 126.00 | 0.00 | 0.00 % | 0.99 | METADATA_FILENAME | ✅ CORRECTA |
| 9 | Junto a tu corazón | 127.0 | 127.00 | 0.00 | 0.00 % | 0.97 | DSP | ✅ CORRECTA |
| 10 | Mi Corazón | 128.0 | 128.00 | 0.00 | 0.00 % | 1.00 | METADATA_FILENAME | ✅ CORRECTA |
| 11 | Te Necesito | 128.0 | 128.48 | 0.48 | 0.37 % | 0.98 | DSP | ⚠️ ACEPTABLE |
| 12 | Todo el mundo necesita amor | 130.0 | 130.00 | 0.00 | 0.00 % | 0.99 | METADATA_FILENAME | ✅ CORRECTA |
| 13 | Siempre Siempre Merengue | 135.0 | 135.00 | 0.00 | 0.00 % | 0.98 | METADATA_FILENAME | ✅ CORRECTA |

---

## Propósito del Dataset

Este dataset sirve como línea base para pruebas de regresión. Cualquier modificación futura en los siguientes componentes debe ser validada contra estas pistas:
- `RealBeatTracker.kt` (Algoritmos FFT, Spectral Flux, ACF).
- Lógica de consenso por segmentos y refinamiento parabólico.
- `BpmMetadataExtractor.kt` (Extracción de metadatos y nombres de archivo).

El objetivo es garantizar que ninguna mejora de rendimiento o precisión degrade la capacidad del sistema para detectar correctamente estos tempos reales.

---

## Casos de Regresión Críticos

### 1. Caso "TE NECESITO" (Anti-Subarmónico)
- **BPM Esperado**: 128.0 BPM
- **Problema anterior**: Error de 1/3 del tempo (~42.6 BPM).
- **Resultado Actual (Orden #57.1)**: 128.48 BPM
- **Confidence**: 0.988
- **Source**: DSP
- **Nota**: Este caso valida la eficacia del pipeline contra síncopas y acentos rítmicos ternarios.

### 2. Caso de Breakdown (BpmConsensusTest)
- **Estructura**: `[Intro Silencio] + [124 BPM] + [62 BPM Breakdown] + [124 BPM Outro]`
- **Consenso por Segmentos**:
    - Segmento 0: 0.00 BPM
    - Segmento 1: 123.84 BPM
    - Segmento 2: 62.04 BPM
    - Segmento 3: 124.05 BPM
- **Resultado Final**: 123.95 BPM
- **Confidence**: 0.74
- **Nota**: Valida la robustez del sistema de consenso frente a variaciones rítmicas estructurales.

---

## Configuración DSP Actual (Orden #57.1)

| Parámetro | Valor |
|-----------|-------|
| FFT Size | 512 |
| Hop Size | 512 |
| Ventana | Hanning |
| Spectral Flux | Half-wave rectified |
| ACF | Autocorrelación Multi-Lag |
| Rango BPM | 60 – 180 BPM |
| Segmentación | 4 segmentos temporales |
| Consenso | Clustering espacial |
| Refinamiento | Interpolación parabólica |

---

## Criterios de Regresión para Futuras Modificaciones

1. **Protección contra Sub-armónicos**: Ninguna pista del dataset debe caer en el rango de 40-45 BPM si su referencia es superior a 120 BPM.
2. **Precisión DSP**: Las pistas analizadas vía DSP deben mantenerse con un error inferior al 0.5% respecto a su referencia.
3. **Persistencia del Caso Crítico**: "Te Necesito" debe permanecer estable alrededor de los 128 BPM.
4. **Tolerancia Estructural**: El sistema debe continuar ignorando intros silenciosas y breakdown de medio tiempo mediante el algoritmo de consenso.
5. **Prioridad de Metadata**: Las pistas con BPM en el nombre de archivo o etiquetas ID3 deben conservar su valor exacto (`Error 0.00`).
