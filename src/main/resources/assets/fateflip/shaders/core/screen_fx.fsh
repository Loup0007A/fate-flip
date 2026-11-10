#version 150
// (Minecraft 26.x : la ligne #version est remplacée par la cible, voir « shader_rewrites » de target.json)

// Fate Flip — tous les effets d'écran dans un seul programme (Mode = numéro de l'effet).
// Les numéros sont recopiés dans com.fateflip.render.FxPass.

uniform sampler2D DiffuseSampler;    // image à transformer
uniform sampler2D DepthSampler;      // profondeur du monde (copiée en fin de rendu du niveau)
uniform sampler2D HandDepthSampler;  // profondeur de la main (1.0 ailleurs)
uniform sampler2D AuxSampler;        // texture annexe (glyphes ASCII)

//#if mc26
//$$ // Minecraft 26.x : paramètres dans un bloc (voir render/PostFx de la cible 26.x, même ordre)
//$$ layout(std140) uniform FxInfo {
//$$     mat4 Extra;
//$$     vec4 Params;
//$$     vec4 Params2;
//$$     vec4 QuadUV;
//$$     vec2 OutSize;
//$$     vec2 DepthRange;
//$$     int Mode;
//$$     float Time;
//$$     float Intensity;
//$$     float Calm;
//$$     float ZeroToOne;
//$$ };
//$$ layout(location = 0) in vec2 texCoord;
//$$ layout(location = 0) out vec4 fragColor;
//#else
uniform int Mode;
uniform float Time;        // secondes depuis le début de l'effet
uniform float Intensity;   // 0..1, fondu d'entrée / de sortie
uniform float Calm;        // 1 = « réduire les scintillements »
uniform vec2 OutSize;      // taille de la zone de sortie en pixels
uniform vec4 Params;
uniform vec4 Params2;
uniform mat4 Extra;
uniform vec2 DepthRange;   // plans proche / lointain

in vec2 texCoord;
out vec4 fragColor;
//#endif

const float PI = 3.14159265;
const float TAU = 6.2831853;

// ───────────────────────── outils ─────────────────────────
float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}
vec2 hash22(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.xx + p3.yz) * p3.zy);
}
float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash12(i), hash12(i + vec2(1.0, 0.0)), u.x),
               mix(hash12(i + vec2(0.0, 1.0)), hash12(i + vec2(1.0, 1.0)), u.x), u.y);
}
float lum(vec3 c) { return dot(c, vec3(0.299, 0.587, 0.114)); }
float aspect() { return OutSize.x / OutSize.y; }
vec3 tex(vec2 uv) { return texture(DiffuseSampler, clamp(uv, vec2(0.0005), vec2(0.9995))).rgb; }
bool inside01(vec2 uv) { return uv.x >= 0.0 && uv.y >= 0.0 && uv.x <= 1.0 && uv.y <= 1.0; }

//#if mc26
//$$ // 26.x : profondeur inversée (1 = proche, 0 = ciel). rawDepth la remet dans le sens habituel.
//$$ float rawDepth(vec2 uv) {
//$$     return 1.0 - max(texture(DepthSampler, uv).r, texture(HandDepthSampler, uv).r);
//$$ }
//$$ float linDepthRaw(float d) {
//$$     float w = 1.0 - d;
//$$     float n = DepthRange.x, f = DepthRange.y;
//$$     if (ZeroToOne > 0.5) return f * n / (n + w * (f - n));
//$$     float z = w * 2.0 - 1.0;
//$$     return 2.0 * f * n / (n + f + z * (f - n));
//$$ }
//#else
float rawDepth(vec2 uv) {
    return min(texture(DepthSampler, uv).r, texture(HandDepthSampler, uv).r);
}
float linDepthRaw(float d) {
    float z = d * 2.0 - 1.0;
    float n = DepthRange.x, f = DepthRange.y;
    return 2.0 * n * f / (f + n - z * (f - n));
}
//#endif
float linDepth(vec2 uv) { return linDepthRaw(rawDepth(uv)); }
bool isSky(vec2 uv) { return rawDepth(uv) >= 0.99999; }

