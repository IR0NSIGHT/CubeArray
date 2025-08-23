package com.example;

// LWJGL Instanced Cube Rendering Example
// Requires LWJGL 3 and OpenGL 3.3+

import org.lwjgl.*;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.*;

import java.nio.*;
import java.util.*;

import org.joml.*;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;

import com.example.SchemReader;


public class InstancedCubes {

    private long window;
    private int width = 1600;
    private int height = 1000;

    private int vao, vbo, ebo, instanceVBO;
    private int shaderProgram;

    private Vector3f[] instancePositions = {
        new Vector3f(0, 0, 0),
        new Vector3f(2, 0, 0),
        new Vector3f(-2, 0, 0),
        new Vector3f(0, 2, 0),
        new Vector3f(0, -2, 0),
        new Vector3f(0, -3, 1),
    };
    private Vector3f[] instanceColors;

    public void run() throws Exception {
        init();
        loop();
        cleanup();
    }

    int gridX = 1000;
    int gridY = 10;
    int gridZ = 1000;

    private void init() throws Exception {
        var res = SchemReader.loadNbtFile();
        instancePositions = res[0];
        instanceColors = res[1];

        System.out.println("generating " + instancePositions.length + " cubes");

        GLFWErrorCallback.createPrint(System.err).set();

        if (!glfwInit())
            throw new IllegalStateException("Unable to initialize GLFW");

        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW.GLFW_SAMPLES, 4); // 4x MSAA

        window = glfwCreateWindow(width, height, "Instanced Cubes", NULL, NULL);
        if (window == NULL)
            throw new RuntimeException("Failed to create GLFW window");

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        glfwShowWindow(window);

        GL.createCapabilities();

