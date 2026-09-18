from PIL import Image
import math, pathlib, json

N = 32
PALETTE = [
    "#0B1422",  # 0  фон низ
    "#122036",  # 1  фон середина
    "#1A2C4A",  # 2  фон верх
    "#070C14",  # 3  контур
    "#3A2415",  # 4  дерево тень
    "#5C3A20",  # 5  дерево
    "#8A5E33",  # 6  дерево свет
    "#B98A52",  # 7  дерево блик
    "#2E7CD6",  # 8  оправа
    "#5BA3EE",  # 9  оправа свет
    "#8C5E12",  # 10 золото тень
    "#F2C777",  # 11 золото
    "#FFE9A8",  # 12 золото свет
    "#FFFFFF",  # 13 блик
    "#A9D4FF",  # 14 свечение
    "#6E4BB5",  # 15 магический пурпур
]
NAMES = ["фон низ","фон середина","фон верх","контур","дерево тень","дерево","дерево свет","дерево блик",
         "оправа","оправа свет","золото тень","золото","золото свет","блик","свечение","пурпур"]

g = [[0]*N for _ in range(N)]

# --- фон: виньетка от центра, ступенями (полосы читались как горизонт)
for y in range(N):
    for x in range(N):
        d = math.hypot(x - 15.5, y - 13.0)
        g[y][x] = 2 if d < 9 else (1 if d < 15 else 0)

# --- аура вокруг шара: шахматный дизеринг, приём пиксель-арта вместо градиента
ocx, ocy, orad = 15.5, 9.0, 5.6
for y in range(N):
    for x in range(N):
        d = math.hypot(x - ocx, y - ocy)
        if orad + 1.2 < d <= 8.4 and (x + y) % 2 == 0:
            g[y][x] = 14
        elif 8.4 < d <= 10.4 and (x + y) % 4 == 0:
            g[y][x] = 14

# --- шар
hcx, hcy = 13.6, 7.0          # источник света сверху слева
for y in range(N):
    for x in range(N):
        d = math.hypot(x - ocx, y - ocy)
        if d <= orad:
            dh = math.hypot(x - hcx, y - hcy)
            if dh < 1.5:   g[y][x] = 13
            elif dh < 3.0: g[y][x] = 12
            elif d < 4.3:  g[y][x] = 11
            else:          g[y][x] = 10
        elif orad < d <= orad + 1.15:
            g[y][x] = 3

def box(x0, x1, y0, y1, c):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if 0 <= x < N and 0 <= y < N:
                g[y][x] = c

# --- оправа под шаром
box(11, 20, 14, 16, 3)
box(12, 19, 15, 15, 8)
box(12, 19, 14, 14, 9)
box(12, 19, 16, 16, 8)
box(13, 13, 15, 15, 9)
g[15][12] = 3
g[15][19] = 3

# --- древко
box(13, 18, 17, 31, 3)
for y in range(17, 31):
    g[y][14] = 6
    g[y][15] = 7
    g[y][16] = 5
    g[y][17] = 4

for y in (18, 23, 28):
    g[y][16] = 4
for y in (19, 27):
    g[y][14] = 7

# --- обмотки
for y0 in (20, 25):
    box(12, 19, y0, y0 + 1, 3)
    box(13, 18, y0, y0, 6)
    box(13, 18, y0 + 1, y0 + 1, 4)
    g[y0][13] = 7

# --- искры
for (x, y, c) in [(7, 4, 15), (25, 6, 15), (22, 15, 14), (8, 17, 14), (27, 20, 15), (5, 11, 14)]:
    g[y][x] = c
for (x, y) in [(7, 3), (7, 5), (6, 4), (8, 4)]:
    if g[y][x] not in (3,):
        g[y][x] = 15

# --- рамка иконки
for i in range(N):
    g[0][i] = g[N-1][i] = g[i][0] = g[i][N-1] = 3

rgb = [tuple(int(PALETTE[i][k:k+2], 16) for k in (1, 3, 5)) for i in range(16)]
img = Image.new("P", (N, N))
flat = []
for c in rgb: flat.extend(c)
img.putpalette(flat + [0] * (768 - len(flat)))
img.putdata([g[y][x] for y in range(N) for x in range(N)])

# Кладём рядом со скриптом. NEAREST обязателен: обычное сглаживание замылит пиксель-арт.
out = pathlib.Path(__file__).parent
img.save(out / "icon-32.png")
for s in (128, 512):
    img.resize((s, s), Image.NEAREST).save(out / f"icon-{s}.png")

used = sorted({g[y][x] for y in range(N) for x in range(N)})
print("использовано цветов:", len(used), "из 16 ->", used)

# SVG со слиянием горизонтальных пробегов — для артборда
runs = []
for y in range(N):
    x = 0
    while x < N:
        c = g[y][x]; x2 = x
        while x2 + 1 < N and g[y][x2 + 1] == c: x2 += 1
        runs.append((x, y, x2 - x + 1, c)); x = x2 + 1
svg_rects = "".join(f'<rect x="{x}" y="{y}" width="{w}" height="1" fill="{PALETTE[c]}"></rect>' for x, y, w, c in runs)
pathlib.Path("pixel_rects.svg").write_text(svg_rects, encoding="utf-8")
pathlib.Path("pixel_palette.json").write_text(json.dumps([{"hex": PALETTE[i], "name": NAMES[i]} for i in range(16)], ensure_ascii=False), encoding="utf-8")
print("прямоугольников в svg:", len(runs))
