#!/usr/bin/env python3
"""Converts SVG icons to Android VectorDrawable XML, and VectorDrawable XML back to SVG.

Python standard library only. Run with ``python3 -I``.

    python3 -I tools/icon/svg_to_vector.py svg2vd icon.svg ic_icon.xml [--width 108] [--height 108]
                                                                        [--source NAME] [--lint-ignore ID]
    python3 -I tools/icon/svg_to_vector.py vd2svg ic_icon.xml preview.svg [--px 432]

svg2vd supports the subset of SVG that maps 1:1 onto VectorDrawable:

  * <path>, <circle>, <ellipse>, <rect> (incl. rx/ry), <line>, <polyline>, <polygon>
  * <g> with transform="translate/scale/rotate/matrix" (no skew) -> <group>
  * fill / stroke as a colour or url(#gradient); fill-opacity, stroke-opacity and opacity -> fillAlpha /
    strokeAlpha; fill-rule="evenodd" -> fillType="evenOdd"; stroke-width, stroke-linecap,
    stroke-linejoin, stroke-miterlimit. Presentation attributes and style="..." are both read.
  * <linearGradient> / <radialGradient> with gradientUnits="userSpaceOnUse", any number of stops
    (stop-opacity -> colour alpha), spreadMethod -> tileMode, and href inheritance. They become inline
    <aapt:attr name="android:fillColor"><gradient .../></aapt:attr> blocks. Gradient coordinates are in
    viewport units, exactly like userSpaceOnUse coordinates in the SVG.

Anything that VectorDrawable cannot express (filters, masks, clip-paths, patterns, text, images, group
opacity, gradientTransform, objectBoundingBox gradients, radial focal points, skew) is rejected with an
error instead of being silently dropped.

vd2svg renders the VectorDrawable semantics back to SVG (fillAlpha multiplies the gradient alpha, groups
apply pivot -> scale -> rotate -> translate) so the generated XML can be compared with its source.
"""
import argparse
import math
import os
import re
import sys
import xml.etree.ElementTree as ET

SVG_NS = "http://www.w3.org/2000/svg"
XLINK_NS = "http://www.w3.org/1999/xlink"
ANDROID_NS = "http://schemas.android.com/apk/res/android"
AAPT_NS = "http://schemas.android.com/aapt"
A = "{%s}" % ANDROID_NS


class ConversionError(Exception):
    pass


# --------------------------------------------------------------------------------------------- numbers
def fmt(v):
    """Compact number: at most 4 decimals, no trailing zeros, no '-0'."""
    s = ("%.4f" % v).rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


def parse_length(value, default=None):
    if value is None:
        return default
    m = re.match(r"^\s*([-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?)\s*(px|dp|pt)?\s*$", value)
    if not m:
        raise ConversionError("unsupported length %r (use unitless, px or dp)" % value)
    return float(m.group(1))


# --------------------------------------------------------------------------------------------- colours
NAMED_COLOURS = {
    "black": "#000000", "white": "#FFFFFF", "red": "#FF0000", "green": "#008000", "blue": "#0000FF",
    "yellow": "#FFFF00", "gray": "#808080", "grey": "#808080",
}


def parse_colour(value):
    """Returns (r, g, b, a) with a in 0..1, or None for 'none'/'transparent'."""
    v = value.strip()
    low = v.lower()
    if low in ("none", "transparent"):
        return None
    if low in NAMED_COLOURS:
        v = NAMED_COLOURS[low]
    m = re.match(r"^rgba?\(\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)\s*(?:,\s*([\d.]+)\s*)?\)$", v)
    if m:
        a = float(m.group(4)) if m.group(4) is not None else 1.0
        return (int(float(m.group(1))), int(float(m.group(2))), int(float(m.group(3))), a)
    if not v.startswith("#"):
        raise ConversionError("unsupported colour %r" % value)
    h = v[1:]
    if len(h) in (3, 4):
        h = "".join(c * 2 for c in h)
    if len(h) == 6:
        return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 1.0)
    if len(h) == 8:  # SVG/CSS order: RRGGBBAA
        return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), int(h[6:8], 16) / 255.0)
    raise ConversionError("unsupported colour %r" % value)


