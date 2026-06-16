#version 300 es
precision mediump float;

in vec3 vPosition;
in vec4 aColor;
in vec3 aNormal;
out vec4 vColor;
out vec3 vNormal;
out vec3 vFragPos;

uniform mat4 uMVPMatrix;
uniform mat3 uNormalMatrix;
uniform float uTime;
uniform float uBeat;

void main() {
    vec4 position = vec4(vPosition, 1.0);
    
    // Add animation based on beat
    float beatScale = 1.0 + uBeat * 0.2;
    position.xyz *= beatScale;
    
    gl_Position = uMVPMatrix * position;
    vFragPos = vec3(position);
    vNormal = normalize(uNormalMatrix * aNormal);
    vColor = aColor;
}
