create table userWallet
(
    userId int(11)        not null primary key,
    amount DECIMAL(13, 4) not null,
    key wallet_user_1 (userId),
    constraint `wallet_user_1` foreign key (userId) references `user` (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8;

alter table game
    change start_state lastSnapshot json;
alter table game
    drop column deleted;
alter table game
    drop column deletedDate;
alter table game
    change game_state status text;

create table userLog
(
    userId int(11)   not null,
    `time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    primary key (userId, `time`),
    key user_log_1 (userId),
    constraint `user_log_1` foreign key (userId) references `user` (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8;


