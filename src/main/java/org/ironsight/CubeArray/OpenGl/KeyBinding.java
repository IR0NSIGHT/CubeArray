package org.ironsight.CubeArray.OpenGl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.lwjgl.glfw.GLFW.*;
public enum KeyBinding {

    // Movement
    MOVE_UP(GLFW_KEY_Q, "Q"),
    MOVE_DOWN(GLFW_KEY_E, "E"),
    MOVE_FORWARD(GLFW_KEY_W, "W"),
    MOVE_BACK(GLFW_KEY_S, "S"),
    MOVE_RIGHT(GLFW_KEY_D, "D"),
    MOVE_LEFT(GLFW_KEY_A, "A"),
    ROTATE_LEFT(0, "Unknown"),
    ROTATE_RIGHT(0, "Unknown"),
    MOVE_FAST(GLFW_KEY_LEFT_CONTROL, "Left Ctrl"),
    TOGGLE_AUTOROTATE(GLFW_KEY_SPACE, "Space"),
    TOGGLE_FPV(GLFW_KEY_V, "V"),
    SCREENSHOT(GLFW_KEY_P, "P"),

    // Mouse
    MOVE_CAM_MOUSE(GLFW_MOUSE_BUTTON_RIGHT, "Mouse Right"),
    ROTATE_CAM_MOUSE(GLFW_MOUSE_BUTTON_LEFT, "Mouse Left"),

    // Keypad
    ZOOM_IN(GLFW_KEY_KP_ADD, "Numpad +"),
    ZOOM_OUT(GLFW_KEY_KP_SUBTRACT, "Numpad -"),
    CAM_FIX_POS_0(GLFW_KEY_KP_0, "Numpad 0"),
    CAM_FIX_POS_1(GLFW_KEY_KP_1, "Numpad 1"),
    CAM_FIX_POS_2(GLFW_KEY_KP_2, "Numpad 2"),
    CAM_FIX_POS_3(GLFW_KEY_KP_3, "Numpad 3"),
    CAM_FIX_POS_4(GLFW_KEY_KP_4, "Numpad 4"),
    CAM_FIX_POS_5(GLFW_KEY_KP_5, "Numpad 5"),
    CAM_FIX_POS_6(GLFW_KEY_KP_6, "Numpad 6"),
    CAM_FIX_POS_7(GLFW_KEY_KP_7, "Numpad 7"),
    CAM_FIX_POS_8(GLFW_KEY_KP_8, "Numpad 8"),
    CAM_FIX_POS_9(GLFW_KEY_KP_9, "Numpad 9");

    // numeric glfw key code
    public final int key;
    public final String keyName;

    private KeyBinding(int glfw_key, String keyName) {
        this.key = glfw_key;
        this.keyName = keyName;
    }

    public static void main(String[] args) {
        // Prepare the Markdown content
        StringBuilder sb = new StringBuilder();
        sb.append("# Key Bindings\n\n");
        sb.append("| Action | Key |\n");
        sb.append("|--------|-----|\n");

        Arrays.stream(KeyBinding.values())
                .sorted((a, b) -> a.name().toLowerCase().compareTo(b.name().toLowerCase()))
                .forEach(k -> sb.append("| ")
                        .append(k.toString().toLowerCase().replace("_", " "))
                        .append(" | ")
                        .append(k.keyName)
                        .append(" |\n"));

        // Write to file
        Path file = Path.of("KeyBindings.md");
        try {
            Files.writeString(file, sb.toString());
            System.out.println("KeyBindings.md generated successfully!");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
