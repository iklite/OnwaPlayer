attribute vec3 vPosition;
attribute vec3 aNormal;
uniform float uTime;
uniform float uBeat;
varying vec3 vNormal;
varying vec3 vPositionWorld;

void main() {
    vec3 pos = vPosition;
    // Create wave effect
    float wave = sin(pos.x * 3.0 + uTime * 2.0) * 0.1 
               + sin(pos.y * 2.0 + uTime * 1.7) * 0.08
               + sin(pos.x * 5.0 + pos.y * 3.0 + uTime * 1.3) * 0.05;
    
    pos.z = wave * (1.0 + uBeat * 0.5);
    
    // Update normals for lighting
    vNormal = normalize(aNormal + vec3(0.0, 0.0, wave * 0.5));
    vPositionWorld = pos;
    
    gl_Position = vec4(pos, 1.0);
}
