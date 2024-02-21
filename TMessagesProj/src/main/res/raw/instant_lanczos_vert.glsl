uniform mat4 uMVPMatrix;
uniform mat4 uSTMatrix;

attribute vec4 aPosition;
attribute vec4 aTextureCoord;

uniform float texelWidthOffset;
uniform float texelHeightOffset;

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
   gl_Position = uMVPMatrix * aPosition;

   vec2 firstOffset = vec2(texelWidthOffset, texelHeightOffset);
   vec2 secondOffset = vec2(2.0 * texelWidthOffset, 2.0 * texelHeightOffset);
   vec2 thirdOffset = vec2(3.0 * texelWidthOffset, 3.0 * texelHeightOffset);
   vec2 fourthOffset = vec2(4.0 * texelWidthOffset, 4.0 * texelHeightOffset);

   centerTextureCoordinate = (uSTMatrix * aTextureCoord).xy;
   oneStepLeftTextureCoordinate = centerTextureCoordinate - firstOffset;
   twoStepsLeftTextureCoordinate = centerTextureCoordinate - secondOffset;
   threeStepsLeftTextureCoordinate = centerTextureCoordinate - thirdOffset;
   fourStepsLeftTextureCoordinate = centerTextureCoordinate - fourthOffset;
   oneStepRightTextureCoordinate = centerTextureCoordinate + firstOffset;
   twoStepsRightTextureCoordinate = centerTextureCoordinate + secondOffset;
   threeStepsRightTextureCoordinate = centerTextureCoordinate + thirdOffset;
   fourStepsRightTextureCoordinate = centerTextureCoordinate + fourthOffset;
}