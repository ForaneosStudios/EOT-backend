# Backend - PWA del videojuego

API de comunidad, foro y logros con Java 21, Spring Boot y PostgreSQL. La
autenticación corresponde a Supabase Auth; Spring valida sus tokens y aplica los
permisos de la aplicación.

## Autenticación y perfiles

Configura `SUPABASE_AUTH_ISSUER` con la URL del emisor, por ejemplo
`https://<project-ref>.supabase.co/auth/v1`. Spring valida firma ES256 o RS256,
emisor, audiencia `authenticated` y caducidad. Sin emisor, rechaza los tokens.
Se requiere una clave asimétrica publicada en el JWKS de Supabase; los proyectos
que solo usen una clave simétrica antigua necesitan una configuración diferente.

En la PWA (que no se encuentra en este repositorio):

1. Registra e inicia sesión usando el SDK de Supabase Auth.
2. Envía el token de acceso a Spring con `Authorization: Bearer <token>`.
3. Para crear el perfil del juego, llama una vez a `POST /api/v1/users/me/profile`
   con `{"username":"jugador", "profilePicture":null}`. El correo e ID se toman
   del token, no de la solicitud. El rol inicial es siempre `USER`.
4. Usa `GET /api/v1/users/me` para consultar el perfil.
5. Para cerrar la sesión usa Supabase `signOut({ scope: 'local' })`. Spring no
   administra cookies de sesión. Un token ya emitido sigue válido hasta expirar.

Ejemplo para un cliente JavaScript (requiere `@supabase/supabase-js` y sus propias
variables de configuración):

```javascript
const { data, error } = await supabase.auth.signInWithPassword({ email, password });
if (error) throw error;

const response = await fetch(`${apiUrl}/api/v1/users/me`, {
  headers: { Authorization: `Bearer ${data.session.access_token}` },
});
if (!response.ok) throw new Error(`Error de API: ${response.status}`);
const profile = await response.json();

// Al cerrar sesión:
await supabase.auth.signOut({ scope: 'local' });
```

El registro se realiza con `supabase.auth.signUp({ email, password })`. Si está
activada la confirmación por correo, primero debe confirmarse y después iniciar
sesión; el perfil de Spring se crea con el token de esa sesión. El SDK debe usar
únicamente la clave publicable, nunca la clave secreta o `service_role`.

El perfil guarda el ID de Auth y un rol `USER` o `ADMIN`. Los administradores se
asignan exclusivamente mediante una operación confiable de administración en la
base; no existe un endpoint público que permita hacerlo. Los perfiles antiguos no
se vinculan automáticamente por correo. Las contraseñas antiguas permanecen en la
base durante la transición, pero el backend ya no las usa para iniciar sesión.
Las cuentas de Auth y las contraseñas las administra Supabase, no `public.users`.

Las lecturas públicas de categorías, hilos, posts y logros visibles no requieren
token. Crear categorías y logros requiere `ADMIN`; crear hilos y posts y consultar
o actualizar progreso requiere autenticación y pertenencia al usuario, o ser
administrador. Los usuarios no pueden consultar la lista de correos de otros.
La actualización de progreso todavía acepta datos enviados por el cliente y no
constituye una verificación antifraude de logros.

Las tablas de Supabase mantienen RLS activado y sin políticas públicas. Se
revocaron sus privilegios directos a `anon` y `authenticated`: los datos se
acceden mediante Spring, no directamente desde el SDK de Supabase. Spring debe
usar un usuario de base con permisos adecuados y aplicar su propia autorización.

Para un cliente de otro origen, define `APP_CORS_ALLOWED_ORIGINS` como una lista
de orígenes explícitos separados por comas. Se usa Bearer, no cookies, y no se
habilitan credenciales CORS. Configura HTTPS en producción. Antes de publicar,
configura confirmación de correo, URLs de redirección y SMTP en Supabase Auth.

## Configuración

