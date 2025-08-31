package com.example;

// LWJGL Instanced Cube Rendering Example
// Requires LWJGL 3 and OpenGL 3.3+

import org.lwjgl.*;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;

import java.lang.Math;
import java.nio.*;
import java.util.Arrays;

import org.joml.*;

import static com.example.GlUtils.bind1DTexturePalette;
import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.system.MemoryUtil.*;


public class InstancedCubes {

    int gridX = 1000;
    int gridY = 10;
    int gridZ = 1000;
    float radius = java.lang.Math.max(gridX, gridY) * 1.1f;
    float yaw = (float) java.lang.Math.toRadians(15);
    float pitch = (float) java.lang.Math.toRadians(15);
    float maxRadius = java.lang.Math.max(gridX, gridY);
    //mouse movement since last frame
    float xoffset;
    float yoffset;
    private long window;
    private int width = 1920;
    private int height = 1080;
    private int vao, vbo, ebo, instanceVBO;
    private int shaderProgram;
    private double lastMouseX, lastMouseY;
    private boolean firstMouse = true;
    private SchemReader.CubeSetup inputData;
    private Vector3f cameraTarget = new Vector3f(0, 0, 0); // the point the camera looks at
    private SchemReader.CubeSetup setup;
    private float autoRotate = 5f;

    public InstancedCubes(SchemReader.CubeSetup setup) {
        this.setup = setup;
    }

    public static void main(String[] args) throws Exception {

        var setup = SchemReader.prepareData(SchemReader.loadDefaultObjects());

        new InstancedCubes(setup).run();
    }

    public void run() throws Exception {
        init(setup);

        loop();
        cleanup();
    }

    private void init(SchemReader.CubeSetup res) throws Exception {
        inputData = res;

        System.out.println("generating " + inputData.positions.length + " cubes");

        GLFWErrorCallback.createPrint(System.err).set();

        if (!glfwInit()) throw new IllegalStateException("Unable to initialize GLFW");

        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW.GLFW_SAMPLES, 4); // 4x MSAA

        window = glfwCreateWindow(width, height, "Instanced Cubes", NULL, NULL);
        if (window == NULL) throw new RuntimeException("Failed to create GLFW window");

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        glfwShowWindow(window);

        GL.createCapabilities();

