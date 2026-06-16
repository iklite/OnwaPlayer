#version 300 es
precision mediump float;

in vec4 vColor;
out vec4 fragColor;

uniform float uTime;
uniform float uBeat;

void main() {
    fragColor = vColor;
    
    // Add specular highlight
    float specular = pow(max(0.0, sin(uTime * 3.0)), 32.0);
    fragColor.rgb += specular * 0.3 * (1.0 + uBeat);
}
