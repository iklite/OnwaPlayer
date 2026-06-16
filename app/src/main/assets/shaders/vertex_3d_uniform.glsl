#version 300 es

precision highp float;

in vec3 vPosition;
in vec3 aNormal;

uniform mat4 uMVPMatrix;
uniform mat4 uModel;

out vec3 vWorldPos;
out vec3 vNormal;

void main() {
    gl_Position = uMVPMatrix * vec4(vPosition, 1.0);
    
    vWorldPos = (uModel * vec4(vPosition, 1.0)).xyz;
    
    mat3 normalMatrix = transpose(inverse(mat3(uModel)));
    vNormal = normalize(normalMatrix * aNormal);
}
