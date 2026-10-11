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

1. **Clonar el repositorio:**
   ```bash
   git clone [https://github.com/tu-usuario/repo-backend-juego.git](https://github.com/tu-usuario/repo-backend-juego.git)
   cd repo-backend-juego