Las credenciales no deben incluirse en archivos versionados. Usa variables de
entorno `SUPABASE_DB_URL`, `SUPABASE_DB_USER`, `SUPABASE_DB_PASSWORD` y
`SUPABASE_AUTH_ISSUER`. Como alternativa local, crea `.env.supabase.local` en la
raíz con esas mismas claves en formato `CLAVE=valor`; el archivo está ignorado
por Git y Spring lo importa opcionalmente. No incluyas el usuario ni contraseña
en la URL JDBC. Si credenciales anteriores estuvieron en el repositorio, rótalas.

```powershell
mvn spring-boot:run
```

Sin perfil, el backend usa Supabase y valida el esquema sin modificarlo.

## Pruebas locales con H2

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=h2"
mvn test
```

H2 crea el esquema en memoria y carga datos de ejemplo. La consola está en
`http://localhost:8080/h2-console`, URL `jdbc:h2:mem:eot`, usuario `sa` y
contraseña vacía. Los datos se pierden al parar la aplicación. H2 no ejecuta
Supabase Auth: los tests simulan el proveedor y prueban también la validación
criptográfica de JWT con claves de prueba. Para uso manual, un token real puede
autenticarse, pero su perfil debe crearse en la base H2.

## PostgreSQL local con Docker

```powershell
docker compose up -d
docker compose ps
mvn spring-boot:run "-Dspring-boot.run.profiles=postgres"
```

El contenedor expone el puerto 5433 únicamente en `127.0.0.1` y crea el esquema y datos de ejemplo solo
al iniciar un volumen nuevo. El perfil local usa las credenciales predeterminadas
de `LocalPostgresDataSourceConfig`; cambiar `.env` por sí solo no las cambia.

Para un volumen ya existente, aplica estas migraciones antes de arrancar la API
(ajusta usuario/base si los cambiaste). No es necesario borrar el volumen:

```powershell
Get-Content -Raw src/main/resources/db/supabase/migrations/001_add_user_role.sql | docker exec -i eot-postgres psql -v ON_ERROR_STOP=1 -U eot -d eot
Get-Content -Raw src/main/resources/db/postgres/migrations/002_link_auth_local.sql | docker exec -i eot-postgres psql -v ON_ERROR_STOP=1 -U eot -d eot
Get-Content -Raw src/main/resources/db/supabase/migrations/003_allow_auth_profiles.sql | docker exec -i eot-postgres psql -v ON_ERROR_STOP=1 -U eot -d eot
```

Este PostgreSQL no incluye `auth.users`: refleja los campos, pero no el servicio de
Auth ni su clave foránea. No uses `docker compose down -v` salvo que quieras borrar
los datos locales.

## Supabase Auth y PostgreSQL locales

Con Docker Desktop activo, el proyecto incluye la configuración y las migraciones
para iniciar un stack local independiente del contenedor PostgreSQL anterior:

**Seguridad:** en este equipo la CLI publicó los puertos de Supabase en todas las
interfaces de red (`0.0.0.0`), incluso al usar una red Docker configurada para
localhost. Usa este stack solo en una red confiable y revisa `docker ps` y la
configuración del firewall de Docker/Windows antes de iniciarlo; contiene claves
y contraseñas conocidas de desarrollo. Para detenerlo sin borrar sus volúmenes,
usa `npx.cmd --yes supabase@2.122.0 stop` (no uses `--no-backup`).

```powershell
npx.cmd --yes supabase@2.122.0 start
mvn spring-boot:run "-Dspring-boot.run.profiles=supabase-local"
```

El primer inicio descarga imágenes. La API local de Supabase usa el puerto 54321,
PostgreSQL el 54322 y Studio el 54323. `npx.cmd --yes supabase@2.122.0 status`
muestra la URL y claves de desarrollo; nunca publiques la clave secreta.
La confirmación de correo está desactivada solo en la configuración local. El
backend usa el JWKS local y las migraciones se aplican en el primer inicio.

Las migraciones en `supabase/migrations` preparan la base local desde cero. No
ejecutes `supabase db push` contra el proyecto remoto sin revisar y reconciliar
primero el historial: algunas migraciones ya fueron aplicadas por separado.