        setupShaders();
        setupBuffers();
    }

    private void setupShaders() {

        String vertexShaderSource = """
            #version 330 core
            layout(location = 0) in vec3 aPos;
            layout(location = 1) in vec3 aInstancePos;
            layout(location = 2) in vec3 aInstanceColor;

            
            uniform mat4 projection;
            uniform mat4 view;

            out vec3 vColor;
            out vec3 vWorldPos;

            void main() {
                vec3 worldPos = aPos + aInstancePos;
                vWorldPos = worldPos;
                vColor = aInstanceColor;
                gl_Position = projection * view * vec4(worldPos, 1.0);
            }

        """;
        String geometryShaderSource = """
            #version 330 core
            layout(triangles) in;
            layout(triangle_strip, max_vertices = 3) out;

            in vec3 vColor[3];
            in vec3 vWorldPos[3];

            
            out vec3 gColor;
            flat out vec3 Normal;
            out vec3 FragPos;

            void main() {
                vec3 edge1 = vWorldPos[1] - vWorldPos[0];
                vec3 edge2 = vWorldPos[2] - vWorldPos[0];
                vec3 faceNormal = normalize(cross(edge1, edge2));

                for (int i = 0; i < 3; ++i) {
                    FragPos = vWorldPos[i];
                    Normal = faceNormal;
                    gColor = vColor[i]; // Pass color
                    gl_Position = gl_in[i].gl_Position;
                    EmitVertex();
                }
                EndPrimitive();
            }

        """;

        String fragmentShaderSource = """
            #version 330 core
            out vec4 FragColor;

            in vec3 gColor;
            in vec3 FragPos;
            flat in vec3 Normal;

            uniform vec3 lightDir = normalize(vec3(-0.5, -1.0, -0.3));
            uniform vec3 lightColor = vec3(1.0, 1.0, 1.0);

            void main() {
                float diff = max(dot(Normal, -lightDir), 0.0);
                vec3 diffuse = diff * lightColor;
                vec3 result = 0.1+ diffuse * gColor;
                FragColor = vec4(result, 1.0);
            }
        """;


        int vertexShader = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vertexShader, vertexShaderSource);
        glCompileShader(vertexShader);
        checkCompileErrors(vertexShader, "VERTEX");

        int geometryShader = glCreateShader(GL_GEOMETRY_SHADER);
        glShaderSource(geometryShader, geometryShaderSource);
        glCompileShader(geometryShader);
        checkCompileErrors(geometryShader, "GEOMETRY");

        int fragmentShader = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fragmentShader, fragmentShaderSource);
        glCompileShader(fragmentShader);
        checkCompileErrors(fragmentShader, "FRAGMENT");

        shaderProgram = glCreateProgram();
        glAttachShader(shaderProgram, vertexShader);
        glAttachShader(shaderProgram, geometryShader);
        glAttachShader(shaderProgram, fragmentShader);
        glLinkProgram(shaderProgram);
        checkCompileErrors(shaderProgram, "PROGRAM");

        glDeleteShader(vertexShader);
        glDeleteShader(geometryShader);
        glDeleteShader(fragmentShader);
    }

    private void setupBuffers() {
        float[] cubeVertices = {
            -0.5f, -0.5f, -0.5f,
             0.5f, -0.5f, -0.5f,
             0.5f,  0.5f, -0.5f,
            -0.5f,  0.5f, -0.5f,
            -0.5f, -0.5f,  0.5f,
             0.5f, -0.5f,  0.5f,
             0.5f,  0.5f,  0.5f,
            -0.5f,  0.5f,  0.5f
        };

        int[] cubeIndices = {
            0, 1, 2, 2, 3, 0,
            4, 5, 6, 6, 7, 4,
            0, 1, 5, 5, 4, 0,
            2, 3, 7, 7, 6, 2,
            0, 3, 7, 7, 4, 0,
            1, 2, 6, 6, 5, 1
        };

        vao = glGenVertexArrays();
        glBindVertexArray(vao);

        vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, cubeVertices, GL_STATIC_DRAW);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);

        ebo = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, cubeIndices, GL_STATIC_DRAW);

        FloatBuffer instanceData = BufferUtils.createFloatBuffer(instancePositions.length * 3);
        for (Vector3f pos : instancePositions) {
            instanceData.put(pos.x).put(pos.y).put(pos.z);
        }
        instanceData.flip();

        instanceVBO = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, instanceVBO);
        glBufferData(GL_ARRAY_BUFFER, instanceData, GL_STATIC_DRAW);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, 3 * Float.BYTES, 0);
        glEnableVertexAttribArray(1);
        glVertexAttribDivisor(1, 1);

        FloatBuffer colorData = BufferUtils.createFloatBuffer(instanceColors.length * 3);
        for (Vector3f color : instanceColors) {
            colorData.put(color.x).put(color.y).put(color.z);
        }
        colorData.flip();

        int colorVBO = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, colorVBO);
        glBufferData(GL_ARRAY_BUFFER, colorData, GL_STATIC_DRAW);
        glVertexAttribPointer(2, 3, GL_FLOAT, false, 3 * Float.BYTES, 0);
        glEnableVertexAttribArray(2);
        glVertexAttribDivisor(2, 1); // One color per instance

        glBindVertexArray(0);
    }

    float radius = java.lang.Math.max(gridX, gridY) * 1.1f;
    float yaw = (float)java.lang.Math.toRadians(15);
    float pitch = (float)java.lang.Math.toRadians(15);
    float maxRadius = java.lang.Math.max(gridX, gridY);
    
    private double lastMouseX, lastMouseY;
    private boolean firstMouse = true;

    private Vector3f cameraTarget = new Vector3f(0, 0, 0); // the point the camera looks at

    //mouse movement since last frame
    float xoffset;
    float yoffset;

    private void loop() {
        glEnable(GL_DEPTH_TEST);

        FloatBuffer projBuffer = BufferUtils.createFloatBuffer(16);
        FloatBuffer viewBuffer = BufferUtils.createFloatBuffer(16);

        Matrix4f projection = new Matrix4f().perspective((float)java.lang.Math.toRadians(45.0f), (float)width / height, 10f, 10000.0f);

        // Scroll callback for zoom
        glfwSetScrollCallback(window, (win, xoffset, yoffset) -> {
            radius *= java.lang.Math.pow(0.9, yoffset); // exponential zoom
            radius = java.lang.Math.max(2.0f, java.lang.Math.min(maxRadius, radius)); // clamp zoom
        });

        // Mouse click callback
        glfwSetMouseButtonCallback(window, (win, button, action, mods) -> {
            if (button == GLFW_MOUSE_BUTTON_LEFT && action == GLFW_PRESS) {
                //rotateCamera = !rotateCamera; // toggle rotation
                ;
            }
        });


        // Mouse movement callback for pitch and yaw
        glfwSetCursorPosCallback(window, (win, xpos, ypos) -> {


            if (firstMouse) {
                lastMouseX = xpos;
                lastMouseY = ypos;
                firstMouse = false;
            }


            xoffset += xpos - lastMouseX;
            yoffset += lastMouseY - ypos; // reversed: y-coordinates go from bottom to top

            lastMouseX = xpos;
            lastMouseY = ypos;
        });


        while (!glfwWindowShouldClose(window)) {
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            
            // Calculate camera direction vectors
            Vector3f forward = new Vector3f(
                (float)java.lang.Math.sin(yaw),
                0,
                (float)java.lang.Math.cos(yaw)
            ).normalize();

            Vector3f up = new Vector3f(0,1,0);
            Vector3f right = new Vector3f(forward).cross(up).normalize();
            Vector3f forwardFlat = new Vector3f(up).cross(right).normalize();
            float moveSpeed = java.lang.Math.max(.5f,0.006f * radius);


            Vector3f movement = new Vector3f(0,0,0);

            if (glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS)
                movement.sub(forward);
            if (glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS)
                movement.add(forward);
            if (glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS)
                movement.add(right);
            if (glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS)
                movement.sub(right);
            if (glfwGetKey(window, GLFW_KEY_E) == GLFW_PRESS)
                movement.add(up);
            if (glfwGetKey(window, GLFW_KEY_Q) == GLFW_PRESS)
                movement.sub(up);

        

            boolean rotateCamera = (glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_LEFT) == GLFW_PRESS);
                     
            boolean moveCamera = (glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_RIGHT) == GLFW_PRESS);
                  
            if (movement.length() != 0)
                movement.normalize().mul(moveSpeed);

            if (rotateCamera) {
                float sensitivity = 0.002f; // adjust for speed
                yaw -= xoffset * sensitivity;
                pitch -= yoffset * sensitivity;

                // Clamp pitch to avoid flipping
                pitch = java.lang.Math.max(-1.5f, java.lang.Math.min(1.5f, pitch));
            } else if (moveCamera) {
                //System.out.println("MOVE CAMERA BY MOUSE:" + xoffset "," + yoffset);
                float sensitivityShift = 0.5f;
                movement.add(right.mul(xoffset * sensitivityShift));
                movement.add(forwardFlat.mul(yoffset * sensitivityShift));
            };
            
            
           


            // Calculate camera position
            float camX = (float)(radius * java.lang.Math.cos(pitch) * java.lang.Math.sin(yaw));
            float camY = (float)(radius * java.lang.Math.sin(pitch));
            float camZ = (float)(radius * java.lang.Math.cos(pitch) * java.lang.Math.cos(yaw));

            Vector3f cameraPos = new Vector3f(camX, camY, camZ);
            
          //  System.out.printf("yaw %f pitch %f \n",yaw,pitch);

            cameraTarget.add(movement);
            cameraPos.add(cameraTarget);
            


            Matrix4f view = new Matrix4f().lookAt(cameraPos, cameraTarget, new Vector3f(0, 1, 0));

            // reset mouse movement tracker
            xoffset = 0;
            yoffset = 0;

            projection.get(projBuffer);
            view.get(viewBuffer);

            glUseProgram(shaderProgram);

            int projLoc = glGetUniformLocation(shaderProgram, "projection");
            int viewLoc = glGetUniformLocation(shaderProgram, "view");

            glUniformMatrix4fv(projLoc, false, projBuffer);
            glUniformMatrix4fv(viewLoc, false, viewBuffer);

            glBindVertexArray(vao);
            glDrawElementsInstanced(GL_TRIANGLES, 36, GL_UNSIGNED_INT, 0, instancePositions.length);
            glBindVertexArray(0);

            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }





    private void cleanup() {
        glDeleteVertexArrays(vao);
        glDeleteBuffers(vbo);
        glDeleteBuffers(ebo);
        glDeleteBuffers(instanceVBO);
        glDeleteProgram(shaderProgram);

        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
        glfwTerminate();
        glfwSetErrorCallback(null).free();
    }

    private void checkCompileErrors(int shader, String type) {
        int success;
        if (type.equals("PROGRAM")) {
            success = glGetProgrami(shader, GL_LINK_STATUS);
            if (success == GL_FALSE) {
                System.err.println("ERROR::PROGRAM_LINKING_ERROR");
                System.err.println(glGetProgramInfoLog(shader));
            }
        } else {
            success = glGetShaderi(shader, GL_COMPILE_STATUS);
            if (success == GL_FALSE) {
                System.err.println("ERROR::SHADER_COMPILATION_ERROR of type: " + type);
                System.err.println(glGetShaderInfoLog(shader));
            }
        }
    }

    public static void main(String[] args) throws Exception {
        new InstancedCubes().run();
    }
}
