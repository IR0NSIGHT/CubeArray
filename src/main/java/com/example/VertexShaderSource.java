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
            uniform int paletteSize; // total number of entries
                            
            // Pass to fragment shader
            out vec3 vColor;
            out vec3 vWorldPos;
                            
            void main()
            {
             // Normalize palette index -> [0..1] texcoord
             float texCoord = (float(aInstanceColorIndex) + 0.5) / float(paletteSize);
                            
             // Lookup per-block attributes
             vec3 blockColor = texture(colorPaletteTex, texCoord).rgb;
             vec3 blockSize  = texture(sizePaletteTex,  texCoord).rgb;
             vec3 blockOffset = texture(offsetPaletteTex,  texCoord).rgb;
              
             // Scale + translate block vertex into world space
             vec3 scaledVertex = aPos * blockSize;
             vec3 worldPos     = scaledVertex + blockOffset + aInstancePos;
                            
             // Outputs
             vColor    = blockColor;
             vWorldPos = worldPos;
                            
             gl_Position = projection * view * vec4(worldPos, 1.0);
            }
            """;
}
