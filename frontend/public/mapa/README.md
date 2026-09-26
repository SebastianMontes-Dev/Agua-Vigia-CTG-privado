# Mapa base local — ADR-072

Extraído el 2026-09-25 desde `https://build.protomaps.com/20260924.pmtiles`, con go-pmtiles v1.31.2 y `scripts/preparar-mapa-base.sh pmtiles`. Fuente fijada por ADR-072; el script permite elegir otra versión explícitamente.

- Archivo: `cartagena.pmtiles`, versionado con Git LFS.
- Tamaño: **5 132 380 bytes** (4,895 MiB).
- SHA-256: `8067729ff2d3c264bbba975e698730c1d5ec4502424bd0c563884d91ef3fbfe1`.
- Bbox: `-75.76,10.13,-75.31,10.69`; zoom máximo: 15.
- Metadatos: Protomaps Basemap 4.15.2; OSM replication 2026-09-24T04:00:00Z.
- San Bernardo e Isla Fuerte quedan fuera del extracto; sus polígonos se muestran sobre el fondo neutro.

El estilo de `src/mapa/estilo-base.ts` usa exclusivamente neutros de los tokens de cada tema y glifos de este directorio. No solicita sprites ni recursos externos. Atribución obligatoria: © OpenStreetMap.
