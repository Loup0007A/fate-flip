#version 150
// (Minecraft 26.x : la ligne #version est remplacée par la cible, voir « shader_rewrites » de target.json)

//#if mc26
//$$ // Minecraft 26.x : un triangle plein écran, coordonnées de texture prises dans QuadUV.
//$$ layout(std140) uniform FxInfo {
//$$     mat4 Extra;
//$$     vec4 Params;
//$$     vec4 Params2;
//$$     vec4 QuadUV;
//$$     vec2 OutSize;
//$$     vec2 DepthRange;
//$$     int Mode;
//$$     float Time;
//$$     float Intensity;
//$$     float Calm;
//$$     float ZeroToOne;
//$$ };
//$$
//$$ layout(location = 0) out vec2 texCoord;
//$$
//$$ void main() {
//$$     vec2 uv = vec2((gl_VertexIndex << 1) & 2, gl_VertexIndex & 2);
//$$     gl_Position = vec4(uv * 2.0 - 1.0, 0.0, 1.0);
//$$     texCoord = mix(QuadUV.xy, QuadUV.zw, uv);
//$$ }
//#else
// Quad plein écran (ou sous-rectangle) : Position est déjà en coordonnées 0..1 de la fenêtre.
in vec3 Position;
in vec2 UV0;

out vec2 texCoord;

void main() {
    gl_Position = vec4(Position.xy * 2.0 - 1.0, 0.0, 1.0);
    texCoord = UV0;
}
//#endif
