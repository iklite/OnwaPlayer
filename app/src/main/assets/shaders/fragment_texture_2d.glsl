#version 300 es

precision highp float;

in vec2 vTexCoord;
in vec4 vColor;

uniform sampler2D uTexture;

out vec4 fragColor;

void main() {
    vec4 texColor = texture(uTexture, vTexCoord);
    
    vec3 finalColor = vColor.rgb * texColor.rgb;
    float finalAlpha = vColor.a * texColor.a;
    
    fragColor = vec4(finalColor, finalAlpha);
}
