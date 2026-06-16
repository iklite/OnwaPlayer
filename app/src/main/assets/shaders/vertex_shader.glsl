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
    
    // Add some animation based on time and beat
    float beatEffect = uBeat * 0.1;
    position.y += sin(uTime + position.x * 10.0) * 0.05 * (1.0 + beatEffect);
    
    gl_Position = uMVPMatrix * position;
    vColor = aColor;
    
    // Add some color animation
    vColor.r += sin(uTime * 2.0) * 0.1;
    vColor.g += cos(uTime * 1.5) * 0.1;
    vColor.b += sin(uTime * 1.2 + 1.0) * 0.1;
}
