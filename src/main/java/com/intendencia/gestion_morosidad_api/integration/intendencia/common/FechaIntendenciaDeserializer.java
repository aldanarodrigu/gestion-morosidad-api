package com.intendencia.gestion_morosidad_api.integration.intendencia.common;

import java.time.LocalDate;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ext.javatime.deser.LocalDateDeserializer;

/** El origen usa 0000-00-00 para una fecha no informada. Otros valores invalidos se rechazan. */
public class FechaIntendenciaDeserializer extends ValueDeserializer<LocalDate> {
    @Override public LocalDate deserialize(JsonParser parser, DeserializationContext contexto) {
        if (parser.hasToken(JsonToken.VALUE_STRING) && "0000-00-00".equals(parser.getString().trim()))
            return null;
        return LocalDateDeserializer.INSTANCE.deserialize(parser, contexto);
    }
}