def argb(rgba, alpha=1.0):
    r, g, b, a = rgba
    aa = max(0, min(255, int(round(a * alpha * 255))))
    return "#%02X%02X%02X%02X" % (aa, r, g, b)


def parse_android_colour(value):
    """'#AARRGGBB' / '#RRGGBB' / '#ARGB' / '#RGB' -> (r, g, b, a)."""
    h = value.strip()
    if not h.startswith("#"):
        raise ConversionError("unsupported VectorDrawable colour %r (resource references are not resolved)" % value)
    h = h[1:]
    if len(h) == 3:
        h = "FF" + "".join(c * 2 for c in h)
    elif len(h) == 4:
        h = "".join(c * 2 for c in h)
    elif len(h) == 6:
        h = "FF" + h
    if len(h) != 8:
        raise ConversionError("unsupported VectorDrawable colour %r" % value)
    return (int(h[2:4], 16), int(h[4:6], 16), int(h[6:8], 16), int(h[0:2], 16) / 255.0)


# --------------------------------------------------------------------------------------------- styles
STYLE_PROPS = (
    "fill", "fill-opacity", "fill-rule", "stroke", "stroke-width", "stroke-opacity", "stroke-linecap",
    "stroke-linejoin", "stroke-miterlimit", "opacity", "stop-color", "stop-opacity", "display",
    "visibility", "clip-path", "mask", "filter",
)
INHERITED = ("fill", "fill-opacity", "fill-rule", "stroke", "stroke-width", "stroke-opacity",
             "stroke-linecap", "stroke-linejoin", "stroke-miterlimit", "visibility")


def own_style(el):
    props = {}
    for k in STYLE_PROPS:
        if el.get(k) is not None:
            props[k] = el.get(k).strip()
    style = el.get("style")
    if style:
        for decl in style.split(";"):
            if ":" in decl:
                k, v = decl.split(":", 1)
                k = k.strip()
                if k in STYLE_PROPS:
                    props[k] = v.strip()
    return props


def local(tag):
    return tag.split("}", 1)[1] if tag.startswith("{") else tag


# --------------------------------------------------------------------------------------------- shapes
def shape_to_path(el):
    tag = local(el.tag)
    g = lambda n, d=0.0: parse_length(el.get(n), d)
    if tag == "path":
        d = el.get("d")
        if not d:
            return None
        return re.sub(r"\s+", " ", d.replace(",", " ")).strip()
    if tag == "circle":
        cx, cy, r = g("cx"), g("cy"), g("r")
        if r <= 0:
            return None
        return "M%s %sA%s %s 0 1 0 %s %sA%s %s 0 1 0 %s %sZ" % (
            fmt(cx - r), fmt(cy), fmt(r), fmt(r), fmt(cx + r), fmt(cy), fmt(r), fmt(r), fmt(cx - r), fmt(cy))
    if tag == "ellipse":
        cx, cy, rx, ry = g("cx"), g("cy"), g("rx"), g("ry")
        if rx <= 0 or ry <= 0:
            return None
        return "M%s %sA%s %s 0 1 0 %s %sA%s %s 0 1 0 %s %sZ" % (
            fmt(cx - rx), fmt(cy), fmt(rx), fmt(ry), fmt(cx + rx), fmt(cy), fmt(rx), fmt(ry), fmt(cx - rx), fmt(cy))
    if tag == "rect":
        x, y, w, h = g("x"), g("y"), g("width"), g("height")
        if w <= 0 or h <= 0:
            return None
        rx = el.get("rx")
        ry = el.get("ry")
        rx = parse_length(rx) if rx is not None else None
        ry = parse_length(ry) if ry is not None else None
        if rx is None and ry is None:
            return "M%s %sH%sV%sH%sZ" % (fmt(x), fmt(y), fmt(x + w), fmt(y + h), fmt(x))
        rx = ry if rx is None else rx
        ry = rx if ry is None else ry
        rx, ry = min(rx, w / 2), min(ry, h / 2)
        return ("M%s %sH%sA%s %s 0 0 1 %s %sV%sA%s %s 0 0 1 %s %sH%sA%s %s 0 0 1 %s %sV%sA%s %s 0 0 1 %s %sZ" % (
            fmt(x + rx), fmt(y), fmt(x + w - rx), fmt(rx), fmt(ry), fmt(x + w), fmt(y + ry), fmt(y + h - ry),
            fmt(rx), fmt(ry), fmt(x + w - rx), fmt(y + h), fmt(x + rx), fmt(rx), fmt(ry), fmt(x), fmt(y + h - ry),
            fmt(y + ry), fmt(rx), fmt(ry), fmt(x + rx), fmt(y)))
    if tag == "line":
        return "M%s %sL%s %s" % (fmt(g("x1")), fmt(g("y1")), fmt(g("x2")), fmt(g("y2")))
    if tag in ("polyline", "polygon"):
        nums = [float(n) for n in re.findall(r"[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?", el.get("points", ""))]
        if len(nums) < 4:
            return None
        pts = list(zip(nums[0::2], nums[1::2]))
        d = "M%s %s" % (fmt(pts[0][0]), fmt(pts[0][1])) + "".join("L%s %s" % (fmt(x), fmt(y)) for x, y in pts[1:])
        return d + ("Z" if tag == "polygon" else "")
    raise ConversionError("unsupported element <%s>" % tag)


