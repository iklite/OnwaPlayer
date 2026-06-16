#version 300 es
precision mediump float;

in vec4 vColor;
in vec3 vNormal;
in vec3 vFragPos;
out vec4 fragColor;

uniform vec3 uLightPos;
uniform vec3 uViewPos;
uniform float uTime;
uniform float uBeat;

void main() {
    vec3 lightColor = vec3(1.0, 1.0, 1.0);
    
    // Ambient
    float ambientStrength = 0.3;
    vec3 ambient = ambientStrength * lightColor;
    
    // Diffuse
    vec3 norm = normalize(vNormal);
    vec3 lightDir = normalize(uLightPos - vFragPos);
    float diff = max(dot(norm, lightDir), 0.0);
    vec3 diffuse = diff * lightColor;
    
    // Specular
    float specularStrength = 0.5;
    vec3 viewDir = normalize(uViewPos - vFragPos);
    vec3 reflectDir = reflect(-lightDir, norm);
    float spec = pow(max(dot(viewDir, reflectDir), 0.0), 32.0);
    vec3 specular = specularStrength * spec * lightColor;
    
    // Combine lighting
    vec3 result = (ambient + diffuse + specular) * vColor.rgb;
    
    // Add beat-based pulsing
    result *= (1.0 + uBeat * 0.2);
    
    fragColor = vec4(result, vColor.a);
}
