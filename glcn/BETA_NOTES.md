## Beta

Versión de pruebas de OwnTV Fanart. Se instala al lado de la normal.
- 📺 Player picture strip and programme info: when a guide lists two programmes at once, the floating card and the info screen name the same show as the card (the one that started last), and "Up next" follows it
- 🏃 Home: moving from card to card feels like the player's picture strip — the focus box is on the next card almost at once, with only a small give, instead of trailing behind and catching up
- 📺 Programme info: the channel goes on playing in a small window up on the right, so nothing is missed while you read
- 🔤 Programme info: the synopsis reads over bright pictures (deeper shade, a little narrower), the age rating is a badge of its own, and "Airs again this week" only shows when it does
- ⏪ Programme info: From the start (catch-up channels) and Record buttons beside Technical data
- 🗂️ Emby / Jellyfin: choose how a library is split into categories — by genre (now series too), by platform or network (Netflix, HBO…, from Emby's studios) or as one list each for films and series (Settings › Fanart › Emby / Jellyfin categories; applies at the next sync)
- 🧯 Emby / Jellyfin sync reads the library a page at a time instead of holding every episode (with its media streams) in memory at once — a big library filled the memory while syncing
- 🇪🇸 Emby / Jellyfin genres in Spanish when the app is in Spanish (Ciencia ficción, Miniseries, Documental…), so a library tagged in English and one in Spanish share their categories
- 🏷️ Emby / Jellyfin by platform: each platform shows its own logo (TMDB's network logo) in the category list; variants are joined (HBO Nordic → HBO, Paramount+ with Showtime → Paramount+, Sky One/Max/Atlantic → Sky…), networks with only one or two shows go in "Other networks", and films keep their genres (their studios are their makers, not where they stream)
- 🪄 Programme info opens with the picture folding into its window: the TV shrinks smoothly to its place on the right, its corners rounding, while the backdrop comes up around it (and the reverse on closing)
- 📺 TV profile: Settings no longer offers the Home layouts and Home backdrop — a TV profile always has its own TV Home
- ✨ New motion (off on modest boxes and in light mode): Settings pages slide in from the right; menus and windows grow in with a fade; entering a profile, its screen opens up towards you as Home comes in; the player's picture strip rises card after card; the programme bar fills as the player's card comes up
- ◀ ▶ Programme info walks the channels: ◀ ▶ (or CH±) show what is on the next channels of the list, sliding smoothly while the window keeps playing yours; ▼ goes to the buttons (◀ ▶ there between Record and Technical data) and ▲ back; OK tunes the channel shown. The channels either side are read ahead so each page is ready
- 🗓️ Guide: the provider's own short guide also names, with overlapping programmes, the one that started last — like every other screen
- 🧠 Memory: the now/next caches no longer grow with every channel ever focused, channel logos are fetched by a few workers (not one task per logo) and skipped in light mode, and Home reads the live channels once instead of twice
- 🧘 **Changing section (Home, Live TV, Sports…) is a clean cut:** the small sideways slide that read as a judder is gone, and pages ease out without going past
- 🎬 Emby / Jellyfin by platform: films from a streaming platform's own label (Netflix, Apple, Prime Video, Disney+, HBO Max…) go under that platform with its logo; the rest keep their genre
- 🏷️ **Category names: text, icons or logos** (Settings › Fanart › Category icons): words only by default; icons beside the name; or, for the platforms, their logo in place of the name. Logos drop the white plate and take the row's colour; "Other networks" gets an icon, and a platform no show carries finds its logo on TMDB's network page
- ↔️ **Move Home's rows:** in the TV profile's Home, hold OK on a row's name (Starting now, Films on air…) and move it with ◀ ▶; OK leaves it there. In the Dock, hold OK on a tab › Move row — same keys, and the order is the one in Settings › Home
