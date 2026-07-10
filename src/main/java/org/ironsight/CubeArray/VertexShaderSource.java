package org.ironsight.CubeArray;

public class VertexShaderSource {
  public static final String source =
      """
            #version 330 core

            // Per-vertex cube geometry
            layout(location = 0) in vec3 aPos;

            // Per-vertex attributes
            layout(location = 1) in vec2 vertexUV;
            layout(location = 4) in float aFaceId; // which of the 6 cube faces this vertex belongs to

            // Per-instance attributes
            layout(location = 2) in vec3 aInstancePos;
            layout(location = 3) in int  aInstanceColorIndex;


            // Camera transforms
            uniform mat4 projection;
            uniform mat4 view;

            // Palettes (bound to texture units 0 and 1 in your Java code)
            uniform sampler2D colorPaletteTex;
            uniform sampler2D sizePaletteTex;
            uniform sampler2D offsetPaletteTex;
            uniform sampler2D rotationPaletteTex;
            uniform sampler2D uvPaletteTex;
            uniform int paletteSize; // total number of entries
            uniform float atlasSize; // block texture atlas width/height in pixels

            // Pass to fragment shader
            out vec3 vColor;
            out vec3 vWorldPos;
            out vec2 UV;
            out vec4 viewPos;
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

            float rand(float n) {
                return fract(sin(n) * 43758.5453123);
            }

            void main()
            {
             // Normalize palette index -> [0..1] texcoord
             float texCoord = (float(aInstanceColorIndex) + 0.5) / float(paletteSize);

             // Lookup per-block attributes
             vec3 blockColor = texture(colorPaletteTex, vec2(texCoord, 0.5)).rgb;
             vec3 blockSize  = texture(sizePaletteTex,  vec2(texCoord, 0.5)).rgb;
             vec3 blockOffset = texture(offsetPaletteTex,  vec2(texCoord, 0.5)).rgb;
             vec3 blockRotation = texture(rotationPaletteTex,  vec2(texCoord, 0.5)).rgb;
             // uv palette is 2D: column = block type, row = face. Pick this vertex's face row.
             float faceCoord = (aFaceId + 0.5) / 6.0;
             vec4 uvCoords =  texture(uvPaletteTex, vec2(texCoord, faceCoord)).rgba;
             // Scale + translate block vertex into world space


             vec3 scaledVertex = aPos * blockSize + blockOffset;
             // blockstate rotation around the block centre: Minecraft rotates by x (around the
             // X axis) then y (around the Y axis), so compose as Ry * Rx
             vec3 rotatedPosition =
                 rotationY(blockRotation.y) * rotationX(blockRotation.x) * scaledVertex;
             vec3 worldPos     = rotatedPosition + aInstancePos;

             // Outputs
             float r = rand(gl_InstanceID) * 0.025;
             vColor    = blockColor + vec3(r);
             vWorldPos = worldPos;

             vec2 uvStart = uvCoords.rg;
             vec2 uvEnd = uvCoords.ba;
             if (uvCoords != vec4(0,0,0,0)) { // inset by half a texel so edges dont flicker/bleed; scales with atlas size so small crops (e.g. a torch strip) survive. skip 0,0,0,0 uvs which have no texture
                float halfTexel = 0.5 / atlasSize;
                uvStart = uvCoords.rg + halfTexel;
                uvEnd = uvCoords.ba - halfTexel;
             }

             UV = (vertexUV * (uvEnd - uvStart)) + uvStart;
             viewPos = view * vec4(worldPos, 1.0);
             gl_Position = projection * view * vec4(worldPos, 1.0);
            }
            """;
}
