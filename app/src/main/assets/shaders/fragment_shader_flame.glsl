precision mediump float;
varying vec4 vColor;

void main() {
    vec2 coord = gl_PointCoord - vec2(0.5);
    float dist = length(coord);
    if(dist > 0.5) discard;
    
    float alpha = 1.0 - smoothstep(0.0, 0.5, dist);
    vec4 color = vColor;
    color.a *= alpha;
    
    // Add inner glow
    float glow = 1.0 - dist * 2.0;
    color.rgb += glow * 0.3;
    
    gl_FragColor = color;
}
