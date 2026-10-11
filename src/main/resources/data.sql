-- ==============================================================================
-- DATASET COMPLETO DE PRUEBA PARA H2 (Modo PostgreSQL)
-- Proyecto: PWA del Videojuego (Comunidad, Lore, Arte y Logros)
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- 1. USUARIOS (users)
-- ------------------------------------------------------------------------------
INSERT INTO users (user_id, username, email, password, profile_picture, is_deleted, created_at, updated_at) VALUES
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a01', 'admin_valkyrie', 'admin@eot.com', '$2a$10$abcdefghijklmnopqrstuvwxyz123456', 'https://images.unsplash.com/photo-1566492031773-4f4e44671857?w=150', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a02', 'lore_keeper_elric', 'lore@eot.com', '$2a$10$abcdefghijklmnopqrstuvwxyz123456', 'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=150', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a03', 'speedrunner_kai', 'kai@eot.com', '$2a$10$abcdefghijklmnopqrstuvwxyz123456', 'https://images.unsplash.com/photo-1570295999919-56ceb5ecca61?w=150', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a04', 'concept_artist_sol', 'sol@eot.com', '$2a$10$abcdefghijklmnopqrstuvwxyz123456', 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=150', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a05', 'novice_adventurer', 'novice@eot.com', '$2a$10$abcdefghijklmnopqrstuvwxyz123456', 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a06', 'shadow_assassin', 'shadow@eot.com', '$2a$10$abcdefghijklmnopqrstuvwxyz123456', 'https://images.unsplash.com/photo-1522075469751-3a6694fb2f61?w=150', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ------------------------------------------------------------------------------
-- 2. CATEGORÍAS DEL FORO (categories)
-- ------------------------------------------------------------------------------
INSERT INTO categories (category_id, category_name, description, is_deleted, created_at, updated_at) VALUES
('10eebc99-9c0b-4ef8-bb6d-6bb9bd380a01', 'Noticias y Parches Oficiales', 'Comunicados de los desarrolladores, notas de versión y eventos en vivo.', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('10eebc99-9c0b-4ef8-bb6d-6bb9bd380a02', 'Lore, Historia y Misterios', 'Análisis profundo de la trama del juego, inscripciones rúnicas y teorías de la comunidad.', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('10eebc99-9c0b-4ef8-bb6d-6bb9bd380a03', 'Guías, Estrategias y Builds', 'Consejos para vencer jefes, combinaciones de habilidades y rutas de speedrun.', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('10eebc99-9c0b-4ef8-bb6d-6bb9bd380a04', 'Galería de Concept Art y Fan Art', 'Bocetos oficiales de producción, diseños de personajes e ilustraciones creadas por fans.', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('10eebc99-9c0b-4ef8-bb6d-6bb9bd380a05', 'La Taberna (Comunidad General)', 'Conversaciones casuales, presentaciones y debate libre sobre el juego.', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ------------------------------------------------------------------------------
-- 3. HILOS DE DISCUSIÓN (threads)
-- ------------------------------------------------------------------------------
INSERT INTO threads (thread_id, category_id, user_id, title, content, is_locked, is_deleted, created_at, updated_at) VALUES
('20eebc99-9c0b-4ef8-bb6d-6bb9bd380a01', '10eebc99-9c0b-4ef8-bb6d-6bb9bd380a01', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a01', 
 'Notas del Parche v1.2: Expansión Las Tierras Marchitas', 
 '¡Saludos viajeros! La versión 1.2 añade una nueva zona del mapa, 3 jefes colosales y balanceo de magia oscura. El hilo permanecerá fijado para consultas.', 
 true, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('20eebc99-9c0b-4ef8-bb6d-6bb9bd380a02', '10eebc99-9c0b-4ef8-bb6d-6bb9bd380a02', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a02', 
 'El Enigma de la Séptima Dinastía: ¿Quién forjó la Espada del Alba?', 
 'Analizando las escrituras encontradas en el nivel 4 de las Catacumbas, descubrí una referencia directa al Rey Ciego. ¿Creen que él fue el forjador original antes de la corrupción?', 
 false, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('20eebc99-9c0b-4ef8-bb6d-6bb9bd380a03', '10eebc99-9c0b-4ef8-bb6d-6bb9bd380a03', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a03', 
 'Build definitiva de Destreza Pura para Speedrun Any%', 
 'Comparto la distribución exacta de puntos de atributo y armas necesarias para saltarse la fase 2 del Guardián del Umbral. Logré un tiempo de 24m 12s.', 
 false, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('20eebc99-9c0b-4ef8-bb6d-6bb9bd380a04', '10eebc99-9c0b-4ef8-bb6d-6bb9bd380a04', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a04', 
 'Evolución Visual: Desde el boceto a lápiz hasta el modelo 3D del Golem', 
 'Les comparto cómo nació el diseño del Golem de Obsidiana. Inicialmente tenía alas de piedra, pero decidimos darle un aspecto más monolítico y pesado.', 
 false, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('20eebc99-9c0b-4ef8-bb6d-6bb9bd380a05', '10eebc99-9c0b-4ef8-bb6d-6bb9bd380a05', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a05', 
 '¡Hola a todos! Acabo de comenzar a jugar y tengo una duda con los parrys', 
 'Soy nuevo en la comunidad. ¿El timing de desvío con escudo ligero cambia según el tipo de ataque del enemigo o siempre tiene los mismos frames de invulnerabilidad?', 
 false, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('20eebc99-9c0b-4ef8-bb6d-6bb9bd380a06', '10eebc99-9c0b-4ef8-bb6d-6bb9bd380a02', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a06', 
 'Secretos ocultos en el diálogo del Mercader Errante', 
 'Si hablas con el mercader llevando el Anillo de Jade equipado, revela una línea de voz secreta sobre el destino de la princesa.', 
 false, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ------------------------------------------------------------------------------
-- 4. PUBLICACIONES Y RESPUESTAS (posts)
-- ------------------------------------------------------------------------------
INSERT INTO posts (post_id, thread_id, user_id, parent_post_id, content, is_deleted, created_at, updated_at) VALUES
-- Posts del hilo de Lore (20eebc99-9c0b-4ef8-bb6d-6bb9bd380a02)
('30eebc99-9c0b-4ef8-bb6d-6bb9bd380a01', '20eebc99-9c0b-4ef8-bb6d-6bb9bd380a02', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a02', NULL, 
 'Aquí adjunto la transcripción de las 3 tablas cuneiformes del templo superior.', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('30eebc99-9c0b-4ef8-bb6d-6bb9bd380a02', '20eebc99-9c0b-4ef8-bb6d-6bb9bd380a02', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a06', '30eebc99-9c0b-4ef8-bb6d-6bb9bd380a01', 
 '¡Brillante! Coincide con la descripción del escudo que se obtiene en el Acto 3.', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('30eebc99-9c0b-4ef8-bb6d-6bb9bd380a03', '20eebc99-9c0b-4ef8-bb6d-6bb9bd380a02', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a03', '30eebc99-9c0b-4ef8-bb6d-6bb9bd380a02', 
 'Exacto, y si examinas el ícono en alta resolución se ve la misma insignia rúnica.', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- Posts del hilo de Speedrun (20eebc99-9c0b-4ef8-bb6d-6bb9bd380a03)
('30eebc99-9c0b-4ef8-bb6d-6bb9bd380a04', '20eebc99-9c0b-4ef8-bb6d-6bb9bd380a03', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a03', NULL, 
 'Lista de equipamiento: Dagas de Viento +5, Capa de Sombras y Poción de Vigor nivel 2.', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('30eebc99-9c0b-4ef8-bb6d-6bb9bd380a05', '20eebc99-9c0b-4ef8-bb6d-6bb9bd380a03', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a05', '30eebc99-9c0b-4ef8-bb6d-6bb9bd380a04', 
 '¿Las dagas se consiguen antes o después del pantano? Me cuesta encontrarlas.', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('30eebc99-9c0b-4ef8-bb6d-6bb9bd380a06', '20eebc99-9c0b-4ef8-bb6d-6bb9bd380a03', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a03', '30eebc99-9c0b-4ef8-bb6d-6bb9bd380a05', 
 'Justo antes de entrar al puente del pantano, a la derecha hay una pared ilusoria.', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- Posts del hilo de Concept Art (20eebc99-9c0b-4ef8-bb6d-6bb9bd380a04)
('30eebc99-9c0b-4ef8-bb6d-6bb9bd380a07', '20eebc99-9c0b-4ef8-bb6d-6bb9bd380a04', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a04', NULL, 
 'Pronto publicaremos los bocetos de las armaduras de la guardia real.', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('30eebc99-9c0b-4ef8-bb6d-6bb9bd380a08', '20eebc99-9c0b-4ef8-bb6d-6bb9bd380a04', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a01', '30eebc99-9c0b-4ef8-bb6d-6bb9bd380a07', 
 'El trabajo del departamento de arte en esta expansión ha sido insuperable.', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- Posts del hilo de Taberna / Dudas (20eebc99-9c0b-4ef8-bb6d-6bb9bd380a05)
('30eebc99-9c0b-4ef8-bb6d-6bb9bd380a09', '20eebc99-9c0b-4ef8-bb6d-6bb9bd380a05', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a06', NULL, 
 'El timing es fijo (12 frames activos), pero los ataques pesados tienen un retraso visual.', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('30eebc99-9c0b-4ef8-bb6d-6bb9bd380a10', '20eebc99-9c0b-4ef8-bb6d-6bb9bd380a05', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a05', '30eebc99-9c0b-4ef8-bb6d-6bb9bd380a09', 
 '¡Muchas gracias! Ya pude vencer al primer caballero con ese consejo.', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ------------------------------------------------------------------------------
-- 5. LOGROS DEL SISTEMA (achievements)
-- ------------------------------------------------------------------------------
INSERT INTO achievements (ach_id, ach_name, ach_description, ach_picture, is_ach_hidden, is_deleted, created_at, updated_at) VALUES
('40eebc99-9c0b-4ef8-bb6d-6bb9bd380a01', 'Despertar del Héroe', 'Completa el tutorial y huye de la Mazmorra de Ceniza.', 'https://assets.eot.com/badges/awakening.png', false, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('40eebc99-9c0b-4ef8-bb6d-6bb9bd380a02', 'Arqueólogo del Reino', 'Descubre y activa los 5 monolitos antiguos de lore.', 'https://assets.eot.com/badges/archaeologist.png', false, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('40eebc99-9c0b-4ef8-bb6d-6bb9bd380a03', 'Asesino Implacable', 'Derrota a 100 criaturas corruptas de la niebla.', 'https://assets.eot.com/badges/slayer.png', false, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('40eebc99-9c0b-4ef8-bb6d-6bb9bd380a04', 'Danza de Acero', 'Vence a un jefe de zona sin recibir ningún golpe.', 'https://assets.eot.com/badges/flawless.png', false, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('40eebc99-9c0b-4ef8-bb6d-6bb9bd380a05', 'Coleccionista de Obsidiana', 'Consigue todas las armas forjadas en obsidiana negra.', 'https://assets.eot.com/badges/obsidian_collector.png', false, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('40eebc99-9c0b-4ef8-bb6d-6bb9bd380a06', 'Voz de la Comunidad', 'Publica tu primer hilo o comentario en los foros de la PWA.', 'https://assets.eot.com/badges/community_voice.png', false, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('40eebc99-9c0b-4ef8-bb6d-6bb9bd380a07', 'El Secreto de la Corona', 'Logro Oculto: Encuentra el relicario de la reina en el fondo del lago.', 'https://assets.eot.com/badges/secret_crown.png', true, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('40eebc99-9c0b-4ef8-bb6d-6bb9bd380a08', 'El Fin de la Noche', 'Logro Oculto: Desbloquea el final verdadero del videojuego.', 'https://assets.eot.com/badges/true_ending.png', true, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ------------------------------------------------------------------------------
-- 6. PROGRESO DE LOGROS DE USUARIOS (user_achievements)
-- ------------------------------------------------------------------------------
INSERT INTO user_achievements (user_id, ach_id, progress, is_unlocked, unlocked_at, is_deleted, created_at, updated_at) VALUES
-- Progreso de admin_valkyrie (Jugador veterano y desarrollador)
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a01', '40eebc99-9c0b-4ef8-bb6d-6bb9bd380a01', 100, true, CURRENT_TIMESTAMP, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a01', '40eebc99-9c0b-4ef8-bb6d-6bb9bd380a02', 100, true, CURRENT_TIMESTAMP, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a01', '40eebc99-9c0b-4ef8-bb6d-6bb9bd380a03', 100, true, CURRENT_TIMESTAMP, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a01', '40eebc99-9c0b-4ef8-bb6d-6bb9bd380a06', 100, true, CURRENT_TIMESTAMP, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- Progreso de speedrunner_kai (Completista y jugador habilidoso)
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a03', '40eebc99-9c0b-4ef8-bb6d-6bb9bd380a01', 100, true, CURRENT_TIMESTAMP, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a03', '40eebc99-9c0b-4ef8-bb6d-6bb9bd380a03', 100, true, CURRENT_TIMESTAMP, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a03', '40eebc99-9c0b-4ef8-bb6d-6bb9bd380a04', 100, true, CURRENT_TIMESTAMP, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a03', '40eebc99-9c0b-4ef8-bb6d-6bb9bd380a08', 100, true, CURRENT_TIMESTAMP, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- Progreso de lore_keeper_elric (Enfocado en exploración y lore)
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a02', '40eebc99-9c0b-4ef8-bb6d-6bb9bd380a01', 100, true, CURRENT_TIMESTAMP, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a02', '40eebc99-9c0b-4ef8-bb6d-6bb9bd380a02', 100, true, CURRENT_TIMESTAMP, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a02', '40eebc99-9c0b-4ef8-bb6d-6bb9bd380a07', 100, true, CURRENT_TIMESTAMP, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- Progreso de novice_adventurer (Principiante con progreso parcial)
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a05', '40eebc99-9c0b-4ef8-bb6d-6bb9bd380a01', 100, true, CURRENT_TIMESTAMP, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a05', '40eebc99-9c0b-4ef8-bb6d-6bb9bd380a02', 40, false, NULL, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a05', '40eebc99-9c0b-4ef8-bb6d-6bb9bd380a03', 25, false, NULL, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a05', '40eebc99-9c0b-4ef8-bb6d-6bb9bd380a06', 100, true, CURRENT_TIMESTAMP, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
