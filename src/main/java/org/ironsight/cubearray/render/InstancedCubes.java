package org.ironsight.cubearray.render;

// LWJGL Instanced Cube Rendering Example
// Requires LWJGL 3 and OpenGL 3.3+

import static java.lang.Math.toDegrees;
import static java.lang.Math.toRadians;
import static org.ironsight.cubearray.render.KeyBinding.*;
import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.system.MemoryUtil.NULL;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;
import org.ironsight.cubearray.mcmodel.Face;
import org.ironsight.cubearray.platform.ResourceUtils;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

public class InstancedCubes {

  // Serializes the process-global GLFW lifecycle (glfwInit/glfwCreateWindow/glfwTerminate),
  // shared by the interactive viewer and every offscreen renderToFile call, so concurrent
  // renders on different threads cannot destroy each other's window/context.
  private static final Object GLFW_LOCK = new Object();

  int gridX = 1000;
  int gridY = 10;
  int gridZ = 1000;
  float maxRadius = java.lang.Math.max(gridX, gridY);
  // mouse movement since last frame
  float xoffset;
  float yoffset;
  private long window;
  private int width = 1920;
  private int height = 1080;
  private int vao, vbo, ebo, instanceVBO, colorIndexVBO;
  private int shaderProgram;
  private int colorPaletteTexId, sizePaletteTexId, offsetPaletteTexId,
              rotationPaletteTexId, uvPaletteTexId;
  private int blockTexId;
  private double lastMouseX, lastMouseY;
  private boolean firstMouse = true;
  private CameraState cameraState;
  private volatile CubeSetup setup;
  private volatile CubeSetup lastUploadedSetup;
  private float autoRotate = 5f;
  private CameraState orbitCamera;
  private CameraState initialPos;
  private Vector3f schematicCenter;
  private FixedYaw fixPos_0,
      fixPos_1,
      fixPos_2,
      fixPos_3,
      fixPos_4,
      fixPos_5,
      fixPos_6,
      fixPos_7,
      fixPos_8,
      fixPos_9;
  private boolean isFPV = false;
  private CameraState prevCameraState;
  private double lastChangeTime;
  private CameraTransition transition;

  public InstancedCubes(CubeSetup setup) {
    this.setup = setup;
    updateFixedPositions();
    cameraState = initialPos;
    transition =
        new CameraTransition(
            new CameraState(schematicCenter, fixPos_2.yaw(), initialPos.pitch(), 0f, initialPos.radius()),
            new CameraState(schematicCenter, fixPos_3.yaw(), initialPos.pitch(), 0f, initialPos.radius()),
            System.currentTimeMillis(), System.currentTimeMillis() + 1000);
  }

  private void updateFixedPositions() {
    var dim = new Vector3f(setup.max).sub(setup.min);
    var center = new Vector3f(setup.min).add(setup.max).mul(0.5f);
    float radius = Math.max(dim.x, Math.max(dim.y, dim.z)) * 2;
    maxRadius = Math.max(radius, Math.max(gridX, Math.max(gridY, gridZ)));

    initialPos =
        new CameraState(center, (float) toRadians(210), (float) toRadians(30), 0f, radius);
    schematicCenter = center;
    fixPos_0 = new FixedYaw((float) toRadians(210));
    fixPos_1 = new FixedYaw((float) toRadians(-45));
    fixPos_2 = new FixedYaw(0);
    fixPos_3 = new FixedYaw((float) toRadians(45));
    fixPos_4 = new FixedYaw((float) toRadians(-90));
    fixPos_5 = new FixedYaw(0);
    fixPos_6 = new FixedYaw((float) toRadians(90));
    fixPos_7 = new FixedYaw((float) toRadians(-135));
    fixPos_8 = new FixedYaw((float) toRadians(-180));
    fixPos_9 = new FixedYaw((float) toRadians(135));
  }

  /** Thread-safe: replaces the rendered data on the next frame. Camera position is preserved. */
  public void replaceData(CubeSetup newSetup) {
    if (newSetup == null) return;
    this.setup = newSetup;
  }

  public void run() throws Exception {
    // Hold the GLFW lock for the whole interactive session so background offscreen renders
    // (list icons / previews) cannot call glfwTerminate while this window is alive. Queued
    // renders run after the viewer window is closed.
    synchronized (GLFW_LOCK) {
      init();

      loop();
      cleanup();
    }
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
    setupVertexData();
    uploadInstanceData();
    uploadPaletteTextures();
    lastUploadedSetup = setup;
  }