# --------------------------------------------------------------------------------------------- transforms
def parse_transform(value):
    """Returns the affine matrix (a, b, c, d, e, f) of an SVG transform list."""
    m = (1.0, 0.0, 0.0, 1.0, 0.0, 0.0)
    if not value:
        return m

    def mul(p, q):
        a1, b1, c1, d1, e1, f1 = p
        a2, b2, c2, d2, e2, f2 = q
        return (a1 * a2 + c1 * b2, b1 * a2 + d1 * b2, a1 * c2 + c1 * d2, b1 * c2 + d1 * d2,
                a1 * e2 + c1 * f2 + e1, b1 * e2 + d1 * f2 + f1)

    for name, args in re.findall(r"(\w+)\s*\(([^)]*)\)", value):
        v = [float(n) for n in re.findall(r"[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?", args)]
        if name == "translate":
            t = (1, 0, 0, 1, v[0], v[1] if len(v) > 1 else 0)
        elif name == "scale":
            t = (v[0], 0, 0, v[1] if len(v) > 1 else v[0], 0, 0)
        elif name == "rotate":
            r = math.radians(v[0])
            t = (math.cos(r), math.sin(r), -math.sin(r), math.cos(r), 0, 0)
            if len(v) == 3:
                t = mul(mul((1, 0, 0, 1, v[1], v[2]), t), (1, 0, 0, 1, -v[1], -v[2]))
        elif name == "matrix":
            t = tuple(v)
        else:
            raise ConversionError("unsupported transform %s()" % name)
        m = mul(m, t)
    return m


def decompose(m):
    """Affine matrix -> VectorDrawable group attributes (translate, rotate, scale; no skew)."""
    a, b, c, d, e, f = m
    sx = math.hypot(a, b)
    if sx < 1e-9:
        raise ConversionError("degenerate transform")
    rot = math.degrees(math.atan2(b, a))
    sy = (a * d - b * c) / sx
    if abs(a * c + b * d) > 1e-6 * max(1.0, sx * abs(sy)):
        raise ConversionError("skewed transforms cannot be expressed as a VectorDrawable group")
    attrs = {}
    if abs(e) > 1e-9:
        attrs["translateX"] = fmt(e)
    if abs(f) > 1e-9:
        attrs["translateY"] = fmt(f)
    if abs(rot) > 1e-9:
        attrs["rotation"] = fmt(rot)
    if abs(sx - 1) > 1e-9:
        attrs["scaleX"] = fmt(sx)
    if abs(sy - 1) > 1e-9:
        attrs["scaleY"] = fmt(sy)
    return attrs