vec3 rgb2hsv(vec3 c) {
    vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
    vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
    vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
    float d = q.x - min(q.w, q.y);
    float e = 1.0e-10;
    return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
}
vec3 hsv2rgb(vec3 c) {
    vec3 p = abs(fract(c.xxx + vec3(1.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0);
    return c.z * mix(vec3(1.0), clamp(p - 1.0, 0.0, 1.0), c.y);
}
vec3 hueRotate(vec3 c, float a) {
    const vec3 k = vec3(0.57735);
    float ca = cos(a);
    return c * ca + cross(k, c) * sin(a) + k * dot(k, c) * (1.0 - ca);
}
vec3 saturate3(vec3 c, float s) { return mix(vec3(lum(c)), c, s); }
float vignette(vec2 uv, float strength) {
    vec2 p = (uv - 0.5) * vec2(aspect(), 1.0) / aspect();
    return 1.0 - strength * smoothstep(0.25, 0.85, length(p) * 1.4);
}
float bayer4(vec2 p) {
    const float m[16] = float[16](0.0, 8.0, 2.0, 10.0, 12.0, 4.0, 14.0, 6.0,
                                  3.0, 11.0, 1.0, 9.0, 15.0, 7.0, 13.0, 5.0);
    ivec2 i = ivec2(mod(p, 4.0));
    return (m[i.x + i.y * 4] + 0.5) / 16.0;
}
float sobelLum(vec2 uv, vec2 px) {
    float tl = lum(tex(uv + vec2(-px.x, px.y))), t = lum(tex(uv + vec2(0.0, px.y))), tr = lum(tex(uv + px));
    float l = lum(tex(uv - vec2(px.x, 0.0))), r = lum(tex(uv + vec2(px.x, 0.0)));
    float bl = lum(tex(uv - px)), b = lum(tex(uv - vec2(0.0, px.y))), br = lum(tex(uv + vec2(px.x, -px.y)));
    float gx = -tl - 2.0 * l - bl + tr + 2.0 * r + br;
    float gy = -tl - 2.0 * t - tr + bl + 2.0 * b + br;
    return length(vec2(gx, gy));
}
float depthEdge(vec2 uv, vec2 px) {
    float c = linDepth(uv);
    float e = abs(linDepth(uv + vec2(px.x, 0.0)) - c) + abs(linDepth(uv - vec2(px.x, 0.0)) - c)
            + abs(linDepth(uv + vec2(0.0, px.y)) - c) + abs(linDepth(uv - vec2(0.0, px.y)) - c);
    return e / max(c, 0.5);
}
vec3 blur9(vec2 uv, float r) {
    vec2 o = vec2(r / aspect(), r);
    vec3 s = tex(uv) * 0.2;
    s += (tex(uv + vec2(o.x, 0.0)) + tex(uv - vec2(o.x, 0.0)) + tex(uv + vec2(0.0, o.y)) + tex(uv - vec2(0.0, o.y))) * 0.12;
    s += (tex(uv + o) + tex(uv - o) + tex(uv + vec2(o.x, -o.y)) + tex(uv + vec2(-o.x, o.y))) * 0.08;
    return s;
}
vec3 blurDisk(vec2 uv, float r) {
    // 16 échantillons en spirale (disque de Vogel)
    vec3 s = vec3(0.0);
    for (int i = 0; i < 16; i++) {
        float fi = float(i) + 0.5;
        float rr = sqrt(fi / 16.0) * r;
        float a = fi * 2.39996;
        s += tex(uv + vec2(cos(a) / aspect(), sin(a)) * rr);
    }
    return s / 16.0;
}
// Vide étoilé pour les zones hors de l'image.
vec3 voidBg(vec2 uv) {
    vec2 p = floor(uv * OutSize / 3.0);
    float h = hash12(p);
    float star = step(0.996, h) * (0.55 + 0.45 * sin(Time * 2.0 + h * 60.0));
    vec3 g = mix(vec3(0.025, 0.015, 0.05), vec3(0.09, 0.04, 0.15), uv.y);
    g += vec3(0.12, 0.05, 0.2) * pow(noise(uv * 3.0 + Time * 0.03), 3.0);
    return g + vec3(star) * vec3(0.9, 0.85, 1.0);
}

// ───────────────────────── ESPACE ─────────────────────────
// 1 — Triptyque : Params.xyz = position x de chaque bande source, Params2.xyz = « soulèvement » pendant le déplacement.
vec3 fxTriptych(vec2 uv) {
    float w = 1.0 / 3.0;
    float gutter = 2.0 / OutSize.x;
    for (int pass = 0; pass < 2; pass++) {
        for (int s = 0; s < 3; s++) {
            float lift = Params2[s];
            if ((pass == 0) != (lift > 0.01)) continue;
            float scale = 1.0 - 0.07 * lift;
            float cx = Params[s] + w * 0.5;
            vec2 q = vec2((uv.x - cx) / scale + cx, (uv.y - 0.5) / scale + 0.5 + 0.012 * lift);
            float lx = q.x - Params[s];
            if (lx >= 0.0 && lx < w && q.y >= 0.0 && q.y <= 1.0) {
                vec3 c = tex(vec2(float(s) * w + lx, q.y));
                float edge = min(lx, w - lx) * aspect();
                c *= mix(0.6, 1.0, smoothstep(0.0, 0.02, edge));
                if (min(lx, w - lx) < gutter) c = vec3(0.92, 0.88, 0.8);
                return c;
            }
        }
    }
    // ombre portée des bandes soulevées sur le fond
    return voidBg(uv) * 0.6;
}

// 4 — Taquin : Extra = tuile affichée dans chaque case (8 = trou) ; Params = (tuile mobile, case départ, case arrivée, progression).
float cellTile(int i) { return Extra[i / 4][i - (i / 4) * 4]; }
vec3 taquinTile(int t, vec2 f) {
    vec2 src = (vec2(float(t - (t / 3) * 3), float(t / 3)) + f) / 3.0;
    vec3 c = tex(src);
    float bw = 0.035;
    float e = min(min(f.x, f.y), min(1.0 - f.x, 1.0 - f.y));
    if (f.y > 1.0 - bw || f.x < bw) c = mix(c, vec3(1.0), 0.35);      // arête éclairée
    else if (f.y < bw || f.x > 1.0 - bw) c *= 0.55;                     // arête dans l'ombre
    if (e < 0.008) c = vec3(0.05, 0.04, 0.08);
    return c;
}
vec3 fxTaquin(vec2 uv) {
    vec2 g = uv * 3.0;
    vec2 cell = clamp(floor(g), vec2(0.0), vec2(2.0));
    vec2 f = g - cell;
    int moving = int(Params.x + 0.5);
    int from = int(Params.y + 0.5);
    int to = int(Params.z + 0.5);
    if (Params.x >= 0.0) {
        vec2 a = vec2(float(from - (from / 3) * 3), float(from / 3));
        vec2 b = vec2(float(to - (to / 3) * 3), float(to / 3));
        vec2 lm = g - mix(a, b, Params.w);
        if (lm.x >= 0.0 && lm.y >= 0.0 && lm.x < 1.0 && lm.y < 1.0) return taquinTile(moving, lm);
    }
    int ci = int(cell.y) * 3 + int(cell.x);
    int t = int(cellTile(ci) + 0.5);
    if (t >= 8 || (Params.x >= 0.0 && (ci == from || ci == to))) {
        vec3 hole = vec3(0.03, 0.02, 0.06) + 0.05 * vec3(0.4, 0.2, 0.7) * (1.0 - length(f - 0.5));
        return hole;
    }
    return taquinTile(t, f);
}

// 5 — Kaléidoscope
vec3 fxKaleido(vec2 uv) {
    vec2 p = (uv - 0.5) * vec2(aspect(), 1.0);
    float r = length(p);
    float seg = TAU / max(Params.x, 3.0);
    float a = atan(p.y, p.x) + Time * 0.12;
    a = mod(a, seg);
    a = abs(a - seg * 0.5);
    vec2 q = vec2(cos(a), sin(a)) * r * (1.0 + 0.08 * sin(Time * 0.7));
    vec2 s = q / vec2(aspect(), 1.0) + vec2(0.5, 0.55);
    s = 1.0 - abs(1.0 - mod(s, 2.0));
    vec3 c = tex(s);
    return c * (1.0 - 0.25 * smoothstep(0.4, 0.9, r));
}

// 6 — Vertige : Params.x = angle (radians)
vec3 fxVertigo(vec2 uv) {
    vec2 p = (uv - 0.5) * vec2(aspect(), 1.0);
    float c = cos(Params.x), s = sin(Params.x);
    p = mat2(c, -s, s, c) * p / 1.08;
    vec2 q = p / vec2(aspect(), 1.0) + 0.5;
    if (!inside01(q)) return voidBg(uv);
    return tex(q);
}

// 7 — Format vertical (9:16)
vec3 fxPortrait(vec2 uv) {
    float w = (9.0 / 16.0) / aspect();
    float dx = abs(uv.x - 0.5);
    if (dx < w * 0.5) {
        vec3 c = tex(uv);
        // coins arrondis du « téléphone »
        vec2 lp = vec2((uv.x - 0.5 + w * 0.5) * aspect(), uv.y);
        vec2 corner = min(lp, vec2(w * aspect(), 1.0) - lp);
        float rad = 0.035;
        if (corner.x < rad && corner.y < rad && length(vec2(rad) - corner) > rad) return vec3(0.0);
        return c;
    }
    vec2 s = 0.5 + (uv - 0.5) * w;
    vec3 bg = blurDisk(s, 0.03) * 0.42;
    float shadow = smoothstep(0.0, 0.05, dx - w * 0.5);
    return bg * mix(0.35, 1.0, shadow);
}

// 8 — Fenêtre baladeuse : Params = (centre x, centre y, échelle)
vec3 fxWandering(vec2 uv) {
    vec2 c = Params.xy;
    float sc = Params.z;
    vec2 q = (uv - c) / sc + 0.5;
    if (inside01(q)) {
        vec3 col = tex(q);
        float e = min(min(q.x, q.y), min(1.0 - q.x, 1.0 - q.y));
        return e < 0.004 ? vec3(0.85, 0.8, 1.0) : col;
    }
    vec2 d = max(abs(q - 0.5) - 0.5, 0.0) * sc;
    float shadow = smoothstep(0.0, 0.05, length(d * vec2(aspect(), 1.0)));
    return voidBg(uv) * mix(0.15, 1.0, shadow);
}

// 9 — Fonte
vec3 fxMelt(vec2 uv) {
    float col = floor(uv.x * OutSize.x / 5.0);
    float n = hash12(vec2(col, 1.7));
    float m = noise(vec2(uv.x * 14.0, Time * 0.25));
    float drip = (pow(n, 3.0) * 0.1 + m * 0.06) * (0.55 + 0.45 * sin(Time * 0.7 + n * TAU)) * Intensity;
    float wob = sin(uv.y * 40.0 + Time * 3.0 + col * 0.3) * 0.0015;
    vec2 s = vec2(uv.x + wob, uv.y + drip);
    vec3 c = (tex(s) * 2.0 + tex(s + vec2(0.0, 0.006)) + tex(s + vec2(0.0, 0.012))) * 0.25;
    float gloss = smoothstep(0.02, 0.0, abs(fract((uv.y + drip) * 6.0) - 0.5)) * drip * 2.0;
    return c * (1.0 - drip * 1.2) + vec3(gloss) * 0.25;
}

// 10 — Tourbillon
vec3 fxWhirl(vec2 uv) {
    vec2 p = (uv - 0.5) * vec2(aspect(), 1.0);
    float r = length(p);
    float R = 0.8;
    float k = (1.6 + 1.4 * sin(Time * 0.45)) * Intensity;
    float a = k * pow(max(0.0, 1.0 - r / R), 2.0);
    float c = cos(a), s = sin(a);
    vec2 q = mat2(c, -s, s, c) * p;
    vec3 col = tex(q / vec2(aspect(), 1.0) + 0.5);
    return col * (1.0 - 0.25 * smoothstep(0.1, 0.0, r) * Intensity);
}

// 11 — Œil de poisson
vec3 fxFisheye(vec2 uv) {
    vec2 p = (uv - 0.5) * 2.0;
    p.x *= aspect();
    float maxr = length(vec2(aspect(), 1.0));
    float r = length(p) / maxr;
    vec2 q = p * mix(1.0, 0.45 + 0.55 * r * r, Intensity);
    q.x /= aspect();
    vec2 s = q * 0.5 + 0.5;
    float edge = smoothstep(1.0, 0.82, length(p) / max(aspect(), 1.0) * 0.95);
    return tex(s) * mix(1.0, edge, Intensity);
}

// 12 — Écran brisé : Params2.xy = point d'impact
vec3 fxShatter(vec2 uv) {
    vec2 p = uv * vec2(aspect(), 1.0) * 3.2;
    vec2 cell = floor(p);
    float d1 = 9.0, d2 = 9.0;
    vec2 best = vec2(0.0);
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 c = cell + vec2(float(x), float(y));
            vec2 o = c + hash22(c + Params2.zw * 17.0);
            float d = length(p - o);
            if (d < d1) { d2 = d1; d1 = d; best = c; }
            else if (d < d2) { d2 = d; }
        }
    }
    vec2 h = hash22(best * 1.37 + 4.0);
    vec2 offs = (h - 0.5) * 0.018 * Intensity;
    vec2 s = uv + offs;
    vec3 col = tex(s) * (0.9 + 0.2 * h.x);
    // fissures radiales depuis l'impact
    vec2 ip = (uv - Params2.xy) * vec2(aspect(), 1.0);
    float ang = atan(ip.y, ip.x);
    float rays = abs(sin(ang * 7.0 + noise(vec2(length(ip) * 6.0, ang)) * 1.2));
    float radial = smoothstep(0.025, 0.0, rays) * smoothstep(0.9, 0.05, length(ip));
    float near = smoothstep(1.1, 0.15, length(ip));
    float edge = smoothstep(0.022, 0.0, d2 - d1) * mix(0.25, 1.0, near) * step(0.25, hash12(best + 9.0) + near);
    float ringd = 0.5 - abs(fract(length(ip) * 9.0 + noise(vec2(ang * 3.0, 1.0)) * 0.6) - 0.5);
    float rings = smoothstep(0.035, 0.0, ringd) * smoothstep(0.3, 0.05, length(ip)) * step(0.35, noise(vec2(ang * 5.0, 2.0)));
    float crack = max(max(edge, radial), rings) * Intensity;
    col = mix(col, vec3(0.95, 0.97, 1.0), crack * 0.75);
    col *= 1.0 - smoothstep(0.08, 0.0, length(ip)) * 0.5 * Intensity;
    return col;
}