        setupShaders();
        setupBuffers();
    }

    private void loop() {
        glEnable(GL_DEPTH_TEST);
        glClearColor(0.53f, 0.81f, 0.92f, 1f);

        FloatBuffer projBuffer = BufferUtils.createFloatBuffer(16);
        FloatBuffer viewBuffer = BufferUtils.createFloatBuffer(16);

        Matrix4f projection = new Matrix4f().perspective((float) java.lang.Math.toRadians(45.0f),
                (float) width / height, .1f, 10000.0f);

        // Scroll callback for zoom
        glfwSetScrollCallback(window, (win, xoffset, yoffset) -> {
            radius *= (float) Math.pow(0.85, yoffset); // exponential zoom
            radius = java.lang.Math.max(0.0f, java.lang.Math.min(maxRadius, radius)); // clamp zoom
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

            xoffset += (float) (xpos - lastMouseX);
            yoffset += (float) (lastMouseY - ypos); // reversed: y-coordinates go from bottom to top

            lastMouseX = xpos;
            lastMouseY = ypos;
        });
        final boolean[] keys = new boolean[GLFW_KEY_LAST];
        glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_UNKNOWN) return;

            if (action == GLFW_PRESS) {
                if (!keys[key]) {
                    // 🔹 Run once when key is pressed
                    System.out.println("Key pressed once: " + key);

                    // put your action here
                    if (key == GLFW_KEY_SPACE) {
                        autoRotate = autoRotate == 0 ? 5 : 0;
                    }
                }
                keys[key] = true;
            } else if (action == GLFW_RELEASE) {
                keys[key] = false;
                System.out.println("Key released: " + key);
            }
        });

        double lastTime = glfwGetTime();

        while (!glfwWindowShouldClose(window)) {
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT); //clear screen

            double currentTime = glfwGetTime();
            float deltaTime = (float) (currentTime - lastTime);
            lastTime = currentTime;

            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            // Calculate camera direction vectors
            Vector3f forward = new Vector3f(
                    (float) Math.sin(yaw),
                    0,
                    (float) Math.cos(yaw)
            ).normalize();

            Vector3f up = new Vector3f(0, 1, 0);
            Vector3f right = new Vector3f(forward).cross(up).normalize();
            Vector3f forwardFlat = new Vector3f(up).cross(right).normalize();

            // Movement speed scaled by deltaTime
            float moveSpeedKeys = 10f * deltaTime;

            Vector3f movement = new Vector3f(0, 0, 0);

            if (glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS) movement.sub(forward);
            if (glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS) movement.add(forward);
            if (glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS) movement.add(right);
            if (glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS) movement.sub(right);
            if (glfwGetKey(window, GLFW_KEY_E) == GLFW_PRESS) movement.add(up);
            if (glfwGetKey(window, GLFW_KEY_Q) == GLFW_PRESS) movement.sub(up);
            if (glfwGetKey(window, GLFW_KEY_LEFT_CONTROL) == GLFW_PRESS) moveSpeedKeys *= 4;
            if (movement.length() != 0) movement.normalize().mul(moveSpeedKeys);

            boolean rotateCameraByMouse = (glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_LEFT) == GLFW_PRESS);
            boolean moveCameraByMouse = (glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_RIGHT) == GLFW_PRESS);

            if (rotateCameraByMouse) {
                float sensitivity = .2f * deltaTime; // scaled by delta time
                yaw -= xoffset * sensitivity;
                pitch -= yoffset * sensitivity;

                // Clamp pitch to avoid flipping
                pitch = Math.max(-1.5f, Math.min(1.5f, pitch));
            } else if (moveCameraByMouse) {
                float sensitivityShift = 25f * deltaTime;
                movement.add(right.mul(xoffset * sensitivityShift));
                movement.add(forwardFlat.mul(yoffset * sensitivityShift));
            }

            if (autoRotate != 0) {
                yaw = (float) Math.toRadians((Math.toDegrees(yaw) + autoRotate * deltaTime + 360f) % 360f);
                radius += 4 * deltaTime;
            }

            // Calculate camera position (orbit style)
            float camX = (float) (radius * Math.cos(pitch) * Math.sin(yaw));
            float camY = (float) (radius * Math.sin(pitch));
            float camZ = (float) (radius * Math.cos(pitch) * Math.cos(yaw));

            Vector3f cameraPos = new Vector3f(camX, camY, camZ);

            cameraTarget.add(movement);
            cameraPos.add(cameraTarget);

            Matrix4f view = new Matrix4f().lookAt(cameraPos, cameraTarget, new Vector3f(0, 1, 0));

            // reset mouse offsets
            xoffset = 0;
            yoffset = 0;

            projection.get(projBuffer);
            view.get(viewBuffer);

            glUseProgram(shaderProgram);
            glUniformMatrix4fv(glGetUniformLocation(shaderProgram, "projection"), false, projBuffer);
            glUniformMatrix4fv(glGetUniformLocation(shaderProgram, "view"), false, viewBuffer);

            glBindVertexArray(vao);
            glDrawElementsInstanced(GL_TRIANGLES, 36, GL_UNSIGNED_INT, 0, inputData.positions.length);
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

    private void setupShaders() {
        // --- Compile Shaders ---
        int vertexShader = compileShader(GL_VERTEX_SHADER, VertexShaderSource.source, "VERTEX");
        int geometryShader = compileShader(GL_GEOMETRY_SHADER, GeometryShaderSource.source, "GEOMETRY");
        int fragmentShader = compileShader(GL_FRAGMENT_SHADER, FragmentShaderSource.source, "FRAGMENT");

        // --- Link Program ---
        shaderProgram = glCreateProgram();
        glAttachShader(shaderProgram, vertexShader);
        glAttachShader(shaderProgram, geometryShader);
        glAttachShader(shaderProgram, fragmentShader);
        glLinkProgram(shaderProgram);
        checkCompileErrors(shaderProgram, "PROGRAM");

        // --- Delete shaders after linking ---
        glDeleteShader(vertexShader);
        glDeleteShader(geometryShader);
        glDeleteShader(fragmentShader);
    }

    private void setupBuffers() {
        // --- Cube geometry ---
        float[] cubeVertices = {
                0, 0, 0,    0.5f,0.5f,
                1, 0, 0,    1.5f,0.5f,
                1, 0, 1,    1.5f,1.5f,
                0, 0, 1,    0.5f,1.5f,

                0, 1, 0,    0.5f,0.5f,
                1, 1, 0,    1.5f,0.5f,
                1, 1, 1,    1.5f,1.5f,
                0, 1, 1,    0.5f,1.5f,
        };
        for (int i = 0; i < cubeVertices.length; i++) {
            cubeVertices[i] -= 0.5f;
        }
        for (float f: cubeVertices){
            assert f >= -1 && f <= 1 : "out of range " + f;
        }

        int[] cubeIndices = {
                //bottom
                2, 1, 0,
                0, 3, 2,
                //top plane 1
                5, 6, 7,
                7, 4, 5,
                //front (in z dir)
                1, 5, 4,
                4, 0, 1,
                //back (-z dir)
                3, 7, 6,
                6, 2, 3,
                //left
                0, 4, 7,
                7, 3, 0,
                //right
                2, 6, 5,
                5, 1, 2
        };

        vao = glGenVertexArrays();
        glBindVertexArray(vao);

        // --- Element buffer ---
        ebo = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, cubeIndices, GL_STATIC_DRAW);

        // --- Vertex buffer ---
        vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, cubeVertices, GL_STATIC_DRAW);
        final int attribIndexVERTEXPOS = 0;
        final int attribIndexUVPOS = 1;
        final int attribIndexINSTANCEPOS = 2;
        final int attribIndexCOLORINDEX = 3;
        glVertexAttribPointer(attribIndexVERTEXPOS, 3, GL_FLOAT, false, 5 * Float.BYTES, 0);
        glEnableVertexAttribArray(attribIndexVERTEXPOS);

        glVertexAttribPointer(attribIndexUVPOS, 2, GL_FLOAT, false, 5 * Float.BYTES, 3 * Float.BYTES);
        glEnableVertexAttribArray(attribIndexUVPOS);



        // --- Instance positions ---
        FloatBuffer instancePositionsFlat = BufferUtils.createFloatBuffer(inputData.positions.length * 3);
        for (Vector3f pos : inputData.positions) instancePositionsFlat.put(pos.x).put(pos.y).put(pos.z);
        instancePositionsFlat.flip();

        instanceVBO = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, instanceVBO);
        glBufferData(GL_ARRAY_BUFFER, instancePositionsFlat, GL_STATIC_DRAW);
        glVertexAttribPointer(attribIndexINSTANCEPOS, 3, GL_FLOAT, false, 3 * Float.BYTES, 0);
        glEnableVertexAttribArray(attribIndexINSTANCEPOS);
        glVertexAttribDivisor(attribIndexINSTANCEPOS, 1);

        // --- Instance color indices ---
        IntBuffer colorIndexData = BufferUtils.createIntBuffer(inputData.colorIndices.length);
        colorIndexData.put(inputData.colorIndices).flip();

        int colorIndexVBO = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, colorIndexVBO);
        glBufferData(GL_ARRAY_BUFFER, colorIndexData, GL_STATIC_DRAW);
        glVertexAttribIPointer(attribIndexCOLORINDEX, 1, GL_INT, Integer.BYTES, 0);
        glEnableVertexAttribArray(attribIndexCOLORINDEX);
        glVertexAttribDivisor(attribIndexCOLORINDEX, 1);

        glBindVertexArray(0);

        glUseProgram(shaderProgram);

        // --- Lights ---
        int lightDirLoc = glGetUniformLocation(shaderProgram, "lightDir");
        int lightColorLoc = glGetUniformLocation(shaderProgram, "lightColor");
        Vector3f lightDir = new Vector3f(-1, -2, -3).normalize();
        glUniform3f(lightDirLoc, lightDir.x, lightDir.y, lightDir.z);
        glUniform3f(lightColorLoc, 1.0f, 1.0f, 1.0f);

        // --- Color palette ---
        int colorPaletteTexID = bind1DTexturePalette(inputData.colorPalette, "colorPaletteTex", GL_TEXTURE0,
                shaderProgram);
        int sizePaletteTexID = bind1DTexturePalette(inputData.sizePalette, "sizePaletteTex", GL_TEXTURE1,
                shaderProgram);

        int offsetPaletteTexID = bind1DTexturePalette(inputData.offsetPalette, "offsetPaletteTex", GL_TEXTURE2,
                shaderProgram);

        // --- Offset palette ---
        int rotationPaletteTexID = bind1DTexturePalette(inputData.rotationPalette, "rotationPaletteTex", GL_TEXTURE3,
                shaderProgram);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_1D, colorPaletteTexID);

        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_1D, sizePaletteTexID);

        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_1D, offsetPaletteTexID);

        glActiveTexture(GL_TEXTURE3);
        glBindTexture(GL_TEXTURE_1D, rotationPaletteTexID);

        // Make a simple 2x2 checkerboard (black/white, RGBA)
        byte[] pixels = {
                (byte) 255, (byte) 255, (byte) 255, (byte) 255, 0, 0, 0, (byte) 255,
                0, 0, 0, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255
        };
     //   Arrays.fill(pixels,(byte)128);

        int texId = GlUtils.createSimple2DTexture(2, 2, pixels);

        int texUniform = glGetUniformLocation(shaderProgram, "blockTexture");
        glUniform1i(texUniform, 4);

        glActiveTexture(GL_TEXTURE4);
        glBindTexture(GL_TEXTURE_2D, texId);

        //-------------
        int paletteSizeLoc = glGetUniformLocation(shaderProgram, "paletteSize");
        glUniform1i(paletteSizeLoc, inputData.offsetPalette.length);

        glDisable(GL_CULL_FACE);
    }


    /**
     * Utility to compile a shader and check for errors.
     */
    private int compileShader(int type, String source, String typeName) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        checkCompileErrors(shader, typeName);
        return shader;
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
}
