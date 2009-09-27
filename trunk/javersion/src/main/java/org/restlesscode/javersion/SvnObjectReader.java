package org.restlesscode.javersion;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.restlesscode.javersion.SerializationTable.StoreMethod;
import org.restlesscode.javersion.annotations.SvnContent;
import org.restlesscode.javersion.annotations.SvnProperty;
import org.restlesscode.javersion.annotations.SvnStorable;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;

/**
 * Reads java objects from an SVN repository.
 * Probably not thread safe yet.
 */
public class SvnObjectReader {

	private static final Log LOG = LogFactory.getLog(SvnObjectReader.class);
	
	protected SvnStore svnStore;
	
	public SvnObjectReader(SvnStore svnStore) {
		this.svnStore = svnStore;
	}

	/**
	 * Reads and returns a java object from a subversion repository.
	 * @param path Path from root of SVN repository of object
	 * @param revision Revision number to load, -1 for HEAD
	 * @param clazz Type of class to return
	 * @return Object constructed from repo
	 * @throws IOException If object isn't found or if there was an error during serialization
	 * @throws MissingObjectException 
	 */
	public <T> T read(String path, long revision, Class<T> clazz) throws IOException, MissingObjectException {
	
		SVNProperties fileProperties = new SVNProperties();
    	ByteArrayOutputStream baos = new ByteArrayOutputStream();
    	try {
	        svnStore.repository.getFile(path, revision, fileProperties, baos);
	        
	        if (! clazz.isAnnotationPresent(SvnStorable.class)) {
				throw new IllegalArgumentException("Class " + clazz + " does not conform to SvnStorable annotation.");
			}
			
	        T obj = clazz.newInstance();
	        
	        for (Method m : clazz.getMethods()) {
	        	Class<?> returnType = m.getReturnType();
				if (m.isAnnotationPresent(SvnProperty.class)) {
					
					LOG.info("Will restore " + m.getName() + " as " + returnType);
					String fieldName = Utils.checkStorability(m, clazz);
					String setterName = Utils.getSetMethod(fieldName);
					Method setMethod = clazz.getMethod(setterName, returnType);
					
					if (svnStore.serializationTable.getStorageMethod(returnType) == StoreMethod.SERIALIZE_OBJECT) {
						byte []val = fileProperties.getBinaryValue("jvn.property." + fieldName);
						setFieldFromBytes(setMethod, returnType, obj, val);
					} else {
						String val = fileProperties.getStringValue("jvn.property." + fieldName);
						setFieldFromString(setMethod, returnType, obj, val);
					}
				}
				if (m.isAnnotationPresent(SvnContent.class)) {
					LOG.info("Will restore " + m.getName() + " as " + m.getReturnType());
					String fieldName = Utils.checkStorability(m, clazz);
					String setterName = Utils.getSetMethod(fieldName);
					Method setMethod = clazz.getMethod(setterName, returnType);
					if (svnStore.serializationTable.getStorageMethod(returnType) == StoreMethod.SERIALIZE_OBJECT) {
						setFieldFromBytes(setMethod, returnType, obj, baos.toByteArray());
					} else {
						String contents = new String(baos.toByteArray());
						setFieldFromString(setMethod, returnType, obj, contents);
					}
				}
			}
	        
	        return obj;
    	} catch (SVNException e) {
    		// Could do a checkPath call to SVN before this instead of this fuzzier check for
    		// a missing object, but figured I'd minimize calls to SVN when possible
    		if (e.getMessage().contains("path not found")) {
    			throw new MissingObjectException();
    		}
    		throw new IOException(e);
    	} catch (InstantiationException e) {
    		throw new IOException(e);
		} catch (IllegalAccessException e) {
			throw new IOException(e);
		} catch (SecurityException e) {
			throw new IOException(e);
		} catch (NoSuchMethodException e) {
			throw new IOException(e);
		} catch (IllegalArgumentException e) {
			throw new IOException(e);
		} catch (InvocationTargetException e) {
			throw new IOException(e);
		} catch (ClassNotFoundException e) {
			throw new IOException(e);
		}
	}
	
	protected void setFieldFromBytes(Method setMethod, Class<?> paramType, Object obj, byte[] value) throws IllegalArgumentException, IllegalAccessException, InvocationTargetException, IOException, ClassNotFoundException {
		if (value == null) return;
		
		ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(value));
		Object storedObj = ois.readObject();
		setMethod.invoke(obj, storedObj);
	}
	
	protected void setFieldFromString(Method setMethod, Class<?> paramType, Object obj, String value) throws IllegalArgumentException, IllegalAccessException, InvocationTargetException, IOException, ClassNotFoundException, SecurityException, NoSuchMethodException, InstantiationException {
		if (value == null) return;
		
		SerializationTable.StoreMethod storeMethod = svnStore.serializationTable.getStorageMethod(paramType);
		
		switch (storeMethod) {
			case TO_STRING: 
				if (paramType == String.class) {
					setMethod.invoke(obj, value);
				} else if (paramType == int.class) {
					setMethod.invoke(obj, Integer.parseInt(value));
				} else if (paramType == byte.class) {
					setMethod.invoke(obj, Byte.parseByte(value));
				} else if (paramType == short.class) {
					setMethod.invoke(obj, Short.parseShort(value));
				} else if (paramType == long.class) {
					setMethod.invoke(obj, Long.parseLong(value));
				} else if (paramType == float.class) {
					setMethod.invoke(obj, Float.parseFloat(value));
				} else if (paramType == double.class) {
					setMethod.invoke(obj, Double.parseDouble(value));
				} else if (paramType == boolean.class) {
					setMethod.invoke(obj, Boolean.parseBoolean(value));
				} else if (paramType == char.class) {
					setMethod.invoke(obj, value.charAt(0));
				} else if (paramType == Character.class) {
					setMethod.invoke(obj, new Character(value.charAt(0)));
				}
				break;
			case TO_STRING_CONSTRUCTOR: 
				Constructor<?> c = paramType.getConstructor(String.class);
				Object newInst = c.newInstance(value);
				setMethod.invoke(obj, newInst);
				break;
			case REGISTERED:
				Object deserializedObj = svnStore.serializationTable.deserializeCustom(value, paramType);
				setMethod.invoke(obj, deserializedObj);
				break;
		}
	}
}
