#version 150

// Quad plein écran (ou sous-rectangle) : Position est déjà en coordonnées 0..1 de la fenêtre.
in vec3 Position;
in vec2 UV0;

out vec2 texCoord;

void main() {
    gl_Position = vec4(Position.xy * 2.0 - 1.0, 0.0, 1.0);
    texCoord = UV0;
}
