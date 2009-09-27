package org.restlesscode.javersion;

import java.io.IOException;
import java.lang.reflect.Method;

import org.restlesscode.javersion.annotations.SvnProperty;
import org.restlesscode.javersion.annotations.SvnStorable;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class TestSvnObjectWriter extends TestCase {

	public TestSvnObjectWriter(String testName) {
        super(testName);
    }

    public static Test suite() {
        return new TestSuite(TestSvnObjectWriter.class);
    }

    public void testCheckStorability() throws IOException {
    	StorableObject o = new StorableObject();
    	Class c = o.getClass();
    	for (Method m : c.getMethods()) {
    		if (m.isAnnotationPresent(SvnProperty.class)) {
    			String name = Utils.checkStorability(m, c);
    			assertTrue(name.equals("id"));
    		}
    	}
    }

    public void testCheckStorability2() throws IOException {
    	NotStorableObject1 o = new NotStorableObject1();
    	Class c = o.getClass();
    	for (Method m : c.getMethods()) {
    		if (m.isAnnotationPresent(SvnProperty.class)) {
    			try {
    				Utils.checkStorability(m, c);
    				assertTrue(false);
    			} catch (IOException e) { }
    		}
    	}
    }
    
    @SvnStorable(version=1)
    class StorableObject {
    	
    	@SvnProperty
    	public String getId() { return "id"; }
    	public void setId(String s) { }
    }
    
    @SvnStorable(version=1)
    class NotStorableObject1 {
    	
    	@SvnProperty
    	public String getId() { return "id"; }
    }
}
