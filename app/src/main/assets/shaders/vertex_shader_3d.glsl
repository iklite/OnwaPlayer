#version 300 es
precision mediump float;

in vec3 vPosition;
in vec4 aColor;
out vec4 vColor;

uniform mat4 uMVPMatrix;
uniform float uTime;
uniform float uBeat;

void main() {
    vec4 position = vec4(vPosition, 1.0);
    
    // Add some 3D animation effects
    float scale = 1.0 + sin(uTime * 0.5) * 0.05 + uBeat * 0.1;
    position.xyz *= scale;
    
    gl_Position = uMVPMatrix * position;
    vColor = aColor;
    
    // Add depth-based color variation
    float depth = gl_Position.z * 0.5;
    vColor.rgb *= (1.0 - depth * 0.3);
}
