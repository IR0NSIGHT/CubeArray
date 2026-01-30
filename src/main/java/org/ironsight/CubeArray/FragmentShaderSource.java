package org.ironsight.CubeArray;

public class FragmentShaderSource {
    public static final String source = """
            #version 330 core
            out vec4 FragColor;

            in vec3 gColor;
            in vec3 FragPos;
            in vec2 fragUV;
            in vec4 fragViewPos;
            flat in vec3 Normal;

            uniform vec3 lightDir;
            uniform vec3 lightColor;
                            
                        
            uniform sampler2D blockTexture;  // your bound texture
                                    
            void main() {
                // --- Lighting ---
                vec3 norm = normalize(Normal);
                float diff = max(dot(norm, -lightDir), 0.0);       // Lambertian diffuse
                vec3 diffuse = diff * lightColor;
                vec3 ambient = vec3(0.7);                          // ambient term
                vec3 gain = vec3(0.2);
                vec3 lighting =  ambient + 0.7 * diffuse;          // mix ambient + diffuse
            
                // --- Texture + vertex color blending ---
                float colorStrength = clamp(-fragViewPos.z / 200.0, 0.0, 1.0);
                if (fragUV.x == 0) { //materials without a texture have UV(0,0)
                    colorStrength = 1;
                }
                //colorStrength = 1; //DEBUG
                vec4 texColor = texture(blockTexture, fragUV);
                vec4 baseColor = mix(texColor, vec4(gColor, 1.0), colorStrength);
                
                // --- Transparency discard ---
                if(baseColor.a < 0.01 || (baseColor.a != 1.0 && baseColor.a < colorStrength * 5.0))
                    discard;
            
                // --- Output final color with lighting applied ---
                FragColor = vec4(baseColor.rgb * lighting, 1.0);
            }
            """;
}
