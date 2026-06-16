#version 300 es

precision highp float;

const int MAX_LIGHTS = 3;

struct Light {
    vec3 position;
    vec3 color;
};

in vec3 vWorldPos;
in vec4 vColor;

uniform int uLightCount;
uniform vec3 uAmbient;
uniform Light uLights[MAX_LIGHTS];

out vec4 fragColor;

void main() {
    vec3 ambient = uAmbient;
    vec3 lighting = vec3(0.0);

    for (int i = 0; i < MAX_LIGHTS; i++) {
        if (i >= uLightCount) break;

        vec3 lightDir = uLights[i].position - vWorldPos;
        float dist = length(lightDir);
        lightDir = normalize(lightDir);

        float attenuation = 1.0 / (1.0 + 0.4 * dist * dist);
        vec3 contribution = uLights[i].color * attenuation;
        lighting += contribution;
    }

    lighting = min(lighting, vec3(1.8));

    vec3 finalColor = vColor.rgb * (ambient + lighting);
    finalColor = clamp(finalColor, 0.0, 1.0);

    fragColor = vec4(finalColor, vColor.a);
}
