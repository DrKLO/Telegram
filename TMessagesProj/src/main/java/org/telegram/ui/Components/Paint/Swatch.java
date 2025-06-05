package org.telegram.ui.Components.Paint;

public class Swatch {

    public int color;
    public float colorLocation;
    public float brushWeight;

    public Swatch(int color, float colorLocation, float brushWeight) {
        this.color = color;
        this.colorLocation = colorLocation;
        this.brushWeight = brushWeight;
    }

    public Swatch clone() {
        return new Swatch(color, colorLocation, brushWeight);
    }
}
