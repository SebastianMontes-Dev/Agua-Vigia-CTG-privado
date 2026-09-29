# Boletines guardados para la ingesta sin internet (ADR-082)

`boletines-acuacar.json` son **boletines reales de Acuacar** tal como los devuelve su API pública de WordPress
(`/wp-json/wp/v2/posts`), sin editar: título y contenido en HTML, enlace, fecha y portada. No hay nada inventado.

Lo lee `ColectorLocalDeBoletines` cuando `INGESTA_MODO=local` (`aguavigia.ingesta.modo=local`). Siguen el mismo
camino que uno en vivo (limpieza, deduplicación por hash, prefiltro, extractor, propuesta). Como Acuacar es fuente
oficial, sus propuestas **se publican solas** (ADR-034), igual que en vivo; la prensa, que es lo que llena la cola de
revisión del veedor, no se lee en este modo.

## Renovarlos

Solo si hace falta una tanda más reciente, y respetando la ética de datos del proyecto (`CLAUDE.md`): `robots.txt` de
Acuacar leído (solo excluye `/wp-admin/`), el `User-Agent` de `COLLECTOR_USER_AGENT` y una sola petición.

```bash
curl -A "$COLLECTOR_USER_AGENT" \
  "https://www.acuacar.com/wp-json/wp/v2/posts?per_page=100&orderby=date&order=desc&_fields=id,date,link,title,content,_links,_embedded&_embed=wp:featuredmedia"
```

Se conservan los campos `fecha`, `enlace`, `titulo`, `contenido` y `portada` de los boletines elegidos, y se actualiza
`capturadoEn`. Después, `./mvnw test -Dtest='ColectorLocalDeBoletinesTest,IngestaLocalDeExtremoAExtremoTest'`.
