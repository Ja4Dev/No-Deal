package com.juego.net;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.loader.G3dModelLoader;
import com.badlogic.gdx.graphics.g3d.model.data.ModelData;
import com.badlogic.gdx.graphics.g3d.model.data.ModelMesh;
import com.badlogic.gdx.graphics.g3d.model.data.ModelMeshPart;
import com.badlogic.gdx.graphics.g3d.model.data.ModelNode;
import com.badlogic.gdx.graphics.g3d.model.data.ModelNodePart;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.collision.btBvhTriangleMeshShape;
import com.badlogic.gdx.physics.bullet.collision.btTriangleMesh;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.ObjectMap;

/**
 * Static collision geometry of a level, built straight from the model file's
 * vertex data. Unlike {@code Bullet.obtainStaticNodeShape(model.nodes)} this
 * never creates a {@code Model}, so it needs no OpenGL context and works on
 * the headless dedicated server. Client and server use the same geometry, so
 * they agree on what blocks a bullet.
 */
public class MapCollision implements Disposable {
	private final btTriangleMesh triangles = new btTriangleMesh();
	public final btBvhTriangleMeshShape shape;
	private int triangleCount;

	public MapCollision(FileHandle modelFile) {
		ModelData data = new G3dModelLoader(new JsonReader()).loadModelData(modelFile);
		ObjectMap<String, ModelMeshPart> parts = new ObjectMap<>();
		ObjectMap<ModelMeshPart, ModelMesh> meshOf = new ObjectMap<>();
		for (ModelMesh mesh : data.meshes) {
			for (ModelMeshPart part : mesh.parts) {
				parts.put(part.id, part);
				meshOf.put(part, mesh);
			}
		}
		Matrix4 identity = new Matrix4();
		for (ModelNode node : data.nodes)
			addNode(node, identity, parts, meshOf);
		if (triangleCount == 0)
			throw new GdxRuntimeException(modelFile + " has no collision triangles");
		shape = new btBvhTriangleMeshShape(triangles, true);
	}

	private void addNode(ModelNode node, Matrix4 parent, ObjectMap<String, ModelMeshPart> parts,
			ObjectMap<ModelMeshPart, ModelMesh> meshOf) {
		Matrix4 local = new Matrix4().set(node.translation == null ? Vector3.Zero : node.translation,
				node.rotation == null ? new com.badlogic.gdx.math.Quaternion() : node.rotation,
				node.scale == null ? new Vector3(1, 1, 1) : node.scale);
		Matrix4 global = new Matrix4(parent).mul(local);
		if (node.parts != null) {
			for (ModelNodePart nodePart : node.parts) {
				ModelMeshPart part = parts.get(nodePart.meshPartId);
				if (part == null)
					throw new GdxRuntimeException("Node " + node.id + " references unknown mesh part " + nodePart.meshPartId);
				addTriangles(meshOf.get(part), part, global);
			}
		}
		if (node.children != null) {
			for (ModelNode child : node.children)
				addNode(child, global, parts, meshOf);
		}
	}

	private void addTriangles(ModelMesh mesh, ModelMeshPart part, Matrix4 transform) {
		int stride = 0, positionOffset = -1;
		for (VertexAttribute attribute : mesh.attributes) {
			if (attribute.usage == VertexAttributes.Usage.Position)
				positionOffset = stride;
			stride += attribute.numComponents;
		}
		if (positionOffset < 0)
			return;
		Vector3 a = new Vector3(), b = new Vector3(), c = new Vector3();
		short[] indices = part.indices;
		for (int i = 0; i + 2 < indices.length; i += 3) {
			vertex(mesh.vertices, indices[i], stride, positionOffset, transform, a);
			vertex(mesh.vertices, indices[i + 1], stride, positionOffset, transform, b);
			vertex(mesh.vertices, indices[i + 2], stride, positionOffset, transform, c);
			triangles.addTriangle(a, b, c);
			triangleCount++;
		}
	}

	private static void vertex(float[] vertices, short index, int stride, int offset, Matrix4 transform, Vector3 out) {
		int base = (index & 0xFFFF) * stride + offset; // indices are unsigned shorts
		out.set(vertices[base], vertices[base + 1], vertices[base + 2]).mul(transform);
	}

	public int getTriangleCount() {
		return triangleCount;
	}

	@Override
	public void dispose() {
		shape.dispose();
		triangles.dispose();
	}
}
