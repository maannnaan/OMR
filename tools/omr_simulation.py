"""Line-by-line Python port of SheetLayout / OmrDetector / OmrReader.decide, tested on synthetic photos."""
import math, random, itertools
import numpy as np, cv2

# ---- SheetLayout (same numbers as SheetLayout.kt) ----
class Sheet:
    def __init__(s, n, opts, roll, setcode, names):
        s.n, s.opts = n, opts
        s.contentTop = 286 if names else 214
        s.cell, s.idR = 34, 12
        s.gridTop = s.contentTop + 30 + 34 + 10
        s.panels = []; x = 70
        if roll > 0: s.panels.append(('roll', x, roll)); x += roll * 34 + 26
        if setcode: s.panels.append(('set', x, 3))
        s.ansLeft = ((s.panels[-1][1] + s.panels[-1][2] * 34 + 8) if s.panels else 40) + 26
        s.ansRight, s.ansTop, s.numW, s.gap = 950, s.contentTop + 40, 46, 18
        areaW = s.ansRight - s.ansLeft; avail = 1300 - s.ansTop - 8
        comfort = max(1, int(areaW // (s.numW + opts * 34 + s.gap)))
        tight = max(1, int(areaW // (s.numW + opts * 24 + s.gap)))
        fit = max(1, int(avail // 24)); needed = math.ceil(n / fit)
        s.cols = max(1, max(min(comfort, math.ceil(n / 10)), min(needed, tight)))
        s.per = math.ceil(n / s.cols)
        s.rowH = min(40, avail / s.per)
        s.step = min(34, (areaW / s.cols - s.numW - s.gap) / opts)
        s.r = min(12, s.rowH * 0.34, s.step * 0.38)
    def ans(s, q, o):
        c, r = q // s.per, q % s.per
        cw = (s.ansRight - s.ansLeft) / s.cols
        return (s.ansLeft + c * cw + s.numW + o * s.step + s.step / 2, s.ansTop + r * s.rowH + s.rowH / 2)
    def idb(s, p, col, d):
        return (p[1] + col * 34 + 17, s.gridTop + d * 34 + 17)

MARKERS = [(50, 50), (950, 50), (950, 1364), (50, 1364)]

def render(sh, answers, roll, setc, k=2):
    """Printed sheet at k px/unit with filled bubbles. answers: list of (opt, darkness) lists per question."""
    W, H = int(1000 * k), int(1414 * k)
    img = np.full((H, W), 255, np.uint8)
    for (x, y) in MARKERS: cv2.rectangle(img, (int((x - 20) * k), int((y - 20) * k)), (int((x + 20) * k), int((y + 20) * k)), 0, -1)
    cv2.rectangle(img, (int(86 * k), int(30 * k)), (int(102 * k), int(46 * k)), 0, -1)       # orientation mark
    cv2.putText(img, "Jalalabad Cantonment Public School", (int(260 * k), int(112 * k)), cv2.FONT_HERSHEY_SIMPLEX, 1.0 * k / 2, 0, 2)
    def bubble(c, r, fill):
        cv2.circle(img, (int(c[0] * k), int(c[1] * k)), int(r * k), 120, max(1, int(1.6 * k / 2)))
        cv2.putText(img, "A", (int((c[0] - r * 0.35) * k), int((c[1] + r * 0.35) * k)), cv2.FONT_HERSHEY_SIMPLEX, r * k / 34, 165, 1)
        if fill > 0: cv2.circle(img, (int(c[0] * k), int(c[1] * k)), int(r * 0.92 * k), int(255 * (1 - fill)), -1)
    for q in range(sh.n):
        marks = dict(answers[q])
        for o in range(sh.opts): bubble(sh.ans(q, o), sh.r, marks.get(o, 0))
    for p, val in zip(sh.panels, [roll, setc]):
        for col in range(p[2]):
            for d in range(10):
                fill = 0
                for (dd, f) in val[col]:
                    if dd == d: fill = f
                bubble(sh.idb(p, col, d), 12, fill)
    return img

def photograph(sheet_img, k, tilt_deg, turn_deg, flip, shadow, noise, rng):
    """Place the sheet on a darker desk with perspective (tilt), in-plane turn, optional 180° flip, uneven light and noise."""
    H, W = sheet_img.shape
    if flip: sheet_img = cv2.rotate(sheet_img, cv2.ROTATE_180)
    out_w, out_h = 1080, 1920
    desk = np.full((out_h, out_w), 95, np.uint8)
    src = np.float32([[0, 0], [W, 0], [W, H], [0, H]])
    sw = 860 if abs(turn_deg) < 45 else 0.9 * out_w * W / H; sh_ = sw * H / W
    cx, cy = out_w / 2 + rng.uniform(-30, 30), out_h / 2 + rng.uniform(-40, 40)
    t = math.radians(turn_deg); kx = math.tan(math.radians(tilt_deg)) * 0.35
    pts = []
    for (x, y) in [(-sw / 2, -sh_ / 2), (sw / 2, -sh_ / 2), (sw / 2, sh_ / 2), (-sw / 2, sh_ / 2)]:
        xx = x * (1 - kx if y < 0 else 1 + kx * 0.3)
        pts.append([cx + xx * math.cos(t) - y * math.sin(t), cy + xx * math.sin(t) + y * math.cos(t)])
    M = cv2.getPerspectiveTransform(src, np.float32(pts))
    warped = cv2.warpPerspective(sheet_img, M, (out_w, out_h), borderValue=0)
    mask = cv2.warpPerspective(np.full_like(sheet_img, 255), M, (out_w, out_h))
    photo = np.where(mask > 0, warped, desk).astype(np.float32)
    if shadow:   # light falls off across the page
        g = np.linspace(1.0, 0.55, out_w)[None, :] * np.linspace(1.0, 0.85, out_h)[:, None]
        photo *= g
    photo += rng.normal(0, noise, photo.shape)
    photo = cv2.GaussianBlur(np.clip(photo, 0, 255).astype(np.uint8), (3, 3), 0)
    return photo

# ---- OmrDetector.detect (port) ----
def order_clockwise(p):
    cx = sum(q[0] for q in p) / 4; cy = sum(q[1] for q in p) / 4
    s = sorted(p, key=lambda q: math.atan2(q[1] - cy, q[0] - cx))
    start = min(range(4), key=lambda i: s[i][0] + s[i][1])
    return [s[(start + i) % 4] for i in range(4)]

def quad_area(q):
    return abs(sum(q[i][0] * q[(i + 1) % 4][1] - q[(i + 1) % 4][0] * q[i][1] for i in range(4))) / 2

def detect(gray):
    ms = max(gray.shape); scale = 1200 / ms if ms > 1200 else 1.0
    small = cv2.resize(gray, (int(gray.shape[1] * scale), int(gray.shape[0] * scale)), interpolation=cv2.INTER_AREA)
    small = cv2.GaussianBlur(small, (3, 3), 0)
    b = cv2.adaptiveThreshold(small, 255, cv2.ADAPTIVE_THRESH_MEAN_C, cv2.THRESH_BINARY_INV, 51, 25)
    cs, _ = cv2.findContours(b, cv2.RETR_LIST, cv2.CHAIN_APPROX_SIMPLE)
    area_img = small.shape[0] * small.shape[1]; blobs = []
    for c in cs:
        a = cv2.contourArea(c)
        if a < area_img * 0.00003 or a > area_img * 0.02: continue
        (cx, cy), (w, h), _ = cv2.minAreaRect(c)
        if w <= 0 or h <= 0 or not (0.6 <= w / h <= 1.65): continue
        if a / (w * h) < 0.86: continue          # squares fill their rotated box; circles only ~0.79
        if cv2.contourArea(cv2.convexHull(c)) > a * 1.08: continue
        blobs.append(((cx, cy), math.sqrt(a)))
    # RETR_LIST returns both edges of a thick outline; keep one blob per position
    uniq = []
    for bl in sorted(blobs, key=lambda t: -t[1]):
        if all(math.hypot(bl[0][0] - u[0][0], bl[0][1] - u[0][1]) > u[1] * 0.5 for u in uniq): uniq.append(bl)
    blobs = uniq
    if len(blobs) < 4: return None
    top = sorted(blobs, key=lambda t: -t[1])[:10]
    best, best_a = None, 0
    for four in itertools.combinations(top, 4):
        sz = [f[1] for f in four]
        if min(sz) < max(sz) * 0.45: continue
        a = quad_area(order_clockwise([f[0] for f in four]))
        if a > best_a: best_a, best = a, four
    if best is None or best_a < area_img * 0.12: return None
    pts = order_clockwise([f[0] for f in best])
    msz = sum(f[1] for f in best) / 4
    cand = []
    for o in blobs:
        if o in best or not (msz * 0.22 <= o[1] <= msz * 0.7): continue
        i = min(range(4), key=lambda j: math.hypot(pts[j][0] - o[0][0], pts[j][1] - o[0][1]))
        dist = math.hypot(pts[i][0] - o[0][0], pts[i][1] - o[0][1])
        if dist < msz * 2.4: cand.append((dist, i))
    k = min(cand)[1] if cand else 0
    pts = [pts[(i + k) % 4] for i in range(4)]
    return [(x / scale, y / scale) for (x, y) in pts], k * 90

# ---- OmrReader (port) ----
FILLED, PRESENT = 0.28, 0.10
def decide(raw, er):
    base = min(raw); d = [v - base for v in raw]
    order = sorted(range(len(d)), key=lambda i: -d[i]); a = order[0]
    if d[a] < FILLED: return ('blank',)
    b = order[1] if len(order) > 1 else None
    if b is None or d[b] < PRESENT: return ('m', a)
    return ('m', a, 'erased', b) if d[b] < d[a] * er else ('double', min(a, b), max(a, b))

def read(gray, corners, sh, er=0.55):
    M = cv2.getPerspectiveTransform(np.float32(corners), np.float32(MARKERS))
    flat = cv2.warpPerspective(gray, M, (1000, 1414))
    bg = cv2.GaussianBlur(flat, (0, 0), 22)
    norm = cv2.divide(flat, bg, scale=255)
    def dark(c, r):
        rr = max(2, int(r * 0.62)); x, y = int(c[0] - rr), int(c[1] - rr)
        return 1 - norm[y:y + 2 * rr, x:x + 2 * rr].mean() / 255
    marks = [decide([dark(sh.ans(q, o), sh.r) for o in range(sh.opts)], er) for q in range(sh.n)]
    ids = []
    for p in sh.panels:
        s = ''
        for col in range(p[2]):
            m = decide([dark(sh.idb(p, col, d), 12) for d in range(10)], er)
            s += str(m[1]) if m[0] == 'm' else '?'
        ids.append(s)
    return marks, ids

# ---- run scenarios ----
def scenario(name, n, opts, roll_digits, tilt, turn, flip, shadow, noise, seed):
    rng = np.random.default_rng(seed); random.seed(seed)
    sh = Sheet(n, opts, roll_digits, True, True)
    truth, answers = [], []
    for q in range(n):
        r = random.random()
        if r < 0.06: truth.append(('blank',)); answers.append([])
        elif r < 0.12: a, b = random.sample(range(opts), 2); truth.append(('double', min(a, b), max(a, b))); answers.append([(a, 0.85), (b, 0.8)])
        elif r < 0.18: a, b = random.sample(range(opts), 2); truth.append(('m', a, 'erased', b)); answers.append([(a, 0.88), (b, 0.25)])
        else: a = random.randrange(opts); truth.append(('m', a)); answers.append([(a, random.uniform(0.7, 0.95))])
    roll = [[(d, 0.9)] for d in map(int, "0" * (roll_digits - 2) + "27")]
    setc = [[(1, 0.9)], [(0, 0.9)], [(3, 0.9)]]
    img = render(sh, answers, roll, setc)
    photo = photograph(img, 2, tilt, turn, flip, shadow, noise, rng)
    det = detect(photo)
    if det is None: print(f"{name:42s} NOT DETECTED"); return False
    corners, rot = det
    marks, ids = read(photo, corners, sh)
    ok = sum(1 for m, t in zip(marks, truth) if m == t)
    want_roll = "0" * (roll_digits - 2) + "27"
    good = ok == n and ids == [want_roll, "103"]
    print(f"{name:42s} rot={rot:3d}  marks {ok}/{n}  roll={ids[0]} set={ids[1]}  {'OK' if good else 'MISMATCH'}")
    if not good:
        bad = [(q, truth[q], marks[q]) for q in range(n) if marks[q] != truth[q]][:5]
        print("   first mismatches:", bad)
    return good

results = [
    scenario("flat, 30Q x 4", 30, 4, 3, 0, 0, False, False, 4, 1),
    scenario("tilted 15°, turned 8°", 30, 4, 3, 15, 8, False, False, 5, 2),
    scenario("tilted 22°, turned -12°, shadow", 30, 4, 3, 22, -12, False, True, 6, 3),
    scenario("upside down, 40Q x 4", 40, 4, 3, 10, 5, True, False, 5, 4),
    scenario("turned 90°", 20, 5, 3, 5, 90, False, False, 5, 5),
    scenario("100Q x 6, 8-digit roll", 100, 6, 8, 8, 4, False, False, 4, 6),
    scenario("50Q x 4, heavy noise + shadow", 50, 4, 3, 12, -6, False, True, 10, 7),
]
print(f"\n{sum(results)}/{len(results)} scenarios read perfectly")