// ───────────────────────── VISION ─────────────────────────
vec3 fxNoir(vec2 uv) {
    float l = lum(tex(uv));
    l = smoothstep(0.04, 0.92, l);
    l = pow(l, 1.2);
    float grain = (hash12(uv * OutSize + floor(Time * 24.0) * vec2(17.0, 31.0)) - 0.5) * 0.12;
    l += grain;
    l *= vignette(uv, 0.7);
    l *= 1.0 + (1.0 - Calm) * (noise(vec2(Time * 9.0, 0.0)) - 0.5) * 0.07;
    float sx = hash12(vec2(floor(Time * 5.0), 3.0));
    if (abs(uv.x - sx) < 0.0007 && hash12(vec2(floor(Time * 5.0), 9.0)) > 0.55) l += 0.2;
    return vec3(l) * vec3(1.0, 0.985, 0.95);
}
vec3 fxScarlet(vec2 uv) {
    vec3 c = tex(uv);
    vec3 hsv = rgb2hsv(c);
    float hueDist = min(hsv.x, 1.0 - hsv.x);
    float red = smoothstep(0.075, 0.02, hueDist) * smoothstep(0.28, 0.5, hsv.y) * smoothstep(0.12, 0.3, hsv.z);
    vec3 g = vec3(smoothstep(0.02, 0.95, lum(c)));
    vec3 keep = saturate3(c, 1.35) * vec3(1.1, 0.92, 0.92);
    return mix(g * vec3(0.96, 0.98, 1.0), keep, red) * vignette(uv, 0.45);
}
vec3 fxNegative(vec2 uv) {
    vec3 c = 1.0 - tex(uv);
    c = mix(c, c * vec3(1.0, 0.86, 0.66), 0.3);
    return c * vignette(uv, 0.3);
}
vec3 thermalPalette(float h) {
    vec3 c0 = vec3(0.02, 0.0, 0.12), c1 = vec3(0.25, 0.0, 0.62), c2 = vec3(0.85, 0.0, 0.42);
    vec3 c3 = vec3(1.0, 0.42, 0.0), c4 = vec3(1.0, 0.88, 0.15), c5 = vec3(1.0, 1.0, 0.95);
    if (h < 0.2) return mix(c0, c1, h / 0.2);
    if (h < 0.45) return mix(c1, c2, (h - 0.2) / 0.25);
    if (h < 0.7) return mix(c2, c3, (h - 0.45) / 0.25);
    if (h < 0.9) return mix(c3, c4, (h - 0.7) / 0.2);
    return mix(c4, c5, (h - 0.9) / 0.1);
}
vec3 fxThermal(vec2 uv) {
    float l = lum(blur9(uv, 0.003));
    float d = linDepth(uv);
    float near = 1.0 - smoothstep(1.5, 40.0, d);
    if (isSky(uv)) { near = 0.0; l *= 0.25; }
    float heat = clamp(0.5 * l + 0.6 * near - 0.08 + (noise(uv * 30.0 + Time) - 0.5) * 0.04, 0.0, 1.0);
    vec3 c = thermalPalette(heat);
    c *= 0.92 + 0.08 * sin(uv.y * OutSize.y * 1.2);
    return c;
}
vec3 pico(int i) {
    const vec3 P[16] = vec3[16](
        vec3(0.0, 0.0, 0.0), vec3(0.114, 0.169, 0.325), vec3(0.494, 0.145, 0.325), vec3(0.0, 0.529, 0.318),
        vec3(0.671, 0.322, 0.212), vec3(0.373, 0.341, 0.31), vec3(0.761, 0.765, 0.78), vec3(1.0, 0.945, 0.91),
        vec3(1.0, 0.0, 0.302), vec3(1.0, 0.639, 0.0), vec3(1.0, 0.925, 0.153), vec3(0.0, 0.894, 0.212),
        vec3(0.161, 0.678, 1.0), vec3(0.514, 0.463, 0.612), vec3(1.0, 0.467, 0.659), vec3(1.0, 0.8, 0.667));
    return P[i];
}
vec3 fxEightBit(vec2 uv) {
    float px = max(2.0, floor(OutSize.y / 140.0));
    vec2 cell = floor(uv * OutSize / px);
    vec3 c = tex((cell + 0.5) * px / OutSize);
    c = saturate3(c, 1.3) + (bayer4(cell) - 0.5) * 0.09;
    float best = 99.0;
    vec3 outc = vec3(0.0);
    for (int i = 0; i < 16; i++) {
        vec3 p = pico(i);
        vec3 d = (c - p) * vec3(0.9, 1.2, 0.7);
        float dd = dot(d, d);
        if (dd < best) { best = dd; outc = p; }
    }
    return outc;
}
vec3 fxPocket(vec2 uv) {
    float px = max(2.0, floor(OutSize.y / 144.0));
    vec2 cell = floor(uv * OutSize / px);
    float l = lum(tex((cell + 0.5) * px / OutSize));
    l = pow(clamp(l, 0.0, 1.0), 0.85) + (bayer4(cell) - 0.5) * 0.22;
    int idx = int(clamp(l * 4.0, 0.0, 3.0));
    vec3 pal[4] = vec3[4](vec3(0.059, 0.22, 0.059), vec3(0.188, 0.384, 0.188), vec3(0.545, 0.675, 0.059), vec3(0.608, 0.737, 0.059));
    vec3 c = pal[idx];
    vec2 lp = mod(uv * OutSize, px);
    if (px >= 3.0 && (lp.x < 1.0 || lp.y < 1.0)) c *= 0.88;
    return c;
}
vec3 fxVhs(vec2 uv) {
    float t = Time;
    float wild = 1.0 - 0.7 * Calm;
    vec2 u = uv;
    float burst = step(0.9, noise(vec2(t * 0.6, 5.0)));
    u.x += (noise(vec2(u.y * 45.0, t * 2.5)) - 0.5) * (0.003 + 0.01 * burst * wild);
    float bandY = fract(-t * 0.11);
    float band = smoothstep(0.04, 0.0, abs(u.y - bandY));
    u.x += band * (hash12(vec2(floor(u.y * OutSize.y / 2.0), floor(t * 30.0))) - 0.5) * 0.035 * wild;
    if (u.y < 0.025) u.x += (hash12(vec2(floor(u.y * OutSize.y), floor(t * 24.0))) - 0.5) * 0.05 * wild;
    vec3 c;
    c.r = (tex(u + vec2(0.004, 0.0)).r + tex(u + vec2(0.007, 0.0)).r) * 0.5;
    c.g = tex(u).g;
    c.b = (tex(u - vec2(0.004, 0.0)).b + tex(u - vec2(0.008, 0.0)).b) * 0.5;
    c = saturate3(c, 1.3) * vec3(1.06, 0.97, 1.08);
    c *= 0.9 + 0.1 * sin(uv.y * OutSize.y * PI * 0.5);
    c += (hash12(uv * OutSize + t * 61.0) - 0.5) * 0.09 * wild;
    c += band * 0.1 * wild;
    return c * vignette(uv, 0.35);
}
vec3 fxCrt(vec2 uv) {
    vec2 q = uv * 2.0 - 1.0;
    q *= 1.0 + 0.06 * vec2(q.y * q.y, q.x * q.x);
    q *= 1.02;
    vec2 u = q * 0.5 + 0.5;
    vec2 m = smoothstep(vec2(0.0), vec2(0.012), u) * smoothstep(vec2(0.0), vec2(0.012), 1.0 - u);
    float frame = m.x * m.y;
    if (frame <= 0.0) return vec3(0.0);
    vec3 c = vec3(tex(u + vec2(0.0012, 0.0)).r, tex(u).g, tex(u - vec2(0.0012, 0.0)).b);
    float scan = 0.72 + 0.28 * pow(sin(u.y * OutSize.y * PI / 3.0), 2.0);
    c *= scan;
    int col = int(mod(uv.x * OutSize.x, 3.0));
    vec3 mask = col == 0 ? vec3(1.0, 0.75, 0.75) : (col == 1 ? vec3(0.75, 1.0, 0.75) : vec3(0.75, 0.75, 1.0));
    c *= mask * 1.18;
    c += pow(c, vec3(3.0)) * 0.25;
    c *= vignette(uv, 0.5) * frame;
    c += vec3(0.04) * smoothstep(0.5, 0.0, length(uv - vec2(0.25, 0.8)));
    return c;
}
vec3 fxAnaglyph(vec2 uv) {
    float d = linDepth(uv);
    float sep = isSky(uv) ? 0.0015 : mix(0.011, 0.0015, smoothstep(1.0, 40.0, d));
    vec3 L = tex(uv + vec2(sep, 0.0));
    vec3 R = tex(uv - vec2(sep, 0.0));
    return vec3(L.r * 0.7 + lum(L) * 0.3, R.g, R.b);
}
vec3 fxTipsy(vec2 uv) {
    vec2 u = uv + vec2(sin(uv.y * 6.0 + Time * 1.3), cos(uv.x * 5.0 + Time * 1.1)) * 0.008 * Intensity;
    vec2 dbl = vec2(sin(Time * 0.7), cos(Time * 0.9)) * 0.02 * (0.6 + 0.4 * sin(Time * 0.33)) * Intensity;
    vec3 c = mix(blur9(u, 0.0015), tex(u + dbl), 0.42);
    c = saturate3(c, 1.15) * vec3(1.05, 1.0, 0.95);
    return c * vignette(uv, 0.4);
}
vec3 fxTunnel(vec2 uv) {
    float ph = mod(Time, 0.95);
    float beat = exp(-ph * 9.0) + 0.65 * exp(-max(ph - 0.22, 0.0) * 9.0) * step(0.22, ph);
    vec2 p = (uv - 0.5) * vec2(aspect(), 1.0);
    float r = length(p);
    float radius = mix(0.9, 0.36, Intensity) + 0.04 * beat;
    float inner = smoothstep(radius, radius * 0.5, r);
    vec3 c = tex(uv);
    vec3 blurred = blurDisk(uv, 0.012);
    vec3 outer = saturate3(blurred, 0.3) * vec3(0.55, 0.12, 0.12) * (0.6 + 0.4 * beat);
    c = mix(outer, c, inner);
    c *= smoothstep(radius * 1.55, radius * 0.9, r);
    return c;
}
vec3 fxSketch(vec2 uv) {
    vec2 px = 1.0 / OutSize;
    float tt = floor(Time * 6.0);
    vec2 j = (hash22(vec2(tt, 2.0)) - 0.5) * 1.5 * px;
    float edge = max(isSky(uv) ? 0.0 : smoothstep(0.06, 0.18, depthEdge(uv + j, px * 1.5)), smoothstep(0.45, 1.0, sobelLum(uv + j, px * 2.5)) * 0.8);
    float l = lum(tex(uv));
    vec2 pp = uv * OutSize;
    float h1 = 1.0 - smoothstep(0.5, 1.3, abs(fract((pp.x + pp.y) / 7.0) - 0.5) * 7.0);
    float h2 = 1.0 - smoothstep(0.5, 1.3, abs(fract((pp.x - pp.y) / 6.0) - 0.5) * 6.0);
    float h3 = 1.0 - smoothstep(0.4, 1.1, abs(fract(pp.y / 5.0) - 0.5) * 5.0);
    float hatch = step(l, 0.45) * h1 * 0.45 + step(l, 0.28) * h2 * 0.55 + step(l, 0.14) * h3 * 0.65;
    float ink = clamp(max(edge * 1.1, hatch), 0.0, 1.0);
    vec3 paper = vec3(0.97, 0.95, 0.89) * (0.94 + 0.06 * noise(pp * 0.35)) * (0.97 + 0.03 * noise(pp * 0.05 + 3.0));
    vec3 c = mix(paper, vec3(0.16, 0.16, 0.2), ink);
    c = mix(c, c * mix(vec3(1.0), saturate3(tex(uv), 1.2) * 1.3, 0.25), 0.6);
    return c;
}
vec3 fxSourceCode(vec2 uv) {
    float cs = max(6.0, floor(OutSize.y / 62.0));
    vec2 pp = uv * OutSize;
    vec2 cell = floor(pp / cs);
    vec2 local = fract(pp / cs);
    vec3 col = tex((cell + 0.5) * cs / OutSize);
    float l = lum(col);
    float g = floor(clamp(l * 1.2, 0.0, 0.999) * 10.0);
    float speed = 0.5 + hash12(vec2(cell.x, 7.0));
    float head = fract(Time * speed * 0.22 + hash12(vec2(cell.x, 3.0)));
    float trail = fract(head - (1.0 - uv.y));
    float rain = exp(-trail * 7.0);
    if (rain > 0.6) g = floor(hash12(cell + floor(Time * 12.0)) * 9.0) + 1.0;
    float ink = texture(AuxSampler, vec2((g + local.x) / 10.0, 1.0 - local.y)).r;
    vec3 green = vec3(0.15, 1.0, 0.4);
    vec3 c = green * ink * (0.3 + 0.95 * l) + vec3(0.75, 1.0, 0.85) * ink * rain * 0.85;
    c = mix(c, col * ink * 1.4, 0.18);
    return c + vec3(0.0, 0.025, 0.01);
}
vec3 fxPsychedelic(vec2 uv) {
    vec2 u = uv + vec2(sin(uv.y * 10.0 + Time * 2.0), cos(uv.x * 8.0 + Time * 1.7)) * 0.006 * Intensity;
    vec3 c = tex(u);
    c = hueRotate(c, Time * 0.9 + length((uv - 0.5) * vec2(aspect(), 1.0)) * 7.0);
    c = saturate3(c, 1.7);
    float e = sobelLum(u, 1.5 / OutSize);
    c += hsv2rgb(vec3(fract(Time * 0.2 + uv.x + uv.y), 0.9, 1.0)) * smoothstep(0.15, 0.6, e) * 0.6;
    return c;
}
vec3 fxMyopia(vec2 uv) {
    float d = linDepth(uv);
    float b = isSky(uv) ? 1.0 : smoothstep(2.5, 16.0, d);
    vec3 c = b > 0.02 ? blurDisk(uv, 0.016 * b * Intensity) : tex(uv);
    return c;
}
vec3 fxEcholocation(vec2 uv) {
    float period = 2.4;
    float age = mod(Time, period);
    float speed = 24.0;
    float ring = age * speed;
    vec2 px = 1.5 / OutSize;
    float d = linDepth(uv);
    if (isSky(uv)) d = 1000.0;
    float edge = clamp(depthEdge(uv, px) * 6.0, 0.0, 1.0) + smoothstep(0.15, 0.6, sobelLum(uv, px)) * 0.35;
    float since = (ring - d) / speed;
    float glow = since >= 0.0 ? exp(-since * 1.4) : 0.0;
    float front = exp(-abs(ring - d) * 0.9);
    vec3 cyan = vec3(0.35, 0.95, 1.0);
    vec3 c = vec3(0.0, 0.008, 0.02);
    c += cyan * edge * glow * 1.3;
    c += cyan * front * (0.15 + edge) * 0.6;
    c += vec3(lum(tex(uv))) * glow * 0.1 * cyan;
    c += cyan * edge * 0.05;
    return c;
}
vec3 fxWetLens(vec2 uv) {
    vec3 base = blur9(uv, 0.0025) * vec3(0.92, 0.96, 1.04);
    vec2 a = vec2(aspect(), 1.0);
    vec2 offset = vec2(0.0);
    float drop = 0.0;
    float hl = 0.0;
    float rim = 0.0;
    // gouttes posées
    vec2 p = uv * a * 7.0;
    vec2 cell = floor(p);
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 c = cell + vec2(float(x), float(y));
            vec2 h = hash22(c);
            if (h.y < 0.35) continue;
            vec2 center = c + h;
            float r = 0.12 + 0.22 * hash12(c + 3.1);
            vec2 dv = (p - center) / r;
            float dl = length(dv);
            if (dl < 1.0) {
                float m = smoothstep(1.0, 0.85, dl);
                offset += -dv * 0.09 * r * m;
                drop = max(drop, m);
                hl = max(hl, smoothstep(0.2, 0.0, length(dv - vec2(-0.3, 0.45))) * m);
                rim = max(rim, smoothstep(0.7, 1.0, dl) * m);
            }
        }
    }
    // gouttes qui glissent, avec leur traînée
    vec2 q = uv * a * vec2(4.0, 1.0);
    float colx = floor(q.x);
    float hx = hash12(vec2(colx, 9.0));
    float sp = 0.06 + 0.1 * hx;
    float yy = fract(hash12(vec2(colx, 4.0)) - Time * sp);
    float xx = fract(q.x) - (0.3 + 0.4 * hash12(vec2(colx, 2.0))) + sin(uv.y * 30.0 + colx) * 0.02;
    vec2 dv2 = vec2(xx * 4.0, (uv.y - yy) * 1.0) / 0.035;
    dv2.x *= 0.55;
    float dl2 = length(dv2);
    if (dl2 < 1.0) {
        float m = smoothstep(1.0, 0.8, dl2);
        offset += -dv2 * 0.012 * m;
        drop = max(drop, m);
        hl = max(hl, smoothstep(0.3, 0.0, length(dv2 - vec2(-0.3, 0.4))) * m);
    }
    float trailM = step(abs(xx * 4.0), 0.012) * step(yy, uv.y) * smoothstep(0.25, 0.0, uv.y - yy);
    offset += vec2(0.0, 0.004) * trailM;
    vec3 c = mix(base, tex(uv + offset) * 1.05, max(drop, trailM * 0.6));
    c *= 1.0 - 0.35 * rim;
    return c + vec3(hl * 0.35);
}
vec3 fxComic(vec2 uv) {
    vec2 px = 1.2 / OutSize;
    vec3 c = saturate3(tex(uv), 1.45);
    c = floor(c * 4.0 + 0.5) / 4.0;
    float l = lum(c);
    float e = smoothstep(0.3, 0.6, sobelLum(uv, px));
    float de = isSky(uv) ? 0.0 : smoothstep(0.08, 0.2, depthEdge(uv, px));
    float ink = max(e, de);
    vec2 pp = uv * OutSize;
    vec2 rp = mat2(0.7071, -0.7071, 0.7071, 0.7071) * pp / 6.0;
    float dotR = length(fract(rp) - 0.5);
    float dots = step(dotR, (1.0 - l) * 0.55) * step(l, 0.6);
    c *= 1.0 - dots * 0.35;
    c = mix(c, vec3(0.05, 0.04, 0.08), ink);
    return c * vec3(1.0, 0.98, 0.93);
}
vec3 fxCinemascope(vec2 uv) {
    float bar = 0.5 * (1.0 - aspect() / 2.39) * Intensity;
    if (uv.y < bar || uv.y > 1.0 - bar) return vec3(0.0);
    vec3 c = tex(uv);
    float l = lum(c);
    vec3 grade = mix(vec3(0.72, 0.95, 1.08), vec3(1.14, 0.98, 0.82), smoothstep(0.15, 0.85, l));
    c = mix(c, c * grade, 0.85);
    c = smoothstep(0.0, 1.0, c);
    float streak = 0.0;
    for (int i = -6; i <= 6; i++) {
        vec3 s = tex(uv + vec2(float(i) * 0.012, 0.0));
        streak += max(lum(s) - 0.72, 0.0) * (1.0 - abs(float(i)) / 7.0);
    }
    c += vec3(0.35, 0.6, 1.0) * streak * 0.25;
    c += (hash12(uv * OutSize + floor(Time * 24.0)) - 0.5) * 0.04;
    return c * vignette(uv, 0.35);
}
vec3 fxNightGoggles(vec2 uv) {
    float l = lum(blur9(uv, 0.0015));
    l = pow(clamp(l, 0.0, 1.0), 0.5) * 1.2;
    l += (hash12(uv * OutSize + floor(Time * 30.0)) - 0.5) * 0.12;
    l *= 0.93 + 0.07 * sin(uv.y * OutSize.y * 1.4 + Time * 10.0);
    vec3 c = vec3(0.22, 1.0, 0.32) * l + vec3(0.01, 0.05, 0.015);
    vec2 p = (uv - 0.5) * vec2(aspect(), 1.0);
    float r1 = length(p - vec2(-0.2, 0.0)), r2 = length(p - vec2(0.2, 0.0));
    float m = smoothstep(0.47, 0.44, min(r1, r2));
    float inner = smoothstep(0.2, 0.46, min(r1, r2));
    return c * m * (1.0 - 0.35 * inner);
}
vec3 fxColourSwap(vec2 uv) {
    float seg = floor(Time / 6.0);
    float f = smoothstep(0.0, 1.2, mod(Time, 6.0));
    return hueRotate(tex(uv), (seg + f) * 2.0944);
}

