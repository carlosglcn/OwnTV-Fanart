## Beta

Versión de pruebas de OwnTV Fanart. Se instala al lado de la normal.

- 🎬 Films and series get their cast, title lettering, genres and trailer back: last version's trailer-language update made the cached TMDB details of every title with a trailer expire, and the cache clean-up after the next playlist sync deleted them; the titles then refetched with only the search result. Those incomplete titles are cleared once, so the next look fetches them whole (within the hourly allowance), and a failed fetch no longer replaces full details. The first beta of this fix asked for them again from every screen and ran out of memory
- 🪟 Channel options over the player (hold OK on a strip card): the sheet is nearly solid, so the card behind it (synopsis, Up next) no longer shows through and mixes with the menu
- 🎭 Programme info: a film's cast moves to the right, in its own panel between "Airs again this week" and "Up next", so the synopsis has the whole left column and most read whole
- 🏷️ Quality marks: "[DV R]" reads Dolby Vision · REMUX on the title's page (the picture format first, as on the posters, and Dolby Vision spelled out); posters keep DV