# --------------------------------------------------------------------------------------------- gradients
class Gradient:
    def __init__(self, kind, attrs, stops):
        self.kind = kind  # 'linear' | 'radial'
        self.attrs = attrs
        self.stops = stops  # [(offset, (r, g, b, a))]


def collect_gradients(root):
    raw = {}
    for el in root.iter():
        tag = local(el.tag)
        if tag in ("linearGradient", "radialGradient") and el.get("id"):
            raw[el.get("id")] = el

    def resolve(gid, seen=()):
        el = raw.get(gid)
        if el is None:
            raise ConversionError("gradient #%s not found" % gid)
        if gid in seen:
            raise ConversionError("circular gradient reference #%s" % gid)
        href = el.get("{%s}href" % XLINK_NS) or el.get("href")
        parent = resolve(href[1:], seen + (gid,)) if href else None
        kind = "linear" if local(el.tag) == "linearGradient" else "radial"
        attrs = dict(parent.attrs) if parent is not None else {}
        for k in ("gradientUnits", "gradientTransform", "spreadMethod", "x1", "y1", "x2", "y2", "cx", "cy", "r", "fx", "fy"):
            if el.get(k) is not None:
                attrs[k] = el.get(k)
        stops = []
        for st in el:
            if local(st.tag) != "stop":
                continue
            props = own_style(st)
            off = st.get("offset", "0").strip()
            o = float(off[:-1]) / 100.0 if off.endswith("%") else float(off)
            col = parse_colour(props.get("stop-color", "#000000")) or (0, 0, 0, 0.0)
            op = float(props.get("stop-opacity", "1"))
            stops.append((max(0.0, min(1.0, o)), (col[0], col[1], col[2], col[3] * op)))
        if not stops and parent is not None:
            stops = parent.stops
        # SVG clamps each offset to be >= the previous one
        fixed, prev = [], 0.0
        for o, c in stops:
            prev = max(prev, o)
            fixed.append((prev, c))
        return Gradient(kind, attrs, fixed)

    return {gid: resolve(gid) for gid in raw}


def gradient_xml(grad, gid, alpha, indent):
    a = grad.attrs
    if a.get("gradientUnits") != "userSpaceOnUse":
        raise ConversionError("gradient #%s must use gradientUnits=\"userSpaceOnUse\"" % gid)
    if a.get("gradientTransform") and parse_transform(a["gradientTransform"]) != (1.0, 0.0, 0.0, 1.0, 0.0, 0.0):
        raise ConversionError("gradient #%s: gradientTransform is not supported" % gid)
    if len(grad.stops) < 2:
        raise ConversionError("gradient #%s needs at least two stops" % gid)
    tile = {"pad": "clamp", "reflect": "mirror", "repeat": "repeat"}[a.get("spreadMethod", "pad")]
    p = indent
    lines = [p + '<aapt:attr name="android:%s">' % ("fillColor" if alpha == "fill" else "strokeColor")]
    if grad.kind == "linear":
        x1, y1 = parse_length(a.get("x1"), 0.0), parse_length(a.get("y1"), 0.0)
        x2, y2 = parse_length(a.get("x2"), 0.0), parse_length(a.get("y2"), 0.0)
        head = ('android:type="linear" android:startX="%s" android:startY="%s" android:endX="%s" android:endY="%s"'
                % (fmt(x1), fmt(y1), fmt(x2), fmt(y2)))
    else:
        cx, cy, r = parse_length(a.get("cx"), 0.0), parse_length(a.get("cy"), 0.0), parse_length(a.get("r"), 0.0)
        fx, fy = a.get("fx"), a.get("fy")
        if (fx is not None and abs(parse_length(fx) - cx) > 1e-6) or (fy is not None and abs(parse_length(fy) - cy) > 1e-6):
            raise ConversionError("gradient #%s: radial focal points are not supported" % gid)
        if r <= 0:
            raise ConversionError("gradient #%s: radius must be > 0" % gid)
        head = ('android:type="radial" android:centerX="%s" android:centerY="%s" android:gradientRadius="%s"'
                % (fmt(cx), fmt(cy), fmt(r)))
    if tile != "clamp":
        head += ' android:tileMode="%s"' % tile
    lines.append(p + "    <gradient %s>" % head)
    for o, c in grad.stops:
        lines.append(p + '        <item android:offset="%s" android:color="%s"/>' % (fmt(o), argb(c)))
    lines.append(p + "    </gradient>")
    lines.append(p + "</aapt:attr>")
    return lines


