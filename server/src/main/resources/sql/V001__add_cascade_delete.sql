-- Add CASCADE DELETE to game_event and game_players foreign keys
-- This ensures that when a game is deleted, all related records are automatically deleted

-- Drop existing foreign key for game_event
ALTER TABLE `game_event` DROP FOREIGN KEY `game_event_game`;

-- Recreate with ON DELETE CASCADE
ALTER TABLE `game_event`
    ADD CONSTRAINT `game_event_game`
        FOREIGN KEY (`game_id`) REFERENCES `game` (`id`)
            ON DELETE CASCADE;

-- Drop existing foreign key for game_players
ALTER TABLE `game_players` DROP FOREIGN KEY `game_players_game`;

-- Recreate with ON DELETE CASCADE
ALTER TABLE `game_players`
    ADD CONSTRAINT `game_players_game`
        FOREIGN KEY (`game_id`) REFERENCES `game` (`id`)
            ON DELETE CASCADE;
