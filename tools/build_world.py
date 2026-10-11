"""Gera assets/world.bin para o Soberano a partir do Natural Earth.

Saída (big-endian, lida por WorldLoader.kt):
  magic "SOBW", versão int
  W, H float (tamanho do mapa em unidades)
  nPaises int; por país: code UTF, nome UTF, continente UTF, sub-região UTF,
       pop long, pib double (US$ bi), renda byte (1..5), cor byte, capital int
  nProv int; por província: nome UTF, pais short, pop int, area float,
       cx cy float, costeira byte, nViz short + viz short[],
       nAneis short; por anel: nPts short + (x short, y short)[] em 1/8 de unidade
"""
import json, math, struct, collections, sys
from shapely.geometry import shape, Point, Polygon, MultiPolygon
from shapely.ops import unary_union, transform
from shapely.strtree import STRtree
from shapely.validation import make_valid

W = 4000.0
LAT_MAX, LAT_MIN = 84.0, -58.0
S = W / (2 * math.pi)

def miller_y(lat):
    lat = max(min(lat, LAT_MAX), LAT_MIN)
    r = math.radians(lat)
    return 1.25 * math.log(math.tan(math.pi / 4 + 0.4 * r))

YMAX = miller_y(LAT_MAX)
H = (YMAX - miller_y(LAT_MIN)) * S

def proj(lon, lat, z=None):
    return ((lon + 180.0) / 360.0 * W, (YMAX - miller_y(lat)) * S)

def projgeom(g):
    return transform(lambda xs, ys, zs=None: tuple(zip(*[proj(x, y) for x, y in zip(xs, ys)])) if False else _proj_arrays(xs, ys), g)

def _proj_arrays(xs, ys):
    px, py = [], []
    for x, y in zip(xs, ys):
        a, b = proj(x, y)
        px.append(a); py.append(b)
    return px, py

def fix(g):
    if not g.is_valid:
        g = make_valid(g)
    if g.geom_type == 'GeometryCollection':
        g = unary_union([p for p in g.geoms if p.geom_type in ('Polygon', 'MultiPolygon')])
    return g

# ---------- países ----------
adm0 = json.load(open('ne_10m_admin_0_countries.geojson'))['features']
rows = [f['properties'] for f in adm0]
by_sov = collections.defaultdict(list)
for r in rows:
    by_sov[r['SOV_A3']].append(r)

INCOME = {'1. High income: OECD': 5, '2. High income: nonOECD': 4, '3. Upper middle income': 3,
          '4. Lower middle income': 2, '5. Low income': 1}

home_of = {}   # adm0_a3 -> código do país dono
countries = {} # code -> dict
for sov, rs in by_sov.items():
    home = None
    for r in rs:
        if r['HOMEPART'] == 1 and r['TYPE'] in ('Sovereign country', 'Country'):
            home = r; break
    if home is None:
        home = max(rs, key=lambda r: r['POP_EST'] or 0)
    code = home['ADM0_A3']
    if code == 'ATA':
        continue
    pop = sum(max(r['POP_EST'] or 0, 0) for r in rs)
    gdp = sum(max(r['GDP_MD'] or 0, 0) for r in rs) / 1000.0
    countries[code] = dict(code=code, name=home['NAME_PT'] or home['NAME'], cont=home['CONTINENT'],
                           sub=home['SUBREGION'], pop=pop, gdp=gdp,
                           inc=INCOME.get(home['INCOME_GRP'], 2), color=home['MAPCOLOR9'] or 1)
    for r in rs:
        home_of[r['ADM0_A3']] = code

PT_BR = {'Arménia': 'Armênia', 'Estónia': 'Estônia', 'Irão': 'Irã', 'Quénia': 'Quênia', 'Letónia': 'Letônia',
         'Macedónia do Norte': 'Macedônia do Norte', 'Madagáscar': 'Madagascar', 'Maurícia': 'Maurício',
         'Mónaco': 'Mônaco', 'Polónia': 'Polônia', 'Roménia': 'Romênia', 'Eslovénia': 'Eslovênia',
         'Vietname': 'Vietnã', 'Iémen': 'Iêmen', 'Chéquia': 'Tchéquia', 'Benim': 'Benin',
         'República da Irlanda': 'Irlanda', 'República Turca do Chipre do Norte': 'Chipre do Norte',
         'Turquemenistão': 'Turcomenistão', 'Sara Ocidental': 'Saara Ocidental', 'Países Baixos': 'Holanda',
         'Singapura': 'Singapura', 'Moçambique': 'Moçambique', 'Bielorrússia': 'Bielorrússia'}
for c in countries.values():
    c['name'] = PT_BR.get(c['name'], c['name'])
# pseudo-países do Natural Earth
for k, v in list(home_of.items()):
    if v == 'KAS':
        home_of[k] = 'IND'
    if v == 'PGA':
        del home_of[k]
countries.pop('KAS', None); countries.pop('PGA', None)
PROV_BR = {'Moscovo': 'Moscou', 'Nova Iorque': 'Nova York', 'São Petersburgo': 'São Petersburgo'}

