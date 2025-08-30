package com.example;

public class FragmentShaderSource {
    public static final String source = """
                #version 330 core
                out vec4 FragColor;

                in vec3 gColor;
                in vec3 FragPos;
                flat in vec3 Normal;

                uniform vec3 lightDir;
                uniform vec3 lightColor;

                void main() {
                    float diff = max(dot(Normal, -lightDir), 0.0);
                    vec3 diffuse = diff * lightColor;

                    vec3 ambient = vec3(1,1,1);
                    //DEBUG: show normals: vec3 result = (Normal + vec3(1,1,1)*vec3(0.5,0.5,0.5));
                    vec3 result =  (vec3(0.5) * ambient + vec3(0.5) *diffuse) * gColor;
                    FragColor = vec4(result, 1.0);
                }
                """;
}
