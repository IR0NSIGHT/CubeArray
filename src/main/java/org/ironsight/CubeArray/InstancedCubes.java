package org.ironsight.CubeArray;

// LWJGL Instanced Cube Rendering Example
// Requires LWJGL 3 and OpenGL 3.3+

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.List;

import static java.lang.Math.toDegrees;
import static java.lang.Math.toRadians;
import static org.ironsight.CubeArray.GlUtils.bind1DTexturePalette;
import static org.ironsight.CubeArray.OpenGl.KeyBinding.*;
import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class InstancedCubes {

    int gridX = 1000;
    int gridY = 10;
    int gridZ = 1000;
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
    private CameraState cameraState;
    private SchemReader.CubeSetup setup;
    private float autoRotate = 5f;
    private CameraState orbitCamera;
    private CameraState initialPos;
    private CameraState fixPos_0, fixPos_1, fixPos_2, fixPos_3, fixPos_4, fixPos_5, fixPos_6, fixPos_7, fixPos_8, fixPos_9;
    private boolean isFPV = false;
    private CameraTransition transition;

    public InstancedCubes(SchemReader.CubeSetup setup) {
        this.setup = setup;

        initialPos = new CameraState(
                new Vector3f(setup.min).add(setup.max).mul(0.5f), //center
                (float) toRadians(30), //slightly from the side
                (float) toRadians(30), //slightly from above
                new Vector3f(setup.max).sub(setup.min).length() //diagonal = radius
        );

        cameraState = initialPos;
        fixPos_0 = initialPos;
        fixPos_1 = new CameraState(
                initialPos.target,
                (float) toRadians(-45),
                initialPos.yaw,
                initialPos.radius
        );
        fixPos_2 = new CameraState(
                initialPos.target,
                (float) toRadians(0),
                initialPos.yaw,
                initialPos.radius
        );
        fixPos_3 = new CameraState(
                initialPos.target,
                (float) toRadians(45),
                initialPos.yaw,
                initialPos.radius
        );
        fixPos_4 = new CameraState(
                initialPos.target,
                (float) toRadians(-90),
                initialPos.yaw,
                initialPos.radius
        );
        fixPos_5 = new CameraState(
                initialPos.target,
                (float) toRadians(0),
                (float) toRadians(89),
                initialPos.radius
        );
        fixPos_6 = new CameraState(
                initialPos.target,
                (float) toRadians(90),
                initialPos.yaw,
                initialPos.radius
        );
        fixPos_7 = new CameraState(
                initialPos.target,
                (float) toRadians(-135),
                initialPos.yaw,
                initialPos.radius
        );
        fixPos_8 = new CameraState(
                initialPos.target,
                (float) toRadians(-180),
                initialPos.yaw,
                initialPos.radius
        );
        fixPos_9 = new CameraState(
                initialPos.target,
                (float) toRadians(135),
                initialPos.yaw,
                initialPos.radius
        );

        transition = new CameraTransition(
                fixPos_2, fixPos_3, System.currentTimeMillis(), System.currentTimeMillis() + 1000
        );
    }

    // entry point to directly render a schematic
    public static void main(String[] args) throws Exception {
        File schemFile = new File("src/main/resources/schematics/ir0nsight/survivalTown.schem");
        if (!schemFile.exists()) {
            throw new RuntimeException("File not found: " + schemFile.getAbsolutePath());
        }
        System.out.println("File path: " + schemFile.getAbsolutePath());

        SchemReader.CubeSetup setup = SchemReader.prepareData(SchemReader.loadSchematics(List.of(schemFile.toPath())));
        if (setup == null) {
            throw new RuntimeException("could not load schematic into cube setup");
        } else {
            new InstancedCubes(setup).run();
        }
    }

    public void run() throws Exception {
        init();

        loop();
        cleanup();
    }

    private void init() throws Exception {
        System.out.println("generating " + setup.positions.length + " cubes");

        GLFWErrorCallback.createPrint(System.err).set();

        if (!glfwInit()) throw new IllegalStateException("Unable to initialize GLFW");

        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW.GLFW_SAMPLES, 8); // 4x MSAA

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
        glClearColor(0.53f, 0.81f, 0.92f, 1f);

        FloatBuffer projBuffer = BufferUtils.createFloatBuffer(16);
        FloatBuffer viewBuffer = BufferUtils.createFloatBuffer(16);
        autoRotate = 0f;

        Matrix4f projection = new Matrix4f().perspective((float) toRadians(45.0f),
                (float) width / height, .1f, 10000.0f);
        /* TODO add orthographic perspective?
        float aspect = (float) width / height;
        float size = 20.0f; // world units visible vertically

        projection = new Matrix4f().ortho(
                -size * aspect, size * aspect,
                -size, size,
                0.1f, 10000.0f
        ); */

        GLFW.glfwSetKeyCallback(window, (windowHandle, key, scancode, action, mods) -> {
            if (action == GLFW.GLFW_PRESS) {
                String name = GLFW.glfwGetKeyName(key, scancode);
                System.out.println("############### Pressed: " + name + ", " + scancode);
            }
        });


        // Scroll callback for zoom
        glfwSetScrollCallback(window, (win, xoffset, yoffset) -> {
            cameraState = zoom(cameraState, (float)yoffset);
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
                    if (key == TOGGLE_AUTOROTATE.key) {
                        autoRotate = autoRotate == 0 ? 5 : 0;
                    } else if (key == TOGGLE_FPV.key) {
                        CameraState newState;
                        //toggle orbit and FPV camera
                        if (!isFPV) {
                            /*is orbit*/
                            orbitCamera = cameraState; //save for later
                            newState = new CameraState(
                                    cameraState.target(),
                                    cameraState.yaw(),
                                    cameraState.pitch(),
                                    0.1f // new radius
                            );
                        } else {
                            newState = new CameraState(
                                    cameraState.target(),
                                    cameraState.yaw(),
                                    cameraState.pitch(),
                                    orbitCamera.radius // new radius
                            );
                        }
                        isFPV = !isFPV;
                        transition = new CameraTransition(
                                cameraState, newState, System.currentTimeMillis(), System.currentTimeMillis() + 500
                        );
                    } else if (key == CAM_FIX_POS_0.key) {
                        transition = new CameraTransition(
                                cameraState, fixPos_0, System.currentTimeMillis(), System.currentTimeMillis() + 500
                        );
                    } else if (key == CAM_FIX_POS_1.key) {
                        transition = new CameraTransition(
                                cameraState, fixPos_1, System.currentTimeMillis(), System.currentTimeMillis() + 500
                        );
                    } else if (key == CAM_FIX_POS_2.key) {
                        transition = new CameraTransition(
                                cameraState, fixPos_2, System.currentTimeMillis(), System.currentTimeMillis() + 500
                        );
                    } else if (key == CAM_FIX_POS_3.key) {
                        transition = new CameraTransition(
                                cameraState, fixPos_3, System.currentTimeMillis(), System.currentTimeMillis() + 500
                        );
                    } else if (key == CAM_FIX_POS_4.key) {
                        transition = new CameraTransition(
                                cameraState, fixPos_4, System.currentTimeMillis(), System.currentTimeMillis() + 500
                        );
                    } else if (key == CAM_FIX_POS_5.key) {
                        transition = new CameraTransition(
                                cameraState, fixPos_5, System.currentTimeMillis(), System.currentTimeMillis() + 500
                        );
                    } else if (key == CAM_FIX_POS_6.key) {
                        transition = new CameraTransition(
                                cameraState, fixPos_6, System.currentTimeMillis(), System.currentTimeMillis() + 500
                        );
                    } else if (key == CAM_FIX_POS_7.key) {
                        transition = new CameraTransition(
                                cameraState, fixPos_7, System.currentTimeMillis(), System.currentTimeMillis() + 500
                        );
                    } else if (key == CAM_FIX_POS_8.key) {
                        transition = new CameraTransition(
                                cameraState, fixPos_8, System.currentTimeMillis(), System.currentTimeMillis() + 500
                        );
                    } else if (key == CAM_FIX_POS_9.key) {
                        transition = new CameraTransition(
                                cameraState, fixPos_9, System.currentTimeMillis(), System.currentTimeMillis() + 500
                        );
                    } else if (key == ZOOM_IN.key) {
                        // zoom in
                        cameraState = zoom(cameraState,2);
                    } else if (key == ZOOM_OUT.key) {
                        // zoom out
                        cameraState = zoom(cameraState,-2);
                    } else if (key == SCREENSHOT.key) {
                        saveScreenshot();
                    }
                }
                keys[key] = true;
            } else if (action == GLFW_RELEASE) {
                keys[key] = false;
                System.out.println("Key released: " + key + ", scancode=" + scancode);
            }
        });

        double lastTime = glfwGetTime();

        while (!glfwWindowShouldClose(window)) {
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT); //clear screen

            double currentTime = glfwGetTime();
            float deltaTime = (float) (currentTime - lastTime);
            lastTime = currentTime;

            renderText("FPS: " + Math.round(1f / deltaTime), 10, 30);


            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            // Calculate camera direction vectors
            Vector3f forward = new Vector3f(
                    (float) Math.sin(cameraState.yaw),
                    0,
                    (float) Math.cos(cameraState.yaw)
            ).normalize();

            Vector3f up = new Vector3f(0, 1, 0);
            Vector3f right = new Vector3f(forward).cross(up).normalize();
            Vector3f forwardFlat = new Vector3f(up).cross(right).normalize();

            // Movement speed scaled by deltaTime
            float moveSpeedKeys = 10f * deltaTime;

            Vector3f movement = new Vector3f(0, 0, 0);

            if (glfwGetKey(window, MOVE_BACK.key) == GLFW_PRESS) movement.add(forward); // NOTE: forward and right movement are inverted for some reason.
            if (glfwGetKey(window, MOVE_FORWARD.key) == GLFW_PRESS) movement.sub(forward);
            if (glfwGetKey(window, MOVE_RIGHT.key) == GLFW_PRESS) movement.sub(right);
            if (glfwGetKey(window, MOVE_LEFT.key) == GLFW_PRESS) movement.add(right);
            if (glfwGetKey(window, MOVE_UP.key) == GLFW_PRESS) movement.sub(up);
            if (glfwGetKey(window, MOVE_DOWN.key) == GLFW_PRESS) movement.add(up);

            if (glfwGetKey(window, MOVE_FAST.key) == GLFW_PRESS) moveSpeedKeys *= 4;
            if (movement.length() != 0) movement.normalize().mul(moveSpeedKeys);

            boolean rotateCameraByMouse = (glfwGetMouseButton(window, ROTATE_CAM_MOUSE.key) == GLFW_PRESS);
            boolean moveCameraByMouse = (glfwGetMouseButton(window, MOVE_CAM_MOUSE.key) == GLFW_PRESS);

            if (rotateCameraByMouse) {
                float sensitivity = .2f * deltaTime; // scaled by delta time
                float yaw = cameraState.yaw - xoffset * sensitivity;
                float pitch = cameraState.pitch - yoffset * sensitivity;

                // Clamp pitch to avoid flipping
                pitch = Math.max(-1.5f, Math.min(1.5f, pitch));
                cameraState = new CameraState(
                        cameraState.target(),
                        yaw,
                        pitch,
                        cameraState.radius // new radius
                );

            } else if (moveCameraByMouse) {
                float sensitivityShift = 25f * deltaTime;
                movement.add(right.mul(xoffset * sensitivityShift));
                movement.add(forwardFlat.mul(yoffset * sensitivityShift));
            }

            if (autoRotate != 0) {
                cameraState = new CameraState(
                        cameraState.target(),
                        (float) toRadians((toDegrees(cameraState.yaw) + autoRotate * deltaTime + 360f) % 360f),
                        cameraState.pitch,
                        cameraState.radius // new radius
                );
            }

            // Calculate camera position (orbit style)
            float camX = (float) (cameraState.radius() * Math.cos(cameraState.pitch()) * Math.sin(cameraState.yaw()));
            float camY = (float) (cameraState.radius() * Math.sin(cameraState.pitch()));
            float camZ = (float) (cameraState.radius() * Math.cos(cameraState.pitch()) * Math.cos(cameraState.yaw()));

            Vector3f cameraPos = new Vector3f(camX, camY, camZ);
            // apply movement
            if (movement.length() != 0) {
                cameraState = new CameraState(
                        new Vector3f(cameraState.target).add(movement),
                        cameraState.yaw(),
                        cameraState.pitch,
                        cameraState.radius // new radius
                );
            }
            /*
            if (movement.length() != 0) {
                System.out.println("dimension=" + new Vector3f(setup.max).sub(setup.min));
                System.out.printf("camera state=%s\n", cameraState.toString());
            } */


            if (transition != null) {
                cameraState = transition.getStateAt(System.currentTimeMillis());
                if (System.currentTimeMillis() > transition.timeEnd())
                    transition = null;
            }


            // CALCULATE POSITIONS - DO NOT CHANGE CAMERA STATE AFTER THIS
            cameraPos.add(cameraState.target);
            Matrix4f view = new Matrix4f().lookAt(cameraPos, cameraState.target(), new Vector3f(0, 1, 0));

            // reset mouse offsets
            xoffset = 0;
            yoffset = 0;

            projection.get(projBuffer);
            view.get(viewBuffer);

            glUseProgram(shaderProgram);
            glUniformMatrix4fv(glGetUniformLocation(shaderProgram, "projection"), false, projBuffer);
            glUniformMatrix4fv(glGetUniformLocation(shaderProgram, "view"), false, viewBuffer);

            glBindVertexArray(vao);
            glDrawElementsInstanced(GL_TRIANGLES, 36, GL_UNSIGNED_INT, 0, setup.positions.length);
            glBindVertexArray(0);

            glfwSwapBuffers(window);
            glfwPollEvents();
        }

    }

    private void saveScreenshot() {
        try {
            File out = ResourceUtils.getScreenshotPath().resolve("screenshot_CubeArray_"+setup.name+ "_" +System.currentTimeMillis()+".png").toFile();
            if (out.exists())
                return; //pointless to screenshot the sam ething twice.

            ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, buffer);

            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int i = (x + width * y) * 4;
                    int r = buffer.get(i) & 0xFF;
                    int g = buffer.get(i + 1) & 0xFF;
                    int b = buffer.get(i + 2) & 0xFF;
                    int a = buffer.get(i + 3) & 0xFF;
                    image.setRGB(x, height - y - 1, (a<<24)|(r<<16)|(g<<8)|b);
                }
            }
            ImageIO.write(image, "png", out);
            System.out.println("screenshot saved to " + out);
        } catch (IOException ex ){
            System.out.println(ex);
        }
    }

    private CameraState zoom(CameraState cameraState, float factor) {
        float newRadius = java.lang.Math.max(0.0f, java.lang.Math.min(maxRadius, cameraState.radius() * (float) Math.pow(0.85, factor))); // clamp zoom
        return new CameraState(
                cameraState.target(),
                cameraState.yaw(),
                cameraState.pitch(),
                newRadius // new radius
        );
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

    private void setupBuffers() throws IOException {
        // --- Cube geometry ---
        final float uv_d = 0.5f;
        final float uv_u = 1.5f;
        final float uv_l = 0.5f;
        final float uv_r = 1.5f;
        float[] cubeVertices = {
                //top and bottom have uv shifted one to the right, bc they use a special texture _top
                0, 1, 0, uv_d + 1, uv_l,
                1, 1, 0, uv_d + 1, uv_r,
                1, 1, 1, uv_u + 1, uv_r,
                0, 1, 1, uv_u + 1, uv_l, // BOTTOM QUAD
                0, 0, 0, uv_d + 1, uv_l,
                1, 0, 0, uv_d + 1, uv_r,
                1, 0, 1, uv_u + 1, uv_r,
                0, 0, 1, uv_u + 1, uv_l, //TOP QUAD

                // FRONT + BACK QUAD
                0, 1, 0, uv_u, uv_l,    //  0
                1, 1, 0, uv_d, uv_l,    //  1
                1, 1, 1, uv_u, uv_l,    //  2
                0, 1, 1, uv_d, uv_l,    //  3
                0, 0, 0, uv_u, uv_r,    //  4
                1, 0, 0, uv_d, uv_r,    //  5
                1, 0, 1, uv_u, uv_r,    //  6
                0, 0, 1, uv_d, uv_r,    //  7

                // LEFT 0 4 7 3  RIGHT 2 6 5 1
                0, 1, 0, uv_d, uv_l,    //  0
                1, 1, 0, uv_u, uv_l,    //  1
                1, 1, 1, uv_d, uv_l,    //  2
                0, 1, 1, uv_u, uv_l,    //  3
                0, 0, 0, uv_d, uv_r,    //  4
                1, 0, 0, uv_u, uv_r,    //  5
                1, 0, 1, uv_d, uv_r,    //  6
                0, 0, 1, uv_u, uv_r,    //  7
        };
        for (int i = 0; i < cubeVertices.length; i++) {
            cubeVertices[i] -= 0.5f;
        }

        int[] cubeIndices = {
                2, 1, 0, //bottom
                0, 3, 2,
                5, 6, 7,//top plane 1
                7, 4, 5,

                8 + 1, 8 + 5, 8 + 4,//front (in z dir)
                8 + 4, 8 + 0, 8 + 1,
                8 + 3, 8 + 7, 8 + 6,//back (-z dir)
                8 + 6, 8 + 2, 8 + 3,

                16 + 0, 16 + 4, 16 + 7, //left
                16 + 7, 16 + 3, 16 + 0,
                16 + 2, 16 + 6, 16 + 5, //right
                16 + 5, 16 + 1, 16 + 2
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
        FloatBuffer instancePositionsFlat = BufferUtils.createFloatBuffer(setup.positions.length * 3);
        for (Vector3f pos : setup.positions) instancePositionsFlat.put(pos.x).put(pos.y).put(pos.z);
        instancePositionsFlat.flip();

        instanceVBO = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, instanceVBO);
        glBufferData(GL_ARRAY_BUFFER, instancePositionsFlat, GL_STATIC_DRAW);
        glVertexAttribPointer(attribIndexINSTANCEPOS, 3, GL_FLOAT, false, 3 * Float.BYTES, 0);
        glEnableVertexAttribArray(attribIndexINSTANCEPOS);
        glVertexAttribDivisor(attribIndexINSTANCEPOS, 1);

        // --- Instance color indices ---
        IntBuffer colorIndexData = BufferUtils.createIntBuffer(setup.colorIndices.length);
        colorIndexData.put(setup.colorIndices).flip();

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
        Vector3f lightDir = new Vector3f(-1, 5, -3).normalize();
        glUniform3f(lightDirLoc, lightDir.x, lightDir.y, lightDir.z);
        glUniform3f(lightColorLoc, 1.0f, 1.0f, 1.0f);

        int colorPaletteTexID = GlUtils.bind1DTexturePalette(setup.colorPalette, "colorPaletteTex", GL_TEXTURE0,
                shaderProgram);
        int sizePaletteTexID = GlUtils.bind1DTexturePalette(setup.sizePalette, "sizePaletteTex", GL_TEXTURE1,
                shaderProgram);
        int offsetPaletteTexID = GlUtils.bind1DTexturePalette(setup.offsetPalette, "offsetPaletteTex", GL_TEXTURE2,
                shaderProgram);
        int rotationPaletteTexID = GlUtils.bind1DTexturePalette(setup.rotationPalette, "rotationPaletteTex", GL_TEXTURE3,
                shaderProgram);
        int uvPaletteTexId = GlUtils.bind1DTexturePalette(setup.uvCoordsPalette, "uvPaletteTex", GL_TEXTURE4,
                shaderProgram);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_1D, colorPaletteTexID);

        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_1D, sizePaletteTexID);

        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_1D, offsetPaletteTexID);

        glActiveTexture(GL_TEXTURE3);
        glBindTexture(GL_TEXTURE_1D, rotationPaletteTexID);

        glActiveTexture(GL_TEXTURE4);
        glBindTexture(GL_TEXTURE_1D, uvPaletteTexId);

        BufferedImage image = setup.textureAtlas;
        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage abgr = new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR);
        var graphics = abgr.getGraphics();
        graphics.drawImage(image, 0, 0, null);
        graphics.dispose();
        byte[] pixels = ((DataBufferByte) abgr.getRaster().getDataBuffer()).getData();
        for (int i = 0; i < pixels.length; i += 4) {
            byte a = pixels[i], b = pixels[i + 1], g = pixels[i + 2], r = pixels[i + 3];
            pixels[i] = r;
            pixels[i + 1] = g;
            pixels[i + 2] = b;
            pixels[i + 3] = a;
        }
        int texId = GlUtils.createSimple2DTexture(abgr.getWidth(), abgr.getHeight(), pixels);

        int texUniform = glGetUniformLocation(shaderProgram, "blockTexture");
        glUniform1i(texUniform, 4);

        glActiveTexture(GL_TEXTURE4);
        glBindTexture(GL_TEXTURE_2D, texId);

        //-------------
        int paletteSizeLoc = glGetUniformLocation(shaderProgram, "paletteSize");
        glUniform1i(paletteSizeLoc, setup.offsetPalette.length);

        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);

        // Enable blending
        glEnable(GL_DEPTH_TEST);
        //    glEnable(GL_BLEND);
        //    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);


    }

    public void renderText(String text, float x, float y) {
        glfwSetWindowTitle(window, text);

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

    public record CameraTransition(
            CameraState start,
            CameraState end,
            long timeStart,
            long timeEnd
    ) {
        CameraState getStateAt(long time) {
            float delta = (float) (time - timeStart) / (timeEnd - timeStart);
            delta = Math.min(1,Math.max(0,delta));
            return start.multiply(1 - delta).add(end.multiply(delta));
        }
    }

    public record CameraState(
            Vector3f target,
            float yaw,
            float pitch,
            float radius
    ) {
        /**
         * Component-wise addition with another CameraState
         */
        public CameraState add(CameraState other) {
            return new CameraState(
                    new Vector3f(this.target).add(other.target), // create new Vector3f to keep immutability
                    this.yaw + other.yaw,
                    this.pitch + other.pitch,
                    this.radius + other.radius
            );
        }

        /**
         * Scale all numeric components by a factor
         */
        public CameraState multiply(float factor) {
            return new CameraState(
                    new Vector3f(this.target).mul(factor), // multiply vector
                    this.yaw * factor,
                    this.pitch * factor,
                    this.radius * factor
            );
        }

        @Override
        public String toString() {
            return String.format(
                    "CameraState[x=%.2f, y=%.2f, z=%.2f, yaw=%.1f°, pitch=%.1f°, radius=%.2f]",
                    target.x, target.y, target.z, toDegrees(yaw), toDegrees(pitch), radius
            );
        }
    }


}
