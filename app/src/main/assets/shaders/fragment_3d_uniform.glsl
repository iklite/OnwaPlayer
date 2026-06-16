#version 300 es

precision highp float;

const int MAX_LIGHTS = 3;

struct Light {
    vec3 position;
    vec3 color;
};

in vec3 vWorldPos;
in vec3 vNormal;

uniform vec4 uColor;
uniform int uLightCount;
uniform vec3 uAmbient;
uniform Light uLights[MAX_LIGHTS];

out vec4 fragColor;

void main() {
    vec3 N = normalize(vNormal);
    vec3 V = normalize(vec3(0.0, 0.0, 1.0) - vWorldPos);

    vec3 ambient = uAmbient;
    vec3 diffuse = vec3(0.0);
    vec3 specular = vec3(0.0);

    for (int i = 0; i < MAX_LIGHTS; i++) {
        if (i >= uLightCount) break;

        vec3 L = uLights[i].position - vWorldPos;
        float dist = length(L);
        L = normalize(L);

        float NdotL = max(dot(N, L), 0.0);
        
        vec3 H = normalize(L + V);
        float NdotH = max(dot(N, H), 0.0);
        float spec = pow(NdotH, 32.0);

        float attenuation = 1.0 / (1.0 + 0.5 * dist + 0.2 * dist * dist);

        diffuse += uLights[i].color * NdotL * attenuation;
        specular += uLights[i].color * spec * attenuation * 0.5;
    }

    vec3 finalColor = uColor.rgb * (ambient + diffuse) + specular;
    finalColor = clamp(finalColor, 0.0, 1.0);

    fragColor = vec4(finalColor, uColor.a);
}
