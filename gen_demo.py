#!/usr/bin/env python3
"""Genera la lista demo de OwnTV Fanart: demo.m3u, epg.xml, epg.xml.gz y logos/.

Uso:  python3 gen_demo.py [AAAA-MM-DD] [días]
Por defecto empieza hoy a las 00:00 (hora de Madrid) y cubre 21 días.
Vuelve a ejecutarlo cuando la guía caduque y sube los archivos al repo.
"""
import gzip, os, sys
from datetime import datetime, timedelta, timezone
from xml.sax.saxutils import escape

REPO_RAW = "https://raw.githubusercontent.com/carlosglcn/OwnTV-Fanart/demo"
# Archivos oficiales de Blender (y la réplica de demos de Kodi en el MIT para Big Buck Bunny).
BBB = "https://mirrors.mit.edu/kodi/demo-files/BBB/bbb_sunflower_1080p_30fps_normal.mp4"
SINTEL = "https://download.blender.org/demo/movies/Sintel.2010.720p.mkv"
TOS = "https://download.blender.org/demo/movies/ToS/tears_of_steel_720p.mov"

# Películas abiertas de Blender (Creative Commons BY). Títulos tal cual en TMDB.
FILMS = {
    "bbb":    ("Big Buck Bunny", 2008, "Animación",
               "Un conejo enorme y bonachón decide darles una lección a tres roedores que le hacen la vida imposible en el bosque."),
    "sintel": ("Sintel", 2010, "Fantasía",
               "Una joven cruza tierras heladas y desiertos en busca del pequeño dragón que crió y que le arrebataron."),
    "tos":    ("Tears of Steel", 2012, "Ciencia ficción",
               "En una Ámsterdam del futuro, un grupo de científicos intenta salvar el mundo de los robots reviviendo una ruptura del pasado."),
    "ed":     ("Elephants Dream", 2006, "Animación",
               "Dos hombres recorren una máquina infinita y extraña: uno la ve como un mundo maravilloso y el otro empieza a dudar."),
    "spring": ("Spring", 2019, "Fantasía",
               "Una pastora y su perro se enfrentan a los espíritus del bosque para que vuelva la primavera."),
    "sprite": ("Sprite Fight", 2021, "Animación",
               "Unos espíritus del bosque y un grupo de niños de acampada acaban en una guerra sin cuartel."),
    "cosmos": ("Cosmos Laundromat", 2015, "Fantasía",
               "Una oveja harta de todo conoce a un vendedor misterioso que le ofrece una vida distinta… y otra, y otra."),
    "agent":  ("Agent 327: Operation Barbershop", 2017, "Acción",
               "El agente secreto 327 investiga una barbería que esconde mucho más que tijeras."),
    "llama":  ("Caminandes: Llamigos", 2016, "Animación",
               "La llama Koro y un pingüino se pelean por la última baya del invierno patagónico."),
    "coffee": ("Coffee Run", 2020, "Drama",
               "Una mujer corre por la ciudad, café en mano, mientras repasa los recuerdos de una relación."),
    "charge": ("Charge", 2022, "Ciencia ficción",
               "En un mundo sin energía, un viejo guarda defiende la última fuente de electricidad de una ladrona decidida."),
}

# Canal: id, nombre, color del logo, siglas, stream, película principal, dos cortos que alternan
CHANNELS = [
    ("cinelibre",  "Cine Libre",      "#E4572E", "CL", f"{BBB}",  "bbb",    ("llama", "cosmos")),
    ("fantasia",   "Fantasía Libre",  "#7B5EA7", "FL", f"{SINTEL}", "sintel", ("sprite", "agent")),
    ("scifi",      "Sci-Fi Libre",    "#1F8A9E", "SF", f"{TOS}",  "tos",    ("agent", "cosmos")),
    ("cinelibrehls","Cine Libre HLS", "#D9A21B", "HLS","https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8", "bbb", ("sprite", "llama")),
]
MAIN_MIN, SHORT_MIN = 30, 15

# Las mismas películas, también en la sección Películas.
MOVIES = [
    ("bbb",    BBB),
    ("sintel", SINTEL),
    ("tos",    TOS),
]

TZ = timezone(timedelta(hours=1))  # Madrid en invierno (sin horario de verano hasta finales de marzo)


