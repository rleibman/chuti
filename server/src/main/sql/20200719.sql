CREATE TABLE `token`
(
    tok          varchar(255) not null,
    userId       int(11)      not null,
    tokenPurpose text         not null,
    expireTime   timestamp    not null,
    PRIMARY KEY (`tok`),
    constraint `token_user_id` foreign key (userId) references `user` (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8;
