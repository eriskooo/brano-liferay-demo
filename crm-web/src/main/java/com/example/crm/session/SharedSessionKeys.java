package com.example.crm.session;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

/**
 * Register zdieľaných kľúčov (kľúč → typ) a JSON prevod. Zdieľa sa len to, čo je tu – rovnaké kľúče ako
 * {@code SharedKeys} v starej appke. Neznáme polia sa ignorujú (stará appka môže pridať pole skôr).
 */
@Component
public class SharedSessionKeys {

    public static final String PREFIX = "APP_";
    public static final String MESSAGES = PREFIX + "MESSAGES";

    private final JsonMapper mapper;
    private final Map<String, JavaType> types;

    public SharedSessionKeys(JsonMapper mapper) {
        this.mapper = mapper.rebuild().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
        this.types = Map.of(
                MESSAGES, this.mapper.getTypeFactory().constructCollectionType(List.class, UiMessage.class),
                PREFIX + LastSearchCriteria.KEY, this.mapper.constructType(LastSearchCriteria.class),
                PREFIX + LastViewedRecord.KEY, this.mapper.constructType(LastViewedRecord.class));
    }

    /** Typ hodnoty zdieľaného kľúča (s prefixom), alebo prázdno, ak sa kľúč nezdieľa. */
    public Optional<JavaType> typeOf(String fullKey) {
        return Optional.ofNullable(types.get(fullKey));
    }

    public String toJson(Object value) {
        return mapper.writeValueAsString(value);
    }

    public Object fromJson(String json, JavaType type) {
        return mapper.readValue(json, type);
    }
}
