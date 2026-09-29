# Boletines guardados para la ingesta sin internet (ADR-082)

`boletines-acuacar.json` son **boletines reales de Acuacar** tal como los devuelve su API pública de WordPress
(`/wp-json/wp/v2/posts`), sin editar: título y contenido en HTML, enlace, fecha y portada. No hay nada inventado.

Lo lee `ColectorLocalDeBoletines` cuando `INGESTA_MODO=local` (`aguavigia.ingesta.modo=local`). Pasan por el mismo
camino que uno en vivo (limpieza, deduplicación por hash, prefiltro, extractor, propuesta **pendiente** de revisión),
así que el veedor sigue decidiendo qué llega al mapa (ADR-028).

## Renovarlos

Solo si hace falta una tanda más reciente, y respetando la ética de datos del proyecto (`CLAUDE.md`): `robots.txt` de
Acuacar leído (solo excluye `/wp-admin/`), el `User-Agent` de `COLLECTOR_USER_AGENT` y una sola petición.

```bash
curl -A "$COLLECTOR_USER_AGENT" \
  "https://www.acuacar.com/wp-json/wp/v2/posts?per_page=100&orderby=date&order=desc&_fields=id,date,link,title,content,_links,_embedded&_embed=wp:featuredmedia"
```

Se conservan los campos `fecha`, `enlace`, `titulo`, `contenido` y `portada` de los boletines elegidos, y se actualiza
`capturadoEn`. Después, `./mvnw test -Dtest='ColectorLocalDeBoletinesTest,IngestaLocalDeExtremoAExtremoTest'`.
