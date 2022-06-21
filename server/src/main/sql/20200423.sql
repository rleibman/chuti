alter table game modify column game_state TEXT NOT NULL;
alter table game_players add primary key (user_id, game_id);
alter table game_players add column `sort_order` int(11) NOT NULL;