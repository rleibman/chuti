CREATE TABLE `user`
(
    `id`             int(11)    NOT NULL AUTO_INCREMENT,
    `hashedPassword` text       NULL,
    `name`           text       NOT NULL,
    `created`        timestamp  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `lastUpdated`    timestamp  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `lastLoggedIn`   timestamp  NULL     DEFAULT NULL,
    `email`          varchar(255)        DEFAULT NULL,
    `deleted`        tinyint(4) NOT NULL DEFAULT '0',
    `deletedDate`    timestamp  NULL     DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8;

ALTER TABLE `user`
    ADD not_archived BOOLEAN
        GENERATED ALWAYS AS (IF(deleted = false, 1, NULL)) VIRTUAL;

ALTER TABLE `user`
    ADD CONSTRAINT UNIQUE (email, not_archived);

create table friends
(
    one int(11) not null,
    two int(11) not null,
    key friends_user_1 (one),
    constraint `friends_user_1` foreign key (one) references `user` (id),
    key friends_user_2 (two),
    constraint `friends_user_2` foreign key (two) references `user` (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8;

create table game
(
    `id`          int(11)    NOT NULL AUTO_INCREMENT,
    current_index int(11)    NOT NULL default 0,
    start_state   json       not null,
    game_state    json       not null,
    `created`     timestamp  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `lastUpdated` timestamp  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `deleted`     tinyint(4) NOT NULL DEFAULT '0',
    `deletedDate` timestamp  NULL     DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8;

create table game_event
(
    game_id       int(11) not null,
    current_index int(11) NOT NULL default 0,
    event_data    json    not null,
    primary key (game_id, current_index),
    key game_event_game (game_id),
    constraint game_event_game foreign key (game_id) references `game` (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8;

create table game_players
(
    user_id int(11) not null,
    game_id int(11) not null,
    key game_players_user (user_id),
    constraint game_players_user foreign key (user_id) references `user` (id),
    key game_players_game (game_id),
    constraint game_players_game foreign key (game_id) references `game` (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8;