  private void loop() {
    glClearColor(0.53f, 0.81f, 0.92f, 1f);

    FloatBuffer projBuffer = BufferUtils.createFloatBuffer(16);
    FloatBuffer viewBuffer = BufferUtils.createFloatBuffer(16);
    autoRotate = 0f;

    Matrix4f projection =
        new Matrix4f().perspective((float) toRadians(45.0f), (float) width / height, .1f, 10000.0f);
    /* TODO add orthographic perspective?
    float aspect = (float) width / height;
    float size = 20.0f; // world units visible vertically

    projection = new Matrix4f().ortho(
            -size * aspect, size * aspect,
            -size, size,
            0.1f, 10000.0f
    ); */

    GLFW.glfwSetKeyCallback(
        window,
        (windowHandle, key, scancode, action, mods) -> {
          if (action == GLFW.GLFW_PRESS) {
            String name = GLFW.glfwGetKeyName(key, scancode);
            System.out.println("############### Pressed: " + name + ", " + scancode);
          }
        });

    // Scroll callback for zoom
    glfwSetScrollCallback(
        window,
        (win, xoffset, yoffset) -> {
          cameraState = zoom(cameraState, (float) yoffset);
        });

    // Mouse click callback
    glfwSetMouseButtonCallback(
        window,
        (win, button, action, mods) -> {
          if (button == GLFW_MOUSE_BUTTON_LEFT && action == GLFW_PRESS) {
            // rotateCamera = !rotateCamera; // toggle rotation
            ;
          }
        });

    // Mouse movement callback for pitch and yaw
    glfwSetCursorPosCallback(
        window,
        (win, xpos, ypos) -> {
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
    glfwSetKeyCallback(
        window,
        (win, key, scancode, action, mods) -> {
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
                // toggle orbit and FPV camera
                if (!isFPV) {
                  /*is orbit*/
                  orbitCamera = cameraState; // save for later
                  newState =
                      new CameraState(
                          cameraState.target(),
                          cameraState.yaw(),
                          cameraState.pitch(),
                          0f,
                          0.1f // new radius
                          );
                } else {
                  newState =
                      new CameraState(
                          cameraState.target(),
                          cameraState.yaw(),
                          cameraState.pitch(),
                          0f,
                          orbitCamera.radius // new radius
                          );
                }
                isFPV = !isFPV;
                transition =
                    new CameraTransition(
                        cameraState,
                        newState,
                        System.currentTimeMillis(),
                        System.currentTimeMillis() + 500);
              } else if (key == CAM_FIX_POS_0.key) {
                CameraState target =
                    new CameraState(
                        cameraState.target(), fixPos_0.yaw(),
                        (float) toRadians(30), 0f, cameraState.radius());
                transition =
                    new CameraTransition(
                        cameraState, target, System.currentTimeMillis(),
                        System.currentTimeMillis() + 500);
              } else if (key == CAM_FIX_POS_1.key) {
                CameraState target =
                    new CameraState(
                        cameraState.target(), fixPos_1.yaw(),
                        cameraState.pitch(), 0f, cameraState.radius());
                transition =
                    new CameraTransition(
                        cameraState, target, System.currentTimeMillis(),
                        System.currentTimeMillis() + 500);
              } else if (key == CAM_FIX_POS_2.key) {
                CameraState target =
                    new CameraState(
                        cameraState.target(), fixPos_2.yaw(),
                        cameraState.pitch(), 0f, cameraState.radius());
                transition =
                    new CameraTransition(
                        cameraState, target, System.currentTimeMillis(),
                        System.currentTimeMillis() + 500);
              } else if (key == CAM_FIX_POS_3.key) {
                CameraState target =
                    new CameraState(
                        cameraState.target(), fixPos_3.yaw(),
                        cameraState.pitch(), 0f, cameraState.radius());
                transition =
                    new CameraTransition(
                        cameraState, target, System.currentTimeMillis(),
                        System.currentTimeMillis() + 500);
              } else if (key == CAM_FIX_POS_4.key) {
                CameraState target =
                    new CameraState(
                        cameraState.target(), fixPos_4.yaw(),
                        cameraState.pitch(), 0f, cameraState.radius());
                transition =
                    new CameraTransition(
                        cameraState, target, System.currentTimeMillis(),
                        System.currentTimeMillis() + 500);
              } else if (key == CAM_FIX_POS_5.key) {
                CameraState target =
                    new CameraState(
                        schematicCenter, fixPos_5.yaw(),
                        (float) toRadians(89), 0f, cameraState.radius());
                transition =
                    new CameraTransition(
                        cameraState, target, System.currentTimeMillis(),
                        System.currentTimeMillis() + 500);
              } else if (key == CAM_FIX_POS_6.key) {
                CameraState target =
                    new CameraState(
                        cameraState.target(), fixPos_6.yaw(),
                        cameraState.pitch(), 0f, cameraState.radius());
                transition =
                    new CameraTransition(
                        cameraState, target, System.currentTimeMillis(),
                        System.currentTimeMillis() + 500);
              } else if (key == CAM_FIX_POS_7.key) {
                CameraState target =
                    new CameraState(
                        cameraState.target(), fixPos_7.yaw(),
                        cameraState.pitch(), 0f, cameraState.radius());
                transition =
                    new CameraTransition(
                        cameraState, target, System.currentTimeMillis(),
                        System.currentTimeMillis() + 500);
              } else if (key == CAM_FIX_POS_8.key) {
                CameraState target =
                    new CameraState(
                        cameraState.target(), fixPos_8.yaw(),
                        cameraState.pitch(), 0f, cameraState.radius());
                transition =
                    new CameraTransition(
                        cameraState, target, System.currentTimeMillis(),
                        System.currentTimeMillis() + 500);
              } else if (key == CAM_FIX_POS_9.key) {
                CameraState target =
                    new CameraState(
                        cameraState.target(), fixPos_9.yaw(),
                        cameraState.pitch(), 0f, cameraState.radius());
                transition =
                    new CameraTransition(
                        cameraState, target, System.currentTimeMillis(),
                        System.currentTimeMillis() + 500);
              } else if (key == ZOOM_IN.key) {
                // zoom in
                cameraState = zoom(cameraState, 2);
              } else if (key == ZOOM_OUT.key) {
                // zoom out
                cameraState = zoom(cameraState, -2);
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
    prevCameraState = null;

    while (!glfwWindowShouldClose(window)) {
      double currentTime = glfwGetTime();
      float deltaTime = (float) (currentTime - lastTime);
      lastTime = currentTime;

      renderText("FPS: " + Math.round(1f / deltaTime), 10, 30);

      boolean dataChanged = setup != lastUploadedSetup;
      if (dataChanged) {
        uploadInstanceData();
        uploadPaletteTextures();
        updateFixedPositions();
        lastUploadedSetup = setup;
      }

      // Calculate camera direction vectors
      Vector3f forward =
          new Vector3f((float) Math.sin(cameraState.yaw), 0, (float) Math.cos(cameraState.yaw))
              .normalize();

      Vector3f up = new Vector3f(0, 1, 0);
      Vector3f right = new Vector3f(forward).cross(up).normalize();
      Vector3f forwardFlat = new Vector3f(up).cross(right).normalize();

      // Movement speed scaled by deltaTime
      float moveSpeedKeys = 10f * deltaTime;

      Vector3f movement = new Vector3f(0, 0, 0);

      if (glfwGetKey(window, MOVE_BACK.key) == GLFW_PRESS)
        movement.add(forward); // NOTE: forward and right movement are inverted for some reason.
      if (glfwGetKey(window, MOVE_FORWARD.key) == GLFW_PRESS) movement.sub(forward);
      if (glfwGetKey(window, MOVE_RIGHT.key) == GLFW_PRESS) movement.sub(right);
      if (glfwGetKey(window, MOVE_LEFT.key) == GLFW_PRESS) movement.add(right);
      if (glfwGetKey(window, MOVE_UP.key) == GLFW_PRESS) movement.sub(up);
      if (glfwGetKey(window, MOVE_DOWN.key) == GLFW_PRESS) movement.add(up);

      if (glfwGetKey(window, MOVE_FAST.key) == GLFW_PRESS) moveSpeedKeys *= 4;
      if (movement.length() != 0) movement.normalize().mul(moveSpeedKeys);

      boolean rotateCameraByMouse =
          (glfwGetMouseButton(window, ROTATE_CAM_MOUSE.key) == GLFW_PRESS);
      boolean moveCameraByMouse = (glfwGetMouseButton(window, MOVE_CAM_MOUSE.key) == GLFW_PRESS);

      if (rotateCameraByMouse) {
        float sensitivity = .2f * deltaTime; // scaled by delta time
        float yaw = cameraState.yaw - xoffset * sensitivity;
        float pitch = cameraState.pitch - yoffset * sensitivity;

        // Clamp pitch to avoid flipping
        pitch = Math.max(-1.5f, Math.min(1.5f, pitch));
        cameraState =
            new CameraState(
                cameraState.target(), yaw, pitch, 0f, cameraState.radius // new radius
                );

      } else if (moveCameraByMouse) {
        float sensitivityShift = 25f * deltaTime;
        movement.add(right.mul(xoffset * sensitivityShift));
        movement.add(forwardFlat.mul(yoffset * sensitivityShift));
      }

      if (autoRotate != 0) {
        cameraState =
            new CameraState(
                cameraState.target(),
                (float)
                    toRadians((toDegrees(cameraState.yaw) + autoRotate * deltaTime + 360f) % 360f),
                cameraState.pitch,
                0f,
                cameraState.radius // new radius
                );
      }

      // Calculate camera position (orbit style)
      float camX =
          (float)
              (cameraState.radius() * Math.cos(cameraState.pitch()) * Math.sin(cameraState.yaw()));
      float camY = (float) (cameraState.radius() * Math.sin(cameraState.pitch()));
      float camZ =
          (float)
              (cameraState.radius() * Math.cos(cameraState.pitch()) * Math.cos(cameraState.yaw()));

      Vector3f cameraPos = new Vector3f(camX, camY, camZ);
      // apply movement
      if (movement.length() != 0) {
        cameraState =
            new CameraState(
                new Vector3f(cameraState.target).add(movement),
                cameraState.yaw(),
                cameraState.pitch,
                0f,
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
        if (System.currentTimeMillis() > transition.timeEnd()) transition = null;
      }

      // reset mouse offsets
      xoffset = 0;
      yoffset = 0;

      if (prevCameraState == null || !cameraState.equals(prevCameraState) || dataChanged) {
        lastChangeTime = glfwGetTime();
      }

      boolean needsRedraw = (glfwGetTime() - lastChangeTime) < 0.5;

      if (needsRedraw) {
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        // CALCULATE POSITIONS - DO NOT CHANGE CAMERA STATE AFTER THIS
        cameraPos.add(cameraState.target);
        Matrix4f view =
            new Matrix4f().lookAt(cameraPos, cameraState.target(), new Vector3f(0, 1, 0));

        projection.get(projBuffer);
        view.get(viewBuffer);

        glUseProgram(shaderProgram);
        glUniformMatrix4fv(glGetUniformLocation(shaderProgram, "projection"), false, projBuffer);
        glUniformMatrix4fv(glGetUniformLocation(shaderProgram, "view"), false, viewBuffer);

        glBindVertexArray(vao);
        glDrawElementsInstanced(GL_TRIANGLES, 36, GL_UNSIGNED_INT, 0, setup.positions.length);
        glBindVertexArray(0);

        glfwSwapBuffers(window);
      } else {
        try {
          Thread.sleep(10);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }

      prevCameraState = cameraState;
      glfwPollEvents();
    }
  }

  private void saveScreenshot(Path out) throws IOException {
    // Reads from whatever framebuffer is currently bound as GL_READ_FRAMEBUFFER. The caller
    // is responsible for binding the correct source (the visible window's default framebuffer
    // for the interactive path, or the offscreen resolve FBO for renderToFile).
    ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);
    glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, buffer);

    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int i = (x + width * y) * 4;
        int r = buffer.get(i) & 0xFF;
        int g = buffer.get(i + 1) & 0xFF;
        int b = buffer.get(i + 2) & 0xFF;
        int a = buffer.get(i + 3) & 0xFF;
        image.setRGB(x, height - y - 1, (a << 24) | (r << 16) | (g << 8) | b);
      }
    }
    ImageIO.write(image, "png", out.toFile());
  }

  private void saveScreenshot() {
    try {
      Path out =
          ResourceUtils.getScreenshotPath()
              .resolve(
                  "screenshot_CubeArray_" + setup.name + "_" + System.currentTimeMillis() + ".png");
      if (out.toFile().exists()) return;
      // interactive viewer: read from the visible window's default framebuffer
      glBindFramebuffer(GL_READ_FRAMEBUFFER, 0);
      glReadBuffer(GL_BACK);
      saveScreenshot(out);
    } catch (IOException ex) {
      System.out.println(ex);
    }
  }

  private CameraState zoom(CameraState cameraState, float factor) {
    float newRadius =
        java.lang.Math.max(
            0.0f,
            java.lang.Math.min(
                maxRadius, cameraState.radius() * (float) Math.pow(0.85, factor))); // clamp zoom
    return new CameraState(
        cameraState.target(), cameraState.yaw(), cameraState.pitch(), 0f, newRadius // new radius
        );
  }

  private void cleanup() {
    glDeleteVertexArrays(vao);
    glDeleteBuffers(vbo);
    glDeleteBuffers(ebo);
    glDeleteBuffers(instanceVBO);
    glDeleteBuffers(colorIndexVBO);
    glDeleteTextures(colorPaletteTexId);
    glDeleteTextures(sizePaletteTexId);
    glDeleteTextures(offsetPaletteTexId);
    glDeleteTextures(rotationPaletteTexId);
    glDeleteTextures(uvPaletteTexId);
    glDeleteTextures(blockTexId);
    glDeleteProgram(shaderProgram);

    glfwFreeCallbacks(window);
    glfwDestroyWindow(window);
    glfwTerminate();
    glfwSetErrorCallback(null).free();
  }

  public static void renderToFile(
      CubeSetup setup, Path outputPath, int width, int height) throws Exception {

    // glfwInit/glfwTerminate are process-global, not per-thread. The app renders on
    // several background threads (list-icon renderer, preview renderer, interactive viewer),
    // so serialize the whole GLFW lifecycle to stop one render from tearing down another's
    // window/context.
    synchronized (GLFW_LOCK) {
      GLFWErrorCallback.createPrint(System.err).set();
      if (!glfwInit()) throw new IllegalStateException("Unable to initialize GLFW");

      glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
      glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
      glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
      glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);

      long window = glfwCreateWindow(width, height, "", NULL, NULL);
      if (window == NULL) throw new RuntimeException("Failed to create GLFW window");

      glfwMakeContextCurrent(window);
      GL.createCapabilities();

      InstancedCubes renderer = new InstancedCubes(setup);
      renderer.width = width;
      renderer.height = height;
      renderer.window = window;
      renderer.setupShaders();
      renderer.setupVertexData();
      renderer.uploadInstanceData();
      renderer.uploadPaletteTextures();

      renderer.cameraState = renderer.initialPos;

      // Render into an explicit offscreen framebuffer rather than the hidden window's default
      // framebuffer, whose contents are driver-dependent/undefined ("pixel ownership") and read
      // back as all-zero (blank) when driven off the main thread. A multisampled FBO preserves
      // the 8x MSAA of the on-screen path; it is then blit-resolved into a single-sample FBO
      // that glReadPixels can read.
      final int samples = 8;
      int msaaFbo = glGenFramebuffers();
      glBindFramebuffer(GL_FRAMEBUFFER, msaaFbo);
      int colorRb = glGenRenderbuffers();
      glBindRenderbuffer(GL_RENDERBUFFER, colorRb);
      glRenderbufferStorageMultisample(GL_RENDERBUFFER, samples, GL_RGBA8, width, height);
      glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, colorRb);
      int depthRb = glGenRenderbuffers();
      glBindRenderbuffer(GL_RENDERBUFFER, depthRb);
      glRenderbufferStorageMultisample(GL_RENDERBUFFER, samples, GL_DEPTH_COMPONENT24, width, height);
      glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, depthRb);
      if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
        throw new RuntimeException("Offscreen MSAA framebuffer incomplete");

      // single-sample resolve target used for read-back
      int resolveFbo = glGenFramebuffers();
      glBindFramebuffer(GL_FRAMEBUFFER, resolveFbo);
      int resolveColorRb = glGenRenderbuffers();
      glBindRenderbuffer(GL_RENDERBUFFER, resolveColorRb);
      glRenderbufferStorage(GL_RENDERBUFFER, GL_RGBA8, width, height);
      glFramebufferRenderbuffer(
          GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, resolveColorRb);
      if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
        throw new RuntimeException("Offscreen resolve framebuffer incomplete");

      glBindFramebuffer(GL_FRAMEBUFFER, msaaFbo);
      glViewport(0, 0, width, height); // FBOs do not get an automatic viewport

      glClearColor(0.53f, 0.81f, 0.92f, 1f);

      Matrix4f projection =
          new Matrix4f()
              .perspective((float) toRadians(45.0f), (float) width / height, .1f, 10000.0f);

      CameraState cam = renderer.cameraState;
      float camX = (float) (cam.radius() * Math.cos(cam.pitch()) * Math.sin(cam.yaw()));
      float camY = (float) (cam.radius() * Math.sin(cam.pitch()));
      float camZ = (float) (cam.radius() * Math.cos(cam.pitch()) * Math.cos(cam.yaw()));
      Vector3f cameraPos = new Vector3f(camX, camY, camZ).add(cam.target());
      Matrix4f view = new Matrix4f().lookAt(cameraPos, cam.target(), new Vector3f(0, 1, 0));

      FloatBuffer projBuffer = BufferUtils.createFloatBuffer(16);
      FloatBuffer viewBuffer = BufferUtils.createFloatBuffer(16);
      projection.get(projBuffer);
      view.get(viewBuffer);

      glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
      glUseProgram(renderer.shaderProgram);
      glUniformMatrix4fv(
          glGetUniformLocation(renderer.shaderProgram, "projection"), false, projBuffer);
      glUniformMatrix4fv(glGetUniformLocation(renderer.shaderProgram, "view"), false, viewBuffer);
      glBindVertexArray(renderer.vao);
      glDrawElementsInstanced(GL_TRIANGLES, 36, GL_UNSIGNED_INT, 0, setup.positions.length);
      glBindVertexArray(0);

      glFinish();

      // resolve the multisampled color buffer into the single-sample FBO, then read from it
      glBindFramebuffer(GL_READ_FRAMEBUFFER, msaaFbo);
      glBindFramebuffer(GL_DRAW_FRAMEBUFFER, resolveFbo);
      glBlitFramebuffer(
          0, 0, width, height, 0, 0, width, height, GL_COLOR_BUFFER_BIT, GL_NEAREST);

      Path parent = outputPath.getParent();
      if (parent != null) Files.createDirectories(parent);

      glBindFramebuffer(GL_READ_FRAMEBUFFER, resolveFbo);
      glReadBuffer(GL_COLOR_ATTACHMENT0);
      renderer.saveScreenshot(outputPath);

      glDeleteRenderbuffers(colorRb);
      glDeleteRenderbuffers(depthRb);
      glDeleteRenderbuffers(resolveColorRb);
      glDeleteFramebuffers(msaaFbo);
      glDeleteFramebuffers(resolveFbo);
      glDeleteVertexArrays(renderer.vao);
      glDeleteBuffers(renderer.vbo);
      glDeleteBuffers(renderer.ebo);
      glDeleteProgram(renderer.shaderProgram);
      glfwDestroyWindow(window);
      glfwTerminate();
      glfwSetErrorCallback(null).free();
    }
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

