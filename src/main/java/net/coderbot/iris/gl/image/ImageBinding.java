package net.coderbot.iris.gl.image;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.EXTShaderImageLoadStore;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL42C;

import java.util.function.IntSupplier;

public class ImageBinding {
	private final int imageUnit;
	private final int internalFormat;
	private final IntSupplier textureID;
	private final boolean useExt;

	public ImageBinding(int imageUnit, int internalFormat, IntSupplier textureID) {
		this.textureID = textureID;
		this.imageUnit = imageUnit;
		this.internalFormat = internalFormat;
		this.useExt = !GL.getCapabilities().OpenGL42;
	}

	public void update() {
		if (useExt) {
			EXTShaderImageLoadStore.glBindImageTextureEXT(imageUnit, textureID.getAsInt(), 0, false, 0, GL42C.GL_READ_WRITE, internalFormat);
		} else {
			GL42C.glBindImageTexture(imageUnit, textureID.getAsInt(), 0, false, 0, GL42C.GL_READ_WRITE, internalFormat);
		}
	}
}