# --------------------------------------------------------------------------------------------- svg2vd
def paint_of(value, gradients, what):
    """'none' | colour | url(#id) -> ('none', None) | ('colour', rgba) | ('gradient', (id, Gradient))."""
    if value is None:
        return ("colour", (0, 0, 0, 1.0)) if what == "fill" else ("none", None)
    m = re.match(r"^url\(\s*#([^)\s]+)\s*\)", value.strip())
    if m:
        gid = m.group(1)
        if gid not in gradients:
            raise ConversionError("paint server #%s not found (only gradients are supported)" % gid)
        return ("gradient", (gid, gradients[gid]))
    c = parse_colour(value)
    return ("none", None) if c is None else ("colour", c)


def svg_to_vd(svg_path, width=None, height=None, source=None, lint_ignore=None):
    tree = ET.parse(svg_path)
    root = tree.getroot()
    if local(root.tag) != "svg":
        raise ConversionError("%s is not an SVG document" % svg_path)
    vb = root.get("viewBox")
    if vb:
        minx, miny, vw, vh = [float(n) for n in re.split(r"[\s,]+", vb.strip())]
    else:
        minx = miny = 0.0
        vw, vh = parse_length(root.get("width")), parse_length(root.get("height"))
    w = width if width is not None else parse_length(root.get("width"), vw)
    h = height if height is not None else parse_length(root.get("height"), vh)
    gradients = collect_gradients(root)
    stats = {"paths": 0, "gradients": 0, "groups": 0}

    out = ['<?xml version="1.0" encoding="utf-8"?>']
    if source:
        out.append("<!-- Generated by tools/icon/svg_to_vector.py from %s. Do not edit by hand. -->" % source)
    out.append('<vector xmlns:android="http://schemas.android.com/apk/res/android"')
    out.append('    xmlns:aapt="http://schemas.android.com/aapt"')
    if lint_ignore:
        out.append('    xmlns:tools="http://schemas.android.com/tools"')
        out.append('    tools:ignore="%s"' % ",".join(lint_ignore))
    out.append('    android:width="%sdp"' % fmt(w))
    out.append('    android:height="%sdp"' % fmt(h))
    out.append('    android:viewportWidth="%s"' % fmt(vw))
    out.append('    android:viewportHeight="%s">' % fmt(vh))

    def emit(el, inherited, indent):
        tag = local(el.tag)
        if tag in ("defs", "title", "desc", "metadata", "linearGradient", "radialGradient", "style"):
            return
        if tag in ("filter", "mask", "clipPath", "pattern", "text", "image", "use", "symbol", "marker", "foreignObject", "switch"):
            raise ConversionError("<%s> cannot be expressed as a VectorDrawable" % tag)
        props = dict(inherited)
        own = own_style(el)
        for bad in ("clip-path", "mask", "filter"):
            if own.get(bad, "none") != "none":
                raise ConversionError("%s on <%s> is not supported" % (bad, tag))
        if own.get("display") == "none":
            return
        props.update({k: v for k, v in own.items() if k in INHERITED})
        if tag in ("g", "svg"):
            if tag == "g" and own.get("opacity") not in (None, "1"):
                raise ConversionError("group opacity is not supported by VectorDrawable; put it on the paths")
            m = parse_transform(el.get("transform"))
            attrs = decompose(m) if m != (1.0, 0.0, 0.0, 1.0, 0.0, 0.0) else {}
            inner = indent
            if attrs:
                stats["groups"] += 1
                out.append(indent + "<group " + " ".join('android:%s="%s"' % kv for kv in attrs.items()) + ">")
                inner = indent + "    "
            for child in el:
                emit(child, props, inner)
            if attrs:
                out.append(indent + "</group>")
            return
        if props.get("visibility") in ("hidden", "collapse"):
            return
        if el.get("transform"):
            m = parse_transform(el.get("transform"))
            attrs = decompose(m)
            out.append(indent + "<group " + " ".join('android:%s="%s"' % kv for kv in attrs.items()) + ">")
            stats["groups"] += 1
            emit_path(el, props, own, indent + "    ")
            out.append(indent + "</group>")
            return
        emit_path(el, props, own, indent)

    def emit_path(el, props, own, indent):
        d = shape_to_path(el)
        if not d:
            return
        opacity = float(own.get("opacity", "1"))
        fill = paint_of(props.get("fill"), gradients, "fill")
        stroke = paint_of(props.get("stroke"), gradients, "stroke")
        fill_a = float(props.get("fill-opacity", "1")) * opacity
        stroke_a = float(props.get("stroke-opacity", "1")) * opacity
        if fill[0] == "none" and stroke[0] == "none":
            return
        attrs, children = [], []
        if fill[0] == "colour":
            attrs.append('android:fillColor="%s"' % argb(fill[1]))
        elif fill[0] == "gradient":
            children += gradient_xml(fill[1][1], fill[1][0], "fill", indent + "    ")
            stats["gradients"] += 1
        if fill[0] != "none" and fill_a < 1:
            attrs.append('android:fillAlpha="%s"' % fmt(fill_a))
        if fill[0] != "none" and props.get("fill-rule") == "evenodd":
            attrs.append('android:fillType="evenOdd"')
        if stroke[0] != "none":
            if stroke[0] == "colour":
                attrs.append('android:strokeColor="%s"' % argb(stroke[1]))
            else:
                children += gradient_xml(stroke[1][1], stroke[1][0], "stroke", indent + "    ")
                stats["gradients"] += 1
            attrs.append('android:strokeWidth="%s"' % fmt(parse_length(props.get("stroke-width"), 1.0)))
            if stroke_a < 1:
                attrs.append('android:strokeAlpha="%s"' % fmt(stroke_a))
            cap = props.get("stroke-linecap", "butt")
            if cap != "butt":
                attrs.append('android:strokeLineCap="%s"' % cap)
            join = props.get("stroke-linejoin", "miter")
            if join not in ("miter", "round", "bevel"):
                raise ConversionError("stroke-linejoin %r is not supported" % join)
            if join != "miter":
                attrs.append('android:strokeLineJoin="%s"' % join)
            if props.get("stroke-miterlimit"):
                attrs.append('android:strokeMiterLimit="%s"' % fmt(float(props["stroke-miterlimit"])))
        attrs.append('android:pathData="%s"' % d)
        stats["paths"] += 1
        if children:
            out.append(indent + "<path")
            for a_ in attrs:
                out.append(indent + "    " + a_)
            out[-1] += ">"
            out.extend(children)
            out.append(indent + "</path>")
        else:
            out.append(indent + "<path " + " ".join(attrs) + "/>")

    indent = "    "
    wrap = abs(minx) > 1e-9 or abs(miny) > 1e-9
    if wrap:
        out.append(indent + '<group android:translateX="%s" android:translateY="%s">' % (fmt(-minx), fmt(-miny)))
        indent += "    "
    for child in root:
        emit(child, {}, indent)
    if wrap:
        out.append("    </group>")
    out.append("</vector>")
    return "\n".join(out) + "\n", stats


