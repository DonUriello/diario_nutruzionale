package pj.eu.diarionutrizionale;

import org.springframework.boot.SpringApplication;

public class TestDiarionutrizionaleApplication {

	public static void main(String[] args) {
		SpringApplication.from(DiarionutrizionaleApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