// ───────────────────────── 2.1 : nouvelles cartes ─────────────────────────
// 34 — Ombres chinoises : silhouettes noires sur un ciel de crépuscule
vec3 fxSilhouette(vec2 uv) {
    vec2 px = 1.5 / OutSize;
    vec3 top = vec3(0.16, 0.05, 0.32), mid = vec3(0.95, 0.38, 0.22), low = vec3(1.0, 0.82, 0.42);
    float y = uv.y;
    vec3 sky = y > 0.55 ? mix(mid, top, smoothstep(0.55, 1.0, y)) : mix(low, mid, smoothstep(0.2, 0.55, y));
    vec2 sunp = (uv - vec2(0.68, 0.42)) * vec2(aspect(), 1.0);
    float sun = smoothstep(0.11, 0.1, length(sunp));
    sky = mix(sky, vec3(1.0, 0.93, 0.7), sun) + vec3(1.0, 0.6, 0.3) * 0.25 * exp(-length(sunp) * 4.0);
    if (isSky(uv)) return sky;
    float d = linDepth(uv);
    float rim = clamp(depthEdge(uv, px) * 5.0, 0.0, 1.0);
    float haze = smoothstep(20.0, 120.0, d) * 0.55;
    vec3 c = mix(vec3(0.02, 0.01, 0.03), sky * 0.55, haze);
    return c + vec3(1.0, 0.55, 0.25) * rim * 0.35 * (1.0 - haze);
}
// 35 — Vitrail
vec3 fxStainedGlass(vec2 uv) {
    vec2 p = uv * vec2(aspect(), 1.0) * 11.0;
    vec2 cell = floor(p);
    float d1 = 9.0, d2 = 9.0;
    vec2 bestC = vec2(0.0);
    for (int y = -1; y <= 1; y++) for (int x = -1; x <= 1; x++) {
        vec2 c = cell + vec2(float(x), float(y));
        vec2 o = c + 0.15 + 0.7 * hash22(c);
        float d = length(p - o);
        if (d < d1) { d2 = d1; d1 = d; bestC = o; } else if (d < d2) d2 = d;
    }
    vec2 cuv = bestC / (vec2(aspect(), 1.0) * 11.0);
    vec3 col = saturate3(tex(clamp(cuv, 0.0, 1.0)), 1.7) * 1.15;
    col *= 0.85 + 0.25 * noise(uv * OutSize * 0.08 + bestC);
    col += vec3(1.0, 0.95, 0.8) * 0.18 * smoothstep(0.6, 0.0, length(uv - vec2(0.3, 0.85)));
    float lead = smoothstep(0.05, 0.11, d2 - d1);
    return mix(vec3(0.05, 0.04, 0.05), col, lead);
}
// 36 — Pointillisme : deux trames de points ronds, le papier apparaît entre eux
vec3 fxPointillism(vec2 uv) {
    float cs = max(5.0, floor(OutSize.y / 80.0));
    vec3 paper = vec3(0.97, 0.95, 0.89);
    vec3 outc = paper;
    float best = 9.0;
    for (int layer = 0; layer < 2; layer++) {
        vec2 off = vec2(float(layer) * 0.5);
        vec2 pp = uv * OutSize / cs + off;
        vec2 cell = floor(pp);
        for (int y = -1; y <= 1; y++) for (int x = -1; x <= 1; x++) {
            vec2 c = cell + vec2(float(x), float(y));
            vec2 o = c + 0.5 + (hash22(c + float(layer) * 31.0) - 0.5) * 0.5;
            float r = 0.42 + 0.14 * hash12(c + 7.0 + float(layer));
            float d = length(pp - o) / r;
            if (d < 1.0 && d < best) {
                best = d;
                vec3 col = saturate3(tex((o - off) * cs / OutSize), 1.7);
                outc = col + (hash12(c + 3.0) - 0.5) * 0.1;
            }
        }
    }
    return mix(outc, paper, smoothstep(0.85, 1.0, best) * step(best, 1.0));
}
// 37 — Radiographie
vec3 fxXray(vec2 uv) {
    vec2 px = 1.5 / OutSize;
    float l = lum(blur9(uv, 0.002));
    float d = linDepth(uv);
    float near = isSky(uv) ? 0.0 : 1.0 - smoothstep(2.0, 50.0, d);
    float base = (1.0 - l) * 0.55 + near * 0.35;
    float edge = isSky(uv) ? 0.0 : clamp(depthEdge(uv, px) * 4.0, 0.0, 1.0);
    vec3 c = vec3(0.55, 0.82, 1.0) * base + vec3(0.9, 0.97, 1.0) * edge * 0.8;
    c *= 0.9 + 0.1 * sin(uv.y * OutSize.y * 1.1);
    return c * vignette(uv, 0.6) + vec3(0.0, 0.02, 0.05);
}
// 38 — Hologramme
vec3 fxHologram(vec2 uv) {
    float wild = 1.0 - 0.7 * Calm;
    vec2 u = uv;
    float band = step(0.93, noise(vec2(floor(uv.y * 40.0), floor(Time * 6.0)))) * wild;
    u.x += band * 0.012;
    float l = lum(tex(u));
    float e = smoothstep(0.2, 0.7, sobelLum(u, 1.5 / OutSize));
    vec3 c = vec3(0.25, 0.85, 1.0) * (0.25 + l * 0.9) + vec3(0.7, 1.0, 1.0) * e * 0.7;
    c *= 0.75 + 0.25 * step(0.5, fract(uv.y * OutSize.y / 3.0 - Time * 2.0));
    c *= 0.92 + 0.08 * sin(Time * 30.0) * wild;
    c = mix(vec3(0.0, 0.04, 0.09), c, 0.9);
    c.r = mix(c.r, lum(tex(u + vec2(0.004, 0.0))) * 0.3, 0.3);
    return c;
}
// 39 — Maquette (tilt-shift)
vec3 fxTiltShift(vec2 uv) {
    float b = smoothstep(0.1, 0.42, abs(uv.y - 0.48));
    vec3 c = b > 0.02 ? blurDisk(uv, 0.014 * b * Intensity) : tex(uv);
    c = saturate3(c, 1.55);
    c = smoothstep(0.02, 0.98, c);
    return c * vignette(uv, 0.25);
}
// 40 — Mur d'écrans (3×3)
vec3 fxVideoWall(vec2 uv) {
    vec2 g = uv * 3.0;
    vec2 cell = floor(g);
    vec2 f = fract(g);
    float bez = 0.035;
    if (f.x < bez || f.y < bez || f.x > 1.0 - bez || f.y > 1.0 - bez) return vec3(0.04, 0.04, 0.05);
    vec2 q = (f - bez) / (1.0 - 2.0 * bez);
    float id = cell.x + cell.y * 3.0;
    float slot = floor(Time / 3.0);
    float noisy = floor(hash12(vec2(slot, 1.0)) * 9.0);
    if (abs(id - noisy) < 0.5 && hash12(vec2(slot, 2.0)) > 0.4) {
        float n = hash12(q * OutSize / 3.0 + floor(Time * 24.0));
        return vec3(n * 0.8) * (0.7 + 0.3 * Calm);
    }
    vec3 c = tex(q);
    c = hueRotate(c, (hash12(cell) - 0.5) * 0.6) * (0.85 + 0.25 * hash12(cell + 5.0));
    c *= 0.88 + 0.12 * sin(q.y * OutSize.y);
    return c;
}
// 41 — Mise en abyme (effet Droste en spirale)
vec3 fxDroste(vec2 uv) {
    vec2 a = vec2(aspect(), 1.0);
    vec2 p = (uv - 0.5) * a;
    float k = 0.48;
    float th = 0.22 + 0.06 * sin(Time * 0.5);
    float frameW = 0.012;
    for (int i = 0; i < 7; i++) {
        vec2 inner = 0.5 * a * k;
        if (abs(p.x) < inner.x && abs(p.y) < inner.y) {
            vec2 e = inner - abs(p);
            if (min(e.x, e.y) < frameW * k) return vec3(0.83, 0.66, 0.3) * (0.7 + 0.3 * float(i % 2));
            float c = cos(th), s = sin(th);
            p = mat2(c, -s, s, c) * (p / k);
        } else break;
    }
    return tex(p / a + 0.5);
}
// 42 — Ricochet : une onde part du centre toutes les 3 s
vec3 fxRipple(vec2 uv) {
    vec2 p = (uv - 0.5) * vec2(aspect(), 1.0);
    float r = length(p);
    float age = mod(Time, 3.0);
    float ring = age * 0.55;
    float env = exp(-pow((r - ring) * 9.0, 2.0)) * (1.0 - age / 3.0);
    float w = sin((r - ring) * 60.0) * env;
    vec2 dir = r > 0.0001 ? p / r : vec2(0.0);
    vec2 q = uv + dir / vec2(aspect(), 1.0) * w * 0.018 * Intensity;
    vec3 c = tex(q);
    return c + vec3(0.9, 0.95, 1.0) * max(w, 0.0) * 0.18 * Intensity;
}
// 43 — Bug d'affichage
vec3 fxGlitch(vec2 uv) {
    float wild = 1.0 - 0.75 * Calm;
    float tt = floor(Time * mix(4.0, 12.0, wild));
    vec2 u = uv;
    vec2 blk = floor(uv * vec2(8.0, 24.0));
    float h = hash12(blk + tt);
    float act = step(0.88, h) * step(0.35, hash12(vec2(tt, 2.0)));
    u.x += act * (hash12(blk + tt + 3.0) - 0.5) * 0.18 * wild;
    vec3 c;
    float split = 0.004 + act * 0.02;
    c.r = tex(u + vec2(split, 0.0)).r;
    c.g = tex(u).g;
    c.b = tex(u - vec2(split, 0.0)).b;
    if (act > 0.5 && hash12(blk + tt + 9.0) > 0.6) c = 1.0 - c;
    float line = step(0.985, hash12(vec2(floor(uv.y * OutSize.y / 2.0), tt)));
    c = mix(c, vec3(hash12(uv + tt)), line * 0.6 * wild);
    return c;
}

