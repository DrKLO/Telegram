precision lowp float;

uniform sampler2D sTexture;

varying vec2 centerTextureCoordinate;
varying vec2 oneStepLeftTextureCoordinate;
varying vec2 twoStepsLeftTextureCoordinate;
varying vec2 threeStepsLeftTextureCoordinate;
varying vec2 fourStepsLeftTextureCoordinate;
varying vec2 oneStepRightTextureCoordinate;
varying vec2 twoStepsRightTextureCoordinate;
varying vec2 threeStepsRightTextureCoordinate;
varying vec2 fourStepsRightTextureCoordinate;

void main() {
    lowp vec4 fragmentColor = texture2D(sTexture, centerTextureCoordinate) * 0.38026;

    fragmentColor += texture2D(sTexture, oneStepLeftTextureCoordinate) * 0.27667;
    fragmentColor += texture2D(sTexture, oneStepRightTextureCoordinate) * 0.27667;

    fragmentColor += texture2D(sTexture, twoStepsLeftTextureCoordinate) * 0.08074;
    fragmentColor += texture2D(sTexture, twoStepsRightTextureCoordinate) * 0.08074;

    fragmentColor += texture2D(sTexture, threeStepsLeftTextureCoordinate) * -0.02612;
    fragmentColor += texture2D(sTexture, threeStepsRightTextureCoordinate) * -0.02612;

    fragmentColor += texture2D(sTexture, fourStepsLeftTextureCoordinate) * -0.02143;
    fragmentColor += texture2D(sTexture, fourStepsRightTextureCoordinate) * -0.02143;

    gl_FragColor = fragmentColor;
}