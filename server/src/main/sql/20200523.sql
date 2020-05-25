alter table user add column `active` tinyint(4) NOT NULL DEFAULT '0';
alter table game_players add column invited tinyint(4) NOT NULL DEFAULT '0';