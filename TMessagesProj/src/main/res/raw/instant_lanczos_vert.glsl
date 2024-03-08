uniform mat4 uMVPMatrix;
uniform mat4 uSTMatrix;

attribute vec4 aPosition;
attribute vec4 aTextureCoord;

uniform vec2 texelSize;

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

   vec2 firstOffset = texelSize;
   vec2 secondOffset = 2.0 * texelSize;
   vec2 thirdOffset = 3.0 * texelSize;
   vec2 fourthOffset = 4.0 * texelSize;

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