// ───────────────────────── 2.2 ─────────────────────────
// 44 — Intolérance au soleil. Params = (soleil u, soleil v, visible à l'écran, force)
// Params2 = (face au soleil -1..1, persistance u, persistance v, persistance force)
vec3 fxSun(vec2 uv) {
    vec2 sp = Params.xy;
    float on = Params.z, k = Params.w;
    float face = smoothstep(0.45, 1.0, Params2.x) * k;
    vec2 d = (uv - sp) * vec2(aspect(), 1.0);
    float r = length(d);
    // le soleil caché derrière le relief éblouit beaucoup moins
    vec2 spc = clamp(sp, vec2(0.002), vec2(0.998));
    float vis = on * k * (isSky(spc) ? 1.0 : 0.3);
    // chaleur qui fait onduler l'air autour du soleil
    float heat = vis * (1.0 - smoothstep(0.0, 0.55, r));
    vec2 wob = vec2(sin(uv.y * 90.0 + Time * 7.0), cos(uv.x * 80.0 + Time * 6.0)) * 0.004 * heat;
    vec3 c = tex(uv + wob);
    // surexposition : les zones claires débordent
    vec3 bl = blur9(uv, 0.012);
    c += max(bl - 0.55, 0.0) * (1.6 * face + 2.2 * heat);
    // le disque aveuglant : on ne voit plus rien là où est le soleil
    float core = vis * (1.0 - smoothstep(0.10 + 0.10 * face, 0.36 + 0.34 * face, r));
    float halo = vis * exp(-r * 3.2) * 0.9;
    // traînée horizontale façon objectif anamorphique
    float streak = vis * exp(-abs(d.y) * 70.0) * exp(-abs(d.x) * 1.6);
    // reflets fantômes le long de l'axe soleil -> centre
    vec2 axis = vec2(0.5) - sp;
    vec3 ghosts = vec3(0.0);
    for (int i = 1; i <= 4; i++) {
        float f = float(i) * 0.55;
        vec2 gp = sp + axis * (1.0 + f);
        float gr = length((uv - gp) * vec2(aspect(), 1.0));
        float size = 0.025 + 0.022 * float(i);
        float ring = smoothstep(size, size * 0.7, gr) * (0.6 + 0.4 * smoothstep(size * 0.5, size, gr));
        vec3 tint = i == 1 ? vec3(0.4, 1.0, 0.6) : i == 2 ? vec3(1.0, 0.5, 0.3) : i == 3 ? vec3(0.5, 0.6, 1.0) : vec3(1.0, 0.9, 0.4);
        ghosts += tint * ring * 0.22;
    }
    ghosts *= vis;
    // exposition de la scène, puis le soleil par-dessus (blanc pur)
    c = 1.0 - exp(-c * (1.25 + 1.4 * face));
    vec3 white = vec3(1.0, 0.99, 0.94);
    c = mix(c, white, clamp(core * 1.3 + halo * 0.55 + face * 0.6, 0.0, 1.0));
    c += vec3(1.0, 0.82, 0.55) * streak * 0.9 + ghosts;
    // franges colorées au bord de la zone aveuglée
    float edge = vis * smoothstep(0.08, 0.0, abs(r - (0.38 + 0.3 * face))) * 0.35;
    c += vec3(0.9, 0.3, 1.0) * edge;
    // persistance rétinienne : une tache sombre reste là où était le soleil
    float ai = Params2.w * (1.0 - smoothstep(0.04, 0.22, length((uv - Params2.yz) * vec2(aspect(), 1.0))));
    c = mix(c, c * vec3(0.25, 0.12, 0.42) + vec3(0.04, 0.1, 0.02), clamp(ai * 1.2, 0.0, 0.9));
    if (Calm > 0.5) c = min(c, vec3(0.86));
    return c;
}
// 45 — Vision de mouche : une mosaïque d'yeux hexagonaux, chacun avec son propre point de vue.
vec3 fxFly(vec2 uv) {
    vec2 p = (uv - 0.5) * vec2(aspect(), 1.0);
    float rr = length(p);
    p *= 1.0 - 0.18 * rr * rr;
    float cell = 0.088;
    vec2 s = vec2(1.0, 1.7320508);
    vec2 q = p / cell;
    vec4 hc = floor(vec4(q, q - vec2(0.5, 1.0)) / s.xyxy) + 0.5;
    vec4 h = vec4(q - hc.xy * s, q - (hc.zw + 0.5) * s);
    vec4 hex = dot(h.xy, h.xy) < dot(h.zw, h.zw) ? vec4(h.xy, hc.xy) : vec4(h.zw, hc.zw + 0.5);
    vec2 local = hex.xy;                        // dans la facette, |local| <= ~0.58
    vec2 center = hex.zw * s * cell;            // centre de la facette (repère p)
    vec2 cuv = center / vec2(aspect(), 1.0) + 0.5;
    // chaque facette voit une portion agrandie et un peu décalée du monde
    float seed = hash12(hex.zw);
    vec2 jitter = (hash22(hex.zw + 3.0) - 0.5) * 0.012;
    vec2 suv = cuv + jitter + local * cell * 1.9 / vec2(aspect(), 1.0);
    vec3 c = tex(suv);
    // reflets irisés propres à chaque facette
    c *= mix(vec3(1.0), 0.75 + 0.5 * hsv2rgb(vec3(fract(seed + Time * 0.03), 0.6, 1.0)), 0.22);
    // bombé : centre lumineux, bords sombres, liseré noir entre les facettes
    float hd = max(abs(local.x) * 0.866 + abs(local.y) * 0.5, abs(local.y)) / 0.5;
    c *= 1.15 - 0.55 * hd * hd;
    c *= smoothstep(1.0, 0.86, hd);
    float spec = smoothstep(0.35, 0.0, length(local - vec2(-0.16, 0.2)));
    c += vec3(0.9, 1.0, 0.95) * spec * 0.18;
    return c * vignette(uv, 0.5);
}
// 46 — Ciel de sang
vec3 fxBloodSky(vec2 uv) {
    vec3 c = tex(uv);
    float sky = isSky(uv) ? 1.0 : 0.0;
    float d = linDepth(uv);
    float l = lum(c);
    // le ciel : dégradé cramoisi qui garde le relief lumineux du ciel d'origine
    vec3 top = vec3(0.22, 0.0, 0.03), hor = vec3(0.9, 0.16, 0.06);
    vec3 skyc = mix(top, hor, smoothstep(0.3, 0.9, l));
    float n = noise(uv * vec2(5.0, 2.5) + vec2(Time * 0.04, 0.0)) * noise(uv * vec2(11.0, 6.0) - vec2(Time * 0.025, Time * 0.01));
    skyc += vec3(0.32, 0.03, 0.0) * smoothstep(0.15, 0.6, n);
    // le soleil (ou la lune) devient un disque noir cerné de rouge
    float body = smoothstep(0.9, 0.99, l);
    skyc = mix(skyc, vec3(0.04, 0.0, 0.0), body);
    skyc += vec3(1.0, 0.2, 0.05) * smoothstep(0.75, 0.9, l) * (1.0 - body) * 0.6;
    // le sol : lumière rouge et brume cramoisie au loin
    vec3 land = c * vec3(1.08, 0.6, 0.52);
    land = mix(land, hor * 0.55, smoothstep(24.0, 180.0, d) * 0.75);
    c = mix(land, skyc, sky);
    return c * vignette(uv, 0.45);
}

