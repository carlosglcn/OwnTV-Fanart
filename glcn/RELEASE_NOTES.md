## Fix — Trakt open to everyone

- 🎞️ **Trakt now works for everyone:** the app signs in through its production Trakt app — before, Trakt answered "this app is still in development" to anyone not invited as a tester.
- 🔁 If you had already connected Trakt, connect it again once in Settings › Sources › Trakt.
- ℹ️ OwnTV Fanart uses Trakt but is not endorsed or certified by Trakt.

🙏 Thank you to everyone who tried Trakt right away and reported it.

---

## What's new — Trakt, a smoother app and a new player channel list

### 🎞️ Trakt
- 🎞️ **Trakt sign-in** (Settings › Sources): type a code at trakt.tv/activate; each profile can have its own account
- ✅ Films and episodes you finish (or mark watched) go to your Trakt history — except Emby/Jellyfin titles, which the server's own Trakt plugin reports
- 📋 Films watched on Trakt get their ✓ here, and a new Home row, **"My Trakt watchlist"**, shows the watchlist films in your playlists
- ℹ️ OwnTV Fanart uses Trakt but is not endorsed or certified by Trakt

### 🌊 Smoother everywhere
- ⚡ **Brisker:** menus, the focus glide, scrolling, popups and transitions run at 0.6× their old length; the slow backdrop drifts stay calm, and with Android's animations off they stay off
- 🌊 **Lists glide on a spring** that keeps its speed: they set off gently, flow under a held key and slow down softly
- 🎯 **The focus travels:** from the top bar to a row, or a menu to its page, the highlight glides over; in TV en directo and the Guide it slides between channels with a little give
- 🃏 **Home cards pop** as they take focus, with a small bounce and a deeper shadow; a title chosen on Home grows into its page
- ✂️ **No more cut-off cards on Home:** a row only partly on screen fades as a whole and comes back as it slides in
- 🫧 Side menu icons hop as the focus reaches them; ✨ sections come in with a short fade and rise
- 🚀 Smoother right after an update: the app's screens are compiled ahead, now including the player

### 📺 Live TV and the player
- 🖼️ **New player channel list** (◀ while watching): the picture slides aside and keeps playing, the programme on air with time left and synopsis, and the next programmes as picture cards; ◀ opens the categories with their full names
- ⏩ **Fast channel lists:** hold ▲/▼ to speed up (1, then 3, then 6 at a time), or type a channel number to jump to it
- 🔁 **TV en directo, Cinema layout:** ▶ on the last channel goes round to the first (and ◀ on the first to the last, with the menu on top)
- 🎮 New remote shortcut, **Programme info**; Cine and Ficha banners show the stream's facts and the programme's picture
- 📡 Channels without a logo take the guide's picon; logos on a plain white square lose the backdrop
- 🔒 A channel that won't open says why (locked category, profile not allowed)
- ⏩ An empty stream from an overloaded server moves on to mpv after 2.5 s instead of 12 s
- 🔈 Audio tracks your device can't play are marked and never forced on; a decoder that refuses a channel gives up after two tries

### 😴 Sleep timer
- 😴 It keeps counting in the preview pane and Multiview; the last minute dims the picture (any button gives 15 more minutes), and at the end a black screen lets the TV's own sleep take over

### 👻 Details
- 👻 **Faint "ghosts":** the channel's logo in grey behind live cards, the Guide's programme panel and the focused channel row; the season ("T2") and a film's year huge and in outline; both teams' crests behind each match card
- 🌅 **New launch screen:** the icon's sunset as a wide landscape with the logo
- 🌫️ New wallpapers: Bruma azul, Bruma perla, Amanecer pastel and Arena

### 🗂️ Playlists and Settings
- 🩺 **Playlist health at a glance:** a coloured dot, last update, days left before it expires and, after a failed update, the reason with Try again; ⏰ a notice a week before an Xtream subscription ends
- ⏸️ Playlists can be **turned off** without deleting them, and each can have its own **colour and tag**
- 🧭 **Simpler Settings:** everyday settings first, the rest behind "Show advanced settings"; Fanart settings also in their own group; NEW badges for what changed; the Settings cards show your current values

### 🎬 Films, series and Home
- ✅ Films you have already watched no longer appear in Recent films or Featured
- 🎞️ Trailers in the title's own language when there is none in Spanish or English, with Spanish subtitles when the video has them
- 🔑 Emby/Jellyfin keeps its sign-in across restarts; 🧹 a title taken out of Keep watching stays out
- 📺 Recent channels on the TV's home show times, logo and fanart even in launchers without internet (such as Clean Your TV)

### 🔧 Fixes
- 🔔 White or light accents: the reminder bell, the watched check, the chosen mark, the scroll bubble and the Guide's focused times read clearly
- 🏷️ Opening a channel shows its own name at once; 🛠️ fixed a crash in the channel list after changing group
- 🧯 A preview starved of memory is let go instead of freezing the Guide
- 🔕 "Rebuilding sports…" no longer pops up on every screen

### 🔄 From the original OwnTV
- Merged OwnTV 5.2.2 — Settings lock with its own PIN and protected profiles, local auto-sync between paired devices, title art and 4K badge in the player, series title art, "Record every showing" stoppable from Recordings and better sports matching. Thank you, ahXN00 and contributors!

### 🙏 Thank you
Thank you to everyone on Telegram and Discord who tests, reports and shares ideas — the smoother feel, the fast channel lists and Trakt in this version all come from your messages.
