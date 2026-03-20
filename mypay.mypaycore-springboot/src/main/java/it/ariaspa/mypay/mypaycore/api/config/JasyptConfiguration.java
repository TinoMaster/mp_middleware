package it.ariaspa.mypay.mypaycore.api.config;

import org.jasypt.encryption.StringEncryptor;
import org.jasypt.encryption.pbe.PooledPBEStringEncryptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JasyptConfiguration {

    @Value("${spring.datasource.crypt.secret:secret}")
    private String secret;
    @Bean("jasyptStringEncryptor")
    public StringEncryptor stringEncryptor() {
        PooledPBEStringEncryptor encryptor = new PooledPBEStringEncryptor();
        encryptor.setAlgorithm("PBEWithMD5AndDES");
        encryptor.setPassword(secret);
        encryptor.setPoolSize(1);
        return encryptor;
    }

    public static void main(String[] args) {
        //StrongPasswordEncryptor passwordEncryptor = new StrongPasswordEncryptor();
        String password = "";
        String encryptedPassword = new JasyptConfiguration().stringEncryptor().encrypt(password);
        System.out.println("Password criptata: " + encryptedPassword);
    }
}