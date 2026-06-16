#version 300 es

precision highp float;

in vec3 vPosition;
in vec2 aTexCoord;
in vec4 aColor;

uniform mat4 uMVPMatrix;

out vec2 vTexCoord;
out vec4 vColor;

void main() {
    gl_Position = uMVPMatrix * vec4(vPosition, 1.0);
    vTexCoord = aTexCoord;
    vColor = aColor;
}
