attribute vec3 vPosition;
attribute vec4 aColor;
uniform float uTime;
uniform float uBeat;
varying vec4 vColor;

void main() {
    vec3 pos = vPosition;
    
    // Add subtle pulse with beat
    float pulse = sin(uTime * 2.0) * 0.05 * uBeat;
    pos *= (1.0 + pulse);
    
    gl_Position = vec4(pos, 1.0);
    vColor = aColor;
}
