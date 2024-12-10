package com.dsv.model;

public enum EMessageType {
    REQUEST_ACCESS,      // Žádost o přístup ke zdroji
    GRANT_ACCESS,       // Povolení přístupu
    DENY_ACCESS,        // Zamítnutí přístupu
    RELEASE_ACCESS,     // Uvolnění zdroje
    ACKNOWLEDGE,         // Potvrzení přijetí
    CONNECTION_TEST,      // Testovací zpráva pro testování připojení
    READ_CRITIC_VALUE,    // Čtení kritické hodnoty
    WRITE_CRITIC_VALUE,    // Zápis kritické hodnoty
    VALUE_RESPONSE        // Odpověď na čtení kritické hodnoty
} 
