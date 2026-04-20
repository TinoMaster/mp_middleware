package it.ariaspa.mypay.mypaycore.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.ApplicationPidFileWriter;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Punto di ingresso dell'applicazione Spring Boot del middleware.
 *
 * <p>Configura il listener {@link ApplicationPidFileWriter} per scrivere il PID
 * del processo nel file {@code shutdown.pid}, utilizzato per l'arresto controllato
 * dell'applicazione in ambienti di deploy.
 *
 * <p>{@code @EnableScheduling} è necessario per abilitare il task pianificato
 * di pulizia della cache upload proxy ({@code @Scheduled} in
 * {@link it.ariaspa.mypay.mypaycore.api.upload.UploadProxyCacheService}).
 */
@SpringBootApplication
@ComponentScan(basePackages = { "it.ariaspa" })
@EnableScheduling
@Slf4j
public class Application {
	public static void main(String[] args) {
		SpringApplicationBuilder app = new SpringApplicationBuilder(Application.class);
		app.build().addListeners(new ApplicationPidFileWriter("./shutdown.pid"));
		app.run(args);
		log.debug("Applicazione avviata correttamente");
	}
}
