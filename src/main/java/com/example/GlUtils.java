package com.example;

import org.joml.Vector3f;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL11.GL_NEAREST;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL13C.GL_TEXTURE0;
import static org.lwjgl.opengl.GL20.glGetUniformLocation;
import static org.lwjgl.opengl.GL20.glUniform1i;
import static org.lwjgl.opengl.GL30.GL_RGB32F;

public class GlUtils {
    public static int bind1DTexturePalette(Vector3f[] inputData, String textureName, int textureId, int shaderProgram) {
        // --- Color palette ---
        int colorPaletteTexID = glGenTextures();
        glBindTexture(GL_TEXTURE_1D, colorPaletteTexID);
        {
            FloatBuffer colorBuffer = BufferUtils.createFloatBuffer(inputData.length * 3);
            for (Vector3f c : inputData) colorBuffer.put(c.x).put(c.y).put(c.z);
            colorBuffer.flip();

            glTexImage1D(GL_TEXTURE_1D, 0, GL_RGB32F, inputData.length, 0, GL_RGB, GL_FLOAT, colorBuffer);
            glTexParameteri(GL_TEXTURE_1D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_1D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_1D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        }
        int colorLoc = glGetUniformLocation(shaderProgram, textureName);
        int loc = textureId - GL_TEXTURE0;
        glUniform1i(colorLoc, loc);

      //  glActiveTexture(textureId);
     //   glBindTexture(GL_TEXTURE_1D, colorPaletteTexID);

        return colorPaletteTexID;
    }
}
