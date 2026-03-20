package it.ariaspa.mypay.mypaycore.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.ApplicationPidFileWriter;
import org.springframework.context.annotation.ComponentScan;

/**
 * Punto di ingresso dell'applicazione Spring Boot del middleware.
 *
 * <p>Configura il listener {@link ApplicationPidFileWriter} per scrivere il PID
 * del processo nel file {@code shutdown.pid}, utilizzato per l'arresto controllato
 * dell'applicazione in ambienti di deploy.
 */
@SpringBootApplication
@ComponentScan(basePackages = { "it.ariaspa" })
@Slf4j
public class Application {
	public static void main(String[] args) {
		SpringApplicationBuilder app = new SpringApplicationBuilder(Application.class);
		app.build().addListeners(new ApplicationPidFileWriter("./shutdown.pid"));
		app.run(args);
		log.debug("Applicazione avviata correttamente");
	}
}
