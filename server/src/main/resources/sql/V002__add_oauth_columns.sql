-- Add OAuth provider columns to user table
ALTER TABLE `user`
    ADD COLUMN `oauthProvider`     varchar(50)  NULL DEFAULT NULL,
    ADD COLUMN `oauthProviderId`   varchar(255) NULL DEFAULT NULL,
    ADD COLUMN `oauthProviderData` text         NULL DEFAULT NULL;

-- Index for looking up users by OAuth provider
CREATE INDEX `idx_user_oauth` ON `user` (`oauthProvider`, `oauthProviderId`);
