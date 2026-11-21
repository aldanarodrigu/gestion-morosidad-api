package com.intendencia.gestion_morosidad_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GestionMorosidadApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(GestionMorosidadApiApplication.class, args);
	}

}
