package org.ironsight.CubeArray;

import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL11.GL_NEAREST;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13C.GL_TEXTURE0;
import static org.lwjgl.opengl.GL20.glGetUniformLocation;
import static org.lwjgl.opengl.GL20.glUniform1i;
import static org.lwjgl.opengl.GL30.GL_RGB32F;
import static org.lwjgl.opengl.GL30.GL_RGBA32F;

public class GlUtils {
    public static int bind1DTexturePalette(Vector4f[] inputData, String textureName, int textureId, int shaderProgram) {
        // --- Color palette ---
        int colorPaletteTexID = glGenTextures();
        glBindTexture(GL_TEXTURE_1D, colorPaletteTexID);
        {
            FloatBuffer colorBuffer = BufferUtils.createFloatBuffer(inputData.length * 4);
            for (Vector4f c : inputData) colorBuffer.put(c.x).put(c.y).put(c.z).put(c.w);
            colorBuffer.flip();

            glTexImage1D(GL_TEXTURE_1D, 0, GL_RGBA32F, inputData.length, 0, GL_RGBA, GL_FLOAT, colorBuffer);
            glTexParameteri(GL_TEXTURE_1D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_1D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_1D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        }
        int colorLoc = glGetUniformLocation(shaderProgram, textureName);
        int loc = textureId - GL_TEXTURE0;
        glUniform1i(colorLoc, loc);

        return colorPaletteTexID;
    }
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

    public static int createSimple2DTexture(int width, int height, byte[] pixelData) {
        // Generate texture ID
        int textureId = glGenTextures();

        // Bind it to GL_TEXTURE_2D target
        glBindTexture(GL_TEXTURE_2D, textureId);

        // Wrap modes (edge behavior)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        // Filtering (nearest = pixelated look)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

        // Upload pixel data
        ByteBuffer buffer = BufferUtils.createByteBuffer(pixelData.length);
        buffer.put(pixelData).flip();

        // Assume pixelData is RGBA (4 bytes per pixel)
        glTexImage2D(
                GL_TEXTURE_2D,
                0,              // mipmap level
                GL_RGBA,        // internal format (on GPU)
                width,
                height,
                0,              // border (must be 0)
                GL_RGBA,        // format of your data
                GL_UNSIGNED_BYTE,
                buffer
        );

        // Unbind (safety)
        glBindTexture(GL_TEXTURE_2D, 0);

        return textureId;
    }
}
