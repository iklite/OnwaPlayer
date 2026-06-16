#version 300 es

precision highp float;

const int MAX_LIGHTS = 3;

struct Light {
    vec3 position;
    vec3 color;
};

in vec3 vWorldPos;
in vec2 vTexCoord;
in vec4 vColor;

uniform sampler2D uTexture;
uniform int uLightCount;
uniform vec3 uAmbient;
uniform Light uLights[MAX_LIGHTS];

out vec4 fragColor;

void main() {
    vec4 texColor = texture(uTexture, vTexCoord);
    
    vec3 ambient = uAmbient;
    vec3 lighting = vec3(0.0);

    for (int i = 0; i < MAX_LIGHTS; i++) {
        if (i >= uLightCount) break;

        vec3 lightDir = uLights[i].position - vWorldPos;
        float dist = length(lightDir);

        float attenuation = 1.0 / (1.0 + 0.3 * dist * dist);
        vec3 contribution = uLights[i].color * attenuation;
        lighting += contribution;
    }

    lighting = min(lighting, vec3(2.0));

    vec3 baseColor = vColor.rgb * texColor.rgb;
    vec3 finalColor = baseColor * (ambient + lighting);
    finalColor = clamp(finalColor, 0.0, 1.0);
    
    float finalAlpha = vColor.a * texColor.a;

    fragColor = vec4(finalColor, finalAlpha);
}
