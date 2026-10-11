package com.EOT.project;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
class ProjectApplicationTests {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void contextLoads() {
		assertNotNull(jdbcTemplate, "JdbcTemplate debe estar disponible en el contexto de Spring");
	}

	@Test
	void testH2DatabaseCompleteSeedData() {
		// Validar que el dataset completo de H2 se cargó exitosamente
		Integer usersCount = jdbcTemplate.queryForObject("SELECT count(*) FROM users", Integer.class);
		Integer categoriesCount = jdbcTemplate.queryForObject("SELECT count(*) FROM categories", Integer.class);
		Integer threadsCount = jdbcTemplate.queryForObject("SELECT count(*) FROM threads", Integer.class);
		Integer postsCount = jdbcTemplate.queryForObject("SELECT count(*) FROM posts", Integer.class);
		Integer achievementsCount = jdbcTemplate.queryForObject("SELECT count(*) FROM achievements", Integer.class);
		Integer userAchievementsCount = jdbcTemplate.queryForObject("SELECT count(*) FROM user_achievements", Integer.class);

		assertEquals(6, usersCount, "Deben existir 6 usuarios cargados en H2");
		assertEquals(5, categoriesCount, "Deben existir 5 categorías de foro cargadas");
		assertEquals(6, threadsCount, "Deben existir 6 hilos de discusión cargados");
		assertEquals(10, postsCount, "Deben existir 10 publicaciones/respuestas cargadas");
		assertEquals(8, achievementsCount, "Deben existir 8 logros del videojuego cargados");
		assertEquals(15, userAchievementsCount, "Deben existir 15 progresos de logros de usuarios cargados");
	}

}
