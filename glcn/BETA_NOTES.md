## Beta

Versión de pruebas de OwnTV Fanart. Se instala al lado de la normal.

- 🎬 Films and series get their cast, title lettering, genres and trailer back: last version's trailer-language update made the cached TMDB details of every title with a trailer expire, and the cache clean-up after the next playlist sync deleted them; the titles then refetched with only the search result. Those incomplete titles are cleared once, so the next look fetches them whole (within the hourly allowance), and a failed fetch no longer replaces full details. The first beta of this fix asked for them again from every screen and ran out of memory
- 🪟 Channel options over the player (hold OK on a strip card): the sheet is nearly solid, so the card behind it (synopsis, Up next) no longer shows through and mixes with the menu
- 🎭 Programme info: a film's cast moves to the right, in its own panel between "Airs again this week" and "Up next", so the synopsis has the whole left column and most read whole
- 🏷️ Quality marks: "[DV R]" reads Dolby Vision · REMUX on the title's page (the picture format first, as on the posters, and Dolby Vision spelled out); posters keep DV
- 📝 Home (TV layout): Recently watched cards show the programme's synopsis too — it came only from rows that carried it (thanks, Diego)
- 🎨 Category icons: each group in the category lists (Live TV, Movies, Series, the zap panel) gets a picture from its name — ⚽ football, 🏀 basketball, 🎬 cinema, 🧸 kids, 🎵 music, 📰 news, 🌍 documentaries, a country's flag… — and quality groups the posters' mark (DV in gold, HDR, 4K, FHD). Hold OK on a group › Icon to choose another, go back to Automatic or have none; Settings › Fanart › Category icons turns them off
- 🐢 Lighter on modest boxes and in light mode: the TV guide keeps far fewer channel rows in memory (each holds up to two weeks of programmes), seasonal particles draw a third as many, and the ambient aurora stays still
- ⚡ Smoother everywhere: "You may like" (Live TV Cinematic) is worked out off the main thread, and the text matching that runs for every channel and programme (sport, films, provider tags) is prepared once instead of on every call
- 🫧 Elastic motion that keeps up: a single step along a row or down the page glides a touch past and settles back, and Home's focus box lands with a small elastic give; when steps come quickly (a held key or a fast run) each one takes a short plain glide instead, so the screen never falls behind the cursor
- 🎨 Category icons also in the categories sheet (Movies, Series and Live TV with the sheet layout — they only showed in the column one), and film genres get their own: 🔫 action, 🎭 drama, 🕵️ crime and thriller, 🗺️ adventure, 👽 sci-fi, 🧙 fantasy, 💕 romance, 👪 family, ⚔️ war, 🤠 western
