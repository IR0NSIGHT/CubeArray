package com.example;

public class VertexShaderSource {
    public static final String source = """
            #version 330 core
                            
            // Per-vertex cube geometry
            layout(location = 0) in vec3 aPos;
                            
            // Per-instance attributes
            layout(location = 1) in vec3 aInstancePos;
            layout(location = 2) in int  aInstanceColorIndex;
                            
            // Camera transforms
            uniform mat4 projection;
            uniform mat4 view;
                            
            // Palettes (bound to texture units 0 and 1 in your Java code)
            uniform sampler1D colorPaletteTex;
            uniform sampler1D sizePaletteTex;
            uniform sampler1D offsetPaletteTex;
            uniform sampler1D rotationPaletteTex;
            uniform int paletteSize; // total number of entries
                            
            // Pass to fragment shader
            out vec3 vColor;
            out vec3 vWorldPos;
             
            // Rotation around the X axis
            mat3 rotationX(float angle) {
                float sine = sin(angle);
                float cosine = cos(angle);
                        
                return mat3(
                    1.0,    0.0,     0.0,
                    0.0, cosine, -sine,
                    0.0,  sine, cosine
                );
            }
                        
            // Rotation around the Y axis
            mat3 rotationY(float angle) {
                float sine = sin(angle);
                float cosine = cos(angle);
                        
                return mat3(
                    cosine, 0.0,  sine,
                    0.0,    1.0,  0.0,
                   -sine,   0.0, cosine
                );
            }
                        
            // Rotation around the Z axis
            mat3 rotationZ(float angle) {
                float sine = sin(angle);
                float cosine = cos(angle);
                        
                return mat3(
                    cosine, -sine, 0.0,
                    sine,   cosine, 0.0,
                    0.0,     0.0,  1.0
                );
            }
                            
            void main()
            {
             // Normalize palette index -> [0..1] texcoord
             float texCoord = (float(aInstanceColorIndex) + 0.5) / float(paletteSize);
                            
             // Lookup per-block attributes
             vec3 blockColor = texture(colorPaletteTex, texCoord).rgb;
             vec3 blockSize  = texture(sizePaletteTex,  texCoord).rgb;
             vec3 blockOffset = texture(offsetPaletteTex,  texCoord).rgb;
             vec3 blockRotation = texture(rotationPaletteTex,  texCoord).rgb;
             // Scale + translate block vertex into world space
             vec3 scaledVertex = aPos * blockSize + blockOffset;
             vec3 rotatedPosition =  rotationY(blockRotation.y) * scaledVertex;
             vec3 worldPos     = rotatedPosition + aInstancePos;
                            
             // Outputs
             vColor    = blockColor;
             vWorldPos = worldPos;
                            
             gl_Position = projection * view * vec4(worldPos, 1.0);
            }
            """;
}
