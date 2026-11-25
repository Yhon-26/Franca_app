"""Export the existing Franca artwork, without inventing or redrawing the brand."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.transformPen import TransformPen
import uharfbuzz as hb

base = Path(__file__).resolve().parent
out = base.parent.parent / "Franca-logo-exportado"
out.mkdir(exist_ok=True)
source = (base / "franca-reference/Graphite.dc.html").read_text(encoding="utf-8")
mark = re.search(r"<svg\b.*?</svg>", source, re.S).group()
mark = re.sub(r' width="208" height="208"', '', mark)
mark = mark.replace('<svg ', '<svg xmlns="http://www.w3.org/2000/svg" ', 1)
(out / "franca-simbolo.svg").write_text(mark, encoding="utf-8")
word = (base / "franca-wordmark.svg").read_text(encoding="utf-8").strip()
word = word.replace('<svg ', '<svg x="60" y="380" width="480" height="141.26" ', 1)
mark = mark.replace('<svg ', '<svg x="150" y="0" width="300" height="300" ', 1)

font_path = base / "font-source/Manrope.ttf"
font = instantiateVariableFont(TTFont(font_path), {"wght": 600}, inplace=False)
glyphs = font.getGlyphSet()
hf = hb.Font(hb.Face(font_path.read_bytes()))
hf.set_variations({"wght": 600})
buffer = hb.Buffer(); buffer.add_str("CUENTAS CLARAS, HOGAR FIRME"); buffer.guess_segment_properties()
hb.shape(hf, buffer, {"kern": True})
pen = SVGPathPen(glyphs, ntos=lambda n: f"{n:.3f}".rstrip('0').rstrip('.') if n else '0')
bounds = BoundsPen(glyphs)
scale = 16 / font['head'].unitsPerEm
x = 0
for info, pos in zip(buffer.glyph_infos, buffer.glyph_positions):
    transform = (scale, 0, 0, -scale, x + pos.x_offset * scale, -pos.y_offset * scale)
    glyph = glyphs[font.getGlyphName(info.codepoint)]
    glyph.draw(TransformPen(pen, transform)); glyph.draw(TransformPen(bounds, transform))
    x += pos.x_advance * scale + 4
xmin, ymin, xmax, ymax = bounds.bounds
tagline = f'<g transform="translate({300-(xmin+xmax)/2} 600)"><path fill="#A9A9A6" d="{pen.getCommands()}"/></g>'
svg = f'<svg xmlns="http://www.w3.org/2000/svg" width="2400" height="2640" viewBox="0 0 600 660">{mark}{word}{tagline}</svg>'
(out / "franca-logo-principal.svg").write_text(svg, encoding="utf-8")
(out / "franca-logo-fondo-grafito.svg").write_text(svg.replace('viewBox="0 0 600 660">', 'viewBox="0 0 600 660"><path fill="#0E0E10" d="M0 0h600v660H0z"/>', 1), encoding="utf-8")
(out / "LEEME.txt").write_text("FRANCA - Recursos para splash animado\n\nPrincipal: PNG transparente 2400 x 2640 y SVG con textos convertidos a trazados.\nSimbolo: PNG transparente 1200 x 1200 y SVG, para animarlo por separado.\nFondo Grafito: PNG con fondo #0E0E10 para previsualizar.\n\nNombre blanco, lema gris y nudos verdes #89D9B1: representan el conteo inca en la identidad elegida por el usuario. No se utiliza fondo verde.\nLos SVG ya son vectoriales: no hace falta volver a vectorizar el PNG.\nLos SVG principales contienen grupos para simbolo, nombre y lema.\n", encoding="utf-8")
print(out)
