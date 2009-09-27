package org.restlesscode.javersion;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Method;

public class Utils {

	@SuppressWarnings("unchecked")
	protected static String checkStorability(Method m, Class c) throws IOException {
		if (! (m.getReturnType() instanceof Serializable)) {
			throw new IOException("Cannot store non-serializable field.");
		}
		String name = m.getName();
		if (! name.startsWith("get")) {
			throw new IOException("Cannot store non-getter/setter fields.");
		}
		String fieldName = name.substring(3);
		try {
			c.getMethod("set" + fieldName, m.getReturnType());
		} catch (SecurityException e) {
			throw new IOException("Cannot access setter for field " + fieldName);
		} catch (NoSuchMethodException e) {
			throw new IOException("No setter for storable field " + fieldName);
		}
		
		return fieldName.substring(0, 1).toLowerCase() + fieldName.substring(1);
	}
	
	protected static String getSetMethod(String fieldName) {
		return "set" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
	}
}
