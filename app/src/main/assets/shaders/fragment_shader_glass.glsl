precision mediump float;
varying vec4 vColor;
uniform float uTime;

void main() {
    // Glass effect with refraction simulation
    vec2 coord = gl_PointCoord - vec2(0.5);
    float dist = length(coord);
    
    // Refraction distortion
    float refraction = sin(dist * 10.0 - uTime * 3.0) * 0.1;
    coord *= (1.0 + refraction);
    
    // Glass color with edge darkening
    vec4 color = vColor;
    color.rgb *= 1.0 - dist * 0.5;
    
    // Add specular highlight
    vec2 lightDir = vec2(0.3, 0.7);
    float spec = max(dot(normalize(coord), lightDir), 0.0);
    spec = pow(spec, 32.0);
    color.rgb += spec * 0.5;
    
    // Edge glow
    float edge = 1.0 - smoothstep(0.4, 0.5, dist);
    color.a *= edge;
    
    gl_FragColor = color;
}
