package org.restlesscode.javersion;

import java.util.List;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.restlesscode.javersion.annotations.SvnContent;
import org.restlesscode.javersion.annotations.SvnProperty;
import org.restlesscode.javersion.annotations.SvnStorable;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.diff.SVNDeltaGenerator;

/**
 * Writes objects into a subversion repository.
 */
public class SvnObjectWriter {

	private static final Log LOG = LogFactory.getLog(SvnObjectWriter.class);
	
	protected SvnStore svnStore;
	
	public SvnObjectWriter(SvnStore svnStore) {
		this.svnStore = svnStore;
	}
	
	/**
	 * Writes an object to an SVN repository at the given path. Directories will
	 * be created as necessary. Object need not be written already.
	 * @param path Path relative to SVN root to save object at
	 * @param o Object to save
	 * @throws IOException Serialization problem or problem with object
	 */
	public synchronized void write(String path, Object o) throws IOException {
		Class<?> clazz = o.getClass();
		if (! clazz.isAnnotationPresent(SvnStorable.class)) {
			throw new IllegalArgumentException("Class " + clazz + " does not conform to SvnStorable annotation.");
		}
		
		SvnStorable s = (SvnStorable) clazz.getAnnotation(SvnStorable.class);
		int classVersion = s.version();
		LOG.info("Class version = " + classVersion);
		Serializable content = null;
		String contentField = null;
		Map<String, Serializable> properties = new HashMap<String, Serializable>();
		
		for (Method m : clazz.getMethods()) {
			if (m.isAnnotationPresent(SvnProperty.class)) {
				LOG.info("Will store " + m.getName() + " as " + m.getReturnType());
				String fieldName = Utils.checkStorability(m, clazz);
				try {
					Serializable value = (Serializable) m.invoke(o);
					if (value != null) {
						properties.put(fieldName, value);
					}
				} catch (Exception e) {
					LOG.error(e);
					throw new IOException(e);
				}
			}
			if (m.isAnnotationPresent(SvnContent.class)) {
				LOG.info("Will store " + m.getName() + " as " + m.getReturnType());
				if (content != null) {
					throw new IOException("Cannot mark more than one field as SvnContent");
				}
				String fieldName = Utils.checkStorability(m, clazz);
				contentField = fieldName;
				try {
					content = (Serializable) m.invoke(o);
				} catch (Exception e) {
					LOG.error(e);
					throw new IOException(e);
				}
			}
		}
		
		try {
			svnStore.commitLock.acquire();
			writeToSvn(path, clazz.getName(), classVersion, contentField, content, properties);
		} catch (Throwable throwable) {
			throw new IOException(throwable);
		} finally {
			svnStore.commitLock.release();
		}
	}
	
	protected void writeToSvn(String path, String className, int classVersion, String contentField,
			Serializable content, Map<String, Serializable> properties) throws SVNException, IOException {
		
		boolean fileExistsInSvn = checkFileExists(path);
		
		List<String> pathsToCreate = findPathsToCreate(path);
		
		ISVNEditor editor = svnStore.repository.getCommitEditor("Saving object", null);
		editor.openRoot(-1);
		
		for (String p : pathsToCreate) {
			editor.addDir(p, null, -1);
		}
		
		if (fileExistsInSvn) {
			editor.openFile(path, -1);
		} else {
			editor.addFile(path, null, -1);
		}
		
		String checksum = null;
		
		if (content != null) {
			editor.applyTextDelta(path, null);
			SVNDeltaGenerator deltaGenerator = new SVNDeltaGenerator();
			SerializationTable.StoreMethod storeMethod = svnStore.serializationTable.getStorageMethod(content.getClass());
			
			switch (storeMethod) {
				case TO_STRING:
				case TO_STRING_CONSTRUCTOR:
					checksum = deltaGenerator.sendDelta(path, new ByteArrayInputStream(content.toString().getBytes()), editor, true);
					break;
				case REGISTERED:
					String s = svnStore.serializationTable.serializeCustom(content);
					checksum = deltaGenerator.sendDelta(path, new ByteArrayInputStream(s.getBytes()), editor, true);
					break;
				case SERIALIZE_OBJECT:
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					ObjectOutputStream oos = new ObjectOutputStream(baos);
					oos.writeObject(content);
					oos.flush();
					checksum = deltaGenerator.sendDelta(path, new ByteArrayInputStream(baos.toByteArray()), editor, true);
					break;
			}
			
		}
		editor.changeFileProperty(path, "jvn.class.version", SVNPropertyValue.create(new Integer(classVersion).toString()));
		editor.changeFileProperty(path, "jvn.class.name", SVNPropertyValue.create(className));
		
		for (String key : properties.keySet()) {
			Object objToStore = properties.get(key);
			SerializationTable.StoreMethod storeMethod = svnStore.serializationTable.getStorageMethod(objToStore.getClass());
			switch (storeMethod) {
				case TO_STRING:
				case TO_STRING_CONSTRUCTOR:
					editor.changeFileProperty(path, "jvn.property." + key, 
							SVNPropertyValue.create(objToStore.toString()));
					break;
				case REGISTERED:
					editor.changeFileProperty(path, "jvn.property." + key, 
							SVNPropertyValue.create(svnStore.serializationTable.serializeCustom(objToStore)));
					break;
				case SERIALIZE_OBJECT:
					LOG.info("Serializing " + objToStore);
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					ObjectOutputStream oos = new ObjectOutputStream(baos);
					oos.writeObject(objToStore);
					oos.flush();
					editor.changeFileProperty(path, "jvn.property." + key, 
							SVNPropertyValue.create(key, baos.toByteArray()));
					break;
			}
		}
		
		editor.closeFile(path, checksum);
		editor.closeDir();
		SVNCommitInfo commitInfo = editor.closeEdit();
		LOG.info("Commited object revision = " + commitInfo.getNewRevision());
	}

	protected List<String> findPathsToCreate(String path) throws SVNException, IOException {
		
		List<String> pathsToCreate = new ArrayList<String>();
		
		String pathElements[] = path.split("/");
		String currPath = "";
		
		for (int i = 0; i < pathElements.length - 1; i++) {
			String pathElement = pathElements[i];
			currPath += (i > 0 ? "/" : "") + pathElement;
			SVNNodeKind nodeKind = svnStore.repository.checkPath(currPath, -1);
			if (nodeKind == SVNNodeKind.NONE) {
				pathsToCreate.add(currPath);
			} else if (nodeKind == SVNNodeKind.FILE) {
				throw new IOException("Error: cannot make directory, file exists at path " + currPath);
			}
		}
		
		return pathsToCreate;
	}

	protected boolean checkFileExists(String path) throws SVNException {
		return (svnStore.repository.checkPath(path, -1) == SVNNodeKind.FILE);
	}

	
}