void main() {
    vec2 uv = texCoord;
    vec3 orig = texture(DiffuseSampler, uv).rgb;
    vec3 c = orig;
    if (Mode == 1) c = fxTriptych(uv);
    else if (Mode == 2) c = tex(vec2(1.0 - uv.x, uv.y));
    else if (Mode == 3) c = tex(vec2(uv.x, 1.0 - uv.y));
    else if (Mode == 4) c = fxTaquin(uv);
    else if (Mode == 5) c = fxKaleido(uv);
    else if (Mode == 6) c = fxVertigo(uv);
    else if (Mode == 7) c = fxPortrait(uv);
    else if (Mode == 8) c = fxWandering(uv);
    else if (Mode == 9) c = fxMelt(uv);
    else if (Mode == 10) c = fxWhirl(uv);
    else if (Mode == 11) c = fxFisheye(uv);
    else if (Mode == 12) c = fxShatter(uv);
    else if (Mode == 13) c = fxNoir(uv);
    else if (Mode == 14) c = fxScarlet(uv);
    else if (Mode == 15) c = fxNegative(uv);
    else if (Mode == 16) c = fxThermal(uv);
    else if (Mode == 17) c = fxEightBit(uv);
    else if (Mode == 18) c = fxPocket(uv);
    else if (Mode == 19) c = fxVhs(uv);
    else if (Mode == 20) c = fxCrt(uv);
    else if (Mode == 21) c = fxAnaglyph(uv);
    else if (Mode == 22) c = fxTipsy(uv);
    else if (Mode == 23) c = fxTunnel(uv);
    else if (Mode == 24) c = fxSketch(uv);
    else if (Mode == 25) c = fxSourceCode(uv);
    else if (Mode == 26) c = fxPsychedelic(uv);
    else if (Mode == 27) c = fxMyopia(uv);
    else if (Mode == 28) c = fxEcholocation(uv);
    else if (Mode == 29) c = fxWetLens(uv);
    else if (Mode == 30) c = fxComic(uv);
    else if (Mode == 31) c = fxCinemascope(uv);
    else if (Mode == 32) c = fxNightGoggles(uv);
    else if (Mode == 33) c = fxColourSwap(uv);
    else if (Mode == 34) c = fxSilhouette(uv);
    else if (Mode == 35) c = fxStainedGlass(uv);
    else if (Mode == 36) c = fxPointillism(uv);
    else if (Mode == 37) c = fxXray(uv);
    else if (Mode == 38) c = fxHologram(uv);
    else if (Mode == 39) c = fxTiltShift(uv);
    else if (Mode == 40) c = fxVideoWall(uv);
    else if (Mode == 41) c = fxDroste(uv);
    else if (Mode == 42) c = fxRipple(uv);
    else if (Mode == 43) c = fxGlitch(uv);
    else if (Mode == 44) c = fxSun(uv);
    else if (Mode == 45) c = fxFly(uv);
    else if (Mode == 46) c = fxBloodSky(uv);

    // Les effets « géométriques » gèrent eux-mêmes l'intensité ; les autres sont fondus.
    bool selfFaded = Mode == 9 || Mode == 10 || Mode == 11 || Mode == 12 || Mode == 22 || Mode == 23 || Mode == 27 || Mode == 31 || Mode == 39 || Mode == 42;
    if (!selfFaded) c = mix(orig, c, clamp(Intensity, 0.0, 1.0));
    fragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
