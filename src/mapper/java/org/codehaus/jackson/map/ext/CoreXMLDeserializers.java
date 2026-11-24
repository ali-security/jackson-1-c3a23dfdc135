package org.codehaus.jackson.map.ext;

import java.io.IOException;
import java.util.*;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.Duration;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;

import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.JsonToken;
import org.codehaus.jackson.map.DeserializationContext;
import org.codehaus.jackson.map.deser.StdDeserializer;
import org.codehaus.jackson.map.deser.FromStringDeserializer;
import org.codehaus.jackson.map.deser.StdScalarDeserializer;
import org.codehaus.jackson.map.util.Provider;

/**
 * Container deserializers that handle "core" XML types: ones included in standard
 * JDK 1.5. Types are directly needed by JAXB, and are thus supported within core
 * mapper package, not in "xc" package.
 *
 * @since 1.3
 */
public class CoreXMLDeserializers
    implements Provider<StdDeserializer<?>>
{
    /**
     * Data type factories are thread-safe after instantiation (and
     * configuration, if any); and since instantion (esp. implementation
     * introspection) can be expensive we better reuse the instance.
     */
    final static DatatypeFactory _dataTypeFactory;
    static {
        try {
            _dataTypeFactory = DatatypeFactory.newInstance();
        } catch (DatatypeConfigurationException e) {
            throw new RuntimeException(e);
        }
    }
    
    /**
     * Maximum length of a textual {@link Duration} or {@link XMLGregorianCalendar}
     * value accepted for deserialization: {@link DatatypeFactory} parses numeric
     * components with the O(n^2) {@code BigInteger(String)}/{@code BigDecimal(String)}
     * constructors. Length of the whole value is checked as a conservative upper
     * bound, since it is never shorter than any single numeric component, and no
     * valid value comes anywhere near the limit.
     *<p>
     * [databind#6127]: jackson-databind bounds this with {@code StreamReadConstraints},
     * which Jackson 1.x does not have, so a fixed limit is used instead.
     */
    protected final static int MAX_VALUE_LENGTH = 2000;

    /*
    /**********************************************************
    /* Provider implementation
    /**********************************************************
     */

    /**
     * Method called by {@link org.codehaus.jackson.map.deser.BasicDeserializerFactory}
     * to register deserializers this class provides.
     */
    public Collection<StdDeserializer<?>> provide()
    {
        return Arrays.asList(new StdDeserializer<?>[] {
            new DurationDeserializer()
            ,new GregorianCalendarDeserializer()
            ,new QNameDeserializer()
        });
    }
    
    /*
    /**********************************************************
    /* Concrete deserializers
    /**********************************************************
     */

    public static class DurationDeserializer
        extends FromStringDeserializer<Duration>
    {
        public DurationDeserializer() { super(Duration.class); }
    
        @Override
        protected Duration _deserialize(String value, DeserializationContext ctxt)
            throws IllegalArgumentException
        {
            // [databind#6127] DatatypeFactory.newDuration() parses date/time components
            // into BigIntegers (and fractional seconds into BigDecimal) in O(n^2) time;
            // reject oversized values before they reach it
            if (value.length() > MAX_VALUE_LENGTH) {
                throw new IllegalArgumentException("Duration value length ("+value.length()
                        +") exceeds the maximum allowed ("+MAX_VALUE_LENGTH+")");
            }
            return _dataTypeFactory.newDuration(value);
        }
    }

    public static class GregorianCalendarDeserializer
        extends StdScalarDeserializer<XMLGregorianCalendar>
    {
        public GregorianCalendarDeserializer() { super(XMLGregorianCalendar.class); }
        
        @Override
        public XMLGregorianCalendar deserialize(JsonParser jp, DeserializationContext ctxt)
            throws IOException, JsonProcessingException
        {
            // [databind#6127] Similar to Duration: length of the whole textual value
            // is checked as a conservative upper bound for numeric components
            if (jp.getCurrentToken() == JsonToken.VALUE_STRING) {
                int len = jp.getText().trim().length();
                if (len > MAX_VALUE_LENGTH) {
                    throw ctxt.weirdStringException(_valueClass, "value length ("+len
                            +") exceeds the maximum allowed ("+MAX_VALUE_LENGTH+")");
                }
            }
            Date d = _parseDate(jp, ctxt);
            if (d == null) {
                return null;
            }
            GregorianCalendar calendar = new GregorianCalendar();
            calendar.setTime(d);
            return _dataTypeFactory.newXMLGregorianCalendar(calendar);
        }
    }

    public static class QNameDeserializer
        extends FromStringDeserializer<QName>
    {
        public QNameDeserializer() { super(QName.class); }
        
        @Override
        protected QName _deserialize(String value, DeserializationContext ctxt)
            throws IllegalArgumentException
        {
            return QName.valueOf(value);
        }
    }
}
