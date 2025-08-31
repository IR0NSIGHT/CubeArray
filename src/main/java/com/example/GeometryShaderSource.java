package com.example;

public class GeometryShaderSource {
    public static final String source =  """
                #version 330 core
                layout(triangles) in;
                layout(triangle_strip, max_vertices = 3) out;

                in vec3 vColor[3];
                in vec3 vWorldPos[3];
                in vec2 UV[3];
                
                out vec3 gColor;
                flat out vec3 Normal;
                out vec3 FragPos;
                out vec2 fragUV;
                
                void main() {
                    vec3 edge1 = vWorldPos[0] - vWorldPos[1];
                    vec3 edge2 = vWorldPos[2] - vWorldPos[1];
                    vec3 faceNormal = normalize(cross(edge1, edge2));

                    for (int i = 0; i < 3; ++i) {
                        FragPos = vWorldPos[i];
                        Normal = faceNormal;   // same for all verts (flat shading)
                        fragUV = UV[i];
                        gColor = vColor[i];
                        gl_Position = gl_in[i].gl_Position;
        
                        EmitVertex();
                    }
                    EndPrimitive();
                }
                """;
}
