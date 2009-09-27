package org.restlesscode.javersion;

public interface StringSerializer<T> {

	public String serialize(T t);
	public T deserialize(String s);
	
}
