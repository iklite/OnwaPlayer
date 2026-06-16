#version 300 es
precision mediump float;

in vec4 vColor;
out vec4 fragColor;

uniform float uTime;
uniform float uBeat;

void main() {
    fragColor = vColor;
    
    // Add some glow effect based on beat
    float glow = uBeat * 0.3;
    fragColor.rgb += glow;
}
