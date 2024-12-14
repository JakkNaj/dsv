package com.dsv.model;

public enum EMessageType {
    REQUEST_ACCESS,      // Žádost o přístup ke zdroji
    GRANT_ACCESS,       // Povolení přístupu
    DENY_ACCESS,        // Zamítnutí přístupu
    RELEASE_ACCESS,     // Uvolnění zdroje
    ACKNOWLEDGE,         // Potvrzení přijetí
    CONNECTION_TEST,      // Testovací zpráva pro testování připojení
    VALUE_RESPONSE,        // Odpověď na čtení kritické hodnoty
    PRELIMINARY_REQUEST,  // Předběžná žádost o zdroj
    QUEUE_UPDATE,        // Aktualizace fronty od zdroje
} 
