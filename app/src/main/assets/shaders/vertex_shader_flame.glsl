attribute vec3 vPosition;
attribute vec4 aColor;
uniform float uTime;
uniform float uBeat;
varying vec4 vColor;

void main() {
    vec3 pos = vPosition;
    pos.y += sin(uTime * 3.0 + pos.x * 10.0) * 0.05 * (1.0 + uBeat);
    pos.x += cos(uTime * 2.0 + pos.y * 8.0) * 0.03 * uBeat;
    gl_Position = vec4(pos, 1.0);
    gl_PointSize = 10.0 * (1.0 + uBeat * 2.0);
    vColor = aColor;
}