# ---------- províncias originais ----------
adm1 = json.load(open('ne_10m_admin_1_states_provinces.geojson'))['features']
by_country = collections.defaultdict(list)
for f in adm1:
    p = f['properties']
    code = home_of.get(p['adm0_a3']) or home_of.get(p['gu_a3']) or home_of.get(p['sov_a3'])
    if code is None or code not in countries:
        continue
    g = fix(shape(f['geometry']))
    if g.is_empty:
        continue
    nm = p['name_pt'] or p['name'] or p['name_en'] or '?'
    nm = PROV_BR.get(nm, nm).replace('Gronelândia', 'Groenlândia').replace('Moscovo', 'Moscou')
    if nm in ('Federal', 'Distrito Federal') and p['name']:
        nm = p['name'] if p['name'] != 'Distrito Federal' else 'Distrito Federal'
    by_country[code].append(dict(name=nm, geo=p['adm0_a3'],
                                 region=p['region'] or '', geom=g,
                                 area=g.area * 12392.0 * math.cos(math.radians(g.representative_point().y))))

_cities0 = json.load(open('ne_10m_populated_places_simple.geojson'))['features']
_all_items = [it for lst in by_country.values() for it in lst]
_t0 = STRtree([it['geom'] for it in _all_items])
for it in _all_items:
    it['cw'] = 0.0
for c in _cities0:
    pt = Point(c['geometry']['coordinates'][:2])
    for i in _t0.query(pt):
        if _all_items[i]['geom'].contains(pt):
            _all_items[i]['cw'] += max(c['properties'].get('pop_min') or 0, 0)
            break

def lonlat_centroid(g):
    c = g.representative_point()
    return c.x, c.y

def kmeans(items, k):
    pts = []
    for it in items:
        x, y = lonlat_centroid(it['geom'])
        pts.append((x * math.cos(math.radians(y)), y))
    # semente: mais distantes (determinístico)
    centers = [pts[max(range(len(pts)), key=lambda i: items[i]['geom'].area)]]
    while len(centers) < k:
        i = max(range(len(pts)), key=lambda i: min((pts[i][0]-c[0])**2 + (pts[i][1]-c[1])**2 for c in centers))
        centers.append(pts[i])
    for _ in range(25):
        groups = [[] for _ in centers]
        for i, p in enumerate(pts):
            j = min(range(len(centers)), key=lambda j: (p[0]-centers[j][0])**2 + (p[1]-centers[j][1])**2)
            groups[j].append(i)
        centers = [(sum(pts[i][0] for i in g)/len(g), sum(pts[i][1] for i in g)/len(g)) if g else centers[j]
                   for j, g in enumerate(groups)]
    return [g for g in groups if g]

def short_region(names):
    return names[0] if len(names) == 1 else names[0]

provinces = []
units = []
for code, its in sorted(by_country.items()):
    geos = collections.defaultdict(list)
    for it in its:
        geos[it['geo']].append(it)
    homegeo = max(geos, key=lambda g: (g == code, sum(i['area'] for i in geos[g])))
    for g, lst in sorted(geos.items()):
        km = sum(i['area'] for i in lst)
        if g != homegeo and km < 40:
            continue  # ilhotas desabitadas
        units.append((code, lst))
for code, items in units:
    area = sum(it['geom'].area for it in items)  # graus², só para o alvo
    km2 = sum(it['area'] for it in items)
    target = max(1, min(52, round(math.sqrt(km2) / 55)))
    gnames = None
    if len(items) <= target * 1.6:
        groups = [[i] for i in range(len(items))]
    else:
        regions = collections.defaultdict(list)
        for i, it in enumerate(items):
            regions[it['region']].append(i)
        if '' not in regions and target * 0.6 <= len(regions) <= target * 2.2 and len(regions) > 1:
            groups = list(regions.values())
            gnames = list(regions.keys())
        else:
            groups = kmeans(items, target)
            gnames = None
    for gi, g in enumerate(groups):
        geom = fix(unary_union([items[i]['geom'] for i in g]))
        if len(g) == 1:
            name = items[g[0]]['name']
        elif gnames and gnames[gi]:
            name = gnames[gi]
        else:
            biggest = max(g, key=lambda i: (items[i]['cw'], items[i]['geom'].area))
            name = items[biggest]['name']
        provinces.append(dict(name=name, country=code, geom=geom, km2=sum(items[i]['area'] for i in g)))

print('províncias', len(provinces), file=sys.stderr)

# nomes repetidos no mesmo país
_seen = collections.Counter()
for p in provinces:
    _seen[(p['country'], p['name'])] += 1
_n = collections.Counter()
for p in provinces:
    k = (p['country'], p['name'])
    if _seen[k] > 1:
        _n[k] += 1
        if _n[k] > 1:
            p['name'] = '%s %s' % (p['name'], ['', '', 'II', 'III', 'IV', 'V', 'VI'][min(_n[k], 6)])

