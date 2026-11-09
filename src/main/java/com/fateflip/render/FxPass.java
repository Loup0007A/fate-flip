package com.fateflip.render;

/**
 * Un passage du shader d'écran : quel effet, à quelle étape du rendu, avec
 * quels paramètres. Les numéros de mode correspondent à screen_fx.fsh.
 */
public final class FxPass {

    /** WORLD : monde + main, l'interface reste nette. FULL : toute l'image, interface comprise. */
    public enum Stage { WORLD, FULL }

    // ESPACE
    public static final int TRIPTYCH = 1, MIRROR = 2, ANTIPODES = 3, TAQUIN = 4, KALEIDO = 5, VERTIGO = 6,
        PORTRAIT = 7, WANDERING = 8, MELT = 9, WHIRLPOOL = 10, FISHEYE = 11, SHATTERED = 12;
    // VISION
    public static final int NOIR = 13, SCARLET = 14, NEGATIVE = 15, THERMAL = 16, EIGHT_BIT = 17, POCKET = 18,
        VHS = 19, CRT = 20, ANAGLYPH = 21, TIPSY = 22, TUNNEL = 23, SKETCH = 24, SOURCE_CODE = 25,
        PSYCHEDELIC = 26, MYOPIA = 27, ECHOLOCATION = 28, WET_LENS = 29, COMIC = 30, CINEMASCOPE = 31,
        NIGHT_GOGGLES = 32, COLOUR_SWAP = 33;

    public final int mode;
    public final Stage stage;
    public final float[] params = new float[4];
    public final float[] params2 = new float[4];
    public final float[] extra = new float[16];
    public final boolean needsDepth;
    public final boolean usesGlyphs;

    public FxPass(int mode, Stage stage) {
        this.mode = mode;
        this.stage = stage;
        this.needsDepth = mode == THERMAL || mode == ANAGLYPH || mode == MYOPIA || mode == ECHOLOCATION
            || mode == COMIC || mode == SKETCH;
        this.usesGlyphs = mode == SOURCE_CODE;
    }

    public FxPass set(float a, float b, float c, float d) {
        params[0] = a; params[1] = b; params[2] = c; params[3] = d;
        return this;
    }

    public FxPass set2(float a, float b, float c, float d) {
        params2[0] = a; params2[1] = b; params2[2] = c; params2[3] = d;
        return this;
    }
}
