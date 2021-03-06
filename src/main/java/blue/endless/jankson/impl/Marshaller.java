/*
 * MIT License
 *
 * Copyright (c) 2018 Falkreon
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package blue.endless.jankson.impl;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import javax.annotation.Nullable;

import blue.endless.jankson.Comment;
import blue.endless.jankson.JsonArray;
import blue.endless.jankson.JsonElement;
import blue.endless.jankson.JsonNull;
import blue.endless.jankson.JsonObject;
import blue.endless.jankson.JsonPrimitive;

public class Marshaller {
	private static Marshaller INSTANCE = new Marshaller();
	
	public static Marshaller getFallback() { return INSTANCE; }
	
	private Map<Class<?>, Function<Object,?>> primitiveMarshallers = new HashMap<>();
	private Map<Class<?>, Function<JsonObject,?>> typeAdapters = new HashMap<>();
	
	private Map<Class<?>, Function<Object,JsonElement>> serializers = new HashMap<>();
	
	public <T> void register(Class<T> clazz, Function<Object, T> marshaller) {
		primitiveMarshallers.put(clazz, marshaller);
	}
	
	public <T> void registerTypeAdapter(Class<T> clazz, Function<JsonObject, T> adapter) {
		typeAdapters.put(clazz, adapter);
	}
	
	@SuppressWarnings("unchecked")
	public <T> void registerSerializer(Class<T> clazz, Function<T, JsonElement> serializer) {
		serializers.put(clazz, (Function<Object, JsonElement>) serializer);
	}
	
	public Marshaller() {
		register(Void.class, (it)->null);
		
		register(String.class, (it)->(it instanceof String) ? (String)it : it.toString());
		
		register(Byte.class, (it)->(it instanceof Number) ? ((Number)it).byteValue() : null);
		register(Character.class, (it)->(it instanceof Number) ? (char)((Number)it).shortValue() : it.toString().charAt(0));
		register(Short.class, (it)->(it instanceof Number) ? ((Number)it).shortValue() : null);
		register(Integer.class, (it)->(it instanceof Number) ? ((Number)it).intValue() : null);
		register(Long.class, (it)->(it instanceof Number) ? ((Number)it).longValue() : null);
		register(Float.class, (it)->(it instanceof Number) ? ((Number)it).floatValue() : null);
		register(Double.class, (it)->(it instanceof Number) ? ((Number)it).doubleValue() : null);
		register(Boolean.class, (it)->(it instanceof Boolean) ? (Boolean)it : null);
		
		register(Void.TYPE, (it)->null);
		register(Byte.TYPE, (it)->(it instanceof Number) ? ((Number)it).byteValue() : null);
		register(Character.TYPE, (it)->(it instanceof Number) ? (char)((Number)it).shortValue() : it.toString().charAt(0));
		register(Short.TYPE, (it)->(it instanceof Number) ? ((Number)it).shortValue() : null);
		register(Integer.TYPE, (it)->(it instanceof Number) ? ((Number)it).intValue() : null);
		register(Long.TYPE, (it)->(it instanceof Number) ? ((Number)it).longValue() : null);
		register(Float.TYPE, (it)->(it instanceof Number) ? ((Number)it).floatValue() : null);
		register(Double.TYPE, (it)->(it instanceof Number) ? ((Number)it).doubleValue() : null);
		register(Boolean.TYPE, (it)->(it instanceof Boolean) ? (Boolean)it : null);
		
		
		registerSerializer(Void.class, (it)->JsonNull.INSTANCE);
		registerSerializer(Character.class, (it)->new JsonPrimitive(""+it));
		registerSerializer(String.class, JsonPrimitive::new);
		registerSerializer(Byte.class, (it)->new JsonPrimitive(Long.valueOf(it)));
		registerSerializer(Short.class, (it)->new JsonPrimitive(Long.valueOf(it)));
		registerSerializer(Integer.class, (it)->new JsonPrimitive(Long.valueOf(it)));
		registerSerializer(Long.class, JsonPrimitive::new);
		registerSerializer(Float.class, (it)->new JsonPrimitive(Double.valueOf(it)));
		registerSerializer(Double.class, JsonPrimitive::new);
		registerSerializer(Boolean.class, JsonPrimitive::new);
		
		registerSerializer(Void.TYPE, (it)->JsonNull.INSTANCE);
		registerSerializer(Character.TYPE, (it)->new JsonPrimitive(""+it));
		registerSerializer(Byte.TYPE, (it)->new JsonPrimitive(Long.valueOf(it)));
		registerSerializer(Short.TYPE, (it)->new JsonPrimitive(Long.valueOf(it)));
		registerSerializer(Integer.TYPE, (it)->new JsonPrimitive(Long.valueOf(it)));
		registerSerializer(Long.TYPE, JsonPrimitive::new);
		registerSerializer(Float.TYPE, (it)->new JsonPrimitive(Double.valueOf(it)));
		registerSerializer(Double.TYPE, JsonPrimitive::new);
		registerSerializer(Boolean.TYPE, JsonPrimitive::new);
	}
	
	@SuppressWarnings("unchecked")
	@Nullable
	public <T> T marshall(Class<T> clazz, JsonElement elem) {
		if (elem==null) return null;
		if (clazz.isAssignableFrom(elem.getClass())) return (T)elem; //Already the correct type
		
		if (Enum.class.isAssignableFrom(clazz)) {
			if (!(elem instanceof JsonPrimitive)) return null;
			String name = ((JsonPrimitive)elem).getValue().toString();
			
			T[] constants = clazz.getEnumConstants();
			if (constants==null) return null;
			for(T t : constants) {
				if (((Enum<?>)t).name().equals(name)) return t;
			}
		}
		
		if (clazz.equals(String.class)) {
			//Almost everything has a String representation
			if (elem instanceof JsonObject) return (T)((JsonObject)elem).toJson(false, false);
			if (elem instanceof JsonArray) return (T)((JsonArray)elem).toJson(false, false);
			if (elem instanceof JsonPrimitive) return (T)((JsonPrimitive)elem).getValue().toString();
			if (elem instanceof JsonNull) return (T)"null";
			return null;
		}
		
		if (elem instanceof JsonPrimitive) {
			Function<Object, ?> func = primitiveMarshallers.get(clazz);
			if (func!=null) {
				return (T)func.apply(((JsonPrimitive)elem).getValue());
			} else {
				return null;
			}
		} else if (elem instanceof JsonObject) {
			if (clazz.isPrimitive()) return null;
			
			JsonObject obj = (JsonObject) elem;
			obj.setMarshaller(this);

			if (typeAdapters.containsKey(clazz)) {
				return (T) typeAdapters.get(clazz).apply((JsonObject) elem);
			}
			
		} else if (elem instanceof JsonArray) {
			if (clazz.isPrimitive()) return null;
			if (clazz.isArray()) {
				Class<?> componentType = clazz.getComponentType();
				JsonArray array = (JsonArray)elem;
				
				T result = (T) Array.newInstance(componentType, array.size());
				for(int i=0; i<array.size(); i++) {
					Array.set(result, i, marshall(componentType, array.get(i)));
				}
				return result;
			}
		}
		
		return null;
	}
	
	public JsonElement serialize(Object obj) {
		if (obj==null) return JsonNull.INSTANCE;
		
		Function<Object, JsonElement> serializer = serializers.get(obj.getClass());
		if (serializer!=null) return serializer.apply(obj);
		
		if (obj instanceof Enum) {
			return new JsonPrimitive(((Enum<?>)obj).name());
		}
		
		if (obj.getClass().isArray()) {
			
			JsonArray array = new JsonArray();
			array.setMarshaller(this);
			Class<?> component = obj.getClass().getComponentType();
			for(int i=0; i<Array.getLength(obj); i++) {
				Object elem = Array.get(obj, i);
				JsonElement parsed = serialize(elem);
				array.add(parsed);
			}
			return array;
		}
		
		if (obj instanceof Collection) {
			JsonArray array = new JsonArray();
			array.setMarshaller(this);
			for(Object elem : (Collection<?>)obj) {
				JsonElement parsed = serialize(elem);
				array.add(parsed);
			}
			return array;
		}
		
		if (obj instanceof Map) {
			JsonObject result = new JsonObject();
			for(Map.Entry<?,?> entry : ((Map<?,?>)obj).entrySet()) {
				String k = entry.getKey().toString();
				Object v = entry.getValue();
				result.put(k, serialize(v));
			}
			return result;
		}
		
		JsonObject result = new JsonObject();
		//Pull in public fields first
		for(Field f : obj.getClass().getFields()) {
			if (
					Modifier.isStatic(f.getModifiers()) || // Not part of the object
					Modifier.isTransient(f.getModifiers())) continue; //Never serialize
			f.setAccessible(true);
			
			try {
				Object child = f.get(obj);
				String name = f.getName();
				Comment comment = f.getAnnotation(Comment.class);
				if (comment==null) {
					result.put(name, serialize(child));
				} else {
					result.put(name, serialize(child), comment.value());
				}
			} catch (IllegalArgumentException | IllegalAccessException e) {}
		}
		
		/*
		 * TODO: Walk supers and add in private fields from most distant ancestor to least? Seems like a lot of
		 *       extra computation, and would gain support for very few extra cases.
		 */
		
		//Add in what private fields we can reach
		for (Field f : obj.getClass().getDeclaredFields()) {
			if (
					Modifier.isPublic(f.getModifiers()) || // Already serialized
					Modifier.isStatic(f.getModifiers()) || // Not part of the object
					Modifier.isTransient(f.getModifiers())) continue; //Never serialize
			f.setAccessible(true);
			
			try {
				Object child = f.get(obj);
				String name = f.getName();
				Comment comment = f.getAnnotation(Comment.class);
				if (comment==null) {
					result.put(name, serialize(child));
				} else {
					result.put(name, serialize(child), comment.value());
				}
			} catch (IllegalArgumentException | IllegalAccessException e) {}
		}
		
		return result;
	}
	
	/*
	private static void deserializeInto(JsonObject json, Object outer, Field field) {
		field.setAccessible(true);
		
		//Grab the object and try to initialize it if it doesn't exist
		Object obj = null;
		try {
			obj = field.get(outer);
			
			if (obj==null) {
				try {
					obj =  field.getClass().newInstance();
				} catch (InstantiationException | IllegalAccessException e) {}
			}
		} catch (IllegalArgumentException | IllegalAccessException ex) {}
		if (obj==null) return; //Also nothing we can do.
		
		
		for(String s : json.keySet()) {
			Field found = null;
			try {
				found = field.get(obj).getClass().getDeclaredField(s);
			} catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException ex) {}
			if (found==null) {
				try {
					found = obj.getClass().getField(s);
				} catch (NoSuchFieldException | SecurityException e) {}
			}
			if (found!=null) {
				found.setAccessible(true);
				JsonElement elem = json.get(s);
				if (elem instanceof JsonObject) {
					if (found.getType().isPrimitive()) break; //Can't become the object we need it to be, so bail.
					//Recurse!
					try {
						Object inner = found.get(obj);
						if (inner==null) {
							inner = found.getType().newInstance();
							found.set(obj, inner);
						}
						deserializeInto((JsonObject)elem, inner, found);
					} catch (IllegalArgumentException | IllegalAccessException | InstantiationException e) {}
				} else if (elem instanceof JsonArray) {
					if (found.getType().isPrimitive()) break; //Can't become the object we need it to be, so bail.
					try {
						Object inner = found.get(obj);
						if (inner==null) {
							inner = found.getType().newInstance();
							found.set(obj, inner);
						}
						deserializeArray((JsonArray)elem, found, obj, inner);
					
					} catch (IllegalArgumentException | IllegalAccessException | InstantiationException e) {}
				} else {
					try {
						found.set(obj, Marshaller.marshall(found.getType(), elem));
					} catch (Throwable t) {}
				}
			}
		}
	}
	
	private static <T> void deserializeArray(JsonArray json, Field field, Object outer, T obj) {
		if (obj.getClass().isArray()) {
			Class<?> elemType = obj.getClass().getComponentType();
			int len = Array.getLength(obj);
		}
	}*/
}
