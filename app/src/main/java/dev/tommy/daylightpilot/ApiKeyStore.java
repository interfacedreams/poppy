package dev.tommy.daylightpilot;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.security.KeyStore;
import java.nio.charset.StandardCharsets;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Encrypts the user-supplied credential with a non-exportable Android Keystore key. */
final class ApiKeyStore {
    private static final String ALIAS = "poppy.openai";
    private static SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(ALIAS)) return (SecretKey) store.getKey(ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }
    static boolean hasKey(Context context) {
        return context.getSharedPreferences("credentials", Context.MODE_PRIVATE).contains("encrypted");
    }
    static void save(Context context, String value) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        String encrypted = Base64.encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
        if (!context.getSharedPreferences("credentials", Context.MODE_PRIVATE).edit()
                .putString("encrypted", encrypted).putString("iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP)).commit())
            throw new java.io.IOException("Could not save key.");
    }
    static String read(Context context) throws Exception {
        android.content.SharedPreferences prefs = context.getSharedPreferences("credentials", Context.MODE_PRIVATE);
        if (!hasKey(context)) throw new java.io.IOException("Open Settings and add your OpenAI API key first.");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(prefs.getString("iv", ""), Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(prefs.getString("encrypted", ""), Base64.NO_WRAP)), StandardCharsets.UTF_8);
    }
    static void delete(Context context) throws Exception {
        if (!context.getSharedPreferences("credentials", Context.MODE_PRIVATE).edit().clear().commit())
            throw new java.io.IOException("Could not remove key.");
    }
}