# ---------- população por cidades ----------
cities = json.load(open('ne_10m_populated_places_simple.geojson'))['features']
tree = STRtree([p['geom'] for p in provinces])
city_pop = [0.0] * len(provinces)
capital_prov = {}
for c in cities:
    q = c['properties']
    pt = Point(c['geometry']['coordinates'][:2])
    idxs = tree.query(pt)
    hit = None
    for i in idxs:
        if provinces[i]['geom'].contains(pt):
            hit = i; break
    if hit is None:
        continue
    city_pop[hit] += max(q.get('pop_min') or 0, 0) ** 0.9
    if q.get('featurecla') == 'Admin-0 capital' and provinces[hit]['country'] not in capital_prov:
        if home_of.get(q.get('adm0_a3')) == provinces[hit]['country'] or True:
            capital_prov[provinces[hit]['country']] = hit

by_c = collections.defaultdict(list)
for i, p in enumerate(provinces):
    by_c[p['country']].append(i)
for code, idxs in by_c.items():
    tot_a = sum(provinces[i]['geom'].area for i in idxs) or 1
    tot_c = sum(city_pop[i] for i in idxs)
    for i in idxs:
        share = provinces[i]['geom'].area / tot_a
        if tot_c > 0:
            share = 0.04 * share + 0.96 * city_pop[i] / tot_c
        provinces[i]['pop'] = int(countries[code]['pop'] * share)
    if code not in capital_prov:
        capital_prov[code] = max(idxs, key=lambda i: provinces[i]['pop'])

# ---------- vizinhança e costa (em graus) ----------
geoms = [p['geom'] for p in provinces]
tree = STRtree(geoms)
neigh = [set() for _ in provinces]
for i, g in enumerate(geoms):
    gb = g.buffer(0.02)
    for j in tree.query(gb):
        j = int(j)
        if j != i and gb.intersects(geoms[j]):
            neigh[i].add(j); neigh[j].add(i)
coastal = []
for i, g in enumerate(geoms):
    others = unary_union([geoms[j] for j in neigh[i]]) if neigh[i] else None
    edge = g.boundary
    if others is not None:
        edge = edge.difference(others.buffer(0.03))
    coastal.append(1 if edge.length > 0.15 else 0)

# ---------- projeção, simplificação ----------
Q = 8.0  # unidades -> short
out_rings = []
for i, p in enumerate(provinces):
    g = projgeom(p['geom'])
    g = fix(g.simplify(0.35, preserve_topology=True))
    polys = list(g.geoms) if g.geom_type == 'MultiPolygon' else [g]
    polys.sort(key=lambda q: -q.area)
    keep = [q for k, q in enumerate(polys) if k == 0 or q.area > 0.8]
    rings = []
    for q in keep:
        if q.is_empty or q.geom_type != 'Polygon':
            continue
        coords = list(q.exterior.coords)[:-1]
        if len(coords) >= 3:
            rings.append([(int(round(x * Q)), int(round(y * Q))) for x, y in coords])
    if not rings:
        c = g.centroid
        x, y = c.x, c.y
        rings = [[(int((x-1)*Q), int((y-1)*Q)), (int((x+1)*Q), int((y-1)*Q)), (int((x+1)*Q), int((y+1)*Q)), (int((x-1)*Q), int((y+1)*Q))]]
    out_rings.append(rings)
    rp = projgeom(p['geom'].representative_point())
    p['cx'], p['cy'] = rp.x, rp.y
    p['parea'] = g.area

pts_total = sum(len(r) for rs in out_rings for r in rs)
print('pontos', pts_total, file=sys.stderr)

# ---------- escreve ----------
codes = sorted(set(p['country'] for p in provinces))
cidx = {c: i for i, c in enumerate(codes)}

def wutf(f, s):
    b = s.encode('utf-8')
    f.write(struct.pack('>H', len(b))); f.write(b)

with open(sys.argv[1] if len(sys.argv) > 1 else 'world.bin', 'wb') as f:
    f.write(b'SOBW'); f.write(struct.pack('>i', 1))
    f.write(struct.pack('>ff', W, H))
    f.write(struct.pack('>i', len(codes)))
    for c in codes:
        d = countries[c]
        wutf(f, c); wutf(f, d['name']); wutf(f, d['cont'] or ''); wutf(f, d['sub'] or '')
        f.write(struct.pack('>qdbbi', int(d['pop']), float(d['gdp']), d['inc'], int(d['color']), capital_prov[c]))
    f.write(struct.pack('>i', len(provinces)))
    for i, p in enumerate(provinces):
        wutf(f, p['name'])
        f.write(struct.pack('>hiffffb', cidx[p['country']], p['pop'], float(p['km2']), p['cx'], p['cy'], float(p['parea']), coastal[i]))
        nb = sorted(neigh[i])
        f.write(struct.pack('>h', len(nb)))
        for j in nb:
            f.write(struct.pack('>h', j))
        f.write(struct.pack('>h', len(out_rings[i])))
        for r in out_rings[i]:
            f.write(struct.pack('>h', len(r)))
            for x, y in r:
                f.write(struct.pack('>hh', max(-32768, min(32767, x)), max(-32768, min(32767, y))))
print('países', len(codes), 'províncias', len(provinces), file=sys.stderr)
