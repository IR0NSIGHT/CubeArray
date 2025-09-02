package com.example;

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
                float diff = max(dot(Normal, -lightDir), 0.0);
                vec3 diffuse = diff * lightColor;

                vec3 ambient = vec3(1,1,1);
                //DEBUG: show normals: vec3 result = (Normal + vec3(1,1,1)*vec3(0.5,0.5,0.5));
                vec3 result =  (vec3(0.5) * ambient + vec3(0.5) * diffuse);
                float colorStrength = clamp(-fragViewPos.z / 200.0,0,1); //closer: use texture, further: use color
                vec4 baseColor = texture(blockTexture, fragUV);// * (1-colorStrength) + colorStrength * vec4(gColor,1);
                if(baseColor.a < 0.01)  // fully transparent
                    discard;            // skip writing color & depth
                FragColor/*RGBA*/ = baseColor;// * vec4(result.rgb,1);
            }
            """;
}
