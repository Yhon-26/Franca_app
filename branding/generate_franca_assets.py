"""Build Android vectors from the supplied Franca SVG and Fraunces wordmark.

Dev-only requirements: fonttools, uharfbuzz. No font/network runtime dependency in the app.
"""
from pathlib import Path
import re
import xml.etree.ElementTree as ET
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.transformPen import TransformPen
import uharfbuzz as hb

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res"
ANDROID = "http://schemas.android.com/apk/res/android"
ET.register_namespace("android", ANDROID)

def vector(name, width, height, viewport_width, viewport_height, children):
    root = ET.Element("vector", {f"{{{ANDROID}}}width": f"{width}dp", f"{{{ANDROID}}}height": f"{height}dp",
        f"{{{ANDROID}}}viewportWidth": str(viewport_width), f"{{{ANDROID}}}viewportHeight": str(viewport_height)})
    root.extend(children)
    ET.indent(root)
    (RES / "drawable" / f"{name}.xml").write_text(ET.tostring(root, encoding="unicode") + "\n", encoding="utf-8")

def path(**attrs):
    return ET.Element("path", {f"{{{ANDROID}}}{k}": str(v) for k, v in attrs.items()})

def group(**attrs):
    return ET.Element("group", {f"{{{ANDROID}}}{k}": str(v) for k, v in attrs.items()})

html = (ROOT / "branding/franca-reference/Graphite.dc.html").read_text(encoding="utf-8")
svg_text = re.search(r"<svg\b.*?</svg>", html, re.S).group()
svg = ET.fromstring(svg_text)
shapes = []
for element in svg.iter():
    if element.tag == "path":
        shapes.append(path(fillColor="#00000000", strokeColor="#F2F2F5", strokeWidth=9,
            strokeLineCap="round", strokeLineJoin="round", pathData=element.attrib["d"]))
    elif element.tag == "line":
        a = element.attrib
        shapes.append(path(fillColor="#00000000", strokeColor="#F2F2F5", strokeWidth=6,
            strokeLineCap="round", pathData=f'M{a["x1"]},{a["y1"]} L{a["x2"]},{a["y2"]}'))
    elif element.tag == "circle":
        x, y, r = (float(element.attrib[k]) for k in ("cx", "cy", "r"))
        shapes.append(path(fillColor="#89D9B1", strokeColor="#0E0E10",
            strokeWidth=element.attrib.get("stroke-width", "2.5"),
            pathData=f"M{x-r},{y} a{r},{r} 0,1 0,{2*r},0 a{r},{r} 0,1 0,{-2*r},0"))
from copy import deepcopy
mark = group(translateY=-1)
mark.extend(deepcopy(shapes))
vector("ic_franca", 120, 120, 120, 120, [mark])

# Keep the original symbol silhouette and use the Home palette on light surfaces.
light_mark = deepcopy(mark)
stroke = f"{{{ANDROID}}}strokeColor"
fill = f"{{{ANDROID}}}fillColor"
for shape in light_mark:
    if shape.get(fill) == "#00000000":
        shape.set(stroke, "#173FCF")
    else:
        shape.set(fill, "#006849")
        shape.set(stroke, "#F3F6FC")
vector("ic_franca_light", 120, 120, 120, 120, [light_mark])

for name, mono in [("ic_franca_launcher", False), ("ic_franca_monochrome", True)]:
    mark = group(scaleX=.54, scaleY=.54, translateX=21.6, translateY=21.6)
    for shape in deepcopy(shapes):
        if mono and f"{{{ANDROID}}}fillColor" in shape.attrib and shape.attrib[f"{{{ANDROID}}}fillColor"] != "#00000000":
            shape.attrib[f"{{{ANDROID}}}fillColor"] = "#FFFFFF"
            shape.attrib.pop(f"{{{ANDROID}}}strokeColor", None)
            shape.attrib.pop(f"{{{ANDROID}}}strokeWidth", None)
        mark.append(shape)
    vector(name, 108, 108, 108, 108, [mark])

font_path = ROOT / "branding/font-source/Fraunces.ttf"
axes = {"wght": 600, "opsz": 84, "SOFT": 0, "WONK": 1}
font = instantiateVariableFont(TTFont(font_path), axes, inplace=False)
glyphs = font.getGlyphSet()
hb_font = hb.Font(hb.Face(font_path.read_bytes()))
hb_font.set_variations(axes)
buffer = hb.Buffer()
buffer.add_str("franca")
buffer.guess_segment_properties()
hb.shape(hb_font, buffer, {"kern": True})
paths = []
bounds = BoundsPen(glyphs)
x = 0
for info, pos in zip(buffer.glyph_infos, buffer.glyph_positions):
    pen = SVGPathPen(glyphs, ntos=lambda value: f"{value:.2f}".rstrip("0").rstrip(".") if value else "0")
    glyph = glyphs[font.getGlyphName(info.codepoint)]
    transform = (1, 0, 0, -1, x + pos.x_offset, -pos.y_offset)
    glyph.draw(TransformPen(pen, transform))
    glyph.draw(TransformPen(bounds, transform))
    paths.append(pen.getCommands())
    x += pos.x_advance - .025 * font["head"].unitsPerEm
xmin, ymin, xmax, ymax = bounds.bounds
width, height = xmax - xmin + 4, ymax - ymin + 4
word = group(translateX=-xmin + 2, translateY=-ymin + 2)
word.extend(path(fillColor="#F2F2F5", pathData=data) for data in paths)
vector("franca_wordmark", round(40 * width / height, 3), 40, width, height, [word])
(ROOT / "branding/franca-wordmark.svg").write_text(
    f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{xmin-2} {ymin-2} {width} {height}">'
    + ''.join(f'<path fill="#F2F2F5" d="{data}"/>' for data in paths) + '</svg>\n', encoding="utf-8")
print(f"Generated Franca mark, launcher, monochrome and Fraunces 600 wordmark ({width / height:.3f}:1), with green counting knots.")