# --------------------------------------------------------------------------------------------- vd2svg
def vd_to_svg(xml_path, px=None):
    root = ET.parse(xml_path).getroot()
    if root.tag != "vector":
        raise ConversionError("%s is not a <vector> drawable" % xml_path)
    vw = float(root.get(A + "viewportWidth"))
    vh = float(root.get(A + "viewportHeight"))
    w = parse_length(root.get(A + "width"))
    h = parse_length(root.get(A + "height"))
    if px:
        w, h = px, px * h / w
    defs, body = [], []
    seq = [0]

    def colour_attr(rgba, extra_alpha, name):
        r, g, b, a = rgba
        s = '%s="#%02X%02X%02X"' % (name, r, g, b)
        op = a * extra_alpha
        if op < 1:
            s += ' %s-opacity="%s"' % (name, fmt(op))
        return s

    def gradient_def(gel):
        seq[0] += 1
        gid = "g%d" % seq[0]
        kind = gel.get(A + "type", "linear")
        tile = gel.get(A + "tileMode", "clamp")
        spread = {"clamp": "", "mirror": ' spreadMethod="reflect"', "repeat": ' spreadMethod="repeat"'}[tile]
        items = [i for i in gel if i.tag == "item"]
        if items:
            stops = [(float(i.get(A + "offset")), parse_android_colour(i.get(A + "color"))) for i in items]
        else:
            stops = [(0.0, parse_android_colour(gel.get(A + "startColor")))]
            if gel.get(A + "centerColor"):
                stops.append((0.5, parse_android_colour(gel.get(A + "centerColor"))))
            stops.append((1.0, parse_android_colour(gel.get(A + "endColor"))))
        st = "".join('<stop offset="%s" stop-color="#%02X%02X%02X" stop-opacity="%s"/>' % (fmt(o), c[0], c[1], c[2], fmt(c[3]))
                     for o, c in stops)
        if kind == "linear":
            defs.append('<linearGradient id="%s" gradientUnits="userSpaceOnUse" x1="%s" y1="%s" x2="%s" y2="%s"%s>%s</linearGradient>' % (
                gid, gel.get(A + "startX", "0"), gel.get(A + "startY", "0"), gel.get(A + "endX", "0"), gel.get(A + "endY", "0"), spread, st))
        elif kind == "radial":
            defs.append('<radialGradient id="%s" gradientUnits="userSpaceOnUse" cx="%s" cy="%s" r="%s"%s>%s</radialGradient>' % (
                gid, gel.get(A + "centerX", "0"), gel.get(A + "centerY", "0"), gel.get(A + "gradientRadius"), spread, st))
        else:
            raise ConversionError("sweep gradients have no SVG equivalent")
        return gid

    def complex_colour(el, name):
        for child in el:
            if child.tag == "{%s}attr" % AAPT_NS and child.get("name") == "android:" + name:
                for g in child:
                    if g.tag == "gradient":
                        return g
        return None

    def walk(el, indent):
        for child in el:
            if child.tag == "group":
                px_, py_ = float(child.get(A + "pivotX", 0)), float(child.get(A + "pivotY", 0))
                tx, ty = float(child.get(A + "translateX", 0)), float(child.get(A + "translateY", 0))
                rot = float(child.get(A + "rotation", 0))
                sx, sy = float(child.get(A + "scaleX", 1)), float(child.get(A + "scaleY", 1))
                # Android: translate(-pivot), scale, rotate, translate(pivot + translate)
                t = "translate(%s %s) rotate(%s) scale(%s %s) translate(%s %s)" % (
                    fmt(px_ + tx), fmt(py_ + ty), fmt(rot), fmt(sx), fmt(sy), fmt(-px_), fmt(-py_))
                body.append(indent + '<g transform="%s">' % t)
                walk(child, indent + "  ")
                body.append(indent + "</g>")
            elif child.tag == "path":
                d = child.get(A + "pathData")
                attrs = ['d="%s"' % d]
                fa = float(child.get(A + "fillAlpha", 1))
                grad = complex_colour(child, "fillColor")
                if grad is not None:
                    attrs.append('fill="url(#%s)"' % gradient_def(grad))
                    if fa < 1:
                        attrs.append('fill-opacity="%s"' % fmt(fa))
                elif child.get(A + "fillColor"):
                    attrs.append(colour_attr(parse_android_colour(child.get(A + "fillColor")), fa, "fill"))
                else:
                    attrs.append('fill="none"')
                if child.get(A + "fillType") == "evenOdd":
                    attrs.append('fill-rule="evenodd"')
                sgrad = complex_colour(child, "strokeColor")
                sa = float(child.get(A + "strokeAlpha", 1))
                if sgrad is not None or child.get(A + "strokeColor"):
                    if sgrad is not None:
                        attrs.append('stroke="url(#%s)"' % gradient_def(sgrad))
                        if sa < 1:
                            attrs.append('stroke-opacity="%s"' % fmt(sa))
                    else:
                        attrs.append(colour_attr(parse_android_colour(child.get(A + "strokeColor")), sa, "stroke"))
                    attrs.append('stroke-width="%s"' % child.get(A + "strokeWidth", "0"))
                    attrs.append('stroke-linecap="%s"' % child.get(A + "strokeLineCap", "butt"))
                    attrs.append('stroke-linejoin="%s"' % child.get(A + "strokeLineJoin", "miter"))
                    attrs.append('stroke-miterlimit="%s"' % child.get(A + "strokeMiterLimit", "4"))
                for unsupported in ("trimPathStart", "trimPathEnd", "trimPathOffset"):
                    if child.get(A + unsupported):
                        raise ConversionError("%s is not rendered by vd2svg" % unsupported)
                body.append(indent + "<path " + " ".join(attrs) + "/>")
            elif child.tag == "clip-path":
                raise ConversionError("<clip-path> is not rendered by vd2svg")

    walk(root, "  ")
    alpha = float(root.get(A + "alpha", 1))
    open_g = '  <g opacity="%s">' % fmt(alpha) if alpha < 1 else None
    svg = ['<svg xmlns="http://www.w3.org/2000/svg" width="%s" height="%s" viewBox="0 0 %s %s">' % (fmt(w), fmt(h), fmt(vw), fmt(vh))]
    if defs:
        svg.append("  <defs>" + "".join(defs) + "</defs>")
    if open_g:
        svg.append(open_g)
    svg.extend(body)
    if open_g:
        svg.append("  </g>")
    svg.append("</svg>")
    return "\n".join(svg) + "\n"


