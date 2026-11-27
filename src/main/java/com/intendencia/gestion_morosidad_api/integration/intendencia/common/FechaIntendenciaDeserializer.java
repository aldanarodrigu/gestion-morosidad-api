package com.intendencia.gestion_morosidad_api.integration.intendencia.common;

import java.time.LocalDate;
import java.util.regex.Pattern;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ext.javatime.deser.LocalDateDeserializer;

/** Interpreta el marcador 0000-00-00 del proveedor como fecha no informada. */
public class FechaIntendenciaDeserializer extends ValueDeserializer<LocalDate> {

    @Override
    public LocalDate deserialize(JsonParser parser, DeserializationContext contexto) {
        if (parser.hasToken(JsonToken.VALUE_STRING) && noInformada(parser.getString().trim())) {
            return null;
        }
        return LocalDateDeserializer.INSTANCE.deserialize(parser, contexto);
    }

    protected boolean noInformada(String fecha) {
        return "0000-00-00".equals(fecha);
    }

    /** En pendientes, un componente en cero indica un vencimiento sin completar. */
    public static final class Vencimiento extends FechaIntendenciaDeserializer {
        private static final Pattern FORMATO = Pattern.compile("\\d{4}-(?:0\\d|1[0-2])-(?:[0-2]\\d|3[01])");

        @Override
        protected boolean noInformada(String fecha) {
            return FORMATO.matcher(fecha).matches()
                    && (fecha.startsWith("0000-") || fecha.substring(5, 7).equals("00") || fecha.endsWith("-00"));
        }
    }
}
