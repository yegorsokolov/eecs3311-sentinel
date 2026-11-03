package sentinel.persistence;
import com.fasterxml.jackson.core.*;
import java.io.*;
import java.lang.reflect.*;
import java.time.Instant;
import java.util.*;
/** Bounded strict JSON for closed record contracts; no polymorphic type names or executable types. */
public final class JsonCodec {
    private static final JsonFactory JSON = JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(40).maxStringLength(1_000_000).build()).build();
    private JsonCodec() { }
    public static String encode(Object value) {
        try { var out=new StringWriter(); try(var g=JSON.createGenerator(out)){write(g,value);} return out.toString(); }
        catch(Exception ex){throw new IllegalArgumentException("Cannot encode JSON contract.");}
    }
    public static String pretty(Object value) {
        try {var out=new StringWriter();try(var g=JSON.createGenerator(out)){g.useDefaultPrettyPrinter();write(g,value);}return out.toString();}
        catch(Exception ex){throw new IllegalArgumentException("Cannot encode JSON contract.");}
    }
    private static void write(JsonGenerator g,Object v) throws Exception {
        if(v==null)g.writeNull(); else if(v instanceof String || v instanceof UUID || v instanceof Instant || v instanceof Enum<?>)g.writeString(v.toString());
        else if(v instanceof Boolean b)g.writeBoolean(b); else if(v instanceof Number n)g.writeNumber(n.toString());
        else if(v instanceof Map<?,?> m){g.writeStartObject();for(var k:m.keySet().stream().sorted(Comparator.comparing(Object::toString)).toList()){g.writeFieldName(k.toString());write(g,m.get(k));}g.writeEndObject();}
        else if(v instanceof Collection<?> c){g.writeStartArray();for(var x:c)write(g,x);g.writeEndArray();}
        else if(v.getClass().isRecord()){g.writeStartObject();for(var c:v.getClass().getRecordComponents()){g.writeFieldName(c.getName());write(g,c.getAccessor().invoke(v));}g.writeEndObject();}
        else throw new IllegalArgumentException();
    }
    public static Object tree(String text) {
        if(text==null || text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>20*1024*1024)throw new IllegalArgumentException("JSON size limit exceeded.");
        try(var p=JSON.createParser(text)){p.nextToken();Object v=read(p);if(p.nextToken()!=null)throw new IllegalArgumentException();return v;}
        catch(Exception ex){throw new IllegalArgumentException("Invalid JSON contract.");}
    }
    private static Object read(JsonParser p)throws IOException {
        if(p.currentToken()==JsonToken.START_OBJECT){var m=new LinkedHashMap<String,Object>();while(p.nextToken()!=JsonToken.END_OBJECT){if(p.currentToken()!=JsonToken.FIELD_NAME)throw new IOException();String k=p.currentName();p.nextToken();m.put(k,read(p));}return m;}
        if(p.currentToken()==JsonToken.START_ARRAY){var l=new ArrayList<>();while(p.nextToken()!=JsonToken.END_ARRAY){if(l.size()>=100_000)throw new IOException();l.add(read(p));}return l;}
        if(p.currentToken()==JsonToken.VALUE_STRING)return p.getText();
        if(p.currentToken()==JsonToken.VALUE_NUMBER_INT){if(p.getNumberType()==JsonParser.NumberType.INT)return p.getIntValue();return p.getLongValue();}
        if(p.currentToken()==JsonToken.VALUE_TRUE)return true;if(p.currentToken()==JsonToken.VALUE_FALSE)return false;
        if(p.currentToken()==JsonToken.VALUE_NULL)return null;throw new IOException();
    }
    public static <T>T decode(String text,Class<T> type){try{return type.cast(convert(tree(text),type));}catch(Exception ex){throw new IllegalArgumentException("Invalid JSON record.");}}
    @SuppressWarnings({"unchecked","rawtypes"})
    private static Object convert(Object v,Type t)throws Exception {
        if(t instanceof ParameterizedType p){
            if(p.getRawType()==List.class){var out=new ArrayList<>();for(var x:(List<?>)v)out.add(convert(x,p.getActualTypeArguments()[0]));return List.copyOf(out);}
            if(p.getRawType()==Map.class){var out=new LinkedHashMap<>();for(var e:((Map<?,?>)v).entrySet())out.put(convert(e.getKey(),p.getActualTypeArguments()[0]),convert(e.getValue(),p.getActualTypeArguments()[1]));return Map.copyOf(out);}
        }
        Class<?> c=(Class<?>)t;if(v==null){if(c.isPrimitive())throw new IllegalArgumentException();return null;}
        if(c==String.class)return (String)v;if(c==int.class || c==Integer.class)return (Integer)v;
        if(c==long.class || c==Long.class){if(v instanceof Integer n)return n.longValue();if(v instanceof Long)return v;throw new IllegalArgumentException();}
        if(c==boolean.class || c==Boolean.class)return (Boolean)v;
        if(c==UUID.class)return UUID.fromString((String)v);if(c==Instant.class)return Instant.parse((String)v);
        if(c.isEnum())return Enum.valueOf((Class)c,(String)v);
        if(c.isRecord()){
            Map<?,?> m=(Map<?,?>)v;var cs=c.getRecordComponents();if(m.size()!=cs.length)throw new IllegalArgumentException();
            var types=new Class<?>[cs.length];var args=new Object[cs.length];for(int i=0;i<cs.length;i++){if(!m.containsKey(cs[i].getName()))throw new IllegalArgumentException();types[i]=cs[i].getType();args[i]=convert(m.get(cs[i].getName()),cs[i].getGenericType());}
            return c.getDeclaredConstructor(types).newInstance(args);
        }throw new IllegalArgumentException();
    }
}
