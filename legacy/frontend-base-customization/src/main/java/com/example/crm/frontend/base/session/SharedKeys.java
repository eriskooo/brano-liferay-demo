package com.example.crm.frontend.base.session;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Register kľúčov zdieľanej session: kľúč → typ pre JSON. Do Redisu ide len to, čo je tu – zdieľa sa vedome
 * a len jednoduché DTO, ktoré prečíta Java 8 (stará appka) aj Java 21 (nová appka).
 */
public class SharedKeys {

    /** Hlášky pre používateľa – zdieľané vždy. */
    public static final String MESSAGES = "APP_MESSAGES";

    private final TypeFactory typeFactory = TypeFactory.defaultInstance();
    private final Map<String, JavaType> types = new HashMap<>();

    public SharedKeys() {
        types.put(MESSAGES, typeFactory.constructCollectionType(List.class, UiMessage.class));
    }

    /** Zaregistruje ďalšie zdieľané kľúče (Spring XML: mapa kľúč → trieda). */
    public void setTypes(Map<String, Class<?>> registered) {
        for (Map.Entry<String, Class<?>> entry : registered.entrySet()) {
            types.put(entry.getKey(), typeFactory.constructType(entry.getValue()));
        }
    }

    /** Typ hodnoty kľúča, alebo {@code null}, ak sa kľúč nezdieľa. */
    public JavaType typeOf(String key) {
        return types.get(key);
    }

    /** Či sa kľúč zapisuje do zdieľanej session. */
    public boolean isShared(String key) {
        return types.containsKey(key);
    }
}