def make_logos(out):
    from PIL import Image, ImageDraw, ImageFont
    os.makedirs(out, exist_ok=True)
    font_path = "/usr/share/fonts/opentype/inter/InterDisplay-Bold.otf"
    for cid, name, color, short, *_ in CHANNELS:
        size = 512
        img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        d = ImageDraw.Draw(img)
        d.rounded_rectangle((16, 16, size - 16, size - 16), radius=96, fill=color)
        fs = 210 if len(short) <= 2 else 150
        try:
            font = ImageFont.truetype(font_path, fs)
        except OSError:
            font = ImageFont.load_default(fs)
        bbox = d.textbbox((0, 0), short, font=font)
        w, h = bbox[2] - bbox[0], bbox[3] - bbox[1]
        d.text(((size - w) / 2 - bbox[0], (size - h) / 2 - bbox[1] - 20), short, font=font, fill="white")
        try:
            sf = ImageFont.truetype(font_path, 40)
        except OSError:
            sf = ImageFont.load_default(40)
        tag = "DEMO"
        b2 = d.textbbox((0, 0), tag, font=sf)
        d.text(((size - (b2[2] - b2[0])) / 2 - b2[0], size - 120), tag, font=sf, fill=(255, 255, 255, 200))
        img.save(os.path.join(out, f"{cid}.png"))


def make_m3u(path):
    lines = [f'#EXTM3U url-tvg="{REPO_RAW}/epg.xml.gz" x-tvg-url="{REPO_RAW}/epg.xml.gz"']
    for cid, name, _c, _s, url, *_ in CHANNELS:
        lines.append(f'#EXTINF:-1 tvg-id="{cid}.demo" tvg-name="{name}" '
                     f'tvg-logo="{REPO_RAW}/logos/{cid}.png" tvg-type="live" group-title="Demo",{name}')
        lines.append(url)
    # Películas: sin tvg-logo a propósito, para que la app use póster, fanart y tráiler de TMDB.
    for fid, url in MOVIES:
        title, year, _cat, _desc = FILMS[fid]
        lines.append(f'#EXTINF:-1 tvg-name="{title} ({year})" tvg-type="movie" '
                     f'group-title="Demo",{title} ({year})')
        lines.append(url)
    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")


def fmt(t):
    return t.strftime("%Y%m%d%H%M%S %z")


def make_epg(path, start, days):
    out = ['<?xml version="1.0" encoding="UTF-8"?>',
           '<tv generator-info-name="OwnTV Fanart demo">']
    for cid, name, *_ in CHANNELS:
        out.append(f'  <channel id="{cid}.demo"><display-name lang="es">{escape(name)}</display-name>'
                   f'<icon src="{REPO_RAW}/logos/{cid}.png"/></channel>')
    end = start + timedelta(days=days)
    for i, (cid, name, _c, _s, _u, main, shorts) in enumerate(CHANNELS):
        cycle = [(main, MAIN_MIN), (shorts[0], SHORT_MIN), (main, MAIN_MIN), (shorts[1], SHORT_MIN)]
        t = start - timedelta(minutes=15 * i)  # desfase para que cada canal emita algo distinto
        k = 0
        while t < end:
            fid, mins = cycle[k % len(cycle)]
            t2 = t + timedelta(minutes=mins)
            title, year, cat, desc = FILMS[fid]
            if t2 > start:
                out.append(
                    f'  <programme start="{fmt(t)}" stop="{fmt(t2)}" channel="{cid}.demo">'
                    f'<title lang="es">{escape(title)}</title>'
                    f'<desc lang="es">{escape(desc)}</desc>'
                    f'<category lang="es">Película</category><category lang="es">{escape(cat)}</category>'
                    f'<date>{year}</date></programme>')
            t, k = t2, k + 1
    out.append('</tv>')
    data = "\n".join(out) + "\n"
    with open(path, "w", encoding="utf-8") as f:
        f.write(data)
    with gzip.open(path + ".gz", "wb") as f:
        f.write(data.encode("utf-8"))


if __name__ == "__main__":
    day = sys.argv[1] if len(sys.argv) > 1 else datetime.now(TZ).strftime("%Y-%m-%d")
    days = int(sys.argv[2]) if len(sys.argv) > 2 else 21
    start = datetime.strptime(day, "%Y-%m-%d").replace(tzinfo=TZ)
    here = os.path.dirname(os.path.abspath(__file__))
    make_logos(os.path.join(here, "logos"))
    make_m3u(os.path.join(here, "demo.m3u"))
    make_epg(os.path.join(here, "epg.xml"), start, days)
    print("OK", day, days, "días")
