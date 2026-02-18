-- Chuti Database Schema
-- This file contains the complete database schema consolidated from all migrations

CREATE TABLE `user`
(
    `id`             int(11)      NOT NULL AUTO_INCREMENT,
    `hashedPassword` text         NULL,
    `name`           text         NOT NULL,
    `email`          varchar(255) DEFAULT NULL,
    `created`        timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `lastUpdated`    timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `active`         tinyint(4)   NOT NULL DEFAULT '0',
    `deleted`            tinyint(4)   NOT NULL DEFAULT '0',
    `deletedDate`        timestamp    NULL     DEFAULT NULL,
    `oauthProvider`      varchar(50)  NULL     DEFAULT NULL,
    `oauthProviderId`    varchar(255) NULL     DEFAULT NULL,
    `oauthProviderData`  text         NULL     DEFAULT NULL,
    PRIMARY KEY (`id`),
    INDEX `idx_user_oauth` (`oauthProvider`, `oauthProviderId`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8;

CREATE TABLE `friends`
(
    `one` int(11) NOT NULL,
    `two` int(11) NOT NULL,
    KEY `friends_user_1` (`one`),
    KEY `friends_user_2` (`two`),
    CONSTRAINT `friends_user_1` FOREIGN KEY (`one`) REFERENCES `user` (`id`),
    CONSTRAINT `friends_user_2` FOREIGN KEY (`two`) REFERENCES `user` (`id`),
    CONSTRAINT `friendsConstraint` UNIQUE (`one`, `two`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8;

CREATE TABLE `game`
(
    `id`            int(11)    NOT NULL AUTO_INCREMENT,
    `current_index` int(11)    NOT NULL DEFAULT 0,
    `lastSnapshot`  json       NOT NULL,
    `status`        text       NOT NULL,
    `created`       timestamp  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `lastUpdated`   timestamp  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `deleted`       tinyint(4) NOT NULL DEFAULT '0',
    `deletedDate`   timestamp  NULL     DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8;

CREATE TABLE `game_event`
(
    `game_id`       int(11) NOT NULL,
    `current_index` int(11) NOT NULL DEFAULT 0,
    `event_data`    json    NOT NULL,
    PRIMARY KEY (`game_id`, `current_index`),
    KEY `game_event_game` (`game_id`),
    CONSTRAINT `game_event_game` FOREIGN KEY (`game_id`) REFERENCES `game` (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8;

CREATE TABLE `game_players`
(
    `user_id`    int(11)    NOT NULL,
    `game_id`    int(11)    NOT NULL,
    `sort_order` int(11)    NOT NULL,
    `invited`    tinyint(4) NOT NULL DEFAULT '0',
    PRIMARY KEY (`user_id`, `game_id`),
    KEY `game_players_user` (`user_id`),
    KEY `game_players_game` (`game_id`),
    CONSTRAINT `game_players_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
    CONSTRAINT `game_players_game` FOREIGN KEY (`game_id`) REFERENCES `game` (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8;

CREATE TABLE `userWallet`
(
    `userId` int(11)        NOT NULL,
    `amount` DECIMAL(13, 4) NOT NULL,
    PRIMARY KEY (`userId`),
    KEY `wallet_user_1` (`userId`),
    CONSTRAINT `wallet_user_1` FOREIGN KEY (`userId`) REFERENCES `user` (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8;

CREATE TABLE `userLog`
(
    `userId` int(11)   NOT NULL,
    `time`   timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`userId`, `time`),
    KEY `user_log_1` (`userId`),
    CONSTRAINT `user_log_1` FOREIGN KEY (`userId`) REFERENCES `user` (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8;

CREATE TABLE `token`
(
    `tok`          varchar(255) NOT NULL,
    `userId`       int(11)      NOT NULL,
    `tokenPurpose` text         NOT NULL,
    `expireTime`   timestamp    NOT NULL,
    PRIMARY KEY (`tok`),
    CONSTRAINT `token_user_id` FOREIGN KEY (`userId`) REFERENCES `user` (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8;
