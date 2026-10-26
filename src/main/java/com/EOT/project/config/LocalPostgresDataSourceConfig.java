package com.EOT.project.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;

/**
 * Fuente de datos exclusiva para el perfil local de PostgreSQL.
 * Evita que credenciales de un entorno remoto heredadas por el sistema
 * sobrescriban la base de Docker durante el desarrollo.
 */
@Configuration
@Profile("postgres")
public class LocalPostgresDataSourceConfig {

    @Bean
    public DataSource dataSource() {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:postgresql://localhost:5433/eot");
        dataSource.setUsername("eot");
        dataSource.setPassword("eot_dev_password");
        return dataSource;
    }
}
