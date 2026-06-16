precision mediump float;
varying vec3 vNormal;
varying vec3 vPositionWorld;
uniform float uTime;
uniform float uBeat;

void main() {
    // Water color with depth
    vec3 waterColor = vec3(0.1, 0.3, 0.8);
    vec3 foamColor = vec3(0.9, 0.95, 1.0);
    
    // Fake lighting
    vec3 lightDir = normalize(vec3(1.0, 1.0, 1.0));
    float diff = max(dot(vNormal, lightDir), 0.0);
    
    // Add specular highlights
    vec3 viewDir = normalize(vec3(0.0, 0.0, 1.0) - vPositionWorld);
    vec3 reflectDir = reflect(-lightDir, vNormal);
    float spec = pow(max(dot(viewDir, reflectDir), 0.0), 32.0);
    
    // Create foam patterns
    float foam = sin(vPositionWorld.x * 10.0 + uTime * 3.0) 
               * sin(vPositionWorld.y * 8.0 + uTime * 2.0);
    foam = smoothstep(0.7, 0.9, foam) * (1.0 + uBeat);
    
    // Mix colors
    vec3 color = mix(waterColor, foamColor, foam);
    color = color * (0.3 + diff * 0.7) + spec * 0.3;
    
    // Add transparency
    float alpha = 0.7 + diff * 0.3 + foam * 0.2;
    
    gl_FragColor = vec4(color, alpha);
}
