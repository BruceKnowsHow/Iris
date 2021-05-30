package net.coderbot.iris.pipeline.newshader;

import net.coderbot.iris.Iris;
import net.coderbot.iris.gl.shader.ShaderType;
import net.coderbot.iris.shaderpack.transform.StringTransformations;
import net.coderbot.iris.shaderpack.transform.Transformations;

public class TriforceCompositePatcher {
	public static String patch(String source, ShaderType type) {
		StringTransformations transformations = new StringTransformations(source);

		fixVersion(transformations);

		// TODO: More solid way to handle texture matrices
		// TODO: Provide these values with uniforms

		for (int i = 0; i < 8; i++) {
			transformations.replaceExact("gl_TextureMatrix[" + i + "]", "mat4(1.0)");
		}

		// TODO: Other fog things
		// TODO: fogDensity isn't actually implemented!
		// TODO: Does this exist in composite shaders???
		transformations.injectLine(Transformations.InjectionPoint.AFTER_VERSION, "uniform float fogDensity;\n" +
				"uniform float FogStart;\n" +
				"uniform float FogEnd;\n" +
				"uniform vec4 FogColor;\n" +
				"\n" +
				"struct iris_FogParameters {\n" +
				"    vec4 color;\n" +
				"    float density;\n" +
				"    float start;\n" +
				"    float end;\n" +
				"    float scale;\n" +
				"};\n" +
				"\n" +
				"iris_FogParameters iris_Fog = iris_FogParameters(FogColor, fogDensity, FogStart, FogEnd, 1.0 / (FogEnd - FogStart));\n" +
				"\n" +
				"#define gl_Fog iris_Fog");

		// TODO: What if the shader does gl_PerVertex.gl_FogFragCoord ?
		transformations.injectLine(Transformations.InjectionPoint.AFTER_VERSION, "#define gl_FogFragCoord iris_FogFragCoord");

		if (type == ShaderType.VERTEX) {
			transformations.injectLine(Transformations.InjectionPoint.AFTER_VERSION, "out float iris_FogFragCoord;");
		} else if (type == ShaderType.FRAGMENT) {
			transformations.injectLine(Transformations.InjectionPoint.AFTER_VERSION, "in float iris_FogFragCoord;");
		}

		transformations.injectLine(Transformations.InjectionPoint.AFTER_VERSION, "#define gl_ProjectionMatrix mat4(1.0)");

		if (type == ShaderType.VERTEX) {
			transformations.injectLine(Transformations.InjectionPoint.AFTER_VERSION, "#define gl_MultiTexCoord0 vec4(UV0, 0.0, 1.0)");
			transformations.injectLine(Transformations.InjectionPoint.AFTER_VERSION, "in vec2 UV0;");

			transformations.injectLine(Transformations.InjectionPoint.AFTER_VERSION, "#define gl_MultiTexCoord1 vec4(0.0, 0.0, 0.0, 1.0)");
		}

		// No color attributes, the color is always solid white.
		transformations.injectLine(Transformations.InjectionPoint.AFTER_VERSION, "#define gl_Color vec4(1.0, 1.0, 1.0, 1.0)");

		if (type == ShaderType.VERTEX) {
			// https://www.khronos.org/registry/OpenGL-Refpages/gl2.1/xhtml/glNormal.xml
			// The initial value of the current normal is the unit vector, (0, 0, 1).
			transformations.injectLine(Transformations.InjectionPoint.AFTER_VERSION, "#define gl_Normal vec3(0.0, 0.0, 1.0)");
		}

		transformations.injectLine(Transformations.InjectionPoint.AFTER_VERSION, "#define gl_NormalMatrix mat3(1.0)");
		transformations.injectLine(Transformations.InjectionPoint.AFTER_VERSION, "#define gl_ModelViewMatrix mat4(1.0)");

		// TODO: All of the transformed variants of the input matrices, preferably computed on the CPU side...
		transformations.injectLine(Transformations.InjectionPoint.AFTER_VERSION, "#define gl_ModelViewProjectionMatrix (gl_ProjectionMatrix * gl_ModelViewMatrix)");

		if (type == ShaderType.VERTEX) {
			transformations.injectLine(Transformations.InjectionPoint.AFTER_VERSION, "#define gl_Vertex vec4(Position, 1.0)");
			transformations.injectLine(Transformations.InjectionPoint.AFTER_VERSION, "in vec3 Position;");
			transformations.injectLine(Transformations.InjectionPoint.AFTER_VERSION, "vec4 ftransform() { return gl_ModelViewProjectionMatrix * gl_Vertex; }");
		}

		if (type == ShaderType.VERTEX) {
			transformations.injectLine(Transformations.InjectionPoint.AFTER_VERSION, "#define attribute in");
			transformations.injectLine(Transformations.InjectionPoint.AFTER_VERSION, "#define varying out");
		} else if (type == ShaderType.FRAGMENT) {
			transformations.injectLine(Transformations.InjectionPoint.AFTER_VERSION, "#define varying in");
		}

		if (type == ShaderType.FRAGMENT) {
			if (transformations.contains("gl_FragColor")) {
				// TODO: Find a way to properly support gl_FragColor
				Iris.logger.warn("[Triforce Patcher] gl_FragColor is not supported yet, please use gl_FragData! Assuming that the shaderpack author intended to use gl_FragData[0]...");
				transformations.injectLine(Transformations.InjectionPoint.AFTER_VERSION, "#define gl_FragColor iris_FragData[0]");
			}

			transformations.injectLine(Transformations.InjectionPoint.AFTER_VERSION, "#define gl_FragData iris_FragData");
			transformations.injectLine(Transformations.InjectionPoint.AFTER_VERSION, "out vec4 iris_FragData[8];");
		}

		// TODO: Add similar functions for all legacy texture sampling functions
		transformations.injectLine(Transformations.InjectionPoint.AFTER_VERSION, "vec4 texture2D(sampler2D sampler, vec2 coord) { return texture(sampler, coord); }");
		transformations.injectLine(Transformations.InjectionPoint.AFTER_VERSION, "vec4 texture2D(sampler2D sampler, vec2 coord, float bias) { return texture(sampler, coord, bias); }");
		transformations.injectLine(Transformations.InjectionPoint.AFTER_VERSION, "vec4 texture2DLod(sampler2D sampler, vec2 coord, float lod) { return textureLod(sampler, coord, lod); }");
		transformations.injectLine(Transformations.InjectionPoint.AFTER_VERSION, "vec4 shadow2D(sampler2DShadow sampler, vec3 coord) { return vec4(texture(sampler, coord)); }");
		transformations.injectLine(Transformations.InjectionPoint.AFTER_VERSION, "vec4 shadow2DLod(sampler2DShadow sampler, vec3 coord, float lod) { return vec4(textureLod(sampler, coord, lod)); }");

		System.out.println(transformations.toString());

		return transformations.toString();
	}

	private static void fixVersion(Transformations transformations) {
		String prefix = transformations.getPrefix();
		int split = prefix.indexOf("#version");
		String beforeVersion = prefix.substring(0, split);
		String actualVersion = prefix.substring(split + "#version".length()).trim();

		if (actualVersion.endsWith(" core")) {
			throw new IllegalStateException("Transforming a shader that is already built against the core profile???");
		}

		if (!actualVersion.startsWith("1")) {
			if (actualVersion.endsWith("compatibility")) {
				actualVersion = actualVersion.substring(0, actualVersion.length() - "compatibility".length()).trim() + " core";
			} else {
				throw new IllegalStateException("Expected \"compatibility\" after the GLSL version: #version " + actualVersion);
			}
		} else {
			actualVersion = "150 core";
		}

		beforeVersion = beforeVersion.trim();

		if (!beforeVersion.isEmpty()) {
			beforeVersion += "\n";
		}

		transformations.setPrefix(beforeVersion + "#version " + actualVersion + "\n");
	}
}