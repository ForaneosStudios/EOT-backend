# Backend - PWA del Videojuego (Comunidad y Lore)

Repositorio que contiene el código fuente del backend para la Progressive Web App (PWA) oficial del videojuego y la conexion entre el juego y la base de datos. Esta API RESTful centraliza la interacción de la comunidad, la muestra de arte conceptual del juego y el progreso de los jugadores.

## Tecnologías y Herramientas

*   **Framework:** Springboot 4.1.1
*   **Lenguaje:** Java 21
*   **Base de Datos:** PostgreSQL
*   **Autenticación:** [Ej. JWT (JSON Web Tokens)]

## Características Principales

*   **Registro y Autenticación:** Sistema de creación de cuentas, login seguro y gestión de perfiles de jugador.
*   **Comunidad y Foro:** Endpoints para la creación y moderación de hilos de discusión, comentarios y categorías del foro.
*   **Galería de Concept Art:** Servicios para servir e indexar imágenes, bocetos y detalles de preproducción del mundo del juego.
*   **Sistema de Logros:** Lógica para registrar, validar y consultar los trofeos o logros desbloqueados por cada jugador en su perfil.

## Requisitos Previos

*   [Java Development Kit (JDK) 17+ o entorno de Node.js]
 *   [Gestor de base de datos local o en contenedor Docker, ej. PostgreSQL]

## Instalación y Ejecución Local

### H2 (recomendado para pruebas rápidas)

No requiere Docker. Arranca la API y carga el dataset de prueba:

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=h2"
```

La consola queda disponible en `http://localhost:8080/h2-console` con JDBC URL
`jdbc:h2:mem:eot`, usuario `sa` y contraseña vacía. Para ejecutar los tests:

```powershell
mvn test
```

### PostgreSQL local con Docker

1. (Opcional) copia `.env.example` a `.env` y ajusta las credenciales.
2. Inicia PostgreSQL y espera a que esté saludable:

```powershell
docker compose up -d
docker compose ps
```

PostgreSQL queda publicado en `localhost:5433` para no interferir con otra
instalación local que use el puerto estándar 5432.

3. Arranca la API contra el contenedor:

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=postgres"
```

El contenedor inicializa el esquema y el dataset de `src/main/resources/data.sql`
solo al crearse el volumen. Para reinicializar por completo la base local:

```powershell
docker compose down -v
docker compose up -d
```

1. **Clonar el repositorio:**
   ```bash
   git clone [https://github.com/tu-usuario/repo-backend-juego.git](https://github.com/tu-usuario/repo-backend-juego.git)
   cd repo-backend-juego