# --------------------------------------------------------------------------------------------- main
def main(argv=None):
    ap = argparse.ArgumentParser(description="SVG <-> Android VectorDrawable converter (stdlib only).")
    sub = ap.add_subparsers(dest="cmd", required=True)
    a1 = sub.add_parser("svg2vd", help="convert an SVG file to VectorDrawable XML")
    a1.add_argument("svg")
    a1.add_argument("out")
    a1.add_argument("--width", type=float, help="android:width in dp (default: the SVG width)")
    a1.add_argument("--height", type=float, help="android:height in dp (default: the SVG height)")
    a1.add_argument("--source", help="source name recorded in the header comment")
    a1.add_argument("--lint-ignore", action="append", metavar="ID",
                    help="lint issue id to suppress on the <vector> (tools:ignore), e.g. VectorRaster")
    a2 = sub.add_parser("vd2svg", help="render VectorDrawable XML back to SVG")
    a2.add_argument("xml")
    a2.add_argument("out")
    a2.add_argument("--px", type=float, help="output width in px (default: android:width)")
    args = ap.parse_args(argv)
    try:
        if args.cmd == "svg2vd":
            xml, stats = svg_to_vd(args.svg, args.width, args.height, args.source, args.lint_ignore)
            with open(args.out, "w", encoding="utf-8", newline="\n") as fh:
                fh.write(xml)
            print("%s: %d paths, %d gradients, %d groups" % (os.path.basename(args.out), stats["paths"], stats["gradients"], stats["groups"]))
        else:
            with open(args.out, "w", encoding="utf-8", newline="\n") as fh:
                fh.write(vd_to_svg(args.xml, args.px))
    except (ConversionError, ET.ParseError, ValueError, KeyError) as e:
        print("error: %s" % e, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