  private void setupVertexData() {
    final int UP = Face.UP.ordinal();
    final int DOWN = Face.DOWN.ordinal();
    final int NORTH = Face.NORTH.ordinal();
    final int SOUTH = Face.SOUTH.ordinal();
    final int EAST = Face.EAST.ordinal();
    final int WEST = Face.WEST.ordinal();
    float[] cubeVertices = {
      0, 1, 0, 0, 0, UP,
      1, 1, 0, 0, 1, UP,
      1, 1, 1, 1, 1, UP,
      0, 1, 1, 1, 0, UP,
      0, 0, 0, 0, 0, DOWN,
      1, 0, 0, 0, 1, DOWN,
      1, 0, 1, 1, 1, DOWN,
      0, 0, 1, 1, 0, DOWN,
      // -Z (NORTH) and +Z (SOUTH) faces
      0, 1, 0, 1, 0, NORTH,
      1, 1, 0, 0, 0, NORTH,
      1, 1, 1, 1, 0, SOUTH,
      0, 1, 1, 0, 0, SOUTH,
      0, 0, 0, 1, 1, NORTH,
      1, 0, 0, 0, 1, NORTH,
      1, 0, 1, 1, 1, SOUTH,
      0, 0, 1, 0, 1, SOUTH,
      // -X (WEST) and +X (EAST) faces
      0, 1, 0, 0, 0, WEST,
      1, 1, 0, 1, 0, EAST,
      1, 1, 1, 0, 0, EAST,
      0, 1, 1, 1, 0, WEST,
      0, 0, 0, 0, 1, WEST,
      1, 0, 0, 1, 1, EAST,
      1, 0, 1, 0, 1, EAST,
      0, 0, 1, 1, 1, WEST,
    };
    final int floatsPerVertex = 6;
    for (int i = 0; i < cubeVertices.length; i++) {
      if (i % floatsPerVertex < 3) cubeVertices[i] -= 0.5f;
    }
    int[] cubeIndices = {
      2, 1, 0,
      0, 3, 2, 5, 6, 7,
      7, 4, 5, 8 + 1, 8 + 5, 8 + 4,
      8 + 4, 8 + 0, 8 + 1, 8 + 3, 8 + 7, 8 + 6,
      8 + 6, 8 + 2, 8 + 3, 16 + 0, 16 + 4, 16 + 7,
      16 + 7, 16 + 3, 16 + 0, 16 + 2, 16 + 6, 16 + 5,
      16 + 5, 16 + 1, 16 + 2
    };

    vao = glGenVertexArrays();
    glBindVertexArray(vao);

    ebo = glGenBuffers();
    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
    glBufferData(GL_ELEMENT_ARRAY_BUFFER, cubeIndices, GL_STATIC_DRAW);

    vbo = glGenBuffers();
    glBindBuffer(GL_ARRAY_BUFFER, vbo);
    glBufferData(GL_ARRAY_BUFFER, cubeVertices, GL_STATIC_DRAW);
    int stride = floatsPerVertex * Float.BYTES;
    glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 3 * Float.BYTES);
    glEnableVertexAttribArray(1);
    glVertexAttribPointer(4, 1, GL_FLOAT, false, stride, 5 * Float.BYTES);
    glEnableVertexAttribArray(4);
  }

  private void uploadInstanceData() {
    FloatBuffer instancePositionsFlat = BufferUtils.createFloatBuffer(setup.positions.length * 3);
    for (Vector3f pos : setup.positions) instancePositionsFlat.put(pos.x).put(pos.y).put(pos.z);
    instancePositionsFlat.flip();

    if (instanceVBO == 0) instanceVBO = glGenBuffers();
    glBindBuffer(GL_ARRAY_BUFFER, instanceVBO);
    glBufferData(GL_ARRAY_BUFFER, instancePositionsFlat, GL_DYNAMIC_DRAW);
    glVertexAttribPointer(2, 3, GL_FLOAT, false, 3 * Float.BYTES, 0);
    glEnableVertexAttribArray(2);
    glVertexAttribDivisor(2, 1);

    IntBuffer colorIndexData = BufferUtils.createIntBuffer(setup.colorIndices.length);
    colorIndexData.put(setup.colorIndices).flip();

    if (colorIndexVBO == 0) colorIndexVBO = glGenBuffers();
    glBindBuffer(GL_ARRAY_BUFFER, colorIndexVBO);
    glBufferData(GL_ARRAY_BUFFER, colorIndexData, GL_DYNAMIC_DRAW);
    glVertexAttribIPointer(3, 1, GL_INT, Integer.BYTES, 0);
    glEnableVertexAttribArray(3);
    glVertexAttribDivisor(3, 1);

    glBindVertexArray(0);
  }

  private void uploadPaletteTextures() {
    glUseProgram(shaderProgram);

    int lightDirLoc = glGetUniformLocation(shaderProgram, "lightDir");
    int lightColorLoc = glGetUniformLocation(shaderProgram, "lightColor");
    Vector3f lightDir = new Vector3f(-1, 5, -3).normalize();
    glUniform3f(lightDirLoc, lightDir.x, lightDir.y, lightDir.z);
    glUniform3f(lightColorLoc, 1.0f, 1.0f, 1.0f);

    // Color palette
    if (colorPaletteTexId == 0) colorPaletteTexId = glGenTextures();
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, colorPaletteTexId);
    {
      FloatBuffer buf = BufferUtils.createFloatBuffer(setup.colorPalette.length * 3);
      for (Vector3f c : setup.colorPalette) buf.put(c.x).put(c.y).put(c.z);
      buf.flip();
      glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB32F, setup.colorPalette.length, 1, 0, GL_RGB, GL_FLOAT, buf);
      glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
      glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
      glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
      glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
      glUniform1i(glGetUniformLocation(shaderProgram, "colorPaletteTex"), 0);
    }

    // Size palette
    if (sizePaletteTexId == 0) sizePaletteTexId = glGenTextures();
    glActiveTexture(GL_TEXTURE1);
    glBindTexture(GL_TEXTURE_2D, sizePaletteTexId);
    {
      FloatBuffer buf = BufferUtils.createFloatBuffer(setup.sizePalette.length * 3);
      for (Vector3f c : setup.sizePalette) buf.put(c.x).put(c.y).put(c.z);
      buf.flip();
      glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB32F, setup.sizePalette.length, 1, 0, GL_RGB, GL_FLOAT, buf);
      glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
      glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
      glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
      glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
      glUniform1i(glGetUniformLocation(shaderProgram, "sizePaletteTex"), 1);
    }

    // Offset palette
    if (offsetPaletteTexId == 0) offsetPaletteTexId = glGenTextures();
    glActiveTexture(GL_TEXTURE2);
    glBindTexture(GL_TEXTURE_2D, offsetPaletteTexId);
    {
      FloatBuffer buf = BufferUtils.createFloatBuffer(setup.offsetPalette.length * 3);
      for (Vector3f c : setup.offsetPalette) buf.put(c.x).put(c.y).put(c.z);
      buf.flip();
      glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB32F, setup.offsetPalette.length, 1, 0, GL_RGB, GL_FLOAT, buf);
      glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
      glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
      glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
      glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
      glUniform1i(glGetUniformLocation(shaderProgram, "offsetPaletteTex"), 2);
    }

    // Rotation palette
    if (rotationPaletteTexId == 0) rotationPaletteTexId = glGenTextures();
    glActiveTexture(GL_TEXTURE3);
    glBindTexture(GL_TEXTURE_2D, rotationPaletteTexId);
    {
      FloatBuffer buf = BufferUtils.createFloatBuffer(setup.rotationPalette.length * 3);
      for (Vector3f c : setup.rotationPalette) buf.put(c.x).put(c.y).put(c.z);
      buf.flip();
      glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB32F, setup.rotationPalette.length, 1, 0, GL_RGB, GL_FLOAT, buf);
      glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
      glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
      glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
      glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
      glUniform1i(glGetUniformLocation(shaderProgram, "rotationPaletteTex"), 3);
    }

    // UV palette (2D: width = num types, height = num faces)
    if (uvPaletteTexId == 0) uvPaletteTexId = glGenTextures();
    glActiveTexture(GL_TEXTURE4);
    glBindTexture(GL_TEXTURE_2D, uvPaletteTexId);
    {
      FloatBuffer buf = BufferUtils.createFloatBuffer(setup.uvCoordsPalette.length * 4);
      for (Vector4f c : setup.uvCoordsPalette) buf.put(c.x).put(c.y).put(c.z).put(c.w);
      buf.flip();
      glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA32F, setup.offsetPalette.length, Face.values().length, 0, GL_RGBA, GL_FLOAT, buf);
      glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
      glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
      glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
      glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
      glUniform1i(glGetUniformLocation(shaderProgram, "uvPaletteTex"), 4);
    }

    // Texture atlas
    BufferedImage image = setup.textureAtlas;
    int atlasW = image.getWidth();
    int atlasH = image.getHeight();
    BufferedImage abgr = new BufferedImage(atlasW, atlasH, BufferedImage.TYPE_4BYTE_ABGR);
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
    if (blockTexId == 0) blockTexId = glGenTextures();
    glActiveTexture(GL_TEXTURE5);
    glBindTexture(GL_TEXTURE_2D, blockTexId);
    {
      ByteBuffer buf = BufferUtils.createByteBuffer(pixels.length);
      buf.put(pixels).flip();
      glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, atlasW, atlasH, 0, GL_RGBA, GL_UNSIGNED_BYTE, buf);
    }
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glUniform1i(glGetUniformLocation(shaderProgram, "blockTexture"), 5);

    glUniform1i(glGetUniformLocation(shaderProgram, "paletteSize"), setup.offsetPalette.length);
    glUniform1f(glGetUniformLocation(shaderProgram, "atlasSize"), setup.textureAtlas.getWidth());

    glEnable(GL_CULL_FACE);
    glCullFace(GL_BACK);
    glEnable(GL_DEPTH_TEST);
  }

  public void renderText(String text, float x, float y) {
    glfwSetWindowTitle(window, text);
  }

  /** Utility to compile a shader and check for errors. */
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

  public record CameraTransition(CameraState start, CameraState end, long timeStart, long timeEnd) {
    CameraState getStateAt(long time) {
      float delta = (float) (time - timeStart) / (timeEnd - timeStart);
      delta = Math.min(1, Math.max(0, delta));
      return start.multiply(1 - delta).add(end.multiply(delta));
    }
  }

  public record FixedYaw(float yaw) {}

  public record CameraState(Vector3f target, float yaw, float pitch, float roll, float radius) {
    /** Component-wise addition with another CameraState */
    public CameraState add(CameraState other) {
      return new CameraState(
          new Vector3f(this.target).add(other.target), // create new Vector3f to keep immutability
          this.yaw + other.yaw,
          this.pitch + other.pitch,
          this.roll + other.roll,
          this.radius + other.radius);
    }

    /** Scale all numeric components by a factor */
    public CameraState multiply(float factor) {
      return new CameraState(
          new Vector3f(this.target).mul(factor), // multiply vector
          this.yaw * factor,
          this.pitch * factor,
          this.roll * factor,
          this.radius * factor);
    }

    @Override
    public String toString() {
      return String.format(
          "CameraState[x=%.2f, y=%.2f, z=%.2f, yaw=%.1f°, pitch=%.1f°, roll=%.1f°, radius=%.2f]",
          target.x, target.y, target.z, toDegrees(yaw), toDegrees(pitch), toDegrees(roll), radius);
    }
  }
}
