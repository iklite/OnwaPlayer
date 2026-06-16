#version 300 es

precision highp float;

in vec4 vColor;

out vec4 fragColor;

void main() {
    fragColor = clamp(vColor, 0.0, 1.0);
}
