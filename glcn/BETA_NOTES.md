## Beta

Versión de pruebas de OwnTV Fanart. Se instala al lado de la normal.

- 🎬 Films and series get their cast, title lettering, genres and trailer back: last version's trailer-language update made the cached TMDB details of every title with a trailer expire, and the cache clean-up after the next playlist sync deleted them; the titles then refetched with only the search result. They now fetch their full details again (within the hourly allowance), and a failed fetch no longer replaces full details
