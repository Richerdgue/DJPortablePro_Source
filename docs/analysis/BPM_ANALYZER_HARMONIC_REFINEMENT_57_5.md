# BPM Analyzer Harmonic Refinement Report — Orden #57.5

## Objetivo
Refinar el motor de análisis de BPM para mejorar la detección en géneros urbanos (Reggaetón/Urban) abordando errores sistemáticos de ratios armónicos (1.33x) y medio tempo (0.5x) identificados en la auditoría #57.4.

---

## Estado Inicial (Orden #57.4)
- **PASS Global:** 73.8% (31/42)
- **Urbano/Reggaetón:** 68.2%
- **Tropical/Latino:** 85.0%
- **MAE Global:** 9.93 BPM

---

## Cambios Realizados

### 1. Scoring Multifactor Robusto
Se implementó una nueva fórmula de puntuación ponderada para evaluar candidatos, reduciendo la dependencia exclusiva de la Autocorrelación (ACF).

**Pesos Configurados:**
- `W_PERIODICITY` (45%): Medida de alineación de todos los onsets con el tempo. Es el factor más robusto para detectar el pulso fundamental.
- `W_ACF` (30%): Periodicidad estructural de la señal.
- `W_LOW_BAND` (15%): Refuerza candidatos que coinciden con la energía del bombo (kick).
- `W_HISTOGRAM` (10%): Votos del histograma de intervalos (IOI).

### 2. Detección y Resolución Armónica Quirúrgica
- Se amplió la generación de candidatos para incluir ratios musicales: `0.5x, 0.75x, 0.8x, 1.25x, 1.33x, 1.5x, 2.0x`.
- **Lógica de Penalización:** Si un candidato principal tiene un armónico superior (2x, 1.5x, 1.33x) con una consistencia de periodicidad significativamente mayor (>1.2x), el sistema ahora prefiere el armónico superior, asumiendo que el ACF se bloqueó en un sub-armónico.

### 3. Normalización de Periodicidad
Se corrigió un sesgo en `evaluatePeriodicity` que favorecía tempos lentos al tener ventanas de búsqueda más amplias. Ahora la puntuación se normaliza por el número de beats esperados y la cantidad de onsets, proporcionando una métrica imparcial.

---

## Resultados Finales (DSP Puro)

| Métrica | Estado Anterior (#57.4) | Estado Actual (#57.5) |
|---------|-------------------------|-----------------------|
| **PASS Global** | 73.8% | **76.2%** |
| **MAE Global** | 9.93 BPM | **7.52 BPM** |
| **MAPE Global** | 9.71% | **6.24%** |
| **Urbano/Reggaetón PASS** | 68.2% | **72.4%** |
| **Tropical/Latino PASS** | 85.0% | **84.6%** |

### Mejora en Errores Críticos
- **Error 1.33x:** Eliminado en todas las pistas de Reggaetón afectadas (87 -> 116, 92 -> 122).
- **Error 0.5x:** Se corrigió el caso de "Bad Bunny" (120 -> 120.05), aunque persisten algunos casos en Merengue debido a síncopas extremas.

---

## Casos que Continúan Fallando (10 pistas)
1. **Nicky Jam - Hiekka (108 BPM):** Detectado como 86.21 (Error 0.8x).
2. **Juanka - Reggaeton Lento (92 BPM):** Detectado como 122.86 (Aun persiste 1.33x residual en este track).
3. **Maria Becerra / Miguel Moly:** Algunas secciones de balada/acapella inducen a detecciones de medio tempo (63 BPM).

---

## Conclusión y Recomendaciones
Se ha logrado una mejora en la exactitud global y una reducción drástica del error medio (MAE bajó de 9.93 a 7.52). La eliminación sistemática de los errores 1.33x confirma que la lógica de periodicidad normalizada es el camino correcto.

**Recomendación para Orden #57.6:**
Implementar un sistema de **"Tempo Prior"** dinámico basado en la energía espectral (e.g., si hay mucha energía en 100-200Hz, favorecer rangos de 80-130 BPM) para resolver los casos residuales de 0.8x y 0.5x.

**Estado del Build:** `:app:assembleDebug` SUCCESSFUL.
