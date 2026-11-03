# -*- coding: utf-8 -*-
"""把 CSS 里写死的颜色收敛成变量（浅色值保持完全一致），为夜间模式铺路。"""
import re, pathlib

BASE = pathlib.Path(r"D:\dsh-routine\java-reader\src\main\resources\static\css")
FILES = ["app.css", "bauhaus.css", "workspace.css"]

# 浅色值 === 原 hex，替换后视觉零变化；夜间模式在 theme.css 里覆盖同名变量
MAP = {
    "#17161a": "--ink-strong",
    "#20201e": "--ink",
    "#3a3733": "--ink-strong",
    "#6f6a63": "--muted-2",
    "#56534d": "--muted-2",
    "#65645e": "--muted",
    "#f4f2eb": "--paper",
    "#f6f4ef": "--paper-warm",
    "#faf7f2": "--paper-warm",
    "#faf9f5": "--paper-white",
    "#fdfbf7": "--paper-white",
    "#efeade": "--tint-2",
    "#efeadf": "--tint-2",
    "#f2efe8": "--tint-2",
    "#e9e6dc": "--tint",
    "#e6e1d7": "--tint",
    "#eadfc6": "--tint",
    "#cccac1": "--grid",
    "#d8d3c9": "--grid-soft",
    "#d93625": "--red",
    "#c8342a": "--red-deep",
    "#d0372a": "--red-deep",
    "#b13b25": "--red-deep",
    "#f7e3e0": "--accent-soft",
    "#fdf3f1": "--accent-soft",
    "#fdf2ef": "--accent-soft",
    "#234bc4": "--blue",
    "#f4c434": "--yellow",
    "#f8e6a3": "--note",
    # 第二轮：Java 版新增样式里的同色系变体
    "#e2ddd3": "--grid-soft",
    "#ddd6c8": "--grid-soft",
    "#d7cfc0": "--grid-soft",
    "#dcd6ca": "--grid-soft",
    "#b9b4ac": "--grid-soft",
    "#ece8e0": "--tint-2",
    "#f3efe6": "--tint-2",
    "#f2eee6": "--tint-2",
    "#f4f1e9": "--tint-2",
    "#faf8f4": "--paper-white",
    "#faf8f3": "--paper-white",
    "#fdfcf9": "--paper-white",
    "#fffdf8": "--paper-white",
    "#fffaf6": "--paper-white",
    "#fff3f0": "--accent-soft",
    "#fff5f0": "--accent-soft",
    "#fbeeeb": "--accent-soft",
    "#fbe9e6": "--accent-soft",
    "#fff6d2": "--note-soft",
    "#f6dfa8": "--note-line",
    "#fdf6e6": "--note-soft",
    "#fffbe9": "--note-soft",
    "#8a5a12": "--note-ink",
    "#2f7a52": "--mint",
    "#2f6b45": "--mint",
    "#2f7a4f": "--mint",
    "#eef7f1": "--mint-soft",
    "#f4f8f4": "--mint-soft",
    "#e0301e": "--red",
    "#d0392c": "--red",
    "#d0452c": "--red",
    "#c0472f": "--red",
    "#a5321c": "--red-deep",
    "#8d1f10": "--red-deep",
    "#6b6560": "--muted-2",
    "#6a665f": "--muted-2",
    "#6b6459": "--muted-2",
    "#8b8378": "--muted",
    "#8b8375": "--muted",
    "#9b968e": "--muted",
    "#7a766e": "--muted",
    "#7a746a": "--muted",
    "#9a948a": "--muted",
    "#3f3c37": "--ink-strong",
    "#3a3632": "--ink-strong",
    "#4b4842": "--ink-strong",
    "#1d4ed8": "--blue",
}

root_block = re.compile(r":root\s*\{[^}]*\}", re.S)


def split_root(text):
    """把 :root{...} 段落切出来，避免把变量定义自己替换掉。"""
    parts, last = [], 0
    for m in root_block.finditer(text):
        parts.append((text[last:m.start()], False))
        parts.append((m.group(0), True))
        last = m.end()
    parts.append((text[last:], False))
    return parts


def convert(text):
    total = 0
    for seg, is_root in split_root(text):
        if is_root:
            yield seg
            continue
        def repl(m):
            nonlocal total
            hexv = m.group(0).lower()
            tok = MAP.get(hexv)
            if tok:
                total += 1
                return f"var({tok})"
            return m.group(0)
        seg = re.sub(r"#[0-9a-fA-F]{6}\b", repl, seg)
        # 纯白只换背景用法，文字白（红底按钮）保持不动
        seg, n = re.subn(r"(background(?:-color)?\s*:\s*)#fff\b", r"\1var(--paper-white)", seg)
        total += n
        yield seg
    convert.count = total


report = []
for name in FILES:
    p = BASE / name
    src = p.read_text(encoding="utf-8")
    out = "".join(convert(src))
    if out != src:
        p.write_text(out, encoding="utf-8")
    report.append(f"{name}: 替换 {convert.count} 处")

print("\n".join(report))
